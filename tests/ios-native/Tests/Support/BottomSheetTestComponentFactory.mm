#import "BottomSheetTestComponentFactory.h"

#import <React/RCTComponentViewFactory.h>
#import <React/RCTComponentViewProtocol.h>
#import <React/RCTFabricComponentsPlugins.h>
#import <ReactCodegen/RCTThirdPartyComponentsProvider.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/ShadowNodes.h>

using namespace facebook::react;

static std::shared_ptr<BottomSheetViewProps> BottomSheetMakeTestProps(
    BOOL nativeOverlay,
    NSInteger index)
{
  auto props = std::make_shared<BottomSheetViewProps>();
  props->detents = {
      BottomSheetViewDetentsStruct{0, "points", false},
      BottomSheetViewDetentsStruct{320, "points", false},
  };
  props->index = static_cast<int>(index);
  props->animateIn = false;
  props->modal = true;
  props->nativeOverlay = nativeOverlay;
  props->scrimOpacities = {0, 1};
  return props;
}

@implementation BottomSheetTestComponentFactory

+ (UIView *)makeProductionComponentWithNativeOverlay:(BOOL)nativeOverlay
{
  return [self makeProductionComponentWithNativeOverlay:nativeOverlay index:1];
}

+ (UIView *)makeProductionComponentWithNativeOverlay:(BOOL)nativeOverlay index:(NSInteger)index
{
  Class<RCTComponentViewProtocol> componentClass = RCTFabricComponentsProvider("BottomSheetView");
  if (componentClass == Nil) {
    componentClass = [RCTThirdPartyComponentsProvider thirdPartyFabricComponents][@"BottomSheetView"];
  }
  NSCAssert(componentClass != Nil, @"BottomSheetView must be registered with the Fabric provider");

  RCTComponentViewFactory *factory = [RCTComponentViewFactory new];
  [factory registerComponentViewClass:componentClass];
  auto descriptor = [factory createComponentViewWithComponentHandle:BottomSheetViewShadowNode::Handle()];
  UIView<RCTComponentViewProtocol> *component = descriptor.view;

  auto props = BottomSheetMakeTestProps(nativeOverlay, index);
  Props::Shared sharedProps = props;
  [component updateProps:sharedProps oldProps:component.props];
  return component;
}

+ (void)setIndex:(NSInteger)index forProductionComponent:(UIView *)component
{
  UIView<RCTComponentViewProtocol> *typedComponent = (UIView<RCTComponentViewProtocol> *)component;
  const auto &currentProps = static_cast<const BottomSheetViewProps &>(*typedComponent.props);
  auto props = BottomSheetMakeTestProps(currentProps.nativeOverlay, index);
  Props::Shared sharedProps = props;
  [typedComponent updateProps:sharedProps oldProps:typedComponent.props];
}

+ (void)setNativeOverlay:(BOOL)nativeOverlay forProductionComponent:(UIView *)component
{
  UIView<RCTComponentViewProtocol> *typedComponent = (UIView<RCTComponentViewProtocol> *)component;
  const auto &currentProps = static_cast<const BottomSheetViewProps &>(*typedComponent.props);
  auto props = BottomSheetMakeTestProps(nativeOverlay, currentProps.index);
  Props::Shared sharedProps = props;
  [typedComponent updateProps:sharedProps oldProps:typedComponent.props];
}

+ (void)setLayoutSize:(CGSize)size forProductionComponent:(UIView *)component
{
  UIView<RCTComponentViewProtocol> *typedComponent = (UIView<RCTComponentViewProtocol> *)component;
  LayoutMetrics layoutMetrics;
  layoutMetrics.frame.size = {
      static_cast<Float>(size.width),
      static_cast<Float>(size.height),
  };
  [typedComponent updateLayoutMetrics:layoutMetrics oldLayoutMetrics:EmptyLayoutMetrics];
}

+ (void)prepareForRecycle:(UIView *)component
{
  [(UIView<RCTComponentViewProtocol> *)component prepareForRecycle];
}

+ (void)invalidateProductionComponent:(UIView *)component
{
  [(UIView<RCTComponentViewProtocol> *)component invalidate];
}

@end
