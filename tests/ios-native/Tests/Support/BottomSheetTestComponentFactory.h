#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface BottomSheetTestComponentFactory : NSObject

+ (UIView *)makeProductionComponentWithNativeOverlay:(BOOL)nativeOverlay;
+ (UIView *)makeProductionComponentWithNativeOverlay:(BOOL)nativeOverlay index:(NSInteger)index;
+ (void)setIndex:(NSInteger)index forProductionComponent:(UIView *)component;
+ (void)setNativeOverlay:(BOOL)nativeOverlay forProductionComponent:(UIView *)component;
+ (void)setLayoutSize:(CGSize)size forProductionComponent:(UIView *)component;
+ (void)prepareForRecycle:(UIView *)component;
+ (void)invalidateProductionComponent:(UIView *)component;

@end

NS_ASSUME_NONNULL_END
