#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@class BottomSheetContentView;

@protocol BottomSheetContentViewDelegate <NSObject>
- (void)bottomSheetView:(BottomSheetContentView *)view didChangeIndex:(NSInteger)index;
- (void)bottomSheetView:(BottomSheetContentView *)view didChangePosition:(CGFloat)position;
@end

@interface BottomSheetContentView : UIView

@property (nonatomic, weak, nullable) id<BottomSheetContentViewDelegate> delegate;
@property (nonatomic) BOOL animateIn;
@property (nonatomic, readonly) UIView *sheetContainer;

- (void)setDetents:(NSArray<NSDictionary *> *)raw;
- (void)setDetentIndex:(NSInteger)newIndex;
- (void)mountChildComponentView:(UIView *)childView atIndex:(NSInteger)index;
- (void)unmountChildComponentView:(UIView *)childView;

@end

NS_ASSUME_NONNULL_END
