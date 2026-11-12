import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';
import clsx from 'clsx';

import { grab } from '../shared/grab';
import { clamp, SlideActiveContext } from '../shared/pieces';
import styles from './styles.module.css';

// The hero is the page as it first loads, so nothing waits on top of it: the
// sheet rests below the screen until a scroll brings it up.
const DETENTS: [number, `${number}%`] = [0, '100%'];
// With reduced motion, the hero only dims behind the sheet.
const STILL_QUERY = '(prefers-reduced-motion: reduce)';

// The sheet's spring takes this long, and input during it is still that gesture.
const SETTLE_MS = 450;
// A wheel gesture ends after this long without events, momentum included.
const WHEEL_QUIET_MS = 140;
// Wheel movement that commits to a step, so a resting hand's jitter doesn't.
const WHEEL_COMMIT = 20;
// How far a finger travels up the hero before the sheet after it comes up.
const SWIPE_COMMIT = 40;

/** Whether something under the pointer, like a list, can use this scroll itself. */
const scrollsInside = (target: EventTarget | null, direction: number) => {
  for (
    let node = target instanceof Element ? target : null;
    node !== null && node !== document.body;
    node = node.parentElement
  ) {
    const { overflowY } = getComputedStyle(node);
    if (
      (overflowY === 'auto' || overflowY === 'scroll') &&
      node.scrollHeight > node.clientHeight + 1
    ) {
      const room =
        direction > 0
          ? node.scrollHeight - node.clientHeight - node.scrollTop
          : node.scrollTop;
      if (room > 1) return true;
    }
  }
  return false;
};

/**
 * Makes one wheel or trackpad gesture move one slide: its first movement commits
 * and the rest of it, momentum included, is ignored. So a flick can't skip a
 * slide. Lists under the pointer still scroll first.
 */
const useWheelSteps = (step: (way: number) => void) => {
  useEffect(() => {
    let spent = false;
    let time = -Infinity;
    let size = 0;
    let direction = 0;
    let travel = 0;
    let lastStep = -Infinity;
    let listOwnsGesture = false;

    const onWheel = (event: WheelEvent) => {
      if (event.ctrlKey || Math.abs(event.deltaX) > Math.abs(event.deltaY)) {
        return;
      }
      const scale =
        event.deltaMode === 1
          ? 16
          : event.deltaMode === 2
            ? window.innerHeight
            : 1;
      const delta = event.deltaY * scale;
      const nextSize = Math.abs(delta);
      const way = Math.sign(delta);
      if (way === 0) return;

      // Momentum fades and never reverses, so a size that jumps back up or a change
      // of direction is a new swipe, not the old one's tail.
      const now = event.timeStamp;
      const isSettling = now - lastStep < SETTLE_MS;
      const isNewSwipe = !isSettling && nextSize > 24 && nextSize > size * 1.6;
      if (now - time > WHEEL_QUIET_MS || isNewSwipe || way !== direction) {
        spent = false;
        travel = 0;
      }
      time = now;
      size = nextSize;
      direction = way;

      // A gesture sends dozens of events, and the check below forces a layout, so
      // it only runs until the gesture is decided.
      if (spent) {
        if (!listOwnsGesture) event.preventDefault();
        return;
      }
      if (scrollsInside(event.target, way)) {
        // Reaching the end of the list shouldn't also change slide mid-flick.
        spent = true;
        listOwnsGesture = true;
        return;
      }

      event.preventDefault();
      listOwnsGesture = false;
      travel += delta;
      if (Math.abs(travel) < WHEEL_COMMIT) return;
      spent = true;
      lastStep = now;
      step(way);
    };

    window.addEventListener('wheel', onWheel, { passive: false });
    return () => window.removeEventListener('wheel', onWheel);
  }, [step]);
};

/**
 * With nothing of the sheet on screen, a finger brings it up by swiping up the
 * hero. A swipe that moved the hero's own sheet is that sheet's: the library
 * cancels its moves.
 */
const useSwipeUp = (
  stack: { current: HTMLElement | null },
  sheet: { current: HTMLElement | null },
  step: (way: number) => void
) => {
  useEffect(() => {
    const target = stack.current!;
    let start: { x: number; y: number } | null = null;
    let isSheetDrag = false;

    const onStart = (event: TouchEvent) => {
      // The sheet takes a finger wherever it lands.
      if (sheet.current!.contains(event.target as Node)) {
        start = null;
        return;
      }
      const touch = event.touches[0]!;
      start =
        event.touches.length === 1
          ? { x: touch.clientX, y: touch.clientY }
          : null;
      isSheetDrag = false;
    };
    const onMove = (event: TouchEvent) => {
      if (event.defaultPrevented) isSheetDrag = true;
    };
    const onEnd = (event: TouchEvent) => {
      const touch = event.changedTouches[0];
      if (start === null || touch === undefined || isSheetDrag) return;
      const dy = start.y - touch.clientY;
      const dx = Math.abs(touch.clientX - start.x);
      start = null;
      if (dy > SWIPE_COMMIT && dy > dx) step(1);
    };

    target.addEventListener('touchstart', onStart, { passive: true });
    target.addEventListener('touchmove', onMove, { passive: true });
    target.addEventListener('touchend', onEnd);
    return () => {
      target.removeEventListener('touchstart', onStart);
      target.removeEventListener('touchmove', onMove);
      target.removeEventListener('touchend', onEnd);
    };
  }, [stack, sheet, step]);
};

const useKeySteps = (step: (way: number) => void) => {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const { target } = event;
      if (
        event.altKey ||
        event.ctrlKey ||
        event.metaKey ||
        (target instanceof HTMLElement &&
          (target.isContentEditable ||
            target.closest('input, textarea, select') !== null))
      ) {
        return;
      }
      const isControl =
        target instanceof Element && target.closest('button, a') !== null;
      let way = 0;
      if (event.key === 'ArrowDown' || event.key === 'PageDown') way = 1;
      else if (event.key === 'ArrowUp' || event.key === 'PageUp') way = -1;
      else if (event.key === ' ' && !isControl) way = event.shiftKey ? -1 : 1;
      else if (event.key === 'Home') way = -1;
      else if (event.key === 'End') way = 1;
      if (way === 0 || scrollsInside(target, way)) return;
      event.preventDefault();
      step(way);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [step]);
};

/**
 * With a mouse only the top strip drags the sheet, so the text on it can be
 * selected. A finger can start anywhere, the way it would scroll a page.
 */
const useMouseDragsHandleOnly = () => {
  const body = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const element = body.current!;
    const onDown = (event: PointerEvent) => {
      if (event.pointerType === 'mouse') event.stopPropagation();
    };
    element.addEventListener('pointerdown', onDown);
    return () => element.removeEventListener('pointerdown', onDown);
  }, []);
  return body;
};

/**
 * The hero, with a real sheet resting below the screen until it's scrolled,
 * swiped, or stepped open over it. As the sheet rises, the hero recedes.
 */
export const SlideStack = ({
  hero,
  sheet,
  label,
}: {
  hero: ReactNode;
  sheet: ReactNode;
  /** Names the sheet for the button that opens it. */
  label: string;
}) => {
  const [isOpen, setIsOpen] = useState(false);
  // Whether the sheet has arrived at the top, so its content can start moving
  // without competing with the sheet's own animation.
  const [isSettled, setIsSettled] = useState(false);
  const settled = useRef(false);
  const root = useRef<HTMLDivElement>(null);
  // Where the sheet moves, kept apart from the hero.
  const area = useRef<HTMLDivElement>(null);
  const receder = useRef<HTMLDivElement>(null);
  const dim = useRef<HTMLDivElement>(null);
  const body = useMouseDragsHandleOnly();
  const position = useRef(0);
  // Kept rather than read each frame, when reading it would force a layout.
  const height = useRef(0);
  const still = useMemo(() => window.matchMedia(STILL_QUERY), []);

  const step = useCallback((way: number) => setIsOpen(way > 0), []);
  useWheelSteps(step);
  useSwipeUp(root, area, step);
  useKeySteps(step);

  // Written directly on the elements that need it, never through React state.
  const applyPosition = useCallback(
    (next: number) => {
      position.current = next;
      const progress = clamp(next / height.current);
      const atTop = next >= height.current - 0.5;
      if (atTop !== settled.current) {
        settled.current = atTop;
        setIsSettled(atTop);
      }
      receder.current!.style.transform =
        progress > 0 && !still.matches
          ? `scale(${1 - 0.06 * progress}) translateY(${-1.5 * progress}%)`
          : '';
      dim.current!.style.opacity = `${0.45 * progress}`;
    },
    [still]
  );

  useEffect(() => {
    const element = area.current!;
    const observer = new ResizeObserver(() => {
      height.current = element.clientHeight;
      applyPosition(position.current);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, [applyPosition]);

  return (
    <div className={styles.stack} ref={root}>
      <div className={styles.receder} inert={isOpen} ref={receder}>
        {hero}
        <div className={styles.dim} ref={dim} />
      </div>
      <div className={styles.area} ref={area}>
        <BottomSheet
          detents={DETENTS}
          // Opens right up to the navbar, with no room kept for a status bar.
          topInset={0}
          index={isOpen ? 1 : 0}
          animateIn={false}
          onIndexChange={(index) => setIsOpen(index === 1)}
          onPositionChange={(event) =>
            applyPosition(event.nativeEvent.position)
          }
          // The surface draws its own shadow. Given one, the library would clip
          // the whole sheet on every frame, repainting all of it; without it the
          // sheet moves as a single GPU transform.
          surface={<div className={styles.surface} />}
        >
          <div className={styles.sheet}>
            <button
              className={clsx(grab, styles.handle)}
              type="button"
              aria-label={`Open ${label}`}
              tabIndex={isOpen ? -1 : 0}
              onClick={() => setIsOpen(true)}
            >
              <span className={styles.grabber} />
            </button>
            <div ref={body} className={styles.body} inert={!isOpen}>
              <SlideActiveContext.Provider value={isOpen && isSettled}>
                {sheet}
              </SlideActiveContext.Provider>
            </div>
          </div>
        </BottomSheet>
      </div>
    </div>
  );
};
