#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

// An immutable, short-lived description of one verified pre-delete native
// hierarchy. Consume synchronously before the registry applies the transaction.
@interface BottomSheetNativeViewDeletion : NSObject

- (instancetype)initWithDeletedViews:(NSSet<UIView *> *)deletedViews
          survivingRegisteredViews:(NSSet<UIView *> *)survivingRegisteredViews NS_DESIGNATED_INITIALIZER;
- (instancetype)init NS_UNAVAILABLE;

@property (nonatomic, copy, readonly) NSSet<UIView *> *deletedViews;
@property (nonatomic, copy, readonly) NSSet<UIView *> *survivingRegisteredViews;

@end

NS_ASSUME_NONNULL_END
