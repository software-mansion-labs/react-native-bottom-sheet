#pragma once

#include <jsi/jsi.h>
#include <react/renderer/components/ReactNativeBottomSheetSpec/BottomSheetViewState.h>
#include <react/renderer/components/ReactNativeBottomSheetSpec/EventEmitters.h>
#include <react/renderer/components/ReactNativeBottomSheetSpec/Props.h>
#include <react/renderer/components/view/ConcreteViewShadowNode.h>
#include <react/renderer/core/StateData.h>
#include <yoga/YGNodeStyle.h>

#include <string_view>

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

  // Hands each appended child the card's current geometry, as `adopt` does for
  // a clone that already has its children (see `syncCardContent`).
  void appendChild(const std::shared_ptr<const ShadowNode>& child) override;

  // Toggles the `RootNodeKind` trait so a native-overlay sheet re-roots its
  // descendants' layout coordinate space (see the definition). Driven from props
  // by the component descriptor's `adopt`.
  void setIsOverlayRoot(bool isOverlayRoot);

  // Replaces each child that follows the card (the surface and the content
  // wrapper) with a clone carrying the card's geometry from this node's state.
  // Called from the component descriptor's `adopt`, so it runs whenever the
  // state or the children change.
  void syncCardContent();

 private:
  std::shared_ptr<const ShadowNode> withCardGeometry(
      const std::shared_ptr<const ShadowNode>& child) const;
};

JSI_EXPORT extern const char BottomSheetSurfaceViewComponentName[];

// The part of a child of the sheet that follows the card: the surface and the
// content wrapper, which the native host scales and shifts with layer
// transforms. Reporting that as the child's transform, instead of the sheet's,
// lets `measure` include it in native-overlay mode too, where the sheet is a
// layout root and `measure` skips a root's transform.
//
// The sheet's state holds the geometry, and a node can't reach its parent's
// state, so the sheet copies it into its children whenever either changes (see
// `BottomSheetViewShadowNode::syncCardContent`). That costs no state updates of
// its own: the copy rides the sheet's.
//
// The sheet finds these children by component name rather than `dynamic_cast`:
// on Android the descriptors are instantiated in the app's autolinking library,
// whose hidden type info `dynamic_cast` can't match across the library boundary.
class BottomSheetCardContent {
 public:
  // Which child this is, and so how it extends below the sheet.
  enum class Kind {
    // Stretched between its offsets, which ignores margins.
    Surface,
    // In flow, where a negative margin grows it.
    ContentWrapper,
  };

  virtual Kind kind() const = 0;

  bool hasCardGeometry(Float offsetX, Float scale, Float bottomOverflow) const {
    return cardOffsetX_ == offsetX && cardScale_ == scale &&
        bottomOverflow_ == bottomOverflow;
  }

  bool hasBottomOverflow(Float bottomOverflow) const {
    return bottomOverflow_ == bottomOverflow;
  }

  void setCardGeometry(Float offsetX, Float scale, Float bottomOverflow) {
    cardOffsetX_ = offsetX;
    cardScale_ = scale;
    if (bottomOverflow_ != bottomOverflow) {
      bottomOverflow_ = bottomOverflow;
      applyBottomOverflow();
    }
  }

 protected:
  BottomSheetCardContent() = default;
  BottomSheetCardContent(const BottomSheetCardContent&) = default;
  ~BottomSheetCardContent() = default;

  // Extends the child's Yoga box below the sheet by `bottomOverflow_`: scaled
  // down, it would otherwise end above a narrow card's bottom edge.
  virtual void applyBottomOverflow() = 0;

  Float bottomOverflow_{0};

  // Scales the child about the sheet's top-left corner and shifts it right by
  // the offset, expressed about the child's own center, which is where layout
  // metrics apply a transform: p ↦ center + scale · (p - center) + t equals
  // scale · p + offset when t = offset - (1 - scale) · center.
  Transform cardTransform(const Rect& frame) const {
    if (cardScale_ == 1 && cardOffsetX_ == 0) {
      return Transform::Identity();
    }
    auto center = frame.getCenter();
    auto transform = Transform::Scale(cardScale_, cardScale_, 1);
    transform.matrix[12] = cardOffsetX_ - (1 - cardScale_) * center.x;
    transform.matrix[13] = -(1 - cardScale_) * center.y;
    return transform;
  }

 private:
  Float cardOffsetX_{0};
  Float cardScale_{1};
};

// A child of the sheet that follows the card. Fabric never mounts a shadow
// node's `getTransform`, so this only reaches `measure` and the like; the host
// transforms the native views itself.
template <
    const char* concreteComponentName,
    typename ViewPropsT,
    typename ViewEventEmitterT,
    BottomSheetCardContent::Kind cardContentKind>
class BottomSheetCardContentShadowNode
    : public ConcreteViewShadowNode<
          concreteComponentName,
          ViewPropsT,
          ViewEventEmitterT,
          StateData>,
      public BottomSheetCardContent {
  using Base = ConcreteViewShadowNode<
      concreteComponentName,
      ViewPropsT,
      ViewEventEmitterT,
      StateData>;

 public:
  BottomSheetCardContentShadowNode(
      const ShadowNodeFragment& fragment,
      const ShadowNodeFamily::Shared& family,
      ShadowNodeTraits traits)
      : Base(fragment, family, traits) {}

  // A clone keeps the geometry; the sheet replaces it when it changes.
  BottomSheetCardContentShadowNode(
      const ShadowNode& sourceShadowNode,
      const ShadowNodeFragment& fragment)
      : Base(sourceShadowNode, fragment),
        BottomSheetCardContent(
            static_cast<const BottomSheetCardContentShadowNode&>(
                sourceShadowNode)) {
    // New props rebuild the Yoga style, dropping the overflow.
    if (fragment.props && bottomOverflow_ != 0) {
      applyBottomOverflow();
    }
  }

  Kind kind() const override {
    return cardContentKind;
  }

  // These views are the library's own and never take a style transform, so the
  // card's is the whole of it.
  Transform getTransform() const override {
    return cardTransform(this->getLayoutMetrics().frame);
  }

 protected:
  void applyBottomOverflow() override {
    if (cardContentKind == Kind::Surface) {
      YGNodeStyleSetPosition(&this->yogaNode_, YGEdgeBottom, -bottomOverflow_);
    } else {
      YGNodeStyleSetMargin(&this->yogaNode_, YGEdgeBottom, -bottomOverflow_);
    }
  }
};

// The surface lives here rather than in the generated ShadowNodes.h, which the
// custom header search path shadows.
using BottomSheetSurfaceViewShadowNode = BottomSheetCardContentShadowNode<
    BottomSheetSurfaceViewComponentName,
    BottomSheetSurfaceViewProps,
    BottomSheetSurfaceViewEventEmitter,
    BottomSheetCardContent::Kind::Surface>;

JSI_EXPORT extern const char BottomSheetContentWrapperViewComponentName[];

using BottomSheetContentWrapperViewShadowNode = BottomSheetCardContentShadowNode<
    BottomSheetContentWrapperViewComponentName,
    BottomSheetContentWrapperViewProps,
    BottomSheetContentWrapperViewEventEmitter,
    BottomSheetCardContent::Kind::ContentWrapper>;

// The card content of a child of the sheet, or null for any other child.
inline const BottomSheetCardContent* bottomSheetCardContentOf(
    const ShadowNode& node) {
  std::string_view name = node.getComponentName();
  if (name == BottomSheetSurfaceViewComponentName) {
    return &static_cast<const BottomSheetSurfaceViewShadowNode&>(node);
  }
  if (name == BottomSheetContentWrapperViewComponentName) {
    return &static_cast<const BottomSheetContentWrapperViewShadowNode&>(node);
  }
  return nullptr;
}

inline BottomSheetCardContent* bottomSheetCardContentOf(ShadowNode& node) {
  return const_cast<BottomSheetCardContent*>(
      bottomSheetCardContentOf(static_cast<const ShadowNode&>(node)));
}

} // namespace facebook::react
