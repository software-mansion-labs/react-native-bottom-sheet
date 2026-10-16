// Compile the real JNI resource in this test-only translation unit. Its native
// factory stays private to production; no testing entry point is added there.
#include "../../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/PortalPublicationSourceAndroid.cpp"
#include <react/renderer/components/ReactNativeBottomSheetSpec/ComponentDescriptors.h>
#include <react/renderer/components/ReactNativeBottomSheetSpec/PortalContextGraph.h>
#include <react/renderer/components/view/ViewComponentDescriptor.h>

using namespace facebook;
using namespace facebook::react;
using Children = std::vector<std::shared_ptr<const ShadowNode>>;

namespace {
class FabricPublicationJniFixture final : public jni::HybridClass<FabricPublicationJniFixture> {
 public:
  static constexpr auto kJavaDescriptor =
      "Lcom/swmansion/reactnativebottomsheet/accessibility/FabricPublicationJniFixture;";

  FabricPublicationJniFixture() {
    auto tree = std::make_unique<ShadowTree>(1,
        LayoutConstraints{.minimumSize = {800, 600}, .maximumSize = {800, 600}},
        LayoutContext{}, *manager_, *context_);
    tree_ = tree.get();
    manager_->getShadowTreeRegistry().add(std::move(tree));
    auto host = make(13, "absolute", {make(14, "relative", {}, true)}, false, true);
    auto area = make(11, "relative", {make(31, "static", {host})});
    commit([&](const RootShadowNode& root) {
      return std::static_pointer_cast<RootShadowNode>(root.ShadowNode::clone(
          {.children = std::make_shared<const Children>(Children{area})}));
    });
    tree_->getMountingCoordinator()->pullTransaction(true);
  }

  ~FabricPublicationJniFixture() override {
    manager_->getShadowTreeRegistry().remove(1);
  }

  static void registerNatives() {
    registerHybrid({
        makeNativeMethod("lease", FabricPublicationJniFixture::lease),
        makeNativeMethod(
            "takeDeliveredPublicationState", FabricPublicationJniFixture::takeDeliveredPublicationState),
        makeNativeMethod(
            "makeWrapperContainingBlock", FabricPublicationJniFixture::makeWrapperContainingBlock),
        makeNativeMethod(
            "hasNewerStateInSameFamily", FabricPublicationJniFixture::hasNewerStateInSameFamily),
        makeNativeMethod(
            "addSecondSheetAndTakeDeliveredStates", FabricPublicationJniFixture::addSecondSheetAndTakeDeliveredStates),
    });
  }

 private:
  std::shared_ptr<const ShadowNode> make(Tag tag, const char* position,
      Children children = {}, bool sheet = false, bool host = false) {
    const ComponentDescriptor& descriptor = sheet
        ? static_cast<const ComponentDescriptor&>(sheets_) : views_;
    folly::dynamic raw = folly::dynamic::object("position", position)("width", 300)("height", 560)
        ("collapsable", !sheet && !host);
    if (host) raw["nativeID"] = bottomsheet::PortalHostNativeID;
    auto props = descriptor.cloneProps(PropsParserContext(1, *context_), nullptr, RawProps(raw));
    auto family = descriptor.createFamily({.tag = tag, .surfaceId = 1, .instanceHandle = nullptr});
    return descriptor.createShadowNode({.props = props,
        .children = std::make_shared<const Children>(std::move(children)),
        .state = descriptor.createInitialState(props, family)}, family);
  }

  void commit(const ShadowTreeCommitTransaction& transaction) {
    auto status = tree_->commit(transaction, {});
    if (status != ShadowTreeCommitStatus::Succeeded) {
      throw std::runtime_error("Fixture commit failed");
    }
  }

  jni::local_ref<bottomsheet::FabricPortalPublication::jhybridobject> lease() {
    return bottomsheet::FabricPortalPublication::newObjectCxxArgs(manager_);
  }

  jni::local_ref<StateWrapper::jhybridobject> takeDeliveredPublicationState() {
    auto transaction = tree_->getMountingCoordinator()->pullTransaction(true);
    if (!transaction) return nullptr;
    for (const auto& mutation : transaction->getMutations()) {
      const auto& child = mutation.newChildShadowView;
      const auto* state =
          dynamic_cast<const BottomSheetViewShadowNode::ConcreteState*>(child.state.get());
      if (child.tag == 14 && state && !state->getData().portalPublicationId.empty()) {
        // Same SDK construction and State assignment as FabricMountingManager.
        auto wrapper = StateWrapperImpl::newObjectJavaArgs();
        wrapper->cthis()->setState(child.state);
        return jni::static_ref_cast<StateWrapper::jhybridobject>(wrapper);
      }
    }
    return nullptr;
  }

  void makeWrapperContainingBlock() {
    commit([&](const RootShadowNode& root) {
      const auto& wrapper = *bottomsheet::indexPortalShadowNodes(root)->at(31);
      auto props = views_.cloneProps(PropsParserContext(1, *context_), wrapper.getProps(),
          RawProps(folly::dynamic::object("position", "relative")));
      return std::static_pointer_cast<RootShadowNode>(root.cloneTree(wrapper.getFamily(),
          [&](const ShadowNode& node) { return node.clone({.props = props}); }));
    });
  }

  jni::local_ref<jni::JArrayClass<StateWrapper::javaobject>::javaobject>
  addSecondSheetAndTakeDeliveredStates() {
    auto second = make(15, "relative", {}, true);
    commit([&](const RootShadowNode& root) {
      const auto& host = *bottomsheet::indexPortalShadowNodes(root)->at(13);
      auto children = host.getChildren();
      children.push_back(second);
      return std::static_pointer_cast<RootShadowNode>(root.cloneTree(host.getFamily(),
          [&](const ShadowNode& node) {
            return node.clone({.children = std::make_shared<const Children>(children)});
          }));
    });
    auto transaction = tree_->getMountingCoordinator()->pullTransaction(true);
    std::map<Tag, State::Shared> states;
    if (transaction) {
      for (const auto& mutation : transaction->getMutations()) {
        const auto& child = mutation.newChildShadowView;
        if ((child.tag == 14 || child.tag == 15) && child.state) {
          states.insert_or_assign(child.tag, child.state);
        }
      }
    }
    if (states.size() != 2) throw std::runtime_error("Missing two delivered sheet States");
    auto result = jni::JArrayClass<StateWrapper::javaobject>::newArray(states.size());
    size_t index = 0;
    for (const auto& [tag, state] : states) {
      auto wrapper = StateWrapperImpl::newObjectJavaArgs();
      wrapper->cthis()->setState(state);
      result->setElement(index++, jni::static_ref_cast<StateWrapper::jhybridobject>(wrapper).get());
    }
    return result;
  }

  jboolean hasNewerStateInSameFamily(
      jni::alias_ref<StateWrapper::javaobject> delivered,
      jni::alias_ref<StateWrapper::javaobject> current) {
    auto oldState =
        jni::static_ref_cast<StateWrapperImpl::jhybridobject>(delivered)->cthis()->getState();
    auto newState =
        jni::static_ref_cast<StateWrapperImpl::jhybridobject>(current)->cthis()->getState();
    return oldState && newState && oldState != newState && oldState->getMostRecentState() == newState;
  }

  std::shared_ptr<ContextContainer> context_ = std::make_shared<ContextContainer>();
  std::shared_ptr<UIManager> manager_ = std::make_shared<UIManager>(RuntimeExecutor{}, context_);
  ViewComponentDescriptor views_{{.eventDispatcher = {}, .contextContainer = context_, .flavor = nullptr}};
  BottomSheetViewComponentDescriptor sheets_{{.eventDispatcher = {}, .contextContainer = context_, .flavor = nullptr}};
  ShadowTree* tree_ = nullptr;
};
} // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_swmansion_reactnativebottomsheet_accessibility_FabricPublicationJniFixture_create(
    JNIEnv*, jclass) {
  try {
    FabricPublicationJniFixture::registerNatives();
    return FabricPublicationJniFixture::newObjectCxxArgs().release();
  } catch (...) {
    jni::translatePendingCppExceptionToJavaException();
    return nullptr;
  }
}
