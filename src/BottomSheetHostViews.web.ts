export {
  BottomSheetView,
  BottomSheetSurfaceView,
} from './web/BottomSheetWebView';
// The web controller transforms the content directly, and portals need no
// native host, so plain views will do.
export {
  View as BottomSheetContentWrapperView,
  View as BottomSheetPortalHostView,
} from 'react-native';
