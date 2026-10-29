#pragma once

#ifdef ANDROID
#include "BottomSheetViewState.h"
#include <react/renderer/core/ConcreteState.h>
#include <atomic>

namespace facebook::react {

// Identity belongs to the State object: distinct States may share StateData.
class BottomSheetViewConcreteState final : public ConcreteState<BottomSheetViewState> {
  using Base = ConcreteState<BottomSheetViewState>;

 public:
  using Base::Base;
  BottomSheetViewConcreteState(const BottomSheetViewConcreteState&) = delete;
  BottomSheetViewConcreteState& operator=(const BottomSheetViewConcreteState&) = delete;

  const std::string& portalStateId() const { return portalStateId_; }

  // Overrides State serialization on Android. Other RN SDK targets omit the
  // virtual serialization interface, while retaining the same State data type.
  folly::dynamic getDynamic() const {
    auto result = getData().getDynamic();
    result["portalStateId"] = portalStateId_;
    return result;
  }

 private:
  inline static std::atomic<uint64_t> nextIdentity_{0};
  const std::string portalStateId_{std::to_string(++nextIdentity_)};
};

} // namespace facebook::react
#endif
