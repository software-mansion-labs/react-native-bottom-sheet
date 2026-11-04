module.exports = {
  dependency: {
    platforms: {
      android: {
        libraryName: 'ReactNativeBottomSheetSpec',
        componentDescriptors: [
          'BottomSheetViewComponentDescriptor',
          'BottomSheetSurfaceViewComponentDescriptor',
          'BottomSheetContentWrapperViewComponentDescriptor',
        ],
        cmakeListsPath: 'src/main/jni/CMakeLists.txt',
      },
    },
  },
};
