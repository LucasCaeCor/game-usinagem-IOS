import Foundation
import FirebaseAuth
import FirebaseFirestore
import Gzip

enum IOSCloudSyncAction: String {
    case uploaded
    case restored
    case upToDate
    case conflict
    case noLocal
}

struct IOSCloudSyncResult {
    let action: IOSCloudSyncAction
    let message: String
    let saveId: String
    let revision: Int64
}

enum IOSCloudSaveError: LocalizedError {
    case notGoogleUser
    case incompleteRemote(String)
    case futureSchema
    case noCloudBackup
    case noLocalSave
    case checksum
    case invalidCompressedPayload

    var errorDescription: String? {
        switch self {
        case .notGoogleUser:
            return "O Cloud Save privado exige uma conta Google autenticada."
        case .incompleteRemote(let text):
            return text
        case .futureSchema:
            return "Este save foi criado por uma versão mais nova do protocolo. Atualize o app."
        case .noCloudBackup:
            return "Ainda não existe backup desta conta na nuvem."
        case .noLocalSave:
            return "Ainda não existe save local para enviar."
        case .checksum:
            return "A verificação SHA-256 do backup falhou. O save local foi preservado."
        case .invalidCompressedPayload:
            return "O backup da nuvem está comprimido em um formato inválido."
        }
    }
}

private struct IOSRemoteMeta {
    let saveId: String
    let revision: Int64
    let chunkPrefix: String
    let chunkCount: Int
    let checksum: String
    let schema: Int
}

final class FirebaseCloudSaveService {
    static let shared = FirebaseCloudSaveService()

    static let didUpdateNotification = Notification.Name("UsinagemCloudSaveDidUpdate")
    static let lastMessageKey = "usinagemmaster.cloudsave.v23.last_message"
    static let lastActionKey = "usinagemmaster.cloudsave.v23.last_action"
    static let lastRevisionKey = "usinagemmaster.cloudsave.v23.last_revision"

    private let db = Firestore.firestore()
    private let adapter = LocalSaveV23Adapter.shared
    private let chunkCharacters = 620_000
    private var timer: Timer?
    private var syncing = false

    private init() {}

    var cachedSummary: String {
        let message = UserDefaults.standard.string(forKey: Self.lastMessageKey)
            ?? "Cloud Save ainda não sincronizado neste iPhone."
        let revision = UserDefaults.standard.integer(forKey: Self.lastRevisionKey)
        return revision > 0 ? "\(message)\nRevisão \(revision)" : message
    }

    func startAutoSync() {
        stopAutoSync()
        guard Auth.auth().currentUser != nil else { return }
        timer = Timer.scheduledTimer(withTimeInterval: 300, repeats: true) { [weak self] _ in
            self?.synchronize { _ in }
        }
    }

    func stopAutoSync() {
        timer?.invalidate()
        timer = nil
    }

    func synchronize(
        completion: @escaping (Result<IOSCloudSyncResult, Error>) -> Void
    ) {
        guard !syncing else {
            completion(.success(IOSCloudSyncResult(
                action: .upToDate,
                message: "Sincronização já está em andamento.",
                saveId: adapter.localSaveId(createIfNeeded: false) ?? "",
                revision: adapter.trackingStatus().revision
            )))
            return
        }

        do {
            let user = try googleUser()
            syncing = true
            readRemoteMeta(uid: user.uid) { [weak self] result in
                guard let self else { return }
                switch result {
                case .failure(let error):
                    self.finish(.failure(error), completion)
                case .success(let remote):
                    self.decide(user: user, remote: remote, completion: completion)
                }
            }
        } catch {
            completion(.failure(error))
        }
    }

    func forceRestore(
        completion: @escaping (Result<IOSCloudSyncResult, Error>) -> Void
    ) {
        do {
            let user = try googleUser()
            syncing = true
            readRemoteMeta(uid: user.uid) { [weak self] result in
                guard let self else { return }
                switch result {
                case .failure(let error):
                    self.finish(.failure(error), completion)
                case .success(nil):
                    self.finish(.failure(IOSCloudSaveError.noCloudBackup), completion)
                case .success(let remote?):
                    self.restore(user: user, meta: remote, completion: completion)
                }
            }
        } catch {
            completion(.failure(error))
        }
    }

    func forceUpload(
        completion: @escaping (Result<IOSCloudSyncResult, Error>) -> Void
    ) {
        do {
            let user = try googleUser()
            guard let local = try adapter.capturePayload() else {
                completion(.failure(IOSCloudSaveError.noLocalSave))
                return
            }
            syncing = true
            readRemoteMeta(uid: user.uid) { [weak self] result in
                guard let self else { return }
                switch result {
                case .failure(let error):
                    self.finish(.failure(error), completion)
                case .success(let remote):
                    let saveId = remote?.saveId
                        ?? self.adapter.localSaveId(createIfNeeded: true)
                        ?? UUID().uuidString.lowercased()
                    let revision = (remote?.revision ?? 0) + 1
                    self.upload(
                        user: user,
                        saveId: saveId,
                        revision: revision,
                        payload: local,
                        completion: completion
                    )
                }
            }
        } catch {
            completion(.failure(error))
        }
    }

    private func decide(
        user: User,
        remote: IOSRemoteMeta?,
        completion: @escaping (Result<IOSCloudSyncResult, Error>) -> Void
    ) {
        let remembered = adapter.trackingStatus()
        let localPayload: V23LocalPayload?
        do {
            localPayload = try adapter.capturePayload()
        } catch {
            finish(.failure(error), completion)
            return
        }

        if remote == nil {
            guard let localPayload else {
                finish(.success(IOSCloudSyncResult(
                    action: .noLocal,
                    message: "Conta conectada. Ainda não existe save local nem backup na nuvem.",
                    saveId: "",
                    revision: 0
                )), completion)
                return
            }

            if let oldUID = remembered.uid, oldUID != user.uid {
                finish(.success(IOSCloudSyncResult(
                    action: .conflict,
                    message: "Este save do iPhone estava vinculado a outra conta Google. Nada foi enviado para a conta atual.",
                    saveId: remembered.saveId ?? "",
                    revision: remembered.revision
                )), completion)
                return
            }

            let saveId = adapter.localSaveId(createIfNeeded: true)
                ?? UUID().uuidString.lowercased()
            upload(
                user: user,
                saveId: saveId,
                revision: 1,
                payload: localPayload,
                completion: completion
            )
            return
        }

        guard let remote else { return }
        guard remote.schema <= 1 else {
            finish(.failure(IOSCloudSaveError.futureSchema), completion)
            return
        }

        // Instalação nova: se não há save KMP ainda, a nuvem vence.
        guard let localPayload else {
            restore(user: user, meta: remote, completion: completion)
            return
        }

        let localSaveId = adapter.localSaveId(createIfNeeded: true) ?? ""

        // Mesmo comportamento V23 Android: outro aparelho/slot = restaurar a nuvem.
        if remote.saveId != localSaveId {
            restore(user: user, meta: remote, completion: completion)
            return
        }

        let sameTrackedSlot =
            remembered.uid == user.uid &&
            remembered.saveId == remote.saveId

        if !sameTrackedSlot {
            restore(user: user, meta: remote, completion: completion)
            return
        }

        if remote.revision > remembered.revision {
            if localPayload.fingerprint == remembered.fingerprint {
                restore(user: user, meta: remote, completion: completion)
            } else {
                finish(.success(IOSCloudSyncResult(
                    action: .conflict,
                    message: "Há progresso novo no iPhone e uma revisão mais nova no Android/nuvem. Nenhum dos dois foi sobrescrito.",
                    saveId: remote.saveId,
                    revision: remote.revision
                )), completion)
            }
            return
        }

        if remote.revision == remembered.revision &&
            remote.checksum != remembered.fingerprint {
            finish(.success(IOSCloudSyncResult(
                action: .conflict,
                message: "Android e iPhone publicaram versões diferentes da mesma revisão. Escolha qual progresso manter.",
                saveId: remote.saveId,
                revision: remote.revision
            )), completion)
            return
        }

        if localPayload.fingerprint != remembered.fingerprint ||
            remote.revision < remembered.revision {
            let next = max(remote.revision, remembered.revision) + 1
            upload(
                user: user,
                saveId: remote.saveId,
                revision: next,
                payload: localPayload,
                completion: completion
            )
            return
        }

        adapter.remember(
            uid: user.uid,
            saveId: remote.saveId,
            revision: remote.revision,
            fingerprint: localPayload.fingerprint
        )
        registerAccount(user: user, saveId: remote.saveId)
        finish(.success(IOSCloudSyncResult(
            action: .upToDate,
            message: "Save Android/iPhone está sincronizado.",
            saveId: remote.saveId,
            revision: remote.revision
        )), completion)
    }

    private func readRemoteMeta(
        uid: String,
        completion: @escaping (Result<IOSRemoteMeta?, Error>) -> Void
    ) {
        db.collection("cloud_saves")
            .document(uid)
            .collection("meta")
            .document("main")
            .getDocument { snapshot, error in
                if let error {
                    completion(.failure(error))
                    return
                }
                guard let snapshot, snapshot.exists else {
                    completion(.success(nil))
                    return
                }

                let data = snapshot.data() ?? [:]
                let saveId = data["saveId"] as? String ?? ""
                let revision = (data["revision"] as? NSNumber)?.int64Value ?? 0
                let count = (data["chunkCount"] as? NSNumber)?.intValue ?? 0
                let checksum = data["checksum"] as? String ?? ""
                let schema = (data["schema"] as? NSNumber)?.intValue ?? 1
                let prefix = (data["chunkPrefix"] as? String)
                    ?? self.revisionPrefix(revision)

                guard !saveId.isEmpty, revision > 0, count > 0, !checksum.isEmpty else {
                    completion(.failure(IOSCloudSaveError.incompleteRemote(
                        "Backup da conta está incompleto. O save do iPhone não foi alterado."
                    )))
                    return
                }

                completion(.success(IOSRemoteMeta(
                    saveId: saveId,
                    revision: revision,
                    chunkPrefix: prefix,
                    chunkCount: count,
                    checksum: checksum,
                    schema: schema
                )))
            }
    }

    private func restore(
        user: User,
        meta: IOSRemoteMeta,
        completion: @escaping (Result<IOSCloudSyncResult, Error>) -> Void
    ) {
        guard meta.schema <= 1 else {
            finish(.failure(IOSCloudSaveError.futureSchema), completion)
            return
        }

        let root = db.collection("cloud_saves").document(user.uid)
        var chunks = Array<String?>(repeating: nil, count: meta.chunkCount)
        let group = DispatchGroup()
        let lock = NSLock()
        var firstError: Error?

        for index in 0..<meta.chunkCount {
            group.enter()
            let id = "\(meta.chunkPrefix)_c\(String(format: "%04d", index))"
            root.collection("chunks").document(id).getDocument { snapshot, error in
                defer { group.leave() }
                lock.lock()
                defer { lock.unlock() }

                if firstError != nil { return }
                if let error {
                    firstError = error
                    return
                }
                guard let snapshot, snapshot.exists,
                      let value = snapshot.data()?["data"] as? String else {
                    firstError = IOSCloudSaveError.incompleteRemote(
                        "Backup incompleto: bloco \(index + 1)/\(meta.chunkCount) ausente."
                    )
                    return
                }
                chunks[index] = value
            }
        }

        group.notify(queue: .main) {
            if let firstError {
                self.finish(.failure(firstError), completion)
                return
            }

            let joined = chunks.compactMap { $0 }.joined()
            guard let compressed = Data(base64Encoded: joined) else {
                self.finish(.failure(IOSCloudSaveError.invalidCompressedPayload), completion)
                return
            }

            do {
                let uncompressed = try compressed.gunzipped()
                guard let json = String(data: uncompressed, encoding: .utf8) else {
                    throw IOSCloudSaveError.invalidCompressedPayload
                }
                let checksum = self.adapter.sha256(json)
                guard checksum == meta.checksum else {
                    throw IOSCloudSaveError.checksum
                }

                try self.adapter.applyRemoteAndroidJSON(json, saveId: meta.saveId)
                self.adapter.remember(
                    uid: user.uid,
                    saveId: meta.saveId,
                    revision: meta.revision,
                    fingerprint: checksum
                )
                self.registerAccount(user: user, saveId: meta.saveId)

                self.finish(.success(IOSCloudSyncResult(
                    action: .restored,
                    message: "Sua fábrica do Android foi restaurada neste iPhone.",
                    saveId: meta.saveId,
                    revision: meta.revision
                )), completion)
            } catch {
                self.finish(.failure(error), completion)
            }
        }
    }

    private func upload(
        user: User,
        saveId: String,
        revision: Int64,
        payload: V23LocalPayload,
        completion: @escaping (Result<IOSCloudSyncResult, Error>) -> Void
    ) {
        do {
            let compressed = try Data(payload.json.utf8).gzipped()
            let encoded = compressed.base64EncodedString()
            let chunks = split(encoded, size: chunkCharacters)
            guard !chunks.isEmpty else {
                throw IOSCloudSaveError.invalidCompressedPayload
            }

            let prefix = "\(revisionPrefix(revision))_\(String(payload.fingerprint.prefix(16)))"
            let root = db.collection("cloud_saves").document(user.uid)

            writeChunks(
                root: root,
                chunks: chunks,
                prefix: prefix,
                revision: revision,
                start: 0
            ) { error in
                if let error {
                    self.finish(.failure(error), completion)
                    return
                }

                let meta: [String: Any] = [
                    "uid": user.uid,
                    "saveId": saveId,
                    "schema": 1,
                    "revision": revision,
                    "chunkPrefix": prefix,
                    "chunkCount": chunks.count,
                    "checksum": payload.fingerprint,
                    "compressedChars": encoded.count,
                    "clientUpdatedAtMs": Int64(Date().timeIntervalSince1970 * 1000.0),
                    "serverUpdatedAt": FieldValue.serverTimestamp(),
                ]

                root.collection("meta").document("main")
                    .setData(meta, merge: true) { error in
                        if let error {
                            self.finish(.failure(error), completion)
                            return
                        }

                        do {
                            try self.adapter.markUploaded(json: payload.json)
                            self.adapter.adoptCloudSaveId(saveId)
                            self.adapter.remember(
                                uid: user.uid,
                                saveId: saveId,
                                revision: revision,
                                fingerprint: payload.fingerprint
                            )
                            self.registerAccount(user: user, saveId: saveId)
                            self.cleanupOldChunks(
                                root: root,
                                keepRevision: revision
                            )

                            self.finish(.success(IOSCloudSyncResult(
                                action: .uploaded,
                                message: "Progresso do iPhone salvo no Cloud Save da mesma conta Android.",
                                saveId: saveId,
                                revision: revision
                            )), completion)
                        } catch {
                            self.finish(.failure(error), completion)
                        }
                    }
            }
        } catch {
            finish(.failure(error), completion)
        }
    }

    private func writeChunks(
        root: DocumentReference,
        chunks: [String],
        prefix: String,
        revision: Int64,
        start: Int,
        completion: @escaping (Error?) -> Void
    ) {
        if start >= chunks.count {
            completion(nil)
            return
        }

        let end = min(start + 350, chunks.count)
        let batch = db.batch()

        for index in start..<end {
            let id = "\(prefix)_c\(String(format: "%04d", index))"
            let ref = root.collection("chunks").document(id)
            batch.setData([
                "revision": revision,
                "index": index,
                "data": chunks[index],
            ], forDocument: ref)
        }

        batch.commit { error in
            if let error {
                completion(error)
            } else {
                self.writeChunks(
                    root: root,
                    chunks: chunks,
                    prefix: prefix,
                    revision: revision,
                    start: end,
                    completion: completion
                )
            }
        }
    }

    private func cleanupOldChunks(root: DocumentReference, keepRevision: Int64) {
        root.collection("chunks").getDocuments { snapshot, _ in
            guard let docs = snapshot?.documents else { return }
            let old = docs.filter {
                (($0.data()["revision"] as? NSNumber)?.int64Value ?? Int64.min) < keepRevision
            }

            for groupStart in stride(from: 0, to: old.count, by: 350) {
                let end = min(groupStart + 350, old.count)
                let batch = self.db.batch()
                for index in groupStart..<end {
                    batch.deleteDocument(old[index].reference)
                }
                batch.commit(completion: nil)
            }
        }
    }

    private func registerAccount(user: User, saveId: String) {
        db.collection("player_accounts").document(user.uid).setData([
            "uid": user.uid,
            "email": user.email as Any,
            "displayName": user.displayName as Any,
            "localSaveId": saveId,
            "provider": "google",
            "cloudSaveEnabled": true,
            "lastLinkedAt": FieldValue.serverTimestamp(),
            "clientLinkedAtMs": Int64(Date().timeIntervalSince1970 * 1000.0),
        ], merge: true)
    }

    private func googleUser() throws -> User {
        guard let user = Auth.auth().currentUser,
              user.providerData.contains(where: { $0.providerID == "google.com" }) else {
            throw IOSCloudSaveError.notGoogleUser
        }
        return user
    }

    private func finish(
        _ result: Result<IOSCloudSyncResult, Error>,
        _ completion: @escaping (Result<IOSCloudSyncResult, Error>) -> Void
    ) {
        syncing = false

        if case .success(let value) = result {
            UserDefaults.standard.set(value.message, forKey: Self.lastMessageKey)
            UserDefaults.standard.set(value.action.rawValue, forKey: Self.lastActionKey)
            UserDefaults.standard.set(Int(value.revision), forKey: Self.lastRevisionKey)
            NotificationCenter.default.post(name: Self.didUpdateNotification, object: nil)
        }

        completion(result)
    }

    private func split(_ value: String, size: Int) -> [String] {
        guard size > 0, !value.isEmpty else { return [] }
        var out: [String] = []
        var index = value.startIndex

        while index < value.endIndex {
            let end = value.index(index, offsetBy: size, limitedBy: value.endIndex) ?? value.endIndex
            out.append(String(value[index..<end]))
            index = end
        }
        return out
    }

    private func revisionPrefix(_ revision: Int64) -> String {
        let raw = String(revision)
        return "r" + String(repeating: "0", count: max(0, 12 - raw.count)) + raw
    }
}
