#import "BottomSheetComponentView.h"
#import "BottomSheetContentView.h"

#import <React/RCTConversions.h>
#import <React/RCTFabricComponentsPlugins.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/ComponentDescriptors.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/EventEmitters.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/Props.h>
#import <react/renderer/components/ReactNativeBottomSheetSpec/RCTComponentViewHelpers.h>

using namespace facebook::react;

@interface BottomSheetComponentView () <BottomSheetContentViewDelegate>
@end

@implementation BottomSheetComponentView {
  BottomSheetContentView *_sheetView;
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
        @"height": @(detent.height),
        @"programmatic": @(detent.programmatic),
      }];
    }
    [_sheetView setDetents:detentsArray];
  }

  if (newViewProps.index != oldViewProps.index) {
    [_sheetView setDetentIndex:newViewProps.index];
  }

  if (newViewProps.animateIn != oldViewProps.animateIn) {
    _sheetView.animateIn = newViewProps.animateIn;
  }

  [super updateProps:props oldProps:oldProps];
}

- (void)mountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index
{
  [_sheetView mountChildComponentView:childComponentView atIndex:index];
}

- (void)unmountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index
{
  [_sheetView unmountChildComponentView:childComponentView];
}

#pragma mark - BottomSheetContentViewDelegate

- (void)bottomSheetView:(BottomSheetContentView *)view didChangeIndex:(NSInteger)index
{
  if (_eventEmitter) {
    auto emitter = std::static_pointer_cast<const BottomSheetViewEventEmitter>(_eventEmitter);
    emitter->onIndexChange({.index = static_cast<int>(index)});
  }
}

- (void)bottomSheetView:(BottomSheetContentView *)view didChangePosition:(CGFloat)position
{
  if (_eventEmitter) {
    auto emitter = std::static_pointer_cast<const BottomSheetViewEventEmitter>(_eventEmitter);
    emitter->onPositionChange({.position = static_cast<double>(position)});
  }
}

@end

Class<RCTComponentViewProtocol> BottomSheetViewCls(void)
{
  return BottomSheetComponentView.class;
}
