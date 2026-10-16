import React, { useLayoutEffect } from 'react';
import { AppRegistry, NativeModules, View } from 'react-native';
import NativeSheet from '../../src/BottomSheetNativeComponent';
import { renderPortalHost } from '../../src/PortalHost';

function MountedPublication({ generation = 0, contextInWrapper = false }) {
  useLayoutEffect(() => {
    NativeModules.PublicationMountProbe.didCommit(generation);
  }, [generation]);
  const sheetProps = {
    style: { flex: 1 },
    detents: [
      { value: 0, kind: 'points', programmatic: false },
      { value: 200, kind: 'points', programmatic: false },
    ],
    index: 1,
    modal: true,
    animateIn: false,
    hasCloseRequestHandler: false,
    scrollableExpandNegotiation: 0,
    scrollableCollapseNegotiation: 0,
  };
  const sheet = React.createElement(NativeSheet, {
    ...sheetProps,
    testID: 'mounted-sheet',
  });
  return React.createElement(
    View,
    { collapsable: false, style: { width: 640, height: 640 } },
    React.createElement(
      View,
      {
        testID: 'local-area',
        collapsable: false,
        style: { width: 320, height: 640, position: 'relative' },
      },
      React.createElement(View, {
        testID: 'local-background',
        accessibilityLabel: `Background generation ${generation}`,
        style: { position: 'absolute', width: 20, height: 20 },
      }),
      React.createElement(
        View,
        { style: { position: contextInWrapper ? 'relative' : 'static' } },
        renderPortalHost([['one', sheet]])
      )
    ),
    React.createElement(NativeSheet, {
      ...sheetProps,
      testID: 'inactive-nonportal-sheet',
      index: 0,
      modal: false,
    })
  );
}

AppRegistry.registerComponent('MountedPublication', () => MountedPublication);
