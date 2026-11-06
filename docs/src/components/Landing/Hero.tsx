import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
  type Ref,
} from 'react';
import Link from '@docusaurus/Link';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';
import clsx from 'clsx';

import { Header } from './Header';
import { Arrow, prefersReducedMotion } from './pieces';
import landing from './landing.module.css';
import styles from './Hero.module.css';

const INSTALL = 'npm i @swmansion/react-native-bottom-sheet';
const TOP_INSET = 72;
// The lowest stop keeps the native and react descriptions on the sheet.
const DETENTS: [number, number, `${number}%`] = [216, 320, '100%'];
const LABELS = ['216', '320', "'100%'"];
const LETTERS = ['A', 'B', 'C'];

const ruleStyle = (i: number): CSSProperties =>
  i === 2 ? { top: TOP_INSET } : { bottom: DETENTS[i] as number };

const SURFACE = <div className={clsx(landing.grab, styles.heroSurface)} />;
const SURFACE_STYLE = { borderTopLeftRadius: 36, borderTopRightRadius: 36 };

const CopyInstall = ({ focusable = true }: { focusable?: boolean }) => {
  const [copied, setCopied] = useState(false);
  return (
    <button
      className={clsx(styles.install, landing.press)}
      data-copied={copied}
      tabIndex={focusable ? 0 : -1}
      aria-label="Copy install command"
      onClick={() => {
        void navigator.clipboard?.writeText(INSTALL);
        setCopied(true);
        setTimeout(() => setCopied(false), 1400);
      }}
    >
      <span>{INSTALL}</span>
      <i className={styles.iconSwap} aria-hidden>
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor">
          <rect x="5.5" y="5.5" width="8" height="8" />
          <path d="M10.5 3v-.5h-8v8H3" />
        </svg>
        <svg
          viewBox="0 0 16 16"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.6"
        >
          <path d="m3 8.5 3.2 3.2L13 5" />
        </svg>
      </i>
    </button>
  );
};

/**
 * The hero's copy. It renders twice: dark on the page, and white above the real
 * sheet, clipped to the sheet's edge so the type inverts exactly along it.
 */
const HeroCopy = ({
  navy,
  index,
  onSnap,
  layerRef,
  children,
}: {
  navy?: boolean;
  index: number;
  onSnap: (index: number) => void;
  layerRef?: Ref<HTMLDivElement>;
  children?: ReactNode;
}) => (
  <div
    className={clsx(
      styles.world,
      navy ? styles.worldNavy : styles.worldLight
    )}
    ref={layerRef}
    aria-hidden={navy}
  >
    <div className={styles.heroCopy}>
      <h1 style={{ '--i': 0 } as CSSProperties}>
        <span className={styles.eyebrow}>React Native Bottom Sheet</span>
        <span className={styles.headline}>
          Native mechanics.
          <br />
          Your surface.
        </span>
      </h1>
      <p className={styles.lede} style={{ '--i': 2 } as CSSProperties}>
        Gestures, snapping, and scroll negotiation run in native code.
        Everything you see is plain React Native, on iOS, Android, and the web.
      </p>
      <div className={styles.heroActions} style={{ '--i': 3 } as CSSProperties}>
        <Link
          className={clsx(landing.cta, landing.press)}
          to="/getting-started"
          tabIndex={navy ? -1 : 0}
        >
          Get started <Arrow />
        </Link>
        <CopyInstall focusable={!navy} />
      </div>
    </div>
    {DETENTS.map((_, i) => (
      <div key={i} className={styles.rule} style={ruleStyle(i)} />
    ))}
    {DETENTS.map((_, i) => (
      <button
        key={i}
        className={clsx(styles.callout, landing.press)}
        style={ruleStyle(i)}
        aria-pressed={index === i}
        aria-label={`Snap to detent ${LABELS[i]}`}
        tabIndex={navy ? -1 : 0}
        onClick={() => onSnap(i)}
      >
        <span>{LABELS[i]}</span>
        <i>{LETTERS[i]}</i>
      </button>
    ))}
    {children}
  </div>
);

/** What the server sends: the same copy, in the same place, before the sheet mounts. */
export const HeroPoster = () => (
  <section className={styles.hero}>
    <div className={styles.heroStage}>
      <HeroCopy index={1} onSnap={() => {}} />
      <Header />
    </div>
  </section>
);

export const Hero = () => {
  const [index, setIndex] = useState(1);
  const [touched, setTouched] = useState(false);
  const stage = useRef<HTMLDivElement>(null);
  const navy = useRef<HTMLDivElement>(null);
  const ride = useRef<HTMLDivElement>(null);
  const shownPosition = useRef<HTMLElement>(null);
  const shownIndex = useRef<HTMLElement>(null);
  const last = useRef({ position: 0, index: 0 });
  const snap = (next: number) => {
    setIndex(next);
    setTouched(true);
  };

  // Everything a frame touches is a direct transform or clip-path write on the
  // one element that needs it, never React state or an inherited variable.
  const apply = useCallback((position: number, fractional: number) => {
    last.current = { position, index: fractional };
    const edge = stage.current!.offsetHeight - position;
    navy.current!.style.clipPath = `inset(${edge}px 0 0 0 round 36px 36px 0 0)`;
    ride.current!.style.transform = `translate3d(0, ${-position}px, 0)`;
    shownPosition.current!.textContent = Math.round(position).toString();
    shownIndex.current!.textContent = fractional.toFixed(2);
  }, []);

  useEffect(() => {
    const observer = new ResizeObserver(() =>
      apply(last.current.position, last.current.index)
    );
    observer.observe(stage.current!);
    return () => observer.disconnect();
  }, [apply]);

  return (
    <section className={styles.hero}>
      <div className={styles.heroStage} ref={stage}>
        <HeroCopy index={index} onSnap={snap} />
        <div className={styles.ride} ref={ride}>
          <div className={styles.hint} data-gone={touched}>
            <svg
              width="16"
              height="16"
              viewBox="0 0 16 16"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.6"
              aria-hidden
            >
              <path d="M8 13.5V3M3.5 7.5 8 3l4.5 4.5" />
            </svg>
            Drag me
          </div>
        </div>
        <div className={styles.sheetLayer}>
          <BottomSheet
            detents={DETENTS}
            topInset={TOP_INSET}
            index={index}
            onIndexChange={snap}
            animateIn={!prefersReducedMotion()}
            surface={SURFACE}
            surfaceStyle={SURFACE_STYLE}
            onPositionChange={(event) => {
              const { position, index: fractional } = event.nativeEvent;
              apply(position, fractional);
            }}
          >
            <div className={clsx(landing.grab, styles.heroGrab)}>
              <div className={styles.heroGrabber} />
            </div>
          </BottomSheet>
        </div>
        <HeroCopy navy index={index} onSnap={snap} layerRef={navy}>
          <div className={styles.xray}>
            <div>
              <b>native</b>
              <span>The drag, the snap, and the scroll handoff.</span>
            </div>
            <div>
              <b>react</b>
              <span>The surface and everything on it. Plain views.</span>
            </div>
          </div>
          <div className={styles.readout}>
            onPositionChange · position <b ref={shownPosition}>0</b> · index{' '}
            <b ref={shownIndex}>0.00</b>
          </div>
        </HeroCopy>
        <Header />
      </div>
    </section>
  );
};
