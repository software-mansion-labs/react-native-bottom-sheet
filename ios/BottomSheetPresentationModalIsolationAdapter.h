#import "BottomSheetPortalIsolation.h"

NS_ASSUME_NONNULL_BEGIN

// The existing per-window isolation writer. Both local portal cuts and window
// boundaries transfer through its one ledger; rejected proposals preserve it.
@interface BottomSheetPresentationModalIsolationAdapter : NSObject
- (void)registerBoundary:(UIView *)boundary;
- (void)unregisterBoundary:(UIView *)boundary;
- (void)reconcileTopBoundary:(nullable UIView *)topBoundary;
// nil means unavailable source facts: retain leases, retire released flags only.
- (BOOL)reconcilePortalPresentations:(nullable NSArray<BottomSheetPortalPresentation *> *)presentations
                      orderProvider:(BottomSheetPresentationOrderProvider)orderProvider
                          ifCurrent:(nullable BOOL (NS_NOESCAPE ^)(void))isCurrent;
- (void)invalidate;
@end

NS_ASSUME_NONNULL_END
