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

        let chatsNav = UINavigationController(rootViewController: chats)
        let modelsNav = UINavigationController(rootViewController: models)

        chatsNav.tabBarItem = UITabBarItem(title: "Чаты", image: UIImage(systemName: "bubble.left"), tag: 0)
        modelsNav.tabBarItem = UITabBarItem(title: "Модели", image: UIImage(systemName: "slider.horizontal.3"), tag: 1)

        SenchaAppearance.applyNavigationBar(chatsNav.navigationBar)
        SenchaAppearance.applyNavigationBar(modelsNav.navigationBar)
        SenchaAppearance.applyTabBar(tabBar)

        viewControllers = [chatsNav, modelsNav]
    }

    deinit {
        networkMonitor.stop()
    }
}
