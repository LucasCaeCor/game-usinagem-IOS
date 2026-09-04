package br.com.usinagemmaster.game.model

data class CompanySave(
    val name: String = "Oficina Império do Aço",
    val cashCents: Long = 3_500_000L,
    val reputation: Int = 0,
    val companyLevel: Int = 1,
    val warehouseSpace: Int = 100,
    val usedWarehouseSpace: Int = 0,
    val lastSimulationAt: Long = 0L,
)

data class MachineSave(
    val id: String,
    val machineType: String,
    val level: Int = 1,
    val condition: Int = 1000,
    val accumulatedWorkMinutes: Long = 0L,
    val installed: Boolean = true,
    val gridX: Int = 0,
    val gridY: Int = 0,
)

data class EmployeeSave(
    val id: String,
    val name: String,
    val specialty: String,
    val skillLevel: Int = 1,
    val experience: Long = 0L,
    val salaryCents: Long = 245_000L,
    val morale: Int = 80,
    val trait: String = "Cuidadoso",
    val assignedMachineId: String? = null,
    val legendaryCode: String? = null,
    val fatigue: Double = 0.0,
    val restingUntil: Long = 0L,
)

data class ContractSave(
    val id: String,
    val clientName: String,
    val type: String,
    val quantity: Int,
    val completedQuantity: Int = 0,
    val productionProgressMilli: Long = 0L,
    val difficulty: Int,
    val requiredQuality: Int,
    val rewardCents: Long,
    val penaltyCents: Long,
    val reputationReward: Int,
    val reputationPenalty: Int,
    val generatedAt: Long,
    val startedAt: Long = 0L,
    val deadlineAt: Long,
    val status: String = "AVAILABLE",
    val rewardPaid: Boolean = false,
    val special: Boolean = false,
)

data class ProductionCargoSave(
    val id: String,
    val valueCents: Long,
    val unitsMilli: Long,
    val cycles: Long,
    val createdAt: Long,
    val deliveredAt: Long = 0L,
) {
    val pending: Boolean get() = deliveredAt <= 0L
}

data class FinanceSave(
    val id: String,
    val type: String,
    val category: String,
    val amountCents: Long,
    val description: String,
    val createdAt: Long,
)

data class GoalSave(
    val id: String,
    val title: String,
    val target: Int,
    val rewardCents: Long,
    val ticketReward: Int = 0,
    val claimed: Boolean = false,
)

enum class ShiftMode {
    DAY_12H,
    CONTINUOUS_24H,
}

data class ExpansionSave(
    val specialty: String = "generalista",
    val companySkills: Set<String> = emptySet(),
    val playerSkills: Set<String> = emptySet(),
    val gachaTickets: Int = 5,
    val pityEpic: Int = 0,
    val pityLegendary: Int = 0,
    val ownedSkins: Set<String> = setOf("operador_padrao"),
    val equippedSkin: String = "operador_padrao",
    val ownedCharacters: Set<String> = emptySet(),
    val equippedCharacter: String? = null,
    val tools: Map<String, Int> = mapOf(
        "broca_madeira" to 2,
        "ferramenta_soldada" to 2,
        "fresa_hss" to 1,
    ),
    val contractTools: Map<String, String> = emptyMap(),
    val premiumMachines: Set<String> = emptySet(),
    val playerXp: Long = 0L,
    val lastDailyTicketDay: Long = -1L,
)

data class PlayerProfileSave(
    val name: String = "Dono da Oficina",
    val gender: String = "MALE",
    val skinStyle: String = "WORKSHOP",
    val bodyType: String = "STANDARD",
    val skinTone: String = "MEDIUM",
    val hairStyle: String = "SHORT",
    val hairColor: String = "DARK",
    val uniformColor: String = "NAVY",
    val helmetColor: String = "YELLOW",
    val accessory: String = "NONE",
    val onboardingComplete: Boolean = false,
)

data class UiSettingsSave(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val legendarySpeechEnabled: Boolean = true,
    val legendarySpeechSeconds: Int = 5,
)

data class WorkforceSave(
    val idleEmployeeId: String? = null,
    val idleSinceAt: Long = 0L,
    val idleUntilAt: Long = 0L,
    val nextIdleCheckAt: Long = 0L,
)

data class GameSave(
    val schemaVersion: Int = 3,
    val company: CompanySave,
    val machines: List<MachineSave> = emptyList(),
    val employees: List<EmployeeSave> = emptyList(),
    val contracts: List<ContractSave> = emptyList(),
    val cargo: List<ProductionCargoSave> = emptyList(),
    val finances: List<FinanceSave> = emptyList(),
    val goals: List<GoalSave> = emptyList(),
    val expansion: ExpansionSave = ExpansionSave(),
    val profile: PlayerProfileSave = PlayerProfileSave(),
    val uiSettings: UiSettingsSave = UiSettingsSave(),
    val workforce: WorkforceSave = WorkforceSave(),
    val shiftMode: ShiftMode = ShiftMode.DAY_12H,
    val boostTokens: Int = 2,
    val snackUntil: Long = 0L,
    val lastDailyBonusDay: Long = -1L,
    val lastMinigameAt: Long = 0L,
    val bestMinigameScore: Double = 0.0,
)
