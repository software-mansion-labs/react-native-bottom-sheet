#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

// The library's internal content container carries registered Fabric children
// through native presentation. Its component validates the current attachment
// before supplying their registered owner; stale or foreign ownership is nil.
@protocol BottomSheetPortalNativeContainer <NSObject>
@property (nonatomic, readonly, nullable) UIView *portalNativeOwner;
@end

NS_ASSUME_NONNULL_END
