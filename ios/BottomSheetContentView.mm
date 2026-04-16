#import "BottomSheetContentView.h"

#if __has_include("ReactNativeBottomSheet-Swift.h")
#import "ReactNativeBottomSheet-Swift.h"
#else
#import <ReactNativeBottomSheet/ReactNativeBottomSheet-Swift.h>
#endif

@interface BottomSheetContentView () <RNSBottomSheetHostingViewDelegate>
@end

@implementation BottomSheetContentView {
  RNSBottomSheetHostingView *_impl;
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    _impl = [[RNSBottomSheetHostingView alloc] initWithFrame:self.bounds];
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

- (void)resetSheetState
{
  [_impl resetSheetState];
}

- (void)bottomSheetHostingView:(RNSBottomSheetHostingView *)view didChangeIndex:(NSInteger)index
{
  [self.delegate bottomSheetView:self didChangeIndex:index];
}

- (void)bottomSheetHostingView:(RNSBottomSheetHostingView *)view didSettle:(NSInteger)index
{
  [self.delegate bottomSheetView:self didSettle:index];
}

- (void)bottomSheetHostingView:(RNSBottomSheetHostingView *)view didChangePosition:(CGFloat)position
{
  [self.delegate bottomSheetView:self didChangePosition:position];
}

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  CGPoint implPoint = [self convertPoint:point toView:_impl];
  return [_impl hitTest:implPoint withEvent:event];
}

@end
