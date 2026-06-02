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

- (UIView *)sheetContainer
{
  return _impl.sheetContainer;
}

- (BOOL)disableScrollableNegotiation
{
  return _impl.disableScrollableNegotiation;
}

- (void)setDisableScrollableNegotiation:(BOOL)disableScrollableNegotiation
{
  _impl.disableScrollableNegotiation = disableScrollableNegotiation;
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

- (void)setMaxDetentHeight:(CGFloat)maxDetentHeight
{
  _impl.maxDetentHeight = maxDetentHeight;
}

- (void)setDetentIndex:(NSInteger)newIndex
{
  [_impl setDetentIndex:newIndex];
}

- (void)setScrimColor:(UIColor *)color
{
  _impl.scrimColor = color;
}

- (void)setScrimOpacities:(NSArray<NSNumber *> *)opacities
{
  [_impl setScrimOpacities:opacities];
}

- (CGFloat)currentContentOffsetY
{
  return _impl.currentContentOffsetY;
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

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  CGPoint implPoint = [self convertPoint:point toView:_impl];
  return [_impl hitTest:implPoint withEvent:event];
}

@end
