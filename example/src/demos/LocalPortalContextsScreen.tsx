import { type ReactNode, useState } from 'react';
import { Button, Platform, StyleSheet, Text, View } from 'react-native';
import {
  BottomSheetProvider,
  ModalBottomSheet,
} from '@swmansion/react-native-bottom-sheet';

import { SheetBackground } from '../demoShared';

export const PortalPanel = ({
  name,
  index,
  onIndexChange,
  extraBackground,
  children,
}: {
  name: string;
  index: number;
  onIndexChange: (index: number) => void;
  extraBackground: boolean;
  children?: ReactNode;
}) => {
  const [backgroundCount, setBackgroundCount] = useState(0);
  const [sheetCount, setSheetCount] = useState(0);

  return (
    <View collapsable={false} style={styles.panel}>
      <BottomSheetProvider>
        <View style={styles.background}>
          <Text>Panel {name}</Text>
          <Button
            title={`Activate background ${name}`}
            onPress={() => setBackgroundCount((count) => count + 1)}
          />
          <Text>Background activations: {backgroundCount}</Text>
          {extraBackground && (
            <Button
              title={`Extra background ${name}`}
              onPress={() => setBackgroundCount((count) => count + 1)}
            />
          )}
        </View>
        <ModalBottomSheet
          detents={[0, 190]}
          index={index}
          onIndexChange={onIndexChange}
          onCloseRequest={() => onIndexChange(0)}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <View style={styles.sheetContent}>
            <Text>Sheet {name}</Text>
            <Button
              title={`Activate sheet ${name}`}
              onPress={() => setSheetCount((count) => count + 1)}
            />
            <Text>Sheet activations: {sheetCount}</Text>
            <Button title={`Close ${name}`} onPress={() => onIndexChange(0)} />
            {children}
          </View>
        </ModalBottomSheet>
      </BottomSheetProvider>
    </View>
  );
};

export const LocalPortalContextsScreen = () => {
  const [indexA, setIndexA] = useState(0);
  const [indexB, setIndexB] = useState(0);
  const [extraBackground, setExtraBackground] = useState(false);
  const [outsideCount, setOutsideCount] = useState(0);

  return (
    <View style={styles.screen}>
      <Text>
        {Platform.OS === 'ios'
          ? 'With VoiceOver, Escape in A closes A while B stays open. Each open panel hides its own background.'
          : 'Local accessibility context integration is currently under development on Android.'}
      </Text>
      <View style={styles.openButtons}>
        <Button title="Open A" onPress={() => setIndexA(1)} />
        <Button title="Open B" onPress={() => setIndexB(1)} />
      </View>
      <Button
        title="Activate outside toolbar"
        onPress={() => setOutsideCount((count) => count + 1)}
      />
      <Text>Outside activations: {outsideCount}</Text>
      <Button
        title={
          extraBackground ? 'Remove extra background' : 'Add extra background'
        }
        onPress={() => setExtraBackground((present) => !present)}
      />
      <View style={styles.panels}>
        <PortalPanel
          name="A"
          index={indexA}
          onIndexChange={setIndexA}
          extraBackground={extraBackground}
        />
        <PortalPanel
          name="B"
          index={indexB}
          onIndexChange={setIndexB}
          extraBackground={extraBackground}
        />
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  screen: { flex: 1, padding: 12, gap: 8, backgroundColor: 'white' },
  openButtons: { flexDirection: 'row', justifyContent: 'space-around' },
  panels: { flexDirection: 'row', gap: 8 },
  panel: { flex: 1, height: 300, borderWidth: 1, borderColor: '#999' },
  background: { padding: 8, gap: 8 },
  sheetContent: { padding: 8, gap: 8 },
});
