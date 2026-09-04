import Foundation
import FirebaseAuth
import FirebaseCore
import GoogleSignIn
import UIKit

enum UsinagemAuthError: LocalizedError {
    case missingClientID
    case missingGoogleToken
    case missingFirebaseUser

    var errorDescription: String? {
        switch self {
        case .missingClientID:
            return "O Client ID do Firebase não foi encontrado."
        case .missingGoogleToken:
            return "O Google não retornou um token válido."
        case .missingFirebaseUser:
            return "O Firebase não retornou o usuário autenticado."
        }
    }
}

final class FirebaseAccountService {
    static let shared = FirebaseAccountService()

    static let accountLabelKey = "usinagemmaster.online.account.label"

    private init() {}

    var currentUser: User? {
        Auth.auth().currentUser
    }

    var accountLabel: String {
        guard let user = currentUser else {
            return "Offline • progresso local"
        }

        let identity =
            user.displayName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
                ? user.displayName!
                : (user.email ?? "Conta Google")

        return "Google • \(identity)"
    }

    func refreshCachedLabel() {
        UserDefaults.standard.set(accountLabel, forKey: Self.accountLabelKey)
    }

    func signInWithGoogle(
        presenting viewController: UIViewController,
        completion: @escaping (Result<User, Error>) -> Void
    ) {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            completion(.failure(UsinagemAuthError.missingClientID))
            return
        }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)

        GIDSignIn.sharedInstance.signIn(withPresenting: viewController) { result, error in
            if let error {
                completion(.failure(error))
                return
            }

            guard let googleUser = result?.user,
                  let idToken = googleUser.idToken?.tokenString else {
                completion(.failure(UsinagemAuthError.missingGoogleToken))
                return
            }

            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: googleUser.accessToken.tokenString
            )

            Auth.auth().signIn(with: credential) { [weak self] authResult, error in
                if let error {
                    completion(.failure(error))
                    return
                }

                guard let firebaseUser = authResult?.user else {
                    completion(.failure(UsinagemAuthError.missingFirebaseUser))
                    return
                }

                self?.refreshCachedLabel()
                FirebaseCommunityService.shared.refreshAll(
                    message: "Conta Google conectada. Consultando sua fábrica antiga…"
                )
                completion(.success(firebaseUser))
            }
        }
    }

    @discardableResult
    func signOut() -> Error? {
        do {
            try Auth.auth().signOut()
            GIDSignIn.sharedInstance.signOut()
            FirebaseCloudSaveService.shared.stopAutoSync()
            refreshCachedLabel()
            FirebaseCommunityService.shared.clear()
            return nil
        } catch {
            return error
        }
    }
}
