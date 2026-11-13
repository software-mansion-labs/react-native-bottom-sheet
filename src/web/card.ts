/// <reference lib="dom" />

import {
  cardSpan,
  contentScale,
  firstOpenPosition,
  type SideInsets,
} from './detents';
import { clamp, processedColorToCss } from './dom';
import type {
  BottomSheetWebConfig,
  BottomSheetWebElements,
  RawDetent,
  ResolvedDetent,
} from './types';

/** The controller's state that the card's shape derives from. */
export type CardHost = {
  readonly elements: BottomSheetWebElements | null;
  readonly config: BottomSheetWebConfig | null;
  readonly hostHeight: number;
  readonly translateY: number;
  readonly detents: ResolvedDetent[];
  readonly rawDetents: ReadonlyArray<RawDetent>;
  readonly widestCardLeft: number;
  readonly widestCardInsets: SideInsets;
  readonly isLayoutRTL: boolean;
};

const NO_INSETS = { insetLeft: 0, insetRight: 0, insetBottom: 0 };

export class SheetCard {
  /** The card's CSS corner radii, even while none is rounded. */
  private cardCorners = '';
  private isCardRounded = false;
  private appliedContentTransform: string | null = null;
  private appliedCardHeight = NaN;
  private appliedCardLeft = NaN;
  private appliedCardRight = NaN;
  private appliedClipInset = NaN;

  constructor(private readonly host: CardHost) {}

  resetContentTransform() {
    this.appliedContentTransform = null;
  }

  /**
   * Applies the card's corners and shadows. They change only with the props,
   * so per-frame updates touch the card's height and clip alone.
   */
  updateCardShape() {
    const { elements, config } = this.host;
    if (elements === null || config === null) return;
    const { shadow } = elements;
    const corners = [0, 1, 2, 3]
      .map((corner) => `${config.cornerRadii[corner] ?? 0}px`)
      .join(' ');
    this.cardCorners = corners;
    this.isCardRounded = config.cornerRadii.some((radius) => radius > 0);
    shadow.style.borderRadius = corners;
    shadow.style.boxShadow = config.shadows
      .map(
        ({ offsetX, offsetY, blurRadius, spreadDistance, color }) =>
          `${offsetX}px ${offsetY}px ${blurRadius}px ${spreadDistance}px ${processedColorToCss(color)}`
      )
      .join(', ');
    this.appliedCardHeight = NaN;
    this.updateCardClip();
  }

  /**
   * Clips the surface and content to the card. At or above the first open
   * detent the card's bottom stays above the bottom gap and its side gaps blend
   * between detents; below it, it keeps that detent's shape and slides down
   * rigidly, so a closing sheet leaves through the gap with its shape intact.
   * Cards with square corners and no gaps stay unclipped.
   */
  updateCardClip() {
    const hostState = this.host;
    const elements = hostState.elements;
    if (elements === null || hostState.config === null) return;
    const { card, content, host, shadow, surface } = elements;
    const position = this.currentPosition();
    const insets = this.cardInsets(position);
    const bottom = insets.insetBottom;
    const span = cardSpan(hostState, insets, host.clientWidth);
    const left = span.left;
    const right = host.clientWidth - span.left - span.width;
    const cardHeight = clamp(position - bottom, 0, hostState.hostHeight);
    const clipInset = hostState.hostHeight - cardHeight;

    // Scale the surface and content to the card's width, with their top-left
    // corners on the card's. The transform origin is the box's, which starts at
    // the container's left, so the layout margin scales along. Ahead of the
    // check below: the widest card can change while this card stays the same.
    const scale = contentScale(hostState, insets);
    const transform =
      scale === 1 && left === hostState.widestCardLeft
        ? ''
        : `translateX(${left - scale * hostState.widestCardLeft}px) scale(${scale})`;
    if (transform !== this.appliedContentTransform) {
      this.appliedContentTransform = transform;
      surface.style.transform = transform;
      content.style.transform = transform;
    }

    if (
      cardHeight === this.appliedCardHeight &&
      clipInset === this.appliedClipInset &&
      left === this.appliedCardLeft &&
      right === this.appliedCardRight
    ) {
      return;
    }
    this.appliedCardHeight = cardHeight;
    this.appliedClipInset = clipInset;
    this.appliedCardLeft = left;
    this.appliedCardRight = right;

    shadow.style.height = `${cardHeight}px`;
    shadow.style.left = `${left}px`;
    shadow.style.right = `${right}px`;
    card.style.clipPath = this.clipsToCard()
      ? `inset(0px ${right}px ${clipInset}px ${left}px round ${this.cardCorners})`
      : '';
  }

  private currentPosition() {
    return Math.max(
      this.host.hostHeight - this.host.translateY,
      firstOpenPosition(this.host.detents)
    );
  }

  /** The height the card has right now, in layout units. */
  currentLayoutHeight() {
    const hostState = this.host;
    const position = this.currentPosition();
    const insets = this.cardInsets(position);
    const height = clamp(
      position - insets.insetBottom,
      0,
      hostState.hostHeight
    );
    const scale = contentScale(hostState, insets);
    return scale > 0 ? height / scale : height;
  }

  /**
   * The card's gaps at a sheet position, blended between the detents around it.
   * Resolved detents ascend by position.
   */
  private cardInsets(position: number) {
    const detents = this.host.detents;
    const upperIndex = detents.findIndex(
      (detent) => position <= detent.position
    );
    if (upperIndex <= 0)
      return detents[upperIndex] ?? detents[detents.length - 1] ?? NO_INSETS;
    const upper = detents[upperIndex]!;
    const lower = detents[upperIndex - 1]!;
    const span = upper.position - lower.position;
    const t = span <= 0 ? 1 : (position - lower.position) / span;
    const blend = (from: number, to: number) => from + (to - from) * t;
    return {
      insetLeft: blend(lower.insetLeft, upper.insetLeft),
      insetRight: blend(lower.insetRight, upper.insetRight),
      insetBottom: blend(lower.insetBottom, upper.insetBottom),
    };
  }

  /** Whether the card is cut out of the surface: rounded, or keeping gaps. */
  private clipsToCard() {
    return (
      this.isCardRounded ||
      this.isNarrowedByMaxWidth() ||
      this.host.rawDetents.some(
        (detent) =>
          detent.insetLeft > 0 ||
          detent.insetRight > 0 ||
          detent.insetBottom > 0
      )
    );
  }

  /**
   * Whether `maxWidth` leaves a gap beside the card in this host. The widest
   * card has the tightest insets, so a cap that doesn't narrow it narrows none.
   */
  private isNarrowedByMaxWidth() {
    const { widestCardInsets } = this.host;
    const width = this.host.elements?.host.clientWidth ?? 0;
    const available =
      width - widestCardInsets.insetLeft - widestCardInsets.insetRight;
    return cardSpan(this.host, widestCardInsets, width).width < available;
  }
}
