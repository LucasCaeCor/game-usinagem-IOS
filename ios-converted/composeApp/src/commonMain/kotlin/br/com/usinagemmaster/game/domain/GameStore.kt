package br.com.usinagemmaster.game.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.model.*
import br.com.usinagemmaster.domain.simulation.*
import br.com.usinagemmaster.game.model.*
import br.com.usinagemmaster.game.persistence.GameSaveCodec
import br.com.usinagemmaster.game.persistence.PlatformSaveStorage
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private const val SAVE_KEY = "usinagemmaster.kmp.save.v6" // mantém o save criado pela V6.
private const val CYCLE_MILLIS = 10L * 60L * 1000L
private const val MAX_OFFLINE_MILLIS = 24L * 60L * 60L * 1000L
private const val REST_MILLIS = 60L * 60L * 1000L

private const val DAILY_BOOST_TOKENS = 2
private const val MINIGAME_COOLDOWN_MILLIS = 15L * 60L * 1000L
private const val TEAM_SNACK_COST_CENTS = 25_000L
private const val SNACK_IMMUNITY_MILLIS = 8L * 60L * 60L * 1000L
private const val EMPLOYEE_IDLE_MAX_MILLIS = 7L * 60L * 1000L
private const val IDLE_CHECK_MIN_MILLIS = 2L * 60L * 1000L
private const val IDLE_CHECK_MAX_MILLIS = 5L * 60L * 1000L
private const val REPRIMAND_GRACE_MILLIS = 60L * 60L * 1000L
private const val IDLE_EVENT_CHANCE_PERCENT = 30

class GameStore {
    var state by mutableStateOf(loadOrCreate())
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var factoryFrame by mutableStateOf(FactoryFrame())
        private set

    var ownerFrame by mutableStateOf(FactoryOwnerFrame())
        private set

    var lastGachaReward by mutableStateOf<GachaRewardDef?>(null)
        private set

    private val factorySimulation = FactorySimulation()
    private val ownerSimulation = FactoryOwnerSimulation()
    private var cargoInTransitIds: List<String> = emptyList()
    private var idCounter: Long = 0L

    init {
        normalizeAndPersist()
        simulateOffline()
        ensureContracts()
        refreshFactoryInput()
    }

    val dashboard: DashboardStatus
        get() = DashboardStatus(
            companyName = state.company.name,
            cashCents = state.company.cashCents,
            reputation = state.company.reputation,
            companyLevel = state.company.companyLevel,
            machines = state.machines.size,
            employees = state.employees.size,
            activeContracts = state.contracts.count { it.status == "ACTIVE" },
            warehouseSpace = state.company.warehouseSpace,
            usedWarehouseSpace = state.company.usedWarehouseSpace,
            lastSimulationAt = state.company.lastSimulationAt,
        )

    val production: ProductionSnapshot
        get() = calculateProduction(currentTimeMillis())

    val pendingCargo: List<ProductionCargoSave>
        get() = state.cargo.filter { it.pending }

    val pendingCargoCents: Long
        get() = pendingCargo.sumOf { it.valueCents }

    val pendingCargoUnits: Double
        get() = pendingCargo.sumOf { it.unitsMilli }.toDouble() / 1000.0

    val machineShop
        get() = MachineType.values().mapNotNull { MachineCatalog.byType(it.name) }

    val snackActive: Boolean
        get() = state.snackUntil > currentTimeMillis()

    val idleEmployee: EmployeeSave?
        get() = state.workforce.idleEmployeeId?.let { id -> state.employees.firstOrNull { it.id == id } }

    val minigameAvailable: Boolean
        get() = currentTimeMillis() - state.lastMinigameAt >= MINIGAME_COOLDOWN_MILLIS

    val minigameRemainingMillis: Long
        get() = (MINIGAME_COOLDOWN_MILLIS - (currentTimeMillis() - state.lastMinigameAt)).coerceAtLeast(0L)

    fun clearMessage() {
        message = null
    }

    /** Economia/disciplinas; chamado 1x/s. */
    fun tick() {
        val now = currentTimeMillis()
        updateIdleDiscipline(now)

        val elapsed = (now - state.company.lastSimulationAt).coerceAtLeast(0L)
        val settled = (elapsed / CYCLE_MILLIS) * CYCLE_MILLIS
        if (settled >= CYCLE_MILLIS) {
            simulateSettled(settled, now, advanceClock = true)
            ensureContracts()
        }
        refreshFactoryInput()
    }

    /** Cena operacional; chamado em cadência visual (~20 fps), sem alterar ledger por frame. */
    fun advanceVisual(seconds: Double) {
        refreshFactoryInput()
        factoryFrame = factorySimulation.advance(seconds)

        ownerSimulation.update(factoryMachineInputs())
        ownerFrame = ownerSimulation.advance(seconds)

        if (ownerFrame.activity == OwnerActivity.AWAITING_PAYMENT && cargoInTransitIds.isNotEmpty()) {
            settleCargoDelivery(cargoInTransitIds)
            cargoInTransitIds = emptyList()
            ownerSimulation.paymentRecorded()
            ownerFrame = ownerSimulation.snapshot()
        }

        factoryFrame = factoryFrame.copy(
            owner = ownerFrame,
            cargoInTransit = cargoInTransitIds,
        )
    }

    fun boost10Minutes() {
        if (state.boostTokens <= 0) return notify("Você não possui impulsos de +10 min.")
        if (!WorkLifeRules.factoryOpen(state.shiftMode, currentTimeMillis())) {
            return notify("Fábrica fechada. No turno 12h o expediente é 07:00–19:00.")
        }
        if (production.operatingMachines <= 0) return notify("Nenhuma máquina está produzindo agora.")

        state = state.copy(boostTokens = state.boostTokens - 1)
        simulateSettled(CYCLE_MILLIS, currentTimeMillis(), advanceClock = false, boost = true)
        notify("Impulso aplicado. A produção foi enviada para CARGA.")
        persist()
    }

    fun dailyBonus() {
        val now = currentTimeMillis()
        val day = now / 86_400_000L
        if (state.lastDailyBonusDay == day) return notify("Bônus diário já recebido.")
        val reward = max(150_000L, production.netPer10MinutesCents)
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents + reward),
            boostTokens = state.boostTokens + DAILY_BOOST_TOKENS,
            lastDailyBonusDay = day,
            finances = addFinance(state.finances, "INCOME", "BONUS", reward, "Bônus diário"),
        )
        notify("Bônus diário: ${money(reward)} + $DAILY_BOOST_TOKENS impulsos.")
        persist()
    }

    fun claimDailyGachaTicket() {
        val day = currentTimeMillis() / 86_400_000L
        if (state.expansion.lastDailyTicketDay == day) return notify("Ficha diária da roleta já coletada.")
        state = state.copy(
            expansion = state.expansion.copy(
                gachaTickets = state.expansion.gachaTickets + 1,
                lastDailyTicketDay = day,
            )
        )
        notify("Ficha diária da Roleta Industrial coletada.")
        persist()
    }

    fun settlePrecisionMinigame(score: Double) {
        val now = currentTimeMillis()
        if (now - state.lastMinigameAt < MINIGAME_COOLDOWN_MILLIS) {
            return notify("O minigame ainda está em recarga.")
        }
        val safe = score.coerceIn(0.0, 1.0)
        val baseCycle = max(80_000L, production.netPer10MinutesCents)
        val reward = (baseCycle * (0.30 + safe * 0.70)).toLong()
        val tokens = if (safe >= .82) 2 else 1
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents + reward),
            boostTokens = state.boostTokens + tokens,
            lastMinigameAt = now,
            bestMinigameScore = max(state.bestMinigameScore, safe),
            finances = addFinance(state.finances, "INCOME", "BONUS", reward, "Minigame de precisão"),
        )
        notify("Precisão ${(safe * 100).roundToInt()}% • ${money(reward)} • +$tokens impulso(s).")
        persist()
    }

    fun buySnack() {
        if (state.company.cashCents < TEAM_SNACK_COST_CENTS) {
            return notify("Caixa insuficiente para o cento de salgados.")
        }
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents - TEAM_SNACK_COST_CENTS),
            snackUntil = currentTimeMillis() + SNACK_IMMUNITY_MILLIS,
            workforce = state.workforce.copy(
                idleEmployeeId = null,
                idleSinceAt = 0L,
                idleUntilAt = 0L,
            ),
            finances = addFinance(
                state.finances, "EXPENSE", "SALARY", TEAM_SNACK_COST_CENTS,
                "Cento de salgados • foco da equipe por 8h"
            ),
        )
        notify("Equipe alimentada. Celular/ociosidade bloqueados por 8 horas.")
        persist()
    }

    fun reprimandIdleEmployee() {
        val employee = idleEmployee ?: return notify("Ninguém está no celular agora.")
        val now = currentTimeMillis()
        state = state.copy(
            workforce = state.workforce.copy(
                idleEmployeeId = null,
                idleSinceAt = 0L,
                idleUntilAt = 0L,
                nextIdleCheckAt = now + REPRIMAND_GRACE_MILLIS,
            ),
        )
        notify("${employee.name} voltou ao posto. Nova tolerância por 1 hora.")
        persist()
    }

    fun startCargoDelivery() {
        if (ownerFrame.busy || cargoInTransitIds.isNotEmpty()) return notify("O dono já está em uma entrega.")
        val ids = pendingCargo.map { it.id }
        if (ids.isEmpty()) return notify("Não há carga pronta para expedição.")

        ownerSimulation.update(factoryMachineInputs())
        if (!ownerSimulation.start()) return
        cargoInTransitIds = ids // snapshot: carga criada depois fica para a próxima viagem.
        ownerFrame = ownerSimulation.snapshot()
        notify("Expedição iniciada: ${ids.size} lote(s) nesta viagem.")
    }

    private fun settleCargoDelivery(ids: List<String>) {
        val selected = state.cargo.filter { it.id in ids && it.pending }
        if (selected.isEmpty()) return

        val now = currentTimeMillis()
        val value = selected.sumOf { it.valueCents }
        val cycles = selected.sumOf { it.cycles }
        val selectedIds = selected.map { it.id }.toSet()

        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents + value),
            cargo = state.cargo.map {
                if (it.id in selectedIds && it.pending) it.copy(deliveredAt = now) else it
            },
            finances = addFinance(
                state.finances,
                "INCOME",
                "PRODUCTION",
                value,
                "Entrega de carga • $cycles ciclo(s) de produção",
            ),
        )
        notify("Entrega concluída. ${money(value)} entrou no caixa.")
        persist()
    }

    fun buyMachine(machineType: String) {
        val def = MachineCatalog.byType(machineType) ?: return notify("Máquina desconhecida.")
        if (state.company.cashCents < def.priceCents) return notify("Caixa insuficiente.")
        if (state.company.usedWarehouseSpace + def.space > state.company.warehouseSpace) {
            return notify("Espaço insuficiente no galpão.")
        }
        val spot = freeGridPosition() ?: return notify("Não há posição livre no layout atual.")
        val machine = MachineSave(
            id = newId("machine"),
            machineType = def.type.name,
            condition = 1000,
            gridX = spot.first,
            gridY = spot.second,
        )
        state = state.copy(
            company = state.company.copy(
                cashCents = state.company.cashCents - def.priceCents,
                usedWarehouseSpace = state.company.usedWarehouseSpace + def.space,
            ),
            machines = state.machines + machine,
            finances = addFinance(state.finances, "EXPENSE", "MACHINE", def.priceCents, "Compra: ${def.name}"),
        )
        notify("${def.name} instalada no chão de fábrica.")
        persistAndRefresh()
    }

    fun repairMachine(id: String) {
        val machine = state.machines.firstOrNull { it.id == id } ?: return
        val def = MachineCatalog.byType(machine.machineType) ?: return
        val missing = (1000 - machine.condition).coerceAtLeast(0)
        if (missing == 0) return notify("A máquina já está em perfeito estado.")
        val cost = (def.maintenanceCents * missing / 1000L).coerceAtLeast(5_000L)
        if (state.company.cashCents < cost) return notify("Caixa insuficiente para manutenção.")
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents - cost),
            machines = state.machines.map { if (it.id == id) it.copy(condition = 1000) else it },
            finances = addFinance(state.finances, "EXPENSE", "MAINTENANCE", cost, "Manutenção: ${def.name}"),
        )
        notify("Manutenção concluída por ${money(cost)}.")
        persistAndRefresh()
    }

    fun sellMachine(id: String) {
        val machine = state.machines.firstOrNull { it.id == id } ?: return
        val def = MachineCatalog.byType(machine.machineType) ?: return
        val conditionFactor = .5 + machine.condition.coerceIn(0, 1000) / 2000.0
        val resale = (def.priceCents * .60 * conditionFactor).toLong().coerceAtLeast(1L)
        state = state.copy(
            company = state.company.copy(
                cashCents = state.company.cashCents + resale,
                usedWarehouseSpace = (state.company.usedWarehouseSpace - def.space).coerceAtLeast(0),
            ),
            machines = state.machines.filterNot { it.id == id },
            employees = state.employees.map {
                if (it.assignedMachineId == id) it.copy(assignedMachineId = null) else it
            },
            finances = addFinance(state.finances, "INCOME", "MACHINE", resale, "Revenda: ${def.name}"),
        )
        notify("${def.name} vendida por ${money(resale)}.")
        persistAndRefresh()
    }

    fun moveMachineNext(id: String) {
        val machine = state.machines.firstOrNull { it.id == id } ?: return
        val occupied = state.machines.filterNot { it.id == id }.map { it.gridX to it.gridY }.toSet()
        val all = (0..5).flatMap { y -> (0..4).map { x -> x to y } }
        val currentIndex = all.indexOf(machine.gridX to machine.gridY).coerceAtLeast(0)
        val next = (1..all.size)
            .map { all[(currentIndex + it) % all.size] }
            .firstOrNull { it !in occupied } ?: return
        state = state.copy(
            machines = state.machines.map {
                if (it.id == id) it.copy(gridX = next.first, gridY = next.second) else it
            }
        )
        notify("Máquina movida para a próxima baia livre.")
        persistAndRefresh()
    }

    fun hireEmployee() {
        val random = Random(currentTimeMillis() + idCounter)
        val specialty = EmployeeSpecialty.values().random(random)
        val level = random.nextInt(1, 5)
        val salary = 180_000L + level * 65_000L
        if (state.company.cashCents < salary) return notify("Caixa insuficiente para a contratação.")

        val firstNames = listOf(
            "Carlos", "Marcos", "João", "Rafael", "Bruno", "Diego", "André", "Paulo", "Felipe", "Ricardo",
            "Luciana", "Patrícia", "Camila", "Fernanda", "Amanda", "Juliana", "Mariana", "Beatriz", "Renata",
            "Larissa", "Daniela", "Aline", "Carolina", "Bianca", "Vanessa", "Jéssica", "Natália", "Priscila",
            "Letícia", "Isabela",
        )
        val lastNames = listOf("Silva", "Oliveira", "Santos", "Souza", "Costa", "Ferreira", "Lima", "Rodrigues", "Almeida", "Gomes")
        val traits = listOf("Rápido", "Perfeccionista", "Aprende rápido", "Econômico", "CNC especialista", "Distraído", "Falta muito", "Cuidadoso")

        val employee = EmployeeSave(
            id = newId("employee"),
            name = "${firstNames.random(random)} ${lastNames.random(random)}",
            specialty = specialty.name,
            skillLevel = level,
            salaryCents = salary,
            morale = random.nextInt(65, 96),
            trait = traits.random(random),
        )
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents - salary),
            employees = state.employees + employee,
            finances = addFinance(state.finances, "EXPENSE", "SALARY", salary, "Admissão e primeiro salário: ${employee.name}"),
        )
        notify("${employee.name} contratado.")
        persistAndRefresh()
    }

    fun fireEmployee(id: String) {
        val employee = state.employees.firstOrNull { it.id == id } ?: return
        state = state.copy(
            employees = state.employees.filterNot { it.id == id },
            workforce = if (state.workforce.idleEmployeeId == id) {
                state.workforce.copy(idleEmployeeId = null, idleSinceAt = 0L, idleUntilAt = 0L)
            } else state.workforce,
        )
        notify("${employee.name} desligado da equipe.")
        persistAndRefresh()
    }

    fun assignEmployeeNext(employeeId: String) {
        val employee = state.employees.firstOrNull { it.id == employeeId } ?: return
        if (state.machines.isEmpty()) return notify("Compre uma máquina antes.")
        val current = state.machines.indexOfFirst { it.id == employee.assignedMachineId }
        val candidateOrder = (1..state.machines.size).map {
            state.machines[(current + it).mod(state.machines.size)]
        }
        val target = candidateOrder.firstOrNull { machine ->
            state.employees.none { it.id != employeeId && it.assignedMachineId == machine.id }
        } ?: return notify("Todas as máquinas já possuem operador.")

        state = state.copy(
            employees = state.employees.map {
                if (it.id == employeeId) it.copy(assignedMachineId = target.id) else it
            }
        )
        notify("${employee.name} atribuído a ${machineName(target.machineType)}.")
        persistAndRefresh()
    }

    fun unassignEmployee(employeeId: String) {
        state = state.copy(
            employees = state.employees.map {
                if (it.id == employeeId) it.copy(assignedMachineId = null) else it
            }
        )
        persistAndRefresh()
    }

    fun restEmployee(id: String) {
        val now = currentTimeMillis()
        state = state.copy(
            employees = state.employees.map {
                if (it.id == id) it.copy(restingUntil = now + REST_MILLIS) else it
            },
            workforce = if (state.workforce.idleEmployeeId == id) {
                state.workforce.copy(idleEmployeeId = null, idleSinceAt = 0L, idleUntilAt = 0L)
            } else state.workforce,
        )
        notify("Funcionário enviado para a Copa por 1 hora.")
        persistAndRefresh()
    }

    fun acceptContract(id: String) {
        val contract = state.contracts.firstOrNull { it.id == id } ?: return
        if (contract.status != "AVAILABLE") return
        if (!contractAllowed(contract)) return notify(contractLockReason(contract))
        val now = currentTimeMillis()
        state = state.copy(
            contracts = state.contracts.map {
                if (it.id == id) it.copy(status = "ACTIVE", startedAt = now) else it
            }
        )
        notify("Contrato de ${contract.clientName} aceito.")
        persist()
    }

    fun cancelContract(id: String) {
        val contract = state.contracts.firstOrNull { it.id == id } ?: return
        if (contract.status != "ACTIVE") return
        state = state.copy(
            company = state.company.copy(
                cashCents = (state.company.cashCents - contract.penaltyCents).coerceAtLeast(0L),
                reputation = (state.company.reputation - contract.reputationPenalty).coerceAtLeast(0),
            ),
            contracts = state.contracts.map {
                if (it.id == id) it.copy(status = "CANCELLED") else it
            },
            expansion = state.expansion.copy(
                contractTools = state.expansion.contractTools - id
            ),
            finances = addFinance(
                state.finances, "EXPENSE", "CONTRACT", contract.penaltyCents,
                "Cancelamento: ${contract.clientName}"
            ),
        )
        updateCompanyLevel()
        notify("Contrato cancelado com multa de ${money(contract.penaltyCents)}.")
        persist()
    }

    fun archiveContract(id: String) {
        val status = state.contracts.firstOrNull { it.id == id }?.status ?: return
        if (status !in setOf("COMPLETED", "FAILED", "CANCELLED")) return
        state = state.copy(
            contracts = state.contracts.filterNot { it.id == id },
            expansion = state.expansion.copy(contractTools = state.expansion.contractTools - id),
        )
        ensureContracts()
        persist()
    }

    fun bindTool(contractId: String, toolId: String?) {
        val contract = state.contracts.firstOrNull { it.id == contractId } ?: return
        if (contract.status !in setOf("AVAILABLE", "ACTIVE")) return

        val bindings = state.expansion.contractTools.toMutableMap()
        if (toolId == null) {
            bindings.remove(contractId)
        } else {
            val tool = GameProgression.tools.firstOrNull { it.id == toolId }
                ?: return notify("Ferramenta inválida.")
            val inventory = state.expansion.tools[toolId] ?: 0
            val reservedElsewhere = bindings.count { (cid, tid) -> cid != contractId && tid == toolId }
            if (inventory <= reservedElsewhere) return notify("Todas as unidades de ${tool.name} já estão reservadas.")
            bindings[contractId] = toolId
        }
        state = state.copy(expansion = state.expansion.copy(contractTools = bindings))
        persist()
    }

    fun expandWarehouse() {
        val currentLevel = ((state.company.warehouseSpace - 100) / 50) + 1
        val cost = 2_000_000L * currentLevel
        if (state.company.cashCents < cost) {
            return notify("Faltam ${money(cost - state.company.cashCents)} para ampliar.")
        }
        state = state.copy(
            company = state.company.copy(
                cashCents = state.company.cashCents - cost,
                warehouseSpace = state.company.warehouseSpace + 50,
            ),
            finances = addFinance(state.finances, "EXPENSE", "FACILITY", cost, "Expansão do galpão"),
        )
        notify("Galpão ampliado em +50 m².")
        persist()
    }

    fun claimGoal(id: String) {
        val goal = state.goals.firstOrNull { it.id == id } ?: return
        if (goal.claimed) return notify("Recompensa já coletada.")
        val progress = goalProgress(goal)
        if (progress < goal.target) return notify("Meta ainda não concluída.")
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents + goal.rewardCents),
            goals = state.goals.map { if (it.id == id) it.copy(claimed = true) else it },
            expansion = state.expansion.copy(
                gachaTickets = state.expansion.gachaTickets + goal.ticketReward
            ),
            finances = addFinance(state.finances, "INCOME", "BONUS", goal.rewardCents, "Recompensa de meta: ${goal.title}"),
        )
        notify(
            "Meta resgatada: ${money(goal.rewardCents)}" +
                if (goal.ticketReward > 0) " + ${goal.ticketReward} ficha(s)." else "."
        )
        persist()
    }

    fun goalProgress(goal: GoalSave): Int = when (goal.id) {
        "first_employee" -> if (state.employees.isNotEmpty()) 1 else 0
        "three_machines", "ten_machines", "twenty_machines", "thirty_machines" -> state.machines.size
        "fifteen_employees", "thirty_employees" -> state.employees.size
        "reputation_10", "reputation_100", "reputation_250", "reputation_500" -> state.company.reputation
        "company_level_10", "company_level_20" -> state.company.companyLevel
        "warehouse_150", "warehouse_300", "warehouse_500" -> state.company.warehouseSpace
        else -> 0
    }

    fun setShift(mode: ShiftMode) {
        state = state.copy(shiftMode = mode)
        notify(if (mode == ShiftMode.DAY_12H) "Turno 07:00–19:00 ativado." else "Operação 24 horas ativada.")
        persistAndRefresh()
    }

    fun setSpecialty(code: String) {
        val def = GameProgression.specialties.firstOrNull { it.code == code } ?: return
        if (state.company.companyLevel < def.minLevel) return notify("Nível ${def.minLevel} necessário.")
        state = state.copy(expansion = state.expansion.copy(specialty = code))
        notify("Especialização: ${def.label}.")
        persistAndRefresh()
    }

    fun unlockCompanySkill(id: String) {
        val def = GameProgression.companySkills.firstOrNull { it.id == id } ?: return
        if (id in state.expansion.companySkills) return
        if (GameProgression.companySkillPoints(state.company.companyLevel, state.expansion.companySkills) <= 0) {
            return notify("Sem pontos de skill da empresa.")
        }
        if (!GameProgression.canUnlock(def, state.company.companyLevel, state.expansion.companySkills)) {
            return notify("Requisitos da skill não atendidos.")
        }
        state = state.copy(expansion = state.expansion.copy(companySkills = state.expansion.companySkills + id))
        notify("${def.name} desbloqueada.")
        persistAndRefresh()
    }

    fun unlockPlayerSkill(id: String) {
        val def = GameProgression.playerSkills.firstOrNull { it.id == id } ?: return
        if (id in state.expansion.playerSkills) return
        if (GameProgression.playerSkillPoints(state.company.companyLevel, state.expansion.playerSkills) <= 0) {
            return notify("Sem pontos do personagem.")
        }
        if (!GameProgression.canUnlock(def, state.company.companyLevel, state.expansion.playerSkills)) {
            return notify("Requisitos da skill não atendidos.")
        }
        state = state.copy(expansion = state.expansion.copy(playerSkills = state.expansion.playerSkills + id))
        notify("${def.name} desbloqueada.")
        persistAndRefresh()
    }

    fun buyPremiumMachine(id: String) {
        val def = GameProgression.premiumMachines.firstOrNull { it.id == id }
            ?: return notify("Máquina premium inválida.")
        if (state.company.companyLevel < def.minLevel) return notify("Nível ${def.minLevel} necessário.")
        if (id in state.expansion.premiumMachines) return notify("Máquina premium já adquirida.")
        if (state.company.cashCents < def.priceCents) return notify("Caixa insuficiente.")

        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents - def.priceCents),
            expansion = state.expansion.copy(premiumMachines = state.expansion.premiumMachines + id),
            finances = addFinance(state.finances, "EXPENSE", "MACHINE", def.priceCents, "Máquina premium: ${def.name}"),
        )
        notify("${def.name} adicionada à célula premium.")
        persistAndRefresh()
    }

    fun spinGacha() {
        if (state.expansion.gachaTickets <= 0) return notify("Você não tem fichas da roleta.")
        val random = Random(currentTimeMillis() + idCounter)
        val epicPity = state.expansion.pityEpic + 1
        val legendaryPity = state.expansion.pityLegendary + 1
        val forcedLegendary = legendaryPity >= 80
        val forcedEpic = epicPity >= 30
        val roll = random.nextInt(10_000)

        val reward = when {
            forcedLegendary -> gachaByMinimumRarity(RarityDef.LEGENDARY, random)
            forcedEpic -> gachaByMinimumRarity(RarityDef.EPIC, random)
            roll < 80 -> gachaByMinimumRarity(RarityDef.LEGENDARY, random) // 0,8%
            roll < 450 -> gachaByMinimumRarity(RarityDef.EPIC, random)     // +3,7%
            roll < 1_050 -> randomPremiumMachine(random)                    // +6,0%
            roll < 2_250 -> randomCharacter(random)                         // +12%
            roll < 4_050 -> randomSkin(random)                              // +18%
            roll < 6_450 -> randomTool(RarityDef.RARE, random)              // +24%
            else -> randomTool(RarityDef.COMMON, random)                    // restante; ficha nunca é prêmio
        }

        var expansion = applyGachaReward(state.expansion, reward)
        expansion = expansion.copy(
            gachaTickets = (expansion.gachaTickets - 1).coerceAtLeast(0),
            pityLegendary = if (reward.rarity == RarityDef.LEGENDARY) 0 else legendaryPity,
            pityEpic = if (reward.rarity.rank >= RarityDef.EPIC.rank) 0 else epicPity,
        )
        state = state.copy(expansion = expansion)
        lastGachaReward = reward
        notify("Roleta • ${reward.rarity.label}: ${reward.title}")
        persistAndRefresh()
    }

    fun equipSkin(id: String) {
        if (id !in state.expansion.ownedSkins) return notify("Skin ainda não obtida.")
        val visualStyle = when (id) {
            "princesa", "princesa_dourada" -> "PRINCESA"
            "pinoquio" -> "PINOQUIO"
            "tatuzao" -> "TATUZAO"
            "magrao" -> "MAGRAO"
            "kendao" -> "KENDAO_KIMONO"
            else -> state.profile.skinStyle
        }
        state = state.copy(
            expansion = state.expansion.copy(equippedSkin = id),
            profile = state.profile.copy(skinStyle = visualStyle),
        )
        persistAndRefresh()
    }

    fun equipCharacter(id: String?) {
        if (id != null && id !in state.expansion.ownedCharacters) return notify("Personagem ainda não obtido.")
        state = state.copy(expansion = state.expansion.copy(equippedCharacter = id))
        persistAndRefresh()
    }

    fun updateProfile(profile: PlayerProfileSave) {
        state = state.copy(profile = profile.copy(onboardingComplete = true))
        persistAndRefresh()
    }

    fun updateUiSettings(settings: UiSettingsSave) {
        state = state.copy(uiSettings = settings.copy(legendarySpeechSeconds = settings.legendarySpeechSeconds.coerceIn(2, 12)))
        persist()
    }

    fun resetSave() {
        PlatformSaveStorage.remove(SAVE_KEY)
        state = createInitial()
        cargoInTransitIds = emptyList()
        ownerSimulation.cancel()
        ownerFrame = ownerSimulation.snapshot()
        factoryFrame = FactoryFrame()
        normalizeAndPersist()
        ensureContracts()
        refreshFactoryInput()
        notify("Novo jogo iniciado.")
    }

    private fun simulateOffline() {
        val now = currentTimeMillis()
        val elapsed = (now - state.company.lastSimulationAt).coerceAtLeast(0L)
        if (elapsed < CYCLE_MILLIS) return
        val capped = min(elapsed, MAX_OFFLINE_MILLIS)
        val settled = (capped / CYCLE_MILLIS) * CYCLE_MILLIS
        if (settled >= CYCLE_MILLIS) {
            simulateSettled(settled, now, advanceClock = true)
            if (pendingCargoCents > 0) {
                notify("Produção offline fechada. CARGA pronta: ${money(pendingCargoCents)}.")
            }
        }
    }

    private fun simulateSettled(
        elapsedMillis: Long,
        eventTime: Long,
        advanceClock: Boolean,
        boost: Boolean = false,
    ) {
        var working = state
        val cycles = (elapsedMillis / CYCLE_MILLIS).coerceAtLeast(1L)
        var cursor = if (advanceClock) working.company.lastSimulationAt else eventTime - CYCLE_MILLIS

        repeat(cycles.toInt().coerceAtMost(144)) { index ->
            val cycleEnd = cursor + CYCLE_MILLIS
            val open = WorkLifeRules.factoryOpen(working.shiftMode, cycleEnd)
            working = if (!open) {
                working.copy(
                    employees = working.employees.map {
                        WorkLifeRules.afterClosedShift(it, 10)
                    },
                    contracts = working.contracts.map {
                        if (it.status == "ACTIVE") it.copy(deadlineAt = it.deadlineAt + CYCLE_MILLIS) else it
                    },
                )
            } else {
                simulateOpenCycle(working, cycleEnd, boost && index == 0)
            }
            cursor = cycleEnd
        }

        if (advanceClock) {
            val target = (state.company.lastSimulationAt + cycles * CYCLE_MILLIS).coerceAtMost(eventTime)
            working = working.copy(company = working.company.copy(lastSimulationAt = target))
        }
        state = working
        updateCompanyLevel()
        normalizeUnlockedSkins()
        persist()
    }

    private fun simulateOpenCycle(input: GameSave, eventTime: Long, boost: Boolean): GameSave {
        var working = input
        val snapshot = calculateProduction(eventTime, working)
        val producedUnits = snapshot.totalUnitsPerHour / 6.0
        val passiveNet = EconomyBalance.boostedProfit((snapshot.netPerHourCents / 6L).coerceAtLeast(0L))
        var productionMilli = (producedUnits * 1000.0).toLong().coerceAtLeast(0L)
        var company = working.company
        var contracts = working.contracts.toMutableList()
        var finances = working.finances
        var expansion = working.expansion

        if (productionMilli > 0L) {
            for (i in contracts.indices) {
                val contract = contracts[i]
                if (contract.status != "ACTIVE" || productionMilli <= 0L) continue

                val toolEffect = GameProgression.toolEffect(expansion, contract.id)
                val effectiveQuality = snapshot.averageQuality + toolEffect.qualityBonus
                val qualityGap = contract.requiredQuality - effectiveQuality
                val qualityFactor = when {
                    qualityGap <= 0 -> 1.0
                    qualityGap <= 10 -> .70
                    else -> .30
                }
                val effectiveFactor = qualityFactor * toolEffect.speedMultiplier

                val targetMilli = contract.quantity * 1000L
                val current = contract.productionProgressMilli.coerceAtMost(targetMilli)
                val needed = (targetMilli - current).coerceAtLeast(0L)
                val acceptedAvailable = (productionMilli * effectiveFactor).toLong().coerceAtLeast(0L)
                val applied = min(acceptedAvailable, needed)
                val rawConsumed = if (effectiveFactor <= 0.0) {
                    productionMilli
                } else {
                    ceil(applied / effectiveFactor).toLong()
                }
                val next = current + applied
                productionMilli = (productionMilli - rawConsumed).coerceAtLeast(0L)

                if (next >= targetMilli) {
                    val alreadyPaid = contract.rewardPaid
                    contracts[i] = contract.copy(
                        completedQuantity = contract.quantity,
                        productionProgressMilli = targetMilli,
                        status = "COMPLETED",
                        rewardPaid = true,
                    )
                    if (!alreadyPaid) {
                        company = company.copy(
                            cashCents = company.cashCents + contract.rewardCents,
                            reputation = company.reputation + contract.reputationReward,
                        )
                        expansion = expansion.copy(
                            playerXp = expansion.playerXp + contract.difficulty * 120L + contract.requiredQuality
                        )
                        finances = addFinance(
                            finances, "INCOME", "CONTRACT", contract.rewardCents,
                            "Contrato concluído: ${contract.clientName}", eventTime
                        )
                        expansion = consumeBoundTool(expansion, contract.id)
                    }
                } else {
                    contracts[i] = contract.copy(
                        completedQuantity = (next / 1000L).toInt(),
                        productionProgressMilli = next,
                    )
                }
            }
        }

        val operatingIds = snapshot.machineProduction.filter { it.isOperating }.map { it.machineId }.toSet()
        val machines = working.machines.map { machine ->
            if (machine.id !in operatingIds) machine else {
                val newMinutes = machine.accumulatedWorkMinutes + 10L
                val wearBefore = machine.accumulatedWorkMinutes / 20L
                val wearAfter = newMinutes / 20L
                machine.copy(
                    accumulatedWorkMinutes = newMinutes,
                    condition = (machine.condition - (wearAfter - wearBefore).toInt()).coerceAtLeast(0),
                )
            }
        }

        val employees = working.employees.map { employee ->
            when {
                WorkLifeRules.resting(employee, eventTime) -> WorkLifeRules.afterRest(employee, 10)
                employee.assignedMachineId in operatingIds -> {
                    val newExperience = employee.experience + 10L
                    val skill = (1 + (newExperience / 480L).toInt()).coerceIn(employee.skillLevel, 10)
                    WorkLifeRules.afterWorked(
                        employee.copy(experience = newExperience, skillLevel = skill),
                        10, working.shiftMode
                    )
                }
                else -> WorkLifeRules.advanceFatigue(
                    employee, assigned = false,
                    continuous = working.shiftMode == ShiftMode.CONTINUOUS_24H,
                    workHours = 10.0 / 60.0, pausedHours = 0.0, restHours = 0.0,
                )
            }
        }

        var penalty = 0L
        var reputationLoss = 0
        contracts = contracts.map { contract ->
            if (contract.status == "ACTIVE" && eventTime > contract.deadlineAt) {
                penalty += contract.penaltyCents
                reputationLoss += contract.reputationPenalty
                finances = addFinance(
                    finances, "EXPENSE", "CONTRACT", contract.penaltyCents,
                    "Multa por atraso: ${contract.clientName}", eventTime
                )
                expansion = expansion.copy(contractTools = expansion.contractTools - contract.id)
                contract.copy(status = "FAILED")
            } else contract
        }.toMutableList()

        company = company.copy(
            cashCents = (company.cashCents - penalty).coerceAtLeast(0L),
            reputation = (company.reputation - reputationLoss).coerceAtLeast(0),
        )

        val cargo = if (producedUnits > 0.0 || passiveNet > 0L) {
            working.cargo + ProductionCargoSave(
                id = if (boost) newId("boost") else newId("cycle"),
                valueCents = passiveNet,
                unitsMilli = (producedUnits * 1000.0).toLong().coerceAtLeast(0L),
                cycles = 1L,
                createdAt = eventTime,
            )
        } else working.cargo

        return working.copy(
            company = company,
            machines = machines,
            employees = employees,
            contracts = contracts,
            cargo = cargo.takeLast(300),
            finances = finances,
            expansion = expansion,
        )
    }

    private fun calculateProduction(now: Long, save: GameSave = state): ProductionSnapshot {
        if (!WorkLifeRules.factoryOpen(save.shiftMode, now)) {
            return ProductionSnapshot(idleMachines = save.machines.count { it.installed })
        }

        val machineRuntime = save.machines.filter { it.installed }.map {
            MachineRuntime(it.id, it.machineType, it.level, it.condition)
        }
        val employeeRuntime = save.employees
            .filterNot { WorkLifeRules.resting(it, now) }
            .map {
                EmployeeRuntime(
                    id = it.id,
                    specialty = it.specialty,
                    skillLevel = it.skillLevel,
                    morale = it.morale,
                    trait = it.trait,
                    assignedMachineId = it.assignedMachineId,
                    legendaryCode = it.legendaryCode,
                )
            }

        val idleIds = if (save.snackUntil > now) {
            emptySet()
        } else {
            save.workforce.idleEmployeeId?.takeIf { save.workforce.idleUntilAt > now }?.let(::setOf)
                ?: emptySet()
        }

        val base = ProductionEngine.calculate(
            machines = machineRuntime,
            employees = employeeRuntime,
            idleEmployeeIds = idleIds,
            modifiers = GameProgression.modifiers(save.expansion),
        )

        val adjustedMachines = base.machineProduction.map { mp ->
            val employee = save.employees.firstOrNull { it.id == mp.employeeId }
            val efficiency = employee?.let { WorkLifeRules.efficiency(it, now) } ?: 1.0
            mp.copy(unitsPerHour = mp.unitsPerHour * efficiency)
        }
        val adjustedUnits = adjustedMachines.filter { it.isOperating }.sumOf { it.unitsPerHour }
        val ratio = if (base.totalUnitsPerHour > 0.0) {
            (adjustedUnits / base.totalUnitsPerHour).coerceIn(0.0, 1.0)
        } else 0.0
        val gross = (base.grossPerHourCents * ratio).toLong()
        return base.copy(
            totalUnitsPerHour = adjustedUnits,
            grossPerHourCents = gross,
            netPerHourCents = (gross - base.energyPerHourCents).coerceAtLeast(0L),
            machineProduction = adjustedMachines,
        )
    }

    private fun refreshFactoryInput() {
        factorySimulation.update(
            FactoryInput(
                machines = factoryMachineInputs(),
                workers = factoryWorkerInputs(),
                open = WorkLifeRules.factoryOpen(state.shiftMode, currentTimeMillis()),
                cycleStartedAt = state.company.lastSimulationAt,
            )
        )
        ownerSimulation.update(factoryMachineInputs())
        factoryFrame = factorySimulation.snapshot().copy(
            owner = ownerSimulation.snapshot(),
            cargoInTransit = cargoInTransitIds,
        )
        ownerFrame = ownerSimulation.snapshot()
    }

    private fun factoryMachineInputs(): List<FactoryMachineInput> {
        val p = calculateProduction(currentTimeMillis())
        return state.machines.map { machine ->
            val mp = p.machineProduction.firstOrNull { it.machineId == machine.id }
            FactoryMachineInput(
                id = machine.id,
                gridX = machine.gridX,
                gridY = machine.gridY,
                installed = machine.installed,
                condition = machine.condition,
                productive = mp?.isOperating == true,
                unitsPerHour = mp?.unitsPerHour ?: 0.0,
            )
        }
    }

    private fun factoryWorkerInputs(): List<FactoryWorkerInput> {
        val now = currentTimeMillis()
        return state.employees.map { employee ->
            FactoryWorkerInput(
                id = employee.id,
                machineId = employee.assignedMachineId,
                skill = employee.skillLevel,
                fatigue = employee.fatigue.roundToInt(),
                resting = WorkLifeRules.resting(employee, now),
                onPhone = state.snackUntil <= now &&
                    state.workforce.idleEmployeeId == employee.id &&
                    state.workforce.idleUntilAt > now,
            )
        }
    }

    private fun updateIdleDiscipline(now: Long) {
        var workforce = state.workforce

        if (state.snackUntil > now) {
            if (workforce.idleEmployeeId != null) {
                workforce = workforce.copy(idleEmployeeId = null, idleSinceAt = 0L, idleUntilAt = 0L)
                state = state.copy(workforce = workforce)
                persist()
            }
            return
        }

        if (workforce.idleEmployeeId != null && now >= workforce.idleUntilAt) {
            workforce = workforce.copy(
                idleEmployeeId = null,
                idleSinceAt = 0L,
                idleUntilAt = 0L,
                nextIdleCheckAt = randomIdleCheckAt(now),
            )
            state = state.copy(workforce = workforce)
            persist()
            return
        }

        if (workforce.idleEmployeeId != null) return

        if (workforce.nextIdleCheckAt <= 0L) {
            state = state.copy(workforce = workforce.copy(nextIdleCheckAt = randomIdleCheckAt(now)))
            persist()
            return
        }

        if (now < workforce.nextIdleCheckAt) return

        val random = Random(now + idCounter)
        val candidates = state.employees.filter {
            it.assignedMachineId != null && !WorkLifeRules.resting(it, now)
        }
        val nextCheck = randomIdleCheckAt(now)

        if (candidates.isEmpty() || random.nextInt(100) >= IDLE_EVENT_CHANCE_PERCENT) {
            state = state.copy(workforce = workforce.copy(nextIdleCheckAt = nextCheck))
            persist()
            return
        }

        val weighted = candidates.flatMap { employee ->
            val weight = when {
                employee.trait == "Distraído" -> 4
                employee.trait == "Falta muito" -> 3
                employee.morale < 55 -> 3
                employee.morale < 75 -> 2
                employee.trait == "Perfeccionista" || employee.trait == "Cuidadoso" -> 1
                else -> 1
            }
            List(weight) { employee }
        }
        val chosen = weighted.random(random)
        val duration = random.nextLong(2L * 60L * 1000L, EMPLOYEE_IDLE_MAX_MILLIS + 1L)
        state = state.copy(
            workforce = WorkforceSave(
                idleEmployeeId = chosen.id,
                idleSinceAt = now,
                idleUntilAt = now + duration,
                nextIdleCheckAt = nextCheck,
            )
        )
        notify("${chosen.name} pegou o celular no meio do expediente.")
        persist()
    }

    private fun randomIdleCheckAt(now: Long): Long {
        val random = Random(now + idCounter++)
        return now + random.nextLong(IDLE_CHECK_MIN_MILLIS, IDLE_CHECK_MAX_MILLIS + 1L)
    }

    private fun ensureContracts() {
        val level = state.company.companyLevel
        val target = when {
            level >= 10 -> 9
            level >= 7 -> 8
            level >= 4 -> 7
            level >= 2 -> 6
            else -> 5
        }
        val available = state.contracts.count { it.status == "AVAILABLE" && contractAllowed(it) }
        val missing = (target - available).coerceAtLeast(0)
        if (missing == 0) return
        val next = state.contracts.toMutableList()
        repeat(missing) { next += generateContract(forceHigh = it < 2) }
        state = state.copy(contracts = next)
        persist()
    }

    private fun generateContract(forceHigh: Boolean): ContractSave {
        val now = currentTimeMillis()
        val random = Random(now + idCounter++)
        val clients = listOf(
            "Metalúrgica Horizonte", "AutoPeças Brasil", "AgroMec", "Hidráulica Forte",
            "AçoSul", "TecnoBombas", "AeroMec", "MinasTech", "Ferrovia Sul", "Precision Parts",
        )
        val normalTypes = listOf(
            "Peça unitária", "Lote pequeno", "Lote médio", "Retrabalho",
            "Eixo e flange", "Dispositivo industrial",
        )
        val specialTypes = listOf(
            "Protótipo crítico", "Lote urgente", "Peça aeroespacial",
            "Recuperação de emergência", "Tolerância extrema",
        )

        val allowed = (1..5).filter { difficultyAllowed(it) }
        val difficulty = if (forceHigh) allowed.maxOrNull() ?: 1 else allowed.random(random)
        val specialChance = (5 + state.company.companyLevel * 2).coerceAtMost(25)
        val special = random.nextInt(100) < specialChance
        val qtyUpper = (18 + state.company.companyLevel * 2).coerceAtMost(70)
        val qty = random.nextInt(5, max(6, qtyUpper + 1)) * difficulty
        val requiredQuality = (43 + difficulty * 9 + if (special) 7 else 0).coerceAtMost(98)
        val reward = (
            320_000L +
                difficulty * 280_000L +
                qty * 13_000L +
                if (special) 650_000L + difficulty * 180_000L else 0L
            )
        val hours = if (special) 5L + difficulty * 2L else 8L + difficulty * 3L
        return ContractSave(
            id = newId("contract"),
            clientName = clients.random(random),
            type = (if (special) specialTypes else normalTypes).random(random),
            quantity = qty,
            difficulty = difficulty,
            requiredQuality = requiredQuality,
            rewardCents = reward,
            penaltyCents = reward / if (special) 3L else 4L,
            reputationReward = difficulty * 2 + if (special) difficulty else 0,
            reputationPenalty = difficulty + if (special) 1 else 0,
            generatedAt = now,
            deadlineAt = now + hours * 60L * 60L * 1000L,
            special = special,
        )
    }

    private fun difficultyAllowed(difficulty: Int): Boolean {
        val (minLevel, minSkills, specialtyRequired) = GameProgression.contractGate(difficulty)
        val commercialBonus = if ("comercial" in state.expansion.companySkills) 1 else 0
        return state.company.companyLevel >= minLevel &&
            state.expansion.companySkills.size + commercialBonus >= minSkills &&
            (!specialtyRequired || state.expansion.specialty != "generalista")
    }

    private fun contractAllowed(contract: ContractSave): Boolean = difficultyAllowed(contract.difficulty)

    fun contractLockReason(contract: ContractSave): String {
        val (minLevel, minSkills, specialtyRequired) = GameProgression.contractGate(contract.difficulty)
        if (state.company.companyLevel < minLevel) return "Exige nível $minLevel da empresa."
        val commercialBonus = if ("comercial" in state.expansion.companySkills) 1 else 0
        if (state.expansion.companySkills.size + commercialBonus < minSkills) {
            return "Exige $minSkills skill(s) da empresa."
        }
        if (specialtyRequired && state.expansion.specialty == "generalista") {
            return "Defina uma especialidade para contratos nível máximo."
        }
        return "Disponível"
    }

    private fun consumeBoundTool(expansion: ExpansionSave, contractId: String): ExpansionSave {
        val toolId = expansion.contractTools[contractId] ?: return expansion
        val counts = expansion.tools.toMutableMap()
        val next = ((counts[toolId] ?: 0) - 1).coerceAtLeast(0)
        if (next == 0) counts.remove(toolId) else counts[toolId] = next
        return expansion.copy(
            tools = counts,
            contractTools = expansion.contractTools - contractId,
        )
    }

    private fun randomSkin(random: Random): GachaRewardDef {
        val pool = GameProgression.skins.filter {
            it.gachaOnly &&
                it.minLevel <= state.company.companyLevel + 5 &&
                it.id !in state.expansion.ownedSkins
        }
        if (pool.isEmpty()) return randomTool(RarityDef.RARE, random)
        val def = pool.random(random)
        return GachaRewardDef("skin", def.id, def.name, def.rarity)
    }

    private fun randomCharacter(random: Random): GachaRewardDef {
        val pool = GameProgression.characters.filter {
            it.minLevel <= state.company.companyLevel + 5 &&
                it.id !in state.expansion.ownedCharacters
        }
        if (pool.isEmpty()) return randomTool(RarityDef.RARE, random)
        val def = pool.random(random)
        return GachaRewardDef("character", def.id, def.name, def.rarity)
    }

    private fun randomPremiumMachine(random: Random): GachaRewardDef {
        val pool = GameProgression.premiumMachines.filter {
            it.minLevel <= state.company.companyLevel + 6 &&
                it.id !in state.expansion.premiumMachines
        }
        if (pool.isEmpty()) return randomTool(RarityDef.EPIC, random)
        val def = pool.random(random)
        return GachaRewardDef("premium_machine", def.id, def.name, def.rarity)
    }

    private fun randomTool(minRarity: RarityDef, random: Random): GachaRewardDef {
        val pool = GameProgression.tools.filter { it.rarity.rank >= minRarity.rank }
            .ifEmpty { GameProgression.tools }
        val def = pool.random(random)
        return GachaRewardDef("tool", def.id, def.name, def.rarity)
    }

    private fun gachaByMinimumRarity(minRarity: RarityDef, random: Random): GachaRewardDef {
        val candidates = buildList<GachaRewardDef> {
            GameProgression.skins.filter {
                it.gachaOnly && it.rarity.rank >= minRarity.rank &&
                    it.minLevel <= state.company.companyLevel + 6 &&
                    it.id !in state.expansion.ownedSkins
            }.forEach { add(GachaRewardDef("skin", it.id, it.name, it.rarity)) }

            GameProgression.characters.filter {
                it.rarity.rank >= minRarity.rank &&
                    it.minLevel <= state.company.companyLevel + 6 &&
                    it.id !in state.expansion.ownedCharacters
            }.forEach { add(GachaRewardDef("character", it.id, it.name, it.rarity)) }

            GameProgression.premiumMachines.filter {
                it.rarity.rank >= minRarity.rank &&
                    it.minLevel <= state.company.companyLevel + 6 &&
                    it.id !in state.expansion.premiumMachines
            }.forEach { add(GachaRewardDef("premium_machine", it.id, it.name, it.rarity)) }

            GameProgression.tools.filter { it.rarity.rank >= minRarity.rank }.forEach {
                add(GachaRewardDef("tool", it.id, it.name, it.rarity))
            }
        }
        return candidates.randomOrNull(random) ?: randomTool(minRarity, random)
    }

    private fun applyGachaReward(expansion: ExpansionSave, reward: GachaRewardDef): ExpansionSave =
        when (reward.type) {
            "skin" -> expansion.copy(ownedSkins = expansion.ownedSkins + reward.id)
            "character" -> expansion.copy(ownedCharacters = expansion.ownedCharacters + reward.id)
            "premium_machine" -> expansion.copy(premiumMachines = expansion.premiumMachines + reward.id)
            "tool" -> expansion.copy(
                tools = expansion.tools + (reward.id to ((expansion.tools[reward.id] ?: 0) + 1))
            )
            else -> expansion
        }

    private fun updateCompanyLevel() {
        val computed = (1 + state.company.reputation / 20).coerceAtLeast(state.company.companyLevel)
        if (computed != state.company.companyLevel) {
            state = state.copy(company = state.company.copy(companyLevel = computed))
        }
    }

    private fun normalizeUnlockedSkins() {
        val unlocked = GameProgression.unlockedLevelSkins(state.company.companyLevel)
        val owned = state.expansion.ownedSkins + unlocked + "operador_padrao"
        if (owned != state.expansion.ownedSkins) {
            state = state.copy(expansion = state.expansion.copy(ownedSkins = owned))
        }
    }

    private fun normalizeAndPersist() {
        var save = state
        if (save.company.lastSimulationAt <= 0L) {
            save = save.copy(company = save.company.copy(lastSimulationAt = currentTimeMillis()))
        }
        if (save.goals.isEmpty() || save.goals.none { it.id == "ten_machines" }) {
            val claimed = save.goals.filter { it.claimed }.map { it.id }.toSet()
            save = save.copy(goals = defaultGoals().map { if (it.id in claimed) it.copy(claimed = true) else it })
        }
        if (save.workforce.nextIdleCheckAt <= 0L) {
            save = save.copy(workforce = save.workforce.copy(nextIdleCheckAt = randomIdleCheckAt(currentTimeMillis())))
        }
        state = save
        updateCompanyLevel()
        normalizeUnlockedSkins()
        persist()
    }

    private fun loadOrCreate(): GameSave {
        val raw = PlatformSaveStorage.read(SAVE_KEY)
        return raw?.let(GameSaveCodec::decode) ?: createInitial()
    }

    private fun createInitial(): GameSave {
        val now = currentTimeMillis()
        val starterDef = MachineCatalog.byType(MachineType.MECHANICAL_LATHE.name)
        val machine = MachineSave(
            id = "starter_lathe",
            machineType = MachineType.MECHANICAL_LATHE.name,
            level = 1,
            condition = 850,
            gridX = 0,
            gridY = 0,
        )
        return GameSave(
            company = CompanySave(
                name = "Oficina Império do Aço",
                cashCents = 3_500_000L,
                reputation = 0,
                companyLevel = 1,
                warehouseSpace = 100,
                usedWarehouseSpace = starterDef?.space ?: 0,
                lastSimulationAt = now,
            ),
            machines = listOf(machine),
            goals = defaultGoals(),
            workforce = WorkforceSave(nextIdleCheckAt = now + IDLE_CHECK_MIN_MILLIS),
        )
    }

    private fun defaultGoals() = listOf(
        GoalSave("first_employee", "Contrate seu primeiro funcionário", 1, 250_000L),
        GoalSave("three_machines", "Tenha 3 máquinas instaladas", 3, 500_000L),
        GoalSave("reputation_10", "Alcance 10 de reputação", 10, 750_000L),
        GoalSave("warehouse_150", "Expanda o galpão para 150 m²", 150, 400_000L),
        GoalSave("ten_machines", "Complexo industrial • 10 máquinas", 10, 25_000_000L),
        GoalSave("twenty_machines", "Parque fabril gigante • 20 máquinas", 20, 75_000_000L),
        GoalSave("thirty_machines", "IMPÉRIO INDUSTRIAL • 30 máquinas", 30, 200_000_000L, 10),
        GoalSave("fifteen_employees", "Equipe de elite • 15 funcionários", 15, 50_000_000L),
        GoalSave("thirty_employees", "Mega operação • 30 funcionários", 30, 180_000_000L, 8),
        GoalSave("reputation_100", "Referência regional • 100 reputação", 100, 80_000_000L),
        GoalSave("reputation_250", "Lenda da indústria • 250 reputação", 250, 250_000_000L, 10),
        GoalSave("reputation_500", "Nome mundial • 500 reputação", 500, 600_000_000L, 20),
        GoalSave("company_level_10", "Empresa nível 10", 10, 100_000_000L),
        GoalSave("company_level_20", "Empresa nível 20", 20, 500_000_000L, 15),
        GoalSave("warehouse_300", "Galpão de 300 m²", 300, 120_000_000L),
        GoalSave("warehouse_500", "Mega galpão de 500 m²", 500, 300_000_000L, 10),
    )

    private fun freeGridPosition(): Pair<Int, Int>? {
        val occupied = state.machines.map { it.gridX to it.gridY }.toSet()
        for (y in 0..5) for (x in 0..4) {
            if ((x to y) !in occupied) return x to y
        }
        return null
    }

    private fun persistAndRefresh() {
        persist()
        refreshFactoryInput()
    }

    private fun persist() {
        state = state.copy(schemaVersion = 3)
        PlatformSaveStorage.write(SAVE_KEY, GameSaveCodec.encode(state))
    }

    private fun addFinance(
        list: List<FinanceSave>,
        type: String,
        category: String,
        amount: Long,
        description: String,
        at: Long = currentTimeMillis(),
    ): List<FinanceSave> = (
        list + FinanceSave(
            id = newId("finance"),
            type = type,
            category = category,
            amountCents = amount,
            description = description,
            createdAt = at,
        )
        ).takeLast(300)

    private fun newId(prefix: String): String =
        "${prefix}_${currentTimeMillis()}_${idCounter++}"

    private fun machineName(type: String): String =
        MachineCatalog.byType(type)?.name ?: type

    private fun notify(text: String) {
        message = text
    }

    companion object {
        fun money(cents: Long): String {
            val sign = if (cents < 0L) "-" else ""
            val safe = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(cents)
            return "${sign}R$ ${safe / 100L},${(safe % 100L).toString().padStart(2, '0')}"
        }

        fun percent(value: Double): String = "${(value.coerceIn(0.0, 1.0) * 100.0).roundToInt()}%"
    }
}
