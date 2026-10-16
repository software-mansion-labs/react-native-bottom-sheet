#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

#include "BottomSheetMountedPortalContextGraph.h"
#include <react/renderer/mounting/MountingTransaction.h>
#include <react/renderer/uimanager/UIManager.h>

NS_ASSUME_NONNULL_BEGIN

// The presenter adapter supplies the current scheduler's manager. The observer
// never owns a UIManager or replaces its delegate. All methods are main-thread.
typedef std::shared_ptr<facebook::react::UIManager> (^BottomSheetUIManagerProvider)(void);
typedef void (^BottomSheetNativeSubtreeRetirement)(BottomSheetNativeViewDeletion *deletion);

@interface BottomSheetPortalMountingObserver : NSObject

- (instancetype)initWithMountingManager:(RCTMountingManager *)mountingManager
                     uiManagerProvider:(BottomSheetUIManagerProvider)uiManagerProvider
          beforeDeletingNativeSubtrees:(nullable BottomSheetNativeSubtreeRetirement)retirementHandler;
- (instancetype)init NS_UNAVAILABLE;

- (void)willMount:(const facebook::react::MountingTransaction &)transaction;
- (void)didMount:(const facebook::react::MountingTransaction &)transaction owner:(UIView *)owner;
- (void)withdrawOwner:(UIView *)owner;
- (BOOL)ownsNativeView:(UIView *)view;

// The caller additionally verifies attachment to the target input/isolation
// window. No root is returned during mount or after a runtime/coordinator change.
- (facebook::react::RootShadowNode::Shared)currentRootForSurface:(facebook::react::SurfaceId)surface;
- (facebook::react::RootShadowNode::Shared)currentRootForOwner:(UIView *)owner;
- (std::optional<facebook::react::bottomsheet::PortalNativeContextPath>)contextPathForView:
    (UIView *)view
        root:(const facebook::react::RootShadowNode::Shared &)root
   hierarchy:(BottomSheetNativeHierarchySnapshot *)hierarchy;
- (nullable NSSet<UIView *> *)hiddenBranchesForSurface:(facebook::react::SurfaceId)surface
                                      retainedPaths:
    (const std::vector<facebook::react::bottomsheet::PortalRetainedPaths> &)requests;
- (nullable NSSet<UIView *> *)hiddenBranchesForRoot:
    (const facebook::react::RootShadowNode::Shared &)root
                                     retainedPaths:
    (const std::vector<facebook::react::bottomsheet::PortalRetainedPaths> &)requests
                                         hierarchy:(BottomSheetNativeHierarchySnapshot *)hierarchy;
- (nullable NSSet<UIView *> *)hiddenBranchesForRoot:
    (const facebook::react::RootShadowNode::Shared &)root
                                     retainedPaths:
    (const std::vector<facebook::react::bottomsheet::PortalRetainedPaths> &)requests;
- (void)invalidate;

@end

// Resolves the runtime from actual native registry ownership, including apps
// with more than one React instance. Ambiguous ownership returns nil.
FOUNDATION_EXPORT BottomSheetPortalMountingObserver *_Nullable
BottomSheetPortalObserverForView(UIView *view);

NS_ASSUME_NONNULL_END
