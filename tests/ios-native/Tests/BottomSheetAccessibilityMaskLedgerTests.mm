#import <UIKit/UIKit.h>
#import <XCTest/XCTest.h>

#import "../../../ios/BottomSheetAccessibilityMaskLedger.h"

@interface BottomSheetMaskRecordingView : UIView
@property (nonatomic) NSUInteger hiddenWrites;
@property (nonatomic, copy, nullable) void (^afterWrite)(BOOL hidden);
@end

@implementation BottomSheetMaskRecordingView

- (void)setAccessibilityElementsHidden:(BOOL)hidden
{
  self.hiddenWrites++;
  [super setAccessibilityElementsHidden:hidden];
  if (self.afterWrite != nil) {
    self.afterWrite(hidden);
  }
}

@end

@interface BottomSheetAccessibilityMaskLedgerTests : XCTestCase
@end

@implementation BottomSheetAccessibilityMaskLedgerTests

- (void)testWindowTransfersKeepSettledPortalLeasesUntilTheirOwnRelease
{
  auto ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *local = [UIView new];
  UIView *shared = [UIView new];
  UIView *oldWindow = [UIView new];
  UIView *newWindow = [UIView new];
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObjects:local, shared, nil]
      windowHiddenBranches:[NSSet setWithObjects:oldWindow, shared, nil]
      beforeRestoringBranches:nil];

  [ledger reconcilePortalHiddenBranches:nil
      windowHiddenBranches:[NSSet setWithObjects:newWindow, shared, nil]
      beforeRestoringBranches:^{
        XCTAssertTrue(local.accessibilityElementsHidden);
        XCTAssertTrue(shared.accessibilityElementsHidden);
        XCTAssertTrue(oldWindow.accessibilityElementsHidden);
        XCTAssertTrue(newWindow.accessibilityElementsHidden);
      }];
  XCTAssertFalse(oldWindow.accessibilityElementsHidden);
  [ledger reconcilePortalHiddenBranches:[NSSet set]
      windowHiddenBranches:[NSSet setWithObjects:newWindow, shared, nil]
      beforeRestoringBranches:nil];
  XCTAssertFalse(local.accessibilityElementsHidden);
  XCTAssertTrue(shared.accessibilityElementsHidden, @"Window ownership still requires the shared mask");
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObject:shared]
      windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertTrue(shared.accessibilityElementsHidden, @"Portal ownership survives window release");
  XCTAssertFalse(newWindow.accessibilityElementsHidden);
  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertFalse(shared.accessibilityElementsHidden);
}

- (void)testDeletedAncestorsTransferBothLeaseRequirementsToTheirSurvivingFrontier
{
  auto ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *localAncestor = [UIView new];
  UIView *windowAncestor = [UIView new];
  UIView *survivor = [UIView new];
  [localAncestor addSubview:windowAncestor];
  [windowAncestor addSubview:survivor];
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObject:localAncestor]
      windowHiddenBranches:[NSSet setWithObject:windowAncestor] beforeRestoringBranches:nil];
  auto deletion = [[BottomSheetNativeViewDeletion alloc]
      initWithDeletedViews:[NSSet setWithObjects:localAncestor, windowAncestor, nil]
      survivingRegisteredViews:[NSSet setWithObject:survivor]];
  [ledger retireHiddenBranchesForDeletion:deletion];
  XCTAssertFalse(localAncestor.accessibilityElementsHidden);
  XCTAssertFalse(windowAncestor.accessibilityElementsHidden);
  XCTAssertTrue(survivor.accessibilityElementsHidden);
  [survivor removeFromSuperview];

  [ledger reconcilePortalHiddenBranches:nil windowHiddenBranches:[NSSet set]
      beforeRestoringBranches:nil];
  XCTAssertTrue(survivor.accessibilityElementsHidden, @"Inherited portal ownership must survive window release");
  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set]
      beforeRestoringBranches:nil];
  XCTAssertFalse(survivor.accessibilityElementsHidden);
  // Reuse must not resurrect either inherited reason or its old baseline.
  survivor.accessibilityElementsHidden = YES;
  [ledger reconcilePortalHiddenBranches:nil windowHiddenBranches:[NSSet set]
      beforeRestoringBranches:nil];
  XCTAssertTrue(survivor.accessibilityElementsHidden);
}

- (void)testMasksTheSuppliedUnionAndLeavesOtherBranchesUntouched
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *first = [UIView new];
  UIView *second = [UIView new];
  UIView *outside = [UIView new];

  [ledger reconcilePortalHiddenBranches:[NSSet setWithObjects:first, second, nil]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:^{
           XCTAssertTrue(first.accessibilityElementsHidden);
           XCTAssertTrue(second.accessibilityElementsHidden);
           XCTAssertFalse(outside.accessibilityElementsHidden);
         }];

  XCTAssertTrue(first.accessibilityElementsHidden);
  XCTAssertTrue(second.accessibilityElementsHidden);
  XCTAssertFalse(outside.accessibilityElementsHidden);
}

- (void)testReleasingOneBranchPreservesTheRemainingMask
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *first = [UIView new];
  UIView *second = [UIView new];
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObjects:first, second, nil]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];

  [ledger reconcilePortalHiddenBranches:[NSSet setWithObject:second]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:^{
           XCTAssertTrue(first.accessibilityElementsHidden);
           XCTAssertTrue(second.accessibilityElementsHidden);
         }];

  XCTAssertFalse(first.accessibilityElementsHidden);
  XCTAssertTrue(second.accessibilityElementsHidden);
  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertFalse(second.accessibilityElementsHidden);
}

- (void)testCleanupDoesNotRewriteAPreviouslyHiddenApplicationBranch
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  BottomSheetMaskRecordingView *branch = [BottomSheetMaskRecordingView new];
  branch.accessibilityElementsHidden = YES;
  branch.hiddenWrites = 0;
  NSSet<UIView *> *hidden = [NSSet setWithObject:branch];

  [ledger reconcilePortalHiddenBranches:hidden windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  [ledger reconcilePortalHiddenBranches:hidden windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];

  XCTAssertTrue(branch.accessibilityElementsHidden);
  XCTAssertEqual(branch.hiddenWrites, 0u);
}

- (void)testCleanupPreservesALaterVisibleWrite
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  BottomSheetMaskRecordingView *branch = [BottomSheetMaskRecordingView new];
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObject:branch] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  branch.accessibilityElementsHidden = NO;
  branch.hiddenWrites = 0;

  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];

  XCTAssertFalse(branch.accessibilityElementsHidden);
  XCTAssertEqual(branch.hiddenWrites, 0u);
}

- (void)testRemaskingPreservesTheOriginalHiddenBaseline
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *branch = [UIView new];
  branch.accessibilityElementsHidden = YES;
  NSSet<UIView *> *hidden = [NSSet setWithObject:branch];
  [ledger reconcilePortalHiddenBranches:hidden windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  branch.accessibilityElementsHidden = NO;

  [ledger reconcilePortalHiddenBranches:hidden windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertTrue(branch.accessibilityElementsHidden);
  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];

  // iOS preserves the initial baseline, not the latest value before remasking.
  XCTAssertTrue(branch.accessibilityElementsHidden);
}

- (void)testLedgerDoesNotRetainARemovedBranch
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  __weak UIView *removed = nil;
  @autoreleasepool {
    UIView *branch = [UIView new];
    removed = branch;
    [ledger reconcilePortalHiddenBranches:[NSSet setWithObject:branch] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  }

  XCTAssertNil(removed);
  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
}

- (void)testNewAndSharedMasksPrecedeBoundaryTransferAndOldMaskRestoration
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  BottomSheetMaskRecordingView *oldBranch = [BottomSheetMaskRecordingView new];
  UIView *newBranch = [UIView new];
  UIView *shared = [UIView new];
  UIView *oldBoundary = [UIView new];
  UIView *newBoundary = [UIView new];
  oldBoundary.accessibilityViewIsModal = YES;
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObjects:oldBranch, shared, nil]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  __block BOOL observedRestoration = NO;
  oldBranch.afterWrite = ^(BOOL hidden) {
    if (!hidden) {
      observedRestoration = YES;
      XCTAssertTrue(newBranch.accessibilityElementsHidden);
      XCTAssertTrue(shared.accessibilityElementsHidden);
      XCTAssertTrue(newBoundary.accessibilityViewIsModal);
      XCTAssertFalse(oldBoundary.accessibilityViewIsModal);
    }
  };

  [ledger reconcilePortalHiddenBranches:[NSSet setWithObjects:newBranch, shared, nil]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:^{
           XCTAssertTrue(oldBranch.accessibilityElementsHidden);
           XCTAssertTrue(newBranch.accessibilityElementsHidden);
           XCTAssertTrue(shared.accessibilityElementsHidden);
           newBoundary.accessibilityViewIsModal = YES;
           oldBoundary.accessibilityViewIsModal = NO;
         }];

  XCTAssertTrue(observedRestoration);
  XCTAssertFalse(oldBranch.accessibilityElementsHidden);
}

- (void)testRetirementRestoresDeletedNativeBranchesWithoutReleasingOtherMasksOrReusedViews
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *deletedSubtree = [UIView new];
  UIView *nativeChild = [UIView new];
  UIView *remaining = [UIView new];
  UIView *preHiddenChild = [UIView new];
  preHiddenChild.accessibilityElementsHidden = YES;
  [deletedSubtree addSubview:nativeChild];
  [deletedSubtree addSubview:preHiddenChild];
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObjects:nativeChild, remaining, preHiddenChild, nil]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];

  auto deletion = [[BottomSheetNativeViewDeletion alloc]
      initWithDeletedViews:[NSSet setWithObject:deletedSubtree]
      survivingRegisteredViews:[NSSet set]];
  [ledger retireHiddenBranchesForDeletion:deletion];

  XCTAssertFalse(nativeChild.accessibilityElementsHidden);
  XCTAssertTrue(preHiddenChild.accessibilityElementsHidden);
  XCTAssertTrue(remaining.accessibilityElementsHidden);
  // The registry can now reuse the view. Its new application-owned value must
  // not be restored with the retired family's baseline during final cleanup.
  [nativeChild removeFromSuperview];
  nativeChild.accessibilityElementsHidden = YES;
  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertTrue(nativeChild.accessibilityElementsHidden);
  XCTAssertFalse(remaining.accessibilityElementsHidden);
}

- (void)testRetirementDoesNotRemaskAnUnrelatedApplicationWrite
{
  auto ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *deleted = [UIView new];
  UIView *unrelated = [UIView new];
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObjects:deleted, unrelated, nil]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  unrelated.accessibilityElementsHidden = NO;
  auto deletion = [[BottomSheetNativeViewDeletion alloc]
      initWithDeletedViews:[NSSet setWithObject:deleted]
      survivingRegisteredViews:[NSSet set]];

  [ledger retireHiddenBranchesForDeletion:deletion];

  XCTAssertFalse(deleted.accessibilityElementsHidden);
  XCTAssertFalse(unrelated.accessibilityElementsHidden);
}

- (void)testInheritedMaskPrecedesRestorationOfADeletedAncestor
{
  auto ledger = [BottomSheetAccessibilityMaskLedger new];
  auto deleted = [BottomSheetMaskRecordingView new];
  UIView *survivor = [UIView new];
  [deleted addSubview:survivor];
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObject:deleted]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  __block BOOL restored = NO;
  deleted.afterWrite = ^(BOOL hidden) {
    if (!hidden) {
      restored = YES;
      XCTAssertTrue(survivor.accessibilityElementsHidden);
    }
  };
  auto deletion = [[BottomSheetNativeViewDeletion alloc]
      initWithDeletedViews:[NSSet setWithObject:deleted]
      survivingRegisteredViews:[NSSet setWithObject:survivor]];

  [ledger retireHiddenBranchesForDeletion:deletion];

  XCTAssertTrue(restored);
  XCTAssertTrue(survivor.accessibilityElementsHidden);
  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertFalse(survivor.accessibilityElementsHidden);
}

@end
