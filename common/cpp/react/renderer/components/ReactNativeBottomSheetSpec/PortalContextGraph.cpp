#include "PortalContextGraph.h"
#include <react/renderer/components/view/ViewProps.h>
#include <react/renderer/core/LayoutableShadowNode.h>
#include <react/renderer/core/ShadowNodeFamily.h>
#include <cstring>

namespace facebook::react::bottomsheet {

bool isPortalNativePresentationRoot(const ShadowNode& node) {
  return std::strcmp(node.getComponentName(), "ModalHostView") == 0 ||
      (std::strcmp(node.getComponentName(), "BottomSheetView") == 0 &&
       node.getTraits().check(ShadowNodeTraits::Trait::RootNodeKind));
}

namespace {
template <typename Family>
const ShadowNode* currentNodeForFamily(
    const PortalShadowNodeIndex& nodes, const Family* family) {
  if (!family) return nullptr;
  if constexpr (requires { family->getTag(); }) {
    auto found = nodes.find(family->getTag());
    return found != nodes.end() && &found->second->getFamily() == family
        ? found->second
        : nullptr;
  } else {
    // RN 0.76 exposes family identity without a public tag getter.
    for (const auto& [tag, node] : nodes) {
      if (&node->getFamily() == family) return node;
    }
    return nullptr;
  }
}

bool indexNodes(const ShadowNode& node, PortalShadowNodeIndex& nodes) {
  if (!nodes.emplace(node.getTag(), &node).second) return false;
  for (const auto& child : node.getChildren()) {
    if (!indexNodes(*child, nodes)) return false;
  }
  return true;
}

// display:contents is unavailable in older supported Yoga releases.
template <typename Display>
bool isContents(Display value) {
  if constexpr (requires { Display::Contents; }) {
    return value == Display::Contents;
  }
  return false;
}

const ShadowNode* containingBlock(
    const ShadowNode& root,
    const ShadowNodeFamily& host) {
  auto ancestors = host.getAncestors(root);
  for (auto it = ancestors.rbegin(); it != ancestors.rend(); ++it) {
    const auto& node = it->first.get();
    auto layoutable = dynamic_cast<const LayoutableShadowNode*>(&node);
    if (!layoutable) continue;
    // ViewKind guarantees ConcreteViewShadowNode's ViewProps contract without
    // relying on Props RTTI identity across Android shared libraries.
    auto props = node.getTraits().check(ShadowNodeTraits::Trait::ViewKind)
        ? static_cast<const ViewProps*>(node.getProps().get())
        : nullptr;
    if (!props || props->yogaStyle.display() == yoga::Display::None) {
      return nullptr;
    }
    if (isPortalNativePresentationRoot(node)) {
      return isContents(props->yogaStyle.display()) ? nullptr : &node;
    }
    if (isContents(props->yogaStyle.display())) continue;
    if (&node == &root ||
        layoutable->getLayoutMetrics().positionType != PositionType::Static ||
        props->transform != Transform::Identity() || !props->filter.empty()) {
      return &node;
    }
  }
  return nullptr;
}
} // namespace

std::optional<PortalShadowNodeIndex> indexPortalShadowNodes(const ShadowNode& root) {
  PortalShadowNodeIndex nodes;
  if (!indexNodes(root, nodes)) return std::nullopt;
  return nodes;
}

std::optional<PortalContextGraph> discoverPortalContexts(const ShadowNode& root) {
  auto nodes = indexPortalShadowNodes(root);
  if (!nodes) return std::nullopt;
  PortalContextGraph graph{.nodes = std::move(*nodes)};
  for (const auto& [tag, node] : graph.nodes) {
    if (node->getProps()->nativeId != PortalHostNativeID) continue;
    auto boundary = containingBlock(root, node->getFamily());
    if (!boundary) return std::nullopt;
    graph.hostContexts.emplace(tag, boundary->getTag());
    auto [context, inserted] = graph.contexts.try_emplace(
        boundary->getTag(), PortalContext{boundary, {}, std::nullopt});
    context->second.hosts.insert(tag);
  }
  for (auto& [tag, context] : graph.contexts) {
    // Modal content belongs to its native presentation, not the source JSX
    // container. Contexts within that content can still nest with each other.
    if (isPortalNativePresentationRoot(*context.boundary)) continue;
    auto ancestors = context.boundary->getFamily().getAncestors(root);
    for (auto it = ancestors.rbegin(); it != ancestors.rend(); ++it) {
      Tag parent = it->first.get().getTag();
      if (graph.contexts.contains(parent)) {
        context.parent = parent;
        break;
      }
      if (isPortalNativePresentationRoot(it->first.get())) break;
    }
  }
  return graph;
}

std::optional<Tag> PortalContextGraph::hostForPresentation(
    const ShadowNodeFamily& presentation,
    const ShadowNode& root) const {
  auto currentRoot = nodes.find(root.getTag());
  auto currentNode = currentNodeForFamily(nodes, &presentation);
  if (currentRoot == nodes.end() || currentRoot->second != &root ||
      !currentNode) {
    return std::nullopt;
  }
  auto ancestors = presentation.getAncestors(root);
  for (auto it = ancestors.rbegin(); it != ancestors.rend(); ++it) {
    Tag tag = it->first.get().getTag();
    if (hostContexts.contains(tag)) return tag;
    if (isPortalNativePresentationRoot(it->first.get())) break;
  }
  return std::nullopt;
}

std::vector<Tag> PortalContextGraph::contextPathForNode(
    const ShadowNode& node,
    const ShadowNode& root) const {
  std::optional<Tag> context;
  if (contexts.contains(node.getTag())) context = node.getTag();
  else if (!isPortalNativePresentationRoot(node)) {
    auto ancestors = node.getFamily().getAncestors(root);
    for (auto it = ancestors.rbegin(); it != ancestors.rend(); ++it) {
      Tag tag = it->first.get().getTag();
      if (contexts.contains(tag)) {
        context = tag;
        break;
      }
      if (isPortalNativePresentationRoot(it->first.get())) break;
    }
  }
  std::vector<Tag> path;
  while (context) {
    path.push_back(*context);
    context = contexts.at(*context).parent;
  }
  return path;
}

std::optional<PortalIsolationProjection> projectPortalIsolation(
    const PortalContextGraph& graph,
    const PortalNativeMembers& members,
    const std::vector<PortalRetainedPaths>& requests) {
  PortalIsolationProjection projection;
  std::map<Tag, Tag> logicalParents;
  for (const auto& [tag, node] : graph.nodes) {
    for (const auto& child : node->getChildren()) {
      logicalParents.emplace(child->getTag(), tag);
    }
  }
  auto currentNode = [&](const ShadowNodeFamily* family) -> const ShadowNode* {
    return currentNodeForFamily(graph.nodes, family);
  };
  using RetainedEdges = std::map<Tag, std::set<Tag>>;
  std::map<Tag, RetainedEdges> contextEdges;
  std::map<Tag, std::set<Tag>> retainedPresentations;
  for (const auto& request : requests) {
    auto host = currentNode(request.host);
    if (!host || !graph.hostContexts.contains(host->getTag()) ||
        !members.alive.contains(host->getTag()) || request.presentations.empty()) {
      return std::nullopt;
    }
    Tag scope = graph.hostContexts.at(host->getTag());
    auto& edges = contextEdges[scope];
    for (const auto* family : request.presentations) {
      auto presentation = currentNode(family);
      if (!presentation || !members.alive.contains(presentation->getTag())) {
        return std::nullopt;
      }
      Tag child = presentation->getTag();
      bool passesHost = child == host->getTag();
      while (child != scope) {
        auto parent = logicalParents.find(child);
        if (parent == logicalParents.end()) return std::nullopt;
        edges[parent->second].insert(child);
        child = parent->second;
        passesHost |= child == host->getTag();
      }
      if (!passesHost) return std::nullopt;
      retainedPresentations[scope].insert(presentation->getTag());
    }
  }
  for (const auto& [scope, edges] : contextEdges) {
    auto ancestor = graph.contexts.at(scope).parent;
    while (ancestor && !contextEdges.contains(*ancestor)) {
      ancestor = graph.contexts.at(*ancestor).parent;
    }
    if (!ancestor) continue;
    // The nearest active containing context already connects this path to
    // higher contexts. A retained whole presentation must keep its content
    // outside the inner area; only an inactive presentation needs path lifting.
    Tag child = scope;
    auto& outerEdges = contextEdges.at(*ancestor);
    const auto& whole = retainedPresentations.at(*ancestor);
    std::vector<std::pair<Tag, Tag>> liftedEdges;
    while (child != *ancestor && !whole.contains(child)) {
      auto parent = logicalParents.find(child);
      if (parent == logicalParents.end()) return std::nullopt;
      liftedEdges.emplace_back(parent->second, child);
      child = parent->second;
    }
    // A whole retained ancestor already connects the entire path. Partial
    // lifting below it would incorrectly hide content in intervening containers.
    if (whole.contains(child)) continue;
    for (const auto& [parent, retainedChild] : liftedEdges) {
      outerEdges[parent].insert(retainedChild);
    }
  }
  for (const auto& [scope, edges] : contextEdges) {
    for (const auto& [parent, retained] : edges) {
      auto& allRetained = projection.retainedChildren[parent];
      allRetained.insert(retained.begin(), retained.end());
    }
  }
  auto excludeFrontier = [&](auto&& visit, const ShadowNode& node,
                             const std::set<Tag>& branch) -> bool {
    Tag tag = node.getTag();
    if (members.alive.contains(tag)) {
      auto parent = members.parents.find(tag);
      if (parent == members.parents.end()) return false;
      if (!branch.contains(parent->second)) projection.hiddenBranches.insert(tag);
    }
    for (const auto& child : node.getChildren()) {
      if (!visit(visit, *child, branch)) return false;
    }
    return true;
  };
  for (const auto& [parent, retained] : projection.retainedChildren) {
    for (const auto& child : graph.nodes.at(parent)->getChildren()) {
      if (retained.contains(child->getTag())) continue;
      auto branch = indexPortalShadowNodes(*child);
      if (!branch) return std::nullopt;
      std::set<Tag> branchTags;
      for (const auto& [tag, node] : *branch) branchTags.insert(tag);
      if (!excludeFrontier(excludeFrontier, *child, branchTags)) {
        return std::nullopt;
      }
    }
  }
  return projection;
}

} // namespace facebook::react::bottomsheet
