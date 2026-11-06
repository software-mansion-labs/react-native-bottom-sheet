import {
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type RefObject,
} from 'react';
import { ScrollView, Text, View } from 'react-native';
import {
  BottomSheet,
  type ScrollCoordinationMode,
} from '@swmansion/react-native-bottom-sheet';
import clsx from 'clsx';

import { Chip, Code, CodeBlock } from '../../shared/copy';
import {
  ATTACHED,
  DragHint,
  Grabber,
  PLAIN_SHEET_SURFACE,
  Row,
  sheet,
  SheetBody,
} from '../../shared/sheet';
import {
  Backdrop,
  restStops,
  Stage,
  TOP_INSET,
  useRestUnderContent,
} from '../../shared/stage';
import { hasTouchGestures } from '../../../components/InteractiveExample/controls';
import {
  COLLAPSE_HINTS,
  EXPAND_HINTS,
  FingerIndicator,
  MOBILE_PREVIEW_HINT,
  useListMouseDrag,
} from '../../../components/InteractiveExample/coordinationPreview';
import { clamp, Icon, Reveal, SlideActiveContext } from '../../shared/pieces';
import landing from '../../landing.module.css';
import shared from '../../shared/example.module.css';
import styles from './styles.module.css';

const COMMENT_LINES = [
  [
    'Mika',
    '2h',
    'The green at 2:14 is unreal. Had to pause it.',
    '#3fc684',
    '812',
  ],
  ['Jonas', '5h', 'How long did you wait for this shot?', '#38acdd', '96'],
  ['Aiko', '6h', 'Watching this on repeat tonight.', '#782aeb', '241'],
  ['Remy', '9h', 'Which lens was this filmed on?', '#ff6259', '37'],
  ['Sol', '1d', 'Saving this for the next trip north.', '#33488e', '58'],
  ['Noor', '1d', 'The silence makes it even better.', '#001a72', '430'],
  [
    'Teo',
    '1d',
    'I saw this exact sky last winter. Nothing prepares you.',
    '#57b495',
    '127',
  ],
  ['Ines', '2d', 'More of these, please.', '#782aeb', '74'],
] as const;

const COMMENTS = Array.from({ length: 24 }, (_, i) => {
  const [name, age, line, color, likes] =
    COMMENT_LINES[i % COMMENT_LINES.length]!;
  return {
    key: `comment-${i}`,
    item: [`${name} · ${age}`, line, color] as const,
    likes,
  };
});

const THUMB =
  'M7 11v9H4v-9zM7 11l4-8a2 2 0 0 1 2 2v4h6a1 1 0 0 1 1 1.2l-1.4 8a1 1 0 0 1-1 .8H7';

const VideoBackdrop = () => (
  <Backdrop className={styles.backdropVideo}>
    <div className={styles.player}>
      <svg viewBox="0 0 500 280" preserveAspectRatio="xMidYMid slice">
        <defs>
          <linearGradient id="aurora" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#0a2688" />
            <stop offset="0.55" stopColor="#3fc684" />
            <stop offset="1" stopColor="#6fcef5" />
          </linearGradient>
        </defs>
        <rect width="500" height="280" fill="#122154" />
        <path
          d="M-20 200C90 80 170 240 290 120S450 50 530 100v180H-20z"
          fill="url(#aurora)"
          opacity=".85"
        />
        <path
          d="M-10 280V240l70-46 60 34 80-66 70 58 60-30 90 50 90-40v80z"
          fill="#1b2445"
        />
      </svg>
      <div className={styles.playerControls}>
        <Icon d="M8 5h3v14H8zM14 5h3v14h-3z" size={34} fill="currentColor" />
        <div className={styles.playerBottom}>
          <span>2:14 / 8:03</span>
          <Icon d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" size={18} />
        </div>
      </div>
      <div className={styles.playerBar}>
        <i />
      </div>
    </div>
    <div className={styles.videoMeta}>
      <b>Northern lights, in real time</b>
      <span>128K views · 3 days ago</span>
    </div>
    <div className={styles.channel}>
      <i>F</i>
      <div>
        <b>Field Notes</b>
        <span>84K subscribers</span>
      </div>
      <em>Follow</em>
    </div>
    <div className={styles.videoActions}>
      <span>
        <Icon d={THUMB} size={16} />
        12K
      </span>
      <span>
        <Icon
          d="M12 15V4M8 8l4-4 4 4M5 14v5a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-5"
          size={16}
        />
        Share
      </span>
      <span>
        <Icon d="M6 4h12v16l-6-4-6 4z" size={16} />
        Save
      </span>
    </div>
  </Backdrop>
);

const MODES: ScrollCoordinationMode[] = ['none', 'initial', 'handoff'];
const TRACE_SECONDS = 6;

type Sample = { sheet: number; list: number };
type Owner = 'idle' | 'sheet' | 'list';

/** Draws the two values live, and only around real movement. */
const useTrace = (
  canvasRef: RefObject<HTMLCanvasElement | null>,
  sampleRef: RefObject<Sample>,
  onOwner: (owner: Owner) => void,
  active: boolean
) => {
  useEffect(() => {
    if (!active) return undefined;
    const canvas = canvasRef.current!;
    const context = canvas.getContext('2d')!;
    const points: (Sample & { t: number })[] = [];
    let frame = 0;
    let visible = false;
    let lastActive = 0;
    let clock = 0;
    let previousTime = performance.now();
    let previous = { ...sampleRef.current };
    let owner: Owner = 'idle';

    const draw = () => {
      const scale = window.devicePixelRatio || 1;
      const width = canvas.clientWidth;
      const height = canvas.clientHeight;
      if (canvas.width !== width * scale) canvas.width = width * scale;
      if (canvas.height !== height * scale) canvas.height = height * scale;
      context.setTransform(scale, 0, 0, scale, 0, 0);
      context.clearRect(0, 0, width, height);

      context.strokeStyle = '#c1c6e5';
      context.setLineDash([3, 4]);
      context.beginPath();
      context.moveTo(0, height / 2);
      context.lineTo(width, height / 2);
      context.stroke();
      context.setLineDash([]);

      const line = (
        key: keyof Sample,
        color: string,
        top: number,
        laneHeight: number
      ) => {
        context.beginPath();
        context.strokeStyle = color;
        context.lineWidth = 2;
        context.lineJoin = 'round';
        points.forEach((point, i) => {
          const x = width - ((clock - point.t) / TRACE_SECONDS) * width;
          const y = top + (1 - point[key]) * laneHeight;
          if (i === 0) context.moveTo(x, y);
          else context.lineTo(x, y);
        });
        context.stroke();
      };
      line('sheet', '#001a72', 10, height / 2 - 20);
      line('list', '#38acdd', height / 2 + 10, height / 2 - 20);
    };

    const tick = (time: number) => {
      frame = requestAnimationFrame(tick);
      const delta = (time - previousTime) / 1000;
      previousTime = time;
      const sample = sampleRef.current;
      const sheetMoved = Math.abs(sample.sheet - previous.sheet) > 0.0005;
      const listMoved = Math.abs(sample.list - previous.list) > 0.0005;
      previous = { ...sample };
      if (sheetMoved || listMoved) lastActive = time;

      let nextOwner = owner;
      if (sheetMoved) nextOwner = 'sheet';
      else if (listMoved) nextOwner = 'list';
      else if (time - lastActive > 200) nextOwner = 'idle';
      if (nextOwner !== owner) {
        owner = nextOwner;
        onOwner(owner);
      }

      // The trace only advances around real movement, so an idle page is still.
      if (!visible || time - lastActive > 900) return;
      clock += delta;
      points.push({ t: clock, sheet: sample.sheet, list: sample.list });
      while (points.length > 1 && clock - points[0]!.t > TRACE_SECONDS + 0.2) {
        points.shift();
      }
      draw();
    };

    const observer = new IntersectionObserver(([entry]) => {
      visible = entry!.isIntersecting;
    });
    observer.observe(canvas);
    draw();
    frame = requestAnimationFrame(tick);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, [canvasRef, sampleRef, onOwner, active]);
};

const OWNER_LABELS: Record<Owner, string> = {
  sheet: 'sheet owns the gesture',
  list: 'list owns the gesture',
  idle: 'waiting for a drag',
};

export const CoordinationSlide = () => {
  const [expand, setExpand] = useState<ScrollCoordinationMode>('handoff');
  const [collapse, setCollapse] = useState<ScrollCoordinationMode>('initial');
  const [index, setIndex] = useState(0);
  const [dragged, setDragged] = useState(false);
  const stage = useRef<HTMLDivElement>(null);
  const rest = useRestUnderContent(stage, 400);
  const canvas = useRef<HTMLCanvasElement>(null);
  const ownerRef = useRef<HTMLSpanElement>(null);
  const sample = useRef<Sample>({ sheet: 0, list: 0 });
  const onOwner = useCallback((owner: Owner) => {
    if (!ownerRef.current) return;
    ownerRef.current.dataset.owner = owner;
    ownerRef.current.textContent = OWNER_LABELS[owner];
  }, []);
  useTrace(canvas, sample, onOwner, useContext(SlideActiveContext));
  const [isTouchDevice] = useState(hasTouchGestures);
  const listParentRef = useListMouseDrag({
    isEnabled: !isTouchDevice,
    canExpandSheet: index === 0,
    canCollapseSheet: index === 1,
    expand,
    collapse,
  });

  return (
    <section className={shared.chapter} data-flip data-compact>
      <Reveal className={shared.chapterCopy} data-copy>
        <span className={shared.kicker}>02 — scroll coordination</span>
        <h2 className={shared.title}>One gesture, two owners, no wrappers.</h2>
        <p className={clsx(shared.body, styles.intro)}>
          Put an ordinary <code>ScrollView</code> or <code>FlatList</code> in
          the sheet. Drag up on the list and watch the gesture pass from one
          line to the other, without lifting your finger.
        </p>
        <div className={clsx(landing.lightTokens, styles.trace)}>
          <canvas
            ref={canvas}
            aria-label="Live trace of sheet position and list offset"
            role="img"
          />
          <div className={styles.traceLegend}>
            <span>
              <i style={{ color: '#001a72' }} />
              sheet position
            </span>
            <span>
              <i style={{ color: '#38acdd' }} />
              list offset
            </span>
            <span className={styles.owner} ref={ownerRef} data-owner="idle">
              {OWNER_LABELS.idle}
            </span>
          </div>
        </div>
        <div className={shared.modes}>
          {(
            [
              ['expand', expand, setExpand, EXPAND_HINTS],
              ['collapse', collapse, setCollapse, COLLAPSE_HINTS],
            ] as const
          ).map(([name, value, setValue, hints]) => (
            <div key={name} className={styles.mode}>
              <span className={shared.chipsLabel}>{name}</span>
              <div
                className={shared.chips}
                role="group"
                aria-label={`${name} mode`}
              >
                {MODES.map((mode) => (
                  <Chip
                    key={mode}
                    mono
                    active={mode === value}
                    onClick={() => setValue(mode)}
                  >
                    {mode}
                  </Chip>
                ))}
              </div>
              <p className={styles.modeHint}>{hints[value]}</p>
            </div>
          ))}
        </div>
        {!isTouchDevice && (
          <p className={styles.preview}>{MOBILE_PREVIEW_HINT}</p>
        )}
        <CodeBlock docs="scroll-coordination">
          <Code>{`<BottomSheet
  scrollCoordination={{ expand: '${expand}', collapse: '${collapse}' }}
/>`}</Code>
        </CodeBlock>
      </Reveal>
      <Stage backdrop={<VideoBackdrop />} stageRef={stage}>
        <BottomSheet
          // Resting under the video's content, or all the way open.
          detents={restStops(rest).slice(1)}
          topInset={TOP_INSET}
          animateIn={false}
          index={index}
          onIndexChange={(next) => {
            setIndex(next);
            setDragged(true);
          }}
          surface={PLAIN_SHEET_SURFACE}
          surfaceStyle={ATTACHED}
          scrollCoordination={{ expand, collapse }}
          onPositionChange={(event) => {
            sample.current = {
              ...sample.current,
              sheet: clamp(event.nativeEvent.index),
            };
          }}
        >
          <SheetBody fill isGrabbable={false}>
            <DragHint seen={dragged} />
            <Grabber />
            <View style={sheet.head}>
              <Text style={sheet.title}>Comments</Text>
              <Text style={sheet.subtitle}>
                1,204 comments. Drag right on the list.
              </Text>
            </View>
            <div ref={listParentRef} className={styles.listParent}>
              <ScrollView
                style={sheet.fill}
                contentContainerStyle={sheet.listPad}
                scrollEventThrottle={16}
                onScroll={(event) => {
                  const { contentOffset, contentSize, layoutMeasurement } =
                    event.nativeEvent;
                  const range = Math.max(
                    1,
                    contentSize.height - layoutMeasurement.height
                  );
                  sample.current = {
                    ...sample.current,
                    list: clamp(contentOffset.y / range),
                  };
                }}
              >
                {COMMENTS.map(({ key, item, likes }) => (
                  <Row
                    key={key}
                    item={item}
                    round
                    letter
                    trailing={
                      <div className={styles.likes}>
                        <Icon d={THUMB} size={14} />
                        {likes}
                      </div>
                    }
                  />
                ))}
              </ScrollView>
            </div>
          </SheetBody>
        </BottomSheet>
        {!isTouchDevice && <FingerIndicator />}
      </Stage>
    </section>
  );
};
