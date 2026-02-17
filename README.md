# React Native Bottom Sheet

![](https://img.shields.io/npm/v/@swmansion/react-native-bottom-sheet)

React Native Bottom Sheet provides bottom-sheet components for React Native.

## Getting started

1. Install React Native Bottom Sheet:

   ```sh
   npm i @swmansion/react-native-bottom-sheet
   ```

2. Ensure the peer dependencies are installed:

   ```sh
   npm i react-native-gesture-handler@^2.14.0 react-native-reanimated@^3.16.0 react-native-safe-area-context@^4.0.0 react-native-worklets@^0.4.0
   ```

3. Wrap your app with `GestureHandlerRootView` and `BottomSheetProvider`:

   ```tsx
   const App = () => (
     <GestureHandlerRootView>
       <BottomSheetProvider>{/* ... */}</BottomSheetProvider>
     </GestureHandlerRootView>
   );
   ```

## Usage

The library provides two components: `BottomSheet` (inline) and
`ModalBottomSheet` (modal). Both render their children as the sheet content
(including any background) and are controlled via `detents`, `index`, and
`onIndexChange`.

### Inline

`BottomSheet` renders within your screen layout.

```tsx
const [index, setIndex] = useState(0);
const insets = useSafeAreaInsets();
```

```tsx
<BottomSheet index={index} onIndexChange={setIndex}>
  <View
    style={{
      backgroundColor: 'white',
      padding: 16,
      paddingBottom: insets.bottom + 16,
    }}
  >
    <Text>Sheet content</Text>
  </View>
</BottomSheet>
```

### Modal

`ModalBottomSheet` renders above other content with a scrim.

```tsx
const [index, setIndex] = useState(0);
const insets = useSafeAreaInsets();
```

```tsx
<ModalBottomSheet index={index} onIndexChange={setIndex}>
  <View
    style={{
      backgroundColor: 'white',
      padding: 16,
      paddingBottom: insets.bottom + 16,
    }}
  >
    <Text>Sheet content</Text>
  </View>
</ModalBottomSheet>
```

#### Scrim

Tapping the scrim collapses the sheet. You can provide a custom scrim via the
`scrim` prop, which receives a `SharedValue` that goes from 0 when collapsed to
1 when the first nonzero detent is reached:

```tsx
<ModalBottomSheet
  index={index}
  onIndexChange={setIndex}
  scrim={(progress) => (
    <Animated.View
      style={useAnimatedStyle(() => ({
        backgroundColor: `rgba(0, 0, 255, ${0.3 * progress.value})`,
        flex: 1,
      }))}
    />
  )}
>
  {/* ... */}
</ModalBottomSheet>
```

### Detents and index

Detents are the points to which the sheet snaps. Each detent is either a number
(a fixed height in pixels) or `'max'` (the sheet’s content height, capped by the
available screen height). The default detents are `[0, 'max']`.

The `index` prop is a zero-based index into the `detents` array. `onIndexChange`
is called when the sheet snaps to a different detent after a drag.

```tsx
const [index, setIndex] = useState(0);
```

```tsx
<BottomSheet // Or `ModalBottomSheet`.
  detents={[0, 300, 'max']} // Collapsed, 300 px, content height.
  index={index}
  onIndexChange={setIndex}
>
  {/* ... */}
</BottomSheet>
```

### Position tracking

The `position` prop accepts a `SharedValue` that the library keeps in sync with
the sheet’s current position (the distance in pixels from the bottom of the
screen to the top of the sheet). Use it to drive animations tied to the sheet
position.

```tsx
const position = useSharedValue(0);
```

```tsx
<BottomSheet // Or `ModalBottomSheet`.
  index={index}
  onIndexChange={setIndex}
  position={position}
>
  {/* ... */}
</BottomSheet>
```

### Scrollable content

For scrollable sheet content, use `BottomSheetScrollView` or
`BottomSheetFlatList` instead of the standard React Native components. These
integrate scrolling with the sheet’s drag gesture so that dragging down while
scrolled to the top collapses the sheet.

## By [Software Mansion](https://swmansion.com)

Founded in 2012, [Software Mansion](https://swmansion.com) is a software agency
with experience in building web and mobile apps. We are core React Native
contributors and experts in dealing with all kinds of React Native issues. We
can help you build your next dream
product—[hire us](https://swmansion.com/contact/projects?utm_source=react-native-bottom-sheet&utm_medium=readme).

[![](https://logo.swmansion.com/logo?color=white&variant=desktop&width=152&tag=react-native-bottom-sheet-github)](https://swmansion.com)

[![](https://contrib.rocks/image?repo=software-mansion-labs/react-native-bottom-sheet)](https://github.com/software-mansion-labs/react-native-bottom-sheet/graphs/contributors)
