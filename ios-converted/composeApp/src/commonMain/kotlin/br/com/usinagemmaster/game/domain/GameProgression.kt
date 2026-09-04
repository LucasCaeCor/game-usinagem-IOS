package br.com.usinagemmaster.game.domain

import br.com.usinagemmaster.domain.simulation.ProductionModifiers
import br.com.usinagemmaster.game.model.ExpansionSave

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
    val rarity: String,
    val description: String,
    val globalSpeedPct: Int = 0,
    val qualityBonus: Int = 0,
    val turningPct: Int = 0,
    val millingPct: Int = 0,
    val cncPct: Int = 0,
)

data class CharacterDef(
    val id: String,
    val name: String,
    val minLevel: Int,
    val rarity: String,
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
    val rarity: String,
    val speedMultiplier: Double,
    val qualityBonus: Int,
    val description: String,
)

object GameProgression {
    val specialties = listOf(
        SpecialtyDef("generalista", "Usinagem geral", 1, "Flexível em todos os contratos."),
        SpecialtyDef("tornearia", "Tornearia", 2, "+14% em tornos."),
        SpecialtyDef("cnc_torno", "CNC • Torno", 5, "+10% torneamento e +16% CNC."),
        SpecialtyDef("fresagem", "Fresagem", 3, "+14% em fresadoras e centros de usinagem."),
        SpecialtyDef("cnc_fresagem", "CNC • Centro de usinagem", 6, "+10% fresagem e +16% CNC."),
        SpecialtyDef("solda", "Caldeiraria e solda", 4, "+16% em células de soldagem."),
        SpecialtyDef("retifica", "Retífica e acabamento", 4, "+16% em retificação."),
    )

    val companySkills = listOf(
        SkillDef("lean_i", "Lean I", "+5% de velocidade global.", 2),
        SkillDef("lean_ii", "Lean II", "+7% de velocidade adicional.", 5, "lean_i"),
        SkillDef("qualidade_celula", "Célula de qualidade", "+5 de qualidade.", 3),
        SkillDef("ferramentaria", "Ferramentaria interna", "+4% de produtividade.", 4),
        SkillDef("eficiencia_energetica", "Eficiência energética", "Reduz energia em 12%.", 5),
        SkillDef("automacao_cnc", "Automação CNC", "+15% em máquinas CNC.", 7, "lean_i"),
        SkillDef("comercial", "Engenharia comercial", "Acesso comercial avançado.", 6),
        SkillDef("gemeo_digital", "Gêmeo digital", "+8% velocidade e +3 qualidade.", 10, "automacao_cnc"),
    )

    val playerSkills = listOf(
        SkillDef("setup_rapido", "Setup rápido", "+4% de produção.", 1),
        SkillDef("metrologia", "Metrologia", "+4 de qualidade.", 2),
        SkillDef("programacao_cnc", "Programação CNC", "+8% em CNC.", 4, "setup_rapido"),
        SkillDef("lideranca", "Liderança", "+3% global.", 5),
        SkillDef("negociacao", "Negociação", "Valoriza o personagem no mercado.", 6),
        SkillDef("mestre_processo", "Mestre de processo", "+6% global e +4 qualidade.", 9, "metrologia"),
    )

    val skins = listOf(
        SkinDef("operador_padrao", "Operador padrão", 1, "Comum", "Visual inicial."),
        SkinDef("princesa", "Princesa da Usinagem", 3, "Raro", "Coroa, vestido industrial e cabelo longo. +4 qualidade.", qualityBonus = 4),
        SkinDef("pinoquio", "Pinóquio Narigudo", 5, "Raro", "+2% global e +3 qualidade.", globalSpeedPct = 2, qualityBonus = 3),
        SkinDef("tatuzao", "Tatuzão", 7, "Épico", "+10% em torneamento.", turningPct = 10),
        SkinDef("magrao", "Magrão Alto", 9, "Épico", "+6% global.", globalSpeedPct = 6),
        SkinDef("kendao", "Kendão", 12, "Lendário", "+12% fresagem e +8% CNC.", millingPct = 12, cncPct = 8),
        SkinDef("princesa_dourada", "Princesa Dourada", 15, "Lendário", "+5% global e +8 qualidade.", globalSpeedPct = 5, qualityBonus = 8),
    )

    val characters = listOf(
        CharacterDef("cuca_aprendiz", "Cuca • Aprendiz de Setup", 2, "Raro", "+3% global.", globalSpeedPct = 3),
        CharacterDef("mestre_torneiro", "Mestre Torneiro", 5, "Épico", "+12% torneamento.", turningPct = 12),
        CharacterDef("programadora_cnc", "Programadora CNC", 6, "Épico", "+10% CNC e +3 qualidade.", qualityBonus = 3, cncPct = 10),
        CharacterDef("inspetor_zero", "Inspetor Zero", 7, "Épico", "+8 qualidade.", qualityBonus = 8),
        CharacterDef("mestre_5_eixos", "Mestre dos 5 Eixos", 10, "Lendário", "+8% global, +10% fresagem e +10% CNC.", globalSpeedPct = 8, millingPct = 10, cncPct = 10),
        CharacterDef("lenda_chao_fabrica", "Lenda do Chão de Fábrica", 14, "Lendário", "+12% global e +5 qualidade.", globalSpeedPct = 12, qualityBonus = 5),
    )

    val tools = listOf(
        ToolDef("broca_madeira", "Broca de madeira", "Comum", 1.02, -2, "+2% velocidade, -2 qualidade."),
        ToolDef("ferramenta_soldada", "Ferramenta soldada", "Comum", 1.04, 0, "+4% velocidade."),
        ToolDef("fresa_hss", "Fresa HSS", "Comum", 1.06, 2, "+6% velocidade e +2 qualidade."),
        ToolDef("broca_carbeto", "Broca de metal duro", "Raro", 1.12, 3, "+12% velocidade e +3 qualidade."),
        ToolDef("fresa_alto_avanco", "Fresa de alto avanço", "Épico", 1.25, 2, "+25% velocidade e +2 qualidade."),
        ToolDef("pastilha_cbn", "Pastilha CBN", "Lendário", 1.15, 10, "+15% velocidade e +10 qualidade."),
        ToolDef("fresa_pcd", "Fresa PCD", "Lendário", 1.20, 12, "+20% velocidade e +12 qualidade."),
    )

    fun companySkillPoints(level: Int, owned: Set<String>): Int =
        (level / 2 - owned.size).coerceAtLeast(0)

    fun playerSkillPoints(level: Int, owned: Set<String>): Int =
        (1 + level / 3 - owned.size).coerceAtLeast(0)

    fun canUnlock(skill: SkillDef, level: Int, owned: Set<String>): Boolean =
        level >= skill.minLevel && (skill.prerequisite == null || skill.prerequisite in owned)

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

        characters.firstOrNull { it.id == expansion.equippedCharacter && it.id in expansion.ownedCharacters }?.let {
            speed += it.globalSpeedPct / 100.0
            quality += it.qualityBonus
            turning *= 1.0 + it.turningPct / 100.0
            milling *= 1.0 + it.millingPct / 100.0
            cnc *= 1.0 + it.cncPct / 100.0
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
