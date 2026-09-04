import UIKit

final class CloudSyncGateViewController: UIViewController {
    var onReady: (() -> Void)?
    var onContinueLocal: (() -> Void)?

    private let stack = UIStackView()
    private let titleLabel = UILabel()
    private let statusLabel = UILabel()
    private let detailLabel = UILabel()
    private let spinner = UIActivityIndicatorView(style: .large)
    private let cloudButton = UIButton(type: .system)
    private let localButton = UIButton(type: .system)
    private let retryButton = UIButton(type: .system)
    private var started = false

    override func viewDidLoad() {
        super.viewDidLoad()
        configureUI()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !started else { return }
        started = true
        synchronize()
    }

    private func configureUI() {
        view.backgroundColor = UIColor(red: 0.04, green: 0.06, blue: 0.075, alpha: 1)

        let badge = UILabel()
        badge.text = "☁  CLOUD SAVE"
        badge.font = .systemFont(ofSize: 13, weight: .black)
        badge.textColor = UIColor(red: 1.0, green: 0.70, blue: 0.16, alpha: 1)

        titleLabel.text = "Conectando sua fábrica"
        titleLabel.numberOfLines = 0
        titleLabel.font = .systemFont(ofSize: 31, weight: .black)
        titleLabel.textColor = .white

        statusLabel.text = "Procurando o save da sua conta Google…"
        statusLabel.numberOfLines = 0
        statusLabel.font = .systemFont(ofSize: 17, weight: .semibold)
        statusLabel.textColor = UIColor(white: 0.92, alpha: 1)

        detailLabel.text =
            "O iPhone usa o mesmo slot privado cloud_saves/{uid} do Android. " +
            "Nenhum save local é apagado antes de validar os chunks e o SHA-256."
        detailLabel.numberOfLines = 0
        detailLabel.font = .systemFont(ofSize: 14, weight: .regular)
        detailLabel.textColor = UIColor(white: 0.63, alpha: 1)

        spinner.color = UIColor(red: 1.0, green: 0.70, blue: 0.16, alpha: 1)
        spinner.startAnimating()

        configureButton(
            cloudButton,
            title: "Usar save da nuvem",
            background: UIColor(red: 1.0, green: 0.70, blue: 0.16, alpha: 1),
            foreground: UIColor(red: 0.06, green: 0.08, blue: 0.10, alpha: 1),
            selector: #selector(useCloud)
        )
        configureButton(
            localButton,
            title: "Manter este iPhone",
            background: UIColor(white: 1, alpha: 0.08),
            foreground: .white,
            selector: #selector(useLocal)
        )
        configureButton(
            retryButton,
            title: "Tentar novamente",
            background: UIColor(white: 1, alpha: 0.08),
            foreground: .white,
            selector: #selector(retry)
        )

        cloudButton.isHidden = true
        localButton.isHidden = true
        retryButton.isHidden = true

        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false

        [badge, titleLabel, statusLabel, detailLabel, spinner, cloudButton, localButton, retryButton]
            .forEach(stack.addArrangedSubview)

        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24),
            stack.centerYAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerYAnchor),
        ])
    }

    private func configureButton(
        _ button: UIButton,
        title: String,
        background: UIColor,
        foreground: UIColor,
        selector: Selector
    ) {
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 16, weight: .bold)
        button.backgroundColor = background
        button.setTitleColor(foreground, for: .normal)
        button.layer.cornerRadius = 14
        button.heightAnchor.constraint(equalToConstant: 52).isActive = true
        button.addTarget(self, action: selector, for: .touchUpInside)
    }

    private func synchronize() {
        setBusy(true)
        statusLabel.text = "Comparando save Android e iPhone…"
        retryButton.isHidden = true
        cloudButton.isHidden = true
        localButton.isHidden = true

        FirebaseCloudSaveService.shared.synchronize { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.setBusy(false)

                switch result {
                case .failure(let error):
                    self.titleLabel.text = "Não consegui acessar a nuvem"
                    self.statusLabel.text = error.localizedDescription
                    self.detailLabel.text =
                        "Seu save local foi preservado. Você pode tentar novamente " +
                        "ou continuar no iPhone sem restaurar agora."
                    self.retryButton.isHidden = false
                    self.localButton.setTitle("Continuar com save local", for: .normal)
                    self.localButton.isHidden = false

                case .success(let sync):
                    self.statusLabel.text = sync.message
                    switch sync.action {
                    case .restored:
                        self.titleLabel.text = "Fábrica Android recuperada"
                        self.detailLabel.text =
                            "Cloud Save \(short(sync.saveId)) • revisão \(sync.revision). " +
                            "Abrindo o jogo com o progresso restaurado."
                        self.finishSoon()

                    case .uploaded:
                        self.titleLabel.text = "Cloud Save vinculado"
                        self.detailLabel.text =
                            "O save local foi associado à mesma conta Google e enviado com segurança."
                        self.finishSoon()

                    case .upToDate:
                        self.titleLabel.text = "Tudo sincronizado"
                        self.detailLabel.text =
                            "Android e iPhone apontam para o mesmo slot de Cloud Save."
                        self.finishSoon()

                    case .noLocal:
                        self.titleLabel.text = "Conta conectada"
                        self.detailLabel.text =
                            "Não existe backup remoto nem save local ainda. " +
                            "A oficina será criada normalmente e sincronizada depois."
                        self.finishSoon()

                    case .conflict:
                        self.titleLabel.text = "Escolha qual progresso manter"
                        self.detailLabel.text =
                            "Nenhum lado foi sobrescrito. “Usar nuvem” baixa o Android; " +
                            "“Manter este iPhone” publica o estado local como uma nova revisão."
                        self.cloudButton.isHidden = false
                        self.localButton.setTitle("Manter este iPhone", for: .normal)
                        self.localButton.isHidden = false
                    }
                }
            }
        }
    }

    @objc private func useCloud() {
        setBusy(true)
        FirebaseCloudSaveService.shared.forceRestore { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.setBusy(false)
                switch result {
                case .failure(let error):
                    self.statusLabel.text = error.localizedDescription
                    self.retryButton.isHidden = false
                case .success(let sync):
                    self.statusLabel.text = sync.message
                    self.titleLabel.text = "Save da nuvem restaurado"
                    self.finishSoon()
                }
            }
        }
    }

    @objc private func useLocal() {
        // Em erro de rede, permite continuar sem sobrescrever nada.
        if retryButton.isHidden == false {
            onContinueLocal?()
            return
        }

        setBusy(true)
        FirebaseCloudSaveService.shared.forceUpload { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.setBusy(false)
                switch result {
                case .failure(let error):
                    self.statusLabel.text = error.localizedDescription
                    self.retryButton.isHidden = false
                case .success(let sync):
                    self.statusLabel.text = sync.message
                    self.titleLabel.text = "Save do iPhone mantido"
                    self.finishSoon()
                }
            }
        }
    }

    @objc private func retry() {
        synchronize()
    }

    private func setBusy(_ busy: Bool) {
        cloudButton.isEnabled = !busy
        localButton.isEnabled = !busy
        retryButton.isEnabled = !busy
        busy ? spinner.startAnimating() : spinner.stopAnimating()
    }

    private func finishSoon() {
        setBusy(false)
        cloudButton.isHidden = true
        localButton.isHidden = true
        retryButton.isHidden = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) { [weak self] in
            self?.onReady?()
        }
    }

    private func short(_ value: String) -> String {
        guard value.count > 12 else { return value }
        return String(value.prefix(8)) + "…"
    }
}
