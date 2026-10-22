#import "BottomSheetMountedPortalContextGraph.h"
#import "BottomSheetPortalNativeContainer.h"
#import <React/RCTComponentViewRegistry.h>
#import <React/RCTMountingManager.h>
#import <React/RCTViewComponentView.h>
#import <objc/runtime.h>
#include <algorithm>
#include <cstring>

using namespace facebook::react;
using namespace facebook::react::bottomsheet;

namespace {
struct MountedIdentity {
  __weak UIView<RCTComponentViewProtocol> *view;
  ShadowNodeFamily::Weak family;
};

template <typename Node>
ShadowNodeFamily::Weak familyIdentity(const Node& node, const RootShadowNode::Shared& root) {
  if constexpr (requires { node.getFamilyShared(); }) {
    return node.getFamilyShared();
  } else {
    // Older RN exposes only a reference. The applied root owns this family.
    return ShadowNodeFamily::Shared(root, &node.getFamily());
  }
}

template <typename Root>
void advanceFamilyIdentities(MountedIdentity& rootIdentity,
    std::map<Tag, MountedIdentity>& identities, const std::shared_ptr<const Root>& root,
    const PortalShadowNodeIndex& nodes) {
  if constexpr (!requires { root->getFamilyShared(); }) {
    // Compare while the previous root still keeps its families alive, then
    // advance only unchanged identities to the new root's lifetime.
    auto advance = [&](MountedIdentity& identity, const ShadowNode* node) {
      if (node && identity.family.lock().get() == &node->getFamily()) {
        identity.family = familyIdentity(*node, root);
      }
    };
    advance(rootIdentity, root.get());
    for (auto& [tag, identity] : identities) {
      auto node = nodes.find(tag);
      advance(identity, node != nodes.end() ? node->second : nullptr);
    }
  }
}

bool hasConcreteComponentDescriptor(UIView *view) {
  // RN's UIView category supplies the protocol to every UIKit view. Concrete
  // Fabric classes override this required provider; do not invoke its asserting
  // UIView default merely to distinguish a foreign component from native chrome.
  SEL selector = @selector(componentDescriptorProvider);
  Method concrete = class_getClassMethod(object_getClass(view), selector);
  Method fallback = class_getClassMethod(UIView.class, selector);
  return concrete && (!fallback ||
      method_getImplementation(concrete) != method_getImplementation(fallback));
}

template <typename Mutation>
Tag parentTag(const Mutation& mutation) {
  if constexpr (requires { mutation.parentTag; }) return mutation.parentTag;
  else return mutation.parentShadowView.tag; // RN 0.76 mounting representation.
}
} // namespace

@implementation BottomSheetMountedPortalContextGraph {
  __weak RCTMountingManager *_manager;
  std::shared_ptr<const RootShadowNode> _root;
  MountedIdentity _rootIdentity;
  PortalNativeMembers _members;
  std::map<Tag, MountedIdentity> _identities;
}

- (instancetype)initWithMountingManager:(RCTMountingManager *)manager
{
  if (self = [super init]) _manager = manager;
  return self;
}

- (void)publishRoot:(const std::shared_ptr<const facebook::react::RootShadowNode> &)root
          mutations:(const facebook::react::ShadowViewMutation::List &)mutations
{
  NSCAssert(NSThread.isMainThread, @"Native portal membership is main-thread only");
  if (!root || !_manager) {
    [self invalidate];
    return;
  }
  auto nodes = indexPortalShadowNodes(*root);
  if (!nodes) {
    [self invalidate];
    return;
  }
  if (_root && &_root->getFamily() != &root->getFamily()) [self invalidate];
  if (!_root) {
    _rootIdentity = {[_manager.componentViewRegistry findComponentViewWithTag:root->getTag()],
                     familyIdentity(*root, root)};
  }
  for (const auto& mutation : mutations) {
    switch (mutation.type) {
      case ShadowViewMutation::Create: {
        Tag tag = mutation.newChildShadowView.tag;
        _members.alive.insert(tag);
        auto node = nodes->find(tag);
        if (node != nodes->end()) {
          _identities[tag] = {
              [_manager.componentViewRegistry findComponentViewWithTag:tag],
              familyIdentity(*node->second, root)};
        }
        break;
      }
      case ShadowViewMutation::Delete: {
        Tag tag = mutation.oldChildShadowView.tag;
        _members.alive.erase(tag);
        _members.parents.erase(tag);
        _identities.erase(tag);
        break;
      }
      case ShadowViewMutation::Insert:
        _members.parents[mutation.newChildShadowView.tag] = parentTag(mutation);
        break;
      case ShadowViewMutation::Remove:
        _members.parents.erase(mutation.oldChildShadowView.tag);
        break;
      case ShadowViewMutation::Update:
        break;
    }
  }
  advanceFamilyIdentities(_rootIdentity, _identities, root, *nodes);
  _root = root;
}

- (UIView<RCTComponentViewProtocol> *)mounted:(Tag)tag
{
  return [_manager.componentViewRegistry findComponentViewWithTag:tag];
}

- (Tag)registeredParentOf:(UIView *)view hierarchy:(BottomSheetNativeHierarchySnapshot *)hierarchy
{
  for (UIView *parent = [hierarchy parentOfView:view]; parent; parent = [hierarchy parentOfView:parent]) {
    Tag tag = static_cast<Tag>([hierarchy tagOfView:parent]);
    if ([self mounted:tag] == parent) return tag;
    if (hasConcreteComponentDescriptor(parent)) return 0;
    if ([parent conformsToProtocol:@protocol(BottomSheetPortalNativeContainer)]) {
      UIView *owner = ((id<BottomSheetPortalNativeContainer>)parent).portalNativeOwner;
      Tag ownerTag = static_cast<Tag>([hierarchy tagOfView:owner]);
      return owner && [self mounted:ownerTag] == owner ? ownerTag : 0;
    }
    UIView *modalOwner = [hierarchy modalOwnerOfContainer:parent];
    if (modalOwner) {
      Tag ownerTag = static_cast<Tag>([hierarchy tagOfView:modalOwner]);
      return [self mounted:ownerTag] == modalOwner ? ownerTag : 0;
    }
  }
  return 0;
}

- (BOOL)validateBranch:(const ShadowNode &)node
         retainedPath:(const std::set<Tag> &)retainedPath
            hierarchy:(BottomSheetNativeHierarchySnapshot *)hierarchy
{
  Tag tag = node.getTag();
  UIView *view = [self mounted:tag];
  if (&node == _root.get()) {
    if (!view || _rootIdentity.view != view ||
        _rootIdentity.family.lock().get() != &node.getFamily()) return NO;
  } else if (_members.alive.contains(tag)) {
    auto identity = _identities.find(tag);
    if (!view || identity == _identities.end() || identity->second.view != view ||
        identity->second.family.lock().get() != &node.getFamily()) return NO;
    auto parent = _members.parents.find(tag);
    if (parent == _members.parents.end()) return NO;
    if (parent->second != [self registeredParentOf:view hierarchy:hierarchy]) {
      // Navigation may detach inactive background without changing Fabric membership.
      // Retained corridors and attached branches must still match their applied edges.
      if (retainedPath.contains(tag) || [hierarchy parentOfView:view] != nil) return NO;
    }
  } else if (view) {
    return NO;
  }
  for (const auto& child : node.getChildren()) {
    if (![self validateBranch:*child retainedPath:retainedPath hierarchy:hierarchy]) return NO;
  }
  return hierarchy.isValid;
}

- (BOOL)addManagedContainer:(UIView *)container
                  parentTag:(Tag)parent
                      graph:(const PortalContextGraph &)graph
                   retained:(const std::set<Tag> &)retained
                  hierarchy:(BottomSheetNativeHierarchySnapshot *)hierarchy
                 toBranches:(NSMutableSet<UIView *> *)branches
{
  UIView *owner = [self mounted:parent];
  UIView *containerOwner = [container conformsToProtocol:@protocol(BottomSheetPortalNativeContainer)]
      ? ((id<BottomSheetPortalNativeContainer>)container).portalNativeOwner
      : [hierarchy modalOwnerOfContainer:container];
  if (containerOwner != owner) return NO;
  NSMutableSet<UIView *> *keptNativePath = [NSMutableSet new];
  NSMutableArray<UIView *> *pending = [NSMutableArray arrayWithObject:container];
  while (pending.count) {
    UIView *view = pending.lastObject;
    [pending removeLastObject];
    Tag tag = static_cast<Tag>([hierarchy tagOfView:view]);
    if ([self mounted:tag] == view) {
      auto node = graph.nodes.find(tag);
      auto edge = _members.parents.find(tag);
      if (node == graph.nodes.end() || edge == _members.parents.end() ||
          edge->second != parent) return NO;
      std::optional<Tag> logicalChild;
      for (const auto& [ancestor, index] : node->second->getFamily().getAncestors(*_root)) {
        if (ancestor.get().getTag() == parent) {
          logicalChild = ancestor.get().getChildren()[index]->getTag();
          break;
        }
      }
      if (!logicalChild) return NO;
      if (retained.contains(*logicalChild)) {
        UIView *outside = [hierarchy parentOfView:container];
        for (UIView *path = view; path && path != outside; path = [hierarchy parentOfView:path]) {
          [keptNativePath addObject:path];
        }
      }
      continue; // Whole registered frontiers are projected at their logical edges.
    }
    if (hasConcreteComponentDescriptor(view)) return NO;
    if ([view conformsToProtocol:@protocol(BottomSheetPortalNativeContainer)] &&
        ((id<BottomSheetPortalNativeContainer>)view).portalNativeOwner != owner) return NO;
    UIView *modalOwner = [hierarchy modalOwnerOfContainer:view];
    if (modalOwner && modalOwner != owner) return NO;
    [pending addObjectsFromArray:[hierarchy childrenOfView:view] ?: @[]];
  }

  [pending addObject:container];
  while (pending.count) {
    UIView *view = pending.lastObject;
    [pending removeLastObject];
    if (![keptNativePath containsObject:view]) {
      [branches addObject:view];
    } else if ([self mounted:static_cast<Tag>([hierarchy tagOfView:view])] != view) {
      [pending addObjectsFromArray:[hierarchy childrenOfView:view] ?: @[]];
    }
  }
  return hierarchy.isValid;
}

- (NSSet<UIView *> *)hiddenBranchesForRoot:
    (const std::shared_ptr<const facebook::react::RootShadowNode> &)root
                                    retainedPaths:
    (const std::vector<facebook::react::bottomsheet::PortalRetainedPaths> &)requests
{
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];
  auto branches = [self hiddenBranchesForRoot:root retainedPaths:requests hierarchy:hierarchy];
  return [hierarchy validate] ? branches : nil;
}

- (NSSet<UIView *> *)hiddenBranchesForRoot:(const RootShadowNode::Shared &)root
                           retainedPaths:(const std::vector<PortalRetainedPaths> &)requests
                               hierarchy:(BottomSheetNativeHierarchySnapshot *)hierarchy
{
  NSCAssert(NSThread.isMainThread, @"Native portal projection is main-thread only");
  if (!root || root != _root || !_manager) return nil;
  auto graph = discoverPortalContexts(*root);
  if (!graph) return nil;
  std::set<Tag> scopes;
  for (const auto& request : requests) {
    if (!request.host) return nil;
    auto host = std::find_if(graph->hostContexts.begin(), graph->hostContexts.end(),
        [&](const auto& entry) {
          return &graph->nodes.at(entry.first)->getFamily() == request.host;
        });
    if (host == graph->hostContexts.end()) return nil;
    auto view = [self mounted:host->first];
    if (![view isKindOfClass:RCTViewComponentView.class] ||
        ![((RCTViewComponentView *)view).nativeId
            isEqualToString:[NSString stringWithUTF8String:PortalHostNativeID]]) return nil;
    scopes.insert(host->second);
  }
  auto projection = projectPortalIsolation(*graph, _members, requests);
  if (!projection) return nil;
  std::set<Tag> retainedPath;
  for (const auto& [parent, children] : projection->retainedChildren) {
    retainedPath.insert(parent);
    retainedPath.insert(children.begin(), children.end());
  }
  for (Tag scope : scopes) {
    if (![self validateBranch:*graph->contexts.at(scope).boundary
                retainedPath:retainedPath hierarchy:hierarchy]) return nil;
  }
  NSMutableSet<UIView *> *branches = [NSMutableSet new];
  for (Tag tag : projection->hiddenBranches) {
    UIView *view = [self mounted:tag];
    if (!view) return nil;
    [branches addObject:view];
  }
  for (const auto& [parent, retained] : projection->retainedChildren) {
    UIView *view = [self mounted:parent];
    if (!view) continue; // A flattened logical parent has no native-only children.
    NSMutableArray<UIView *> *children = [[hierarchy childrenOfView:view] mutableCopy];
    if (std::strcmp(graph->nodes.at(parent)->getComponentName(), "ModalHostView") == 0) {
      // Stock Modal children mount under its controller's view. Applied edges
      // and the checked public owner discover that container without loading it.
      for (const auto& [childTag, expectedParent] : _members.parents) {
        if (expectedParent != parent) continue;
        UIView *container = [hierarchy parentOfView:[self mounted:childTag]];
        if (!container || [hierarchy modalOwnerOfContainer:container] != view) return nil;
        if ([children indexOfObjectIdenticalTo:container] == NSNotFound) [children addObject:container];
      }
    }
    for (UIView *child in children) {
      Tag tag = static_cast<Tag>([hierarchy tagOfView:child]);
      if (graph->nodes.contains(tag) && [self mounted:tag] == child) continue;
      if ([child conformsToProtocol:@protocol(BottomSheetPortalNativeContainer)] ||
          [hierarchy modalOwnerOfContainer:child] == view) {
        if (![self addManagedContainer:child parentTag:parent graph:*graph
                             retained:retained hierarchy:hierarchy toBranches:branches]) return nil;
        continue;
      }
      NSMutableArray<UIView *> *pending = [NSMutableArray arrayWithObject:child];
      while (pending.count) {
        UIView *next = pending.lastObject;
        [pending removeLastObject];
        Tag nextTag = static_cast<Tag>([hierarchy tagOfView:next]);
        if ([self mounted:nextTag] == next || hasConcreteComponentDescriptor(next)) {
          return nil; // Opaque native ownership must be adapted explicitly.
        }
        [pending addObjectsFromArray:[hierarchy childrenOfView:next] ?: @[]];
      }
      [branches addObject:child];
    }
  }
  return hierarchy.isValid ? branches : nil;
}

- (void)invalidate
{
  NSCAssert(NSThread.isMainThread, @"Native portal membership is main-thread only");
  _root.reset();
  _rootIdentity = {};
  _members = {};
  _identities.clear();
}

- (std::optional<PortalNativeContextPath>)contextPathForView:(UIView *)view
        root:(const RootShadowNode::Shared &)root
   hierarchy:(BottomSheetNativeHierarchySnapshot *)hierarchy
{
  NSCAssert(NSThread.isMainThread, @"Native input ancestry is main-thread only");
  if (!root || root != _root || !_manager) return std::nullopt;
  auto graph = discoverPortalContexts(*root);
  if (!graph) return std::nullopt;
  size_t depth = 0;
  NSHashTable<UIView *> *seen = [NSHashTable hashTableWithOptions:NSPointerFunctionsObjectPointerPersonality];
  for (UIView *current = view; current;
       depth++) {
    if ([seen containsObject:current]) return std::nullopt;
    [seen addObject:current];
    Tag tag = static_cast<Tag>([hierarchy tagOfView:current]);
    if (!hierarchy.isValid) return std::nullopt;
    auto node = graph->nodes.find(tag);
    if (node == graph->nodes.end() || [self mounted:tag] != current) {
      UIView *modalOwner = [hierarchy modalOwnerOfContainer:current];
      if (modalOwner) {
        Tag ownerTag = static_cast<Tag>([hierarchy tagOfView:modalOwner]);
        if (!hierarchy.isValid) return std::nullopt;
        if (!graph->nodes.contains(ownerTag) || [self mounted:ownerTag] != modalOwner) {
          // Another source may own this controller. Its component's physical
          // ancestors cannot establish a context in this publication.
          return PortalNativeContextPath{{}, depth};
        }
        current = modalOwner; // Family and applied edge are checked below on the next step.
      } else {
        current = [hierarchy parentOfView:current];
      }
      continue;
    }
    if (node->second == root.get()) {
      if (_rootIdentity.view != current ||
          _rootIdentity.family.lock().get() != &node->second->getFamily()) return std::nullopt;
    } else {
      auto identity = _identities.find(tag);
      auto parent = _members.parents.find(tag);
      if (!_members.alive.contains(tag) || identity == _identities.end() ||
          identity->second.view != current ||
          identity->second.family.lock().get() != &node->second->getFamily() ||
          parent == _members.parents.end() ||
          parent->second != [self registeredParentOf:current hierarchy:hierarchy]) return std::nullopt;
    }
    auto contexts = graph->contextPathForNode(*node->second, *root);
    return hierarchy.isValid ? std::optional<PortalNativeContextPath>{{contexts, depth}}
                             : std::nullopt;
  }
  return hierarchy.isValid ? std::optional<PortalNativeContextPath>{{{}, depth}}
                           : std::nullopt;
}

- (BottomSheetNativeViewDeletion *)nativeDeletionForMutations:(const ShadowViewMutation::List &)mutations
{
  NSCAssert(NSThread.isMainThread, @"Native portal retirement is main-thread only");
  if (!_root || !_manager) return nil;
  auto nodes = indexPortalShadowNodes(*_root);
  if (!nodes) return nil;
  NSMutableSet<UIView *> *deleted = [NSMutableSet new];
  for (const auto& mutation : mutations) {
    if (mutation.type != ShadowViewMutation::Delete) continue;
    Tag tag = mutation.oldChildShadowView.tag;
    auto identity = _identities.find(tag);
    auto node = nodes->find(tag);
    UIView *view = [self mounted:tag];
    if (!view || !_members.alive.contains(tag) || identity == _identities.end() ||
        identity->second.view != view || node == nodes->end() ||
        identity->second.family.lock().get() != &node->second->getFamily()) return nil;
    [deleted addObject:view];
  }
  NSMutableSet<UIView *> *survivors = [NSMutableSet new];
  NSMutableArray<UIView *> *pending = [NSMutableArray arrayWithArray:deleted.allObjects];
  while (pending.count) {
    UIView *view = pending.lastObject;
    [pending removeLastObject];
    if (![deleted containsObject:view] &&
        ([self mounted:static_cast<Tag>(view.tag)] == view ||
         hasConcreteComponentDescriptor(view))) {
      [survivors addObject:view];
      continue; // A surviving registered subtree keeps all of its native children.
    }
    [pending addObjectsFromArray:view.subviews];
  }
  return [[BottomSheetNativeViewDeletion alloc] initWithDeletedViews:deleted
      survivingRegisteredViews:survivors];
}

@end
