package br.com.usinagemmaster.game.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.model.*
import br.com.usinagemmaster.domain.simulation.EconomyBalance
import br.com.usinagemmaster.domain.simulation.ProductionEngine
import br.com.usinagemmaster.domain.simulation.ProductionModifiers
import br.com.usinagemmaster.game.model.*
import br.com.usinagemmaster.game.persistence.GameSaveCodec
import br.com.usinagemmaster.game.persistence.PlatformSaveStorage
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random

private const val SAVE_KEY = "usinagemmaster.kmp.save.v6"
private const val CYCLE_MILLIS = 10L * 60L * 1000L
private const val MAX_OFFLINE_MILLIS = 24L * 60L * 60L * 1000L
private const val REST_MILLIS = 60L * 60L * 1000L

enum class OwnerActivity(val label: String) {
    IDLE("Aguardando carga"),
    COLLECTING("Indo buscar a carga"),
    LOADING("Carregando caixas"),
    DELIVERING("Levando à entrega"),
    UNLOADING("Descarregando"),
    RETURNING("Voltando ao galpão"),
}

class GameStore {
    var state by mutableStateOf(loadOrCreate())
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var ownerActivity by mutableStateOf(OwnerActivity.IDLE)
        private set

    private var deliveryIds: List<String> = emptyList()
    private var deliveryStartedAt: Long = 0L
    private var idCounter: Long = 0L

    init {
        normalizeAndPersist()
        simulateOffline()
        ensureContracts()
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

    fun clearMessage() {
        message = null
    }

    fun tick() {
        val now = currentTimeMillis()
        advanceOwner(now)

        val elapsed = (now - state.company.lastSimulationAt).coerceAtLeast(0L)
        val settled = (elapsed / CYCLE_MILLIS) * CYCLE_MILLIS
        if (settled >= CYCLE_MILLIS) {
            simulateSettled(settled, now, advanceClock = true)
            ensureContracts()
        }
    }

    fun boost10Minutes() {
        if (state.boostTokens <= 0) return notify("Você não possui impulsos de +10 min.")
        if (!WorkLifeRules.factoryOpen(state.shiftMode, currentTimeMillis())) {
            return notify("Fábrica fechada no turno 12h. O expediente é 07:00–19:00.")
        }
        if (production.operatingMachines <= 0) return notify("Nenhuma máquina está produzindo agora.")

        state = state.copy(boostTokens = state.boostTokens - 1)
        simulateSettled(CYCLE_MILLIS, currentTimeMillis(), advanceClock = false, boost = true)
        notify("Impulso aplicado. A produção foi enviada para CARGA.")
        persist()
    }

    fun dailyBonus() {
        val day = currentTimeMillis() / 86_400_000L
        if (state.lastDailyBonusDay == day) return notify("Bônus diário já recebido.")
        val reward = 150_000L + state.company.companyLevel * 25_000L
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents + reward),
            boostTokens = state.boostTokens + 1,
            lastDailyBonusDay = day,
            finances = addFinance(state.finances, "INCOME", "BONUS", reward, "Bônus diário"),
        )
        notify("Bônus diário recebido: ${money(reward)} + 1 impulso.")
        persist()
    }

    fun buySnack() {
        val cost = EconomyBalance.TEAM_SNACK_COST_CENTS
        if (state.company.cashCents < cost) return notify("Caixa insuficiente para o cento de salgados.")
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents - cost),
            snackUntil = currentTimeMillis() + EconomyBalance.SNACK_IMMUNITY_MILLIS,
            finances = addFinance(state.finances, "EXPENSE", "SALARY", cost, "Cento de salgados • foco da equipe por 8h"),
        )
        notify("Equipe alimentada. Foco protegido por 8 horas.")
        persist()
    }

    fun startCargoDelivery() {
        if (ownerActivity != OwnerActivity.IDLE) return
        val ids = pendingCargo.map { it.id }
        if (ids.isEmpty()) return notify("Não há carga pronta.")
        deliveryIds = ids
        deliveryStartedAt = currentTimeMillis()
        ownerActivity = OwnerActivity.COLLECTING
        notify("O dono foi buscar ${ids.size} carga(s).")
    }

    private fun advanceOwner(now: Long) {
        if (ownerActivity == OwnerActivity.IDLE || deliveryStartedAt <= 0L) return
        val elapsed = now - deliveryStartedAt
        ownerActivity = when {
            elapsed < 1_200L -> OwnerActivity.COLLECTING
            elapsed < 2_400L -> OwnerActivity.LOADING
            elapsed < 4_200L -> OwnerActivity.DELIVERING
            elapsed < 5_200L -> OwnerActivity.UNLOADING
            else -> {
                finishCargoDelivery()
                OwnerActivity.RETURNING
            }
        }
        if (elapsed >= 6_200L && ownerActivity == OwnerActivity.RETURNING) {
            ownerActivity = OwnerActivity.IDLE
            deliveryIds = emptyList()
            deliveryStartedAt = 0L
        }
    }

    private fun finishCargoDelivery() {
        val selected = state.cargo.filter { it.id in deliveryIds && it.pending }
        if (selected.isEmpty()) return
        val now = currentTimeMillis()
        val value = selected.sumOf { it.valueCents }
        val cycles = selected.sumOf { it.cycles }
        val ids = selected.map { it.id }.toSet()
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents + value),
            cargo = state.cargo.map { if (it.id in ids && it.pending) it.copy(deliveredAt = now) else it },
            finances = addFinance(
                state.finances,
                "INCOME",
                "PRODUCTION",
                value,
                "Entrega de carga • $cycles ciclo(s) de produção",
            ),
        )
        notify("Carga entregue. ${money(value)} entrou no caixa.")
        persist()
    }

    fun buyMachine(machineType: String) {
        val def = MachineCatalog.byType(machineType) ?: return notify("Máquina desconhecida.")
        if (state.company.cashCents < def.priceCents) return notify("Caixa insuficiente.")
        if (state.company.usedWarehouseSpace + def.space > state.company.warehouseSpace) {
            return notify("Espaço insuficiente no galpão.")
        }
        val occupied = state.machines.map { it.gridX to it.gridY }.toSet()
        val spot = (0..5).flatMap { y -> (0..4).map { x -> x to y } }.firstOrNull { it !in occupied }
            ?: return notify("Não há posição livre no layout.")

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
        notify("${def.name} instalada.")
        persist()
    }

    fun repairMachine(id: String) {
        val machine = state.machines.firstOrNull { it.id == id } ?: return
        val def = MachineCatalog.byType(machine.machineType) ?: return
        val missing = (1000 - machine.condition).coerceAtLeast(0)
        if (missing == 0) return notify("A máquina já está em condição máxima.")
        val cost = (def.maintenanceCents * missing / 1000L).coerceAtLeast(5_000L)
        if (state.company.cashCents < cost) return notify("Caixa insuficiente para manutenção.")
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents - cost),
            machines = state.machines.map { if (it.id == id) it.copy(condition = 1000) else it },
            finances = addFinance(state.finances, "EXPENSE", "MAINTENANCE", cost, "Manutenção: ${def.name}"),
        )
        notify("Manutenção concluída por ${money(cost)}.")
        persist()
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
        persist()
    }

    fun moveMachineNext(id: String) {
        val machine = state.machines.firstOrNull { it.id == id } ?: return
        val occupied = state.machines.filterNot { it.id == id }.map { it.gridX to it.gridY }.toSet()
        val all = (0..5).flatMap { y -> (0..4).map { x -> x to y } }
        val currentIndex = all.indexOf(machine.gridX to machine.gridY).coerceAtLeast(0)
        val next = (1..all.size).map { all[(currentIndex + it) % all.size] }.firstOrNull { it !in occupied }
            ?: return
        state = state.copy(machines = state.machines.map {
            if (it.id == id) it.copy(gridX = next.first, gridY = next.second) else it
        })
        persist()
    }

    fun hireEmployee() {
        val random = Random(currentTimeMillis())
        val specialty = EmployeeSpecialty.values().random(random)
        val level = random.nextInt(1, 5)
        val salary = 180_000L + level * 65_000L
        if (state.company.cashCents < salary) return notify("Caixa insuficiente para a contratação.")

        val first = listOf(
            "Carlos", "Marcos", "João", "Rafael", "Bruno", "Diego", "André", "Paulo",
            "Luciana", "Patrícia", "Camila", "Fernanda", "Amanda", "Juliana", "Mariana", "Beatriz",
        ).random(random)
        val last = listOf("Silva", "Santos", "Oliveira", "Souza", "Costa", "Ferreira", "Almeida", "Lima").random(random)
        val traits = listOf("Cuidadoso", "Rápido", "Perfeccionista", "Distraído", "Casca grossa", "Elétrico")
        val employee = EmployeeSave(
            id = newId("employee"),
            name = "$first $last",
            specialty = specialty.name,
            skillLevel = level,
            salaryCents = salary,
            morale = random.nextInt(65, 96),
            trait = traits.random(random),
        )
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents - salary),
            employees = state.employees + employee,
            finances = addFinance(state.finances, "EXPENSE", "SALARY", salary, "Contratação: ${employee.name}"),
        )
        notify("${employee.name} contratado.")
        persist()
    }

    fun assignEmployeeNext(employeeId: String) {
        val employee = state.employees.firstOrNull { it.id == employeeId } ?: return
        if (state.machines.isEmpty()) return notify("Compre uma máquina antes.")
        val current = state.machines.indexOfFirst { it.id == employee.assignedMachineId }
        val candidateOrder = (1..state.machines.size).map { state.machines[(current + it).mod(state.machines.size)] }
        val target = candidateOrder.firstOrNull { machine ->
            state.employees.none { it.id != employeeId && it.assignedMachineId == machine.id }
        } ?: return notify("Todas as máquinas já possuem operador.")

        state = state.copy(employees = state.employees.map {
            if (it.id == employeeId) it.copy(assignedMachineId = target.id) else it
        })
        notify("${employee.name} atribuído a ${machineName(target.machineType)}.")
        persist()
    }

    fun restEmployee(id: String) {
        val now = currentTimeMillis()
        state = state.copy(employees = state.employees.map {
            if (it.id == id) it.copy(restingUntil = now + REST_MILLIS) else it
        })
        notify("Funcionário enviado para a Copa por 1 hora.")
        persist()
    }

    fun acceptContract(id: String) {
        val c = state.contracts.firstOrNull { it.id == id } ?: return
        if (c.status != "AVAILABLE") return
        if (!contractAllowed(c)) return notify("Seu nível ainda não atende este contrato.")
        val now = currentTimeMillis()
        state = state.copy(contracts = state.contracts.map {
            if (it.id == id) it.copy(status = "ACTIVE", startedAt = now) else it
        })
        notify("Contrato de ${c.clientName} aceito.")
        persist()
    }

    fun cancelContract(id: String) {
        val c = state.contracts.firstOrNull { it.id == id } ?: return
        if (c.status != "ACTIVE") return
        state = state.copy(
            company = state.company.copy(
                cashCents = (state.company.cashCents - c.penaltyCents).coerceAtLeast(0L),
                reputation = (state.company.reputation - c.reputationPenalty).coerceAtLeast(0),
            ),
            contracts = state.contracts.map { if (it.id == id) it.copy(status = "CANCELLED") else it },
            finances = addFinance(state.finances, "EXPENSE", "CONTRACT", c.penaltyCents, "Cancelamento: ${c.clientName}"),
        )
        updateCompanyLevel()
        notify("Contrato cancelado com multa de ${money(c.penaltyCents)}.")
        persist()
    }

    fun expandWarehouse() {
        val currentLevel = ((state.company.warehouseSpace - 100) / 50) + 1
        val cost = 2_000_000L * currentLevel
        if (state.company.cashCents < cost) return notify("Faltam ${money(cost - state.company.cashCents)} para ampliar.")
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
        if (goal.claimed) return
        val progress = goalProgress(goal)
        if (progress < goal.target) return notify("Meta ainda não concluída.")
        state = state.copy(
            company = state.company.copy(cashCents = state.company.cashCents + goal.rewardCents),
            goals = state.goals.map { if (it.id == id) it.copy(claimed = true) else it },
            finances = addFinance(state.finances, "INCOME", "BONUS", goal.rewardCents, "Meta: ${goal.title}"),
        )
        notify("Meta resgatada: ${money(goal.rewardCents)}.")
        persist()
    }

    fun goalProgress(goal: GoalSave): Int = when (goal.id) {
        "first_employee" -> state.employees.size
        "three_machines" -> state.machines.size
        "reputation_10" -> state.company.reputation
        "warehouse_150" -> state.company.warehouseSpace
        else -> 0
    }

    fun setShift(mode: ShiftMode) {
        state = state.copy(shiftMode = mode)
        notify(if (mode == ShiftMode.DAY_12H) "Turno 07:00–19:00 ativado." else "Operação 24 horas ativada.")
        persist()
    }

    fun setSpecialty(code: String) {
        val def = GameProgression.specialties.firstOrNull { it.code == code } ?: return
        if (state.company.companyLevel < def.minLevel) return notify("Nível ${def.minLevel} necessário.")
        state = state.copy(expansion = state.expansion.copy(specialty = code))
        notify("Especialização: ${def.label}.")
        persist()
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
        persist()
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
        persist()
    }

    fun spinGacha() {
        if (state.expansion.gachaTickets <= 0) return notify("Sem fichas para a Roleta Industrial.")
        val random = Random(currentTimeMillis())
        val candidates = buildList {
            addAll(GameProgression.tools.filter { it.id !in listOf("broca_madeira", "ferramenta_soldada") }.map { "tool:${it.id}:${it.name}" })
            addAll(GameProgression.skins.filter { it.minLevel <= state.company.companyLevel + 6 }.map { "skin:${it.id}:${it.name}" })
            addAll(GameProgression.characters.filter { it.minLevel <= state.company.companyLevel + 6 }.map { "character:${it.id}:${it.name}" })
        }
        val reward = candidates.random(random)
        val parts = reward.split(':', limit = 3)
        var exp = state.expansion.copy(gachaTickets = state.expansion.gachaTickets - 1)
        when (parts[0]) {
            "tool" -> exp = exp.copy(tools = exp.tools + (parts[1] to ((exp.tools[parts[1]] ?: 0) + 1)))
            "skin" -> {
                if (parts[1] in exp.ownedSkins) {
                    val tool = GameProgression.tools.random(random)
                    exp = exp.copy(tools = exp.tools + (tool.id to ((exp.tools[tool.id] ?: 0) + 1)))
                } else exp = exp.copy(ownedSkins = exp.ownedSkins + parts[1])
            }
            "character" -> {
                if (parts[1] in exp.ownedCharacters) {
                    val tool = GameProgression.tools.random(random)
                    exp = exp.copy(tools = exp.tools + (tool.id to ((exp.tools[tool.id] ?: 0) + 1)))
                } else exp = exp.copy(ownedCharacters = exp.ownedCharacters + parts[1])
            }
        }
        state = state.copy(expansion = exp)
        notify("Roleta: ${parts.getOrElse(2) { "recompensa industrial" }}.")
        persist()
    }

    fun equipSkin(id: String) {
        if (id !in state.expansion.ownedSkins) return
        state = state.copy(expansion = state.expansion.copy(equippedSkin = id))
        persist()
    }

    fun resetSave() {
        PlatformSaveStorage.remove(SAVE_KEY)
        state = createInitial()
        ownerActivity = OwnerActivity.IDLE
        deliveryIds = emptyList()
        normalizeAndPersist()
        ensureContracts()
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
            val staged = pendingCargoCents
            if (staged > 0) notify("Produção offline fechada. Carga pronta: ${money(staged)}.")
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

            if (!open) {
                working = working.copy(
                    employees = working.employees.map { WorkLifeRules.afterRest(it, 10) },
                    contracts = working.contracts.map {
                        if (it.status == "ACTIVE") it.copy(deadlineAt = it.deadlineAt + CYCLE_MILLIS) else it
                    },
                )
            } else {
                working = simulateOpenCycle(working, cycleEnd, boost && index == 0)
            }
            cursor = cycleEnd
        }

        if (advanceClock) {
            val target = (state.company.lastSimulationAt + cycles * CYCLE_MILLIS).coerceAtMost(eventTime)
            working = working.copy(company = working.company.copy(lastSimulationAt = target))
        }
        state = working
        updateCompanyLevel()
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
                val targetMilli = contract.quantity * 1000L
                val current = contract.productionProgressMilli.coerceAtMost(targetMilli)
                val needed = (targetMilli - current).coerceAtLeast(0L)
                val qualityGap = contract.requiredQuality - snapshot.averageQuality
                val qualityFactor = when {
                    qualityGap <= 0 -> 1.0
                    qualityGap <= 10 -> .70
                    else -> .30
                }
                val applied = min((productionMilli * qualityFactor).toLong(), needed)
                val consumed = if (qualityFactor <= 0.0) productionMilli else ceil(applied / qualityFactor).toLong()
                val next = current + applied
                productionMilli = (productionMilli - consumed).coerceAtLeast(0L)

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
                        expansion = expansion.copy(playerXp = expansion.playerXp + contract.difficulty * 120L + contract.requiredQuality)
                        finances = addFinance(
                            finances,
                            "INCOME",
                            "CONTRACT",
                            contract.rewardCents,
                            "Contrato concluído: ${contract.clientName}",
                            eventTime,
                        )
                    }
                } else {
                    contracts[i] = contract.copy(
                        completedQuantity = (next / 1000L).toInt(),
                        productionProgressMilli = next,
                    )
                }
            }
        }

        var machines = working.machines
        var employees = working.employees
        val operatingIds = snapshot.machineProduction.filter { it.isOperating }.map { it.machineId }.toSet()
        machines = machines.map { machine ->
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
        employees = employees.map { employee ->
            if (employee.assignedMachineId !in operatingIds) {
                if (WorkLifeRules.resting(employee, eventTime)) WorkLifeRules.afterRest(employee, 10) else employee
            } else {
                val newExperience = employee.experience + 10L
                val skill = (1 + (newExperience / 480L).toInt()).coerceIn(employee.skillLevel, 10)
                WorkLifeRules.afterWorked(employee.copy(experience = newExperience, skillLevel = skill), 10, working.shiftMode)
            }
        }

        var penalty = 0L
        var reputationLoss = 0
        contracts = contracts.map { contract ->
            if (contract.status == "ACTIVE" && eventTime > contract.deadlineAt) {
                penalty += contract.penaltyCents
                reputationLoss += contract.reputationPenalty
                finances = addFinance(
                    finances,
                    "EXPENSE",
                    "CONTRACT",
                    contract.penaltyCents,
                    "Multa por atraso: ${contract.clientName}",
                    eventTime,
                )
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
            cargo = cargo,
            finances = finances,
            expansion = expansion,
        )
    }

    private fun calculateProduction(now: Long, save: GameSave = state): ProductionSnapshot {
        if (!WorkLifeRules.factoryOpen(save.shiftMode, now)) return ProductionSnapshot(
            idleMachines = save.machines.count { it.installed }
        )

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

        val base = ProductionEngine.calculate(
            machines = machineRuntime,
            employees = employeeRuntime,
            idleEmployeeIds = emptySet(),
            modifiers = GameProgression.modifiers(save.expansion),
        )

        val adjustedMachines = base.machineProduction.map { mp ->
            val employee = save.employees.firstOrNull { it.id == mp.employeeId }
            val efficiency = employee?.let { WorkLifeRules.efficiency(it, now) } ?: 1.0
            mp.copy(unitsPerHour = mp.unitsPerHour * efficiency)
        }
        val adjustedUnits = adjustedMachines.filter { it.isOperating }.sumOf { it.unitsPerHour }
        val ratio = if (base.totalUnitsPerHour > 0.0) (adjustedUnits / base.totalUnitsPerHour).coerceIn(0.0, 1.0) else 0.0
        val gross = (base.grossPerHourCents * ratio).toLong()
        return base.copy(
            totalUnitsPerHour = adjustedUnits,
            grossPerHourCents = gross,
            netPerHourCents = (gross - base.energyPerHourCents).coerceAtLeast(0L),
            machineProduction = adjustedMachines,
        )
    }

    private fun ensureContracts() {
        val available = state.contracts.count { it.status == "AVAILABLE" && contractAllowed(it) }
        if (available >= 5) return
        val newContracts = state.contracts.toMutableList()
        repeat(5 - available) { newContracts += generateContract() }
        state = state.copy(contracts = newContracts)
        persist()
    }

    private fun generateContract(): ContractSave {
        val now = currentTimeMillis()
        val random = Random(now + idCounter)
        val clients = listOf("Metalúrgica Horizonte", "AutoPeças Brasil", "AgroMec", "Hidráulica Forte", "AçoSul", "TecnoBombas")
        val types = listOf("Peça unitária", "Lote pequeno", "Lote médio", "Retrabalho", "Estrutura metálica")
        val maxDifficulty = when {
            state.company.companyLevel >= 10 -> 5
            state.company.companyLevel >= 7 -> 4
            state.company.companyLevel >= 4 -> 3
            state.company.companyLevel >= 2 -> 2
            else -> 1
        }
        val difficulty = random.nextInt(1, maxDifficulty + 1)
        val qty = random.nextInt(4, 30) * difficulty
        val reward = 350_000L + difficulty * 250_000L + qty * 12_000L
        return ContractSave(
            id = newId("contract"),
            clientName = clients.random(random),
            type = types.random(random),
            quantity = qty,
            difficulty = difficulty,
            requiredQuality = 45 + difficulty * 9,
            rewardCents = reward,
            penaltyCents = reward / 4L,
            reputationReward = difficulty * 2,
            reputationPenalty = difficulty,
            generatedAt = now,
            deadlineAt = now + (6L + difficulty * 3L) * 60L * 60L * 1000L,
        )
    }

    private fun contractAllowed(c: ContractSave): Boolean {
        val minLevel = when (c.difficulty.coerceIn(1, 5)) {
            1 -> 1
            2 -> 2
            3 -> 4
            4 -> 7
            else -> 10
        }
        val skillNeed = when (c.difficulty.coerceIn(1, 5)) {
            3 -> 1
            4 -> 2
            5 -> 3
            else -> 0
        }
        val specialtyNeeded = c.difficulty >= 5
        return state.company.companyLevel >= minLevel &&
            state.expansion.companySkills.size >= skillNeed &&
            (!specialtyNeeded || state.expansion.specialty != "generalista")
    }

    private fun updateCompanyLevel() {
        val computed = (1 + state.company.reputation / 20).coerceAtLeast(state.company.companyLevel)
        if (computed != state.company.companyLevel) {
            state = state.copy(company = state.company.copy(companyLevel = computed))
        }
    }

    private fun normalizeAndPersist() {
        var save = state
        if (save.company.lastSimulationAt <= 0L) {
            save = save.copy(company = save.company.copy(lastSimulationAt = currentTimeMillis()))
        }
        if (save.goals.isEmpty()) {
            save = save.copy(goals = defaultGoals())
        }
        state = save
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
        )
    }

    private fun defaultGoals() = listOf(
        GoalSave("first_employee", "Contrate seu primeiro funcionário", 1, 250_000L),
        GoalSave("three_machines", "Tenha 3 máquinas instaladas", 3, 500_000L),
        GoalSave("reputation_10", "Alcance 10 de reputação", 10, 750_000L),
        GoalSave("warehouse_150", "Expanda o galpão para 150 m²", 150, 400_000L),
    )

    private fun persist() {
        PlatformSaveStorage.write(SAVE_KEY, GameSaveCodec.encode(state))
    }

    private fun addFinance(
        list: List<FinanceSave>,
        type: String,
        category: String,
        amount: Long,
        description: String,
        at: Long = currentTimeMillis(),
    ): List<FinanceSave> = (list + FinanceSave(
        id = newId("finance"),
        type = type,
        category = category,
        amountCents = amount,
        description = description,
        createdAt = at,
    )).takeLast(200)

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
    }
}
