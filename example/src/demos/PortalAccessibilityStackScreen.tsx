import { useRef, useState } from 'react';
import {
  Button,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  ModalBottomSheet,
  programmatic,
} from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

type PortalName = 'lower' | 'upper';
type PortalPhase = 'open' | 'closing' | 'settle';
type UpperPresentation = 'portal' | 'nativeOverlay';

const PORTAL_PHASE_LABEL: Record<PortalPhase, string> = {
  open: 'opening',
  closing: 'closing',
  settle: 'settled',
};

const PLATFORM_CLOSE_INSTRUCTION =
  Platform.OS === 'ios'
    ? 'Verify focus stays in upper. Escape must affect only upper.'
    : Platform.OS === 'android'
      ? 'Verify focus stays in upper. Back must affect only upper.'
      : 'Verify screen reader focus stays in upper.';

const PortalFocusTarget = ({ label }: { label: string }) => (
  <Pressable
    accessibilityLabel={label}
    accessibilityRole="button"
    onPress={() => undefined}
    style={styles.focusTarget}
  >
    <Text style={styles.focusTargetText}>{label}</Text>
  </Pressable>
);

export const PortalAccessibilityStackScreen = () => {
  const [lowerIndex, setLowerIndex] = useState(0);
  const [upperIndex, setUpperIndex] = useState(0);
  const [upperPresentation, setUpperPresentation] =
    useState<UpperPresentation>('portal');
  const [upperProgrammaticOnly, setUpperProgrammaticOnly] = useState(false);
  const [portalPhase, setPortalPhase] = useState<
    Record<PortalName, PortalPhase>
  >({
    lower: 'settle',
    upper: 'settle',
  });
  const pendingSettle = useRef<Record<PortalName, boolean>>({
    lower: false,
    upper: false,
  });
  const bottomPadding = useSheetBottomPadding();

  const recordPortalEvent = (
    portal: PortalName,
    phase: PortalPhase,
    index: number
  ) => {
    const message = `[two-portals] ${portal} ${phase} index=${index}`;
    console.log(message);
    setPortalPhase((current) => ({
      ...current,
      [portal]: phase,
    }));
  };

  const beginTransition = (
    portal: PortalName,
    phase: Exclude<PortalPhase, 'settle'>,
    index: number,
    setIndex: (index: number) => void
  ) => {
    pendingSettle.current[portal] = true;
    recordPortalEvent(portal, phase, index);
    setIndex(index);
  };

  const openLower = () => beginTransition('lower', 'open', 1, setLowerIndex);

  const closeLower = () => {
    if (lowerIndex === 0) return;
    beginTransition('lower', 'closing', 0, setLowerIndex);
  };

  const openUpper = (
    presentation: UpperPresentation,
    programmaticOnly: boolean
  ) => {
    setUpperPresentation(presentation);
    setUpperProgrammaticOnly(programmaticOnly);
    beginTransition('upper', 'open', 1, setUpperIndex);
  };

  const closeUpper = () => {
    if (upperIndex === 0) return;
    beginTransition('upper', 'closing', 0, setUpperIndex);
  };

  const toggleUpperPresentation = () => {
    setUpperPresentation((current) =>
      current === 'portal' ? 'nativeOverlay' : 'portal'
    );
  };

  const handleIndexChange = (
    portal: PortalName,
    nextIndex: number,
    setIndex: (index: number) => void
  ) => {
    beginTransition(
      portal,
      nextIndex === 0 ? 'closing' : 'open',
      nextIndex,
      setIndex
    );
  };

  const handleSettle = (portal: PortalName, index: number) => {
    if (!pendingSettle.current[portal]) return;
    pendingSettle.current[portal] = false;
    recordPortalEvent(portal, 'settle', index);
  };

  return (
    <DemoScreen
      title="Stacked modal accessibility"
      sheet={
        <>
          <ModalBottomSheet
            detents={[0, 440]}
            index={lowerIndex}
            onIndexChange={(nextIndex) =>
              handleIndexChange('lower', nextIndex, setLowerIndex)
            }
            onSettle={(index) => handleSettle('lower', index)}
            onCloseRequest={closeLower}
            scrimColor={MODAL_SCRIM_COLOR}
            surface={<SheetBackground style={StyleSheet.absoluteFill} />}
          >
            <SheetHeader title="Lower portal" onClose={closeLower} />
            <View
              style={[styles.sheetContent, { paddingBottom: bottomPadding }]}
            >
              <PortalFocusTarget label="Lower focus target" />
              <Text style={styles.sectionTitle}>Open upper as</Text>
              <Button
                title="Portal"
                onPress={() => openUpper('portal', false)}
              />
              <Button
                title="nativeOverlay"
                onPress={() => openUpper('nativeOverlay', false)}
              />
              <Button
                title="Programmatic-only portal"
                onPress={() => openUpper('portal', true)}
              />
              <Text style={styles.hint}>
                Screen reader focus should not reach this lower sheet while the
                upper sheet is active.
              </Text>
            </View>
          </ModalBottomSheet>

          <ModalBottomSheet
            detents={upperProgrammaticOnly ? [programmatic(0), 440] : [0, 440]}
            index={upperIndex}
            nativeOverlay={upperPresentation === 'nativeOverlay'}
            onIndexChange={(nextIndex) =>
              handleIndexChange('upper', nextIndex, setUpperIndex)
            }
            onSettle={(index) => handleSettle('upper', index)}
            onCloseRequest={closeUpper}
            scrimColor={MODAL_SCRIM_COLOR}
            surface={
              <SheetBackground
                style={[StyleSheet.absoluteFill, styles.upperSurface]}
              />
            }
          >
            <SheetHeader
              title={`Upper ${upperPresentation}`}
              onClose={closeUpper}
            />
            <View
              style={[styles.sheetContent, { paddingBottom: bottomPadding }]}
            >
              <PortalFocusTarget label="Upper focus target" />
              <Text style={styles.modeStatus}>
                {upperPresentation} ·{' '}
                {upperProgrammaticOnly
                  ? 'programmatic-only close'
                  : 'dismissible'}
              </Text>
              <Text style={styles.hint}>{PLATFORM_CLOSE_INSTRUCTION}</Text>
              <Button
                title={
                  upperPresentation === 'portal'
                    ? 'Move upper to nativeOverlay'
                    : 'Move upper to portal'
                }
                onPress={toggleUpperPresentation}
              />
            </View>
          </ModalBottomSheet>
        </>
      }
    >
      <Text style={styles.instructions}>
        Open the lower sheet, then choose an upper variant.
      </Text>
      <Button title="Open lower portal" onPress={openLower} />
      <View style={styles.statusCard}>
        <Text style={styles.statusTitle}>Current lifecycle</Text>
        <Text style={styles.statusLine}>
          lower: {PORTAL_PHASE_LABEL[portalPhase.lower]} · index {lowerIndex}
        </Text>
        <Text style={styles.statusLine}>
          upper: {PORTAL_PHASE_LABEL[portalPhase.upper]} · index {upperIndex}
        </Text>
      </View>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  instructions: {
    color: '#444',
    fontSize: 15,
    lineHeight: 22,
  },
  sheetContent: {
    paddingHorizontal: 20,
    paddingTop: 20,
    gap: 16,
  },
  focusTarget: {
    minHeight: 56,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: '#295ea7',
    backgroundColor: '#eaf2ff',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  focusTargetText: {
    color: '#143b70',
    fontSize: 18,
    fontWeight: '700',
  },
  hint: {
    color: '#555',
    lineHeight: 20,
  },
  upperSurface: {
    backgroundColor: '#fff9e8',
  },
  statusCard: {
    borderRadius: 12,
    backgroundColor: '#f3f3f3',
    padding: 12,
    gap: 4,
  },
  statusTitle: {
    fontWeight: '700',
    marginBottom: 4,
  },
  statusLine: {
    fontFamily: 'monospace',
    fontVariant: ['tabular-nums'],
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
  },
  modeStatus: {
    color: '#555',
    fontWeight: '600',
    textAlign: 'center',
  },
});
