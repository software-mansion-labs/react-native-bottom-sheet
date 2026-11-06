import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
} from 'react';
import type { ScrollCoordinationMode } from '@swmansion/react-native-bottom-sheet';

import { hasTouchGestures, Hint, Toggle } from './controls';

export const EXPAND_HINTS: Record<ScrollCoordinationMode, string> = {
  none: 'Dragging up in the list never moves the sheet.',
  initial:
    'Dragging up moves the sheet, which stops at the top until you lift. Drag up again to scroll the list.',
  handoff:
    'Dragging up moves the sheet to the top, then scrolls the list without lifting.',
};

export const COLLAPSE_HINTS: Record<ScrollCoordinationMode, string> = {
  none: 'Dragging down in the list never moves the sheet.',
  initial:
    'Dragging down from a scrolled list only scrolls it. From its start, it moves the sheet.',
  handoff:
    'Dragging down scrolls the list to its start, then moves the sheet without lifting.',
};

export const MOBILE_PREVIEW_HINT =
  'This simulates a phone, so a mouse drag acts like a finger, shown as a circle.';

/** How far a mouse moves before a press counts as a drag. */
const DRAG_SLOP = 4;

const FINGER_STYLE: CSSProperties = {
  position: 'absolute',
  zIndex: 1,
  top: -18,
  left: -18,
  width: 36,
  height: 36,
  borderRadius: '50%',
  border: '1px solid rgba(255, 255, 255, 0.7)',
  backgroundColor: 'rgba(120, 130, 160, 0.45)',
  opacity: 0,
  pointerEvents: 'none',
  transition: 'opacity 120ms ease-out',
};

/** Shows where a simulated finger is, for as long as it's pressed on the stage. */
export const FingerIndicator = () => {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const finger = ref.current!;
    const stage = finger.parentElement!;
    let isPressed = false;

    const place = ({ clientX, clientY }: PointerEvent) => {
      const { left, top } = stage.getBoundingClientRect();
      finger.style.transform = `translate(${clientX - left}px, ${clientY - top}px)`;
    };
    const onDown = (event: PointerEvent) => {
      if (event.button !== 0 || !stage.contains(event.target as Node)) return;
      isPressed = true;
      place(event);
      finger.style.opacity = '1';
    };
    const onMove = (event: PointerEvent) => {
      if (isPressed) place(event);
    };
    const onUp = () => {
      isPressed = false;
      finger.style.opacity = '0';
    };

    // Captured, as the sheet's own gesture handling sees these events first.
    window.addEventListener('pointerdown', onDown, true);
    window.addEventListener('pointermove', onMove, true);
    window.addEventListener('pointerup', onUp, true);
    window.addEventListener('pointercancel', onUp, true);
    return () => {
      window.removeEventListener('pointerdown', onDown, true);
      window.removeEventListener('pointermove', onMove, true);
      window.removeEventListener('pointerup', onUp, true);
      window.removeEventListener('pointercancel', onUp, true);
    };
  }, []);

  return <div ref={ref} style={FINGER_STYLE} aria-hidden />;
};

/**
 * Whether a mouse should act like a finger. A touch device already has the real
 * gestures, so it has nothing to preview.
 */
export const useMobilePreview = () => {
  const [isMobilePreview, setIsMobilePreview] = useState(true);
  const [isTouchDevice] = useState(hasTouchGestures);
  return { isMobilePreview, setIsMobilePreview, isTouchDevice };
};

/** The switch for a mobile preview, and what each state of it means. */
export const MobilePreviewToggle = ({
  value,
  onChange,
}: {
  value: boolean;
  onChange: (value: boolean) => void;
}) => (
  <>
    <Toggle label="Mobile preview" value={value} onChange={onChange} />
    <Hint>
      {value
        ? MOBILE_PREVIEW_HINT
        : "Most of these gestures are for touch, so a mouse on the web mostly won't do them. Dragging only scrolls the list during a handoff. Otherwise, use the wheel."}
    </Hint>
  </>
);

/**
 * A mouse can't drag a list the way a finger does, so this does it for the drags
 * the sheet leaves to the list. The sheet handles the others itself. Goes on the
 * list's direct parent, and listens natively, as a page can stop a press from
 * reaching React.
 */
export const useListMouseDrag = (options: {
  isEnabled: boolean;
  /** Whether the sheet has a larger detent to move to. */
  canExpandSheet: boolean;
  /** Whether the sheet has a smaller detent to move to. */
  canCollapseSheet: boolean;
  expand: ScrollCoordinationMode;
  collapse: ScrollCoordinationMode;
}) => {
  const latest = useRef(options);
  latest.current = options;

  // A ref callback, as the list can mount after this does.
  return useCallback((parent: HTMLDivElement) => {
    const onDown = (event: PointerEvent) => {
      const list = parent.firstElementChild as HTMLElement;
      // A press on the list itself, rather than a row, is on its scrollbar.
      if (
        !latest.current.isEnabled ||
        event.pointerType !== 'mouse' ||
        event.button !== 0 ||
        event.target === list
      ) {
        return;
      }

      const startY = event.clientY;
      let lastY: number | null = null;

      const listOwnsDrag = (dy: number) => {
        const { canExpandSheet, canCollapseSheet, expand, collapse } =
          latest.current;
        const isExpanding = dy < 0;
        const canMoveSheet = isExpanding ? canExpandSheet : canCollapseSheet;
        const mode = isExpanding ? expand : collapse;
        if (!canMoveSheet || mode === 'none') return true;
        return mode === 'initial' && !isExpanding && list.scrollTop > 0;
      };

      const onMove = ({ clientY }: PointerEvent) => {
        if (lastY === null) {
          const dy = clientY - startY;
          if (Math.abs(dy) < DRAG_SLOP) return;
          if (!listOwnsDrag(dy)) {
            stop();
            return;
          }
          lastY = clientY;
          return;
        }
        list.scrollTop -= clientY - lastY;
        lastY = clientY;
      };
      const stop = () => {
        window.removeEventListener('pointermove', onMove);
        window.removeEventListener('pointerup', stop);
        window.removeEventListener('pointercancel', stop);
      };

      window.addEventListener('pointermove', onMove);
      window.addEventListener('pointerup', stop);
      window.addEventListener('pointercancel', stop);
    };

    parent.addEventListener('pointerdown', onDown);
    return () => parent.removeEventListener('pointerdown', onDown);
  }, []);
};
