import { useState } from 'react';
import { Button, StyleSheet, Switch, Text, View } from 'react-native';
import {
  ModalBottomSheet,
  type ModalDetent,
} from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SECTION_HEIGHT,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

export const AccessibleDismissalScreen = () => {
  const [index, setIndex] = useState(0);
  const [isClosedProgrammatic, setIsClosedProgrammatic] = useState(false);
  const [fillsScreen, setFillsScreen] = useState(false);
  const [usesCustomLabel, setUsesCustomLabel] = useState(false);
  const [usesNativeOverlay, setUsesNativeOverlay] = useState(false);
  const [indexChanges, setIndexChanges] = useState(0);
  const sheetBottomPadding = useSheetBottomPadding(0);

  const detents: ModalDetent[] = [
    { value: 0, programmatic: isClosedProgrammatic },
    fillsScreen ? '100%' : 'content',
  ];

  return (
    <DemoScreen
      title="Accessible dismissal"
      sheet={
        <ModalBottomSheet
          index={index}
          onIndexChange={(newIndex) => {
            setIndex(newIndex);
            setIndexChanges((count) => count + 1);
          }}
          detents={detents}
          topInset={fillsScreen ? 0 : undefined}
          nativeOverlay={usesNativeOverlay}
          scrimColor={MODAL_SCRIM_COLOR}
          scrimAccessibilityLabel={usesCustomLabel ? 'Cerrar' : undefined}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Accessible dismissal"
            onClose={() => setIndex(0)}
          />
          <View
            style={[
              styles.sheetBody,
              {
                minHeight: SECTION_HEIGHT + sheetBottomPadding,
                paddingBottom: sheetBottomPadding,
              },
              fillsScreen && styles.fill,
            ]}
          >
            <Text style={styles.sheetText}>
              With VoiceOver or TalkBack on, find the scrim’s button above the
              sheet, or use the escape gesture (iOS) or the dismiss action
              (Android) from here.
            </Text>
            <Button title="A control in the sheet" onPress={() => {}} />
          </View>
        </ModalBottomSheet>
      }
    >
      <Toggle
        label="Programmatic-only closed detent"
        value={isClosedProgrammatic}
        onValueChange={setIsClosedProgrammatic}
      />
      <Toggle
        label="Fills the screen"
        value={fillsScreen}
        onValueChange={setFillsScreen}
      />
      <Toggle
        label="Custom label (“Cerrar”)"
        value={usesCustomLabel}
        onValueChange={setUsesCustomLabel}
      />
      <Toggle
        label="Native overlay"
        value={usesNativeOverlay}
        onValueChange={setUsesNativeOverlay}
      />
      <Button title="Open sheet" onPress={() => setIndex(1)} />
      <Text style={styles.readout}>
        index: {index} · index changes: {indexChanges}
      </Text>
    </DemoScreen>
  );
};

const Toggle = ({
  label,
  value,
  onValueChange,
}: {
  label: string;
  value: boolean;
  onValueChange: (value: boolean) => void;
}) => (
  <View style={styles.toggleRow}>
    <Text style={styles.toggleLabel}>{label}</Text>
    <Switch
      accessibilityLabel={label}
      value={value}
      onValueChange={onValueChange}
    />
  </View>
);

const styles = StyleSheet.create({
  sheetBody: {
    gap: 16,
    paddingHorizontal: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fill: { flex: 1 },
  sheetText: { fontSize: 15, lineHeight: 21, textAlign: 'center' },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  toggleLabel: { flex: 1, fontSize: 15 },
  readout: { fontSize: 13, color: '#555', textAlign: 'center' },
});
