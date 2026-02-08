import UIKit

final class ChatViewController: UIViewController {
    private let chat: AppStore.ChatItem
    private let networkMonitor: NetworkMonitor
    private var networkObserverId: UUID?
    private var isConnected = true
    private var isStreaming = false
    private var streamingTimer: Timer?
    private var streamingTokens: [String] = []
    private var streamingIndex = 0
    private let tableView = UITableView(frame: .zero, style: .plain)
    private let inputBar = ChatInputBar()
    private let emptyLabel = UILabel()
    private let headerWrapper = UIView()
    private let headerContainer = UIView()
    private let headerLabel = UILabel()

    init(chat: AppStore.ChatItem, networkMonitor: NetworkMonitor) {
        self.chat = chat
        self.networkMonitor = networkMonitor
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.systemBackground
        title = chat.title
        navigationItem.prompt = "Модель: \(chat.model.name)"

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.register(ChatMessageCell.self, forCellReuseIdentifier: ChatMessageCell.reuseId)
        tableView.dataSource = self
        tableView.delegate = self
        tableView.separatorStyle = .none
        tableView.keyboardDismissMode = .interactive

        emptyLabel.text = "Напишите первое сообщение, чтобы начать."
        emptyLabel.textColor = UIColor.secondaryLabel
        emptyLabel.textAlignment = .center
        emptyLabel.numberOfLines = 0
        emptyLabel.font = UIFont.preferredFont(forTextStyle: .body)

        inputBar.translatesAutoresizingMaskIntoConstraints = false
        inputBar.onSend = { [weak self] in
            self?.sendMessage()
        }

        view.addSubview(tableView)
        view.addSubview(inputBar)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: inputBar.topAnchor),
            inputBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            inputBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            inputBar.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor)
        ])

        configureHeader()
        updateEmptyState()

        networkObserverId = networkMonitor.addObserver { [weak self] connected in
            self?.isConnected = connected
            self?.updateHeader()
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateHeaderSize()
    }

    deinit {
        streamingTimer?.invalidate()
        if let id = networkObserverId {
            networkMonitor.removeObserver(id)
        }
    }

    private func sendMessage() {
        guard !isStreaming else { return }
        let rawText = inputBar.textField.text ?? ""
        let text = rawText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        inputBar.textField.text = nil

        _ = appendMessage(role: "user", content: text)
        let assistantId = appendMessage(role: "assistant", content: "")
        reloadMessages(scrollToBottom: true)

        if chat.model.requiresNetwork && !isConnected {
            updateMessage(id: assistantId, content: "Нет сети. Проверьте подключение и попробуйте снова.")
            reloadMessages(scrollToBottom: true)
            return
        }
        simulateStreamingReply(for: text, messageId: assistantId)
    }

    private func simulateStreamingReply(for prompt: String, messageId: String) {
        isStreaming = true
        inputBar.setSending(true)
        let response = "Ответ от \(chat.model.name): понял запрос и отвечаю постепенно, как будто это стриминг."
        streamingTokens = response.split(separator: " ").map(String.init)
        streamingIndex = 0
        var current = ""
        streamingTimer?.invalidate()
        streamingTimer = Timer.scheduledTimer(withTimeInterval: 0.06, repeats: true) { [weak self] timer in
            guard let self else {
                timer.invalidate()
                return
            }
            if self.streamingIndex >= self.streamingTokens.count {
                timer.invalidate()
                self.isStreaming = false
                self.inputBar.setSending(false)
                return
            }
            let token = self.streamingTokens[self.streamingIndex]
            self.streamingIndex += 1
            current += (current.isEmpty ? "" : " ") + token
            self.updateMessage(id: messageId, content: current)
            self.reloadMessages(scrollToBottom: true)
        }
    }

    private func appendMessage(role: String, content: String) -> String {
        let message = AppStore.ChatMessage(id: "msg-\(UUID().uuidString)", role: role, content: content)
        chat.messages.append(message)
        updateEmptyState()
        return message.id
    }

    private func updateMessage(id: String, content: String) {
        if let index = chat.messages.firstIndex(where: { $0.id == id }) {
            chat.messages[index].content = content
        }
    }

    private func reloadMessages(scrollToBottom: Bool) {
        tableView.reloadData()
        updateEmptyState()
        if scrollToBottom {
            scrollToBottomRow()
        }
    }

    private func scrollToBottomRow() {
        let count = chat.messages.count
        guard count > 0 else { return }
        let indexPath = IndexPath(row: count - 1, section: 0)
        tableView.scrollToRow(at: indexPath, at: .bottom, animated: true)
    }

    private func updateEmptyState() {
        tableView.backgroundView = chat.messages.isEmpty ? emptyLabel : nil
    }

    private func configureHeader() {
        headerWrapper.translatesAutoresizingMaskIntoConstraints = false
        headerContainer.translatesAutoresizingMaskIntoConstraints = false
        headerContainer.backgroundColor = SenchaAppearance.senchaMist
        headerContainer.layer.cornerRadius = 12

        headerLabel.translatesAutoresizingMaskIntoConstraints = false
        headerLabel.font = UIFont.preferredFont(forTextStyle: .subheadline)
        headerLabel.textColor = UIColor.label
        headerLabel.numberOfLines = 0

        headerWrapper.addSubview(headerContainer)
        headerContainer.addSubview(headerLabel)

        NSLayoutConstraint.activate([
            headerContainer.topAnchor.constraint(equalTo: headerWrapper.topAnchor, constant: 12),
            headerContainer.bottomAnchor.constraint(equalTo: headerWrapper.bottomAnchor, constant: -12),
            headerContainer.leadingAnchor.constraint(equalTo: headerWrapper.leadingAnchor, constant: 16),
            headerContainer.trailingAnchor.constraint(equalTo: headerWrapper.trailingAnchor, constant: -16),
            headerLabel.topAnchor.constraint(equalTo: headerContainer.topAnchor, constant: 12),
            headerLabel.bottomAnchor.constraint(equalTo: headerContainer.bottomAnchor, constant: -12),
            headerLabel.leadingAnchor.constraint(equalTo: headerContainer.leadingAnchor, constant: 16),
            headerLabel.trailingAnchor.constraint(equalTo: headerContainer.trailingAnchor, constant: -16)
        ])
        updateHeader()
    }

    private func updateHeader() {
        guard chat.model.requiresNetwork, !isConnected else {
            tableView.tableHeaderView = nil
            return
        }
        headerLabel.text = "Нет сети. Эта модель работает только при подключении к интернету."
        tableView.tableHeaderView = headerWrapper
        updateHeaderSize()
    }

    private func updateHeaderSize() {
        guard tableView.tableHeaderView === headerWrapper else { return }
        let targetSize = CGSize(width: tableView.bounds.width, height: UIView.layoutFittingCompressedSize.height)
        let size = headerWrapper.systemLayoutSizeFitting(targetSize)
        headerWrapper.frame = CGRect(x: 0, y: 0, width: tableView.bounds.width, height: size.height)
        tableView.tableHeaderView = headerWrapper
    }
}

extension ChatViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        chat.messages.count
    }

    func tableView(
        _ tableView: UITableView,
        cellForRowAt indexPath: IndexPath
    ) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: ChatMessageCell.reuseId, for: indexPath)
        guard let messageCell = cell as? ChatMessageCell else { return cell }
        messageCell.configure(with: chat.messages[indexPath.row])
        return messageCell
    }
}

final class ChatMessageCell: UITableViewCell {
    static let reuseId = "ChatMessageCell"

    private let bubbleView = UIView()
    private let messageLabel = UILabel()
    private var leadingConstraint: NSLayoutConstraint?
    private var trailingConstraint: NSLayoutConstraint?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        backgroundColor = .clear
        contentView.backgroundColor = .clear

        bubbleView.translatesAutoresizingMaskIntoConstraints = false
        bubbleView.layer.cornerRadius = 16
        bubbleView.layer.masksToBounds = true

        messageLabel.translatesAutoresizingMaskIntoConstraints = false
        messageLabel.numberOfLines = 0
        messageLabel.font = UIFont.preferredFont(forTextStyle: .body)

        contentView.addSubview(bubbleView)
        bubbleView.addSubview(messageLabel)

        leadingConstraint = bubbleView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16)
        trailingConstraint = bubbleView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16)
        leadingConstraint?.isActive = true

        NSLayoutConstraint.activate([
            bubbleView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 6),
            bubbleView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -6),
            bubbleView.widthAnchor.constraint(lessThanOrEqualTo: contentView.widthAnchor, multiplier: 0.78),
            messageLabel.topAnchor.constraint(equalTo: bubbleView.topAnchor, constant: 10),
            messageLabel.bottomAnchor.constraint(equalTo: bubbleView.bottomAnchor, constant: -10),
            messageLabel.leadingAnchor.constraint(equalTo: bubbleView.leadingAnchor, constant: 12),
            messageLabel.trailingAnchor.constraint(equalTo: bubbleView.trailingAnchor, constant: -12)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(with message: AppStore.ChatMessage) {
        messageLabel.text = message.content
        let isUser = message.role == "user"
        bubbleView.backgroundColor = isUser
            ? SenchaAppearance.senchaGreen.withAlphaComponent(0.25)
            : UIColor.secondarySystemBackground
        leadingConstraint?.isActive = !isUser
        trailingConstraint?.isActive = isUser
    }
}

final class ChatInputBar: UIView, UITextFieldDelegate {
    let textField = UITextField()
    private let sendButton = UIButton(type: .system)
    private let backgroundView: UIView
    var onSend: (() -> Void)?

    override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: 58)
    }

    override init(frame: CGRect) {
        backgroundView = SenchaAppearance.makeGlassBackgroundView()
        super.init(frame: frame)

        backgroundView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(backgroundView)

        let container = (backgroundView as? UIVisualEffectView)?.contentView ?? backgroundView

        textField.translatesAutoresizingMaskIntoConstraints = false
        textField.placeholder = "Сообщение"
        textField.borderStyle = .none
        textField.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.8)
        textField.layer.cornerRadius = 14
        textField.setLeftPadding(12)
        textField.setRightPadding(12)
        textField.returnKeyType = .send
        textField.delegate = self

        var config = UIButton.Configuration.tinted()
        config.baseBackgroundColor = SenchaAppearance.senchaLeaf
        config.baseForegroundColor = UIColor.white
        config.cornerStyle = .capsule
        config.title = "Отправить"
        sendButton.configuration = config
        sendButton.addTarget(self, action: #selector(handleSend), for: .touchUpInside)

        let stack = UIStackView(arrangedSubviews: [textField, sendButton])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .horizontal
        stack.spacing = 12
        stack.alignment = .center

        container.addSubview(stack)

        NSLayoutConstraint.activate([
            backgroundView.topAnchor.constraint(equalTo: topAnchor),
            backgroundView.bottomAnchor.constraint(equalTo: bottomAnchor),
            backgroundView.leadingAnchor.constraint(equalTo: leadingAnchor),
            backgroundView.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.topAnchor.constraint(equalTo: container.topAnchor, constant: 8),
            stack.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -8),
            stack.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -16),
            textField.heightAnchor.constraint(equalToConstant: 36)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func setSending(_ sending: Bool) {
        sendButton.isEnabled = !sending
    }

    @objc private func handleSend() {
        onSend?()
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        onSend?()
        return true
    }
}

private extension UITextField {
    func setLeftPadding(_ value: CGFloat) {
        let paddingView = UIView(frame: CGRect(x: 0, y: 0, width: value, height: 1))
        leftView = paddingView
        leftViewMode = .always
    }

    func setRightPadding(_ value: CGFloat) {
        let paddingView = UIView(frame: CGRect(x: 0, y: 0, width: value, height: 1))
        rightView = paddingView
        rightViewMode = .always
    }
}
