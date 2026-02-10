import Shared
import UIKit
import UniformTypeIdentifiers
import AVFAudio

final class SpeechViewController: UIViewController, UIDocumentPickerDelegate {
    private let session: IosSpeechSession
    private let networkMonitor: NetworkMonitor

    private var observerId: UUID?
    private var selectedAudioPath: String?
    private var selectedAudioName: String?
    private var selectedAudioMime: String?
    private var selectedAudioSize: Int64 = 0

    private var audioPlayer: AVAudioPlayer?

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    private let offlineLabel = UILabel()
    private let nodeField = UITextField()
    private let segmented = UISegmentedControl(items: ["STT", "TTS", "Артефакты"])

    private let sttStack = UIStackView()
    private let ttsStack = UIStackView()
    private let artifactsStack = UIStackView()

    private let sttFileLabel = UILabel()
    private let sttLanguageField = UITextField()
    private let sttErrorLabel = UILabel()
    private let sttResultView = UITextView()
    private let sttSpinner = UIActivityIndicatorView(style: .medium)

    private let ttsTextView = UITextView()
    private let ttsFormatControl = UISegmentedControl(items: ["m4a", "wav"])
    private let ttsErrorLabel = UILabel()
    private let ttsSpinner = UIActivityIndicatorView(style: .medium)
    private let ttsPlayButton = UIButton(type: .system)
    private let ttsStopButton = UIButton(type: .system)

    init(networkMonitor: NetworkMonitor) {
        self.networkMonitor = networkMonitor
        self.session = IosSpeechSession()
        super.init(nibName: nil, bundle: nil)
        title = "Медиа"
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        setupLayout()
        setupActions()
        refreshArtifacts()
        observerId = networkMonitor.addObserver { [weak self] connected in
            self?.offlineLabel.isHidden = connected
        }
    }

    deinit {
        if let observerId {
            networkMonitor.removeObserver(observerId)
        }
    }

    private func setupLayout() {
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        contentStack.axis = .vertical
        contentStack.spacing = 16

        view.addSubview(scrollView)
        scrollView.addSubview(contentStack)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            contentStack.topAnchor.constraint(equalTo: scrollView.topAnchor, constant: 16),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor, constant: -16),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor, constant: -16),
            contentStack.widthAnchor.constraint(equalTo: scrollView.widthAnchor, constant: -32)
        ])

        offlineLabel.text = "Оффлайн: требуется сеть для узла"
        offlineLabel.textColor = .secondaryLabel
        offlineLabel.font = .preferredFont(forTextStyle: .footnote)
        offlineLabel.isHidden = true
        contentStack.addArrangedSubview(offlineLabel)

        configureField(nodeField, placeholder: "https://node.example.com")
        nodeField.text = session.nodeAddress
        contentStack.addArrangedSubview(makeSectionTitle("Compute node"))
        contentStack.addArrangedSubview(nodeField)

        segmented.selectedSegmentIndex = 0
        contentStack.addArrangedSubview(segmented)

        configureSttStack()
        configureTtsStack()
        configureArtifactsStack()

        contentStack.addArrangedSubview(sttStack)
        contentStack.addArrangedSubview(ttsStack)
        contentStack.addArrangedSubview(artifactsStack)
        updateVisibleStack()
    }

    private func configureSttStack() {
        sttStack.axis = .vertical
        sttStack.spacing = 12

        let pickButton = UIButton(type: .system)
        pickButton.setTitle("Выбрать аудио", for: .normal)
        pickButton.addTarget(self, action: #selector(pickAudioTapped), for: .touchUpInside)

        sttFileLabel.text = "Файл не выбран"
        sttFileLabel.font = .preferredFont(forTextStyle: .footnote)
        sttFileLabel.textColor = .secondaryLabel

        configureField(sttLanguageField, placeholder: "Язык (опционально)")

        let transcribeButton = UIButton(type: .system)
        transcribeButton.setTitle("Расшифровать", for: .normal)
        transcribeButton.addTarget(self, action: #selector(transcribeTapped), for: .touchUpInside)

        let exportButton = UIButton(type: .system)
        exportButton.setTitle("Экспорт .txt", for: .normal)
        exportButton.addTarget(self, action: #selector(exportTranscript), for: .touchUpInside)

        sttErrorLabel.textColor = .systemRed
        sttErrorLabel.font = .preferredFont(forTextStyle: .footnote)
        sttErrorLabel.numberOfLines = 0

        sttResultView.isEditable = false
        sttResultView.font = .preferredFont(forTextStyle: .body)
        sttResultView.heightAnchor.constraint(equalToConstant: 160).isActive = true

        sttStack.addArrangedSubview(makeSectionTitle("STT"))
        sttStack.addArrangedSubview(pickButton)
        sttStack.addArrangedSubview(sttFileLabel)
        sttStack.addArrangedSubview(sttLanguageField)
        sttStack.addArrangedSubview(transcribeButton)
        sttStack.addArrangedSubview(sttSpinner)
        sttStack.addArrangedSubview(sttErrorLabel)
        sttStack.addArrangedSubview(sttResultView)
        sttStack.addArrangedSubview(exportButton)
    }

    private func configureTtsStack() {
        ttsStack.axis = .vertical
        ttsStack.spacing = 12

        ttsTextView.font = .preferredFont(forTextStyle: .body)
        ttsTextView.layer.borderColor = UIColor.secondaryLabel.cgColor
        ttsTextView.layer.borderWidth = 1
        ttsTextView.layer.cornerRadius = 8
        ttsTextView.heightAnchor.constraint(equalToConstant: 120).isActive = true

        let synthButton = UIButton(type: .system)
        synthButton.setTitle("Синтезировать", for: .normal)
        synthButton.addTarget(self, action: #selector(synthesizeTapped), for: .touchUpInside)

        ttsErrorLabel.textColor = .systemRed
        ttsErrorLabel.font = .preferredFont(forTextStyle: .footnote)
        ttsErrorLabel.numberOfLines = 0

        ttsPlayButton.setTitle("Воспроизвести", for: .normal)
        ttsPlayButton.addTarget(self, action: #selector(playAudio), for: .touchUpInside)
        ttsStopButton.setTitle("Стоп", for: .normal)
        ttsStopButton.addTarget(self, action: #selector(stopAudio), for: .touchUpInside)

        let audioButtons = UIStackView(arrangedSubviews: [ttsPlayButton, ttsStopButton])
        audioButtons.axis = .horizontal
        audioButtons.spacing = 12

        ttsStack.addArrangedSubview(makeSectionTitle("TTS"))
        ttsStack.addArrangedSubview(ttsTextView)
        ttsStack.addArrangedSubview(ttsFormatControl)
        ttsStack.addArrangedSubview(synthButton)
        ttsStack.addArrangedSubview(ttsSpinner)
        ttsStack.addArrangedSubview(ttsErrorLabel)
        ttsStack.addArrangedSubview(audioButtons)
    }

    private func configureArtifactsStack() {
        artifactsStack.axis = .vertical
        artifactsStack.spacing = 12
        artifactsStack.addArrangedSubview(makeSectionTitle("Артефакты"))
    }

    private func setupActions() {
        segmented.addTarget(self, action: #selector(segmentChanged), for: .valueChanged)
        nodeField.addTarget(self, action: #selector(nodeAddressChanged), for: .editingChanged)
    }

    @objc private func segmentChanged() {
        updateVisibleStack()
        if segmented.selectedSegmentIndex == 2 {
            refreshArtifacts()
        }
    }

    @objc private func nodeAddressChanged() {
        session.nodeAddress = nodeField.text ?? ""
    }

    private func updateVisibleStack() {
        let index = segmented.selectedSegmentIndex
        sttStack.isHidden = index != 0
        ttsStack.isHidden = index != 1
        artifactsStack.isHidden = index != 2
    }

    @objc private func pickAudioTapped() {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [UTType.audio])
        picker.delegate = self
        present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return }
        guard url.startAccessingSecurityScopedResource() else { return }
        defer { url.stopAccessingSecurityScopedResource() }
        let fileName = url.lastPathComponent
        let target = localAudioCopy(url: url, fileName: fileName)
        selectedAudioPath = target.path
        selectedAudioName = fileName
        selectedAudioMime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "audio/mpeg"
        selectedAudioSize = fileSize(url: target)
        sttFileLabel.text = fileName
    }

    @objc private func transcribeTapped() {
        sttErrorLabel.text = nil
        guard let path = selectedAudioPath else {
            sttErrorLabel.text = "Выберите аудио файл"
            return
        }
        let mime = selectedAudioMime ?? "audio/mpeg"
        let input = AudioInput(
            localPath: path,
            mimeType: mime,
            sizeBytes: selectedAudioSize,
            durationMillis: nil,
            fileName: selectedAudioName,
            data: nil
        )
        let params = SttParams(
            language: sttLanguageField.text?.isEmpty == false ? sttLanguageField.text : nil,
            diarization: false,
            timestamps: false
        )
        sttSpinner.startAnimating()
        let handle = session.startStt(input: input, params: params)
        session.observeSttResult(handle: handle) { [weak self] result, error in
            self?.sttSpinner.stopAnimating()
            if let error {
                self?.sttErrorLabel.text = error
            } else {
                self?.sttResultView.text = result?.text
            }
        }
    }

    @objc private func exportTranscript() {
        guard let text = sttResultView.text, !text.isEmpty else { return }
        let url = exportTextFile(text: text)
        let picker = UIDocumentPickerViewController(forExporting: [url])
        present(picker, animated: true)
    }

    @objc private func synthesizeTapped() {
        ttsErrorLabel.text = nil
        let text = ttsTextView.text ?? ""
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            ttsErrorLabel.text = "Введите текст"
            return
        }
        let format = ttsFormatControl.selectedSegmentIndex == 1 ? "audio/wav" : "audio/m4a"
        let params = TtsParams(voiceId: "default", format: format, sampleRateHz: nil)
        ttsSpinner.startAnimating()
        let handle = session.startTts(input: TextInput(text: text), params: params)
        session.observeTtsResult(handle: handle) { [weak self] _, error in
            self?.ttsSpinner.stopAnimating()
            if let error {
                self?.ttsErrorLabel.text = error
                return
            }
            let jobId = handle.id.value
            if let path = self?.session.audioPathForJob(jobId: jobId) {
                self?.prepareAudioPlayer(path: path)
            }
        }
    }

    @objc private func playAudio() {
        audioPlayer?.play()
    }

    @objc private func stopAudio() {
        audioPlayer?.stop()
    }

    private func prepareAudioPlayer(path: String) {
        do {
            audioPlayer = try AVAudioPlayer(contentsOf: URL(fileURLWithPath: path))
            audioPlayer?.prepareToPlay()
        } catch {
            ttsErrorLabel.text = "Не удалось воспроизвести аудио"
        }
    }

    private func refreshArtifacts() {
        artifactsStack.arrangedSubviews.forEach { view in
            if view !== artifactsStack.arrangedSubviews.first {
                artifactsStack.removeArrangedSubview(view)
                view.removeFromSuperview()
            }
        }
        let items = session.artifactsSnapshot()
        if items.isEmpty {
            let empty = UILabel()
            empty.text = "Артефактов нет."
            empty.textColor = .secondaryLabel
            empty.font = .preferredFont(forTextStyle: .footnote)
            artifactsStack.addArrangedSubview(empty)
            return
        }
        items.forEach { artifact in
            let label = UILabel()
            label.numberOfLines = 0
            label.font = .preferredFont(forTextStyle: .subheadline)
            let meta = "Модель: \(artifact.origin.modelId)"
            if artifact.type == .text {
                let snippet = artifact.text ?? ""
                label.text = "\(meta)\n\(snippet.prefix(200))"
                artifactsStack.addArrangedSubview(label)
            } else {
                label.text = "\(meta)\nАудио"
                artifactsStack.addArrangedSubview(label)
                if let path = artifact.ref?.localPath {
                    let playButton = UIButton(type: .system)
                    playButton.setTitle("Воспроизвести", for: .normal)
                    playButton.addAction(UIAction { [weak self] _ in
                        self?.prepareAudioPlayer(path: path)
                        self?.audioPlayer?.play()
                    }, for: .touchUpInside)
                    artifactsStack.addArrangedSubview(playButton)
                } else {
                    let hint = UILabel()
                    hint.text = "Аудио без локального файла"
                    hint.textColor = .secondaryLabel
                    hint.font = .preferredFont(forTextStyle: .footnote)
                    artifactsStack.addArrangedSubview(hint)
                }
            }
        }
    }

    private func configureField(_ field: UITextField, placeholder: String) {
        field.borderStyle = .roundedRect
        field.placeholder = placeholder
        field.autocapitalizationType = .none
        field.autocorrectionType = .no
    }

    private func makeSectionTitle(_ title: String) -> UILabel {
        let label = UILabel()
        label.text = title
        label.font = .preferredFont(forTextStyle: .headline)
        return label
    }

    private func localAudioCopy(url: URL, fileName: String) -> URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
            .appendingPathComponent("sencha/inputs", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let target = dir.appendingPathComponent(fileName)
        if FileManager.default.fileExists(atPath: target.path) {
            try? FileManager.default.removeItem(at: target)
        }
        try? FileManager.default.copyItem(at: url, to: target)
        return target
    }

    private func exportTextFile(text: String) -> URL {
        let dir = FileManager.default.temporaryDirectory
        let url = dir.appendingPathComponent("transcript.txt")
        try? text.data(using: .utf8)?.write(to: url)
        return url
    }

    private func fileSize(url: URL) -> Int64 {
        (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int64) ?? 0
    }
}
