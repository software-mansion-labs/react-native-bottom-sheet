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

- (void)testMasksTheSuppliedUnionAndLeavesOtherBranchesUntouched
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *first = [UIView new];
  UIView *second = [UIView new];
  UIView *outside = [UIView new];

  [ledger reconcileHiddenBranches:[NSSet setWithObjects:first, second, nil]
         beforeRestoringBranches:^{
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
  [ledger reconcileHiddenBranches:[NSSet setWithObjects:first, second, nil]
         beforeRestoringBranches:nil];

  [ledger reconcileHiddenBranches:[NSSet setWithObject:second]
         beforeRestoringBranches:^{
           XCTAssertTrue(first.accessibilityElementsHidden);
           XCTAssertTrue(second.accessibilityElementsHidden);
         }];

  XCTAssertFalse(first.accessibilityElementsHidden);
  XCTAssertTrue(second.accessibilityElementsHidden);
  [ledger reconcileHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertFalse(second.accessibilityElementsHidden);
}

- (void)testCleanupDoesNotRewriteAPreviouslyHiddenApplicationBranch
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  BottomSheetMaskRecordingView *branch = [BottomSheetMaskRecordingView new];
  branch.accessibilityElementsHidden = YES;
  branch.hiddenWrites = 0;
  NSSet<UIView *> *hidden = [NSSet setWithObject:branch];

  [ledger reconcileHiddenBranches:hidden beforeRestoringBranches:nil];
  [ledger reconcileHiddenBranches:hidden beforeRestoringBranches:nil];
  [ledger reconcileHiddenBranches:[NSSet set] beforeRestoringBranches:nil];

  XCTAssertTrue(branch.accessibilityElementsHidden);
  XCTAssertEqual(branch.hiddenWrites, 0u);
}

- (void)testCleanupPreservesALaterVisibleWrite
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  BottomSheetMaskRecordingView *branch = [BottomSheetMaskRecordingView new];
  [ledger reconcileHiddenBranches:[NSSet setWithObject:branch] beforeRestoringBranches:nil];
  branch.accessibilityElementsHidden = NO;
  branch.hiddenWrites = 0;

  [ledger reconcileHiddenBranches:[NSSet set] beforeRestoringBranches:nil];

  XCTAssertFalse(branch.accessibilityElementsHidden);
  XCTAssertEqual(branch.hiddenWrites, 0u);
}

- (void)testRemaskingPreservesTheOriginalHiddenBaseline
{
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *branch = [UIView new];
  branch.accessibilityElementsHidden = YES;
  NSSet<UIView *> *hidden = [NSSet setWithObject:branch];
  [ledger reconcileHiddenBranches:hidden beforeRestoringBranches:nil];
  branch.accessibilityElementsHidden = NO;

  [ledger reconcileHiddenBranches:hidden beforeRestoringBranches:nil];
  XCTAssertTrue(branch.accessibilityElementsHidden);
  [ledger reconcileHiddenBranches:[NSSet set] beforeRestoringBranches:nil];

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
    [ledger reconcileHiddenBranches:[NSSet setWithObject:branch] beforeRestoringBranches:nil];
  }

  XCTAssertNil(removed);
  [ledger reconcileHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
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
  [ledger reconcileHiddenBranches:[NSSet setWithObjects:oldBranch, shared, nil]
         beforeRestoringBranches:nil];
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

  [ledger reconcileHiddenBranches:[NSSet setWithObjects:newBranch, shared, nil]
         beforeRestoringBranches:^{
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
  [ledger reconcileHiddenBranches:[NSSet setWithObjects:nativeChild, remaining, preHiddenChild, nil]
         beforeRestoringBranches:nil];

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
  [ledger reconcileHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertTrue(nativeChild.accessibilityElementsHidden);
  XCTAssertFalse(remaining.accessibilityElementsHidden);
}

- (void)testRetirementDoesNotRemaskAnUnrelatedApplicationWrite
{
  auto ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *deleted = [UIView new];
  UIView *unrelated = [UIView new];
  [ledger reconcileHiddenBranches:[NSSet setWithObjects:deleted, unrelated, nil]
         beforeRestoringBranches:nil];
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
  [ledger reconcileHiddenBranches:[NSSet setWithObject:deleted]
         beforeRestoringBranches:nil];
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
  [ledger reconcileHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertFalse(survivor.accessibilityElementsHidden);
}

@end
