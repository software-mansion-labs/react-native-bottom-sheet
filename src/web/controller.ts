/// <reference lib="dom" />

import { SheetCard } from './card';
import {
  raiseCloseRequestRegistration,
  setCloseRequestRegistration,
} from './closeRequest';
import {
  areNumbersEqual,
  areRawDetentsEqual,
  areResolvedDetentsEqual,
  areScrimOpacitiesEqual,
  areShadowsEqual,
  contentRegionHeight,
  firstOpenPosition,
  isInvalidContentDetentTarget,
  resolveDetentSpecs,
  surfaceOverflow,
} from './detents';
import { clamp, readSafeAreaTop } from './dom';
import { SheetGestures } from './gestures';
import { applyScrim, detentIndex } from './scrim';
import { ScrollMomentum } from './scrollCoordination';
import { SheetSnapping } from './snapping';
import { setStackRegistration, updateStacks } from './stacking';
import type {
  BottomSheetWebCallbacks,
  BottomSheetWebConfig,
  BottomSheetWebElements,
  RawDetent,
  ResolvedDetent,
} from './types';

// The web counterpart of `BottomSheetHostingView.swift` and
// `BottomSheetHostView.kt`. It owns the same geometry, snapping, scrim, and
// scroll coordination behavior, driven by DOM measurement, pointer and touch
// events, and requestAnimationFrame instead of a native view hierarchy. The
// sheet is moved by writing the container's transform directly, so a drag or
// settle never re-renders React.

export class BottomSheetWebController {
  elements: BottomSheetWebElements | null = null;
  private callbacks: BottomSheetWebCallbacks | null = null;
  // Events raised while props are applied, which happens in a React layout
  // effect. Like native events, they are delivered asynchronously, so a
  // handler that sets state does not nest a render inside the commit.
  private deferredEvents: (() => void)[] | null = null;
  config: BottomSheetWebConfig | null = null;
  private appliedIndex: number | null = null;

  rawDetents: ReadonlyArray<RawDetent> = [];
  /** The widest card's side gaps, which the content is laid out against. */
  widestCardLeft = 0;
  widestCardGaps = 0;
  statusBarOverlap = 0;
  detents: ResolvedDetent[] = [];
  targetIndex = 0;
  pendingIndex: number | null = null;
  hasLaidOut = false;
  hostHeight = 0;
  private lastAppliedHostHeight = NaN;
  private appliedSurfaceOverflow = 0;
  translateY = 0;
  hostRect: DOMRect | null = null;
  /** The scale that pushes the sheet back, after its translation. */
  private stackTransform = '';
  lastReportedError: string | null = null;

  scrimPinnedFull = false;
  isPanning = false;

  private frameRequest: number | null = null;
  private lastFrameTime = 0;
  private needsLayout = false;
  private resizeObserver: ResizeObserver | null = null;
  private mutationObserver: MutationObserver | null = null;

  readonly snapping = new SheetSnapping(this);
  readonly momentum = new ScrollMomentum(() => this.requestFrame());
  private readonly card = new SheetCard(this);
  private readonly gestures = new SheetGestures(this);

  // MARK: Lifecycle

  attach(elements: BottomSheetWebElements) {
    this.elements = elements;
    const { host, scrim, container, surface, content } = elements;
    surface.style.transformOrigin = '0 0';
    content.style.transformOrigin = '0 0';
    this.card.resetContentTransform();
    // `clip`, unlike `hidden`, does not make the host a scroll container that
    // focusing an input in the translated sheet could scroll.
    host.style.overflow = 'clip';
    container.style.visibility = 'hidden';
    scrim.style.visibility = 'hidden';
    scrim.style.opacity = '0';
    scrim.style.pointerEvents = 'none';
    elements.scrimDismiss.style.visibility = 'hidden';
    elements.scrimDismiss.style.transform = 'scaleY(0)';
    elements.scrimDismiss.setAttribute('aria-hidden', 'true');
    this.card.updateCardShape();
    if (this.hasLaidOut) {
      // Reattached after a detach that kept the sheet's state, as when Strict
      // Mode replays effects: restore where the sheet is and resume any settle
      // in flight. The spring is time-based, so it picks up where it would be.
      this.setTranslateY(this.translateY);
      this.updateSheetVisibility(this.hostHeight - this.translateY);
      if (this.snapping.isSpringActive) {
        this.requestFrame();
      }
    }

    this.resizeObserver = new ResizeObserver(this.scheduleLayout);
    this.mutationObserver = new MutationObserver(this.observeContent);
    this.observeContent();
    this.mutationObserver.observe(content, { childList: true });

    this.gestures.addListeners(container);
    scrim.addEventListener('click', this.handleScrimPress);
    // Screen readers activate the button with a click.
    elements.scrimDismiss.addEventListener('click', this.handleScrimPress);
    setStackRegistration(this, true);
  }

  detach() {
    const elements = this.elements;
    if (elements === null) return;
    const { scrim, scrimDismiss, container } = elements;
    this.gestures.removeListeners(container);
    scrim.removeEventListener('click', this.handleScrimPress);
    scrimDismiss.removeEventListener('click', this.handleScrimPress);
    this.resizeObserver?.disconnect();
    this.mutationObserver?.disconnect();
    this.resizeObserver = null;
    this.mutationObserver = null;
    if (this.frameRequest !== null) {
      cancelAnimationFrame(this.frameRequest);
      this.frameRequest = null;
    }
    this.gestures.reset();
    setCloseRequestRegistration(this, false);
    setStackRegistration(this, false);
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
      raiseCloseRequestRegistration(this);
    }
    if (
      previous !== null &&
      !areScrimOpacitiesEqual(previous.detents, config.detents)
    ) {
      this.updateScrim();
    }
    if (
      previous === null ||
      !areNumbersEqual(previous.cornerRadii, config.cornerRadii) ||
      !areShadowsEqual(previous.shadows, config.shadows)
    ) {
      this.card.updateCardShape();
    }
    if (
      previous === null ||
      !areRawDetentsEqual(previous.detents, config.detents)
    ) {
      this.rawDetents = config.detents;
      // Flagged in `normalizeDetents`, which lays the content out against it.
      const widest = config.detents.find((detent) => detent.widestCard);
      this.widestCardLeft = widest?.insetLeft ?? 0;
      this.widestCardGaps =
        (widest?.insetLeft ?? 0) + (widest?.insetRight ?? 0);
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
    // The open order, `stacked` and the target detent all shape the stack.
    updateStacks();
  }

  emit(event: (callbacks: BottomSheetWebCallbacks) => void) {
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
    this.gestures.shouldCaptureResponder();

  handleCloseRequest() {
    // While a visible sheet is animating to a closed target, further requests
    // are consumed without another callback until it settles.
    if (this.snapping.isTargetClosed()) return;
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

  get settleDuration() {
    return this.config?.settleDuration ?? 0;
  }

  requireConfig(): BottomSheetWebConfig {
    return this.config!;
  }

  private layout() {
    this.needsLayout = false;
    const elements = this.elements;
    if (elements === null || this.config === null) return;
    const { host, container, content } = elements;
    const hostHeight = host.clientHeight;
    if (hostHeight <= 0 || host.clientWidth <= 0) return;

    this.hostHeight = hostHeight;
    // The part of the top safe-area inset that overlaps the host: the default
    // top inset.
    this.hostRect = host.getBoundingClientRect();
    this.statusBarOverlap = Math.max(0, readSafeAreaTop() - this.hostRect.top);
    container.style.height = `${hostHeight}px`;
    content.style.height = `${contentRegionHeight(this)}px`;
    this.card.updateCardClip();

    this.resolveDetentsFromLayout();
    this.replayPendingIndexIfResolved();
    this.updateSurfaceOverflow();
    this.lastAppliedHostHeight = hostHeight;

    if (!this.hasLaidOut && this.detents.length > 0) {
      const indexToApply = this.pendingIndex ?? this.targetIndex;
      const clampedIndex = clamp(indexToApply, 0, this.detents.length - 1);

      if (
        this.config.animateIn &&
        isInvalidContentDetentTarget(this, clampedIndex)
      ) {
        // Wait for the content to be measured before animating in.
        this.targetIndex = clampedIndex;
        this.pendingIndex = clampedIndex;
        this.setTranslateY(hostHeight);
        this.emitPosition();
        return;
      }

      this.hasLaidOut = true;
      this.pendingIndex = null;
      this.targetIndex = clampedIndex;

      if (this.config.animateIn) {
        this.setTranslateY(hostHeight);
        this.emitPosition();
        this.snapping.snapToIndex(this.targetIndex, 0, false, true);
      } else {
        this.setTranslateY(this.snapping.translationY(this.targetIndex));
        this.emitPosition();
      }
      return;
    }

    if (this.snapping.isSpringActive || this.isPanning) return;
    if (this.hasLaidOut) {
      this.setTranslateY(this.snapping.translationY(this.targetIndex));
    }
    this.updateScrim();
  }

  private resolveDetentsFromLayout() {
    if (
      this.hasLaidOut &&
      isInvalidContentDetentTarget(this, this.targetIndex)
    ) {
      return;
    }
    const resolved = resolveDetentSpecs(this);
    if (resolved === null) return;
    const hostHeight = this.hostHeight;
    // Also fall through when only the host's height changed: translations derive from
    // it, so they must be re-anchored even when the detents are unchanged.
    if (
      areResolvedDetentsEqual(resolved, this.detents) &&
      hostHeight === this.lastAppliedHostHeight
    ) {
      return;
    }

    // The re-anchor math preserves the on-screen sheet height across the
    // geometry change, so relate the current translation to the height it was
    // computed against.
    const previousHostHeight = Number.isFinite(this.lastAppliedHostHeight)
      ? this.lastAppliedHostHeight
      : hostHeight;
    const firstOpen = firstOpenPosition(this.detents);
    const wasScrimFull =
      this.hasLaidOut &&
      !this.isPanning &&
      this.requireConfig().modal &&
      firstOpen > 0 &&
      previousHostHeight - this.snapping.currentTranslateY() + 0.5 >= firstOpen;
    this.scrimPinnedFull = this.scrimPinnedFull || wasScrimFull;
    this.detents = resolved;
    this.lastAppliedHostHeight = hostHeight;

    if (this.detents.length === 0 || !this.hasLaidOut || this.isPanning) {
      return;
    }

    this.targetIndex = clamp(this.targetIndex, 0, this.detents.length - 1);
    const targetTy = this.snapping.translationY(this.targetIndex);

    if (this.snapping.isSpringActive) {
      const emitsSettle = this.snapping.springEmitsSettle;
      const visualTy = this.snapping.cancelSpring();
      // Re-anchor the in-flight position so the sheet keeps the same on-screen
      // height across the resize.
      const visibleHeight = previousHostHeight - visualTy;
      this.setTranslateY(clamp(hostHeight - visibleHeight, 0, hostHeight));
      this.emitPosition();
      this.snapping.snapToIndex(this.targetIndex, 0, false, emitsSettle, true);
      return;
    }

    const currentVisibleHeight = previousHostHeight - this.translateY;
    const targetHeight = this.snapping.detentPosition(this.targetIndex);
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
      this.setTranslateY(
        clamp(hostHeight - currentVisibleHeight, 0, hostHeight)
      );
      this.emitPosition();
      this.snapping.snapToIndex(this.targetIndex, 0, false, false, true);
    }
  }

  /** Keeps a moving sheet's overflow: its card can be above the current detents. */
  private updateSurfaceOverflow() {
    const surface = this.elements?.surface;
    if (surface == null) return;
    const overflow = surfaceOverflow(
      this,
      this.hasLaidOut ? this.card.currentLayoutHeight() : 0
    );
    this.appliedSurfaceOverflow =
      this.snapping.isSpringActive || this.isPanning
        ? Math.max(overflow, this.appliedSurfaceOverflow)
        : overflow;
    surface.style.bottom = `${-this.appliedSurfaceOverflow}px`;
  }

  private shouldAnimateContentHeight(index: number) {
    const spec = this.rawDetents[index];
    if (spec === undefined) return this.requireConfig().animateContentHeight;
    return this.requireConfig().animateContentHeight || spec.kind !== 'content';
  }

  // MARK: Detents and positions

  private currentSheetHeight() {
    return this.hostHeight - this.snapping.currentTranslateY();
  }

  setTranslateY(ty: number) {
    this.translateY = ty;
    const container = this.elements?.container;
    if (container) {
      container.style.transform = `translate3d(0px, ${ty}px, 0px)${this.stackTransform}`;
    }
    this.card.updateCardClip();
    // A sheet this one covers follows it in the same frame.
    updateStacks();
  }

  // MARK: Stacking

  cardSides(position: number) {
    return this.card.cardSides(position);
  }

  setStackTransform(
    transform: { scale: number; originX: number; originY: number } | null
  ) {
    const container = this.elements?.container;
    const stackTransform =
      transform === null ? '' : ` scale(${transform.scale})`;
    if (container && transform !== null) {
      container.style.transformOrigin = `${transform.originX}px ${transform.originY}px`;
    }
    if (stackTransform === this.stackTransform) return;
    this.stackTransform = stackTransform;
    if (container) {
      container.style.transform = `translate3d(0px, ${this.translateY}px, 0px)${stackTransform}`;
    }
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
    this.snapping.snapToIndex(newIndex, 0, false);
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

  requestFrame() {
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
    const wasSpringActive = this.snapping.isSpringActive;
    this.snapping.stepSpring(time);
    if (wasSpringActive && !this.snapping.isSpringActive) {
      this.updateSurfaceOverflow();
    }
    if (this.momentum.isActive) {
      this.momentum.step(Math.min(elapsed, 64));
    }
    if (
      this.snapping.isSpringActive ||
      this.momentum.isActive ||
      this.needsLayout
    ) {
      this.requestFrame();
    } else {
      this.lastFrameTime = 0;
    }
  };

  // MARK: Position, scrim, and visibility

  emitPosition() {
    const position = this.hostHeight - this.translateY;
    this.updateScrim(position);
    this.updateSheetVisibility(position);
    const index = detentIndex(position, this.detents);
    this.emit((callbacks) => callbacks.onPositionChange(position, index));
  }

  private updateSheetVisibility(position: number) {
    const container = this.elements?.container;
    if (!container) return;
    container.style.visibility = position <= 0.5 ? 'hidden' : 'visible';
  }

  private updateScrim(position = this.currentSheetHeight()) {
    applyScrim(this, position);
  }

  /**
   * The closed detent a scrim press, including the scrim's dismiss button,
   * snaps the sheet to, while one would. Requiring an open target also ignores
   * presses once a close is running, including one to another zero-height
   * detent.
   */
  get scrimDismissTargetIndex() {
    const dismissIndex = this.snapping.scrimDismissIndex;
    if (
      this.config === null ||
      !this.config.modal ||
      dismissIndex === null ||
      this.elements?.scrim.style.visibility !== 'visible' ||
      !(this.snapping.detentPosition(this.targetIndex) > 0) ||
      (this.snapping.isSpringActive && this.currentSheetHeight() <= 0.5)
    ) {
      return null;
    }
    return dismissIndex;
  }

  private readonly handleScrimPress = () => {
    const dismissIndex = this.scrimDismissTargetIndex;
    if (dismissIndex === null) return;
    this.snapping.snapToIndex(dismissIndex, 0);
  };

  updateCloseRequestRegistration() {
    const config = this.config;
    setCloseRequestRegistration(
      this,
      config !== null &&
        config.modal &&
        config.hasCloseRequestHandler &&
        this.hasLaidOut &&
        (!this.snapping.isTargetClosed() || this.snapping.isSpringActive)
    );
  }
}
