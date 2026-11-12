import { codegenNativeComponent, type ViewProps } from 'react-native';

// The container `BottomSheetProvider` renders its portals into. It carries no
// props of its own: modal sheets find it by component type, and while one of
// them is open it hides its siblings, the screen behind the sheets, from
// screen readers.
export interface NativeProps extends ViewProps {}

export default codegenNativeComponent<NativeProps>('BottomSheetPortalHostView');
