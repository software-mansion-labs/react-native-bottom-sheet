#import "BottomSheetTestComponentFactory.h"

#import <React/RCTComponentViewFactory.h>
#import <React/RCTComponentViewProtocol.h>
#import <React/RCTFabricComponentsPlugins.h>
#import <React/RCTMountingTransactionObserving.h>
#import <ReactCodegen/RCTThirdPartyComponentsProvider.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/ShadowNodes.h>
#import <objc/runtime.h>

using namespace facebook::react;

static id BottomSheetReadPresentationIvar(id object, const char *name)
{
  if (object == nil) return nil;
  Ivar ivar = class_getInstanceVariable(object_getClass(object), name);
  NSCAssert(ivar != NULL, @"Expected existing presentation ivar %s", name);
  return object_getIvar(object, ivar);
}

static std::shared_ptr<BottomSheetViewProps> BottomSheetMakeTestProps(
    BOOL nativeOverlay,
    NSInteger index,
    BOOL closedDetentProgrammatic)
{
  auto props = std::make_shared<BottomSheetViewProps>();
  props->detents = {
      BottomSheetViewDetentsStruct{0, "points", closedDetentProgrammatic},
      BottomSheetViewDetentsStruct{320, "points", false},
  };
  props->index = static_cast<int>(index);
  props->animateIn = false;
  props->modal = true;
  // Match the NativeView prop used by BottomSheet.tsx, including its empty
  // in-tree slot after the sheet has been hoisted into the window.
  props->pointerEvents = PointerEventsMode::BoxNone;
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
  auto props = BottomSheetMakeTestProps(nativeOverlay, index, NO);
  return [self makeProductionComponentWithProps:props];
}

+ (UIView *)makeProductionOverlayWithoutClosedDetent
{
  auto props = BottomSheetMakeTestProps(YES, 0, NO);
  props->detents = {BottomSheetViewDetentsStruct{320, "points", false}};
  return [self makeProductionComponentWithProps:props];
}

+ (UIView *)makeProductionComponentWithProps:(const std::shared_ptr<BottomSheetViewProps> &)props
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

  Props::Shared sharedProps = props;
  [component updateProps:sharedProps oldProps:component.props];
  return component;
}

+ (void)setIndex:(NSInteger)index forProductionComponent:(UIView *)component
{
  UIView<RCTComponentViewProtocol> *typedComponent = (UIView<RCTComponentViewProtocol> *)component;
  const auto &currentProps = static_cast<const BottomSheetViewProps &>(*typedComponent.props);
  BOOL closedDetentProgrammatic = currentProps.detents.empty()
      ? NO
      : currentProps.detents.front().programmatic;
  auto props =
      BottomSheetMakeTestProps(currentProps.nativeOverlay, index, closedDetentProgrammatic);
  Props::Shared sharedProps = props;
  [typedComponent updateProps:sharedProps oldProps:typedComponent.props];
}

+ (void)setNativeOverlay:(BOOL)nativeOverlay forProductionComponent:(UIView *)component
{
  UIView<RCTComponentViewProtocol> *typedComponent = (UIView<RCTComponentViewProtocol> *)component;
  const auto &currentProps = static_cast<const BottomSheetViewProps &>(*typedComponent.props);
  BOOL closedDetentProgrammatic = currentProps.detents.empty()
      ? NO
      : currentProps.detents.front().programmatic;
  auto props =
      BottomSheetMakeTestProps(nativeOverlay, currentProps.index, closedDetentProgrammatic);
  Props::Shared sharedProps = props;
  [typedComponent updateProps:sharedProps oldProps:typedComponent.props];
}

+ (void)setClosedDetentProgrammatic:(BOOL)programmatic
             forProductionComponent:(UIView *)component
{
  UIView<RCTComponentViewProtocol> *typedComponent = (UIView<RCTComponentViewProtocol> *)component;
  const auto &currentProps = static_cast<const BottomSheetViewProps &>(*typedComponent.props);
  auto props = BottomSheetMakeTestProps(
      currentProps.nativeOverlay,
      currentProps.index,
      programmatic);
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

+ (void)performObservedMountForProductionComponent:(UIView *)component
                                         mutation:(void (^)(void))mutation
{
  NSCAssert([component conformsToProtocol:@protocol(RCTMountingTransactionObserving)],
            @"The production component must observe Fabric mounting transactions");
  id<RCTMountingTransactionObserving> observer = (id<RCTMountingTransactionObserving>)component;
  TransactionTelemetry transactionTelemetry;
  MountingTransaction transaction{1, 1, {}, std::move(transactionTelemetry)};
  SurfaceTelemetry surfaceTelemetry;
  [observer mountingTransactionWillMount:transaction withSurfaceTelemetry:surfaceTelemetry];
  mutation();
  [observer mountingTransactionDidMount:transaction withSurfaceTelemetry:surfaceTelemetry];
}

+ (void)invalidateProductionComponent:(UIView *)component
{
  [(UIView<RCTComponentViewProtocol> *)component invalidate];
}

+ (BottomSheetPresentationController *)presentationControllerForComponent:(UIView *)component
{
  BottomSheetPresentationController *controller =
      BottomSheetReadPresentationIvar(component, "_presentationController");
  NSCAssert(controller != nil, @"The production component must have a presentation controller");
  return controller;
}

+ (nullable UIWindow *)registeredWindowForController:(BottomSheetPresentationController *)controller
{
  id coordinator = BottomSheetReadPresentationIvar(controller, "_coordinator");
  return BottomSheetReadPresentationIvar(coordinator, "_window");
}

@end
