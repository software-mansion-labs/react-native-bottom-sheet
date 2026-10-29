#pragma once

#import <React/RCTComponentViewRegistry.h>
#import <React/RCTMountingManager.h>
#import <React/RCTViewComponentView.h>
#import <React/RCTModalHostViewComponentView.h>
#import "../../../../ios/BottomSheetPortalMountingObserver.h"
#include <react/renderer/components/view/ViewComponentDescriptor.h>
#include <react/renderer/components/modal/ModalHostViewComponentDescriptor.h>

namespace facebook::react::bottomsheet::tests {
using Children = std::vector<std::shared_ptr<const ShadowNode>>;
struct RuntimeFixture {
  std::shared_ptr<const ContextContainer> context = std::make_shared<ContextContainer>();
  std::shared_ptr<UIManager> uiManager = std::make_shared<UIManager>(RuntimeExecutor{}, context);
  ViewComponentDescriptor descriptor{{.eventDispatcher = {}, .contextContainer = context,
                                      .flavor = nullptr}};
  ModalHostViewComponentDescriptor modalDescriptor{
      {.eventDispatcher = {}, .contextContainer = context, .flavor = nullptr}};
  ShadowTree *tree;
  RCTMountingManager *__strong mountingManager = [RCTMountingManager new];
  UIView *__strong viewport = [UIView new];

  RuntimeFixture() {
    auto ownedTree = std::make_unique<ShadowTree>(1,
        LayoutConstraints{.minimumSize = {800, 600}, .maximumSize = {800, 600}},
        LayoutContext{}, *uiManager, *context);
    tree = ownedTree.get();
    uiManager->getShadowTreeRegistry().add(std::move(ownedTree));
    [mountingManager.componentViewRegistry.componentViewFactory
        registerComponentViewClass:RCTViewComponentView.class];
    [mountingManager.componentViewRegistry.componentViewFactory
        registerComponentViewClass:RCTModalHostViewComponentView.class];
    [mountingManager attachSurfaceToView:viewport surfaceId:1];
  }
  ~RuntimeFixture() {
    [mountingManager detachSurfaceFromView:viewport surfaceId:1];
    uiManager->getShadowTreeRegistry().remove(1);
  }

  std::shared_ptr<ViewShadowNode> node(Tag tag, Children children = {}, bool host = false) {
    folly::dynamic raw = folly::dynamic::object("collapsable", false)
        ("position", "absolute")("width", 300)("height", 210);
    if (host) raw["nativeID"] = PortalHostNativeID;
    auto props = descriptor.cloneProps(PropsParserContext(1, *context), nullptr,
                                       RawProps(std::move(raw)));
    auto family = descriptor.createFamily({.tag = tag, .surfaceId = 1, .instanceHandle = nullptr});
    return std::static_pointer_cast<ViewShadowNode>(descriptor.createShadowNode(
        {.props = props, .children = std::make_shared<const Children>(std::move(children))}, family));
  }
  RootShadowNode::Shared mount(Children children) {
    tree->commit([&](const RootShadowNode& current) {
      return std::static_pointer_cast<RootShadowNode>(current.ShadowNode::clone(
          {.children = std::make_shared<const Children>(std::move(children))}));
    }, {});
    [mountingManager scheduleTransaction:tree->getMountingCoordinator()];
    return tree->getMountingCoordinator()->getBaseRevision().rootShadowNode;
  }
  std::shared_ptr<ModalHostViewShadowNode> modal(Tag tag, Children children) {
    auto props = modalDescriptor.cloneProps(PropsParserContext(1, *context), nullptr,
        RawProps(folly::dynamic::object("visible", false)));
    auto family = modalDescriptor.createFamily(
        {.tag = tag, .surfaceId = 1, .instanceHandle = nullptr});
    auto state = std::make_shared<ModalHostViewShadowNode::ConcreteState>(
        std::make_shared<const ModalHostViewState>(facebook::react::Size{800, 600}), family);
    return std::static_pointer_cast<ModalHostViewShadowNode>(modalDescriptor.createShadowNode(
        {.props = props, .children = std::make_shared<const Children>(std::move(children)),
         .state = state}, family));
  }
  BottomSheetPortalMountingObserver *observer() {
    std::weak_ptr<UIManager> current = uiManager;
    return [[BottomSheetPortalMountingObserver alloc] initWithMountingManager:mountingManager
        uiManagerProvider:^{ return current.lock(); } beforeDeletingNativeSubtrees:nil];
  }
  UIView *view(Tag tag) {
    return [mountingManager.componentViewRegistry findComponentViewWithTag:tag];
  }
};
} // namespace facebook::react::bottomsheet::tests
