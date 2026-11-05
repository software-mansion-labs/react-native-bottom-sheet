import { useEffect, useState } from 'react';

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

const SCRIM_COLORS = [
  { label: 'Black', value: 'rgba(0, 0, 0, 0.5)', swatch: '#000000' },
  { label: 'Navy', value: 'rgba(0, 26, 114, 0.5)', swatch: '#001a72' },
  { label: 'Purple', value: 'rgba(120, 42, 235, 0.5)', swatch: '#782aeb' },
];

export const ScrimExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(1);
    const [scrimColor, setScrimColor] = useState(SCRIM_COLORS[0]!.value);
    const [middle, setMiddle] = useState(0.5);
    const [top, setTop] = useState(1);

    return (
      <Example
        stage={
          <>
            <StageContent hidden={index > 0}>
              <StageButton label="Open sheet" onPress={() => setIndex(1)} />
            </StageContent>
            <StageSheet
              detents={[0, 140, 'content']}
              index={index}
              onIndexChange={setIndex}
              scrimColor={scrimColor}
              scrimOpacities={[0, middle, top]}
            >
              <SheetBody title="Drag between detents">
                <SheetText>
                  The scrim interpolates between the opacities you set for each
                  detent. Tap it to close the sheet.
                </SheetText>
                <SheetText>
                  Keep dragging up to reach the content detent, which is as tall
                  as everything in this sheet.
                </SheetText>
                <SheetText>
                  Change the opacities and the color while the sheet is open to
                  see how they blend with what's behind it.
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
              <Segmented
                label="scrimColor"
                options={SCRIM_COLORS}
                value={scrimColor}
                onChange={setScrimColor}
              />
            </ControlGroup>
            <ControlGroup>
              <Slider
                label="Opacity at index 1"
                value={middle}
                onChange={setMiddle}
              />
              <Slider
                label="Opacity at index 2"
                value={top}
                onChange={setTop}
              />
            </ControlGroup>
          </>
        }
        code={`const [index, setIndex] = useState(1);

<ModalBottomSheet
  detents={[0, 140, 'content']}
  index={index}
  onIndexChange={setIndex}
  scrimColor="${scrimColor}"
  scrimOpacities={[0, ${middle}, ${top}]}
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

const ExpensiveContent = ({ onLog }: { onLog: (message: string) => void }) => {
  const [count, setCount] = useState(0);

  useEffect(() => {
    onLog('<ExpensiveContent /> mounted');
    return () => onLog('<ExpensiveContent /> unmounted');
  }, [onLog]);

  return (
    <>
      <SheetText>This counter is state inside the sheet's content.</SheetText>
      <StageButton
        secondary
        label={`Tapped ${count} ${count === 1 ? 'time' : 'times'}`}
        onPress={() => setCount((current) => current + 1)}
      />
    </>
  );
};

export const LazyExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(1);
    const [lazy, setLazy] = useState(true);
    const { entries, log, clear } = useEventLog();

    return (
      <Example
        stage={
          <>
            <StageContent hidden={index > 0}>
              <StageButton label="Open sheet" onPress={() => setIndex(1)} />
            </StageContent>
            <StageSheet
              lazy={lazy}
              index={index}
              onIndexChange={setIndex}
              onSettle={(settled) => log(`onSettle(${settled})`)}
              scrimColor="rgba(0, 0, 0, 0.4)"
            >
              <SheetBody title="Sheet content">
                <ExpensiveContent onLog={log} />
              </SheetBody>
            </StageSheet>
          </>
        }
        controls={
          <>
            <ControlGroup>
              <Toggle label="lazy" value={lazy} onChange={setLazy} />
              <Hint>
                Tap the counter, then close and reopen the sheet. With{' '}
                <code>lazy</code>, its state resets, and the content unmounts
                only once the sheet settles.
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

<ModalBottomSheet${lazy ? '\n  lazy' : ''}
  index={index}
  onIndexChange={setIndex}
  surface={
    <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
  }
>
  <ExpensiveContent />
</ModalBottomSheet>;`}
      />
    );
  },
  { height: 320 }
);

const STACKED_SHEETS = [
  { name: 'A', height: 340, color: 'var(--example-tint-blue)' },
  { name: 'B', height: 240, color: 'var(--example-tint-green)' },
  { name: 'C', height: 140, color: 'var(--example-tint-purple)' },
];

export const StackingExample = interactiveExample(
  () => {
    // Most recently opened last, which is also the order the sheets stack in.
    const [openOrder, setOpenOrder] = useState<string[]>(['A', 'B']);

    const setOpen = (name: string, open: boolean) =>
      setOpenOrder((order) => {
        const others = order.filter((other) => other !== name);
        return open ? [...others, name] : others;
      });

    return (
      <Example
        stage={
          <>
            <StageContent hidden={openOrder.length > 0}>
              <StageButton
                label="Open sheet A"
                onPress={() => setOpen('A', true)}
              />
            </StageContent>
            {STACKED_SHEETS.map(({ name, height, color }, position) => (
              <StageSheet
                key={name}
                detents={[0, height]}
                index={openOrder.includes(name) ? 1 : 0}
                onIndexChange={(next) => setOpen(name, next === 1)}
                scrimColor="rgba(0, 0, 0, 0.25)"
                surfaceColor={color}
              >
                <SheetBody title={`Sheet ${name}`}>
                  {position < STACKED_SHEETS.length - 1 && (
                    <StageButton
                      secondary
                      label={`Open sheet ${STACKED_SHEETS[position + 1]!.name}`}
                      onPress={() =>
                        setOpen(STACKED_SHEETS[position + 1]!.name, true)
                      }
                    />
                  )}
                </SheetBody>
              </StageSheet>
            ))}
          </>
        }
        controls={
          <>
            <ControlGroup>
              {STACKED_SHEETS.map(({ name }) => (
                <Toggle
                  key={name}
                  label={`Sheet ${name}`}
                  value={openOrder.includes(name)}
                  onChange={(open) => setOpen(name, open)}
                />
              ))}
            </ControlGroup>
            <ControlGroup>
              <Hint>
                The tree always renders A, B, C, but the last sheet you opened
                is on top. Open C and then A, and the taller A covers it.
              </Hint>
            </ControlGroup>
          </>
        }
        code={`// The tree order doesn't matter: the most recently opened sheet is on top.
<>
  <ModalBottomSheet detents={[0, 340]} index={a} onIndexChange={setA}>
    {/* Sheet A */}
  </ModalBottomSheet>
  <ModalBottomSheet detents={[0, 240]} index={b} onIndexChange={setB}>
    {/* Sheet B */}
  </ModalBottomSheet>
  <ModalBottomSheet detents={[0, 140]} index={c} onIndexChange={setC}>
    {/* Sheet C */}
  </ModalBottomSheet>
</>;`}
      />
    );
  },
  { height: 420 }
);

export const CloseRequestExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(1);
    const [closes, setCloses] = useState(true);
    const { entries, log, clear } = useEventLog();

    return (
      <Example
        stage={
          <>
            <StageContent hidden={index > 0}>
              <StageButton label="Open sheet" onPress={() => setIndex(1)} />
            </StageContent>
            <StageSheet
              detents={[0, 170]}
              index={index}
              onIndexChange={setIndex}
              onCloseRequest={() => {
                log('onCloseRequest()');
                if (closes) setIndex(0);
              }}
              onSettle={(settled) => log(`onSettle(${settled})`)}
              scrimColor="rgba(0, 0, 0, 0.4)"
            >
              <SheetBody title="Press Escape">
                <SheetText>
                  The sheet only reports the request. Your handler decides
                  whether it closes.
                </SheetText>
              </SheetBody>
            </StageSheet>
          </>
        }
        controls={
          <>
            <ControlGroup>
              <Toggle
                label="Handler closes the sheet"
                value={closes}
                onChange={setCloses}
              />
              <Hint>
                Press <kbd>Esc</kbd>. On Android, the system Back button sends
                the same request.
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
  detents={[0, 170]}
  index={index}
  onIndexChange={setIndex}
  onCloseRequest={() => {${closes ? '\n    setIndex(0);' : '\n    // Keep the sheet open.'}
  }}
  surface={
    <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
  }
>
  {/* ... */}
</ModalBottomSheet>;`}
      />
    );
  },
  { height: 320 }
);
