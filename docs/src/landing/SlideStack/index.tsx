import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';
import clsx from 'clsx';

import { clamp, Icon, SlideActiveContext } from '../shared/pieces';
import landing from '../landing.module.css';
import styles from './styles.module.css';

export type Slide = {
  id: string;
  /** Names the slide on the bar that shows before it opens, on small screens. */
  label: string;
  tone: 'paper' | 'sky' | 'ending';
  content: ReactNode;
};

/** How much of the next sheet shows at the bottom of a slide. */
const PEEK = 56;
const DETENTS: [number, `${number}%`] = [PEEK, '100%'];
const RECEDE_QUERY =
  '(min-width: 861px) and (prefers-reduced-motion: no-preference)';

// The sheet's spring takes this long, and input during it is still that gesture.
const SETTLE_MS = 450;
// A wheel gesture ends after this long without events, momentum included.
const WHEEL_QUIET_MS = 140;
// Wheel movement that commits to a step, so a resting hand's jitter doesn't.
const WHEEL_COMMIT = 20;

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

const useKeySteps = (step: (way: number) => void, count: number) => {
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
      else if (event.key === 'Home') way = -count;
      else if (event.key === 'End') way = count;
      if (way === 0 || scrollsInside(target, way)) return;
      event.preventDefault();
      step(way);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [step, count]);
};

/**
 * Holds the grabbing cursor for a whole press on a sheet. The library follows a
 * drag on the window, so the pointer can leave the sheet or cross a button.
 */
const useGrabbingCursor = () => {
  useEffect(() => {
    const root = document.documentElement;
    const grab = `.${CSS.escape(landing.grab!)}`;
    const onDown = (event: PointerEvent) => {
      if (event.button !== 0 || !(event.target instanceof Element)) return;
      if (event.target.closest(grab) === null) return;
      root.dataset.sheetGrabbing = '';
    };
    const onUp = () => delete root.dataset.sheetGrabbing;
    window.addEventListener('pointerdown', onDown, true);
    window.addEventListener('pointerup', onUp, true);
    window.addEventListener('pointercancel', onUp, true);
    return () => {
      onUp();
      window.removeEventListener('pointerdown', onDown, true);
      window.removeEventListener('pointerup', onUp, true);
      window.removeEventListener('pointercancel', onUp, true);
    };
  }, []);
};

/**
 * With a mouse only the top strip drags a slide, so the text on it can be
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

const SlideSheet = ({
  slide,
  open,
  isCurrent,
  isNext,
  hasNext,
  followsHero,
  onOpenChange,
  onPosition,
  onOpen,
}: {
  slide: Slide;
  open: boolean;
  isCurrent: boolean;
  isNext: boolean;
  hasNext: boolean;
  followsHero: boolean;
  onOpenChange: (open: boolean) => void;
  onPosition: (position: number) => void;
  onOpen: () => void;
}) => {
  const body = useMouseDragsHandleOnly();
  const nextBar = useRef<HTMLSpanElement>(null);
  return (
    <div className={styles.host}>
      <BottomSheet
        detents={DETENTS}
        // Without it, receding moves this host above the screen, which the
        // library reads as a status bar and shortens the sheet's '100%' for.
        topInset={0}
        index={open ? 1 : 0}
        animateIn={false}
        onIndexChange={(index) => onOpenChange(index === 1)}
        onPositionChange={(event) => {
          const { position } = event.nativeEvent;
          onPosition(position);
          // Only reads as a label at rest: it clears as the sheet opens.
          if (nextBar.current) {
            nextBar.current.style.opacity = `${1 - clamp((position - PEEK) / (PEEK * 2))}`;
          }
        }}
        // The surface draws its own corners and shadow. Given them, the library
        // would clip the whole slide on every frame, repainting all of it; without
        // them a slide moves as a single GPU transform.
        surface={<div className={styles.surface} data-tone={slide.tone} />}
      >
        <div className={styles.slide}>
          <button
            className={clsx(landing.grab, styles.handle)}
            type="button"
            aria-label={`Open ${slide.label}`}
            tabIndex={isNext ? 0 : -1}
            onClick={isNext ? onOpen : undefined}
          >
            <span className={styles.grabber} />
            <span
              ref={nextBar}
              className={styles.nextBar}
              data-follows-hero={followsHero}
            >
              <span>
                <small>Next</small>
                {slide.label}
              </span>
              <Icon d="M6 15l6-6 6 6" size={20} />
            </span>
          </button>
          <div
            ref={body}
            className={styles.body}
            data-slide-body
            data-tone={slide.tone}
            data-has-next={hasNext}
            inert={!isCurrent}
          >
            <SlideActiveContext.Provider value={isCurrent}>
              {slide.content}
            </SlideActiveContext.Provider>
          </div>
        </div>
      </BottomSheet>
    </div>
  );
};

/**
 * The page's slides: each one is a real sheet, resting with its top edge showing
 * until it's dragged, flicked, or stepped open over the one before. As a sheet
 * moves, it brings the next one along below it and sends the last one back.
 */
export const SlideStack = ({
  hero,
  slides,
}: {
  hero: ReactNode;
  slides: Slide[];
}) => {
  // 0 is the hero, and each slide after it is one higher.
  const [active, setActive] = useState(0);
  const root = useRef<HTMLDivElement>(null);
  const layers = useRef<(HTMLDivElement | null)[]>([]);
  const receders = useRef<(HTMLDivElement | null)[]>([]);
  const dims = useRef<(HTMLDivElement | null)[]>([]);
  const positions = useRef<number[]>([]);
  // Kept rather than read each frame, when reading it would force a layout.
  const height = useRef(0);
  const recedes = useMemo(() => window.matchMedia(RECEDE_QUERY), []);
  const count = slides.length;

  const step = useCallback(
    (way: number) => setActive((current) => clamp(current + way, 0, count)),
    [count]
  );
  useWheelSteps(step);
  useKeySteps(step, count);
  useGrabbingCursor();

  // Written directly on the elements that need it, never through React state.
  const applyPosition = useCallback(
    (layer: number, position: number) => {
      positions.current[layer] = position;
      const next = layers.current[layer + 1];
      if (next) {
        next.style.transform = `translate3d(0, ${height.current - position}px, 0)`;
      }

      const below = receders.current[layer - 1];
      const dim = dims.current[layer - 1];
      if (!below || !dim) return;
      const progress = recedes.matches
        ? clamp((position - PEEK) / (height.current - PEEK))
        : 0;
      below.style.transform =
        progress > 0
          ? `scale(${1 - 0.06 * progress}) translateY(${-1.5 * progress}%)`
          : '';
      below.style.borderRadius = progress > 0 ? '28px' : '';
      dim.style.opacity = `${0.45 * progress}`;
    },
    [recedes]
  );

  // Sheets further on wait below the screen until the one before brings them up.
  useLayoutEffect(() => {
    height.current = root.current!.clientHeight;
    layers.current.slice(2).forEach((layer) => {
      if (layer) layer.style.transform = 'translate3d(0, 100%, 0)';
    });
  }, []);

  useEffect(() => {
    const element = root.current!;
    const observer = new ResizeObserver(() => {
      height.current = element.clientHeight;
      positions.current.forEach((position, layer) =>
        applyPosition(layer, position)
      );
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, [applyPosition]);

  return (
    <div className={styles.stack} ref={root}>
      <div
        className={styles.layer}
        data-covered={active > 1}
        ref={(element) => {
          layers.current[0] = element;
        }}
      >
        <div
          className={styles.receder}
          inert={active !== 0}
          ref={(element) => {
            receders.current[0] = element;
          }}
        >
          {hero}
          <div
            className={styles.dim}
            ref={(element) => {
              dims.current[0] = element;
            }}
          />
        </div>
      </div>
      {slides.map((slide, i) => {
        const layer = i + 1;
        return (
          <div
            key={slide.id}
            className={styles.layer}
            data-covered={active > layer + 1}
            ref={(element) => {
              layers.current[layer] = element;
            }}
          >
            <div
              className={styles.receder}
              ref={(element) => {
                receders.current[layer] = element;
              }}
            >
              <SlideSheet
                slide={slide}
                open={active >= layer}
                isCurrent={active === layer}
                isNext={active === layer - 1}
                hasNext={layer < count}
                followsHero={layer === 1}
                onOpenChange={(open) => setActive(open ? layer : layer - 1)}
                onPosition={(position) => applyPosition(layer, position)}
                onOpen={() => setActive(layer)}
              />
              <div
                className={styles.dim}
                ref={(element) => {
                  dims.current[layer] = element;
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
};
