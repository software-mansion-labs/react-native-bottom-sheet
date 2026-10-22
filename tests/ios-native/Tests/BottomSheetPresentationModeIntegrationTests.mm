#import <UIKit/UIKit.h>
#import <XCTest/XCTest.h>

#import "../../../ios/BottomSheetPresentationOwnership.h"
#import "ReactNativeBottomSheet-Swift.h"
#import "Support/BottomSheetTestComponentFactory.h"
#import "Support/BottomSheetAccessibilityObservation.h"
#import "../../../ios/BottomSheetComponentView.h"
#import "../../../ios/BottomSheetPortalMountingObserver.h"
#import <React/RCTComponentViewRegistry.h>
#import <React/RCTMountingManager.h>
#include <react/renderer/components/view/ViewComponentDescriptor.h>

using namespace facebook::react;
using namespace facebook::react::bottomsheet;

namespace {
using PortalChildren = std::vector<std::shared_ptr<const ShadowNode>>;

struct MountedProductionPortal {
  std::shared_ptr<const ContextContainer> context = std::make_shared<ContextContainer>();
  std::shared_ptr<UIManager> manager = std::make_shared<UIManager>(RuntimeExecutor{}, context);
  ViewComponentDescriptor views{{.eventDispatcher = {}, .contextContainer = context, .flavor = nullptr}};
  ComponentDescriptor::Unique sheets = [BottomSheetComponentView componentDescriptorProvider].constructor(
      {.eventDispatcher = {}, .contextContainer = context, .flavor = nullptr});
  RCTMountingManager *__strong mounting = [RCTMountingManager new];
  UIView *__strong viewport = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 390, 844)];
  BottomSheetPortalMountingObserver *__strong observer;
  ShadowTree *tree;

  MountedProductionPortal() {
    auto owned = std::make_unique<ShadowTree>(1,
        LayoutConstraints{.minimumSize = {390, 844}, .maximumSize = {390, 844}},
        LayoutContext{}, *manager, *context);
    tree = owned.get();
    manager->getShadowTreeRegistry().add(std::move(owned));
    [mounting.componentViewRegistry.componentViewFactory registerComponentViewClass:RCTViewComponentView.class];
    [mounting.componentViewRegistry.componentViewFactory registerComponentViewClass:BottomSheetComponentView.class];
    [mounting attachSurfaceToView:viewport surfaceId:1];
    std::weak_ptr<UIManager> weakManager = manager;
    observer = [[BottomSheetPortalMountingObserver alloc] initWithMountingManager:mounting
        uiManagerProvider:^{ return weakManager.lock(); } beforeDeletingNativeSubtrees:nil];
  }

  ~MountedProductionPortal() {
    [observer invalidate];
    [mounting detachSurfaceFromView:viewport surfaceId:1];
    manager->getShadowTreeRegistry().remove(1);
  }

  std::shared_ptr<ViewShadowNode> view(Tag tag, PortalChildren children = {}, bool host = false) {
    folly::dynamic raw = folly::dynamic::object("collapsable", false)
        ("position", "absolute")("width", 390)("height", 844);
    if (host) raw["nativeID"] = PortalHostNativeID;
    auto props = views.cloneProps(PropsParserContext(1, *context), nullptr, RawProps(std::move(raw)));
    auto family = views.createFamily({.tag = tag, .surfaceId = 1, .instanceHandle = nullptr});
    return std::static_pointer_cast<ViewShadowNode>(views.createShadowNode(
        {.props = props, .children = std::make_shared<const PortalChildren>(std::move(children))}, family));
  }

  std::shared_ptr<const ShadowNode> sheet(Tag tag, NSInteger index) {
    auto raw = folly::dynamic::object("modal", true)("index", index)("animateIn", false)
        ("position", "absolute")("width", 390)("height", 844)
        ("detents", folly::dynamic::array(
            folly::dynamic::object("value", 0)("kind", "points")("programmatic", false),
            folly::dynamic::object("value", 320)("kind", "points")("programmatic", false)));
    auto props = sheets->cloneProps(PropsParserContext(1, *context), nullptr, RawProps(std::move(raw)));
    auto family = sheets->createFamily({.tag = tag, .surfaceId = 1, .instanceHandle = nullptr});
    return sheets->createShadowNode(
        {.props = props, .children = ShadowNode::emptySharedShadowNodeSharedList(),
         .state = sheets->createInitialState(props, family)}, family);
  }

  void mount(PortalChildren children) {
    tree->commit([&](const RootShadowNode &current) {
      return std::static_pointer_cast<RootShadowNode>(current.ShadowNode::clone(
          {.children = std::make_shared<const PortalChildren>(std::move(children))}));
    }, {});
    [mounting scheduleTransaction:tree->getMountingCoordinator()];
  }

  UIView *native(Tag tag) {
    return [mounting.componentViewRegistry findComponentViewWithTag:tag];
  }
};
} // namespace

static UIWindow *BottomSheetMakeIntegrationWindow(void)
{
  UIWindow *window = [[UIWindow alloc] initWithFrame:CGRectMake(0, 0, 390, 844)];
  window.rootViewController = [UIViewController new];
  window.rootViewController.view.frame = window.bounds;
  window.hidden = NO;
  return window;
}

static BottomSheetHostingView *BottomSheetFindIntegrationHost(UIView *view)
{
  if ([view isKindOfClass:BottomSheetHostingView.class]) {
    return (BottomSheetHostingView *)view;
  }
  for (UIView *subview in view.subviews) {
    BottomSheetHostingView *host = BottomSheetFindIntegrationHost(subview);
    if (host != nil) {
      return host;
    }
  }
  return nil;
}

static UIView *BottomSheetFindIntegrationDismiss(UIView *view)
{
  if (view.isAccessibilityElement &&
      [view.accessibilityLabel isEqualToString:@"Dismiss"] &&
      (view.accessibilityTraits & UIAccessibilityTraitButton) != 0) {
    return view;
  }
  for (UIView *subview in view.subviews) {
    UIView *dismiss = BottomSheetFindIntegrationDismiss(subview);
    if (dismiss != nil) {
      return dismiss;
    }
  }
  return nil;
}

static UIView *BottomSheetAssertIntegrationDismissGeometry(BottomSheetHostingView *host)
{
  UIView *dismiss = BottomSheetFindIntegrationDismiss(host);
  XCTAssertNotNil(dismiss);
  CGRect hostRect = CGRectMake(
      CGRectGetMinX(host.bounds),
      CGRectGetMinY(host.bounds),
      CGRectGetWidth(host.bounds),
      MAX(0, host.currentContentOffsetY));
  // The live spring sample can advance between the cached Dismiss frame and
  // currentContentOffsetY. Use the same subpixel bound as screen geometry below.
  XCTAssertEqualWithAccuracy(CGRectGetMinX(dismiss.frame), CGRectGetMinX(hostRect), 0.5);
  XCTAssertEqualWithAccuracy(CGRectGetMinY(dismiss.frame), CGRectGetMinY(hostRect), 0.5);
  XCTAssertEqualWithAccuracy(CGRectGetWidth(dismiss.frame), CGRectGetWidth(hostRect), 0.5);
  XCTAssertEqualWithAccuracy(CGRectGetHeight(dismiss.frame), CGRectGetHeight(hostRect), 0.5);
  CGRect expectedFrame = UIAccessibilityConvertFrameToScreenCoordinates(hostRect, host);
  XCTAssertEqualWithAccuracy(
      CGRectGetMinX(dismiss.accessibilityFrame), CGRectGetMinX(expectedFrame), 0.5);
  XCTAssertEqualWithAccuracy(
      CGRectGetMinY(dismiss.accessibilityFrame), CGRectGetMinY(expectedFrame), 0.5);
  XCTAssertEqualWithAccuracy(
      CGRectGetWidth(dismiss.accessibilityFrame), CGRectGetWidth(expectedFrame), 0.5);
  XCTAssertEqualWithAccuracy(
      CGRectGetHeight(dismiss.accessibilityFrame), CGRectGetHeight(expectedFrame), 0.5);
  return dismiss;
}

static BOOL BottomSheetHasHiddenAncestorBeforeWindow(UIView *view, UIWindow *window)
{
  UIView *current = view;
  while (current != nil && current != window) {
    if (current.accessibilityElementsHidden) {
      return YES;
    }
    current = current.superview;
  }
  return NO;
}

static void BottomSheetLayoutIntegrationTree(UIView *view)
{
  [view setNeedsLayout];
  [view layoutIfNeeded];
  for (UIView *subview in view.subviews) {
    BottomSheetLayoutIntegrationTree(subview);
  }
}

static void BottomSheetTearDownIntegrationComponent(UIView *component)
{
  [BottomSheetTestComponentFactory prepareForRecycle:component];
  [component removeFromSuperview];
}

static void BottomSheetTearDownIntegrationWindow(UIWindow *window)
{
  window.hidden = YES;
  window.rootViewController = nil;
}

@interface BottomSheetPresentationModeIntegrationTests : XCTestCase
@end

@implementation BottomSheetPresentationModeIntegrationTests

- (void)tearDown
{
  [BottomSheetAccessibilityObservation endAccessibilityObservation];
  [super tearDown];
}

- (void)testMountedPortalNotifiesAfterOpeningAndClosingIsolationCommits
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  MountedProductionPortal fixture;
  [window.rootViewController.view addSubview:fixture.viewport];
  auto sheet = fixture.sheet(14, 0);
  auto background = fixture.view(12);
  auto inactiveScreen = fixture.view(16);
  auto portal = fixture.view(13, {sheet}, true);
  auto area = fixture.view(11, {background, inactiveScreen, portal});
  fixture.mount({area});
  [fixture.native(16) removeFromSuperview];
  UIView *component = fixture.native(14);
  BottomSheetLayoutIntegrationTree(window);
  BottomSheetHostingView *host = BottomSheetFindIntegrationHost(component);
  XCTAssertNotNil(host);
  XCTAssertFalse(fixture.native(12).accessibilityElementsHidden);
  XCTAssertTrue(host.accessibilityElementsHidden);

  [BottomSheetAccessibilityObservation beginAccessibilityObservationForDismiss:host
      voiceOverRunning:YES focused:NO];
  UIAccessibilityElement *backgroundFocus = [[UIAccessibilityElement alloc]
      initWithAccessibilityContainer:fixture.native(12)];
  [BottomSheetAccessibilityObservation setVoiceOverFocusedElement:backgroundFocus];

  auto openProps = fixture.sheets->cloneProps(PropsParserContext(1, *fixture.context), sheet->getProps(),
      RawProps(folly::dynamic::object("index", 1)));
  auto opened = sheet->clone({.props = openProps});
  auto openPortal = portal->clone({.children = std::make_shared<const PortalChildren>(PortalChildren{opened})});
  fixture.mount({area->clone({.children = std::make_shared<const PortalChildren>(PortalChildren{background, inactiveScreen, openPortal})})});
  XCTAssertNotNil([host.sheetContainer.layer animationForKey:@"bottomSheetSettle"]);
  XCTAssertFalse(fixture.native(12).accessibilityElementsHidden);
  XCTAssertTrue(host.accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 0u);

  [host animationDidStop:[CAAnimation new] finished:YES];

  XCTAssertTrue(fixture.native(12).accessibilityElementsHidden,
                @"After opening settles, the local background must leave accessibility navigation");
  XCTAssertFalse(host.accessibilityElementsHidden,
                 @"After isolation commits, the opened portal content must become accessible");
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 1u);
  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:component mutation:^{}];
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 1u,
                @"An unchanged mount must not restart VoiceOver navigation");

  UIAccessibilityElement *sheetFocus = [[UIAccessibilityElement alloc]
      initWithAccessibilityContainer:host.sheetContainer];
  [BottomSheetAccessibilityObservation setVoiceOverFocusedElement:sheetFocus];
  XCTAssertTrue([host accessibilityPerformEscape],
                @"A portal with a current Fabric source must route its virtual origin to local dismissal");
  XCTAssertTrue(fixture.native(12).accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 1u);
  [host animationDidStop:[CAAnimation new] finished:YES];
  XCTAssertFalse(fixture.native(12).accessibilityElementsHidden);
  XCTAssertTrue(host.accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 2u,
                @"Restoring the previous interface must notify VoiceOver after masks are released");
  BottomSheetTearDownIntegrationComponent(component);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testMountedPortalModeChangesNotifyOnlyWhenTheAccessibleInterfaceChanges
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  MountedProductionPortal fixture;
  [window.rootViewController.view addSubview:fixture.viewport];
  auto sheet = fixture.sheet(14, 1);
  auto background = fixture.view(12);
  auto portal = fixture.view(13, {sheet}, true);
  auto area = fixture.view(11, {background, portal});
  fixture.mount({area});
  BottomSheetLayoutIntegrationTree(window);
  UIView *component = fixture.native(14);
  BottomSheetHostingView *host = BottomSheetFindIntegrationHost(component);
  [BottomSheetAccessibilityObservation beginAccessibilityObservationForDismiss:host
      voiceOverRunning:YES focused:NO];
  auto update = [&](folly::dynamic props) {
    auto nextProps = fixture.sheets->cloneProps(PropsParserContext(1, *fixture.context), sheet->getProps(),
        RawProps(std::move(props)));
    sheet = sheet->clone({.props = nextProps});
    auto nextPortal = portal->clone({.children = std::make_shared<const PortalChildren>(PortalChildren{sheet})});
    fixture.mount({area->clone({.children = std::make_shared<const PortalChildren>(PortalChildren{background, nextPortal})})});
    BottomSheetLayoutIntegrationTree(window);
  };

  update(folly::dynamic::object("nativeOverlay", true));
  XCTAssertTrue(host.superview.accessibilityViewIsModal);
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 1u);
  update(folly::dynamic::object("nativeOverlay", false));
  XCTAssertFalse(host.superview.accessibilityViewIsModal);
  XCTAssertTrue(fixture.native(12).accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 2u);
  update(folly::dynamic::object("modal", false));
  XCTAssertFalse(fixture.native(12).accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 3u);
  update(folly::dynamic::object("modal", true));
  XCTAssertTrue(fixture.native(12).accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 4u);

  update(folly::dynamic::object("scrimOpacities", folly::dynamic::array(0, 0)));
  XCTAssertNil(BottomSheetFindIntegrationDismiss(host));
  update(folly::dynamic::object("detents", folly::dynamic::array(
      folly::dynamic::object("value", 0)("kind", "points")("programmatic", true),
      folly::dynamic::object("value", 320)("kind", "points")("programmatic", false))));
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 4u,
                @"Scrim and dismissal eligibility are not new modal interfaces");
  BottomSheetTearDownIntegrationComponent(component);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testLocalHandoffDoesNotResetAvailableOrUnmappableVoiceOverFocus
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  MountedProductionPortal fixture;
  [window.rootViewController.view addSubview:fixture.viewport];
  auto sheet = fixture.sheet(14, 0);
  fixture.mount({fixture.view(2), fixture.view(11, {fixture.view(12), fixture.view(13, {sheet}, true)})});
  BottomSheetLayoutIntegrationTree(window);
  UIView *component = fixture.native(14);
  BottomSheetHostingView *host = BottomSheetFindIntegrationHost(component);
  [BottomSheetAccessibilityObservation beginAccessibilityObservationForDismiss:host
      voiceOverRunning:YES focused:NO];
  UIAccessibilityElement *outsideFocus = [[UIAccessibilityElement alloc]
      initWithAccessibilityContainer:fixture.native(2)];
  NSUInteger expectedLayouts = 0;
  UIView *outside = fixture.native(2);
  outside.isAccessibilityElement = YES;
  outside.accessibilityElementsHidden = YES; // hides descendants, not its own element
  for (id focus in @[ outside, [NSObject new] ]) {
    [BottomSheetAccessibilityObservation setVoiceOverFocusedElement:focus];
    [host setDetentIndex:1];
    [host animationDidStop:[CAAnimation new] finished:YES];
    XCTAssertTrue(fixture.native(12).accessibilityElementsHidden);
    XCTAssertTrue(fixture.native(2).accessibilityElementsHidden,
                  @"The outside application's pre-hidden descendants must stay unchanged");
    expectedLayouts += 1;
    XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityLayoutChangedNotificationCount], expectedLayouts);
    XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 0u);
    [host setDetentIndex:0];
    [host animationDidStop:[CAAnimation new] finished:YES];
    XCTAssertFalse(fixture.native(12).accessibilityElementsHidden);
    expectedLayouts += 1;
    XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityLayoutChangedNotificationCount], expectedLayouts);
    XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 0u);
  }
  outside.accessibilityElementsHidden = NO;
  [BottomSheetAccessibilityObservation setVoiceOverFocusedElement:outsideFocus];
  [host setDetentIndex:1];
  [host animationDidStop:[CAAnimation new] finished:YES];
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 0u);
  expectedLayouts += 1;
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityLayoutChangedNotificationCount], expectedLayouts);
  BottomSheetTearDownIntegrationComponent(component);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testChangingTheContainingBlockKeepsThePortalAndRefreshesTheInterface
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  MountedProductionPortal fixture;
  [window.rootViewController.view addSubview:fixture.viewport];
  auto sheet = fixture.sheet(14, 1);
  auto portal = fixture.view(13, {sheet}, true);
  auto backgroundA = fixture.view(12);
  auto backgroundC = fixture.view(22);
  auto areaC = fixture.view(21, {backgroundC, portal});
  auto staticProps = fixture.views.cloneProps(PropsParserContext(1, *fixture.context), areaC->getProps(),
      RawProps(folly::dynamic::object("position", "static")));
  auto staticAreaC = areaC->clone({.props = staticProps});
  auto areaA = fixture.view(11, {backgroundA, staticAreaC});
  fixture.mount({areaA});
  BottomSheetLayoutIntegrationTree(window);
  BottomSheetHostingView *host = BottomSheetFindIntegrationHost(fixture.native(14));
  [BottomSheetAccessibilityObservation beginAccessibilityObservationForDismiss:host
      voiceOverRunning:YES focused:NO];
  XCTAssertTrue(fixture.native(12).accessibilityElementsHidden);

  // Change the containing block through ordinary props, preserving every family
  // and native instance rather than illegally moving a ShadowNodeFamily.
  fixture.mount({areaA->clone({
      .children = std::make_shared<const PortalChildren>(PortalChildren{backgroundA, areaC})})});
  BottomSheetLayoutIntegrationTree(window);

  XCTAssertEqual(BottomSheetFindIntegrationHost(fixture.native(14)), host);
  XCTAssertFalse(fixture.native(12).accessibilityElementsHidden);
  XCTAssertTrue(fixture.native(22).accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetAccessibilityObservation accessibilityScreenChangedNotificationCount], 1u,
                @"The same sheet identity can establish a different local modal boundary");
  BottomSheetTearDownIntegrationComponent(fixture.native(14));
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testNestedUnmappedProductionPortalsDoNotExpandToWindowModalBoundary
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  UIView *lowerComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  UIView *upperComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  lowerComponent.frame = window.bounds;
  upperComponent.frame = window.bounds;
  [window.rootViewController.view addSubview:lowerComponent];
  BottomSheetLayoutIntegrationTree(window);
  BottomSheetHostingView *lowerHost = BottomSheetFindIntegrationHost(lowerComponent);
  XCTAssertNotNil(lowerHost);
  [lowerHost.sheetContainer addSubview:upperComponent];
  BottomSheetLayoutIntegrationTree(window);
  BottomSheetHostingView *upperHost = BottomSheetFindIntegrationHost(upperComponent);
  UIView *lowerBoundary = lowerHost.superview;
  UIView *upperBoundary = upperHost.superview;

  XCTAssertFalse(lowerBoundary.accessibilityViewIsModal);
  XCTAssertFalse(upperBoundary.accessibilityViewIsModal);
  XCTAssertTrue([upperBoundary isDescendantOfView:lowerBoundary]);

  BottomSheetTearDownIntegrationComponent(upperComponent);
  BottomSheetTearDownIntegrationComponent(lowerComponent);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testSameWindowReparentPublishesOnlyFinalOrderAndPreservesIdentity
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  UIView *lowerBranch = [[UIView alloc] initWithFrame:window.bounds];
  UIView *upperBranch = [[UIView alloc] initWithFrame:window.bounds];
  UIView *backBranch = [[UIView alloc] initWithFrame:window.bounds];
  UIView *lowerComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  UIView *upperComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  lowerComponent.frame = window.bounds;
  upperComponent.frame = window.bounds;
  [window.rootViewController.view addSubview:lowerBranch];
  [window.rootViewController.view addSubview:upperBranch];
  [window.rootViewController.view insertSubview:backBranch atIndex:0];
  [lowerBranch addSubview:lowerComponent];
  [upperBranch addSubview:upperComponent];
  BottomSheetLayoutIntegrationTree(window);
  BottomSheetHostingView *upperHost = BottomSheetFindIntegrationHost(upperComponent);
  UIView *upperBoundary = upperHost.superview;
  BottomSheetPresentationIdentity *upperIdentity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];
  XCTAssertNotNil(upperIdentity);

  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:upperComponent
                                                                  mutation:^{
    [upperComponent removeFromSuperview];
    [backBranch addSubview:upperComponent];
    XCTAssertFalse(upperBoundary.accessibilityViewIsModal);
    XCTAssertEqualObjects(
        [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window], upperIdentity);
  }];

  XCTAssertFalse(upperBoundary.accessibilityViewIsModal);
  BottomSheetPresentationIdentity *lowerIdentity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];
  XCTAssertNotNil(lowerIdentity);
  XCTAssertNotEqualObjects(lowerIdentity, upperIdentity);

  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:upperComponent
                                                                  mutation:^{
    [window.rootViewController.view bringSubviewToFront:backBranch];
  }];

  XCTAssertFalse(upperBoundary.accessibilityViewIsModal);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window], upperIdentity);

  BottomSheetTearDownIntegrationComponent(upperComponent);
  BottomSheetTearDownIntegrationComponent(lowerComponent);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testMountCompletionReconcilesNativeOverlayZOrderChange
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  UIView *firstBranch = [[UIView alloc] initWithFrame:window.bounds];
  UIView *secondBranch = [[UIView alloc] initWithFrame:window.bounds];
  UIView *firstComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:YES];
  UIView *secondComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:YES];
  BottomSheetHostingView *firstHost = BottomSheetFindIntegrationHost(firstComponent);
  BottomSheetHostingView *secondHost = BottomSheetFindIntegrationHost(secondComponent);
  firstComponent.frame = window.bounds;
  secondComponent.frame = window.bounds;
  [window.rootViewController.view addSubview:firstBranch];
  [window.rootViewController.view addSubview:secondBranch];
  [firstBranch addSubview:firstComponent];
  [secondBranch addSubview:secondComponent];
  BottomSheetLayoutIntegrationTree(window);
  UIView *firstBoundary = firstHost.superview;
  UIView *secondBoundary = secondHost.superview;
  XCTAssertNotNil(firstBoundary);
  XCTAssertNotNil(secondBoundary);
  UIView *firstOverlay = firstBoundary.superview;
  XCTAssertEqual(firstOverlay.superview, window);
  XCTAssertFalse(firstBoundary.accessibilityViewIsModal);
  XCTAssertTrue(secondBoundary.accessibilityViewIsModal);

  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:firstComponent
                                                                  mutation:^{
    firstOverlay.layer.zPosition = 2;
    XCTAssertFalse(firstBoundary.accessibilityViewIsModal);
    XCTAssertTrue(secondBoundary.accessibilityViewIsModal);
  }];

  XCTAssertTrue(firstBoundary.accessibilityViewIsModal);
  XCTAssertFalse(secondBoundary.accessibilityViewIsModal);

  BottomSheetTearDownIntegrationComponent(secondComponent);
  BottomSheetTearDownIntegrationComponent(firstComponent);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testPortalOverlayRoundTripPreservesBoundaryIdentityAndOwnership
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  UIView *component =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  component.frame = window.bounds;
  [window.rootViewController.view addSubview:component];
  BottomSheetLayoutIntegrationTree(window);
  BottomSheetHostingView *host = BottomSheetFindIntegrationHost(component);
  UIView *boundary = host.superview;
  BottomSheetPresentationIdentity *identity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];
  UIView *dismiss = BottomSheetAssertIntegrationDismissGeometry(host);

  [BottomSheetTestComponentFactory setNativeOverlay:YES forProductionComponent:component];
  BottomSheetLayoutIntegrationTree(window);

  XCTAssertEqual(host.superview, boundary);
  XCTAssertTrue(boundary.accessibilityViewIsModal);
  XCTAssertFalse(boundary.superview.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window], identity);

  [BottomSheetTestComponentFactory setNativeOverlay:NO forProductionComponent:component];
  BottomSheetLayoutIntegrationTree(window);

  XCTAssertEqual(host.superview, boundary);
  XCTAssertTrue(boundary.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window], identity);

  BottomSheetTearDownIntegrationComponent(component);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testWindowMigrationRoundTripReconcilesSourceBeforeDestination
{
  UIWindow *firstWindow = BottomSheetMakeIntegrationWindow();
  UIWindow *secondWindow = BottomSheetMakeIntegrationWindow();
  secondWindow.frame = CGRectMake(25, 45, 430, 700);
  secondWindow.rootViewController.view.frame = secondWindow.bounds;
  UIView *firstSentinel = [UIView new];
  UIView *secondSentinel = [UIView new];
  firstSentinel.accessibilityElementsHidden = YES;
  secondSentinel.accessibilityViewIsModal = YES;
  [firstWindow.rootViewController.view addSubview:firstSentinel];
  [secondWindow.rootViewController.view addSubview:secondSentinel];
  UIView *component =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  component.frame = firstWindow.bounds;
  [firstWindow.rootViewController.view addSubview:component];
  BottomSheetLayoutIntegrationTree(firstWindow);
  BottomSheetHostingView *host = BottomSheetFindIntegrationHost(component);
  UIView *boundary = host.superview;
  BottomSheetPresentationIdentity *identity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:firstWindow];
  XCTAssertFalse(boundary.accessibilityViewIsModal);
  UIView *dismiss = BottomSheetAssertIntegrationDismissGeometry(host);

  [component removeFromSuperview];

  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:firstWindow]);
  component.frame = secondWindow.bounds;
  [secondWindow.rootViewController.view addSubview:component];

  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  BottomSheetLayoutIntegrationTree(secondWindow);

  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertTrue(firstSentinel.accessibilityElementsHidden);
  XCTAssertTrue(secondSentinel.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:secondWindow], identity);

  [component removeFromSuperview];

  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:secondWindow]);
  component.frame = firstWindow.bounds;
  [firstWindow.rootViewController.view addSubview:component];

  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  BottomSheetLayoutIntegrationTree(firstWindow);

  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertTrue(firstSentinel.accessibilityElementsHidden);
  XCTAssertTrue(secondSentinel.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:firstWindow], identity);

  BottomSheetTearDownIntegrationComponent(component);
  BottomSheetTearDownIntegrationWindow(secondWindow);
  BottomSheetTearDownIntegrationWindow(firstWindow);
}

- (void)testNativeOverlayWindowMigrationRoundTripRetainsOneIdentity
{
  UIWindow *firstWindow = BottomSheetMakeIntegrationWindow();
  UIWindow *secondWindow = BottomSheetMakeIntegrationWindow();
  secondWindow.frame = CGRectMake(25, 45, 430, 700);
  secondWindow.rootViewController.view.frame = secondWindow.bounds;
  UIView *component =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:YES];
  BottomSheetHostingView *host = BottomSheetFindIntegrationHost(component);
  UIView *boundary = host.superview;
  component.frame = firstWindow.bounds;
  [firstWindow.rootViewController.view addSubview:component];
  BottomSheetLayoutIntegrationTree(firstWindow);
  BottomSheetPresentationIdentity *identity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:firstWindow];
  XCTAssertNotNil(identity);
  UIView *dismiss = BottomSheetAssertIntegrationDismissGeometry(host);

  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:component
                                                                  mutation:^{
    [component removeFromSuperview];
    component.frame = secondWindow.bounds;
    [secondWindow.rootViewController.view addSubview:component];
  }];

  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  BottomSheetLayoutIntegrationTree(secondWindow);

  XCTAssertTrue(boundary.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);
  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:firstWindow]);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:secondWindow], identity);

  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:component
                                                                  mutation:^{
    [component removeFromSuperview];
    component.frame = firstWindow.bounds;
    [firstWindow.rootViewController.view addSubview:component];
  }];

  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  BottomSheetLayoutIntegrationTree(firstWindow);

  XCTAssertTrue(boundary.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);
  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:secondWindow]);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:firstWindow], identity);

  BottomSheetTearDownIntegrationComponent(component);
  BottomSheetTearDownIntegrationWindow(secondWindow);
  BottomSheetTearDownIntegrationWindow(firstWindow);
}

- (void)testRecycleRevokesOldIdentityAndReattachCreatesNewIdentity
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  UIView *component =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  component.frame = window.bounds;
  [window.rootViewController.view addSubview:component];
  BottomSheetLayoutIntegrationTree(window);
  BottomSheetPresentationIdentity *oldIdentity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];
  XCTAssertNotNil(oldIdentity);
  BottomSheetHostingView *host = BottomSheetFindIntegrationHost(component);
  UIView *boundary = host.superview;
  UIView *oldDismiss = BottomSheetAssertIntegrationDismissGeometry(host);

  [component removeFromSuperview];
  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertFalse(oldDismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(oldDismiss.accessibilityFrame, CGRectZero));
  [BottomSheetTestComponentFactory prepareForRecycle:component];

  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window]);

  [BottomSheetTestComponentFactory setIndex:1 forProductionComponent:component];
  [BottomSheetTestComponentFactory setLayoutSize:window.bounds.size
                          forProductionComponent:component];
  [window.rootViewController.view addSubview:component];
  BottomSheetLayoutIntegrationTree(window);
  XCTAssertFalse(host.superview.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), oldDismiss);
  BottomSheetPresentationIdentity *newIdentity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];

  XCTAssertNotNil(newIdentity);
  XCTAssertNotEqualObjects(newIdentity, oldIdentity);

  BottomSheetTearDownIntegrationComponent(component);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testInvalidatingClosingNativeOverlayTopCancelsHostAndPromotesLower
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  UIView *lowerComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  UIView *upperComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:YES];
  BottomSheetHostingView *lowerHost = BottomSheetFindIntegrationHost(lowerComponent);
  BottomSheetHostingView *upperHost = BottomSheetFindIntegrationHost(upperComponent);
  lowerComponent.frame = window.bounds;
  upperComponent.frame = window.bounds;
  [window.rootViewController.view addSubview:lowerComponent];
  [window.rootViewController.view addSubview:upperComponent];
  BottomSheetLayoutIntegrationTree(window);
  UIView *lowerBoundary = lowerHost.superview;
  UIView *upperBoundary = upperHost.superview;
  UIView *overlayContainer = upperBoundary.superview;
  BottomSheetPresentationIdentity *upperIdentity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];
  XCTAssertNotNil(upperIdentity);
  XCTAssertEqual(overlayContainer.superview, window);
  XCTAssertTrue([upperHost accessibilityPerformEscape]);
  XCTAssertNotNil([upperHost.sheetContainer.layer animationForKey:@"bottomSheetSettle"]);
  XCTAssertTrue(upperBoundary.accessibilityViewIsModal);

  [upperComponent removeFromSuperview];
  [BottomSheetTestComponentFactory invalidateProductionComponent:upperComponent];

  XCTAssertFalse(lowerBoundary.accessibilityViewIsModal);
  XCTAssertFalse(upperBoundary.accessibilityViewIsModal);
  XCTAssertNil([upperHost.sheetContainer.layer animationForKey:@"bottomSheetSettle"]);
  XCTAssertEqual(upperHost.sheetContainer.alpha, 0);
  XCTAssertNil(upperBoundary.superview);
  XCTAssertNil(overlayContainer.superview);
  BottomSheetPresentationIdentity *remainingIdentity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];
  XCTAssertNotNil(remainingIdentity);
  XCTAssertNotEqualObjects(remainingIdentity, upperIdentity);
  XCTAssertFalse([upperHost accessibilityPerformEscape]);

  BottomSheetTearDownIntegrationComponent(lowerComponent);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testProgrammaticOnlyTopKeepsLowerPresentationAndBackgroundIsolated
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  UIView *lowerComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  UIView *topComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:YES];
  BottomSheetHostingView *lowerHost = BottomSheetFindIntegrationHost(lowerComponent);
  BottomSheetHostingView *topHost = BottomSheetFindIntegrationHost(topComponent);
  lowerComponent.frame = window.bounds;
  topComponent.frame = window.bounds;
  [window.rootViewController.view addSubview:lowerComponent];
  [window.rootViewController.view addSubview:topComponent];
  BottomSheetLayoutIntegrationTree(window);
  UIView *lowerBoundary = lowerHost.superview;
  UIView *topBoundary = topHost.superview;
  UIView *retainedTopDismiss = BottomSheetAssertIntegrationDismissGeometry(topHost);
  BottomSheetPresentationIdentity *topIdentity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];

  [BottomSheetTestComponentFactory setClosedDetentProgrammatic:YES
                                        forProductionComponent:topComponent];
  BottomSheetLayoutIntegrationTree(window);

  XCTAssertNil(BottomSheetFindIntegrationDismiss(topHost));
  XCTAssertFalse(retainedTopDismiss.isAccessibilityElement);
  XCTAssertTrue(topBoundary.accessibilityViewIsModal);
  XCTAssertFalse(lowerBoundary.accessibilityViewIsModal);
  XCTAssertTrue(BottomSheetHasHiddenAncestorBeforeWindow(lowerBoundary, window));
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window], topIdentity);
  XCTAssertTrue([topHost accessibilityPerformEscape]);
  XCTAssertNil([topHost.sheetContainer.layer animationForKey:@"bottomSheetSettle"]);

  BottomSheetTearDownIntegrationComponent(topComponent);
  BottomSheetTearDownIntegrationComponent(lowerComponent);
  BottomSheetTearDownIntegrationWindow(window);
}

@end
