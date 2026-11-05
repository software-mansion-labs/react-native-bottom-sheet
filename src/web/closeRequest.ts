/// <reference lib="dom" />

export type CloseRequestTarget = {
  handleCloseRequest(): void;
};

// Modal sheets with a close-request handler that are open or animating closed,
// in the order they opened. Escape is routed to the most recent one, matching
// Android's topmost-presentation routing.
const closeRequestStack: CloseRequestTarget[] = [];
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

/**
 * Escape goes to the most recently opened sheet. One that reopens while
 * still closing is already registered, so it moves to the top.
 */
export const raiseCloseRequestRegistration = (target: CloseRequestTarget) => {
  const index = closeRequestStack.indexOf(target);
  if (index === -1 || index === closeRequestStack.length - 1) return;
  closeRequestStack.splice(index, 1);
  closeRequestStack.push(target);
};

export const setCloseRequestRegistration = (
  target: CloseRequestTarget,
  isRegistered: boolean
) => {
  const index = closeRequestStack.indexOf(target);
  if (isRegistered === (index !== -1)) return;
  if (isRegistered) {
    closeRequestStack.push(target);
    if (!areEscapeListenersInstalled) {
      areEscapeListenersInstalled = true;
      window.addEventListener('keydown', handleEscapeKeyDown);
      window.addEventListener('keyup', handleEscapeKeyUp);
      window.addEventListener('blur', cancelEscapePress);
    }
  } else {
    closeRequestStack.splice(index, 1);
  }
};
