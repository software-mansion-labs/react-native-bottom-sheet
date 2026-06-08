import { FlatList, View } from 'react-native';
import { router } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { DEMO_CASES } from '../src/demoCases';
import { CaseRow } from '../src/demoShared';

const DemoCaseSeparator = () => (
  <View style={{ height: 1, backgroundColor: '#eee' }} />
);

export default function HomeScreen() {
  const insets = useSafeAreaInsets();

  return (
    <View
      style={{
        flex: 1,
        backgroundColor: 'white',
      }}
    >
      <FlatList
        data={DEMO_CASES}
        keyExtractor={(item) => item.key}
        contentContainerStyle={{
          paddingTop: insets.top,
          paddingBottom: insets.bottom,
        }}
        ItemSeparatorComponent={DemoCaseSeparator}
        renderItem={({ item }) => (
          <CaseRow
            title={item.title}
            isError={item.throws}
            onPress={() => router.push(item.href)}
          />
        )}
      />
    </View>
  );
}
