import {
  Component,
  Fragment,
  useEffect,
  useRef,
  useState,
  type ReactNode,
  type RefObject,
} from 'react';
import Link from '@docusaurus/Link';
import {
  Pressable,
  StyleSheet,
  Text,
  View,
  type ViewStyle,
} from 'react-native';
import type { Detent } from '@swmansion/react-native-bottom-sheet';
import clsx from 'clsx';

import { Arrow, clamp, useWhenIdle } from './pieces';
import landing from './landing.module.css';
import styles from './example.module.css';

const FONT = 'var(--ifm-font-family-base)';
const INK = '#001a72';
export const TOP_INSET = 24;

const KEYWORDS = /^(const|return|import|from|true|false)$/;
const TOKENS =
  /(\/\/.*|\{\/\*.*?\*\/\}|'[^']*'|<\/?[A-Z][\w.]*|\/>|\b\d+(?:\.\d+)?\b|\b[a-z]+\b)/g;

/** Just enough JSX highlighting for the snippets on this page. */
const highlight = (code: string) =>
  code.split(TOKENS).map((part, i) => {
    if (i % 2 === 0) return part;
    let kind: string | undefined;
    if (part.startsWith('//') || part.startsWith('{/*')) kind = styles.tC;
    else if (part.startsWith("'")) kind = styles.tS;
    else if (part.startsWith('<') || part === '/>') kind = styles.tJ;
    else if (/^\d/.test(part)) kind = styles.tN;
    else if (KEYWORDS.test(part)) kind = styles.tK;
    return kind ? (
      <span key={i} className={kind}>
        {part}
      </span>
    ) : (
      part
    );
  });

export const Code = ({ children }: { children: string }) => (
  <div className={styles.code}>
    <pre key={children}>{highlight(children)}</pre>
  </div>
);

/**
 * Every variant in one slot, as tall as the tallest. Switching crossfades them,
 * and nothing around the slot moves.
 */
export const Swap = ({
  active,
  items,
}: {
  active: string;
  items: [string, ReactNode][];
}) => (
  <div className={styles.swap}>
    {items.map(([id, node]) => (
      <div
        key={id}
        className={styles.swapItem}
        data-on={id === active}
        aria-hidden={id !== active}
      >
        {node}
      </div>
    ))}
  </div>
);

export const CodeSwap = ({
  active,
  items,
}: {
  active: string;
  items: [string, string][];
}) => (
  <div className={styles.code}>
    <Swap
      active={active}
      items={items.map(([id, code]) => [
        id,
        <pre key={id}>{highlight(code)}</pre>,
      ])}
    />
  </div>
);

export const DocsButton = ({ page }: { page: string }) => (
  <Link className={clsx(styles.docsButton, landing.press)} to={`/${page}`}>
    Check docs <Arrow />
  </Link>
);

/**
 * Code beside an example, and the way into the docs. The code is always shown on
 * wide screens; on small ones it sits behind a toggle, so the options and the
 * example fit on one screen.
 */
export const CodeBlock = ({
  docs,
  children,
}: {
  docs: string;
  children: ReactNode;
}) => {
  const [open, setOpen] = useState(false);
  return (
    <div className={styles.codeBlock} data-open={open}>
      <div className={styles.codeActions}>
        <button
          className={clsx(styles.codeToggle, landing.press)}
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden
          >
            <path d="M5.5 4.5 2 8l3.5 3.5M10.5 4.5 14 8l-3.5 3.5" />
          </svg>
          {open ? 'Hide code' : 'Show code'}
        </button>
        <DocsButton page={docs} />
      </div>
      <div className={styles.codeCollapse}>
        <div className={styles.codeCollapseInner}>{children}</div>
      </div>
    </div>
  );
};

export const Chip = ({
  active,
  mono,
  onClick,
  children,
}: {
  active: boolean;
  mono?: boolean;
  onClick: () => void;
  children: ReactNode;
}) => (
  <button
    className={clsx(styles.chip, landing.press, mono && styles.chipMono)}
    aria-pressed={active}
    onClick={onClick}
  >
    {children}
  </button>
);

export const Backdrop = ({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) => (
  <div className={clsx(styles.backdrop, className)} data-backdrop aria-hidden>
    {children}
  </div>
);

export const Grabber = () => (
  <View style={sheet.grabberWrap}>
    <View style={sheet.grabber} />
  </View>
);

export const Row = ({
  item: [name, meta, color],
  round,
  letter,
  trailing,
}: {
  item: readonly [string, string, string, ...unknown[]];
  round?: boolean;
  letter?: boolean;
  trailing: ReactNode;
}) => (
  <View style={sheet.row}>
    <View
      style={[sheet.tile, round && sheet.tileRound, { backgroundColor: color }]}
    >
      {letter ? <Text style={sheet.tileText}>{name[0]}</Text> : null}
    </View>
    <View style={sheet.rowText}>
      <Text style={sheet.rowTitle}>{name}</Text>
      <Text style={sheet.rowMeta}>{meta}</Text>
    </View>
    {typeof trailing === 'string' ? (
      <Text style={sheet.rowTrailing}>{trailing}</Text>
    ) : (
      trailing
    )}
  </View>
);

export const SHEET_SURFACE = (
  <div
    className={landing.grab}
    style={{
      position: 'absolute',
      inset: 0,
      background: 'var(--swm-white)',
    }}
  />
);

export const ATTACHED = {
  borderTopLeftRadius: 28,
  borderTopRightRadius: 28,
  boxShadow: '0 -6 28 rgba(0, 26, 114, 0.28)',
};

export const SheetButton = ({
  onPress,
  children,
}: {
  onPress: () => void;
  children: string;
}) => (
  <Pressable
    onPress={onPress}
    style={({ pressed }) => [
      sheet.button,
      pressed && { transform: [{ scale: 0.97 }] },
    ]}
  >
    <Text style={sheet.buttonText}>{children}</Text>
  </Pressable>
);

/** All of a sheet is a drag target, so all of it says so. */
export const SheetBody = ({
  fill,
  children,
}: {
  fill?: boolean;
  children: ReactNode;
}) => (
  <div
    className={clsx(
      landing.grab,
      styles.sheetBody,
      fill && styles.sheetBodyFill
    )}
  >
    {children}
  </div>
);

/** Shown on a demo until its first drag. */
export const DragHint = ({ seen }: { seen: boolean }) => (
  <div className={styles.dragHint} data-seen={seen} aria-hidden>
    <svg
      width="12"
      height="14"
      viewBox="0 0 12 14"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
    >
      <path d="M6 1v12M2.5 4.5 6 1l3.5 3.5M2.5 9.5 6 13l3.5-3.5" />
    </svg>
    Drag
  </div>
);

/** A closed sheet has nothing left to grab, so offer the way back. */
export const Reopen = ({
  visible,
  onPress,
  children,
}: {
  visible: boolean;
  onPress: () => void;
  children: string;
}) => (
  <button
    className={clsx(styles.reopen, landing.press)}
    data-visible={visible}
    tabIndex={visible ? 0 : -1}
    onClick={onPress}
  >
    {children}
  </button>
);

/**
 * Switches an example's case through the sheet's public props alone. Where the
 * sheet can't simply spring there, it closes first, swaps, and opens again.
 */
export const useCaseChange = (
  initial: string,
  mustClose: (from: string, to: string) => boolean
) => {
  const [wanted, setWanted] = useState(initial);
  const [shown, setShown] = useState(initial);
  const [index, setIndex] = useState(1);
  const closing = useRef(false);
  const latest = useRef(initial);
  const fallback = useRef<ReturnType<typeof setTimeout>>(undefined);
  const showLatest = () => {
    closing.current = false;
    clearTimeout(fallback.current);
    setShown(latest.current);
    setIndex(1);
  };
  const request = (next: string) => {
    latest.current = next;
    setWanted(next);
    if (closing.current) return;
    if (next === shown) {
      setIndex(1);
    } else if (index === 0 || !mustClose(shown, next)) {
      setShown(next);
      setIndex(1);
    } else {
      closing.current = true;
      setIndex(0);
      // In case the close never settles, such as in a background tab.
      fallback.current = setTimeout(showLatest, 900);
    }
  };
  const onSettle = (settled: number) => {
    if (closing.current && settled === 0) showLatest();
  };
  return { wanted, shown, index, setIndex, request, onSettle };
};

const MIN_REST = 160;
const REST_GAP = 14;

/** Lets an app's own screen respond to its sheet, as real apps do. */
export const followSheet = (stage: HTMLElement | null, position: number) => {
  if (!stage) return;
  // Only once the sheet rises past where it rests, so a resting screen is intact.
  const t = clamp((position / stage.offsetHeight - 0.58) / 0.34);
  stage.querySelectorAll<HTMLElement>('[data-follow]').forEach((element) => {
    element.style.transform = `scale(${1 - 0.2 * t})`;
    element.style.opacity = `${1 - t}`;
  });
};

/**
 * Where a sheet should rest: directly under its app's content, as in a real app,
 * however tall the frame is. Layout offsets, so a receding slide doesn't skew it.
 */
export const useRestUnderContent = (
  stageRef: RefObject<HTMLDivElement | null>,
  fallback: number
) => {
  const [measure, setMeasure] = useState({ rest: fallback, frame: 0 });
  useEffect(() => {
    const stage = stageRef.current!;
    const update = () => {
      const children = stage.querySelector('[data-backdrop]')?.children;
      if (!children) return;
      // A short frame hides some of a screen's parts, which have no layout.
      const contentEnd = Math.max(
        ...[...children]
          .filter(
            (child): child is HTMLElement =>
              child instanceof HTMLElement && child.offsetParent !== null
          )
          .map((child) => child.offsetTop + child.offsetHeight)
      );
      const frame = stage.offsetHeight;
      setMeasure({
        rest: Math.max(MIN_REST, Math.round(frame - contentEnd - REST_GAP)),
        frame,
      });
    };
    update();
    const observer = new ResizeObserver(update);
    observer.observe(stage);
    return () => observer.disconnect();
  }, [stageRef]);
  return measure;
};

/**
 * Stops for a sheet resting under its content. Percentages, since they stay in
 * order even for the moment a frame resizes before this has measured it again.
 */
export const restStops = ({
  rest,
  frame,
}: {
  rest: number;
  frame: number;
}): Detent[] => {
  const height = frame || 800;
  const percent = (position: number) =>
    `${Number((clamp(position / (height - TOP_INSET)) * 100).toFixed(2))}%` as const;
  return [percent(Math.min(160, rest * 0.6)), percent(rest), '100%'];
};

/**
 * Keeps a layout the library rejects from taking the whole page down: it retries
 * the example, which will usually have measured its frame by then.
 */
class SheetBoundary extends Component<
  { children: ReactNode },
  { attempt: number; failed?: boolean }
> {
  override state = { attempt: 0, failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  override componentDidCatch() {
    const { attempt } = this.state;
    if (attempt < 3) {
      requestAnimationFrame(() =>
        this.setState({ failed: false, attempt: attempt + 1 })
      );
    }
  }

  override render() {
    const { failed, attempt } = this.state;
    return failed ? null : (
      <Fragment key={attempt}>{this.props.children}</Fragment>
    );
  }
}

/**
 * A plain host for a demo sheet, with no device around it: the library runs on
 * iOS, Android, and the web. Mounts the sheet while the browser is idle, as doing
 * it when a slide arrives would land in the middle of that slide's animation.
 */
export const Stage = ({
  backdrop,
  rail,
  children,
  stageRef,
}: {
  backdrop: ReactNode;
  rail?: ReactNode;
  children: ReactNode;
  stageRef: RefObject<HTMLDivElement | null>;
}) => {
  const ref = useRef<HTMLDivElement>(null);
  const ready = useWhenIdle();
  // A finger in the frame moves the example, and never the slide around it.
  useEffect(() => {
    const frame = ref.current!;
    const onTouchStart = (event: TouchEvent) => event.stopPropagation();
    frame.addEventListener('touchstart', onTouchStart, { passive: true });
    return () => frame.removeEventListener('touchstart', onTouchStart);
  }, []);
  return (
    <div className={styles.chapterStage} ref={ref} data-frame>
      <div className={clsx(landing.lightTokens, styles.stage)} ref={stageRef}>
        {backdrop}
        {ready ? <SheetBoundary>{children}</SheetBoundary> : null}
      </div>
      {rail ? <div className={styles.rail}>{rail}</div> : null}
    </div>
  );
};

const STAGE_CLEARANCE = 40;

/**
 * Gives every example's frame one height, so the frames line up from step to step:
 * the tallest any slide needs for its frame to reach above its kicker. On small
 * screens each frame takes what its slide has left, to fit one screen.
 * Only on load and resize: swapping a demo's copy shouldn't move a frame.
 */
export const useFrameHeight = () => {
  useEffect(() => {
    const fit = () => {
      const bodies = [
        ...document.querySelectorAll<HTMLElement>('[data-slide-body]'),
      ].filter((body) => body.querySelector('[data-frame]') !== null);
      const frames = bodies.map((body) =>
        body.querySelector<HTMLElement>('[data-frame]')!
      );
      if (window.innerWidth <= 860) {
        frames.forEach((frame) => {
          frame.style.height = '';
        });
        return;
      }
      let height = 0;
      let available = 0;
      bodies.forEach((body) => {
        const copy = body.querySelector<HTMLElement>('[data-copy]')!;
        const chapter = copy.offsetParent as HTMLElement;
        // Layout offsets, not rects, which would include the copy's entrance shift.
        const top = chapter.offsetTop + copy.offsetTop;
        const peek = parseFloat(getComputedStyle(body).paddingBottom);
        const clear = parseFloat(getComputedStyle(chapter).paddingTop);
        available = body.clientHeight - clear - peek;
        height = Math.max(
          height,
          body.scrollHeight - peek - top + STAGE_CLEARANCE
        );
      });
      const shared = `${clamp(height, Math.min(680, available), available)}px`;
      frames.forEach((frame) => {
        frame.style.height = shared;
      });
    };
    fit();
    void document.fonts?.ready.then(fit);
    window.addEventListener('resize', fit);
    return () => window.removeEventListener('resize', fit);
  }, []);
};

export const sheet = StyleSheet.create({
  fill: { flex: 1 },
  grabberWrap: { alignItems: 'center', paddingTop: 10, paddingBottom: 8 },
  grabber: {
    width: 38,
    height: 5,
    borderRadius: 3,
    backgroundColor: '#c1c6e5',
  },
  head: { paddingHorizontal: 20, paddingBottom: 12 },
  title: {
    fontFamily: FONT,
    fontSize: 22,
    fontWeight: '500',
    letterSpacing: -0.4,
    color: INK,
  },
  subtitle: {
    fontFamily: FONT,
    fontSize: 14,
    color: '#33488e',
    marginTop: 2,
    opacity: 0.85,
  },
  listPad: { paddingBottom: 36 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 9,
    gap: 12,
  },
  rowText: { flex: 1 },
  tile: {
    width: 42,
    height: 42,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tileRound: { borderRadius: 21 },
  tileText: {
    fontFamily: FONT,
    color: '#fcfcff',
    fontSize: 17,
    fontWeight: '500',
  },
  rowTitle: { fontFamily: FONT, fontSize: 15.5, fontWeight: '500', color: INK },
  rowMeta: {
    fontFamily: FONT,
    fontSize: 13,
    opacity: 0.68,
    marginTop: 1,
    color: INK,
  },
  rowTrailing: {
    fontFamily: FONT,
    fontSize: 14.5,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
    color: INK,
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 24,
  },
  button: {
    height: 42,
    paddingHorizontal: 16,
    justifyContent: 'center',
    backgroundColor: INK,
    // Web-only: eases the press scale.
    ...({
      transitionProperty: 'transform',
      transitionDuration: '160ms',
      transitionTimingFunction: 'cubic-bezier(0.23, 1, 0.32, 1)',
    } as ViewStyle),
  },
  buttonText: {
    fontFamily: FONT,
    color: '#fcfcff',
    fontSize: 14.5,
    fontWeight: '500',
  },
});
