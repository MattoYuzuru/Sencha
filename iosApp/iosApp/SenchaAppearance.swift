import UIKit

enum SenchaAppearance {
    static let senchaGreen = UIColor(red: 0.49, green: 0.75, blue: 0.62, alpha: 1.0)
    static let senchaLeaf = UIColor(red: 0.36, green: 0.56, blue: 0.47, alpha: 1.0)
    static let senchaMist = UIColor(red: 0.91, green: 0.95, blue: 0.93, alpha: 1.0)
    static let senchaClay = UIColor(red: 0.96, green: 0.95, blue: 0.93, alpha: 1.0)

    static func applyNavigationBar(_ navigationBar: UINavigationBar) {
        let appearance = UINavigationBarAppearance()
        configureGlassBackground(appearance)
        appearance.titleTextAttributes = [
            .foregroundColor: UIColor.label,
            .font: UIFont.preferredFont(forTextStyle: .headline)
        ]
        navigationBar.standardAppearance = appearance
        navigationBar.scrollEdgeAppearance = appearance
        navigationBar.tintColor = senchaLeaf
    }

    static func applyTabBar(_ tabBar: UITabBar) {
        let appearance = UITabBarAppearance()
        configureGlassBackground(appearance)
        tabBar.standardAppearance = appearance
        tabBar.scrollEdgeAppearance = appearance
        tabBar.tintColor = senchaLeaf
        tabBar.unselectedItemTintColor = UIColor.secondaryLabel
    }

    static func makeGlassBackgroundView() -> UIView {
        if UIAccessibility.isReduceTransparencyEnabled {
            let view = UIView()
            view.backgroundColor = UIColor.systemBackground
            return view
        }
        let view = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterial))
        view.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.6)
        return view
    }

    private static func configureGlassBackground(_ navAppearance: UINavigationBarAppearance) {
        let blurEffect = UIAccessibility.isReduceTransparencyEnabled ? nil : UIBlurEffect(style: .systemThinMaterial)
        navAppearance.configureWithTransparentBackground()
        navAppearance.backgroundEffect = blurEffect
        navAppearance.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.6)
    }

    private static func configureGlassBackground(_ tabAppearance: UITabBarAppearance) {
        let blurEffect = UIAccessibility.isReduceTransparencyEnabled ? nil : UIBlurEffect(style: .systemThinMaterial)
        tabAppearance.configureWithTransparentBackground()
        tabAppearance.backgroundEffect = blurEffect
        tabAppearance.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.7)
    }
}
