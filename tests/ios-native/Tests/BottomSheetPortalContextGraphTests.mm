#import <XCTest/XCTest.h>

#include "../../../common/cpp/react/renderer/components/ReactNativeBottomSheetSpec/PortalContextGraph.h"
#include <react/renderer/components/view/ViewComponentDescriptor.h>
#include <react/renderer/components/modal/ModalHostViewComponentDescriptor.h>
#include <react/renderer/core/ShadowNodeFragment.h>

using namespace facebook::react;
using namespace facebook::react::bottomsheet;

namespace {
using Children = std::vector<std::shared_ptr<const ShadowNode>>;

ViewComponentDescriptor descriptor({
    .eventDispatcher = {},
    .contextContainer = std::make_shared<ContextContainer>(),
    .flavor = nullptr,
});

ModalHostViewComponentDescriptor modalDescriptor({
    .eventDispatcher = {},
    .contextContainer = std::make_shared<ContextContainer>(),
    .flavor = nullptr,
});

std::shared_ptr<ModalHostViewShadowNode> modal(Tag tag, Children children) {
  auto family = modalDescriptor.createFamily(
      {.tag = tag, .surfaceId = 1, .instanceHandle = nullptr});
  auto state = std::make_shared<ModalHostViewShadowNode::ConcreteState>(
      std::make_shared<const ModalHostViewState>(facebook::react::Size{800, 600}), family);
  return std::static_pointer_cast<ModalHostViewShadowNode>(
      modalDescriptor.createShadowNode(
          {.props = std::make_shared<ModalHostViewProps>(),
           .children = std::make_shared<const Children>(std::move(children)),
           .state = state}, family));
}

std::shared_ptr<ViewShadowNode> node(
    Tag tag,
    Children children = {},
    bool concrete = true,
    bool host = false) {
  auto props = std::make_shared<ViewProps>();
  props->collapsable = !concrete;
  if (host) props->nativeId = PortalHostNativeID;
  auto family = descriptor.createFamily(
      {.tag = tag, .surfaceId = 1, .instanceHandle = nullptr});
  auto result = std::static_pointer_cast<ViewShadowNode>(
      descriptor.createShadowNode(
          {.props = props,
           .children = std::make_shared<const Children>(std::move(children))},
          family));
  result->setLayoutMetrics({.frame = {{0, 0}, {300, 210}}});
  return result;
}
} // namespace

@interface BottomSheetPortalContextGraphTests : XCTestCase
@end

@implementation BottomSheetPortalContextGraphTests

- (void)testModalContentKeepsLocalNestingWithoutInheritingItsSourceContext
{
  auto sheetC = node(44);
  auto hostC = node(43, {sheetC}, true, true);
  auto sheetB = node(34, {node(41, {node(42), hostC})});
  auto hostB = node(33, {sheetB}, true, true);
  auto outsideModalPanel = node(2);
  auto modalContent = modal(30, {outsideModalPanel, node(31, {node(32), hostB})});
  auto sheetA = node(18);
  auto hostA = node(13, {node(14, {modalContent}), sheetA}, true, true);
  auto root = node(1, {node(11, {node(12), hostA})});
  auto graph = discoverPortalContexts(*root);
  XCTAssertTrue(graph.has_value());
  if (!graph) return;

  XCTAssertFalse(graph->contexts.at(31).parent.has_value());
  XCTAssertTrue(graph->contexts.at(41).parent == 31);
  XCTAssertFalse(graph->hostForPresentation(outsideModalPanel->getFamily(), *root).has_value());
  PortalNativeMembers members{
      .alive = {2, 11, 12, 13, 14, 18, 30, 31, 32, 33, 34, 41, 42, 43, 44},
      .parents = {{11, 1}, {12, 11}, {13, 11}, {14, 13}, {18, 13},
                  {30, 14}, {2, 30}, {31, 30}, {32, 31}, {33, 31},
                  {34, 33}, {41, 34}, {42, 41}, {43, 41}, {44, 43}}};
  auto projection = projectPortalIsolation(*graph, members,
      {{&hostA->getFamily(), {&sheetA->getFamily()}},
       {&hostB->getFamily(), {&sheetB->getFamily()}},
       {&hostC->getFamily(), {&sheetC->getFamily()}}});
  XCTAssertTrue(projection.has_value());
  if (projection) XCTAssertTrue((projection->hiddenBranches == std::set<Tag>{12, 14, 32, 42}));
}

- (void)testDisjointFlattenedAreasUseTheirOwnContainingBlocks
{
  auto sheetA = node(14);
  auto sheetB = node(24);
  auto hostA = node(13, {sheetA}, true, true);
  auto hostB = node(23, {sheetB}, true, true);
  auto root = node(1, {node(2), node(11, {node(12), hostA}, false),
                       node(21, {node(22), hostB}, false)});

  auto graph = discoverPortalContexts(*root);

  XCTAssertTrue(graph.has_value());
  if (!graph) return;
  XCTAssertEqual(graph->contexts.size(), 2u);
  XCTAssertEqual(graph->hostContexts.at(13), 11);
  XCTAssertEqual(graph->hostContexts.at(23), 21);
  XCTAssertEqual(graph->hostForPresentation(sheetA->getFamily(), *root).value(), 13);
  XCTAssertEqual(graph->hostForPresentation(sheetB->getFamily(), *root).value(), 23);
}

- (void)testSharedHostsFormOneContextWhileAnInnerAreaRetainsItsParent
{
  auto innerHost = node(33, {node(34)}, true, true);
  auto inner = node(31, {node(32), innerHost}, false);
  auto outerHost = node(13, {node(14, {inner})}, true, true);
  auto sharedHost = node(17, {node(18)}, true, true);
  auto root = node(1, {node(11, {node(12), outerHost, sharedHost}, false)});

  auto graph = discoverPortalContexts(*root);

  XCTAssertTrue(graph.has_value());
  if (!graph) return;
  XCTAssertEqual(graph->contexts.size(), 2u);
  XCTAssertTrue((graph->contexts.at(11).hosts == std::set<Tag>{13, 17}));
  XCTAssertFalse(graph->contexts.at(11).parent.has_value());
  XCTAssertTrue(graph->contexts.at(31).parent == 11);
  XCTAssertEqual(graph->hostContexts.at(33), 31);
}

- (void)testIsolationExcludesOnlyEachLocalBackgroundAndKeepsTheOutsideToolbar
{
  auto sheetA = node(14);
  auto sheetB = node(24);
  auto hostA = node(13, {sheetA}, true, true);
  auto hostB = node(23, {sheetB}, true, true);
  auto root = node(1, {node(2), node(11, {node(12), hostA}, false),
                       node(21, {node(22), hostB}, false)});
  auto graph = discoverPortalContexts(*root);
  XCTAssertTrue(graph.has_value());
  if (!graph) return;
  PortalNativeMembers members{
      .alive = {2, 12, 13, 14, 22, 23, 24},
      .parents = {{2, 1}, {12, 1}, {13, 1}, {14, 13},
                  {22, 1}, {23, 1}, {24, 23}}};

  auto projection = projectPortalIsolation(
      *graph, members, {{&hostA->getFamily(), {&sheetA->getFamily()}},
                        {&hostB->getFamily(), {&sheetB->getFamily()}}});

  XCTAssertTrue(projection.has_value());
  if (!projection) return;
  XCTAssertTrue((projection->hiddenBranches == std::set<Tag>{12, 22}));
}

- (void)testMaterializedStaticParentDoesNotReplaceTheLayoutContainingBlock
{
  auto host = node(13, {node(14)}, true, true);
  auto staticParent = node(10, {host});
  auto metrics = staticParent->getLayoutMetrics();
  metrics.positionType = PositionType::Static;
  staticParent->setLayoutMetrics(metrics);
  auto root = node(1, {node(11, {node(12), staticParent}, false)});

  auto graph = discoverPortalContexts(*root);

  XCTAssertTrue(graph.has_value());
  if (graph) XCTAssertEqual(graph->hostContexts.at(13), 11);
}

- (void)testSharedContextMergesRetainedHostsBeforeExcludingBackground
{
  auto sheetA = node(14);
  auto sheetB = node(24);
  auto hostA = node(13, {sheetA}, true, true);
  auto hostB = node(23, {sheetB}, true, true);
  auto root = node(1, {node(2), node(11, {node(12), hostA, hostB}, false)});
  auto graph = discoverPortalContexts(*root);
  XCTAssertTrue(graph.has_value());
  if (!graph) return;
  PortalNativeMembers members{.alive = {2, 12, 13, 14, 23, 24},
      .parents = {{2, 1}, {12, 1}, {13, 1}, {14, 13}, {23, 1}, {24, 23}}};

  auto projection = projectPortalIsolation(*graph, members,
      {{&hostA->getFamily(), {&sheetA->getFamily()}},
       {&hostB->getFamily(), {&sheetB->getFamily()}}});

  XCTAssertTrue(projection.has_value());
  if (projection) XCTAssertTrue((projection->hiddenBranches == std::set<Tag>{12}));
}

- (void)testInnerReleaseKeepsTheOuterBackgroundExcluded
{
  auto sheetB = node(34);
  auto hostB = node(33, {sheetB}, true, true);
  auto inner = node(31, {node(32), hostB}, false);
  auto sheetA = node(14, {inner});
  auto hostA = node(13, {sheetA}, true, true);
  auto root = node(1, {node(2), node(11, {node(12), hostA}, false)});
  auto graph = discoverPortalContexts(*root);
  XCTAssertTrue(graph.has_value());
  if (!graph) return;
  PortalNativeMembers members{.alive = {2, 12, 13, 14, 32, 33, 34},
      .parents = {{2, 1}, {12, 1}, {13, 1}, {14, 13}, {32, 14}, {33, 14}, {34, 33}}};
  PortalRetainedPaths outer{&hostA->getFamily(), {&sheetA->getFamily()}};
  PortalRetainedPaths innerPath{&hostB->getFamily(), {&sheetB->getFamily()}};

  auto open = projectPortalIsolation(*graph, members, {outer, innerPath});
  auto released = projectPortalIsolation(*graph, members, {outer});

  XCTAssertTrue(open.has_value());
  XCTAssertTrue(released.has_value());
  if (open) XCTAssertTrue((open->hiddenBranches == std::set<Tag>{12, 32}));
  if (released) XCTAssertTrue((released->hiddenBranches == std::set<Tag>{12}));
}

- (void)testUnresolvedTopRetainsEveryActivePresentationWithoutExposingBackground
{
  auto sheetA = node(14);
  auto sheetB = node(15);
  auto host = node(13, {sheetA, sheetB}, true, true);
  auto root = node(1, {node(2), node(11, {node(12), host}, false)});
  auto graph = discoverPortalContexts(*root);
  XCTAssertTrue(graph.has_value());
  if (!graph) return;
  PortalNativeMembers members{.alive = {2, 12, 13, 14, 15},
      .parents = {{2, 1}, {12, 1}, {13, 1}, {14, 13}, {15, 13}}};

  auto unknown = projectPortalIsolation(*graph, members,
      {{&host->getFamily(), {&sheetA->getFamily(), &sheetB->getFamily()}}});
  auto known = projectPortalIsolation(*graph, members,
      {{&host->getFamily(), {&sheetB->getFamily()}}});

  XCTAssertTrue(unknown.has_value());
  XCTAssertTrue(known.has_value());
  if (unknown) XCTAssertTrue((unknown->hiddenBranches == std::set<Tag>{12}));
  if (known) XCTAssertTrue((known->hiddenBranches == std::set<Tag>{12, 14}));
}

- (void)testRemovedOrReusedFamiliesCannotSupplyRetainedPaths
{
  auto oldSheet = node(14);
  auto sheet = node(14);
  auto host = node(13, {sheet}, true, true);
  auto root = node(1, {node(11, {node(12), host}, false)});
  auto graph = discoverPortalContexts(*root);
  XCTAssertTrue(graph.has_value());
  if (!graph) return;
  PortalNativeMembers members{.alive = {12, 13, 14},
      .parents = {{12, 1}, {13, 1}, {14, 13}}};

  XCTAssertFalse(graph->hostForPresentation(oldSheet->getFamily(), *root).has_value());
  XCTAssertFalse(projectPortalIsolation(*graph, members,
      {{&host->getFamily(), {&oldSheet->getFamily()}}}).has_value());
  auto empty = projectPortalIsolation(*graph, members, {});
  XCTAssertTrue(empty.has_value());
  if (empty) XCTAssertTrue(empty->hiddenBranches.empty());
}

- (void)testInnerPresentationSurvivesAnInactiveOuterSheetWithoutExposingItsBackground
{
  auto sheetB = node(34);
  auto innerHost = node(33, {sheetB}, true, true);
  auto innerArea = node(31, {node(32), innerHost}, false);
  auto sheetA = node(14, {node(16), innerArea});
  auto sheetC = node(15);
  auto outerHost = node(13, {sheetA, sheetC}, true, true);
  auto root = node(1, {node(2), node(11, {node(12), outerHost}, false)});
  auto graph = discoverPortalContexts(*root);
  XCTAssertTrue(graph.has_value());
  if (!graph) return;
  PortalNativeMembers members{.alive = {2, 12, 13, 14, 15, 16, 32, 33, 34},
      .parents = {{2, 1}, {12, 1}, {13, 1}, {14, 13}, {15, 13},
                  {16, 14}, {32, 14}, {33, 14}, {34, 33}}};
  PortalRetainedPaths outer{&outerHost->getFamily(), {&sheetC->getFamily()}};
  PortalRetainedPaths inner{&innerHost->getFamily(), {&sheetB->getFamily()}};

  auto open = projectPortalIsolation(*graph, members, {outer, inner});
  auto released = projectPortalIsolation(*graph, members, {outer});

  XCTAssertTrue(open.has_value());
  XCTAssertTrue(released.has_value());
  if (open) XCTAssertTrue((open->hiddenBranches == std::set<Tag>{12, 16, 32}));
  if (released) XCTAssertTrue((released->hiddenBranches == std::set<Tag>{12, 14}));
}

- (void)testAnInnerContextDoesNotHideContentOutsideItInTheRetainedOuterPresentation
{
  auto sheetB = node(34);
  auto innerHost = node(33, {sheetB}, true, true);
  auto innerArea = node(31, {node(32), innerHost}, false);
  auto sheetA = node(14, {node(17, {node(16), innerArea}, false)});
  auto outerHost = node(13, {sheetA, node(15)}, true, true);
  auto root = node(1, {node(11, {node(12), outerHost}, false)});
  auto graph = discoverPortalContexts(*root);
  XCTAssertTrue(graph.has_value());
  if (!graph) return;
  PortalNativeMembers members{.alive = {12, 13, 14, 15, 16, 32, 33, 34},
      .parents = {{12, 1}, {13, 1}, {14, 13}, {15, 13}, {16, 14},
                  {32, 14}, {33, 14}, {34, 33}}};

  auto projection = projectPortalIsolation(*graph, members,
      {{&outerHost->getFamily(), {&sheetA->getFamily()}},
       {&innerHost->getFamily(), {&sheetB->getFamily()}}});

  XCTAssertTrue(projection.has_value());
  if (projection) XCTAssertTrue((projection->hiddenBranches == std::set<Tag>{12, 15, 32}));
}

@end
