#ifdef ANDROID
#include "PortalPublicationSource.h"
#include <fbjni/fbjni.h>
#include <react/fabric/Binding.h>
#include <react/fabric/JFabricUIManager.h>
#include <react/fabric/StateWrapperImpl.h>
#include <react/renderer/scheduler/Scheduler.h>

namespace facebook::react::bottomsheet {
namespace {
#if __has_include(<react/fabric/FabricUIManagerBinding.h>)
using NativeBinding = FabricUIManagerBinding;
#else
using NativeBinding = Binding;
#endif

struct JFabricPortalReceipt : jni::JavaClass<JFabricPortalReceipt> {
  static constexpr auto kJavaDescriptor =
      "Lcom/swmansion/reactnativebottomsheet/accessibility/FabricPortalReceipt;";

  static jni::local_ref<JFabricPortalReceipt> create(const PortalSourceReceipt& receipt) {
    auto contexts = jni::JArrayInt::newArray(receipt.contexts.size());
    contexts->setRegion(0, receipt.contexts.size(), receipt.contexts.data());
    auto ancestors = jni::JArrayInt::newArray(receipt.ancestors.size());
    ancestors->setRegion(0, receipt.ancestors.size(), receipt.ancestors.data());
    auto tags = jni::JArrayInt::newArray(receipt.logicalTags.size());
    tags->setRegion(0, receipt.logicalTags.size(), receipt.logicalTags.data());
    std::vector<jint> edges;
    for (const auto& [child, parent] : receipt.nativeParents) {
      edges.push_back(child);
      edges.push_back(parent);
    }
    auto parents = jni::JArrayInt::newArray(edges.size());
    parents->setRegion(0, edges.size(), edges.data());
    return newInstance(
        jni::make_jstring(receipt.publicationId), static_cast<jint>(receipt.host),
        contexts, ancestors, tags, parents);
  }
};

struct JFabricPortalIsolationProjection : jni::JavaClass<JFabricPortalIsolationProjection> {
  static constexpr auto kJavaDescriptor =
      "Lcom/swmansion/reactnativebottomsheet/accessibility/FabricPortalIsolationProjection;";

  static jni::local_ref<JFabricPortalIsolationProjection> create(const PortalIsolationProjection& projection) {
    std::vector<jint> hidden(projection.hiddenBranches.begin(), projection.hiddenBranches.end());
    auto branches = jni::JArrayInt::newArray(hidden.size());
    branches->setRegion(0, hidden.size(), hidden.data());
    std::vector<jint> edges;
    for (const auto& [parent, children] : projection.retainedChildren) {
      for (Tag child : children) {
        edges.push_back(parent);
        edges.push_back(child);
      }
    }
    auto retained = jni::JArrayInt::newArray(edges.size());
    retained->setRegion(0, edges.size(), edges.data());
    return newInstance(branches, retained);
  }
};

class FabricPortalPublication final : public jni::HybridClass<FabricPortalPublication> {
 public:
  static constexpr auto kJavaDescriptor =
      "Lcom/swmansion/reactnativebottomsheet/accessibility/FabricPortalPublication;";

  explicit FabricPortalPublication(const std::shared_ptr<UIManager>& manager)
      : source_(manager) {}

  static void registerNatives() {
    registerHybrid({
        makeNativeMethod("initHybrid", FabricPortalPublication::initHybrid),
        makeNativeMethod("bootstrapNative", FabricPortalPublication::bootstrapNative),
        makeNativeMethod("sheetTagsNative", FabricPortalPublication::sheetTagsNative),
        makeNativeMethod("receiptNative", FabricPortalPublication::receiptNative),
        makeNativeMethod("isolationNative", FabricPortalPublication::isolationNative),
    });
  }

 private:
  static jni::local_ref<jhybriddata> initHybrid(
      jni::alias_ref<jclass>, jni::alias_ref<JFabricUIManager::javaobject> manager) {
    // JFabricUIManager::getBinding dereferences mBinding without a null guard.
    // Keep the actual binding object alive while reading its scheduler.
    static const auto field = JFabricUIManager::javaClassStatic()->getField<NativeBinding::javaobject>("mBinding");
    auto binding = manager->getFieldValue(field);
    if (!binding) return nullptr;
    auto scheduler = binding->cthis()->getScheduler();
    return scheduler ? makeCxxInstance(scheduler->getUIManager()) : nullptr;
  }

  void bootstrapNative() {
    source_.bootstrap();
  }

  static State::Shared deliveredState(jni::alias_ref<StateWrapper::javaobject> object) {
    if (!object || !object->isInstanceOf(StateWrapperImpl::javaClassStatic())) return nullptr;
    StateWrapperImpl* wrapper;
    try {
      wrapper = jni::static_ref_cast<StateWrapperImpl::jhybridobject>(object)->cthis();
    } catch (const jni::JniException& exception) {
      // fbjni reports a reset hybrid pointer as NPE. Other JNI failures surface.
      if (!exception.getThrowable()->isInstanceOf(jni::findClassLocal("java/lang/NullPointerException"))) throw;
      return nullptr;
    }
    // The delivered State must not be replaced by the family's newest State.
    return wrapper->getState();
  }

  static std::optional<std::map<Tag, State::Shared>> deliveredStates(
      jni::alias_ref<jintArray> tags,
      jni::alias_ref<jni::JArrayClass<StateWrapper::javaobject>::javaobject> objects) {
    if (!tags || !objects || tags->size() == 0 || tags->size() != objects->size()) return std::nullopt;
    std::vector<jint> values(tags->size());
    tags->getRegion(0, values.size(), values.data());
    std::map<Tag, State::Shared> states;
    for (size_t index = 0; index < values.size(); ++index) {
      auto state = deliveredState(objects->getElement(index));
      if (!state || !states.emplace(values[index], std::move(state)).second) return std::nullopt;
    }
    return states;
  }

  jni::local_ref<jintArray> sheetTagsNative(
      jint surface, jint tag, jni::alias_ref<StateWrapper::javaobject> object) {
    auto tags = source_.sheetTagsForState(surface, tag, deliveredState(object));
    if (!tags) return nullptr;
    auto result = jni::JArrayInt::newArray(tags->size());
    result->setRegion(0, tags->size(), tags->data());
    return result;
  }

  jni::local_ref<JFabricPortalReceipt> receiptNative(
      jint surface, jint tag, jni::alias_ref<jintArray> tags,
      jni::alias_ref<jni::JArrayClass<StateWrapper::javaobject>::javaobject> objects) {
    auto states = deliveredStates(tags, objects);
    if (!states) return nullptr;
    auto selected = states->find(tag);
    if (selected == states->end() || !source_.rootForStates(surface, *states)) return nullptr;
    auto receipt = source_.receiptForState(surface, tag, selected->second);
    if (!receipt) return nullptr;
    return JFabricPortalReceipt::create(*receipt);
  }

  jni::local_ref<JFabricPortalIsolationProjection> isolationNative(
      jint surface, jni::alias_ref<jintArray> tags,
      jni::alias_ref<jni::JArrayClass<StateWrapper::javaobject>::javaobject> objects,
      jni::alias_ref<jintArray> parentPairs,
      jni::alias_ref<jni::JArrayClass<jintArray>::javaobject> retainedGroups) {
    auto states = deliveredStates(tags, objects);
    if (!states || !parentPairs || parentPairs->size() % 2 != 0 || !retainedGroups) return nullptr;
    std::vector<jint> parents(parentPairs->size());
    parentPairs->getRegion(0, parents.size(), parents.data());
    PortalNativeMembers members;
    for (size_t index = 0; index < parents.size(); index += 2) {
      if (!members.parents.emplace(parents[index], parents[index + 1]).second) return nullptr;
      members.alive.insert(parents[index]);
    }
    std::map<Tag, std::vector<Tag>> retained;
    for (size_t index = 0; index < retainedGroups->size(); ++index) {
      auto group = retainedGroups->getElement(index);
      if (!group || group->size() == 0) return nullptr;
      std::vector<jint> row(group->size());
      group->getRegion(0, row.size(), row.data());
      if (!retained.emplace(row.front(), std::vector<Tag>(row.begin() + 1, row.end())).second) return nullptr;
    }
    auto projection = source_.isolationForStates(surface, *states, members, retained);
    return projection ? JFabricPortalIsolationProjection::create(*projection) : nullptr;
  }

  PortalPublicationSource source_;
};
} // namespace
} // namespace facebook::react::bottomsheet

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  return facebook::jni::initialize(vm, [] {
    facebook::react::bottomsheet::FabricPortalPublication::registerNatives();
  });
}
#endif
