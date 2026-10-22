#import <UIKit/UIKit.h>

#import "BottomSheetPresentationOwnership.h"
#import "BottomSheetPortalNativeContainer.h"

NS_ASSUME_NONNULL_BEGIN

@class BottomSheetContentView;

@protocol BottomSheetContentViewDelegate <NSObject>
- (void)bottomSheetView:(BottomSheetContentView *)view didChangeIndex:(NSInteger)index;
- (void)bottomSheetView:(BottomSheetContentView *)view didSettle:(NSInteger)index;
- (void)bottomSheetView:(BottomSheetContentView *)view
      didChangePosition:(CGFloat)position
                  index:(CGFloat)index;
- (void)bottomSheetView:(BottomSheetContentView *)view didReportError:(NSString *)message;
- (void)bottomSheetView:(BottomSheetContentView *)view
    didChangePresentationActive:(BOOL)presentationActive;
- (BottomSheetPresentationEscapeRoute)voiceOverEscapeRouteForBottomSheetView:
    (BottomSheetContentView *)view;
// Fired after each layout pass with fresh native geometry, so the component
// layer can push the content wrapper's target size (and, in overlay mode, the
// sheet frame) into the shadow tree.
- (void)bottomSheetViewDidLayout:(BottomSheetContentView *)view;
@optional
// Supplies current registered ownership for the internal native content path.
- (nullable UIView *)portalNativeOwnerForBottomSheetView:(BottomSheetContentView *)view;
@end

@interface BottomSheetContentView : UIView <BottomSheetPortalNativeContainer, BottomSheetPortalAccessibilityTransition>

@property (nonatomic, weak, nullable) id<BottomSheetContentViewDelegate> delegate;
@property (nonatomic) BOOL animateIn;
@property (nonatomic) BOOL animateContentHeight;
@property (nonatomic) BOOL modal;
@property (nonatomic) BOOL defersAccessibilityPresentation;
@property (nonatomic) NSInteger scrollableExpandNegotiation;
@property (nonatomic) NSInteger scrollableCollapseNegotiation;
@property (nonatomic, readonly) UIView *sheetContainer;
@property (nonatomic, readonly) BOOL isModalAccessibilityActive;
@property (nonatomic, readonly, getter=isPresentationActive) BOOL presentationActive;

- (void)setDetents:(NSArray<NSDictionary *> *)raw;
// Records the content-region inset of a state Fabric committed.
- (void)contentRegionStateDidCommit:(CGFloat)inset;
// Notifies the host that a Fabric mounting transaction has finished applying
// layout, so content laid out against the committed content-region inset is
// measurable again.
- (void)contentRegionLayoutDidApply;
// Whether full-height detents may extend under the status bar; feeds the
// natively computed detent cap.
- (void)setExtendUnderStatusBar:(BOOL)extendUnderStatusBar;
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
