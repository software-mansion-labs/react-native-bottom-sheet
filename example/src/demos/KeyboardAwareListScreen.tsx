import { useMemo, useState } from 'react';
import { Button, StyleSheet, Text, TextInput, View } from 'react-native';
import {
  KeyboardAwareScrollView,
  useKeyboardState,
} from 'react-native-keyboard-controller';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DATA,
  DemoScreen,
  ListRow,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

const LIST_BOTTOM_PADDING = 24;
const KEYBOARD_BOTTOM_OFFSET = 16;

export const KeyboardAwareListScreen = () => {
  const [index, setIndex] = useState(0);
  const [query, setQuery] = useState('');
  const sheetBottomPadding = useSheetBottomPadding();
  const keyboardHeight = useKeyboardState((state) => state.height);
  const listBottomPadding = Math.max(
    LIST_BOTTOM_PADDING,
    sheetBottomPadding - keyboardHeight
  );
  const filteredData = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    if (normalizedQuery.length === 0) {
      return DATA;
    }
    return DATA.filter((item) =>
      item.title.toLowerCase().includes(normalizedQuery)
    );
  }, [query]);

  return (
    <DemoScreen
      title="Keyboard-aware list"
      sheet={
        <ModalBottomSheet
          detents={[0, 'content']}
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <View style={styles.fullSheet}>
            <SheetHeader
              title="Keyboard-aware list"
              onClose={() => setIndex(0)}
            />
            <KeyboardAwareScrollView
              bottomOffset={KEYBOARD_BOTTOM_OFFSET}
              contentContainerStyle={[
                styles.listContent,
                { paddingBottom: listBottomPadding },
              ]}
              keyboardDismissMode="interactive"
              keyboardShouldPersistTaps="handled"
              stickyHeaderIndices={[0]}
              style={styles.scrollView}
            >
              <View style={styles.searchBarContainer}>
                <TextInput
                  value={query}
                  onChangeText={setQuery}
                  placeholder="Search items"
                  returnKeyType="search"
                  style={styles.searchInput}
                />
              </View>
              {filteredData.length > 0 ? (
                filteredData.map((item, itemIndex) => (
                  <ListRow key={item.id} item={item} index={itemIndex} />
                ))
              ) : (
                <View style={styles.emptyState}>
                  <Text style={styles.emptyText}>No matching items</Text>
                </View>
              )}
            </KeyboardAwareScrollView>
          </View>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  fullSheet: {
    flex: 1,
  },
  scrollView: {
    flex: 1,
  },
  listContent: {
    paddingBottom: 0,
  },
  searchBarContainer: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: 'white',
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  searchInput: {
    height: 44,
    borderRadius: 12,
    backgroundColor: '#f1f3f5',
    paddingHorizontal: 14,
    fontSize: 16,
  },
  emptyState: {
    minHeight: 160,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    color: '#667085',
    fontWeight: '600',
  },
});
