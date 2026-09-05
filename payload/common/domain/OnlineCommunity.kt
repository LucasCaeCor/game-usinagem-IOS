package br.com.usinagemmaster.game.domain

import br.com.usinagemmaster.domain.catalog.MachineCatalog

data class OnlineMachineSnapshot(
    val id: String,
    val type: String,
    val name: String,
    val level: Int,
    val x: Int,
    val y: Int,
    val premium: Boolean,
    val operating: Boolean,
    val condition: Int,
)

data class OnlineWorkerSnapshot(
    val id: String,
    val name: String,
    val specialty: String,
    val skillLevel: Int,
    val assignedMachineId: String?,
)

data class OnlineFactorySnapshot(
    val uid: String,
    val playerName: String,
    val companyName: String,
    val companyLevel: Int,
    val reputation: Int,
    val specialty: String,
    val employeeCount: Int,
    val updatedAt: Long,
    val productionPer10Minutes: Double,
    val activeContracts: Int,
    val pendingLots: Int,
    val ownerAvatar: String,
    val ownerStage: String?,
    val ownerMachineId: String?,
    val ownerCarrying: Boolean,
    val machines: List<OnlineMachineSnapshot>,
    val workers: List<OnlineWorkerSnapshot>,
)

data class LinkedOnlineProfile(
    val source: String,
    val uid: String,
    val playerName: String,
    val companyName: String,
    val companyLevel: Int,
    val reputation: Int,
)

data class OnlineCharacterOffer(
    val ownerUid: String,
    val playerName: String,
    val boostPct: Int,
    val leasedUntil: Long,
    val skills: List<String>,
)

data class OnlineMyCharacterOffer(
    val published: Boolean = false,
    val boostPct: Int = 0,
    val leasedBy: String = "",
    val leasedUntil: Long = 0L,
    val skills: List<String> = emptyList(),
)

data class OnlineOutgoingRental(
    val id: String,
    val renterUid: String,
    val companyName: String,
    val endsAt: Long,
    val manualOps: Int,
    val lastManualOpAt: Long,
)

data class OnlineActiveHire(
    val id: String,
    val ownerUid: String,
    val playerName: String,
    val boostPct: Int,
    val endsAt: Long,
    val manualOps: Int,
)

data class OnlineRemoteOperationResult(
    val token: String,
    val xp: Long,
)

data class OnlineCommunitySnapshot(
    val signedIn: Boolean = false,
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val linkedProfile: LinkedOnlineProfile? = null,
    val factories: List<OnlineFactorySnapshot> = emptyList(),
    val offers: List<OnlineCharacterOffer> = emptyList(),
    val myOffer: OnlineMyCharacterOffer? = null,
    val outgoingRental: OnlineOutgoingRental? = null,
    val activeHire: OnlineActiveHire? = null,
    val remoteOperationResult: OnlineRemoteOperationResult? = null,
    val message: String? = null,
    val error: String? = null,
    val refreshedAt: Long = 0L,
)

expect fun onlineCommunityRaw(): String
expect fun requestOnlineCommunityRefresh()
expect fun publishOnlineFactory(raw: String)
expect fun hireOnlineCharacter(ownerUid: String)
expect fun publishOnlineCharacterOffer(raw: String)
expect fun withdrawOnlineCharacterOffer()
expect fun operateOnlineRental(raw: String)

fun decodeOnlineCommunity(raw: String): OnlineCommunitySnapshot {
    if (raw.isBlank()) return OnlineCommunitySnapshot()
    var signedIn = false
    var uid = ""
    var displayName = ""
    var email = ""
    var linked: LinkedOnlineProfile? = null
    val factories = mutableListOf<OnlineFactorySnapshot>()
    val offers = mutableListOf<OnlineCharacterOffer>()
    var myOffer: OnlineMyCharacterOffer? = null
    var outgoingRental: OnlineOutgoingRental? = null
    var activeHire: OnlineActiveHire? = null
    var remoteResult: OnlineRemoteOperationResult? = null
    var message: String? = null
    var error: String? = null
    var refreshedAt = 0L

    raw.lineSequence().filter { it.isNotBlank() }.forEach { line ->
        val p = line.split('|')
        when (p.firstOrNull()) {
            "ACCOUNT" -> {
                signedIn = p.getOrNull(1) == "1"
                uid = onlineUnescape(p.getOrNull(2).orEmpty())
                displayName = onlineUnescape(p.getOrNull(3).orEmpty())
                email = onlineUnescape(p.getOrNull(4).orEmpty())
            }
            "ME" -> linked = LinkedOnlineProfile(
                source = onlineUnescape(p.getOrNull(1).orEmpty()), uid = onlineUnescape(p.getOrNull(2).orEmpty()),
                playerName = onlineUnescape(p.getOrNull(3).orEmpty()), companyName = onlineUnescape(p.getOrNull(4).orEmpty()),
                companyLevel = p.getOrNull(5)?.toIntOrNull() ?: 1, reputation = p.getOrNull(6)?.toIntOrNull() ?: 0,
            )
            "FACTORY" -> factories += OnlineFactorySnapshot(
                uid = onlineUnescape(p.getOrNull(1).orEmpty()),
                playerName = onlineUnescape(p.getOrNull(2).orEmpty()),
                companyName = onlineUnescape(p.getOrNull(3).orEmpty()),
                companyLevel = p.getOrNull(4)?.toIntOrNull() ?: 1,
                reputation = p.getOrNull(5)?.toIntOrNull() ?: 0,
                specialty = onlineUnescape(p.getOrNull(6).orEmpty()),
                employeeCount = p.getOrNull(7)?.toIntOrNull() ?: 0,
                updatedAt = p.getOrNull(8)?.toLongOrNull() ?: 0L,
                productionPer10Minutes = p.getOrNull(9)?.toDoubleOrNull() ?: 0.0,
                activeContracts = p.getOrNull(10)?.toIntOrNull() ?: 0,
                pendingLots = p.getOrNull(11)?.toIntOrNull() ?: 0,
                ownerAvatar = onlineUnescape(p.getOrNull(12).orEmpty()).ifBlank { "WORKSHOP" },
                ownerStage = onlineUnescape(p.getOrNull(13).orEmpty()).ifBlank { null },
                ownerMachineId = onlineUnescape(p.getOrNull(14).orEmpty()).ifBlank { null },
                ownerCarrying = p.getOrNull(15) == "1",
                machines = decodeOnlineMachines(p.getOrNull(16).orEmpty()),
                workers = decodeOnlineWorkers(p.getOrNull(17).orEmpty()),
            )
            "OFFER" -> offers += OnlineCharacterOffer(
                ownerUid = onlineUnescape(p.getOrNull(1).orEmpty()),
                playerName = onlineUnescape(p.getOrNull(2).orEmpty()),
                boostPct = p.getOrNull(3)?.toIntOrNull() ?: 4,
                leasedUntil = p.getOrNull(4)?.toLongOrNull() ?: 0L,
                skills = p.getOrNull(5).orEmpty().split(',').filter { it.isNotBlank() }.map(::onlineUnescape),
            )
            "MYOFFER" -> myOffer = OnlineMyCharacterOffer(
                published = p.getOrNull(1) == "1",
                boostPct = p.getOrNull(2)?.toIntOrNull() ?: 0,
                leasedBy = onlineUnescape(p.getOrNull(3).orEmpty()),
                leasedUntil = p.getOrNull(4)?.toLongOrNull() ?: 0L,
                skills = p.getOrNull(5).orEmpty().split(',').filter { it.isNotBlank() }.map(::onlineUnescape),
            )
            "OUTRENTAL" -> outgoingRental = OnlineOutgoingRental(
                id = onlineUnescape(p.getOrNull(1).orEmpty()), renterUid = onlineUnescape(p.getOrNull(2).orEmpty()),
                companyName = onlineUnescape(p.getOrNull(3).orEmpty()), endsAt = p.getOrNull(4)?.toLongOrNull() ?: 0L,
                manualOps = p.getOrNull(5)?.toIntOrNull() ?: 0, lastManualOpAt = p.getOrNull(6)?.toLongOrNull() ?: 0L,
            )
            "ACTIVEHIRE" -> activeHire = OnlineActiveHire(
                id = onlineUnescape(p.getOrNull(1).orEmpty()), ownerUid = onlineUnescape(p.getOrNull(2).orEmpty()),
                playerName = onlineUnescape(p.getOrNull(3).orEmpty()), boostPct = p.getOrNull(4)?.toIntOrNull() ?: 0,
                endsAt = p.getOrNull(5)?.toLongOrNull() ?: 0L, manualOps = p.getOrNull(6)?.toIntOrNull() ?: 0,
            )
            "REMOTE_RESULT" -> remoteResult = OnlineRemoteOperationResult(
                token = onlineUnescape(p.getOrNull(1).orEmpty()), xp = p.getOrNull(2)?.toLongOrNull() ?: 0L,
            )
            "MESSAGE" -> message = onlineUnescape(p.getOrNull(1).orEmpty()).ifBlank { null }
            "ERROR" -> error = onlineUnescape(p.getOrNull(1).orEmpty()).ifBlank { null }
            "STATUS" -> refreshedAt = p.getOrNull(1)?.toLongOrNull() ?: 0L
        }
    }

    return OnlineCommunitySnapshot(
        signedIn, uid, displayName, email, linked, factories.sortedByDescending { it.updatedAt }, offers,
        myOffer, outgoingRental, activeHire, remoteResult, message, error, refreshedAt,
    )
}

fun encodeOnlineCharacterOffer(store: GameStore): String {
    val playerLevel = GameProgression.playerProgress(store.state.expansion.playerXp).level
    val boost = (4 + playerLevel / 4 + store.state.expansion.playerSkills.size * 2 + if ("negociacao" in store.state.expansion.playerSkills) 4 else 0).coerceIn(4, 25)
    val skills = store.state.expansion.playerSkills.sorted().joinToString(",") { onlineEscape(it) }
    return listOf("V1", onlineEscape(store.state.profile.name), boost.toString(), store.state.expansion.playerXp.toString(), skills).joinToString("|")
}

fun encodeOnlineRentalOperation(rentalId: String, machineId: String, score: Int): String =
    listOf("V1", onlineEscape(rentalId), onlineEscape(machineId), score.coerceIn(0, 100).toString()).joinToString("|")

fun encodeOnlineFactoryPublication(store: GameStore): String {
    val production = store.production
    val machineRuntime = production.machineProduction.associateBy { it.machineId }
    val machines = store.state.machines.take(60).joinToString("^") { machine ->
        val def = MachineCatalog.byType(machine.machineType)
        listOf(onlineEscape(machine.id), onlineEscape(machine.machineType), onlineEscape(def?.name ?: machine.machineType),
            machine.level.toString(), machine.gridX.toString(), machine.gridY.toString(), "0",
            if (machineRuntime[machine.id]?.isOperating == true) "1" else "0", machine.condition.toString()).joinToString("~")
    }
    val workers = store.state.employees.take(60).joinToString("^") { employee ->
        listOf(onlineEscape(employee.id), onlineEscape(employee.name), onlineEscape(employee.specialty),
            employee.skillLevel.toString(), onlineEscape(employee.assignedMachineId.orEmpty())).joinToString("~")
    }
    return listOf(
        "V1", onlineEscape(store.state.profile.name), onlineEscape(store.state.company.name), store.state.company.companyLevel.toString(),
        store.state.company.reputation.toString(), onlineEscape(store.state.expansion.specialty), store.state.employees.size.toString(),
        production.totalUnitsPer10Minutes.toString(), store.state.contracts.count { it.status == "ACTIVE" }.toString(), store.pendingCargo.size.toString(),
        onlineEscape(store.state.profile.skinStyle), onlineEscape(store.ownerFrame.activity.name), "", if (store.ownerFrame.carrying) "1" else "0", machines, workers,
    ).joinToString("|")
}

private fun decodeOnlineMachines(raw: String): List<OnlineMachineSnapshot> =
    if (raw.isBlank()) emptyList() else raw.split('^').mapNotNull { row ->
        val p = row.split('~'); if (p.size < 9) return@mapNotNull null
        OnlineMachineSnapshot(onlineUnescape(p[0]), onlineUnescape(p[1]), onlineUnescape(p[2]), p[3].toIntOrNull() ?: 1,
            p[4].toIntOrNull() ?: 0, p[5].toIntOrNull() ?: 0, p[6] == "1", p[7] == "1", p[8].toIntOrNull() ?: 1000)
    }

private fun decodeOnlineWorkers(raw: String): List<OnlineWorkerSnapshot> =
    if (raw.isBlank()) emptyList() else raw.split('^').mapNotNull { row ->
        val p = row.split('~'); if (p.size < 5) return@mapNotNull null
        OnlineWorkerSnapshot(onlineUnescape(p[0]), onlineUnescape(p[1]), onlineUnescape(p[2]), p[3].toIntOrNull() ?: 1, onlineUnescape(p[4]).ifBlank { null })
    }

private fun onlineEscape(value: String): String = value.replace("%", "%25").replace("|", "%7C").replace("~", "%7E").replace("^", "%5E").replace(",", "%2C").replace("\n", "%0A").replace("\r", "%0D")
private fun onlineUnescape(value: String): String = value.replace("%0D", "\r").replace("%0A", "\n").replace("%2C", ",").replace("%5E", "^").replace("%7E", "~").replace("%7C", "|").replace("%25", "%")
