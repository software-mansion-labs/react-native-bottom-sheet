#import "BottomSheetContentView.h"

struct DetentSpec {
  CGFloat height;
  BOOL programmatic;
};

@interface BottomSheetContentView () <UIGestureRecognizerDelegate>
@end

@implementation BottomSheetContentView {
  UIView *_sheetContainer;
  UIPanGestureRecognizer *_panGesture;
  UIViewPropertyAnimator *_activeAnimator;
  CADisplayLink *_displayLink;

  NSArray<NSValue *> *_detentSpecs; // Boxed DetentSpec structs
  NSInteger _targetIndex;
  NSNumber *_pendingIndex; // nil = no pending index
  BOOL _hasLaidOut;
  BOOL _isPanning;
}

// MARK: - Init

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    _animateIn = YES;
    _targetIndex = 0;
    _hasLaidOut = NO;
    _isPanning = NO;
    _detentSpecs = @[];

    self.backgroundColor = UIColor.clearColor;
    self.clipsToBounds = NO;

    _sheetContainer = [[UIView alloc] initWithFrame:CGRectZero];
    _sheetContainer.backgroundColor = UIColor.clearColor;
    _sheetContainer.clipsToBounds = NO;
    [self addSubview:_sheetContainer];

    _panGesture = [[UIPanGestureRecognizer alloc] initWithTarget:self action:@selector(handlePan:)];
    _panGesture.delegate = self;
    [_sheetContainer addGestureRecognizer:_panGesture];
  }
  return self;
}

// MARK: - Detent helpers

static DetentSpec DetentSpecFromValue(NSValue *value)
{
  DetentSpec spec;
  [value getValue:&spec];
  return spec;
}

static NSValue *DetentSpecToValue(DetentSpec spec)
{
  return [NSValue valueWithBytes:&spec objCType:@encode(DetentSpec)];
}

- (DetentSpec)detentAtIndex:(NSInteger)index
{
  if (index < 0 || index >= (NSInteger)_detentSpecs.count) {
    return {0, NO};
  }
  return DetentSpecFromValue(_detentSpecs[index]);
}

- (DetentSpec)lastDetent
{
  if (_detentSpecs.count == 0) return {0, NO};
  return DetentSpecFromValue(_detentSpecs.lastObject);
}

// MARK: - Layout

- (void)layoutSubviews
{
  [super layoutSubviews];
  if (self.bounds.size.width <= 0 || self.bounds.size.height <= 0) return;

  CGFloat maxHeight = _detentSpecs.count > 0 ? [self lastDetent].height : self.bounds.size.height;
  _sheetContainer.bounds = CGRectMake(0, 0, self.bounds.size.width, maxHeight);
  _sheetContainer.center = CGPointMake(self.bounds.size.width / 2.0, self.bounds.size.height - maxHeight / 2.0);

  if (!_hasLaidOut && _detentSpecs.count > 0) {
    _hasLaidOut = YES;
    NSInteger indexToApply = _pendingIndex != nil ? _pendingIndex.integerValue : _targetIndex;
    _pendingIndex = nil;
    _targetIndex = MAX(0, MIN((NSInteger)_detentSpecs.count - 1, indexToApply));

    if (_animateIn) {
      CGFloat closedTy = _detentSpecs.count > 0 ? [self lastDetent].height : self.bounds.size.height;
      _sheetContainer.transform = CGAffineTransformMakeTranslation(0, closedTy);
      [self emitPosition];
      [self snapToIndex:_targetIndex velocity:0];
    } else {
      _sheetContainer.transform = CGAffineTransformMakeTranslation(0, [self translationYForIndex:_targetIndex]);
      [self emitPosition];
      [_delegate bottomSheetView:self didChangeIndex:_targetIndex];
    }
    return;
  }

  if (_activeAnimator != nil || _isPanning) return;
  _sheetContainer.transform = CGAffineTransformMakeTranslation(0, [self translationYForIndex:_targetIndex]);
}

// MARK: - Public

- (UIView *)sheetContainer
{
  return _sheetContainer;
}

- (void)mountChildComponentView:(UIView *)childView atIndex:(NSInteger)index
{
  [_sheetContainer insertSubview:childView atIndex:index];
}

- (void)unmountChildComponentView:(UIView *)childView
{
  [childView removeFromSuperview];
}

// MARK: - Hit testing

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  CGPoint containerPoint = [self convertPoint:point toView:_sheetContainer];
  if (!CGRectContainsPoint(_sheetContainer.bounds, containerPoint)) return nil;
  return [_sheetContainer hitTest:containerPoint withEvent:event];
}

// MARK: - Prop setters

- (void)setDetents:(NSArray<NSDictionary *> *)raw
{
  NSMutableArray<NSValue *> *specs = [NSMutableArray new];
  for (NSDictionary *dict in raw) {
    NSNumber *height = dict[@"height"];
    if (height == nil) continue;
    BOOL programmatic = [dict[@"programmatic"] boolValue];
    DetentSpec spec = {height.doubleValue, programmatic};
    [specs addObject:DetentSpecToValue(spec)];
  }
  _detentSpecs = [specs copy];
  [self setNeedsLayout];
}

- (void)setDetentIndex:(NSInteger)newIndex
{
  if (newIndex < 0) return;

  if (!_hasLaidOut) {
    _pendingIndex = @(newIndex);
    _targetIndex = newIndex;
    return;
  }

  if (newIndex >= (NSInteger)_detentSpecs.count || newIndex == _targetIndex) return;
  [self snapToIndex:newIndex velocity:0];
}

// MARK: - Snap logic

- (CGFloat)translationYForIndex:(NSInteger)index
{
  CGFloat maxHeight = _detentSpecs.count > 0 ? [self lastDetent].height : self.bounds.size.height;
  CGFloat snapHeight = [self detentAtIndex:index].height;
  return maxHeight - snapHeight;
}

- (CGFloat)draggableMinTy
{
  NSInteger highestIndex = 0;
  for (NSInteger i = (NSInteger)_detentSpecs.count - 1; i >= 0; i--) {
    if (!DetentSpecFromValue(_detentSpecs[i]).programmatic) {
      highestIndex = i;
      break;
    }
  }
  return [self translationYForIndex:highestIndex];
}

- (CGFloat)draggableMaxTy
{
  NSInteger lowestIndex = 0;
  for (NSInteger i = 0; i < (NSInteger)_detentSpecs.count; i++) {
    if (!DetentSpecFromValue(_detentSpecs[i]).programmatic) {
      lowestIndex = i;
      break;
    }
  }
  return [self translationYForIndex:lowestIndex];
}

- (void)emitPosition
{
  CGFloat maxHeight = _detentSpecs.count > 0 ? [self lastDetent].height : self.bounds.size.height;
  CALayer *presentation = _sheetContainer.layer.presentationLayer;
  CGFloat ty = presentation != nil ? presentation.affineTransform.ty : _sheetContainer.transform.ty;
  [_delegate bottomSheetView:self didChangePosition:maxHeight - ty];
}

- (void)startDisplayLink
{
  if (_displayLink != nil) return;
  _displayLink = [CADisplayLink displayLinkWithTarget:self selector:@selector(displayLinkFired)];
  [_displayLink addToRunLoop:NSRunLoop.mainRunLoop forMode:NSRunLoopCommonModes];
}

- (void)stopDisplayLink
{
  [_displayLink invalidate];
  _displayLink = nil;
}

- (void)displayLinkFired
{
  [self emitPosition];
}

- (void)snapToIndex:(NSInteger)index velocity:(CGFloat)velocity
{
  if (index < 0 || index >= (NSInteger)_detentSpecs.count) return;
  _targetIndex = index;

  CGFloat currentTy = _sheetContainer.transform.ty;
  CGFloat targetTy = [self translationYForIndex:index];
  CGFloat distance = targetTy - currentTy;

  CGFloat velocityRatio = distance != 0 ? velocity / distance : 0;
  CGFloat clampedRatio = MIN(MAX(velocityRatio, -5), 5);
  CGVector initialVelocity = CGVectorMake(0, clampedRatio);

  [_activeAnimator stopAnimation:YES];

  UISpringTimingParameters *spring = [[UISpringTimingParameters alloc] initWithDampingRatio:1.0
                                                                           initialVelocity:initialVelocity];
  UIViewPropertyAnimator *animator = [[UIViewPropertyAnimator alloc] initWithDuration:0.45
                                                                     timingParameters:spring];

  __weak BottomSheetContentView *weakSelf = self;
  NSInteger capturedIndex = index;
  [animator addAnimations:^{
    BottomSheetContentView *strongSelf = weakSelf;
    if (strongSelf == nil) return;
    strongSelf->_sheetContainer.transform = CGAffineTransformMakeTranslation(0, targetTy);
  }];
  [animator addCompletion:^(UIViewAnimatingPosition position) {
    BottomSheetContentView *strongSelf = weakSelf;
    if (strongSelf == nil || position != UIViewAnimatingPositionEnd) return;
    [strongSelf stopDisplayLink];
    [strongSelf emitPosition];
    strongSelf->_activeAnimator = nil;
    [strongSelf->_delegate bottomSheetView:strongSelf didChangeIndex:capturedIndex];
  }];
  [animator startAnimation];
  _activeAnimator = animator;
  [self startDisplayLink];
}

// MARK: - Pan gesture

- (void)handlePan:(UIPanGestureRecognizer *)gesture
{
  CGFloat maxHeight = _detentSpecs.count > 0 ? [self lastDetent].height : self.bounds.size.height;

  switch (gesture.state) {
    case UIGestureRecognizerStateBegan: {
      _isPanning = YES;
      [gesture setTranslation:CGPointZero inView:self];
      if (_activeAnimator != nil) {
        [self stopDisplayLink];
        CALayer *presentation = _sheetContainer.layer.presentationLayer;
        CGAffineTransform visual = presentation != nil ? presentation.affineTransform : _sheetContainer.transform;
        [_activeAnimator stopAnimation:YES];
        _sheetContainer.transform = visual;
        _activeAnimator = nil;
      }
      break;
    }
    case UIGestureRecognizerStateChanged: {
      CGFloat delta = [gesture translationInView:self].y;
      [gesture setTranslation:CGPointZero inView:self];
      CGFloat minTy = [self draggableMinTy];
      CGFloat maxTy = [self draggableMaxTy];
      CGFloat newTy = MAX(minTy, MIN(maxTy, _sheetContainer.transform.ty + delta));
      _sheetContainer.transform = CGAffineTransformMakeTranslation(0, newTy);
      [self emitPosition];
      break;
    }
    case UIGestureRecognizerStateEnded:
    case UIGestureRecognizerStateCancelled: {
      _isPanning = NO;
      CGFloat velocity = [gesture velocityInView:self].y;
      CGFloat currentHeight = maxHeight - _sheetContainer.transform.ty;
      NSInteger index = [self bestSnapIndexForHeight:currentHeight velocity:velocity];
      [self snapToIndex:index velocity:velocity];
      break;
    }
    case UIGestureRecognizerStateFailed:
      _isPanning = NO;
      break;
    default:
      break;
  }
}

- (NSInteger)bestSnapIndexForHeight:(CGFloat)height velocity:(CGFloat)velocity
{
  NSMutableArray<NSNumber *> *draggableIndices = [NSMutableArray new];
  for (NSInteger i = 0; i < (NSInteger)_detentSpecs.count; i++) {
    if (!DetentSpecFromValue(_detentSpecs[i]).programmatic) {
      [draggableIndices addObject:@(i)];
    }
  }
  if (draggableIndices.count == 0) return _targetIndex;

  CGFloat flickThreshold = 600.0;

  if (velocity < -flickThreshold) {
    for (NSNumber *idx in draggableIndices) {
      if ([self detentAtIndex:idx.integerValue].height > height) return idx.integerValue;
    }
    return draggableIndices.lastObject.integerValue;
  }
  if (velocity > flickThreshold) {
    for (NSInteger i = (NSInteger)draggableIndices.count - 1; i >= 0; i--) {
      NSInteger idx = draggableIndices[i].integerValue;
      if ([self detentAtIndex:idx].height < height) return idx;
    }
    return draggableIndices.firstObject.integerValue;
  }

  NSInteger bestIndex = _targetIndex;
  CGFloat bestDistance = CGFLOAT_MAX;
  for (NSNumber *idx in draggableIndices) {
    CGFloat distance = fabs([self detentAtIndex:idx.integerValue].height - height);
    if (distance < bestDistance) {
      bestDistance = distance;
      bestIndex = idx.integerValue;
    }
  }
  return bestIndex;
}

// MARK: - Scroll view helper

- (UIScrollView *)firstScrollViewIn:(UIView *)view
{
  for (UIView *sub in view.subviews) {
    if ([sub isKindOfClass:UIScrollView.class]) return (UIScrollView *)sub;
    UIScrollView *found = [self firstScrollViewIn:sub];
    if (found != nil) return found;
  }
  return nil;
}

// MARK: - UIGestureRecognizerDelegate

- (BOOL)gestureRecognizerShouldBegin:(UIGestureRecognizer *)gestureRecognizer
{
  if (gestureRecognizer != _panGesture) return YES;

  CGPoint velocity = [_panGesture velocityInView:self];
  if (fabs(velocity.y) <= fabs(velocity.x)) return NO;

  NSInteger maxDraggableIndex = 0;
  for (NSInteger i = (NSInteger)_detentSpecs.count - 1; i >= 0; i--) {
    if (!DetentSpecFromValue(_detentSpecs[i]).programmatic) {
      maxDraggableIndex = i;
      break;
    }
  }
  if (_targetIndex < maxDraggableIndex) return YES;

  if (velocity.y < 0) return NO;

  UIScrollView *scrollView = [self firstScrollViewIn:_sheetContainer];
  BOOL scrollAtTop = scrollView == nil || scrollView.contentOffset.y <= 0;
  return scrollAtTop;
}

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
    shouldBeRequiredToFailByGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer
{
  return gestureRecognizer == _panGesture && [otherGestureRecognizer isKindOfClass:UIPanGestureRecognizer.class];
}

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
    shouldRecognizeSimultaneouslyWithGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer
{
  return NO;
}

@end
