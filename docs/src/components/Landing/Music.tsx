import { useEffect, useRef, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import {
  BottomSheet,
  type Detent,
  type SurfaceStyle,
} from '@swmansion/react-native-bottom-sheet';

import {
  ATTACHED,
  Backdrop,
  Chip,
  CodeBlock,
  CodeSwap,
  DragHint,
  followSheet,
  Grabber,
  Reopen,
  Row,
  sheet,
  SHEET_SURFACE,
  SheetBody,
  SheetButton,
  Stage,
  Swap,
  TOP_INSET,
  useCaseChange,
} from './example';
import { Icon, Reveal } from './pieces';
import shared from './example.module.css';
import styles from './Music.module.css';

const TRACKS = [
  ['Paper Boats', 'Harbor Lights', '#38acdd', '4:05'],
  ['Night Ferry', 'Low Orbit', '#001a72', '2:58'],
  ['Salt & Static', 'Low Orbit', '#57b495', '3:31'],
  ['Long Exposure', 'The Quiet Hours', '#ff6259', '5:12'],
  ['Undertow', 'The Quiet Hours', '#33488e', '3:20'],
  ['Second Wind', 'Marginalia', '#782aeb', '4:18'],
  ['Lanterns', 'Marginalia', '#38acdd', '2:47'],
  ['Far Shore', 'Field Notes', '#001a72', '6:01'],
  ['Tidewater', 'Field Notes', '#57b495', '3:39'],
  ['Copper Sky', 'Nocturne', '#ff6259', '4:44'],
  ['Last Light', 'Nocturne', '#33488e', '5:27'],
] as const;

const CARD = { left: 16, right: 16, bottom: 32 };

type Preset = {
  id: string;
  label: string;
  detents: Detent[];
  labels: string[];
  shape?: SurfaceStyle;
  text: string;
  backLabel?: string;
  code: string;
};

/** The cases, with fixed heights that fit the frame so they stay in order. */
const makePresets = (frameHeight: number): Preset[] => {
  const fit = (value: number, ratio: number) =>
    Math.round(Math.min(value, (frameHeight - TOP_INSET) * ratio));
  const fixedLow = fit(400, 0.5);
  const fixedHigh = fit(680, 0.9);
  const card = fit(420, 0.55);
  const stepLow = fit(300, 0.35);
  const stepHigh = fit(600, 0.7);
  return [
    {
      id: 'content',
      label: 'Content',
      detents: [0, 'content', '100%'],
      labels: ['0', "'content'", "'100%'"],
      text: 'The sheet measures what you put in it, and follows when that changes. Add a song, then drag it up or down.',
      code: `<BottomSheet
  detents={[0, 'content', '100%']}
  index={index}
  onIndexChange={setIndex}
/>`,
    },
    {
      id: 'fixed',
      label: 'Fixed',
      detents: [0, fixedLow, fixedHigh],
      labels: ['0', `${fixedLow}`, `${fixedHigh}`],
      text: 'A number is a height in points or dp. Flick it and it skips ahead on velocity alone.',
      code: `<BottomSheet
  detents={[0, ${fixedLow}, ${fixedHigh}]}
  index={index}
  onIndexChange={setIndex}
/>`,
    },
    {
      id: 'percent',
      label: 'Percent',
      detents: ['0%', '45%', '85%'],
      labels: ["'0%'", "'45%'", "'85%'"],
      text: 'Percentages resolve against the available height, and update when it changes.',
      code: `<BottomSheet
  detents={['0%', '45%', '85%']}
  index={index}
  onIndexChange={setIndex}
/>`,
    },
    {
      id: 'card',
      label: 'Floating card',
      detents: [0, { value: card, insets: CARD }, '100%'],
      labels: ['0', `${card}`, "'100%'"],
      shape: { borderRadius: 32, borderCurve: 'continuous' },
      text: 'Insets float the sheet as a card. Drag up and the gaps blend away, with no relayout.',
      code: `<BottomSheet
  detents={[
    0,
    { value: ${card}, insets: { left: 16, right: 16, bottom: 32 } },
    '100%',
  ]}
  surfaceStyle={{ borderRadius: 32 }}
/>`,
    },
    {
      id: 'programmatic',
      label: 'Programmatic',
      detents: [0, stepLow, stepHigh, { value: '100%', programmatic: true }],
      labels: ['0', `${stepLow}`, `${stepHigh}`, "'100%' code only"],
      text: `Drag it up and it stops at ${stepHigh}. Only your code can take it further, and from there you can drag it back.`,
      backLabel: `Back to ${stepHigh}`,
      code: `<BottomSheet
  detents={[
    0,
    ${stepLow},
    ${stepHigh},
    { value: '100%', programmatic: true },
  ]}
/>`,
    },
  ];
};

const resolveTick = (
  detent: Detent,
  height: number,
  measured: Record<number, number>,
  i: number
) => {
  const value = typeof detent === 'object' ? detent.value : detent;
  const bottom = typeof detent === 'object' ? (detent.insets?.bottom ?? 0) : 0;
  if (value === 'content') return measured[i] ?? null;
  if (typeof value === 'number') return value === 0 ? 0 : value + bottom;
  return (parseFloat(value) / 100) * (height - TOP_INSET - bottom) + bottom;
};

const MusicBackdrop = () => (
  <Backdrop className={styles.backdropMusic}>
    <div className={shared.appBar}>
      <Icon d="M6 9l6 6 6-6" />
      <div className={styles.appBarTitle}>
        <small>Playing from album</small>
        Harbor Lights
      </div>
      <Icon d="M5 12h.01M12 12h.01M19 12h.01" strokeWidth={3} />
    </div>
    <div className={styles.album} data-follow>
      <span>Harbor</span>
      <span>Lights</span>
      <svg viewBox="0 0 300 80" preserveAspectRatio="none">
        <path
          d="M0 50c40-30 70 20 110 0s70-30 110-6 50 12 80-4v40H0z"
          fill="rgb(252 252 255 / 0.2)"
        />
        <path
          d="M0 62c50-22 80 16 130 0s80-14 170 0v18H0z"
          fill="rgb(252 252 255 / 0.25)"
        />
      </svg>
    </div>
    <div className={styles.nowPlaying}>
      <div>
        <b>Slow Tide</b>
        <span>Harbor Lights</span>
      </div>
      <Icon
        d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"
        size={24}
        fill="currentColor"
      />
    </div>
    <div className={styles.trackBar}>
      <i />
    </div>
    <div className={styles.trackTimes}>
      <span>1:24</span>
      <span>3:42</span>
    </div>
    <div className={styles.transport}>
      <Icon d="M4 7h3l10 10h3M4 17h3l3-3M14 10l3-3h3M18 4l3 3-3 3M18 14l3 3-3 3" />
      <Icon d="M19 5v14L8 12zM6 5v14" size={30} fill="currentColor" />
      <div className={styles.transportMain}>
        <Icon d="M8 5h3v14H8zM14 5h3v14h-3z" size={28} fill="currentColor" />
      </div>
      <Icon d="M5 5v14l11-7zM18 5v14" size={30} fill="currentColor" />
      <Icon d="M4 11V9a3 3 0 0 1 3-3h13M17 3l3 3-3 3M20 13v2a3 3 0 0 1-3 3H4M7 21l-3-3 3-3" />
    </div>
  </Backdrop>
);

export const DetentsSlide = () => {
  // A detent's height springs by itself. Its shape can't, so a floating card
  // closes, swaps, and opens rather than jumping.
  const { wanted, shown, index, setIndex, request, onSettle } = useCaseChange(
    'content',
    (from, to) => (from === 'card') !== (to === 'card')
  );
  const [rows, setRows] = useState(2);
  const [dragged, setDragged] = useState(false);
  const [height, setHeight] = useState(0);
  const [measured, setMeasured] = useState<Record<number, number>>({});
  const screen = useRef<HTMLDivElement>(null);
  const lastPosition = useRef(0);
  const presets = makePresets(height || 800);
  const preset = presets.find((item) => item.id === shown)!;

  useEffect(() => {
    const element = screen.current!;
    const observer = new ResizeObserver(() => setHeight(element.offsetHeight));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const choose = (next: Preset) => {
    setMeasured({});
    request(next.id);
  };

  const rail = height
    ? preset.detents.map((detent, i) => {
        const position = resolveTick(detent, height, measured, i);
        if (position === null) return null;
        return (
          <div
            key={i}
            className={shared.tick}
            data-active={index === i}
            style={{ transform: `translateY(${-position}px)` }}
          >
            {preset.labels[i]}
          </div>
        );
      })
    : null;

  return (
    <section className={shared.chapter} data-compact>
      <Reveal className={shared.chapterCopy} data-copy>
        <span className={shared.kicker}>01 — detents</span>
        <h2 className={shared.title}>Stops where you mean it to.</h2>
        <Swap
          active={wanted}
          items={presets.map((item) => [
            item.id,
            <p key={item.id} className={shared.body}>
              {item.text}
            </p>,
          ])}
        />
        <div className={shared.chips} role="group" aria-label="Detent examples">
          {presets.map((item) => (
            <Chip
              key={item.id}
              active={item.id === wanted}
              onClick={() => choose(item)}
            >
              {item.label}
            </Chip>
          ))}
        </div>
        <span className={shared.chipsLabel}>index</span>
        <div className={shared.chips} role="group" aria-label="Snap to detent">
          {preset.detents.map((_, i) => (
            <Chip key={i} mono active={index === i} onClick={() => setIndex(i)}>
              {i}
            </Chip>
          ))}
        </div>
        <CodeBlock docs="detents-and-index">
          <CodeSwap
            active={wanted}
            items={presets.map((item) => [item.id, item.code])}
          />
        </CodeBlock>
      </Reveal>
      <Stage backdrop={<MusicBackdrop />} rail={rail} stageRef={screen}>
        <Reopen visible={index === 0} onPress={() => setIndex(1)}>
          Show the queue
        </Reopen>
        <BottomSheet
          detents={preset.detents}
          topInset={TOP_INSET}
          index={index}
          onIndexChange={(next) => {
            setIndex(next);
            setDragged(true);
          }}
          surface={SHEET_SURFACE}
          surfaceStyle={preset.shape ?? ATTACHED}
          onPositionChange={(event) => {
            lastPosition.current = event.nativeEvent.position;
            followSheet(screen.current, event.nativeEvent.position);
          }}
          onSettle={(settled) => {
            setMeasured((previous) => ({
              ...previous,
              [settled]: lastPosition.current,
            }));
            onSettle(settled);
          }}
        >
          <SheetBody fill={preset.id !== 'content'}>
            <DragHint seen={dragged} />
            <Grabber />
            <View style={sheet.head}>
              <Text style={sheet.title}>Up next</Text>
              <Text style={sheet.subtitle}>
                {TRACKS.length} songs in your queue
              </Text>
            </View>
            {preset.id === 'content' ? (
              <div key={preset.id} className={styles.caseFade}>
                {TRACKS.slice(0, rows).map((track) => (
                  <Row key={track[0]} item={track} trailing={track[3]} />
                ))}
                <View style={sheet.actions}>
                  <SheetButton
                    onPress={() => setRows((n) => Math.min(n + 1, 8))}
                  >
                    Add a song
                  </SheetButton>
                  <SheetButton
                    onPress={() => setRows((n) => Math.max(n - 1, 1))}
                  >
                    Remove
                  </SheetButton>
                </View>
              </div>
            ) : (
              <div key={preset.id} className={styles.caseFade}>
                {preset.id === 'programmatic' ? (
                  <View style={sheet.actions}>
                    <SheetButton onPress={() => setIndex(index === 3 ? 2 : 3)}>
                      {index === 3 ? preset.backLabel! : 'Expand from code'}
                    </SheetButton>
                  </View>
                ) : null}
                <ScrollView
                  style={sheet.fill}
                  contentContainerStyle={sheet.listPad}
                >
                  {TRACKS.map((track) => (
                    <Row key={track[0]} item={track} trailing={track[3]} />
                  ))}
                </ScrollView>
              </div>
            )}
          </SheetBody>
        </BottomSheet>
      </Stage>
    </section>
  );
};
