module.exports = {
  dependency: {
    platforms: {
      android: {
        libraryName: 'ReactNativeBottomSheetSpec',
        componentDescriptors: [
          'BottomSheetViewComponentDescriptor',
          'BottomSheetSurfaceViewComponentDescriptor',
          'BottomSheetContentWrapperViewComponentDescriptor',
          'BottomSheetPortalHostViewComponentDescriptor',
        ],
        cmakeListsPath: 'src/main/jni/CMakeLists.txt',
      },
    },
  },
};
