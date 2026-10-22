#import "BottomSheetPortalMountingObserver.h"
#import "BottomSheetPresentationOwnership.h"
#import <React/RCTBridgeModule.h>
#import <React/RCTScheduler.h>
#import <React/RCTSurfacePresenter.h>
#import <ReactCommon/RCTTurboModule.h>
#include <atomic>

using namespace facebook::react;

// Module construction asks RCTInstance for its normal presenter injection. It
// exposes no JS methods and does not replace scheduler/mounting delegates.
@interface BottomSheetPresentationModule : NSObject <RCTBridgeModule, RCTTurboModule> {
  std::atomic<bool> _valid;
}
@property (nonatomic, strong) BottomSheetPortalMountingObserver *observer;
@end

@implementation BottomSheetPresentationModule
RCT_EXPORT_MODULE(BottomSheetPresentation)
+ (BOOL)requiresMainQueueSetup { return NO; }

- (instancetype)init
{
  if (self = [super init]) _valid = true;
  return self;
}

- (std::shared_ptr<TurboModule>)getTurboModule:(const ObjCTurboModule::InitParams &)params
{
  return std::make_shared<ObjCTurboModule>(params);
}

- (void)setSurfacePresenter:(id<RCTSurfacePresenterStub>)presenter
{
  __weak BottomSheetPresentationModule *weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    BottomSheetPresentationModule *module = weakSelf;
    if (!module || !module->_valid.load()) return;
    [module.observer invalidate];
    __weak RCTSurfacePresenter *surfacePresenter = (RCTSurfacePresenter *)presenter;
    module.observer = [[BottomSheetPortalMountingObserver alloc]
        initWithMountingManager:surfacePresenter.mountingManager
        uiManagerProvider:^{
          RCTScheduler *scheduler = surfacePresenter.scheduler;
          return scheduler ? scheduler.uiManager : std::shared_ptr<UIManager>{};
        } beforeDeletingNativeSubtrees:^(BottomSheetNativeViewDeletion *deletion) {
          [BottomSheetPresentationCoordinator retireNativeViewDeletion:deletion];
        }];
  });
}

- (void)invalidate
{
  _valid = false;
  dispatch_async(dispatch_get_main_queue(), ^{
    [self.observer invalidate];
    self.observer = nil;
  });
}
@end
