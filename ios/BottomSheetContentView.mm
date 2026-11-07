#import "BottomSheetContentView.h"

#if __has_include("ReactNativeBottomSheet-Swift.h")
#import "ReactNativeBottomSheet-Swift.h"
#else
#import <ReactNativeBottomSheet/ReactNativeBottomSheet-Swift.h>
#endif

@interface BottomSheetContentView () <BottomSheetHostingViewDelegate>
@end

@implementation BottomSheetContentView {
  BottomSheetHostingView *_impl;
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    _impl = [[BottomSheetHostingView alloc] initWithFrame:self.bounds];
    _impl.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    _impl.eventDelegate = self;
    [self addSubview:_impl];
  }

  return self;
}

- (BOOL)animateIn
{
  return _impl.animateIn;
}

- (void)setAnimateIn:(BOOL)animateIn
{
  _impl.animateIn = animateIn;
}

- (BOOL)animateContentHeight
{
  return _impl.animateContentHeight;
}

- (void)setAnimateContentHeight:(BOOL)animateContentHeight
{
  _impl.animateContentHeight = animateContentHeight;
}

- (UIView *)sheetContainer
{
  return _impl.sheetContainer;
}

- (NSInteger)scrollExpandCoordination
{
  return _impl.scrollExpandCoordination;
}

- (void)setScrollExpandCoordination:(NSInteger)scrollExpandCoordination
{
  _impl.scrollExpandCoordination = scrollExpandCoordination;
}

- (NSInteger)scrollCollapseCoordination
{
  return _impl.scrollCollapseCoordination;
}

- (void)setScrollCollapseCoordination:(NSInteger)scrollCollapseCoordination
{
  _impl.scrollCollapseCoordination = scrollCollapseCoordination;
}

- (BOOL)modal
{
  return _impl.modal;
}

- (void)setModal:(BOOL)modal
{
  _impl.modal = modal;
}

- (void)setDetents:(NSArray<NSDictionary *> *)raw
{
  [_impl setDetents:raw];
}

- (void)contentRegionStateDidCommit:(CGFloat)inset
{
  [_impl contentRegionStateDidCommit:inset];
}

- (void)contentRegionLayoutDidApply
{
  [_impl contentRegionLayoutDidApply];
}

- (CGFloat)contentRegionInset
{
  return _impl.contentRegionInset;
}

- (CGFloat)surfaceOverflowHolding:(CGFloat)previous
{
  return [_impl surfaceOverflowWithHolding:previous];
}

- (void)setDetentIndex:(NSInteger)newIndex
{
  [_impl setDetentIndex:newIndex];
}

- (void)setScrimColor:(UIColor *)color
{
  _impl.scrimColor = color;
}

- (void)setScrimAccessibilityLabel:(NSString *)label
{
  _impl.scrimAccessibilityLabel = label;
}

- (void)setSurfaceCornerRadii:(NSArray<NSNumber *> *)radii
{
  [_impl setSurfaceCornerRadii:radii];
}

- (void)setSurfaceCornerCurve:(NSString *)curve
{
  [_impl setSurfaceCornerCurve:curve];
}

- (void)setSurfaceShadows:(NSArray<NSDictionary *> *)shadows
{
  [_impl setSurfaceShadows:shadows];
}

- (CGFloat)currentContentOffsetX
{
  return _impl.currentContentOffsetX;
}

- (CGFloat)currentContentOffsetY
{
  return _impl.currentContentOffsetY;
}

- (CGFloat)currentContentScale
{
  return _impl.currentContentScale;
}

- (BOOL)isModalAccessibilityActive
{
  return _impl.isModalAccessibilityActive;
}

- (void)mountChildComponentView:(UIView *)childView atIndex:(NSInteger)index
{
  [_impl mountChildComponentView:childView atIndex:index];
}

- (void)unmountChildComponentView:(UIView *)childView
{
  [_impl unmountChildComponentView:childView];
}

- (void)mountSurfaceComponentView:(UIView *)surfaceView atIndex:(NSInteger)index
{
  [_impl mountSurfaceComponentView:surfaceView atIndex:index];
}

- (void)unmountSurfaceComponentView:(UIView *)surfaceView
{
  [_impl unmountSurfaceComponentView:surfaceView];
}

- (void)resetSheetState
{
  [_impl resetSheetState];
}

- (void)bottomSheetHostingView:(BottomSheetHostingView *)view didChangeIndex:(NSInteger)index
{
  [self.delegate bottomSheetView:self didChangeIndex:index];
}

- (void)bottomSheetHostingView:(BottomSheetHostingView *)view didSettle:(NSInteger)index
{
  [self.delegate bottomSheetView:self didSettle:index];
}

- (void)bottomSheetHostingView:(BottomSheetHostingView *)view
              didChangePosition:(CGFloat)position
                          index:(CGFloat)index
{
  [self.delegate bottomSheetView:self didChangePosition:position index:index];
}

- (void)bottomSheetHostingView:(BottomSheetHostingView *)view didReportError:(NSString *)message
{
  [self.delegate bottomSheetView:self didReportError:message];
}

- (void)bottomSheetHostingViewDidLayout:(BottomSheetHostingView *)view
{
  [self.delegate bottomSheetViewDidLayout:self];
}

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  CGPoint implPoint = [self convertPoint:point toView:_impl];
  return [_impl hitTest:implPoint withEvent:event];
}

@end
