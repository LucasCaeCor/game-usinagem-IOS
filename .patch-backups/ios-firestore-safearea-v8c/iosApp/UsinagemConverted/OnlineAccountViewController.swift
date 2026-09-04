import FirebaseAuth
import UIKit

final class OnlineAccountViewController: UIViewController {
    private let stack = UIStackView()
    private let status = UILabel()
    private let primaryButton = UIButton(type: .system)
    private let secondaryButton = UIButton(type: .system)
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
        render()
    }

    private func configure() {
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: "person.crop.circle.fill"))
        icon.tintColor = UIColor(red: 1.0, green: 0.70, blue: 0.16, alpha: 1)
        icon.contentMode = .scaleAspectFit
        icon.heightAnchor.constraint(equalToConstant: 82).isActive = true

        status.numberOfLines = 0
        status.textAlignment = .center
        status.textColor = .white
        status.font = .systemFont(ofSize: 17, weight: .semibold)

        primaryButton.layer.cornerRadius = 14
        primaryButton.heightAnchor.constraint(equalToConstant: 52).isActive = true
        primaryButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .bold)
        primaryButton.addTarget(self, action: #selector(primaryTapped), for: .touchUpInside)

        secondaryButton.layer.cornerRadius = 14
        secondaryButton.heightAnchor.constraint(equalToConstant: 48).isActive = true
        secondaryButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        secondaryButton.addTarget(self, action: #selector(secondaryTapped), for: .touchUpInside)

        spinner.hidesWhenStopped = true
        spinner.color = .white

        let note = UILabel()
        note.text = "Entrar ou sair da conta não apaga máquinas, funcionários, contratos, dinheiro ou progresso salvos localmente."
        note.numberOfLines = 0
        note.textAlignment = .center
        note.font = .systemFont(ofSize: 13, weight: .regular)
        note.textColor = UIColor(white: 0.65, alpha: 1)

        [icon, status, primaryButton, secondaryButton, spinner, note].forEach(stack.addArrangedSubview)
        view.addSubview(stack)

        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24),
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 34),
        ])
    }

    private func render() {
        if let user = FirebaseAccountService.shared.currentUser {
            let name = user.displayName ?? "Conta Google"
            let email = user.email ?? "e-mail não informado"
            status.text = "\(name)\n\(email)\n\nFirebase UID\n\(user.uid)"

            primaryButton.setTitle("Conta Google conectada", for: .normal)
            primaryButton.setTitleColor(
                UIColor(red: 0.56, green: 0.93, blue: 0.66, alpha: 1),
                for: .normal
            )
            primaryButton.backgroundColor = UIColor(
                red: 0.18, green: 0.46, blue: 0.25, alpha: 0.25
            )
            primaryButton.isEnabled = false

            secondaryButton.setTitle("Sair da conta", for: .normal)
            secondaryButton.setTitleColor(
                UIColor(red: 1.0, green: 0.45, blue: 0.43, alpha: 1),
                for: .normal
            )
            secondaryButton.backgroundColor = UIColor(white: 1, alpha: 0.055)
            secondaryButton.isHidden = false
        } else {
            status.text = "Modo offline\nSeu save local continua disponível."

            primaryButton.setTitle("Entrar com Google", for: .normal)
            primaryButton.setTitleColor(
                UIColor(red: 0.06, green: 0.08, blue: 0.10, alpha: 1),
                for: .normal
            )
            primaryButton.backgroundColor = .white
            primaryButton.isEnabled = true

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
                case .failure(let error):
                    self.showError(error.localizedDescription)
                }
            }
        }
    }

    @objc private func secondaryTapped() {
        if let error = FirebaseAccountService.shared.signOut() {
            showError(error.localizedDescription)
            return
        }
        render()
    }

    private func setLoading(_ loading: Bool) {
        primaryButton.isEnabled = !loading
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

    @objc private func close() {
        FirebaseAccountService.shared.refreshCachedLabel()
        dismiss(animated: true)
    }
}
