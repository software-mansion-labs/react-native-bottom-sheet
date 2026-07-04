import { codegenNativeComponent, type ViewProps } from 'react-native';

// Wrapper around the sheet content. It carries no props of its own: the
// library identifies it by component type and, in native-overlay mode, owns
// its Yoga geometry — the native side pushes the overlay's real size and the
// detent cap into the wrapper's shadow state, so the content lays out against
// ground truth instead of JS-estimated window dimensions. Inline (in-window)
// sheets never push state, leaving the JS-provided flex styles in effect.
export interface NativeProps extends ViewProps {}

export default codegenNativeComponent<NativeProps>(
  'BottomSheetContentWrapperView'
);
