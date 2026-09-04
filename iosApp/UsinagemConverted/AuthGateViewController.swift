import FirebaseAuth
import UIKit

final class AuthGateViewController: UIViewController {
    var onAuthenticated: (() -> Void)?
    var onContinueOffline: (() -> Void)?

    private let gradient = CAGradientLayer()
    private let content = UIStackView()
    private let statusLabel = UILabel()
    private let errorLabel = UILabel()
    private let spinner = UIActivityIndicatorView(style: .medium)
    private let googleButton = UIButton(type: .system)
    private let offlineButton = UIButton(type: .system)

    override func viewDidLoad() {
        super.viewDidLoad()
        configureBackground()
        configureUI()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        gradient.frame = view.bounds
    }

    private func configureBackground() {
        view.backgroundColor = UIColor(red: 0.04, green: 0.06, blue: 0.075, alpha: 1)
        gradient.colors = [
            UIColor(red: 0.04, green: 0.06, blue: 0.075, alpha: 1).cgColor,
            UIColor(red: 0.07, green: 0.10, blue: 0.12, alpha: 1).cgColor,
            UIColor(red: 0.12, green: 0.085, blue: 0.035, alpha: 1).cgColor,
        ]
        gradient.locations = [0.0, 0.62, 1.0]
        view.layer.insertSublayer(gradient, at: 0)
    }

    private func configureUI() {
        let badge = UILabel()
        badge.text = "⚙  USINAGEM MASTER"
        badge.font = .systemFont(ofSize: 14, weight: .black)
        badge.textColor = UIColor(red: 1.0, green: 0.70, blue: 0.16, alpha: 1)

        let title = UILabel()
        title.text = "Sua oficina.\nSua identidade."
        title.numberOfLines = 0
        title.font = .systemFont(ofSize: 35, weight: .black)
        title.textColor = .white

        let subtitle = UILabel()
        subtitle.text = "Entre com Google para usar a comunidade. Seu progresso local continua no iPhone e não é apagado pelo login."
        subtitle.numberOfLines = 0
        subtitle.font = .systemFont(ofSize: 16, weight: .regular)
        subtitle.textColor = UIColor(white: 0.78, alpha: 1)

        let card = UIView()
        card.backgroundColor = UIColor(white: 1, alpha: 0.055)
        card.layer.cornerRadius = 24
        card.layer.borderWidth = 1
        card.layer.borderColor = UIColor(white: 1, alpha: 0.10).cgColor

        let features = UIStackView(arrangedSubviews: [
            featureRow(symbol: "externaldrive.fill", text: "Save local preservado"),
            featureRow(symbol: "person.2.fill", text: "Comunidade e identidade online"),
            featureRow(symbol: "lock.shield.fill", text: "Firebase Authentication"),
        ])
        features.axis = .vertical
        features.spacing = 15
        features.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(features)

        NSLayoutConstraint.activate([
            features.topAnchor.constraint(equalTo: card.topAnchor, constant: 18),
            features.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 18),
            features.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -18),
            features.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -18),
        ])

        googleButton.setTitle("  Entrar com Google", for: .normal)
        googleButton.setImage(UIImage(systemName: "person.crop.circle.badge.checkmark"), for: .normal)
        googleButton.titleLabel?.font = .systemFont(ofSize: 17, weight: .bold)
        googleButton.tintColor = UIColor(red: 0.06, green: 0.08, blue: 0.10, alpha: 1)
        googleButton.backgroundColor = .white
        googleButton.layer.cornerRadius = 14
        googleButton.heightAnchor.constraint(equalToConstant: 54).isActive = true
        googleButton.addTarget(self, action: #selector(signInTapped), for: .touchUpInside)
        googleButton.accessibilityLabel = "Entrar com Google"

        offlineButton.setTitle("Continuar offline", for: .normal)
        offlineButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        offlineButton.setTitleColor(UIColor(white: 0.86, alpha: 1), for: .normal)
        offlineButton.backgroundColor = UIColor(white: 1, alpha: 0.06)
        offlineButton.layer.cornerRadius = 14
        offlineButton.layer.borderWidth = 1
        offlineButton.layer.borderColor = UIColor(white: 1, alpha: 0.10).cgColor
        offlineButton.heightAnchor.constraint(equalToConstant: 50).isActive = true
        offlineButton.addTarget(self, action: #selector(offlineTapped), for: .touchUpInside)

        errorLabel.numberOfLines = 0
        errorLabel.font = .systemFont(ofSize: 13, weight: .medium)
        errorLabel.textColor = UIColor(red: 1.0, green: 0.43, blue: 0.42, alpha: 1)
        errorLabel.isHidden = true

        statusLabel.text = "Firebase pronto"
        statusLabel.font = .systemFont(ofSize: 12, weight: .medium)
        statusLabel.textColor = UIColor(white: 0.55, alpha: 1)
        statusLabel.textAlignment = .center

        spinner.hidesWhenStopped = true
        spinner.color = .white

        content.axis = .vertical
        content.spacing = 18
        content.translatesAutoresizingMaskIntoConstraints = false
        content.addArrangedSubview(badge)
        content.addArrangedSubview(title)
        content.setCustomSpacing(10, after: title)
        content.addArrangedSubview(subtitle)
        content.addArrangedSubview(card)
        content.setCustomSpacing(24, after: card)
        content.addArrangedSubview(googleButton)
        content.addArrangedSubview(offlineButton)
        content.addArrangedSubview(errorLabel)
        content.addArrangedSubview(spinner)
        content.addArrangedSubview(statusLabel)
        view.addSubview(content)

        NSLayoutConstraint.activate([
            content.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            content.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24),
            content.centerYAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerYAnchor),
        ])
    }

    private func featureRow(symbol: String, text: String) -> UIView {
        let image = UIImageView(image: UIImage(systemName: symbol))
        image.tintColor = UIColor(red: 1.0, green: 0.70, blue: 0.16, alpha: 1)
        image.contentMode = .scaleAspectFit
        image.widthAnchor.constraint(equalToConstant: 26).isActive = true

        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 15, weight: .semibold)
        label.textColor = UIColor(white: 0.90, alpha: 1)

        let row = UIStackView(arrangedSubviews: [image, label])
        row.axis = .horizontal
        row.spacing = 12
        row.alignment = .center
        return row
    }

    @objc private func signInTapped() {
        setLoading(true)
        errorLabel.isHidden = true

        FirebaseAccountService.shared.signInWithGoogle(presenting: self) { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.setLoading(false)

                switch result {
                case .success:
                    self.onAuthenticated?()
                case .failure(let error):
                    self.errorLabel.text = error.localizedDescription
                    self.errorLabel.isHidden = false
                }
            }
        }
    }

    @objc private func offlineTapped() {
        FirebaseAccountService.shared.refreshCachedLabel()
        onContinueOffline?()
    }

    private func setLoading(_ loading: Bool) {
        googleButton.isEnabled = !loading
        offlineButton.isEnabled = !loading
        loading ? spinner.startAnimating() : spinner.stopAnimating()
        statusLabel.text = loading ? "Conectando ao Google…" : "Firebase pronto"
    }
}
