import { useState } from 'react';

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

export const FloatingCardExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(1);
    const [insets, setInsets] = useState(INITIAL_INSETS);

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
              detents={[
                0,
                {
                  value: 180,
                  insets: {
                    left: insets.side,
                    right: insets.side,
                    bottom: insets.bottom,
                  },
                },
                {
                  value: '100%',
                  insets: {
                    left: insets.topSide,
                    right: insets.topSide,
                    bottom: insets.topBottom,
                  },
                },
              ]}
              topInset={insets.top}
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
  detents={[
    0,
    { value: 180, insets: { left: ${Math.round(insets.side)}, right: ${Math.round(insets.side)}, bottom: ${Math.round(insets.bottom)} } },
    { value: '100%', insets: { left: ${Math.round(insets.topSide)}, right: ${Math.round(insets.topSide)}, bottom: ${Math.round(insets.topBottom)} } },
  ]}
  topInset={${Math.round(insets.top)}}
  surfaceStyle={{
    borderRadius: 28,
    boxShadow: '0 2 16 rgba(0, 16, 72, 0.16)',
  }}
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

    /** Marks a change made from code, as the sheet doesn't report those. */
    const setIndexFromCode = (next: number) => {
      log(`setIndex(${next}) from code`);
      setIndex(next);
    };

    return (
      <Example
        stage={
          <>
            <StageContent>
              <StageButton
                label="Open sheet"
                onPress={() => setIndexFromCode(1)}
              />
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
                  Dragging fires onIndexChange, then onSettle. Setting the index
                  from code fires only onSettle.
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
                onChange={setIndexFromCode}
              />
              <Hint>
                Drag the sheet or tap the dimmed area to see{' '}
                <code>onIndexChange</code>. Set the index here and it doesn't
                fire, because your code already knows.
              </Hint>
            </ControlGroup>
            <ControlGroup>
              <EventLog
                entries={entries}
                onClear={clear}
                empty="Drag the sheet to see its events."
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

const CARD_DETENT = 320;
const CODE_ONLY_DETENT = 170;

// Leaves room on the sheet's left for the detent markers.
const MARKED_SHEET_INSETS = { left: 100, right: 16 };

const DetentMarker = ({
  height,
  label,
  isCodeOnly,
}: {
  height: number;
  label: string;
  isCodeOnly: boolean;
}) => (
  <div
    aria-hidden
    style={{
      position: 'absolute',
      bottom: height,
      left: 0,
      right: 0,
      padding: '0 8px 2px 12px',
      borderBottom: `2px ${isCodeOnly ? 'dashed var(--example-button)' : 'solid var(--example-border)'}`,
      color: isCodeOnly ? 'var(--example-button)' : 'var(--example-text)',
      fontSize: 12,
      fontWeight: isCodeOnly ? 500 : 400,
    }}
  >
    <div style={{ maxWidth: 76 }}>{label}</div>
  </div>
);

export const ProgrammaticDetentExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(2);
    const [isCodeOnly, setIsCodeOnly] = useState(true);
    const middleCode = isCodeOnly
      ? `{ value: ${CODE_ONLY_DETENT}, programmatic: true }`
      : CODE_ONLY_DETENT;

    return (
      <Example
        stage={
          <>
            <StageContent>
              <StageButton label="Open sheet" onPress={() => setIndex(2)} />
            </StageContent>
            <DetentMarker
              height={CARD_DETENT}
              label="index 2"
              isCodeOnly={false}
            />
            <DetentMarker
              height={CODE_ONLY_DETENT}
              label={isCodeOnly ? 'index 1, code only' : 'index 1'}
              isCodeOnly={isCodeOnly}
            />
            <StageSheet
              detents={[
                0,
                {
                  value: CODE_ONLY_DETENT,
                  programmatic: isCodeOnly,
                  insets: MARKED_SHEET_INSETS,
                },
                { value: CARD_DETENT, insets: MARKED_SHEET_INSETS },
              ]}
              index={index}
              onIndexChange={setIndex}
              scrimColor="rgba(0, 0, 0, 0.25)"
            >
              <SheetBody title={index === 1 ? 'At index 1' : 'Drag me down'}>
                <StageButton
                  secondary
                  label={index === 1 ? 'Go to index 2' : 'Go to index 1'}
                  onPress={() => setIndex(index === 1 ? 2 : 1)}
                />
              </SheetBody>
            </StageSheet>
          </>
        }
        controls={
          <ControlGroup>
            <Toggle
              label="Index 1 is programmatic"
              value={isCodeOnly}
              onChange={setIsCodeOnly}
            />
            <Hint>
              1. Drag the sheet down and let go at the dashed line. It can't
              stop there, so it moves on to another detent.
            </Hint>
            <Hint>
              2. Press <strong>Go to index 1</strong>. Your code can still stop
              there.
            </Hint>
            <Hint>
              3. Turn the switch off and drag again. Now a drag stops there too.
            </Hint>
          </ControlGroup>
        }
        code={`const [index, setIndex] = useState(2);

<ModalBottomSheet
  detents={[0, ${middleCode}, ${CARD_DETENT}]}
  index={index}
  onIndexChange={setIndex}
  surface={
    <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
  }
>
  <Button title="Go to index 1" onPress={() => setIndex(1)} />
</ModalBottomSheet>;`}
      />
    );
  },
  { height: 420 }
);
