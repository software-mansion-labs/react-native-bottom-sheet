import { useEffect, useRef } from 'react';

import styles from './grab.module.css';

/** Marks what drags a sheet, for the grab cursor. */
export const grab = styles.grab!;

/**
 * With a mouse only the top strip drags the sheet, so the text on it can be
 * selected. A finger can start anywhere, the way it would scroll a page.
 */
export const useMouseDragsHandleOnly = () => {
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
 * Holds the grabbing cursor for a whole press on anything marked `grab`. The
 * library follows a drag on the window, so the pointer can leave the sheet or
 * cross a button.
 */
export const useGrabbingCursor = () => {
  useEffect(() => {
    const root = document.documentElement;
    const selector = `.${CSS.escape(grab)}`;
    const onDown = (event: PointerEvent) => {
      if (event.button !== 0 || !(event.target instanceof Element)) return;
      if (event.target.closest(selector) === null) return;
      root.dataset.sheetGrabbing = '';
    };
    const onUp = () => delete root.dataset.sheetGrabbing;
    // A release outside the window may never arrive, but the next move shows it.
    const onMove = (event: PointerEvent) => {
      if (event.buttons === 0 && root.dataset.sheetGrabbing !== undefined) {
        onUp();
      }
    };
    window.addEventListener('pointerdown', onDown, true);
    window.addEventListener('pointermove', onMove, true);
    window.addEventListener('pointerup', onUp, true);
    window.addEventListener('pointercancel', onUp, true);
    window.addEventListener('blur', onUp);
    return () => {
      onUp();
      window.removeEventListener('pointerdown', onDown, true);
      window.removeEventListener('pointermove', onMove, true);
      window.removeEventListener('pointerup', onUp, true);
      window.removeEventListener('pointercancel', onUp, true);
      window.removeEventListener('blur', onUp);
    };
  }, []);
};
