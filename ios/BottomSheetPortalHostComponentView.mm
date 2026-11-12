#import "BottomSheetPortalHostComponentView.h"

#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/ComponentDescriptors.h"

#import <React/RCTFabricComponentsPlugins.h>
#import <React/RCTMountingTransactionObserving.h>

using namespace facebook::react;

// VoiceOver honors `accessibilityViewIsModal` only on a window-level view, not
// on one nested in the React Native hierarchy like this host, so what the top
// sheet covers is hidden instead: the host's siblings and the portals below the
// top sheet's, marked `accessibilityElementsHidden` while a sheet is modal, then
// given back their own value. Re-checking after every mount covers views Fabric
// mounts, removes, or reorders meanwhile.
@interface BottomSheetPortalHostComponentView () <RCTMountingTransactionObserving>
@end

@implementation BottomSheetPortalHostComponentView {
  NSHashTable<UIView *> *_modalSheets;
  // The views this host hid. Each one was visible before.
  NSHashTable<UIView *> *_hiddenViews;
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    const auto &defaultProps = BottomSheetPortalHostViewShadowNode::defaultSharedProps();
    _props = defaultProps;
    _modalSheets = [NSHashTable weakObjectsHashTable];
    _hiddenViews = [NSHashTable weakObjectsHashTable];
  }
  return self;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<BottomSheetPortalHostViewComponentDescriptor>();
}

- (void)setSheet:(UIView *)sheet isModal:(BOOL)isModal
{
  if (isModal == [_modalSheets containsObject:sheet]) {
    return;
  }
  if (isModal) {
    [_modalSheets addObject:sheet];
  } else {
    [_modalSheets removeObject:sheet];
  }
  [self updateHiddenViews];
}

- (void)didMoveToWindow
{
  [super didMoveToWindow];
  [self updateHiddenViews];
}

- (void)mountingTransactionDidMount:(const MountingTransaction &)transaction
               withSurfaceTelemetry:(const SurfaceTelemetry &)surfaceTelemetry
{
  [self updateHiddenViews];
}

- (void)updateHiddenViews
{
  NSMutableSet<UIView *> *covered = [NSMutableSet new];
  if (_modalSheets.count > 0 && self.window != nil) {
    // The screen behind the sheets.
    for (UIView *sibling in self.superview.subviews) {
      if (sibling != self) {
        [covered addObject:sibling];
      }
    }
    // The portals below the top modal sheet's. Portals are ordered by when their
    // sheet opened, so the last one holding a modal sheet is on top.
    UIView *topPortal = [self topModalPortal];
    for (UIView *portal in self.subviews) {
      if (portal != topPortal) {
        [covered addObject:portal];
      }
    }
  }
  for (UIView *view in _hiddenViews.allObjects) {
    if (![covered containsObject:view]) {
      [_hiddenViews removeObject:view];
      view.accessibilityElementsHidden = NO;
    }
  }
  for (UIView *view in covered) {
    if (!view.accessibilityElementsHidden) {
      view.accessibilityElementsHidden = YES;
      [_hiddenViews addObject:view];
    }
  }
}

/// The portal, a direct subview of this host, holding the topmost modal sheet.
- (nullable UIView *)topModalPortal
{
  UIView *topPortal = nil;
  NSUInteger topIndex = 0;
  for (UIView *sheet in _modalSheets) {
    UIView *portal = sheet;
    while (portal != nil && portal.superview != self) {
      portal = portal.superview;
    }
    if (portal == nil) {
      continue;
    }
    NSUInteger index = [self.subviews indexOfObject:portal];
    if (topPortal == nil || index > topIndex) {
      topPortal = portal;
      topIndex = index;
    }
  }
  return topPortal;
}

- (void)prepareForRecycle
{
  [super prepareForRecycle];
  [_modalSheets removeAllObjects];
  [self updateHiddenViews];
}

@end

Class<RCTComponentViewProtocol> BottomSheetPortalHostViewCls(void)
{
  return BottomSheetPortalHostComponentView.class;
}
