#pragma once

#include <jsi/jsi.h>
#include <react/renderer/components/ReactNativeBottomSheetSpec/BottomSheetContentWrapperViewState.h>
#include <react/renderer/components/ReactNativeBottomSheetSpec/BottomSheetViewState.h>
#include <react/renderer/components/ReactNativeBottomSheetSpec/EventEmitters.h>
#include <react/renderer/components/ReactNativeBottomSheetSpec/Props.h>
#include <react/renderer/components/view/ConcreteViewShadowNode.h>
#include <react/renderer/core/StateData.h>

namespace facebook::react {

JSI_EXPORT extern const char BottomSheetViewComponentName[];

class JSI_EXPORT BottomSheetViewShadowNode final
    : public ConcreteViewShadowNode<
          BottomSheetViewComponentName,
          BottomSheetViewProps,
          BottomSheetViewEventEmitter,
          BottomSheetViewState> {
  using ConcreteViewShadowNode::ConcreteViewShadowNode;

 public:
  Point getContentOriginOffset(bool includeTransform) const override;

  // Toggles the `RootNodeKind` trait so a native-overlay sheet re-roots its
  // descendants' layout coordinate space (see the definition). Driven from props
  // by the component descriptor's `adopt`.
  void setIsOverlayRoot(bool isOverlayRoot);
};

JSI_EXPORT extern const char BottomSheetSurfaceViewComponentName[];

// The surface needs no custom shadow-node behavior, so this mirrors the codegen
// alias exactly (state is the default StateData). It only lives here because the
// custom header search path shadows the generated ShadowNodes.h.
using BottomSheetSurfaceViewShadowNode = ConcreteViewShadowNode<
    BottomSheetSurfaceViewComponentName,
    BottomSheetSurfaceViewProps,
    BottomSheetSurfaceViewEventEmitter,
    StateData>;

JSI_EXPORT extern const char BottomSheetContentWrapperViewComponentName[];

// Content wrapper whose size, in native-overlay mode, is forced from native-
// pushed state (overlay width × detent cap) by its component descriptor, so
// the content subtree lays out against measured geometry instead of
// JS-estimated dimensions.
using BottomSheetContentWrapperViewShadowNode = ConcreteViewShadowNode<
    BottomSheetContentWrapperViewComponentName,
    BottomSheetContentWrapperViewProps,
    BottomSheetContentWrapperViewEventEmitter,
    BottomSheetContentWrapperViewState>;

} // namespace facebook::react
