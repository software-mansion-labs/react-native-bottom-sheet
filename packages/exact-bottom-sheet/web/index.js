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

/** A shadow's CSS colour at `opacity` (0–1, default 1). */
const shadowColor = (color, opacity) => {
  const alpha = Number(opacity);
  if (!Number.isFinite(alpha) || alpha >= 1) return color;
  return `color-mix(in srgb, ${color} ${Math.max(0, alpha) * 100}%, transparent)`;
};

// When each sheet last opened, relative to the others, as `BottomSheet.tsx`
// counts it: the controller routes Escape to the most recently opened modal.
let lastOpenOrder = 0;

/**
 * A Contract node, found by its `testId` next to the sheet's element, moved
 * into one of the sheet's elements and given back where it was. The runtime
 * puts a node back when its parent's children are patched (its home is
 * watched for that), and removes a region's nodes by walking siblings
 * between its anchors, so it can't find a node moved into the sheet: a hidden
 * stand-in is left in the node's place, and when the runtime removes it (a
 * lazy sheet's content unmounting), the node is dropped with it.
 */
class Adoption {
  constructor(
    element,
    container,
    { before = () => null, adopted, released, changed }
  ) {
    this.element = element;
    this.container = container;
    this.before = before;
    this.adopted = adopted ?? (() => {});
    this.released = released ?? (() => {});
    this.id = '';
    this.node = null;
    this.home = null;
    this.next = null;
    this.place = -1;
    this.standIn = null;
    this.homeObserver = new MutationObserver(() => queueMicrotask(changed));
  }

  get isAdopted() {
    return this.node?.parentElement === this.container;
  }

  reconcile() {
    if (
      this.isAdopted &&
      this.standIn &&
      !this.standIn.isConnected &&
      this.home?.isConnected
    ) {
      // The runtime removed the node's region: the node goes with it, and the
      // home stays watched for the next one (a lazy sheet reopening).
      const home = this.home;
      this.node.remove();
      this.release();
      this.homeObserver.observe(home, { childList: true });
    }
    if (
      this.node &&
      (!this.node.isConnected ||
        this.node.getAttribute('data-testid') !== this.id)
    ) {
      this.release();
    }
    if (!this.node && this.id) {
      const node = this.find();
      if (node) {
        this.node = node;
        this.adopted(node);
      }
    }
    const node = this.node;
    if (node && node.parentElement !== this.container) {
      if (node.parentElement) this.remember(node);
      this.container.insertBefore(node, this.before());
      return true;
    }
    return false;
  }

  /** Where the node sits before it is adopted, to give it back there. */
  remember(node) {
    const home = node.parentElement;
    this.home = home;
    this.next = node.nextSibling;
    this.place = Array.prototype.indexOf.call(home.childNodes, node);
    this.standIn?.remove();
    this.standIn = node.ownerDocument.createElement('div');
    this.standIn.hidden = true;
    this.standIn.setAttribute('aria-hidden', 'true');
    home.insertBefore(this.standIn, node);
    this.homeObserver.disconnect();
    this.homeObserver.observe(home, { childList: true });
  }

  /** Lets the node go, back in its parent at its place if it is still in the
   * sheet (an id change, destroy). */
  release() {
    const node = this.node;
    const home = this.home;
    this.homeObserver.disconnect();
    this.node = null;
    this.home = null;
    const standIn = this.standIn;
    this.standIn = null;
    if (node) this.released(node);
    if (!node || node.parentElement !== this.container || !home?.isConnected) {
      standIn?.remove();
      return;
    }
    if (standIn?.parentNode === home) {
      standIn.replaceWith(node);
      return;
    }
    standIn?.remove();
    const next =
      this.next?.parentNode === home
        ? this.next
        : (home.childNodes[this.place] ?? null);
    home.insertBefore(node, next);
  }

  find() {
    const selector = `[data-testid="${CSS.escape(this.id)}"]`;
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
}

class BottomSheetView {
  constructor({ implementation, element, props, events, view }) {
    this.element = element;
    this.events = events;
    this.view = view;
    this.props = {};
    // What the sheet discloses to Exact's agent, through the implementation's
    // `state()` (see `stateEntry`).
    this.sheets = implementation?.sheets ?? null;
    this.settledIndex = null;
    this.lastPosition = 0;
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
    // Holds the adopted surface node (`surfaceId`, the `BottomSheet`
    // component's `${id}-surface`), between the widest card's insets.
    this.surfaceSlot = make(
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
      onIndexChange: (index) => {
        events.emit('indexchange', index);
        this.index = index;
        this.publishState();
      },
      onSettle: (index) => {
        events.emit('settle', index);
        this.settledIndex = index;
        this.publishState();
        const detent = this.detents?.[index];
        if (detent) this.setRestingClosed(isNormalizedDetentClosed(detent));
      },
      // Sent only when the app asks (`trackPosition`, which
      // `TrackedBottomSheet` sets): Exact can't tell a view which events are
      // bound, and this one runs every frame the sheet moves.
      onPositionChange: (position, index) => {
        this.lastPosition = position;
        if (this.props.trackPosition === true)
          events.emit('positionchange', { position, index });
      },
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
    const changed = () => this.reconcile();
    this.contentAdoption = new Adoption(element, this.wrapper, {
      before: () => this.marker,
      adopted: () => queueMicrotask(() => this.observeNode()),
      released: () => {
        this.nodeObserver.disconnect();
        this.childObserver.disconnect();
      },
      changed,
    });
    this.surfaceAdoption = new Adoption(element, this.surfaceSlot, { changed });
    this.regionObserver.observe(this.card);
    this.lastRegion = '';
    this.region = { width: 0, height: 0, surface: 0 };
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
    // The adopted surface node, drawn by Contract (`surfaceId`), or the raw
    // tag's `surfaceColor`, unused while a node is set.
    this.surfaceAdoption.id =
      typeof next.surfaceId === 'string' ? next.surfaceId : '';
    Object.assign(this.surfaceSlot.style, {
      left: `${widest?.insetLeft ?? 0}px`,
      right: `${widest?.insetRight ?? 0}px`,
    });
    if (this.surfaceAdoption.id) {
      if (this.surfaceSlot.parentElement !== this.surface)
        this.surface.append(this.surfaceSlot);
    } else {
      this.surfaceSlot.remove();
    }
    if (
      !this.surfaceAdoption.id &&
      typeof next.surfaceColor === 'string' &&
      next.surfaceColor !== ''
    ) {
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
      // `opacity` scales the colour, as `shadowOpacity` does on iOS; mixed in
      // CSS, so any CSS colour (`light-dark()` included) still works.
      color: shadowColor(
        typeof s.color === 'string' ? s.color : 'black',
        s.opacity
      ),
    }));
    const radii = Array.isArray(next.surfaceCornerRadii)
      ? next.surfaceCornerRadii.map((r) => Math.max(0, Number(r) || 0))
      : [];
    this.contentId = typeof next.contentId === 'string' ? next.contentId : '';
    this.contentAdoption.id = this.contentId;
    this.index = index;
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
    this.publishState();
  }

  /** Keeps the content and surface nodes in the sheet. */
  reconcile() {
    if (this.destroyed) return;
    if (this.contentAdoption.reconcile()) this.layoutMarker();
    this.surfaceAdoption.reconcile();
    // Republished when what is adopted differs from what was published.
    if (this.publishedAdoption !== this.adoption) this.publishState();
  }

  /** Whether the content and the surface nodes are adopted. */
  get adoption() {
    return `${this.contentAdoption.isAdopted},${this.surfaceAdoption.isAdopted}`;
  }

  get contentNode() {
    return this.contentAdoption.node;
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
    // The surface's height: the card's, which the surface fills.
    const surface = this.card.clientHeight;
    const key = `${width}x${height}x${surface}`;
    if (key === this.lastRegion || width <= 0 || height <= 0) return;
    const regionChanged =
      width !== this.region.width || height !== this.region.height;
    this.lastRegion = key;
    this.region = { width, height, surface };
    if (regionChanged) this.events.emit('regionchange', { width, height });
    this.emitChange();
  }

  // The built-in `change` event, "<width>,<height>,<resting closed: 0|1>,
  // <surface height>", which a child component can handle with its own action
  // (a typed event must name a root action): the `BottomSheet` component lays
  // its content node out with the region, its surface node with the region's
  // width and the surface height, and unmounts a `lazy` sheet once it rests
  // closed.
  emitChange() {
    if (this.destroyed) return;
    const { width, height, surface } = this.region;
    this.events.change(
      `${width},${height},${this.restingClosed ? 1 : 0},${surface}`
    );
  }

  setRestingClosed(closed) {
    if (this.restingClosed === closed) return;
    this.restingClosed = closed;
    this.emitChange();
    this.publishState();
  }

  /** The sheet's key in the agent's state: its id in the `BottomSheet`
   * component (`contentId` without `-content`), else the view's. */
  get stateKey() {
    return this.contentId?.endsWith('-content')
      ? this.contentId.slice(0, -'-content'.length)
      : this.contentId || String(this.view);
  }

  /** Discloses the sheet to Exact's agent, as the iOS adapter does: its target
   * and settled index, whether it rests closed, its position when it last
   * settled, and whether its content and surface nodes are adopted. */
  publishState() {
    if (!this.sheets || this.destroyed) return;
    const key = this.stateKey;
    if (this.publishedKey && this.publishedKey !== key)
      this.sheets.delete(this.publishedKey);
    this.publishedKey = key;
    this.publishedAdoption = this.adoption;
    this.sheets.set(key, {
      index: this.index ?? null,
      settled: this.settledIndex,
      restingClosed: this.restingClosed ?? true,
      position: this.lastPosition,
      content: this.contentAdoption.isAdopted,
      surface: this.surfaceAdoption.isAdopted,
      modal: false,
    });
  }

  destroy() {
    this.destroyed = true;
    this.regionObserver.disconnect();
    this.nodeObserver.disconnect();
    this.childObserver.disconnect();
    this.controller.detach();
    this.contentAdoption.release();
    this.surfaceAdoption.release();
    if (this.publishedKey) this.sheets?.delete(this.publishedKey);
    this.host.remove();
  }
}

export const roster = { 'swm-bottom-sheet': { view: BottomSheetView } };

/** The session's instance: each sheet's entry for the agent (`state()`,
 * `state.extensions`), by the sheet's id. */
export function implementation() {
  const sheets = new Map();
  return {
    sheets,
    state() {
      return { sheets: Object.fromEntries(sheets) };
    },
  };
}
