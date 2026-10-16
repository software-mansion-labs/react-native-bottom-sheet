#include <react/renderer/components/ReactNativeBottomSheetSpec/ComponentDescriptors.h>
#include <react/renderer/components/ReactNativeBottomSheetSpec/PortalPublicationSource.h>
#include <react/renderer/components/ReactNativeBottomSheetSpec/PortalContextGraph.h>
#include <react/renderer/components/view/ViewComponentDescriptor.h>
#include <react/renderer/components/modal/ModalHostViewShadowNode.h>
#include <iostream>

using namespace facebook::react;
using namespace facebook::react::bottomsheet;
using Children = std::vector<std::shared_ptr<const ShadowNode>>;

namespace {
struct Fixture {
  std::shared_ptr<ContextContainer> context = std::make_shared<ContextContainer>();
  std::shared_ptr<UIManager> manager = std::make_shared<UIManager>(RuntimeExecutor{}, context);
  ViewComponentDescriptor views{{.eventDispatcher = {}, .contextContainer = context, .flavor = nullptr}};
  BottomSheetViewComponentDescriptor sheets{{.eventDispatcher = {}, .contextContainer = context, .flavor = nullptr}};
  // The Android descriptor's screen-size factory needs JNI. This pure host
  // fixture uses the SDK Modal node with explicit State instead.
  ConcreteComponentDescriptor<ModalHostViewShadowNode> modals{
      {.eventDispatcher = {}, .contextContainer = context, .flavor = nullptr}};
  std::unique_ptr<PortalPublicationSource> source = std::make_unique<PortalPublicationSource>(manager);
  std::vector<SurfaceId> surfaces;

  std::shared_ptr<const ShadowNode> make(Tag tag, bool concrete, const char* position,
                         Children children = {}, bool sheet = false, bool host = false,
                         SurfaceId surface = 1) {
    const ComponentDescriptor& descriptor = sheet ? static_cast<const ComponentDescriptor&>(sheets) : views;
    folly::dynamic raw = folly::dynamic::object("collapsable", !concrete)
        ("position", position)("width", 300)("height", 560);
    if (host) raw["nativeID"] = PortalHostNativeID;
    auto props = descriptor.cloneProps(PropsParserContext(surface, *context), nullptr, RawProps(raw));
    auto family = descriptor.createFamily({.tag = tag, .surfaceId = surface, .instanceHandle = nullptr});
    auto state = descriptor.createInitialState(props, family);
    if (sheet) {
      auto geometry = std::make_shared<BottomSheetViewState>();
      geometry->contentOffsetY = 39;
      geometry->frameSize = {300, 560};
      geometry->contentRegionInset = 24;
      state = std::make_shared<const BottomSheetViewShadowNode::ConcreteState>(geometry, *state);
    }
    return descriptor.createShadowNode({.props = props,
        .children = std::make_shared<const Children>(std::move(children)), .state = state}, family);
  }

  ShadowTree& tree(SurfaceId surface = 1) {
    auto owned = std::make_unique<ShadowTree>(surface,
        LayoutConstraints{.minimumSize = {800, 600}, .maximumSize = {800, 600}},
        LayoutContext{}, *manager, *context);
    auto& result = *owned;
    manager->getShadowTreeRegistry().add(std::move(owned));
    surfaces.push_back(surface);
    return result;
  }

  RootShadowNode::Shared enqueue(ShadowTree& tree, Children children) {
    auto status = tree.commit([&](const RootShadowNode& root) {
      return std::static_pointer_cast<RootShadowNode>(root.ShadowNode::clone(
          {.children = std::make_shared<const Children>(std::move(children))}));
    }, {});
    if (status != ShadowTreeCommitStatus::Succeeded) std::abort();
    return tree.getCurrentRevision().rootShadowNode;
  }

  MountingTransaction commit(ShadowTree& tree, Children children) {
    enqueue(tree, std::move(children));
    auto transaction = tree.getMountingCoordinator()->pullTransaction(true);
    if (!transaction) std::abort();
    return std::move(*transaction);
  }

  ~Fixture() {
    source.reset();
    if (manager) {
      for (auto surface : surfaces) manager->getShadowTreeRegistry().remove(surface);
    }
  }
};

State::Shared delivered(const MountingTransaction& transaction, Tag tag) {
  State::Shared result;
  for (const auto& mutation : transaction.getMutations()) {
    if (mutation.newChildShadowView.tag == tag && mutation.newChildShadowView.state) {
      result = mutation.newChildShadowView.state;
    }
  }
  return result;
}

const BottomSheetViewState& data(const State::Shared& state) {
  return std::static_pointer_cast<const BottomSheetViewShadowNode::ConcreteState>(state)->getData();
}

class LaterLayoutHook final : public UIManagerCommitHook {
 public:
  bool relative = false;
  State::Shared restoredState;
  const ViewComponentDescriptor& descriptor;
  const ContextContainer& context;
  LaterLayoutHook(const ViewComponentDescriptor& descriptor, const ContextContainer& context)
      : descriptor(descriptor), context(context) {}
  void commitHookWasRegistered(const UIManager&) noexcept override {}
  void commitHookWasUnregistered(const UIManager&) noexcept override {}
  RootShadowNode::Unshared shadowTreeWillCommit(const ShadowTree&,
      const RootShadowNode::Shared&, const RootShadowNode::Unshared& root) noexcept override {
    if (!relative) return root;
    const auto& wrapper = *indexPortalShadowNodes(*root)->at(31);
    auto props = descriptor.cloneProps(PropsParserContext(1, context), wrapper.getProps(),
        RawProps(folly::dynamic::object("position", "relative")));
    auto result = std::static_pointer_cast<RootShadowNode>(root->cloneTree(wrapper.getFamily(),
        [&](const ShadowNode& node) { return node.clone({.props = props}); }));
    if (restoredState) {
      const auto& sheet = *indexPortalShadowNodes(*result)->at(14);
      result = std::static_pointer_cast<RootShadowNode>(result->cloneTree(sheet.getFamily(),
          [&](const ShadowNode& node) { return node.clone({.state = restoredState}); }));
    }
    return result;
  }
};
} // namespace

int main() {
  int tests = 0;
  int failures = 0;
  auto expect = [&](const char* name, bool valid) {
    ++tests;
    failures += !valid;
    std::cout << (valid ? "PASS " : "FAIL ") << name << '\n';
  };

  {
    Fixture f;
    auto& tree = f.tree();
    auto sheet = f.make(14, true, "relative", {}, true);
    auto host = f.make(13, true, "absolute", {sheet}, false, true);
    auto area = f.make(11, false, "relative", {f.make(12, true, "relative"), host});
    auto mutation = f.commit(tree, {area});
    auto native = delivered(mutation, 14);
    auto root = f.source->rootForState(1, 14, native);
    expect("a delivered BottomSheet state identifies its exact source and preserves native geometry",
           native && !data(native).portalPublicationId.empty() &&
           data(native).contentOffsetY == 39 && data(native).frameSize == facebook::react::Size{300, 560} &&
           data(native).contentRegionInset == 24 &&
           root == tree.getCurrentRevision().rootShadowNode && root &&
           discoverPortalContexts(*root)->hostContexts.at(13) == 11);
    auto receipt = f.source->receiptForState(1, 14, native);
    expect("expected native structure includes background but preserves the flattened logical context",
           receipt && receipt->logicalTags == std::vector<Tag>{1, 11, 12, 13, 14} &&
           receipt->nativeParents == std::map<Tag, Tag>{{1, 0}, {12, 1}, {13, 1}, {14, 13}} &&
           receipt->contexts == std::vector<Tag>{11});
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto sheet = f.make(14, true, "relative", {}, true);
    auto host = f.make(13, true, "absolute", {sheet}, false, true);
    auto wrapper = f.make(31, false, "static", {host});
    auto area = f.make(11, false, "relative", {wrapper});
    LaterLayoutHook later(f.views, *f.context);
    f.manager->registerCommitHook(later);
    auto first = delivered(f.commit(tree, {area}), 14);
    auto firstRoot = tree.getCurrentRevision().rootShadowNode;
    later.relative = true;
    auto second = delivered(f.commit(tree, {area}), 14);
    auto secondRoot = tree.getCurrentRevision().rootShadowNode;
    auto third = delivered(f.commit(tree, tree.getCurrentRevision().rootShadowNode->getChildren()), 14);
    auto old = f.source->rootForState(1, 14, first);
    auto delayed = f.source->rootForState(1, 14, second);
    expect("delayed states recover final containing blocks after later hooks and a newer commit",
           third && second && first && first != second && second != third &&
           old == firstRoot && delayed == secondRoot && old && delayed &&
           discoverPortalContexts(*old)->hostContexts.at(13) == 11 &&
           discoverPortalContexts(*delayed)->hostContexts.at(13) == 31 &&
           indexPortalShadowNodes(*old)->at(14)->getProps() ==
               indexPortalShadowNodes(*delayed)->at(14)->getProps());
    f.manager->unregisterCommitHook(later);
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto host = f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true);
    auto first = delivered(f.commit(tree, {f.make(11, false, "relative", {host})}), 14);
    std::weak_ptr<const RootShadowNode> retired = tree.getCurrentRevision().rootShadowNode;
    State::Shared previous;
    State::Shared latest;
    for (int i = 0; i < 40; ++i) {
      previous = latest;
      latest = delivered(f.commit(tree, tree.getCurrentRevision().rootShadowNode->getChildren()), 14);
    }
    expect("finite source history releases old roots and rejects evicted states while recent states remain available",
           retired.expired() && !f.source->rootForState(1, 14, first) &&
           f.source->rootForState(1, 14, previous) &&
           f.source->rootForState(1, 14, latest) == tree.getCurrentRevision().rootShadowNode);
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto host = f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true);
    auto area = f.make(11, false, "relative", {f.make(31, false, "static", {host})});
    LaterLayoutHook later(f.views, *f.context);
    f.manager->registerCommitHook(later);
    auto first = delivered(f.commit(tree, {area}), 14);
    later.relative = true;
    later.restoredState = first;
    f.commit(tree, {area});
    expect("a later hook reusing one state in distinct final roots makes that source ambiguous",
           !f.source->rootForState(1, 14, first));
    f.manager->unregisterCommitHook(later);
  }

  {
    Fixture f;
    auto& firstTree = f.tree();
    auto areaA = f.make(11, false, "relative", {
        f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)});
    auto areaB = f.make(21, false, "relative", {
        f.make(23, true, "absolute", {f.make(24, true, "relative", {}, true)}, false, true)});
    auto firstMount = f.commit(firstTree, {areaA, areaB});
    auto a = delivered(firstMount, 14);
    auto b = delivered(firstMount, 24);
    auto firstRoot = firstTree.getCurrentRevision().rootShadowNode;
    auto& secondTree = f.tree(2);
    auto secondArea = f.make(111, false, "relative", {
        f.make(113, true, "absolute", {f.make(114, true, "relative", {}, true, false, 2)}, false, true, 2)}, false, false, 2);
    auto other = delivered(f.commit(secondTree, {secondArea}), 114);
    auto copiedData = std::make_shared<BottomSheetViewState>(data(a));
    auto forged = std::make_shared<const BottomSheetViewShadowNode::ConcreteState>(copiedData, *a);
    auto graph = discoverPortalContexts(*firstRoot);
    expect("all owners share one source per root while surfaces and exact state identities remain separate",
           a && b && other && data(a).portalPublicationId == data(b).portalPublicationId &&
           data(a).portalPublicationId != data(other).portalPublicationId &&
           f.source->rootForState(1, 14, a) == firstRoot &&
           f.source->rootForState(1, 24, b) == firstRoot &&
           f.source->rootForState(2, 114, other) == secondTree.getCurrentRevision().rootShadowNode &&
           !f.source->rootForState(2, 14, a) && !f.source->rootForState(1, 14, b) &&
           !f.source->rootForState(1, 14, forged) && !f.source->rootForState(1, 14, nullptr) &&
           graph->hostContexts.at(13) == 11 && graph->hostContexts.at(23) == 21);
    f.manager->getShadowTreeRegistry().remove(1);
    f.surfaces.erase(f.surfaces.begin());
    expect("stopping one surface rejects its old source while the other surface remains available",
           !f.source->rootForState(1, 14, a) &&
           f.source->rootForState(2, 114, other) == secondTree.getCurrentRevision().rootShadowNode);
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto oldSheet = f.make(14, true, "relative", {}, true);
    auto first = delivered(f.commit(tree, {oldSheet}), 14);
    auto firstFamily = indexPortalShadowNodes(*tree.getCurrentRevision().rootShadowNode)->at(14)->getFamilyShared();
    f.commit(tree, {});
    auto second = delivered(f.commit(tree, {f.make(14, true, "relative", {}, true)}), 14);
    auto secondRoot = f.source->rootForState(1, 14, second);
    expect("a recycled tag belongs to its new family and cannot identify the earlier state as current",
           secondRoot == tree.getCurrentRevision().rootShadowNode &&
           indexPortalShadowNodes(*secondRoot)->at(14)->getFamilyShared() != firstFamily &&
           f.source->rootForState(1, 14, first) != secondRoot && first != second);
    f.source.reset();
    auto before = tree.getCurrentRevision().rootShadowNode;
    auto children = before->getChildren();
    children.push_back(f.make(99, true, "relative"));
    f.commit(tree, children);
    expect("destroying the source unregisters its hook before another commit",
           indexPortalShadowNodes(*tree.getCurrentRevision().rootShadowNode)->at(14)->getState() == second);
    f.source = std::make_unique<PortalPublicationSource>(f.manager);
    expect("a fresh source rejects the prior source namespace",
           !f.source->rootForState(1, 14, second));
    auto fresh = delivered(f.commit(tree, tree.getCurrentRevision().rootShadowNode->getChildren()), 14);
    expect("the fresh source supplies a distinct identity for the same live family",
           fresh && data(fresh).portalPublicationId != data(second).portalPublicationId &&
           f.source->rootForState(1, 14, fresh) == tree.getCurrentRevision().rootShadowNode);
    std::weak_ptr<UIManager> runtime = f.manager;
    f.manager->getShadowTreeRegistry().remove(1);
    f.surfaces.clear();
    f.manager.reset();
    expect("the source does not retain its runtime and rejects reads after runtime destruction",
           runtime.expired() && !f.source->rootForState(1, 14, fresh));
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto host = f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true);
    auto area = f.make(11, false, "relative", {f.make(31, false, "static", {host})});
    LaterLayoutHook later(f.views, *f.context);
    f.manager->registerCommitHook(later);
    auto first = delivered(f.commit(tree, {area}), 14);
    std::weak_ptr<const RootShadowNode> original = tree.getCurrentRevision().rootShadowNode;
    for (int i = 0; i < 40; ++i) {
      f.commit(tree, tree.getCurrentRevision().rootShadowNode->getChildren());
    }
    later.relative = true;
    later.restoredState = first;
    f.commit(tree, {area});
    expect("restoring an old state after history eviction cannot assign it to a different final root",
           original.expired() && !f.source->rootForState(1, 14, first));
    f.manager->unregisterCommitHook(later);
  }

  {
    Fixture f;
    f.source.reset();
    auto& tree = f.tree();
    auto sheet = f.make(14, true, "relative", {}, true);
    auto first = delivered(f.commit(tree, {sheet}), 14);
    f.source = std::make_unique<PortalPublicationSource>(f.manager);
    f.source->bootstrap();
    auto transaction = tree.getMountingCoordinator()->pullTransaction(true);
    auto stamped = transaction ? delivered(*transaction, 14) : nullptr;
    expect("source installation bootstraps an already mounted sheet through a new native state mutation",
           data(first).portalPublicationId.empty() && stamped && stamped != first &&
           !data(stamped).portalPublicationId.empty() && data(stamped).contentOffsetY == 39 &&
           data(stamped).frameSize == facebook::react::Size{300, 560} && data(stamped).contentRegionInset == 24 &&
           f.source->rootForState(1, 14, stamped) == tree.getCurrentRevision().rootShadowNode);
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto areaA = f.make(11, false, "relative", {
        f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)});
    auto areaB = f.make(21, false, "relative", {
        f.make(23, true, "absolute", {f.make(24, true, "relative", {}, true)}, false, true)});
    auto mount = f.commit(tree, {areaA, areaB});
    auto a = f.source->receiptForState(1, 14, delivered(mount, 14));
    auto b = f.source->receiptForState(1, 24, delivered(mount, 24));
    expect("receipts preserve one publication identity and separate local host context paths",
           a && b && !a->publicationId.empty() && a->publicationId == b->publicationId &&
           a->host == 13 && a->contexts == std::vector<Tag>{11} &&
           a->ancestors == std::vector<Tag>{14, 13, 11, 1} &&
           b->host == 23 && b->contexts == std::vector<Tag>{21} &&
           b->ancestors == std::vector<Tag>{24, 23, 21, 1});
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto innerHost = f.make(23, true, "absolute", {
        f.make(24, true, "relative", {}, true)}, false, true);
    auto outerSheet = f.make(14, true, "relative", {innerHost}, true);
    auto outerHost = f.make(13, true, "absolute", {outerSheet}, false, true);
    auto mount = f.commit(tree, {f.make(11, false, "relative", {outerHost})});
    auto outer = f.source->receiptForState(1, 14, delivered(mount, 14));
    auto inner = f.source->receiptForState(1, 24, delivered(mount, 24));
    expect("an outer presentation receipt does not adopt its descendant portal context",
           outer && inner && outer->host == 13 && outer->contexts == std::vector<Tag>{11} &&
           inner->host == 23 && inner->contexts == std::vector<Tag>{14, 11});
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto host = f.make(13, true, "absolute", {
        f.make(14, true, "relative", {}, true)}, false, true);
    auto area = f.make(11, false, "relative", {f.make(31, false, "static", {host})});
    LaterLayoutHook later(f.views, *f.context);
    f.manager->registerCommitHook(later);
    auto first = delivered(f.commit(tree, {area}), 14);
    later.relative = true;
    auto second = delivered(f.commit(tree, {area}), 14);
    auto delayed = f.source->receiptForState(1, 14, first);
    auto current = f.source->receiptForState(1, 14, second);
    expect("reading a delayed receipt never substitutes the newer containing block",
           delayed && current && delayed->publicationId != current->publicationId &&
           delayed->contexts == std::vector<Tag>{11} && current->contexts == std::vector<Tag>{31} &&
           delayed->ancestors == std::vector<Tag>{14, 13, 31, 11, 1} &&
           current->ancestors == delayed->ancestors);
    f.manager->unregisterCommitHook(later);
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto standalone = delivered(f.commit(tree, {
        f.make(14, true, "relative", {}, true)}), 14);
    expect("a nonportal or unavailable state never supplies a root fallback receipt",
           f.source->rootForState(1, 14, standalone) &&
           !f.source->receiptForState(1, 14, standalone) &&
           !f.source->receiptForState(2, 14, standalone) &&
           !f.source->receiptForState(1, 14, nullptr));
  }

  {
    Fixture f;
    auto& oldTree = f.tree();
    auto old = delivered(f.commit(oldTree, {
        f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)}), 14);
    auto oldRoot = oldTree.getCurrentRevision().rootShadowNode;
    f.commit(oldTree, oldRoot->getChildren());
    f.manager->getShadowTreeRegistry().remove(1);
    f.surfaces.clear();
    auto& restarted = f.tree();
    auto fresh = delivered(f.commit(restarted, {
        f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)}), 14);
    expect("restarting a surface with reused tags rejects States from its retired lifetime",
           oldRoot->getFamilyShared() != restarted.getCurrentRevision().rootShadowNode->getFamilyShared() &&
           !f.source->rootForState(1, 14, old) && !f.source->receiptForState(1, 14, old) &&
           f.source->rootForState(1, 14, fresh) == restarted.getCurrentRevision().rootShadowNode);
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto firstMount = f.commit(tree, {
        f.make(11, false, "relative", {
            f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)}),
        f.make(21, false, "relative", {
            f.make(23, true, "absolute", {f.make(24, true, "relative", {}, true)}, false, true)})});
    auto firstRoot = tree.getCurrentRevision().rootShadowNode;
    auto secondMount = f.commit(tree, firstRoot->getChildren());
    auto secondRoot = tree.getCurrentRevision().rootShadowNode;
    auto a1 = delivered(firstMount, 14);
    auto b1 = delivered(firstMount, 24);
    auto a2 = delivered(secondMount, 14);
    auto b2 = delivered(secondMount, 24);
    expect("complete owner States resolve one final revision while mixed revisions reject",
           f.source->rootForStates(1, {{14, a1}, {24, b1}}) == firstRoot &&
           f.source->rootForStates(1, {{14, a2}, {24, b2}}) == secondRoot &&
           !f.source->rootForStates(1, {{14, a1}, {24, b2}}) &&
           !f.source->rootForStates(1, {}));
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto mount = f.commit(tree, {
        f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true),
        f.make(23, true, "absolute", {f.make(24, true, "relative", {}, true)}, false, true),
        f.make(34, true, "relative", {}, true)});
    auto a = delivered(mount, 14);
    auto b = delivered(mount, 24);
    auto nonportal = delivered(mount, 34);
    expect("one exact delivered State discovers all source sheets including nonportal sheets",
           f.source->sheetTagsForState(1, 14, a) == std::vector<Tag>{14, 24, 34} &&
           !f.source->sheetTagsForState(2, 14, a) &&
           !f.source->sheetTagsForState(1, 99, a) &&
           !f.source->sheetTagsForState(1, 14, nullptr));
    expect("complete source agreement includes nonportal sheets and rejects missing or excess entries",
           f.source->rootForStates(1, {{14, a}, {24, b}, {34, nonportal}}) ==
               tree.getCurrentRevision().rootShadowNode &&
           !f.source->rootForStates(1, {{14, a}, {24, b}}) &&
           !f.source->rootForStates(1, {{24, b}, {34, nonportal}}) &&
           !f.source->rootForStates(1, {{14, a}, {24, b}, {34, nonportal}, {99, nonportal}}));
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto family = f.modals.createFamily({.tag = 30, .surfaceId = 1, .instanceHandle = nullptr});
    auto state = std::make_shared<const ModalHostViewShadowNode::ConcreteState>(
        std::make_shared<const ModalHostViewState>(facebook::react::Size{800, 600}), family);
    auto area = f.make(11, false, "relative", {
        f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)});
    auto modal = f.modals.createShadowNode({.props = std::make_shared<ModalHostViewProps>(),
        .children = std::make_shared<const Children>(Children{area}), .state = state}, family);
    auto mount = f.commit(tree, {f.make(21, false, "relative", {modal})});
    auto receipt = f.source->receiptForState(1, 14, delivered(mount, 14));
    expect("source ancestry ends at the native Modal owner without inheriting its JSX parent",
           receipt && receipt->contexts == std::vector<Tag>{11} &&
           receipt->ancestors == std::vector<Tag>{14, 13, 11, 30});
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto area = f.make(11, false, "relative", {
        f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)});
    auto overlay = f.make(24, true, "relative", {area}, true);
    auto overlayProps = f.sheets.cloneProps(PropsParserContext(1, *f.context), overlay->getProps(),
        RawProps(folly::dynamic::object("nativeOverlay", true)));
    overlay = overlay->clone({.props = overlayProps});
    auto outsideHost = f.make(7, true, "absolute", {f.make(8, true, "relative", {}, true)}, false, true);
    auto firstMount = f.commit(tree, {f.make(5, false, "relative", {outsideHost, overlay})});
    auto firstState = delivered(firstMount, 14);
    auto inlineProps = f.sheets.cloneProps(PropsParserContext(1, *f.context), overlayProps,
        RawProps(folly::dynamic::object("nativeOverlay", false)));
    auto secondMount = f.commit(tree, tree.getCurrentRevision().rootShadowNode->cloneTree(
        overlay->getFamily(), [&](const ShadowNode& node) { return node.clone({.props = inlineProps}); })->getChildren());
    auto oldReceipt = f.source->receiptForState(1, 14, firstState);
    auto currentReceipt = f.source->receiptForState(1, 14, delivered(secondMount, 14));
    expect("nativeOverlay ancestry and local nesting follow its native root across a return to inline",
           overlay->getTraits().check(ShadowNodeTraits::Trait::RootNodeKind) &&
           oldReceipt && oldReceipt->contexts == std::vector<Tag>{11} &&
           oldReceipt->ancestors == std::vector<Tag>{14, 13, 11, 24} &&
           currentReceipt && currentReceipt->contexts == std::vector<Tag>{11, 5} &&
           currentReceipt->ancestors == std::vector<Tag>{14, 13, 11, 24, 5, 1});
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto overlay = f.make(24, true, "static", {
        f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)}, true);
    auto props = f.sheets.cloneProps(PropsParserContext(1, *f.context), overlay->getProps(),
        RawProps(folly::dynamic::object("nativeOverlay", true)));
    auto geometry = std::make_shared<BottomSheetViewState>(data(overlay->getState()));
    geometry->frameSize = {};
    auto state = std::make_shared<const BottomSheetViewShadowNode::ConcreteState>(geometry,
        static_cast<const BottomSheetViewShadowNode::ConcreteState&>(*overlay->getState()));
    overlay = overlay->clone({.props = props, .state = state});
    auto outsideHost = f.make(7, true, "absolute", {f.make(8, true, "relative", {}, true)}, false, true);
    auto mount = f.commit(tree, {f.make(5, false, "relative", {outsideHost, overlay})});
    auto root = tree.getCurrentRevision().rootShadowNode;
    auto graph = discoverPortalContexts(*root);
    auto receipt = f.source->receiptForState(1, 14, delivered(mount, 14));
    auto nativeRoot = static_cast<const BottomSheetViewShadowNode*>(graph->nodes.at(24));
    expect("a static nativeOverlay before its first geometry report cannot inherit an outside containing block",
           data(nativeRoot->getState()).frameSize == facebook::react::Size{} &&
           nativeRoot->getLayoutMetrics().positionType == PositionType::Static &&
           graph->hostContexts.at(13) == 24 && !graph->contexts.at(24).parent &&
           receipt && receipt->contexts == std::vector<Tag>{24} &&
           receipt->ancestors == std::vector<Tag>{14, 13, 24});
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto area = f.make(11, false, "relative", {f.make(31, false, "static", {
        f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)})});
    LaterLayoutHook later(f.views, *f.context);
    f.manager->registerCommitHook(later);
    auto initial = delivered(f.commit(tree, {area}), 14);
    auto initialRoot = tree.getCurrentRevision().rootShadowNode;
    auto skippedRoot = f.enqueue(tree, initialRoot->getChildren());
    auto skippedState = indexPortalShadowNodes(*skippedRoot)->at(14)->getState();
    later.relative = true;
    auto finalRoot = f.enqueue(tree, skippedRoot->getChildren());
    auto coordinator = tree.getMountingCoordinator();
    auto baseBeforePull = coordinator->getBaseRevision().rootShadowNode;
    auto merged = coordinator->pullTransaction(true);
    auto mergedState = merged ? delivered(*merged, 14) : nullptr;
    auto receipt = f.source->receiptForState(1, 14, mergedState);
    expect("coalesced commits deliver the final source context without substituting a skipped revision",
           baseBeforePull == initialRoot && skippedRoot != finalRoot &&
           mergedState && mergedState != initial && mergedState != skippedState &&
           mergedState == indexPortalShadowNodes(*finalRoot)->at(14)->getState() &&
           f.source->rootForStates(1, {{14, mergedState}}) == finalRoot &&
           receipt && receipt->contexts == std::vector<Tag>{31} &&
           !coordinator->pullTransaction(true));
    f.manager->unregisterCommitHook(later);
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto area = f.make(11, false, "relative", {f.make(31, false, "static", {
        f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)})});
    LaterLayoutHook later(f.views, *f.context);
    f.manager->registerCommitHook(later);
    auto first = delivered(f.commit(tree, {area}), 14);
    auto firstRoot = tree.getCurrentRevision().rootShadowNode;
    later.relative = true;
    auto second = delivered(f.commit(tree, firstRoot->getChildren()), 14);
    auto secondRoot = tree.getCurrentRevision().rootShadowNode;
    auto thirdRoot = f.enqueue(tree, secondRoot->getChildren());
    auto coordinator = tree.getMountingCoordinator();
    auto firstReceipt = f.source->receiptForState(1, 14, first);
    auto secondReceipt = f.source->receiptForState(1, 14, second);
    expect("asynchronously pulled batches retain their exact source while both base and current revisions advance",
           first && second && first != second &&
           coordinator->getBaseRevision().rootShadowNode == secondRoot &&
           tree.getCurrentRevision().rootShadowNode == thirdRoot &&
           coordinator->hasPendingTransactions() &&
           f.source->rootForStates(1, {{14, first}}) == firstRoot &&
           f.source->rootForStates(1, {{14, second}}) == secondRoot &&
           firstReceipt && firstReceipt->contexts == std::vector<Tag>{11} &&
           secondReceipt && secondReceipt->contexts == std::vector<Tag>{31});
    f.manager->unregisterCommitHook(later);
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto area = f.make(11, false, "relative", {f.make(31, false, "static", {
        f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)})});
    LaterLayoutHook later(f.views, *f.context);
    f.manager->registerCommitHook(later);
    auto initial = delivered(f.commit(tree, {area}), 14);
    auto initialRoot = tree.getCurrentRevision().rootShadowNode;
    auto coordinator = tree.getMountingCoordinator();
    coordinator->didPerformAsyncTransactions();
    tree.setCommitMode(ShadowTree::CommitMode::Suspended);
    later.relative = true;
    f.enqueue(tree, initialRoot->getChildren());
    auto finalRoot = f.enqueue(tree, tree.getCurrentRevision().rootShadowNode->getChildren());
    auto oldReceipt = f.source->receiptForState(1, 14, initial);
    bool suspended = !coordinator->hasPendingTransactions() &&
        !coordinator->pullTransaction(true) &&
        coordinator->getBaseRevision().rootShadowNode == initialRoot &&
        finalRoot != initialRoot &&
        f.source->rootForStates(1, {{14, initial}}) == initialRoot &&
        oldReceipt && oldReceipt->contexts == std::vector<Tag>{11};
    tree.setCommitMode(ShadowTree::CommitMode::Normal);
    auto resumed = coordinator->pullTransaction(true);
    auto resumedState = resumed ? delivered(*resumed, 14) : nullptr;
    auto receipt = f.source->receiptForState(1, 14, resumedState);
    expect("suspended mounting keeps the delivered context until resumption supplies the final merged State",
           suspended && resumedState && resumedState != initial &&
           resumedState == indexPortalShadowNodes(*finalRoot)->at(14)->getState() &&
           f.source->rootForStates(1, {{14, resumedState}}) == finalRoot &&
           receipt && receipt->contexts == std::vector<Tag>{31});
    f.manager->unregisterCommitHook(later);
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto mount = f.commit(tree, {
        f.make(11, false, "relative", {f.make(12, true, "relative"),
            f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)}),
        f.make(21, false, "relative", {f.make(22, true, "relative"),
            f.make(23, true, "absolute", {f.make(24, true, "relative", {}, true)}, false, true)}),
        f.make(99, true, "relative")});
    PortalNativeMembers members{{1, 12, 13, 14, 22, 23, 24, 99},
        {{1, 0}, {12, 1}, {13, 1}, {14, 13}, {22, 1}, {23, 1}, {24, 23}, {99, 1}}};
    std::map<Tag, State::Shared> states{{14, delivered(mount, 14)}, {24, delivered(mount, 24)}};
    auto projection = f.source->isolationForStates(1, states, members, {{13, {14}}, {23, {24}}});
    expect("exact delivered source projects two flattened local contexts without excluding the outside toolbar",
           projection && projection->hiddenBranches == std::set<Tag>{12, 22} &&
           projection->retainedChildren == std::map<Tag, std::set<Tag>>{
               {11, {13}}, {13, {14}}, {21, {23}}, {23, {24}}});
    expect("source projection rejects nonhosts, nonsheets, crossed hosts and empty active retention",
           !f.source->isolationForStates(1, states, members, {{99, {14}}}) &&
           !f.source->isolationForStates(1, states, members, {{13, {99}}}) &&
           !f.source->isolationForStates(1, states, members, {{13, {24}}}) &&
           !f.source->isolationForStates(1, states, members, {{13, {}}}));
    auto released = f.source->isolationForStates(1, states, members, {});
    expect("an empty retained request releases a valid source without inventing an unavailable source",
           released && released->hiddenBranches.empty() && released->retainedChildren.empty() &&
           !f.source->isolationForStates(1, {}, members, {}));
  }

  {
    Fixture f;
    auto& tree = f.tree();
    auto area = f.make(11, false, "relative", {f.make(12, true, "relative"),
        f.make(31, false, "static", {f.make(32, true, "relative"),
            f.make(13, true, "absolute", {f.make(14, true, "relative", {}, true)}, false, true)})});
    LaterLayoutHook later(f.views, *f.context);
    f.manager->registerCommitHook(later);
    auto first = f.commit(tree, {area, f.make(34, true, "relative", {}, true)});
    auto firstRoot = tree.getCurrentRevision().rootShadowNode;
    later.relative = true;
    auto second = f.commit(tree, firstRoot->getChildren());
    PortalNativeMembers members{{1, 12, 13, 14, 32, 34},
        {{1, 0}, {12, 1}, {13, 1}, {14, 13}, {32, 1}, {34, 1}}};
    std::map<Tag, State::Shared> oldStates{{14, delivered(first, 14)}, {34, delivered(first, 34)}};
    std::map<Tag, State::Shared> newStates{{14, delivered(second, 14)}, {34, delivered(second, 34)}};
    auto delayed = f.source->isolationForStates(1, oldStates, members, {{13, {14}}});
    auto current = f.source->isolationForStates(1, newStates, members, {{13, {14}}});
    expect("projection keeps the delivered containing block while a newer source has a narrower boundary",
           delayed && delayed->hiddenBranches == std::set<Tag>{12, 32} &&
           current && current->hiddenBranches == std::set<Tag>{32});
    expect("projection requires exact complete States including nonportal sheets before resolving retained tags",
           !f.source->isolationForStates(1, {{14, newStates.at(14)}}, members, {{13, {14}}}) &&
           !f.source->isolationForStates(1, {{14, newStates.at(14)}, {34, oldStates.at(34)}}, members, {{13, {14}}}) &&
           !f.source->isolationForStates(2, newStates, members, {{13, {14}}}) &&
           !f.source->isolationForStates(1, newStates, members, {{13, {34}}}));
    f.manager->unregisterCommitHook(later);
  }

  std::cout << "STATE RESULT tests=" << tests << " failures=" << failures << '\n';
  return failures ? 1 : 0;
}
