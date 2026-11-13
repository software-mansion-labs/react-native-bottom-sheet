/// <reference types="bun" />

import { describe, expect, test } from 'bun:test';

import {
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
    ['leading', false, LEFT],
    ['center', false, CENTER],
    ['trailing', false, RIGHT],
    ['leading', true, RIGHT],
    ['center', true, CENTER],
    ['trailing', true, LEFT],
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

  test('a maximum width wider than the space leaves the card at its insets', () => {
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

  test('a centered card stays centered on the host with uneven insets that allow it', () => {
    expect(placeCard(1000, 100, 20, 600, CENTER)).toEqual({
      left: 200,
      width: 600,
    });
  });

  test('a centered card moves off-center only as far as an inset requires', () => {
    // Centered it would start at 100, inside the 150 left inset.
    expect(placeCard(1000, 150, 0, 800, CENTER)).toEqual({
      left: 150,
      width: 800,
    });
  });

  test('a leading card sits at the left inset', () => {
    expect(placeCard(1000, 24, 24, 400, LEFT)).toEqual({
      left: 24,
      width: 400,
    });
  });

  test('a trailing card sits at the right inset', () => {
    expect(placeCard(1000, 24, 24, 400, RIGHT)).toEqual({
      left: 576,
      width: 400,
    });
  });

  test('a card never goes past either edge, whatever its placement', () => {
    for (const bias of [LEFT, CENTER, RIGHT]) {
      for (const [width, left, right, max] of [
        [320, 16, 16, 400],
        [1024, 0, 0, 500],
        [1024, 300, 40, 900],
        [768, 0, 700, 400],
      ] as const) {
        const span = placeCard(width, left, right, max, bias);
        expect(span.left).toBeGreaterThanOrEqual(left);
        expect(span.left + span.width).toBeLessThanOrEqual(width - right);
        expect(span.width).toBeLessThanOrEqual(max);
      }
    }
  });

  test('insets that leave no space give an empty card at the left inset', () => {
    expect(placeCard(300, 200, 200, 400, RIGHT)).toEqual({
      left: 200,
      width: 0,
    });
  });

  test('a host narrower than the maximum width is filled, as on a phone', () => {
    expect(placeCard(390, 0, 0, 600, LEFT)).toEqual({ left: 0, width: 390 });
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

  test('flips leading and trailing in a right-to-left layout', () => {
    expect(resolvePlacement('leading', true)).toBe(RIGHT);
    expect(resolvePlacement('trailing', true)).toBe(LEFT);
  });

  test.each<unknown>(['left', 'right', 'start', '', 0.5])(
    'rejects %p',
    (value: unknown) => {
      expect(() =>
        resolvePlacement(value as unknown as SheetPlacement, false)
      ).toThrow(/Invalid bottom sheet `placement`/);
    }
  );
});
