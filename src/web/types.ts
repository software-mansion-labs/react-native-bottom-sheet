/// <reference lib="dom" />

import type { ColorValue } from 'react-native';

export type RawDetent = Readonly<{
  value: number;
  kind: string;
  programmatic: boolean;
  usesStatusBarTopInset: boolean;
  insetTop: number;
  insetLeft: number;
  insetRight: number;
  insetBottom: number;
  /** The widest open card, which the content is laid out against. */
  widestCard: boolean;
  /** The scrim’s opacity at this detent, in 0-1. */
  scrimOpacity: number;
}>;

export type ResolvedDetent = Readonly<{
  /**
   * How far the card's top rests above the host's bottom: the card's height
   * plus its bottom inset. Zero when closed, whatever the inset.
   */
  position: number;
  programmatic: boolean;
  insetLeft: number;
  insetRight: number;
  insetBottom: number;
}>;

export type BottomSheetWebConfig = Readonly<{
  detents: ReadonlyArray<RawDetent>;
  index: number;
  animateIn: boolean;
  animateContentHeight: boolean;
  modal: boolean;
  hasCloseRequestHandler: boolean;
  /** When the sheet last opened, relative to other sheets. */
  openOrder: number;
  scrollExpandCoordination: number;
  scrollCollapseCoordination: number;
  /** Physical corner radii: top-left, top-right, bottom-right, bottom-left. */
  cornerRadii: ReadonlyArray<number>;
  shadows: ReadonlyArray<
    Readonly<{
      offsetX: number;
      offsetY: number;
      blurRadius: number;
      spreadDistance: number;
      /** Usually processed: ARGB, or a platform color object. */
      color: ColorValue;
    }>
  >;
}>;

export type BottomSheetWebCallbacks = Readonly<{
  onIndexChange: (index: number) => void;
  onSettle: (index: number) => void;
  onPositionChange: (position: number, index: number) => void;
  onCloseRequest: () => void;
  onError: (message: string) => void;
}>;

export type BottomSheetWebElements = Readonly<{
  host: HTMLElement;
  scrim: HTMLElement;
  /**
   * The scrim's dismiss button for screen readers. Draws nothing and takes no
   * pointer, and is scaled to the part of the scrim above the sheet.
   */
  scrimDismiss: HTMLElement;
  container: HTMLElement;
  /** Wraps the surface and content and is clipped to the card shape. */
  card: HTMLElement;
  /** Sized to the card behind it, and casts the card's shadows. */
  shadow: HTMLElement;
  /** Spans the sheet like `content`, so one transform scales the surface. */
  surface: HTMLElement;
  content: HTMLElement;
}>;
