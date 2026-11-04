// The views `BottomSheet` renders, resolved per platform. The web build swaps
// in `BottomSheetHostViews.web.ts`; the split lives here rather than on the
// codegen specs, which are published as TypeScript and would otherwise win
// Metro's platform resolution over their compiled web counterparts.
export { default as BottomSheetView } from './BottomSheetNativeComponent';
export { default as BottomSheetSurfaceView } from './BottomSheetSurfaceNativeComponent';
export { default as BottomSheetContentWrapperView } from './BottomSheetContentWrapperNativeComponent';
