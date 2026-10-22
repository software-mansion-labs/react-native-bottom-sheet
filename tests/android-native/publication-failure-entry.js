// Test-only real ReactSurface/source fixture; no Activity/window/UI.
import React, { useLayoutEffect } from 'react';
import {
  AppRegistry,
  NativeModules,
  View,
  requireNativeComponent,
} from 'react-native';
import NativeSheet from '../../src/BottomSheetNativeComponent';
import { renderPortalHost } from '../../src/PortalHost';

const FailureProbe = requireNativeComponent('MountFailureProbeView');
function FailurePublication({ generation = 0 }) {
  useLayoutEffect(() => {
    NativeModules.MountFailureProbe.didCommit(generation);
  }, [generation]);
  return React.createElement(
    View,
    {
      collapsable: false,
      style: { width: 640, height: 640 },
    },
    React.createElement(
      View,
      {
        testID: 'local-area',
        collapsable: false,
        style: { position: 'relative', width: 320, height: 640 },
      },
      React.createElement(View, {
        testID: 'local-background',
        accessibilityLabel: 'Background',
        style: { width: 20, height: 20 },
      }),
      React.createElement(
        View,
        {
          style: { position: generation > 0 ? 'relative' : 'static' },
        },
        renderPortalHost([
          [
            'one',
            React.createElement(NativeSheet, {
              testID: 'mounted-sheet',
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
            }),
          ],
        ])
      )
    ),
    React.createElement(FailureProbe, {
      testID: 'failure-probe',
      collapsable: false,
      style: { width: 150 + generation * 17, height: 20 },
    })
  );
}
AppRegistry.registerComponent('FailurePublication', () => FailurePublication);
