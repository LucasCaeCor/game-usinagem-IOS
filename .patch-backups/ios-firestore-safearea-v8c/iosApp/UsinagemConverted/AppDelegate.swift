import UIKit
import Shared
import FirebaseAuth
import FirebaseCore
import GoogleSignIn

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    private let openAccountNotification = Notification.Name("UsinagemOpenOnlineAccount")

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        FirebaseApp.configure()
        FirebaseAccountService.shared.refreshCachedLabel()

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(openOnlineAccountPanel),
            name: openAccountNotification,
            object: nil
        )

        let window = UIWindow(frame: UIScreen.main.bounds)
        self.window = window

        if Auth.auth().currentUser != nil {
            showGame(animated: false)
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

    private func showLogin(animated: Bool) {
        let controller = AuthGateViewController()

        controller.onAuthenticated = { [weak self] in
            FirebaseAccountService.shared.refreshCachedLabel()
            self?.showGame(animated: true)
        }

        controller.onContinueOffline = { [weak self] in
            FirebaseAccountService.shared.refreshCachedLabel()
            self?.showGame(animated: true)
        }

        setRoot(controller, animated: animated)
    }

    private func showGame(animated: Bool) {
        let controller = MainViewControllerKt.MainViewController()
        setRoot(controller, animated: animated)
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
