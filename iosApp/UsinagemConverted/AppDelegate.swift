import UIKit
import Shared
import FirebaseAuth
import FirebaseCore
import GoogleSignIn

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    private let openAccountNotification = Notification.Name("UsinagemOpenOnlineAccount")
    private var foregroundSyncRunning = false

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        FirebaseApp.configure()
        FirebaseAccountService.shared.refreshCachedLabel()
        FirebaseCommunityService.shared.start()

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(openOnlineAccountPanel),
            name: openAccountNotification,
            object: nil
        )

        let window = UIWindow(frame: UIScreen.main.bounds)
        self.window = window

        if Auth.auth().currentUser != nil {
            FirebaseCommunityService.shared.refreshAll()
            showCloudSyncGate(animated: false)
        } else {
            showLogin(animated: false)
        }

        window.makeKeyAndVisible()
        return true
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        // Não restauramos nada enquanto o GameStore está suspenso em memória.
        // A próxima ativação compara a nuvem novamente antes de aceitar alterações.
        FirebaseCloudSaveService.shared.stopAutoSync()
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        guard Auth.auth().currentUser != nil else { return }
        guard let root = window?.rootViewController else { return }
        if root is AuthGateViewController || root is CloudSyncGateViewController { return }
        performForegroundCloudSync()
    }

    private func showLogin(animated: Bool) {
        FirebaseCloudSaveService.shared.stopAutoSync()

        let controller = AuthGateViewController()

        controller.onAuthenticated = { [weak self] in
            FirebaseAccountService.shared.refreshCachedLabel()
            FirebaseCommunityService.shared.refreshAll(
                message: "Login concluído. Consultando Cloud Save completo…"
            )
            self?.showCloudSyncGate(animated: true)
        }

        controller.onContinueOffline = { [weak self] in
            FirebaseAccountService.shared.refreshCachedLabel()
            self?.showGame(animated: true)
        }

        setRoot(controller, animated: animated)
    }

    private func showCloudSyncGate(animated: Bool) {
        FirebaseCloudSaveService.shared.stopAutoSync()

        let controller = CloudSyncGateViewController()
        controller.onReady = { [weak self] in
            FirebaseAccountService.shared.refreshCachedLabel()
            FirebaseCommunityService.shared.refreshAll(
                message: "Cloud Save conferido. Conta e fábrica vinculadas."
            )
            self?.showGame(animated: true)
        }
        controller.onContinueLocal = { [weak self] in
            self?.showGame(animated: true)
        }

        setRoot(controller, animated: animated)
    }

    private func showGame(animated: Bool) {
        let controller = MainViewControllerKt.MainViewController()
        setRoot(controller, animated: animated)
        FirebaseCloudSaveService.shared.startAutoSync()

        // Se este era o primeiro save do aparelho, o GameStore já terá sido criado
        // quando este bloco executar. Uma sincronização curta depois cria a revisão 1.
        if Auth.auth().currentUser != nil {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
                FirebaseCloudSaveService.shared.synchronize { _ in }
            }
        }
    }

    private func setRoot(_ controller: UIViewController, animated: Bool) {
        guard let window else { return }

        if animated, window.rootViewController != nil {
            UIView.transition(
                with: window,
                duration: 0.28,
                options: [.transitionCrossDissolve, .allowAnimatedContent]
            ) {
                window.rootViewController = controller
            }
        } else {
            window.rootViewController = controller
        }
    }

    private func performForegroundCloudSync() {
        guard !foregroundSyncRunning else { return }
        foregroundSyncRunning = true

        FirebaseCloudSaveService.shared.synchronize { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.foregroundSyncRunning = false

                switch result {
                case .failure:
                    break

                case .success(let value):
                    if value.action == .restored {
                        // O arquivo KMP foi trocado; recriar o root faz GameStore ler a revisão restaurada.
                        self.showGame(animated: true)
                    } else if value.action == .conflict {
                        self.presentCloudConflict(value)
                    }
                }
            }
        }
    }

    private func presentCloudConflict(_ sync: IOSCloudSyncResult) {
        guard let presenter = topViewController(from: window?.rootViewController),
              presenter.presentedViewController == nil else {
            return
        }

        let alert = UIAlertController(
            title: "Conflito de Cloud Save",
            message: sync.message,
            preferredStyle: .alert
        )

        alert.addAction(UIAlertAction(title: "Usar nuvem/Android", style: .default) { [weak self] _ in
            FirebaseCloudSaveService.shared.forceRestore { result in
                DispatchQueue.main.async {
                    if case .success = result {
                        self?.showGame(animated: true)
                    }
                }
            }
        })

        alert.addAction(UIAlertAction(title: "Manter iPhone", style: .destructive) { _ in
            FirebaseCloudSaveService.shared.forceUpload { _ in }
        })

        alert.addAction(UIAlertAction(title: "Agora não", style: .cancel))
        presenter.present(alert, animated: true)
    }

    @objc private func openOnlineAccountPanel() {
        guard let presenter = topViewController(from: window?.rootViewController) else {
            return
        }

        let account = OnlineAccountViewController()
        let navigation = UINavigationController(rootViewController: account)
        navigation.modalPresentationStyle = .pageSheet

        if let sheet = navigation.sheetPresentationController {
            sheet.detents = [.medium(), .large()]
            sheet.prefersGrabberVisible = true
        }

        presenter.present(navigation, animated: true)
    }

    private func topViewController(from root: UIViewController?) -> UIViewController? {
        if let presented = root?.presentedViewController {
            return topViewController(from: presented)
        }
        if let navigation = root as? UINavigationController {
            return topViewController(from: navigation.visibleViewController)
        }
        if let tab = root as? UITabBarController {
            return topViewController(from: tab.selectedViewController)
        }
        return root
    }
}
