#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface BottomSheetAccessibilityObservation : NSObject

+ (void)beginAccessibilityObservationForDismiss:(UIView *)dismiss
                                voiceOverRunning:(BOOL)voiceOverRunning
                                         focused:(BOOL)focused
    NS_SWIFT_NAME(begin(forDismiss:voiceOverRunning:focused:));
+ (void)resetAccessibilityNotificationCount NS_SWIFT_NAME(resetNotificationCount());
+ (NSUInteger)accessibilityLayoutChangedNotificationCount
    NS_SWIFT_NAME(layoutChangedNotificationCount());
+ (void)endAccessibilityObservation NS_SWIFT_NAME(end());

@end

NS_ASSUME_NONNULL_END
