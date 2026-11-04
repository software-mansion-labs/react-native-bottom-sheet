/// <reference lib="dom" />

import type { ColorValue } from 'react-native';

import { CONTENT_HEIGHT_MARKER_NATIVE_ID } from './bottomSheetUtils';

// The web counterpart of `BottomSheetHostingView.swift` and
// `BottomSheetHostView.kt`. It owns the same geometry, snapping, scrim, and
// scrollable-negotiation behavior, driven by DOM measurement, pointer and touch
// events, and requestAnimationFrame instead of a native view hierarchy. The
// sheet is moved by writing the container's transform directly, so a drag or
// settle never re-renders React.

export type RawDetent = Readonly<{
  value: number;
  kind: string;
  programmatic: boolean;
  insetTop: number;
  insetLeft: number;
  insetRight: number;
  insetBottom: number;
}>;

type ResolvedDetent = Readonly<{
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
  extendUnderStatusBar: boolean;
  scrollableExpandNegotiation: number;
  scrollableCollapseNegotiation: number;
  scrimOpacities: ReadonlyArray<number>;
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
  container: HTMLElement;
  /** Wraps the surface and content and is clipped to the card shape. */
  card: HTMLElement;
  /** Sized to the card behind it, and casts the card's shadows. */
  shadow: HTMLElement;
  content: HTMLElement;
}>;

// `scrollableNegotiation` levels: none, initial (1), and handoff.
const NEGOTIATION_NONE = 0;
const NEGOTIATION_HANDOFF = 2;

const SPRING_DURATION_MS = 450;
// Pick the stiffness so the sheet looks settled (within ~0.5% of target) right
// at the duration. For a critically-damped spring that point is ω·t ≈ 8.
const SPRING_OMEGA = 8 / (SPRING_DURATION_MS / 1000);
const FLICK_VELOCITY = 600;
const EDGE_TOLERANCE = 0.5;
// Movement, in CSS pixels, before a pointer is treated as a drag rather than a
// press. Below the browsers' own touch-scroll slop, so the sheet decides
// ownership before native scrolling can begin.
const DRAG_SLOP = 6;
const VELOCITY_SAMPLE_WINDOW_MS = 100;
const CLICK_SUPPRESSION_MS = 400;
// Per-millisecond velocity retention of a released manual scroll, matching
// UIScrollView's normal deceleration rate.
const SCROLL_DECELERATION_RATE = 0.998;
const SCROLL_MOMENTUM_MIN_VELOCITY = 20;

/** Critically damped (ζ = 1) spring; see `CriticalSpring.swift`. */
class CriticalSpring {
  constructor(
    readonly from: number,
    readonly target: number,
    /** Initial velocity in pixels per second. */
    readonly v0: number,
    /** `performance.now()` timestamp the curve starts at. */
    readonly startTime: number
  ) {}

  value(time: number) {
    const t = Math.max(0, time - this.startTime) / 1000;
    const a = this.from - this.target;
    const decay = Math.exp(-SPRING_OMEGA * t);
    return this.target + decay * (a + (this.v0 + SPRING_OMEGA * a) * t);
  }

  isFinished(time: number) {
    return time - this.startTime >= SPRING_DURATION_MS;
  }
}

type ScrollableEntry = Readonly<{ element: HTMLElement; inverted: boolean }>;

type GestureMode = 'pending' | 'ignored' | 'sheet' | 'scroll';

type Gesture = {
  kind: 'touch' | 'mouse';
  pointerId: number | null;
  touchId: number | null;
  startX: number;
  startY: number;
  lastY: number;
  samples: { time: number; y: number }[];
  chain: ScrollableEntry[];
  mode: GestureMode;
  // In `scroll` mode, whether this controller scrolls the chain itself (it has
  // been preventing the browser's own scrolling) or the browser is scrolling.
  manualScroll: boolean;
  negotiationLevel: number;
  startingIndex: number;
  dragDetents: ResolvedDetent[];
  dragRange: { minTy: number; maxTy: number };
  didMoveSheet: boolean;
  scrollOwnsLowerBoundary: boolean;
};

// Moves already claimed by a nested sheet's gesture. Every sheet under the
// pointer tracks it, and the innermost one decides first since the event
// bubbles outward; an enclosing sheet only takes the gesture if it declines.
const claimedEvents = new WeakSet<Event>();

// Modal sheets with a close-request handler that are open or animating closed,
// in the order they opened. Escape is routed to the most recent one, matching
// Android's topmost-presentation routing.
const closeRequestStack: BottomSheetWebController[] = [];
let areEscapeListenersInstalled = false;
// Whether an Escape press that may become a close request is in progress: it
// began with an unmodified, non-repeating keydown and has not been canceled.
let isEscapePressPending = false;

const hasModifiers = (event: KeyboardEvent) =>
  event.altKey || event.ctrlKey || event.metaKey || event.shiftKey;

const topCloseRequestController = () =>
  closeRequestStack[closeRequestStack.length - 1];

// Like on Android, a close request is emitted once per completed, unmodified
// Escape press: on keyup. Auto-repeat, modifiers, other keys, and focus loss
// cancel the press. Escape is consumed while a sheet owns it either way.
const handleEscapeKeyDown = (event: KeyboardEvent) => {
  if (event.key !== 'Escape') {
    isEscapePressPending = false;
    return;
  }
  if (event.repeat) {
    if (isEscapePressPending && topCloseRequestController() !== undefined) {
      event.preventDefault();
    }
    return;
  }
  isEscapePressPending =
    !event.defaultPrevented && !event.isComposing && !hasModifiers(event);
  if (isEscapePressPending && topCloseRequestController() !== undefined) {
    event.preventDefault();
  }
};

const handleEscapeKeyUp = (event: KeyboardEvent) => {
  if (event.key !== 'Escape' || !isEscapePressPending) return;
  isEscapePressPending = false;
  const controller = topCloseRequestController();
  if (
    controller === undefined ||
    event.defaultPrevented ||
    hasModifiers(event)
  ) {
    return;
  }
  event.preventDefault();
  controller.handleCloseRequest();
};

const cancelEscapePress = () => {
  isEscapePressPending = false;
};

const readSafeAreaTop = (() => {
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

const isVerticallyScrollable = (element: HTMLElement) => {
  const { overflowY } = getComputedStyle(element);
  return (
    (overflowY === 'auto' || overflowY === 'scroll') &&
    element.scrollHeight > element.clientHeight
  );
};

// React Native for Web inverts lists with a negative vertical scale.
const isInverted = (element: HTMLElement) => {
  const { transform } = getComputedStyle(element);
  if (transform === 'none') return false;
  const match = /^matrix\(([^)]+)\)$/.exec(transform);
  const d = match ? Number(match[1]!.split(',')[3]) : 1;
  return d < 0;
};

const maxScrollTop = (element: HTMLElement) =>
  Math.max(0, element.scrollHeight - element.clientHeight);

/** Whether a scrollable shows the visual top of its content. */
const isAtStart = (entry: ScrollableEntry, requireExactOffset = false) => {
  const tolerance = requireExactOffset ? 0 : EDGE_TOLERANCE;
  const { element } = entry;
  return entry.inverted
    ? element.scrollTop >= maxScrollTop(element) - tolerance
    : element.scrollTop <= tolerance;
};

// Presses on a scrollbar drag it rather than the sheet.
const isScrollbarHit = (event: PointerEvent) => {
  const target = event.target;
  if (!(target instanceof HTMLElement)) return false;
  const hasVerticalScrollbar = target.offsetWidth > target.clientWidth;
  const hasHorizontalScrollbar = target.offsetHeight > target.clientHeight;
  return (
    (hasVerticalScrollbar && event.offsetX > target.clientWidth) ||
    (hasHorizontalScrollbar && event.offsetY > target.clientHeight)
  );
};

const isTextEditingTarget = (target: EventTarget | null) =>
  target instanceof HTMLElement &&
  (target.isContentEditable ||
    target.tagName === 'INPUT' ||
    target.tagName === 'TEXTAREA' ||
    target.tagName === 'SELECT');

/**
 * The tallest the card can be at a detent: the cap, less the detent's top and
 * bottom insets. Percentages resolve against it, so `100%` fits.
 */
const availableCardHeight = (spec: RawDetent, cap: number) =>
  Math.max(0, cap - spec.insetTop - spec.insetBottom);

/**
 * The smallest sum of side insets across the open detents, and the left inset
 * that goes with it: the gaps of the widest card, which the content is laid out
 * against. On a tie, the tallest detent.
 */
const findWidestCard = (detents: ReadonlyArray<RawDetent>) => {
  let gaps = Infinity;
  let left = 0;
  for (const detent of detents) {
    if (detent.kind !== 'content' && detent.value === 0) continue;
    const detentGaps = detent.insetLeft + detent.insetRight;
    if (detentGaps <= gaps) {
      gaps = detentGaps;
      left = detent.insetLeft;
    }
  }
  return { gaps: gaps === Infinity ? 0 : gaps, left };
};

const clamp = (value: number, min: number, max: number) =>
  Math.min(Math.max(value, min), max);

/**
 * Formats a color for CSS. Platform color objects have no web equivalent and
 * draw transparent.
 */
const processedColorToCss = (color: ColorValue) => {
  if (typeof color === 'string') return color;
  if (typeof color !== 'number') return 'transparent';
  // Processed colors may arrive as signed 32-bit integers.
  const argb = ((color % 2 ** 32) + 2 ** 32) % 2 ** 32;
  const channel = (shift: number) => Math.floor(argb / 2 ** shift) % 256;
  return `rgba(${channel(16)}, ${channel(8)}, ${channel(0)}, ${channel(24) / 255})`;
};

export class BottomSheetWebController {
  private elements: BottomSheetWebElements | null = null;
  private callbacks: BottomSheetWebCallbacks | null = null;
  // Events raised while props are applied, which happens in a React layout
  // effect. Like native events, they are delivered asynchronously, so a
  // handler that sets state does not nest a render inside the commit.
  private deferredEvents: (() => void)[] | null = null;
  private config: BottomSheetWebConfig | null = null;
  private appliedIndex: number | null = null;

  private rawDetents: ReadonlyArray<RawDetent> = [];
  private detents: ResolvedDetent[] = [];
  private targetIndex = 0;
  private pendingIndex: number | null = null;
  private hasLaidOut = false;
  private cap = 0;
  private lastAppliedCap = NaN;
  private translateY = 0;
  private lastReportedError: string | null = null;

  private spring: CriticalSpring | null = null;
  private springTargetIndex = 0;
  private springEmitsSettle = false;
  private scrimPinnedFull = false;

  private gesture: Gesture | null = null;
  private isPanning = false;
  private suppressClicksUntil = 0;
  private momentum: { entry: ScrollableEntry; velocity: number } | null = null;
  private restoreUserSelect: (() => void) | null = null;

  private frameRequest: number | null = null;
  private lastFrameTime = 0;
  private needsLayout = false;
  /** The card's CSS corner radii, or null while no corner is rounded. */
  private cardCorners = '';
  private isCardRounded = false;
  private appliedCardHeight = NaN;
  private appliedCardLeft = NaN;
  private appliedCardRight = NaN;
  private appliedClipInset = NaN;
  private resizeObserver: ResizeObserver | null = null;
  private mutationObserver: MutationObserver | null = null;

  // MARK: Lifecycle

  attach(elements: BottomSheetWebElements) {
    this.elements = elements;
    const { host, scrim, container, content } = elements;
    // `clip`, unlike `hidden`, does not make the host a scroll container that
    // focusing an input in the translated sheet could scroll.
    host.style.overflow = 'clip';
    container.style.visibility = 'hidden';
    scrim.style.visibility = 'hidden';
    scrim.style.opacity = '0';
    scrim.style.pointerEvents = 'none';
    this.updateCardShape();
    if (this.hasLaidOut) {
      // Reattached after a detach that kept the sheet's state, as when Strict
      // Mode replays effects: restore where the sheet is and resume any settle
      // in flight. The spring is time-based, so it picks up where it would be.
      this.setTranslateY(this.translateY);
      this.updateSheetVisibility(this.cap - this.translateY);
      if (this.spring !== null) {
        this.requestFrame();
      }
    }

    this.resizeObserver = new ResizeObserver(this.scheduleLayout);
    this.mutationObserver = new MutationObserver(this.observeContent);
    this.observeContent();
    this.mutationObserver.observe(content, { childList: true });

    container.addEventListener('pointerdown', this.handlePointerDown);
    container.addEventListener('touchstart', this.handleTouchStart, {
      passive: true,
    });
    container.addEventListener('touchmove', this.handleTouchMove, {
      passive: false,
    });
    container.addEventListener('touchend', this.handleTouchEnd);
    container.addEventListener('touchcancel', this.handleTouchEnd);
    container.addEventListener('click', this.handleContainerClick, true);
    scrim.addEventListener('click', this.handleScrimPress);
  }

  detach() {
    const elements = this.elements;
    if (elements === null) return;
    const { scrim, container } = elements;
    container.removeEventListener('pointerdown', this.handlePointerDown);
    this.setWindowPointerListeners(false);
    container.removeEventListener('touchstart', this.handleTouchStart);
    container.removeEventListener('touchmove', this.handleTouchMove);
    container.removeEventListener('touchend', this.handleTouchEnd);
    container.removeEventListener('touchcancel', this.handleTouchEnd);
    container.removeEventListener('click', this.handleContainerClick, true);
    scrim.removeEventListener('click', this.handleScrimPress);
    this.resizeObserver?.disconnect();
    this.mutationObserver?.disconnect();
    this.resizeObserver = null;
    this.mutationObserver = null;
    if (this.frameRequest !== null) {
      cancelAnimationFrame(this.frameRequest);
      this.frameRequest = null;
    }
    this.restoreUserSelect?.();
    this.restoreUserSelect = null;
    this.gesture = null;
    this.isPanning = false;
    this.momentum = null;
    this.setCloseRequestRegistration(false);
    this.elements = null;
  }

  setCallbacks(callbacks: BottomSheetWebCallbacks) {
    this.callbacks = callbacks;
  }

  /** Applies the latest props, in the order the native hosts receive them. */
  update(config: BottomSheetWebConfig) {
    const isOutermostUpdate = this.deferredEvents === null;
    this.deferredEvents ??= [];
    try {
      this.applyConfig(config);
    } finally {
      if (isOutermostUpdate) {
        const events = this.deferredEvents;
        this.deferredEvents = null;
        if (events.length > 0) {
          queueMicrotask(() => events.forEach((event) => event()));
        }
      }
    }
  }

  private applyConfig(config: BottomSheetWebConfig) {
    const previous = this.config;
    this.config = config;
    if (previous !== null && config.openOrder > previous.openOrder) {
      this.raiseCloseRequestRegistration();
    }
    if (
      previous !== null &&
      previous.scrimOpacities !== config.scrimOpacities
    ) {
      this.updateScrim();
    }
    if (
      previous === null ||
      !areNumbersEqual(previous.cornerRadii, config.cornerRadii) ||
      !areShadowsEqual(previous.shadows, config.shadows)
    ) {
      this.updateCardShape();
    }
    if (
      previous === null ||
      !areRawDetentsEqual(previous.detents, config.detents)
    ) {
      this.rawDetents = config.detents;
    }

    const indexChanged = this.appliedIndex !== config.index;
    this.appliedIndex = config.index;
    const wasLaidOut = this.hasLaidOut;
    if (indexChanged && !wasLaidOut) {
      this.setDetentIndex(config.index);
    }
    this.layout();
    if (indexChanged && wasLaidOut) {
      this.setDetentIndex(config.index);
    }
    this.updateScrim();
    this.updateCloseRequestRegistration();
  }

  private emit(event: (callbacks: BottomSheetWebCallbacks) => void) {
    const dispatch = () => {
      if (this.callbacks !== null) event(this.callbacks);
    };
    if (this.deferredEvents !== null) {
      this.deferredEvents.push(dispatch);
    } else {
      dispatch();
    }
  }

  /** Whether React Native for Web should hand the responder to the sheet. */
  readonly shouldCaptureResponder = () =>
    this.gesture !== null &&
    (this.gesture.mode === 'sheet' ||
      (this.gesture.mode === 'scroll' && this.gesture.manualScroll));

  handleCloseRequest() {
    // While a visible sheet is animating to a closed target, further requests
    // are consumed without another callback until it settles.
    if (this.isTargetClosed()) return;
    this.emit((callbacks) => callbacks.onCloseRequest());
  }

  // MARK: Geometry

  private readonly observeContent = () => {
    const observer = this.resizeObserver;
    const elements = this.elements;
    if (observer === null || elements === null) return;
    observer.disconnect();
    observer.observe(elements.host);
    // The content height is read from the marker's position, which moves when
    // any of the content wrapper's children resizes.
    const wrapper = elements.content.firstElementChild;
    if (wrapper !== null) {
      this.mutationObserver?.observe(wrapper, { childList: true });
      for (const child of Array.from(wrapper.children)) {
        observer.observe(child);
      }
    }
    this.scheduleLayout();
  };

  private readonly scheduleLayout = () => {
    this.needsLayout = true;
    this.requestFrame();
  };

  private requireConfig(): BottomSheetWebConfig {
    return this.config!;
  }

  /**
   * The detent cap: the host's height minus the part of the top safe-area
   * inset that overlaps it, unless the sheet may extend under the status bar.
   */
  private resolveCap(hostHeight: number) {
    const { host } = this.elements!;
    if (this.requireConfig().extendUnderStatusBar) return hostHeight;
    const topOverlap = Math.max(
      0,
      readSafeAreaTop() - host.getBoundingClientRect().top
    );
    return clamp(hostHeight - topOverlap, 0, hostHeight);
  }

  /**
   * The height the content wrapper is laid out in: the card's height at the
   * tallest detent, clamped to the space its insets leave. A trailing `content`
   * detent is bounded by that space alone, since it is measured from this very
   * region.
   */
  private contentRegionHeight(cap: number) {
    const tallest = this.rawDetents[this.rawDetents.length - 1];
    if (tallest === undefined) return cap;
    const available = availableCardHeight(tallest, cap);
    let bound: number;
    switch (tallest.kind) {
      case 'points':
        bound = tallest.value;
        break;
      case 'percentage':
        bound = available * tallest.value;
        break;
      default:
        bound = available;
    }
    const height = clamp(bound, 0, available);
    const scale = this.contentScale(tallest);
    return scale > 0
      ? Math.min(height / scale, this.elements!.host.clientHeight)
      : height;
  }

  /**
   * How much the content is scaled at a detent: its card's width over the width
   * of the widest card, which the content is laid out against.
   */
  private contentScale(spec: RawDetent) {
    const width = this.elements?.host.clientWidth ?? 0;
    const widest = width - findWidestCard(this.rawDetents).gaps;
    if (widest <= 0) return 1;
    return Math.max(0, width - spec.insetLeft - spec.insetRight) / widest;
  }

  private measureContentHeight(): number | null {
    const wrapper = this.elements?.content.firstElementChild;
    if (wrapper == null) return null;
    let marker: Element | null = null;
    for (const child of Array.from(wrapper.children)) {
      if (child.id === CONTENT_HEIGHT_MARKER_NATIVE_ID) {
        marker = child;
        break;
      }
    }
    if (!(marker instanceof HTMLElement) || !(wrapper instanceof HTMLElement)) {
      return null;
    }
    // Layout offsets ignore the sheet's own transform. Rendered rectangles
    // would pick up its sub-pixel translation as noise in the content height,
    // which would re-anchor the sheet, which would move it again.
    const height =
      marker.offsetParent === wrapper
        ? marker.offsetTop
        : marker.offsetTop - wrapper.offsetTop;
    return Number.isFinite(height) && height > 0 ? height : null;
  }

  private layout() {
    this.needsLayout = false;
    const elements = this.elements;
    if (elements === null || this.config === null) return;
    const { host, container, content } = elements;
    const hostHeight = host.clientHeight;
    if (hostHeight <= 0 || host.clientWidth <= 0) return;

    const cap = this.resolveCap(hostHeight);
    this.cap = cap;
    container.style.top = `${hostHeight - cap}px`;
    container.style.height = `${cap}px`;
    content.style.height = `${this.contentRegionHeight(cap)}px`;
    this.updateCardClip();

    this.resolveDetentsFromLayout();
    this.replayPendingIndexIfResolved();
    this.lastAppliedCap = cap;

    if (!this.hasLaidOut && this.detents.length > 0) {
      const indexToApply = this.pendingIndex ?? this.targetIndex;
      const clampedIndex = clamp(indexToApply, 0, this.detents.length - 1);

      if (
        this.config.animateIn &&
        this.isInvalidContentDetentTarget(clampedIndex)
      ) {
        // Wait for the content to be measured before animating in.
        this.targetIndex = clampedIndex;
        this.pendingIndex = clampedIndex;
        this.setTranslateY(cap);
        this.emitPosition();
        return;
      }

      this.hasLaidOut = true;
      this.pendingIndex = null;
      this.targetIndex = clampedIndex;

      if (this.config.animateIn) {
        this.setTranslateY(cap);
        this.emitPosition();
        this.snapToIndex(this.targetIndex, 0, false, true);
      } else {
        this.setTranslateY(this.translationY(this.targetIndex));
        this.emitPosition();
      }
      return;
    }

    if (this.spring !== null || this.isPanning) return;
    if (this.hasLaidOut) {
      this.setTranslateY(this.translationY(this.targetIndex));
    }
    this.updateScrim();
  }

  private resolveDetentSpecs(): ResolvedDetent[] | null {
    const maxHeight = this.cap;
    const contentHeight = this.measureContentHeight();
    const measuredContentHeight =
      maxHeight > 0 && contentHeight !== null
        ? Math.min(contentHeight, maxHeight)
        : null;
    const resolved: ResolvedDetent[] = [];

    for (const [index, spec] of this.rawDetents.entries()) {
      const available = availableCardHeight(spec, maxHeight);
      let height: number;
      switch (spec.kind) {
        case 'points':
          height = spec.value;
          break;
        case 'percentage':
          height = available * spec.value;
          break;
        default:
          height =
            (measuredContentHeight !== null
              ? measuredContentHeight * this.contentScale(spec)
              : null) ?? this.unresolvedContentDetentHeight(index, maxHeight);
      }
      const resolvedHeight = clamp(height, 0, available);
      const position =
        resolvedHeight > 0 ? resolvedHeight + spec.insetBottom : 0;
      const previous = resolved[resolved.length - 1];
      if (previous !== undefined && position < previous.position) {
        const message = `Invalid bottom sheet detent at index ${index}: resolved position ${position} is lower than previous detent position ${previous.position}. Detents must be passed in ascending order.`;
        if (this.lastReportedError !== message) {
          this.lastReportedError = message;
          this.emit((callbacks) => callbacks.onError(message));
        }
        return null;
      }
      resolved.push({
        position,
        programmatic: spec.programmatic,
        insetLeft: spec.insetLeft,
        insetRight: spec.insetRight,
        insetBottom: spec.insetBottom,
      });
    }

    this.lastReportedError = null;
    return resolved;
  }

  private unresolvedContentDetentHeight(index: number, maxHeight: number) {
    for (const spec of this.rawDetents.slice(index + 1)) {
      if (spec.kind === 'content') continue;
      const available = availableCardHeight(spec, maxHeight);
      const height =
        spec.kind === 'points' ? spec.value : available * spec.value;
      // Whatever its inset, this detent must not rise above the next one's top.
      return (
        clamp(height, 0, available) +
        spec.insetBottom -
        this.rawDetents[index]!.insetBottom
      );
    }
    return maxHeight;
  }

  private resolveDetentsFromLayout() {
    if (
      this.hasLaidOut &&
      this.isInvalidContentDetentTarget(this.targetIndex)
    ) {
      return;
    }
    const resolved = this.resolveDetentSpecs();
    if (resolved === null) return;
    const cap = this.cap;
    // Also fall through when only the cap changed: translations derive from
    // the cap, so they must be re-anchored even when the detents are unchanged.
    if (
      areResolvedDetentsEqual(resolved, this.detents) &&
      cap === this.lastAppliedCap
    ) {
      return;
    }

    // The re-anchor math preserves the on-screen sheet height across the
    // geometry change, so relate the current translation to the cap it was
    // computed against.
    const previousCap = Number.isFinite(this.lastAppliedCap)
      ? this.lastAppliedCap
      : cap;
    const firstOpenPosition = this.firstOpenPosition();
    const wasScrimFull =
      this.hasLaidOut &&
      !this.isPanning &&
      this.requireConfig().modal &&
      firstOpenPosition > 0 &&
      previousCap - this.currentTranslateY() + 0.5 >= firstOpenPosition;
    this.scrimPinnedFull = this.scrimPinnedFull || wasScrimFull;
    this.detents = resolved;
    this.lastAppliedCap = cap;

    if (this.detents.length === 0 || !this.hasLaidOut || this.isPanning) {
      return;
    }

    this.targetIndex = clamp(this.targetIndex, 0, this.detents.length - 1);
    const targetTy = this.translationY(this.targetIndex);

    if (this.spring !== null) {
      const emitsSettle = this.springEmitsSettle;
      const visualTy = this.cancelSpring();
      // Re-anchor the in-flight position so the sheet keeps the same on-screen
      // height across the resize.
      const visibleHeight = previousCap - visualTy;
      this.setTranslateY(clamp(cap - visibleHeight, 0, cap));
      this.emitPosition();
      this.snapToIndex(this.targetIndex, 0, false, emitsSettle, true);
      return;
    }

    const currentVisibleHeight = previousCap - this.translateY;
    const targetHeight = this.detentAt(this.targetIndex).position;
    if (
      Math.abs(targetHeight - currentVisibleHeight) <= 0.5 ||
      !this.shouldAnimateContentHeight(this.targetIndex)
    ) {
      this.setTranslateY(targetTy);
      this.emitPosition();
      this.scrimPinnedFull = false;
    } else {
      // The target detent changed height: re-anchor at the current visible
      // height, then animate to the new one. The surface covers the full
      // sheet, so a shrink never exposes blank space.
      this.setTranslateY(clamp(cap - currentVisibleHeight, 0, cap));
      this.emitPosition();
      this.snapToIndex(this.targetIndex, 0, false, false, true);
    }
  }

  private shouldAnimateContentHeight(index: number) {
    const spec = this.rawDetents[index];
    if (spec === undefined) return this.requireConfig().animateContentHeight;
    return this.requireConfig().animateContentHeight || spec.kind !== 'content';
  }

  private isInvalidContentDetentTarget(index: number) {
    const spec = this.rawDetents[index];
    if (spec === undefined || spec.kind !== 'content') return false;
    return this.measureContentHeight() === null;
  }

  // MARK: Detents and positions

  private detentAt(index: number): ResolvedDetent {
    return (
      this.detents[index] ?? {
        position: 0,
        programmatic: false,
        insetLeft: 0,
        insetRight: 0,
        insetBottom: 0,
      }
    );
  }

  private translationY(index: number) {
    return this.cap - this.detentAt(index).position;
  }

  private snapCandidateIndices(
    including: number | null,
    detents: ResolvedDetent[] = this.detents
  ) {
    const indices = new Set<number>();
    detents.forEach((detent, index) => {
      if (!detent.programmatic) indices.add(index);
    });
    if (including !== null && detents[including] !== undefined) {
      indices.add(including);
    }
    return Array.from(indices).sort(
      (a, b) => detents[a]!.position - detents[b]!.position
    );
  }

  private draggableRange(
    including: number | null,
    detents: ResolvedDetent[] = this.detents
  ) {
    const candidates = this.snapCandidateIndices(including, detents);
    if (candidates.length === 0) return { minTy: 0, maxTy: 0 };
    const translations = candidates.map(
      (index) => this.cap - detents[index]!.position
    );
    return {
      minTy: Math.min(...translations),
      maxTy: Math.max(...translations),
    };
  }

  private bestSnapIndex(
    position: number,
    velocity: number,
    including: number | null,
    detents: ResolvedDetent[]
  ) {
    const candidates = this.snapCandidateIndices(including, detents);
    if (candidates.length === 0) return this.targetIndex;

    if (velocity < -FLICK_VELOCITY) {
      return (
        candidates.find((index) => detents[index]!.position > position) ??
        candidates[candidates.length - 1]!
      );
    }
    if (velocity > FLICK_VELOCITY) {
      return (
        candidates.findLast((index) => detents[index]!.position < position) ??
        candidates[0]!
      );
    }
    return candidates.reduce((best, index) =>
      Math.abs(detents[index]!.position - position) <
      Math.abs(detents[best]!.position - position)
        ? index
        : best
    );
  }

  private get closedIndex() {
    const index = this.detents.findIndex((detent) => detent.position === 0);
    return index === -1 ? null : index;
  }

  private get scrimDismissIndex() {
    const closedIndex = this.closedIndex;
    if (closedIndex === null || this.detents[closedIndex]!.programmatic) {
      return null;
    }
    return closedIndex;
  }

  private isTargetClosed() {
    return this.closedIndex !== null && this.targetIndex === this.closedIndex;
  }

  private currentTranslateY(time = performance.now()) {
    return this.spring !== null ? this.spring.value(time) : this.translateY;
  }

  private currentSheetHeight() {
    return this.cap - this.currentTranslateY();
  }

  private setTranslateY(ty: number) {
    this.translateY = ty;
    const container = this.elements?.container;
    if (container) {
      container.style.transform = `translate3d(0px, ${ty}px, 0px)`;
    }
    this.updateCardClip();
  }

  /**
   * Applies the card's corners and shadows. They change only with the props,
   * so per-frame updates touch the card's height and clip alone.
   */
  private updateCardShape() {
    const elements = this.elements;
    const config = this.config;
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
  private updateCardClip() {
    const elements = this.elements;
    if (elements === null || this.config === null) return;
    const { card, content, host, shadow } = elements;
    const position = Math.max(
      this.cap - this.translateY,
      this.firstOpenPosition()
    );
    const left = this.interpolateInset(position, 'insetLeft');
    const right = this.interpolateInset(position, 'insetRight');
    const bottom = this.interpolateInset(position, 'insetBottom');
    const cardHeight = clamp(position - bottom, 0, this.cap);
    const clipInset = this.cap - cardHeight;
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

    // Scale the content to the card's width, with its top-left corner on the
    // card's. The transform origin is the content box's, which starts at the
    // container's left, so the layout margin scales along.
    const widestCard = findWidestCard(this.rawDetents);
    const widest = host.clientWidth - widestCard.gaps;
    const scale =
      widest > 0 ? Math.max(0, host.clientWidth - left - right) / widest : 1;
    content.style.transformOrigin = '0 0';
    content.style.transform =
      scale === 1 && left === widestCard.left
        ? ''
        : `translateX(${left - scale * widestCard.left}px) scale(${scale})`;

    shadow.style.height = `${cardHeight}px`;
    shadow.style.left = `${left}px`;
    shadow.style.right = `${right}px`;
    card.style.clipPath = this.clipsToCard()
      ? `inset(0px ${right}px ${clipInset}px ${left}px round ${this.cardCorners})`
      : '';
  }

  private firstOpenPosition() {
    return this.detents.find((detent) => detent.position > 0)?.position ?? 0;
  }

  private interpolateInset(
    position: number,
    side: 'insetLeft' | 'insetRight' | 'insetBottom'
  ) {
    return this.interpolate(
      position,
      this.detents.map((detent) => detent[side])
    );
  }

  /** Whether the card is cut out of the surface: rounded, or keeping gaps. */
  private clipsToCard() {
    return (
      this.isCardRounded ||
      this.rawDetents.some(
        (detent) =>
          detent.insetLeft > 0 ||
          detent.insetRight > 0 ||
          detent.insetBottom > 0
      )
    );
  }

  private setDetentIndex(newIndex: number) {
    if (newIndex < 0) return;

    if (!this.hasLaidOut) {
      this.pendingIndex = newIndex;
      this.targetIndex = newIndex;
      return;
    }

    if (newIndex >= this.detents.length) {
      // The resolved detents lag the raw ones while they fail validation;
      // retain the index and replay it once they resolve.
      if (newIndex < this.rawDetents.length) {
        this.pendingIndex = newIndex;
      }
      return;
    }
    // A newer request supersedes a retained one.
    this.pendingIndex = null;
    if (newIndex === this.targetIndex) return;
    this.snapToIndex(newIndex, 0, false);
  }

  private replayPendingIndexIfResolved() {
    const index = this.pendingIndex;
    if (
      !this.hasLaidOut ||
      this.isPanning ||
      index === null ||
      index >= this.detents.length
    ) {
      return;
    }
    this.pendingIndex = null;
    this.setDetentIndex(index);
  }

  // MARK: Animation

  private snapToIndex(
    index: number,
    velocity: number,
    emitIndexChange = true,
    emitSettle = true,
    preserveScrimPin = false
  ) {
    if (index < 0 || index >= this.detents.length) return;
    if (emitIndexChange) {
      // A user-driven snap supersedes an index retained from props.
      this.pendingIndex = null;
    }

    this.targetIndex = index;
    if (!preserveScrimPin) {
      this.scrimPinnedFull = false;
    }

    const now = performance.now();
    const currentTy =
      this.spring !== null ? this.cancelSpring(now) : this.translateY;
    const targetTy = this.translationY(index);
    const distance = targetTy - currentTy;
    const velocityRatio = distance !== 0 ? velocity / distance : 0;
    const v0 = clamp(velocityRatio, -5, 5) * distance;

    this.springEmitsSettle = emitSettle;
    this.springTargetIndex = index;
    this.spring = new CriticalSpring(currentTy, targetTy, v0, now);
    this.requestFrame();
    this.updateCloseRequestRegistration();

    // Report the index change as soon as the snap is committed; `onSettle`
    // remains the signal for the end of movement.
    if (emitIndexChange) {
      this.emit((callbacks) => callbacks.onIndexChange(index));
    }
  }

  private cancelSpring(time = performance.now()) {
    const visualTy = this.currentTranslateY(time);
    this.spring = null;
    this.springEmitsSettle = false;
    this.setTranslateY(visualTy);
    return visualTy;
  }

  private finishSpring() {
    if (this.spring === null) return;
    const index = this.springTargetIndex;
    const emitSettle = this.springEmitsSettle;
    this.spring = null;
    this.springEmitsSettle = false;
    this.setTranslateY(this.translationY(index));
    this.scrimPinnedFull = false;
    this.emitPosition();
    this.updateCloseRequestRegistration();
    if (emitSettle) {
      this.emit((callbacks) => callbacks.onSettle(index));
    }
  }

  private requestFrame() {
    if (this.frameRequest !== null || this.elements === null) return;
    this.frameRequest = requestAnimationFrame(this.handleFrame);
  }

  private readonly handleFrame = (time: number) => {
    this.frameRequest = null;
    const elapsed = this.lastFrameTime > 0 ? time - this.lastFrameTime : 16;
    this.lastFrameTime = time;
    if (this.needsLayout) {
      this.layout();
    }
    const spring = this.spring;
    if (spring !== null) {
      if (spring.isFinished(time)) {
        this.finishSpring();
      } else {
        this.setTranslateY(spring.value(time));
        this.emitPosition();
      }
    }
    if (this.momentum !== null) {
      this.stepMomentum(Math.min(elapsed, 64));
    }
    if (this.spring !== null || this.momentum !== null || this.needsLayout) {
      this.requestFrame();
    } else {
      this.lastFrameTime = 0;
    }
  };

  // MARK: Position, scrim, and visibility

  private emitPosition() {
    const position = this.cap - this.translateY;
    this.updateScrim(position);
    this.updateSheetVisibility(position);
    const index = this.detentIndex(position);
    this.emit((callbacks) => callbacks.onPositionChange(position, index));
  }

  private updateSheetVisibility(position: number) {
    const container = this.elements?.container;
    if (!container) return;
    container.style.visibility = position <= 0.5 ? 'hidden' : 'visible';
  }

  private updateScrim(position = this.currentSheetHeight()) {
    const scrim = this.elements?.scrim;
    if (!scrim || this.config === null) return;
    let opacity = 0;
    // Until the first layout places the sheet, and while settled on the
    // closed detent (where detent updates can report a stale non-zero
    // position), keep the scrim hidden.
    const isSettledClosed =
      this.isTargetClosed() && this.spring === null && !this.isPanning;
    if (this.config.modal && this.hasLaidOut && !isSettledClosed) {
      // While the sheet is fully open and only its geometry is resizing, hold
      // the fully-open opacity instead of dipping it until the re-anchor ends.
      opacity = this.scrimPinnedFull
        ? this.scrimOpacity(
            Math.max(0, ...this.detents.map((detent) => detent.position))
          )
        : this.scrimOpacity(position);
    }
    const isVisible = opacity > 0.001;
    scrim.style.opacity = String(isVisible ? opacity : 0);
    scrim.style.visibility = isVisible ? 'visible' : 'hidden';
    scrim.style.pointerEvents = isVisible ? 'auto' : 'none';
  }

  private scrimOpacity(position: number) {
    const opacities = this.requireConfig().scrimOpacities;
    const values = this.detents.map((_, index) =>
      clamp(opacities[Math.min(index, opacities.length - 1)] ?? 1, 0, 1)
    );
    return this.interpolate(position, values);
  }

  /** Fractional detent index, interpolated by position between detents. */
  private detentIndex(position: number) {
    return this.interpolate(
      position,
      this.detents.map((_, index) => index)
    );
  }

  private interpolate(position: number, values: number[]) {
    const pairs = this.detents
      .map((detent, index) => ({
        position: detent.position,
        value: values[index]!,
      }))
      .sort((a, b) => a.position - b.position);
    const first = pairs[0];
    const last = pairs[pairs.length - 1];
    if (first === undefined || last === undefined) return 0;
    if (position <= first.position) return first.value;
    if (position >= last.position) return last.value;
    for (let i = 1; i < pairs.length; i++) {
      const upper = pairs[i]!;
      if (position <= upper.position) {
        const lower = pairs[i - 1]!;
        const span = upper.position - lower.position;
        const t = span <= 0 ? 1 : (position - lower.position) / span;
        return lower.value + (upper.value - lower.value) * t;
      }
    }
    return last.value;
  }

  private readonly handleScrimPress = () => {
    const dismissIndex = this.scrimDismissIndex;
    if (
      this.config === null ||
      !this.config.modal ||
      dismissIndex === null ||
      this.targetIndex === dismissIndex ||
      (this.spring !== null && this.currentSheetHeight() <= 0.5)
    ) {
      return;
    }
    this.snapToIndex(dismissIndex, 0);
  };

  private updateCloseRequestRegistration() {
    const config = this.config;
    this.setCloseRequestRegistration(
      config !== null &&
        config.modal &&
        config.hasCloseRequestHandler &&
        this.hasLaidOut &&
        (!this.isTargetClosed() || this.spring !== null)
    );
  }

  /**
   * Escape goes to the most recently opened sheet. One that reopens while
   * still closing is already registered, so it moves to the top.
   */
  private raiseCloseRequestRegistration() {
    const index = closeRequestStack.indexOf(this);
    if (index === -1 || index === closeRequestStack.length - 1) return;
    closeRequestStack.splice(index, 1);
    closeRequestStack.push(this);
  }

  private setCloseRequestRegistration(isRegistered: boolean) {
    const index = closeRequestStack.indexOf(this);
    if (isRegistered === (index !== -1)) return;
    if (isRegistered) {
      closeRequestStack.push(this);
      if (!areEscapeListenersInstalled) {
        areEscapeListenersInstalled = true;
        window.addEventListener('keydown', handleEscapeKeyDown);
        window.addEventListener('keyup', handleEscapeKeyUp);
        window.addEventListener('blur', cancelEscapePress);
      }
    } else {
      closeRequestStack.splice(index, 1);
    }
  }

  // MARK: Gestures

  private readonly handlePointerDown = (event: PointerEvent) => {
    // Touch input is handled through touch events, whose moves can be
    // prevented one at a time to keep the browser from scrolling.
    if (
      event.pointerType === 'touch' ||
      event.button !== 0 ||
      isTextEditingTarget(event.target) ||
      isScrollbarHit(event)
    ) {
      return;
    }
    this.beginGesture('mouse', event.clientX, event.clientY, event.target);
    this.gesture!.pointerId = event.pointerId;
    // A fast drag can leave the sheet before it is recognized, so follow the
    // pointer on the window. Capturing it here instead would retarget the
    // click of a plain press away from the content.
    this.setWindowPointerListeners(true);
  };

  private setWindowPointerListeners(isListening: boolean) {
    if (isListening) {
      window.addEventListener('pointermove', this.handlePointerMove, true);
      window.addEventListener('pointerup', this.handlePointerUp, true);
      window.addEventListener('pointercancel', this.handlePointerUp, true);
    } else {
      window.removeEventListener('pointermove', this.handlePointerMove, true);
      window.removeEventListener('pointerup', this.handlePointerUp, true);
      window.removeEventListener('pointercancel', this.handlePointerUp, true);
    }
  }

  private readonly handlePointerMove = (event: PointerEvent) => {
    const gesture = this.gesture;
    if (gesture === null || gesture.pointerId !== event.pointerId) return;
    if (this.moveGesture(event, event.clientX, event.clientY)) {
      event.preventDefault();
    }
  };

  private readonly handlePointerUp = (event: PointerEvent) => {
    if (this.gesture?.pointerId === event.pointerId) {
      this.endGesture();
    }
  };

  private readonly handleTouchStart = (event: TouchEvent) => {
    const touch = event.changedTouches[0];
    if (
      touch === undefined ||
      this.gesture !== null ||
      event.touches.length > 1
    ) {
      return;
    }
    this.beginGesture('touch', touch.clientX, touch.clientY, event.target);
    this.gesture!.touchId = touch.identifier;
  };

  private readonly handleTouchMove = (event: TouchEvent) => {
    const gesture = this.gesture;
    if (gesture === null) return;
    const touch = Array.from(event.changedTouches).find(
      (candidate) => candidate.identifier === gesture.touchId
    );
    if (touch === undefined) return;
    if (
      this.moveGesture(event, touch.clientX, touch.clientY) &&
      event.cancelable
    ) {
      event.preventDefault();
    }
  };

  private readonly handleTouchEnd = (event: TouchEvent) => {
    const gesture = this.gesture;
    if (
      gesture !== null &&
      Array.from(event.changedTouches).some(
        (touch) => touch.identifier === gesture.touchId
      )
    ) {
      this.endGesture();
    }
  };

  private readonly handleContainerClick = (event: MouseEvent) => {
    // A click that ends a sheet drag must not press the content under it.
    if (performance.now() < this.suppressClicksUntil) {
      event.preventDefault();
      event.stopPropagation();
    }
  };

  private beginGesture(
    kind: Gesture['kind'],
    x: number,
    y: number,
    target: EventTarget | null
  ) {
    this.momentum = null;
    this.suppressClicksUntil = 0;
    this.gesture = {
      kind,
      pointerId: null,
      touchId: null,
      startX: x,
      startY: y,
      lastY: y,
      samples: [{ time: performance.now(), y }],
      chain: this.scrollableChain(target),
      mode: 'pending',
      manualScroll: false,
      negotiationLevel: NEGOTIATION_NONE,
      startingIndex: this.targetIndex,
      dragDetents: this.detents,
      dragRange: { minTy: 0, maxTy: 0 },
      didMoveSheet: false,
      scrollOwnsLowerBoundary: false,
    };
  }

  private scrollableChain(target: EventTarget | null): ScrollableEntry[] {
    const container = this.elements?.container;
    const chain: ScrollableEntry[] = [];
    if (!container || !(target instanceof Element)) return chain;
    for (
      let element: Element | null = target;
      element !== null && element !== container;
      element = element.parentElement
    ) {
      if (element instanceof HTMLElement && isVerticallyScrollable(element)) {
        chain.push({ element, inverted: isInverted(element) });
      }
    }
    return chain;
  }

  private negotiationLevel(deltaY: number) {
    const config = this.requireConfig();
    const raw =
      deltaY < 0
        ? config.scrollableExpandNegotiation
        : config.scrollableCollapseNegotiation;
    return clamp(Math.round(raw), NEGOTIATION_NONE, NEGOTIATION_HANDOFF);
  }

  /** Port of the native `gestureRecognizerShouldBegin`. */
  private shouldBegin(gesture: Gesture, dx: number, dy: number) {
    if (Math.abs(dy) <= Math.abs(dx)) return false;
    if (!this.hasLaidOut) return false;
    if (this.snapCandidateIndices(this.targetIndex).length <= 1) return false;

    const range = this.draggableRange(this.targetIndex);
    const currentTy = this.currentTranslateY();
    const canMoveSheet =
      dy < 0 ? currentTy > range.minTy + 0.5 : currentTy < range.maxTy - 0.5;
    if (gesture.chain.length === 0) return canMoveSheet;

    const level = this.negotiationLevel(dy);
    if (level === NEGOTIATION_NONE || !canMoveSheet) return false;
    if (level === NEGOTIATION_HANDOFF) return true;

    // Initial-only collapse is available at the largest detent only when all
    // scrollable ancestors were already at their visual start.
    const isAtLargestDetent = currentTy <= range.minTy + 0.5;
    if (dy > 0 && isAtLargestDetent) {
      return gesture.chain.every((entry) => isAtStart(entry));
    }
    return true;
  }

  /** Port of the native pan `.began` state. */
  private startPan(gesture: Gesture, dy: number) {
    if (this.spring !== null) {
      this.cancelSpring();
    }
    this.isPanning = true;
    // The user takes control: drop any index retained from props.
    this.pendingIndex = null;
    this.scrimPinnedFull = false;
    gesture.startingIndex = this.targetIndex;
    gesture.dragDetents = this.detents;
    gesture.dragRange = this.draggableRange(this.targetIndex);
    gesture.negotiationLevel = this.negotiationLevel(dy);

    const container = this.elements!.container;
    const activeElement = document.activeElement;
    if (
      activeElement instanceof HTMLElement &&
      container.contains(activeElement)
    ) {
      activeElement.blur();
    }

    gesture.mode = 'sheet';
    if (gesture.chain.length > 0) {
      const isAtLargestDetent =
        this.translateY <= gesture.dragRange.minTy + 0.5;
      if (
        gesture.negotiationLevel === NEGOTIATION_HANDOFF &&
        isAtLargestDetent &&
        (dy < 0 || !gesture.chain.every((entry) => isAtStart(entry)))
      ) {
        gesture.mode = 'scroll';
        // Mice cannot drag-scroll content natively, so scroll it here.
        gesture.manualScroll = gesture.kind === 'mouse';
      }
    }

    if (gesture.kind === 'mouse') {
      window.getSelection()?.removeAllRanges();
      const rootStyle = document.documentElement.style;
      const previousUserSelect = rootStyle.userSelect;
      rootStyle.userSelect = 'none';
      this.restoreUserSelect = () => {
        rootStyle.userSelect = previousUserSelect;
      };
    }
  }

  /** Returns whether the move belongs to the sheet and must not scroll. */
  private moveGesture(event: Event, x: number, y: number): boolean {
    const gesture = this.gesture!;
    if (gesture.mode === 'ignored') return false;
    if (claimedEvents.has(event)) {
      // A nested sheet owns this gesture.
      if (gesture.mode === 'pending') {
        gesture.mode = 'ignored';
        return false;
      }
    }

    const now = performance.now();
    gesture.samples.push({ time: now, y });
    while (
      gesture.samples.length > 2 &&
      now - gesture.samples[0]!.time > VELOCITY_SAMPLE_WINDOW_MS
    ) {
      gesture.samples.shift();
    }

    if (gesture.mode === 'pending') {
      const dx = x - gesture.startX;
      const dy = y - gesture.startY;
      if (Math.hypot(dx, dy) < DRAG_SLOP) return false;
      if (!this.shouldBegin(gesture, dx, dy)) {
        gesture.mode = 'ignored';
        return false;
      }
      claimedEvents.add(event);
      this.startPan(gesture, dy);
      // Like the native hosts, measure movement from where the drag was
      // recognized, so the slop is not applied as a jump.
      gesture.lastY = gesture.startY + Math.sign(dy) * DRAG_SLOP;
    }

    claimedEvents.add(event);
    const delta = y - gesture.lastY;
    gesture.lastY = y;
    const range = gesture.dragRange;
    const handoff = gesture.negotiationLevel === NEGOTIATION_HANDOFF;

    if (gesture.mode === 'scroll') {
      if (gesture.manualScroll) {
        this.scrollChainBy(gesture.chain, delta);
      }
      if (gesture.scrollOwnsLowerBoundary) {
        // Once the sheet is fully collapsed, downward movement belongs to the
        // content. On reversal, let it return to its start before the sheet
        // can expand again.
        if (
          delta < 0 &&
          gesture.chain.every((entry) => isAtStart(entry, true))
        ) {
          gesture.scrollOwnsLowerBoundary = false;
          gesture.mode = 'sheet';
        }
        return gesture.manualScroll;
      }
      // A downward scroll remains with the content until every scrollable in
      // the touched chain reaches its visual start. Start moving the sheet on
      // the following update so the boundary movement is not consumed twice.
      if (delta > 0 && gesture.chain.every((entry) => isAtStart(entry))) {
        gesture.mode = 'sheet';
      }
      return gesture.manualScroll;
    }

    const newTy = clamp(this.translateY + delta, range.minTy, range.maxTy);
    if (Math.abs(newTy - this.translateY) > Number.EPSILON) {
      gesture.didMoveSheet = true;
      this.setTranslateY(newTy);
      this.emitPosition();
    }

    if (handoff && gesture.chain.length > 0) {
      if (delta < 0 && newTy <= range.minTy + 0.5) {
        // The sheet reached its largest detent: hand the rest of the drag to
        // the content. The browser's scrolling stays prevented for this
        // gesture, so scroll it here.
        gesture.mode = 'scroll';
        gesture.manualScroll = true;
        gesture.scrollOwnsLowerBoundary = false;
      } else if (delta > 0 && newTy >= range.maxTy - 0.5) {
        // The sheet has no smaller detent to consume.
        gesture.mode = 'scroll';
        gesture.manualScroll = true;
        gesture.scrollOwnsLowerBoundary = true;
      }
    }
    return true;
  }

  private endGesture() {
    const gesture = this.gesture;
    this.gesture = null;
    this.setWindowPointerListeners(false);
    this.restoreUserSelect?.();
    this.restoreUserSelect = null;
    if (
      gesture === null ||
      gesture.mode === 'pending' ||
      gesture.mode === 'ignored'
    ) {
      return;
    }
    this.isPanning = false;

    const velocity = this.releaseVelocity(gesture);
    if (gesture.didMoveSheet || gesture.manualScroll) {
      this.suppressClicksUntil = performance.now() + CLICK_SUPPRESSION_MS;
    }
    if (gesture.mode === 'scroll' && gesture.manualScroll) {
      this.startMomentum(gesture.chain, velocity);
    }
    if (gesture.chain.length > 0 && !gesture.didMoveSheet) {
      return;
    }

    const height = this.cap - this.translateY;
    const detents =
      gesture.dragDetents.length === this.detents.length
        ? gesture.dragDetents
        : this.detents;
    const index = this.bestSnapIndex(
      height,
      velocity,
      gesture.startingIndex,
      detents
    );
    this.snapToIndex(index, velocity);
  }

  private releaseVelocity(gesture: Gesture) {
    const now = performance.now();
    const samples = gesture.samples.filter(
      (sample) => now - sample.time <= VELOCITY_SAMPLE_WINDOW_MS
    );
    const first = samples[0];
    const last = samples[samples.length - 1];
    if (first === undefined || last === undefined || last.time <= first.time) {
      return 0;
    }
    return ((last.y - first.y) / (last.time - first.time)) * 1000;
  }

  /** Scrolls the chain, innermost first, as a finger moving by `delta` would. */
  private scrollChainBy(chain: ScrollableEntry[], delta: number) {
    let remaining = delta;
    for (const entry of chain) {
      if (Math.abs(remaining) < Number.EPSILON) return;
      const { element } = entry;
      const scrollDelta = entry.inverted ? remaining : -remaining;
      const before = element.scrollTop;
      element.scrollTop = clamp(before + scrollDelta, 0, maxScrollTop(element));
      const applied = element.scrollTop - before;
      remaining -= entry.inverted ? applied : -applied;
    }
  }

  private startMomentum(chain: ScrollableEntry[], velocity: number) {
    const entry = chain.find((candidate) => {
      const { element } = candidate;
      const scrollVelocity = candidate.inverted ? velocity : -velocity;
      return scrollVelocity > 0
        ? element.scrollTop < maxScrollTop(element)
        : element.scrollTop > 0;
    });
    if (
      entry === undefined ||
      Math.abs(velocity) < SCROLL_MOMENTUM_MIN_VELOCITY
    ) {
      return;
    }
    this.momentum = { entry, velocity };
    this.requestFrame();
  }

  private stepMomentum(elapsed: number) {
    const momentum = this.momentum!;
    const { element, inverted } = momentum.entry;
    momentum.velocity *= Math.pow(SCROLL_DECELERATION_RATE, elapsed);
    const delta = (momentum.velocity * elapsed) / 1000;
    const before = element.scrollTop;
    element.scrollTop = clamp(
      before + (inverted ? delta : -delta),
      0,
      maxScrollTop(element)
    );
    if (
      element.scrollTop === before ||
      Math.abs(momentum.velocity) < SCROLL_MOMENTUM_MIN_VELOCITY
    ) {
      this.momentum = null;
    }
  }
}

const areNumbersEqual = (a: ReadonlyArray<number>, b: ReadonlyArray<number>) =>
  a.length === b.length && a.every((value, index) => value === b[index]);

const areShadowsEqual = (
  a: BottomSheetWebConfig['shadows'],
  b: BottomSheetWebConfig['shadows']
) =>
  a.length === b.length &&
  a.every((shadow, index) => {
    const other = b[index]!;
    return (
      shadow.offsetX === other.offsetX &&
      shadow.offsetY === other.offsetY &&
      shadow.blurRadius === other.blurRadius &&
      shadow.spreadDistance === other.spreadDistance &&
      shadow.color === other.color
    );
  });

const areRawDetentsEqual = (
  a: ReadonlyArray<RawDetent>,
  b: ReadonlyArray<RawDetent>
) =>
  a.length === b.length &&
  a.every(
    (detent, index) =>
      detent.value === b[index]!.value &&
      detent.kind === b[index]!.kind &&
      detent.programmatic === b[index]!.programmatic &&
      detent.insetTop === b[index]!.insetTop &&
      detent.insetLeft === b[index]!.insetLeft &&
      detent.insetRight === b[index]!.insetRight &&
      detent.insetBottom === b[index]!.insetBottom
  );

const areResolvedDetentsEqual = (a: ResolvedDetent[], b: ResolvedDetent[]) =>
  a.length === b.length &&
  a.every(
    (detent, index) =>
      detent.position === b[index]!.position &&
      detent.programmatic === b[index]!.programmatic &&
      detent.insetLeft === b[index]!.insetLeft &&
      detent.insetRight === b[index]!.insetRight &&
      detent.insetBottom === b[index]!.insetBottom
  );
