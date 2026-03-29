# React Native Bottom Sheet

![](https://img.shields.io/npm/v/@swmansion/react-native-bottom-sheet)

![](cover.png)

React Native Bottom Sheet provides bottom&zwj;-&zwj;sheet components for
React&nbsp;Native.

## Highlights

- Native implementation for optimal&nbsp;performance.
- Bring your own sheet&nbsp;surface.
- Dynamic, content&zwj;-&zwj;based sizing out of the&nbsp;box.
- Automatic handling of vertically scrollable&nbsp;children.
- Position tracking for driving UI tied to&nbsp;sheets.
- Programmatic&zwj;-&zwj;only detents for snap points unreachable
  by&nbsp;dragging.

## Getting started

1. Install React Native Bottom&nbsp;Sheet:

   ```sh
   npm i @swmansion/react-native-bottom-sheet
   ```

2. Ensure the peer dependency is&nbsp;installed:

   ```sh
   npm i react-native-safe-area-context@^4.0.0
   ```

3. Wrap your app with&nbsp;`BottomSheetProvider`:

   ```tsx
   const App = () => <BottomSheetProvider>{/* ... */}</BottomSheetProvider>;
   ```

## Usage

The library provides two components: `BottomSheet` (inline) and
`ModalBottomSheet` (modal). Both render their children as the sheet content
(including any background) and are controlled via `detents`, `index`,
and&nbsp;`onIndexChange`.

### Inline

`BottomSheet` renders within your screen&nbsp;layout.

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

`ModalBottomSheet` renders above other content with a&nbsp;scrim.

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

Tapping the scrim collapses the sheet. Use `scrimColor` to customize
its&nbsp;color:

```tsx
<ModalBottomSheet
  index={index}
  onIndexChange={setIndex}
  scrimColor="rgba(0, 0, 0, 0.3)"
>
  {/* ... */}
</ModalBottomSheet>
```

### Detents and index

Detents are the points to which the sheet snaps. Each detent is either a number
(a fixed height in pixels) or `'max'` (the sheet’s content height, capped by the
available screen height). The default detents are `[0, 'max']`.

The `index` prop is a zero&zwj;-&zwj;based index into the `detents` array.
`onIndexChange` is called when the sheet snaps to a different detent after
a drag. You can also control the sheet externally by updating the
index&nbsp;state.

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

#### Programmatic-only detents

If you want a detent to be reachable only via code (not by dragging), use the
object form or the `programmatic` helper. Programmatic detents are excluded from
drag snapping but can still be targeted via `index`&nbsp;updates.

```tsx
<BottomSheet
  detents={[0, programmatic(300), 'max']}
  index={index}
  onIndexChange={setIndex}
>
  {/* ... */}
</BottomSheet>
```

### Position tracking

Use `onPositionChange` to observe the sheet’s current position (the distance in
pixels from the bottom of the screen to the top of the&nbsp;sheet).

```tsx
<BottomSheet // Or `ModalBottomSheet`.
  index={index}
  onIndexChange={setIndex}
  onPositionChange={(position) => {
    console.log(position);
  }}
>
  {/* ... */}
</BottomSheet>
```

If you want to keep the latest position in a Reanimated shared value, update it
from the&nbsp;callback:

```tsx
const position = useSharedValue(0);
```

```tsx
<BottomSheet
  index={index}
  onIndexChange={setIndex}
  onPositionChange={(nextPosition) => {
    position.value = nextPosition;
  }}
>
  {/* ... */}
</BottomSheet>
```

## By [Software Mansion](https://swmansion.com)

Founded in 2012, [Software Mansion](https://swmansion.com) is a software agency
with experience in building web and mobile apps. We are core React Native
contributors and experts in dealing with all kinds of React Native issues. We
can help you build your next dream
product&zwj;—&zwj;[hire&nbsp;us](https://swmansion.com/contact/projects?utm_source=react-native-bottom-sheet&utm_medium=readme).

[![](https://logo.swmansion.com/logo?color=white&variant=desktop&width=152&tag=react-native-bottom-sheet-github)](https://swmansion.com)

[![](https://contrib.rocks/image?repo=software-mansion-labs/react-native-bottom-sheet)](https://github.com/software-mansion-labs/react-native-bottom-sheet/graphs/contributors)

## Sponsored by [Gobi Maps](https://www.gobimaps.com)

A social map for exploring your&nbsp;city.

[<img src="gobi.png" height="80" />](https://www.gobimaps.com)
