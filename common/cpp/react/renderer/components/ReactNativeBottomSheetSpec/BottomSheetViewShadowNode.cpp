#include "BottomSheetStateHelper.h"
#include "ShadowNodes.h"

namespace facebook::react {

Point BottomSheetViewShadowNode::getContentOriginOffset(
    bool /*includeTransform*/) const {
  auto state =
      std::static_pointer_cast<const BottomSheetViewShadowNode::ConcreteState>(
          getState());
  const auto& data = state->getData();
  return {data.contentOffsetX, data.contentOffsetY};
}

void updateBottomSheetContentOffset(
    const State::Shared& state,
    float contentOffsetX,
    float contentOffsetY) {
  auto concreteState =
      std::static_pointer_cast<const BottomSheetViewShadowNode::ConcreteState>(
          state);
  concreteState->updateState(
      [contentOffsetX, contentOffsetY](const BottomSheetViewState& /*oldState*/)
          -> BottomSheetViewShadowNode::ConcreteState::SharedData {
        auto newState = std::make_shared<BottomSheetViewState>();
        const_cast<BottomSheetViewState&>(*newState).contentOffsetX =
            contentOffsetX;
        const_cast<BottomSheetViewState&>(*newState).contentOffsetY =
            contentOffsetY;
        return newState;
      });
}

} // namespace facebook::react
