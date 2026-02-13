import { useState } from 'react';
import type { ReactNode } from 'react';
import type { ViewStyle } from 'react-native';
import { Button, Text, TouchableOpacity, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import {
  BottomSheet,
  BottomSheetFlatList,
  BottomSheetProvider,
  BottomSheetScrollView,
  ModalBottomSheet,
} from '@swmansion/react-native-bottom-sheet';

const HANDLE_HEIGHT = 16;
const HEADER_HEIGHT = 64;
const SHEET_HEADER_HEIGHT = HANDLE_HEIGHT + HEADER_HEIGHT;
const SECTION_HEIGHT = 176;
const LIST_ITEM_HEIGHT = 48;

const DATA = Array.from({ length: 48 }, (_, i) => ({
  id: String(i),
  title: `Item ${i + 1}`,
}));

const CloseButton = ({ onPress }: { onPress: () => void }) => (
  <TouchableOpacity
    onPress={onPress}
    style={{
      width: 40,
      height: 40,
      borderRadius: 20,
      backgroundColor: '#eee',
      alignItems: 'center',
      justifyContent: 'center',
    }}
  >
    <Ionicons name="close" size={24} color="black" />
  </TouchableOpacity>
);

const SheetBackground = ({
  children,
  style,
}: {
  children: ReactNode;
  style?: ViewStyle;
}) => (
  <View
    style={{
      backgroundColor: 'white',
      borderTopLeftRadius: 16,
      borderTopRightRadius: 16,
      shadowColor: '#000',
      shadowOpacity: 0.19,
      shadowRadius: 5.62,
      elevation: 6,
      shadowOffset: { height: 4 },
      ...style,
    }}
  >
    {children}
  </View>
);

const SheetHeader = ({
  title,
  onClose,
}: {
  title: string;
  onClose: () => void;
}) => (
  <View>
    <View style={{ alignItems: 'center', paddingTop: 8, paddingBottom: 4 }}>
      <View
        style={{
          width: 36,
          height: 4,
          borderRadius: 2,
          backgroundColor: '#ddd',
        }}
      />
    </View>
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: 16,
        height: HEADER_HEIGHT,
        paddingHorizontal: 16,
        borderBottomWidth: 1,
        borderBottomColor: '#eee',
      }}
    >
      <CloseButton onPress={onClose} />
      <Text style={{ fontSize: 20, fontWeight: 'bold' }}>{title}</Text>
    </View>
  </View>
);

const AppContent = () => {
  const [basicIndex, setBasicIndex] = useState(0);
  const [flatListIndex, setFlatListIndex] = useState(0);
  const [scrollViewIndex, setScrollViewIndex] = useState(0);
  const [inlineIndex, setInlineIndex] = useState(0);
  return (
    <BottomSheetProvider>
      <View
        style={{
          flex: 1,
          alignItems: 'center',
          justifyContent: 'center',
          gap: 12,
        }}
      >
        <Button title="Basic modal" onPress={() => setBasicIndex(1)} />
        <Button
          title="Modal with ScrollView"
          onPress={() => setScrollViewIndex(1)}
        />
        <Button
          title="Modal with FlatList"
          onPress={() => setFlatListIndex(1)}
        />
        <Button title="Inline with detents" onPress={() => setInlineIndex(1)} />
      </View>
      <ModalBottomSheet index={basicIndex} onIndexChange={setBasicIndex}>
        <SheetBackground>
          <SheetHeader title="Basic modal" onClose={() => setBasicIndex(0)} />
          <View
            style={{
              height: SECTION_HEIGHT,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text>Swipe down or tap scrim to dismiss</Text>
          </View>
        </SheetBackground>
      </ModalBottomSheet>
      <ModalBottomSheet
        index={scrollViewIndex}
        onIndexChange={setScrollViewIndex}
      >
        <SheetBackground style={{ flex: 1 }}>
          <SheetHeader
            title="Modal with ScrollView"
            onClose={() => setScrollViewIndex(0)}
          />
          <BottomSheetScrollView contentContainerStyle={{ paddingBottom: 24 }}>
            {DATA.map((item, index) => (
              <View
                key={item.id}
                style={{
                  height: LIST_ITEM_HEIGHT,
                  justifyContent: 'center',
                  paddingHorizontal: 16,
                  borderBottomWidth: index === DATA.length - 1 ? 0 : 1,
                  borderBottomColor: '#eee',
                }}
              >
                <Text>{item.title}</Text>
              </View>
            ))}
          </BottomSheetScrollView>
        </SheetBackground>
      </ModalBottomSheet>
      <ModalBottomSheet index={flatListIndex} onIndexChange={setFlatListIndex}>
        <SheetBackground style={{ flex: 1 }}>
          <SheetHeader
            title="Modal with FlatList"
            onClose={() => setFlatListIndex(0)}
          />
          <BottomSheetFlatList
            data={DATA}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ paddingBottom: 24 }}
            renderItem={({ item, index }) => (
              <View
                style={{
                  height: LIST_ITEM_HEIGHT,
                  justifyContent: 'center',
                  paddingHorizontal: 16,
                  borderBottomWidth: index === DATA.length - 1 ? 0 : 1,
                  borderBottomColor: '#eee',
                }}
              >
                <Text>{item.title}</Text>
              </View>
            )}
          />
        </SheetBackground>
      </ModalBottomSheet>
      <BottomSheet
        detents={[0, SHEET_HEADER_HEIGHT + SECTION_HEIGHT, 'max']}
        index={inlineIndex}
        onIndexChange={setInlineIndex}
      >
        <SheetBackground>
          <SheetHeader
            title="Inline with detents"
            onClose={() => setInlineIndex(0)}
          />
          <View
            style={{
              height: SECTION_HEIGHT,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text>Section 1</Text>
          </View>
          <View
            style={{
              height: SECTION_HEIGHT,
              alignItems: 'center',
              justifyContent: 'center',
              borderTopWidth: 1,
              borderTopColor: '#eee',
            }}
          >
            <Text>Section 2</Text>
          </View>
        </SheetBackground>
      </BottomSheet>
    </BottomSheetProvider>
  );
};

const App = () => (
  <SafeAreaProvider>
    <GestureHandlerRootView style={{ flex: 1 }}>
      <AppContent />
    </GestureHandlerRootView>
  </SafeAreaProvider>
);

export default App;
