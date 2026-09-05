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
    val jobGrade: Int = 1,
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
    val claimedRentalXpIds: Set<String> = emptySet(),
    val remoteHireOwnerUid: String? = null,
    val remoteHireName: String? = null,
    val remoteHireBoostPct: Int = 0,
    val remoteHireEndsAt: Long = 0L,
)

data class LegendaryMissionSave(
    val id: String,
    val legendaryCode: String,
    val title: String,
    val description: String,
    val metric: String,
    val target: Long,
    val progress: Long = 0L,
    val rewardCents: Long,
    val claimed: Boolean = false,
)

data class DailyMissionSave(
    val id: String,
    val title: String,
    val description: String,
    val metric: String,
    val target: Long,
    val baseValue: Long,
    val rewardType: String,
    val rewardValue: Long,
    val rewardItemId: String = "",
    val claimed: Boolean = false,
)

data class DailyMissionStateSave(
    val day: Long = -1L,
    val missions: List<DailyMissionSave> = emptyList(),
)

data class OwnerWorkBatchSave(
    val id: String,
    val machineId: String,
    val machineType: String,
    val contractId: String,
    val stage: String,
    val producedQuantity: Int,
    val quality: Int,
    val precision: Int,
    val speed: Int,
    val mistakes: Int,
    val perfect: Boolean,
    val manual: Boolean,
    val reworkCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CareerSave(
    val activeBatch: OwnerWorkBatchSave? = null,
    val masteryXp: Map<String, Int> = emptyMap(),
    val unlockedSkills: Set<String> = emptySet(),
    val milestones: Set<String> = emptySet(),
    val achievements: Set<String> = emptySet(),
    val totalManualOperations: Int = 0,
    val assistedOperations: Int = 0,
    val perfectOperations: Int = 0,
    val approvedBatches: Int = 0,
    val shippedBatches: Int = 0,
    val reworkedBatches: Int = 0,
    val scrappedBatches: Int = 0,
    val bestScore: Int = 0,
    val operationStreak: Int = 0,
    val earnedSkillPoints: Int = 1,
    val productionPolicy: String = "BALANCED",
    val lastOperationAt: Long = 0L,
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
    val schemaVersion: Int = 4,
    val company: CompanySave,
    val machines: List<MachineSave> = emptyList(),
    val employees: List<EmployeeSave> = emptyList(),
    val contracts: List<ContractSave> = emptyList(),
    val cargo: List<ProductionCargoSave> = emptyList(),
    val finances: List<FinanceSave> = emptyList(),
    val goals: List<GoalSave> = emptyList(),
    val legendaryMissions: List<LegendaryMissionSave> = emptyList(),
    val dailyMissions: DailyMissionStateSave = DailyMissionStateSave(),
    val career: CareerSave = CareerSave(),
    val expansion: ExpansionSave = ExpansionSave(),
    val profile: PlayerProfileSave = PlayerProfileSave(),
    val uiSettings: UiSettingsSave = UiSettingsSave(),
    val workforce: WorkforceSave = WorkforceSave(),
    val shiftMode: ShiftMode = ShiftMode.DAY_12H,
    val autoRest: Boolean = true,
    val playerFatigue: Double = 0.0,
    val playerRestingUntil: Long = 0L,
    val boostTokens: Int = 2,
    val snackUntil: Long = 0L,
    val lastDailyBonusDay: Long = -1L,
    val lastMinigameAt: Long = 0L,
    val bestMinigameScore: Double = 0.0,
    val lastPayrollCycle: Long = -1L,
    val autoCargoDelivery: Boolean = false,
)
