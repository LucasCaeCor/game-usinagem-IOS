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
private const val REST_MILLIS = 2L * 60L * 60L * 1000L

private const val DAILY_BOOST_TOKENS = 2
private const val MINIGAME_COOLDOWN_MILLIS = 15L * 60L * 1000L
private const val TEAM_SNACK_COST_CENTS = 25_000L
private const val SNACK_IMMUNITY_MILLIS = 8L * 60L * 60L * 1000L
private const val EMPLOYEE_IDLE_MAX_MILLIS = 7L * 60L * 1000L
private const val IDLE_CHECK_MIN_MILLIS = 2L * 60L * 1000L
private const val IDLE_CHECK_MAX_MILLIS = 5L * 60L * 1000L
private const val REPRIMAND_GRACE_MILLIS = 60L * 60L * 1000L
private const val IDLE_EVENT_CHANCE_PERCENT = 30
private const val DAY_MILLIS = 86_400_000L
private const val PAYROLL_MONTH_MILLIS = 30L * DAY_MILLIS

private const val VISUAL_EXPERIENCE_V27 = "visual_experience_v27"

private const val V28_SYSTEMS = "v28_factory_systems_social"

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
        ensureDailyMissions()
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

    val factoryExpansionStage: Int
        get() = ((state.company.warehouseSpace - 100) / 50).coerceAtLeast(0)

    val factoryGridColumns: Int
        get() = (5 + factoryExpansionStage / 2).coerceIn(5, 10)

    val factoryGridRows: Int
        get() = (6 + (factoryExpansionStage + 1) / 2).coerceIn(6, 12)

    val factoryBayCapacity: Int
        get() = factoryGridColumns * factoryGridRows

    val snackActive: Boolean
        get() = state.snackUntil > currentTimeMillis()

    val idleEmployee: EmployeeSave?
        get() = state.workforce.idleEmployeeId?.let { id -> state.employees.firstOrNull { it.id == id } }

    val minigameAvailable: Boolean
        get() = currentTimeMillis() - state.lastMinigameAt >= MINIGAME_COOLDOWN_MILLIS

    val minigameRemainingMillis: Long
        get() = (MINIGAME_COOLDOWN_MILLIS - (currentTimeMillis() - state.lastMinigameAt)).coerceAtLeast(0L)


    val focusModeRemainingMillis: Long
        get() = (state.snackUntil - currentTimeMillis()).coerceAtLeast(0L)

    val dailyBonusRemainingMillis: Long
        get() {
            val now = currentTimeMillis()
            val today = now / DAY_MILLIS
            return if (state.lastDailyBonusDay < today) 0L else ((today + 1L) * DAY_MILLIS - now).coerceAtLeast(0L)
        }

    val dailyTicketRemainingMillis: Long
        get() {
            val now = currentTimeMillis()
            val today = now / DAY_MILLIS
            return if (state.expansion.lastDailyTicketDay < today) 0L else ((today + 1L) * DAY_MILLIS - now).coerceAtLeast(0L)
        }


    val monthlyPayrollCents: Long
        get() = state.employees.sumOf { it.salaryCents }

    val monthlyPayrollRemainingMillis: Long
        get() {
            val now = currentTimeMillis()
            val cycle = now / PAYROLL_MONTH_MILLIS
            val last = if (state.lastPayrollCycle < 0L) cycle else state.lastPayrollCycle
            return (((last + 1L) * PAYROLL_MONTH_MILLIS) - now).coerceAtLeast(0L)
        }

    fun monthlyPayrollRemainingMillisAt(now: Long): Long {
        val cycle = now / PAYROLL_MONTH_MILLIS
        val last = if (state.lastPayrollCycle < 0L) cycle else state.lastPayrollCycle
        return (((last + 1L) * PAYROLL_MONTH_MILLIS) - now).coerceAtLeast(0L)
    }

    val autoCargoDeliveryEnabled: Boolean
        get() = state.autoCargoDelivery


    val dailyMissionResetRemainingMillis: Long
        get() {
            val now = currentTimeMillis()
            val today = now / DAY_MILLIS
            return ((today + 1L) * DAY_MILLIS - now).coerceAtLeast(0L)
        }

    val dailyMissions: List<DailyMissionSave>
        get() = state.dailyMissions.missions

    fun dailyMissionProgress(mission: DailyMissionSave): Long =
        (dailyMetricValue(mission.metric) - mission.baseValue).coerceIn(0L, mission.target)

    fun claimDailyMission(id: String) {
        ensureDailyMissions()
        val mission = state.dailyMissions.missions.firstOrNull { it.id == id }
            ?: return notify("Missão diária não encontrada.")
        if (mission.claimed) return notify("Recompensa diária já coletada.")
        if (dailyMissionProgress(mission) < mission.target) return notify("Missão diária ainda não concluída.")

        var company = state.company
        var expansion = state.expansion
        var finances = state.finances
        val rewardText = when (mission.rewardType) {
            "XP" -> {
                expansion = expansion.copy(playerXp = expansion.playerXp + mission.rewardValue.coerceAtLeast(0L))
                "+${mission.rewardValue} XP do personagem"
            }
            "TOOL" -> {
                val quantity = mission.rewardValue.toInt().coerceAtLeast(1)
                val itemId = mission.rewardItemId.ifBlank { "fresa_hss" }
                expansion = expansion.copy(tools = expansion.tools + (itemId to ((expansion.tools[itemId] ?: 0) + quantity)))
                val toolName = GameProgression.tools.firstOrNull { it.id == itemId }?.name ?: itemId
                "+$quantity • $toolName"
            }
            else -> {
                val money = mission.rewardValue.coerceAtLeast(0L)
                company = company.copy(cashCents = company.cashCents + money)
                finances = addFinance(finances, "INCOME", "DAILY_MISSION", money, "Missão diária: ${mission.title}")
                money(money)
            }
        }
        state = state.copy(
            company = company,
            expansion = expansion,
            finances = finances,
            dailyMissions = state.dailyMissions.copy(
                missions = state.dailyMissions.missions.map { if (it.id == id) it.copy(claimed = true) else it }
            ),
        )
        notify("Missão diária concluída • $rewardText")
        persistAndRefresh()
    }

    fun clearMessage() {
        message = null
    }

    /** Economia/disciplinas; chamado 1x/s. */
    fun tick() {
        val now = currentTimeMillis()
        ensureDailyMissions(now)
        processMonthlyPayroll(now)
        updateIdleDiscipline(now)

        val elapsed = (now - state.company.lastSimulationAt).coerceAtLeast(0L)
        val settled = (elapsed / CYCLE_MILLIS) * CYCLE_MILLIS
        if (settled >= CYCLE_MILLIS) {
            simulateSettled(settled, now, advanceClock = true)
            ensureContracts()
        }
        if (state.autoCargoDelivery && pendingCargo.isNotEmpty() && !ownerFrame.busy && cargoInTransitIds.isEmpty()) {
            startCargoDelivery()
        }
        refreshFactoryInput()
    }

    /** Cena operacional; chamado em cadência visual (~20 fps), sem alterar ledger por frame. */
    fun advanceVisual(seconds: Double) {
        refreshFactoryInput()
        factoryFrame = factorySimulation.advance(seconds)

        ownerSimulation.update(factoryMachineInputs(), factoryGridColumns, factoryGridRows)
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
        if (snackActive) return notify("Modo foco já está ativo por ${durationCompact(focusModeRemainingMillis)}. Não é possível acumular tempo.")
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
                "Modo foco da equipe por 8h"
            ),
        )
        notify("Equipe alimentada. Celular/ociosidade bloqueados por 8 horas.")
        persist()
    }

    fun reprimandIdleEmployee() {
        val employee = idleEmployee ?: return notify("Ninguém está no celular agora.")
        reprimandEmployee(employee.id)
    }

    /** Paridade Fábrica Viva Android: a bronca pertence ao funcionário tocado na cena. */
    fun reprimandEmployee(id: String) {
        val employee = state.employees.firstOrNull { it.id == id }
            ?: return notify("Funcionário não encontrado.")
        val now = currentTimeMillis()
        if (state.workforce.idleEmployeeId != id || state.workforce.idleUntilAt <= now) {
            return notify("${employee.name} já está no posto.")
        }
        state = state.copy(
            workforce = state.workforce.copy(
                idleEmployeeId = null,
                idleSinceAt = 0L,
                idleUntilAt = 0L,
                nextIdleCheckAt = now + REPRIMAND_GRACE_MILLIS,
            ),
        )
        notify("${employee.name} voltou ao posto. Nova tolerância por 1 hora.")
        persistAndRefresh()
    }

    fun startCargoDelivery() {
        if (ownerFrame.busy || cargoInTransitIds.isNotEmpty()) return notify("O dono já está em uma entrega.")
        val ids = pendingCargo.map { it.id }
        if (ids.isEmpty()) return notify("Não há carga pronta para expedição.")

        ownerSimulation.update(factoryMachineInputs(), factoryGridColumns, factoryGridRows)
        if (!ownerSimulation.start()) return
        cargoInTransitIds = ids // snapshot: carga criada depois fica para a próxima viagem.
        ownerFrame = ownerSimulation.snapshot()
        notify("Expedição iniciada: ${ids.size} lote(s) nesta viagem.")
    }

    fun setAutoCargoDelivery(enabled: Boolean) {
        if (state.autoCargoDelivery == enabled) return
        state = state.copy(autoCargoDelivery = enabled)
        notify(if (enabled) "Entrega automática ativada: o dono despacha cargas prontas quando estiver livre." else "Entrega automática desativada.")
        persist()
        if (enabled && pendingCargo.isNotEmpty() && !ownerFrame.busy && cargoInTransitIds.isEmpty()) startCargoDelivery()
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
        val all = (0 until factoryGridRows).flatMap { y -> (0 until factoryGridColumns).map { x -> x to y } }
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

    fun moveMachineTo(machineId: String, gridX: Int, gridY: Int) {
        val machine = state.machines.firstOrNull { it.id == machineId } ?: return notify("Máquina não encontrada.")
        val x = gridX.coerceIn(0, factoryGridColumns - 1)
        val y = gridY.coerceIn(0, factoryGridRows - 1)
        val occupied = state.machines.any { it.id != machineId && it.gridX == x && it.gridY == y }
        if (occupied) return notify("Essa baia já está ocupada.")
        state = state.copy(machines = state.machines.map { if (it.id == machine.id) it.copy(gridX = x, gridY = y) else it })
        notify("${machineName(machine.machineType)} movida para a baia ${x + 1}.${y + 1}.")
        persistAndRefresh()
    }

    fun autoLayoutMachines() {
        if (state.machines.isEmpty()) return notify("Não há máquinas para organizar.")
        val sorted = state.machines.sortedWith(
            compareBy<MachineSave> { MachineCatalog.byType(it.machineType)?.specialty?.name ?: "" }
                .thenByDescending { MachineCatalog.byType(it.machineType)?.priceCents ?: 0L }
        )
        val positions = (0 until factoryGridRows).flatMap { y -> (0 until factoryGridColumns).map { x -> x to y } }
        val byId = sorted.mapIndexed { index, machine -> machine.id to positions[index % positions.size] }.toMap()
        state = state.copy(machines = state.machines.map { machine ->
            val pos = byId[machine.id] ?: (machine.gridX to machine.gridY)
            machine.copy(gridX = pos.first, gridY = pos.second)
        })
        notify("Galpão reorganizado por família de processo e valor tecnológico.")
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

    fun promotionRequirementMinutes(employeeId: String): Long {
        val employee = state.employees.firstOrNull { it.id == employeeId } ?: return Long.MAX_VALUE
        return (employee.jobGrade.coerceIn(1, 5) * 480L)
    }

    fun promoteEmployee(employeeId: String) {
        val employee = state.employees.firstOrNull { it.id == employeeId } ?: return notify("Funcionário não encontrado.")
        if (employee.jobGrade >= 5) return notify("${employee.name} já está no grau máximo da carreira interna.")
        val requirement = promotionRequirementMinutes(employeeId)
        if (employee.experience < requirement) return notify("${employee.name} precisa de ${requirement - employee.experience} min de experiência para a próxima promoção.")
        val raise = (employee.salaryCents * 12L / 100L).coerceAtLeast(20_000L)
        state = state.copy(employees = state.employees.map { current ->
            if (current.id == employeeId) current.copy(
                jobGrade = (current.jobGrade + 1).coerceAtMost(5),
                skillLevel = (current.skillLevel + 1).coerceAtMost(20),
                salaryCents = current.salaryCents + raise,
                morale = (current.morale + 6).coerceAtMost(100),
            ) else current
        })
        notify("${employee.name} promovido para Grau ${employee.jobGrade + 1}. Skill e salário foram atualizados.")
        persistAndRefresh()
    }

    fun crossTrainEmployee(employeeId: String, specialty: String) {
        val employee = state.employees.firstOrNull { it.id == employeeId } ?: return notify("Funcionário não encontrado.")
        if (employee.legendaryCode != null) return notify("Lendários mantêm sua função exclusiva.")
        if (employee.jobGrade < 2 || employee.experience < 720L) return notify("Treinamento cruzado libera no Grau 2 e com 720 min de experiência.")
        val valid = EmployeeSpecialty.values().firstOrNull { it.name == specialty } ?: return notify("Função inválida.")
        if (employee.specialty == valid.name) return notify("${employee.name} já exerce essa função.")
        val cost = (employee.salaryCents / 2L).coerceAtLeast(100_000L)
        if (state.company.cashCents < cost) return notify("Caixa insuficiente para o treinamento (${money(cost)}).")
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents - cost),
            employees = state.employees.map { if (it.id == employeeId) it.copy(specialty = valid.name, assignedMachineId = null, morale = (it.morale + 4).coerceAtMost(100)) else it },
            finances = addFinance(state.finances, "EXPENSE", "TRAINING", cost, "Treinamento cruzado: ${employee.name} → ${valid.name}"),
        )
        notify("${employee.name} agora está habilitado como ${valid.name}.")
        persistAndRefresh()
    }

    /** Paridade do diálogo de máquina Android: troca direta de operador pela máquina tocada. */
    fun assignEmployeeToMachine(employeeId: String, machineId: String) {
        val machine = state.machines.firstOrNull { it.id == machineId }
            ?: return notify("Máquina não encontrada.")
        val employee = state.employees.firstOrNull { it.id == employeeId }
            ?: return notify("Funcionário não encontrado.")
        if (!machine.installed) return notify("Instale a máquina antes de atribuir um operador.")

        state = state.copy(
            employees = state.employees.map { current ->
                when {
                    current.id == employee.id -> current.copy(assignedMachineId = machine.id)
                    current.assignedMachineId == machine.id -> current.copy(assignedMachineId = null)
                    else -> current
                }
            }
        )
        notify("${employee.name} atribuído a ${machineName(machine.machineType)}.")
        persistAndRefresh()
    }

    fun clearMachineOperator(machineId: String) {
        val machine = state.machines.firstOrNull { it.id == machineId }
            ?: return notify("Máquina não encontrada.")
        val operator = state.employees.firstOrNull { it.assignedMachineId == machineId }
        if (operator == null) return notify("Essa máquina já está sem operador.")
        state = state.copy(
            employees = state.employees.map {
                if (it.assignedMachineId == machineId) it.copy(assignedMachineId = null) else it
            }
        )
        notify("Operador removido de ${machineName(machine.machineType)}.")
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
        notify("Funcionário enviado para a Copa por 2 horas.")
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

        // V28: active contracts automatically reserve the best currently-free tool.
        // The tool remains only reserved here; consumption still happens when the contract settles.
        val bestTool = bestToolForContract(id)
        if (bestTool != null) {
            state = state.copy(
                expansion = state.expansion.copy(
                    contractTools = state.expansion.contractTools + (id to bestTool.id)
                )
            )
            notify("Contrato de ${contract.clientName} aceito • ${bestTool.name} reservada automaticamente.")
        } else {
            notify("Contrato de ${contract.clientName} aceito • nenhuma ferramenta livre para reservar.")
        }
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

    fun bestToolForContract(contractId: String): ToolDef? {
        val contract = state.contracts.firstOrNull { it.id == contractId } ?: return null
        val bindings = state.expansion.contractTools
        return GameProgression.tools
            .filter { tool ->
                val inventory = state.expansion.tools[tool.id] ?: 0
                val reserved = bindings.count { (cid, tid) -> cid != contractId && tid == tool.id }
                inventory > reserved
            }
            .maxByOrNull { toolSuitabilityScore(contract, it) }
    }

    fun toolRecommendationReason(contractId: String): String {
        val contract = state.contracts.firstOrNull { it.id == contractId } ?: return "Contrato indisponível"
        val tool = bestToolForContract(contractId) ?: return "Sem ferramenta livre"
        return when {
            contract.requiredQuality >= 88 || contract.difficulty >= 5 -> "${tool.name} • prioridade: qualidade e estabilidade"
            contract.requiredQuality >= 76 || contract.difficulty >= 3 -> "${tool.name} • melhor equilíbrio entre qualidade e velocidade"
            else -> "${tool.name} • prioridade: produtividade"
        }
    }

    fun autoBindToolForContract(contractId: String) {
        val contract = state.contracts.firstOrNull { it.id == contractId } ?: return notify("Contrato não encontrado.")
        if (contract.status !in setOf("AVAILABLE", "ACTIVE")) return notify("Esse contrato não aceita mais reserva de ferramenta.")
        val tool = bestToolForContract(contractId) ?: return notify("Nenhuma ferramenta disponível para este contrato.")
        bindTool(contractId, tool.id)
        notify("${tool.name} reservada automaticamente para ${contract.clientName}.")
    }

    fun autoDistributeContractTools() {
        val eligible = state.contracts
            .filter { it.status in setOf("ACTIVE", "AVAILABLE") }
            .sortedWith(compareBy<ContractSave> { if (it.status == "ACTIVE") 0 else 1 }.thenByDescending { it.requiredQuality }.thenByDescending { it.difficulty })
        if (eligible.isEmpty()) return notify("Não há contratos para distribuir ferramentas.")

        val remaining = state.expansion.tools.toMutableMap()
        val newBindings = state.expansion.contractTools.filterKeys { cid -> state.contracts.none { it.id == cid && it.status in setOf("ACTIVE", "AVAILABLE") } }.toMutableMap()
        var assigned = 0
        eligible.forEach { contract ->
            val tool = GameProgression.tools
                .filter { (remaining[it.id] ?: 0) > 0 }
                .maxByOrNull { toolSuitabilityScore(contract, it) }
            if (tool != null) {
                newBindings[contract.id] = tool.id
                remaining[tool.id] = (remaining[tool.id] ?: 0) - 1
                assigned++
            } else {
                newBindings.remove(contract.id)
            }
        }
        state = state.copy(expansion = state.expansion.copy(contractTools = newBindings))
        notify("Ferramentaria automática: $assigned contrato(s) receberam a melhor ferramenta disponível.")
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

    /**
     * API usada pela UI Android-parity.
     *
     * Mantém uma única fonte de verdade para a mudança de turno:
     * setShiftMode delega para setShift, que já atualiza estado,
     * notificação, persistência e refresh.
     */
    fun setShiftMode(mode: ShiftMode) {
        setShift(mode)
    }

    fun operatorFitScore(employeeId: String, machineId: String): Int {
        val employee = state.employees.firstOrNull { it.id == employeeId } ?: return 0
        val machine = state.machines.firstOrNull { it.id == machineId } ?: return 0
        return operatorFitScore(employee, machine)
    }

    fun operatorFitLabel(employeeId: String, machineId: String): String {
        val score = operatorFitScore(employeeId, machineId)
        return when {
            score >= 125 -> "EXCELENTE"
            score >= 100 -> "ÓTIMO"
            score >= 78 -> "BOM"
            score >= 55 -> "REGULAR"
            else -> "BAIXO"
        }
    }

    fun assignBestOperator(machineId: String) {
        val machine = state.machines.firstOrNull { it.id == machineId } ?: return notify("Máquina não encontrada.")
        val now = currentTimeMillis()
        val pool = state.employees.filter { employee ->
            employee.restingUntil <= now && (employee.assignedMachineId == null || employee.assignedMachineId == machineId)
        }.ifEmpty { state.employees.filter { it.restingUntil <= now } }
        val best = pool.maxByOrNull { operatorFitScore(it, machine) } ?: return notify("Nenhum operador disponível.")
        state = state.copy(employees = state.employees.map { current ->
            when {
                current.id == best.id -> current.copy(assignedMachineId = machine.id)
                current.assignedMachineId == machine.id -> current.copy(assignedMachineId = null)
                else -> current
            }
        })
        notify("${best.name} assumiu ${machineName(machine.machineType)} • encaixe ${operatorFitLabel(best.id, machine.id)}.")
        persistAndRefresh()
    }

    fun autoDistributeOperators() {
        val machines = state.machines.filter { it.installed }.sortedWith(
            compareByDescending<MachineSave> { MachineCatalog.byType(it.machineType)?.priceCents ?: 0L }
                .thenByDescending { it.level }
        )
        if (machines.isEmpty()) return notify("Instale máquinas antes de distribuir a equipe.")
        val now = currentTimeMillis()
        val supportRoles = setOf("STOCK_ASSISTANT", "QUALITY_INSPECTOR", "CNC_PROGRAMMER")
        val remaining = state.employees.filter { it.restingUntil <= now && it.specialty !in supportRoles }.toMutableList()
        if (remaining.isEmpty()) return notify("Nenhum operador está disponível neste momento.")
        val assignments = linkedMapOf<String, String>()
        machines.forEach { machine ->
            val best = remaining.maxByOrNull { operatorFitScore(it, machine) } ?: return@forEach
            assignments[machine.id] = best.id
            remaining.remove(best)
        }
        state = state.copy(employees = state.employees.map { employee ->
            val machineId = assignments.entries.firstOrNull { it.value == employee.id }?.key
            employee.copy(assignedMachineId = machineId)
        })
        notify("Equipe distribuída automaticamente: melhor encaixe entre especialidade, experiência, moral e fadiga.")
        persistAndRefresh()
    }

    fun syncRemoteHire(ownerUid: String?, name: String?, boostPct: Int, endsAt: Long) {
        val active = ownerUid != null && endsAt > currentTimeMillis()
        val next = if (active) state.expansion.copy(
            remoteHireOwnerUid = ownerUid,
            remoteHireName = name,
            remoteHireBoostPct = boostPct.coerceIn(0, 25),
            remoteHireEndsAt = endsAt,
        ) else state.expansion.copy(
            remoteHireOwnerUid = null, remoteHireName = null, remoteHireBoostPct = 0, remoteHireEndsAt = 0L,
        )
        if (next != state.expansion) { state = state.copy(expansion = next); persistAndRefresh() }
    }

    fun claimRemoteOperationXp(token: String, xp: Long) {
        if (token.isBlank() || token in state.expansion.claimedRentalXpIds) return
        val safeXp = xp.coerceIn(0L, 2_000L)
        state = state.copy(expansion = state.expansion.copy(
            playerXp = state.expansion.playerXp + safeXp,
            claimedRentalXpIds = state.expansion.claimedRentalXpIds + token,
        ))
        notify("Trabalho externo concluído • +$safeXp XP do personagem.")
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
        if (GameProgression.playerSkillPoints(state.company.companyLevel, state.expansion.playerXp, state.expansion.playerSkills) <= 0) {
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

    fun spinGacha(): GachaRewardDef? {
        if (state.expansion.gachaTickets <= 0) {
            notify("Você não tem fichas da roleta.")
            return null
        }
        val random = Random(currentTimeMillis() + idCounter)
        val epicPity = state.expansion.pityEpic + 1
        val legendaryPity = state.expansion.pityLegendary + 1
        val forcedLegendary = legendaryPity >= 100
        val forcedEpic = epicPity >= 40
        val roll = random.nextInt(10_000)

        val reward = when {
            forcedLegendary -> gachaByMinimumRarity(RarityDef.LEGENDARY, random)
            forcedEpic -> gachaByMinimumRarity(RarityDef.EPIC, random)
            roll < 80 -> gachaByMinimumRarity(RarityDef.LEGENDARY, random) // 0,8%
            roll < 450 -> gachaByMinimumRarity(RarityDef.EPIC, random)     // +3,7%
            roll < 1_250 -> randomPremiumMachine(random)                    // +8,0%
            roll < 3_050 -> randomCharacter(random)                         // +18%
            roll < 5_250 -> randomSkin(random)                              // +22%
            roll < 7_650 -> randomTool(RarityDef.RARE, random)              // +24%
            else -> randomTool(RarityDef.COMMON, random)                    // restante; ficha nunca é prêmio
        }

        var expansion = applyGachaReward(state.expansion, reward)
        expansion = expansion.copy(
            gachaTickets = (expansion.gachaTickets - 1).coerceAtLeast(0),
            pityLegendary = if (reward.rarity == RarityDef.LEGENDARY) 0 else legendaryPity,
            pityEpic = if (reward.rarity.rank >= RarityDef.EPIC.rank) 0 else epicPity,
        )
        if (reward.type == "legendary_employee") {
            val def = LegendaryEmployeeCatalog.byCode(reward.id)
            if (def != null && state.employees.none { it.legendaryCode == def.code }) {
                val employee = EmployeeSave(
                    id = newId("legendary"),
                    name = def.name,
                    specialty = def.specialty,
                    skillLevel = def.skillLevel,
                    salaryCents = def.salaryCents,
                    morale = def.morale,
                    trait = def.trait,
                    legendaryCode = def.code,
                )
                val mission = seedLegendaryMission(def.code)
                state = state.copy(
                    expansion = expansion,
                    employees = state.employees + employee,
                    legendaryMissions = if (mission != null && state.legendaryMissions.none { it.id == mission.id }) state.legendaryMissions + mission else state.legendaryMissions,
                )
            } else {
                state = state.copy(expansion = expansion)
            }
        } else {
            state = state.copy(expansion = expansion)
        }
        lastGachaReward = reward
        notify("Roleta • ${reward.rarity.label}: ${reward.title}")
        persistAndRefresh()
        return reward
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

    fun renameCompany(name: String) {
        val clean = name.trim().take(36)
        if (clean.length < 3) return notify("O nome da empresa precisa ter pelo menos 3 caracteres.")
        state = state.copy(company = state.company.copy(name = clean))
        notify("Empresa renomeada para $clean.")
        persist()
    }

    fun setAutoRest(enabled: Boolean) {
        state = state.copy(autoRest = enabled)
        notify(if (enabled) "Descanso automático ativado." else "Descanso automático desativado.")
        persist()
    }

    fun returnEmployeeFromBreak(id: String) {
        state = state.copy(
            employees = state.employees.map {
                if (it.id == id) it.copy(restingUntil = 0L) else it
            }
        )
        notify("Funcionário retornou ao posto.")
        persistAndRefresh()
    }

    fun restPlayer() {
        state = state.copy(playerRestingUntil = currentTimeMillis() + REST_MILLIS)
        notify("Você foi para a Copa por 2 horas.")
        persist()
    }

    fun returnPlayerFromBreak() {
        state = state.copy(playerRestingUntil = 0L)
        notify("Você voltou ao chão de fábrica.")
        persist()
    }

    fun hireLegendaryEmployee() {
        notify("Equipe lendária é exclusiva da Roleta Industrial. Não existe contratação direta.")
    }

    fun claimLegendaryMission(id: String) {
        val mission = state.legendaryMissions.firstOrNull { it.id == id }
            ?: return notify("Missão lendária não encontrada.")
        if (mission.claimed) return notify("Recompensa já coletada.")
        if (mission.progress < mission.target) return notify("Missão lendária ainda não concluída.")

        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents + mission.rewardCents),
            legendaryMissions = state.legendaryMissions.map {
                if (it.id == id) it.copy(claimed = true) else it
            },
            finances = addFinance(
                state.finances,
                "INCOME",
                "BONUS",
                mission.rewardCents,
                "Missão lendária: ${mission.title}",
            ),
        )
        notify("${mission.title}: ${money(mission.rewardCents)} coletados.")
        persist()
    }

    fun operateMachine(
        machineId: String,
        contractId: String,
        result: MinigameResult,
        manual: Boolean = true,
    ) {
        if (state.career.activeBatch != null) {
            return notify("Finalize ou descarte o lote atual antes de iniciar outro.")
        }
        val machine = state.machines.firstOrNull { it.id == machineId }
            ?: return notify("Máquina não encontrada.")
        if (machine.condition <= 80) return notify("Faça manutenção antes de operar.")
        val contract = state.contracts.firstOrNull {
            it.id == contractId && it.status == "ACTIVE" && it.completedQuantity < it.quantity
        } ?: return notify("Escolha um contrato ativo.")

        val career = state.career
        val mastery = career.mastery(machine.machineType)
        val score = result.normalizedScore
        val quantity = if (manual) {
            suggestedManualQuantity(machine.machineType, score, mastery, career)
        } else {
            (suggestedManualQuantity(machine.machineType, .42f, mastery, career) * .62)
                .roundToInt().coerceAtLeast(1)
        }
        val quality = (
            46 +
                score * 46f +
                mastery.qualityBonus +
                career.manualQualityBonus() -
                result.mistakes * 4
            ).roundToInt().coerceIn(35, 100)
        val now = currentTimeMillis()
        val batch = OwnerWorkBatchSave(
            id = newId("owner_batch"),
            machineId = machine.id,
            machineType = machine.machineType,
            contractId = contract.id,
            stage = ProductionStage.MACHINED.name,
            producedQuantity = quantity,
            quality = quality,
            precision = (result.precision.coerceIn(0f, 1f) * 100).roundToInt(),
            speed = (result.speed.coerceIn(0f, 1f) * 100).roundToInt(),
            mistakes = result.mistakes.coerceAtLeast(0),
            perfect = manual && result.perfect,
            manual = manual,
            createdAt = now,
            updatedAt = now,
        )
        val masteryGain = if (manual) 35 + (score * 85).roundToInt() else 18
        val masteryXp = career.masteryXp.toMutableMap().apply {
            this[machine.machineType] = (this[machine.machineType] ?: 0) + masteryGain
        }
        var nextCareer = career.copy(
            activeBatch = batch,
            masteryXp = masteryXp,
            totalManualOperations = career.totalManualOperations + if (manual) 1 else 0,
            assistedOperations = career.assistedOperations + if (manual) 0 else 1,
            perfectOperations = career.perfectOperations + if (manual && result.perfect) 1 else 0,
            bestScore = max(career.bestScore, (score * 100).roundToInt()),
            operationStreak = if (manual && score >= .72f) career.operationStreak + 1 else 0,
            lastOperationAt = now,
        )
        nextCareer = updateCareerRewards(nextCareer)

        val xp = if (manual) 30L + (score * 70).toLong() else 12L
        state = state.copy(
            career = nextCareer,
            expansion = state.expansion.copy(playerXp = state.expansion.playerXp + xp),
        )
        syncCareerPrestigeCharacters()
        notify(
            if (manual)
                "Lote usinado: $quantity pç • Q$quality. Agora leve ao Q (Qualidade)."
            else
                "Ciclo assistido: $quantity pç. Leve o lote ao Q."
        )
        persistAndRefresh()
    }

    fun moveOwnerBatchToQuality() {
        val batch = state.career.activeBatch ?: return notify("Nenhum lote ativo.")
        if (batch.stage !in setOf(ProductionStage.MACHINED.name, ProductionStage.WAITING_QC.name)) {
            return notify("O lote não está pronto para ir à Qualidade.")
        }
        state = state.copy(
            career = state.career.copy(
                activeBatch = batch.copy(stage = ProductionStage.QC.name, updatedAt = currentTimeMillis())
            )
        )
        notify("Lote no Controle de Qualidade. Faça a medição dimensional.")
        persist()
    }

    fun inspectOwnerBatch(approve: Boolean) {
        val batch = state.career.activeBatch ?: return notify("Nenhum lote ativo.")
        if (batch.stage !in setOf(ProductionStage.QC.name, ProductionStage.WAITING_QC.name)) {
            return notify("Leve o lote à Qualidade primeiro.")
        }
        val contract = state.contracts.firstOrNull { it.id == batch.contractId }
            ?: return notify("Contrato não encontrado.")
        val shouldApprove = batch.quality >= contract.requiredQuality
        val approved = approve && shouldApprove
        val nextStage = if (approved) ProductionStage.APPROVED else ProductionStage.REWORK
        var career = state.career.copy(
            activeBatch = batch.copy(stage = nextStage.name, updatedAt = currentTimeMillis()),
            approvedBatches = state.career.approvedBatches + if (approved) 1 else 0,
            reworkedBatches = state.career.reworkedBatches + if (approved) 0 else 1,
        )
        career = updateCareerRewards(career)
        state = state.copy(career = career)
        notify(if (approved) "Lote aprovado. Leve ao P (Embalagem)." else "Retrabalho necessário. Volte à máquina.")
        persist()
    }

    fun reworkOwnerBatch(result: MinigameResult) {
        val batch = state.career.activeBatch ?: return notify("Nenhum lote ativo.")
        if (batch.stage != ProductionStage.REWORK.name) return notify("Lote não está em retrabalho.")
        val score = result.normalizedScore
        val improved = (8 + score * 22 - result.mistakes * 3).roundToInt()
        val now = currentTimeMillis()
        val updated = batch.copy(
            stage = ProductionStage.MACHINED.name,
            quality = (batch.quality + improved).coerceIn(35, 100),
            precision = max(batch.precision, (result.precision * 100).roundToInt()),
            speed = ((batch.speed + (result.speed * 100).roundToInt()) / 2).coerceIn(0, 100),
            mistakes = batch.mistakes + result.mistakes,
            perfect = batch.perfect && result.perfect,
            reworkCount = batch.reworkCount + 1,
            updatedAt = now,
        )
        val masteryXp = state.career.masteryXp.toMutableMap().apply {
            this[batch.machineType] = (this[batch.machineType] ?: 0) + 24 + (score * 50).roundToInt()
        }
        var career = state.career.copy(
            activeBatch = updated,
            masteryXp = masteryXp,
            totalManualOperations = state.career.totalManualOperations + 1,
            lastOperationAt = now,
        )
        career = updateCareerRewards(career)
        state = state.copy(
            career = career,
            expansion = state.expansion.copy(
                playerXp = state.expansion.playerXp + 20L + (score * 45).toLong()
            ),
        )
        syncCareerPrestigeCharacters()
        notify("Retrabalho concluído • Q${updated.quality}. Leve novamente à Qualidade.")
        persist()
    }

    fun packOwnerBatch() {
        val batch = state.career.activeBatch ?: return notify("Nenhum lote ativo.")
        if (batch.stage != ProductionStage.APPROVED.name) return notify("A Qualidade precisa aprovar o lote.")
        state = state.copy(
            career = state.career.copy(
                activeBatch = batch.copy(
                    stage = ProductionStage.READY_TO_SHIP.name,
                    updatedAt = currentTimeMillis(),
                )
            )
        )
        notify("${batch.producedQuantity} peça(s) embaladas. Leve ao E (Expedição).")
        persist()
    }

    fun shipOwnerBatch() {
        val batch = state.career.activeBatch ?: return notify("Nenhum lote ativo.")
        if (batch.stage != ProductionStage.READY_TO_SHIP.name) return notify("Embale o lote antes de expedir.")
        val contract = state.contracts.firstOrNull { it.id == batch.contractId }
            ?: return notify("Contrato do lote não encontrado.")
        if (contract.status != "ACTIVE") return notify("Contrato não está mais ativo.")
        if (batch.quality < contract.requiredQuality) return notify("O lote não atingiu a qualidade mínima.")

        val remaining = (contract.quantity - contract.completedQuantity).coerceAtLeast(0)
        if (remaining <= 0) return notify("Esse contrato já foi concluído.")
        val applied = batch.producedQuantity.coerceAtMost(remaining)
        val newCompleted = contract.completedQuantity + applied
        val complete = newCompleted >= contract.quantity
        val commercialPct = state.career.commercialCompletionBonusPct()
        val reward = if (complete && !contract.rewardPaid) contract.rewardCents else 0L
        val bonus = reward * commercialPct.coerceIn(0, 25) / 100L

        var expansion = state.expansion.copy(
            playerXp = state.expansion.playerXp +
                if (complete) GameProgression.characterXpForContract(
                    contract.difficulty,
                    contract.quantity,
                    contract.requiredQuality,
                ) else 0L
        )
        if (complete) expansion = consumeBoundTool(expansion, contract.id)

        var finances = state.finances
        if (reward > 0L) {
            finances = addFinance(
                finances, "INCOME", "CONTRACT", reward,
                "Contrato concluído pelo dono: ${contract.clientName}"
            )
        }
        if (bonus > 0L) {
            finances = addFinance(
                finances, "INCOME", "BONUS", bonus,
                "Bônus comercial: ${contract.clientName}"
            )
        }

        var career = state.career.copy(
            activeBatch = null,
            shippedBatches = state.career.shippedBatches + 1,
        )
        career = updateCareerRewards(career)

        state = state.copy(
            company = state.company.copy(
                cashCents = state.company.cashCents + reward + bonus,
                reputation = state.company.reputation + if (complete && !contract.rewardPaid) contract.reputationReward else 0,
            ),
            contracts = state.contracts.map {
                if (it.id != contract.id) it else it.copy(
                    completedQuantity = if (complete) it.quantity else newCompleted,
                    productionProgressMilli = if (complete) it.quantity * 1000L
                        else max(it.productionProgressMilli, newCompleted * 1000L),
                    status = if (complete) "COMPLETED" else it.status,
                    rewardPaid = if (complete) true else it.rewardPaid,
                )
            },
            finances = finances,
            expansion = expansion,
            career = career,
        )
        syncCareerPrestigeCharacters()
        updateCompanyLevel()
        ensureContracts()
        notify(
            "Expedido: $applied pç em ${contract.clientName}" +
                if (reward > 0L) " • CONTRATO PAGO" else ""
        )
        persistAndRefresh()
    }

    fun scrapOwnerBatch() {
        val batch = state.career.activeBatch ?: return notify("Nenhum lote ativo.")
        var career = state.career.copy(
            activeBatch = null,
            scrappedBatches = state.career.scrappedBatches + 1,
        )
        career = updateCareerRewards(career)
        state = state.copy(career = career)
        notify("${batch.producedQuantity} peça(s) refugadas; nenhuma peça foi creditada.")
        persist()
    }

    fun abandonOwnerBatch() {
        if (state.career.activeBatch == null) return
        state = state.copy(career = state.career.copy(activeBatch = null))
        notify("Lote manual descartado.")
        persist()
    }

    fun unlockIndustrialSkill(id: String) {
        val def = IndustrialSkillCatalog.byId(id) ?: return notify("Skill industrial inválida.")
        if (!IndustrialSkillCatalog.canUnlock(def, state.career, state.company.companyLevel)) {
            return notify("Pré-requisito, nível ou pontos insuficientes.")
        }
        state = state.copy(
            career = state.career.copy(unlockedSkills = state.career.unlockedSkills + id)
        )
        notify("${def.name} aprendida.")
        persistAndRefresh()
    }

    fun setProductionPolicy(policy: ProductionPolicy) {
        if (policy != ProductionPolicy.BALANCED && !state.career.hasSkill("diretor_industrial")) {
            return notify("Políticas avançadas liberam com Diretor industrial.")
        }
        state = state.copy(career = state.career.copy(productionPolicy = policy.name))
        notify("Política: ${policy.label}.")
        persistAndRefresh()
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

    private fun updateCareerRewards(input: CareerSave): CareerSave {
        var career = input
        val marks = career.milestones.toMutableSet()
        var points = career.earnedSkillPoints

        fun award(id: String, amount: Int) {
            if (marks.add(id)) points += amount
        }

        if (career.totalManualOperations >= 1) award("manual_1", 1)
        if (career.totalManualOperations >= 10) award("manual_10", 1)
        if (career.totalManualOperations >= 25) award("manual_25", 1)
        if (career.totalManualOperations >= 50) award("manual_50", 1)
        if (career.totalManualOperations >= 100) award("manual_100", 2)
        if (career.totalManualOperations >= 250) award("manual_250", 2)
        if (career.perfectOperations >= 5) award("perfect_5", 1)
        if (career.perfectOperations >= 20) award("perfect_20", 2)
        if (career.approvedBatches >= 10) award("approved_10", 1)
        if (career.shippedBatches >= 20) award("shipped_20", 2)
        if (career.reworkedBatches >= 10) award("rework_10", 1)
        if (career.masteryXp.values.any { MachineMastery("x", it).level >= 10 }) {
            award("mastery_10", 2)
        }

        val achievements = career.achievements.toMutableSet()
        if (career.totalManualOperations >= 1) achievements += "Primeiro cavaco"
        if (career.perfectOperations >= 1) achievements += "Peça perfeita"
        if (career.bestScore >= 95) achievements += "Na medida"
        if (career.operationStreak >= 5) achievements += "Ritmo de oficina"
        if (career.approvedBatches >= 25) achievements += "Zero surpresa"
        if (career.shippedBatches >= 50) achievements += "Dono põe a mão na massa"
        if (career.masteryXp.values.any { MachineMastery("x", it).level >= 20 }) {
            achievements += "Mestre de máquina"
        }

        career = career.copy(
            milestones = marks,
            achievements = achievements,
            earnedSkillPoints = points,
        )
        return career
    }

    private fun syncCareerPrestigeCharacters() {
        val career = state.career
        val grants = buildSet {
            if (career.totalManualOperations >= 100) add("mestre_torneiro")
            if (career.perfectOperations >= 25) add("inspetor_zero")
            if (career.masteryXp.values.any { it >= 900 }) add("programadora_cnc")
            if (career.shippedBatches >= 100 && career.perfectOperations >= 40) add("mestre_5_eixos")
            if (career.shippedBatches >= 250 && career.totalManualOperations >= 500) add("lenda_chao_fabrica")
        }
        if (grants.isNotEmpty()) {
            state = state.copy(
                expansion = state.expansion.copy(
                    ownedCharacters = state.expansion.ownedCharacters + grants
                )
            )
        }
    }

    private fun advanceLegendaryMissions(
        save: GameSave,
        snapshot: ProductionSnapshot,
        operatingIds: Set<String>,
        elapsedMinutes: Long,
    ): List<LegendaryMissionSave> {
        if (save.legendaryMissions.isEmpty()) return save.legendaryMissions
        val machineById = save.machines.associateBy { it.id }
        val operatingTypes = save.machines
            .filter { it.id in operatingIds }
            .map { it.machineType }
            .toSet()

        return save.legendaryMissions.map { mission ->
            if (mission.claimed || mission.progress >= mission.target) return@map mission
            val definition = LegendaryMissionCatalog.byId(mission.id) ?: return@map mission
            val employee = save.employees.firstOrNull {
                it.legendaryCode == definition.legendaryCode
            } ?: return@map mission
            val assignedType = employee.assignedMachineId
                ?.let(machineById::get)
                ?.machineType

            val delta = legendaryMissionProgressDelta(
                definition = definition,
                legendaryEmployeeCode = employee.legendaryCode,
                assignedMachineType = assignedType,
                operatingMachineTypes = operatingTypes,
                operatingMachines = snapshot.operatingMachines,
                averageQuality = snapshot.averageQuality,
                elapsedMinutes = elapsedMinutes,
            )
            mission.copy(progress = (mission.progress + delta).coerceAtMost(mission.target))
        }
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
                    playerFatigue = (
                        working.playerFatigue - 8.5 * (10.0 / 60.0)
                    ).coerceIn(0.0, 100.0),
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
            val advanced = when {
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
            if (
                working.shiftMode == ShiftMode.CONTINUOUS_24H &&
                working.autoRest &&
                employee.assignedMachineId != null &&
                advanced.fatigue >= 88.0 &&
                advanced.restingUntil <= eventTime
            ) {
                advanced.copy(restingUntil = eventTime + REST_MILLIS)
            } else advanced
        }

        val playerResting = working.playerRestingUntil > eventTime
        val playerFatigue = if (playerResting) {
            (
                working.playerFatigue - 28.0 * (10.0 / 60.0)
            ).coerceIn(0.0, 100.0)
        } else {
            (
                working.playerFatigue +
                    (if (working.shiftMode == ShiftMode.CONTINUOUS_24H) 6.5 else 4.0) *
                    (10.0 / 60.0)
            ).coerceIn(0.0, 100.0)
        }
        val playerRestingUntil = if (
            working.shiftMode == ShiftMode.CONTINUOUS_24H &&
            working.autoRest &&
            playerFatigue >= 88.0 &&
            working.playerRestingUntil <= eventTime
        ) {
            eventTime + REST_MILLIS
        } else working.playerRestingUntil

        val missionProgress = advanceLegendaryMissions(
            save = working.copy(employees = employees),
            snapshot = snapshot,
            operatingIds = operatingIds,
            elapsedMinutes = 10L,
        )

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
            legendaryMissions = missionProgress,
            playerFatigue = playerFatigue,
            playerRestingUntil = playerRestingUntil,
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

        val baseModifiers = GameProgression.modifiers(save.expansion)
        val remoteProfessionalMultiplier = if (save.expansion.remoteHireEndsAt > now) {
            1.0 + save.expansion.remoteHireBoostPct.coerceIn(0, 25) / 100.0
        } else 1.0
        val base = ProductionEngine.calculate(
            machines = machineRuntime,
            employees = employeeRuntime,
            idleEmployeeIds = idleIds,
            modifiers = baseModifiers.copy(
                globalSpeedMultiplier =
                    baseModifiers.globalSpeedMultiplier * save.career.automationSpeedMultiplier() * remoteProfessionalMultiplier,
                qualityBonus =
                    baseModifiers.qualityBonus + save.career.automationQualityBonus(),
                energyMultiplier =
                    baseModifiers.energyMultiplier * save.career.energyMultiplier(),
            ),
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
                gridColumns = factoryGridColumns,
                gridRows = factoryGridRows,
            )
        )
        ownerSimulation.update(factoryMachineInputs(), factoryGridColumns, factoryGridRows)
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
                machineType = machine.machineType,
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
                specialty = employee.specialty,
            )
        }
    }

    private fun processMonthlyPayroll(now: Long) {
        val cycle = now / PAYROLL_MONTH_MILLIS
        if (state.lastPayrollCycle < 0L) {
            state = state.copy(lastPayrollCycle = cycle)
            persist()
            return
        }
        if (cycle <= state.lastPayrollCycle) return

        val payroll = monthlyPayrollCents
        state = if (payroll <= 0L) {
            state.copy(lastPayrollCycle = cycle)
        } else {
            state.copy(
                company = state.company.copy(cashCents = state.company.cashCents - payroll),
                lastPayrollCycle = cycle,
                finances = addFinance(
                    state.finances,
                    "EXPENSE",
                    "SALARY",
                    payroll,
                    "Folha salarial mensal • ${state.employees.size} funcionário(s)",
                    now,
                ),
            )
        }
        if (payroll > 0L) notify("Folha mensal paga: ${money(payroll)} • ${state.employees.size} funcionário(s).")
        persist()
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
            if (minRarity == RarityDef.LEGENDARY) {
                val hired = state.employees.mapNotNull { it.legendaryCode }.toSet()
                LegendaryEmployeeCatalog.all.filter {
                    it.unlockLevel <= state.company.companyLevel && it.code !in hired
                }.forEach { add(GachaRewardDef("legendary_employee", it.code, it.name, RarityDef.LEGENDARY)) }
            }
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
        if (save.lastPayrollCycle < 0L) {
            save = save.copy(lastPayrollCycle = currentTimeMillis() / PAYROLL_MONTH_MILLIS)
        }
        val existingMissionIds = save.legendaryMissions.map { it.id }.toMutableSet()
        val seededMissions = save.legendaryMissions.toMutableList()
        save.employees.mapNotNull { it.legendaryCode }.distinct().forEach { code ->
            seedLegendaryMission(code)?.let { mission ->
                if (existingMissionIds.add(mission.id)) seededMissions += mission
            }
        }
        val sanitizedCareer = save.career.copy(
            earnedSkillPoints = save.career.earnedSkillPoints.coerceAtLeast(1),
            productionPolicy = runCatching {
                ProductionPolicy.valueOf(save.career.productionPolicy)
            }.getOrDefault(ProductionPolicy.BALANCED).name,
        )
        save = save.copy(
            schemaVersion = 4,
            legendaryMissions = seededMissions,
            career = sanitizedCareer,
            playerFatigue = save.playerFatigue.coerceIn(0.0, 100.0),
        )
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
            lastPayrollCycle = now / PAYROLL_MONTH_MILLIS,
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
        for (y in 0 until factoryGridRows) for (x in 0 until factoryGridColumns) {
            if ((x to y) !in occupied) return x to y
        }
        return null
    }

    private fun toolSuitabilityScore(contract: ContractSave, tool: ToolDef): Double {
        val qualityPressure = ((contract.requiredQuality - 60).coerceAtLeast(0) / 12.0) + contract.difficulty * .42
        val qualityWeight = 1.4 + qualityPressure
        val speedScore = (tool.speedMultiplier - 1.0) * 100.0
        val qualityScore = tool.qualityBonus * qualityWeight
        val highQualityPenalty = if (contract.requiredQuality >= 80 && tool.qualityBonus < 0) 120.0 else 0.0
        return speedScore + qualityScore + tool.rarity.rank * 1.5 - highQualityPenalty
    }

    private fun operatorFitScore(employee: EmployeeSave, machine: MachineSave): Int {
        val specialty = MachineCatalog.byType(machine.machineType)?.specialty?.name
        val supportPenalty = if (employee.specialty in setOf("STOCK_ASSISTANT", "QUALITY_INSPECTOR", "CNC_PROGRAMMER") && employee.specialty != specialty) -70 else 0
        val specialtyBonus = if (employee.specialty == specialty) 60 else 0
        val skillBonus = employee.skillLevel.coerceIn(1, 20) * 9
        val experienceBonus = (employee.experience / 240L).toInt().coerceAtMost(25)
        val moraleBonus = employee.morale.coerceIn(0, 100) / 5
        val fatiguePenalty = (employee.fatigue.coerceIn(0.0, 1.0) * 25.0).toInt()
        val currentBonus = if (employee.assignedMachineId == machine.id) 6 else 0
        val legendaryBonus = if (employee.legendaryCode != null) 12 else 0
        val traitBonus = when {
            employee.trait.contains("CNC", ignoreCase = true) && machine.machineType.contains("CNC", ignoreCase = true) -> 14
            employee.trait.contains("Perfeccion", ignoreCase = true) -> 8
            employee.trait.contains("Ráp", ignoreCase = true) -> 5
            employee.trait.contains("Cuidad", ignoreCase = true) -> 5
            else -> 0
        }
        return specialtyBonus + supportPenalty + skillBonus + experienceBonus + moraleBonus + currentBonus + legendaryBonus + traitBonus - fatiguePenalty
    }

    private fun durationCompact(value: Long): String {
        val total = (value / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        fun two(v: Long) = v.toString().padStart(2, '0')
        return if (h > 0L) "${two(h)}:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
    }

    private fun ensureDailyMissions(now: Long = currentTimeMillis()) {
        val day = now / DAY_MILLIS
        if (state.dailyMissions.day == day && state.dailyMissions.missions.size == 3) return

        val level = state.company.companyLevel.coerceAtLeast(1)
        val variant = (day % 3L).toInt()
        val moneyMetric = listOf("CONTRACTS_COMPLETED", "MACHINE_MINUTES", "APPROVED_BATCHES")[variant]
        val moneyTarget = when (moneyMetric) {
            "MACHINE_MINUTES" -> 60L
            "APPROVED_BATCHES" -> 1L
            else -> 2L
        }
        val xpMetric = listOf("MANUAL_OPERATIONS", "SHIPPED_BATCHES", "CONTRACTS_COMPLETED")[(variant + 1) % 3]
        val xpTarget = if (xpMetric == "CONTRACTS_COMPLETED") 2L else 1L
        val toolMetric = listOf("MACHINE_MINUTES", "APPROVED_BATCHES", "MANUAL_OPERATIONS")[(variant + 2) % 3]
        val toolTarget = when (toolMetric) {
            "MACHINE_MINUTES" -> 100L
            else -> 2L
        }
        val toolPool = listOf("broca_madeira", "ferramenta_soldada", "fresa_hss", "broca_carbeto")
        val toolId = toolPool[((day + level) % toolPool.size).toInt()]

        fun mission(id: String, title: String, description: String, metric: String, target: Long, rewardType: String, rewardValue: Long, rewardItemId: String = "") =
            DailyMissionSave(
                id = "${day}_$id",
                title = title,
                description = description,
                metric = metric,
                target = target,
                baseValue = dailyMetricValue(metric),
                rewardType = rewardType,
                rewardValue = rewardValue,
                rewardItemId = rewardItemId,
            )

        val missions = listOf(
            mission(
                "cash",
                dailyMissionTitle(moneyMetric),
                dailyMissionDescription(moneyMetric, moneyTarget),
                moneyMetric,
                moneyTarget,
                "MONEY",
                300_000L + level * 75_000L,
            ),
            mission(
                "xp",
                dailyMissionTitle(xpMetric),
                dailyMissionDescription(xpMetric, xpTarget),
                xpMetric,
                xpTarget,
                "XP",
                280L + level * 35L,
            ),
            mission(
                "tool",
                dailyMissionTitle(toolMetric),
                dailyMissionDescription(toolMetric, toolTarget),
                toolMetric,
                toolTarget,
                "TOOL",
                1L,
                toolId,
            ),
        )
        state = state.copy(dailyMissions = DailyMissionStateSave(day, missions))
        persist()
    }

    private fun dailyMetricValue(metric: String): Long = when (metric) {
        "CONTRACTS_COMPLETED" -> state.contracts.count { it.status == "COMPLETED" }.toLong()
        "MANUAL_OPERATIONS" -> state.career.totalManualOperations.toLong()
        "SHIPPED_BATCHES" -> state.career.shippedBatches.toLong()
        "APPROVED_BATCHES" -> state.career.approvedBatches.toLong()
        "MACHINE_MINUTES" -> state.machines.sumOf { it.accumulatedWorkMinutes }
        else -> 0L
    }

    private fun dailyMissionTitle(metric: String): String = when (metric) {
        "CONTRACTS_COMPLETED" -> "Carteira em movimento"
        "MANUAL_OPERATIONS" -> "Dono no chão de fábrica"
        "SHIPPED_BATCHES" -> "Fluxo até a expedição"
        "APPROVED_BATCHES" -> "Qualidade sem atalho"
        "MACHINE_MINUTES" -> "Parque fabril em ritmo"
        else -> "Missão do turno"
    }

    private fun dailyMissionDescription(metric: String, target: Long): String = when (metric) {
        "CONTRACTS_COMPLETED" -> "Conclua $target contrato(s) hoje."
        "MANUAL_OPERATIONS" -> "Conclua $target operação(ões) manual(is) nas máquinas."
        "SHIPPED_BATCHES" -> "Expeda $target lote(s) do dono pelo fluxo M → Q → P → E."
        "APPROVED_BATCHES" -> "Aprove $target lote(s) no Controle de Qualidade."
        "MACHINE_MINUTES" -> "Acumule $target minutos de trabalho somados nas máquinas."
        else -> "Complete a meta diária antes da virada."
    }

    private fun persistAndRefresh() {
        persist()
        refreshFactoryInput()
    }

    private fun persist() {
        state = state.copy(schemaVersion = 4)
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
            val whole = safe / 100L
            val grouped = whole.toString().reversed().chunked(3).joinToString(".").reversed()
            val centsPart = (safe % 100L).toString().padStart(2, '0')
            return "${sign}R$ ${grouped},${centsPart}"
        }

        fun percent(value: Double): String = "${(value.coerceIn(0.0, 1.0) * 100.0).roundToInt()}%"
    }
}
