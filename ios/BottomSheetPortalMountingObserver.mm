#import "BottomSheetPortalMountingObserver.h"
#import <React/RCTComponentViewRegistry.h>
#import <React/RCTMountingManager.h>
#include <react/renderer/mounting/Differentiator.h>

using namespace facebook::react;
using namespace facebook::react::bottomsheet;

static NSHashTable<BottomSheetPortalMountingObserver *> *BottomSheetPortalObservers(void)
{
  NSCAssert(NSThread.isMainThread, @"Portal runtime installation must run on main");
  static NSHashTable<BottomSheetPortalMountingObserver *> *observers;
  static dispatch_once_t once;
  dispatch_once(&once, ^{ observers = [NSHashTable weakObjectsHashTable]; });
  return observers;
}

BottomSheetPortalMountingObserver *BottomSheetPortalObserverForView(UIView *view)
{
  BottomSheetPortalMountingObserver *selected = nil;
  for (BottomSheetPortalMountingObserver *observer in BottomSheetPortalObservers()) {
    if (![observer ownsNativeView:view]) continue;
    if (selected != nil) return nil;
    selected = observer;
  }
  return selected;
}

namespace {
struct SurfacePublication {
  RootShadowNode::Shared root;
  std::weak_ptr<const MountingCoordinator> coordinator;
  MountingTransaction::Number number = 0;
  MountingTransaction::Number inFlight = 0;
  BottomSheetMountedPortalContextGraph *__strong graph;
  NSHashTable<UIView *> *__strong owners = [NSHashTable weakObjectsHashTable];
};
struct AppliedRoot {
  RootShadowNode::Shared root;
  std::shared_ptr<const MountingCoordinator> coordinator;
};
AppliedRoot appliedRoot(const UIManager& manager, SurfaceId surface) {
  AppliedRoot result;
  manager.getShadowTreeRegistry().visit(surface, [&](const ShadowTree& tree) {
    result.coordinator = tree.getMountingCoordinator();
    result.root = result.coordinator->getBaseRevision().rootShadowNode;
  });
  return result;
}
} // namespace

@implementation BottomSheetPortalMountingObserver {
  __weak RCTMountingManager *_mountingManager;
  BottomSheetUIManagerProvider _uiManagerProvider;
  BottomSheetNativeSubtreeRetirement _retirementHandler;
  std::weak_ptr<UIManager> _epochManager;
  std::map<SurfaceId, SurfacePublication> _surfaces;
}

- (instancetype)initWithMountingManager:(RCTMountingManager *)mountingManager
                     uiManagerProvider:(BottomSheetUIManagerProvider)uiManagerProvider
          beforeDeletingNativeSubtrees:(BottomSheetNativeSubtreeRetirement)retirementHandler
{
  if (self = [super init]) {
    _mountingManager = mountingManager;
    _uiManagerProvider = [uiManagerProvider copy];
    _retirementHandler = [retirementHandler copy];
    [BottomSheetPortalObservers() addObject:self];
  }
  return self;
}

- (std::shared_ptr<UIManager>)currentManager
{
  NSCAssert(NSThread.isMainThread, @"Portal publication must run on main");
  auto current = _uiManagerProvider ? _uiManagerProvider() : nullptr;
  if (!_mountingManager) current.reset();
  if (current != _epochManager.lock() || !current) {
    _surfaces.clear();
    _epochManager = current;
  }
  return current;
}

- (void)willMount:(const MountingTransaction &)transaction
{
  auto manager = [self currentManager];
  if (!manager) return;
  auto current = appliedRoot(*manager, transaction.getSurfaceId());
  if (!current.coordinator) return;
  auto& data = _surfaces[transaction.getSurfaceId()];
  if (data.coordinator.lock() != current.coordinator) data = {};
  if (data.root && transaction.getNumber() <= data.number) return;
  if (data.inFlight == transaction.getNumber()) return;
  BottomSheetNativeViewDeletion *deletion = nil;
  if (data.root && !data.inFlight && transaction.getNumber() == data.number + 1) {
    deletion = [data.graph nativeDeletionForMutations:transaction.getMutations()];
  }
  data.coordinator = current.coordinator;
  data.inFlight = transaction.getNumber();
  // Block queries before a callback can reenter. Do not use the publication
  // reference after the callback, which may invalidate this runtime.
  if (deletion.deletedViews.count && _retirementHandler) _retirementHandler(deletion);
}

- (void)didMount:(const MountingTransaction &)transaction owner:(UIView *)owner
{
  auto manager = [self currentManager];
  if (!manager) return;
  auto current = appliedRoot(*manager, transaction.getSurfaceId());
  if (!current.root || !current.coordinator ||
      [_mountingManager.componentViewRegistry
          findComponentViewWithTag:static_cast<Tag>(owner.tag)] != owner) return;
  auto nodes = indexPortalShadowNodes(*current.root);
  if (!nodes || !nodes->contains(static_cast<Tag>(owner.tag))) return;
  auto& data = _surfaces[transaction.getSurfaceId()];
  bool sameCoordinator = data.coordinator.lock() == current.coordinator;
  if (sameCoordinator && data.root && transaction.getNumber() <= data.number) {
    [data.owners addObject:owner];
    return; // Duplicate own observers must not end a later in-flight mount.
  }
  if (data.inFlight && (!sameCoordinator || data.inFlight != transaction.getNumber())) return;
  bool bootstrap = !data.root || !sameCoordinator || transaction.getNumber() != data.number + 1;
  if (bootstrap) {
    data.graph = [[BottomSheetMountedPortalContextGraph alloc]
        initWithMountingManager:_mountingManager];
    auto empty = current.root->ShadowNode::clone(
        {.children = ShadowNode::emptySharedShadowNodeSharedList()});
    auto mutations = calculateShadowViewMutations(*empty, *current.root);
    [data.graph publishRoot:current.root mutations:mutations];
  } else {
    [data.graph publishRoot:current.root mutations:transaction.getMutations()];
  }
  data.root = current.root;
  data.coordinator = current.coordinator;
  data.number = transaction.getNumber();
  data.inFlight = 0;
  [data.owners addObject:owner];
}

- (void)withdrawOwner:(UIView *)owner
{
  [self currentManager];
  for (auto it = _surfaces.begin(); it != _surfaces.end();) {
    [it->second.owners removeObject:owner];
    if (it->second.owners.count == 0) it = _surfaces.erase(it);
    else ++it;
  }
}

- (BOOL)ownsNativeView:(UIView *)view
{
  return [self currentManager] &&
      [_mountingManager.componentViewRegistry
          findComponentViewWithTag:static_cast<Tag>(view.tag)] == view;
}

- (RootShadowNode::Shared)currentRootForSurface:(SurfaceId)surface
{
  auto manager = [self currentManager];
  if (!manager) return {};
  auto found = _surfaces.find(surface);
  if (found == _surfaces.end()) return {};
  auto& data = found->second;
  if (data.owners.count == 0) {
    _surfaces.erase(found);
    return {};
  }
  if (data.inFlight) return {};
  auto current = appliedRoot(*manager, surface);
  return current.root == data.root && current.coordinator == data.coordinator.lock()
      ? data.root : RootShadowNode::Shared{};
}

- (NSSet<UIView *> *)hiddenBranchesForSurface:(SurfaceId)surface
                              retainedPaths:(const std::vector<PortalRetainedPaths> &)requests
{
  auto root = [self currentRootForSurface:surface];
  if (!root) return nil;
  return [_surfaces.at(surface).graph hiddenBranchesForRoot:root retainedPaths:requests];
}

- (std::optional<PortalNativeContextPath>)contextPathForView:(UIView *)view
        root:(const RootShadowNode::Shared &)root
   hierarchy:(BottomSheetNativeHierarchySnapshot *)hierarchy
{
  if (!root || [self currentRootForSurface:root->getSurfaceId()] != root) return std::nullopt;
  return [_surfaces.at(root->getSurfaceId()).graph contextPathForView:view
      root:root hierarchy:hierarchy];
}

- (NSSet<UIView *> *)hiddenBranchesForRoot:(const RootShadowNode::Shared &)root
                           retainedPaths:(const std::vector<PortalRetainedPaths> &)requests
{
  if (!root || [self currentRootForSurface:root->getSurfaceId()] != root) return nil;
  return [_surfaces.at(root->getSurfaceId()).graph hiddenBranchesForRoot:root retainedPaths:requests];
}

- (NSSet<UIView *> *)hiddenBranchesForRoot:(const RootShadowNode::Shared &)root
                           retainedPaths:(const std::vector<PortalRetainedPaths> &)requests
                               hierarchy:(BottomSheetNativeHierarchySnapshot *)hierarchy
{
  if (!root || [self currentRootForSurface:root->getSurfaceId()] != root) return nil;
  return [_surfaces.at(root->getSurfaceId()).graph hiddenBranchesForRoot:root
      retainedPaths:requests hierarchy:hierarchy];
}

- (RootShadowNode::Shared)currentRootForOwner:(UIView *)owner
{
  if (![self ownsNativeView:owner]) return {};
  std::vector<SurfaceId> surfaces;
  for (const auto& [surface, data] : _surfaces) {
    if ([data.owners containsObject:owner]) surfaces.push_back(surface);
  }
  if (surfaces.size() != 1) return {};
  return [self currentRootForSurface:surfaces.front()];
}

- (void)invalidate
{
  NSCAssert(NSThread.isMainThread, @"Portal publication must run on main");
  [BottomSheetPortalObservers() removeObject:self];
  _uiManagerProvider = nil;
  _retirementHandler = nil;
  _epochManager.reset();
  _surfaces.clear();
}
@end
