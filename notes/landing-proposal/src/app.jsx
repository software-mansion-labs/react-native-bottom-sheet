import { Component, Fragment, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';

const FONT = 'Aeonik, system-ui, -apple-system, sans-serif';
const INSTALL = 'npm i @swmansion/react-native-bottom-sheet';
const TOP_INSET = 24;

const clamp = (value, min = 0, max = 1) => Math.min(max, Math.max(min, value));
const prefersReducedMotion = () =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Fires once, when the element comes near the viewport. */
const useInView = (margin = '200px') => {
  const ref = useRef(null);
  const [inView, setInView] = useState(false);
  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setInView(true);
          observer.disconnect();
        }
      },
      { rootMargin: margin }
    );
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [margin]);
  return [ref, inView];
};

/* ---------- Small pieces ---------- */

const Arrow = () => (
  <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden>
    <path
      d="M3 9h11M10 4.5 14.5 9 10 13.5"
      stroke="currentColor"
      strokeWidth="1.6"
    />
  </svg>
);

const CopyInstall = ({ focusable = true }) => {
  const [copied, setCopied] = useState(false);
  return (
    <button
      className="install press"
      data-copied={copied}
      tabIndex={focusable ? 0 : -1}
      aria-label="Copy install command"
      onClick={() => {
        navigator.clipboard?.writeText(INSTALL);
        setCopied(true);
        setTimeout(() => setCopied(false), 1400);
      }}
    >
      <span>{INSTALL}</span>
      <i className="icon-swap" aria-hidden>
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor">
          <rect x="5.5" y="5.5" width="8" height="8" />
          <path d="M10.5 3v-.5h-8v8H3" />
        </svg>
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6">
          <path d="m3 8.5 3.2 3.2L13 5" />
        </svg>
      </i>
    </button>
  );
};

const KEYWORDS = /^(const|return|import|from|true|false)$/;
const TOKENS =
  /(\/\/.*|\{\/\*.*?\*\/\}|'[^']*'|<\/?[A-Z][\w.]*|\/>|\b\d+(?:\.\d+)?\b|\b[a-z]+\b)/g;

/** Just enough JSX highlighting for the snippets on this page. */
const highlight = (code) =>
  code.split(TOKENS).map((part, i) => {
    if (i % 2 === 0) return part;
    let kind = null;
    if (part.startsWith('//') || part.startsWith('{/*')) kind = 't-c';
    else if (part.startsWith("'")) kind = 't-s';
    else if (part.startsWith('<') || part === '/>') kind = 't-j';
    else if (/^\d/.test(part)) kind = 't-n';
    else if (KEYWORDS.test(part)) kind = 't-k';
    return kind ? (
      <span key={i} className={kind}>
        {part}
      </span>
    ) : (
      part
    );
  });

const Code = ({ children }) => (
  <div className="code">
    <pre key={children}>{highlight(children)}</pre>
  </div>
);

/**
 * Every variant in one slot, as tall as the tallest. Switching crossfades them,
 * and nothing around the slot moves.
 */
const Swap = ({ active, items }) => (
  <div className="swap">
    {items.map(([id, node]) => (
      <div key={id} className="swap-item" data-on={id === active} aria-hidden={id !== active}>
        {node}
      </div>
    ))}
  </div>
);

const CodeSwap = ({ active, items }) => (
  <div className="code">
    <Swap active={active} items={items.map(([id, code]) => [id, <pre key={id}>{highlight(code)}</pre>])} />
  </div>
);

const DOCS = 'https://software-mansion-labs.github.io/react-native-bottom-sheet';

const DocsButton = ({ page }) => (
  <a className="docs-button press" href={`${DOCS}/${page}`}>
    Check docs <Arrow />
  </a>
);

/**
 * Code beside an example, and the way into the docs. The code is always shown on
 * wide screens; on small ones it sits behind a toggle, so the options and the
 * example fit on one screen.
 */
const CodeBlock = ({ docs, children }) => {
  const [open, setOpen] = useState(false);
  return (
    <div className="code-block" data-open={open}>
      <div className="code-actions">
        <button className="code-toggle press" aria-expanded={open} onClick={() => setOpen(!open)}>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
            <path d="M5.5 4.5 2 8l3.5 3.5M10.5 4.5 14 8l-3.5 3.5" />
          </svg>
          {open ? 'Hide code' : 'Show code'}
        </button>
        <DocsButton page={docs} />
      </div>
      <div className="code-collapse">
        <div className="code-collapse-inner">{children}</div>
      </div>
    </div>
  );
};

const Chip = ({ active, mono, onClick, children }) => (
  <button
    className={`chip press${mono ? ' chip--mono' : ''}`}
    aria-pressed={active}
    onClick={onClick}
  >
    {children}
  </button>
);

const Note = ({ children }) => <aside className="note">{children}</aside>;

const Reveal = ({ as: Tag = 'div', children, ...props }) => {
  const [ref, inView] = useInView('-8% 0px');
  return (
    <Tag ref={ref} className="reveal" data-in={inView} {...props}>
      {children}
    </Tag>
  );
};

/* ---------- A real app behind each example (fictional names, sample data) ---------- */

const Icon = ({ d, size = 22, fill = 'none', strokeWidth = 1.8 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke={fill === 'none' ? 'currentColor' : 'none'} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round">
    <path d={d} />
  </svg>
);

const ICONS = {
  chevronDown: 'M6 9l6 6 6-6',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4',
  heart: 'M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z',
  shuffle: 'M4 7h3l10 10h3M4 17h3l3-3M14 10l3-3h3M18 4l3 3-3 3M18 14l3 3-3 3',
  repeat: 'M4 11V9a3 3 0 0 1 3-3h13M17 3l3 3-3 3M20 13v2a3 3 0 0 1-3 3H4M7 21l-3-3 3-3',
  thumb: 'M7 11v9H4v-9zM7 11l4-8a2 2 0 0 1 2 2v4h6a1 1 0 0 1 1 1.2l-1.4 8a1 1 0 0 1-1 .8H7',
  share: 'M12 15V4M8 8l4-4 4 4M5 14v5a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-5',
  save: 'M6 4h12v16l-6-4-6 4z',
  fullscreen: 'M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5',
  sun: 'M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4',
};

const ROLL_MS = 480;

/** One character. When it changes, the old one leaves as the new one arrives. */
const RollingChar = ({ char, direction, order }) => {
  const shown = useRef(char);
  const [leaving, setLeaving] = useState(null);
  useLayoutEffect(() => {
    if (shown.current === char) return undefined;
    setLeaving(shown.current);
    shown.current = char;
    const timer = setTimeout(() => setLeaving(null), ROLL_MS + order * 40);
    return () => clearTimeout(timer);
  }, [char, order]);
  const moving = leaving !== null;
  return (
    <span
      className="roll-char"
      data-digit={/\d/.test(char)}
      data-moving={moving}
      data-direction={direction}
      style={{ '--roll-delay': `${order * 40}ms` }}
    >
      {moving ? <span className="roll-out">{leaving}</span> : null}
      <span key={char} className={moving ? 'roll-in' : undefined}>
        {char}
      </span>
    </span>
  );
};

/**
 * A number that rolls to its next value: digits move up when it rises and down
 * when it falls, and only the digits that changed move, from the right.
 */
const RollingNumber = ({ value, direction, className }) => {
  const chars = [...value];
  return (
    <span className={`rolling${className ? ` ${className}` : ''}`} aria-label={value}>
      {chars.map((char, i) => {
        // Keyed from the right, so places stay put when the length changes.
        const fromRight = chars.length - 1 - i;
        return <RollingChar key={fromRight} char={char} direction={direction} order={fromRight} />;
      })}
    </span>
  );
};

const money = (value) =>
  value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const Backdrop = ({ kind, children }) => (
  <div className={`backdrop backdrop--${kind}`} aria-hidden>
    {children}
  </div>
);

const MusicBackdrop = () => (
  <Backdrop kind="music">
    <div className="app-bar">
      <Icon d={ICONS.chevronDown} />
      <div className="app-bar-title">
        <small>Playing from album</small>
        Harbor Lights
      </div>
      <Icon d={ICONS.more} strokeWidth={3} />
    </div>
    <div className="album" data-follow>
      <span>Harbor</span>
      <span>Lights</span>
      <svg viewBox="0 0 300 80" preserveAspectRatio="none">
        <path d="M0 50c40-30 70 20 110 0s70-30 110-6 50 12 80-4v40H0z" fill="rgb(252 252 255 / 0.2)" />
        <path d="M0 62c50-22 80 16 130 0s80-14 170 0v18H0z" fill="rgb(252 252 255 / 0.25)" />
      </svg>
    </div>
    <div className="now-playing">
      <div>
        <b>Slow Tide</b>
        <span>Harbor Lights</span>
      </div>
      <Icon d={ICONS.heart} size={24} fill="currentColor" />
    </div>
    <div className="track-bar">
      <i />
    </div>
    <div className="track-times">
      <span>1:24</span>
      <span>3:42</span>
    </div>
    <div className="transport">
      <Icon d={ICONS.shuffle} />
      <Icon d="M19 5v14L8 12zM6 5v14" size={30} fill="currentColor" />
      <div className="transport-main">
        <Icon d="M8 5h3v14H8zM14 5h3v14h-3z" size={28} fill="currentColor" />
      </div>
      <Icon d="M5 5v14l11-7zM18 5v14" size={30} fill="currentColor" />
      <Icon d={ICONS.repeat} />
    </div>
  </Backdrop>
);

const VideoBackdrop = () => (
  <Backdrop kind="video">
    <div className="player">
      <svg viewBox="0 0 500 280" preserveAspectRatio="xMidYMid slice">
        <defs>
          <linearGradient id="aurora" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#0a2688" />
            <stop offset="0.55" stopColor="#3fc684" />
            <stop offset="1" stopColor="#6fcef5" />
          </linearGradient>
        </defs>
        <rect width="500" height="280" fill="#122154" />
        <path d="M-20 200C90 80 170 240 290 120S450 50 530 100v180H-20z" fill="url(#aurora)" opacity=".85" />
        <path d="M-10 280V240l70-46 60 34 80-66 70 58 60-30 90 50 90-40v80z" fill="#1b2445" />
      </svg>
      <div className="player-controls">
        <Icon d="M8 5h3v14H8zM14 5h3v14h-3z" size={34} fill="currentColor" />
        <div className="player-bottom">
          <span>2:14 / 8:03</span>
          <Icon d={ICONS.fullscreen} size={18} />
        </div>
      </div>
      <div className="player-bar">
        <i />
      </div>
    </div>
    <div className="video-meta">
      <b>Northern lights, in real time</b>
      <span>128K views · 3 days ago</span>
    </div>
    <div className="channel">
      <i>F</i>
      <div>
        <b>Field Notes</b>
        <span>84K subscribers</span>
      </div>
      <em>Follow</em>
    </div>
    <div className="video-actions">
      <span>
        <Icon d={ICONS.thumb} size={16} />
        12K
      </span>
      <span>
        <Icon d={ICONS.share} size={16} />
        Share
      </span>
      <span>
        <Icon d={ICONS.save} size={16} />
        Save
      </span>
    </div>
  </Backdrop>
);

const PortfolioBackdrop = ({ value, gain, direction }) => (
  <Backdrop kind="portfolio">
    <div className="app-bar">
      <b>Portfolio</b>
      <Icon d={ICONS.search} />
    </div>
    <strong>
      <RollingNumber value={`$${money(value)}`} direction={direction} />
    </strong>
    <em>
      <RollingNumber value={`+$${money(gain)}`} direction={direction} /> today · sample data
    </em>
    <svg viewBox="0 0 400 150" preserveAspectRatio="none">
      <defs>
        <linearGradient id="area" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#a0dfc0" stopOpacity=".3" />
          <stop offset="1" stopColor="#3fc684" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d="M0 120C30 110 50 124 90 92s60-6 100-34 50 10 90-12 70-22 120-30v134H0z" fill="url(#area)" />
      <path d="M0 120C30 110 50 124 90 92s60-6 100-34 50 10 90-12 70-22 120-30" fill="none" stroke="#a0dfc0" strokeWidth="2.5" vectorEffect="non-scaling-stroke" />
    </svg>
    <div className="ranges">
      <b>1D</b>
      <span>1W</span>
      <span>1M</span>
      <span>1Y</span>
      <span>All</span>
    </div>
  </Backdrop>
);

/* ---------- Sheet content, built with plain React Native ---------- */

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
];

const COMMENT_LINES = [
  ['Mika', '2h', 'The green at 2:14 is unreal. Had to pause it.', '#3fc684', '812'],
  ['Jonas', '5h', 'How long did you wait for this shot?', '#38acdd', '96'],
  ['Aiko', '6h', 'Watching this on repeat tonight.', '#782aeb', '241'],
  ['Remy', '9h', 'Which lens was this filmed on?', '#ff6259', '37'],
  ['Sol', '1d', 'Saving this for the next trip north.', '#33488e', '58'],
  ['Noor', '1d', 'The silence makes it even better.', '#001a72', '430'],
  ['Teo', '1d', 'I saw this exact sky last winter. Nothing prepares you.', '#57b495', '127'],
  ['Ines', '2d', 'More of these, please.', '#782aeb', '74'],
];
const COMMENTS = Array.from({ length: 24 }, (_, i) => {
  const [name, age, line, color, likes] = COMMENT_LINES[i % COMMENT_LINES.length];
  return { key: `comment-${i}`, item: [`${name} · ${age}`, line, color], likes };
});

const WATCHLIST = [
  ['NORD', 'Nordlys Energy', 142.3, '#38acdd', 0.7],
  ['ALTA', 'Alta Robotics', 86.12, '#782aeb', 1.3],
  ['KITE', 'Kite Logistics', 23.84, '#57b495', 2.1],
  ['PINE', 'Pinewood Foods', 54.6, '#31775d', 0.4],
  ['HALO', 'Halo Biotech', 212.45, '#ff6259', 1.8],
  ['ROOK', 'Rook Security', 67.91, '#001a72', 0.9],
  ['TIDE', 'Tidewater Shipping', 38.27, '#33488e', 1.5],
  ['VOLT', 'Volt Storage', 119.03, '#38acdd', 2.4],
  ['MILL', 'Millstone Group', 44.5, '#91823d', 0.6],
  ['ORBT', 'Orbit Networks', 301.18, '#782aeb', 1.1],
];

const Grabber = ({ tone = '#c1c6e5' }) => (
  <View style={styles.grabberWrap}>
    <View style={[styles.grabber, { backgroundColor: tone }]} />
  </View>
);

const Row = ({ item: [name, meta, color], ink = '#001a72', round, letter, leading, trailing }) => (
  <View style={styles.row}>
    {leading ?? (
      <View style={[styles.tile, round && styles.tileRound, { backgroundColor: color }]}>
        {letter ? <Text style={styles.tileText}>{name[0]}</Text> : null}
      </View>
    )}
    <View style={styles.rowText}>
      <Text style={[styles.rowTitle, { color: ink }]}>{name}</Text>
      <Text style={[styles.rowMeta, { color: ink }]}>{meta}</Text>
    </View>
    {typeof trailing === 'string' ? (
      <Text style={[styles.rowTrailing, { color: ink }]}>{trailing}</Text>
    ) : (
      trailing
    )}
  </View>
);

const ACCENT_SURFACE = (
  <div className="grab" style={{ position: 'absolute', inset: 0, background: '#fcfcff' }} />
);
const ATTACHED = {
  borderTopLeftRadius: 28,
  borderTopRightRadius: 28,
  boxShadow: '0 -6 28 rgba(0, 26, 114, 0.28)',
};

const SheetButton = ({ onPress, children }) => (
  <Pressable
    onPress={onPress}
    style={({ pressed }) => [
      styles.sheetButton,
      pressed && { transform: [{ scale: 0.97 }] },
    ]}
  >
    <Text style={styles.sheetButtonText}>{children}</Text>
  </Pressable>
);

/* ---------- Pieces every demo shares ---------- */

/** All of a sheet is a drag target, so all of it says so. */
const SheetBody = ({ fill, children }) => (
  <div className={`grab sheet-body${fill ? ' sheet-body--fill' : ''}`}>{children}</div>
);

/**
 * Switches an example's case through the sheet's public props alone. Where the
 * sheet can't simply spring there, it closes first, swaps, and opens again.
 */
const useCaseChange = (initial, mustClose) => {
  const [wanted, setWanted] = useState(initial);
  const [shown, setShown] = useState(initial);
  const [index, setIndex] = useState(1);
  const closing = useRef(false);
  const latest = useRef(initial);
  const fallback = useRef(0);
  const showLatest = () => {
    closing.current = false;
    clearTimeout(fallback.current);
    setShown(latest.current);
    setIndex(1);
  };
  const request = (next) => {
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
  const onSettle = (settled) => {
    if (closing.current && settled === 0) showLatest();
  };
  return { wanted, shown, index, setIndex, request, onSettle };
};

/** Shown on a demo until its first drag. */
const DragHint = ({ seen }) => (
  <div className="drag-hint" data-seen={seen} aria-hidden>
    <svg width="12" height="14" viewBox="0 0 12 14" fill="none" stroke="currentColor" strokeWidth="1.5">
      <path d="M6 1v12M2.5 4.5 6 1l3.5 3.5M2.5 9.5 6 13l3.5-3.5" />
    </svg>
    Drag
  </div>
);

/** A closed sheet has nothing left to grab, so offer the way back. */
const Reopen = ({ visible, onPress, children = 'Open the sheet' }) => (
  <button className="reopen press" data-visible={visible} tabIndex={visible ? 0 : -1} onClick={onPress}>
    {children}
  </button>
);

const MIN_REST = 160;
const REST_GAP = 14;

/** Lets an app's own screen respond to its sheet, as real apps do. */
const followSheet = (stage, position) => {
  if (!stage) return;
  // Only once the sheet rises past where it rests, so a resting screen is intact.
  const t = clamp((position / stage.offsetHeight - 0.58) / 0.34);
  stage.querySelectorAll('[data-follow]').forEach((element) => {
    element.style.transform = `scale(${1 - 0.2 * t})`;
    element.style.opacity = 1 - t;
  });
};

/**
 * Where a sheet should rest: directly under its app's content, as in a real app,
 * however tall the frame is. Layout offsets, so a receding section doesn't skew it.
 */
const useRestUnderContent = (stageRef, fallback) => {
  const [measure, setMeasure] = useState({ rest: fallback, frame: 0 });
  useEffect(() => {
    const stage = stageRef.current;
    const update = () => {
      const children = stage.querySelector('.backdrop')?.children;
      if (!children) return;
      // A short frame hides some of a screen's parts, which have no layout.
      const contentEnd = Math.max(
        ...[...children]
          .filter((child) => child instanceof HTMLElement && child.offsetParent !== null)
          .map((child) => child.offsetTop + child.offsetHeight)
      );
      const frame = stage.offsetHeight;
      setMeasure({ rest: Math.max(MIN_REST, Math.round(frame - contentEnd - REST_GAP)), frame });
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
const restStops = ({ rest, frame }) => {
  const height = frame || 800;
  const percent = (position) => `${(clamp(position / (height - TOP_INSET)) * 100).toFixed(2)}%`;
  return [percent(Math.min(160, rest * 0.6)), percent(rest), '100%'];
};

/**
 * Keeps a layout the library rejects from taking the whole page down: it retries
 * the example, which will usually have measured its frame by then.
 */
class SheetBoundary extends Component {
  state = { attempt: 0 };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch() {
    const { attempt } = this.state;
    if (attempt < 3) {
      requestAnimationFrame(() => this.setState({ failed: false, attempt: attempt + 1 }));
    }
  }

  render() {
    const { failed, attempt } = this.state;
    return failed ? null : <Fragment key={attempt}>{this.props.children}</Fragment>;
  }
}

/* ---------- Stages ---------- */

/**
 * A plain host for a demo sheet, with no device around it: the library runs on
 * iOS, Android, and the web. Mounts the sheet near the viewport, so it opens as
 * you arrive.
 */
const Stage = ({ backdrop, rail, children, stageRef }) => {
  const [ref, inView] = useInView('-10% 0px');
  return (
    <div className="chapter-stage" ref={ref}>
      <div className="stage" ref={stageRef}>
        {backdrop}
        {inView ? <SheetBoundary>{children}</SheetBoundary> : null}
      </div>
      {rail ? <div className="rail">{rail}</div> : null}
    </div>
  );
};

/* ---------- Header: the same as the Reanimated and Gesture Handler docs ---------- */

const readTheme = () => {
  try {
    const saved = localStorage.getItem('theme');
    if (saved === 'light' || saved === 'dark') return saved;
  } catch {
    // Storage can be unavailable; the system preference still applies.
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
};

document.documentElement.dataset.theme = readTheme();

const ThemeToggle = () => {
  const [theme, setTheme] = useState(document.documentElement.dataset.theme);
  const toggle = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    setTheme(next);
    try {
      localStorage.setItem('theme', next);
    } catch {
      // Only remembering the choice is lost.
    }
  };
  const label = `Switch between dark and light mode (currently ${theme} mode)`;
  return (
    <button className="theme-toggle" type="button" title={label} aria-label={label} aria-live="polite" onClick={toggle}>
      <span data-active={theme === 'light'}>
        <Icon d={ICONS.sun} size={23} />
      </span>
      <span data-active={theme === 'dark'}>
        <Icon d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" size={23} />
      </span>
    </button>
  );
};

const Header = () => (
  <nav className="navbar">
    <a className="brand" href="#" aria-label="React Native Bottom Sheet">
      <span className="wordmark">
        <span>React Native</span>
        <b>Bottom Sheet</b>
      </span>
    </a>
    <div className="navbar-right">
      <ThemeToggle />
      <a className="navbar-github" href="https://github.com/software-mansion-labs/react-native-bottom-sheet" aria-label="GitHub repository">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
          <path d="M12 .5a11.5 11.5 0 0 0-3.64 22.41c.58.1.79-.25.79-.56v-2.15c-3.2.7-3.88-1.36-3.88-1.36-.52-1.33-1.28-1.68-1.28-1.68-1.05-.72.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.29 1.19-3.09-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11 11 0 0 1 5.78 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.8 1.19 1.83 1.19 3.09 0 4.42-2.7 5.4-5.26 5.68.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.5 11.5 0 0 0 12 .5z" />
        </svg>
      </a>
    </div>
  </nav>
);

/* ---------- Hero ---------- */

const HERO_TOP_INSET = 72;
// The lowest stop keeps the native and react descriptions on the sheet.
const HERO_DETENTS = [216, 320, '100%'];
const HERO_LABELS = ['216', '320', "'100%'"];
const LETTERS = ['A', 'B', 'C'];

const heroRuleStyle = (i) =>
  i === 2 ? { top: HERO_TOP_INSET } : { bottom: HERO_DETENTS[i] };

const HERO_SURFACE = <div className="grab hero-surface" />;

/**
 * The hero's copy. It renders twice: dark on the page, and white above the real
 * sheet, clipped to the sheet's edge so the type inverts exactly along it.
 */
const HeroCopy = ({ navy, index, onSnap, layerRef, children }) => (
  <div className={`world world--${navy ? 'navy' : 'light'}`} ref={layerRef} aria-hidden={navy}>
    <div className="hero-copy">
      <h1 style={{ '--i': 0 }}>
        <span className="eyebrow">React Native Bottom Sheet</span>
        <span className="headline">
          Native mechanics.
          <br />
          Your surface.
        </span>
      </h1>
      <p className="lede" style={{ '--i': 2 }}>
        Gestures, snapping, and scroll negotiation run in native code. Everything
        you see is plain React Native, on iOS, Android, and the web.
      </p>
      <div className="hero-actions" style={{ '--i': 3 }}>
        <a className="cta press" href={`${DOCS}/getting-started`} tabIndex={navy ? -1 : 0}>
          Get started <Arrow />
        </a>
        <CopyInstall focusable={!navy} />
      </div>
    </div>
    {HERO_DETENTS.map((_, i) => (
      <div key={i} className="rule" style={heroRuleStyle(i)} />
    ))}
    {HERO_DETENTS.map((_, i) => (
      <button
        key={i}
        className="callout press"
        style={heroRuleStyle(i)}
        aria-pressed={index === i}
        aria-label={`Snap to detent ${HERO_LABELS[i]}`}
        tabIndex={navy ? -1 : 0}
        onClick={() => onSnap(i)}
      >
        <span>{HERO_LABELS[i]}</span>
        <i>{LETTERS[i]}</i>
      </button>
    ))}
    {children}
  </div>
);

const Hero = () => {
  const [index, setIndex] = useState(1);
  const [touched, setTouched] = useState(false);
  const stage = useRef(null);
  const navy = useRef(null);
  const ride = useRef(null);
  const shownPosition = useRef(null);
  const shownIndex = useRef(null);
  const last = useRef({ position: 0, index: 0 });
  const snap = (next) => {
    setIndex(next);
    setTouched(true);
  };

  // Everything a frame touches is a direct transform or clip-path write on the
  // one element that needs it, never React state and never an inherited
  // variable.
  const apply = useCallback((position, fractional) => {
    last.current = { position, index: fractional };
    const edge = stage.current.offsetHeight - position;
    navy.current.style.clipPath = `inset(${edge}px 0 0 0 round 36px 36px 0 0)`;
    ride.current.style.transform = `translate3d(0, ${-position}px, 0)`;
    shownPosition.current.textContent = Math.round(position).toString();
    shownIndex.current.textContent = fractional.toFixed(2);
  }, []);

  useEffect(() => {
    const observer = new ResizeObserver(() =>
      apply(last.current.position, last.current.index)
    );
    observer.observe(stage.current);
    return () => observer.disconnect();
  }, [apply]);

  return (
    <section className="hero">
      <div className="hero-stage" ref={stage} style={{ '--next': '--s1' }}>
        <HeroCopy index={index} onSnap={snap} />
        <div className="ride" ref={ride}>
          <div className="hint" data-gone={touched}>
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden>
              <path d="M8 13.5V3M3.5 7.5 8 3l4.5 4.5" />
            </svg>
            Drag me
          </div>
        </div>
        <div className="sheet-layer">
          <BottomSheet
            detents={HERO_DETENTS}
            topInset={HERO_TOP_INSET}
            index={index}
            onIndexChange={snap}
            animateIn={!prefersReducedMotion()}
            surface={HERO_SURFACE}
            surfaceStyle={{ borderTopLeftRadius: 36, borderTopRightRadius: 36 }}
            onPositionChange={(event) => {
              const { position, index: fractional } = event.nativeEvent;
              apply(position, fractional);
            }}
          >
            <div className="grab hero-grab">
              <div className="hero-grabber" />
            </div>
          </BottomSheet>
        </div>
        <HeroCopy navy index={index} onSnap={snap} layerRef={navy}>
          <div className="xray">
            <div>
              <b>native</b>
              <span>The drag, the snap, and the scroll handoff.</span>
            </div>
            <div>
              <b>react</b>
              <span>The surface and everything on it. Plain views.</span>
            </div>
          </div>
          <div className="readout">
            onPositionChange · position <b ref={shownPosition}>0</b> · index <b ref={shownIndex}>0.00</b>
          </div>
        </HeroCopy>
        <Header />
      </div>
    </section>
  );
};

/* ---------- Every section is a sheet that rises over the last ---------- */

// The sheet's own spring (src/web/criticalSpring.ts), so a section settles the
// way a sheet does.
const SETTLE_MS = 450;
const springProgress = (t) => 1 - Math.exp(-8 * t) * (1 + 8 * t);
// How far past a snap point counts as meaning to leave it: one wheel tick does.
const COMMIT_RATIO = 0.06;
// A wheel gesture ends after this long without events, momentum included.
const WHEEL_QUIET_MS = 140;
// Wheel movement that commits to a step, so a resting hand's jitter doesn't.
const WHEEL_COMMIT = 20;
// A section needs this much extra height before it scrolls freely inside.
const MIN_FREE_RANGE = 40;

const TAP_SLOP = 4;
const FLICK_SPEED = 0.3;
const FLICK_HOLD_MS = 100;

/** Whether something under the pointer, like a list, can use this scroll itself. */
const scrollsInside = (target, direction) => {
  for (let node = target; node instanceof Element && node !== document.body; node = node.parentElement) {
    const { overflowY } = getComputedStyle(node);
    if ((overflowY === 'auto' || overflowY === 'scroll') && node.scrollHeight > node.clientHeight + 1) {
      const room =
        direction > 0 ? node.scrollHeight - node.clientHeight - node.scrollTop : node.scrollTop;
      if (room > 1) return true;
    }
  }
  return false;
};

/**
 * Makes each section a step, the way a sheet moves between detents.
 *
 * A wheel or trackpad gesture moves exactly one section: its first movement
 * commits and the rest of it, momentum included, is ignored. So a flick can't
 * skip a section or overshoot one and need repositioning. Lists under the pointer
 * still scroll first, and a section taller than the screen scrolls freely inside.
 *
 * Touch uses native snapping (see `html` in the styles). Anything else that
 * scrolls, like a keyboard or the scrollbar, settles once it stops, knowing the
 * direction: native `scroll-snap` only knows the closest point, which would send
 * a gentle scroll straight back.
 */
const useSectionSnap = () => {
  useEffect(() => {
    const root = document.documentElement;
    let lastY = window.scrollY;
    let direction = 0;
    let frame = 0;
    let settling = false;
    let touching = false;
    let dragging = false;
    let idle = 0;
    let wheelSpent = false;
    let wheelTime = -Infinity;
    let wheelSize = 0;
    let wheelWay = 0;
    let wheelTravel = 0;

    // Native snapping would fight a page that's following the pointer.
    const restoreSnap = () => {
      if (!dragging) root.style.scrollSnapType = '';
    };

    const cancel = () => {
      cancelAnimationFrame(frame);
      settling = false;
      restoreSnap();
    };

    // Each section's stops: a tall one can be scrolled freely between the two.
    const ranges = () => [
      [0, 0],
      ...[...document.querySelectorAll('.sentinel')].map((element) => {
        const top = element.getBoundingClientRect().top + window.scrollY;
        const free = element.offsetHeight - window.innerHeight;
        return [top, free >= MIN_FREE_RANGE ? top + free : top];
      }),
    ];

    const animateTo = (target) => {
      const from = window.scrollY;
      if (Math.abs(target - from) < 1) {
        restoreSnap();
        return;
      }
      if (prefersReducedMotion()) {
        window.scrollTo(0, target);
        restoreSnap();
        return;
      }
      cancelAnimationFrame(frame);
      settling = true;
      const startTime = performance.now();
      const step = (time) => {
        const t = clamp((time - startTime) / SETTLE_MS);
        window.scrollTo(0, from + (target - from) * (t === 1 ? 1 : springProgress(t)));
        if (t < 1) {
          frame = requestAnimationFrame(step);
        } else {
          settling = false;
          lastY = window.scrollY;
          direction = 0;
          restoreSnap();
        }
      };
      frame = requestAnimationFrame(step);
    };

    const settle = () => {
      if (settling || touching || dragging) return;
      const y = window.scrollY;
      const stops = ranges();
      if (stops.some(([start, end]) => y >= start - 1 && y <= end + 1)) return;
      const nextIndex = stops.findIndex(([start]) => start > y);
      if (nextIndex <= 0) return;
      const from = stops[nextIndex - 1][1];
      const to = stops[nextIndex][0];
      const commit = (to - from) * COMMIT_RATIO;
      let target = y - from < to - y ? from : to;
      if (direction > 0 && y - from > commit) target = to;
      if (direction < 0 && to - y > commit) target = from;
      animateTo(target);
    };

    // Drags a sheet between showing at the bottom and sitting at the top, like a
    // sheet between two detents. A flick or a tap decides it just as a drag does.
    const onHandleDown = (event) => {
      const handle = event.target instanceof Element ? event.target.closest('.sheet-handle') : null;
      if (!handle || event.button !== 0) return;
      cancel();
      const stops = ranges();
      const index = [...document.querySelectorAll('.sheet-handle')].indexOf(handle) + 1;
      const startY = window.scrollY;
      const startPointer = event.clientY;
      const from = Math.min(stops[index - 1][1], startY);
      const to = Math.max(stops[index][0], startY);
      let lastMoveY = startY;
      let lastMoveTime = event.timeStamp;
      let velocity = 0;

      dragging = true;
      root.classList.add('sheet-dragging');
      root.style.scrollSnapType = 'none';
      handle.setPointerCapture(event.pointerId);

      const onMove = (move) => {
        if (move.pointerId !== event.pointerId) return;
        const y = clamp(startY - (move.clientY - startPointer), from, to);
        const elapsed = move.timeStamp - lastMoveTime;
        if (elapsed > 0) velocity = (y - lastMoveY) / elapsed;
        lastMoveY = y;
        lastMoveTime = move.timeStamp;
        window.scrollTo(0, y);
      };
      const onEnd = (end) => {
        if (end.pointerId !== event.pointerId) return;
        handle.removeEventListener('pointermove', onMove);
        handle.removeEventListener('pointerup', onEnd);
        handle.removeEventListener('pointercancel', onEnd);
        dragging = false;
        root.classList.remove('sheet-dragging');
        const y = window.scrollY;
        const held = end.timeStamp - lastMoveTime > FLICK_HOLD_MS;
        let target = y - from > (to - from) / 2 ? to : from;
        if (Math.abs(end.clientY - startPointer) < TAP_SLOP) target = to;
        else if (!held && Math.abs(velocity) > FLICK_SPEED) target = velocity > 0 ? to : from;
        animateTo(target);
      };
      handle.addEventListener('pointermove', onMove);
      handle.addEventListener('pointerup', onEnd);
      handle.addEventListener('pointercancel', onEnd);
    };

    const onWheel = (event) => {
      if (dragging) {
        event.preventDefault();
        return;
      }
      if (event.ctrlKey || Math.abs(event.deltaX) > Math.abs(event.deltaY)) return;
      const scale = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? window.innerHeight : 1;
      const delta = event.deltaY * scale;
      const size = Math.abs(delta);
      const way = Math.sign(delta);
      if (way === 0) return;

      // Momentum fades and never reverses, so a size that jumps back up or a change
      // of direction is a new swipe, not the old one's tail.
      const now = event.timeStamp;
      const isNewSwipe = !settling && size > 24 && size > wheelSize * 1.6;
      if (now - wheelTime > WHEEL_QUIET_MS || isNewSwipe || way !== wheelWay) {
        wheelSpent = false;
        wheelTravel = 0;
      }
      wheelTime = now;
      wheelSize = size;
      wheelWay = way;

      if (scrollsInside(event.target, way)) {
        // Reaching the end of the list shouldn't also change section mid-flick.
        wheelSpent = true;
        return;
      }

      const y = window.scrollY;
      const stops = ranges();
      let index = 0;
      stops.forEach(([start], i) => {
        if (start <= y + 1) index = i;
      });
      const [start, end] = stops[index];
      const hasRoom = way > 0 ? y < end - 1 : y > start + 1 && y <= end + 1;
      if (hasRoom && end > start) {
        const limit = way > 0 ? end : start;
        // Free scrolling inside a tall section stops at its edge.
        if (Math.abs(limit - y) > size) return;
        event.preventDefault();
        window.scrollTo(0, limit);
        wheelSpent = true;
        return;
      }

      event.preventDefault();
      if (wheelSpent) return;
      wheelTravel += delta;
      if (Math.abs(wheelTravel) < WHEEL_COMMIT) return;
      wheelSpent = true;

      let target = null;
      if (way > 0 && stops[index + 1]) [target] = stops[index + 1];
      if (way < 0 && y > start + 1) target = start;
      else if (way < 0 && stops[index - 1]) [, target] = stops[index - 1];
      if (target !== null) animateTo(target);
    };

    const onScroll = () => {
      if (settling) return;
      const y = window.scrollY;
      if (y !== lastY) direction = Math.sign(y - lastY);
      lastY = y;
      // For browsers without `scrollend`.
      clearTimeout(idle);
      idle = setTimeout(settle, 180);
    };
    const onScrollEnd = () => {
      clearTimeout(idle);
      settle();
    };
    const onTouchStart = () => {
      touching = true;
      cancel();
    };
    const onTouchEnd = () => {
      touching = false;
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('scrollend', onScrollEnd);
    window.addEventListener('wheel', onWheel, { passive: false });
    window.addEventListener('keydown', cancel);
    window.addEventListener('pointerdown', cancel);
    window.addEventListener('pointerdown', onHandleDown);
    window.addEventListener('touchstart', onTouchStart, { passive: true });
    window.addEventListener('touchend', onTouchEnd, { passive: true });
    window.addEventListener('touchcancel', onTouchEnd, { passive: true });
    return () => {
      cancel();
      clearTimeout(idle);
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('scrollend', onScrollEnd);
      window.removeEventListener('wheel', onWheel);
      window.removeEventListener('keydown', cancel);
      window.removeEventListener('pointerdown', cancel);
      window.removeEventListener('pointerdown', onHandleDown);
      window.removeEventListener('touchstart', onTouchStart);
      window.removeEventListener('touchend', onTouchEnd);
      window.removeEventListener('touchcancel', onTouchEnd);
    };
  }, []);
};

/**
 * Holds the grabbing cursor for a whole press on a sheet. The library follows a
 * drag on the window, so the pointer can leave the sheet or cross a button mid-drag.
 */
const useGrabbingCursor = () => {
  useEffect(() => {
    const root = document.documentElement;
    const onDown = (event) => {
      if (event.button !== 0 || !(event.target instanceof Element)) return;
      if (!event.target.closest('.grab') || event.target.closest('button, a, [role="button"]')) return;
      root.classList.add('sheet-dragging');
    };
    const onUp = () => root.classList.remove('sheet-dragging');
    window.addEventListener('pointerdown', onDown, true);
    window.addEventListener('pointerup', onUp, true);
    window.addEventListener('pointercancel', onUp, true);
    return () => {
      window.removeEventListener('pointerdown', onDown, true);
      window.removeEventListener('pointerup', onUp, true);
      window.removeEventListener('pointercancel', onUp, true);
    };
  }, []);
};

const STAGE_CLEARANCE = 40;

/**
 * Gives every example's frame one height, so the frames line up from step to step:
 * the tallest any section needs for its frame to reach above its kicker. On small
 * screens each frame takes what its section has left, to fit one screen.
 * Only on load and resize: swapping a demo's copy shouldn't move a frame.
 */
const useFrameHeight = () => {
  useEffect(() => {
    const fit = () => {
      const sections = [...document.querySelectorAll('.sheet-section')].filter((section) =>
        section.querySelector('.chapter-stage')
      );
      const stages = sections.map((section) => section.querySelector('.chapter-stage'));
      if (window.innerWidth <= 860) {
        stages.forEach((stage) => {
          stage.style.height = '';
        });
        return;
      }
      let height = 0;
      let available = 0;
      sections.forEach((section) => {
        const copy = section.querySelector('.chapter-copy');
        // Layout offsets, not rects, which would include the copy's entrance shift.
        const top = copy.offsetParent.offsetTop + copy.offsetTop;
        const peek = parseFloat(getComputedStyle(section.firstElementChild).paddingBottom);
        const clear = parseFloat(getComputedStyle(section.querySelector('.chapter')).paddingTop);
        available = window.innerHeight - clear - peek;
        height = Math.max(height, section.offsetHeight - peek - top + STAGE_CLEARANCE);
      });
      const shared = `${clamp(height, Math.min(680, available), available)}px`;
      stages.forEach((stage) => {
        stage.style.height = shared;
      });
    };
    fit();
    document.fonts?.ready.then(fit);
    window.addEventListener('resize', fit);
    return () => window.removeEventListener('resize', fit);
  }, []);
};

const SheetSection = ({ name, next, tone, order, id, children }) => {
  const ref = useRef(null);
  const sentinel = useRef(null);
  useEffect(() => {
    const element = ref.current;
    const measure = () => {
      const height = element.offsetHeight;
      element.style.top = `${Math.min(0, window.innerHeight - height)}px`;
      // As tall as its section: a snap area taller than the viewport still
      // scrolls freely inside, so a tall section can be read to its end.
      const span = Math.max(height, window.innerHeight);
      sentinel.current.style.height = `${span}px`;
      sentinel.current.style.marginBottom = `${-span}px`;
    };
    measure();
    document.fonts?.ready.then(measure);
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    window.addEventListener('resize', measure);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', measure);
    };
  }, []);
  return (
    <>
      {/*
        Scrolls normally, so it can time the sheet below it as that rises, and
        mark the point that sheet settles on.
      */}
      <div className="sentinel" id={id} ref={sentinel} style={{ viewTimelineName: name }} aria-hidden />
      <section className="sheet-section" ref={ref} style={{ zIndex: order }}>
        <div
          className="sheet-inner"
          data-tone={tone}
          data-recedes={next ? '' : undefined}
          style={next ? { '--next': next } : undefined}
        >
          <div className="sheet-handle" aria-hidden>
            <span className="page-grabber" />
          </div>
          {children}
        </div>
      </section>
    </>
  );
};

/* ---------- 01: Detents ---------- */

const CARD = { left: 16, right: 16, bottom: 32 };

/** The cases, with fixed heights that fit the frame so they stay in order. */
const makeDetentPresets = (frameHeight) => {
  const fit = (value, ratio) => Math.round(Math.min(value, (frameHeight - TOP_INSET) * ratio));
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

const resolveTick = (detent, height, measured, i) => {
  const value = typeof detent === 'object' ? detent.value : detent;
  const bottom = typeof detent === 'object' ? (detent.insets?.bottom ?? 0) : 0;
  if (value === 'content') return measured[i] ?? null;
  if (typeof value === 'number') return value === 0 ? 0 : value + bottom;
  return (parseFloat(value) / 100) * (height - TOP_INSET - bottom) + bottom;
};

const DetentsChapter = () => {
  // A detent's height springs by itself. Its shape can't, so a floating card
  // closes, swaps, and opens rather than jumping.
  const { wanted, shown, index, setIndex, request, onSettle } = useCaseChange(
    'content',
    (from, to) => (from === 'card') !== (to === 'card')
  );
  const [rows, setRows] = useState(2);
  const [dragged, setDragged] = useState(false);
  const [height, setHeight] = useState(0);
  const [measured, setMeasured] = useState({});
  const screen = useRef(null);
  const lastPosition = useRef(0);
  const presets = makeDetentPresets(height || 800);
  const preset = presets.find((item) => item.id === shown);

  useEffect(() => {
    if (!screen.current) return undefined;
    const observer = new ResizeObserver(() => setHeight(screen.current.offsetHeight));
    observer.observe(screen.current);
    return () => observer.disconnect();
  }, []);

  const choose = (next) => {
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
            className="tick"
            data-active={index === i}
            style={{ transform: `translateY(${-position}px)` }}
          >
            {preset.labels[i]}
          </div>
        );
      })
    : null;

  return (
    <section className="chapter">
      <Reveal className="chapter-copy reveal">
        <span className="kicker">01 — detents</span>
        <h2 className="title">Stops where you mean it to.</h2>
        <Swap
          active={wanted}
          items={presets.map((item) => [item.id, <p key={item.id} className="body">{item.text}</p>])}
        />
        <div className="chips" role="group" aria-label="Detent examples">
          {presets.map((item) => (
            <Chip key={item.id} active={item.id === wanted} onClick={() => choose(item)}>
              {item.label}
            </Chip>
          ))}
        </div>
        <span className="chips-label">index</span>
        <div className="chips" role="group" aria-label="Snap to detent">
          {preset.detents.map((_, i) => (
            <Chip key={i} mono active={index === i} onClick={() => setIndex(i)}>
              {i}
            </Chip>
          ))}
        </div>
        <CodeBlock docs="detents-and-index">
          <CodeSwap active={wanted} items={presets.map((item) => [item.id, item.code])} />
        </CodeBlock>
        <Note>
          Sonner’s pattern: every option is something to press, paired with the
          exact code that produces it. Ticks beside the stage glide on the
          sheet’s own curve, so the page and the product move as one.
        </Note>
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
          surface={ACCENT_SURFACE}
          surfaceStyle={preset.shape ?? ATTACHED}
          onPositionChange={(event) => {
            lastPosition.current = event.nativeEvent.position;
            followSheet(screen.current, event.nativeEvent.position);
          }}
          onSettle={(settled) => {
            setMeasured((previous) => ({ ...previous, [settled]: lastPosition.current }));
            onSettle(settled);
          }}
        >
          <SheetBody fill={preset.id !== 'content'}>
            <DragHint seen={dragged} />
            <Grabber />
            <View style={styles.sheetHead}>
              <Text style={styles.sheetTitle}>Up next</Text>
              <Text style={styles.sheetSub}>{TRACKS.length} songs in your queue</Text>
            </View>
            {preset.id === 'content' ? (
              <View key={preset.id} style={styles.caseFade}>
                {TRACKS.slice(0, rows).map((track) => (
                  <Row key={track[0]} item={track} trailing={track[3]} />
                ))}
                <View style={styles.sheetActions}>
                  <SheetButton onPress={() => setRows((n) => Math.min(n + 1, 8))}>Add a song</SheetButton>
                  <SheetButton onPress={() => setRows((n) => Math.max(n - 1, 1))}>Remove</SheetButton>
                </View>
              </View>
            ) : (
              <div key={preset.id} className="case-fade">
                {preset.id === 'programmatic' ? (
                  <View style={styles.sheetActions}>
                    <SheetButton onPress={() => setIndex(index === 3 ? 2 : 3)}>
                      {index === 3 ? preset.backLabel : 'Expand from code'}
                    </SheetButton>
                  </View>
                ) : null}
                <ScrollView style={styles.fill} contentContainerStyle={styles.listPad}>
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

/* ---------- 02: Scroll negotiation ---------- */

const MODES = ['none', 'initial', 'handoff'];
const TRACE_SECONDS = 6;

const useTrace = (canvasRef, sampleRef, onOwner) => {
  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas.getContext('2d');
    const points = [];
    let frame = 0;
    let visible = false;
    let lastActive = 0;
    let clock = 0;
    let previousTime = performance.now();
    let previous = { ...sampleRef.current };
    let owner = 'idle';

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
      [1 / 2].forEach((f) => {
        context.beginPath();
        context.moveTo(0, height * f);
        context.lineTo(width, height * f);
        context.stroke();
      });
      context.setLineDash([]);

      const line = (key, color, top, laneHeight) => {
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

    const tick = (time) => {
      frame = requestAnimationFrame(tick);
      const delta = (time - previousTime) / 1000;
      previousTime = time;
      const sample = sampleRef.current;
      const sheetMoved = Math.abs(sample.sheet - previous.sheet) > 0.0005;
      const listMoved = Math.abs(sample.list - previous.list) > 0.0005;
      previous = { ...sample };
      if (sheetMoved || listMoved) lastActive = time;

      const nextOwner = sheetMoved ? 'sheet' : listMoved ? 'list' : time - lastActive > 200 ? 'idle' : owner;
      if (nextOwner !== owner) {
        owner = nextOwner;
        onOwner(owner);
      }

      // The trace only advances around real movement, so an idle page is still.
      if (!visible || time - lastActive > 900) return;
      clock += delta;
      points.push({ t: clock, sheet: sample.sheet, list: sample.list });
      while (points.length > 1 && clock - points[0].t > TRACE_SECONDS + 0.2) points.shift();
      draw();
    };

    const observer = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
    });
    observer.observe(canvas);
    draw();
    frame = requestAnimationFrame(tick);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, [canvasRef, sampleRef, onOwner]);
};

const NegotiationChapter = () => {
  const [expand, setExpand] = useState('handoff');
  const [collapse, setCollapse] = useState('initial');
  const [index, setIndex] = useState(1);
  const [dragged, setDragged] = useState(false);
  const stage = useRef(null);
  const rest = useRestUnderContent(stage, 400);
  const canvas = useRef(null);
  const ownerRef = useRef(null);
  const sample = useRef({ sheet: 0, list: 0 });
  const onOwner = useCallback((owner) => {
    if (!ownerRef.current) return;
    ownerRef.current.dataset.owner = owner;
    ownerRef.current.textContent =
      owner === 'sheet' ? 'sheet owns the gesture' : owner === 'list' ? 'list owns the gesture' : 'waiting for a drag';
  }, []);
  useTrace(canvas, sample, onOwner);

  return (
    <section className="chapter" data-flip data-compact>
      <Reveal className="chapter-copy reveal">
        <span className="kicker">02 — scrollable negotiation</span>
        <h2 className="title">One gesture, two owners, no wrappers.</h2>
        <p className="body">
          Put an ordinary <code>ScrollView</code> or <code>FlatList</code> in the
          sheet. Drag up on the list and watch the gesture pass from one line to
          the other, without lifting your finger.
        </p>
        <div className="trace">
          <canvas ref={canvas} aria-label="Live trace of sheet position and list offset" role="img" />
          <div className="trace-legend">
            <span><i style={{ color: '#001a72' }} />sheet position</span>
            <span><i style={{ color: '#38acdd' }} />list offset</span>
            <span className="owner" ref={ownerRef} data-owner="idle">waiting for a drag</span>
          </div>
        </div>
        <div className="modes">
          {[
            ['expand', expand, setExpand],
            ['collapse', collapse, setCollapse],
          ].map(([name, value, setValue]) => (
            <div key={name}>
              <span className="chips-label">{name}</span>
              <div className="chips" role="group" aria-label={`${name} mode`}>
                {MODES.map((mode) => (
                  <Chip key={mode} mono active={mode === value} onClick={() => setValue(mode)}>
                    {mode}
                  </Chip>
                ))}
              </div>
            </div>
          ))}
        </div>
        <CodeBlock docs="scrollable-negotiation">
          <Code>{`<BottomSheet
  scrollableNegotiation={{
    expand: '${expand}',
    collapse: '${collapse}',
  }}
/>`}</Code>
        </CodeBlock>
        <Note>
          The trace reads real values from <code>onPositionChange</code> and{' '}
          <code>onScroll</code>. It only advances around movement, so an idle
          page stays still.
        </Note>
      </Reveal>
      <Stage backdrop={<VideoBackdrop />} stageRef={stage}>
        <BottomSheet
          detents={restStops(rest)}
          topInset={TOP_INSET}
          index={index}
          onIndexChange={(next) => {
            setIndex(next);
            setDragged(true);
          }}
          surface={ACCENT_SURFACE}
          surfaceStyle={ATTACHED}
          scrollableNegotiation={{ expand, collapse }}
          onPositionChange={(event) => {
            sample.current = { ...sample.current, sheet: clamp(event.nativeEvent.index / 2) };
          }}
        >
          <SheetBody fill>
            <DragHint seen={dragged} />
            <Grabber />
            <View style={styles.sheetHead}>
              <Text style={styles.sheetTitle}>Comments</Text>
              <Text style={styles.sheetSub}>1,204 comments. Drag right on the list.</Text>
            </View>
            <ScrollView
              style={styles.fill}
              contentContainerStyle={styles.listPad}
              scrollEventThrottle={16}
              onScroll={(event) => {
                const { contentOffset, contentSize, layoutMeasurement } = event.nativeEvent;
                const range = Math.max(1, contentSize.height - layoutMeasurement.height);
                sample.current = { ...sample.current, list: clamp(contentOffset.y / range) };
              }}
            >
              {COMMENTS.map(({ key, item, likes }) => (
                <Row
                  key={key}
                  item={item}
                  round
                  letter
                  trailing={
                    <div className="likes">
                      <Icon d={ICONS.thumb} size={14} />
                      {likes}
                    </div>
                  }
                />
              ))}
            </ScrollView>
          </SheetBody>
        </BottomSheet>
      </Stage>
    </section>
  );
};

/* ---------- 03: Threads ---------- */

const MARKET_CYCLE_MS = 5000;
const PORTFOLIO_OPEN = 46926.32;

const portfolioValue = (quotes) =>
  48210.42 + quotes.reduce((sum, { price, open }) => sum + (price - open) * 12, 0);

const openMarket = () => {
  const quotes = WATCHLIST.map(([ticker, name, price, color, swing]) => ({
    ticker,
    name,
    color,
    swing,
    open: price,
    price,
    direction: 'up',
  }));
  return { quotes, value: portfolioValue(quotes), direction: 'up' };
};

/** One step of sample data: each price moves a little, up or down. */
const nextMarket = (market) => {
  const quotes = market.quotes.map((quote) => {
    const step = quote.open * (quote.swing / 100) * (Math.random() * 0.8 - 0.4);
    return { ...quote, price: quote.price + step, direction: step < 0 ? 'down' : 'up' };
  });
  const value = portfolioValue(quotes);
  return { quotes, value, direction: value < market.value ? 'down' : 'up' };
};


const ThreadsChapter = () => {
  const [blocked, setBlocked] = useState(false);
  const [index, setIndex] = useState(1);
  const [dragged, setDragged] = useState(false);
  const [market, setMarket] = useState(openMarket);
  const stage = useRef(null);
  const rest = useRestUnderContent(stage, 410);
  const uiValue = useRef(null);
  const jsValue = useRef(null);
  const blockedRef = useRef(false);

  // A cycle counted in time JS was free: blocking JS stalls it, as it would in an app.
  useEffect(() => {
    let last = performance.now();
    let elapsed = 0;
    const id = setInterval(() => {
      const now = performance.now();
      if (!blockedRef.current) elapsed += now - last;
      last = now;
      if (elapsed >= MARKET_CYCLE_MS) {
        elapsed -= MARKET_CYCLE_MS;
        setMarket(nextMarket);
      }
    }, 100);
    return () => clearInterval(id);
  }, []);

  const block = () => {
    if (blockedRef.current) return;
    blockedRef.current = true;
    setBlocked(true);
    setTimeout(() => {
      blockedRef.current = false;
      setBlocked(false);
    }, 4000);
  };

  return (
    <section className="chapter" data-compact>
      <Reveal className="chapter-copy reveal">
        <span className="kicker">03 — native host</span>
        <h2 className="title">Your JS can be busy. The sheet isn’t waiting.</h2>
        <p className="body">
          The drag, the snap, and the scroll handoff don’t pass through the JS
          thread, so a heavy render inside the sheet doesn’t stop your finger
          from moving it.
        </p>
        <div className="lanes" data-blocked={blocked}>
          <div className="lane" data-lane="ui">
            <span>native gesture</span>
            <div className="lane-track" />
            <output ref={uiValue}>—</output>
          </div>
          <div className="lane" data-lane="js">
            <span>JS events</span>
            <div className="lane-track" />
            <output ref={jsValue}>—</output>
          </div>
        </div>
        <button className="block-btn press" data-blocked={blocked} onClick={block}>
          <span>{blocked ? 'JS thread blocked…' : 'Block the JS thread for 4 seconds'}</span>
        </button>
        <p className="honest">
          Illustration. On the web, the sheet and JavaScript share one thread, so
          this page can only pretend. The shipped section should play recordings
          from iOS and Android.
        </p>
        <div className="code-actions code-actions--solo">
          <DocsButton page="how-it-compares" />
        </div>
        <Note>
          Deliberately no competitor side by side: a staged “their sheet freezes”
          clip wouldn’t be fair, since Reanimated-based sheets also run gestures
          off JS. The claim stays about this library.
        </Note>
      </Reveal>
      <Stage
        backdrop={
          <PortfolioBackdrop
            value={market.value}
            gain={market.value - PORTFOLIO_OPEN}
            direction={market.direction}
          />
        }
        stageRef={stage}
      >
        <BottomSheet
          detents={restStops(rest)}
          topInset={TOP_INSET}
          index={index}
          onIndexChange={(next) => {
            setIndex(next);
            setDragged(true);
          }}
          surface={ACCENT_SURFACE}
          surfaceStyle={ATTACHED}
          onPositionChange={(event) => {
            const shown = Math.round(event.nativeEvent.position);
            if (uiValue.current) uiValue.current.textContent = `${shown}`;
            if (!blockedRef.current && jsValue.current) jsValue.current.textContent = `${shown}`;
          }}
        >
          <SheetBody fill>
            <DragHint seen={dragged} />
            <Grabber />
            <View style={styles.sheetHead}>
              <Text style={styles.sheetTitle}>Watchlist</Text>
            </View>
            <ScrollView style={styles.fill} contentContainerStyle={styles.listPad}>
              {market.quotes.map(({ ticker, name, color, open, price, direction }) => {
                const change = ((price - open) / open) * 100;
                return (
                  <Row
                    key={ticker}
                    item={[ticker, name, color]}
                    round
                    letter
                    trailing={
                      <div className="quote">
                        <RollingNumber className="quote-price" value={money(price)} direction={direction} />
                        <RollingNumber
                          className="quote-change"
                          value={`${change < 0 ? '−' : '+'}${Math.abs(change).toFixed(2)}%`}
                          direction={direction}
                        />
                      </div>
                    }
                  />
                );
              })}
            </ScrollView>
          </SheetBody>
        </BottomSheet>
      </Stage>
    </section>
  );
};

/* ---------- Page ---------- */

/* The same ending as the Reanimated and Gesture Handler docs. */
const Ending = ({ gobiLight, gobiDark }) => (
  <div className="ending">
    <Reveal className="hire reveal">
      <h2 className="hire-title">
        We are <span>Software Mansion</span>
      </h2>
      <p className="hire-body">
        React Native Core Contributors and experts in dealing with all kinds of
        React Native issues. No matter if you need help with gestures, animations
        or React Native development we can help.
      </p>
      <a
        className="cta press hire-cta"
        href="https://swmansion.com/contact/projects?utm_source=react-native-bottom-sheet&utm_medium=docs"
      >
        Hire us <Arrow />
      </a>
      <a className="sponsor" href="https://www.gobimaps.com">
        <span>Sponsored by</span>
        <img className="for-light" src={gobiLight} alt="Gobi Maps" />
        <img className="for-dark" src={gobiDark} alt="Gobi Maps" />
      </a>
    </Reveal>
    <footer className="site-footer">
      <a className="site-footer-logo" href="https://swmansion.com/" aria-label="Software Mansion">
        <svg width="74" height="40" viewBox="0 0 74 40" fill="currentColor" aria-hidden>
          <path fillRule="evenodd" clipRule="evenodd" d="M11.9946 0.115234H12.3739H72.8476H73.7652L73.7631 1.03209L73.7025 27.4563L73.7016 27.8326L73.4361 28.0993L62.1541 39.4327L61.8857 39.7023H61.505H1.03123H0.115723V38.7876V12.3633V11.9844L0.383869 11.7165L11.7265 0.383161L11.9946 0.115234ZM12.7531 1.94475L3.24145 11.4486H61.1257L70.6374 1.94475H12.7531ZM71.927 3.24346L62.4205 12.7422V36.5707L71.8723 27.0759L71.927 3.24346ZM1.94674 37.8728V13.2781H60.5895V37.8728H1.94674Z" />
          <path d="M16.6804 28.1822C16.6804 30.1822 15.4066 31.8792 12.1919 31.8792C10.7968 31.8792 9.70498 31.5762 8.67383 31.1519V28.4247C9.70498 28.9701 10.9787 29.2731 12.1312 29.2731C13.1624 29.2731 13.7083 28.9701 13.7083 28.364C13.7083 26.5459 8.67383 27.0913 8.67383 23.3337C8.67383 21.0307 10.5542 19.5762 13.1017 19.5762C14.1935 19.5762 15.164 19.8186 16.1951 20.4247V23.3337C14.8001 22.4853 13.8296 22.1822 13.041 22.1822C12.0705 22.1822 11.5853 22.5459 11.5853 23.0913C11.5853 24.8489 16.6804 24.1822 16.6804 28.1822Z" />
          <path d="M36.7575 19.8789L32.9362 31.6365H30.0248L27.4166 23.5759L24.869 31.6365H21.9575L18.1362 19.8789H21.4116L23.5346 27.7577L26.0215 19.8789H28.933L31.4198 27.7577L33.5428 19.8789C33.5428 19.8789 36.7575 19.8789 36.7575 19.8789Z" />
          <path d="M55.0149 23.8792V31.6368H52.1641V24.3641C52.1641 23.0913 51.2543 22.3641 50.2838 22.3641C49.2526 22.3641 48.4034 23.0913 48.4034 24.3641V31.6368H45.492V24.3641C45.492 23.0913 44.5821 22.3641 43.6116 22.3641C42.5805 22.3641 41.7313 23.0913 41.7313 24.3641V31.6368H38.8198V19.8792H41.5493V20.7277C42.3985 19.8792 43.3083 19.5762 44.4002 19.5762C45.6133 19.5762 46.7051 20.1216 47.4329 21.0913C48.4034 20.061 49.6165 19.5762 51.0116 19.5762C53.3165 19.6368 55.0149 21.3944 55.0149 23.8792Z" />
        </svg>
      </a>
      <p>
        © <a href="https://swmansion.com/">Software Mansion</a> {new Date().getFullYear()}.
        <span> All trademarks and copyrights belong to their respective owners. Read about our </span>
        <a href="https://swmansion.com/privacy/policy/">Privacy Policy</a>.
      </p>
    </footer>
  </div>
);

const NotesToggle = () => {
  const [on, setOn] = useState(false);
  useEffect(() => {
    document.documentElement.dataset.notes = on;
  }, [on]);
  return (
    <button className="notes-toggle press" aria-pressed={on} onClick={() => setOn(!on)}>
      <span>{on ? 'Hide design ' : 'Show design '}</span>notes
    </button>
  );
};

const App = () => {
  const { gobiLight, gobiDark } = document.getElementById('root').dataset;
  useSectionSnap();
  useGrabbingCursor();
  useFrameHeight();
  return (
    <div className="sheets">
      <div className="snap-top" aria-hidden />
      <Hero />
      <SheetSection name="--s1" next="--s2" tone="paper" order={1}>
        <DetentsChapter />
      </SheetSection>
      <SheetSection name="--s2" next="--s3" tone="sky" order={2}>
        <NegotiationChapter />
      </SheetSection>
      <SheetSection name="--s3" next="--s4" tone="paper" order={3}>
        <ThreadsChapter />
      </SheetSection>
      <SheetSection name="--s4" tone="ending" order={4}>
        <Ending gobiLight={gobiLight} gobiDark={gobiDark} />
      </SheetSection>
      <NotesToggle />
    </div>
  );
};

const styles = StyleSheet.create({
  fill: { flex: 1 },
  grabberWrap: { alignItems: 'center', paddingTop: 10, paddingBottom: 8 },
  grabber: {
    width: 38,
    height: 5,
    borderRadius: 3,
    transitionProperty: 'background-color',
    transitionDuration: '240ms',
  },
  caseFade: { animationDuration: '240ms', animationKeyframes: { from: { opacity: 0 } } },
  sheetHead: { paddingHorizontal: 20, paddingBottom: 12 },
  sheetTitle: {
    fontFamily: FONT,
    fontSize: 22,
    fontWeight: '500',
    letterSpacing: -0.4,
    color: '#001a72',
  },
  sheetSub: {
    fontFamily: FONT,
    fontSize: 14,
    color: '#33488e',
    marginTop: 2,
    opacity: 0.85,
  },
  sheetCounter: {
    fontFamily: FONT,
    fontSize: 44,
    fontWeight: '500',
    letterSpacing: -1,
    color: '#001a72',
    fontVariant: ['tabular-nums'],
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
  tileText: { fontFamily: FONT, color: '#fcfcff', fontSize: 17, fontWeight: '500' },
  rowTitle: { fontFamily: FONT, fontSize: 15.5, fontWeight: '500' },
  rowMeta: { fontFamily: FONT, fontSize: 13, opacity: 0.68, marginTop: 1 },
  rowTrailing: { fontFamily: FONT, fontSize: 14.5, fontWeight: '500', fontVariant: ['tabular-nums'] },
  tileRound: { borderRadius: 21 },
  sheetActions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 24,
  },
  sheetButton: {
    height: 42,
    paddingHorizontal: 16,
    justifyContent: 'center',
    backgroundColor: '#001a72',
    transitionProperty: 'transform',
    transitionDuration: '160ms',
    transitionTimingFunction: 'cubic-bezier(0.23, 1, 0.32, 1)',
  },
  sheetButtonText: { fontFamily: FONT, color: '#fcfcff', fontSize: 14.5, fontWeight: '500' },
});

createRoot(document.getElementById('root')).render(<App />);
