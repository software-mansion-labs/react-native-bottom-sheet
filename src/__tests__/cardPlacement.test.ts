/// <reference types="bun" />

import { describe, expect, test } from 'bun:test';

import {
  columnWidth,
  crowdedInsetsWarning,
  placeCard,
  resolveMaxWidth,
  resolvePlacement,
  type SheetPlacement,
} from '../cardPlacement';

const LEFT = 0;
const CENTER = 0.5;
const RIGHT = 1;

describe('resolvePlacement bias', () => {
  test.each<[SheetPlacement, boolean, number]>([
    ['start', false, LEFT],
    ['center', false, CENTER],
    ['end', false, RIGHT],
    ['start', true, RIGHT],
    ['center', true, CENTER],
    ['end', true, LEFT],
  ])(
    '%s with isRTL %p is %p',
    (placement: SheetPlacement, isRTL: boolean, bias: number) => {
      expect(resolvePlacement(placement, isRTL)).toBe(bias);
    }
  );
});

describe('placeCard', () => {
  test('without a maximum width, the card fills the space between its insets', () => {
    expect(placeCard(400, 16, 8, 0, CENTER)).toEqual({ left: 16, width: 376 });
  });

  test('a maximum width wider than the host leaves the card at its insets', () => {
    expect(placeCard(400, 16, 16, 600, CENTER)).toEqual({
      left: 16,
      width: 368,
    });
  });

  test('a centered card has its middle on the host’s', () => {
    const span = placeCard(1000, 0, 0, 600, CENTER);
    expect(span).toEqual({ left: 200, width: 600 });
    expect(span.left + span.width / 2).toBe(500);
  });

  test('the maximum width includes the side insets', () => {
    // A 600-point column, centered at 200, with the card 16 points in on each side.
    expect(placeCard(1000, 16, 16, 600, CENTER)).toEqual({
      left: 216,
      width: 568,
    });
  });

  test('a capped sheet looks as it would on a screen as wide as its maximum width', () => {
    for (const bias of [LEFT, CENTER, RIGHT]) {
      const capped = placeCard(1000, 24, 8, 540, bias);
      const narrow = placeCard(540, 24, 8, 0, bias);
      const columnLeft = bias * (1000 - 540);
      expect(capped).toEqual({
        left: columnLeft + narrow.left,
        width: narrow.width,
      });
    }
  });

  test('a card keeps the same width as its insets blend while the column stays put', () => {
    // Moving from an attached detent to a floating one shrinks the card within
    // the column, as on a phone, rather than sliding it.
    expect(placeCard(1000, 0, 0, 540, LEFT)).toEqual({ left: 0, width: 540 });
    expect(placeCard(1000, 16, 16, 540, LEFT)).toEqual({
      left: 16,
      width: 508,
    });
  });

  test('uneven insets shift the card within a centered column', () => {
    expect(placeCard(1000, 100, 20, 600, CENTER)).toEqual({
      left: 300,
      width: 480,
    });
  });

  test('a start card sits at the left inset', () => {
    expect(placeCard(1000, 24, 24, 400, LEFT)).toEqual({
      left: 24,
      width: 352,
    });
  });

  test('an end card sits at the right inset', () => {
    expect(placeCard(1000, 24, 24, 400, RIGHT)).toEqual({
      left: 624,
      width: 352,
    });
  });

  test('a card with room never goes past either inset, whatever its placement', () => {
    for (const bias of [LEFT, CENTER, RIGHT]) {
      for (const [width, left, right, max] of [
        [320, 16, 16, 400],
        [1024, 0, 0, 500],
        [1024, 300, 40, 900],
        [768, 0, 300, 400],
      ] as const) {
        const span = placeCard(width, left, right, max, bias);
        expect(span.left).toBeGreaterThanOrEqual(left);
        expect(span.left + span.width).toBeLessThanOrEqual(width - right);
        expect(span.width).toBeLessThanOrEqual(Math.max(0, max - left - right));
      }
    }
  });

  test('insets that fill the column give an empty card', () => {
    expect(placeCard(300, 200, 200, 400, RIGHT)).toEqual({
      left: 200,
      width: 0,
    });
    expect(placeCard(1000, 200, 200, 400, LEFT)).toEqual({
      left: 200,
      width: 0,
    });
  });

  test('a host narrower than the maximum width is filled, as on a phone', () => {
    expect(placeCard(390, 0, 0, 600, LEFT)).toEqual({ left: 0, width: 390 });
  });
});

describe('columnWidth', () => {
  test('is the host’s width without a cap, or under a cap wider than the host', () => {
    expect(columnWidth(800, 0)).toBe(800);
    expect(columnWidth(800, 1000)).toBe(800);
  });

  test('is the cap when the host is wider', () => {
    expect(columnWidth(800, 540)).toBe(540);
  });
});

describe('resolveMaxWidth', () => {
  test('unset or infinite means no cap', () => {
    expect(resolveMaxWidth(undefined)).toBe(0);
    expect(resolveMaxWidth(null as unknown as undefined)).toBe(0);
    expect(resolveMaxWidth(Infinity)).toBe(0);
  });

  test('a positive width is kept', () => {
    expect(resolveMaxWidth(540)).toBe(540);
  });

  test.each([0, -1, NaN, -Infinity, '500' as unknown as number])(
    'rejects %p',
    (value: number) => {
      expect(() => resolveMaxWidth(value)).toThrow(
        /Invalid bottom sheet `maxWidth`/
      );
    }
  );
});

describe('resolvePlacement', () => {
  test('defaults to center', () => {
    expect(resolvePlacement(undefined, false)).toBe(CENTER);
    expect(resolvePlacement(undefined, true)).toBe(CENTER);
  });

  test('flips start and end in a right-to-left layout', () => {
    expect(resolvePlacement('start', true)).toBe(RIGHT);
    expect(resolvePlacement('end', true)).toBe(LEFT);
  });

  test.each<unknown>(['left', 'right', 'leading', 'trailing', '', 0.5])(
    'rejects %p',
    (value: unknown) => {
      expect(() =>
        resolvePlacement(value as unknown as SheetPlacement, false)
      ).toThrow(/Invalid bottom sheet `placement`/);
    }
  );
});

describe('pixel snapping', () => {
  test('a centered card in an odd free width rests on a whole device pixel', () => {
    // 1001 - 600 = 401 free points: 200.5 unsnapped.
    expect(placeCard(1001, 0, 0, 600, CENTER).left).toBe(201);
    expect(placeCard(1001, 0, 0, 600, CENTER, 2).left).toBe(200.5);
    expect(placeCard(1001, 0, 0, 600, CENTER, 3).left).toBeCloseTo(200.667, 3);
  });

  test('the insets apply after the column is snapped', () => {
    expect(placeCard(1001, 16, 16, 600, CENTER).left).toBe(217);
    expect(placeCard(1001, 16, 16, 600, CENTER, 2).left).toBe(216.5);
  });
});

describe('crowdedInsetsWarning', () => {
  const insets = (insetLeft: number, insetRight: number) => ({
    insetLeft,
    insetRight,
  });

  test('is null when every card has room', () => {
    expect(
      crowdedInsetsWarning([insets(16, 16), insets(199, 200)], 400, 0)
    ).toBe(null);
  });

  test('names the first detent whose insets fill the host', () => {
    expect(
      crowdedInsetsWarning([insets(16, 16), insets(200, 200)], 400, 0)
    ).toMatch(
      /detent at index 1: `insets.left` and `insets.right` add up to 400, which leaves no room for the sheet in its 400-point-wide host/
    );
  });

  test('checks the insets against a maximum width narrower than the host', () => {
    expect(
      crowdedInsetsWarning([insets(16, 16), insets(150, 150)], 1000, 300)
    ).toMatch(
      /add up to 300, which leaves no room for the sheet in its 300-point `maxWidth`/
    );
    expect(crowdedInsetsWarning([insets(150, 150)], 1000, 301)).toBe(null);
    // A cap wider than the host leaves the host as the bound.
    expect(crowdedInsetsWarning([insets(150, 150)], 300, 600)).toMatch(
      /in its 300-point-wide host/
    );
  });

  test('is null before the host is laid out', () => {
    expect(crowdedInsetsWarning([insets(300, 300)], 0, 0)).toBe(null);
    expect(crowdedInsetsWarning([insets(300, 300)], NaN, 0)).toBe(null);
  });
});
