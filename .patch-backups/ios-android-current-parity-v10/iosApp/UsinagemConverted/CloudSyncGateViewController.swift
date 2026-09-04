import Foundation
import UIKit

final class CloudSyncGateViewController: UIViewController {
    var onReady: (() -> Void)?
    var onContinueLocal: (() -> Void)?

    private let titleLabel = UILabel()
    private let statusLabel = UILabel()
    private let detailLabel = UILabel()
    private let spinner = UIActivityIndicatorView(style: .large)
    private let cloudButton = UIButton(type: .system)
    private let localButton = UIButton(type: .system)
    private let retryButton = UIButton(type: .system)

    private var didStartSync = false
    private var isNetworkFailureMode = false

    override func viewDidLoad() {
        super.viewDidLoad()
        configureView()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)

        if !didStartSync {
            didStartSync = true
            synchronize()
        }
    }

    private func configureView() {
        view.backgroundColor = UIColor(
            red: 0.04,
            green: 0.06,
            blue: 0.075,
            alpha: 1.0
        )

        let badgeLabel = UILabel()
        badgeLabel.text = "☁  CLOUD SAVE"
        badgeLabel.font = UIFont.systemFont(ofSize: 13, weight: .black)
        badgeLabel.textColor = UIColor(
            red: 1.0,
            green: 0.70,
            blue: 0.16,
            alpha: 1.0
        )

        titleLabel.text = "Conectando sua fábrica"
        titleLabel.numberOfLines = 0
        titleLabel.font = UIFont.systemFont(ofSize: 31, weight: .black)
        titleLabel.textColor = .white

        statusLabel.text = "Procurando o save da sua conta Google…"
        statusLabel.numberOfLines = 0
        statusLabel.font = UIFont.systemFont(ofSize: 17, weight: .semibold)
        statusLabel.textColor = UIColor(white: 0.92, alpha: 1.0)

        detailLabel.text =
            "O iPhone usa o mesmo Cloud Save privado do Android. " +
            "Nenhum progresso é sobrescrito antes da validação."
        detailLabel.numberOfLines = 0
        detailLabel.font = UIFont.systemFont(ofSize: 14, weight: .regular)
        detailLabel.textColor = UIColor(white: 0.63, alpha: 1.0)

        spinner.color = UIColor(
            red: 1.0,
            green: 0.70,
            blue: 0.16,
            alpha: 1.0
        )
        spinner.hidesWhenStopped = true

        cloudButton.setTitle("Usar save da nuvem", for: .normal)
        cloudButton.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        cloudButton.setTitleColor(
            UIColor(red: 0.06, green: 0.08, blue: 0.10, alpha: 1.0),
            for: .normal
        )
        cloudButton.backgroundColor = UIColor(
            red: 1.0,
            green: 0.70,
            blue: 0.16,
            alpha: 1.0
        )
        cloudButton.layer.cornerRadius = 14
        cloudButton.heightAnchor.constraint(equalToConstant: 52).isActive = true
        cloudButton.addTarget(
            self,
            action: #selector(useCloudTapped),
            for: .touchUpInside
        )

        localButton.setTitle("Manter este iPhone", for: .normal)
        localButton.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        localButton.setTitleColor(.white, for: .normal)
        localButton.backgroundColor = UIColor(white: 1.0, alpha: 0.08)
        localButton.layer.cornerRadius = 14
        localButton.heightAnchor.constraint(equalToConstant: 52).isActive = true
        localButton.addTarget(
            self,
            action: #selector(useLocalTapped),
            for: .touchUpInside
        )

        retryButton.setTitle("Tentar novamente", for: .normal)
        retryButton.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        retryButton.setTitleColor(.white, for: .normal)
        retryButton.backgroundColor = UIColor(white: 1.0, alpha: 0.08)
        retryButton.layer.cornerRadius = 14
        retryButton.heightAnchor.constraint(equalToConstant: 52).isActive = true
        retryButton.addTarget(
            self,
            action: #selector(retryTapped),
            for: .touchUpInside
        )

        cloudButton.isHidden = true
        localButton.isHidden = true
        retryButton.isHidden = true

        let stack = UIStackView(arrangedSubviews: [
            badgeLabel,
            titleLabel,
            statusLabel,
            detailLabel,
            spinner,
            cloudButton,
            localButton,
            retryButton
        ])
        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(stack)

        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(
                equalTo: view.safeAreaLayoutGuide.leadingAnchor,
                constant: 24
            ),
            stack.trailingAnchor.constraint(
                equalTo: view.safeAreaLayoutGuide.trailingAnchor,
                constant: -24
            ),
            stack.centerYAnchor.constraint(
                equalTo: view.safeAreaLayoutGuide.centerYAnchor
            )
        ])
    }

    private func synchronize() {
        isNetworkFailureMode = false
        setBusy(true)

        titleLabel.text = "Conectando sua fábrica"
        statusLabel.text = "Comparando save Android e iPhone…"
        detailLabel.text =
            "Validando saveId, revisão, chunks, GZIP e SHA-256 antes de abrir o jogo."

        cloudButton.isHidden = true
        localButton.isHidden = true
        retryButton.isHidden = true

        FirebaseCloudSaveService.shared.synchronize { [weak self] result in
            DispatchQueue.main.async {
                guard let self = self else {
                    return
                }

                self.setBusy(false)

                switch result {
                case .failure(let error):
                    self.showSyncFailure(error)

                case .success(let syncResult):
                    self.handleSyncResult(syncResult)
                }
            }
        }
    }

    private func handleSyncResult(_ syncResult: IOSCloudSyncResult) {
        statusLabel.text = syncResult.message

        switch syncResult.action {
        case .restored:
            titleLabel.text = "Fábrica Android recuperada"
            detailLabel.text =
                "O Cloud Save foi validado e convertido para o save iOS. " +
                "Abrindo o jogo com a fábrica restaurada."
            finishGate()

        case .uploaded:
            titleLabel.text = "Cloud Save vinculado"
            detailLabel.text =
                "O save deste iPhone foi associado à sua conta Google com segurança."
            finishGate()

        case .upToDate:
            titleLabel.text = "Tudo sincronizado"
            detailLabel.text =
                "Android e iPhone estão usando o mesmo slot de Cloud Save."
            finishGate()

        case .noLocal:
            titleLabel.text = "Conta conectada"
            detailLabel.text =
                "Ainda não existe save local nem backup remoto. " +
                "A oficina será criada normalmente."
            finishGate()

        case .conflict:
            titleLabel.text = "Escolha qual progresso manter"
            detailLabel.text =
                "Nenhum progresso foi sobrescrito. " +
                "Use a nuvem para trazer o Android ou mantenha o estado deste iPhone."
            cloudButton.isHidden = false
            localButton.setTitle("Manter este iPhone", for: .normal)
            localButton.isHidden = false
        }
    }

    private func showSyncFailure(_ error: Error) {
        isNetworkFailureMode = true
        titleLabel.text = "Não consegui acessar o Cloud Save"
        statusLabel.text = error.localizedDescription
        detailLabel.text =
            "O save local continua intacto. " +
            "Você pode tentar novamente ou abrir o jogo sem restaurar agora."

        retryButton.isHidden = false
        localButton.setTitle("Continuar com save local", for: .normal)
        localButton.isHidden = false
    }

    @objc private func useCloudTapped() {
        setBusy(true)
        cloudButton.isHidden = true
        localButton.isHidden = true

        FirebaseCloudSaveService.shared.forceRestore { [weak self] result in
            DispatchQueue.main.async {
                guard let self = self else {
                    return
                }

                self.setBusy(false)

                switch result {
                case .failure(let error):
                    self.showSyncFailure(error)

                case .success(let syncResult):
                    self.titleLabel.text = "Save da nuvem restaurado"
                    self.statusLabel.text = syncResult.message
                    self.detailLabel.text =
                        "A fábrica Android foi validada e restaurada neste iPhone."
                    self.finishGate()
                }
            }
        }
    }

    @objc private func useLocalTapped() {
        if isNetworkFailureMode {
            onContinueLocal?()
            return
        }

        setBusy(true)
        cloudButton.isHidden = true
        localButton.isHidden = true

        FirebaseCloudSaveService.shared.forceUpload { [weak self] result in
            DispatchQueue.main.async {
                guard let self = self else {
                    return
                }

                self.setBusy(false)

                switch result {
                case .failure(let error):
                    self.showSyncFailure(error)

                case .success(let syncResult):
                    self.titleLabel.text = "Save do iPhone mantido"
                    self.statusLabel.text = syncResult.message
                    self.detailLabel.text =
                        "O estado local foi publicado como a nova revisão do Cloud Save."
                    self.finishGate()
                }
            }
        }
    }

    @objc private func retryTapped() {
        synchronize()
    }

    private func setBusy(_ busy: Bool) {
        cloudButton.isEnabled = !busy
        localButton.isEnabled = !busy
        retryButton.isEnabled = !busy

        if busy {
            spinner.startAnimating()
        } else {
            spinner.stopAnimating()
        }
    }

    private func finishGate() {
        setBusy(false)
        cloudButton.isHidden = true
        localButton.isHidden = true
        retryButton.isHidden = true

        DispatchQueue.main.asyncAfter(
            deadline: DispatchTime.now() + DispatchTimeInterval.milliseconds(450)
        ) { [weak self] in
            self?.onReady?()
        }
    }
}
