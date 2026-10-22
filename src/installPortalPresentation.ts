import { Platform, TurboModuleRegistry, type TurboModule } from 'react-native';

// Request the internal native adapter before the first sheet mount. RCTInstance
// supplies its own presenter; applications do not pass a root or a boundary.
if (Platform.OS === 'ios') {
  TurboModuleRegistry.getEnforcing<TurboModule>('BottomSheetPresentation');
}
