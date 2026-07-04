#import "BottomSheetComponentView.h"
#import "BottomSheetContentView.h"
#import "BottomSheetSurfaceComponentView.h"
#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/BottomSheetStateHelper.h"
#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/ComponentDescriptors.h"

#import <React/RCTAssert.h>
#import <React/RCTConversions.h>
#import <React/RCTFabricComponentsPlugins.h>
#import <React/RCTSurfaceTouchHandler.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/EventEmitters.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/Props.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/RCTComponentViewHelpers.h>

using namespace facebook::react;

/// Full-window container hosting the sheet in native-overlay mode. It is added
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

@interface BottomSheetComponentView () <BottomSheetContentViewDelegate>
@end

@implementation BottomSheetComponentView {
  BottomSheetContentView *_sheetView;
  State::Shared _sheetState;
  float _lastContentOffsetY;
  BOOL _needsIndexSyncAfterRecycle;
  BOOL _nativeOverlay;
  BottomSheetOverlayContainerView *_overlayContainer;
  RCTSurfaceTouchHandler *_overlayTouchHandler;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<BottomSheetViewComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    static const auto defaultProps = std::make_shared<const BottomSheetViewProps>();
    _props = defaultProps;
    _needsIndexSyncAfterRecycle = NO;
    _nativeOverlay = NO;

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
        @"kind": detent.kind == "content" ? @"content" : @"points",
        @"programmatic": @(detent.programmatic),
      }];
    }
    [_sheetView setDetents:detentsArray];
  }

  if (newViewProps.maxDetentHeight != oldViewProps.maxDetentHeight) {
    [_sheetView setMaxDetentHeight:newViewProps.maxDetentHeight];
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

  // Diff against the `_nativeOverlay` ivar, not `oldViewProps` (which reads the
  // retained `_props`). On a recycled instance `_props` still holds the previous
  // sheet's props, so an `oldProps`-based diff misses true→true and never
  // re-hoists—leaving `prepareForRecycle`'s reset to inline presentation in
  // place, so the sheet renders inline with no overlay or scrim. The ivar is the
  // genuine current presentation state, reset in both `init` and
  // `prepareForRecycle`, so it diffs correctly across recycling.
  if (newViewProps.nativeOverlay != _nativeOverlay) {
    _nativeOverlay = newViewProps.nativeOverlay;
    [self updateOverlayPresentation];
  }

  if (newViewProps.disableScrollableNegotiation != oldViewProps.disableScrollableNegotiation) {
    _sheetView.disableScrollableNegotiation = newViewProps.disableScrollableNegotiation;
  }

  if (newViewProps.scrimColor != oldViewProps.scrimColor) {
    [_sheetView setScrimColor:RCTUIColorFromSharedColor(newViewProps.scrimColor)];
  }

  if (newViewProps.scrimOpacities != oldViewProps.scrimOpacities) {
    NSMutableArray<NSNumber *> *opacities = [NSMutableArray new];
    for (const auto &opacity : newViewProps.scrimOpacities) {
      [opacities addObject:@(opacity)];
    }
    [_sheetView setScrimOpacities:opacities];
  }

  [super updateProps:props oldProps:oldProps];
}

- (void)updateState:(const State::Shared &)state oldState:(const State::Shared &)oldState
{
  _sheetState = state;
}

- (void)didMoveToWindow
{
  [super didMoveToWindow];
  // Attach once a window is available, detach when leaving it. The window is the
  // anchor for the overlay container, so its lifecycle gates presentation.
  if (_nativeOverlay || _overlayContainer != nil) {
    [self updateOverlayPresentation];
  }
}

/// Reconciles where the sheet view is parented with the current `nativeOverlay`
/// flag and window availability: hoisted into a full-window container attached to
/// the host window when on, restored as our own `contentView` when off.
- (void)updateOverlayPresentation
{
  UIWindow *window = self.window;

  if (!_nativeOverlay) {
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
      // the same window. With multiple native-overlay sheets active at once, the
      // last one presented wins the z-order; the feature assumes a single overlay
      // sheet visible at a time.
      [window addSubview:_overlayContainer];
      [self attachOverlayTouchHandler];
    }
    [self updateOverlayAccessibilityState];
  } else {
    if (_overlayContainer != nil) {
      [self restoreInlinePresentation];
    }
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

- (void)updateOverlayAccessibilityState
{
  _overlayContainer.accessibilityViewIsModal = _nativeOverlay && _sheetView.isModalAccessibilityActive;
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

#pragma mark - BottomSheetContentViewDelegate

- (void)bottomSheetView:(BottomSheetContentView *)view didChangeIndex:(NSInteger)index
{
  if (_eventEmitter) {
    auto emitter = std::static_pointer_cast<const BottomSheetViewEventEmitter>(_eventEmitter);
    emitter->onIndexChange({.index = static_cast<int>(index)});
  }
}

- (void)bottomSheetView:(BottomSheetContentView *)view didSettle:(NSInteger)index
{
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

  // The shadow node applies this as a content-origin offset so descendant
  // coordinates (measure, hit-testing) follow the sheet content to where it is
  // physically drawn within the host. In native-overlay mode the content is
  // re-rooted to a window-level full-screen overlay; the shadow node is marked as
  // a layout root in that mode (see BottomSheetViewComponentDescriptor), so the
  // outer-tree origin is dropped structurally and only this in-host displacement
  // remains to be applied here.
  float contentOffsetY = static_cast<float>(view.currentContentOffsetY);
  if (contentOffsetY == _lastContentOffsetY) {
    [self updateOverlayAccessibilityState];
    return;
  }
  _lastContentOffsetY = contentOffsetY;

  if (_sheetState) {
    updateBottomSheetContentOffsetY(_sheetState, contentOffsetY);
  }
  [self updateOverlayAccessibilityState];
}

- (void)bottomSheetView:(BottomSheetContentView *)view didReportError:(NSString *)message
{
  RCTFatal([NSError errorWithDomain:RCTErrorDomain code:0 userInfo:@{NSLocalizedDescriptionKey : message}]);
}

- (void)prepareForRecycle
{
  [super prepareForRecycle];
  // Restore inline parenting after the base class resets Fabric view state so a
  // reused instance starts from the default presentation.
  _nativeOverlay = NO;
  [self restoreInlinePresentation];
  _needsIndexSyncAfterRecycle = YES;
  [_sheetView resetSheetState];
  _sheetState.reset();
  _lastContentOffsetY = 0;
}

@end

Class<RCTComponentViewProtocol> BottomSheetViewCls(void)
{
  return BottomSheetComponentView.class;
}
