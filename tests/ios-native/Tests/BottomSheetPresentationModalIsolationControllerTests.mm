#import <UIKit/UIKit.h>
#import <XCTest/XCTest.h>

#import "../../../ios/BottomSheetPresentationOwnership.h"

@interface BottomSheetModalBoundaryRecordingView : UIView

- (instancetype)initWithName:(NSString *)name
                     writeLog:(NSMutableArray<NSString *> *)writeLog;

@property (nonatomic, copy) NSString *name;
@property (nonatomic, strong) NSMutableArray<NSString *> *writeLog;
@property (nonatomic, copy, nullable) void (^afterWrite)(BOOL modal);

@end

@implementation BottomSheetModalBoundaryRecordingView

- (instancetype)initWithName:(NSString *)name
                     writeLog:(NSMutableArray<NSString *> *)writeLog
{
  if (self = [super initWithFrame:CGRectZero]) {
    _name = [name copy];
    _writeLog = writeLog;
  }
  return self;
}

- (void)setAccessibilityViewIsModal:(BOOL)accessibilityViewIsModal
{
  [self.writeLog addObject:[NSString stringWithFormat:
                                       @"%@:%@",
                                       self.name,
                                       accessibilityViewIsModal ? @"true" : @"false"]];
  [super setAccessibilityViewIsModal:accessibilityViewIsModal];
  if (self.afterWrite != nil) {
    self.afterWrite(accessibilityViewIsModal);
  }
}

@end

@interface BottomSheetAccessibilityHiddenRecordingView : UIView

- (instancetype)initWithName:(NSString *)name
                     writeLog:(NSMutableArray<NSString *> *)writeLog;

@property (nonatomic, copy) NSString *name;
@property (nonatomic, strong) NSMutableArray<NSString *> *writeLog;
@property (nonatomic, copy, nullable) void (^afterWrite)(BOOL hidden);

@end

@implementation BottomSheetAccessibilityHiddenRecordingView

- (instancetype)initWithName:(NSString *)name
                     writeLog:(NSMutableArray<NSString *> *)writeLog
{
  if (self = [super initWithFrame:CGRectZero]) {
    _name = [name copy];
    _writeLog = writeLog;
  }
  return self;
}

- (void)setAccessibilityElementsHidden:(BOOL)accessibilityElementsHidden
{
  [self.writeLog addObject:[NSString stringWithFormat:
                                       @"%@:%@",
                                       self.name,
                                       accessibilityElementsHidden ? @"true" : @"false"]];
  [super setAccessibilityElementsHidden:accessibilityElementsHidden];
  if (self.afterWrite != nil) {
    self.afterWrite(accessibilityElementsHidden);
  }
}

@end

static UIWindow *BottomSheetMakeModalIsolationTestWindow(void)
{
  UIWindow *window = [[UIWindow alloc] initWithFrame:CGRectMake(0, 0, 390, 844)];
  window.rootViewController = [UIViewController new];
  window.hidden = NO;
  return window;
}

static BottomSheetPresentationController *BottomSheetActivateModalBoundary(
    UIView *boundary,
    BottomSheetPresentationMode mode)
{
  BottomSheetPresentationController *controller =
      [[BottomSheetPresentationController alloc] initWithAnchor:boundary];
  [controller updateModal:YES active:YES mode:mode];
  return controller;
}

static void BottomSheetTearDownModalIsolationTestWindow(UIWindow *window)
{
  window.hidden = YES;
  window.rootViewController = nil;
}

@interface BottomSheetPresentationModalIsolationControllerTests : XCTestCase
@end

@implementation BottomSheetPresentationModalIsolationControllerTests

- (void)testTopPathHidesSiblingBranchesAndRestoresThemAfterOwnershipEnds
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  NSMutableArray<NSString *> *hiddenWriteLog = [NSMutableArray new];
  UIWindow *window = BottomSheetMakeModalIsolationTestWindow();
  UIView *applicationBranch = [UIView new];
  UIView *portalHostBranch = [UIView new];
  BottomSheetAccessibilityHiddenRecordingView *lowerPortalBranch =
      [[BottomSheetAccessibilityHiddenRecordingView alloc] initWithName:@"lowerPortal"
                                                                writeLog:hiddenWriteLog];
  BottomSheetAccessibilityHiddenRecordingView *upperPortalBranch =
      [[BottomSheetAccessibilityHiddenRecordingView alloc] initWithName:@"upperPortal"
                                                                writeLog:hiddenWriteLog];
  BottomSheetModalBoundaryRecordingView *lower =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"lower" writeLog:writeLog];
  BottomSheetModalBoundaryRecordingView *upper =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"upper" writeLog:writeLog];
  [window.rootViewController.view addSubview:applicationBranch];
  [window.rootViewController.view addSubview:portalHostBranch];
  [portalHostBranch addSubview:lowerPortalBranch];
  [portalHostBranch addSubview:upperPortalBranch];
  [lowerPortalBranch addSubview:lower];
  [upperPortalBranch addSubview:upper];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateModalBoundary(lower, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *upperController =
      BottomSheetActivateModalBoundary(upper, BottomSheetPresentationModeNativeOverlay);

  XCTAssertTrue(applicationBranch.accessibilityElementsHidden);
  XCTAssertTrue(lowerPortalBranch.accessibilityElementsHidden);
  XCTAssertFalse(upperPortalBranch.accessibilityElementsHidden);
  XCTAssertFalse(upper.accessibilityElementsHidden);
  [hiddenWriteLog removeAllObjects];

  [upperController invalidate];

  XCTAssertEqualObjects(hiddenWriteLog, (@[ @"upperPortal:true", @"lowerPortal:false" ]));
  XCTAssertTrue(applicationBranch.accessibilityElementsHidden);
  XCTAssertFalse(lowerPortalBranch.accessibilityElementsHidden);
  XCTAssertTrue(upperPortalBranch.accessibilityElementsHidden);

  [lowerController invalidate];

  XCTAssertFalse(applicationBranch.accessibilityElementsHidden);
  XCTAssertFalse(lowerPortalBranch.accessibilityElementsHidden);
  XCTAssertFalse(upperPortalBranch.accessibilityElementsHidden);
  BottomSheetTearDownModalIsolationTestWindow(window);
}

- (void)testSameWindowReparentTransfersWindowModalSideBranchIsolation
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *window = BottomSheetMakeModalIsolationTestWindow();
  UIView *applicationBranch = [UIView new];
  BottomSheetAccessibilityHiddenRecordingView *portalBranch =
      [[BottomSheetAccessibilityHiddenRecordingView alloc] initWithName:@"portal"
                                                                writeLog:writeLog];
  BottomSheetAccessibilityHiddenRecordingView *overlayBranch =
      [[BottomSheetAccessibilityHiddenRecordingView alloc] initWithName:@"overlay"
                                                                writeLog:writeLog];
  UIView *boundary = [UIView new];
  [window.rootViewController.view addSubview:applicationBranch];
  [window.rootViewController.view addSubview:portalBranch];
  [window.rootViewController.view addSubview:overlayBranch];
  [portalBranch addSubview:boundary];
  BottomSheetPresentationController *controller =
      BottomSheetActivateModalBoundary(boundary, BottomSheetPresentationModeNativeOverlay);

  XCTAssertTrue(applicationBranch.accessibilityElementsHidden);
  XCTAssertFalse(portalBranch.accessibilityElementsHidden);
  XCTAssertTrue(overlayBranch.accessibilityElementsHidden);
  [writeLog removeAllObjects];

  [controller beginHierarchyMutation];
  [overlayBranch addSubview:boundary];
  [controller updateModal:YES active:YES mode:BottomSheetPresentationModeNativeOverlay];
  [controller endHierarchyMutation];

  XCTAssertEqualObjects(writeLog, (@[ @"portal:true", @"overlay:false" ]));
  XCTAssertTrue(applicationBranch.accessibilityElementsHidden);
  XCTAssertTrue(portalBranch.accessibilityElementsHidden);
  XCTAssertFalse(overlayBranch.accessibilityElementsHidden);
  [writeLog removeAllObjects];

  [controller beginHierarchyMutation];
  [portalBranch addSubview:boundary];
  [controller updateModal:YES active:YES mode:BottomSheetPresentationModeNativeOverlay];
  [controller endHierarchyMutation];

  XCTAssertEqualObjects(writeLog, (@[ @"overlay:true", @"portal:false" ]));
  XCTAssertTrue(applicationBranch.accessibilityElementsHidden);
  XCTAssertFalse(portalBranch.accessibilityElementsHidden);
  XCTAssertTrue(overlayBranch.accessibilityElementsHidden);

  [controller invalidate];

  XCTAssertFalse(applicationBranch.accessibilityElementsHidden);
  XCTAssertFalse(portalBranch.accessibilityElementsHidden);
  XCTAssertFalse(overlayBranch.accessibilityElementsHidden);
  BottomSheetTearDownModalIsolationTestWindow(window);
}

- (void)testWindowMigrationRestoresSourceLedgerBeforeApplyingDestinationLedger
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *firstWindow = BottomSheetMakeModalIsolationTestWindow();
  UIWindow *secondWindow = BottomSheetMakeModalIsolationTestWindow();
  BottomSheetAccessibilityHiddenRecordingView *firstApplicationBranch =
      [[BottomSheetAccessibilityHiddenRecordingView alloc] initWithName:@"firstApplication"
                                                                writeLog:writeLog];
  UIView *firstPresentationBranch = [UIView new];
  BottomSheetAccessibilityHiddenRecordingView *secondApplicationBranch =
      [[BottomSheetAccessibilityHiddenRecordingView alloc] initWithName:@"secondApplication"
                                                                writeLog:writeLog];
  UIView *secondPresentationBranch = [UIView new];
  UIView *boundary = [UIView new];
  [firstWindow.rootViewController.view addSubview:firstApplicationBranch];
  [firstWindow.rootViewController.view addSubview:firstPresentationBranch];
  [secondWindow.rootViewController.view addSubview:secondApplicationBranch];
  [secondWindow.rootViewController.view addSubview:secondPresentationBranch];
  [firstPresentationBranch addSubview:boundary];
  BottomSheetPresentationController *controller =
      BottomSheetActivateModalBoundary(boundary, BottomSheetPresentationModeNativeOverlay);

  XCTAssertTrue(firstApplicationBranch.accessibilityElementsHidden);
  XCTAssertFalse(secondApplicationBranch.accessibilityElementsHidden);
  [writeLog removeAllObjects];

  [controller beginHierarchyMutation];
  [secondPresentationBranch addSubview:boundary];
  [controller endHierarchyMutation];

  XCTAssertEqualObjects(
      writeLog,
      (@[ @"firstApplication:false", @"secondApplication:true" ]));
  XCTAssertFalse(firstApplicationBranch.accessibilityElementsHidden);
  XCTAssertTrue(secondApplicationBranch.accessibilityElementsHidden);
  [writeLog removeAllObjects];

  [controller beginHierarchyMutation];
  [firstPresentationBranch addSubview:boundary];
  [controller endHierarchyMutation];

  XCTAssertEqualObjects(
      writeLog,
      (@[ @"secondApplication:false", @"firstApplication:true" ]));
  XCTAssertTrue(firstApplicationBranch.accessibilityElementsHidden);
  XCTAssertFalse(secondApplicationBranch.accessibilityElementsHidden);

  [controller invalidate];

  XCTAssertFalse(firstApplicationBranch.accessibilityElementsHidden);
  XCTAssertFalse(secondApplicationBranch.accessibilityElementsHidden);
  BottomSheetTearDownModalIsolationTestWindow(secondWindow);
  BottomSheetTearDownModalIsolationTestWindow(firstWindow);
}

- (void)testReentrantHiddenBranchWriteRerunsReconciliationBeforeReturning
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *window = BottomSheetMakeModalIsolationTestWindow();
  BottomSheetAccessibilityHiddenRecordingView *lowerBranch =
      [[BottomSheetAccessibilityHiddenRecordingView alloc] initWithName:@"lowerBranch"
                                                                writeLog:writeLog];
  UIView *upperBranch = [UIView new];
  UIView *lower = [UIView new];
  UIView *upper = [UIView new];
  [window.rootViewController.view addSubview:lowerBranch];
  [window.rootViewController.view addSubview:upperBranch];
  [lowerBranch addSubview:lower];
  [upperBranch addSubview:upper];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateModalBoundary(lower, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *upperController =
      [[BottomSheetPresentationController alloc] initWithAnchor:upper];
  __block BOOL didRemoveNewTop = NO;
  lowerBranch.afterWrite = ^(BOOL hidden) {
    if (hidden && !didRemoveNewTop) {
      didRemoveNewTop = YES;
      [upperController invalidate];
    }
  };

  [upperController updateModal:YES active:YES mode:BottomSheetPresentationModeNativeOverlay];

  XCTAssertTrue(didRemoveNewTop);
  XCTAssertTrue(lowerController.isTopPresentation);
  XCTAssertFalse(upperController.isTopPresentation);
  XCTAssertFalse(lowerBranch.accessibilityElementsHidden);
  XCTAssertTrue(upperBranch.accessibilityElementsHidden);
  XCTAssertTrue(lower.accessibilityViewIsModal);
  XCTAssertFalse(upper.accessibilityViewIsModal);

  lowerBranch.afterWrite = nil;
  [lowerController invalidate];
  XCTAssertFalse(upperBranch.accessibilityElementsHidden);
  BottomSheetTearDownModalIsolationTestWindow(window);
}

- (void)testTopTransferEnablesNewBoundaryBeforeDisablingOldBoundary
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *window = BottomSheetMakeModalIsolationTestWindow();
  BottomSheetModalBoundaryRecordingView *lower =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"lower" writeLog:writeLog];
  BottomSheetModalBoundaryRecordingView *upper =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"upper" writeLog:writeLog];
  [window.rootViewController.view addSubview:lower];
  [window.rootViewController.view addSubview:upper];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateModalBoundary(lower, BottomSheetPresentationModeNativeOverlay);
  [writeLog removeAllObjects];

  BottomSheetPresentationController *upperController =
      BottomSheetActivateModalBoundary(upper, BottomSheetPresentationModeNativeOverlay);

  XCTAssertEqualObjects(writeLog, (@[ @"upper:true", @"lower:false" ]));
  XCTAssertFalse(lower.accessibilityViewIsModal);
  XCTAssertTrue(upper.accessibilityViewIsModal);

  [upperController invalidate];
  [lowerController invalidate];
  BottomSheetTearDownModalIsolationTestWindow(window);
}

- (void)testNestedDescendantIsTheOnlyModalBoundary
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *window = BottomSheetMakeModalIsolationTestWindow();
  BottomSheetModalBoundaryRecordingView *ancestor =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"ancestor" writeLog:writeLog];
  BottomSheetModalBoundaryRecordingView *descendant =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"descendant" writeLog:writeLog];
  [window.rootViewController.view addSubview:ancestor];
  [ancestor addSubview:descendant];
  BottomSheetPresentationController *ancestorController =
      BottomSheetActivateModalBoundary(ancestor, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *descendantController =
      BottomSheetActivateModalBoundary(descendant, BottomSheetPresentationModeNativeOverlay);

  XCTAssertFalse(ancestor.accessibilityViewIsModal);
  XCTAssertTrue(descendant.accessibilityViewIsModal);

  [descendantController invalidate];
  [ancestorController invalidate];
  BottomSheetTearDownModalIsolationTestWindow(window);
}

- (void)testNoTopClearsEveryRegisteredBoundary
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *window = BottomSheetMakeModalIsolationTestWindow();
  BottomSheetModalBoundaryRecordingView *boundary =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"boundary" writeLog:writeLog];
  [window.rootViewController.view addSubview:boundary];
  BottomSheetPresentationController *controller =
      BottomSheetActivateModalBoundary(boundary, BottomSheetPresentationModeNativeOverlay);

  [controller updateModal:YES active:NO mode:BottomSheetPresentationModeNativeOverlay];

  XCTAssertFalse(boundary.accessibilityViewIsModal);

  [controller invalidate];
  BottomSheetTearDownModalIsolationTestWindow(window);
}

- (void)testDifferentWindowsReconcileModalIsolationIndependently
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *firstWindow = BottomSheetMakeModalIsolationTestWindow();
  UIWindow *secondWindow = BottomSheetMakeModalIsolationTestWindow();
  UIView *firstApplicationBranch = [UIView new];
  UIView *firstPresentationBranch = [UIView new];
  UIView *secondApplicationBranch = [UIView new];
  UIView *secondPresentationBranch = [UIView new];
  BottomSheetModalBoundaryRecordingView *firstBoundary =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"first" writeLog:writeLog];
  BottomSheetModalBoundaryRecordingView *secondBoundary =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"second" writeLog:writeLog];
  [firstWindow.rootViewController.view addSubview:firstApplicationBranch];
  [firstWindow.rootViewController.view addSubview:firstPresentationBranch];
  [secondWindow.rootViewController.view addSubview:secondApplicationBranch];
  [secondWindow.rootViewController.view addSubview:secondPresentationBranch];
  [firstPresentationBranch addSubview:firstBoundary];
  [secondPresentationBranch addSubview:secondBoundary];
  BottomSheetPresentationController *firstController =
      BottomSheetActivateModalBoundary(firstBoundary, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *secondController =
      BottomSheetActivateModalBoundary(secondBoundary, BottomSheetPresentationModeNativeOverlay);

  XCTAssertTrue(firstApplicationBranch.accessibilityElementsHidden);
  XCTAssertTrue(secondApplicationBranch.accessibilityElementsHidden);

  [firstController updateModal:YES active:NO mode:BottomSheetPresentationModeNativeOverlay];

  XCTAssertFalse(firstBoundary.accessibilityViewIsModal);
  XCTAssertTrue(secondBoundary.accessibilityViewIsModal);
  XCTAssertFalse(firstApplicationBranch.accessibilityElementsHidden);
  XCTAssertTrue(secondApplicationBranch.accessibilityElementsHidden);

  [secondController invalidate];

  XCTAssertFalse(firstApplicationBranch.accessibilityElementsHidden);
  XCTAssertFalse(secondApplicationBranch.accessibilityElementsHidden);

  [firstController invalidate];
  BottomSheetTearDownModalIsolationTestWindow(secondWindow);
  BottomSheetTearDownModalIsolationTestWindow(firstWindow);
}

- (void)testRemovingLowerBoundaryDoesNotReleaseCurrentTopBoundary
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *window = BottomSheetMakeModalIsolationTestWindow();
  BottomSheetModalBoundaryRecordingView *lower =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"lower" writeLog:writeLog];
  BottomSheetModalBoundaryRecordingView *upper =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"upper" writeLog:writeLog];
  [window.rootViewController.view addSubview:lower];
  [window.rootViewController.view addSubview:upper];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateModalBoundary(lower, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *upperController =
      BottomSheetActivateModalBoundary(upper, BottomSheetPresentationModeNativeOverlay);
  [writeLog removeAllObjects];

  [lowerController invalidate];

  XCTAssertEqualObjects(writeLog, (@[]));
  XCTAssertFalse(lower.accessibilityViewIsModal);
  XCTAssertTrue(upper.accessibilityViewIsModal);

  [upperController invalidate];
  BottomSheetTearDownModalIsolationTestWindow(window);
}

- (void)testReentrantBoundaryWriteRerunsReconciliationBeforeReturning
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *window = BottomSheetMakeModalIsolationTestWindow();
  BottomSheetModalBoundaryRecordingView *lower =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"lower" writeLog:writeLog];
  BottomSheetModalBoundaryRecordingView *upper =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"upper" writeLog:writeLog];
  [window.rootViewController.view addSubview:lower];
  [window.rootViewController.view addSubview:upper];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateModalBoundary(lower, BottomSheetPresentationModeNativeOverlay);
  [writeLog removeAllObjects];
  __block BOOL didReenter = NO;
  upper.afterWrite = ^(BOOL modal) {
    if (modal && !didReenter) {
      didReenter = YES;
      [lowerController reconcilePresentationOrder];
    }
  };

  BottomSheetPresentationController *upperController =
      BottomSheetActivateModalBoundary(upper, BottomSheetPresentationModeNativeOverlay);

  XCTAssertTrue(didReenter);
  XCTAssertEqualObjects(writeLog, (@[ @"upper:true", @"lower:false" ]));
  XCTAssertFalse(lower.accessibilityViewIsModal);
  XCTAssertTrue(upper.accessibilityViewIsModal);

  upper.afterWrite = nil;
  [upperController invalidate];
  [lowerController invalidate];
  BottomSheetTearDownModalIsolationTestWindow(window);
}

- (void)testReentrantNewTopRemovalRestoresSuccessorBeforeClearingReleasedBoundary
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *window = BottomSheetMakeModalIsolationTestWindow();
  BottomSheetModalBoundaryRecordingView *lower =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"lower" writeLog:writeLog];
  BottomSheetModalBoundaryRecordingView *upper =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"upper" writeLog:writeLog];
  [window.rootViewController.view addSubview:lower];
  [window.rootViewController.view addSubview:upper];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateModalBoundary(lower, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *upperController =
      [[BottomSheetPresentationController alloc] initWithAnchor:upper];
  __block BOOL didRemoveNewTop = NO;
  upper.afterWrite = ^(BOOL modal) {
    if (modal && !didRemoveNewTop) {
      didRemoveNewTop = YES;
      [upperController invalidate];
    }
  };
  [writeLog removeAllObjects];

  [upperController updateModal:YES active:YES mode:BottomSheetPresentationModeNativeOverlay];

  XCTAssertTrue(didRemoveNewTop);
  XCTAssertEqualObjects(
      writeLog,
      (@[ @"upper:true", @"lower:false", @"lower:true", @"upper:false" ]));
  XCTAssertTrue(lower.accessibilityViewIsModal);
  XCTAssertFalse(upper.accessibilityViewIsModal);

  upper.afterWrite = nil;
  [lowerController invalidate];
  BottomSheetTearDownModalIsolationTestWindow(window);
}

- (void)testReentrantCurrentTopCleanupIsIdempotentDuringTransfer
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *window = BottomSheetMakeModalIsolationTestWindow();
  BottomSheetModalBoundaryRecordingView *lower =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"lower" writeLog:writeLog];
  BottomSheetModalBoundaryRecordingView *upper =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"upper" writeLog:writeLog];
  [window.rootViewController.view addSubview:lower];
  [window.rootViewController.view addSubview:upper];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateModalBoundary(lower, BottomSheetPresentationModeNativeOverlay);
  __block BOOL didCleanCurrentTop = NO;
  lower.afterWrite = ^(BOOL modal) {
    if (!modal && !didCleanCurrentTop) {
      didCleanCurrentTop = YES;
      [lowerController invalidate];
      [lowerController invalidate];
    }
  };
  [writeLog removeAllObjects];

  BottomSheetPresentationController *upperController =
      BottomSheetActivateModalBoundary(upper, BottomSheetPresentationModeNativeOverlay);

  XCTAssertTrue(didCleanCurrentTop);
  XCTAssertEqualObjects(writeLog, (@[ @"upper:true", @"lower:false" ]));
  XCTAssertFalse(lower.accessibilityViewIsModal);
  XCTAssertTrue(upper.accessibilityViewIsModal);
  XCTAssertEqualObjects(
      [BottomSheetPresentationCoordinator topPresentationIdentityInWindow:window],
      upperController.identity);

  lower.afterWrite = nil;
  [upperController invalidate];
  BottomSheetTearDownModalIsolationTestWindow(window);
}

- (void)testIdempotentReconciliationRepairsOnlyDriftedPrivateBoundary
{
  NSMutableArray<NSString *> *writeLog = [NSMutableArray new];
  UIWindow *window = BottomSheetMakeModalIsolationTestWindow();
  BottomSheetModalBoundaryRecordingView *lower =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"lower" writeLog:writeLog];
  BottomSheetModalBoundaryRecordingView *upper =
      [[BottomSheetModalBoundaryRecordingView alloc] initWithName:@"upper" writeLog:writeLog];
  [window.rootViewController.view addSubview:lower];
  [window.rootViewController.view addSubview:upper];
  BottomSheetPresentationController *lowerController =
      BottomSheetActivateModalBoundary(lower, BottomSheetPresentationModeNativeOverlay);
  BottomSheetPresentationController *upperController =
      BottomSheetActivateModalBoundary(upper, BottomSheetPresentationModeNativeOverlay);
  lower.accessibilityViewIsModal = YES;
  [writeLog removeAllObjects];

  [upperController reconcilePresentationOrder];

  XCTAssertEqualObjects(writeLog, (@[ @"lower:false" ]));
  XCTAssertFalse(lower.accessibilityViewIsModal);
  XCTAssertTrue(upper.accessibilityViewIsModal);

  [upperController invalidate];
  [lowerController invalidate];
  BottomSheetTearDownModalIsolationTestWindow(window);
}

@end
