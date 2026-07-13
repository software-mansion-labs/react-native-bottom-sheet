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
// Fired after each layout pass with fresh native geometry, so the component
// layer can push the content wrapper's target size (and, in overlay mode, the
// sheet frame) into the shadow tree.
- (void)bottomSheetViewDidLayout:(BottomSheetContentView *)view;
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
// Whether full-height detents may extend under the status bar; feeds the
// natively computed detent cap.
- (void)setExtendUnderStatusBar:(BOOL)extendUnderStatusBar;
// Floats the sheet up off the bottom edge by this many points (detached sheet).
- (void)setBottomInset:(CGFloat)bottomInset;
// Corner radius for the detached sheet's floating bottom corners.
- (void)setCornerRadius:(CGFloat)cornerRadius;
// Whether the detached sheet's floating bottom corners use a continuous curve.
- (void)setBorderCurveContinuous:(BOOL)continuous;
// The natively measured inset of the content region: the gap between the
// sheet's height and the detent cap.
- (CGFloat)contentRegionInset;
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
