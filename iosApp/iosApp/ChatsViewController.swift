import UIKit

final class ChatsViewController: UITableViewController {
    private let store: AppStore
    private let networkMonitor: NetworkMonitor
    private var networkObserverId: UUID?
    private var isConnected = true
    private let headerWrapper = UIView()
    private let headerContainer = UIView()
    private let headerLabel = UILabel()

    init(store: AppStore, networkMonitor: NetworkMonitor) {
        self.store = store
        self.networkMonitor = networkMonitor
        super.init(style: .insetGrouped)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Чаты"
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "ChatCell")
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .add,
            target: self,
            action: #selector(createChat)
        )
        configureHeader()
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
        if let id = networkObserverId {
            networkMonitor.removeObserver(id)
        }
    }

    @objc private func createChat() {
        let alert = UIAlertController(title: "Новый чат", message: "Название чата", preferredStyle: .alert)
        alert.addTextField { field in
            field.placeholder = "Название"
        }
        alert.addAction(UIAlertAction(title: "Отмена", style: .cancel))
        alert.addAction(UIAlertAction(title: "Создать", style: .default, handler: { [weak self] _ in
            guard let self else { return }
            let title = alert.textFields?.first?.text ?? ""
            let chat = self.store.createChat(title: title)
            self.tableView.reloadData()
            self.showChat(chat)
        }))
        present(alert, animated: true)
    }

    override func numberOfSections(in tableView: UITableView) -> Int {
        return 2
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if section == 0 {
            return store.chats.count
        }
        return 1
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "ChatCell", for: indexPath)
        var content = cell.defaultContentConfiguration()
        if indexPath.section == 0 {
            let chat = store.chats[indexPath.row]
            content.text = chat.title
            content.secondaryText = "Модель: \(chat.model.name)"
            cell.accessoryType = .disclosureIndicator
        } else {
            content.text = store.chats.isEmpty ? "Создайте чат, чтобы начать" : ""
            content.secondaryText = nil
            cell.accessoryType = .none
        }
        cell.contentConfiguration = content
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard indexPath.section == 0 else { return }
        showChat(store.chats[indexPath.row])
    }

    private func showChat(_ chat: AppStore.ChatItem) {
        let controller = ChatViewController(chat: chat, networkMonitor: networkMonitor)
        navigationController?.pushViewController(controller, animated: true)
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
        if isConnected {
            tableView.tableHeaderView = nil
            return
        }
        headerLabel.text = "Нет сети. Удаленные модели недоступны."
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
