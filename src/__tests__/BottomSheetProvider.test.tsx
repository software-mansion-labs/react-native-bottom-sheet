import { Children, isValidElement, type ReactNode } from 'react';
import { expect, mock, test } from 'bun:test';

const nativeView = 'NativeView';
const absoluteFill = Object.freeze({ position: 'absolute' });

mock.module('react-native', () => ({
  StyleSheet: { absoluteFill },
  View: nativeView,
}));

const { renderPortalHost } = await import('../PortalHost');

test('zero portals render no native host', () => {
  expect(renderPortalHost([])).toBeNull();
});

test('multiple portals share one non-flattened native host in presentation order', () => {
  const host = renderPortalHost([
    ['lower', 'Lower portal'],
    ['upper', 'Upper portal'],
  ]);

  expect(isValidElement(host)).toBe(true);
  if (
    !isValidElement<{
      collapsable: boolean;
      children: ReactNode;
      pointerEvents: string;
      style: object;
    }>(host)
  ) {
    throw new Error('Expected one native portal host.');
  }
  expect(host.type).toBe(nativeView);
  expect(host.props.collapsable).toBe(false);
  expect(host.props.pointerEvents).toBe('box-none');
  expect(host.props.style).toBe(absoluteFill);

  const wrappers = Children.toArray(host.props.children);
  expect(wrappers).toHaveLength(2);
  expect(
    wrappers.map((wrapper) => {
      if (!isValidElement<{ children: ReactNode }>(wrapper)) {
        throw new Error('Expected a native portal wrapper.');
      }
      return wrapper.props.children;
    })
  ).toEqual(['Lower portal', 'Upper portal']);
});
