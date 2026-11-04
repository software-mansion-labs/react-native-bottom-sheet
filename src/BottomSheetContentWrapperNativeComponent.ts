import { codegenNativeComponent, type ViewProps } from 'react-native';

// The in-flow wrapper around the sheet's content. It carries no props of its
// own: it exists so its shadow node can report the card's scale as its
// transform, which keeps `measure` in step with what is drawn even where the
// sheet is a layout root. Natively it is a plain view.
export interface NativeProps extends ViewProps {}

export default codegenNativeComponent<NativeProps>(
  'BottomSheetContentWrapperView'
);
