import type { ReactNode } from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  View,
  type ViewStyle,
} from 'react-native';
import clsx from 'clsx';

import landing from '../landing.module.css';
import styles from './example.module.css';

const FONT = 'var(--ifm-font-family-base)';
const INK = '#001a72';

export const Grabber = () => (
  <View style={sheet.grabberWrap}>
    <View style={sheet.grabber} />
  </View>
);

export const Row = ({
  item: [name, meta, color],
  round,
  letter,
  trailing,
}: {
  item: readonly [string, string, string, ...unknown[]];
  round?: boolean;
  letter?: boolean;
  trailing: ReactNode;
}) => (
  <View style={sheet.row}>
    <View
      style={[sheet.tile, round && sheet.tileRound, { backgroundColor: color }]}
    >
      {letter ? <Text style={sheet.tileText}>{name[0]}</Text> : null}
    </View>
    <View style={sheet.rowText}>
      <Text style={sheet.rowTitle}>{name}</Text>
      <Text style={sheet.rowMeta}>{meta}</Text>
    </View>
    {typeof trailing === 'string' ? (
      <Text style={sheet.rowTrailing}>{trailing}</Text>
    ) : (
      trailing
    )}
  </View>
);

export const SHEET_SURFACE = (
  <div
    className={landing.grab}
    style={{
      position: 'absolute',
      inset: 0,
      background: 'var(--swm-white)',
    }}
  />
);

/** For an example that shows its own pointer, so the cursor stays as it is. */
export const PLAIN_SHEET_SURFACE = (
  <div
    style={{
      position: 'absolute',
      inset: 0,
      background: 'var(--swm-white)',
    }}
  />
);

export const ATTACHED = {
  borderTopLeftRadius: 28,
  borderTopRightRadius: 28,
  boxShadow: '0 -6 28 rgba(0, 26, 114, 0.28)',
};

export const SheetButton = ({
  onPress,
  children,
}: {
  onPress: () => void;
  children: string;
}) => (
  <Pressable
    onPress={onPress}
    style={({ pressed }) => [
      sheet.button,
      pressed && { transform: [{ scale: 0.97 }] },
    ]}
  >
    <Text style={sheet.buttonText}>{children}</Text>
  </Pressable>
);

/** All of a sheet is a drag target, so all of it says so. */
export const SheetBody = ({
  fill,
  isGrabbable = true,
  children,
}: {
  fill?: boolean;
  isGrabbable?: boolean;
  children: ReactNode;
}) => (
  <div
    className={clsx(
      isGrabbable && landing.grab,
      styles.sheetBody,
      fill && styles.sheetBodyFill
    )}
  >
    {children}
  </div>
);

/** Shown on a demo until its first drag. */
export const DragHint = ({ seen }: { seen: boolean }) => (
  <div className={styles.dragHint} data-seen={seen} aria-hidden>
    <svg
      width="12"
      height="14"
      viewBox="0 0 12 14"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
    >
      <path d="M6 1v12M2.5 4.5 6 1l3.5 3.5M2.5 9.5 6 13l3.5-3.5" />
    </svg>
    Drag
  </div>
);

/** A closed sheet has nothing left to grab, so offer the way back. */
export const Reopen = ({
  visible,
  onPress,
  children,
}: {
  visible: boolean;
  onPress: () => void;
  children: string;
}) => (
  <button
    className={clsx(styles.reopen, landing.press)}
    data-visible={visible}
    tabIndex={visible ? 0 : -1}
    onClick={onPress}
  >
    {children}
  </button>
);

export const sheet = StyleSheet.create({
  fill: { flex: 1 },
  grabberWrap: { alignItems: 'center', paddingTop: 10, paddingBottom: 8 },
  grabber: {
    width: 38,
    height: 5,
    borderRadius: 3,
    backgroundColor: '#c1c6e5',
  },
  head: { paddingHorizontal: 20, paddingBottom: 12 },
  title: {
    fontFamily: FONT,
    fontSize: 22,
    fontWeight: '500',
    letterSpacing: -0.4,
    color: INK,
  },
  subtitle: {
    fontFamily: FONT,
    fontSize: 14,
    color: '#33488e',
    marginTop: 2,
    opacity: 0.85,
  },
  listPad: { paddingBottom: 36 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 9,
    gap: 12,
  },
  rowText: { flex: 1 },
  tile: {
    width: 42,
    height: 42,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tileRound: { borderRadius: 21 },
  tileText: {
    fontFamily: FONT,
    color: '#fcfcff',
    fontSize: 17,
    fontWeight: '500',
  },
  rowTitle: { fontFamily: FONT, fontSize: 15.5, fontWeight: '500', color: INK },
  rowMeta: {
    fontFamily: FONT,
    fontSize: 13,
    opacity: 0.68,
    marginTop: 1,
    color: INK,
  },
  rowTrailing: {
    fontFamily: FONT,
    fontSize: 14.5,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
    color: INK,
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 24,
  },
  button: {
    height: 42,
    paddingHorizontal: 16,
    justifyContent: 'center',
    backgroundColor: INK,
    // Web-only: eases the press scale.
    ...({
      transitionProperty: 'transform',
      transitionDuration: '160ms',
      transitionTimingFunction: 'cubic-bezier(0.23, 1, 0.32, 1)',
    } as ViewStyle),
  },
  buttonText: {
    fontFamily: FONT,
    color: '#fcfcff',
    fontSize: 14.5,
    fontWeight: '500',
  },
});
