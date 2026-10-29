import type { ColorValue, ViewProps } from 'react-native';
import type {
  DirectEventHandler,
  Double,
  Int32,
  WithDefault,
} from './codegenTypes';
// RN 0.76 exposes this factory only through the legacy path.
// eslint-disable-next-line @react-native/no-deep-imports
import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';

type NativeDetent = Readonly<{
  value: Double;
  kind: string;
  programmatic: boolean;
}>;

export interface NativeProps extends ViewProps {
  detents: ReadonlyArray<NativeDetent>;
  index: Int32;
  animateIn?: WithDefault<boolean, true>;
  animateContentHeight?: WithDefault<boolean, true>;
  modal: boolean;
  nativeOverlay?: boolean;
  // Signal that JS supplied `onCloseRequest`. Native event-handler presence is
  // not otherwise observable, so Android (and the web host) use this to decide
  // whether a modal should own Back/Escape input.
  hasCloseRequestHandler: boolean;
  // Consulted natively only in native-overlay mode, where the detent cap is
  // computed from the overlay's real bounds and insets; inline sheets bake the
  // flag into the JS-computed maxDetentHeight as before.
  extendUnderStatusBar?: boolean;
  scrollableExpandNegotiation: Int32;
  scrollableCollapseNegotiation: Int32;
  scrimColor?: ColorValue;
  scrimOpacities?: ReadonlyArray<Double>;
  onIndexChange?: DirectEventHandler<Readonly<{ index: Int32 }>>;
  onSettle?: DirectEventHandler<Readonly<{ index: Int32 }>>;
  onPositionChange?: DirectEventHandler<
    Readonly<{ position: Double; index: Double }>
  >;
  onCloseRequest?: DirectEventHandler<null>;
}

export default codegenNativeComponent<NativeProps>('BottomSheetView');
