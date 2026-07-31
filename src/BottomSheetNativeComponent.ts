import {
  codegenNativeComponent,
  type CodegenTypes,
  type ColorValue,
  type ViewProps,
} from 'react-native';

type NativeDetent = Readonly<{
  value: CodegenTypes.Double;
  kind: string;
  programmatic: boolean;
}>;

export interface NativeProps extends ViewProps {
  detents: ReadonlyArray<NativeDetent>;
  index: CodegenTypes.Int32;
  animateIn?: CodegenTypes.WithDefault<boolean, true>;
  animateContentHeight?: CodegenTypes.WithDefault<boolean, true>;
  modal: boolean;
  nativeOverlay?: boolean;
  // The direct event exists in the shared schema on both platforms; this
  // separate boolean is the Android native gate for callback presence.
  requestCloseEnabled: boolean;
  // Consulted natively only in native-overlay mode, where the detent cap is
  // computed from the overlay's real bounds and insets; inline sheets bake the
  // flag into the JS-computed maxDetentHeight as before.
  extendUnderStatusBar?: boolean;
  scrollableExpandNegotiation: CodegenTypes.Int32;
  scrollableCollapseNegotiation: CodegenTypes.Int32;
  scrimColor?: ColorValue;
  scrimOpacities?: ReadonlyArray<CodegenTypes.Double>;
  onIndexChange?: CodegenTypes.DirectEventHandler<
    Readonly<{ index: CodegenTypes.Int32 }>
  >;
  onSettle?: CodegenTypes.DirectEventHandler<
    Readonly<{ index: CodegenTypes.Int32 }>
  >;
  onPositionChange?: CodegenTypes.DirectEventHandler<
    Readonly<{ position: CodegenTypes.Double; index: CodegenTypes.Double }>
  >;
  onRequestClose?: CodegenTypes.DirectEventHandler<null>;
}

export default codegenNativeComponent<NativeProps>('BottomSheetView');
