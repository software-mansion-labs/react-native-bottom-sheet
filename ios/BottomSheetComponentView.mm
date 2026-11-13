#import "BottomSheetComponentView.h"
#import "BottomSheetContentView.h"
#import "BottomSheetPortalHostComponentView.h"
#import "BottomSheetSurfaceComponentView.h"
#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/BottomSheetStateHelper.h"
#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/ComponentDescriptors.h"

#import <React/RCTAssert.h>
#import <React/RCTConversions.h>
#import <React/RCTFabricComponentsPlugins.h>
#import <React/RCTMountingTransactionObserving.h>
#import <React/RCTSurfaceTouchHandler.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/EventEmitters.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/Props.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/RCTComponentViewHelpers.h>

using namespace facebook::react;

/// Full-window container hosting the sheet in window-overlay mode. It is added
/// directly to the host `UIWindow` (above any modal presented in that window)
/// and forwards hit-testing to its single subview—the sheet—so touches outside
/// the sheet and its scrim fall through to the content underneath.
@interface BottomSheetOverlayContainerView : UIView
@end

@implementation BottomSheetOverlayContainerView

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  if (!self.isUserInteractionEnabled || self.isHidden || self.alpha < 0.01) {
    return nil;
  }
  // Bypass UIKit's strict containment policy and hit-test subviews directly: the
  // sheet's own hitTest returns nil outside the sheet/scrim, which is how a tap
  // on empty space passes through to whatever sits below the overlay.
  for (UIView *subview in [self.subviews reverseObjectEnumerator]) {
    CGPoint convertedPoint = [subview convertPoint:point fromView:self];
    UIView *hit = [subview hitTest:convertedPoint withEvent:event];
    if (hit != nil) {
      return hit;
    }
  }
  return nil;
}

- (BOOL)pointInside:(CGPoint)point withEvent:(UIEvent *)event
{
  return [self hitTest:point withEvent:event] != nil;
}

@end

@interface BottomSheetComponentView () <BottomSheetContentViewDelegate, RCTMountingTransactionObserving>
@end

@implementation BottomSheetComponentView {
  BottomSheetContentView *_sheetView;
  State::Shared _sheetState;
  float _lastContentOffsetX;
  float _lastContentOffsetY;
  float _lastContentScale;
  BOOL _needsIndexSyncAfterRecycle;
  BOOL _windowOverlay;
  NSInteger _openOrder;
  BottomSheetOverlayContainerView *_overlayContainer;
  RCTSurfaceTouchHandler *_overlayTouchHandler;
  // The portal host this sheet is presented in, outside window-overlay mode.
  __weak BottomSheetPortalHostComponentView *_portalHost;
  CGSize _lastGeometryFrameSize;
  CGFloat _lastGeometryInset;
  CGFloat _lastGeometrySurfaceOverflow;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<BottomSheetViewComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    const auto &defaultProps = BottomSheetViewShadowNode::defaultSharedProps();
    _props = defaultProps;
    _needsIndexSyncAfterRecycle = NO;
    _windowOverlay = NO;
    _lastContentScale = 1;

    _sheetView = [[BottomSheetContentView alloc] initWithFrame:CGRectZero];
    _sheetView.delegate = self;
    _sheetView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    self.contentView = _sheetView;
  }
  return self;
}

- (void)updateProps:(const Props::Shared &)props oldProps:(const Props::Shared &)oldProps
{
  const auto &newViewProps = static_cast<const BottomSheetViewProps &>(*props);
  const auto &oldViewProps = static_cast<const BottomSheetViewProps &>(*_props);

  // Always update detents — the codegen struct lacks operator==.
  {
    NSMutableArray<NSDictionary *> *detentsArray = [NSMutableArray new];
    for (const auto &detent : newViewProps.detents) {
      [detentsArray addObject:@{
        @"value": @(detent.value),
        @"kind": RCTNSStringFromString(detent.kind),
        @"programmatic": @(detent.programmatic),
        @"usesStatusBarTopInset": @(detent.usesStatusBarTopInset),
        @"insetTop": @(detent.insetTop),
        @"insetLeft": @(detent.insetLeft),
        @"insetRight": @(detent.insetRight),
        @"insetBottom": @(detent.insetBottom),
        @"widestCard": @(detent.widestCard),
        @"maxWidth": @(detent.maxWidth),
        @"placementBias": @(detent.placementBias),
        @"scrimOpacity": @(detent.scrimOpacity),
      }];
    }
    [_sheetView setDetents:detentsArray];
    // The tallest detent bounds the content region, so a detents change can
    // move the pushed inset without any native re-layout; dedups when not.
    [self pushNativeGeometry];
  }

  if (_needsIndexSyncAfterRecycle || newViewProps.index != oldViewProps.index) {
    [_sheetView setDetentIndex:newViewProps.index];
    _needsIndexSyncAfterRecycle = NO;
  }

  if (newViewProps.animateIn != oldViewProps.animateIn) {
    _sheetView.animateIn = newViewProps.animateIn;
  }

  if (newViewProps.animateContentHeight != oldViewProps.animateContentHeight) {
    _sheetView.animateContentHeight = newViewProps.animateContentHeight;
  }

  if (newViewProps.modal != oldViewProps.modal) {
    _sheetView.modal = newViewProps.modal;
  }

  // Diff against the `_windowOverlay` ivar, not `oldViewProps` (which reads the
  // retained `_props`). On a recycled instance `_props` still holds the previous
  // sheet's props, so an `oldProps`-based diff misses true→true and never
  // re-hoists—leaving `prepareForRecycle`'s reset to inline presentation in
  // place, so the sheet renders inline with no overlay or scrim. The ivar is the
  // genuine current presentation state, reset in both `init` and
  // `prepareForRecycle`, so it diffs correctly across recycling.
  if (newViewProps.windowOverlay != _windowOverlay) {
    _windowOverlay = newViewProps.windowOverlay;
    [self updateOverlayPresentation];
    [self updatePortalHost];
  }

  // Diffed against the ivar for the same reason. A window-overlay sheet that
  // opens moves to the front of its window.
  if (newViewProps.openOrder != _openOrder) {
    BOOL didOpen = newViewProps.openOrder > _openOrder;
    _openOrder = newViewProps.openOrder;
    if (didOpen) {
      [self bringOverlayToFront];
    }
  }

  // Compare against native state: codegen defaults to 0 (none), while the
  // hosting view defaults to handoff/initial, so a props diff can skip none.
  if (newViewProps.scrollExpandCoordination != _sheetView.scrollExpandCoordination) {
    _sheetView.scrollExpandCoordination = newViewProps.scrollExpandCoordination;
  }

  if (newViewProps.scrollCollapseCoordination != _sheetView.scrollCollapseCoordination) {
    _sheetView.scrollCollapseCoordination = newViewProps.scrollCollapseCoordination;
  }

  if (newViewProps.settleDuration != oldViewProps.settleDuration) {
    _sheetView.settleDuration = newViewProps.settleDuration;
  }

  // Compared against native state rather than the old props, so the hosting
  // view is right whatever a recycled view's previous props were.
  if (newViewProps.hasCloseRequestHandler != _sheetView.hasCloseRequestHandler) {
    _sheetView.hasCloseRequestHandler = newViewProps.hasCloseRequestHandler;
  }

  if (newViewProps.scrimColor != oldViewProps.scrimColor) {
    [_sheetView setScrimColor:RCTUIColorFromSharedColor(newViewProps.scrimColor)];
  }

  if (newViewProps.scrimAccessibilityLabel != oldViewProps.scrimAccessibilityLabel) {
    [_sheetView setScrimAccessibilityLabel:RCTNSStringFromStringNilIfEmpty(newViewProps.scrimAccessibilityLabel)];
  }

  // Pushed unconditionally (the host dedups): a recycled instance's `_props`
  // still holds the previous sheet's values, so a props diff can miss a reset.
  {
    NSMutableArray<NSNumber *> *radii = [NSMutableArray new];
    for (const auto &radius : newViewProps.surfaceCornerRadii) {
      [radii addObject:@(radius)];
    }
    [_sheetView setSurfaceCornerRadii:radii];
    [_sheetView setSurfaceCornerCurve:newViewProps.surfaceCornerCurve == BottomSheetViewSurfaceCornerCurve::Continuous
                    ? @"continuous"
                    : @"circular"];

    NSMutableArray<NSDictionary *> *shadows = [NSMutableArray new];
    for (const auto &shadow : newViewProps.surfaceShadows) {
      [shadows addObject:@{
        @"offsetX" : @(shadow.offsetX),
        @"offsetY" : @(shadow.offsetY),
        @"blurRadius" : @(shadow.blurRadius),
        @"spreadDistance" : @(shadow.spreadDistance),
        @"color" : RCTUIColorFromSharedColor(shadow.color) ?: UIColor.blackColor,
        @"opacity" : @(shadow.opacity),
        @"excludesInterior" : @(shadow.excludesInterior),
      }];
    }
    [_sheetView setSurfaceShadows:shadows];
  }

  [super updateProps:props oldProps:oldProps];
}

- (void)updateState:(const State::Shared &)state oldState:(const State::Shared &)oldState
{
  _sheetState = state;
  // The committed state carries the content-region inset the content is laid
  // out against in this very transaction; the host uses it to tell a fresh
  // content measurement from one taken against a previous region.
  const auto &data = std::static_pointer_cast<const BottomSheetViewShadowNode::ConcreteState>(state)->getData();
  [_sheetView contentRegionStateDidCommit:data.contentRegionInset];
}

- (void)updateLayoutMetrics:(const LayoutMetrics &)layoutMetrics
           oldLayoutMetrics:(const LayoutMetrics &)oldLayoutMetrics
{
  [super updateLayoutMetrics:layoutMetrics oldLayoutMetrics:oldLayoutMetrics];
  // The sheet node's Yoga padding carries the content-region inset for the
  // in-flow content; the base class would shrink the contentView to the
  // padding-inset content frame, feeding the inset back into the natively
  // measured geometry (host height → inset) in a loop. The native sheet
  // host must keep filling the node — padding is for Yoga children only.
  if (_sheetView.superview == self) {
    _sheetView.frame = self.bounds;
  }
}

- (void)didMoveToWindow
{
  [super didMoveToWindow];
  [self updatePortalHost];
  // Attach once a window is available, detach when leaving it. The window is the
  // anchor for the overlay container, so its lifecycle gates presentation.
  if (_windowOverlay || _overlayContainer != nil) {
    if (self.window == nil) {
      // Fabric reparents this view mid-commit (remove → update → insert) when
      // ancestor view flattening changes — e.g. a pointerEvents flip on open —
      // so the window is often only transiently nil. Tearing the overlay down
      // here would bounce the sheet through the inline slot and re-anchor any
      // running snap against the slot's geometry. Defer one runloop turn: if
      // the window is back by then (the mid-commit case), presentation is left
      // untouched; if the view genuinely left the window, tear down as before.
      __weak __typeof(self) weakSelf = self;
      dispatch_async(dispatch_get_main_queue(), ^{
        __typeof(self) strongSelf = weakSelf;
        if (strongSelf != nil && strongSelf.window == nil) {
          [strongSelf updateOverlayPresentation];
        }
      });
    } else {
      [self updateOverlayPresentation];
    }
  }
}

/// Reconciles where the sheet view is parented with the current `windowOverlay`
/// flag and window availability: hoisted into a full-window container attached to
/// the host window when on, restored as our own `contentView` when off.
- (void)updateOverlayPresentation
{
  UIWindow *window = self.window;

  if (!_windowOverlay) {
    if (_overlayContainer != nil) {
      [self restoreInlinePresentation];
    }
    return;
  }

  if (window != nil) {
    if (_overlayContainer == nil) {
      _overlayContainer = [[BottomSheetOverlayContainerView alloc] initWithFrame:window.bounds];
      _overlayContainer.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    }
    if (_overlayTouchHandler == nil) {
      // Touches inside the container sit outside the RN root view's touch
      // handler, so attach a dedicated surface touch handler to dispatch them to
      // JS (e.g. Pressables in the sheet). It also satisfies the sheet's
      // ancestor "TouchHandler" lookup used to cancel touches on a drag.
      _overlayTouchHandler = [RCTSurfaceTouchHandler new];
    }

    if (_sheetView.superview != _overlayContainer) {
      if (self.contentView == _sheetView) {
        self.contentView = nil;
      }
      _overlayContainer.frame = window.bounds;
      [_overlayContainer addSubview:_sheetView];
      _sheetView.frame = _overlayContainer.bounds;
    }
    if (_overlayContainer.window != window) {
      [self detachOverlayTouchHandler];
      [_overlayContainer removeFromSuperview];
      // Added as the topmost window subview so it floats above modal content in
      // the same window. Opening a sheet brings it back to the top (see
      // `bringOverlayToFront`), so the most recently opened overlay is in front.
      [window addSubview:_overlayContainer];
      [self attachOverlayTouchHandler];
    }
    [self pushNativeGeometry];
    [self updateAccessibilityModality];
  } else {
    if (_overlayContainer != nil) {
      [self restoreInlinePresentation];
    }
  }
}

- (void)bringOverlayToFront
{
  UIView *window = _overlayContainer.superview;
  if (window != nil && window.subviews.lastObject != _overlayContainer) {
    [window bringSubviewToFront:_overlayContainer];
  }
}

- (void)attachOverlayTouchHandler
{
  if (_overlayTouchHandler.view == _overlayContainer) {
    return;
  }
  [self detachOverlayTouchHandler];
  [_overlayTouchHandler attachToView:_overlayContainer];
}

- (void)detachOverlayTouchHandler
{
  UIView *attachedView = _overlayTouchHandler.view;
  if (attachedView != nil) {
    [_overlayTouchHandler detachFromView:attachedView];
  }
}

/// Keeps screen readers out of the screen behind an open modal sheet. In
/// window-overlay mode the overlay container is a window subview, so marking it
/// modal hides the rest of the window; otherwise the portal host does the same
/// for the screen around it.
- (void)updateAccessibilityModality
{
  BOOL isModal = _sheetView.isModalAccessibilityActive;
  _overlayContainer.accessibilityViewIsModal = _windowOverlay && isModal;
  // A presented portal sheet still covers the screen at a transparent detent.
  [_portalHost setSheet:self isModal:!_windowOverlay && _sheetView.modal && _sheetView.isPresented];
}

/// Finds the portal host this sheet is mounted in. Fabric can move the view
/// between parents, so this follows every window change and leaves the previous
/// host first.
- (void)updatePortalHost
{
  BottomSheetPortalHostComponentView *host = nil;
  if (!_windowOverlay && self.window != nil) {
    for (UIView *view = self.superview; view != nil; view = view.superview) {
      if ([view isKindOfClass:BottomSheetPortalHostComponentView.class]) {
        host = (BottomSheetPortalHostComponentView *)view;
        break;
      }
    }
  }
  if (host != _portalHost) {
    [_portalHost setSheet:self isModal:NO];
    _portalHost = host;
  }
  [self updateAccessibilityModality];
}

/// Pushes the current natively measured geometry into the shadow tree: the
/// sheet's frame size (consumed by the component descriptor only in overlay
/// mode, where the sheet is hoisted out of its in-tree slot) and the content
/// region's inset (applied as Yoga bottom padding in every mode, so in-flow
/// content resolves exactly to the tallest height the sheet can reach — the
/// tallest detent, clamped to the space its insets leave). Yoga then lays the sheet
/// subtree out against measured window geometry instead of JS-provided
/// dimensions. Driven by the hosting view's didLayout delegate.
- (void)pushNativeGeometry
{
  if (_sheetView.window == nil || !_sheetState) {
    return;
  }
  CGSize size = _sheetView.bounds.size;
  if (size.width <= 0 || size.height <= 0) {
    return;
  }

  CGFloat inset = _sheetView.contentRegionInset;
  CGFloat surfaceOverflow = [_sheetView surfaceOverflowHolding:_lastGeometrySurfaceOverflow];
  if (CGSizeEqualToSize(size, _lastGeometryFrameSize) && inset == _lastGeometryInset &&
      surfaceOverflow == _lastGeometrySurfaceOverflow) {
    return;
  }
  _lastGeometryFrameSize = size;
  _lastGeometryInset = inset;
  _lastGeometrySurfaceOverflow = surfaceOverflow;
  [self pushStateSnapshot];
}

/// Commits the full native state snapshot — content offset, frame size, and
/// content-region inset — in one update. Always the complete set: the
/// EventQueue coalesces back-to-back state updates for the same node by
/// dropping the older one wholesale, so split partial updates (per-frame
/// offset vs. geometry) would erase each other. See BottomSheetStateHelper.h.
- (void)pushStateSnapshot
{
  if (!_sheetState) {
    return;
  }
  updateBottomSheetState(
      _sheetState,
      _lastContentOffsetX,
      _lastContentOffsetY,
      _lastContentScale,
      {static_cast<Float>(_lastGeometryFrameSize.width),
       static_cast<Float>(_lastGeometryFrameSize.height)},
      isnan(_lastGeometryInset) ? 0 : static_cast<Float>(_lastGeometryInset),
      static_cast<Float>(_lastGeometrySurfaceOverflow));
}

/// Moves the sheet back under this component view and detaches the overlay
/// container. Safe to call when no overlay is active.
- (void)restoreInlinePresentation
{
  if (_overlayContainer != nil) {
    _overlayContainer.accessibilityViewIsModal = NO;
    [self detachOverlayTouchHandler];
    [_overlayContainer removeFromSuperview];
  }
  self.contentView = nil;
  self.contentView = _sheetView;
  _overlayContainer = nil;
}

- (void)mountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index
{
  // Identify the visual surface by component type so the host can own its
  // geometry. Everything else is treated as content.
  if ([childComponentView isKindOfClass:BottomSheetSurfaceComponentView.class]) {
    [_sheetView mountSurfaceComponentView:childComponentView atIndex:index];
  } else {
    [_sheetView mountChildComponentView:childComponentView atIndex:index];
  }
}

- (void)unmountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index
{
  if ([childComponentView isKindOfClass:BottomSheetSurfaceComponentView.class]) {
    [_sheetView unmountSurfaceComponentView:childComponentView];
  } else {
    [_sheetView unmountChildComponentView:childComponentView];
  }
}

#pragma mark - RCTMountingTransactionObserving

/// Fires after a whole mounting transaction has been applied, so every frame
/// in the sheet subtree — the content wrapper sized by the committed
/// content-region inset and the content-height marker inside it — is
/// consistent. The host uses it to resolve a `content` detent it held back
/// while that layout was outstanding; per-view layout hooks fire
/// mid-transaction and cannot tell.
- (void)mountingTransactionDidMount:(const facebook::react::MountingTransaction &)transaction
               withSurfaceTelemetry:(const facebook::react::SurfaceTelemetry &)surfaceTelemetry
{
  [_sheetView contentRegionLayoutDidApply];
  // The content's layout and the detents can move these without the sheet moving.
  [self pushContentGeometry];
}

#pragma mark - BottomSheetContentViewDelegate

- (void)bottomSheetView:(BottomSheetContentView *)view didChangeIndex:(NSInteger)index
{
  if (_eventEmitter) {
    auto emitter = std::static_pointer_cast<const BottomSheetViewEventEmitter>(_eventEmitter);
    emitter->onIndexChange({.index = static_cast<int>(index)});
  }
}

- (void)bottomSheetViewDidRequestClose:(BottomSheetContentView *)view
{
  if (_eventEmitter) {
    auto emitter = std::static_pointer_cast<const BottomSheetViewEventEmitter>(_eventEmitter);
    emitter->onCloseRequest({});
  }
}

- (void)bottomSheetView:(BottomSheetContentView *)view didSettle:(NSInteger)index
{
  [self updateAccessibilityModality];
  if (_eventEmitter) {
    auto emitter = std::static_pointer_cast<const BottomSheetViewEventEmitter>(_eventEmitter);
    emitter->onSettle({.index = static_cast<int>(index)});
  }
}

- (void)bottomSheetView:(BottomSheetContentView *)view
      didChangePosition:(CGFloat)position
                  index:(CGFloat)index
{
  if (_eventEmitter) {
    auto emitter = std::static_pointer_cast<const BottomSheetViewEventEmitter>(_eventEmitter);
    emitter->onPositionChange(
        {.position = static_cast<double>(position), .index = static_cast<double>(index)});
  }

  [self pushContentGeometry];
  [self updateAccessibilityModality];
}

/// The shadow node applies these as a content-origin offset and scale so descendant
/// coordinates (measure, hit-testing) follow the sheet content to where it is
/// physically drawn within the host. In window-overlay mode the content is
/// re-rooted to a window-level full-screen overlay; the shadow node is marked as
/// a layout root in that mode (see BottomSheetViewComponentDescriptor), so the
/// outer-tree origin is dropped structurally and only this in-host displacement
/// remains to be applied here.
- (void)pushContentGeometry
{
  float contentOffsetX = static_cast<float>(_sheetView.currentContentOffsetX);
  float contentOffsetY = static_cast<float>(_sheetView.currentContentOffsetY);
  float contentScale = static_cast<float>(_sheetView.currentContentScale);
  if (contentOffsetX == _lastContentOffsetX && contentOffsetY == _lastContentOffsetY
      && contentScale == _lastContentScale) {
    return;
  }
  _lastContentOffsetX = contentOffsetX;
  _lastContentOffsetY = contentOffsetY;
  _lastContentScale = contentScale;
  [self pushStateSnapshot];
}

- (void)bottomSheetView:(BottomSheetContentView *)view didReportError:(NSString *)message
{
  RCTFatal([NSError errorWithDomain:RCTErrorDomain code:0 userInfo:@{NSLocalizedDescriptionKey : message}]);
}

- (void)bottomSheetViewDidLayout:(BottomSheetContentView *)view
{
  [self pushNativeGeometry];
}

- (void)bottomSheetViewDidUpdateScrim:(BottomSheetContentView *)view
{
  [self updateAccessibilityModality];
}

- (void)prepareForRecycle
{
  [super prepareForRecycle];
  // Restore inline parenting after the base class resets Fabric view state so a
  // reused instance starts from the default presentation.
  _windowOverlay = NO;
  _openOrder = 0;
  [self restoreInlinePresentation];
  [_portalHost setSheet:self isModal:NO];
  _portalHost = nil;
  _needsIndexSyncAfterRecycle = YES;
  [_sheetView resetSheetState];
  _sheetState.reset();
  _lastContentOffsetX = 0;
  _lastContentOffsetY = 0;
  _lastContentScale = 1;
  _lastGeometryFrameSize = CGSizeZero;
  _lastGeometryInset = NAN;
  _lastGeometrySurfaceOverflow = 0;
}

@end

Class<RCTComponentViewProtocol> BottomSheetViewCls(void)
{
  return BottomSheetComponentView.class;
}
