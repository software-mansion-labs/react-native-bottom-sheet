#include "BottomSheetStateHelper.h"
#include "ShadowNodes.h"

#include <algorithm>

namespace facebook::react {

Point BottomSheetViewShadowNode::getContentOriginOffset(
    bool /*includeTransform*/) const {
  auto state =
      std::static_pointer_cast<const BottomSheetViewShadowNode::ConcreteState>(
          getState());
  // The horizontal offset belongs to the card's children, with its scale (see
  // BottomSheetCardContent).
  return {0, state->getData().contentOffsetY};
}

void BottomSheetViewShadowNode::appendChild(
    const std::shared_ptr<const ShadowNode>& child) {
  // React appends the children of a clone after `adopt` has run on it.
  ConcreteViewShadowNode::appendChild(withCardGeometry(child));
}

void BottomSheetViewShadowNode::syncCardContent() {
  // A copy: `replaceChild` swaps the list being iterated.
  auto children = getChildren();
  for (size_t index = 0; index < children.size(); index++) {
    const auto& child = children[index];
    auto synced = withCardGeometry(child);
    if (synced != child) {
      replaceChild(*child, synced, index);
    }
  }
}

std::shared_ptr<const ShadowNode> BottomSheetViewShadowNode::withCardGeometry(
    const std::shared_ptr<const ShadowNode>& child) const {
  auto content = bottomSheetCardContentOf(*child);
  const auto& state =
      static_cast<const ConcreteState&>(*getState()).getData();
  if (content == nullptr) {
    return child;
  }
  std::string_view name = child->getComponentName();
  Float bottomOverflow = name == BottomSheetSurfaceViewComponentName
      ? state.surfaceOverflow
      : std::max<Float>(-state.contentRegionInset, 0);
  if (content->hasCardGeometry(
          state.contentOffsetX, state.contentScale, bottomOverflow)) {
    return child;
  }
  // Props, state and layout stay shared, so the mounting layer sees no change,
  // unless the overflow moved, which needs a new layout pass.
  if (!content->hasBottomOverflow(bottomOverflow)) {
    yogaNode_.setDirty(true);
  }
  auto clone = child->clone({});
  bottomSheetCardContentOf(*clone)->setCardGeometry(
      state.contentOffsetX, state.contentScale, bottomOverflow);
  return clone;
}

void BottomSheetViewShadowNode::setIsOverlayRoot(bool isOverlayRoot) {
  // In native-overlay mode the sheet content is physically re-rooted to a
  // window-level full-screen overlay, decoupled from where this view sits in the
  // React tree. Marking the node as a layout root — exactly as <Modal> does —
  // makes `getRelativeLayoutMetrics` stop its ancestor walk here, so measure and
  // touch coordinates for descendants resolve relative to this full-screen root
  // instead of accumulating the (possibly nonzero) outer-tree origin. Inline that
  // accumulation is correct, so the trait is only set in overlay mode.
  if (isOverlayRoot) {
    traits_.set(ShadowNodeTraits::Trait::RootNodeKind);
  } else {
    traits_.unset(ShadowNodeTraits::Trait::RootNodeKind);
  }
}

void updateBottomSheetState(
    const State::Shared& state,
    float contentOffsetX,
    float contentOffsetY,
    float contentScale,
    Size frameSize,
    Float contentRegionInset,
    Float surfaceOverflow) {
  auto concreteState =
      std::static_pointer_cast<const BottomSheetViewShadowNode::ConcreteState>(
          state);
  concreteState->updateState(
      [contentOffsetX, contentOffsetY, contentScale, frameSize, contentRegionInset, surfaceOverflow](
          const BottomSheetViewState& /*oldState*/)
          -> BottomSheetViewShadowNode::ConcreteState::SharedData {
        // Every field is set: updates coalesce by wholesale replacement (see
        // BottomSheetStateHelper.h), so nothing may rely on the old state.
        auto newState = std::make_shared<BottomSheetViewState>();
        auto& mutableState = const_cast<BottomSheetViewState&>(*newState);
        mutableState.contentOffsetX = contentOffsetX;
        mutableState.contentOffsetY = contentOffsetY;
        mutableState.contentScale = contentScale;
        mutableState.frameSize = frameSize;
        mutableState.contentRegionInset = contentRegionInset;
        mutableState.surfaceOverflow = surfaceOverflow;
        return newState;
      });
}

} // namespace facebook::react
