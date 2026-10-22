import type { ViewProps } from 'react-native';
// RN 0.76 exposes this factory only through the legacy path.
// eslint-disable-next-line @react-native/no-deep-imports
import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';

// The visual surface that sits behind the sheet content. It carries no props of
// its own: the library identifies it by component type and owns its geometry,
// sizing it to cover the full sheet so a content shrink never exposes blank
// space. Its React children provide the visual appearance only.
export interface NativeProps extends ViewProps {}

export default codegenNativeComponent<NativeProps>('BottomSheetSurfaceView');
