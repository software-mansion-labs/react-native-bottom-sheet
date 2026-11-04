#import "BottomSheetContentWrapperComponentView.h"

#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/ComponentDescriptors.h"

#import <React/RCTFabricComponentsPlugins.h>

using namespace facebook::react;

@implementation BottomSheetContentWrapperComponentView

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    const auto &defaultProps = BottomSheetContentWrapperViewShadowNode::defaultSharedProps();
    _props = defaultProps;
  }
  return self;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<BottomSheetContentWrapperViewComponentDescriptor>();
}

@end

Class<RCTComponentViewProtocol> BottomSheetContentWrapperViewCls(void)
{
  return BottomSheetContentWrapperComponentView.class;
}
