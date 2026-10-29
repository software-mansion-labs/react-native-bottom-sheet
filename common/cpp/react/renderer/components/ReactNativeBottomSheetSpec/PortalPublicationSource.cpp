#include "PortalPublicationSource.h"

#ifdef ANDROID
#include "PortalContextGraph.h"
#include "ShadowNodes.h"
#include <react/renderer/mounting/Differentiator.h>
#include <algorithm>
#include <atomic>
#include <stdexcept>
#include <unordered_set>

namespace facebook::react::bottomsheet {
namespace {
std::atomic<uint64_t> nextSource{0};

bool hasUniqueTags(const ShadowNode& node, std::unordered_set<Tag>& tags) {
  if (!tags.insert(node.getTag()).second) return false;
  for (const auto& child : node.getChildren()) {
    if (!hasUniqueTags(*child, tags)) return false;
  }
  return true;
}

template <typename Mutation>
Tag parentTag(const Mutation& mutation) {
  if constexpr (requires { mutation.parentTag; }) return mutation.parentTag;
  else return mutation.parentShadowView.tag; // RN 0.76 mounting representation.
}

std::shared_ptr<const ShadowNode> stampPublication(
    const std::shared_ptr<const ShadowNode>& node, const std::string& id) {
  using Children = std::vector<std::shared_ptr<const ShadowNode>>;
  std::shared_ptr<Children> children;
  for (size_t i = 0; i < node->getChildren().size(); ++i) {
    const auto& child = node->getChildren()[i];
    auto stamped = stampPublication(child, id);
    if (stamped == child) continue;
    if (!children) children = std::make_shared<Children>(node->getChildren());
    (*children)[i] = std::move(stamped);
  }
  State::Shared state;
  if (auto sheet = dynamic_cast<const BottomSheetViewShadowNode*>(node.get())) {
    const auto& previous = static_cast<const ConcreteState<BottomSheetViewState>&>(*sheet->getState());
    auto data = std::make_shared<BottomSheetViewState>(previous.getData());
    data->portalPublicationId = id;
    state = std::make_shared<const BottomSheetViewShadowNode::ConcreteState>(data, previous);
  }
  if (!children && !state) return node;
  // Clone shared ancestors once. RN 0.76 otherwise adopts previously stamped
  // siblings again and replaces their State with the family's older State.
  return node->clone({.children = children, .state = state});
}
}

PortalPublicationSource::PortalPublicationSource(
    const std::shared_ptr<UIManager>& manager)
    : manager_(manager), prefix_(std::to_string(++nextSource) + ":") {
  if (!manager) throw std::invalid_argument("Missing Fabric UIManager");
  manager->registerCommitHook(*this);
}

PortalPublicationSource::~PortalPublicationSource() {
  if (auto manager = manager_.lock()) manager->unregisterCommitHook(*this);
}

void PortalPublicationSource::bootstrap() {
  auto manager = manager_.lock();
  if (!manager) return;
  std::vector<SurfaceId> surfaces;
  manager->getShadowTreeRegistry().enumerate([&](const ShadowTree& tree, bool&) {
    surfaces.push_back(tree.getSurfaceId());
  });
  for (auto surface : surfaces) {
    manager->getShadowTreeRegistry().visit(surface, [](const ShadowTree& tree) {
      tree.commit([](const RootShadowNode& root) {
        return std::static_pointer_cast<RootShadowNode>(root.ShadowNode::clone({}));
      }, {});
    });
  }
}

void PortalPublicationSource::rememberFinalRoot(const RootShadowNode::Shared& root) const {
  auto index = indexPortalShadowNodes(*root);
  if (!index) return;
  std::lock_guard lock(mutex_);
  std::erase_if(finalRoots_, [](const auto& entry) { return entry.second.state.expired(); });
  for (const auto& [tag, node] : *index) {
    auto state = node->getState();
    auto concrete = dynamic_cast<const BottomSheetViewShadowNode::ConcreteState*>(state.get());
    if (!concrete || !concrete->getData().portalPublicationId.starts_with(prefix_)) continue;
    auto [entry, inserted] = finalRoots_.try_emplace(concrete->portalStateId(), FinalRoot{state, root});
    if (!inserted && (entry->second.root.owner_before(root) || root.owner_before(entry->second.root))) {
      entry->second.ambiguous = true;
    }
  }
}

State::Shared PortalPublicationSource::stateForToken(
    SurfaceId surface, const std::string& token) const {
  if (token.empty()) return nullptr;
  auto retained = [&]() -> State::Shared {
    std::lock_guard lock(mutex_);
    auto entry = finalRoots_.find(token);
    if (entry == finalRoots_.end() || entry->second.ambiguous) return nullptr;
    auto root = entry->second.root.lock();
    return root && root->getSurfaceId() == surface ? entry->second.state.lock() : nullptr;
  };
  if (auto state = retained()) return state;
  auto manager = manager_.lock();
  if (!manager) return nullptr;
  RootShadowNode::Shared live;
  RootShadowNode::Shared base;
  manager->getShadowTreeRegistry().visit(surface, [&](const ShadowTree& tree) {
    live = tree.getCurrentRevision().rootShadowNode;
    base = tree.getMountingCoordinator()->getBaseRevision().rootShadowNode;
  });
  if (live) rememberFinalRoot(live);
  if (base && base != live) rememberFinalRoot(base);
  return retained();
}

RootShadowNode::Unshared PortalPublicationSource::shadowTreeWillCommit(
    const ShadowTree&,
    const RootShadowNode::Shared& oldRoot,
    const RootShadowNode::Unshared& proposal) noexcept {
  {
    // The proposal only needs duplicate validation, not a retained node lookup.
    std::unordered_set<Tag> tags;
    if (!hasUniqueTags(*proposal, tags)) return proposal;
  }
  rememberFinalRoot(oldRoot);
  std::string id;
  {
    std::lock_guard lock(mutex_);
    // oldRoot is final, including changes from hooks registered after this one.
    // Never retain this hook's proposal as a committed source.
    if (std::find(history_.begin(), history_.end(), oldRoot) == history_.end()) {
      history_.push_back(oldRoot);
      // Bound retained tree memory even when Java delivery is indefinitely late.
      // An evicted receipt is unavailable, never replaced with another root.
      if (history_.size() > 32) history_.pop_front();
    }
    id = prefix_ + std::to_string(++next_);
  }
  return std::static_pointer_cast<RootShadowNode>(
      std::const_pointer_cast<ShadowNode>(stampPublication(proposal, id)));
}

RootShadowNode::Shared PortalPublicationSource::rootForState(
    SurfaceId surface, Tag tag, const State::Shared& state) const {
  return rootForDeliveredStates(surface, {{tag, state}}, false);
}

RootShadowNode::Shared PortalPublicationSource::rootForDeliveredStates(
    SurfaceId surface, const std::map<Tag, State::Shared>& states,
    bool requireCompleteSource) const {
  if (states.empty()) return nullptr;
  auto manager = manager_.lock();
  if (!manager) return nullptr;
  RootShadowNode::Shared liveRoot;
  RootShadowNode::Shared baseRoot;
  manager->getShadowTreeRegistry().visit(surface, [&](const ShadowTree& tree) {
    liveRoot = tree.getCurrentRevision().rootShadowNode;
    baseRoot = tree.getMountingCoordinator()->getBaseRevision().rootShadowNode;
  });
  if (!liveRoot) return nullptr;
  // History roots were indexed when they became final old roots in the commit
  // hook. Only live/base may be new, including changes from later hooks.
  rememberFinalRoot(liveRoot);
  if (baseRoot != liveRoot) rememberFinalRoot(baseRoot);
  RootShadowNode::Shared result;
  {
    std::lock_guard lock(mutex_);
    for (const auto& [tag, state] : states) {
      auto concrete = dynamic_cast<const BottomSheetViewShadowNode::ConcreteState*>(state.get());
      if (!concrete || !concrete->getData().portalPublicationId.starts_with(prefix_)) return nullptr;
      auto entry = finalRoots_.find(concrete->portalStateId());
      if (entry == finalRoots_.end() || entry->second.ambiguous || entry->second.state.lock() != state) return nullptr;
      auto root = entry->second.root.lock();
      if (!root || (result && result != root)) return nullptr;
      result = std::move(root);
    }
    if (result != liveRoot && result != baseRoot &&
        std::find(history_.begin(), history_.end(), result) == history_.end()) return nullptr;
  }
  if (result->getSurfaceId() != surface ||
      &result->getFamily() != &liveRoot->getFamily()) return nullptr;
  // Share tree acquisition and indexing only within this query. Every supplied
  // State still needs its own exact identity, root and tag agreement.
  auto nodes = indexPortalShadowNodes(*result);
  if (!nodes) return nullptr;
  for (const auto& [tag, state] : states) {
    auto node = nodes->find(tag);
    if (node == nodes->end() || node->second->getState() != state) return nullptr;
  }
  if (requireCompleteSource) {
    size_t sheetCount = 0;
    for (const auto& [tag, node] : *nodes) {
      if (!dynamic_cast<const BottomSheetViewShadowNode*>(node)) continue;
      if (!states.contains(tag)) return nullptr;
      ++sheetCount;
    }
    if (sheetCount != states.size()) return nullptr;
  }
  return result;
}

std::optional<std::vector<Tag>> PortalPublicationSource::sheetTagsForState(
    SurfaceId surface, Tag tag, const State::Shared& state) const {
  auto root = rootForState(surface, tag, state);
  if (!root) return std::nullopt;
  auto nodes = indexPortalShadowNodes(*root);
  if (!nodes) return std::nullopt;
  std::vector<Tag> tags;
  for (const auto& [nodeTag, node] : *nodes) {
    if (dynamic_cast<const BottomSheetViewShadowNode*>(node)) tags.push_back(nodeTag);
  }
  return tags;
}

RootShadowNode::Shared PortalPublicationSource::rootForStates(
    SurfaceId surface, const std::map<Tag, State::Shared>& states) const {
  return rootForDeliveredStates(surface, states, true);
}

std::optional<PortalSourceSnapshot> PortalPublicationSource::receiptsForStates(
    SurfaceId surface, const std::vector<Tag>& tags,
    const std::map<Tag, State::Shared>& states) const {
  if (tags.empty()) return std::nullopt;
  auto root = rootForStates(surface, states);
  if (!root) return std::nullopt;
  auto graph = discoverPortalContexts(*root);
  if (!graph) return std::nullopt;
  PortalSourceSnapshot snapshot;
  std::unordered_set<Tag> requested;
  for (Tag tag : tags) {
    if (!requested.insert(tag).second || !states.contains(tag)) return std::nullopt;
    const auto& node = *graph->nodes.at(tag);
    auto host = graph->hostForPresentation(node.getFamily(), *root);
    if (!host) return std::nullopt;
    std::vector<Tag> ancestors{tag};
    auto path = node.getFamily().getAncestors(*root);
    for (auto it = path.rbegin(); it != path.rend(); ++it) {
      const auto& ancestor = it->first.get();
      ancestors.push_back(ancestor.getTag());
      if (isPortalNativePresentationRoot(ancestor)) break;
    }
    const auto& concrete = static_cast<const BottomSheetViewShadowNode::ConcreteState&>(*states.at(tag));
    snapshot.receipts.push_back({
        concrete.getData().portalPublicationId, *host,
        graph->contextPathForNode(
            *graph->contexts.at(graph->hostContexts.at(*host)).boundary, *root),
        std::move(ancestors)});
  }
  for (const auto& [nodeTag, node] : graph->nodes) snapshot.logicalTags.push_back(nodeTag);
  auto empty = root->ShadowNode::clone({.children = ShadowNode::emptySharedShadowNodeSharedList()});
  snapshot.nativeParents.emplace(root->getTag(), 0);
  for (const auto& mutation : calculateShadowViewMutations(*empty, *root)) {
    if (mutation.type == ShadowViewMutation::Insert && !mutation.mutatedViewIsVirtual()) {
      snapshot.nativeParents.emplace(mutation.newChildShadowView.tag, parentTag(mutation));
    }
  }
  return snapshot;
}

std::optional<std::vector<Tag>> PortalPublicationSource::contextPathForStates(
    SurfaceId surface, const std::map<Tag, State::Shared>& states, Tag tag) const {
  auto root = rootForStates(surface, states);
  if (!root) return std::nullopt;
  auto graph = discoverPortalContexts(*root);
  if (!graph || !graph->nodes.contains(tag)) return std::nullopt;
  return graph->contextPathForNode(*graph->nodes.at(tag), *root);
}

std::optional<PortalIsolationProjection> PortalPublicationSource::isolationForStates(
    SurfaceId surface, const std::map<Tag, State::Shared>& states,
    const PortalNativeMembers& members,
    const std::map<Tag, std::vector<Tag>>& retainedByHost) const {
  auto root = rootForStates(surface, states);
  if (!root) return std::nullopt;
  auto graph = discoverPortalContexts(*root);
  if (!graph) return std::nullopt;
  std::vector<PortalRetainedPaths> requests;
  for (const auto& [hostTag, presentations] : retainedByHost) {
    if (!graph->hostContexts.contains(hostTag)) return std::nullopt;
    PortalRetainedPaths request{&graph->nodes.at(hostTag)->getFamily(), {}};
    for (Tag tag : presentations) {
      if (!states.contains(tag)) return std::nullopt;
      const auto& family = graph->nodes.at(tag)->getFamily();
      if (graph->hostForPresentation(family, *root) != hostTag) return std::nullopt;
      request.presentations.push_back(&family);
    }
    requests.push_back(std::move(request));
  }
  return projectPortalIsolation(*graph, members, requests);
}

} // namespace facebook::react::bottomsheet
#endif
