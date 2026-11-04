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
  // Signal that JS supplied `onCloseRequest`. Native event-handler presence is
  // not otherwise observable, so Android (and the web host) use this to decide
  // whether a modal should own Back/Escape input.
  hasCloseRequestHandler: boolean;
  // Consulted natively only in native-overlay mode, where the detent cap is
  // computed from the overlay's real bounds and insets; inline sheets bake the
  // flag into the JS-computed maxDetentHeight as before.
  extendUnderStatusBar?: boolean;
  scrollableExpandNegotiation: CodegenTypes.Int32;
  scrollableCollapseNegotiation: CodegenTypes.Int32;
  scrimColor?: ColorValue;
  scrimOpacities?: ReadonlyArray<CodegenTypes.Double>;
  // Physical corner radii of the card the host clips the surface and content
  // to: top-left, top-right, bottom-right, bottom-left. Empty or all-zero
  // leaves the sheet unclipped.
  surfaceCornerRadii?: ReadonlyArray<CodegenTypes.Double>;
  surfaceCornerCurve?: CodegenTypes.WithDefault<
    'circular' | 'continuous',
    'circular'
  >;
  // Outset shadows cast by the card, drawn outside its clipped shape. Nested
  // colors are not processed by the view config, so JS passes them processed.
  surfaceShadows?: ReadonlyArray<
    Readonly<{
      offsetX: CodegenTypes.Double;
      offsetY: CodegenTypes.Double;
      blurRadius: CodegenTypes.Double;
      spreadDistance: CodegenTypes.Double;
      color: ColorValue;
    }>
  >;
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
