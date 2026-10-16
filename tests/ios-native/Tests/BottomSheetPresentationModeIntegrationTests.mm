#import <UIKit/UIKit.h>
#import <XCTest/XCTest.h>

#import "../../../ios/BottomSheetPresentationOwnership.h"
#import "ReactNativeBottomSheet-Swift.h"
#import "Support/BottomSheetTestComponentFactory.h"

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
  XCTAssertTrue(CGRectEqualToRect(dismiss.frame, hostRect));
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
