#import "BottomSheetSurfaceComponentView.h"

#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/ComponentDescriptors.h"

#import <React/RCTFabricComponentsPlugins.h>

using namespace facebook::react;

@implementation BottomSheetSurfaceComponentView

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    const auto &defaultProps = BottomSheetSurfaceViewShadowNode::defaultSharedProps();
    _props = defaultProps;
  }
  return self;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<BottomSheetSurfaceViewComponentDescriptor>();
}

@end

Class<RCTComponentViewProtocol> BottomSheetSurfaceViewCls(void)
{
  return BottomSheetSurfaceComponentView.class;
}
