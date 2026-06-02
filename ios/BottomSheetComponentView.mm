#import "BottomSheetComponentView.h"
#import "BottomSheetContentView.h"
#import "BottomSheetSurfaceComponentView.h"
#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/BottomSheetStateHelper.h"
#import "../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/ComponentDescriptors.h"

#import <React/RCTAssert.h>
#import <React/RCTConversions.h>
#import <React/RCTFabricComponentsPlugins.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/EventEmitters.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/Props.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/RCTComponentViewHelpers.h>

using namespace facebook::react;

@interface BottomSheetComponentView () <BottomSheetContentViewDelegate>
@end

@implementation BottomSheetComponentView {
  BottomSheetContentView *_sheetView;
  State::Shared _sheetState;
  float _lastContentOffsetY;
  BOOL _needsIndexSyncAfterRecycle;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<BottomSheetViewComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    static const auto defaultProps = std::make_shared<const BottomSheetViewProps>();
    _props = defaultProps;
    _needsIndexSyncAfterRecycle = NO;

    _sheetView = [[BottomSheetContentView alloc] initWithFrame:CGRectZero];
    _sheetView.delegate = self;
    _sheetView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    self.contentView = _sheetView;
  }
  return self;
}

- (void)updateProps:(const Props::Shared &)props oldProps:(const Props::Shared &)oldProps
{
  const auto &newViewProps = static_cast<const BottomSheetViewProps &>(*props);
  const auto &oldViewProps = static_cast<const BottomSheetViewProps &>(*_props);

  // Always update detents — the codegen struct lacks operator==.
  {
    NSMutableArray<NSDictionary *> *detentsArray = [NSMutableArray new];
    for (const auto &detent : newViewProps.detents) {
      [detentsArray addObject:@{
        @"value": @(detent.value),
        @"kind": detent.kind == "content" ? @"content" : @"points",
        @"programmatic": @(detent.programmatic),
      }];
    }
    [_sheetView setDetents:detentsArray];
  }

  if (newViewProps.maxDetentHeight != oldViewProps.maxDetentHeight) {
    [_sheetView setMaxDetentHeight:newViewProps.maxDetentHeight];
  }

  if (_needsIndexSyncAfterRecycle || newViewProps.index != oldViewProps.index) {
    [_sheetView setDetentIndex:newViewProps.index];
    _needsIndexSyncAfterRecycle = NO;
  }

  if (newViewProps.animateIn != oldViewProps.animateIn) {
    _sheetView.animateIn = newViewProps.animateIn;
  }

  if (newViewProps.modal != oldViewProps.modal) {
    _sheetView.modal = newViewProps.modal;
  }

  if (newViewProps.disableScrollableNegotiation != oldViewProps.disableScrollableNegotiation) {
    _sheetView.disableScrollableNegotiation = newViewProps.disableScrollableNegotiation;
  }

  if (newViewProps.scrimColor != oldViewProps.scrimColor) {
    [_sheetView setScrimColor:RCTUIColorFromSharedColor(newViewProps.scrimColor)];
  }

  if (newViewProps.scrimOpacities != oldViewProps.scrimOpacities) {
    NSMutableArray<NSNumber *> *opacities = [NSMutableArray new];
    for (const auto &opacity : newViewProps.scrimOpacities) {
      [opacities addObject:@(opacity)];
    }
    [_sheetView setScrimOpacities:opacities];
  }

  [super updateProps:props oldProps:oldProps];
}

- (void)updateState:(const State::Shared &)state oldState:(const State::Shared &)oldState
{
  _sheetState = state;
}

- (void)mountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index
{
  // Identify the visual surface by component type so the host can own its
  // geometry. Everything else is treated as content.
  if ([childComponentView isKindOfClass:BottomSheetSurfaceComponentView.class]) {
    [_sheetView mountSurfaceComponentView:childComponentView atIndex:index];
  } else {
    [_sheetView mountChildComponentView:childComponentView atIndex:index];
  }
}

- (void)unmountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index
{
  if ([childComponentView isKindOfClass:BottomSheetSurfaceComponentView.class]) {
    [_sheetView unmountSurfaceComponentView:childComponentView];
  } else {
    [_sheetView unmountChildComponentView:childComponentView];
  }
}

#pragma mark - BottomSheetContentViewDelegate

- (void)bottomSheetView:(BottomSheetContentView *)view didChangeIndex:(NSInteger)index
{
  if (_eventEmitter) {
    auto emitter = std::static_pointer_cast<const BottomSheetViewEventEmitter>(_eventEmitter);
    emitter->onIndexChange({.index = static_cast<int>(index)});
  }
}

- (void)bottomSheetView:(BottomSheetContentView *)view didSettle:(NSInteger)index
{
  if (_eventEmitter) {
    auto emitter = std::static_pointer_cast<const BottomSheetViewEventEmitter>(_eventEmitter);
    emitter->onSettle({.index = static_cast<int>(index)});
  }
}

- (void)bottomSheetView:(BottomSheetContentView *)view
      didChangePosition:(CGFloat)position
                  index:(CGFloat)index
{
  if (_eventEmitter) {
    auto emitter = std::static_pointer_cast<const BottomSheetViewEventEmitter>(_eventEmitter);
    emitter->onPositionChange(
        {.position = static_cast<double>(position), .index = static_cast<double>(index)});
  }

  float contentOffsetY = static_cast<float>(view.currentContentOffsetY);
  if (contentOffsetY == _lastContentOffsetY) {
    return;
  }
  _lastContentOffsetY = contentOffsetY;

  if (_sheetState) {
    updateBottomSheetContentOffsetY(_sheetState, contentOffsetY);
  }
}

- (void)bottomSheetView:(BottomSheetContentView *)view didReportError:(NSString *)message
{
  RCTFatal([NSError errorWithDomain:RCTErrorDomain code:0 userInfo:@{NSLocalizedDescriptionKey : message}]);
}

- (void)prepareForRecycle
{
  [super prepareForRecycle];
  _needsIndexSyncAfterRecycle = YES;
  [_sheetView resetSheetState];
  _sheetState.reset();
  _lastContentOffsetY = 0;
}

@end

Class<RCTComponentViewProtocol> BottomSheetViewCls(void)
{
  return BottomSheetComponentView.class;
}
