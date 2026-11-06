import { useRef, useState, type CSSProperties } from 'react';
import {
  ScrollView,
  StyleSheet,
  Text,
  View,
  type ViewStyle,
} from 'react-native';
import type { ScrollableNegotiationMode } from '@swmansion/react-native-bottom-sheet';

import {
  Control,
  ControlGroup,
  Example,
  Hint,
  interactiveExample,
  Meter,
  Segmented,
  SheetBody,
  StageInlineSheet,
} from '../components/InteractiveExample';
import {
  COLLAPSE_HINTS,
  EXPAND_HINTS,
  FingerIndicator,
  MobilePreviewToggle,
  useListMouseDrag,
  useMobilePreview,
} from '../components/InteractiveExample/negotiationPreview';

const DETENTS = [200, 320];
const ROWS = Array.from({ length: 30 }, (_, i) => `Row ${i + 1}`);

const MODES = (['none', 'initial', 'handoff'] as const).map((value) => ({
  value,
  label: value,
}));

export const NegotiationModesExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(0);
    const [expand, setExpand] = useState<ScrollableNegotiationMode>('handoff');
    const [collapse, setCollapse] =
      useState<ScrollableNegotiationMode>('initial');
    const sheetValueRef = useRef<HTMLSpanElement>(null);
    const sheetFillRef = useRef<HTMLDivElement>(null);
    const listValueRef = useRef<HTMLSpanElement>(null);
    const listFillRef = useRef<HTMLDivElement>(null);
    const { isMobilePreview, setIsMobilePreview, isTouchDevice } =
      useMobilePreview();
    const listParentRef = useListMouseDrag({
      isEnabled: isMobilePreview,
      canExpandSheet: index === 0,
      canCollapseSheet: index === 1,
      expand,
      collapse,
    });

    return (
      <Example
        stage={
          <>
            {/* Above the sheet's largest detent, so both stay in sight. */}
            <div style={METERS_STYLE}>
              <Control
                label="Sheet position"
                value={<span ref={sheetValueRef}>{DETENTS[0]}</span>}
              >
                <Meter fillRef={sheetFillRef} />
              </Control>
              <Control
                label="List offset"
                value={<span ref={listValueRef}>0</span>}
              >
                <Meter fillRef={listFillRef} />
              </Control>
            </div>
            <StageInlineSheet
              detents={DETENTS}
              index={index}
              onIndexChange={setIndex}
              scrollableNegotiation={{ expand, collapse }}
              onPositionChange={({ nativeEvent }) => {
                sheetValueRef.current!.textContent =
                  nativeEvent.position.toFixed(0);
                sheetFillRef.current!.style.transform = `scaleX(${nativeEvent.index})`;
              }}
            >
              <SheetBody title="Drag inside the list" />
              <div ref={listParentRef}>
                <ScrollView
                  style={inner.list}
                  scrollEventThrottle={16}
                  onScroll={({ nativeEvent }) => {
                    const { contentOffset, contentSize, layoutMeasurement } =
                      nativeEvent;
                    const offset = Math.max(0, contentOffset.y);
                    listValueRef.current!.textContent = offset.toFixed(0);
                    listFillRef.current!.style.transform = `scaleX(${
                      offset / (contentSize.height - layoutMeasurement.height)
                    })`;
                  }}
                >
                  {ROWS.map((row) => (
                    <View key={row} style={inner.row}>
                      <Text style={inner.rowText}>{row}</Text>
                    </View>
                  ))}
                </ScrollView>
              </div>
            </StageInlineSheet>
            {isMobilePreview && !isTouchDevice && <FingerIndicator />}
          </>
        }
        controls={
          <ControlGroup>
            <Segmented
              label="expand"
              options={MODES}
              value={expand}
              onChange={setExpand}
            />
            <Segmented
              label="collapse"
              options={MODES}
              value={collapse}
              onChange={setCollapse}
            />
            <Hint>{EXPAND_HINTS[expand]}</Hint>
            <Hint>{COLLAPSE_HINTS[collapse]}</Hint>
            {!isTouchDevice && (
              <MobilePreviewToggle
                value={isMobilePreview}
                onChange={setIsMobilePreview}
              />
            )}
          </ControlGroup>
        }
        code={`const [index, setIndex] = useState(0);

<BottomSheet
  detents={[200, 320]}
  index={index}
  onIndexChange={setIndex}
  scrollableNegotiation={{ expand: '${expand}', collapse: '${collapse}' }}
  surface={
    <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
  }
>
  <ScrollView style={{ height: 250 }}>{/* ... */}</ScrollView>
</BottomSheet>;`}
      />
    );
  },
  { height: 440 }
);

const METERS_STYLE: CSSProperties = {
  position: 'absolute',
  top: 12,
  right: 16,
  left: 16,
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
  maxWidth: 360,
  margin: '0 auto',
  fontSize: 14,
};

const inner = StyleSheet.create({
  list: {
    height: 250,
    // Web-only: a phone's drag stops at the list's edge, not the page.
    ...({ overscrollBehavior: 'contain' } as ViewStyle),
  },
  row: {
    justifyContent: 'center',
    height: 44,
    paddingHorizontal: 20,
    borderTopWidth: 1,
    borderTopColor: 'var(--example-sheet-line)',
  },
  rowText: {
    fontFamily: 'var(--ifm-font-family-base)',
    fontSize: 15,
    color: 'var(--example-text)',
  },
});
