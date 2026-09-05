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
    static let offerPublishKey = "usinagemmaster.online.community.offer.publish"
    static let rentalOperationKey = "usinagemmaster.online.community.rental.operation"
    static let operationResultKey = "usinagemmaster.online.community.rental.result"
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
        NotificationCenter.default.addObserver(
            self, selector: #selector(offerPublishNotification),
            name: Notification.Name("UsinagemCommunityOfferPublish"), object: nil
        )
        NotificationCenter.default.addObserver(
            self, selector: #selector(offerWithdrawNotification),
            name: Notification.Name("UsinagemCommunityOfferWithdraw"), object: nil
        )
        NotificationCenter.default.addObserver(
            self, selector: #selector(rentalOperationNotification),
            name: Notification.Name("UsinagemCommunityRentalOperation"), object: nil
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
            myOffer: nil,
            outgoingRental: nil,
            activeHire: nil,
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

    @objc private func offerPublishNotification() {
        guard let raw = UserDefaults.standard.string(forKey: Self.offerPublishKey), !raw.isEmpty else { return }
        publishCharacterOffer(raw: raw)
    }

    @objc private func offerWithdrawNotification() { withdrawCharacterOffer() }

    @objc private func rentalOperationNotification() {
        guard let raw = UserDefaults.standard.string(forKey: Self.rentalOperationKey), !raw.isEmpty else { return }
        operateRental(raw: raw)
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
        var myOffer: [String: Any]?
        var outgoingRental: [String: Any]?
        var activeHire: [String: Any]?
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

        group.enter()
        db.collection("character_offers").document(user.uid).getDocument { snapshot, error in
            lock.lock()
            if let error { errors.append("Minha oferta: \(error.localizedDescription)") }
            else if let snapshot, snapshot.exists {
                var data = snapshot.data() ?? [:]
                data["_documentId"] = snapshot.documentID
                myOffer = data
            }
            lock.unlock(); group.leave()
        }

        group.enter()
        db.collection("character_rentals").whereField("ownerUid", isEqualTo: user.uid).limit(to: 20).getDocuments { snapshot, error in
            lock.lock()
            if let error { errors.append("Trabalho externo: \(error.localizedDescription)") }
            else {
                let now = Date().timeIntervalSince1970 * 1000.0
                outgoingRental = snapshot?.documents.compactMap { doc -> [String: Any]? in
                    var data = doc.data(); guard self.millis(data["endsAt"]) > now else { return nil }
                    data["_documentId"] = doc.documentID; return data
                }.max(by: { self.millis($0["endsAt"]) < self.millis($1["endsAt"]) })
            }
            lock.unlock(); group.leave()
        }

        group.enter()
        db.collection("character_rentals").whereField("renterUid", isEqualTo: user.uid).limit(to: 20).getDocuments { snapshot, error in
            lock.lock()
            if let error { errors.append("Profissional contratado: \(error.localizedDescription)") }
            else {
                let now = Date().timeIntervalSince1970 * 1000.0
                activeHire = snapshot?.documents.compactMap { doc -> [String: Any]? in
                    var data = doc.data(); guard self.millis(data["endsAt"]) > now else { return nil }
                    data["_documentId"] = doc.documentID; return data
                }.max(by: { self.millis($0["endsAt"]) < self.millis($1["endsAt"]) })
            }
            lock.unlock(); group.leave()
        }

        group.notify(queue: .main) {
            self.cache(
                linked: linked,
                factories: factories,
                offers: offers,
                myOffer: myOffer,
                outgoingRental: outgoingRental,
                activeHire: activeHire,
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
                        "manualOps": 0,
                        "lastManualMachineId": "",
                        "lastManualScore": 0,
                        "lastManualOpAt": Timestamp(date: Date(timeIntervalSince1970: 0)),
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

    private func publishCharacterOffer(raw: String) {
        guard let user = googleUser() else { cacheError("Faça login com Google antes de ofertar seu personagem."); return }
        let p = raw.components(separatedBy: "|")
        guard p.count >= 5, p[0] == "V1" else { cacheError("Oferta do personagem inválida."); return }
        let name = unescape(p[1])
        let boost = max(4, min(25, Int(p[2]) ?? 4))
        let xp = Int64(p[3]) ?? 0
        let level = max(1, Int(Double(max(Int64(0), xp)).squareRoot() / 30.0) + 1)
        let skills = p[4].isEmpty ? [] : p[4].components(separatedBy: ",").map(unescape)
        let ref = db.collection("character_offers").document(user.uid)
        ref.getDocument { snapshot, error in
            if let error { self.cacheError("Oferta: \(error.localizedDescription)"); return }
            let current = snapshot?.data() ?? [:]
            let payload: [String: Any] = [
                "ownerUid": user.uid, "playerName": name, "boostPct": boost,
                "playerXp": xp, "characterLevel": level, "skills": skills,
                "leasedBy": self.string(current["leasedBy"], fallback: ""),
                "leasedUntil": current["leasedUntil"] ?? Timestamp(date: Date(timeIntervalSince1970: 0)),
                "updatedAt": FieldValue.serverTimestamp(),
            ]
            ref.setData(payload, merge: true) { writeError in
                if let writeError { self.cacheError("Oferta: \(writeError.localizedDescription)") }
                else { self.refreshAll(message: "Seu personagem está disponível para contratação por 48h.") }
            }
        }
    }

    private func withdrawCharacterOffer() {
        guard let user = googleUser() else { cacheError("Faça login com Google primeiro."); return }
        let ref = db.collection("character_offers").document(user.uid)
        ref.getDocument { snapshot, error in
            if let error { self.cacheError("Retirar oferta: \(error.localizedDescription)"); return }
            guard let snapshot, snapshot.exists else { self.refreshAll(message: "Seu personagem já não está no mercado."); return }
            let leasedUntil = self.millis(snapshot.data()?["leasedUntil"])
            if leasedUntil > Date().timeIntervalSince1970 * 1000.0 {
                self.cacheError("Seu personagem está contratado no momento; a oferta só pode ser retirada ao fim do vínculo.")
                return
            }
            ref.delete { deleteError in
                if let deleteError { self.cacheError("Retirar oferta: \(deleteError.localizedDescription)") }
                else { self.refreshAll(message: "Personagem retirado do mercado.") }
            }
        }
    }

    private func operateRental(raw: String) {
        guard let user = googleUser() else { cacheError("Faça login com Google primeiro."); return }
        let p = raw.components(separatedBy: "|")
        guard p.count >= 4, p[0] == "V1" else { cacheError("Operação externa inválida."); return }
        let rentalId = unescape(p[1]), machineId = unescape(p[2]), score = max(0, min(100, Int(p[3]) ?? 0))
        let ref = db.collection("character_rentals").document(rentalId)
        var newOps = 0
        db.runTransaction({ transaction, errorPointer -> Any? in
            let snapshot: DocumentSnapshot
            do { snapshot = try transaction.getDocument(ref) }
            catch let e as NSError { errorPointer?.pointee = e; return nil }
            let data = snapshot.data() ?? [:]
            guard self.string(data["ownerUid"], fallback: "") == user.uid else {
                errorPointer?.pointee = NSError(domain:"UsinagemMaster",code:403,userInfo:[NSLocalizedDescriptionKey:"Esse vínculo não pertence ao seu personagem."]); return nil
            }
            let now = Date(); guard self.millis(data["endsAt"]) > now.timeIntervalSince1970 * 1000.0 else {
                errorPointer?.pointee = NSError(domain:"UsinagemMaster",code:410,userInfo:[NSLocalizedDescriptionKey:"O contrato externo já terminou."]); return nil
            }
            let lastOp = self.millis(data["lastManualOpAt"])
            guard now.timeIntervalSince1970 * 1000.0 - lastOp >= 10 * 60 * 1000 else {
                errorPointer?.pointee = NSError(domain:"UsinagemMaster",code:429,userInfo:[NSLocalizedDescriptionKey:"Operação externa em recarga. Aguarde 10 minutos."]); return nil
            }
            newOps = self.int(data["manualOps"], fallback: 0) + 1
            transaction.updateData([
                "manualOps": newOps, "lastManualMachineId": machineId,
                "lastManualScore": score, "lastManualOpAt": Timestamp(date: now),
            ], forDocument: ref)
            return nil
        }) { _, error in
            if let error { self.cacheError("Operação externa: \(error.localizedDescription)"); return }
            let xp = 80 + score * 2
            let token = "\(rentalId):\(newOps)"
            UserDefaults.standard.set("\(self.escape(token))|\(xp)", forKey: Self.operationResultKey)
            self.refreshAll(message: "Operação externa concluída • precisão \(score)% • +\(xp) XP.")
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
        myOffer: [String: Any]?,
        outgoingRental: [String: Any]?,
        activeHire: [String: Any]?,
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

        if let myOffer {
            let skills = (myOffer["skills"] as? [Any] ?? []).compactMap { $0 as? String }.map(escape).joined(separator: ",")
            lines.append(["MYOFFER", "1", String(int(myOffer["boostPct"], fallback: 4)), escape(string(myOffer["leasedBy"], fallback: "")), String(Int64(millis(myOffer["leasedUntil"]))), skills].joined(separator:"|"))
        } else {
            lines.append("MYOFFER|0|0||0|")
        }
        if let outgoingRental {
            lines.append(["OUTRENTAL", escape(string(outgoingRental["_documentId"], fallback:"")), escape(string(outgoingRental["renterUid"], fallback:"")), "", String(Int64(millis(outgoingRental["endsAt"]))), String(int(outgoingRental["manualOps"], fallback:0)), String(Int64(millis(outgoingRental["lastManualOpAt"])))].joined(separator:"|"))
        }
        if let activeHire {
            let ops = int(activeHire["manualOps"], fallback:0)
            let effectiveBoost = min(25, int(activeHire["boostPct"], fallback:4) + min(6, ops))
            lines.append(["ACTIVEHIRE", escape(string(activeHire["_documentId"], fallback:"")), escape(string(activeHire["ownerUid"], fallback:"")), escape(string(activeHire["playerName"], fallback:"Profissional conectado")), String(effectiveBoost), String(Int64(millis(activeHire["endsAt"]))), String(ops)].joined(separator:"|"))
        }
        if let result = UserDefaults.standard.string(forKey: Self.operationResultKey), !result.isEmpty {
            lines.append("REMOTE_RESULT|\(result)")
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
