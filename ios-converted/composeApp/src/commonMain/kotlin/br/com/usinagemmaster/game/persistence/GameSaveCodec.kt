package br.com.usinagemmaster.game.persistence

import br.com.usinagemmaster.game.model.*

/**
 * Codec textual versionado sem dependências extras.
 *
 * Compatibilidade:
 * - lê o save V6 (schema 2);
 * - grava schema 3;
 * - campos novos possuem defaults seguros;
 * - o mesmo SAVE_KEY é mantido pelo GameStore.
 */
object GameSaveCodec {
    fun encode(save: GameSave): String = buildString {
        row("VERSION", 3)
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
            "UX3",
            save.uiSettings.soundEnabled,
            save.uiSettings.hapticsEnabled,
            save.uiSettings.legendarySpeechEnabled,
            save.uiSettings.legendarySpeechSeconds,
        )
        row(
            "WORKFORCE3",
            save.workforce.idleEmployeeId ?: "",
            save.workforce.idleSinceAt,
            save.workforce.idleUntilAt,
            save.workforce.nextIdleCheckAt,
        )
        row(
            "MINIGAME3",
            save.lastMinigameAt,
            save.bestMinigameScore,
        )
        row(
            "EXP3",
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
            encodeIntMap(save.expansion.tools),
            encodeStringMap(save.expansion.contractTools),
            encodeSet(save.expansion.premiumMachines),
            save.expansion.playerXp,
            save.expansion.lastDailyTicketDay,
        )
        row(
            "PROFILE3",
            save.profile.name,
            save.profile.gender,
            save.profile.skinStyle,
            save.profile.bodyType,
            save.profile.skinTone,
            save.profile.hairStyle,
            save.profile.hairColor,
            save.profile.uniformColor,
            save.profile.helmetColor,
            save.profile.accessory,
            save.profile.onboardingComplete,
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
                "C3", it.id, it.clientName, it.type, it.quantity, it.completedQuantity,
                it.productionProgressMilli, it.difficulty, it.requiredQuality,
                it.rewardCents, it.penaltyCents, it.reputationReward,
                it.reputationPenalty, it.generatedAt, it.startedAt, it.deadlineAt,
                it.status, it.rewardPaid, it.special,
            )
        }
        save.cargo.takeLast(300).forEach {
            row("CARGO", it.id, it.valueCents, it.unitsMilli, it.cycles, it.createdAt, it.deliveredAt)
        }
        save.finances.takeLast(300).forEach {
            row("F", it.id, it.type, it.category, it.amountCents, it.description, it.createdAt)
        }
        save.goals.forEach {
            row("G3", it.id, it.title, it.target, it.rewardCents, it.ticketReward, it.claimed)
        }
    }

    fun decode(raw: String): GameSave? = runCatching {
        var version = 2
        var company: CompanySave? = null
        var shift = ShiftMode.DAY_12H
        var boostTokens = 2
        var snackUntil = 0L
        var lastDaily = -1L
        var lastMinigameAt = 0L
        var bestMinigameScore = 0.0
        var expansion = ExpansionSave()
        var profile = PlayerProfileSave()
        var uiSettings = UiSettingsSave()
        var workforce = WorkforceSave()

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
                    shift = runCatching {
                        ShiftMode.valueOf(p.text(1, ShiftMode.DAY_12H.name))
                    }.getOrDefault(ShiftMode.DAY_12H)
                    boostTokens = p.int(2, 2)
                    snackUntil = p.long(3)
                    lastDaily = p.long(4, -1L)
                }
                "UX3" -> uiSettings = UiSettingsSave(
                    soundEnabled = p.bool(1, true),
                    hapticsEnabled = p.bool(2, true),
                    legendarySpeechEnabled = p.bool(3, true),
                    legendarySpeechSeconds = p.int(4, 5).coerceIn(2, 12),
                )
                "WORKFORCE3" -> workforce = WorkforceSave(
                    idleEmployeeId = p.text(1).ifBlank { null },
                    idleSinceAt = p.long(2),
                    idleUntilAt = p.long(3),
                    nextIdleCheckAt = p.long(4),
                )
                "MINIGAME3" -> {
                    lastMinigameAt = p.long(1)
                    bestMinigameScore = p.double(2).coerceIn(0.0, 1.0)
                }
                // V6 expansion row.
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
                    tools = decodeIntMap(p.text(11)).ifEmpty(::starterTools),
                    premiumMachines = decodeSet(p.text(12)),
                    playerXp = p.long(13),
                )
                "EXP3" -> expansion = ExpansionSave(
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
                    tools = decodeIntMap(p.text(11)).ifEmpty(::starterTools),
                    contractTools = decodeStringMap(p.text(12)),
                    premiumMachines = decodeSet(p.text(13)),
                    playerXp = p.long(14),
                    lastDailyTicketDay = p.long(15, -1L),
                )
                // V6 profile row: Portuguese display values are normalized to stable codes.
                "PROFILE" -> profile = PlayerProfileSave(
                    name = p.text(1, "Dono da Oficina"),
                    bodyType = normalizeBody(p.text(2)),
                    skinTone = normalizeSkinTone(p.text(3)),
                    hairStyle = normalizeHair(p.text(4)),
                    hairColor = normalizeHairColor(p.text(5)),
                    uniformColor = normalizeUniform(p.text(6)),
                    helmetColor = if (p.bool(7, true)) "YELLOW" else "NONE",
                    accessory = normalizeAccessory(p.text(8)),
                    onboardingComplete = true,
                )
                "PROFILE3" -> profile = PlayerProfileSave(
                    name = p.text(1, "Dono da Oficina"),
                    gender = p.text(2, "MALE"),
                    skinStyle = p.text(3, "WORKSHOP"),
                    bodyType = p.text(4, "STANDARD"),
                    skinTone = p.text(5, "MEDIUM"),
                    hairStyle = p.text(6, "SHORT"),
                    hairColor = p.text(7, "DARK"),
                    uniformColor = p.text(8, "NAVY"),
                    helmetColor = p.text(9, "YELLOW"),
                    accessory = p.text(10, "NONE"),
                    onboardingComplete = p.bool(11),
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
                    fatigue = p.double(11),
                    restingUntil = p.long(12),
                )
                // V6 contract row.
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
                "C3" -> contracts += ContractSave(
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
                    special = p.bool(18),
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
                    ticketReward = 0,
                    claimed = p.bool(5),
                )
                "G3" -> goals += GoalSave(
                    id = p.text(1),
                    title = p.text(2),
                    target = p.int(3),
                    rewardCents = p.long(4),
                    ticketReward = p.int(5),
                    claimed = p.bool(6),
                )
            }
        }

        GameSave(
            schemaVersion = maxOf(3, version),
            company = company ?: return null,
            machines = machines,
            employees = employees,
            contracts = contracts,
            cargo = cargo,
            finances = finances,
            goals = goals,
            expansion = expansion,
            profile = profile,
            uiSettings = uiSettings,
            workforce = workforce,
            shiftMode = shift,
            boostTokens = boostTokens.coerceAtLeast(0),
            snackUntil = snackUntil,
            lastDailyBonusDay = lastDaily,
            lastMinigameAt = lastMinigameAt,
            bestMinigameScore = bestMinigameScore,
        )
    }.getOrNull()

    private fun starterTools() = mapOf(
        "broca_madeira" to 2,
        "ferramenta_soldada" to 2,
        "fresa_hss" to 1,
    )

    private fun normalizeBody(value: String) = when (value.uppercase()) {
        "MAGRO", "SLIM" -> "SLIM"
        "FORTE", "STRONG" -> "STRONG"
        else -> "STANDARD"
    }

    private fun normalizeSkinTone(value: String) = when (value.uppercase()) {
        "CLARO", "LIGHT" -> "LIGHT"
        "BRONZEADO", "TAN" -> "TAN"
        "ESCURO", "DARK" -> "DARK"
        else -> "MEDIUM"
    }

    private fun normalizeHair(value: String) = when (value.uppercase()) {
        "RASPADINHO", "BUZZ" -> "BUZZ"
        "MOICANO", "MOHAWK" -> "MOHAWK"
        "LONGO", "LONG" -> "LONG"
        "RABO DE CAVALO", "PONYTAIL" -> "PONYTAIL"
        "CACHEADO", "CURLY" -> "CURLY"
        "CARECA", "BALD" -> "BALD"
        else -> "SHORT"
    }

    private fun normalizeHairColor(value: String) = when (value.uppercase()) {
        "CASTANHO", "BROWN" -> "BROWN"
        "LOIRO", "BLONDE" -> "BLONDE"
        "CINZA", "GRAY" -> "GRAY"
        else -> "DARK"
    }

    private fun normalizeUniform(value: String) = when (value.uppercase()) {
        "AZUL", "BLUE" -> "BLUE"
        "GRAFITE", "GRAPHITE" -> "GRAPHITE"
        "VERDE", "GREEN" -> "GREEN"
        "LARANJA", "ORANGE" -> "ORANGE"
        else -> "NAVY"
    }

    private fun normalizeAccessory(value: String) = when (value.uppercase()) {
        "ÓCULOS", "OCULOS", "GLASSES" -> "GLASSES"
        "HEADSET" -> "HEADSET"
        else -> "NONE"
    }

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
        if (value.isBlank()) emptySet()
        else value.split(';').map(::unescape).filter { it.isNotBlank() }.toSet()

    private fun encodeIntMap(values: Map<String, Int>): String =
        values.entries.joinToString(";") { "${escape(it.key)}=${it.value}" }

    private fun decodeIntMap(value: String): Map<String, Int> =
        if (value.isBlank()) emptyMap() else value.split(';').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size != 2) null
            else parts[1].toIntOrNull()?.let { count -> unescape(parts[0]) to count }
        }.toMap()

    private fun encodeStringMap(values: Map<String, String>): String =
        values.entries.joinToString(";") { "${escape(it.key)}=${escape(it.value)}" }

    private fun decodeStringMap(value: String): Map<String, String> =
        if (value.isBlank()) emptyMap() else value.split(';').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size != 2) null else unescape(parts[0]) to unescape(parts[1])
        }.toMap()

    private fun List<String>.text(index: Int, default: String = ""): String =
        getOrNull(index) ?: default

    private fun List<String>.int(index: Int, default: Int = 0): Int =
        getOrNull(index)?.toIntOrNull() ?: default

    private fun List<String>.long(index: Int, default: Long = 0L): Long =
        getOrNull(index)?.toLongOrNull() ?: default

    private fun List<String>.double(index: Int, default: Double = 0.0): Double =
        getOrNull(index)?.toDoubleOrNull() ?: default

    private fun List<String>.bool(index: Int, default: Boolean = false): Boolean =
        when (getOrNull(index)?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> default
        }
}
