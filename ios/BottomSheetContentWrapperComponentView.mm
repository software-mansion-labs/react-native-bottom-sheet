#import "BottomSheetContentWrapperComponentView.h"

#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/BottomSheetStateHelper.h"
#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/ComponentDescriptors.h"

#import <React/RCTFabricComponentsPlugins.h>

using namespace facebook::react;

@implementation BottomSheetContentWrapperComponentView {
  State::Shared _state;
  CGSize _lastPushedSize;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<BottomSheetContentWrapperViewComponentDescriptor>();
}

- (void)updateState:(const State::Shared &)state oldState:(const State::Shared &)oldState
{
  _state = state;
}

- (void)updateFrameSize:(CGSize)size
{
  if (CGSizeEqualToSize(size, _lastPushedSize)) {
    return;
  }
  if (!_state) {
    return;
  }
  _lastPushedSize = size;
  updateBottomSheetContentWrapperFrameSize(
      _state, {static_cast<Float>(size.width), static_cast<Float>(size.height)});
}

- (void)prepareForRecycle
{
  [super prepareForRecycle];
  _state.reset();
  _lastPushedSize = CGSizeZero;
}

@end

Class<RCTComponentViewProtocol> BottomSheetContentWrapperViewCls(void)
{
  return BottomSheetContentWrapperComponentView.class;
}
