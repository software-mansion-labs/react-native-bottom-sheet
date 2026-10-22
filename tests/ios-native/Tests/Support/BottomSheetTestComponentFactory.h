#import <UIKit/UIKit.h>

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

@end

NS_ASSUME_NONNULL_END
