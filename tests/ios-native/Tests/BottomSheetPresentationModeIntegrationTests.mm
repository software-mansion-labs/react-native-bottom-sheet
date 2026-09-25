#import <UIKit/UIKit.h>
#import <XCTest/XCTest.h>

#import <React/RCTMountingTransactionObserving.h>
#import "../../../ios/BottomSheetPresentationOwnership.h"
#import "ReactNativeBottomSheet-Swift.h"
#import "Support/BottomSheetTestComponentFactory.h"

using namespace facebook::react;

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

static void BottomSheetLayoutIntegrationTree(UIView *view)
{
  [view setNeedsLayout];
  [view layoutIfNeeded];
  for (UIView *subview in view.subviews) {
    BottomSheetLayoutIntegrationTree(subview);
  }
}

static void BottomSheetPerformObservedMount(UIView *component, void (^mutation)(void))
{
  id<RCTMountingTransactionObserving> observer = (id<RCTMountingTransactionObserving>)component;
  XCTAssertTrue([component conformsToProtocol:@protocol(RCTMountingTransactionObserving)]);
  TransactionTelemetry transactionTelemetry;
  MountingTransaction transaction{1, 1, {}, std::move(transactionTelemetry)};
  SurfaceTelemetry surfaceTelemetry;
  [observer mountingTransactionWillMount:transaction withSurfaceTelemetry:surfaceTelemetry];
  mutation();
  [observer mountingTransactionDidMount:transaction withSurfaceTelemetry:surfaceTelemetry];
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

- (void)testNestedProductionPortalUsesDescendantAsTop
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
  XCTAssertTrue(upperBoundary.accessibilityViewIsModal);
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

  BottomSheetPerformObservedMount(upperComponent, ^{
    [upperComponent removeFromSuperview];
    [backBranch addSubview:upperComponent];
    XCTAssertTrue(upperBoundary.accessibilityViewIsModal);
    XCTAssertEqualObjects(
        [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window], upperIdentity);
  });

  XCTAssertFalse(upperBoundary.accessibilityViewIsModal);
  BottomSheetPresentationIdentity *lowerIdentity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];
  XCTAssertNotNil(lowerIdentity);
  XCTAssertNotEqualObjects(lowerIdentity, upperIdentity);

  BottomSheetPerformObservedMount(upperComponent, ^{
    [window.rootViewController.view bringSubviewToFront:backBranch];
  });

  XCTAssertTrue(upperBoundary.accessibilityViewIsModal);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window], upperIdentity);

  BottomSheetTearDownIntegrationComponent(upperComponent);
  BottomSheetTearDownIntegrationComponent(lowerComponent);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testMountCompletionPublishesNativeZOrderChange
{
  UIWindow *window = BottomSheetMakeIntegrationWindow();
  UIView *firstBranch = [[UIView alloc] initWithFrame:window.bounds];
  UIView *secondBranch = [[UIView alloc] initWithFrame:window.bounds];
  UIView *firstComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  UIView *secondComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  firstComponent.frame = window.bounds;
  secondComponent.frame = window.bounds;
  [window.rootViewController.view addSubview:firstBranch];
  [window.rootViewController.view addSubview:secondBranch];
  [firstBranch addSubview:firstComponent];
  [secondBranch addSubview:secondComponent];
  BottomSheetLayoutIntegrationTree(window);
  BottomSheetHostingView *firstHost = BottomSheetFindIntegrationHost(firstComponent);
  BottomSheetHostingView *secondHost = BottomSheetFindIntegrationHost(secondComponent);
  UIView *firstBoundary = firstHost.superview;
  UIView *secondBoundary = secondHost.superview;
  XCTAssertFalse(firstBoundary.accessibilityViewIsModal);
  XCTAssertTrue(secondBoundary.accessibilityViewIsModal);

  BottomSheetPerformObservedMount(firstComponent, ^{
    firstBranch.layer.zPosition = 2;
  });

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

  [BottomSheetTestComponentFactory setNativeOverlay:YES forProductionComponent:component];
  BottomSheetLayoutIntegrationTree(window);

  XCTAssertEqual(host.superview, boundary);
  XCTAssertTrue(boundary.accessibilityViewIsModal);
  XCTAssertFalse(boundary.superview.accessibilityViewIsModal);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window], identity);

  [BottomSheetTestComponentFactory setNativeOverlay:NO forProductionComponent:component];
  BottomSheetLayoutIntegrationTree(window);

  XCTAssertEqual(host.superview, boundary);
  XCTAssertTrue(boundary.accessibilityViewIsModal);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window], identity);

  BottomSheetTearDownIntegrationComponent(component);
  BottomSheetTearDownIntegrationWindow(window);
}

- (void)testWindowMigrationRoundTripReconcilesSourceBeforeDestination
{
  UIWindow *firstWindow = BottomSheetMakeIntegrationWindow();
  UIWindow *secondWindow = BottomSheetMakeIntegrationWindow();
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
  XCTAssertTrue(boundary.accessibilityViewIsModal);

  [component removeFromSuperview];

  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:firstWindow]);
  XCTAssertFalse(boundary.accessibilityViewIsModal);
  [secondWindow.rootViewController.view addSubview:component];
  BottomSheetLayoutIntegrationTree(secondWindow);

  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:secondWindow], identity);
  XCTAssertTrue(boundary.accessibilityViewIsModal);
  XCTAssertTrue(firstSentinel.accessibilityElementsHidden);
  XCTAssertTrue(secondSentinel.accessibilityViewIsModal);

  [component removeFromSuperview];

  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:secondWindow]);
  XCTAssertFalse(boundary.accessibilityViewIsModal);
  [firstWindow.rootViewController.view addSubview:component];
  BottomSheetLayoutIntegrationTree(firstWindow);

  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:firstWindow], identity);
  XCTAssertTrue(boundary.accessibilityViewIsModal);
  XCTAssertTrue(firstSentinel.accessibilityElementsHidden);
  XCTAssertTrue(secondSentinel.accessibilityViewIsModal);

  BottomSheetTearDownIntegrationComponent(component);
  BottomSheetTearDownIntegrationWindow(secondWindow);
  BottomSheetTearDownIntegrationWindow(firstWindow);
}

- (void)testNativeOverlayWindowMigrationRoundTripRetainsOneIdentity
{
  UIWindow *firstWindow = BottomSheetMakeIntegrationWindow();
  UIWindow *secondWindow = BottomSheetMakeIntegrationWindow();
  UIView *component =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:YES];
  component.frame = firstWindow.bounds;
  [firstWindow.rootViewController.view addSubview:component];
  BottomSheetLayoutIntegrationTree(firstWindow);
  BottomSheetPresentationIdentity *identity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:firstWindow];
  XCTAssertNotNil(identity);

  BottomSheetPerformObservedMount(component, ^{
    [component removeFromSuperview];
    [secondWindow.rootViewController.view addSubview:component];
    BottomSheetLayoutIntegrationTree(secondWindow);
  });

  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:firstWindow]);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:secondWindow], identity);

  BottomSheetPerformObservedMount(component, ^{
    [component removeFromSuperview];
    [firstWindow.rootViewController.view addSubview:component];
    BottomSheetLayoutIntegrationTree(firstWindow);
  });

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

  [component removeFromSuperview];
  [BottomSheetTestComponentFactory prepareForRecycle:component];

  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window]);

  [BottomSheetTestComponentFactory setIndex:1 forProductionComponent:component];
  [BottomSheetTestComponentFactory setLayoutSize:window.bounds.size
                          forProductionComponent:component];
  [window.rootViewController.view addSubview:component];
  BottomSheetLayoutIntegrationTree(window);
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

  BottomSheetPresentationIdentity *remainingIdentity =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];
  XCTAssertNotNil(remainingIdentity);
  XCTAssertNotEqualObjects(remainingIdentity, upperIdentity);
  XCTAssertTrue(lowerBoundary.accessibilityViewIsModal);
  XCTAssertFalse(upperBoundary.accessibilityViewIsModal);
  XCTAssertNil([upperHost.sheetContainer.layer animationForKey:@"bottomSheetSettle"]);
  XCTAssertEqual(upperHost.sheetContainer.alpha, 0);
  XCTAssertFalse([upperHost accessibilityPerformEscape]);
  XCTAssertNil(upperBoundary.superview);
  XCTAssertNil(overlayContainer.superview);

  BottomSheetTearDownIntegrationComponent(lowerComponent);
  BottomSheetTearDownIntegrationWindow(window);
}

@end
