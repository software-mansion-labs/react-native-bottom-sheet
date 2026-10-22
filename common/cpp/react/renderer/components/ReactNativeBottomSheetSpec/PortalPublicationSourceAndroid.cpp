#ifdef ANDROID
#include "PortalPublicationSource.h"
#include <fbjni/fbjni.h>
#include <react/fabric/Binding.h>
#include <react/fabric/JFabricUIManager.h>
#ifdef BOTTOM_SHEET_DIRECT_STATE_ACCESS
#include <react/fabric/StateWrapperImpl.h>
#endif
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
        makeNativeMethod("usesDirectStateNative", FabricPortalPublication::usesDirectStateNative),
        makeNativeMethod("sheetTagsNative", FabricPortalPublication::sheetTagsNative),
        makeNativeMethod("receiptNative", FabricPortalPublication::receiptNative),
        makeNativeMethod("contextPathNative", FabricPortalPublication::contextPathNative),
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

  jboolean usesDirectStateNative() {
#ifdef BOTTOM_SHEET_DIRECT_STATE_ACCESS
    return true;
#else
    return false;
#endif
  }

  State::Shared deliveredState([[maybe_unused]] jint surface, jni::alias_ref<jobject> object) {
#ifdef BOTTOM_SHEET_DIRECT_STATE_ACCESS
    if (!object || !object->isInstanceOf(StateWrapperImpl::javaClassStatic())) return nullptr;
    try {
      return jni::static_ref_cast<StateWrapperImpl::jhybridobject>(object)->cthis()->getState();
    } catch (const jni::JniException& exception) {
      if (!exception.getThrowable()->isInstanceOf(jni::findClassLocal("java/lang/NullPointerException"))) throw;
      return nullptr;
    }
#else
    if (!object || !object->isInstanceOf(jni::JString::javaClassStatic())) return nullptr;
    return source_.stateForToken(surface, jni::static_ref_cast<jstring>(object)->toStdString());
#endif
  }

  std::optional<std::map<Tag, State::Shared>> deliveredStates(
      jint surface,
      jni::alias_ref<jintArray> tags,
      jni::alias_ref<jni::JArrayClass<jobject>::javaobject> objects) {
    if (!tags || !objects || tags->size() == 0 || tags->size() != objects->size()) return std::nullopt;
    std::vector<jint> values(tags->size());
    tags->getRegion(0, values.size(), values.data());
    std::map<Tag, State::Shared> states;
    for (size_t index = 0; index < values.size(); ++index) {
      auto state = deliveredState(surface, objects->getElement(index));
      if (!state || !states.emplace(values[index], std::move(state)).second) return std::nullopt;
    }
    return states;
  }

  jni::local_ref<jintArray> sheetTagsNative(
      jint surface, jint tag, jni::alias_ref<jobject> object) {
    auto tags = source_.sheetTagsForState(surface, tag, deliveredState(surface, object));
    if (!tags) return nullptr;
    auto result = jni::JArrayInt::newArray(tags->size());
    result->setRegion(0, tags->size(), tags->data());
    return result;
  }

  jni::local_ref<JFabricPortalReceipt> receiptNative(
      jint surface, jint tag, jni::alias_ref<jintArray> tags,
      jni::alias_ref<jni::JArrayClass<jobject>::javaobject> objects) {
    auto states = deliveredStates(surface, tags, objects);
    if (!states) return nullptr;
    auto selected = states->find(tag);
    if (selected == states->end() || !source_.rootForStates(surface, *states)) return nullptr;
    auto receipt = source_.receiptForState(surface, tag, selected->second);
    if (!receipt) return nullptr;
    return JFabricPortalReceipt::create(*receipt);
  }

  jni::local_ref<jintArray> contextPathNative(
      jint surface, jint tag, jni::alias_ref<jintArray> tags,
      jni::alias_ref<jni::JArrayClass<jobject>::javaobject> objects) {
    auto states = deliveredStates(surface, tags, objects);
    if (!states) return nullptr;
    auto path = source_.contextPathForStates(surface, *states, tag);
    if (!path) return nullptr;
    auto result = jni::JArrayInt::newArray(path->size());
    result->setRegion(0, path->size(), path->data());
    return result;
  }

  jni::local_ref<JFabricPortalIsolationProjection> isolationNative(
      jint surface, jni::alias_ref<jintArray> tags,
      jni::alias_ref<jni::JArrayClass<jobject>::javaobject> objects,
      jni::alias_ref<jintArray> parentPairs,
      jni::alias_ref<jni::JArrayClass<jintArray>::javaobject> retainedGroups) {
    auto states = deliveredStates(surface, tags, objects);
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
