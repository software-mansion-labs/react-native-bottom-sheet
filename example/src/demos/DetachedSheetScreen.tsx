import { useState } from 'react';
import {
  Button,
  I18nManager,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useReanimatedKeyboardAnimation } from 'react-native-keyboard-controller';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';
import {
  BottomSheet,
  type Detent,
  type DetentInset,
  ModalBottomSheet,
  type SurfaceStyle,
} from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, MODAL_SCRIM_COLOR, SheetHeader } from '../demoShared';

const FLOATING: DetentInset = { left: 16, right: 16, bottom: 32 };
const ROUNDED: SurfaceStyle = { borderRadius: 28, borderCurve: 'continuous' };
const SHADOW = '0 4 24 rgba(0, 0, 0, 0.35)';

type Scenario = {
  label: string;
  /** What to look for when trying this scenario. */
  expect: string;
  detents: Detent[];
  detentInsets?: DetentInset[];
  shape?: SurfaceStyle;
  scrollable?: boolean;
  keyboard?: boolean;
};

const SCENARIOS: Scenario[] = [
  {
    label: 'Attached, no insets',
    expect: 'Full width and flush with the bottom, as without insets.',
    detents: [0, 320, '100%'],
    shape: { borderTopLeftRadius: 28, borderTopRightRadius: 28 },
  },
  {
    label: 'Floating, attached when full',
    expect: 'Gaps at 320 blend away while dragging up to the full detent.',
    detents: [0, 320, '100%'],
    detentInsets: [{}, FLOATING, {}],
  },
  {
    label: 'Always floating',
    expect: 'The same gaps at every detent. Closing slides through the gap.',
    detents: [0, 320, '100%'],
    detentInsets: [FLOATING],
  },
  {
    label: 'Top inset at full',
    expect:
      'The full detent stops 24 below the status bar and 32 above the bottom.',
    detents: [0, 320, '100%'],
    detentInsets: [{ ...FLOATING, top: 24 }],
  },
  {
    label: 'Percentage detents',
    expect: 'Percentages resolve against the space the insets leave.',
    detents: [0, '25%', '50%', '100%'],
    detentInsets: [{ ...FLOATING, top: 80 }],
  },
  {
    label: 'Content height',
    expect: 'The card hugs its content and floats above the gap.',
    detents: [0, 'content'],
    detentInsets: [FLOATING],
  },
  {
    label: 'Lopsided gaps',
    expect: 'The left gap moves to the right. The blue band keeps its width.',
    detents: [0, 240, '60%'],
    detentInsets: [{}, { left: 48, bottom: 16 }, { right: 48, bottom: 64 }],
  },
  {
    label: 'Square corners',
    expect: 'Gaps clip the surface even with no radius.',
    detents: [0, 320, '100%'],
    detentInsets: [{}, FLOATING, {}],
    shape: {},
  },
  {
    label: 'Mixed radii',
    expect: 'Each corner keeps its own radius as the edges move.',
    detents: [0, 320, '100%'],
    detentInsets: [{}, FLOATING, { left: 8, right: 8, bottom: 8, top: 8 }],
    shape: {
      borderTopLeftRadius: 48,
      borderTopRightRadius: 12,
      borderBottomRightRadius: 32,
      borderBottomLeftRadius: 0,
    },
  },
  {
    label: 'Scrollable content',
    expect: 'The last row is reachable above the gap at the full detent.',
    detents: [0, 320, '100%'],
    detentInsets: [FLOATING],
    scrollable: true,
  },
  {
    label: 'Keyboard input',
    expect: 'The field stays above the keyboard as the card grows behind it.',
    detents: [0, 'content'],
    detentInsets: [FLOATING],
    keyboard: true,
  },
  {
    label: 'No closed detent',
    expect: 'Stays open: the card never leaves, and drags stop at 160.',
    detents: [160, '50%'],
    detentInsets: [FLOATING, { left: 32, right: 32, bottom: 64 }],
  },
];

const Toggle = ({
  label,
  value,
  onValueChange,
}: {
  label: string;
  value: boolean;
  onValueChange: (value: boolean) => void;
}) => (
  <View style={styles.toggle}>
    <Text>{label}</Text>
    <Switch value={value} onValueChange={onValueChange} />
  </View>
);

/**
 * The sheet doesn't avoid the keyboard: as the docs describe, content pads
 * itself. The card's bottom gap already clears part of the keyboard.
 */
const KeyboardBody = () => {
  const { height } = useReanimatedKeyboardAnimation();
  const paddingStyle = useAnimatedStyle(() => ({
    paddingBottom: 16 + Math.max(0, -height.value - (FLOATING.bottom ?? 0)),
  }));

  return (
    <Animated.View style={[styles.body, paddingStyle]}>
      <Text>Type here:</Text>
      <TextInput
        accessibilityLabel="Sheet input"
        placeholder="Sheet input"
        style={styles.input}
      />
    </Animated.View>
  );
};

export const DetachedSheetScreen = () => {
  const [index, setIndex] = useState(0);
  const [scenarioIndex, setScenarioIndex] = useState(0);
  const [isModal, setIsModal] = useState(true);
  const [nativeOverlay, setNativeOverlay] = useState(false);
  const [extendUnderStatusBar, setExtendUnderStatusBar] = useState(false);
  const [hasShadow, setHasShadow] = useState(true);
  const [behindPresses, setBehindPresses] = useState(0);
  const [isRTLOnNextLaunch, setIsRTLOnNextLaunch] = useState(I18nManager.isRTL);
  const scenario = SCENARIOS[scenarioIndex]!;
  const openIndex = scenario.detents[0] === 0 ? 1 : 0;

  const sheetProps = {
    detents: scenario.detents,
    detentInsets: scenario.detentInsets,
    index: Math.min(index, scenario.detents.length - 1),
    onIndexChange: setIndex,
    extendUnderStatusBar,
    animateContentHeight: !scenario.keyboard,
    surfaceStyle: {
      ...(scenario.shape ?? ROUNDED),
      boxShadow: hasShadow ? SHADOW : undefined,
    },
    surface: (
      <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
    ),
    children: (
      <>
        {/* Full-bleed band: its width shows whether the content reflows. */}
        <View style={styles.band}>
          <SheetHeader title={scenario.label} onClose={() => setIndex(0)} />
        </View>
        {scenario.keyboard ? (
          <KeyboardBody />
        ) : scenario.scrollable ? (
          <ScrollView style={{ flex: 1 }}>
            {Array.from({ length: 40 }, (_, row) => (
              <Text key={row} style={styles.row}>
                Row {row + 1}
              </Text>
            ))}
          </ScrollView>
        ) : (
          <View style={styles.body}>
            <Text>{scenario.expect}</Text>
            <View style={styles.detentButtons}>
              {scenario.detents.map((detent, detentIndex) => (
                <Button
                  key={detentIndex}
                  title={`Detent ${detentIndex}`}
                  onPress={() => setIndex(detentIndex)}
                />
              ))}
            </View>
          </View>
        )}
      </>
    ),
  };

  return (
    <DemoScreen
      title="Detached sheet"
      sheet={
        <>
          {/* Under the sheet, to catch presses in the gaps of an inline sheet. */}
          <Pressable
            style={styles.behind}
            onPress={() => setBehindPresses((count) => count + 1)}
          >
            <Text>Presses behind the sheet: {behindPresses}</Text>
          </Pressable>
          {isModal ? (
            <ModalBottomSheet
              {...sheetProps}
              scrimColor={MODAL_SCRIM_COLOR}
              nativeOverlay={nativeOverlay}
            />
          ) : (
            <BottomSheet {...sheetProps} />
          )}
        </>
      }
    >
      <ScrollView style={styles.picker}>
        {SCENARIOS.map((item, itemIndex) => (
          <TouchableOpacity
            key={item.label}
            style={[
              styles.scenario,
              itemIndex === scenarioIndex && styles.scenarioSelected,
            ]}
            onPress={() => {
              setScenarioIndex(itemIndex);
              setIndex(item.detents[0] === 0 ? 1 : 0);
            }}
          >
            <Text>{item.label}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
      <View style={styles.toggles}>
        <Toggle label="Modal" value={isModal} onValueChange={setIsModal} />
        <Toggle
          label="Native overlay"
          value={nativeOverlay}
          onValueChange={setNativeOverlay}
        />
        <Toggle
          label="Under status bar"
          value={extendUnderStatusBar}
          onValueChange={setExtendUnderStatusBar}
        />
        <Toggle label="Shadow" value={hasShadow} onValueChange={setHasShadow} />
        <Toggle
          // Applies on the next app launch.
          label={`RTL (now ${I18nManager.isRTL ? 'on' : 'off'})`}
          value={isRTLOnNextLaunch}
          onValueChange={(value) => {
            I18nManager.allowRTL(value);
            I18nManager.forceRTL(value);
            setIsRTLOnNextLaunch(value);
          }}
        />
      </View>
      <Button title="Open sheet" onPress={() => setIndex(openIndex)} />
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  picker: { maxHeight: 400 },
  scenario: {
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
  },
  scenarioSelected: { backgroundColor: '#dbeafe' },
  toggles: { flexDirection: 'row', flexWrap: 'wrap', columnGap: 16 },
  toggle: {
    width: '45%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  band: { height: 72, backgroundColor: '#3b82f6' },
  body: { padding: 16, gap: 12 },
  detentButtons: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  input: {
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 8,
    padding: 8,
  },
  row: { padding: 16, borderBottomWidth: 1, borderBottomColor: '#ededed' },
  behind: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: 210,
    padding: 8,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fde68a',
  },
});
