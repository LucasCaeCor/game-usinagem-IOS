package br.com.usinagemmaster.game.domain

import br.com.usinagemmaster.game.model.CareerSave
import br.com.usinagemmaster.game.model.LegendaryMissionSave
import br.com.usinagemmaster.game.model.OwnerWorkBatchSave
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ProductionStage(val label: String) {
    RAW("Matéria-prima"),
    WAITING_MACHINE("Aguardando máquina"),
    MACHINING("Usinando"),
    MACHINED("Lote usinado"),
    WAITING_QC("Levar à Qualidade"),
    QC("Inspeção dimensional"),
    APPROVED("Lote aprovado"),
    REWORK("Retrabalho"),
    PACKING("Embalagem"),
    READY_TO_SHIP("Pronto para expedição"),
    SHIPPED("Expedido"),
    SCRAP("Refugo"),
}

enum class OwnerStation(val label: String) {
    MATERIAL("Matéria-prima"),
    TOOLS("Ferramentaria"),
    QUALITY("Qualidade"),
    PACKING("Embalagem"),
    SHIPPING("Expedição"),
    BREAK_ROOM("Copa"),
}

enum class MinigameKind(val title: String) {
    LATHE("Torneamento dimensional"),
    MILLING("Estratégia de fresagem"),
    DRILLING("Furação e profundidade"),
    GRINDING("Retífica de precisão"),
    CNC("Programa CNC e parâmetros"),
    WELDING("Soldagem e energia"),
    EDM("Eletroerosão"),
    LASER("Corte a laser"),
    PLASMA("Corte a plasma"),
    QUALITY("Metrologia"),
}

data class MinigameResult(
    val score: Float,
    val precision: Float,
    val speed: Float,
    val quality: Float,
    val mistakes: Int = 0,
) {
    val normalizedScore: Float get() = score.coerceIn(0f, 1f)
    val perfect: Boolean get() = normalizedScore >= .94f && mistakes == 0
}

data class MachineMastery(val machineType: String, val xp: Int = 0) {
    val level: Int get() = (1 + xp.coerceAtLeast(0) / 180).coerceIn(1, 20)
    val quantityBonusPct: Int get() = ((level - 1) * 2).coerceAtMost(24)
    val qualityBonus: Int get() = ((level - 1) / 4).coerceAtMost(5)
}

enum class IndustrialSkillBranch(val label: String, val icon: String) {
    OPERATION("Operador", "🧑‍🏭"),
    QUALITY("Qualidade", "📏"),
    PRODUCTION("Produção", "🏭"),
    MANAGEMENT("Gestão", "👥"),
    COMMERCIAL("Comercial", "🤝"),
    HYBRID("Especializações", "⚙️"),
}

data class IndustrialSkillDefinition(
    val id: String,
    val name: String,
    val branch: IndustrialSkillBranch,
    val tier: Int,
    val minCompanyLevel: Int,
    val description: String,
    val prerequisites: Set<String> = emptySet(),
    val cost: Int = 1,
)

enum class ProductionPolicy(val label: String, val description: String) {
    BALANCED("Balanceada", "Equilibra prazo, qualidade e margem."),
    DEADLINE("Priorizar prazo", "+produção automática, pequena perda de qualidade."),
    QUALITY("Priorizar qualidade", "+qualidade, produção um pouco mais lenta."),
    PROFIT("Priorizar margem", "Menor consumo, ritmo mais conservador."),
}

object IndustrialSkillCatalog {
    val all = listOf(
        IndustrialSkillDefinition("mao_firme", "Mão firme", IndustrialSkillBranch.OPERATION, 1, 1, "Melhora leitura e qualidade do trabalho manual."),
        IndustrialSkillDefinition("ritmo_producao", "Ritmo de produção", IndustrialSkillBranch.OPERATION, 2, 2, "Sequências boas rendem mais peças.", setOf("mao_firme")),
        IndustrialSkillDefinition("preparador", "Preparador", IndustrialSkillBranch.OPERATION, 3, 4, "Libera setups e reduz penalidade de parâmetros.", setOf("ritmo_producao")),
        IndustrialSkillDefinition("operador_cnc", "Operador CNC", IndustrialSkillBranch.OPERATION, 4, 6, "Melhora desafios de programação CNC.", setOf("preparador")),
        IndustrialSkillDefinition("mestre_usinagem", "Mestre de usinagem", IndustrialSkillBranch.OPERATION, 5, 10, "Peças perfeitas geram lotes maiores.", setOf("operador_cnc"), 2),

        IndustrialSkillDefinition("olho_treinado", "Olho treinado", IndustrialSkillBranch.QUALITY, 1, 1, "Dica extra na inspeção e bônus automático."),
        IndustrialSkillDefinition("metrologista", "Metrologista", IndustrialSkillBranch.QUALITY, 2, 3, "Medições exigentes e mais qualidade manual.", setOf("olho_treinado")),
        IndustrialSkillDefinition("controle_estatistico", "Controle estatístico", IndustrialSkillBranch.QUALITY, 3, 5, "A equipe detecta tendência de processo.", setOf("metrologista")),
        IndustrialSkillDefinition("zero_defeito", "Zero Defeito", IndustrialSkillBranch.QUALITY, 4, 8, "Operações excelentes toleram pequenas falhas.", setOf("controle_estatistico")),
        IndustrialSkillDefinition("mestre_qualidade", "Mestre da Qualidade", IndustrialSkillBranch.QUALITY, 5, 12, "Grande bônus de qualidade automática.", setOf("zero_defeito"), 2),

        IndustrialSkillDefinition("planejamento", "Planejamento", IndustrialSkillBranch.PRODUCTION, 1, 2, "Organiza filas e aumenta produção automática."),
        IndustrialSkillDefinition("balanceamento", "Balanceamento", IndustrialSkillBranch.PRODUCTION, 2, 4, "Reduz gargalos entre postos.", setOf("planejamento")),
        IndustrialSkillDefinition("producao_celular", "Produção celular", IndustrialSkillBranch.PRODUCTION, 3, 6, "Máquinas próximas cooperam melhor.", setOf("balanceamento")),
        IndustrialSkillDefinition("kanban", "Kanban", IndustrialSkillBranch.PRODUCTION, 4, 8, "Reposição e movimentação mais eficientes.", setOf("producao_celular")),
        IndustrialSkillDefinition("lean_manufacturing", "Lean Manufacturing", IndustrialSkillBranch.PRODUCTION, 5, 11, "Menos desperdício e mais velocidade.", setOf("kanban"), 2),
        IndustrialSkillDefinition("industria_4", "Indústria 4.0", IndustrialSkillBranch.PRODUCTION, 6, 15, "Leitura avançada de eficiência, gargalos e qualidade.", setOf("lean_manufacturing"), 2),

        IndustrialSkillDefinition("primeiro_lider", "Primeiro líder", IndustrialSkillBranch.MANAGEMENT, 1, 2, "Melhora o treinamento da equipe."),
        IndustrialSkillDefinition("lider_equipe", "Líder de equipe", IndustrialSkillBranch.MANAGEMENT, 2, 4, "Operadores trabalham melhor em conjunto.", setOf("primeiro_lider")),
        IndustrialSkillDefinition("plano_carreira", "Plano de carreira", IndustrialSkillBranch.MANAGEMENT, 3, 6, "Funcionários evoluem mais rápido.", setOf("lider_equipe")),
        IndustrialSkillDefinition("supervisor", "Supervisor", IndustrialSkillBranch.MANAGEMENT, 4, 9, "Delegação avançada de postos.", setOf("plano_carreira")),
        IndustrialSkillDefinition("gerente_producao", "Gerente de produção", IndustrialSkillBranch.MANAGEMENT, 5, 12, "Automatiza prioridades e melhora fluxo.", setOf("supervisor")),
        IndustrialSkillDefinition("diretor_industrial", "Diretor industrial", IndustrialSkillBranch.MANAGEMENT, 6, 16, "Libera políticas globais de prazo, qualidade e margem.", setOf("gerente_producao"), 2),

        IndustrialSkillDefinition("clientes_locais", "Clientes locais", IndustrialSkillBranch.COMMERCIAL, 1, 1, "Base comercial da empresa."),
        IndustrialSkillDefinition("boa_reputacao", "Boa reputação", IndustrialSkillBranch.COMMERCIAL, 2, 3, "Bônus de fechamento de contratos.", setOf("clientes_locais")),
        IndustrialSkillDefinition("contratos_recorrentes", "Contratos recorrentes", IndustrialSkillBranch.COMMERCIAL, 3, 5, "Clientes valiosos retornam com mais frequência.", setOf("boa_reputacao")),
        IndustrialSkillDefinition("empresas_nacionais", "Empresas nacionais", IndustrialSkillBranch.COMMERCIAL, 4, 8, "Amplia bônus e contratos de maior escala.", setOf("contratos_recorrentes")),
        IndustrialSkillDefinition("exportacao", "Exportação", IndustrialSkillBranch.COMMERCIAL, 5, 12, "Abre mercado global e maior bonificação.", setOf("empresas_nacionais"), 2),
        IndustrialSkillDefinition("fornecedor_estrategico", "Fornecedor estratégico", IndustrialSkillBranch.COMMERCIAL, 6, 16, "Contratos de alto prestígio e bônus máximo.", setOf("exportacao"), 2),

        IndustrialSkillDefinition("usinagem_precisao", "Usinagem de precisão", IndustrialSkillBranch.HYBRID, 1, 8, "Combina operação e metrologia para lotes premium.", setOf("preparador", "metrologista"), 2),
        IndustrialSkillDefinition("producao_autonoma", "Produção autônoma", IndustrialSkillBranch.HYBRID, 2, 12, "Equipe resolve gargalos sem o dono.", setOf("kanban", "supervisor"), 2),
        IndustrialSkillDefinition("celula_cnc_avancada", "Célula CNC avançada", IndustrialSkillBranch.HYBRID, 3, 15, "Bônus manual e automático em células CNC.", setOf("operador_cnc", "producao_celular"), 2),
    )

    fun byId(id: String): IndustrialSkillDefinition? = all.firstOrNull { it.id == id }

    fun canUnlock(skill: IndustrialSkillDefinition, state: CareerSave, companyLevel: Int): Boolean =
        skill.id !in state.unlockedSkills &&
            companyLevel >= skill.minCompanyLevel &&
            skill.prerequisites.all { it in state.unlockedSkills } &&
            state.availableSkillPoints() >= skill.cost
}

data class MachineMinigameBlueprint(
    val kind: MinigameKind,
    val title: String,
    val goal: String,
    val parameterA: String,
    val parameterB: String,
    val targetA: Float,
    val targetB: Float,
    val toleranceA: Float,
    val toleranceB: Float,
)

object MachineMinigameCatalog {
    fun blueprint(machineType: String, difficulty: Int): MachineMinigameBlueprint {
        val d = difficulty.coerceIn(1, 5)
        val tight = (14f - d * 1.5f).coerceAtLeast(5f)
        val t = machineType.uppercase()
        return when {
            "LATHE" in t || "TORNO" in t -> MachineMinigameBlueprint(MinigameKind.LATHE, "Torno • controle de corte", "Aproxime RPM e avanço da janela ideal sem forçar a ferramenta.", "RPM", "Avanço", 62f, 52f, tight, tight)
            "MILL" in t || "FRESA" in t -> MachineMinigameBlueprint(MinigameKind.MILLING, "Fresagem • estratégia de passe", "Escolha a trajetória e equilibre profundidade e avanço.", "Profundidade", "Avanço", 48f, 58f, tight, tight)
            "DRILL" in t || "FURA" in t -> MachineMinigameBlueprint(MinigameKind.DRILLING, "Furação • ferramenta e profundidade", "Escolha a broca e controle rotação/profundidade.", "Rotação", "Profundidade", 55f, 66f, tight, tight)
            "GRIND" in t || "RETIF" in t -> MachineMinigameBlueprint(MinigameKind.GRINDING, "Retífica • microns finais", "Chegue à medida sem ultrapassar a tolerância.", "Passe final", "Avanço", 44f, 38f, tight * .7f, tight * .7f)
            "CNC" in t -> MachineMinigameBlueprint(MinigameKind.CNC, "CNC • OP10", "Ordene Facear → Furar → Contornar e ajuste parâmetros.", "RPM", "Avanço", 64f, 55f, tight, tight)
            "WELD" in t || "SOLDA" in t -> MachineMinigameBlueprint(MinigameKind.WELDING, "Soldagem • aporte térmico", "Controle energia e velocidade para evitar falta de fusão ou empeno.", "Energia", "Velocidade", 57f, 51f, tight, tight)
            "EDM" in t || "EROS" in t -> MachineMinigameBlueprint(MinigameKind.EDM, "EDM • descarga", "Equilibre descarga e gap para ganhar precisão sem instabilidade.", "Descarga", "Gap", 49f, 60f, tight, tight)
            "LASER" in t -> MachineMinigameBlueprint(MinigameKind.LASER, "Laser • foco e gás", "Acerte foco e velocidade para um corte limpo.", "Foco", "Velocidade", 53f, 63f, tight, tight)
            "PLASMA" in t -> MachineMinigameBlueprint(MinigameKind.PLASMA, "Plasma • arco e velocidade", "Equilibre corrente e avanço para reduzir rebarba.", "Corrente", "Velocidade", 61f, 58f, tight, tight)
            else -> MachineMinigameBlueprint(MinigameKind.MILLING, "Operação de usinagem", "Ajuste o processo dentro da janela ideal.", "Parâmetro A", "Parâmetro B", 55f, 55f, tight, tight)
        }
    }
}

data class LegendaryEmployeeDefinition(
    val code: String,
    val name: String,
    val specialty: String,
    val skillLevel: Int,
    val morale: Int,
    val salaryCents: Long,
    val trait: String,
    val unlockLevel: Int,
    val visualScale: Float = 1f,
    val walkSpeedMultiplier: Float = 1f,
    val description: String,
)

object LegendaryEmployeeCatalog {
    val all = listOf(
        LegendaryEmployeeDefinition("tatu_banhado", "Tatu do Banhado", "TURNER", 7, 84, 465_000L, "Casca grossa", 1, 1.03f, .94f, "Torneiro veterano. Mantém ritmo forte mesmo em máquina desgastada."),
        LegendaryEmployeeDefinition("kendao", "Kendão", "MILLER", 8, 78, 525_000L, "Mão pesada", 1, 1.08f, 1f, "Fresador rápido e agressivo, ótimo para lotes médios."),
        LegendaryEmployeeDefinition("chupa_engole", "Chupa Engole", "WELDER", 7, 92, 490_000L, "Rei da solda", 3, 1f, 1.06f, "Soldador de ritmo alto. Brilha quando o setor de caldeiraria aperta."),
        LegendaryEmployeeDefinition("moskitao", "Moskitão", "DRILL_OPERATOR", 8, 86, 505_000L, "Elétrico", 2, .96f, 1.18f, "Operador veloz, vive atravessando o galpão e acelera a furação."),
        LegendaryEmployeeDefinition("nikao_narizudo", "Nikao Narizudo", "QUALITY_INSPECTOR", 9, 76, 610_000L, "Controle total", 4, 1f, 1f, "Inspetor rigoroso. Enquanto contratado, aumenta a qualidade geral da fábrica."),
        LegendaryEmployeeDefinition("gumersvaldo", "Gumersvaldo", "CNC_PROGRAMMER", 10, 73, 790_000L, "Mestre CNC", 4, 1f, .92f, "Programador CNC de elite. Extrai muito mais das máquinas CNC."),
        LegendaryEmployeeDefinition("magrao", "Magrão", "STOCK_ASSISTANT", 7, 88, 410_000L, "Logística rápida", 1, .90f, 1.25f, "Corre no estoque e mantém material chegando às máquinas."),
        LegendaryEmployeeDefinition("pedrao", "Pedrão", "WELDER", 8, 81, 565_000L, "Braço de aço", 1, 1.14f, .93f, "Caldeireiro forte e confiável, excelente em solda e estruturas."),
        LegendaryEmployeeDefinition("nelsinho_treme_treme", "Nelsinho Treme Treme", "DRILL_OPERATOR", 6, 69, 385_000L, "Treme mas entrega", 3, .97f, 1.08f, "Tem seu jeito peculiar, mas surpreende na produtividade."),
        LegendaryEmployeeDefinition("merciao", "Mercião", "GRINDER_OPERATOR", 9, 79, 620_000L, "Acabamento espelho", 2, 1f, 1f, "Especialista em retífica. Entrega acabamento e qualidade excepcionais."),
        LegendaryEmployeeDefinition("bodybuilder", "Bodybuilder", "STOCK_ASSISTANT", 8, 90, 520_000L, "Força bruta", 2, 1.22f, .88f, "Move material pesado sem reclamar e melhora o fluxo interno."),
    )

    private val workQuotes = mapOf(
        "tatu_banhado" to listOf("Esse torno aguenta!", "Manda mais peça.", "No braço e no relógio."),
        "kendao" to listOf("Passa mais avanço!", "Essa fresa vai cantar.", "Lote grande é comigo."),
        "chupa_engole" to listOf("Hoje sai faísca!", "Solda bonita é outra coisa.", "Segura essa estrutura."),
        "moskitao" to listOf("Furo pronto, próximo!", "Não para a furadeira!", "Tô voando hoje."),
        "nikao_narizudo" to listOf("Mede de novo.", "Qualidade primeiro.", "Essa tolerância tá apertada."),
        "gumersvaldo" to listOf("Programa redondo.", "CNC bem ajustado rende.", "Zero erro de offset."),
        "magrao" to listOf("Material chegando!", "Não deixa máquina esperando.", "Estoque tá girando."),
        "pedrao" to listOf("Pode trazer pesado.", "Essa solda segura.", "Estrutura firme."),
        "nelsinho_treme_treme" to listOf("Treme, mas não erra!", "Vai dar certo... vai!", "Furo na medida!"),
        "merciao" to listOf("Quero acabamento espelho.", "Mais uma passada fina.", "Agora ficou bonito."),
        "bodybuilder" to listOf("Carga leve hoje!", "Traz o pallet inteiro.", "Material não fica parado."),
    )

    private val idleQuotes = mapOf(
        "tatu_banhado" to listOf("Cadê serviço?", "Torno parado dá tristeza."),
        "kendao" to listOf("Tem fresa pra mim?", "Quero ver cavaco voar."),
        "chupa_engole" to listOf("Cadê a próxima solda?", "Hoje o café tá forte."),
        "moskitao" to listOf("Parado eu não fico.", "Vou dar uma volta no galpão."),
        "nikao_narizudo" to listOf("Vou conferir aquele lote.", "Alguém viu o paquímetro?"),
        "gumersvaldo" to listOf("Vou revisar o programa.", "Esse setup pode melhorar."),
        "magrao" to listOf("Vou buscar material.", "Tem pallet no estoque."),
        "pedrao" to listOf("Chama quando pesar.", "Vou conferir a bancada."),
        "nelsinho_treme_treme" to listOf("Calma... tá tranquilo.", "Cadê minha furadeira?"),
        "merciao" to listOf("Retífica limpa rende mais.", "Vou conferir o rebolo."),
        "bodybuilder" to listOf("Quem deixou isso no caminho?", "Vou organizar a carga."),
    )

    fun byCode(code: String?): LegendaryEmployeeDefinition? = all.firstOrNull { it.code == code }

    fun quote(code: String?, working: Boolean, index: Int): String? {
        if (code == null) return null
        val source = if (working) workQuotes[code] else idleQuotes[code]
        if (source.isNullOrEmpty()) return null
        return source[abs(index) % source.size]
    }
}

enum class LegendaryMissionMetric {
    OPERATING_MINUTES,
    SUPPORT_MINUTES,
    QUALITY_MINUTES,
}

data class LegendaryMissionDefinition(
    val id: String,
    val legendaryCode: String,
    val title: String,
    val description: String,
    val metric: LegendaryMissionMetric,
    val target: Long,
    val rewardCents: Long,
    val machineTypes: Set<String> = emptySet(),
    val minimumOperatingMachines: Int = 0,
    val minimumQuality: Int = 0,
)

object LegendaryMissionCatalog {
    val all = listOf(
        LegendaryMissionDefinition("mission_tatu_banhado", "tatu_banhado", "Casca grossa no torno", "Deixe Tatu do Banhado produzir por 120 minutos em um torno.", LegendaryMissionMetric.OPERATING_MINUTES, 120, 700_000L, setOf("MECHANICAL_LATHE", "CNC_LATHE")),
        LegendaryMissionDefinition("mission_kendao", "kendao", "Fresa sem dó", "Kendão precisa acumular 120 minutos trabalhando em fresagem.", LegendaryMissionMetric.OPERATING_MINUTES, 120, 800_000L, setOf("UNIVERSAL_MILL", "CNC_MACHINING_CENTER_3_AXIS", "CNC_MACHINING_CENTER_5_AXIS")),
        LegendaryMissionDefinition("mission_chupa_engole", "chupa_engole", "Faísca até o fim", "Acumule 100 minutos de solda com Chupa Engole.", LegendaryMissionMetric.OPERATING_MINUTES, 100, 900_000L, setOf("WELDING_BENCH", "ROBOTIC_WELDING")),
        LegendaryMissionDefinition("mission_moskitao", "moskitao", "Furação relâmpago", "Moskitão deve operar furadeiras por 100 minutos.", LegendaryMissionMetric.OPERATING_MINUTES, 100, 780_000L, setOf("COLUMN_DRILL", "CNC_DRILL")),
        LegendaryMissionDefinition("mission_nikao_narizudo", "nikao_narizudo", "Nada passa torto", "Com Nikao contratado, mantenha qualidade média de 75% ou mais por 90 minutos.", LegendaryMissionMetric.QUALITY_MINUTES, 90, 1_150_000L, minimumQuality = 75),
        LegendaryMissionDefinition("mission_gumersvaldo", "gumersvaldo", "Programa perfeito", "Gumersvaldo deve comandar máquinas CNC por 150 minutos.", LegendaryMissionMetric.OPERATING_MINUTES, 150, 1_600_000L, setOf("CNC_LATHE", "CNC_MACHINING_CENTER_3_AXIS", "CNC_MACHINING_CENTER_5_AXIS", "CNC_DRILL", "EDM", "LASER_CUTTER")),
        LegendaryMissionDefinition("mission_magrao", "magrao", "Material não pode parar", "Com Magrão na equipe, mantenha pelo menos 2 máquinas operando por 90 minutos.", LegendaryMissionMetric.SUPPORT_MINUTES, 90, 850_000L, minimumOperatingMachines = 2),
        LegendaryMissionDefinition("mission_pedrao", "pedrao", "Braço de aço", "Pedrão precisa acumular 130 minutos no setor de solda/caldeiraria.", LegendaryMissionMetric.OPERATING_MINUTES, 130, 950_000L, setOf("WELDING_BENCH", "ROBOTIC_WELDING", "PLASMA_CUTTER")),
        LegendaryMissionDefinition("mission_nelsinho_treme_treme", "nelsinho_treme_treme", "Treme mas entrega", "Nelsinho precisa produzir em furação por 80 minutos.", LegendaryMissionMetric.OPERATING_MINUTES, 80, 700_000L, setOf("COLUMN_DRILL", "CNC_DRILL")),
        LegendaryMissionDefinition("mission_merciao", "merciao", "Espelho no aço", "Mercião deve trabalhar 110 minutos em retífica.", LegendaryMissionMetric.OPERATING_MINUTES, 110, 1_050_000L, setOf("CYLINDRICAL_GRINDER", "CNC_GRINDER")),
        LegendaryMissionDefinition("mission_bodybuilder", "bodybuilder", "Logística pesada", "Com Bodybuilder contratado, mantenha 3 máquinas operando por 120 minutos.", LegendaryMissionMetric.SUPPORT_MINUTES, 120, 1_100_000L, minimumOperatingMachines = 3),
    )

    fun byLegendaryCode(code: String?): LegendaryMissionDefinition? =
        all.firstOrNull { it.legendaryCode == code }

    fun byId(id: String): LegendaryMissionDefinition? = all.firstOrNull { it.id == id }
}

fun CareerSave.mastery(machineType: String): MachineMastery =
    MachineMastery(machineType, masteryXp[machineType] ?: 0)

fun CareerSave.availableSkillPoints(): Int {
    val spent = unlockedSkills.sumOf { IndustrialSkillCatalog.byId(it)?.cost ?: 1 }
    return (earnedSkillPoints - spent).coerceAtLeast(0)
}

fun CareerSave.hasSkill(id: String): Boolean = id in unlockedSkills

fun CareerSave.manualQuantityMultiplier(): Double =
    1.0 +
        (if (hasSkill("ritmo_producao")) .12 else 0.0) +
        (if (hasSkill("mestre_usinagem")) .10 else 0.0) +
        (if (hasSkill("celula_cnc_avancada")) .08 else 0.0)

fun CareerSave.manualQualityBonus(): Int =
    (if (hasSkill("mao_firme")) 2 else 0) +
        (if (hasSkill("metrologista")) 4 else 0) +
        (if (hasSkill("zero_defeito")) 4 else 0) +
        (if (hasSkill("usinagem_precisao")) 5 else 0)

fun CareerSave.automationSpeedMultiplier(): Double {
    var v = 1.0
    listOf(
        "planejamento" to .03,
        "balanceamento" to .05,
        "producao_celular" to .06,
        "kanban" to .05,
        "lean_manufacturing" to .07,
        "producao_autonoma" to .08,
        "gerente_producao" to .05,
    ).forEach { if (hasSkill(it.first)) v += it.second }

    if (hasSkill("diretor_industrial")) {
        v += when (runCatching { ProductionPolicy.valueOf(productionPolicy) }.getOrDefault(ProductionPolicy.BALANCED)) {
            ProductionPolicy.DEADLINE -> .10
            ProductionPolicy.QUALITY -> -.05
            ProductionPolicy.PROFIT -> -.04
            ProductionPolicy.BALANCED -> 0.0
        }
    }
    return v.coerceIn(.85, 1.55)
}

fun CareerSave.automationQualityBonus(): Int {
    var v =
        (if (hasSkill("olho_treinado")) 1 else 0) +
        (if (hasSkill("controle_estatistico")) 3 else 0) +
        (if (hasSkill("mestre_qualidade")) 4 else 0) +
        (if (hasSkill("industria_4")) 2 else 0)
    if (hasSkill("diretor_industrial")) {
        v += when (runCatching { ProductionPolicy.valueOf(productionPolicy) }.getOrDefault(ProductionPolicy.BALANCED)) {
            ProductionPolicy.QUALITY -> 6
            ProductionPolicy.DEADLINE -> -2
            else -> 0
        }
    }
    return v
}

fun CareerSave.energyMultiplier(): Double {
    var value = if (hasSkill("lean_manufacturing")) .94 else 1.0
    if (
        hasSkill("diretor_industrial") &&
        runCatching { ProductionPolicy.valueOf(productionPolicy) }.getOrDefault(ProductionPolicy.BALANCED) == ProductionPolicy.PROFIT
    ) {
        value *= .90
    }
    return value.coerceIn(.75, 1.0)
}

fun CareerSave.commercialCompletionBonusPct(): Int =
    (
        (if (hasSkill("boa_reputacao")) 2 else 0) +
            (if (hasSkill("contratos_recorrentes")) 2 else 0) +
            (if (hasSkill("empresas_nacionais")) 3 else 0) +
            (if (hasSkill("exportacao")) 4 else 0) +
            (if (hasSkill("fornecedor_estrategico")) 5 else 0)
        ).coerceAtMost(16)

fun suggestedManualQuantity(
    machineType: String,
    score: Float,
    mastery: MachineMastery,
    career: CareerSave,
): Int {
    val base = when {
        "CNC" in machineType.uppercase() -> 8
        "LATHE" in machineType.uppercase() -> 7
        else -> 6
    }
    val performance = .75 + score.coerceIn(0f, 1f) * .75
    val masteryFactor = 1.0 + mastery.quantityBonusPct / 100.0
    return (
        base * performance * masteryFactor * career.manualQuantityMultiplier()
        ).roundToInt().coerceAtLeast(1)
}

fun legendaryMissionProgressDelta(
    definition: LegendaryMissionDefinition,
    legendaryEmployeeCode: String?,
    assignedMachineType: String?,
    operatingMachineTypes: Set<String>,
    operatingMachines: Int,
    averageQuality: Int,
    elapsedMinutes: Long,
): Long {
    if (legendaryEmployeeCode != definition.legendaryCode || elapsedMinutes <= 0) return 0L
    val valid = when (definition.metric) {
        LegendaryMissionMetric.OPERATING_MINUTES ->
            assignedMachineType != null &&
                assignedMachineType in operatingMachineTypes &&
                assignedMachineType in definition.machineTypes

        LegendaryMissionMetric.SUPPORT_MINUTES ->
            operatingMachines >= definition.minimumOperatingMachines

        LegendaryMissionMetric.QUALITY_MINUTES ->
            operatingMachines > 0 && averageQuality >= definition.minimumQuality
    }
    return if (valid) elapsedMinutes else 0L
}

fun seedLegendaryMission(code: String): LegendaryMissionSave? =
    LegendaryMissionCatalog.byLegendaryCode(code)?.let {
        LegendaryMissionSave(
            id = it.id,
            legendaryCode = it.legendaryCode,
            title = it.title,
            description = it.description,
            metric = it.metric.name,
            target = it.target,
            progress = 0L,
            rewardCents = it.rewardCents,
            claimed = false,
        )
    }
