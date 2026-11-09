import type { Group, Product } from '.';

export const products: Product[] = [
  { key: 'rnbs', name: 'React Native Bottom Sheet', featured: true },
  {
    key: 'gorhom',
    name: <code>@gorhom/bottom-sheet</code>,
    href: 'https://gorhom.dev/react-native-bottom-sheet',
  },
  {
    key: 'expoUi',
    name: (
      <>
        Expo UI <code>BottomSheet</code>
      </>
    ),
    href: 'https://docs.expo.dev/versions/latest/sdk/ui/universal/bottomsheet/',
  },
  {
    key: 'formSheet',
    name: (
      <>
        Expo Router <code>formSheet</code>
      </>
    ),
    href: 'https://docs.expo.dev/router/advanced/modals/#form-sheet',
  },
  {
    key: 'trueSheet',
    name: 'True Sheet',
    href: 'https://sheet.lodev09.com',
  },
];

export const groups: Group[] = [
  {
    title: 'Architecture',
    rows: [
      {
        feature: 'Native gestures and snapping',
        cells: {
          rnbs: 'yes',
          gorhom: { status: 'no', note: 'Reanimated worklets' },
          expoUi: 'yes',
          formSheet: 'yes',
          trueSheet: 'yes',
        },
      },
      {
        feature: 'No Reanimated or Gesture Handler',
        cells: {
          rnbs: 'yes',
          gorhom: 'no',
          expoUi: 'yes',
          formSheet: 'yes',
          trueSheet: 'yes',
        },
      },
    ],
  },
  {
    title: 'Presentation',
    rows: [
      {
        feature: 'Inline sheets',
        cells: {
          rnbs: 'yes',
          gorhom: 'yes',
          expoUi: 'no',
          formSheet: 'no',
          trueSheet: 'no',
        },
      },
      {
        feature: 'Modal sheets',
        cells: {
          rnbs: 'yes',
          gorhom: 'yes',
          expoUi: 'yes',
          formSheet: 'yes',
          trueSheet: 'yes',
        },
      },
      {
        feature: 'Stacked modal sheets',
        cells: {
          rnbs: 'yes',
          gorhom: 'yes',
          expoUi: { status: 'partial', note: 'Nested in the parent on iOS' },
          formSheet: 'yes',
          trueSheet: 'yes',
        },
      },
      {
        feature: 'Detached (floating card) sheets',
        cells: {
          rnbs: { status: 'yes', note: 'Insets per detent' },
          gorhom: 'yes',
          expoUi: { status: 'partial', note: 'iOS 26+ system style only' },
          formSheet: {
            status: 'partial',
            note: 'iOS 26+ system style only',
          },
          trueSheet: { status: 'partial', note: 'Web only' },
        },
      },
      {
        feature: 'Custom sheet surface',
        cells: {
          rnbs: 'yes',
          gorhom: 'yes',
          expoUi: { status: 'partial', note: 'Color only' },
          formSheet: { status: 'partial', note: 'Color and corner radius' },
          trueSheet: 'yes',
        },
      },
    ],
  },
  {
    title: 'Sizing and detents',
    rows: [
      {
        feature: 'Content-based sizing',
        cells: {
          rnbs: 'yes',
          gorhom: 'yes',
          expoUi: 'yes',
          formSheet: 'yes',
          trueSheet: 'yes',
        },
      },
      {
        feature: 'Custom detents (heights and percentages)',
        cells: {
          rnbs: 'yes',
          gorhom: 'yes',
          expoUi: {
            status: 'partial',
            note: 'Half or full on Android',
          },
          formSheet: {
            status: 'partial',
            note: 'Fractions only, up to 3 on Android',
          },
          trueSheet: {
            status: 'partial',
            note: 'Fractions only, up to 3',
          },
        },
      },
      {
        feature: 'Programmatic-only detents',
        cells: {
          rnbs: 'yes',
          gorhom: {
            status: 'partial',
            note: <code>snapToPosition()</code>,
          },
          expoUi: 'no',
          formSheet: 'no',
          trueSheet: 'no',
        },
      },
    ],
  },
  {
    title: 'Interaction',
    rows: [
      {
        feature: 'Regular React Native scrollables',
        cells: {
          rnbs: 'yes',
          gorhom: { status: 'no', note: 'Library list components' },
          expoUi: {
            status: 'partial',
            note: (
              <>
                Wrapped in <code>RNHostView</code>
              </>
            ),
          },
          formSheet: {
            status: 'partial',
            note: 'Layout constraints',
          },
          trueSheet: {
            status: 'yes',
            note: (
              <>
                Via <code>scrollableRef</code>
              </>
            ),
          },
        },
      },
      {
        feature: 'Position tracking',
        cells: {
          rnbs: 'yes',
          gorhom: 'yes',
          expoUi: 'no',
          formSheet: { status: 'partial', note: 'Detent index only' },
          trueSheet: 'yes',
        },
      },
      {
        feature: 'Keyboard handling',
        cells: {
          rnbs: {
            status: 'yes',
            note: 'App-defined via controlled state and callbacks',
          },
          gorhom: { status: 'yes', note: 'Built-in props and input' },
          expoUi: { status: 'partial', note: 'Platform behavior only' },
          formSheet: {
            status: 'partial',
            note: 'Platform/screen behavior only',
          },
          trueSheet: { status: 'yes', note: 'Native automatic handling' },
        },
      },
    ],
  },
  {
    title: 'Platforms',
    rows: [
      {
        feature: 'Web',
        cells: {
          rnbs: 'yes',
          gorhom: { status: 'partial', note: 'Mobile web' },
          expoUi: 'yes',
          formSheet: { status: 'no', note: 'Separate route' },
          trueSheet: 'yes',
        },
      },
    ],
  },
];
