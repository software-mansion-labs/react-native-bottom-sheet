/// <reference types="bun" />

import { beforeAll, describe, expect, mock, test } from 'bun:test';

// Just enough of React Native for detent normalization and the web host's
// placement, which only reach for these at call time.
mock.module('react-native', () => ({
  I18nManager: { isRTL: false, doLeftAndRightSwapInRTL: true },
  Platform: { OS: 'ios' },
  processColor: (color: unknown) => color,
  StyleSheet: { flatten: (style: unknown) => style },
}));

let normalizeDetents: typeof import('../bottomSheetUtils').normalizeDetents;
let cardSpan: typeof import('../web/detents').cardSpan;
let contentScale: typeof import('../web/detents').contentScale;
let resolvePlacement: typeof import('../cardPlacement').resolvePlacement;

beforeAll(async () => {
  ({ normalizeDetents } = await import('../bottomSheetUtils'));
  ({ cardSpan, contentScale } = await import('../web/detents'));
  ({ resolvePlacement } = await import('../cardPlacement'));
});

describe('normalizeDetents', () => {
  test('carries maxWidth and placement on every detent, the closed one too', () => {
    const detents = normalizeDetents(
      [0, { value: 300, insets: { left: 16, right: 16 } }, '100%'],
      undefined,
      { maxWidth: 540, placementBias: 1 },
      false,
      false
    );
    for (const detent of detents) {
      expect(detent.maxWidth).toBe(540);
      expect(detent.placementBias).toBe(1);
    }
    // A closed detent's insets are 0, whatever was passed.
    expect(detents[0]!.insetLeft).toBe(0);
  });

  test('flags the card with the tightest side insets as widest', () => {
    const detents = normalizeDetents(
      [
        { value: 200, insets: { left: 40, right: 40 } },
        { value: 400, insets: { left: 8, right: 8 } },
      ],
      undefined,
      { maxWidth: 300, placementBias: 0.5 },
      false,
      false
    );
    expect(detents.map((detent) => detent.widestCard)).toEqual([false, true]);
  });
});

describe('web placement', () => {
  const rawDetent = (placementBias: number) => ({
    value: 300,
    kind: 'points',
    programmatic: false,
    usesStatusBarTopInset: true,
    insetTop: 0,
    insetLeft: 0,
    insetRight: 0,
    insetBottom: 0,
    widestCard: true,
    maxWidth: 400,
    placementBias,
    scrimOpacity: 1,
  });
  const host = (placementBias: number) => ({
    elements: { host: { clientWidth: 1000 } } as never,
    rawDetents: [rawDetent(placementBias)],
    widestCardInsets: { insetLeft: 0, insetRight: 0 },
  });
  const noInsets = { insetLeft: 0, insetRight: 0 };

  beforeAll(() => {
    (globalThis as { window?: unknown }).window = { devicePixelRatio: 1 };
  });

  test('places the column by the physical bias JS resolved', () => {
    expect(cardSpan(host(0), noInsets, 1000)).toEqual({ left: 0, width: 400 });
    expect(cardSpan(host(1), noInsets, 1000)).toEqual({
      left: 600,
      width: 400,
    });
  });

  test('a start sheet resolves to the right on a right-to-left page', () => {
    const [detent] = normalizeDetents(
      [{ value: 300 }],
      undefined,
      { maxWidth: 400, placementBias: resolvePlacement('start', true) },
      false,
      true
    );
    expect(cardSpan({ rawDetents: [detent!] }, noInsets, 1000)).toEqual({
      left: 600,
      width: 400,
    });
  });

  test('applies the side insets within the column', () => {
    expect(
      cardSpan(host(0.5), { insetLeft: 16, insetRight: 16 }, 1000)
    ).toEqual({ left: 316, width: 368 });
  });

  test('scales the content by the card’s width over the widest card’s', () => {
    expect(contentScale(host(0.5), noInsets)).toBe(1);
    expect(contentScale(host(0.5), { insetLeft: 100, insetRight: 100 })).toBe(
      0.5
    );
  });
});
