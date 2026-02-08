import Foundation
import Network

final class NetworkMonitor {
    typealias Observer = (Bool) -> Void

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.sencha.network-monitor")
    private var observers: [UUID: Observer] = [:]

    private(set) var isConnected: Bool = true

    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            let connected = path.status == .satisfied
            guard let self else { return }
            self.isConnected = connected
            DispatchQueue.main.async {
                self.observers.values.forEach { $0(connected) }
            }
        }
        monitor.start(queue: queue)
    }

    func stop() {
        monitor.cancel()
    }

    func addObserver(_ observer: @escaping Observer) -> UUID {
        let id = UUID()
        observers[id] = observer
        observer(isConnected)
        return id
    }

    func removeObserver(_ id: UUID) {
        observers.removeValue(forKey: id)
    }
}
