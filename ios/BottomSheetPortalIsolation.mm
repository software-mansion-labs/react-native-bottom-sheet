#import "BottomSheetPortalIsolation.h"
#import "BottomSheetPortalMountingObserver.h"
#import "BottomSheetPortalNativeContainer.h"
#import <objc/runtime.h>

using namespace facebook::react;
using namespace facebook::react::bottomsheet;

namespace {
struct AppliedPortalRoot {
  RootShadowNode::Shared root;
  PortalContextGraph graph;
  BottomSheetPortalMountingObserver *__strong observer;
  std::map<Tag, std::vector<const ShadowNodeFamily *>> retainedByHost;
};
struct ContextPresentation {
  BottomSheetPortalPresentation *__strong presentation;
  const ShadowNode *node;
  Tag host;
};
using ContextKey = std::pair<const RootShadowNode *, Tag>;

struct PortalQuery {
  std::map<const RootShadowNode *, AppliedPortalRoot> roots;
  std::map<ContextKey, std::vector<ContextPresentation>> groups;
};

std::optional<PortalQuery> collectPortalPresentations(
    NSArray<BottomSheetPortalPresentation *> *presentations,
    BottomSheetNativeHierarchySnapshot *hierarchy) {
  PortalQuery query;
  NSMutableSet<BottomSheetPresentationIdentity *> *identities = [NSMutableSet new];
  NSMutableSet<UIView *> *owners = [NSMutableSet new];
  for (BottomSheetPortalPresentation *presentation in presentations) {
    if (!presentation.isActive || !presentation.isEligible) continue;
    if ([identities containsObject:presentation.identity] ||
        [owners containsObject:presentation.owner]) return std::nullopt;
    [identities addObject:presentation.identity];
    [owners addObject:presentation.owner];
    Tag ownerTag = static_cast<Tag>([hierarchy tagOfView:presentation.owner]);
    if (!hierarchy.isValid) return std::nullopt;
    auto root = [presentation.observer currentRootForOwner:presentation.owner];
    if (!root) return std::nullopt;
    auto found = query.roots.find(root.get());
    if (found == query.roots.end()) {
      auto graph = discoverPortalContexts(*root);
      if (!graph) return std::nullopt;
      found = query.roots.emplace(root.get(), AppliedPortalRoot{
          root, std::move(*graph), presentation.observer, {}}).first;
    } else if (found->second.observer != presentation.observer) {
      return std::nullopt;
    }
    auto& graph = found->second.graph;
    auto node = graph.nodes.find(ownerTag);
    if (node == graph.nodes.end()) return std::nullopt;
    auto host = graph.hostForPresentation(node->second->getFamily(), *root);
    if (!host) return std::nullopt;
    query.groups[{root.get(), graph.hostContexts.at(*host)}].push_back(
        {presentation, node->second, *host});
  }
  return query;
}

UIView *nativeActionView(id origin) {
  NSHashTable *seen = [NSHashTable hashTableWithOptions:NSPointerFunctionsObjectPointerPersonality];
  while (origin) {
    if ([seen containsObject:origin]) return nil;
    [seen addObject:origin];
    if ([origin isKindOfClass:UIView.class]) return origin;
    if (![origin isKindOfClass:UIAccessibilityElement.class]) return nil;
    SEL container = @selector(accessibilityContainer);
    Method actual = class_getInstanceMethod(object_getClass(origin), container);
    Method standard = class_getInstanceMethod(UIAccessibilityElement.class, container);
    if (!actual || method_getImplementation(actual) != method_getImplementation(standard)) return nil;
    origin = ((UIAccessibilityElement *)origin).accessibilityContainer;
  }
  return nil;
}

NSSet<UIView *> *nativeCut(const AppliedPortalRoot& publication, BottomSheetNativeHierarchySnapshot *hierarchy) {
  std::vector<PortalRetainedPaths> requests;
  for (const auto& [host, retained] : publication.retainedByHost) {
    requests.push_back({&publication.graph.nodes.at(host)->getFamily(), retained});
  }
  return [publication.observer hiddenBranchesForRoot:publication.root retainedPaths:requests hierarchy:hierarchy];
}

NSSet<UIView *> *composeNativeCuts(NSSet<UIView *> *branches, NSSet<UIView *> *retainedOwners,
                                  BottomSheetNativeHierarchySnapshot *hierarchy) {
  NSMutableSet<UIView *> *result = [NSMutableSet new];
  for (UIView *branch in branches) {
    NSMutableSet<UIView *> *retainedPath = [NSMutableSet new];
    for (UIView *owner in retainedOwners) {
      NSMutableArray<UIView *> *path = [NSMutableArray new];
      UIView *current = owner;
      while (current && current != branch) {
        [path addObject:current];
        current = [hierarchy parentOfView:current];
      }
      if (current == branch) {
        [retainedPath addObjectsFromArray:path];
        [retainedPath addObject:branch];
      }
    }
    NSMutableArray<UIView *> *pending = [NSMutableArray arrayWithObject:branch];
    while (pending.count) {
      UIView *view = pending.lastObject;
      [pending removeLastObject];
      if (![retainedPath containsObject:view]) {
        [result addObject:view];
      } else if (![retainedOwners containsObject:view]) {
        [pending addObjectsFromArray:[hierarchy childrenOfView:view] ?: @[]];
      }
    }
  }
  return hierarchy.isValid ? result : nil;
}
} // namespace

@implementation BottomSheetPortalPresentation
+ (instancetype)presentationForIdentity:(BottomSheetPresentationIdentity *)identity
                                anchor:(UIView *)anchor active:(BOOL)active eligible:(BOOL)eligible
{
  UIView *owner = [anchor conformsToProtocol:@protocol(BottomSheetPortalNativeContainer)]
      ? ((UIView<BottomSheetPortalNativeContainer> *)anchor).portalNativeOwner : anchor;
  if (!owner || owner.window != anchor.window) return nil;
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];
  [hierarchy tagOfView:owner];
  if (!hierarchy.isValid) return nil;
  auto observer = BottomSheetPortalObserverForView(owner);
  if (!observer || ![hierarchy validate]) return nil;
  return [[self alloc] initWithIdentity:identity owner:owner observer:observer
                               active:active eligible:eligible];
}

- (instancetype)initWithIdentity:(BottomSheetPresentationIdentity *)identity
                           owner:(UIView *)owner
                        observer:(BottomSheetPortalMountingObserver *)observer
                          active:(BOOL)active
                        eligible:(BOOL)eligible
{
  if (self = [super init]) {
    _identity = identity;
    _owner = owner;
    _observer = observer;
    _active = active;
    _eligible = eligible;
  }
  return self;
}
@end

@interface BottomSheetPortalIsolation ()
- (instancetype)initWithHiddenBranches:(NSSet<UIView *> *)hiddenBranches
                                   tops:(NSSet<BottomSheetPresentationIdentity *> *)tops;
+ (instancetype)projectionFromQuery:(PortalQuery &)query
                           hierarchy:(BottomSheetNativeHierarchySnapshot *)hierarchy
                       orderProvider:(BottomSheetPresentationOrderProvider)orderProvider;
@end

@implementation BottomSheetPortalIsolation
+ (BottomSheetPresentationEscapeRoute)escapeRouteForCallerIdentity:
    (BottomSheetPresentationIdentity *)callerIdentity
                                                    actionOrigin:(id)origin
                                                   presentations:
    (NSArray<BottomSheetPortalPresentation *> *)presentations
                                                   orderProvider:
    (BottomSheetPresentationOrderProvider)orderProvider
{
  NSCAssert(NSThread.isMainThread, @"Portal input selection must run on main");
  presentations = [presentations copy];
  BottomSheetPortalPresentation *caller = nil;
  for (BottomSheetPortalPresentation *presentation in presentations) {
    if (presentation.identity == callerIdentity) caller = presentation;
  }
  if (!caller.isEligible) {
    return BottomSheetPresentationEscapeRoutePassThrough;
  }
  UIWindow *expectedWindow = caller.owner.window;
  auto callerRoot = [caller.observer currentRootForOwner:caller.owner];
  if (!callerRoot) return BottomSheetPresentationEscapeRoutePassThrough;
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];
  auto query = collectPortalPresentations(presentations, hierarchy);
  if (!query) return BottomSheetPresentationEscapeRoutePassThrough;
  auto& roots = query->roots;
  auto& groups = query->groups;
  std::optional<ContextKey> selected;
  for (const auto& [context, members] : groups) {
    for (const auto& member : members) {
      if (member.presentation.owner.window != expectedWindow) {
        return BottomSheetPresentationEscapeRoutePassThrough;
      }
    }
  }
  auto isolation = [self projectionFromQuery:*query hierarchy:hierarchy orderProvider:orderProvider];
  if (!isolation) return BottomSheetPresentationEscapeRoutePassThrough;
  UIView *originView = nativeActionView(origin);
  if (!originView || (originView != expectedWindow && originView.window != expectedWindow)) {
    return BottomSheetPresentationEscapeRoutePassThrough;
  }
  std::optional<std::pair<size_t, size_t>> best;
  bool ambiguous = false;
  for (const auto& [key, publication] : roots) {
    auto path = [publication.observer contextPathForView:originView
        root:publication.root hierarchy:hierarchy];
    if (!path) return BottomSheetPresentationEscapeRoutePassThrough;
    for (size_t index = 0; index < path->contexts.size(); index++) {
      ContextKey context{key, path->contexts[index]};
      if (!groups.contains(context)) continue;
      std::pair<size_t, size_t> rank{path->nativeDepth, index};
      if (!best || rank < *best) {
        best = rank;
        selected = context;
        ambiguous = false;
      } else if (rank == *best && context != *selected) {
        ambiguous = true;
      }
    }
  }
  if (!selected || ambiguous || caller.owner.window != expectedWindow ||
      [caller.observer currentRootForOwner:caller.owner] != callerRoot) {
    return BottomSheetPresentationEscapeRoutePassThrough;
  }
  for (const auto& [context, members] : groups) {
    for (const auto& member : members) {
      if ([member.presentation.observer currentRootForOwner:member.presentation.owner] !=
          roots.at(context.first).root) {
        return BottomSheetPresentationEscapeRoutePassThrough;
      }
      if (member.presentation.owner.window != expectedWindow) {
        return BottomSheetPresentationEscapeRoutePassThrough;
      }
    }
  }
  if (nativeActionView(origin) != originView || ![hierarchy validate]) {
    return BottomSheetPresentationEscapeRoutePassThrough;
  }
  for (const auto& member : groups.at(*selected)) {
    if ([isolation.topPresentationIdentities containsObject:member.presentation.identity]) {
      return [BottomSheetPresentationEscapeResolver routeForCallerIdentity:callerIdentity
          topPresentationIdentity:member.presentation.identity];
    }
  }
  return BottomSheetPresentationEscapeRoutePassThrough;
}

- (instancetype)initWithHiddenBranches:(NSSet<UIView *> *)hiddenBranches
                                   tops:(NSSet<BottomSheetPresentationIdentity *> *)tops
{
  if (self = [super init]) {
    _hiddenBranches = [hiddenBranches copy];
    _topPresentationIdentities = [tops copy];
  }
  return self;
}

+ (instancetype)projectionFromPresentations:(NSArray<BottomSheetPortalPresentation *> *)presentations
                              orderProvider:(BottomSheetPresentationOrderProvider)orderProvider
{
  NSCAssert(NSThread.isMainThread, @"Portal isolation must run on main");
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];
  if (!hierarchy.isValid) return nil;
  auto query = collectPortalPresentations(presentations, hierarchy);
  return query ? [self projectionFromQuery:*query hierarchy:hierarchy orderProvider:orderProvider] : nil;
}

+ (instancetype)projectionFromQuery:(PortalQuery &)query
                           hierarchy:(BottomSheetNativeHierarchySnapshot *)hierarchy
                       orderProvider:(BottomSheetPresentationOrderProvider)orderProvider
{
  auto& roots = query.roots;
  auto& groups = query.groups;

  NSMutableSet<BottomSheetPresentationIdentity *> *tops = [NSMutableSet new];
  NSMutableSet<UIView *> *retainedOwners = [NSMutableSet new];
  for (const auto& [context, members] : groups) {
    NSMutableArray<BottomSheetPresentationCandidate *> *candidates = [NSMutableArray new];
    for (const auto& member : members) {
      [candidates addObject:[[BottomSheetPresentationCandidate alloc]
          initWithIdentity:member.presentation.identity active:YES eligible:YES]];
    }
    auto decision = [BottomSheetPresentationResolver ownershipFromCandidates:candidates
                                                             orderProvider:orderProvider];
    if (decision.topPresentationIdentity) [tops addObject:decision.topPresentationIdentity];
    auto& retained = roots.at(context.first).retainedByHost;
    for (const auto& member : members) {
      if ([decision.retainedPresentationIdentities containsObject:member.presentation.identity]) {
        retained[member.host].push_back(&member.node->getFamily());
        [retainedOwners addObject:member.presentation.owner];
      }
    }
  }

  // An order provider can reenter native lifecycle. Every source, including a
  // covered presentation, must still belong to this exact completed root.
  auto sourcesAreCurrent = [&] {
    for (const auto& [context, members] : groups) {
      for (const auto& member : members) {
        if ([member.presentation.observer currentRootForOwner:member.presentation.owner] !=
            roots.at(context.first).root) return false;
      }
    }
    return true;
  };
  if (!sourcesAreCurrent()) return nil;
  NSMutableSet<UIView *> *hiddenBranches = [NSMutableSet new];
  for (const auto& [key, publication] : roots) {
    auto branches = nativeCut(publication, hierarchy);
    if (!branches) return nil;
    [hiddenBranches unionSet:branches];
  }
  auto composed = composeNativeCuts(hiddenBranches, retainedOwners, hierarchy);
  if (!composed || !sourcesAreCurrent()) return nil;
  auto result = [[self alloc] initWithHiddenBranches:composed tops:tops];
  return [hierarchy validate] ? result : nil;
}
@end
