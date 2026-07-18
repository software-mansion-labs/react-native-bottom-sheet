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
  // Consulted natively only in native-overlay mode, where the detent cap is
  // computed from the overlay's real bounds and insets; inline sheets bake the
  // flag into the JS-computed maxDetentHeight as before.
  extendUnderStatusBar?: boolean;
  // Floats the sheet up off the bottom edge by this many points (a detached /
  // "floating card" sheet). The detent cap shrinks to keep the sheet inside the
  // safe region above the inset, and the floating bottom edge is clipped +
  // rounded via `cornerRadius`. Horizontal detachment is done by the consumer
  // insetting the host `style` (e.g. `{ left, right }`). Default 0 (anchored).
  bottomInset?: CodegenTypes.WithDefault<CodegenTypes.Double, 0>;
  // Corner radius (points) applied to the detached sheet's floating bottom
  // corners. Match it to the surface's top radius for a uniform card. Only used
  // when `bottomInset > 0`. Default 0 (square clip).
  cornerRadius?: CodegenTypes.WithDefault<CodegenTypes.Double, 0>;
  // Corner curve applied to the detached sheet's floating bottom corners on
  // iOS. Consumers should apply the same `borderCurve` style to their surface
  // so all four corners match. Android keeps its platform round-rect curve.
  borderCurve?: CodegenTypes.WithDefault<'circular' | 'continuous', 'circular'>;
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
}

export default codegenNativeComponent<NativeProps>('BottomSheetView');
