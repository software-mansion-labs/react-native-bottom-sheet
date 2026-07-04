#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@class BottomSheetContentView;

@protocol BottomSheetContentViewDelegate <NSObject>
- (void)bottomSheetView:(BottomSheetContentView *)view didChangeIndex:(NSInteger)index;
- (void)bottomSheetView:(BottomSheetContentView *)view didSettle:(NSInteger)index;
- (void)bottomSheetView:(BottomSheetContentView *)view
      didChangePosition:(CGFloat)position
                  index:(CGFloat)index;
- (void)bottomSheetView:(BottomSheetContentView *)view didReportError:(NSString *)message;
@end

@interface BottomSheetContentView : UIView

@property (nonatomic, weak, nullable) id<BottomSheetContentViewDelegate> delegate;
@property (nonatomic) BOOL animateIn;
@property (nonatomic) BOOL animateContentHeight;
@property (nonatomic) BOOL modal;
@property (nonatomic) BOOL disableScrollableNegotiation;
@property (nonatomic, readonly) UIView *sheetContainer;
@property (nonatomic, readonly) BOOL isModalAccessibilityActive;

- (void)setDetents:(NSArray<NSDictionary *> *)raw;
- (void)setMaxDetentHeight:(CGFloat)maxDetentHeight;
// Native detent cap computed from the overlay window's measured geometry; NAN
// (the default) falls back to the JS-provided maxDetentHeight.
- (void)setOverlayMaxDetentHeight:(CGFloat)overlayMaxDetentHeight;
- (void)setDetentIndex:(NSInteger)newIndex;
- (void)setScrimColor:(UIColor *_Nullable)color;
- (void)setScrimOpacities:(NSArray<NSNumber *> *)opacities;
- (CGFloat)currentContentOffsetY;
- (void)mountChildComponentView:(UIView *)childView atIndex:(NSInteger)index;
- (void)unmountChildComponentView:(UIView *)childView;
- (void)mountSurfaceComponentView:(UIView *)surfaceView atIndex:(NSInteger)index;
- (void)unmountSurfaceComponentView:(UIView *)surfaceView;
- (void)resetSheetState;

@end

NS_ASSUME_NONNULL_END
