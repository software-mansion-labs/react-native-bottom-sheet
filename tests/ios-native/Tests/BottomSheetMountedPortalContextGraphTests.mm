#import <XCTest/XCTest.h>
#import <React/RCTComponentViewRegistry.h>
#import <React/RCTMountingManager.h>
#import <React/RCTViewComponentView.h>
#import <React/RCTModalHostViewComponentView.h>
#import <React/RCTFabricModalHostViewController.h>

#import "../../../ios/BottomSheetMountedPortalContextGraph.h"
#import "../../../ios/BottomSheetPortalNativeContainer.h"
#include <react/renderer/components/view/ViewComponentDescriptor.h>
#include <react/renderer/components/modal/ModalHostViewComponentDescriptor.h>
#include <react/renderer/mounting/Differentiator.h>
#include <react/renderer/mounting/ShadowTree.h>

using namespace facebook::react;
using namespace facebook::react::bottomsheet;

namespace {
using Children = std::vector<std::shared_ptr<const ShadowNode>>;

class TreeDelegate final : public ShadowTreeDelegate {
 public:
  RootShadowNode::Unshared shadowTreeWillCommit(
      const ShadowTree&, const RootShadowNode::Shared&,
      const RootShadowNode::Unshared& next,
      const ShadowTreeCommitOptions&) const override { return next; }
  void shadowTreeDidFinishTransaction(std::shared_ptr<const MountingCoordinator>,
                                     bool) const override {}
  void shadowTreeDidFinishReactCommit(const ShadowTree&) const override {}
  void shadowTreeDidPromoteReactRevision(const ShadowTree&) const override {}
};

struct MountedTree {
  SurfaceId surface;
  ContextContainer context;
  TreeDelegate delegate;
  ViewComponentDescriptor descriptor{
      {.eventDispatcher = {}, .contextContainer = std::make_shared<ContextContainer>(),
       .flavor = nullptr}};
  ModalHostViewComponentDescriptor modalDescriptor{
      {.eventDispatcher = {}, .contextContainer = std::make_shared<ContextContainer>(),
       .flavor = nullptr}};
  ShadowTree tree;
  RCTMountingManager *__strong manager;
  UIView *__strong viewport;

  MountedTree(SurfaceId surfaceId = 1, RCTMountingManager *mountingManager = nil)
      : surface(surfaceId),
        tree(surfaceId, {.minimumSize = {800, 600}, .maximumSize = {800, 600}},
             {}, delegate, context) {
    manager = mountingManager ?: [RCTMountingManager new];
    [manager.componentViewRegistry.componentViewFactory
        registerComponentViewClass:RCTViewComponentView.class];
    [manager.componentViewRegistry.componentViewFactory
        registerComponentViewClass:RCTModalHostViewComponentView.class];
    viewport = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 800, 600)];
    [manager attachSurfaceToView:viewport surfaceId:surface];
  }

  ~MountedTree() { [manager detachSurfaceFromView:viewport surfaceId:surface]; }

  std::shared_ptr<ViewShadowNode> node(Tag tag, Children children = {},
                                      bool concrete = true, bool host = false) {
    folly::dynamic raw = folly::dynamic::object("collapsable", !concrete)
        ("position", "absolute")("left", 0)("top", 0)("width", 300)("height", 210);
    if (host) raw["nativeID"] = PortalHostNativeID;
    auto props = descriptor.cloneProps(PropsParserContext(surface, context), nullptr,
                                       RawProps(std::move(raw)));
    auto family = descriptor.createFamily(
        {.tag = tag, .surfaceId = surface, .instanceHandle = nullptr});
    return std::static_pointer_cast<ViewShadowNode>(descriptor.createShadowNode(
        {.props = props, .children = std::make_shared<const Children>(std::move(children))},
        family));
  }

  RootShadowNode::Shared mount(Children children) {
    tree.commit([&](const RootShadowNode& current) {
      return std::static_pointer_cast<RootShadowNode>(current.ShadowNode::clone(
          {.children = std::make_shared<const Children>(std::move(children))}));
    }, {});
    [manager scheduleTransaction:tree.getMountingCoordinator()];
    return tree.getMountingCoordinator()->getBaseRevision().rootShadowNode;
  }

  std::shared_ptr<ModalHostViewShadowNode> modal(Tag tag, Children children) {
    auto props = modalDescriptor.cloneProps(PropsParserContext(surface, context), nullptr,
        RawProps(folly::dynamic::object("visible", false)));
    auto family = modalDescriptor.createFamily(
        {.tag = tag, .surfaceId = surface, .instanceHandle = nullptr});
    auto state = std::make_shared<ModalHostViewShadowNode::ConcreteState>(
        std::make_shared<const ModalHostViewState>(facebook::react::Size{800, 600}), family);
    return std::static_pointer_cast<ModalHostViewShadowNode>(modalDescriptor.createShadowNode(
        {.props = props, .children = std::make_shared<const Children>(std::move(children)),
         .state = state}, family));
  }

  UIView<RCTComponentViewProtocol> *view(Tag tag) {
    return [manager.componentViewRegistry findComponentViewWithTag:tag];
  }

  void bootstrap(BottomSheetMountedPortalContextGraph *publication,
                 const RootShadowNode::Shared& root) {
    auto empty = root->ShadowNode::clone(
        {.children = ShadowNode::emptySharedShadowNodeSharedList()});
    auto mutations = calculateShadowViewMutations(*empty, *root);
    [publication publishRoot:root mutations:mutations];
  }
};
} // namespace

@interface BottomSheetMountedPortalContextGraphTests : XCTestCase
@end

@interface UnsupportedHierarchyAccessors : UIView
@property (nonatomic) NSUInteger reads;
@end
@implementation UnsupportedHierarchyAccessors
- (NSArray<UIView *> *)subviews { self.reads++; return super.subviews; }
- (UIView *)superview { self.reads++; return super.superview; }
- (NSInteger)tag { self.reads++; return super.tag; }
@end

@interface BottomSheetNativeHierarchySnapshotTests : XCTestCase
@end

@interface CallbackModalOwner : RCTModalHostViewComponentView
@property (nonatomic) NSUInteger typeReads;
@end
@implementation CallbackModalOwner
- (BOOL)isKindOfClass:(Class)type
{
  self.typeReads++;
  return [super isKindOfClass:type];
}
@end

@implementation BottomSheetNativeHierarchySnapshotTests
- (void)testNativeOwnerClassificationDoesNotDispatchApplicationCallbacks
{
  RCTModalHostViewComponentView *modal = [RCTModalHostViewComponentView new];
  RCTViewComponentView *child = [RCTViewComponentView new];
  [modal mountChildComponentView:child index:0];
  UIView *container = child.superview;
  RCTFabricModalHostViewController *controller = (RCTFabricModalHostViewController *)container.nextResponder;
  CallbackModalOwner *owner = [CallbackModalOwner new];
  controller.delegate = (id<RCTFabricModalHostViewControllerDelegate>)owner;
  owner.typeReads = 0;
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];

  XCTAssertEqual([hierarchy modalOwnerOfContainer:container], owner);
  XCTAssertTrue([hierarchy validate]);
  XCTAssertEqual(owner.typeReads, 0);

  [modal unmountChildComponentView:child index:0];
}

- (void)testStockModalContainerResolvesItsPublicNativeOwner
{
  RCTModalHostViewComponentView *modal = [RCTModalHostViewComponentView new];
  RCTViewComponentView *child = [RCTViewComponentView new];
  [modal mountChildComponentView:child index:0];
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];

  XCTAssertEqual([hierarchy modalOwnerOfContainer:child.superview], modal);
  XCTAssertTrue([hierarchy validate]);

  [modal unmountChildComponentView:child index:0];
}

- (void)testModalOwnerChangeInvalidatesCapturedNativeFacts
{
  RCTModalHostViewComponentView *modal = [RCTModalHostViewComponentView new];
  RCTViewComponentView *child = [RCTViewComponentView new];
  [modal mountChildComponentView:child index:0];
  UIView *container = child.superview;
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];
  XCTAssertEqual([hierarchy modalOwnerOfContainer:container], modal);
  RCTFabricModalHostViewController *controller = (RCTFabricModalHostViewController *)container.nextResponder;

  controller.delegate = nil;

  XCTAssertEqual([hierarchy modalOwnerOfContainer:container], modal);
  XCTAssertFalse([hierarchy validate]);
  [modal unmountChildComponentView:child index:0];
}

- (void)testSnapshotKeepsCapturedFactsAndRejectsAnUnpublishedChildInsertion
{
  UIView *parent = [UIView new];
  UIView *child = [UIView new];
  child.tag = 14;
  [parent addSubview:child];
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];
  XCTAssertEqual([hierarchy parentOfView:child], parent);
  XCTAssertEqual([hierarchy tagOfView:child], 14);
  XCTAssertEqualObjects([hierarchy childrenOfView:parent], (@[child]));
  XCTAssertTrue([hierarchy validate]);

  [parent addSubview:[UIView new]];
  XCTAssertEqualObjects([hierarchy childrenOfView:parent], (@[child]));
  XCTAssertFalse([hierarchy validate]);
}

- (void)testStructuralOverridesAreRejectedBeforeAnyAccessorCallback
{
  UnsupportedHierarchyAccessors *view = [UnsupportedHierarchyAccessors new];
  view.reads = 0;
  auto children = [BottomSheetNativeHierarchySnapshot new];
  XCTAssertNil([children childrenOfView:view]);
  XCTAssertFalse(children.isValid);
  auto parent = [BottomSheetNativeHierarchySnapshot new];
  XCTAssertNil([parent parentOfView:view]);
  XCTAssertFalse(parent.isValid);
  auto tag = [BottomSheetNativeHierarchySnapshot new];
  XCTAssertEqual([tag tagOfView:view], 0);
  XCTAssertFalse(tag.isValid);
  XCTAssertEqual(view.reads, 0);
}
@end

@interface MountedPortalContainer : UIView <BottomSheetPortalNativeContainer>
@property (nonatomic, weak, nullable) UIView *portalNativeOwner;
@end

@implementation MountedPortalContainer
@end

@implementation BottomSheetMountedPortalContextGraphTests
- (void)testStockModalContentProjectsOnlyTheActualLocalPanel
{
  MountedTree fixture;
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true, true);
  auto modal = fixture.modal(30, {fixture.node(2), fixture.node(11, {fixture.node(12), host})});
  auto root = fixture.mount({fixture.node(4), fixture.node(41, {fixture.node(42),
      fixture.node(43, {fixture.node(44, {modal})}, true, true)})});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  std::vector<PortalRetainedPaths> requests{{&host->getFamily(), {&sheet->getFamily()}}};

  auto branches = [publication hiddenBranchesForRoot:root retainedPaths:requests];

  XCTAssertEqualObjects(branches, ([NSSet setWithObject:fixture.view(12)]));
  XCTAssertFalse([branches containsObject:fixture.view(2)]);
  XCTAssertFalse([branches containsObject:fixture.view(4)]);

  UIView<RCTComponentViewProtocol> *panel = fixture.view(11);
  RCTModalHostViewComponentView *modalView = (RCTModalHostViewComponentView *)fixture.view(30);
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];
  auto path = [publication contextPathForView:panel root:root hierarchy:hierarchy];
  XCTAssertTrue(path.has_value());
  if (path) XCTAssertTrue(path->contexts == (std::vector<Tag>{11}));
  XCTAssertTrue([hierarchy validate]);

  UIView *outside = [UIView new];
  [panel.superview addSubview:outside];
  auto outsideHierarchy = [BottomSheetNativeHierarchySnapshot new];
  auto outsidePath = [publication contextPathForView:outside root:root hierarchy:outsideHierarchy];
  XCTAssertTrue(outsidePath.has_value());
  if (outsidePath) XCTAssertTrue(outsidePath->contexts.empty());
  XCTAssertTrue([outsideHierarchy validate]);
  [outside removeFromSuperview];

  [modalView unmountChildComponentView:panel index:1];
  XCTAssertNil([publication hiddenBranchesForRoot:root retainedPaths:requests]);
  auto detached = [BottomSheetNativeHierarchySnapshot new];
  XCTAssertFalse([publication contextPathForView:panel root:root hierarchy:detached].has_value());

  [modalView mountChildComponentView:panel index:1];
  XCTAssertEqualObjects([publication hiddenBranchesForRoot:root retainedPaths:requests],
                       ([NSSet setWithObject:fixture.view(12)]));
  RCTFabricModalHostViewController *controller =
      (RCTFabricModalHostViewController *)panel.superview.nextResponder;
  CallbackModalOwner *foreignOwner = [CallbackModalOwner new];
  foreignOwner.tag = 30; // Same tag is insufficient without the registry identity.
  controller.delegate = (id<RCTFabricModalHostViewControllerDelegate>)foreignOwner;
  XCTAssertNil([publication hiddenBranchesForRoot:root retainedPaths:requests]);
}

- (void)testModalContainingBlockIncludesItsNativeBackgroundWithoutHidingTheApplication
{
  MountedTree fixture;
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true, true);
  auto modal = fixture.modal(30, {fixture.node(12), host});
  auto root = fixture.mount({fixture.node(4), fixture.node(41, {fixture.node(42),
      fixture.node(43, {fixture.node(44, {modal})}, true, true)})});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  UIView *nativeBackground = [UIView new];
  [fixture.view(13).superview addSubview:nativeBackground];
  std::vector<PortalRetainedPaths> requests{{&host->getFamily(), {&sheet->getFamily()}}};

  auto branches = [publication hiddenBranchesForRoot:root retainedPaths:requests];

  XCTAssertEqualObjects(branches, ([NSSet setWithObjects:fixture.view(12), nativeBackground, nil]));
  XCTAssertFalse([branches containsObject:fixture.view(4)]);

  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];
  auto path = [publication contextPathForView:nativeBackground root:root hierarchy:hierarchy];
  XCTAssertTrue(path.has_value());
  if (path) XCTAssertTrue(path->contexts == (std::vector<Tag>{30}));
  XCTAssertTrue([hierarchy validate]);

  RCTModalHostViewComponentView *foreign = [RCTModalHostViewComponentView new];
  foreign.tag = 30;
  [foreign mountChildComponentView:foreign index:0];
  auto cyclic = [BottomSheetNativeHierarchySnapshot new];
  auto cyclicPath = [publication contextPathForView:foreign root:root hierarchy:cyclic];
  XCTAssertTrue(!cyclicPath || cyclicPath->contexts.empty());
  [foreign unmountChildComponentView:foreign index:0];

  RCTViewComponentView *foreignContent = [RCTViewComponentView new];
  [foreign mountChildComponentView:foreignContent index:0];
  [fixture.view(14) addSubview:foreign];
  auto unmapped = [BottomSheetNativeHierarchySnapshot new];
  auto foreignPath = [publication contextPathForView:foreignContent
      root:root hierarchy:unmapped];
  XCTAssertTrue(!foreignPath || foreignPath->contexts.empty());
  [foreign removeFromSuperview];
  [foreign unmountChildComponentView:foreignContent index:0];
}


- (void)testNativeActionPathMapsPlainChildrenToTheirLogicalPortalContext
{
  MountedTree fixture;
  auto root = fixture.mount({fixture.node(11, {
      fixture.node(12), fixture.node(13, {fixture.node(14)}, true, true)}, false)});
  auto graph = [[BottomSheetMountedPortalContextGraph alloc] initWithMountingManager:fixture.manager];
  fixture.bootstrap(graph, root);
  UIView *origin = [UIView new];
  [fixture.view(14) addSubview:origin];
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];

  auto path = [graph contextPathForView:origin root:root hierarchy:hierarchy];

  XCTAssertTrue(path.has_value());
  if (path) {
    XCTAssertTrue(path->contexts == (std::vector<Tag>{11}));
    XCTAssertEqual(path->nativeDepth, 1u);
  }
  XCTAssertTrue([hierarchy validate]);
}

- (void)testNativeActionPathRejectsAnUnpublishedRegisteredReparent
{
  MountedTree fixture;
  auto root = fixture.mount({fixture.node(11, {
      fixture.node(12), fixture.node(13, {fixture.node(14)}, true, true)})});
  auto graph = [[BottomSheetMountedPortalContextGraph alloc] initWithMountingManager:fixture.manager];
  fixture.bootstrap(graph, root);
  [fixture.view(12) addSubview:fixture.view(14)];
  auto hierarchy = [BottomSheetNativeHierarchySnapshot new];

  XCTAssertFalse([graph contextPathForView:fixture.view(14) root:root hierarchy:hierarchy].has_value());
}

- (void)testAppliedNativeMembershipProjectsTwoLocalBackgroundsWithoutTheToolbar
{
  MountedTree fixture;
  auto sheetA = fixture.node(14);
  auto sheetB = fixture.node(24);
  auto hostA = fixture.node(13, {sheetA}, true, true);
  auto hostB = fixture.node(23, {sheetB}, true, true);
  auto root = fixture.mount({fixture.node(2),
      fixture.node(11, {fixture.node(12), hostA}, false),
      fixture.node(21, {fixture.node(22), hostB}, false)});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  std::vector<PortalRetainedPaths> requests{
      {&hostA->getFamily(), {&sheetA->getFamily()}},
      {&hostB->getFamily(), {&sheetB->getFamily()}}};

  auto branches = [publication hiddenBranchesForRoot:root retainedPaths:requests];

  XCTAssertNotNil(branches);
  XCTAssertEqualObjects(branches, ([NSSet setWithObjects:fixture.view(12), fixture.view(22), nil]));
  XCTAssertFalse([branches containsObject:fixture.view(2)]);
}

- (void)testRootCanBeTheActualPresentationAreaWithoutAParentFallback
{
  MountedTree fixture;
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true, true);
  auto root = fixture.mount({fixture.node(12), host});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  std::vector<PortalRetainedPaths> requests{{&host->getFamily(), {&sheet->getFamily()}}};

  auto branches = [publication hiddenBranchesForRoot:root retainedPaths:requests];

  XCTAssertEqualObjects(branches, ([NSSet setWithObject:fixture.view(12)]));
}

- (void)testNativeReparentWithoutAnAppliedPublicationRejectsTheCut
{
  MountedTree fixture;
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true, true);
  auto root = fixture.mount({fixture.node(2),
      fixture.node(11, {fixture.node(12), host}, false)});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  std::vector<PortalRetainedPaths> requests{{&host->getFamily(), {&sheet->getFamily()}}};
  UIView *oldParent = fixture.view(14).superview;

  [fixture.view(1) addSubview:fixture.view(14)];
  XCTAssertNil([publication hiddenBranchesForRoot:root retainedPaths:requests]);

  [oldParent addSubview:fixture.view(14)];
  XCTAssertEqualObjects([publication hiddenBranchesForRoot:root retainedPaths:requests],
                       ([NSSet setWithObject:fixture.view(12)]));
}

- (void)testNativeOnlyBackgroundIsDiscoveredWithoutChangingRetainedPaths
{
  MountedTree fixture;
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true, true);
  auto root = fixture.mount({fixture.node(2),
      fixture.node(11, {fixture.node(12), host})});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  std::vector<PortalRetainedPaths> requests{{&host->getFamily(), {&sheet->getFamily()}}};
  UIView *nativeBackground = [UIView new];
  nativeBackground.accessibilityElementsHidden = YES;
  [fixture.view(11) addSubview:nativeBackground];

  auto branches = [publication hiddenBranchesForRoot:root retainedPaths:requests];

  XCTAssertEqualObjects(branches,
      ([NSSet setWithObjects:fixture.view(12), nativeBackground, nil]));
  XCTAssertFalse(fixture.view(12).accessibilityElementsHidden);
  XCTAssertTrue(nativeBackground.accessibilityElementsHidden);
  [nativeBackground removeFromSuperview];
  XCTAssertEqualObjects([publication hiddenBranchesForRoot:root retainedPaths:requests],
                       ([NSSet setWithObject:fixture.view(12)]));
}

- (void)testOpaqueNativeWrapperCannotBeMistakenForInactiveBackground
{
  MountedTree fixture;
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true, true);
  auto root = fixture.mount({fixture.node(11, {fixture.node(12), host})});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  std::vector<PortalRetainedPaths> requests{{&host->getFamily(), {&sheet->getFamily()}}};
  UIView *wrapper = [UIView new];
  [fixture.view(11) addSubview:wrapper];
  [wrapper addSubview:fixture.view(13)];

  XCTAssertNil([publication hiddenBranchesForRoot:root retainedPaths:requests]);
  XCTAssertFalse(wrapper.accessibilityElementsHidden);
}

- (void)testNewCompletedRootRejectsOldFamiliesAndInvalidationWithdrawsProjection
{
  MountedTree fixture;
  auto firstSheet = fixture.node(14);
  auto host = fixture.node(13, {firstSheet}, true, true);
  auto first = fixture.mount({fixture.node(11, {fixture.node(12), host}, false)});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, first);
  // RN may reuse a tag only after the old family has been deleted.
  auto removed = fixture.mount({});
  auto deletes = calculateShadowViewMutations(*first, *removed);
  [publication publishRoot:removed mutations:deletes];
  auto nextSheet = fixture.node(14);
  auto nextHost = fixture.node(13, {nextSheet}, true, true);
  auto next = fixture.mount({fixture.node(11, {fixture.node(12), nextHost}, false)});
  auto mutations = calculateShadowViewMutations(*removed, *next);
  [publication publishRoot:next mutations:mutations];
  std::vector<PortalRetainedPaths> oldPaths{{&host->getFamily(), {&firstSheet->getFamily()}}};
  std::vector<PortalRetainedPaths> currentPaths{{&nextHost->getFamily(), {&nextSheet->getFamily()}}};

  XCTAssertNil([publication hiddenBranchesForRoot:first retainedPaths:oldPaths]);
  XCTAssertNil([publication hiddenBranchesForRoot:next retainedPaths:oldPaths]);
  XCTAssertEqualObjects([publication hiddenBranchesForRoot:next retainedPaths:currentPaths],
                       ([NSSet setWithObject:fixture.view(12)]));
  [publication invalidate];
  XCTAssertNil([publication hiddenBranchesForRoot:next retainedPaths:currentPaths]);
}

- (void)testMovingAMaterializedBoundaryRejectsObsoleteContextNesting
{
  MountedTree fixture;
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true, true);
  auto root = fixture.mount({fixture.node(2),
      fixture.node(11, {fixture.node(12), host})});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  std::vector<PortalRetainedPaths> requests{{&host->getFamily(), {&sheet->getFamily()}}};

  [fixture.view(2) addSubview:fixture.view(11)];

  XCTAssertNil([publication hiddenBranchesForRoot:root retainedPaths:requests]);
}

- (void)testUnresolvableLayoutDoesNotLoseUnchangedCompletedNativeMembership
{
  MountedTree fixture;
  auto hostA = fixture.node(13, {fixture.node(14)}, true, true);
  auto sheetB = fixture.node(24);
  auto hostB = fixture.node(23, {sheetB}, true, true);
  auto areaA = fixture.node(11, {fixture.node(12), hostA}, false);
  auto areaB = fixture.node(21, {fixture.node(22), hostB}, false);
  auto first = fixture.mount({areaA, areaB});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, first);
  std::vector<PortalRetainedPaths> requests{{&hostB->getFamily(), {&sheetB->getFamily()}}};
  auto hiddenProps = fixture.descriptor.cloneProps(PropsParserContext(1, fixture.context),
      areaA->getProps(), RawProps(folly::dynamic::object("display", "none")));
  auto hiddenA = areaA->clone({.props = hiddenProps});

  auto hidden = fixture.mount({hiddenA, areaB});
  auto hidingMutations = calculateShadowViewMutations(*first, *hidden);
  [publication publishRoot:hidden mutations:hidingMutations];
  XCTAssertNil([publication hiddenBranchesForRoot:hidden retainedPaths:requests]);

  auto restored = fixture.mount({areaA, areaB});
  auto restoringMutations = calculateShadowViewMutations(*hidden, *restored);
  [publication publishRoot:restored mutations:restoringMutations];

  XCTAssertEqualObjects([publication hiddenBranchesForRoot:restored retainedPaths:requests],
                       ([NSSet setWithObject:fixture.view(22)]));
}

- (void)testForeignRegisteredSurfaceCannotBeHiddenAsANativeOnlyBackground
{
  MountedTree fixture;
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true, true);
  auto root = fixture.mount({fixture.node(11, {fixture.node(12), host})});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  std::vector<PortalRetainedPaths> requests{{&host->getFamily(), {&sheet->getFamily()}}};
  for (RCTMountingManager *manager in @[fixture.manager, [RCTMountingManager new]]) {
    MountedTree foreign(101, manager);
    foreign.mount({foreign.node(114)});
    [fixture.view(11) addSubview:foreign.viewport];

    XCTAssertNil([publication hiddenBranchesForRoot:root retainedPaths:requests]);
    XCTAssertFalse(foreign.viewport.accessibilityElementsHidden);

    [foreign.viewport removeFromSuperview];
    XCTAssertEqualObjects([publication hiddenBranchesForRoot:root retainedPaths:requests],
                         ([NSSet setWithObject:fixture.view(12)]));
  }
}

- (void)testARegisteredForeignParentCannotBeSkippedInsideARetainedPresentation
{
  MountedTree fixture;
  auto sheet = fixture.node(14, {fixture.node(16)});
  auto host = fixture.node(13, {sheet}, true, true);
  auto root = fixture.mount({fixture.node(11, {fixture.node(12), host})});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  std::vector<PortalRetainedPaths> requests{{&host->getFamily(), {&sheet->getFamily()}}};
  MountedTree foreign(101, fixture.manager);
  foreign.mount({foreign.node(114)});
  [fixture.view(14) addSubview:foreign.viewport];
  [foreign.view(101) addSubview:fixture.view(16)];

  XCTAssertNil([publication hiddenBranchesForRoot:root retainedPaths:requests]);

  [fixture.view(14) addSubview:fixture.view(16)];
  [foreign.viewport removeFromSuperview];
  XCTAssertEqualObjects([publication hiddenBranchesForRoot:root retainedPaths:requests],
                       ([NSSet setWithObject:fixture.view(12)]));
}

- (void)testOwnedHoistedContainerKeepsItsCurrentRegisteredParentBinding
{
  MountedTree fixture;
  auto sheet = fixture.node(14, {fixture.node(16)});
  auto host = fixture.node(13, {sheet}, true, true);
  auto root = fixture.mount({fixture.node(11, {fixture.node(12), host})});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  std::vector<PortalRetainedPaths> requests{{&host->getFamily(), {&sheet->getFamily()}}};
  MountedPortalContainer *content = [MountedPortalContainer new];
  content.portalNativeOwner = fixture.view(14);
  [fixture.viewport addSubview:content];
  [content addSubview:fixture.view(16)];

  XCTAssertEqualObjects([publication hiddenBranchesForRoot:root retainedPaths:requests],
                       ([NSSet setWithObject:fixture.view(12)]));

  content.portalNativeOwner = nil;
  XCTAssertNil([publication hiddenBranchesForRoot:root retainedPaths:requests]);
}

- (void)testOwnedNativeChromeIsExcludedOnlyAlongANarrowRetainedInnerPath
{
  MountedTree fixture;
  auto innerSheet = fixture.node(24);
  auto innerHost = fixture.node(23, {innerSheet}, true, true);
  auto contentRoot = fixture.node(16, {fixture.node(17),
      fixture.node(21, {fixture.node(22), innerHost})});
  auto sheetA = fixture.node(14, {contentRoot});
  auto topA = fixture.node(18);
  auto hostA = fixture.node(13, {sheetA, topA}, true, true);
  auto root = fixture.mount({fixture.node(11, {fixture.node(12), hostA})});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, root);
  MountedPortalContainer *content = [MountedPortalContainer new];
  content.portalNativeOwner = fixture.view(14);
  UIView *chrome = [UIView new];
  UIView *body = [UIView new];
  [fixture.view(14) addSubview:content];
  [content addSubview:chrome];
  [content addSubview:body];
  [body addSubview:fixture.view(16)];
  std::vector<PortalRetainedPaths> narrow{
      {&hostA->getFamily(), {&topA->getFamily()}},
      {&innerHost->getFamily(), {&innerSheet->getFamily()}}};

  XCTAssertEqualObjects([publication hiddenBranchesForRoot:root retainedPaths:narrow],
      ([NSSet setWithObjects:fixture.view(12), fixture.view(17), fixture.view(22), chrome, nil]));

  std::vector<PortalRetainedPaths> whole{
      {&hostA->getFamily(), {&sheetA->getFamily()}},
      {&innerHost->getFamily(), {&innerSheet->getFamily()}}};
  XCTAssertEqualObjects([publication hiddenBranchesForRoot:root retainedPaths:whole],
      ([NSSet setWithObjects:fixture.view(12), fixture.view(18), fixture.view(22), nil]));
}

- (void)testDeletionRetiresExactNativeInstancesBeforeTheRegistryCanRecycleThem
{
  MountedTree fixture;
  auto background = fixture.node(12);
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true, true);
  auto area = fixture.node(11, {background, host});
  auto first = fixture.mount({area});
  auto publication = [[BottomSheetMountedPortalContextGraph alloc]
      initWithMountingManager:fixture.manager];
  fixture.bootstrap(publication, first);
  UILabel *nativeTitle = [UILabel new];
  [fixture.view(12) addSubview:nativeTitle];
  auto nextArea = area->clone({.children = std::make_shared<const Children>(Children{host})});
  auto next = first->ShadowNode::clone({.children =
      std::make_shared<const Children>(Children{nextArea})});
  auto mutations = calculateShadowViewMutations(*first, *next);

  auto deletion = [publication nativeDeletionForMutations:mutations];
  XCTAssertEqualObjects(deletion.deletedViews,
                       ([NSSet setWithObject:fixture.view(12)]));
  XCTAssertEqualObjects(deletion.survivingRegisteredViews, ([NSSet set]));

  fixture.mount({nextArea});
  XCTAssertNil([publication nativeDeletionForMutations:mutations]);
}

@end
