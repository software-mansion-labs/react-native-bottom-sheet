#pragma once

#ifdef ANDROID
#include "PortalContextGraph.h"
#include <react/renderer/uimanager/UIManager.h>
#include <react/renderer/uimanager/UIManagerCommitHook.h>
#include <deque>
#include <map>
#include <mutex>
#include <optional>
#include <string>

namespace facebook::react::bottomsheet {

// Source metadata only; accepting native membership requires separate validation.
struct PortalSourceReceipt {
  std::string publicationId;
  Tag host;
  std::vector<Tag> contexts;
  // Presentation first; ends at its nearest native presentation root or source root.
  std::vector<Tag> ancestors;
  // Expected structure from RN's own Differentiator, not applied membership.
  std::vector<Tag> logicalTags;
  // Materialized children -> parent, including the source root -> 0.
  std::map<Tag, Tag> nativeParents;
};

/**
 * Source identity only; native attachment/registry validation belongs to the
 * consumer. Unavailable history rejects a receipt rather than widening scope.
 */
class PortalPublicationSource final : private UIManagerCommitHook {
 public:
  explicit PortalPublicationSource(const std::shared_ptr<UIManager>& manager);
  ~PortalPublicationSource() override;

  // Called outside a mounting stack, after installing the source.
  void bootstrap();

  RootShadowNode::Shared rootForState(
      SurfaceId surface, Tag tag, const State::Shared& state) const;

  // Source inventory, including inactive/nonportal sheets; not native membership.
  std::optional<std::vector<Tag>> sheetTagsForState(
      SurfaceId surface, Tag tag, const State::Shared& state) const;

  // Complete source agreement, before native attachment/registry validation.
  RootShadowNode::Shared rootForStates(
      SurfaceId surface, const std::map<Tag, State::Shared>& states) const;

  std::optional<PortalSourceReceipt> receiptForState(
      SurfaceId surface, Tag tag, const State::Shared& state) const;

  // Resolve retained tags only in the exact all-sheet source. The native
  // adapter supplies validated membership; this does not establish attachment.
  std::optional<PortalIsolationProjection> isolationForStates(
      SurfaceId surface, const std::map<Tag, State::Shared>& states,
      const PortalNativeMembers& members,
      const std::map<Tag, std::vector<Tag>>& retainedByHost) const;

 private:
  void commitHookWasRegistered(const UIManager&) noexcept override {}
  void commitHookWasUnregistered(const UIManager&) noexcept override {}
  RootShadowNode::Unshared shadowTreeWillCommit(
      const ShadowTree&,
      const RootShadowNode::Shared& oldRoot,
      const RootShadowNode::Unshared& proposal) noexcept override;

  void rememberFinalRoot(const RootShadowNode::Shared& root) const;
  struct FinalRoot {
    std::weak_ptr<const RootShadowNode> root;
    bool ambiguous{false};
  };

  std::weak_ptr<UIManager> manager_;
  const std::string prefix_;
  mutable std::mutex mutex_;
  uint64_t next_{0};
  std::deque<RootShadowNode::Shared> history_;
  // Entries follow live State identities, without retaining States or trees.
  mutable std::map<std::weak_ptr<const State>, FinalRoot, std::owner_less<>> finalRoots_;
};

} // namespace facebook::react::bottomsheet
#endif
