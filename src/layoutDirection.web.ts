/// <reference lib="dom" />

/**
 * Whether the layout runs right to left. React Native for Web's `I18nManager`
 * doesn't report a direction, and its logical styles follow CSS, so this reads
 * the document's direction. A subtree with its own `dir` is not detected.
 */
export const isLayoutRTL = () =>
  typeof document !== 'undefined' &&
  getComputedStyle(document.documentElement).direction === 'rtl';
