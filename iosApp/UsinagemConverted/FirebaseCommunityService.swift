import Foundation
import FirebaseAuth
import FirebaseFirestore

private struct LinkedFactoryProfile {
    let source: String
    let uid: String
    let playerName: String
    let companyName: String
    let companyLevel: Int
    let reputation: Int
}

final class FirebaseCommunityService {
    static let shared = FirebaseCommunityService()

    static let cacheKey = "usinagemmaster.online.community.cache"
    static let publishKey = "usinagemmaster.online.community.publish"
    static let hireKey = "usinagemmaster.online.community.hire"
    static let linkedCompanyKey = "usinagemmaster.online.community.linkedCompany"

    private let db = Firestore.firestore()
    private var started = false

    private init() {}

    func start() {
        guard !started else { return }
        started = true

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(refreshNotification),
            name: Notification.Name("UsinagemCommunityRefresh"),
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(publishNotification),
            name: Notification.Name("UsinagemCommunityPublish"),
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(hireNotification),
            name: Notification.Name("UsinagemCommunityHire"),
            object: nil
        )

        if Auth.auth().currentUser != nil {
            refreshAll()
        } else {
            clear()
        }
    }

    func clear() {
        UserDefaults.standard.removeObject(forKey: Self.linkedCompanyKey)
        cache(
            linked: nil,
            factories: [],
            offers: [],
            message: nil,
            errors: []
        )
    }

    @objc private func refreshNotification() {
        refreshAll()
    }

    @objc private func publishNotification() {
        guard let raw = UserDefaults.standard.string(forKey: Self.publishKey) else {
            return
        }
        publish(raw: raw)
    }

    @objc private func hireNotification() {
        guard let ownerUid = UserDefaults.standard.string(forKey: Self.hireKey),
              !ownerUid.isEmpty else {
            return
        }
        hire(ownerUid: ownerUid)
    }

    func refreshAll(message: String? = nil) {
        guard let user = googleUser() else {
            clear()
            return
        }

        let group = DispatchGroup()
        let lock = NSLock()

        var linked: LinkedFactoryProfile?
        var factories: [[String: Any]] = []
        var offers: [[String: Any]] = []
        var errors: [String] = []

        group.enter()
        resolveLinkedProfile(uid: user.uid) { profile, error in
            lock.lock()
            linked = profile
            if let error { errors.append("Perfil: \(error.localizedDescription)") }
            lock.unlock()
            group.leave()
        }

        group.enter()
        db.collection("public_factories")
            .limit(to: 80)
            .getDocuments { snapshot, error in
                lock.lock()
                if let error {
                    errors.append("Fábricas: \(error.localizedDescription)")
                } else {
                    factories = snapshot?.documents
                        .filter { $0.documentID != user.uid }
                        .map { document in
                            var data = document.data()
                            data["_documentId"] = document.documentID
                            return data
                        } ?? []
                }
                lock.unlock()
                group.leave()
            }

        group.enter()
        db.collection("character_offers")
            .limit(to: 50)
            .getDocuments { snapshot, error in
                lock.lock()
                if let error {
                    errors.append("Mercado: \(error.localizedDescription)")
                } else {
                    let now = Date().timeIntervalSince1970 * 1000.0
                    offers = snapshot?.documents.compactMap { document in
                        var data = document.data()
                        let owner = self.string(data["ownerUid"], fallback: document.documentID)
                        guard owner != user.uid else { return nil }

                        let leasedUntil = self.millis(data["leasedUntil"])
                        guard leasedUntil <= now else { return nil }

                        data["_documentId"] = document.documentID
                        return data
                    } ?? []
                }
                lock.unlock()
                group.leave()
            }

        group.notify(queue: .main) {
            self.cache(
                linked: linked,
                factories: factories,
                offers: offers,
                message: message,
                errors: errors
            )
        }
    }

    private func resolveLinkedProfile(
        uid: String,
        completion: @escaping (LinkedFactoryProfile?, Error?) -> Void
    ) {
        let current = db.collection("public_factories").document(uid)
        current.getDocument { snapshot, currentError in
            if let snapshot, snapshot.exists {
                completion(self.linkedProfile(
                    source: "public_factories",
                    uid: uid,
                    data: snapshot.data() ?? [:]
                ), nil)
                return
            }

            let legacy = self.db.collection("players").document(uid)
            legacy.getDocument { legacySnapshot, legacyError in
                if let legacySnapshot, legacySnapshot.exists {
                    completion(self.linkedProfile(
                        source: "players",
                        uid: uid,
                        data: legacySnapshot.data() ?? [:]
                    ), nil)
                    return
                }

                let accounts = self.db.collection("player_accounts").document(uid)
                accounts.getDocument { accountSnapshot, accountError in
                    if let accountSnapshot, accountSnapshot.exists {
                        completion(self.linkedProfile(
                            source: "player_accounts",
                            uid: uid,
                            data: accountSnapshot.data() ?? [:]
                        ), nil)
                        return
                    }

                    // Missing documents are not an error. Surface permission/network
                    // errors only when no compatible profile was found.
                    completion(
                        nil,
                        accountError ?? legacyError ?? currentError
                    )
                }
            }
        }
    }

    private func linkedProfile(
        source: String,
        uid: String,
        data: [String: Any]
    ) -> LinkedFactoryProfile {
        let user = Auth.auth().currentUser
        let fallbackName =
            user?.displayName ??
            user?.email?.split(separator: "@").first.map(String.init) ??
            "Jogador"

        return LinkedFactoryProfile(
            source: source,
            uid: uid,
            playerName: string(
                data["playerName"] ?? data["displayName"],
                fallback: fallbackName
            ),
            companyName: string(data["companyName"], fallback: ""),
            companyLevel: int(data["companyLevel"], fallback: 1),
            reputation: int(data["reputation"], fallback: 0)
        )
    }

    private func publish(raw: String) {
        guard let user = googleUser() else {
            cacheError("Faça login com uma conta Google real antes de publicar.")
            return
        }

        guard let publication = decodePublication(raw) else {
            cacheError("Snapshot local inválido; a fábrica não foi publicada.")
            return
        }

        let playerName =
            user.displayName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
                ? user.displayName!
                : (user.email?.split(separator: "@").first.map(String.init) ?? publication.playerName)

        var payload: [String: Any] = [
            "uid": user.uid,
            "playerName": playerName,
            "companyName": publication.companyName,
            "companyLevel": publication.companyLevel,
            "reputation": publication.reputation,
            "specialty": publication.specialty,
            "employeeCount": publication.employeeCount,
            "updatedAt": Int64(Date().timeIntervalSince1970 * 1000.0),
            "machines": publication.machines,
            "workers": publication.workers,
            "productionPer10Minutes": publication.productionPer10Minutes,
            "activeContracts": publication.activeContracts,
            "pendingLots": publication.pendingLots,
            "ownerAvatar": publication.ownerAvatar,
            "ownerStage": publication.ownerStage,
            "ownerCarrying": publication.ownerCarrying,
        ]
        payload["ownerMachineId"] = publication.ownerMachineId ?? NSNull()

        db.collection("public_factories")
            .document(user.uid)
            .setData(payload, merge: true) { error in
                if let error {
                    self.cacheError("Publicação: \(error.localizedDescription)")
                    return
                }
                self.refreshAll(message: "Fábrica publicada/atualizada no Firebase.")
            }
    }

    private struct Publication {
        let playerName: String
        let companyName: String
        let companyLevel: Int
        let reputation: Int
        let specialty: String
        let employeeCount: Int
        let productionPer10Minutes: Double
        let activeContracts: Int
        let pendingLots: Int
        let ownerAvatar: String
        let ownerStage: String
        let ownerMachineId: String?
        let ownerCarrying: Bool
        let machines: [[String: Any]]
        let workers: [[String: Any]]
    }

    private func decodePublication(_ raw: String) -> Publication? {
        let p = raw.components(separatedBy: "|")
        guard p.count >= 16, p[0] == "V1" else { return nil }

        let machines = p[14].isEmpty ? [] : p[14]
            .components(separatedBy: "^")
            .compactMap { row -> [String: Any]? in
                let c = row.components(separatedBy: "~")
                guard c.count >= 9 else { return nil }
                return [
                    "id": unescape(c[0]),
                    "type": unescape(c[1]),
                    "name": unescape(c[2]),
                    "level": Int(c[3]) ?? 1,
                    "x": Int(c[4]) ?? 0,
                    "y": Int(c[5]) ?? 0,
                    "premium": c[6] == "1",
                    "operating": c[7] == "1",
                    "condition": Int(c[8]) ?? 1000,
                ]
            }

        let workers = p[15].isEmpty ? [] : p[15]
            .components(separatedBy: "^")
            .compactMap { row -> [String: Any]? in
                let c = row.components(separatedBy: "~")
                guard c.count >= 5 else { return nil }
                return [
                    "id": unescape(c[0]),
                    "name": unescape(c[1]),
                    "specialty": unescape(c[2]),
                    "skillLevel": Int(c[3]) ?? 1,
                    "assignedMachineId": unescape(c[4]),
                ]
            }

        return Publication(
            playerName: unescape(p[1]),
            companyName: unescape(p[2]),
            companyLevel: Int(p[3]) ?? 1,
            reputation: Int(p[4]) ?? 0,
            specialty: unescape(p[5]),
            employeeCount: Int(p[6]) ?? 0,
            productionPer10Minutes: Double(p[7]) ?? 0,
            activeContracts: Int(p[8]) ?? 0,
            pendingLots: Int(p[9]) ?? 0,
            ownerAvatar: unescape(p[10]),
            ownerStage: unescape(p[11]),
            ownerMachineId: unescape(p[12]).isEmpty ? nil : unescape(p[12]),
            ownerCarrying: p[13] == "1",
            machines: machines,
            workers: workers
        )
    }

    private func hire(ownerUid: String) {
        guard let user = googleUser() else {
            cacheError("Faça login com Google primeiro.")
            return
        }
        guard ownerUid != user.uid else {
            cacheError("Você não pode contratar seu próprio personagem.")
            return
        }

        let offerRef = db.collection("character_offers").document(ownerUid)
        let now = Date()
        let endsAt = now.addingTimeInterval(48 * 60 * 60)

        db.runTransaction({ transaction, errorPointer -> Any? in
            let snapshot: DocumentSnapshot
            do {
                snapshot = try transaction.getDocument(offerRef)
            } catch let error as NSError {
                errorPointer?.pointee = error
                return nil
            }

            guard snapshot.exists else {
                errorPointer?.pointee = NSError(
                    domain: "UsinagemMaster",
                    code: 404,
                    userInfo: [NSLocalizedDescriptionKey: "Oferta não está mais disponível."]
                )
                return nil
            }

            let data = snapshot.data() ?? [:]
            let leasedUntil = self.millis(data["leasedUntil"])
            let nowMillis = now.timeIntervalSince1970 * 1000.0

            guard leasedUntil <= nowMillis else {
                errorPointer?.pointee = NSError(
                    domain: "UsinagemMaster",
                    code: 409,
                    userInfo: [NSLocalizedDescriptionKey: "Esse profissional acabou de ser contratado por outra empresa."]
                )
                return nil
            }

            transaction.updateData(
                [
                    "leasedBy": user.uid,
                    "leasedUntil": Timestamp(date: endsAt),
                ],
                forDocument: offerRef
            )
            return nil
        }) { _, error in
            if let error {
                self.cacheError("Contratação: \(error.localizedDescription)")
                return
            }

            offerRef.getDocument { snapshot, readError in
                if let readError {
                    self.cacheError("Contratação confirmada, mas não consegui ler a oferta: \(readError.localizedDescription)")
                    return
                }

                let data = snapshot?.data() ?? [:]
                let playerName = self.string(data["playerName"], fallback: "Profissional conectado")
                let boostPct = self.int(data["boostPct"], fallback: 4)

                self.db.collection("character_rentals").addDocument(
                    data: [
                        "ownerUid": ownerUid,
                        "renterUid": user.uid,
                        "playerName": playerName,
                        "boostPct": boostPct,
                        "startedAt": Timestamp(date: now),
                        "endsAt": Timestamp(date: endsAt),
                    ]
                ) { rentalError in
                    if let rentalError {
                        self.cacheError("Aluguel reservado, mas o registro falhou: \(rentalError.localizedDescription)")
                    } else {
                        self.refreshAll(message: "\(playerName) contratado por 48 horas (+\(boostPct)%).")
                    }
                }
            }
        }
    }

    private func googleUser() -> User? {
        guard let user = Auth.auth().currentUser,
              !user.email.isNilOrEmpty,
              user.providerData.contains(where: { $0.providerID == "google.com" }) else {
            return nil
        }
        return user
    }

    private func cache(
        linked: LinkedFactoryProfile?,
        factories: [[String: Any]],
        offers: [[String: Any]],
        message: String?,
        errors: [String]
    ) {
        let user = Auth.auth().currentUser
        var lines: [String] = []

        lines.append([
            "ACCOUNT",
            user == nil ? "0" : "1",
            escape(user?.uid ?? ""),
            escape(user?.displayName ?? ""),
            escape(user?.email ?? ""),
        ].joined(separator: "|"))

        if let linked {
            lines.append([
                "ME",
                escape(linked.source),
                escape(linked.uid),
                escape(linked.playerName),
                escape(linked.companyName),
                String(linked.companyLevel),
                String(linked.reputation),
            ].joined(separator: "|"))

            if !linked.companyName.isEmpty {
                UserDefaults.standard.set(linked.companyName, forKey: Self.linkedCompanyKey)
                let identity = user?.displayName ?? user?.email ?? "Conta Google"
                UserDefaults.standard.set(
                    "Google • \(identity) • \(linked.companyName)",
                    forKey: FirebaseAccountService.accountLabelKey
                )
            }
        } else {
            UserDefaults.standard.removeObject(forKey: Self.linkedCompanyKey)
        }

        for data in factories {
            let documentId = string(data["_documentId"], fallback: "")
            let uid = string(data["uid"], fallback: documentId)
            let machines = encodeMachines(data["machines"])
            let workers = encodeWorkers(data["workers"])

            lines.append([
                "FACTORY",
                escape(uid),
                escape(string(data["playerName"], fallback: "Jogador")),
                escape(string(data["companyName"], fallback: "Usinagem conectada")),
                String(int(data["companyLevel"], fallback: 1)),
                String(int(data["reputation"], fallback: 0)),
                escape(string(data["specialty"], fallback: "generalista")),
                String(int(data["employeeCount"], fallback: 0)),
                String(Int64(millis(data["updatedAt"]))),
                String(double(data["productionPer10Minutes"], fallback: 0)),
                String(int(data["activeContracts"], fallback: 0)),
                String(int(data["pendingLots"], fallback: 0)),
                escape(string(data["ownerAvatar"], fallback: "WORKSHOP")),
                escape(string(data["ownerStage"], fallback: "")),
                escape(string(data["ownerMachineId"], fallback: "")),
                bool(data["ownerCarrying"]) ? "1" : "0",
                machines,
                workers,
            ].joined(separator: "|"))
        }

        for data in offers {
            let documentId = string(data["_documentId"], fallback: "")
            let owner = string(data["ownerUid"], fallback: documentId)
            let skills = (data["skills"] as? [Any] ?? [])
                .compactMap { $0 as? String }
                .map(escape)
                .joined(separator: ",")

            lines.append([
                "OFFER",
                escape(owner),
                escape(string(data["playerName"], fallback: "Profissional conectado")),
                String(int(data["boostPct"], fallback: 4)),
                String(Int64(millis(data["leasedUntil"]))),
                skills,
            ].joined(separator: "|"))
        }

        if let message, !message.isEmpty {
            lines.append("MESSAGE|\(escape(message))")
        }

        if !errors.isEmpty {
            lines.append("ERROR|\(escape(errors.joined(separator: " • ")))")
        }

        lines.append("STATUS|\(Int64(Date().timeIntervalSince1970 * 1000.0))")

        UserDefaults.standard.set(lines.joined(separator: "\n"), forKey: Self.cacheKey)
        NotificationCenter.default.post(
            name: Notification.Name("UsinagemCommunityDidUpdate"),
            object: nil
        )
    }

    private func cacheError(_ text: String) {
        UserDefaults.standard.set(
            "ACCOUNT|1|\(escape(Auth.auth().currentUser?.uid ?? ""))|" +
                "\(escape(Auth.auth().currentUser?.displayName ?? ""))|" +
                "\(escape(Auth.auth().currentUser?.email ?? ""))\n" +
                "ERROR|\(escape(text))\n" +
                "STATUS|\(Int64(Date().timeIntervalSince1970 * 1000.0))",
            forKey: Self.cacheKey
        )
        NotificationCenter.default.post(
            name: Notification.Name("UsinagemCommunityDidUpdate"),
            object: nil
        )
    }

    private func encodeMachines(_ value: Any?) -> String {
        let rows = value as? [Any] ?? []
        return rows.compactMap { item -> String? in
            guard let data = item as? [String: Any] else { return nil }
            return [
                escape(string(data["id"], fallback: "")),
                escape(string(data["type"], fallback: "")),
                escape(string(data["name"], fallback: "Máquina")),
                String(int(data["level"], fallback: 1)),
                String(int(data["x"], fallback: 0)),
                String(int(data["y"], fallback: 0)),
                bool(data["premium"]) ? "1" : "0",
                bool(data["operating"]) ? "1" : "0",
                String(int(data["condition"], fallback: 1000)),
            ].joined(separator: "~")
        }.joined(separator: "^")
    }

    private func encodeWorkers(_ value: Any?) -> String {
        let rows = value as? [Any] ?? []
        return rows.compactMap { item -> String? in
            guard let data = item as? [String: Any] else { return nil }
            return [
                escape(string(data["id"], fallback: "")),
                escape(string(data["name"], fallback: "Operador")),
                escape(string(data["specialty"], fallback: "GENERALIST")),
                String(int(data["skillLevel"], fallback: 1)),
                escape(string(data["assignedMachineId"], fallback: "")),
            ].joined(separator: "~")
        }.joined(separator: "^")
    }

    private func string(_ value: Any?, fallback: String) -> String {
        if let value = value as? String, !value.isEmpty { return value }
        return fallback
    }

    private func int(_ value: Any?, fallback: Int) -> Int {
        if let number = value as? NSNumber { return number.intValue }
        if let value = value as? Int { return value }
        if let value = value as? Int64 { return Int(value) }
        return fallback
    }

    private func double(_ value: Any?, fallback: Double) -> Double {
        if let number = value as? NSNumber { return number.doubleValue }
        if let value = value as? Double { return value }
        return fallback
    }

    private func bool(_ value: Any?) -> Bool {
        if let number = value as? NSNumber { return number.boolValue }
        if let value = value as? Bool { return value }
        return false
    }

    private func millis(_ value: Any?) -> Double {
        if let timestamp = value as? Timestamp {
            return timestamp.dateValue().timeIntervalSince1970 * 1000.0
        }
        if let number = value as? NSNumber {
            return number.doubleValue
        }
        if let value = value as? Double {
            return value
        }
        if let value = value as? Int64 {
            return Double(value)
        }
        return 0
    }

    private func escape(_ value: String) -> String {
        value
            .replacingOccurrences(of: "%", with: "%25")
            .replacingOccurrences(of: "|", with: "%7C")
            .replacingOccurrences(of: "~", with: "%7E")
            .replacingOccurrences(of: "^", with: "%5E")
            .replacingOccurrences(of: ",", with: "%2C")
            .replacingOccurrences(of: "\n", with: "%0A")
            .replacingOccurrences(of: "\r", with: "%0D")
    }

    private func unescape(_ value: String) -> String {
        value
            .replacingOccurrences(of: "%0D", with: "\r")
            .replacingOccurrences(of: "%0A", with: "\n")
            .replacingOccurrences(of: "%2C", with: ",")
            .replacingOccurrences(of: "%5E", with: "^")
            .replacingOccurrences(of: "%7E", with: "~")
            .replacingOccurrences(of: "%7C", with: "|")
            .replacingOccurrences(of: "%25", with: "%")
    }
}

private extension Optional where Wrapped == String {
    var isNilOrEmpty: Bool {
        self?.isEmpty != false
    }
}
