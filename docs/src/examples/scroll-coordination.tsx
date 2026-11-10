import {
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type PointerEvent as ReactPointerEvent,
} from 'react';
import {
  ScrollView,
  StyleSheet,
  Text,
  View,
  type ViewStyle,
} from 'react-native';
import type { ScrollCoordinationMode } from '@swmansion/react-native-bottom-sheet';

import {
  Control,
  ControlGroup,
  Example,
  hasTouchGestures,
  Hint,
  interactiveExample,
  Meter,
  Segmented,
  SheetBody,
  StageInlineSheet,
  Toggle,
} from '../components/InteractiveExample';

const DETENTS = [200, 320];
const ROWS = Array.from({ length: 30 }, (_, i) => `Row ${i + 1}`);

const MODES = (['none', 'initial', 'handoff'] as const).map((value) => ({
  value,
  label: value,
}));

const EXPAND_HINTS: Record<ScrollCoordinationMode, string> = {
  none: 'Dragging up in the list never moves the sheet.',
  initial:
    'Dragging up moves the sheet, which stops at the top until you lift. Drag up again to scroll the list.',
  handoff:
    'Dragging up moves the sheet to the top, then scrolls the list without lifting.',
};

const COLLAPSE_HINTS: Record<ScrollCoordinationMode, string> = {
  none: 'Dragging down in the list never moves the sheet.',
  initial:
    'At the top, dragging down from a scrolled list only scrolls it. From its start, it moves the sheet.',
  handoff:
    'At the top, dragging down scrolls the list to its start, then moves the sheet without lifting.',
};

/** How far a mouse moves before a press counts as a drag. */
const DRAG_SLOP = 4;

/** Shows where a simulated finger is, for as long as it's pressed on the stage. */
const FingerIndicator = () => {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const finger = ref.current!;
    const stage = finger.parentElement!;
    let isPressed = false;

    const place = ({ clientX, clientY }: PointerEvent) => {
      const { left, top } = stage.getBoundingClientRect();
      finger.style.transform = `translate(${clientX - left}px, ${clientY - top}px)`;
    };
    const onDown = (event: PointerEvent) => {
      if (event.button !== 0 || !stage.contains(event.target as Node)) return;
      isPressed = true;
      place(event);
      finger.style.opacity = '1';
    };
    const onMove = (event: PointerEvent) => {
      if (isPressed) place(event);
    };
    const onUp = () => {
      isPressed = false;
      finger.style.opacity = '0';
    };

    // Captured, as the sheet's own gesture handling sees these events first.
    window.addEventListener('pointerdown', onDown, true);
    window.addEventListener('pointermove', onMove, true);
    window.addEventListener('pointerup', onUp, true);
    window.addEventListener('pointercancel', onUp, true);
    return () => {
      window.removeEventListener('pointerdown', onDown, true);
      window.removeEventListener('pointermove', onMove, true);
      window.removeEventListener('pointerup', onUp, true);
      window.removeEventListener('pointercancel', onUp, true);
    };
  }, []);

  return <div ref={ref} style={FINGER_STYLE} aria-hidden />;
};

export const CoordinationModesExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(0);
    const [expand, setExpand] = useState<ScrollCoordinationMode>('handoff');
    const [collapse, setCollapse] = useState<ScrollCoordinationMode>('initial');
    const sheetValueRef = useRef<HTMLSpanElement>(null);
    const sheetFillRef = useRef<HTMLDivElement>(null);
    const listValueRef = useRef<HTMLSpanElement>(null);
    const listFillRef = useRef<HTMLDivElement>(null);
    const [isMobilePreview, setIsMobilePreview] = useState(true);
    // A touch device already has the real gestures, so it has nothing to preview.
    const [isTouchDevice] = useState(hasTouchGestures);

    // A mouse can't drag a list the way a finger does, so this does it for the
    // drags the sheet leaves to the list. The sheet handles the others itself.
    const dragListWithMouse = (event: ReactPointerEvent<HTMLDivElement>) => {
      const list = event.currentTarget.firstElementChild as HTMLElement;
      // A press on the list itself, rather than a row, is on its scrollbar.
      if (
        !isMobilePreview ||
        event.pointerType !== 'mouse' ||
        event.button !== 0 ||
        event.target === list
      ) {
        return;
      }

      const startY = event.clientY;
      let lastY: number | null = null;

      const listOwnsDrag = (dy: number) => {
        const isExpanding = dy < 0;
        const canMoveSheet = isExpanding ? index === 0 : index === 1;
        const mode = isExpanding ? expand : collapse;
        if (!canMoveSheet || mode === 'none') return true;
        return mode === 'initial' && !isExpanding && list.scrollTop > 0;
      };

      const onMove = ({ clientY }: PointerEvent) => {
        if (lastY === null) {
          const dy = clientY - startY;
          if (Math.abs(dy) < DRAG_SLOP) return;
          if (!listOwnsDrag(dy)) {
            stop();
            return;
          }
          lastY = clientY;
          return;
        }
        list.scrollTop -= clientY - lastY;
        lastY = clientY;
      };
      const stop = () => {
        window.removeEventListener('pointermove', onMove);
        window.removeEventListener('pointerup', stop);
        window.removeEventListener('pointercancel', stop);
      };

      window.addEventListener('pointermove', onMove);
      window.addEventListener('pointerup', stop);
      window.addEventListener('pointercancel', stop);
    };

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
              scrollCoordination={{ expand, collapse }}
              onPositionChange={({ nativeEvent }) => {
                sheetValueRef.current!.textContent =
                  nativeEvent.position.toFixed(0);
                sheetFillRef.current!.style.transform = `scaleX(${nativeEvent.index})`;
              }}
            >
              <SheetBody title="Drag inside the list" />
              <div onPointerDown={dragListWithMouse}>
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
              <>
                <Toggle
                  label="Mobile preview"
                  value={isMobilePreview}
                  onChange={setIsMobilePreview}
                />
                <Hint>
                  {isMobilePreview
                    ? 'This simulates a phone, so a mouse drag acts like a finger, shown as a circle.'
                    : "Most of these gestures are for touch, so a mouse on the web mostly won't do them. Dragging only scrolls the list during a handoff. Otherwise, use the wheel."}
                </Hint>
              </>
            )}
          </ControlGroup>
        }
        code={`const [index, setIndex] = useState(0);

<BottomSheet
  detents={[200, 320]}
  index={index}
  onIndexChange={setIndex}
  scrollCoordination={{ expand: '${expand}', collapse: '${collapse}' }}
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

const FINGER_STYLE: CSSProperties = {
  position: 'absolute',
  zIndex: 1,
  top: -18,
  left: -18,
  width: 36,
  height: 36,
  borderRadius: '50%',
  border: '1px solid rgba(255, 255, 255, 0.7)',
  backgroundColor: 'rgba(120, 130, 160, 0.45)',
  opacity: 0,
  pointerEvents: 'none',
  transition: 'opacity 120ms ease-out',
};

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
