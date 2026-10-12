const { withAppDelegate, withInfoPlist } = require('@expo/config-plugins');

const sceneConfigurationMethod = `
  public func application(
    _ application: UIApplication,
    configurationForConnecting connectingSceneSession: UISceneSession,
    options: UIScene.ConnectionOptions
  ) -> UISceneConfiguration {
    let configuration = UISceneConfiguration(
      name: "Default Configuration",
      sessionRole: connectingSceneSession.role
    )
    configuration.delegateClass = SceneDelegate.self
    return configuration
  }
`;

const sceneDelegateClass = `
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
  var window: UIWindow?

  func scene(
    _ scene: UIScene,
    willConnectTo session: UISceneSession,
    options connectionOptions: UIScene.ConnectionOptions
  ) {
    guard let windowScene = scene as? UIWindowScene,
      let appDelegate = UIApplication.shared.delegate as? AppDelegate,
      let factory = appDelegate.reactNativeFactory
    else {
      return
    }

    let nextWindow = UIWindow(windowScene: windowScene)
    window = nextWindow
    appDelegate.window = nextWindow
    factory.startReactNative(
      withModuleName: "main",
      in: nextWindow,
      launchOptions: nil
    )

    if !connectionOptions.urlContexts.isEmpty {
      self.scene(scene, openURLContexts: connectionOptions.urlContexts)
    }
  }

  func scene(_ scene: UIScene, openURLContexts urlContexts: Set<UIOpenURLContext>) {
    guard let urlContext = urlContexts.first,
      let appDelegate = UIApplication.shared.delegate as? AppDelegate
    else {
      return
    }

    var options: [UIApplication.OpenURLOptionsKey: Any] = [
      .openInPlace: urlContext.options.openInPlace,
    ]
    if let sourceApplication = urlContext.options.sourceApplication {
      options[.sourceApplication] = sourceApplication
    }
    if let annotation = urlContext.options.annotation {
      options[.annotation] = annotation
    }

    _ = appDelegate.application(UIApplication.shared, open: urlContext.url, options: options)
  }
}
`;

function withIosSceneLifecycle(config) {
  config = withInfoPlist(config, (nextConfig) => {
    nextConfig.modResults.UIApplicationSceneManifest = {
      UIApplicationSupportsMultipleScenes: false,
      UISceneConfigurations: {
        UIWindowSceneSessionRoleApplication: [
          {
            UISceneConfigurationName: 'Default Configuration',
            UISceneDelegateClassName: '$(PRODUCT_MODULE_NAME).SceneDelegate',
          },
        ],
      },
    };
    return nextConfig;
  });

  return withAppDelegate(config, (nextConfig) => {
    if (nextConfig.modResults.language !== 'swift') {
      throw new Error('iOS scene lifecycle requires a Swift AppDelegate.');
    }

    let { contents } = nextConfig.modResults;
    if (
      contents.includes(
        'class SceneDelegate: UIResponder, UIWindowSceneDelegate'
      )
    ) {
      return nextConfig;
    }

    const startupBlock =
      /#if os\(iOS\) \|\| os\(tvOS\)\n\s*window = UIWindow\(frame: UIScreen\.main\.bounds\)\n\s*factory\.startReactNative\(\n\s*withModuleName: "main",\n\s*in: window,\n\s*launchOptions: launchOptions\)\n#endif/;
    if (!startupBlock.test(contents)) {
      throw new Error(
        'Could not find the Expo React Native startup block in AppDelegate.swift.'
      );
    }

    let updatedContents = contents
      .replace(
        startupBlock,
        `#if os(iOS) || os(tvOS)
    if #unavailable(iOS 13.0) {
      window = UIWindow(frame: UIScreen.main.bounds)
      factory.startReactNative(
        withModuleName: "main",
        in: window,
        launchOptions: launchOptions)
    }
#endif`
      )
      .replace(
        '\n  // Linking API',
        `\n${sceneConfigurationMethod}\n  // Linking API`
      )
      .replace(
        '\nclass ReactNativeDelegate: ExpoReactNativeFactoryDelegate',
        `\n${sceneDelegateClass}\nclass ReactNativeDelegate: ExpoReactNativeFactoryDelegate`
      );

    nextConfig.modResults.contents = updatedContents;
    return nextConfig;
  });
}

module.exports = withIosSceneLifecycle;
