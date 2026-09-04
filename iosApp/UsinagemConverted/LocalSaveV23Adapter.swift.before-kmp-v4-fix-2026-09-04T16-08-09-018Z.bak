import Foundation
import CryptoKit

struct V23LocalPayload {
    let json: String
    let fingerprint: String
}

struct V23TrackingStatus {
    let uid: String?
    let saveId: String?
    let revision: Int64
    let fingerprint: String
    let syncedAt: Int64
}

enum V23AdapterError: LocalizedError {
    case invalidAndroidSave(String)
    case invalidLocalSave(String)

    var errorDescription: String? {
        switch self {
        case .invalidAndroidSave(let text), .invalidLocalSave(let text):
            return text
        }
    }
}

/// Traduz o save textual KMP/iOS para o JSON V23 do Android e vice-versa.
///
/// Estratégia de segurança:
/// - o JSON Android original é preservado em Application Support;
/// - sistemas que ainda não existem no iOS permanecem opacos e não são apagados;
/// - quando o KMP não mudou desde o restore, o JSON original é reutilizado byte a byte,
///   mantendo o mesmo SHA-256 da revisão Android;
/// - restore sempre mantém um backup local antes de escrever.
final class LocalSaveV23Adapter {
    static let shared = LocalSaveV23Adapter()

    static let kmpSaveKey = "usinagemmaster.kmp.save.v6"

    private let saveIdKey = "usinagemmaster.cloudsave.v23.save_id"
    private let trackedUIDKey = "usinagemmaster.cloudsave.v23.uid"
    private let revisionKey = "usinagemmaster.cloudsave.v23.revision"
    private let fingerprintKey = "usinagemmaster.cloudsave.v23.fingerprint"
    private let syncedAtKey = "usinagemmaster.cloudsave.v23.synced_at"
    private let opaqueKmpHashKey = "usinagemmaster.cloudsave.v23.opaque_kmp_hash"

    private init() {}

    var hasLocalGameSave: Bool {
        guard let raw = UserDefaults.standard.string(forKey: Self.kmpSaveKey) else { return false }
        return !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    func localSaveId(createIfNeeded: Bool) -> String? {
        if let current = UserDefaults.standard.string(forKey: saveIdKey),
           !current.isEmpty {
            return current
        }
        guard createIfNeeded else { return nil }
        let value = UUID().uuidString.lowercased()
        UserDefaults.standard.set(value, forKey: saveIdKey)
        return value
    }

    func adoptCloudSaveId(_ saveId: String) {
        guard !saveId.isEmpty else { return }
        UserDefaults.standard.set(saveId, forKey: saveIdKey)
    }

    func trackingStatus() -> V23TrackingStatus {
        V23TrackingStatus(
            uid: UserDefaults.standard.string(forKey: trackedUIDKey),
            saveId: UserDefaults.standard.string(forKey: saveIdKey),
            revision: Int64(UserDefaults.standard.integer(forKey: revisionKey)),
            fingerprint: UserDefaults.standard.string(forKey: fingerprintKey) ?? "",
            syncedAt: Int64(UserDefaults.standard.integer(forKey: syncedAtKey))
        )
    }

    func remember(uid: String, saveId: String, revision: Int64, fingerprint: String) {
        UserDefaults.standard.set(uid, forKey: trackedUIDKey)
        UserDefaults.standard.set(saveId, forKey: saveIdKey)
        UserDefaults.standard.set(Int(revision), forKey: revisionKey)
        UserDefaults.standard.set(fingerprint, forKey: fingerprintKey)
        UserDefaults.standard.set(Int(Date().timeIntervalSince1970 * 1000.0), forKey: syncedAtKey)
    }

    func capturePayload() throws -> V23LocalPayload? {
        guard let kmp = UserDefaults.standard.string(forKey: Self.kmpSaveKey),
              !kmp.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }

        let kmpHash = sha256(kmp)
        if let opaque = try? readOpaqueJSON(),
           let opaqueHash = UserDefaults.standard.string(forKey: opaqueKmpHashKey),
           opaqueHash == kmpHash {
            return V23LocalPayload(json: opaque, fingerprint: sha256(opaque))
        }

        let base = (try? readOpaqueObject()) ?? emptyAndroidRoot()
        let merged = try mergeKmpIntoAndroid(kmpRaw: kmp, baseRoot: base)
        let json = try canonicalJSON(merged)
        return V23LocalPayload(json: json, fingerprint: sha256(json))
    }

    func markUploaded(json: String) throws {
        guard let kmp = UserDefaults.standard.string(forKey: Self.kmpSaveKey) else {
            throw V23AdapterError.invalidLocalSave("O save local desapareceu antes de concluir o upload.")
        }
        try writeOpaqueJSON(json)
        UserDefaults.standard.set(sha256(kmp), forKey: opaqueKmpHashKey)
    }

    func applyRemoteAndroidJSON(_ json: String, saveId: String) throws {
        guard let data = json.data(using: .utf8),
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw V23AdapterError.invalidAndroidSave("O backup Android não contém um JSON válido.")
        }

        let schema = int(root["schema"], 1)
        guard schema <= 1 else {
            throw V23AdapterError.invalidAndroidSave(
                "Este backup foi criado por uma versão mais nova do protocolo de Cloud Save."
            )
        }

        guard root["company"] is [String: Any] else {
            throw V23AdapterError.invalidAndroidSave(
                "O backup não possui os dados da empresa. O save do iPhone foi preservado."
            )
        }

        let newKmp = try androidToKmp(root)
        guard newKmp.contains("COMPANY|"), newKmp.contains("VERSION|3") else {
            throw V23AdapterError.invalidAndroidSave(
                "A conversão do backup Android não gerou um save KMP válido."
            )
        }

        let oldKmp = UserDefaults.standard.string(forKey: Self.kmpSaveKey)
        let oldOpaque = try? readOpaqueJSON()
        try writeRestoreBackup(kmp: oldKmp, androidJSON: oldOpaque)

        do {
            UserDefaults.standard.set(newKmp, forKey: Self.kmpSaveKey)
            try writeOpaqueJSON(json)
            UserDefaults.standard.set(sha256(newKmp), forKey: opaqueKmpHashKey)
            adoptCloudSaveId(saveId)
        } catch {
            if let oldKmp {
                UserDefaults.standard.set(oldKmp, forKey: Self.kmpSaveKey)
            } else {
                UserDefaults.standard.removeObject(forKey: Self.kmpSaveKey)
            }
            if let oldOpaque {
                try? writeOpaqueJSON(oldOpaque)
            }
            throw error
        }
    }

    // MARK: - Android -> KMP

    private func androidToKmp(_ root: [String: Any]) throws -> String {
        guard let company = root["company"] as? [String: Any] else {
            throw V23AdapterError.invalidAndroidSave("Empresa ausente no Cloud Save.")
        }

        let prefs = dict(root["preferences"])
        let game = dict(prefs["game"])
        let profile = dict(prefs["profile"])
        let expansion = dict(prefs["expansion"])
        let active = dict(prefs["activeGameplay"])
        let workLife = dict(prefs["workLife"])

        var out = ""
        out += row(["VERSION", 4])
        out += row([
            "COMPANY",
            string(company["name"], "Minha Usinagem"),
            i64(company["cashCents"], 0),
            int(company["reputation"], 0),
            int(company["companyLevel"], 1),
            int(company["warehouseSpace"], 100),
            int(company["usedWarehouseSpace"], 0),
            i64(company["lastSimulationAt"], 0),
        ])

        let mode = string(workLife["mode"], "")
        let shift = mode.uppercased().contains("24") ? "CONTINUOUS_24H" : "DAY_12H"
        out += row([
            "SETTINGS",
            shift,
            int(game["boostTokens"], 2),
            i64(game["snackImmunityUntil"], 0),
            epochDayFromAndroidDayKey(int(game["lastDailyRewardDay"], 0)),
        ])

        out += row([
            "UX3",
            bool(game["sound"], true),
            bool(game["vibration"], true),
            bool(game["npcSpeech"], true),
            int(game["speechDurationSeconds"], 8),
        ])

        out += row([
            "WORKFORCE3",
            string(game["idleEmployeeId"], ""),
            i64(game["idleSinceAt"], 0),
            i64(game["idleUntilAt"], 0),
            i64(game["nextIdleCheckAt"], 0),
        ])

        let best = Double(int(active["bestScore"], 0)) / 100.0
        out += row([
            "MINIGAME3",
            i64(game["lastMinigameAt"], 0),
            best,
        ])

        out += row([
            "EXP4",
            string(expansion["specialty"], "generalista"),
            encodeSet(stringArray(expansion["companySkills"])),
            encodeSet(stringArray(expansion["playerSkills"])),
            int(expansion["gachaTickets"], 5),
            int(expansion["pityEpic"], 0),
            int(expansion["pityLegendary"], 0),
            encodeSet(stringArray(expansion["ownedSkins"]).isEmpty
                      ? ["operador_padrao"]
                      : stringArray(expansion["ownedSkins"])),
            string(expansion["equippedSkin"], "operador_padrao"),
            encodeSet(stringArray(expansion["ownedCharacters"])),
            string(expansion["equippedCharacter"], ""),
            encodeIntMap(intDictionary(expansion["tools"])),
            encodeStringMap(stringDictionary(expansion["contractTools"])),
            encodeSet(stringArray(expansion["premiumMachines"])),
            i64(expansion["playerXp"], 0),
            i64(expansion["lastDailyTicketDay"], -1),
            encodeSet(stringArray(expansion["claimedRentalXpIds"])),
            string(expansion["remoteHireOwnerUid"], ""),
            string(expansion["remoteHireName"], ""),
            int(expansion["remoteHireBoostPct"], 0),
            i64(expansion["remoteHireEndsAt"], 0),
        ])

        let mainPlayer = "__main_player__"
        let preciseFatigueForV24 = parseDoubleMap(string(workLife["preciseFatigue"], ""))
        let integerFatigueForV24 = parseIntMap(string(workLife["fatigue"], ""))
        let restingForV24 = parseLongMap(string(workLife["resting"], ""))
        out += row([
            "WORKLIFE4",
            bool(workLife["autoRest"], true),
            preciseFatigueForV24[mainPlayer] ?? Double(integerFatigueForV24[mainPlayer] ?? 0),
            restingForV24[mainPlayer] ?? 0,
        ])

        out += row([
            "CAREER4",
            int(active["manualOps"], 0),
            int(active["assistedOps"], 0),
            int(active["perfectOps"], 0),
            int(active["approvedBatches"], 0),
            int(active["shippedBatches"], 0),
            int(active["reworkedBatches"], 0),
            int(active["scrappedBatches"], 0),
            int(active["bestScore"], 0),
            int(active["streak"], 0),
            int(active["skillPoints"], 1),
            string(active["productionPolicy"], "BALANCED"),
            i64(active["lastOperationAt"], 0),
            encodeSet(stringArray(active["milestones"])),
            encodeSet(stringArray(active["achievements"])),
        ])

        let masteryEntries = stringArray(active["masteryXp"])
        var masteryMap: [String: Int] = [:]
        for token in masteryEntries {
            let pair = token.split(separator: "=", maxSplits: 1)
            if pair.count == 2, let value = Int(pair[1]) {
                masteryMap[String(pair[0])] = value
            }
        }
        out += row([
            "CAREER_SKILLS4",
            encodeSet(stringArray(active["industrialSkills"])),
            encodeIntMap(masteryMap),
        ])

        let activeBatch = string(active["activeBatch"], "")
        if !activeBatch.isEmpty {
            let b = activeBatch.components(separatedBy: "§")
            if b.count >= 15 {
                out += row([
                    "CAREER_BATCH4",
                    b[0], b[1], b[2], b[3], b[4],
                    Int(b[5]) ?? 1,
                    Int(b[6]) ?? 50,
                    Int(b[7]) ?? 0,
                    Int(b[8]) ?? 0,
                    Int(b[9]) ?? 0,
                    b[10] == "1",
                    b[11] == "1",
                    Int(b[12]) ?? 0,
                    Int64(b[13]) ?? 0,
                    Int64(b[14]) ?? 0,
                ])
            }
        }

        for mission in objectArray(root["legendaryMissions"]).sorted(by: objectIdSort) {
            out += row([
                "LM4",
                string(mission["id"], ""),
                string(mission["legendaryCode"], ""),
                string(mission["title"], ""),
                string(mission["description"], ""),
                string(mission["metric"], ""),
                i64(mission["target"], 0),
                i64(mission["progress"], 0),
                i64(mission["rewardCents"], 0),
                bool(mission["claimed"], false),
            ])
        }

        out += row([
            "PROFILE3",
            string(profile["displayName"], "Dono da Oficina"),
            string(profile["gender"], "MALE"),
            string(profile["skinStyle"], "WORKSHOP"),
            string(profile["bodyType"], "STANDARD"),
            string(profile["skinTone"], "MEDIUM"),
            string(profile["hairStyle"], "SHORT"),
            string(profile["hairColor"], "DARK"),
            string(profile["uniformColor"], "NAVY"),
            string(profile["helmetColor"], "YELLOW"),
            string(profile["accessory"], "NONE"),
            bool(profile["onboardingComplete"], false),
        ])

        let preciseFatigue = preciseFatigueForV24
        let integerFatigue = integerFatigueForV24
        let resting = restingForV24

        for machine in objectArray(root["machines"]).sorted(by: objectIdSort) {
            let androidCondition = int(machine["condition"], 100)
            let iosCondition = androidCondition <= 100 ? androidCondition * 10 : androidCondition
            out += row([
                "M",
                string(machine["id"], ""),
                string(machine["machineType"], ""),
                int(machine["level"], 1),
                iosCondition,
                i64(machine["accumulatedWorkMinutes"], 0),
                bool(machine["installed"], true),
                int(machine["gridX"], 0),
                int(machine["gridY"], 0),
            ])
        }

        for employee in objectArray(root["employees"]).sorted(by: objectIdSort) {
            let id = string(employee["id"], "")
            let fatigue = preciseFatigue[id] ?? Double(integerFatigue[id] ?? 0)
            out += row([
                "E",
                id,
                string(employee["name"], "Operador"),
                string(employee["specialty"], "TURNING"),
                int(employee["skillLevel"], 1),
                i64(employee["experience"], 0),
                i64(employee["salaryCents"], 245_000),
                int(employee["morale"], 80),
                string(employee["trait"], "Cuidadoso"),
                string(employee["assignedMachineId"], ""),
                string(employee["legendaryCode"], ""),
                fatigue,
                resting[id] ?? 0,
            ])
        }

        for contract in objectArray(root["contracts"]).sorted(by: objectIdSort) {
            let status = string(contract["status"], "AVAILABLE")
            out += row([
                "C3",
                string(contract["id"], ""),
                string(contract["clientName"], "Cliente"),
                string(contract["contractType"], "GENERAL"),
                int(contract["quantity"], 1),
                int(contract["completedQuantity"], 0),
                i64(contract["productionProgressMilli"], 0),
                int(contract["difficulty"], 1),
                int(contract["requiredQuality"], 50),
                i64(contract["rewardCents"], 0),
                i64(contract["penaltyCents"], 0),
                int(contract["reputationReward"], 0),
                int(contract["reputationPenalty"], 0),
                i64(contract["generatedAt"], 0),
                i64(contract["startedAt"], 0),
                i64(contract["deadlineAt"], 0),
                status,
                status == "COMPLETED",
                false,
            ])
        }

        for cargo in objectArray(root["productionCargo"]).sorted(by: objectIdSort) {
            out += row([
                "CARGO",
                string(cargo["id"], ""),
                i64(cargo["valueCents"], 0),
                i64(cargo["unitsMilli"], 0),
                i64(cargo["cycles"], 1),
                i64(cargo["createdAt"], 0),
                i64(cargo["deliveredAt"], 0),
            ])
        }

        for finance in objectArray(root["finance"]).sorted(by: financeSort) {
            out += row([
                "F",
                string(finance["id"], ""),
                string(finance["type"], "INCOME"),
                string(finance["category"], "BONUS"),
                i64(finance["amountCents"], 0),
                string(finance["description"], ""),
                i64(finance["createdAt"], 0),
            ])
        }

        for goal in objectArray(root["goals"]).sorted(by: objectIdSort) {
            let id = string(goal["id"], "")
            out += row([
                "G3",
                id,
                string(goal["title"], id),
                int(goal["target"], 0),
                i64(goal["rewardCents"], 0),
                ticketReward(id),
                bool(goal["claimed"], false),
            ])
        }

        return out
    }

    // MARK: - KMP -> Android (merge-preserving)

    private func mergeKmpIntoAndroid(
        kmpRaw: String,
        baseRoot: [String: Any]
    ) throws -> [String: Any] {
        let rows = parseRows(kmpRaw)
        guard let companyRow = rows.first(where: { $0.first == "COMPANY" }) else {
            throw V23AdapterError.invalidLocalSave("O save KMP local não possui empresa.")
        }

        var root = baseRoot
        root["schema"] = 1

        var company = dict(root["company"])
        company["id"] = int(company["id"], 1)
        company["name"] = text(companyRow, 1, "Minha Usinagem")
        company["cashCents"] = long(companyRow, 2, 0)
        company["reputation"] = integer(companyRow, 3, 0)
        company["companyLevel"] = integer(companyRow, 4, 1)
        company["warehouseSpace"] = integer(companyRow, 5, 100)
        company["usedWarehouseSpace"] = integer(companyRow, 6, 0)
        company["lastSimulationAt"] = long(companyRow, 7, 0)
        if company["experience"] == nil { company["experience"] = 0 }
        if company["createdAt"] == nil { company["createdAt"] = 0 }
        root["company"] = company

        let oldMachines = indexById(objectArray(root["machines"]))
        let machineRows = rows.filter { $0.first == "M" }
        root["machines"] = machineRows.map { r -> [String: Any] in
            let id = text(r, 1, "")
            let type = text(r, 2, "")
            var old = oldMachines[id] ?? [:]
            let iosCondition = integer(r, 4, 1000)
            old["id"] = id
            old["machineType"] = type
            if old["customName"] == nil { old["customName"] = NSNull() }
            old["sectorType"] = string(old["sectorType"], sectorForMachine(type))
            old["level"] = integer(r, 3, 1)
            old["condition"] = iosCondition > 100 ? max(0, min(100, iosCondition / 10)) : iosCondition
            old["accumulatedWorkMinutes"] = long(r, 5, 0)
            old["installed"] = boolean(r, 6, true)
            old["gridX"] = integer(r, 7, 0)
            old["gridY"] = integer(r, 8, 0)
            if old["purchasedAt"] == nil { old["purchasedAt"] = 0 }
            return old
        }.sorted(by: objectIdSort)

        let oldEmployees = indexById(objectArray(root["employees"]))
        let employeeRows = rows.filter { $0.first == "E" }
        root["employees"] = employeeRows.map { r -> [String: Any] in
            let id = text(r, 1, "")
            var old = oldEmployees[id] ?? [:]
            let legendary = text(r, 10, "")
            old["id"] = id
            old["name"] = text(r, 2, "Operador")
            old["specialty"] = text(r, 3, "TURNING")
            old["skillLevel"] = integer(r, 4, 1)
            old["experience"] = long(r, 5, 0)
            old["salaryCents"] = long(r, 6, 245_000)
            old["morale"] = integer(r, 7, 80)
            old["trait"] = text(r, 8, "Cuidadoso")
            let assigned = text(r, 9, "")
            old["assignedMachineId"] = assigned.isEmpty ? NSNull() : assigned
            old["isLegendary"] = !legendary.isEmpty
            old["legendaryCode"] = legendary.isEmpty ? NSNull() : legendary
            if old["hiredAt"] == nil { old["hiredAt"] = 0 }
            return old
        }.sorted(by: objectIdSort)

        let contractRows = rows.filter { $0.first == "C3" || $0.first == "C" }
        root["contracts"] = contractRows.map { r -> [String: Any] in
            let started = long(r, 14, 0)
            return [
                "id": text(r, 1, ""),
                "clientName": text(r, 2, "Cliente"),
                "contractType": text(r, 3, "GENERAL"),
                "quantity": integer(r, 4, 1),
                "completedQuantity": integer(r, 5, 0),
                "productionProgressMilli": long(r, 6, 0),
                "difficulty": integer(r, 7, 1),
                "requiredQuality": integer(r, 8, 50),
                "rewardCents": long(r, 9, 0),
                "penaltyCents": long(r, 10, 0),
                "reputationReward": integer(r, 11, 0),
                "reputationPenalty": integer(r, 12, 0),
                "generatedAt": long(r, 13, 0),
                "startedAt": started > 0 ? started : NSNull(),
                "deadlineAt": long(r, 15, 0),
                "status": text(r, 16, "AVAILABLE"),
            ]
        }.sorted(by: objectIdSort)

        root["finance"] = mergeHistory(
            base: objectArray(root["finance"]),
            current: rows.filter { $0.first == "F" }.map { r in
                [
                    "id": text(r, 1, ""),
                    "type": text(r, 2, "INCOME"),
                    "category": text(r, 3, "BONUS"),
                    "amountCents": long(r, 4, 0),
                    "description": text(r, 5, ""),
                    "createdAt": long(r, 6, 0),
                ] as [String: Any]
            },
            sort: financeSort
        )

        root["productionCargo"] = mergeHistory(
            base: objectArray(root["productionCargo"]),
            current: rows.filter { $0.first == "CARGO" }.map { r in
                let delivered = long(r, 6, 0)
                return [
                    "id": text(r, 1, ""),
                    "valueCents": long(r, 2, 0),
                    "unitsMilli": long(r, 3, 0),
                    "cycles": long(r, 4, 1),
                    "createdAt": long(r, 5, 0),
                    "deliveredAt": delivered > 0 ? delivered : NSNull(),
                ] as [String: Any]
            },
            sort: objectIdSort
        )

        let oldGoals = indexById(objectArray(root["goals"]))
        var mergedGoals = oldGoals
        for r in rows where r.first == "G3" || r.first == "G" {
            let id = text(r, 1, "")
            var old = oldGoals[id] ?? [:]
            old["id"] = id
            old["title"] = text(r, 2, id)
            old["target"] = integer(r, 3, 0)
            old["rewardCents"] = long(r, 4, 0)
            old["claimed"] = (r.first == "G3") ? boolean(r, 6, false) : boolean(r, 5, false)
            if old["progress"] == nil { old["progress"] = 0 }
            mergedGoals[id] = old
        }
        root["goals"] = Array(mergedGoals.values).sorted(by: objectIdSort)

        // facilities e legendaryMissions permanecem do JSON Android original.
        if root["facilities"] == nil { root["facilities"] = [] }
        if root["legendaryMissions"] == nil { root["legendaryMissions"] = [] }

        var preferences = dict(root["preferences"])
        var game = dict(preferences["game"])
        var profile = dict(preferences["profile"])
        var expansion = dict(preferences["expansion"])
        var activeGameplay = dict(preferences["activeGameplay"])
        var workLife = dict(preferences["workLife"])

        if let settings = rows.first(where: { $0.first == "SETTINGS" }) {
            game["boostTokens"] = integer(settings, 2, 2)
            game["snackImmunityUntil"] = long(settings, 3, 0)
            game["lastDailyRewardDay"] = androidDayKeyFromEpochDay(long(settings, 4, -1))

            let shift = text(settings, 1, "DAY_12H")
            let previous = string(workLife["mode"], "")
            workLife["mode"] = androidWorkLifeMode(shift: shift, previous: previous)
        }

        if let ux = rows.first(where: { $0.first == "UX3" }) {
            game["sound"] = boolean(ux, 1, true)
            game["vibration"] = boolean(ux, 2, true)
            game["npcSpeech"] = boolean(ux, 3, true)
            game["speechDurationSeconds"] = integer(ux, 4, 8)
        }

        if let workforce = rows.first(where: { $0.first == "WORKFORCE3" }) {
            let idle = text(workforce, 1, "")
            game["idleEmployeeId"] = idle.isEmpty ? NSNull() : idle
            game["idleSinceAt"] = long(workforce, 2, 0)
            game["idleUntilAt"] = long(workforce, 3, 0)
            game["nextIdleCheckAt"] = long(workforce, 4, 0)
        }

        if let mini = rows.first(where: { $0.first == "MINIGAME3" }) {
            game["lastMinigameAt"] = long(mini, 1, 0)
        }

        if let p = rows.first(where: { $0.first == "PROFILE3" }) {
            profile["displayName"] = text(p, 1, "Dono da Oficina")
            profile["gender"] = text(p, 2, "MALE")
            profile["skinStyle"] = text(p, 3, "WORKSHOP")
            profile["bodyType"] = text(p, 4, "STANDARD")
            profile["skinTone"] = text(p, 5, "MEDIUM")
            profile["hairStyle"] = text(p, 6, "SHORT")
            profile["hairColor"] = text(p, 7, "DARK")
            profile["uniformColor"] = text(p, 8, "NAVY")
            profile["helmetColor"] = text(p, 9, "YELLOW")
            profile["accessory"] = text(p, 10, "NONE")
            profile["onboardingComplete"] = boolean(p, 11, false)
        }

        if let e = rows.first(where: { $0.first == "EXP4" || $0.first == "EXP3" }) {
            expansion["specialty"] = text(e, 1, "generalista")
            expansion["companySkills"] = Array(decodeSet(text(e, 2, ""))).sorted()
            expansion["playerSkills"] = Array(decodeSet(text(e, 3, ""))).sorted()
            expansion["gachaTickets"] = integer(e, 4, 5)
            expansion["pityEpic"] = integer(e, 5, 0)
            expansion["pityLegendary"] = integer(e, 6, 0)
            expansion["ownedSkins"] = Array(decodeSet(text(e, 7, ""))).sorted()
            expansion["equippedSkin"] = text(e, 8, "operador_padrao")
            expansion["ownedCharacters"] = Array(decodeSet(text(e, 9, ""))).sorted()
            let equipped = text(e, 10, "")
            expansion["equippedCharacter"] = equipped.isEmpty ? NSNull() : equipped
            expansion["tools"] = decodeIntMap(text(e, 11, ""))
            expansion["contractTools"] = decodeStringMap(text(e, 12, ""))
            expansion["premiumMachines"] = Array(decodeSet(text(e, 13, ""))).sorted()
            expansion["playerXp"] = long(e, 14, 0)
            expansion["lastDailyTicketDay"] = long(e, 15, -1)
            if e.first == "EXP4" {
                expansion["claimedRentalXpIds"] = Array(decodeSet(text(e, 16, ""))).sorted()
                let remoteOwner = text(e, 17, "")
                let remoteName = text(e, 18, "")
                expansion["remoteHireOwnerUid"] = remoteOwner.isEmpty ? NSNull() : remoteOwner
                expansion["remoteHireName"] = remoteName.isEmpty ? NSNull() : remoteName
                expansion["remoteHireBoostPct"] = integer(e, 19, 0)
                expansion["remoteHireEndsAt"] = long(e, 20, 0)
            }
        }

        // Carreira industrial V20/V24.
        if let c = rows.first(where: { $0.first == "CAREER4" }) {
            activeGameplay["manualOps"] = integer(c, 1, 0)
            activeGameplay["assistedOps"] = integer(c, 2, 0)
            activeGameplay["perfectOps"] = integer(c, 3, 0)
            activeGameplay["approvedBatches"] = integer(c, 4, 0)
            activeGameplay["shippedBatches"] = integer(c, 5, 0)
            activeGameplay["reworkedBatches"] = integer(c, 6, 0)
            activeGameplay["scrappedBatches"] = integer(c, 7, 0)
            activeGameplay["bestScore"] = integer(c, 8, 0)
            activeGameplay["streak"] = integer(c, 9, 0)
            activeGameplay["skillPoints"] = integer(c, 10, 1)
            activeGameplay["productionPolicy"] = text(c, 11, "BALANCED")
            activeGameplay["lastOperationAt"] = long(c, 12, 0)
            activeGameplay["milestones"] = Array(decodeSet(text(c, 13, ""))).sorted()
            activeGameplay["achievements"] = Array(decodeSet(text(c, 14, ""))).sorted()
        }
        if let c = rows.first(where: { $0.first == "CAREER_SKILLS4" }) {
            activeGameplay["industrialSkills"] = Array(decodeSet(text(c, 1, ""))).sorted()
            let mastery = decodeIntMap(text(c, 2, ""))
            activeGameplay["masteryXp"] = mastery.keys.sorted().map {
                "\($0)=\(mastery[$0] ?? 0)"
            }
        }
        if let b = rows.first(where: { $0.first == "CAREER_BATCH4" }) {
            activeGameplay["activeBatch"] = [
                text(b, 1, ""),
                text(b, 2, ""),
                text(b, 3, ""),
                text(b, 4, ""),
                text(b, 5, "MACHINED"),
                String(integer(b, 6, 1)),
                String(integer(b, 7, 50)),
                String(integer(b, 8, 0)),
                String(integer(b, 9, 0)),
                String(integer(b, 10, 0)),
                boolean(b, 11, false) ? "1" : "0",
                boolean(b, 12, false) ? "1" : "0",
                String(integer(b, 13, 0)),
                String(long(b, 14, 0)),
                String(long(b, 15, 0)),
            ].joined(separator: "§")
        } else {
            activeGameplay["activeBatch"] = NSNull()
        }

        let oldMissions = indexById(objectArray(root["legendaryMissions"]))
        var mergedMissions = oldMissions
        for m in rows where m.first == "LM4" {
            let id = text(m, 1, "")
            mergedMissions[id] = [
                "id": id,
                "legendaryCode": text(m, 2, ""),
                "title": text(m, 3, ""),
                "description": text(m, 4, ""),
                "metric": text(m, 5, ""),
                "target": long(m, 6, 0),
                "progress": long(m, 7, 0),
                "rewardCents": long(m, 8, 0),
                "claimed": boolean(m, 9, false),
            ]
        }
        root["legendaryMissions"] = Array(mergedMissions.values).sorted(by: objectIdSort)

        var fatigue: [String: Double] = [:]
        var resting: [String: Int64] = [:]
        for r in employeeRows {
            let id = text(r, 1, "")
            fatigue[id] = double(r, 11, 0)
            resting[id] = long(r, 12, 0)
        }
        if let w = rows.first(where: { $0.first == "WORKLIFE4" }) {
            fatigue["__main_player__"] = double(w, 2, 0)
            resting["__main_player__"] = long(w, 3, 0)
            workLife["autoRest"] = boolean(w, 1, true)
        } else if workLife["autoRest"] == nil {
            workLife["autoRest"] = true
        }
        workLife["preciseFatigue"] = encodeDoublePipeMap(fatigue)
        workLife["fatigue"] = encodeIntPipeMap(fatigue.mapValues { Int($0.rounded()) })
        workLife["resting"] = encodeLongPipeMap(resting)

        preferences["game"] = game
        preferences["profile"] = profile
        preferences["expansion"] = expansion
        preferences["activeGameplay"] = activeGameplay
        preferences["workLife"] = workLife
        root["preferences"] = preferences

        return root
    }

    // MARK: - Opaque storage

    private func supportDirectory() throws -> URL {
        let base = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let dir = base.appendingPathComponent("UsinagemMasterCloudV23", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func opaqueURL() throws -> URL {
        try supportDirectory().appendingPathComponent("android-cloud-save.json")
    }

    private func backupURL() throws -> URL {
        try supportDirectory().appendingPathComponent("last-pre-restore-backup.json")
    }

    private func readOpaqueJSON() throws -> String {
        let data = try Data(contentsOf: opaqueURL())
        guard let text = String(data: data, encoding: .utf8) else {
            throw V23AdapterError.invalidLocalSave("Cache Android local inválido.")
        }
        return text
    }

    private func readOpaqueObject() throws -> [String: Any] {
        let text = try readOpaqueJSON()
        guard let data = text.data(using: .utf8),
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw V23AdapterError.invalidLocalSave("Cache Android local não pôde ser interpretado.")
        }
        return root
    }

    private func writeOpaqueJSON(_ text: String) throws {
        try Data(text.utf8).write(to: opaqueURL(), options: .atomic)
    }

    private func writeRestoreBackup(kmp: String?, androidJSON: String?) throws {
        let payload: [String: Any] = [
            "createdAtMs": Int64(Date().timeIntervalSince1970 * 1000.0),
            "kmp": kmp ?? NSNull(),
            "androidJSON": androidJSON ?? NSNull(),
        ]
        let data = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
        try data.write(to: backupURL(), options: .atomic)
    }

    // MARK: - Serialization helpers

    func sha256(_ value: String) -> String {
        let digest = SHA256.hash(data: Data(value.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private func canonicalJSON(_ object: [String: Any]) throws -> String {
        guard JSONSerialization.isValidJSONObject(object) else {
            throw V23AdapterError.invalidLocalSave("O snapshot convertido não é um JSON Firestore válido.")
        }
        let data = try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
        guard let text = String(data: data, encoding: .utf8) else {
            throw V23AdapterError.invalidLocalSave("Falha ao codificar o snapshot da oficina.")
        }
        return text
    }

    private func emptyAndroidRoot() -> [String: Any] {
        [
            "schema": 1,
            "machines": [],
            "employees": [],
            "contracts": [],
            "finance": [],
            "facilities": [],
            "goals": [],
            "legendaryMissions": [],
            "productionCargo": [],
            "preferences": [
                "game": [:],
                "profile": [:],
                "expansion": [:],
                "activeGameplay": [:],
                "workLife": [:],
            ],
        ]
    }

    private func parseRows(_ raw: String) -> [[String]] {
        raw.split(whereSeparator: \.isNewline).map { line in
            line.split(separator: "|", omittingEmptySubsequences: false)
                .map { kmpUnescape(String($0)) }
        }
    }

    private func row(_ values: [Any]) -> String {
        values.map { value in
            if value is NSNull { return "" }
            return kmpEscape(String(describing: value))
        }.joined(separator: "|") + "\n"
    }

    private func kmpEscape(_ value: String) -> String {
        value
            .replacingOccurrences(of: "%", with: "%25")
            .replacingOccurrences(of: "|", with: "%7C")
            .replacingOccurrences(of: "\n", with: "%0A")
            .replacingOccurrences(of: "\r", with: "%0D")
            .replacingOccurrences(of: ";", with: "%3B")
            .replacingOccurrences(of: "=", with: "%3D")
    }

    private func kmpUnescape(_ value: String) -> String {
        value
            .replacingOccurrences(of: "%0D", with: "\r")
            .replacingOccurrences(of: "%0A", with: "\n")
            .replacingOccurrences(of: "%7C", with: "|")
            .replacingOccurrences(of: "%3B", with: ";")
            .replacingOccurrences(of: "%3D", with: "=")
            .replacingOccurrences(of: "%25", with: "%")
    }

    private func encodeSet(_ values: [String]) -> String {
        Array(Set(values)).sorted().map(kmpEscape).joined(separator: ";")
    }

    private func decodeSet(_ value: String) -> Set<String> {
        guard !value.isEmpty else { return [] }
        return Set(
            value.split(separator: ";", omittingEmptySubsequences: false)
                .map { kmpUnescape(String($0)) }
                .filter { !$0.isEmpty }
        )
    }

    private func encodeIntMap(_ values: [String: Int]) -> String {
        values.keys.sorted().map { "\(kmpEscape($0))=\(values[$0] ?? 0)" }.joined(separator: ";")
    }

    private func decodeIntMap(_ value: String) -> [String: Int] {
        var out: [String: Int] = [:]
        for token in value.split(separator: ";", omittingEmptySubsequences: false) {
            let pair = token.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard pair.count == 2, let number = Int(pair[1]) else { continue }
            out[kmpUnescape(String(pair[0]))] = number
        }
        return out
    }

    private func encodeStringMap(_ values: [String: String]) -> String {
        values.keys.sorted().map {
            "\(kmpEscape($0))=\(kmpEscape(values[$0] ?? ""))"
        }.joined(separator: ";")
    }

    private func decodeStringMap(_ value: String) -> [String: String] {
        var out: [String: String] = [:]
        for token in value.split(separator: ";", omittingEmptySubsequences: false) {
            let pair = token.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard pair.count == 2 else { continue }
            out[kmpUnescape(String(pair[0]))] = kmpUnescape(String(pair[1]))
        }
        return out
    }

    private func parseIntMap(_ raw: String) -> [String: Int] {
        var out: [String: Int] = [:]
        for token in raw.split(separator: "|") {
            let pair = token.split(separator: "=", maxSplits: 1)
            guard pair.count == 2, let value = Int(pair[1]) else { continue }
            out[String(pair[0])] = value
        }
        return out
    }

    private func parseDoubleMap(_ raw: String) -> [String: Double] {
        var out: [String: Double] = [:]
        for token in raw.split(separator: "|") {
            let pair = token.split(separator: "=", maxSplits: 1)
            guard pair.count == 2, let value = Double(pair[1]) else { continue }
            out[String(pair[0])] = value
        }
        return out
    }

    private func parseLongMap(_ raw: String) -> [String: Int64] {
        var out: [String: Int64] = [:]
        for token in raw.split(separator: "|") {
            let pair = token.split(separator: "=", maxSplits: 1)
            guard pair.count == 2, let value = Int64(pair[1]) else { continue }
            out[String(pair[0])] = value
        }
        return out
    }

    private func encodeDoublePipeMap(_ values: [String: Double]) -> String {
        values.keys.sorted().map { "\($0)=\(values[$0] ?? 0)" }.joined(separator: "|")
    }

    private func encodeIntPipeMap(_ values: [String: Int]) -> String {
        values.keys.sorted().map { "\($0)=\(values[$0] ?? 0)" }.joined(separator: "|")
    }

    private func encodeLongPipeMap(_ values: [String: Int64]) -> String {
        values.keys.sorted().map { "\($0)=\(values[$0] ?? 0)" }.joined(separator: "|")
    }

    private func dict(_ value: Any?) -> [String: Any] {
        value as? [String: Any] ?? [:]
    }

    private func objectArray(_ value: Any?) -> [[String: Any]] {
        value as? [[String: Any]] ?? []
    }

    private func stringArray(_ value: Any?) -> [String] {
        (value as? [Any] ?? []).compactMap { $0 as? String }
    }

    private func intDictionary(_ value: Any?) -> [String: Int] {
        guard let values = value as? [String: Any] else { return [:] }
        return values.reduce(into: [:]) { result, pair in
            if let n = pair.value as? NSNumber { result[pair.key] = n.intValue }
            else if let n = pair.value as? Int { result[pair.key] = n }
        }
    }

    private func stringDictionary(_ value: Any?) -> [String: String] {
        guard let values = value as? [String: Any] else { return [:] }
        return values.reduce(into: [:]) { result, pair in
            if let s = pair.value as? String { result[pair.key] = s }
        }
    }

    private func string(_ value: Any?, _ fallback: String) -> String {
        if value is NSNull { return fallback }
        return (value as? String) ?? fallback
    }

    private func int(_ value: Any?, _ fallback: Int) -> Int {
        if let n = value as? NSNumber { return n.intValue }
        if let n = value as? Int { return n }
        if let n = value as? Int64 { return Int(n) }
        return fallback
    }

    private func i64(_ value: Any?, _ fallback: Int64) -> Int64 {
        if let n = value as? NSNumber { return n.int64Value }
        if let n = value as? Int64 { return n }
        if let n = value as? Int { return Int64(n) }
        return fallback
    }

    private func bool(_ value: Any?, _ fallback: Bool) -> Bool {
        if let n = value as? NSNumber { return n.boolValue }
        if let b = value as? Bool { return b }
        return fallback
    }

    private func text(_ row: [String], _ index: Int, _ fallback: String) -> String {
        guard row.indices.contains(index), !row[index].isEmpty else { return fallback }
        return row[index]
    }

    private func integer(_ row: [String], _ index: Int, _ fallback: Int) -> Int {
        guard row.indices.contains(index), let value = Int(row[index]) else { return fallback }
        return value
    }

    private func long(_ row: [String], _ index: Int, _ fallback: Int64) -> Int64 {
        guard row.indices.contains(index), let value = Int64(row[index]) else { return fallback }
        return value
    }

    private func double(_ row: [String], _ index: Int, _ fallback: Double) -> Double {
        guard row.indices.contains(index), let value = Double(row[index]) else { return fallback }
        return value
    }

    private func boolean(_ row: [String], _ index: Int, _ fallback: Bool) -> Bool {
        guard row.indices.contains(index) else { return fallback }
        switch row[index].lowercased() {
        case "true": return true
        case "false": return false
        default: return fallback
        }
    }

    private func indexById(_ values: [[String: Any]]) -> [String: [String: Any]] {
        Dictionary(uniqueKeysWithValues: values.compactMap { item in
            let id = string(item["id"], "")
            return id.isEmpty ? nil : (id, item)
        })
    }

    private func mergeHistory(
        base: [[String: Any]],
        current: [[String: Any]],
        sort: ([[String: Any]].Element, [[String: Any]].Element) -> Bool
    ) -> [[String: Any]] {
        var merged = indexById(base)
        for item in current {
            let id = string(item["id"], "")
            if !id.isEmpty { merged[id] = item }
        }
        return Array(merged.values).sorted(by: sort)
    }

    private func objectIdSort(_ a: [String: Any], _ b: [String: Any]) -> Bool {
        string(a["id"], "") < string(b["id"], "")
    }

    private func financeSort(_ a: [String: Any], _ b: [String: Any]) -> Bool {
        let at = i64(a["createdAt"], 0)
        let bt = i64(b["createdAt"], 0)
        if at != bt { return at < bt }
        return string(a["id"], "") < string(b["id"], "")
    }

    private func sectorForMachine(_ type: String) -> String {
        switch type {
        case "MECHANICAL_LATHE", "CNC_LATHE":
            return "TURNING"
        case "UNIVERSAL_MILL", "CNC_MACHINING_CENTER_3_AXIS",
             "CNC_MACHINING_CENTER_5_AXIS", "EDM":
            return "MILLING"
        case "COLUMN_DRILL", "CNC_DRILL":
            return "DRILLING"
        case "CYLINDRICAL_GRINDER", "CNC_GRINDER":
            return "GRINDING"
        default:
            return "BOILERMAKING"
        }
    }

    private func ticketReward(_ id: String) -> Int {
        switch id {
        case "thirty_machines": return 10
        case "thirty_employees": return 8
        case "reputation_250": return 10
        case "reputation_500": return 20
        case "company_level_20": return 15
        case "warehouse_500": return 10
        default: return 0
        }
    }

    private func epochDayFromAndroidDayKey(_ key: Int) -> Int64 {
        guard key > 0 else { return -1 }
        let year = key / 1000
        let dayOfYear = key % 1000
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        guard let jan1 = calendar.date(from: DateComponents(year: year, month: 1, day: 1)),
              let date = calendar.date(byAdding: .day, value: max(0, dayOfYear - 1), to: jan1) else {
            return -1
        }
        return Int64(date.timeIntervalSince1970 / 86_400.0)
    }

    private func androidDayKeyFromEpochDay(_ epochDay: Int64) -> Int {
        guard epochDay >= 0 else { return 0 }
        let date = Date(timeIntervalSince1970: TimeInterval(epochDay) * 86_400.0 + 43_200.0)
        let calendar = Calendar(identifier: .gregorian)
        let year = calendar.component(.year, from: date)
        let ordinal = calendar.ordinality(of: .day, in: .year, for: date) ?? 1
        return year * 1000 + ordinal
    }

    private func androidWorkLifeMode(shift: String, previous: String) -> String {
        let continuous = shift.uppercased().contains("24")
        let old = previous.uppercased()
        if continuous {
            if old.contains("24") { return previous }
            return "continuous_24h"
        } else {
            if old.contains("12") || old.contains("SHIFT") { return previous }
            return "shift_12h"
        }
    }
}
