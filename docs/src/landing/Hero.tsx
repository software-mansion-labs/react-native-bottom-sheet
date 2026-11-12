import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
  type FocusEvent,
  type ReactNode,
  type Ref,
} from 'react';
import Link from '@docusaurus/Link';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';
import clsx from 'clsx';

import { grab, useGrabbingCursor, useMouseDragsHandleOnly } from './shared/grab';
import { SwmLogo } from './SwmLogo';
import styles from './Hero.module.css';

/** The sheet's lowest stop, as the stylesheet sets it for the screen's width. */
const useLowestStop = () =>
  useSyncExternalStore(
    (onChange) => {
      window.addEventListener('resize', onChange);
      return () => window.removeEventListener('resize', onChange);
    },
    () =>
      parseFloat(
        getComputedStyle(document.documentElement).getPropertyValue(
          '--hero-stop'
        )
      )
  );

type Marker = {
  label: string;
  letter: string;
  style: { bottom: number } | { top: 0 };
  below: boolean;
};

// The top stop's line is the sheet's top edge, so its label hangs below it.
const markersAt = (stop: number): Marker[] => [
  { label: `${stop}`, letter: 'A', style: { bottom: stop }, below: false },
  { label: '100%', letter: 'B', style: { top: 0 }, below: true },
];

const SURFACE = <div className={styles.surface} />;

/**
 * The hero's copy. It renders twice: in the page's colors, and in the sheet's
 * above the real sheet, clipped to its edge so the colors swap exactly along it.
 * Only the page's copy takes focus. The sheet may cover it, so the copy over
 * the sheet marks the same element as focused, to show it there.
 */
const HeroCopy = ({
  onSheet = false,
  markers = [],
  index,
  onSnap,
  focused = null,
  onFocusedChange,
  layerRef,
  children,
}: {
  onSheet?: boolean;
  markers?: Marker[];
  index: number;
  onSnap: (index: number) => void;
  focused?: string | null;
  onFocusedChange?: (key: string | null) => void;
  layerRef?: Ref<HTMLDivElement>;
  children?: ReactNode;
}) => {
  const focus = (key: string) =>
    onSheet
      ? { 'tabIndex': -1, 'data-focused': focused === key || undefined }
      : {
          onFocus: (event: FocusEvent<HTMLElement>) => {
            if (event.currentTarget.matches(':focus-visible')) {
              onFocusedChange?.(key);
            }
          },
          onBlur: () => onFocusedChange?.(null),
        };
  return (
    <div
      className={clsx(styles.copy, onSheet && styles.copySheet)}
      ref={layerRef}
      aria-hidden={onSheet}
    >
      <div className={styles.creator}>
        Created by
        <a
          href="https://swmansion.com"
          target="_blank"
          rel="noopener"
          {...focus('creator')}
        >
          <SwmLogo />
        </a>
      </div>
      <h1 className={styles.headline}>
        Native mechanics.
        <br />
        Your surface.
      </h1>
      <p className={styles.lede}>
        Gestures, snapping, and scroll coordination run in native code on iOS
        and Android, and in JavaScript on the web. Everything you see is plain
        React Native.
      </p>
      <Link className={styles.cta} to="/docs/getting-started" {...focus('cta')}>
        Get started
        <svg
          width="24"
          height="24"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden
        >
          <path d="M5 12h14M12 5l7 7-7 7" />
        </svg>
      </Link>
      {/* The current stop's line is the sheet's edge, so it's left out. */}
      {markers.map(
        ({ style }, i) =>
          i !== index && <div key={i} className={styles.rule} style={style} />
      )}
      {markers.map(({ label, letter, style, below }, i) => (
        <button
          key={i}
          className={clsx(styles.callout, below && styles.calloutBelow)}
          style={style}
          aria-pressed={index === i}
          aria-label={`Snap to detent ${label}`}
          {...focus(`marker-${i}`)}
          onClick={() => onSnap(i)}
        >
          <span>{label}</span>
          <i>{letter}</i>
        </button>
      ))}
      {children}
    </div>
  );
};

/**
 * What the server sends: the same copy, in the same place, before the sheet
 * mounts. The markers come with the sheet.
 */
export const HeroPoster = () => (
  <section className={styles.hero}>
    <HeroCopy index={0} onSnap={() => {}} />
  </section>
);

export const Hero = () => {
  const stop = useLowestStop();
  const detents = useMemo((): [number, `${number}%`] => [stop, '100%'], [stop]);
  const markers = useMemo(() => markersAt(stop), [stop]);
  const [index, setIndex] = useState(0);
  const [focused, setFocused] = useState<string | null>(null);
  const sheetCopy = useRef<HTMLDivElement>(null);
  const shownPosition = useRef<HTMLElement>(null);
  const shownIndex = useRef<HTMLElement>(null);
  const last = useRef({ position: 0, index: 0 });
  const body = useMouseDragsHandleOnly();
  useGrabbingCursor();

  // Everything a frame touches is a direct style write on the one element that
  // needs it, never React state or an inherited variable.
  const apply = useCallback((position: number, fractional: number) => {
    last.current = { position, index: fractional };
    if (sheetCopy.current === null) return;
    const edge = sheetCopy.current.offsetHeight - position;
    sheetCopy.current.style.clipPath = `inset(${edge}px 0 0 0)`;
    shownPosition.current!.textContent = Math.round(position).toString();
    shownIndex.current!.textContent = fractional.toFixed(2);
  }, []);

  useEffect(() => {
    const observer = new ResizeObserver(() =>
      apply(last.current.position, last.current.index)
    );
    observer.observe(sheetCopy.current!);
    return () => observer.disconnect();
  }, [apply]);

  return (
    <section className={styles.hero}>
      <HeroCopy
        markers={markers}
        index={index}
        onSnap={setIndex}
        onFocusedChange={setFocused}
      />
      <div className={styles.dragHint} aria-hidden>
        <span>Drag me</span>
        {/*
          As the button's arrow: its stroke, and a head of 9.9px arms at 45°.
          The start cap ends on the left edge, the tip on the bottom edge.
        */}
        <svg
          width="100"
          height="64"
          viewBox="0 0 100 64"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M1 14C20 4 44 4 52 16 58 26 46 34 41 26 36 18 50 10 64 16 78 22 88 38 90 63" />
          <path d="M82.46 56.58 90 63l6.42-7.54" />
        </svg>
      </div>
      <div className={styles.sheetLayer}>
        <BottomSheet
          detents={detents}
          topInset={0}
          index={index}
          onIndexChange={setIndex}
          animateIn={false}
          surface={SURFACE}
          onPositionChange={(event) => {
            const { position, index: fractional } = event.nativeEvent;
            apply(position, fractional);
          }}
        >
          <div ref={body} className={styles.sheetBody} />
          <div className={clsx(grab, styles.grabber)} />
        </BottomSheet>
      </div>
      <HeroCopy
        onSheet
        markers={markers}
        index={index}
        onSnap={setIndex}
        focused={focused}
        layerRef={sheetCopy}
      >
        <div className={styles.readout}>
          onPositionChange(<span>position:&nbsp;</span>
          <b ref={shownPosition}>0</b>, <span>index:&nbsp;</span>
          <b ref={shownIndex}>0.00</b>)
        </div>
      </HeroCopy>
    </section>
  );
};
