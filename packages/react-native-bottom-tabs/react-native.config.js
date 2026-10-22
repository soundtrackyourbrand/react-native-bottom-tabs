module.exports = {
  dependency: {
    platforms: {
      ios: {},
      android: {
        componentDescriptors: ['RNCTabViewComponentDescriptor'],
        cmakeListsPath: '../android/src/main/jni/CMakeLists.txt',
      },
    },
  },
  spm: {
    name: 'react_native_bottom_tabs',
  },
};
