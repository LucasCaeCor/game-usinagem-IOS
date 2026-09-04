package br.com.usinagemmaster.game.domain

import br.com.usinagemmaster.domain.simulation.ProductionModifiers
import br.com.usinagemmaster.game.model.ExpansionSave

enum class RarityDef(val label: String, val rank: Int) {
    COMMON("Comum", 0),
    RARE("Raro", 1),
    EPIC("Épico", 2),
    LEGENDARY("Lendário", 3),
}

data class SpecialtyDef(
    val code: String,
    val label: String,
    val minLevel: Int,
    val description: String,
)

data class SkillDef(
    val id: String,
    val name: String,
    val description: String,
    val minLevel: Int,
    val prerequisite: String? = null,
)

data class SkinDef(
    val id: String,
    val name: String,
    val minLevel: Int,
    val rarity: RarityDef,
    val description: String,
    val globalSpeedPct: Int = 0,
    val qualityBonus: Int = 0,
    val turningPct: Int = 0,
    val millingPct: Int = 0,
    val cncPct: Int = 0,
    val gachaOnly: Boolean = false,
)

data class CharacterDef(
    val id: String,
    val name: String,
    val minLevel: Int,
    val rarity: RarityDef,
    val description: String,
    val globalSpeedPct: Int = 0,
    val qualityBonus: Int = 0,
    val turningPct: Int = 0,
    val millingPct: Int = 0,
    val cncPct: Int = 0,
)

data class ToolDef(
    val id: String,
    val name: String,
    val rarity: RarityDef,
    val speedMultiplier: Double,
    val qualityBonus: Int,
    val description: String,
)

data class PremiumMachineDef(
    val id: String,
    val name: String,
    val minLevel: Int,
    val priceCents: Long,
    val rarity: RarityDef,
    val description: String,
    val globalSpeedPct: Int = 0,
    val qualityBonus: Int = 0,
    val turningPct: Int = 0,
    val millingPct: Int = 0,
    val drillingPct: Int = 0,
    val grindingPct: Int = 0,
    val weldingPct: Int = 0,
    val cncPct: Int = 0,
)

data class ToolEffect(
    val speedMultiplier: Double = 1.0,
    val qualityBonus: Int = 0,
    val label: String = "Sem ferramenta especial",
)

data class GachaRewardDef(
    val type: String,
    val id: String,
    val title: String,
    val rarity: RarityDef,
)

object GameProgression {
    val specialties = listOf(
        SpecialtyDef("generalista", "Usinagem geral", 1, "Sem foco exclusivo; flexível em todos os contratos."),
        SpecialtyDef("tornearia", "Tornearia", 2, "+14% em tornos mecânicos e CNC."),
        SpecialtyDef("cnc_torno", "CNC • Torno", 5, "+10% em torneamento e +16% em máquinas CNC."),
        SpecialtyDef("fresagem", "Fresagem", 3, "+14% em fresadoras e centros de usinagem."),
        SpecialtyDef("cnc_fresagem", "CNC • Centro de usinagem", 6, "+10% em fresagem e +16% em CNC."),
        SpecialtyDef("solda", "Caldeiraria e solda", 4, "+16% em células de soldagem."),
        SpecialtyDef("retifica", "Retífica e acabamento", 4, "+16% em retificação."),
    )

    val companySkills = listOf(
        SkillDef("lean_i", "Lean I", "+5% de velocidade global.", 2),
        SkillDef("lean_ii", "Lean II", "+7% de velocidade adicional.", 5, "lean_i"),
        SkillDef("qualidade_celula", "Célula de qualidade", "+5 de qualidade em toda produção.", 3),
        SkillDef("ferramentaria", "Ferramentaria interna", "+4% de produtividade e uso avançado de ferramentas.", 4),
        SkillDef("eficiencia_energetica", "Eficiência energética", "Reduz consumo elétrico em 12%.", 5),
        SkillDef("automacao_cnc", "Automação CNC", "+15% em máquinas CNC.", 7, "lean_i"),
        SkillDef("comercial", "Engenharia comercial", "Libera contratos de requisitos mais altos mais cedo.", 6),
        SkillDef("gemeo_digital", "Gêmeo digital", "+8% velocidade e +3 qualidade.", 10, "automacao_cnc"),
    )

    val playerSkills = listOf(
        SkillDef("setup_rapido", "Setup rápido", "+4% de produção quando o personagem está na empresa.", 1),
        SkillDef("metrologia", "Metrologia", "+4 de qualidade.", 2),
        SkillDef("programacao_cnc", "Programação CNC", "+8% em CNC.", 4, "setup_rapido"),
        SkillDef("lideranca", "Liderança", "+3% global.", 5),
        SkillDef("negociacao", "Negociação", "Aumenta o valor do personagem no mercado conectado.", 6),
        SkillDef("mestre_processo", "Mestre de processo", "+6% global e +4 qualidade.", 9, "metrologia"),
    )

    val skins = listOf(
        SkinDef("operador_padrao", "Operador padrão", 1, RarityDef.COMMON, "Visual inicial sem bônus."),
        SkinDef("princesa", "Princesa da Usinagem", 3, RarityDef.RARE, "Coroa, vestido industrial e cabelo longo. +4 qualidade.", qualityBonus = 4),
        SkinDef("pinoquio", "Pinóquio Narigudo", 5, RarityDef.RARE, "Olho clínico para medida. +2% velocidade e +3 qualidade.", globalSpeedPct = 2, qualityBonus = 3),
        SkinDef("tatuzao", "Tatuzão", 7, RarityDef.EPIC, "Especialista de torno. +10% em torneamento.", turningPct = 10, gachaOnly = true),
        SkinDef("magrao", "Magrão Alto", 9, RarityDef.EPIC, "Alcance e logística melhores. +6% global.", globalSpeedPct = 6, gachaOnly = true),
        SkinDef("kendao", "Kendão", 12, RarityDef.LEGENDARY, "Mestre dos centros de usinagem. +12% fresagem e +8% CNC.", millingPct = 12, cncPct = 8, gachaOnly = true),
        SkinDef("princesa_dourada", "Princesa Dourada", 15, RarityDef.LEGENDARY, "Skin lendária: +8 qualidade e +5% global.", globalSpeedPct = 5, qualityBonus = 8, gachaOnly = true),
    )

    val characters = listOf(
        CharacterDef("operador_multitarefa", "Operador Multitarefa", 1, RarityDef.COMMON, "+2% de produção global.", globalSpeedPct = 2),
        CharacterDef("torneiro_junior", "Torneiro Júnior", 2, RarityDef.COMMON, "+5% em torneamento.", turningPct = 5),
        CharacterDef("cuca_aprendiz", "Cuca • Aprendiz de Setup", 2, RarityDef.RARE, "+3% de produção global.", globalSpeedPct = 3),
        CharacterDef("fresadora_agil", "Fresadora Ágil", 3, RarityDef.RARE, "+7% em fresagem.", millingPct = 7),
        CharacterDef("controle_qualidade", "Controle de Qualidade", 4, RarityDef.RARE, "+4 de qualidade.", qualityBonus = 4),
        CharacterDef("mestre_torneiro", "Mestre Torneiro", 5, RarityDef.EPIC, "+12% em torneamento. Prestígio: somente roleta, pity, metas e eventos.", turningPct = 12),
        CharacterDef("programadora_cnc", "Programadora CNC", 6, RarityDef.EPIC, "+10% CNC e +3 qualidade. Prestígio.", qualityBonus = 3, cncPct = 10),
        CharacterDef("inspetor_zero", "Inspetor Zero", 7, RarityDef.EPIC, "+8 de qualidade. Prestígio.", qualityBonus = 8),
        CharacterDef("mestre_5_eixos", "Mestre dos 5 Eixos", 10, RarityDef.LEGENDARY, "+8% global, +10% fresagem e +10% CNC. Prestígio.", globalSpeedPct = 8, millingPct = 10, cncPct = 10),
        CharacterDef("lenda_chao_fabrica", "Lenda do Chão de Fábrica", 14, RarityDef.LEGENDARY, "+12% global e +5 qualidade. Prestígio.", globalSpeedPct = 12, qualityBonus = 5),
    )

    val tools = listOf(
        ToolDef("broca_madeira", "Broca de madeira", RarityDef.COMMON, 1.02, -2, "Improvisada: +2% velocidade, -2 qualidade."),
        ToolDef("ferramenta_soldada", "Ferramenta soldada", RarityDef.COMMON, 1.04, 0, "+4% velocidade."),
        ToolDef("fresa_hss", "Fresa de aço rápido (HSS)", RarityDef.COMMON, 1.06, 2, "+6% velocidade e +2 qualidade."),
        ToolDef("broca_carbeto", "Broca de metal duro", RarityDef.RARE, 1.12, 3, "+12% velocidade e +3 qualidade."),
        ToolDef("fresa_alto_avanco", "Fresa de alto avanço", RarityDef.EPIC, 1.25, 2, "+25% velocidade e +2 qualidade."),
        ToolDef("pastilha_cbn", "Pastilha CBN", RarityDef.LEGENDARY, 1.15, 10, "+15% velocidade e +10 qualidade."),
        ToolDef("fresa_pcd", "Fresa PCD", RarityDef.LEGENDARY, 1.20, 12, "+20% velocidade e +12 qualidade."),
    )

    val premiumMachines = listOf(
        PremiumMachineDef("torno_hyper", "Torno CNC Hyper X", 6, 25_000_000L, RarityDef.EPIC, "Célula premium: +18% torno e +8% CNC.", turningPct = 18, cncPct = 8),
        PremiumMachineDef("centro_5x_titan", "Centro 5 Eixos TITAN", 9, 55_000_000L, RarityDef.LEGENDARY, "+22% fresagem, +12% CNC e +3 qualidade.", millingPct = 22, cncPct = 12, qualityBonus = 3),
        PremiumMachineDef("celula_robotica", "Célula Robótica de Produção", 12, 90_000_000L, RarityDef.LEGENDARY, "+12% global e +5% CNC.", globalSpeedPct = 12, cncPct = 5),
        PremiumMachineDef("retifica_ultra", "Retífica Ultra Precision", 10, 65_000_000L, RarityDef.LEGENDARY, "+25% retífica e +7 qualidade.", grindingPct = 25, qualityBonus = 7),
        PremiumMachineDef("solda_omega", "Robô de Solda Ômega", 8, 45_000_000L, RarityDef.EPIC, "+24% solda.", weldingPct = 24),
    )

    fun companySkillPoints(level: Int, owned: Set<String>): Int =
        (level / 2 - owned.size).coerceAtLeast(0)

    data class XpProgress(val level: Int, val current: Long, val needed: Long, val total: Long) {
        val fraction: Float get() =
            if (needed <= 0L) 1f else (current.toDouble() / needed.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun playerXpNeededForLevel(level: Int): Long {
        val l = level.coerceAtLeast(1).toLong()
        return 600L + (l - 1L) * 300L + (l - 1L) * (l - 1L) * 45L
    }

    fun playerProgress(totalXp: Long): XpProgress {
        var remaining = totalXp.coerceAtLeast(0L)
        var level = 1
        while (level < 100) {
            val needed = playerXpNeededForLevel(level)
            if (remaining < needed) return XpProgress(level, remaining, needed, totalXp.coerceAtLeast(0L))
            remaining -= needed
            level++
        }
        return XpProgress(100, 1, 1, totalXp.coerceAtLeast(0L))
    }

    fun playerSkillPoints(companyLevel: Int, playerXp: Long, owned: Set<String>): Int {
        val playerLevel = playerProgress(playerXp).level
        return (1 + playerLevel / 2 + companyLevel / 6 - owned.size).coerceAtLeast(0)
    }

    fun characterXpForContract(difficulty: Int, quantity: Int, requiredQuality: Int): Long =
        90L + difficulty.coerceIn(1, 10) * 75L + quantity.coerceIn(1, 2_000) / 4L +
            requiredQuality.coerceIn(0, 100) * 2L

    fun canUnlock(skill: SkillDef, level: Int, owned: Set<String>): Boolean =
        level >= skill.minLevel && (skill.prerequisite == null || skill.prerequisite in owned)

    fun contractGate(difficulty: Int): Triple<Int, Int, Boolean> = when (difficulty.coerceIn(1, 5)) {
        1 -> Triple(1, 0, false)
        2 -> Triple(2, 0, false)
        3 -> Triple(4, 1, false)
        4 -> Triple(7, 2, false)
        else -> Triple(10, 3, true)
    }

    fun toolEffect(expansion: ExpansionSave, contractId: String): ToolEffect {
        val toolId = expansion.contractTools[contractId] ?: return ToolEffect()
        val definition = tools.firstOrNull { it.id == toolId } ?: return ToolEffect()
        return ToolEffect(definition.speedMultiplier, definition.qualityBonus, definition.name)
    }

    fun unlockedLevelSkins(level: Int): Set<String> =
        skins.filter { !it.gachaOnly && it.minLevel <= level }.map { it.id }.toSet()

    fun modifiers(expansion: ExpansionSave): ProductionModifiers {
        var speed = 1.0
        var quality = 0
        var energy = 1.0
        var turning = 1.0
        var milling = 1.0
        var drilling = 1.0
        var grinding = 1.0
        var welding = 1.0
        var cnc = 1.0

        when (expansion.specialty) {
            "tornearia" -> turning *= 1.14
            "cnc_torno" -> { turning *= 1.10; cnc *= 1.16 }
            "fresagem" -> milling *= 1.14
            "cnc_fresagem" -> { milling *= 1.10; cnc *= 1.16 }
            "solda" -> welding *= 1.16
            "retifica" -> grinding *= 1.16
        }

        if ("lean_i" in expansion.companySkills) speed += .05
        if ("lean_ii" in expansion.companySkills) speed += .07
        if ("qualidade_celula" in expansion.companySkills) quality += 5
        if ("ferramentaria" in expansion.companySkills) speed += .04
        if ("eficiencia_energetica" in expansion.companySkills) energy *= .88
        if ("automacao_cnc" in expansion.companySkills) cnc *= 1.15
        if ("gemeo_digital" in expansion.companySkills) { speed += .08; quality += 3 }

        if ("setup_rapido" in expansion.playerSkills) speed += .04
        if ("metrologia" in expansion.playerSkills) quality += 4
        if ("programacao_cnc" in expansion.playerSkills) cnc *= 1.08
        if ("lideranca" in expansion.playerSkills) speed += .03
        if ("mestre_processo" in expansion.playerSkills) { speed += .06; quality += 4 }

        skins.firstOrNull { it.id == expansion.equippedSkin }?.let {
            speed += it.globalSpeedPct / 100.0
            quality += it.qualityBonus
            turning *= 1.0 + it.turningPct / 100.0
            milling *= 1.0 + it.millingPct / 100.0
            cnc *= 1.0 + it.cncPct / 100.0
        }

        characters.firstOrNull {
            it.id == expansion.equippedCharacter && it.id in expansion.ownedCharacters
        }?.let {
            speed += it.globalSpeedPct / 100.0
            quality += it.qualityBonus
            turning *= 1.0 + it.turningPct / 100.0
            milling *= 1.0 + it.millingPct / 100.0
            cnc *= 1.0 + it.cncPct / 100.0
        }

        expansion.premiumMachines.mapNotNull { id ->
            premiumMachines.firstOrNull { it.id == id }
        }.forEach {
            speed += it.globalSpeedPct / 100.0
            quality += it.qualityBonus
            turning *= 1.0 + it.turningPct / 100.0
            milling *= 1.0 + it.millingPct / 100.0
            drilling *= 1.0 + it.drillingPct / 100.0
            grinding *= 1.0 + it.grindingPct / 100.0
            welding *= 1.0 + it.weldingPct / 100.0
            cnc *= 1.0 + it.cncPct / 100.0
        }

        if (expansion.remoteHireEndsAt > currentTimeMillis() && expansion.remoteHireBoostPct > 0) {
            speed += expansion.remoteHireBoostPct.coerceAtMost(25) / 100.0
        }

        return ProductionModifiers(
            globalSpeedMultiplier = speed.coerceAtMost(2.25),
            energyMultiplier = energy.coerceIn(.65, 1.0),
            qualityBonus = quality.coerceAtMost(25),
            turningMultiplier = turning.coerceAtMost(1.75),
            millingMultiplier = milling.coerceAtMost(1.75),
            drillingMultiplier = drilling.coerceAtMost(1.75),
            grindingMultiplier = grinding.coerceAtMost(1.75),
            weldingMultiplier = welding.coerceAtMost(1.75),
            cncMultiplier = cnc.coerceAtMost(1.85),
        )
    }
}
