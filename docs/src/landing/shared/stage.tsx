import {
  Component,
  Fragment,
  useEffect,
  useRef,
  useState,
  type ReactNode,
  type RefObject,
} from 'react';
import type { Detent } from '@swmansion/react-native-bottom-sheet';
import clsx from 'clsx';

import { clamp, useWhenIdle } from './pieces';
import landing from '../landing.module.css';
import styles from './example.module.css';

export const TOP_INSET = 24;

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
