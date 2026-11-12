/* global ResizeObserver, MutationObserver, CSS */
// The Exact adapter for the bottom sheet on the web: the `swm-bottom-sheet`
// view of `@swmansion/exact-bottom-sheet`. It is the counterpart of
// `src/web/BottomSheetWebView.tsx`: it builds the same eight elements with the
// same styles (React Native for Web's view defaults included) and drives them
// with the shared `BottomSheetWebController` (`src/web/*.ts`, bundled into
// `generated/sheet-controller.js` by `scripts/prepare.mjs`).
//
// A package view is a leaf in Exact, so the sheet's content is an ordinary
// Contract node next to the tag, found by the `testId` the tag's `contentId`
// names, and moved into the sheet's content wrapper. See README.md.
import {
  BottomSheetWebController,
  CONTENT_HEIGHT_MARKER_NATIVE_ID,
  isNormalizedDetentClosed,
  normalizeDetents,
  validateIndex,
  validateSettleDuration,
} from './generated/sheet-controller.js';

// React Native for Web's `View` defaults, which every element below starts from.
const VIEW = {
  alignItems: 'stretch',
  border: '0 solid black',
  boxSizing: 'border-box',
  display: 'flex',
  flexBasis: 'auto',
  flexDirection: 'column',
  flexShrink: '0',
  listStyle: 'none',
  margin: '0',
  minHeight: '0',
  minWidth: '0',
  padding: '0',
  position: 'relative',
  textDecoration: 'none',
  zIndex: '0',
};
const FILL = {
  position: 'absolute',
  left: '0',
  right: '0',
  top: '0',
  bottom: '0',
};

const COORDINATION = { none: 0, initial: 1, handoff: 2 };

/** A prop as its value: the runtime passes JSON values, a string is tried as JSON. */
const value = (raw) => {
  if (typeof raw !== 'string') return raw ?? null;
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
};

// A points detent arrives as a string (`SheetDetent.value`); a numeric one is
// handed to `normalizeDetents` as the number React Native would get.
const POINTS = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/;

/** A `SheetDetent` as the React Native detent `normalizeDetents` validates. */
const toDetent = (entry) => {
  const config =
    entry !== null && typeof entry === 'object' ? entry : { value: entry };
  const text = String(config.value ?? '');
  return {
    value: POINTS.test(text) ? Number(text) : text,
    programmatic: config.programmatic ?? undefined,
    insets: config.insets ?? undefined,
    scrimOpacity: config.scrimOpacity ?? undefined,
  };
};

// When each sheet last opened, relative to the others, as `BottomSheet.tsx`
// counts it: the controller routes Escape to the most recently opened modal.
let lastOpenOrder = 0;

class BottomSheetView {
  constructor({ element, props, events, view }) {
    this.element = element;
    this.events = events;
    this.view = view;
    this.props = {};
    this.contentNode = null;
    this.contentHome = null;
    this.contentNext = null;
    this.contentPlace = -1;
    // A hidden element left where the adopted node was. The runtime removes
    // a region's nodes by walking siblings between its anchors, so it can't
    // find a node moved into the sheet: when the region goes (a lazy
    // sheet's content unmounting), it removes the stand-in instead, and the
    // adapter drops the node with it.
    this.standIn = null;
    this.controller = new BottomSheetWebController();
    const doc = element.ownerDocument;
    const make = (style, parent) => {
      const el = doc.createElement('div');
      Object.assign(el.style, VIEW, style);
      parent?.append(el);
      return el;
    };
    // The tag's box spans the screen, as the native view's does; like the
    // React Native wrappers' `pointerEvents="box-none"`, it and the host take
    // no pointer themselves, so a press outside the card and the scrim reaches
    // the page under the sheet. (On iOS the hosting view's hit test declines.)
    element.style.pointerEvents = 'none';
    // `styles.host` and the NativeView's `StyleSheet.absoluteFill`.
    this.host = make(
      { ...FILL, overflow: 'hidden', pointerEvents: 'none' },
      element
    );
    this.scrim = make(FILL, this.host);
    this.scrimDismiss = make(
      { ...FILL, transformOrigin: 'top', pointerEvents: 'none' },
      this.host
    );
    this.scrimDismiss.setAttribute('role', 'button');
    // `pointerEvents="box-none"`: the container takes none, its children do.
    this.container = make(
      {
        position: 'absolute',
        left: '0',
        right: '0',
        top: '0',
        pointerEvents: 'none',
      },
      this.host
    );
    this.shadow = make(
      {
        position: 'absolute',
        left: '0',
        right: '0',
        top: '0',
        pointerEvents: 'none',
      },
      this.container
    );
    this.card = make({ ...FILL, pointerEvents: 'auto' }, this.container);
    this.surface = make({ ...FILL, pointerEvents: 'none' }, this.card);
    // The `BottomSheetSurfaceView` and the background the demos give it.
    this.surfaceFill = make(
      { position: 'absolute', top: '0', bottom: '0', pointerEvents: 'auto' },
      null
    );
    this.content = make({}, this.card);
    // `BottomSheetContentWrapperView`: `flex: 1`, between the widest card's insets.
    this.wrapper = make(
      { flexGrow: '1', flexShrink: '1', flexBasis: '0%' },
      this.content
    );
    // The content-height marker: an absolute child at the adopted node's
    // bottom (or at its end marker, see `endMarkerId`), so `offsetTop` reads
    // the content's height whatever the node's own positioning.
    this.marker = make(
      {
        position: 'absolute',
        left: '0',
        right: '0',
        top: '0',
        height: '0',
        pointerEvents: 'none',
      },
      this.wrapper
    );
    this.marker.id = CONTENT_HEIGHT_MARKER_NATIVE_ID;
    // As tall as the content: the controller re-reads the marker when one of
    // the wrapper's children resizes, which the marker moving alone (an end
    // marker moving inside a node of fixed height) is not; this one resizes
    // whenever the marker moves.
    this.sizer = make(
      {
        position: 'absolute',
        left: '0',
        top: '0',
        width: '0',
        height: '0',
        visibility: 'hidden',
        pointerEvents: 'none',
      },
      this.wrapper
    );

    this.controller.attach({
      host: this.host,
      scrim: this.scrim,
      scrimDismiss: this.scrimDismiss,
      container: this.container,
      card: this.card,
      shadow: this.shadow,
      surface: this.surface,
      content: this.content,
    });
    this.controller.setCallbacks({
      onIndexChange: (index) => events.emit('indexchange', index),
      onSettle: (index) => {
        events.emit('settle', index);
        const detent = this.detents?.[index];
        if (detent) this.setRestingClosed(isNormalizedDetentClosed(detent));
      },
      onPositionChange: (position, index) =>
        events.emit('positionchange', { position, index }),
      onCloseRequest: () => events.emit('closerequest'),
      onError: (message) =>
        console.error(`swm-bottom-sheet ${view}: ${message}`),
    });

    this.nodeObserver = new ResizeObserver(() => this.layoutMarker());
    // The node's children: the end marker moves when one of them is patched
    // in, out or resized.
    this.childObserver = new MutationObserver(() => this.observeNode());
    this.regionObserver = new ResizeObserver(() => this.reportRegion());
    this.regionObserver.observe(this.content);
    this.homeObserver = new MutationObserver(() =>
      queueMicrotask(() => this.reconcile())
    );
    this.lastRegion = '';
    this.region = { width: 0, height: 0 };
    // Whether the sheet rests at a closed detent: the `BottomSheet`
    // component's `lazy` keeps the content mounted until it does, as
    // `BottomSheet.tsx`'s `isSheetActive` (an open index clears it at once, a
    // settle at a closed detent sets it).
    this.restingClosed = null;
    this.openOrder = 0;
    this.wasClosed = null;
    try {
      this.setProps(props);
    } catch (error) {
      // Refused at creation: nothing of the sheet may stay in the element.
      this.destroy();
      throw error;
    }
  }

  setProps(props) {
    const next = Object.fromEntries(
      Object.entries(props ?? {}).map(([k, v]) => [k, value(v)])
    );
    const modal = next.modal === true;
    // Validated as `BottomSheet.tsx` validates before rendering, with the same
    // functions; a refused update applies nothing (Exact keeps the last
    // accepted props and logs the refusal).
    let detents;
    let index;
    let settleDuration;
    try {
      const raw = (Array.isArray(next.detents) ? next.detents : []).map(
        toDetent
      );
      index = next.index;
      settleDuration = next.settleDuration ?? undefined;
      validateIndex(index, raw.length);
      validateSettleDuration(settleDuration);
      detents = normalizeDetents(raw, next.topInset ?? undefined, modal);
    } catch (error) {
      console.error(`swm-bottom-sheet ${this.view}: ${error.message}`);
      throw error;
    }
    this.props = next;
    const closed = isNormalizedDetentClosed(detents[index]);
    if (this.wasClosed === null ? !closed : this.wasClosed && !closed) {
      this.openOrder = ++lastOpenOrder;
    }
    this.wasClosed = closed;
    this.detents = detents;
    if (this.restingClosed === null || !closed) this.setRestingClosed(closed);
    const widest = detents.find((d) => d.widestCard);
    this.wrapper.style.marginLeft = `${widest?.insetLeft ?? 0}px`;
    this.wrapper.style.marginRight = `${widest?.insetRight ?? 0}px`;
    this.scrim.style.backgroundColor =
      typeof next.scrimColor === 'string' ? next.scrimColor : '';
    this.scrimDismiss.setAttribute('aria-label', next.scrimLabel || 'Dismiss');
    if (typeof next.surfaceColor === 'string' && next.surfaceColor !== '') {
      Object.assign(this.surfaceFill.style, {
        left: `${widest?.insetLeft ?? 0}px`,
        right: `${widest?.insetRight ?? 0}px`,
        backgroundColor: next.surfaceColor,
      });
      if (this.surfaceFill.parentElement !== this.surface)
        this.surface.append(this.surfaceFill);
    } else {
      this.surfaceFill.remove();
    }
    const shadows = (
      Array.isArray(next.surfaceShadows) ? next.surfaceShadows : []
    ).map((s) => ({
      offsetX: Number(s.offsetX) || 0,
      offsetY: Number(s.offsetY) || 0,
      blurRadius: Math.max(0, Number(s.blurRadius) || 0),
      spreadDistance: Number(s.spreadDistance) || 0,
      color: typeof s.color === 'string' ? s.color : 'black',
    }));
    const radii = Array.isArray(next.surfaceCornerRadii)
      ? next.surfaceCornerRadii.map((r) => Math.max(0, Number(r) || 0))
      : [];
    this.contentId = typeof next.contentId === 'string' ? next.contentId : '';
    this.reconcile();
    this.controller.update({
      detents,
      index,
      animateIn: next.animateIn !== false,
      animateContentHeight: next.animateContentHeight !== false,
      modal,
      // Escape asks a modal sheet to close (`closerequest`) only when the app
      // says it handles it, as `onCloseRequest` does on React Native Web.
      hasCloseRequestHandler: next.handlesCloseRequest === true,
      openOrder: this.openOrder,
      scrollExpandCoordination:
        COORDINATION[next.scrollExpand] ?? COORDINATION.handoff,
      scrollCollapseCoordination:
        COORDINATION[next.scrollCollapse] ?? COORDINATION.initial,
      settleDuration: settleDuration ?? 0,
      cornerRadii: [0, 1, 2, 3].map((i) => radii[i] ?? 0),
      shadows,
    });
  }

  /** Finds the content node and keeps it in the wrapper; the runtime puts a
   * node back when its parent's children are patched. */
  reconcile() {
    if (this.destroyed) return;
    if (
      this.contentNode?.parentElement === this.wrapper &&
      this.standIn &&
      !this.standIn.isConnected &&
      this.contentHome?.isConnected
    ) {
      // The runtime removed the node's region: the node goes with it, and the
      // home stays watched for the next one (a lazy sheet reopening).
      const home = this.contentHome;
      this.contentNode.remove();
      this.release();
      this.homeObserver.observe(home, { childList: true });
    }
    if (
      this.contentNode &&
      (!this.contentNode.isConnected ||
        this.contentNode.getAttribute('data-testid') !== this.contentId)
    ) {
      this.release();
    }
    if (!this.contentNode && this.contentId) {
      const node = this.find();
      if (node) {
        this.contentNode = node;
        this.observeNode();
      }
    }
    const node = this.contentNode;
    if (node && node.parentElement !== this.wrapper) {
      if (node.parentElement) this.remember(node);
      this.wrapper.insertBefore(node, this.marker);
      this.layoutMarker();
    }
  }

  /** Observes the node and its children's sizes, which move its end marker. */
  observeNode() {
    const node = this.contentNode;
    this.nodeObserver.disconnect();
    this.childObserver.disconnect();
    if (!node) return;
    this.nodeObserver.observe(node);
    for (const child of node.children) this.nodeObserver.observe(child);
    this.childObserver.observe(node, { childList: true });
    this.layoutMarker();
  }

  /** The testId of the adopted node's end marker: `contentId` without a
   * trailing `-content`, then `-end` (the `BottomSheet` component's
   * `${id}-content` holds `${id}-end`, after its children). */
  get endMarkerId() {
    return `${this.contentId.replace(/-content$/, '')}-end`;
  }

  /** Where the node sits before it is adopted, to give it back there. */
  remember(node) {
    const home = node.parentElement;
    this.contentHome = home;
    this.contentNext = node.nextSibling;
    this.contentPlace = Array.prototype.indexOf.call(home.childNodes, node);
    this.standIn?.remove();
    this.standIn = node.ownerDocument.createElement('div');
    this.standIn.hidden = true;
    this.standIn.setAttribute('aria-hidden', 'true');
    home.insertBefore(this.standIn, node);
    this.homeObserver.disconnect();
    this.homeObserver.observe(home, { childList: true });
  }

  /** Lets the adopted node go, back in its parent at its place if it is
   * still in the sheet (a `contentId` change, destroy). */
  release() {
    const node = this.contentNode;
    const home = this.contentHome;
    this.nodeObserver.disconnect();
    this.childObserver.disconnect();
    this.homeObserver.disconnect();
    this.contentNode = null;
    this.contentHome = null;
    const standIn = this.standIn;
    this.standIn = null;
    if (!node || node.parentElement !== this.wrapper || !home?.isConnected) {
      standIn?.remove();
      return;
    }
    if (standIn?.parentNode === home) {
      standIn.replaceWith(node);
      return;
    }
    standIn?.remove();
    const next =
      this.contentNext?.parentNode === home
        ? this.contentNext
        : (home.childNodes[this.contentPlace] ?? null);
    home.insertBefore(node, next);
  }

  find() {
    const selector = `[data-testid="${CSS.escape(this.contentId)}"]`;
    for (
      let level = this.element;
      level?.parentElement;
      level = level.parentElement
    ) {
      for (const candidate of level.parentElement.querySelectorAll(selector)) {
        if (!this.element.contains(candidate)) return candidate;
      }
    }
    return null;
  }

  layoutMarker() {
    const node = this.contentNode;
    let bottom = 0;
    if (node && node.parentElement === this.wrapper) {
      bottom = node.offsetTop + node.offsetHeight;
      // The end marker's top in the node (layout offsets, which the card's
      // scale leaves alone), as the content-height marker after `children`.
      const end = node.querySelector(
        `[data-testid="${CSS.escape(this.endMarkerId)}"]`
      );
      let y = 0;
      let at = end;
      while (at && at !== node) {
        y += at.offsetTop;
        at = at.offsetParent;
      }
      if (end && at === node) bottom = node.offsetTop + y;
    }
    this.marker.style.top = `${bottom}px`;
    this.sizer.style.height = `${bottom}px`;
  }

  reportRegion() {
    const width = this.wrapper.clientWidth;
    const height = this.content.clientHeight;
    const key = `${width}x${height}`;
    if (key === this.lastRegion || width <= 0 || height <= 0) return;
    this.lastRegion = key;
    this.region = { width, height };
    this.events.emit('regionchange', { width, height });
    this.emitChange();
  }

  // The built-in `change` event, "<width>,<height>,<resting closed: 0|1>",
  // which a child component can handle with its own action (a typed event
  // must name a root action): the `BottomSheet` component lays its content
  // node out with the region and unmounts a `lazy` sheet once it rests closed.
  emitChange() {
    if (this.destroyed) return;
    const { width, height } = this.region;
    this.events.change(`${width},${height},${this.restingClosed ? 1 : 0}`);
  }

  setRestingClosed(closed) {
    if (this.restingClosed === closed) return;
    this.restingClosed = closed;
    this.emitChange();
  }

  destroy() {
    this.destroyed = true;
    this.regionObserver.disconnect();
    this.nodeObserver.disconnect();
    this.childObserver.disconnect();
    this.controller.detach();
    this.release();
    this.host.remove();
  }
}

export const roster = { 'swm-bottom-sheet': { view: BottomSheetView } };

export function implementation() {
  return {};
}
