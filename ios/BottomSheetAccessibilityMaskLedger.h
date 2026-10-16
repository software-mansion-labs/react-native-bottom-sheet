#import <UIKit/UIKit.h>
#import "BottomSheetNativeViewDeletion.h"

NS_ASSUME_NONNULL_BEGIN

// Owns hidden-branch mutations, not the selection of presentation boundaries.
// Access on the main thread. New masks precede the synchronous boundary transfer
// callback; obsolete masks are restored after it returns.
@interface BottomSheetAccessibilityMaskLedger : NSObject

- (void)reconcileHiddenBranches:(NSSet<UIView *> *)hiddenBranches
       beforeRestoringBranches:(nullable void (NS_NOESCAPE ^)(void))transferBoundaries;

// The mounting adapter verifies these exact native instances before Delete.
// Restore their leases before the registry can recycle them into other families.
- (void)retireHiddenBranchesForDeletion:(BottomSheetNativeViewDeletion *)deletion;

@end

NS_ASSUME_NONNULL_END
