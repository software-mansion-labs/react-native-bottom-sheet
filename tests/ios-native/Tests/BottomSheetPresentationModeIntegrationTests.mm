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

  std::shared_ptr<const ShadowNode> sheet(Tag tag, NSInteger index,
      bool nativeOverlay = false, PortalChildren children = {}) {
    auto raw = folly::dynamic::object("modal", true)("index", index)("animateIn", false)
        ("nativeOverlay", nativeOverlay)
        ("position", "absolute")("width", 390)("height", 844)
        ("detents", folly::dynamic::array(
            folly::dynamic::object("value", 0)("kind", "points")("programmatic", false),
            folly::dynamic::object("value", 320)("kind", "points")("programmatic", false)));
    auto props = sheets->cloneProps(PropsParserContext(1, *context), nullptr, RawProps(std::move(raw)));
    auto family = sheets->createFamily({.tag = tag, .surfaceId = 1, .instanceHandle = nullptr});
    return sheets->createShadowNode(
        {.props = props, .children = std::make_shared<const PortalChildren>(std::move(children)),
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

- (void)testNativeOverlayComposesSettledLocalPortalWithoutTakingOverItsOutsideContent
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  MountedProductionPortal fixture;
  [window.rootViewController.view addSubview:fixture.viewport];
  auto inner = fixture.sheet(24, 0);
  auto panel = fixture.view(21, {fixture.view(22), fixture.view(23, {inner}, true)});
  auto panelProps = fixture.views.cloneProps(PropsParserContext(1, *fixture.context), panel->getProps(),
      RawProps(folly::dynamic::object("width", 260)("height", 200)));
  panel = std::static_pointer_cast<ViewShadowNode>(panel->clone({.props = panelProps}));
  auto outer = fixture.sheet(44, 1, true, {fixture.view(42), panel});
  fixture.mount({fixture.view(2),
      fixture.view(11, {fixture.view(12), fixture.view(13, {fixture.sheet(14, 1)}, true)}), outer});
  BottomSheetLayoutIntegrationTree(window);
  UIView *outerComponent = fixture.native(44);
  UIView *innerComponent = fixture.native(24);
  UIView *outerContentAncestor = fixture.native(42);
  while (outerContentAncestor && ![outerContentAncestor isKindOfClass:BottomSheetHostingView.class]) {
    outerContentAncestor = outerContentAncestor.superview;
  }
  BottomSheetHostingView *outerHost = (BottomSheetHostingView *)outerContentAncestor;
  BottomSheetHostingView *innerHost = BottomSheetFindIntegrationHost(innerComponent);
  BottomSheetHostingView *underlyingHost = BottomSheetFindIntegrationHost(fixture.native(14));
  XCTAssertNotNil(outerHost);
  XCTAssertNotNil(innerHost);
  XCTAssertNotNil(underlyingHost);
  auto outerController = [BottomSheetTestComponentFactory presentationControllerForComponent:outerComponent];
  auto innerController = [BottomSheetTestComponentFactory presentationControllerForComponent:innerComponent];
  auto underlyingController = [BottomSheetTestComponentFactory presentationControllerForComponent:fixture.native(14)];
  XCTAssertTrue([innerHost isDescendantOfView:outerHost]);
  XCTAssertTrue(CGRectGetWidth(fixture.native(21).bounds) < CGRectGetWidth(outerHost.bounds));
  XCTAssertTrue(innerHost.accessibilityElementsHidden);
  XCTAssertFalse(fixture.native(22).accessibilityElementsHidden);
  XCTAssertFalse(BottomSheetHasHiddenAncestorBeforeWindow(fixture.native(42), window));
  XCTAssertTrue(BottomSheetHasHiddenAncestorBeforeWindow(fixture.native(2), window));

  [innerHost setDetentIndex:1];
  XCTAssertNotNil([innerHost.sheetContainer.layer animationForKey:@"bottomSheetSettle"]);
  XCTAssertTrue(innerHost.accessibilityElementsHidden, @"Opening keeps the preceding interface");
  XCTAssertFalse(fixture.native(22).accessibilityElementsHidden);
  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:innerComponent mutation:^{}];
  XCTAssertFalse(BottomSheetHasHiddenAncestorBeforeWindow(fixture.native(42), window));
  [innerHost animationDidStop:[CAAnimation new] finished:YES];
  XCTAssertTrue(fixture.native(22).accessibilityElementsHidden);
  XCTAssertFalse(innerHost.accessibilityElementsHidden, @"The settled portal is exposed after local masks commit");
  XCTAssertFalse(BottomSheetHasHiddenAncestorBeforeWindow(fixture.native(42), window));
  XCTAssertTrue(BottomSheetHasHiddenAncestorBeforeWindow(fixture.native(2), window));
  XCTAssertTrue(BottomSheetHasHiddenAncestorBeforeWindow(underlyingHost, window));
  XCTAssertTrue(outerHost.superview.accessibilityViewIsModal);
  XCTAssertFalse(innerHost.superview.accessibilityViewIsModal, @"The inner local portal is not window-modal");

  [BottomSheetAccessibilityObservation beginAccessibilityObservationForDismiss:innerHost
      voiceOverRunning:YES focused:NO];
  UIAccessibilityElement *innerOrigin = [[UIAccessibilityElement alloc]
      initWithAccessibilityContainer:innerHost.sheetContainer];
  [BottomSheetAccessibilityObservation setVoiceOverFocusedElement:innerOrigin];
  XCTAssertEqual([innerController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([outerController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteConsume);
  XCTAssertEqual([underlyingController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteConsume);
  [BottomSheetAccessibilityObservation setVoiceOverFocusedElement:fixture.native(42)];
  XCTAssertEqual([outerController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([innerController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteConsume);
  [BottomSheetAccessibilityObservation setVoiceOverFocusedElement:underlyingHost.sheetContainer];
  XCTAssertEqual([outerController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([underlyingController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteConsume);

  [BottomSheetAccessibilityObservation setVoiceOverFocusedElement:innerOrigin];
  XCTAssertTrue([innerHost accessibilityPerformEscape]);
  XCTAssertTrue(fixture.native(22).accessibilityElementsHidden);
  XCTAssertFalse(innerHost.accessibilityElementsHidden, @"Visible closing keeps the committed local interface");
  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:outerComponent mutation:^{}];
  XCTAssertTrue(fixture.native(22).accessibilityElementsHidden,
                @"Refreshing the Active window boundary must not release a moving portal's masks");
  XCTAssertFalse(BottomSheetHasHiddenAncestorBeforeWindow(fixture.native(42), window));
  [innerHost animationDidStop:[CAAnimation new] finished:YES];
  XCTAssertFalse(fixture.native(22).accessibilityElementsHidden);
  XCTAssertTrue(innerHost.accessibilityElementsHidden);
  XCTAssertTrue(BottomSheetHasHiddenAncestorBeforeWindow(fixture.native(2), window));

  [innerHost setDetentIndex:1];
  [innerHost animationDidStop:[CAAnimation new] finished:YES];
  XCTAssertTrue(fixture.native(22).accessibilityElementsHidden);
  [BottomSheetTestComponentFactory prepareForRecycle:innerComponent];
  XCTAssertFalse(fixture.native(22).accessibilityElementsHidden);
  XCTAssertTrue(BottomSheetHasHiddenAncestorBeforeWindow(fixture.native(2), window));
  BottomSheetTearDownIntegrationComponent(outerComponent);
  XCTAssertFalse(BottomSheetHasHiddenAncestorBeforeWindow(fixture.native(2), window));
  XCTAssertTrue(fixture.native(12).accessibilityElementsHidden,
                @"Removing the overlay resumes the independent local portal's isolation");
  BottomSheetTearDownIntegrationComponent(fixture.native(14));
  XCTAssertFalse(fixture.native(12).accessibilityElementsHidden);
  BottomSheetTearDownIntegrationWindow(window);
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
  UIView *closedOverlay = [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:YES index:0];
  closedOverlay.frame = window.bounds;
  [window.rootViewController.view addSubview:closedOverlay];
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
  auto closedOverlayController = [BottomSheetTestComponentFactory presentationControllerForComponent:closedOverlay];
  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:closedOverlayController], window);
  auto portalController = [BottomSheetTestComponentFactory presentationControllerForComponent:component];
  XCTAssertEqual([portalController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal,
                @"A closed, still registered overlay cannot suppress the Active local portal");
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
  BottomSheetTearDownIntegrationComponent(closedOverlay);
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

- (void)testSameWindowReparentPreservesUnmappedPortalRegistrationAndIdentity
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
  BottomSheetPresentationController *upperController =
      [BottomSheetTestComponentFactory presentationControllerForComponent:upperComponent];
  BottomSheetPresentationController *lowerController =
      [BottomSheetTestComponentFactory presentationControllerForComponent:lowerComponent];
  BottomSheetPresentationIdentity *upperIdentity = upperController.identity;
  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:upperController], window);
  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:upperComponent
                                                                  mutation:^{
    [upperComponent removeFromSuperview];
    [backBranch addSubview:upperComponent];
    XCTAssertFalse(upperBoundary.accessibilityViewIsModal);
    XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:upperController], window);
  }];

  XCTAssertFalse(upperBoundary.accessibilityViewIsModal);
  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:lowerController], window);
  XCTAssertEqualObjects(upperController.identity, upperIdentity);

  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:upperComponent
                                                                  mutation:^{
    [window.rootViewController.view bringSubviewToFront:backBranch];
  }];

  XCTAssertFalse(upperBoundary.accessibilityViewIsModal);
  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:upperController], window);
  XCTAssertEqualObjects(upperController.identity, upperIdentity);
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
  BottomSheetPresentationController *controller =
      [BottomSheetTestComponentFactory presentationControllerForComponent:component];
  BottomSheetPresentationIdentity *identity = controller.identity;
  XCTAssertEqual([controller routeForVoiceOverEscape], BottomSheetPresentationEscapeRoutePassThrough);
  UIView *dismiss = BottomSheetAssertIntegrationDismissGeometry(host);

  [BottomSheetTestComponentFactory setNativeOverlay:YES forProductionComponent:component];
  BottomSheetLayoutIntegrationTree(window);

  XCTAssertEqual(host.superview, boundary);
  XCTAssertEqual([BottomSheetTestComponentFactory presentationControllerForComponent:component], controller);
  XCTAssertTrue(boundary.accessibilityViewIsModal);
  XCTAssertFalse(boundary.superview.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);
  XCTAssertEqualObjects(controller.identity, identity);
  XCTAssertEqual([controller routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);
  [BottomSheetTestComponentFactory setNativeOverlay:NO forProductionComponent:component];
  BottomSheetLayoutIntegrationTree(window);

  XCTAssertEqual(host.superview, boundary);
  XCTAssertEqual([BottomSheetTestComponentFactory presentationControllerForComponent:component], controller);
  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);
  XCTAssertEqualObjects(controller.identity, identity);
  XCTAssertEqual([controller routeForVoiceOverEscape], BottomSheetPresentationEscapeRoutePassThrough);
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
  BottomSheetPresentationController *controller =
      [BottomSheetTestComponentFactory presentationControllerForComponent:component];
  BottomSheetPresentationIdentity *identity = controller.identity;
  XCTAssertEqual([controller routeForVoiceOverEscape], BottomSheetPresentationEscapeRoutePassThrough);
  XCTAssertFalse(boundary.accessibilityViewIsModal);
  UIView *dismiss = BottomSheetAssertIntegrationDismissGeometry(host);

  [component removeFromSuperview];

  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  XCTAssertNil([BottomSheetTestComponentFactory registeredWindowForController:controller]);
  component.frame = secondWindow.bounds;
  [secondWindow.rootViewController.view addSubview:component];

  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  BottomSheetLayoutIntegrationTree(secondWindow);

  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertTrue(firstSentinel.accessibilityElementsHidden);
  XCTAssertTrue(secondSentinel.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);
  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:controller], secondWindow);

  XCTAssertEqual([BottomSheetTestComponentFactory presentationControllerForComponent:component], controller);
  XCTAssertEqualObjects(controller.identity, identity);
  XCTAssertEqual([controller routeForVoiceOverEscape], BottomSheetPresentationEscapeRoutePassThrough);

  [component removeFromSuperview];

  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  XCTAssertNil([BottomSheetTestComponentFactory registeredWindowForController:controller]);
  component.frame = firstWindow.bounds;
  [firstWindow.rootViewController.view addSubview:component];

  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  BottomSheetLayoutIntegrationTree(firstWindow);

  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertTrue(firstSentinel.accessibilityElementsHidden);
  XCTAssertTrue(secondSentinel.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);
  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:controller], firstWindow);

  XCTAssertEqual([BottomSheetTestComponentFactory presentationControllerForComponent:component], controller);
  XCTAssertEqualObjects(controller.identity, identity);
  XCTAssertEqual([controller routeForVoiceOverEscape], BottomSheetPresentationEscapeRoutePassThrough);

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
  BottomSheetPresentationController *controller =
      [BottomSheetTestComponentFactory presentationControllerForComponent:component];
  BottomSheetPresentationIdentity *identity = controller.identity;
  XCTAssertEqual([controller routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertTrue(BottomSheetHasHiddenAncestorBeforeWindow(firstWindow.rootViewController.view, firstWindow));
  XCTAssertFalse(BottomSheetHasHiddenAncestorBeforeWindow(secondWindow.rootViewController.view, secondWindow));
  UIView *dismiss = BottomSheetAssertIntegrationDismissGeometry(host);

  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:component
                                                                  mutation:^{
    [component removeFromSuperview];
    component.frame = secondWindow.bounds;
    [secondWindow.rootViewController.view addSubview:component];
  }];

  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  // Mount completion must migrate authority and masks before geometry layout.
  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:controller], secondWindow);
  XCTAssertFalse(BottomSheetHasHiddenAncestorBeforeWindow(firstWindow.rootViewController.view, firstWindow));
  XCTAssertTrue(BottomSheetHasHiddenAncestorBeforeWindow(secondWindow.rootViewController.view, secondWindow));
  XCTAssertTrue(boundary.accessibilityViewIsModal);
  BottomSheetLayoutIntegrationTree(secondWindow);

  XCTAssertTrue(boundary.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);

  XCTAssertEqual([BottomSheetTestComponentFactory presentationControllerForComponent:component], controller);
  XCTAssertEqualObjects(controller.identity, identity);
  XCTAssertEqual([controller routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

  [BottomSheetTestComponentFactory performObservedMountForProductionComponent:component
                                                                  mutation:^{
    [component removeFromSuperview];
    component.frame = firstWindow.bounds;
    [firstWindow.rootViewController.view addSubview:component];
  }];

  XCTAssertFalse(dismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(dismiss.accessibilityFrame, CGRectZero));
  // Mount completion must migrate authority and masks before geometry layout.
  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:controller], firstWindow);
  XCTAssertFalse(BottomSheetHasHiddenAncestorBeforeWindow(secondWindow.rootViewController.view, secondWindow));
  XCTAssertTrue(BottomSheetHasHiddenAncestorBeforeWindow(firstWindow.rootViewController.view, firstWindow));
  XCTAssertTrue(boundary.accessibilityViewIsModal);
  BottomSheetLayoutIntegrationTree(firstWindow);

  XCTAssertTrue(boundary.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), dismiss);

  XCTAssertEqual([BottomSheetTestComponentFactory presentationControllerForComponent:component], controller);
  XCTAssertEqualObjects(controller.identity, identity);
  XCTAssertEqual([controller routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

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
  BottomSheetPresentationController *oldController =
      [BottomSheetTestComponentFactory presentationControllerForComponent:component];
  BottomSheetPresentationIdentity *oldIdentity = oldController.identity;

  BottomSheetHostingView *host = BottomSheetFindIntegrationHost(component);
  UIView *boundary = host.superview;
  UIView *oldDismiss = BottomSheetAssertIntegrationDismissGeometry(host);

  [component removeFromSuperview];
  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertFalse(oldDismiss.isAccessibilityElement);
  XCTAssertTrue(CGRectEqualToRect(oldDismiss.accessibilityFrame, CGRectZero));
  [BottomSheetTestComponentFactory prepareForRecycle:component];

  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertNil([BottomSheetTestComponentFactory registeredWindowForController:oldController]);

  [BottomSheetTestComponentFactory setIndex:1 forProductionComponent:component];
  [BottomSheetTestComponentFactory setLayoutSize:window.bounds.size
                          forProductionComponent:component];
  [window.rootViewController.view addSubview:component];
  BottomSheetLayoutIntegrationTree(window);
  XCTAssertFalse(host.superview.accessibilityViewIsModal);
  XCTAssertEqual(BottomSheetAssertIntegrationDismissGeometry(host), oldDismiss);
  BottomSheetPresentationController *newController =
      [BottomSheetTestComponentFactory presentationControllerForComponent:component];
  BottomSheetPresentationIdentity *newIdentity = newController.identity;

  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:newController], window);

  XCTAssertNotEqualObjects(newIdentity, oldIdentity);

  BottomSheetTearDownIntegrationComponent(component);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testInvalidatingClosingOverlayCancelsHostAndLeavesUnmappedPortalRegistered
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
  BottomSheetPresentationController *upperController =
      [BottomSheetTestComponentFactory presentationControllerForComponent:upperComponent];
  BottomSheetPresentationController *lowerController =
      [BottomSheetTestComponentFactory presentationControllerForComponent:lowerComponent];

  XCTAssertEqual([upperController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);
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
  XCTAssertNil([BottomSheetTestComponentFactory registeredWindowForController:upperController]);

  XCTAssertEqual([lowerController routeForVoiceOverEscape], BottomSheetPresentationEscapeRoutePassThrough);
  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:lowerController], window);
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

  [BottomSheetTestComponentFactory setClosedDetentProgrammatic:YES
                                        forProductionComponent:topComponent];
  BottomSheetLayoutIntegrationTree(window);

  XCTAssertNil(BottomSheetFindIntegrationDismiss(topHost));
  XCTAssertFalse(retainedTopDismiss.isAccessibilityElement);
  XCTAssertTrue(topBoundary.accessibilityViewIsModal);
  XCTAssertFalse(lowerBoundary.accessibilityViewIsModal);
  XCTAssertTrue(BottomSheetHasHiddenAncestorBeforeWindow(lowerBoundary, window));
  XCTAssertTrue([topHost accessibilityPerformEscape]);
  XCTAssertNil([topHost.sheetContainer.layer animationForKey:@"bottomSheetSettle"]);

  BottomSheetTearDownIntegrationComponent(topComponent);
  BottomSheetTearDownIntegrationComponent(lowerComponent);
  BottomSheetTearDownIntegrationWindow(window);
}

@end
