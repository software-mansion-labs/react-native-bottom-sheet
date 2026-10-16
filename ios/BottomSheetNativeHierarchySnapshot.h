#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

// Query-scoped physical facts. Unsupported structural overrides are rejected
// before dispatch; no getter callbacks run during final validation.
@interface BottomSheetNativeHierarchySnapshot : NSObject
@property (nonatomic, readonly, getter=isValid) BOOL valid;
- (nullable UIView *)parentOfView:(nullable UIView *)view;
- (NSInteger)tagOfView:(nullable UIView *)view;
- (nullable NSArray<UIView *> *)childrenOfView:(UIView *)view;
- (nullable UIView *)modalOwnerOfContainer:(UIView *)view;
- (BOOL)validate;
@end

NS_ASSUME_NONNULL_END
