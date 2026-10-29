#import <UIKit/UIKit.h>
#import "../../../../ios/BottomSheetPresentationOwnership.h"

NS_ASSUME_NONNULL_BEGIN

@interface BottomSheetTestComponentFactory : NSObject

+ (UIView *)makeProductionComponentWithNativeOverlay:(BOOL)nativeOverlay;
+ (UIView *)makeProductionComponentWithNativeOverlay:(BOOL)nativeOverlay index:(NSInteger)index;
+ (UIView *)makeProductionOverlayWithoutClosedDetent;
+ (void)setIndex:(NSInteger)index forProductionComponent:(UIView *)component;
+ (void)setNativeOverlay:(BOOL)nativeOverlay forProductionComponent:(UIView *)component;
+ (void)setClosedDetentProgrammatic:(BOOL)programmatic
             forProductionComponent:(UIView *)component;
+ (void)setLayoutSize:(CGSize)size forProductionComponent:(UIView *)component;
+ (void)performObservedMountForProductionComponent:(UIView *)component
                                         mutation:(void (^)(void))mutation;
+ (void)prepareForRecycle:(UIView *)component;
+ (void)invalidateProductionComponent:(UIView *)component;

// Passive inspection of existing runtime authority. These reads never register
// candidates, resolve order, apply masks, or route an accessibility action.
+ (BottomSheetPresentationController *)presentationControllerForComponent:(UIView *)component;
+ (nullable UIWindow *)registeredWindowForController:(BottomSheetPresentationController *)controller;

@end

NS_ASSUME_NONNULL_END
