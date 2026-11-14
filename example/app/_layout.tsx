import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { KeyboardProvider } from 'react-native-keyboard-controller';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { BottomSheetProvider } from '@swmansion/react-native-bottom-sheet';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <KeyboardProvider preload={false}>
        <BottomSheetProvider>
          <Stack
            screenOptions={{
              contentStyle: { backgroundColor: 'white' },
              headerShadowVisible: false,
            }}
          >
            <Stack.Screen name="index" options={{ headerShown: false }} />
            <Stack.Screen
              name="scrim-pressables"
              options={{ title: 'Scrim press isolation', headerShown: false }}
            />
            <Stack.Screen
              name="basic-modal"
              options={{ title: 'Basic modal', headerShown: false }}
            />
            <Stack.Screen
              name="accessible-dismissal"
              options={{ title: 'Accessible dismissal', headerShown: false }}
            />
            <Stack.Screen
              name="surface-style"
              options={{ title: 'Surface style', headerShown: false }}
            />
            <Stack.Screen
              name="wide-screen"
              options={{ title: 'Wide screens', headerShown: false }}
            />
            <Stack.Screen
              name="corner-touches"
              options={{ title: 'Corner touches', headerShown: false }}
            />
            <Stack.Screen
              name="detached-sheet"
              options={{ title: 'Detached sheet', headerShown: false }}
            />
            <Stack.Screen
              name="lazy-mounted-modal"
              options={{ title: 'Lazy mounted modal', headerShown: false }}
            />
            <Stack.Screen
              name="window-overlay-modal"
              options={{
                title: 'Window overlay modal',
                presentation: 'modal',
                headerShown: true,
              }}
            />
            <Stack.Screen
              name="window-overlay-nested"
              options={{
                title: 'Window overlay nested mount',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="window-overlay-full-height-list"
              options={{
                title: 'Window overlay full-height list',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="window-overlay-keyboard-input"
              options={{
                title: 'Window overlay keyboard input',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="close-request"
              options={{
                title: 'Close requests',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="modal-scroll-view"
              options={{ title: 'Modal with ScrollView', headerShown: false }}
            />
            <Stack.Screen
              name="draggable-grabber"
              options={{ title: 'Draggable grabber', headerShown: false }}
            />
            <Stack.Screen
              name="modal-flat-list"
              options={{ title: 'Modal with FlatList', headerShown: false }}
            />
            <Stack.Screen
              name="fixed-detent-scroll-view"
              options={{
                title: 'Fixed detent ScrollView',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="keyboard-content-detent"
              options={{
                title: 'Keyboard content detent',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="keyboard-aware-list"
              options={{ title: 'Keyboard-aware list', headerShown: false }}
            />
            <Stack.Screen
              name="scrim-opacity"
              options={{
                title: 'Per-detent scrim opacity',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="inline-detents"
              options={{ title: 'Inline with detents', headerShown: false }}
            />
            <Stack.Screen
              name="under-status-bar"
              options={{ title: 'Under status bar', headerShown: false }}
            />
            <Stack.Screen
              name="content-larger-detent"
              options={{
                title: 'Content plus larger detent',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="inline-flat-list"
              options={{ title: 'Inline with FlatList', headerShown: false }}
            />
            <Stack.Screen
              name="inline-nested-flat-list"
              options={{
                title: 'Inline with nested FlatLists',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="invalid-detents"
              options={{ title: 'Invalid props', headerShown: false }}
            />
            <Stack.Screen
              name="scroll-coordination"
              options={{
                title: 'Scroll coordination modes',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="scroll-interaction"
              options={{ title: 'Scroll interaction', headerShown: false }}
            />
            <Stack.Screen
              name="programmatic-detent-drag"
              options={{
                title: 'Programmatic detent drag',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="percentage-detents"
              options={{
                title: 'Numeric and percentage detents',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="dynamic-detents"
              options={{ title: 'Dynamic detent updates', headerShown: false }}
            />
            <Stack.Screen
              name="dynamic-content-height"
              options={{ title: 'Dynamic content height', headerShown: false }}
            />
            <Stack.Screen
              name="z-indexed-content"
              options={{
                title: 'Content with zIndex children',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="snap-callbacks"
              options={{
                title: 'Snap lifecycle callbacks',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="settle-duration"
              options={{ title: 'Settle duration', headerShown: false }}
            />
            <Stack.Screen
              name="no-animate-in"
              options={{ title: 'No animate in', headerShown: false }}
            />
            <Stack.Screen
              name="ui-thread-position"
              options={{
                title: 'UI-thread onPositionChange',
                headerShown: false,
              }}
            />
            <Stack.Screen
              name="ui-thread-modal-position"
              options={{
                title: 'UI-thread modal onPositionChange',
                headerShown: false,
              }}
            />
          </Stack>
        </BottomSheetProvider>
      </KeyboardProvider>
    </SafeAreaProvider>
  );
}
