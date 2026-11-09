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
@property (nonatomic) NSInteger scrollExpandCoordination;
@property (nonatomic) NSInteger scrollCollapseCoordination;
@property (nonatomic) NSTimeInterval settleDuration;
@property (nonatomic, readonly) UIView *sheetContainer;
@property (nonatomic, readonly) BOOL isModalAccessibilityActive;

- (void)setDetents:(NSArray<NSDictionary *> *)raw;
// Records the content-region inset of a state Fabric committed.
- (void)contentRegionStateDidCommit:(CGFloat)inset;
// Notifies the host that a Fabric mounting transaction has finished applying
// layout, so content laid out against the committed content-region inset is
// measurable again.
- (void)contentRegionLayoutDidApply;
// The natively measured inset of the content region: the gap between the
// sheet's height and the content region's.
- (CGFloat)contentRegionInset;
// How far the surface extends below the sheet, in layout units. A moving sheet
// keeps at least `previous`, the overflow last reported.
- (CGFloat)surfaceOverflowHolding:(CGFloat)previous;
- (void)setDetentIndex:(NSInteger)newIndex;
- (void)setScrimColor:(UIColor *_Nullable)color;
- (void)setScrimAccessibilityLabel:(NSString *_Nullable)label;
// Physical corner radii of the card (top-left, top-right, bottom-right,
// bottom-left) and its corner curve ("circular" or "continuous").
- (void)setSurfaceCornerRadii:(NSArray<NSNumber *> *)radii;
- (void)setSurfaceCornerCurve:(NSString *)curve;
// Outset card shadows: dictionaries of offsetX, offsetY, blurRadius,
// spreadDistance, a UIColor (possibly dynamic), opacity and excludesInterior.
- (void)setSurfaceShadows:(NSArray<NSDictionary *> *)shadows;
- (CGFloat)currentContentOffsetX;
- (CGFloat)currentContentOffsetY;
- (CGFloat)currentContentScale;
- (void)mountChildComponentView:(UIView *)childView atIndex:(NSInteger)index;
- (void)unmountChildComponentView:(UIView *)childView;
- (void)mountSurfaceComponentView:(UIView *)surfaceView atIndex:(NSInteger)index;
- (void)unmountSurfaceComponentView:(UIView *)surfaceView;
- (void)resetSheetState;

@end

NS_ASSUME_NONNULL_END
