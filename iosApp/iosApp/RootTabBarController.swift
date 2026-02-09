import UIKit

final class RootTabBarController: UITabBarController {
    private let store = AppStore()
    private let networkMonitor = NetworkMonitor()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.systemBackground
        networkMonitor.start()

        let chats = ChatsViewController(store: store, networkMonitor: networkMonitor)
        let models = ModelsViewController(store: store, networkMonitor: networkMonitor)
        let connections = ConnectionsViewController(networkMonitor: networkMonitor)

        let chatsNav = UINavigationController(rootViewController: chats)
        let modelsNav = UINavigationController(rootViewController: models)
        let connectionsNav = UINavigationController(rootViewController: connections)

        chatsNav.tabBarItem = UITabBarItem(title: "Чаты", image: UIImage(systemName: "bubble.left"), tag: 0)
        modelsNav.tabBarItem = UITabBarItem(title: "Модели", image: UIImage(systemName: "slider.horizontal.3"), tag: 1)
        connectionsNav.tabBarItem = UITabBarItem(title: "Связи", image: UIImage(systemName: "link"), tag: 2)

        SenchaAppearance.applyNavigationBar(chatsNav.navigationBar)
        SenchaAppearance.applyNavigationBar(modelsNav.navigationBar)
        SenchaAppearance.applyNavigationBar(connectionsNav.navigationBar)
        SenchaAppearance.applyTabBar(tabBar)

        viewControllers = [chatsNav, modelsNav, connectionsNav]
    }

    deinit {
        networkMonitor.stop()
    }
}

final class ConnectionsViewController: UIViewController {
    private struct NodeItem {
        let id: UUID
        var name: String
        var address: String
        var lastSeen: Date?
        var status: String?
    }

    private let networkMonitor: NetworkMonitor
    private var nodeItems: [NodeItem] = [] {
        didSet { refreshNodes() }
    }

    private var observerId: UUID?

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    private let offlineLabel = UILabel()
    private let syncStatusLabel = UILabel()
    private let syncErrorLabel = UILabel()
    private let serverField = UITextField()
    private let codeField = UITextField()
    private let deviceField = UITextField()
    private let syncButton = UIButton(type: .system)
    private let connectButton = UIButton(type: .system)
    private let nodesStack = UIStackView()
    private let nodeNameField = UITextField()
    private let nodeAddressField = UITextField()
    private let addNodeButton = UIButton(type: .system)

    init(networkMonitor: NetworkMonitor) {
        self.networkMonitor = networkMonitor
        super.init(nibName: nil, bundle: nil)
        title = "Связи"
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        setupLayout()
        refreshNodes()
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

        offlineLabel.text = "Оффлайн: синхронизация приостановлена"
        offlineLabel.textColor = .secondaryLabel
        offlineLabel.font = .preferredFont(forTextStyle: .footnote)
        offlineLabel.isHidden = true
        contentStack.addArrangedSubview(offlineLabel)

        contentStack.addArrangedSubview(makeSectionTitle("Sync storage"))

        configureField(serverField, placeholder: "https://sync.example.com")
        configureField(codeField, placeholder: "Одноразовый код")
        configureField(deviceField, placeholder: "Имя устройства (опционально)")
        contentStack.addArrangedSubview(serverField)
        contentStack.addArrangedSubview(codeField)
        contentStack.addArrangedSubview(deviceField)

        connectButton.setTitle("Подключить", for: .normal)
        connectButton.addTarget(self, action: #selector(connectTapped), for: .touchUpInside)
        syncButton.setTitle("Синхронизировать", for: .normal)
        syncButton.addTarget(self, action: #selector(syncTapped), for: .touchUpInside)

        let syncButtons = UIStackView(arrangedSubviews: [connectButton, syncButton])
        syncButtons.axis = .horizontal
        syncButtons.spacing = 12
        contentStack.addArrangedSubview(syncButtons)

        syncStatusLabel.text = "Статус: не подключено"
        syncStatusLabel.font = .preferredFont(forTextStyle: .subheadline)
        contentStack.addArrangedSubview(syncStatusLabel)

        syncErrorLabel.textColor = .systemRed
        syncErrorLabel.font = .preferredFont(forTextStyle: .footnote)
        syncErrorLabel.numberOfLines = 0
        contentStack.addArrangedSubview(syncErrorLabel)

        contentStack.addArrangedSubview(makeSectionTitle("Compute nodes"))

        let onboarding = UILabel()
        onboarding.text = "Рекомендуем: используйте Tailscale/ZeroTier для подключения домашнего сервера без проброса портов."
        onboarding.font = .preferredFont(forTextStyle: .footnote)
        onboarding.textColor = .secondaryLabel
        onboarding.numberOfLines = 0
        contentStack.addArrangedSubview(onboarding)

        nodesStack.axis = .vertical
        nodesStack.spacing = 8
        contentStack.addArrangedSubview(nodesStack)

        contentStack.addArrangedSubview(makeSectionTitle("Добавить узел"))
        configureField(nodeNameField, placeholder: "Имя узла")
        configureField(nodeAddressField, placeholder: "Адрес (например, https://node.local)")
        contentStack.addArrangedSubview(nodeNameField)
        contentStack.addArrangedSubview(nodeAddressField)

        addNodeButton.setTitle("Сохранить узел", for: .normal)
        addNodeButton.addTarget(self, action: #selector(addNodeTapped), for: .touchUpInside)
        contentStack.addArrangedSubview(addNodeButton)
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

    private func refreshNodes() {
        nodesStack.arrangedSubviews.forEach { view in
            nodesStack.removeArrangedSubview(view)
            view.removeFromSuperview()
        }

        if nodeItems.isEmpty {
            let empty = UILabel()
            empty.text = "Узлы не добавлены."
            empty.textColor = .secondaryLabel
            empty.font = .preferredFont(forTextStyle: .footnote)
            nodesStack.addArrangedSubview(empty)
            return
        }

        nodeItems.forEach { node in
            let card = UIView()
            card.backgroundColor = UIColor.secondarySystemBackground
            card.layer.cornerRadius = 12
            card.translatesAutoresizingMaskIntoConstraints = false

            let stack = UIStackView()
            stack.axis = .vertical
            stack.spacing = 4
            stack.translatesAutoresizingMaskIntoConstraints = false

            let title = UILabel()
            title.text = node.name
            title.font = .preferredFont(forTextStyle: .subheadline)

            let address = UILabel()
            address.text = node.address
            address.font = .preferredFont(forTextStyle: .footnote)
            address.textColor = .secondaryLabel

            let status = UILabel()
            status.text = node.status ?? "Тест не выполнялся"
            status.font = .preferredFont(forTextStyle: .footnote)
            status.textColor = .secondaryLabel

            let testButton = UIButton(type: .system)
            testButton.setTitle("Test connection", for: .normal)
            testButton.addAction(UIAction { [weak self] _ in
                self?.testNode(node)
            }, for: .touchUpInside)

            stack.addArrangedSubview(title)
            stack.addArrangedSubview(address)
            stack.addArrangedSubview(status)
            stack.addArrangedSubview(testButton)

            card.addSubview(stack)
            NSLayoutConstraint.activate([
                stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 12),
                stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 12),
                stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -12),
                stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -12)
            ])

            nodesStack.addArrangedSubview(card)
        }
    }

    @objc private func connectTapped() {
        syncErrorLabel.text = nil
        #if !DEBUG
        if let text = serverField.text, text.hasPrefix("http://") {
            syncErrorLabel.text = "HTTPS обязателен для релизной сборки."
            return
        }
        #endif
        syncStatusLabel.text = "Статус: подключено"
    }

    @objc private func syncTapped() {
        syncErrorLabel.text = nil
        syncStatusLabel.text = "Синхронизация запущена"
    }

    @objc private func addNodeTapped() {
        guard let name = nodeNameField.text, !name.isEmpty,
              let address = nodeAddressField.text, !address.isEmpty else {
            syncErrorLabel.text = "Заполните имя и адрес узла"
            return
        }
        nodeItems.append(NodeItem(id: UUID(), name: name, address: address, lastSeen: nil, status: nil))
        nodeNameField.text = nil
        nodeAddressField.text = nil
    }

    private func testNode(_ node: NodeItem) {
        guard let url = URL(string: "\(node.address)/v1/health") else {
            updateNode(node, status: "Некорректный адрес")
            return
        }
        let task = URLSession.shared.dataTask(with: url) { [weak self] _, response, error in
            DispatchQueue.main.async {
                if let error {
                    self?.updateNode(node, status: "Ошибка: \(error.localizedDescription)")
                } else if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                    self?.updateNode(node, status: "OK")
                } else {
                    self?.updateNode(node, status: "Нет ответа")
                }
            }
        }
        task.resume()
    }

    private func updateNode(_ node: NodeItem, status: String) {
        if let index = nodeItems.firstIndex(where: { $0.id == node.id }) {
            nodeItems[index].status = status
        }
    }
}
