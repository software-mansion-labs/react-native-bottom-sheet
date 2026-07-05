const { AndroidConfig, withStringsXml } = require('expo/config-plugins');

// Overrides the Android launcher label. Expo otherwise derives it from the
// Expo config `name`, which must stay "React Native Bottom Sheet Example" so
// that the generated native project names don't collide with the library pod
// (iOS scheme-name ambiguity). iOS uses `ios.infoPlist.CFBundleDisplayName`
// for the same purpose.
module.exports = function withAndroidAppName(config, appName) {
  return withStringsXml(config, (modConfig) => {
    modConfig.modResults = AndroidConfig.Strings.setStringItem(
      [
        AndroidConfig.Resources.buildResourceItem({
          name: 'app_name',
          value: appName,
        }),
      ],
      modConfig.modResults
    );
    return modConfig;
  });
};
