#pragma once

#include <react/renderer/core/ShadowNode.h>
#include <map>
#include <optional>
#include <set>
#include <vector>

namespace facebook::react::bottomsheet {

inline constexpr char PortalHostNativeID[] =
    "__react_native_bottom_sheet_portal_host";

using PortalShadowNodeIndex = std::map<Tag, const ShadowNode*>;

/** RN Modal and nativeOverlay end source ancestry and local context inheritance. */
bool isPortalNativePresentationRoot(const ShadowNode& node);

/** Index family bindings even when a host's layout cannot define a context. */
std::optional<PortalShadowNodeIndex> indexPortalShadowNodes(const ShadowNode& root);

struct PortalContext {
  const ShadowNode* boundary;
  std::set<Tag> hosts;
  std::optional<Tag> parent;
};

/**
 * A read-only view of one applied Fabric root. The caller keeps that exact root
 * alive and validates its native publication before consuming this snapshot.
 * A context is the host's layout containing block, which may be flattened.
 */
struct PortalContextGraph {
  PortalShadowNodeIndex nodes;
  std::map<Tag, Tag> hostContexts;
  std::map<Tag, PortalContext> contexts;

  std::optional<Tag> hostForPresentation(
      const ShadowNodeFamily& presentation,
      const ShadowNode& root) const;

  // Innermost first, bounded by actual native Modal presentation ancestry.
  std::vector<Tag> contextPathForNode(
      const ShadowNode& node,
      const ShadowNode& root) const;
};

/** Unresolvable layout or ambiguous identity rejects the whole projection. */
std::optional<PortalContextGraph> discoverPortalContexts(const ShadowNode& root);

struct PortalRetainedPaths {
  const ShadowNodeFamily* host;
  std::vector<const ShadowNodeFamily*> presentations;
};

/** Native membership comes from completed mounting instructions, not FormsView. */
struct PortalNativeMembers {
  std::set<Tag> alive;
  std::map<Tag, Tag> parents;
};

struct PortalIsolationProjection {
  std::set<Tag> hiddenBranches;
  // Native adapters also inspect ordinary native-only siblings at these edges.
  std::map<Tag, std::set<Tag>> retainedChildren;
};

/**
 * Merge every context's retained paths before computing excluded native
 * frontiers. Family identity must belong to this exact graph. An empty request
 * list releases isolation; an empty retained path in an active context rejects.
 */
std::optional<PortalIsolationProjection> projectPortalIsolation(
    const PortalContextGraph& graph,
    const PortalNativeMembers& members,
    const std::vector<PortalRetainedPaths>& requests);

} // namespace facebook::react::bottomsheet
