#import <UIKit/UIKit.h>
#import <XCTest/XCTest.h>

#import <math.h>

#import "../../../ios/BottomSheetPresentationOwnership.h"
#import "Support/BottomSheetTestComponentFactory.h"

@interface BottomSheetPresentationOwnershipTests : XCTestCase
@end

static UIWindow *BottomSheetMakeTestWindow(void)
{
  UIWindow *window = [[UIWindow alloc] initWithFrame:CGRectMake(0, 0, 390, 844)];
  window.rootViewController = [UIViewController new];
  window.hidden = NO;
  return window;
}

static BottomSheetPresentationController *BottomSheetActivateCandidate(
    UIView *anchor,
    BottomSheetPresentationMode mode)
{
  BottomSheetPresentationController *controller =
      [[BottomSheetPresentationController alloc] initWithAnchor:anchor];
  [controller updateModal:YES active:YES mode:mode];
  return controller;
}

static void BottomSheetTearDownWindow(UIWindow *window)
{
  window.hidden = YES;
  window.rootViewController = nil;
}

static void BottomSheetLayoutViewTree(UIView *root)
{
  [root setNeedsLayout];
  [root layoutIfNeeded];
  for (UIView *subview in root.subviews) {
    BottomSheetLayoutViewTree(subview);
  }
}

@implementation BottomSheetPresentationOwnershipTests

- (void)testPureResolverFiltersInactiveCandidate
{
  BottomSheetPresentationIdentity *lowerIdentity = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *upperIdentity = [BottomSheetPresentationIdentity new];
  NSArray *candidates = @[
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:lowerIdentity active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:upperIdentity active:NO eligible:YES],
  ];

  BottomSheetPresentationIdentity *topIdentity =
      [BottomSheetPresentationResolver topPresentationIdentityFromCandidates:candidates
                                                               orderProvider:^BottomSheetPresentationOrder(
                                                                   BottomSheetPresentationIdentity *first,
                                                                   BottomSheetPresentationIdentity *second) {
    return first == upperIdentity ? BottomSheetPresentationOrderAbove
                                  : BottomSheetPresentationOrderBelow;
  }];

  XCTAssertEqualObjects(topIdentity, lowerIdentity);
}

- (void)testPureResolverReturnsNoTopForUnknownRelationship
{
  BottomSheetPresentationIdentity *firstIdentity = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *secondIdentity = [BottomSheetPresentationIdentity new];
  NSArray *candidates = @[
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:firstIdentity active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:secondIdentity active:YES eligible:YES],
  ];

  BottomSheetPresentationIdentity *topIdentity =
      [BottomSheetPresentationResolver topPresentationIdentityFromCandidates:candidates
                                                               orderProvider:^BottomSheetPresentationOrder(
                                                                   BottomSheetPresentationIdentity *first,
                                                                   BottomSheetPresentationIdentity *second) {
    return BottomSheetPresentationOrderUnknown;
  }];

  XCTAssertNil(topIdentity);
}

- (void)testEscapeResolverAttemptsForTopAndConsumesLower
{
  BottomSheetPresentationIdentity *lowerIdentity = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *topIdentity = [BottomSheetPresentationIdentity new];

  XCTAssertEqual(
      [BottomSheetPresentationEscapeResolver routeForCallerIdentity:topIdentity
                                             topPresentationIdentity:topIdentity],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual(
      [BottomSheetPresentationEscapeResolver routeForCallerIdentity:lowerIdentity
                                             topPresentationIdentity:topIdentity],
      BottomSheetPresentationEscapeRouteConsume);
}

- (void)testEscapeResolverPassesThroughWithoutTop
{
  BottomSheetPresentationIdentity *callerIdentity = [BottomSheetPresentationIdentity new];

  XCTAssertEqual(
      [BottomSheetPresentationEscapeResolver routeForCallerIdentity:callerIdentity
                                             topPresentationIdentity:nil],
      BottomSheetPresentationEscapeRoutePassThrough);
}

- (void)testOneActiveCandidateBecomesTop
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *anchor = [UIView new];
  [window.rootViewController.view addSubview:anchor];
  BottomSheetPresentationController *controller =
      BottomSheetActivateCandidate(anchor, BottomSheetPresentationModePortal);

  XCTAssertTrue(controller.isTopPresentation);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window],
      controller.identity);

  [controller invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testLaterSiblingWinsRegardlessOfRegistrationOrder
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *lowerAnchor = [UIView new];
  UIView *upperAnchor = [UIView new];
  [window.rootViewController.view addSubview:lowerAnchor];
  [window.rootViewController.view addSubview:upperAnchor];

  BottomSheetPresentationController *upperController =
      BottomSheetActivateCandidate(upperAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateCandidate(lowerAnchor, BottomSheetPresentationModePortal);

  XCTAssertTrue(upperController.isTopPresentation);
  XCTAssertFalse(lowerController.isTopPresentation);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window],
      upperController.identity);

  [lowerController invalidate];
  [upperController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testDescendantCandidateOutranksPresentationAncestor
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *ancestorAnchor = [UIView new];
  UIView *descendantAnchor = [UIView new];
  [window.rootViewController.view addSubview:ancestorAnchor];
  [ancestorAnchor addSubview:descendantAnchor];

  BottomSheetPresentationController *ancestorController =
      BottomSheetActivateCandidate(ancestorAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *descendantController =
      BottomSheetActivateCandidate(descendantAnchor, BottomSheetPresentationModePortal);

  XCTAssertFalse(ancestorController.isTopPresentation);
  XCTAssertTrue(descendantController.isTopPresentation);

  [descendantController invalidate];
  [ancestorController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testFiniteBranchZPositionOverridesSiblingIndex
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *higherZBranch = [UIView new];
  UIView *laterSiblingBranch = [UIView new];
  higherZBranch.layer.zPosition = 2;
  [window.rootViewController.view addSubview:higherZBranch];
  [window.rootViewController.view addSubview:laterSiblingBranch];

  UIView *higherZAnchor = [UIView new];
  UIView *laterSiblingAnchor = [UIView new];
  [higherZBranch addSubview:higherZAnchor];
  [laterSiblingBranch addSubview:laterSiblingAnchor];
  BottomSheetPresentationController *higherZController =
      BottomSheetActivateCandidate(higherZAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *laterSiblingController =
      BottomSheetActivateCandidate(laterSiblingAnchor, BottomSheetPresentationModePortal);

  XCTAssertTrue(higherZController.isTopPresentation);
  XCTAssertFalse(laterSiblingController.isTopPresentation);

  [laterSiblingController invalidate];
  [higherZController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testCurrentSiblingReorderChangesTopWithoutReregistration
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *firstAnchor = [UIView new];
  UIView *secondAnchor = [UIView new];
  [window.rootViewController.view addSubview:firstAnchor];
  [window.rootViewController.view addSubview:secondAnchor];
  BottomSheetPresentationController *firstController =
      BottomSheetActivateCandidate(firstAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *secondController =
      BottomSheetActivateCandidate(secondAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationIdentity *firstIdentity = firstController.identity;

  [window.rootViewController.view bringSubviewToFront:firstAnchor];
  [secondController reconcilePresentationOrder];

  XCTAssertTrue(firstController.isTopPresentation);
  XCTAssertFalse(secondController.isTopPresentation);
  XCTAssertEqualObjects(firstController.identity, firstIdentity);

  [secondController invalidate];
  [firstController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testCurrentFiniteZChangeChangesTopWithoutReregistration
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *firstBranch = [UIView new];
  UIView *secondBranch = [UIView new];
  [window.rootViewController.view addSubview:firstBranch];
  [window.rootViewController.view addSubview:secondBranch];
  UIView *firstAnchor = [UIView new];
  UIView *secondAnchor = [UIView new];
  [firstBranch addSubview:firstAnchor];
  [secondBranch addSubview:secondAnchor];
  BottomSheetPresentationController *firstController =
      BottomSheetActivateCandidate(firstAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *secondController =
      BottomSheetActivateCandidate(secondAnchor, BottomSheetPresentationModePortal);

  firstBranch.layer.zPosition = 3;
  [firstController reconcilePresentationOrder];

  XCTAssertTrue(firstController.isTopPresentation);
  XCTAssertFalse(secondController.isTopPresentation);

  [secondController invalidate];
  [firstController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testNonfiniteBranchZProducesNoTopWithoutRegistrationFallback
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *firstBranch = [UIView new];
  UIView *secondBranch = [UIView new];
  firstBranch.layer.zPosition = NAN;
  [window.rootViewController.view addSubview:firstBranch];
  [window.rootViewController.view addSubview:secondBranch];
  UIView *firstAnchor = [UIView new];
  UIView *secondAnchor = [UIView new];
  [firstBranch addSubview:firstAnchor];
  [secondBranch addSubview:secondAnchor];

  BottomSheetPresentationController *secondController =
      BottomSheetActivateCandidate(secondAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *firstController =
      BottomSheetActivateCandidate(firstAnchor, BottomSheetPresentationModePortal);

  XCTAssertFalse(firstController.isTopPresentation);
  XCTAssertFalse(secondController.isTopPresentation);
  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window]);

  [firstController invalidate];
  [secondController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testInactiveUpperCandidateLeavesLowerCandidateTop
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *lowerAnchor = [UIView new];
  UIView *upperAnchor = [UIView new];
  [window.rootViewController.view addSubview:lowerAnchor];
  [window.rootViewController.view addSubview:upperAnchor];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateCandidate(lowerAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *upperController =
      [[BottomSheetPresentationController alloc] initWithAnchor:upperAnchor];
  [upperController updateModal:YES active:NO mode:BottomSheetPresentationModePortal];

  XCTAssertTrue(lowerController.isTopPresentation);
  XCTAssertFalse(upperController.isTopPresentation);

  [upperController invalidate];
  [lowerController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testPortalAndNativeOverlayModeDoNotOverrideNativeOrder
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *overlayAnchor = [UIView new];
  UIView *portalAnchor = [UIView new];
  [window.rootViewController.view addSubview:overlayAnchor];
  [window.rootViewController.view addSubview:portalAnchor];

  BottomSheetPresentationController *portalController =
      BottomSheetActivateCandidate(portalAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *overlayController =
      BottomSheetActivateCandidate(overlayAnchor, BottomSheetPresentationModeNativeOverlay);

  XCTAssertTrue(portalController.isTopPresentation);
  XCTAssertFalse(overlayController.isTopPresentation);

  [overlayController invalidate];
  [portalController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testCandidatesInDifferentWindowsHaveIndependentTopPresentations
{
  UIWindow *firstWindow = BottomSheetMakeTestWindow();
  UIWindow *secondWindow = BottomSheetMakeTestWindow();
  UIView *firstAnchor = [UIView new];
  UIView *secondAnchor = [UIView new];
  [firstWindow.rootViewController.view addSubview:firstAnchor];
  [secondWindow.rootViewController.view addSubview:secondAnchor];

  BottomSheetPresentationController *firstController =
      BottomSheetActivateCandidate(firstAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *secondController =
      BottomSheetActivateCandidate(secondAnchor, BottomSheetPresentationModeNativeOverlay);

  XCTAssertTrue(firstController.isTopPresentation);
  XCTAssertTrue(secondController.isTopPresentation);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:firstWindow],
      firstController.identity);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:secondWindow],
      secondController.identity);

  [secondController invalidate];
  [firstController invalidate];
  BottomSheetTearDownWindow(secondWindow);
  BottomSheetTearDownWindow(firstWindow);
}

- (void)testDetachedCandidateCannotRemainTop
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *lowerAnchor = [UIView new];
  UIView *upperAnchor = [UIView new];
  [window.rootViewController.view addSubview:lowerAnchor];
  [window.rootViewController.view addSubview:upperAnchor];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateCandidate(lowerAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *upperController =
      BottomSheetActivateCandidate(upperAnchor, BottomSheetPresentationModePortal);

  [upperAnchor removeFromSuperview];
  [upperController reconcilePresentationOrder];

  XCTAssertFalse(upperController.isTopPresentation);
  XCTAssertTrue(lowerController.isTopPresentation);

  [upperController invalidate];
  [lowerController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testDuplicateAttachedAnchorIsIncomparableAndProducesNoTop
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *anchor = [UIView new];
  [window.rootViewController.view addSubview:anchor];
  BottomSheetPresentationController *firstController =
      BottomSheetActivateCandidate(anchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *secondController =
      BottomSheetActivateCandidate(anchor, BottomSheetPresentationModeNativeOverlay);

  XCTAssertFalse(firstController.isTopPresentation);
  XCTAssertFalse(secondController.isTopPresentation);
  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window]);

  [secondController invalidate];
  [firstController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testRemovingCurrentTopTransfersNeutralOwnershipToNextCandidate
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *lowerAnchor = [UIView new];
  UIView *upperAnchor = [UIView new];
  [window.rootViewController.view addSubview:lowerAnchor];
  [window.rootViewController.view addSubview:upperAnchor];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateCandidate(lowerAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *upperController =
      BottomSheetActivateCandidate(upperAnchor, BottomSheetPresentationModePortal);

  [upperController invalidate];
  [upperController invalidate];

  XCTAssertFalse(upperController.isTopPresentation);
  XCTAssertTrue(lowerController.isTopPresentation);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window],
      lowerController.identity);

  [lowerController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testWeakCandidateCleanupDoesNotRetainControllerOrAnchor
{
  UIWindow *window = BottomSheetMakeTestWindow();
  __weak BottomSheetPresentationController *weakController;
  __weak UIView *weakAnchor;

  @autoreleasepool {
    UIView *anchor = [UIView new];
    [window.rootViewController.view addSubview:anchor];
    BottomSheetPresentationController *controller =
        BottomSheetActivateCandidate(anchor, BottomSheetPresentationModePortal);
    weakController = controller;
    weakAnchor = anchor;
    [anchor removeFromSuperview];
  }

  XCTAssertNil(weakController);
  XCTAssertNil(weakAnchor);
  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window]);

  BottomSheetTearDownWindow(window);
}

- (void)testWeakAnchorPurgeClearsTopForLiveController
{
  UIWindow *window = BottomSheetMakeTestWindow();
  BottomSheetPresentationController *controller;
  __weak UIView *weakAnchor;

  @autoreleasepool {
    UIView *anchor = [UIView new];
    [window.rootViewController.view addSubview:anchor];
    controller = BottomSheetActivateCandidate(anchor, BottomSheetPresentationModePortal);
    weakAnchor = anchor;
    [anchor removeFromSuperview];
  }

  XCTAssertNil(weakAnchor);
  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window]);
  XCTAssertFalse(controller.isTopPresentation);

  [controller invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testHierarchyMutationPublishesOnlyFinalNativeOrder
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *firstAnchor = [UIView new];
  UIView *secondAnchor = [UIView new];
  [window.rootViewController.view addSubview:firstAnchor];
  [window.rootViewController.view addSubview:secondAnchor];
  BottomSheetPresentationController *firstController =
      BottomSheetActivateCandidate(firstAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *secondController =
      BottomSheetActivateCandidate(secondAnchor, BottomSheetPresentationModePortal);

  [secondController beginHierarchyMutation];
  [window.rootViewController.view bringSubviewToFront:firstAnchor];
  [secondController reconcilePresentationOrder];

  XCTAssertTrue(secondController.isTopPresentation);
  XCTAssertFalse(firstController.isTopPresentation);

  [secondController endHierarchyMutation];

  XCTAssertFalse(secondController.isTopPresentation);
  XCTAssertTrue(firstController.isTopPresentation);

  [secondController invalidate];
  [firstController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testProductionPortalAndNativeOverlayComponentsTransferTopOwnership
{
  UIWindow *window = BottomSheetMakeTestWindow();
  window.rootViewController.view.frame = window.bounds;
  UIView *portalComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:NO];
  UIView *overlayComponent =
      [BottomSheetTestComponentFactory makeProductionComponentWithNativeOverlay:YES];
  portalComponent.frame = window.bounds;
  overlayComponent.frame = window.bounds;

  [window.rootViewController.view addSubview:portalComponent];
  [window.rootViewController.view addSubview:overlayComponent];
  BottomSheetLayoutViewTree(window);

  BottomSheetPresentationIdentity *overlayTop =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];
  XCTAssertNotNil(overlayTop);

  [BottomSheetTestComponentFactory prepareForRecycle:overlayComponent];

  BottomSheetPresentationIdentity *portalTop =
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window];
  XCTAssertNotNil(portalTop);
  XCTAssertNotEqualObjects(portalTop, overlayTop);

  [BottomSheetTestComponentFactory prepareForRecycle:portalComponent];
  XCTAssertNil([BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window]);
  [overlayComponent removeFromSuperview];
  [portalComponent removeFromSuperview];
  BottomSheetTearDownWindow(window);
}

@end
