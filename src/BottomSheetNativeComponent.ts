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
  // Carried with the detent, not in a prop of their own, so a detents change
  // reaches the host in one piece: props within an update arrive in no
  // particular order on Android, and detents are validated as they resolve.
  // The sheet-wide `topInset` is carried the same way, on every detent.
  //
  // Whether the status bar's overlap with the host replaces `insetTop`.
  usesStatusBarTopInset: boolean;
  insetTop: CodegenTypes.Double;
  insetLeft: CodegenTypes.Double;
  insetRight: CodegenTypes.Double;
  insetBottom: CodegenTypes.Double;
  // The widest open card, which the content is laid out against. Flagged in JS,
  // which lays the content out, so every platform agrees on it.
  widestCard: boolean;
  // The scrim’s opacity at this detent, in 0-1. Ignored by inline sheets.
  scrimOpacity: CodegenTypes.Double;
}>;

export interface NativeProps extends ViewProps {
  detents: ReadonlyArray<NativeDetent>;
  index: CodegenTypes.Int32;
  animateIn?: CodegenTypes.WithDefault<boolean, true>;
  animateContentHeight?: CodegenTypes.WithDefault<boolean, true>;
  modal: boolean;
  nativeOverlay?: boolean;
  // When the sheet last opened, relative to other sheets (0 until it first
  // opens). A higher value raises a native-overlay sheet above the others and
  // ranks it first for Android portal close requests.
  openOrder?: CodegenTypes.Int32;
  // Signal that JS supplied `onCloseRequest`. Native event-handler presence is
  // not otherwise observable, so Android (and the web host) use this to decide
  // whether a modal should own Back/Escape input.
  hasCloseRequestHandler: boolean;
  scrollableExpandNegotiation: CodegenTypes.Int32;
  scrollableCollapseNegotiation: CodegenTypes.Int32;
  scrimColor?: ColorValue;
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
      // iOS only. Android and web draw `boxShadow`, which is always opaque here.
      opacity: CodegenTypes.Double;
      // iOS only. `boxShadow` leaves the card's interior unpainted, while the
      // `shadow*` props paint under it. Android and web draw `boxShadow` alone,
      // and exclude the interior themselves.
      excludesInterior: boolean;
    }>
  >;
  // Android only. Casts a system shadow from the card's shape.
  surfaceElevation?: CodegenTypes.WithDefault<CodegenTypes.Double, 0>;
  // Android only, API 28+. The color of the elevation shadow.
  surfaceShadowColor?: ColorValue;
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
