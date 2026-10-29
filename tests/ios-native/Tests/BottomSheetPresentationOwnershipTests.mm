#import <UIKit/UIKit.h>
#import <XCTest/XCTest.h>

#import <math.h>

#import "../../../ios/BottomSheetPresentationOwnership.h"
#import "Support/BottomSheetAccessibilityObservation.h"
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

- (void)testForegroundNativeOverlayRoutesEscapeRegardlessOfActionOrigin
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *outside = [UIView new];
  UIView *portalAnchor = [UIView new];
  UIView *overlayAnchor = [UIView new];
  [window.rootViewController.view addSubview:outside];
  [window.rootViewController.view addSubview:portalAnchor];
  [window.rootViewController.view addSubview:overlayAnchor];
  BottomSheetPresentationController *portal =
      BottomSheetActivateCandidate(portalAnchor, BottomSheetPresentationModePortal);
  BottomSheetPresentationController *overlay =
      BottomSheetActivateCandidate(overlayAnchor, BottomSheetPresentationModeNativeOverlay);
  [BottomSheetAccessibilityObservation beginAccessibilityObservationForDismiss:outside
      voiceOverRunning:YES focused:NO];
  @try {
    // The window-modal branch precedes local source/origin mapping, including
    // when the portal's Fabric source is unavailable in this controller test.
    for (id origin in @[outside, portalAnchor, NSNull.null, [NSObject new]]) {
      [BottomSheetAccessibilityObservation setVoiceOverFocusedElement:
          origin == NSNull.null ? nil : origin];
      XCTAssertEqual([portal routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteConsume);
      XCTAssertEqual([overlay routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);
    }
  } @finally {
    [BottomSheetAccessibilityObservation endAccessibilityObservation];
    [overlay invalidate];
    [portal invalidate];
    BottomSheetTearDownWindow(window);
  }
}

- (void)testOneActiveCandidateBecomesTop
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *anchor = [UIView new];
  [window.rootViewController.view addSubview:anchor];
  BottomSheetPresentationController *controller =
      BottomSheetActivateCandidate(anchor, BottomSheetPresentationModeNativeOverlay);

  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:controller], window);
  XCTAssertEqual([controller routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

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
      BottomSheetActivateCandidate(upperAnchor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateCandidate(lowerAnchor, BottomSheetPresentationModeNativeOverlay);

  XCTAssertEqual([upperController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

  [lowerController invalidate];
  [upperController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testUIKitOrderPlacesDescendantAboveAncestor
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *ancestorAnchor = [UIView new];
  UIView *descendantAnchor = [UIView new];
  [window.rootViewController.view addSubview:ancestorAnchor];
  [ancestorAnchor addSubview:descendantAnchor];

  XCTAssertEqual([BottomSheetPresentationUIKitOrderResolver orderOfAnchor:descendantAnchor
      relativeTo:ancestorAnchor inWindow:window], BottomSheetPresentationOrderAbove);
  XCTAssertEqual([BottomSheetPresentationUIKitOrderResolver orderOfAnchor:ancestorAnchor
      relativeTo:descendantAnchor inWindow:window], BottomSheetPresentationOrderBelow);

  BottomSheetTearDownWindow(window);
}

- (void)testUIKitOrderUsesFiniteBranchZPositionBeforeSiblingIndex
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

  XCTAssertEqual([BottomSheetPresentationUIKitOrderResolver orderOfAnchor:higherZAnchor
      relativeTo:laterSiblingAnchor inWindow:window], BottomSheetPresentationOrderAbove);
  XCTAssertEqual([BottomSheetPresentationUIKitOrderResolver orderOfAnchor:laterSiblingAnchor
      relativeTo:higherZAnchor inWindow:window], BottomSheetPresentationOrderBelow);

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
      BottomSheetActivateCandidate(firstAnchor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *secondController =
      BottomSheetActivateCandidate(secondAnchor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationIdentity *firstIdentity = firstController.identity;

  [window.rootViewController.view bringSubviewToFront:firstAnchor];
  [secondController reconcilePresentationOrder];

  XCTAssertEqual([firstController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);
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
      BottomSheetActivateCandidate(firstAnchor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *secondController =
      BottomSheetActivateCandidate(secondAnchor, BottomSheetPresentationModeNativeOverlay);

  firstBranch.layer.zPosition = 3;
  [firstController reconcilePresentationOrder];

  XCTAssertEqual([firstController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

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
      BottomSheetActivateCandidate(secondAnchor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *firstController =
      BottomSheetActivateCandidate(firstAnchor, BottomSheetPresentationModeNativeOverlay);

  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:firstController], window);
  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:secondController], window);
  XCTAssertEqual([firstController routeForVoiceOverEscape], BottomSheetPresentationEscapeRoutePassThrough);

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
      BottomSheetActivateCandidate(lowerAnchor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *upperController =
      [[BottomSheetPresentationController alloc] initWithAnchor:upperAnchor];
  [upperController updateModal:YES active:NO mode:BottomSheetPresentationModeNativeOverlay];

  XCTAssertEqual([lowerController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

  [upperController invalidate];
  [lowerController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testUnmappedPortalAboveOverlayDoesNotTakeItsWindowInput
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

  XCTAssertEqual([portalController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteConsume);
  XCTAssertEqual([overlayController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

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
      BottomSheetActivateCandidate(firstAnchor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *secondController =
      BottomSheetActivateCandidate(secondAnchor, BottomSheetPresentationModeNativeOverlay);

  XCTAssertEqual([firstController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([secondController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

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
      BottomSheetActivateCandidate(lowerAnchor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *upperController =
      BottomSheetActivateCandidate(upperAnchor, BottomSheetPresentationModeNativeOverlay);

  [upperAnchor removeFromSuperview];
  [upperController reconcilePresentationOrder];

  XCTAssertNil([BottomSheetTestComponentFactory registeredWindowForController:upperController]);
  XCTAssertEqual([lowerController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

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
      BottomSheetActivateCandidate(anchor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *secondController =
      BottomSheetActivateCandidate(anchor, BottomSheetPresentationModeNativeOverlay);

  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:firstController], window);
  XCTAssertEqual([BottomSheetTestComponentFactory registeredWindowForController:secondController], window);
  XCTAssertEqual([firstController routeForVoiceOverEscape], BottomSheetPresentationEscapeRoutePassThrough);

  [secondController invalidate];
  [firstController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testRemovingCurrentOverlayTransfersWindowInputToRemainingOverlay
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *lowerAnchor = [UIView new];
  UIView *upperAnchor = [UIView new];
  [window.rootViewController.view addSubview:lowerAnchor];
  [window.rootViewController.view addSubview:upperAnchor];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateCandidate(lowerAnchor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *upperController =
      BottomSheetActivateCandidate(upperAnchor, BottomSheetPresentationModeNativeOverlay);

  [upperController invalidate];
  [upperController invalidate];

  XCTAssertNil([BottomSheetTestComponentFactory registeredWindowForController:upperController]);
  XCTAssertEqual([lowerController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

  [lowerController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testWeakCandidateCleanupDoesNotRetainControllerOrAnchor
{
  UIWindow *window = BottomSheetMakeTestWindow();
  UIView *background = [UIView new];
  [window.rootViewController.view addSubview:background];
  __weak BottomSheetPresentationController *weakController;
  __weak UIView *weakAnchor;

  @autoreleasepool {
    UIView *anchor = [UIView new];
    [window.rootViewController.view addSubview:anchor];
    BottomSheetPresentationController *controller =
        BottomSheetActivateCandidate(anchor, BottomSheetPresentationModeNativeOverlay);
    weakController = controller;
    weakAnchor = anchor;
    XCTAssertTrue(background.accessibilityElementsHidden);
    [anchor removeFromSuperview];
  }

  XCTAssertNil(weakController);
  XCTAssertNil(weakAnchor);
  XCTAssertFalse(background.accessibilityElementsHidden);

  BottomSheetTearDownWindow(window);
}

- (void)testEscapeWithReleasedAnchorPassesThroughAndClearsRegistration
{
  UIWindow *window = BottomSheetMakeTestWindow();
  BottomSheetPresentationController *controller;
  __weak UIView *weakAnchor;

  @autoreleasepool {
    UIView *anchor = [UIView new];
    [window.rootViewController.view addSubview:anchor];
    controller = BottomSheetActivateCandidate(anchor, BottomSheetPresentationModeNativeOverlay);
    weakAnchor = anchor;
    [anchor removeFromSuperview];
  }

  XCTAssertNil(weakAnchor);
  // Escape is the actual input under test. Releasing a foreign UIView alone
  // does not promise a lifecycle update; the action must reject the stale anchor.
  XCTAssertEqual([controller routeForVoiceOverEscape], BottomSheetPresentationEscapeRoutePassThrough);
  XCTAssertNil([BottomSheetTestComponentFactory registeredWindowForController:controller]);

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
      BottomSheetActivateCandidate(firstAnchor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *secondController =
      BottomSheetActivateCandidate(secondAnchor, BottomSheetPresentationModeNativeOverlay);

  [secondController beginHierarchyMutation];
  [window.rootViewController.view bringSubviewToFront:firstAnchor];
  [secondController reconcilePresentationOrder];

  XCTAssertTrue(secondAnchor.accessibilityViewIsModal);
  XCTAssertFalse(firstAnchor.accessibilityViewIsModal);
  XCTAssertEqual([firstController routeForVoiceOverEscape], BottomSheetPresentationEscapeRoutePassThrough);

  [secondController endHierarchyMutation];

  XCTAssertEqual([firstController routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

  [secondController invalidate];
  [firstController invalidate];
  BottomSheetTearDownWindow(window);
}

- (void)testRemovingProductionOverlayDoesNotInventWindowInputForUnmappedPortal
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
  BottomSheetPresentationController *portal =
      [BottomSheetTestComponentFactory presentationControllerForComponent:portalComponent];
  BottomSheetPresentationController *overlay =
      [BottomSheetTestComponentFactory presentationControllerForComponent:overlayComponent];
  XCTAssertEqual([overlay routeForVoiceOverEscape], BottomSheetPresentationEscapeRouteAttemptLocal);

  [BottomSheetTestComponentFactory prepareForRecycle:overlayComponent];

  XCTAssertNil([BottomSheetTestComponentFactory registeredWindowForController:overlay]);
  XCTAssertEqual([portal routeForVoiceOverEscape], BottomSheetPresentationEscapeRoutePassThrough);

  [BottomSheetTestComponentFactory prepareForRecycle:portalComponent];

  XCTAssertNil([BottomSheetTestComponentFactory registeredWindowForController:portal]);
  [overlayComponent removeFromSuperview];
  [portalComponent removeFromSuperview];
  BottomSheetTearDownWindow(window);
}

@end
