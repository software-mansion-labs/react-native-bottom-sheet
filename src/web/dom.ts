/// <reference lib="dom" />

import type { ColorValue } from 'react-native';

export const readSafeAreaTop = (() => {
  let probe: HTMLElement | null = null;
  return () => {
    if (probe === null) {
      probe = document.createElement('div');
      probe.style.cssText =
        'position:fixed;top:0;left:0;width:0;height:0;visibility:hidden;pointer-events:none;padding-top:env(safe-area-inset-top);';
      document.body.appendChild(probe);
    }
    const value = parseFloat(getComputedStyle(probe).paddingTop);
    return Number.isFinite(value) ? value : 0;
  };
})();

// Presses on a scrollbar drag it rather than the sheet.
export const isScrollbarHit = (event: PointerEvent) => {
  const target = event.target;
  if (!(target instanceof HTMLElement)) return false;
  const hasVerticalScrollbar = target.offsetWidth > target.clientWidth;
  const hasHorizontalScrollbar = target.offsetHeight > target.clientHeight;
  return (
    (hasVerticalScrollbar && event.offsetX > target.clientWidth) ||
    (hasHorizontalScrollbar && event.offsetY > target.clientHeight)
  );
};

export const isTextEditingTarget = (target: EventTarget | null) =>
  target instanceof HTMLElement &&
  (target.isContentEditable ||
    target.tagName === 'INPUT' ||
    target.tagName === 'TEXTAREA' ||
    target.tagName === 'SELECT');

export const clamp = (value: number, min: number, max: number) =>
  Math.min(Math.max(value, min), max);

/**
 * Formats a color for CSS. Platform color objects have no web equivalent and
 * draw transparent.
 */
export const processedColorToCss = (color: ColorValue) => {
  if (typeof color === 'string') return color;
  if (typeof color !== 'number') return 'transparent';
  // Processed colors may arrive as signed 32-bit integers.
  const argb = ((color % 2 ** 32) + 2 ** 32) % 2 ** 32;
  const channel = (shift: number) => Math.floor(argb / 2 ** shift) % 256;
  return `rgba(${channel(16)}, ${channel(8)}, ${channel(0)}, ${channel(24) / 255})`;
};
