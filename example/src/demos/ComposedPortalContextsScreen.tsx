import { useState } from 'react';
import {
  Button,
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  ModalBottomSheet,
  programmatic,
} from '@swmansion/react-native-bottom-sheet';

import { SheetBackground } from '../demoShared';
import { PortalPanel } from './LocalPortalContextsScreen';

type OverlayMode = 'visible' | 'transparent' | 'programmatic';

const ModalWorkspace = ({ onClose }: { onClose: () => void }) => {
  const insets = useSafeAreaInsets();
  const [indexA, setIndexA] = useState(0);
  const [indexB, setIndexB] = useState(0);
  const [extraBackground, setExtraBackground] = useState(false);
  const [outsideCount, setOutsideCount] = useState(0);
  const [overlay, setOverlay] = useState<OverlayMode | null>(null);
  const [overlayIndex, setOverlayIndex] = useState(1);
  const [overlayCount, setOverlayCount] = useState(0);

  return (
    <ScrollView
      collapsable={false}
      style={styles.modalScroll}
      contentContainerStyle={[
        styles.modalContent,
        { paddingTop: insets.top + 8, paddingBottom: insets.bottom },
      ]}
    >
      <Text>Local panels inside RN Modal</Text>
      <Button title="Close RN Modal" onPress={onClose} />
      <Button
        title="Activate modal toolbar"
        onPress={() => setOutsideCount((count) => count + 1)}
      />
      <Text>Toolbar activations: {outsideCount}</Text>
      <Text>Open a window overlay above both panels:</Text>
      <View style={styles.overlayButtons}>
        {(['visible', 'transparent', 'programmatic'] as const).map((mode) => (
          <Button
            key={mode}
            title={mode}
            onPress={() => {
              setOverlayIndex(1);
              setOverlay(mode);
            }}
          />
        ))}
      </View>
      <View style={styles.buttons}>
        <Button title="Open A" onPress={() => setIndexA(1)} />
        <Button title="Open B" onPress={() => setIndexB(1)} />
      </View>
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
      <Button
        title={
          extraBackground ? 'Remove extra background' : 'Add extra background'
        }
        onPress={() => setExtraBackground((present) => !present)}
      />
      {overlay !== null && (
        <ModalBottomSheet
          nativeOverlay
          detents={[overlay === 'programmatic' ? programmatic(0) : 0, 260]}
          index={overlayIndex}
          onIndexChange={setOverlayIndex}
          onSettle={(nextIndex) => {
            if (nextIndex === 0) setOverlay(null);
          }}
          scrimOpacities={overlay === 'transparent' ? [0, 0] : undefined}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <View style={styles.overlayContent}>
            <Text>Window overlay: {overlay}</Text>
            <Button
              title="Activate overlay content"
              onPress={() => setOverlayCount((count) => count + 1)}
            />
            <Text>Overlay activations: {overlayCount}</Text>
            <Text>Toolbar activations: {outsideCount}</Text>
            <Button title="Close overlay" onPress={() => setOverlayIndex(0)} />
          </View>
        </ModalBottomSheet>
      )}
    </ScrollView>
  );
};

export const ComposedPortalContextsScreen = () => {
  const [appIndex, setAppIndex] = useState(1);
  const [modalVisible, setModalVisible] = useState(false);

  return (
    <View style={styles.screen}>
      <Text>RN Modal keeps its local panels separate from the App sheet.</Text>
      <Text>App sheet: {appIndex === 0 ? 'closed' : 'open'}</Text>
      <Button
        title="Open RN Modal"
        onPress={() => {
          setAppIndex(1);
          setModalVisible(true);
        }}
      />
      <View style={styles.panels}>
        <PortalPanel
          name="App"
          index={appIndex}
          onIndexChange={setAppIndex}
          extraBackground={false}
        >
          <Modal
            visible={modalVisible}
            animationType="none"
            presentationStyle="overFullScreen"
            onRequestClose={() => setModalVisible(false)}
          >
            {modalVisible && (
              <ModalWorkspace onClose={() => setModalVisible(false)} />
            )}
          </Modal>
        </PortalPanel>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  screen: { flex: 1, padding: 12, gap: 8, backgroundColor: 'white' },
  buttons: { flexDirection: 'row', justifyContent: 'space-around' },
  overlayButtons: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-around',
    gap: 4,
  },
  modalScroll: { flex: 1, backgroundColor: 'white' },
  modalContent: { flexGrow: 1, padding: 12, gap: 8 },
  panels: { flexDirection: 'row', gap: 8 },
  overlayContent: { padding: 16, gap: 12 },
});
