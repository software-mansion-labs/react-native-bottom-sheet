import { type RefObject, useRef, useState } from 'react';
import {
  Button,
  I18nManager,
  Image,
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
  type DetentInsets,
  type DetentValue,
  ModalBottomSheet,
  type SurfaceStyle,
} from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, MODAL_SCRIM_COLOR, SheetHeader } from '../demoShared';

const FLOATING: DetentInsets = { left: 16, right: 16, bottom: 32 };
const ROUNDED: SurfaceStyle = { borderRadius: 28, borderCurve: 'continuous' };
const SHADOW = '0 4 24 rgba(0, 0, 0, 0.35)';

/** A detent floating with the standard gaps. */
const floating = (value: DetentValue): Detent => ({ value, insets: FLOATING });

type Scenario = {
  label: string;
  /** What to look for when trying this scenario. */
  expect: string;
  detents: Detent[];
  topInset?: number;
  shape?: SurfaceStyle;
  scrollable?: boolean;
  keyboard?: boolean;
  patternedSurface?: boolean;
  hugeGaps?: boolean;
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
    detents: [0, floating(320), '100%'],
  },
  {
    label: 'Always floating',
    expect: 'The same gaps at every detent. Closing slides through the gap.',
    detents: [0, floating(320), floating('100%')],
  },
  {
    label: 'Top inset at full',
    expect: 'The full detent stops 120 below the top and 32 above the bottom.',
    detents: [0, floating(320), floating('100%')],
    topInset: 120,
  },
  {
    label: 'Percentage detents',
    expect: 'Percentages resolve against the space the insets leave.',
    detents: [0, floating('25%'), floating('50%'), floating('100%')],
    topInset: 80,
  },
  {
    label: 'Content height',
    expect: 'The card hugs its content and floats above the gap.',
    detents: [0, floating('content')],
  },
  {
    label: 'Scaled content height',
    expect: 'The card fits its scaled content, then grows to the full width.',
    detents: [0, floating('content'), '100%'],
  },
  {
    label: 'Scrollable, narrows when full',
    expect: 'The last row reaches the bottom of the narrower full card.',
    detents: [0, 320, floating('100%')],
    scrollable: true,
  },
  {
    label: 'Narrow when full',
    expect: 'The surface and the last row reach the narrow card’s bottom.',
    detents: [
      0,
      { value: 180, insets: { left: 24, right: 24, bottom: 24 } },
      { value: '100%', insets: { left: 64, right: 64 } },
    ],
    scrollable: true,
  },
  {
    label: 'Lopsided gaps',
    expect: 'The gap moves across. The blue band keeps touching the card.',
    detents: [
      0,
      { value: 240, insets: { left: 48, bottom: 16 } },
      { value: '60%', insets: { right: 48, bottom: 64 } },
    ],
  },
  {
    label: 'Square corners',
    expect: 'Gaps clip the surface even with no radius.',
    detents: [0, floating(320), '100%'],
    shape: {},
  },
  {
    label: 'Mixed radii',
    expect: 'Each corner keeps its own radius as the edges move.',
    detents: [
      0,
      floating(320),
      { value: '100%', insets: { left: 8, right: 8, bottom: 8 } },
    ],
    topInset: 8,
    shape: {
      borderTopLeftRadius: 48,
      borderTopRightRadius: 12,
      borderBottomRightRadius: 32,
      borderBottomLeftRadius: 0,
    },
  },
  {
    label: 'Huge gaps',
    expect: 'Scale 0.68 on a 402-wide screen. Tap a button to compare.',
    detents: [
      0,
      { value: 400, insets: { left: 64, right: 64, bottom: 96 } },
      '100%',
    ],
    hugeGaps: true,
  },
  {
    label: 'Patterned surface',
    expect: 'The border and circle scale with the card, like an image would.',
    detents: [0, floating(320), '100%'],
    patternedSurface: true,
  },
  {
    label: 'Scrollable content',
    expect: 'The last row is reachable above the gap at the full detent.',
    detents: [0, floating(320), floating('100%')],
    scrollable: true,
  },
  {
    label: 'Keyboard input',
    expect: 'The field stays above the keyboard as the card grows behind it.',
    detents: [0, floating('content')],
    keyboard: true,
  },
  {
    label: 'No closed detent',
    expect:
      'Drags stop at 160. No detent hides it, so the X unmounts the sheet.',
    detents: [
      floating(160),
      { value: '50%', insets: { left: 32, right: 32, bottom: 64 } },
    ],
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

/** Taps report what `measure` and `measureInWindow` return, to compare with the screen. */
const MeasureProbe = ({
  surfaceRef,
}: {
  surfaceRef: RefObject<View | null>;
}) => {
  const ref = useRef<View>(null);
  const [presses, setPresses] = useState(0);
  const [report, setReport] = useState({
    page: '-',
    window: '-',
    surface: '-',
  });
  const measure = () => {
    setPresses((count) => count + 1);
    ref.current?.measure((_x, _y, width, height, pageX, pageY) =>
      setReport((previous) => ({
        ...previous,
        page: roundedFrame([pageX, pageY, width, height]),
      }))
    );
    ref.current?.measureInWindow((x, y, width, height) =>
      setReport((previous) => ({
        ...previous,
        window: roundedFrame([x, y, width, height]),
      }))
    );
    surfaceRef.current?.measure((_x, _y, width, height, pageX, pageY) =>
      setReport((previous) => ({
        ...previous,
        surface: roundedFrame([pageX, pageY, width, height]),
      }))
    );
  };
  return (
    <Pressable
      ref={ref}
      accessibilityLabel="Measure probe"
      onPress={measure}
      style={({ pressed }) => [styles.probe, pressed && styles.probePressed]}
    >
      <Text>
        {presses}: page {report.page}
        {'\n'}window {report.window}
        {'\n'}surface {report.surface}
      </Text>
    </Pressable>
  );
};

const roundedFrame = (values: number[]) =>
  values.map((value) => Math.round(value * 10) / 10).join(', ');

/**
 * Each press reports the button's and the image's measurements. Sliding off a
 * button and releasing must not count as a press.
 */
const MeasuredButtons = ({
  imageRef,
}: {
  imageRef: RefObject<Image | null>;
}) => {
  const [report, setReport] = useState('Tap a button');
  const [presses, setPresses] = useState({ A: 0, B: 0 });
  const refs = { A: useRef<View>(null), B: useRef<View>(null) };

  const onPress = (name: 'A' | 'B') => {
    setPresses((previous) => ({ ...previous, [name]: previous[name] + 1 }));
    refs[name].current?.measure((_x, _y, width, height, pageX, pageY) => {
      refs[name].current?.measureInWindow((x, y, windowWidth, windowHeight) => {
        imageRef.current?.measure(
          (
            _imageX,
            _imageY,
            imageWidth,
            imageHeight,
            imagePageX,
            imagePageY
          ) => {
            setReport(
              `${name} page ${roundedFrame([pageX, pageY, width, height])}\n` +
                `${name} window ${roundedFrame([x, y, windowWidth, windowHeight])}\n` +
                `image ${roundedFrame([imagePageX, imagePageY, imageWidth, imageHeight])}`
            );
          }
        );
      });
    });
  };

  return (
    <>
      <View style={styles.measuredButtons}>
        {(['A', 'B'] as const).map((name) => (
          <Pressable
            key={name}
            ref={refs[name]}
            accessibilityLabel={`Measured button ${name}`}
            onPress={() => onPress(name)}
            style={({ pressed }) => [
              styles.measuredButton,
              pressed && styles.measuredButtonPressed,
            ]}
          >
            <Text style={styles.measuredButtonText}>
              {name} ({presses[name]})
            </Text>
          </Pressable>
        ))}
      </View>
      <Text style={styles.measuredReport}>{report}</Text>
    </>
  );
};

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
  const [isUnmounted, setIsUnmounted] = useState(false);
  const [scenarioIndex, setScenarioIndex] = useState(0);
  const [isModal, setIsModal] = useState(true);
  const [nativeOverlay, setNativeOverlay] = useState(false);
  const [isUnderStatusBar, setIsUnderStatusBar] = useState(false);
  const [hasShadow, setHasShadow] = useState(true);
  const [behindPresses, setBehindPresses] = useState(0);
  const surfaceRef = useRef<View>(null);
  const imageRef = useRef<Image>(null);
  const [isRTLOnNextLaunch, setIsRTLOnNextLaunch] = useState(I18nManager.isRTL);
  const scenario = SCENARIOS[scenarioIndex]!;
  const hasClosedDetent = scenario.detents[0] === 0;
  const openIndex = hasClosedDetent ? 1 : 0;

  const sheetProps = {
    detents: scenario.detents,
    topInset: isUnderStatusBar ? 0 : scenario.topInset,
    index: Math.min(index, scenario.detents.length - 1),
    onIndexChange: setIndex,
    animateContentHeight: !scenario.keyboard,
    surfaceStyle: {
      ...(scenario.shape ?? ROUNDED),
      boxShadow: hasShadow ? SHADOW : undefined,
    },
    surface: (
      <View
        // Toggling the modes remounts the sheet, and the old surface can unmount
        // after the new one mounts, so it only clears its own view.
        ref={(view) => {
          surfaceRef.current = view;
          return () => {
            if (surfaceRef.current === view) surfaceRef.current = null;
          };
        }}
        style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]}
      >
        {scenario.patternedSurface && (
          <View style={[StyleSheet.absoluteFill, styles.pattern]}>
            <View style={styles.patternCircle} />
          </View>
        )}
      </View>
    ),
    children: (
      <>
        {/* Full-bleed band: it must touch the card's edges at every detent. */}
        <View style={styles.band}>
          <SheetHeader
            title={scenario.label}
            onClose={() =>
              hasClosedDetent ? setIndex(0) : setIsUnmounted(true)
            }
          />
        </View>
        {scenario.hugeGaps && (
          // Full-bleed too: it must touch the card's edges at every detent.
          <Image
            ref={imageRef}
            accessibilityLabel="Measured image"
            resizeMode="cover"
            source={require('../../assets/icon.png')}
            style={styles.measuredImage}
          />
        )}
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
            {scenario.hugeGaps ? (
              <MeasuredButtons imageRef={imageRef} />
            ) : (
              <MeasureProbe surfaceRef={surfaceRef} />
            )}
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
          {isUnmounted ? null : isModal ? (
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
              setIsUnmounted(false);
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
          value={isUnderStatusBar}
          onValueChange={setIsUnderStatusBar}
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
      <Button
        title="Open sheet"
        onPress={() => {
          setIsUnmounted(false);
          setIndex(openIndex);
        }}
      />
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  picker: { maxHeight: 360 },
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
  measuredImage: { height: 150, width: '100%' },
  measuredButtons: { flexDirection: 'row', gap: 8 },
  measuredButton: {
    alignItems: 'center',
    backgroundColor: '#2563eb',
    borderRadius: 8,
    flex: 1,
    padding: 12,
  },
  measuredButtonPressed: { backgroundColor: '#1e3a8a' },
  measuredButtonText: { color: 'white', fontWeight: '600' },
  measuredReport: { minHeight: 56 },
  probe: { backgroundColor: '#eef2ff', padding: 8, minHeight: 72 },
  probePressed: { backgroundColor: '#c7d2fe' },
  pattern: {
    alignItems: 'center',
    justifyContent: 'center',
    borderColor: '#ef4444',
    borderWidth: 6,
  },
  patternCircle: {
    backgroundColor: '#fde68a',
    borderRadius: 60,
    height: 120,
    width: 120,
  },
  band: { backgroundColor: '#3b82f6' },
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
