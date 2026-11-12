#import "BottomSheetPortalHostComponentView.h"

#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/ComponentDescriptors.h"

#import <React/RCTFabricComponentsPlugins.h>
#import <React/RCTMountingTransactionObserving.h>

using namespace facebook::react;

// VoiceOver honors `accessibilityViewIsModal` only on a window-level view, not
// on one nested in the React Native hierarchy like this host, so the siblings
// are hidden instead: marked `accessibilityElementsHidden` while a sheet is
// modal, then given back their own value. Re-checking after every mount covers
// siblings Fabric mounts or removes meanwhile.
@interface BottomSheetPortalHostComponentView () <RCTMountingTransactionObserving>
@end

@implementation BottomSheetPortalHostComponentView {
  NSHashTable<UIView *> *_modalSheets;
  // The siblings this host hid. Each one was visible before.
  NSHashTable<UIView *> *_hiddenSiblings;
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    const auto &defaultProps = BottomSheetPortalHostViewShadowNode::defaultSharedProps();
    _props = defaultProps;
    _modalSheets = [NSHashTable weakObjectsHashTable];
    _hiddenSiblings = [NSHashTable weakObjectsHashTable];
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
  [self updateSiblings];
}

- (void)didMoveToWindow
{
  [super didMoveToWindow];
  [self updateSiblings];
}

- (void)mountingTransactionDidMount:(const MountingTransaction &)transaction
               withSurfaceTelemetry:(const SurfaceTelemetry &)surfaceTelemetry
{
  [self updateSiblings];
}

- (void)updateSiblings
{
  UIView *container = _modalSheets.count > 0 && self.window != nil ? self.superview : nil;
  // Restore what is no longer a sibling, or everything once nothing is modal.
  for (UIView *sibling in _hiddenSiblings.allObjects) {
    if (container == nil || sibling.superview != container) {
      [self restoreSibling:sibling];
    }
  }
  for (UIView *sibling in container.subviews) {
    if (sibling != self && !sibling.accessibilityElementsHidden) {
      sibling.accessibilityElementsHidden = YES;
      [_hiddenSiblings addObject:sibling];
    }
  }
}

- (void)restoreSibling:(UIView *)sibling
{
  [_hiddenSiblings removeObject:sibling];
  sibling.accessibilityElementsHidden = NO;
}

- (void)prepareForRecycle
{
  [super prepareForRecycle];
  [_modalSheets removeAllObjects];
  [self updateSiblings];
}

@end

Class<RCTComponentViewProtocol> BottomSheetPortalHostViewCls(void)
{
  return BottomSheetPortalHostComponentView.class;
}
