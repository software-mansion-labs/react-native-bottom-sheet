import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { BottomSheetProvider } from '@swmansion/react-native-bottom-sheet';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <BottomSheetProvider>
        <Stack
          screenOptions={{
            contentStyle: { backgroundColor: 'white' },
            headerShadowVisible: false,
          }}
        >
          <Stack.Screen name="index" options={{ headerShown: false }} />
          <Stack.Screen
            name="basic-modal"
            options={{ title: 'Basic modal', headerShown: false }}
          />
          <Stack.Screen
            name="modal-scroll-view"
            options={{ title: 'Modal with ScrollView', headerShown: false }}
          />
          <Stack.Screen
            name="modal-flat-list"
            options={{ title: 'Modal with FlatList', headerShown: false }}
          />
          <Stack.Screen
            name="inline-detents"
            options={{ title: 'Inline with detents', headerShown: false }}
          />
          <Stack.Screen
            name="inline-flat-list"
            options={{ title: 'Inline with FlatList', headerShown: false }}
          />
          <Stack.Screen
            name="invalid-detents"
            options={{ title: 'Invalid detents', headerShown: false }}
          />
          <Stack.Screen
            name="disable-scrollable-negotiation"
            options={{
              title: 'Disable scrollable negotiation',
              headerShown: false,
            }}
          />
          <Stack.Screen
            name="dynamic-detents"
            options={{ title: 'Dynamic detent updates', headerShown: false }}
          />
        </Stack>
      </BottomSheetProvider>
    </SafeAreaProvider>
  );
}
