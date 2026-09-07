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
  // Android-only signal that JS supplied `onCloseRequest`. Native event-handler
  // presence is not otherwise observable, so Android uses this to decide whether
  // a modal should own Back/Escape input.
  hasCloseRequestHandler: boolean;
  // Consulted natively only in native-overlay mode, where the detent cap is
  // computed from the overlay's real bounds and insets; inline sheets bake the
  // flag into the JS-computed maxDetentHeight as before.
  extendUnderStatusBar?: boolean;
  scrollableExpandNegotiation: CodegenTypes.Int32;
  scrollableCollapseNegotiation: CodegenTypes.Int32;
  // Seconds of release velocity projected onto the sheet's position before the
  // detent a drag release resolves to is chosen. 0 disables projection.
  releaseProjection?: CodegenTypes.WithDefault<CodegenTypes.Double, 0>;
  // Seconds the settle animation takes. 0 defers to the platform default.
  settleDuration?: CodegenTypes.WithDefault<CodegenTypes.Double, 0>;
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
  onCloseRequest?: CodegenTypes.DirectEventHandler<null>;
}

export default codegenNativeComponent<NativeProps>('BottomSheetView');
