#pragma once

#import "../../../../ios/BottomSheetPortalMountingObserver.h"

namespace facebook::react::bottomsheet::tests {
inline NSSet<UIView *> *hiddenBranches(id publication, const RootShadowNode::Shared& root,
                                     const std::vector<PortalRetainedPaths>& requests) {
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];
  auto branches = [publication hiddenBranchesForRoot:root retainedPaths:requests hierarchy:hierarchy];
  return [hierarchy validate] ? branches : nil;
}

inline NSSet<UIView *> *hiddenBranches(BottomSheetPortalMountingObserver *observer,
                                     SurfaceId surface,
                                     const std::vector<PortalRetainedPaths>& requests) {
  return hiddenBranches(observer, [observer currentRootForSurface:surface], requests);
}
} // namespace facebook::react::bottomsheet::tests
