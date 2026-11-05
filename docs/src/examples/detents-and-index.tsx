import { useEffect, useRef, useState } from 'react';
import { programmatic } from '@swmansion/react-native-bottom-sheet';

import {
  ControlGroup,
  EventLog,
  Example,
  Hint,
  interactiveExample,
  Segmented,
  SheetBody,
  SheetText,
  Slider,
  StageButton,
  StageContent,
  StageSheet,
  Toggle,
  useEventLog,
} from '../components/InteractiveExample';

const DETENT_FORMS = {
  number: { label: 'Number', sizeLabel: 'Height', sizes: [120, 200, 280] },
  percentage: {
    label: 'Percentage',
    sizeLabel: 'Percentage',
    sizes: ['30%', '60%', '90%'],
  },
  content: { label: 'Content', sizeLabel: 'Paragraphs', sizes: [1, 2, 3] },
} as const;

type DetentForm = keyof typeof DETENT_FORMS;

const PARAGRAPHS = [
  'A content detent is as tall as what the sheet measures inside it.',
  'Add a paragraph, and the sheet grows to fit it.',
  "It's capped by the available height, so it can't outgrow the stage.",
];

export const DetentTypesExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(1);
    const [form, setForm] = useState<DetentForm>('number');
    const [sizeIndex, setSizeIndex] = useState(1);
    const { label, sizeLabel, sizes } = DETENT_FORMS[form];
    const size = sizes[sizeIndex]!;
    const detent = form === 'content' ? 'content' : size;
    const detentCode = typeof detent === 'number' ? detent : `'${detent}'`;

    return (
      <Example
        stage={
          <>
            <StageContent>
              <StageButton label="Open sheet" onPress={() => setIndex(1)} />
            </StageContent>
            <StageSheet
              detents={[0, detent as number | `${number}%` | 'content']}
              index={index}
              onIndexChange={setIndex}
              scrimColor="rgba(0, 0, 0, 0.4)"
            >
              <SheetBody title={`${label} detent`}>
                {form === 'content' ? (
                  PARAGRAPHS.slice(0, size as number).map((paragraph) => (
                    <SheetText key={paragraph}>{paragraph}</SheetText>
                  ))
                ) : (
                  <SheetText>
                    The sheet snaps open at {detentCode}, whatever is inside it.
                  </SheetText>
                )}
              </SheetBody>
            </StageSheet>
          </>
        }
        controls={
          <ControlGroup>
            <Segmented
              label="Detent form"
              options={Object.entries(DETENT_FORMS).map(([value, entry]) => ({
                value: value as DetentForm,
                label: entry.label,
                compactLabel: value === 'percentage' ? '%' : undefined,
              }))}
              value={form}
              onChange={(next) => {
                setForm(next);
                setIndex(1);
              }}
            />
            <Segmented
              label={sizeLabel}
              options={sizes.map((value, i) => ({
                value: i,
                label: String(value),
              }))}
              value={sizeIndex}
              onChange={(next) => {
                setSizeIndex(next);
                setIndex(1);
              }}
            />
          </ControlGroup>
        }
        code={`const [index, setIndex] = useState(1);

<ModalBottomSheet
  detents={[0, ${detentCode}]}
  index={index}
  onIndexChange={setIndex}
  surface={
    <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
  }
>
  {/* ... */}
</ModalBottomSheet>;`}
      />
    );
  },
  { height: 360 }
);

const CARD_SHAPE = {
  borderRadius: 28,
  boxShadow: '0 2 16 rgba(0, 16, 72, 0.16)',
};

const InsetSlider = ({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
}) => (
  <Slider
    label={label}
    value={value}
    onChange={onChange}
    max={64}
    decimals={0}
  />
);

const INITIAL_INSETS = {
  side: 24,
  bottom: 24,
  topSide: 0,
  top: 0,
  topBottom: 0,
};

// Critically damped, like the sheet's own spring: settled at ω·t ≈ 8.
const EASED_SETTLE_OMEGA = 8 / 0.45;
const EASED_FOLLOW_OMEGA = 8 / 0.12;
const EASED_FOLLOW_WINDOW_MS = 100;

/**
 * Eases to a value that changes. The sheet applies insets that don't move it at
 * once, so the example animates them itself: a one-off change on the sheet's
 * curve, and one changing rapidly, like a dragged slider, closely behind it.
 */
const useEasedValue = (target: number) => {
  const [value, setValue] = useState(target);
  const motion = useRef({
    position: target,
    velocity: 0,
    changedAt: -Infinity,
  });

  useEffect(() => {
    const current = motion.current;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      current.position = target;
      current.velocity = 0;
      setValue(target);
      return;
    }
    let last = performance.now();
    const omega =
      last - current.changedAt < EASED_FOLLOW_WINDOW_MS
        ? EASED_FOLLOW_OMEGA
        : EASED_SETTLE_OMEGA;
    current.changedAt = last;
    let frame = 0;
    const step = () => {
      const now = performance.now();
      const elapsed = (now - last) / 1000;
      last = now;
      const distance = current.position - target;
      const decay = Math.exp(-omega * elapsed);
      const push = current.velocity + omega * distance;
      current.position = target + decay * (distance + push * elapsed);
      current.velocity = decay * (current.velocity - omega * push * elapsed);
      if (
        Math.abs(current.position - target) < 0.01 &&
        Math.abs(current.velocity) < 0.5
      ) {
        current.position = target;
        current.velocity = 0;
        setValue(target);
        return;
      }
      setValue(current.position);
      frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [target]);

  return value;
};

export const FloatingCardExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(1);
    const [insets, setInsets] = useState(INITIAL_INSETS);
    const side = useEasedValue(insets.side);
    const topSide = useEasedValue(insets.topSide);
    const topBottom = useEasedValue(insets.topBottom);

    /** Moves to the detent a slider belongs to, so its change is on screen. */
    const changeInset =
      (detent: number, name: keyof typeof INITIAL_INSETS) =>
      (value: number) => {
        setInsets((current) => ({ ...current, [name]: value }));
        setIndex(detent);
      };

    return (
      <Example
        stage={
          <>
            <StageContent>
              <StageButton label="Open sheet" onPress={() => setIndex(1)} />
            </StageContent>
            <StageSheet
              detents={[0, 180, '100%']}
              insets={[
                {},
                {
                  left: side,
                  right: side,
                  bottom: insets.bottom,
                },
                {
                  left: topSide,
                  right: topSide,
                  top: insets.top,
                  bottom: topBottom,
                },
              ]}
              surfaceStyle={CARD_SHAPE}
              index={index}
              onIndexChange={setIndex}
              scrimColor="rgba(0, 0, 0, 0.4)"
            >
              <SheetBody title="Floating card">
                <SheetText>
                  Drag it up. The gaps blend between the detents, and its
                  content scales rather than reflows.
                </SheetText>
              </SheetBody>
            </StageSheet>
          </>
        }
        controls={
          <ControlGroup>
            <Segmented
              label="index"
              options={[
                { value: 0, label: '0' },
                { value: 1, label: '1' },
                { value: 2, label: '2' },
              ]}
              value={index}
              onChange={setIndex}
            />
            <InsetSlider
              label="left and right at index 1"
              value={insets.side}
              onChange={changeInset(1, 'side')}
            />
            <InsetSlider
              label="bottom at index 1"
              value={insets.bottom}
              onChange={changeInset(1, 'bottom')}
            />
            <InsetSlider
              label="left and right at index 2"
              value={insets.topSide}
              onChange={changeInset(2, 'topSide')}
            />
            <InsetSlider
              label="bottom at index 2"
              value={insets.topBottom}
              onChange={changeInset(2, 'topBottom')}
            />
            <InsetSlider
              label="top inset"
              value={insets.top}
              onChange={changeInset(2, 'top')}
            />
          </ControlGroup>
        }
        code={`const [index, setIndex] = useState(1);

<ModalBottomSheet
  detents={[0, 180, '100%']}
  insets={[
    {},
    { left: ${Math.round(insets.side)}, right: ${Math.round(insets.side)}, bottom: ${Math.round(insets.bottom)} },
    { left: ${Math.round(insets.topSide)}, right: ${Math.round(insets.topSide)}, top: ${Math.round(insets.top)}, bottom: ${Math.round(insets.topBottom)} },
  ]}
  surfaceStyle={{ borderRadius: 28 }}
  index={index}
  onIndexChange={setIndex}
  surface={
    <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
  }
>
  {/* ... */}
</ModalBottomSheet>;`}
      />
    );
  },
  { height: 420 }
);

export const IndexEventsExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(1);
    const { entries, log, clear } = useEventLog();

    return (
      <Example
        stage={
          <>
            <StageContent>
              <StageButton label="Open sheet" onPress={() => setIndex(1)} />
            </StageContent>
            <StageSheet
              detents={[0, 140, 280]}
              index={index}
              onIndexChange={(next) => {
                log(`onIndexChange(${next})`);
                setIndex(next);
              }}
              onSettle={(settled) => log(`onSettle(${settled})`)}
              scrimColor="rgba(0, 0, 0, 0.4)"
            >
              <SheetBody title="Drag me">
                <SheetText>
                  A drag reports both events. Setting the index reports only the
                  settle.
                </SheetText>
              </SheetBody>
            </StageSheet>
          </>
        }
        controls={
          <>
            <ControlGroup>
              <Segmented
                label="index"
                options={[
                  { value: 0, label: '0' },
                  { value: 1, label: '1' },
                  { value: 2, label: '2' },
                ]}
                value={index}
                onChange={setIndex}
              />
              <Hint>
                Drag the sheet, then set the index here, and compare the events
                each one fires.
              </Hint>
            </ControlGroup>
            <ControlGroup>
              <EventLog
                entries={entries}
                onClear={clear}
                empty="Events appear here as they fire."
              />
            </ControlGroup>
          </>
        }
        code={`const [index, setIndex] = useState(1);

<ModalBottomSheet
  detents={[0, 140, 280]}
  index={index}
  // Fires when a drag commits to a detent, before the animation settles.
  onIndexChange={setIndex}
  // Fires when any snap finishes, including one you started by setting \`index\`.
  onSettle={(settled) => console.log(\`Settled at \${settled}.\`)}
  surface={
    <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
  }
>
  {/* ... */}
</ModalBottomSheet>;`}
      />
    );
  },
  { height: 360 }
);

export const ProgrammaticDetentExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(2);
    const [middleOnly, setMiddleOnly] = useState(true);
    const [closedOnly, setClosedOnly] = useState(false);
    const closedCode = closedOnly ? 'programmatic(0)' : '0';
    const middleCode = middleOnly ? 'programmatic(170)' : '170';

    return (
      <Example
        stage={
          <>
            <StageContent>
              <StageButton label="Open sheet" onPress={() => setIndex(2)} />
            </StageContent>
            <StageSheet
              detents={[
                closedOnly ? programmatic(0) : 0,
                middleOnly ? programmatic(170) : 170,
                320,
              ]}
              index={index}
              onIndexChange={setIndex}
              scrimColor="rgba(0, 0, 0, 0.4)"
            >
              <SheetBody title="Drag me down">
                <SheetText>
                  A drag can't stop at a programmatic detent, but setting the
                  index still reaches it.
                </SheetText>
              </SheetBody>
            </StageSheet>
          </>
        }
        controls={
          <>
            <ControlGroup>
              <Segmented
                label="index"
                options={[
                  { value: 0, label: '0' },
                  { value: 1, label: '1' },
                  { value: 2, label: '2' },
                ]}
                value={index}
                onChange={setIndex}
              />
              <Toggle
                label="Middle detent is programmatic"
                value={middleOnly}
                onChange={setMiddleOnly}
              />
              <Toggle
                label="Closed detent is programmatic"
                value={closedOnly}
                onChange={setClosedOnly}
              />
            </ControlGroup>
            <ControlGroup>
              <Hint>
                Drag down from the top: the sheet passes the middle detent
                unless you turn it off. With a programmatic closed detent,
                neither a drag nor a tap on the scrim closes the sheet.
              </Hint>
            </ControlGroup>
          </>
        }
        code={`const [index, setIndex] = useState(2);

<ModalBottomSheet
  detents={[${closedCode}, ${middleCode}, 320]}
  index={index}
  onIndexChange={setIndex}
  surface={
    <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
  }
>
  {/* ... */}
</ModalBottomSheet>;`}
      />
    );
  },
  { height: 420 }
);
