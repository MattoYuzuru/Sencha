import UIKit

final class ModelsViewController: UITableViewController {
    private let store: AppStore
    private let networkMonitor: NetworkMonitor
    private var networkObserverId: UUID?
    private var isConnected = true
    private let headerWrapper = UIView()
    private let headerContainer = UIView()
    private let headerLabel = UILabel()
    private let headerSpinner = UIActivityIndicatorView(style: .medium)
    private let emptyLabel = UILabel()
    private let categoryPriority = ["Чат", "Vision", "Audio", "Video"]

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
        title = "Модели"
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "ModelCell")
        emptyLabel.text = "Пока нет моделей. Добавьте провайдеры."
        emptyLabel.textColor = UIColor.secondaryLabel
        emptyLabel.textAlignment = .center
        emptyLabel.numberOfLines = 0
        emptyLabel.font = UIFont.preferredFont(forTextStyle: .body)
        configureHeader()
        updateEmptyState()
        networkObserverId = networkMonitor.addObserver { [weak self] connected in
            self?.isConnected = connected
            self?.updateHeader()
            self?.tableView.reloadData()
            self?.updateEmptyState()
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

    override func numberOfSections(in tableView: UITableView) -> Int {
        return groupedModels.count
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return groupedModels[section].models.count
    }

    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        return groupedModels[section].category
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "ModelCell", for: indexPath)
        let model = groupedModels[indexPath.section].models[indexPath.row]
        var content = cell.defaultContentConfiguration()
        content.text = model.name
        content.secondaryText = model.requiresNetwork ? "\(model.provider) · Нужна сеть" : model.provider
        cell.contentConfiguration = content
        cell.accessoryType = model.id == store.selectedModel.id ? .checkmark : .none
        if model.requiresNetwork && !isConnected {
            cell.contentView.alpha = 0.5
            cell.isUserInteractionEnabled = false
            cell.selectionStyle = .none
        } else {
            cell.contentView.alpha = 1.0
            cell.isUserInteractionEnabled = true
            cell.selectionStyle = .default
        }
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let model = groupedModels[indexPath.section].models[indexPath.row]
        guard !(model.requiresNetwork && !isConnected) else {
            presentOfflineAlert()
            return
        }
        store.selectModel(model)
        tableView.reloadData()
    }

    private var groupedModels: [(category: String, models: [AppStore.ModelItem])] {
        let grouped = Dictionary(grouping: store.models, by: { $0.category })
        let sortedCategories = grouped.keys.sorted { left, right in
            let leftIndex = categoryPriority.firstIndex(of: left) ?? Int.max
            let rightIndex = categoryPriority.firstIndex(of: right) ?? Int.max
            if leftIndex == rightIndex {
                return left < right
            }
            return leftIndex < rightIndex
        }
        return sortedCategories.map { category in
            (category: category, models: grouped[category] ?? [])
        }
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

        headerSpinner.translatesAutoresizingMaskIntoConstraints = false
        headerSpinner.hidesWhenStopped = true

        headerWrapper.addSubview(headerContainer)
        headerContainer.addSubview(headerLabel)
        headerContainer.addSubview(headerSpinner)

        NSLayoutConstraint.activate([
            headerContainer.topAnchor.constraint(equalTo: headerWrapper.topAnchor, constant: 12),
            headerContainer.bottomAnchor.constraint(equalTo: headerWrapper.bottomAnchor, constant: -12),
            headerContainer.leadingAnchor.constraint(equalTo: headerWrapper.leadingAnchor, constant: 16),
            headerContainer.trailingAnchor.constraint(equalTo: headerWrapper.trailingAnchor, constant: -16),
            headerSpinner.centerYAnchor.constraint(equalTo: headerLabel.centerYAnchor),
            headerSpinner.leadingAnchor.constraint(equalTo: headerContainer.leadingAnchor, constant: 16),
            headerLabel.topAnchor.constraint(equalTo: headerContainer.topAnchor, constant: 12),
            headerLabel.bottomAnchor.constraint(equalTo: headerContainer.bottomAnchor, constant: -12),
            headerLabel.leadingAnchor.constraint(equalTo: headerSpinner.trailingAnchor, constant: 12),
            headerLabel.trailingAnchor.constraint(equalTo: headerContainer.trailingAnchor, constant: -16)
        ])
        updateHeader()
    }

    private func updateHeader() {
        if store.isLoadingModels {
            headerLabel.text = "Загрузка моделей…"
            headerSpinner.startAnimating()
            tableView.tableHeaderView = headerWrapper
            updateHeaderSize()
            updateEmptyState()
            return
        }
        if let error = store.modelsError {
            headerLabel.text = "Ошибка: \(error)"
            headerSpinner.stopAnimating()
            tableView.tableHeaderView = headerWrapper
            updateHeaderSize()
            updateEmptyState()
            return
        }
        if !isConnected {
            headerLabel.text = "Нет сети. Доступны только локальные модели."
            headerSpinner.stopAnimating()
            tableView.tableHeaderView = headerWrapper
            updateHeaderSize()
            updateEmptyState()
            return
        }
        headerSpinner.stopAnimating()
        tableView.tableHeaderView = nil
        updateEmptyState()
    }

    private func updateHeaderSize() {
        guard tableView.tableHeaderView === headerWrapper else { return }
        let targetSize = CGSize(width: tableView.bounds.width, height: UIView.layoutFittingCompressedSize.height)
        let size = headerWrapper.systemLayoutSizeFitting(targetSize)
        headerWrapper.frame = CGRect(x: 0, y: 0, width: tableView.bounds.width, height: size.height)
        tableView.tableHeaderView = headerWrapper
    }

    private func presentOfflineAlert() {
        let alert = UIAlertController(
            title: "Нет сети",
            message: "Эта модель требует сетевого подключения.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Ок", style: .default))
        present(alert, animated: true)
    }

    private func updateEmptyState() {
        tableView.backgroundView = groupedModels.isEmpty ? emptyLabel : nil
    }
}
