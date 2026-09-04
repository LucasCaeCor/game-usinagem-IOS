package br.com.usinagemmaster.game.persistence

import br.com.usinagemmaster.game.model.*

/**
 * Codec textual versionado e sem dependência de kotlinx.serialization.
 * Cada mutação do jogo é persistida no storage nativo da plataforma.
 */
object GameSaveCodec {
    fun encode(save: GameSave): String = buildString {
        row("VERSION", save.schemaVersion)
        row(
            "COMPANY",
            save.company.name,
            save.company.cashCents,
            save.company.reputation,
            save.company.companyLevel,
            save.company.warehouseSpace,
            save.company.usedWarehouseSpace,
            save.company.lastSimulationAt,
        )
        row(
            "SETTINGS",
            save.shiftMode.name,
            save.boostTokens,
            save.snackUntil,
            save.lastDailyBonusDay,
        )
        row(
            "EXP",
            save.expansion.specialty,
            encodeSet(save.expansion.companySkills),
            encodeSet(save.expansion.playerSkills),
            save.expansion.gachaTickets,
            save.expansion.pityEpic,
            save.expansion.pityLegendary,
            encodeSet(save.expansion.ownedSkins),
            save.expansion.equippedSkin,
            encodeSet(save.expansion.ownedCharacters),
            save.expansion.equippedCharacter ?: "",
            encodeMap(save.expansion.tools),
            encodeSet(save.expansion.premiumMachines),
            save.expansion.playerXp,
        )
        row(
            "PROFILE",
            save.profile.name,
            save.profile.bodyType,
            save.profile.skinTone,
            save.profile.hair,
            save.profile.hairColor,
            save.profile.uniform,
            save.profile.helmet,
            save.profile.accessory,
        )
        save.machines.forEach {
            row(
                "M", it.id, it.machineType, it.level, it.condition,
                it.accumulatedWorkMinutes, it.installed, it.gridX, it.gridY,
            )
        }
        save.employees.forEach {
            row(
                "E", it.id, it.name, it.specialty, it.skillLevel, it.experience,
                it.salaryCents, it.morale, it.trait, it.assignedMachineId ?: "",
                it.legendaryCode ?: "", it.fatigue, it.restingUntil,
            )
        }
        save.contracts.forEach {
            row(
                "C", it.id, it.clientName, it.type, it.quantity, it.completedQuantity,
                it.productionProgressMilli, it.difficulty, it.requiredQuality,
                it.rewardCents, it.penaltyCents, it.reputationReward,
                it.reputationPenalty, it.generatedAt, it.startedAt, it.deadlineAt,
                it.status, it.rewardPaid,
            )
        }
        save.cargo.forEach {
            row("CARGO", it.id, it.valueCents, it.unitsMilli, it.cycles, it.createdAt, it.deliveredAt)
        }
        save.finances.takeLast(200).forEach {
            row("F", it.id, it.type, it.category, it.amountCents, it.description, it.createdAt)
        }
        save.goals.forEach {
            row("G", it.id, it.title, it.target, it.rewardCents, it.claimed)
        }
    }

    fun decode(raw: String): GameSave? = runCatching {
        var version = 2
        var company: CompanySave? = null
        var shift = ShiftMode.DAY_12H
        var boostTokens = 2
        var snackUntil = 0L
        var lastDaily = -1L
        var expansion = ExpansionSave()
        var profile = PlayerProfileSave()
        val machines = mutableListOf<MachineSave>()
        val employees = mutableListOf<EmployeeSave>()
        val contracts = mutableListOf<ContractSave>()
        val cargo = mutableListOf<ProductionCargoSave>()
        val finances = mutableListOf<FinanceSave>()
        val goals = mutableListOf<GoalSave>()

        raw.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val p = line.split('|').map(::unescape)
            when (p.firstOrNull()) {
                "VERSION" -> version = p.int(1, 2)
                "COMPANY" -> company = CompanySave(
                    name = p.text(1, "Oficina Império do Aço"),
                    cashCents = p.long(2, 3_500_000L),
                    reputation = p.int(3),
                    companyLevel = p.int(4, 1),
                    warehouseSpace = p.int(5, 100),
                    usedWarehouseSpace = p.int(6),
                    lastSimulationAt = p.long(7),
                )
                "SETTINGS" -> {
                    shift = runCatching { ShiftMode.valueOf(p.text(1, ShiftMode.DAY_12H.name)) }
                        .getOrDefault(ShiftMode.DAY_12H)
                    boostTokens = p.int(2, 2)
                    snackUntil = p.long(3)
                    lastDaily = p.long(4, -1L)
                }
                "EXP" -> expansion = ExpansionSave(
                    specialty = p.text(1, "generalista"),
                    companySkills = decodeSet(p.text(2)),
                    playerSkills = decodeSet(p.text(3)),
                    gachaTickets = p.int(4, 5),
                    pityEpic = p.int(5),
                    pityLegendary = p.int(6),
                    ownedSkins = decodeSet(p.text(7)).ifEmpty { setOf("operador_padrao") },
                    equippedSkin = p.text(8, "operador_padrao"),
                    ownedCharacters = decodeSet(p.text(9)),
                    equippedCharacter = p.text(10).ifBlank { null },
                    tools = decodeMap(p.text(11)).ifEmpty {
                        mapOf("broca_madeira" to 2, "ferramenta_soldada" to 2, "fresa_hss" to 1)
                    },
                    premiumMachines = decodeSet(p.text(12)),
                    playerXp = p.long(13),
                )
                "PROFILE" -> profile = PlayerProfileSave(
                    name = p.text(1, "Dono da Oficina"),
                    bodyType = p.text(2, "Padrão"),
                    skinTone = p.text(3, "Médio"),
                    hair = p.text(4, "Curto"),
                    hairColor = p.text(5, "Castanho"),
                    uniform = p.text(6, "Azul industrial"),
                    helmet = p.bool(7, true),
                    accessory = p.text(8, "Nenhum"),
                )
                "M" -> machines += MachineSave(
                    id = p.text(1),
                    machineType = p.text(2),
                    level = p.int(3, 1),
                    condition = p.int(4, 1000),
                    accumulatedWorkMinutes = p.long(5),
                    installed = p.bool(6, true),
                    gridX = p.int(7),
                    gridY = p.int(8),
                )
                "E" -> employees += EmployeeSave(
                    id = p.text(1),
                    name = p.text(2),
                    specialty = p.text(3),
                    skillLevel = p.int(4, 1),
                    experience = p.long(5),
                    salaryCents = p.long(6, 245_000L),
                    morale = p.int(7, 80),
                    trait = p.text(8, "Cuidadoso"),
                    assignedMachineId = p.text(9).ifBlank { null },
                    legendaryCode = p.text(10).ifBlank { null },
                    fatigue = p.int(11),
                    restingUntil = p.long(12),
                )
                "C" -> contracts += ContractSave(
                    id = p.text(1),
                    clientName = p.text(2),
                    type = p.text(3),
                    quantity = p.int(4, 1),
                    completedQuantity = p.int(5),
                    productionProgressMilli = p.long(6),
                    difficulty = p.int(7, 1),
                    requiredQuality = p.int(8, 50),
                    rewardCents = p.long(9),
                    penaltyCents = p.long(10),
                    reputationReward = p.int(11),
                    reputationPenalty = p.int(12),
                    generatedAt = p.long(13),
                    startedAt = p.long(14),
                    deadlineAt = p.long(15),
                    status = p.text(16, "AVAILABLE"),
                    rewardPaid = p.bool(17),
                )
                "CARGO" -> cargo += ProductionCargoSave(
                    id = p.text(1),
                    valueCents = p.long(2),
                    unitsMilli = p.long(3),
                    cycles = p.long(4, 1),
                    createdAt = p.long(5),
                    deliveredAt = p.long(6),
                )
                "F" -> finances += FinanceSave(
                    id = p.text(1),
                    type = p.text(2),
                    category = p.text(3),
                    amountCents = p.long(4),
                    description = p.text(5),
                    createdAt = p.long(6),
                )
                "G" -> goals += GoalSave(
                    id = p.text(1),
                    title = p.text(2),
                    target = p.int(3),
                    rewardCents = p.long(4),
                    claimed = p.bool(5),
                )
            }
        }

        GameSave(
            schemaVersion = version.coerceAtLeast(2),
            company = company ?: return null,
            machines = machines,
            employees = employees,
            contracts = contracts,
            cargo = cargo,
            finances = finances,
            goals = goals,
            expansion = expansion,
            profile = profile,
            shiftMode = shift,
            boostTokens = boostTokens.coerceAtLeast(0),
            snackUntil = snackUntil,
            lastDailyBonusDay = lastDaily,
        )
    }.getOrNull()

    private fun StringBuilder.row(vararg values: Any?) {
        append(values.joinToString("|") { escape(it?.toString().orEmpty()) })
        append('\n')
    }

    private fun escape(value: String): String = value
        .replace("%", "%25")
        .replace("|", "%7C")
        .replace("\n", "%0A")
        .replace("\r", "%0D")
        .replace(";", "%3B")
        .replace("=", "%3D")

    private fun unescape(value: String): String = value
        .replace("%0D", "\r")
        .replace("%0A", "\n")
        .replace("%7C", "|")
        .replace("%3B", ";")
        .replace("%3D", "=")
        .replace("%25", "%")

    private fun encodeSet(values: Set<String>): String =
        values.joinToString(";") { escape(it) }

    private fun decodeSet(value: String): Set<String> =
        if (value.isBlank()) emptySet() else value.split(';').map(::unescape).filter { it.isNotBlank() }.toSet()

    private fun encodeMap(values: Map<String, Int>): String =
        values.entries.joinToString(";") { "${escape(it.key)}=${it.value}" }

    private fun decodeMap(value: String): Map<String, Int> =
        if (value.isBlank()) emptyMap() else value.split(';').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size != 2) null else parts[1].toIntOrNull()?.let { count -> unescape(parts[0]) to count }
        }.toMap()

    private fun List<String>.text(index: Int, default: String = ""): String =
        getOrNull(index) ?: default

    private fun List<String>.int(index: Int, default: Int = 0): Int =
        getOrNull(index)?.toIntOrNull() ?: default

    private fun List<String>.long(index: Int, default: Long = 0L): Long =
        getOrNull(index)?.toLongOrNull() ?: default

    private fun List<String>.bool(index: Int, default: Boolean = false): Boolean =
        when (getOrNull(index)?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> default
        }
}
