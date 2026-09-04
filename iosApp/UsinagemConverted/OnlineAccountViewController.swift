import FirebaseAuth
import UIKit

final class OnlineAccountViewController: UIViewController {
    private let stack = UIStackView()
    private let status = UILabel()
    private let cloudStatus = UILabel()
    private let primaryButton = UIButton(type: .system)
    private let secondaryButton = UIButton(type: .system)
    private let syncButton = UIButton(type: .system)
    private let spinner = UIActivityIndicatorView(style: .medium)

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Conta online"
        view.backgroundColor = UIColor(red: 0.055, green: 0.075, blue: 0.09, alpha: 1)

        navigationItem.rightBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .done,
            target: self,
            action: #selector(close)
        )

        configure()

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(communityUpdated),
            name: Notification.Name("UsinagemCommunityDidUpdate"),
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(cloudUpdated),
            name: FirebaseCloudSaveService.didUpdateNotification,
            object: nil
        )

        FirebaseCommunityService.shared.refreshAll()
        render()
    }

    private func configure() {
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: "person.crop.circle.fill"))
        icon.tintColor = UIColor(red: 1.0, green: 0.70, blue: 0.16, alpha: 1)
        icon.contentMode = .scaleAspectFit
        icon.heightAnchor.constraint(equalToConstant: 72).isActive = true

        status.numberOfLines = 0
        status.textAlignment = .center
        status.textColor = .white
        status.font = .systemFont(ofSize: 16, weight: .semibold)

        cloudStatus.numberOfLines = 0
        cloudStatus.textAlignment = .center
        cloudStatus.font = .systemFont(ofSize: 13, weight: .medium)
        cloudStatus.textColor = UIColor(red: 0.61, green: 0.83, blue: 1.0, alpha: 1)

        primaryButton.layer.cornerRadius = 14
        primaryButton.heightAnchor.constraint(equalToConstant: 50).isActive = true
        primaryButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .bold)
        primaryButton.addTarget(self, action: #selector(primaryTapped), for: .touchUpInside)

        syncButton.setTitle("Sincronizar save agora", for: .normal)
        syncButton.layer.cornerRadius = 14
        syncButton.heightAnchor.constraint(equalToConstant: 48).isActive = true
        syncButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .bold)
        syncButton.backgroundColor = UIColor(red: 0.15, green: 0.32, blue: 0.44, alpha: 1)
        syncButton.setTitleColor(.white, for: .normal)
        syncButton.addTarget(self, action: #selector(syncTapped), for: .touchUpInside)

        secondaryButton.layer.cornerRadius = 14
        secondaryButton.heightAnchor.constraint(equalToConstant: 48).isActive = true
        secondaryButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        secondaryButton.addTarget(self, action: #selector(secondaryTapped), for: .touchUpInside)

        spinner.hidesWhenStopped = true
        spinner.color = .white

        let note = UILabel()
        note.text =
            "Cloud Save usa o mesmo UID Google e o mesmo slot privado do Android. " +
            "Conflitos nunca são sobrescritos silenciosamente."
        note.numberOfLines = 0
        note.textAlignment = .center
        note.font = .systemFont(ofSize: 12, weight: .regular)
        note.textColor = UIColor(white: 0.62, alpha: 1)

        [icon, status, cloudStatus, primaryButton, syncButton, secondaryButton, spinner, note]
            .forEach(stack.addArrangedSubview)

        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24),
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 28),
        ])
    }

    private func render() {
        cloudStatus.text = FirebaseCloudSaveService.shared.cachedSummary

        if let user = FirebaseAccountService.shared.currentUser {
            let name = user.displayName ?? "Conta Google"
            let email = user.email ?? "e-mail não informado"
            let linked = UserDefaults.standard.string(
                forKey: FirebaseCommunityService.linkedCompanyKey
            )
            let linkedText = linked.map { "\n\nFábrica social\n\($0)" } ?? ""
            status.text = "\(name)\n\(email)\n\nFirebase UID\n\(user.uid)\(linkedText)"

            primaryButton.setTitle("Conta Google conectada", for: .normal)
            primaryButton.setTitleColor(
                UIColor(red: 0.56, green: 0.93, blue: 0.66, alpha: 1),
                for: .normal
            )
            primaryButton.backgroundColor = UIColor(
                red: 0.18, green: 0.46, blue: 0.25, alpha: 0.25
            )
            primaryButton.isEnabled = false

            syncButton.isHidden = false
            secondaryButton.setTitle("Sair da conta", for: .normal)
            secondaryButton.setTitleColor(
                UIColor(red: 1.0, green: 0.45, blue: 0.43, alpha: 1),
                for: .normal
            )
            secondaryButton.backgroundColor = UIColor(white: 1, alpha: 0.055)
            secondaryButton.isHidden = false
        } else {
            status.text = "Modo offline\nSeu save local continua disponível."
            cloudStatus.text = "Cloud Save pausado até entrar com Google."

            primaryButton.setTitle("Entrar com Google", for: .normal)
            primaryButton.setTitleColor(
                UIColor(red: 0.06, green: 0.08, blue: 0.10, alpha: 1),
                for: .normal
            )
            primaryButton.backgroundColor = .white
            primaryButton.isEnabled = true

            syncButton.isHidden = true
            secondaryButton.isHidden = true
        }
    }

    @objc private func primaryTapped() {
        guard FirebaseAccountService.shared.currentUser == nil else { return }
        setLoading(true)

        FirebaseAccountService.shared.signInWithGoogle(presenting: self) { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.setLoading(false)
                switch result {
                case .success:
                    self.render()
                    self.syncTapped()
                case .failure(let error):
                    self.showError(error.localizedDescription)
                }
            }
        }
    }

    @objc private func syncTapped() {
        guard FirebaseAccountService.shared.currentUser != nil else { return }
        setLoading(true)
        FirebaseCloudSaveService.shared.synchronize { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.setLoading(false)
                switch result {
                case .failure(let error):
                    self.showError(error.localizedDescription)
                case .success(let sync):
                    self.render()
                    if sync.action == .conflict {
                        self.presentConflict(sync)
                    } else if sync.action == .restored {
                        self.showInfo(
                            "Save restaurado",
                            "A fábrica da nuvem foi restaurada. Feche esta tela; o jogo será recarregado na próxima abertura."
                        )
                    }
                }
            }
        }
    }

    private func presentConflict(_ sync: IOSCloudSyncResult) {
        let alert = UIAlertController(
            title: "Conflito de Cloud Save",
            message: sync.message,
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Usar nuvem/Android", style: .default) { _ in
            FirebaseCloudSaveService.shared.forceRestore { [weak self] result in
                DispatchQueue.main.async {
                    self?.setLoading(false)
                    if case .failure(let error) = result {
                        self?.showError(error.localizedDescription)
                    } else {
                        self?.render()
                    }
                }
            }
        })
        alert.addAction(UIAlertAction(title: "Manter iPhone", style: .destructive) { _ in
            FirebaseCloudSaveService.shared.forceUpload { [weak self] result in
                DispatchQueue.main.async {
                    self?.setLoading(false)
                    if case .failure(let error) = result {
                        self?.showError(error.localizedDescription)
                    } else {
                        self?.render()
                    }
                }
            }
        })
        alert.addAction(UIAlertAction(title: "Cancelar", style: .cancel))
        present(alert, animated: true)
    }

    @objc private func secondaryTapped() {
        FirebaseCloudSaveService.shared.stopAutoSync()
        if let error = FirebaseAccountService.shared.signOut() {
            showError(error.localizedDescription)
            return
        }
        render()
    }

    private func setLoading(_ loading: Bool) {
        primaryButton.isEnabled = !loading
        syncButton.isEnabled = !loading
        secondaryButton.isEnabled = !loading
        loading ? spinner.startAnimating() : spinner.stopAnimating()
    }

    private func showError(_ message: String) {
        let alert = UIAlertController(
            title: "Não foi possível concluir",
            message: message,
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    private func showInfo(_ title: String, _ message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    @objc private func communityUpdated() {
        FirebaseAccountService.shared.refreshCachedLabel()
        render()
    }

    @objc private func cloudUpdated() {
        render()
    }

    @objc private func close() {
        FirebaseAccountService.shared.refreshCachedLabel()
        dismiss(animated: true)
    }
}
