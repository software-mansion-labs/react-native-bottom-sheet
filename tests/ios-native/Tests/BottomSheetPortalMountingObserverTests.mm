#import <XCTest/XCTest.h>
#import <React/RCTComponentViewRegistry.h>
#import <React/RCTMountingManager.h>
#import <React/RCTViewComponentView.h>
#import <React/RCTModalHostViewComponentView.h>
#import "../../../ios/BottomSheetPortalMountingObserver.h"
#import "../../../ios/BottomSheetAccessibilityMaskLedger.h"
#import "../../../ios/BottomSheetPortalIsolation.h"
#import "../../../ios/BottomSheetPresentationOwnership.h"
#import "../../../ios/BottomSheetPresentationModalIsolationAdapter.h"
#import "../../../ios/BottomSheetPortalNativeContainer.h"
#include <react/renderer/components/view/ViewComponentDescriptor.h>
#include <react/renderer/components/modal/ModalHostViewComponentDescriptor.h>
#include <react/renderer/mounting/Differentiator.h>

using namespace facebook::react;
using namespace facebook::react::bottomsheet;

// A native application container may synchronously change lifecycle during a
// getter. This is the UIKit boundary, not a replacement for the Fabric adapter.
@interface ReentrantPortalNativeContainer : UIView
@property (nonatomic, copy) void (^onReadSubviews)(void);
@end
@implementation ReentrantPortalNativeContainer
- (NSArray<UIView *> *)subviews
{
  if (self.onReadSubviews) self.onReadSubviews();
  return super.subviews;
}
@end

@interface PortalOwnedContentAnchor : UIView <BottomSheetPortalNativeContainer>
@property (nonatomic, weak) UIView *portalNativeOwner;
@end
@implementation PortalOwnedContentAnchor
@end

namespace {
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
} // namespace

@interface BottomSheetPortalMountingObserverTests : XCTestCase
@end
@implementation BottomSheetPortalMountingObserverTests

- (void)testStockModalAndApplicationPortalsShareTheWriterWithoutMergingTheirScopes
{
  RuntimeFixture fixture;
  auto modalPanel = fixture.node(11, {fixture.node(12),
      fixture.node(13, {fixture.node(14)}, true)});
  fixture.mount({fixture.node(4), fixture.node(41, {fixture.node(42),
      fixture.node(43, {fixture.node(44, {fixture.modal(30, {fixture.node(2), modalPanel})})}, true)})});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  UIView *applicationOwner = fixture.view(44);
  UIView *modalOwner = fixture.view(14);
  [observer didMount:first owner:applicationOwner];
  [observer didMount:first owner:modalOwner];
  auto applicationIdentity = [BottomSheetPresentationIdentity new];
  auto modalIdentity = [BottomSheetPresentationIdentity new];
  auto application = [BottomSheetPortalPresentation presentationForIdentity:applicationIdentity
      anchor:applicationOwner active:YES eligible:YES];
  auto modal = [BottomSheetPortalPresentation presentationForIdentity:modalIdentity
      anchor:modalOwner active:YES eligible:YES];
  XCTAssertNotNil(application);
  XCTAssertNotNil(modal);
  if (!application || !modal) return;
  NSArray *presentations = @[application, modal];
  auto adapter = [BottomSheetPresentationModalIsolationAdapter new];
  BottomSheetPresentationOrderProvider order = ^BottomSheetPresentationOrder(
      BottomSheetPresentationIdentity *a, BottomSheetPresentationIdentity *b) {
    return BottomSheetPresentationOrderUnknown;
  };

  XCTAssertTrue([adapter reconcilePortalPresentations:presentations
      orderProvider:order ifCurrent:nil]);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(42).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(2).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(4).accessibilityElementsHidden);
  XCTAssertFalse(modalOwner.accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:modalIdentity
      actionOrigin:modalOwner presentations:presentations orderProvider:order],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:applicationIdentity
      actionOrigin:fixture.view(2) presentations:presentations orderProvider:order],
      BottomSheetPresentationEscapeRoutePassThrough);

  auto inactiveModal = [BottomSheetPortalPresentation presentationForIdentity:modalIdentity
      anchor:modalOwner active:NO eligible:YES];
  XCTAssertNotNil(inactiveModal);
  if (!inactiveModal) return;
  NSArray *releasedModal = @[application, inactiveModal];
  XCTAssertTrue([adapter reconcilePortalPresentations:releasedModal
      orderProvider:order ifCurrent:nil]);
  XCTAssertFalse(fixture.view(12).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(42).accessibilityElementsHidden);

  XCTAssertTrue([adapter reconcilePortalPresentations:presentations
      orderProvider:order ifCurrent:nil]);
  auto modalView = (RCTModalHostViewComponentView *)fixture.view(30);
  UIView<RCTComponentViewProtocol> *panel = (UIView<RCTComponentViewProtocol> *)fixture.view(11);
  [modalView unmountChildComponentView:panel index:1];
  XCTAssertFalse([adapter reconcilePortalPresentations:presentations
      orderProvider:order ifCurrent:nil]);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(42).accessibilityElementsHidden);
  [modalView mountChildComponentView:panel index:1];
  XCTAssertTrue([adapter reconcilePortalPresentations:presentations
      orderProvider:order ifCurrent:nil]);
  [adapter invalidate];
  XCTAssertFalse(fixture.view(12).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(42).accessibilityElementsHidden);
}

- (void)testCompletedMountMasksNewBackgroundWithoutChangingActivePresentation
{
  RuntimeFixture fixture;
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true);
  auto background = fixture.node(12);
  auto area = fixture.node(11, {background, host});
  auto outside = fixture.node(2);
  auto firstRoot = fixture.mount({outside, area});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  UIView *owner = fixture.view(14);
  UIView *oldBackground = fixture.view(12);
  [observer didMount:first owner:owner];
  auto identity = [BottomSheetPresentationIdentity new];
  auto presentation = [BottomSheetPortalPresentation presentationForIdentity:identity
      anchor:owner active:YES eligible:YES];
  auto adapter = [BottomSheetPresentationModalIsolationAdapter new];
  BottomSheetPresentationOrderProvider order = ^BottomSheetPresentationOrder(
      BottomSheetPresentationIdentity *a, BottomSheetPresentationIdentity *b) {
    return BottomSheetPresentationOrderUnknown;
  };
  XCTAssertNotNil(presentation);
  [adapter reconcilePortalPresentations:@[presentation] orderProvider:order ifCurrent:nil];
  XCTAssertTrue(oldBackground.accessibilityElementsHidden);

  auto nextArea = area->clone({.children = std::make_shared<const Children>(
      Children{background, fixture.node(16), host})});
  auto nextRoot = firstRoot->ShadowNode::clone({.children =
      std::make_shared<const Children>(Children{outside, nextArea})});
  auto mutations = calculateShadowViewMutations(*firstRoot, *nextRoot);
  MountingTransaction next(1, 2, std::move(mutations), {});
  [observer willMount:next];
  XCTAssertFalse([adapter reconcilePortalPresentations:@[presentation]
      orderProvider:order ifCurrent:nil]);
  XCTAssertTrue(oldBackground.accessibilityElementsHidden);

  fixture.mount({outside, nextArea});
  [observer didMount:next owner:owner];
  XCTAssertTrue([adapter reconcilePortalPresentations:@[presentation]
      orderProvider:order ifCurrent:nil]);

  XCTAssertEqual(fixture.view(14), owner);
  XCTAssertEqual(presentation.identity, identity);
  XCTAssertTrue(fixture.view(16).accessibilityElementsHidden);
  XCTAssertTrue(oldBackground.accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(2).accessibilityElementsHidden);
  XCTAssertFalse(owner.accessibilityElementsHidden);
  [adapter invalidate];
  XCTAssertFalse(oldBackground.accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(16).accessibilityElementsHidden);
}

- (void)testReleasedBoundaryClearsItsFlagWithoutRestoringMasksWhenPublicationIsRejected
{
  RuntimeFixture fixture;
  fixture.mount({fixture.node(11, {fixture.node(12),
      fixture.node(13, {fixture.node(14)}, true)})});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  auto presentation = [[BottomSheetPortalPresentation alloc]
      initWithIdentity:[BottomSheetPresentationIdentity new] owner:fixture.view(14)
      observer:observer active:YES eligible:YES];
  auto adapter = [BottomSheetPresentationModalIsolationAdapter new];
  UIView *boundary = [UIView new];
  [adapter registerBoundary:boundary];
  BottomSheetPresentationOrderProvider order = ^BottomSheetPresentationOrder(
      BottomSheetPresentationIdentity *a, BottomSheetPresentationIdentity *b) {
    return BottomSheetPresentationOrderUnknown;
  };
  [adapter reconcilePortalPresentations:@[presentation] orderProvider:order ifCurrent:nil];
  boundary.accessibilityViewIsModal = YES;
  [adapter unregisterBoundary:boundary];
  MountingTransaction inFlight(1, 2, {}, {});
  [observer willMount:inFlight];

  [adapter reconcilePortalPresentations:@[presentation] orderProvider:order ifCurrent:nil];

  XCTAssertFalse(boundary.accessibilityViewIsModal);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  [adapter invalidate];
  XCTAssertFalse(fixture.view(12).accessibilityElementsHidden);
}

- (void)testIsolationWriterRetainsUnknownActivePathsAndPreservesLeasesForRejectedSources
{
  RuntimeFixture fixture;
  fixture.mount({fixture.node(11, {fixture.node(12),
      fixture.node(13, {fixture.node(14), fixture.node(15)}, true)})});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  [observer didMount:first owner:fixture.view(15)];
  auto identityA = [BottomSheetPresentationIdentity new];
  auto identityB = [BottomSheetPresentationIdentity new];
  NSArray *presentations = @[
    [[BottomSheetPortalPresentation alloc] initWithIdentity:identityA owner:fixture.view(14)
        observer:observer active:YES eligible:YES],
    [[BottomSheetPortalPresentation alloc] initWithIdentity:identityB owner:fixture.view(15)
        observer:observer active:YES eligible:YES]];
  auto adapter = [BottomSheetPresentationModalIsolationAdapter new];
  BottomSheetPresentationOrderProvider known = ^BottomSheetPresentationOrder(
      BottomSheetPresentationIdentity *a, BottomSheetPresentationIdentity *b) {
    return a == identityB ? BottomSheetPresentationOrderAbove : BottomSheetPresentationOrderBelow;
  };
  BottomSheetPresentationOrderProvider unknown = ^BottomSheetPresentationOrder(
      BottomSheetPresentationIdentity *a, BottomSheetPresentationIdentity *b) {
    return BottomSheetPresentationOrderUnknown;
  };
  [adapter reconcilePortalPresentations:presentations orderProvider:known ifCurrent:nil];
  XCTAssertTrue(fixture.view(14).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:fixture.view(14) presentations:presentations orderProvider:known],
      BottomSheetPresentationEscapeRouteConsume);

  [adapter reconcilePortalPresentations:presentations orderProvider:unknown
      ifCurrent:^BOOL { return NO; }];
  XCTAssertTrue(fixture.view(14).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);

  [adapter reconcilePortalPresentations:presentations orderProvider:unknown ifCurrent:nil];
  XCTAssertFalse(fixture.view(14).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(15).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityB
      actionOrigin:fixture.view(15) presentations:presentations orderProvider:unknown],
      BottomSheetPresentationEscapeRoutePassThrough);

  MountingTransaction inFlight(1, 2, {}, {});
  [observer willMount:inFlight];
  XCTAssertFalse([adapter reconcilePortalPresentations:presentations
      orderProvider:known ifCurrent:nil]);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(14).accessibilityElementsHidden);
  [adapter invalidate];
  XCTAssertFalse(fixture.view(12).accessibilityElementsHidden);
}

- (void)testAuthorityAnchorUsesItsCurrentRegisteredOwnerAndObserverWithoutAnotherRegistration
{
  RuntimeFixture fixture;
  fixture.mount({fixture.node(11, {fixture.node(12),
      fixture.node(13, {fixture.node(14)}, true)})});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  PortalOwnedContentAnchor *anchor = [PortalOwnedContentAnchor new];
  anchor.portalNativeOwner = fixture.view(14);
  [fixture.view(14) addSubview:anchor];
  anchor.accessibilityViewIsModal = YES;
  auto identity = [BottomSheetPresentationIdentity new];
  auto presentation = [BottomSheetPortalPresentation presentationForIdentity:identity
      anchor:anchor active:YES eligible:YES];
  auto adapter = [BottomSheetPresentationModalIsolationAdapter new];
  [adapter registerBoundary:anchor];
  if (presentation) {
    [adapter reconcilePortalPresentations:@[presentation]
        orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                    BottomSheetPresentationIdentity *b) {
          return BottomSheetPresentationOrderUnknown;
        } ifCurrent:nil];
  }

  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  XCTAssertFalse(anchor.accessibilityViewIsModal);

  [observer invalidate];
  XCTAssertNil([BottomSheetPortalPresentation presentationForIdentity:identity
      anchor:anchor active:YES eligible:YES]);
  [adapter invalidate];
}

- (void)testExistingIsolationWriterAppliesIndependentPortalCutsWithoutHidingOutsideControls
{
  RuntimeFixture fixture;
  fixture.mount({fixture.node(2),
      fixture.node(11, {fixture.node(12), fixture.node(13, {fixture.node(14)}, true)}),
      fixture.node(21, {fixture.node(22), fixture.node(23, {fixture.node(24)}, true)})});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  [observer didMount:first owner:fixture.view(24)];
  UIView *nativeToolbar = [UIView new];
  [fixture.viewport addSubview:nativeToolbar];
  auto adapter = [BottomSheetPresentationModalIsolationAdapter new];
  [adapter registerBoundary:fixture.view(14)];
  [adapter registerBoundary:fixture.view(24)];
  NSArray *presentations = @[
    [[BottomSheetPortalPresentation alloc] initWithIdentity:[BottomSheetPresentationIdentity new]
        owner:fixture.view(14) observer:observer active:YES eligible:YES],
    [[BottomSheetPortalPresentation alloc] initWithIdentity:[BottomSheetPresentationIdentity new]
        owner:fixture.view(24) observer:observer active:YES eligible:YES]];

  [adapter reconcilePortalPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        return BottomSheetPresentationOrderUnknown;
      } ifCurrent:nil];

  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(22).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(11).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(21).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(2).accessibilityElementsHidden);
  XCTAssertFalse(nativeToolbar.accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(14).accessibilityViewIsModal);
  XCTAssertFalse(fixture.view(24).accessibilityViewIsModal);

  [adapter invalidate];
  XCTAssertFalse(fixture.view(12).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(22).accessibilityElementsHidden);
}

- (void)testFirstOwnCompletedMountBootstrapsTheCurrentAppliedRoot
{
  RuntimeFixture fixture;
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true);
  auto root = fixture.mount({fixture.node(2), fixture.node(11, {fixture.node(12), host})});
  auto observer = fixture.observer();
  // The first own component can be created after willMount notifications.
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  std::vector<PortalRetainedPaths> paths{{&host->getFamily(), {&sheet->getFamily()}}};

  XCTAssertTrue([observer currentRootForSurface:1] == root);
  XCTAssertEqualObjects([observer hiddenBranchesForSurface:1 retainedPaths:paths],
                       ([NSSet setWithObject:fixture.view(12)]));
}

- (void)testDuplicateObserverCannotExposeAnInFlightMountAndCompletionAddsNewBackground
{
  RuntimeFixture fixture;
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true);
  auto background = fixture.node(12);
  auto area = fixture.node(11, {background, host});
  auto firstRoot = fixture.mount({area});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  MountingTransaction beginNext(1, 2, {}, {});

  [observer willMount:beginNext];
  [observer didMount:first owner:fixture.view(14)];
  XCTAssertFalse(bool([observer currentRootForSurface:1]));

  auto changedArea = area->clone({.children = std::make_shared<const Children>(
      Children{background, host, fixture.node(16)})});
  auto nextRoot = fixture.mount({changedArea});
  auto mutations = calculateShadowViewMutations(*firstRoot, *nextRoot);
  MountingTransaction next(1, 2, std::move(mutations), {});
  [observer didMount:next owner:fixture.view(14)];
  std::vector<PortalRetainedPaths> paths{{&host->getFamily(), {&sheet->getFamily()}}};

  XCTAssertTrue([observer currentRootForSurface:1] == nextRoot);
  XCTAssertEqualObjects([observer hiddenBranchesForSurface:1 retainedPaths:paths],
                       ([NSSet setWithObjects:fixture.view(12), fixture.view(16), nil]));
}

- (void)testCurrentSourceOwnerResolvesOnlyItsOwnCompletedPublication
{
  RuntimeFixture fixture;
  auto root = fixture.mount({fixture.node(12), fixture.node(14)});
  auto observer = fixture.observer();
  UIView *owner = fixture.view(14);
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:owner];
  UIView *foreignInstance = [UIView new];
  foreignInstance.tag = owner.tag;

  XCTAssertTrue([observer currentRootForOwner:owner] == root);
  XCTAssertFalse(bool([observer currentRootForOwner:fixture.view(12)]));
  XCTAssertFalse(bool([observer currentRootForOwner:foreignInstance]));

  MountingTransaction next(1, 2, {}, {});
  [observer willMount:next];
  XCTAssertFalse(bool([observer currentRootForOwner:owner]));
  [observer withdrawOwner:owner];
  XCTAssertFalse(bool([observer currentRootForOwner:owner]));
}

- (void)testIndependentPortalContextsRetainTheirOwnTopAndOnlyTheirLocalBackground
{
  RuntimeFixture fixture;
  auto sheetA = fixture.node(14);
  auto sheetB = fixture.node(24);
  fixture.mount({fixture.node(2),
      fixture.node(11, {fixture.node(12), fixture.node(13, {sheetA}, true)}),
      fixture.node(21, {fixture.node(22), fixture.node(23, {sheetB}, true)})});
  std::weak_ptr<UIManager> current = fixture.uiManager;
  __block NSUInteger reads = 0;
  __block NSUInteger moveOnRead = 0;
  __block UIView *originToMove = nil;
  UIView *targetOwner = fixture.view(24);
  auto observer = [[BottomSheetPortalMountingObserver alloc]
      initWithMountingManager:fixture.mountingManager uiManagerProvider:^{
        if (++reads == moveOnRead) [targetOwner addSubview:originToMove];
        return current.lock();
      } beforeDeletingNativeSubtrees:nil];
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  [observer didMount:first owner:fixture.view(24)];
  auto identityA = [BottomSheetPresentationIdentity new];
  auto identityB = [BottomSheetPresentationIdentity new];
  NSArray *presentations = @[
    [[BottomSheetPortalPresentation alloc] initWithIdentity:identityA owner:fixture.view(14)
        observer:observer active:YES eligible:YES],
    [[BottomSheetPortalPresentation alloc] initWithIdentity:identityB owner:fixture.view(24)
        observer:observer active:YES eligible:YES]];

  auto isolation = [BottomSheetPortalIsolation projectionFromPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        return a == identityB ? BottomSheetPresentationOrderAbove : BottomSheetPresentationOrderBelow;
      }];

  XCTAssertNotNil(isolation);
  XCTAssertEqualObjects(isolation.hiddenBranches,
      ([NSSet setWithObjects:fixture.view(12), fixture.view(22), nil]));
  XCTAssertEqualObjects(isolation.topPresentationIdentities,
      ([NSSet setWithObjects:identityA, identityB, nil]));
  XCTAssertFalse(fixture.view(12).accessibilityElementsHidden);
  BottomSheetPresentationOrderProvider globalOrder = ^BottomSheetPresentationOrder(
      BottomSheetPresentationIdentity *a, BottomSheetPresentationIdentity *b) {
    return a == identityB ? BottomSheetPresentationOrderAbove : BottomSheetPresentationOrderBelow;
  };
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:fixture.view(14) presentations:presentations orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityB
      actionOrigin:fixture.view(24) presentations:presentations orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  UIView *nativeChild = [UIView new];
  [fixture.view(14) addSubview:nativeChild];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:nativeChild presentations:presentations orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  UIAccessibilityElement *outer = [[UIAccessibilityElement alloc] initWithAccessibilityContainer:nativeChild];
  UIAccessibilityElement *inner = [[UIAccessibilityElement alloc] initWithAccessibilityContainer:outer];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:inner presentations:presentations orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  [fixture.view(24) addSubview:nativeChild];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityB
      actionOrigin:inner presentations:presentations orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:inner presentations:presentations orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteConsume);
  [nativeChild removeFromSuperview];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityB
      actionOrigin:inner presentations:presentations orderProvider:globalOrder],
      BottomSheetPresentationEscapeRoutePassThrough);
  outer.accessibilityContainer = inner;
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:inner presentations:presentations orderProvider:globalOrder],
      BottomSheetPresentationEscapeRoutePassThrough);
  outer.accessibilityContainer = nil;
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:fixture.view(2) presentations:presentations orderProvider:globalOrder],
      BottomSheetPresentationEscapeRoutePassThrough);

  [fixture.view(14) addSubview:nativeChild];
  reads = 0;
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:nativeChild presentations:presentations orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  NSUInteger queryReads = reads;
  // Reenter at every provider checkpoint. Once the origin moves into B, A
  // must not close using an earlier mapping; reject it or route to B.
  originToMove = nativeChild;
  for (NSUInteger checkpoint = 1; checkpoint <= queryReads; checkpoint++) {
    [fixture.view(14) addSubview:nativeChild];
    reads = 0;
    moveOnRead = checkpoint;
    auto route = [BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
        actionOrigin:nativeChild presentations:presentations orderProvider:globalOrder];
    XCTAssertEqual(nativeChild.superview, targetOwner);
    XCTAssertNotEqual(route, BottomSheetPresentationEscapeRouteAttemptLocal);
  }
  moveOnRead = 0;
}

- (void)testPortalProjectionRejectsACompletedRootChangedDuringNativeOrderResolution
{
  RuntimeFixture fixture;
  auto sheetA = fixture.node(14);
  auto sheetB = fixture.node(15);
  auto host = fixture.node(13, {sheetA, sheetB}, true);
  auto background = fixture.node(12);
  auto area = fixture.node(11, {background, host});
  auto root = fixture.mount({area});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  [observer didMount:first owner:fixture.view(15)];
  auto identityA = [BottomSheetPresentationIdentity new];
  auto identityB = [BottomSheetPresentationIdentity new];
  NSArray *presentations = @[
    [[BottomSheetPortalPresentation alloc] initWithIdentity:identityA owner:fixture.view(14)
        observer:observer active:YES eligible:YES],
    [[BottomSheetPortalPresentation alloc] initWithIdentity:identityB owner:fixture.view(15)
        observer:observer active:YES eligible:YES]];
  RuntimeFixture *runtime = &fixture;
  __block BOOL changed = NO;

  auto isolation = [BottomSheetPortalIsolation projectionFromPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        if (!changed) {
          changed = YES;
          MountingTransaction begin(1, 2, {}, {});
          [observer willMount:begin];
          auto nextArea = area->clone({.children = std::make_shared<const Children>(
              Children{background, host, runtime->node(16)})});
          auto nextRoot = runtime->mount({nextArea});
          auto mutations = calculateShadowViewMutations(*root, *nextRoot);
          MountingTransaction next(1, 2, std::move(mutations), {});
          [observer didMount:next owner:runtime->view(14)];
          [observer didMount:next owner:runtime->view(15)];
        }
        return a == identityB ? BottomSheetPresentationOrderAbove : BottomSheetPresentationOrderBelow;
      }];

  XCTAssertTrue(changed);
  XCTAssertNil(isolation);
}

- (void)testSharedContainingBlockMergesHostsAndUnknownTopRetainsBothNativePaths
{
  RuntimeFixture fixture;
  fixture.mount({fixture.node(11, {fixture.node(12),
      fixture.node(13, {fixture.node(14)}, true),
      fixture.node(17, {fixture.node(18)}, true)})});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  [observer didMount:first owner:fixture.view(18)];
  auto identityA = [BottomSheetPresentationIdentity new];
  auto identityB = [BottomSheetPresentationIdentity new];
  NSArray *presentations = @[
    [[BottomSheetPortalPresentation alloc] initWithIdentity:identityA owner:fixture.view(14)
        observer:observer active:YES eligible:YES],
    [[BottomSheetPortalPresentation alloc] initWithIdentity:identityB owner:fixture.view(18)
        observer:observer active:YES eligible:YES]];

  auto known = [BottomSheetPortalIsolation projectionFromPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        return a == identityB ? BottomSheetPresentationOrderAbove : BottomSheetPresentationOrderBelow;
      }];
  XCTAssertEqualObjects(known.hiddenBranches,
      ([NSSet setWithObjects:fixture.view(12), fixture.view(13), nil]));
  XCTAssertEqualObjects(known.topPresentationIdentities, [NSSet setWithObject:identityB]);

  auto unknown = [BottomSheetPortalIsolation projectionFromPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        return BottomSheetPresentationOrderUnknown;
      }];
  XCTAssertEqualObjects(unknown.hiddenBranches, [NSSet setWithObject:fixture.view(12)]);
  XCTAssertEqual(unknown.topPresentationIdentities.count, 0);
}

- (void)testInactiveSourcesReleaseTheCutAndWithdrawnActiveSourcesRejectIt
{
  RuntimeFixture fixture;
  fixture.mount({fixture.node(11, {fixture.node(12),
      fixture.node(13, {fixture.node(14)}, true)})});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  UIView *owner = fixture.view(14);
  [observer didMount:first owner:owner];
  [observer withdrawOwner:owner];
  auto identity = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationOrderProvider order = ^BottomSheetPresentationOrder(
      BottomSheetPresentationIdentity *a, BottomSheetPresentationIdentity *b) {
    return BottomSheetPresentationOrderUnknown;
  };
  auto inactive = [[BottomSheetPortalPresentation alloc] initWithIdentity:identity owner:owner
      observer:observer active:NO eligible:YES];
  auto released = [BottomSheetPortalIsolation projectionFromPresentations:@[inactive] orderProvider:order];
  XCTAssertNotNil(released);
  XCTAssertEqual(released.hiddenBranches.count, 0);
  XCTAssertEqual(released.topPresentationIdentities.count, 0);

  auto active = [[BottomSheetPortalPresentation alloc] initWithIdentity:identity owner:owner
      observer:observer active:YES eligible:YES];
  XCTAssertNil([BottomSheetPortalIsolation projectionFromPresentations:@[active] orderProvider:order]);
}

- (void)testPortalProjectionRejectsASourceWithdrawnDuringNativeOrderResolution
{
  RuntimeFixture fixture;
  fixture.mount({fixture.node(11, {fixture.node(12),
      fixture.node(13, {fixture.node(14), fixture.node(15)}, true)})});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  UIView *ownerA = fixture.view(14);
  UIView *ownerB = fixture.view(15);
  [observer didMount:first owner:ownerA];
  [observer didMount:first owner:ownerB];
  auto identityA = [BottomSheetPresentationIdentity new];
  auto identityB = [BottomSheetPresentationIdentity new];
  NSArray *presentations = @[
    [[BottomSheetPortalPresentation alloc] initWithIdentity:identityA owner:ownerA
        observer:observer active:YES eligible:YES],
    [[BottomSheetPortalPresentation alloc] initWithIdentity:identityB owner:ownerB
        observer:observer active:YES eligible:YES]];

  auto isolation = [BottomSheetPortalIsolation projectionFromPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        [observer withdrawOwner:ownerB];
        return a == identityB ? BottomSheetPresentationOrderAbove : BottomSheetPresentationOrderBelow;
      }];

  XCTAssertNil(isolation);
}

- (void)testCrossRuntimeCompositionKeepsOnlyCurrentRetainedPaths
{
  RuntimeFixture outer;
  RuntimeFixture inner;
  outer.mount({outer.node(11, {outer.node(12),
      outer.node(13, {outer.node(14), outer.node(18)}, true)})});
  inner.mount({inner.node(2), inner.node(21, {inner.node(22),
      inner.node(23, {inner.node(24)}, true)})});
  UIView *coveredOuter = outer.view(14);
  UIView *nativeChrome = [UIView new];
  UIView *bridge = [UIView new];
  [coveredOuter addSubview:nativeChrome];
  [coveredOuter addSubview:bridge];
  [bridge addSubview:inner.viewport];
  auto outerObserver = outer.observer();
  auto innerObserver = inner.observer();
  MountingTransaction first(1, 1, {}, {});
  [outerObserver didMount:first owner:coveredOuter];
  [outerObserver didMount:first owner:outer.view(18)];
  [innerObserver didMount:first owner:inner.view(24)];
  auto outerTop = [BottomSheetPresentationIdentity new];
  auto innerTop = [BottomSheetPresentationIdentity new];
  NSArray *presentations = @[
    [[BottomSheetPortalPresentation alloc] initWithIdentity:outerTop owner:outer.view(18)
        observer:outerObserver active:YES eligible:YES],
    [[BottomSheetPortalPresentation alloc] initWithIdentity:innerTop owner:inner.view(24)
        observer:innerObserver active:YES eligible:YES]];

  auto isolation = [BottomSheetPortalIsolation projectionFromPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        return BottomSheetPresentationOrderUnknown;
      }];

  XCTAssertEqualObjects(isolation.hiddenBranches,
      ([NSSet setWithObjects:outer.view(12), nativeChrome, inner.view(2), inner.view(22), nil]));
  XCTAssertEqualObjects(isolation.topPresentationIdentities,
      ([NSSet setWithObjects:outerTop, innerTop, nil]));

  auto outerA = [BottomSheetPresentationIdentity new];
  NSArray *wholeOuter = @[
    [[BottomSheetPortalPresentation alloc] initWithIdentity:outerA owner:coveredOuter
        observer:outerObserver active:YES eligible:YES],
    [[BottomSheetPortalPresentation alloc] initWithIdentity:outerTop owner:outer.view(18)
        observer:outerObserver active:NO eligible:YES],
    [[BottomSheetPortalPresentation alloc] initWithIdentity:innerTop owner:inner.view(24)
        observer:innerObserver active:YES eligible:YES]];
  auto whole = [BottomSheetPortalIsolation projectionFromPresentations:wholeOuter
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        return BottomSheetPresentationOrderUnknown;
      }];
  XCTAssertEqualObjects(whole.hiddenBranches,
      ([NSSet setWithObjects:outer.view(12), outer.view(18), inner.view(22), nil]));
  XCTAssertEqualObjects(whole.topPresentationIdentities,
      ([NSSet setWithObjects:outerA, innerTop, nil]));
  BottomSheetPresentationOrderProvider order = ^BottomSheetPresentationOrder(
      BottomSheetPresentationIdentity *a, BottomSheetPresentationIdentity *b) {
    return BottomSheetPresentationOrderUnknown;
  };
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:innerTop
      actionOrigin:inner.view(24) presentations:wholeOuter orderProvider:order],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:outerA
      actionOrigin:inner.view(24) presentations:wholeOuter orderProvider:order],
      BottomSheetPresentationEscapeRouteConsume);
  NSArray *inactiveInner = @[wholeOuter[0], wholeOuter[1],
      [[BottomSheetPortalPresentation alloc] initWithIdentity:innerTop owner:inner.view(24)
          observer:innerObserver active:NO eligible:YES]];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:outerA
      actionOrigin:inner.view(22) presentations:inactiveInner orderProvider:order],
      BottomSheetPresentationEscapeRouteAttemptLocal);

  UIView *innerOwner = inner.view(24);
  UIView *lateBackground = [UIView new];
  UIView *innerRoot = inner.view(1);
  ReentrantPortalNativeContainer *backgroundAccessor = [ReentrantPortalNativeContainer new];
  [outer.view(11) addSubview:backgroundAccessor];
  __block NSUInteger backgroundReads = 0;
  backgroundAccessor.onReadSubviews = ^{
    if (++backgroundReads == 2) [coveredOuter addSubview:lateBackground];
  };
  auto changedDuringLowerQuery = [BottomSheetPortalIsolation projectionFromPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        return BottomSheetPresentationOrderUnknown;
      }];
  XCTAssertNil(changedDuringLowerQuery);
  XCTAssertEqual(backgroundReads, 0);
  [backgroundAccessor removeFromSuperview];
  [lateBackground removeFromSuperview];

  ReentrantPortalNativeContainer *unsafeBridge = [ReentrantPortalNativeContainer new];
  [bridge removeFromSuperview];
  [coveredOuter addSubview:unsafeBridge];
  [unsafeBridge addSubview:inner.viewport];
  __block NSUInteger readCount = 0;
  unsafeBridge.onReadSubviews = ^{
    if (++readCount == 2) [innerRoot addSubview:lateBackground];
  };
  auto changedDuringValidation = [BottomSheetPortalIsolation projectionFromPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        return BottomSheetPresentationOrderUnknown;
      }];
  XCTAssertNil(changedDuringValidation);
  XCTAssertEqual(readCount, 0);
  unsafeBridge.onReadSubviews = nil;
  [lateBackground removeFromSuperview];

  __weak ReentrantPortalNativeContainer *weakBridge = unsafeBridge;
  unsafeBridge.onReadSubviews = ^{
    weakBridge.onReadSubviews = nil;
    [coveredOuter addSubview:lateBackground];
  };
  auto changedChildren = [BottomSheetPortalIsolation projectionFromPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        return BottomSheetPresentationOrderUnknown;
      }];
  XCTAssertNil(changedChildren);
  [lateBackground removeFromSuperview];

  UIView *innerHost = innerOwner.superview;
  unsafeBridge.onReadSubviews = ^{ [innerRoot addSubview:innerOwner]; };
  auto reparented = [BottomSheetPortalIsolation projectionFromPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        return BottomSheetPresentationOrderUnknown;
      }];
  XCTAssertNil(reparented);
  unsafeBridge.onReadSubviews = nil;
  [innerHost addSubview:innerOwner];

  unsafeBridge.onReadSubviews = ^{ [innerObserver withdrawOwner:innerOwner]; };
  auto withdrawn = [BottomSheetPortalIsolation projectionFromPresentations:presentations
      orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                  BottomSheetPresentationIdentity *b) {
        return BottomSheetPresentationOrderUnknown;
      }];
  XCTAssertNil(withdrawn);
}

- (void)testLastOwnObserverWithdrawalReleasesThePublicationAndCanBootstrapAgain
{
  RuntimeFixture fixture;
  auto sheetA = fixture.node(14);
  auto sheetB = fixture.node(15);
  auto host = fixture.node(13, {sheetA, sheetB}, true);
  auto root = fixture.mount({fixture.node(11, {fixture.node(12), host})});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  [observer didMount:first owner:fixture.view(15)];

  [observer withdrawOwner:fixture.view(14)];
  XCTAssertTrue([observer currentRootForSurface:1] == root);
  [observer withdrawOwner:fixture.view(15)];
  XCTAssertFalse(bool([observer currentRootForSurface:1]));

  [observer didMount:first owner:fixture.view(15)];
  std::vector<PortalRetainedPaths> paths{{&host->getFamily(), {&sheetB->getFamily()}}};
  XCTAssertEqualObjects([observer hiddenBranchesForSurface:1 retainedPaths:paths],
                       ([NSSet setWithObjects:fixture.view(12), fixture.view(14), nil]));
}

- (void)testChangingTheCurrentUIManagerRevokesTheOldPublication
{
  RuntimeFixture fixture;
  auto root = fixture.mount({fixture.node(14)});
  __block std::weak_ptr<UIManager> current = fixture.uiManager;
  auto observer = [[BottomSheetPortalMountingObserver alloc]
      initWithMountingManager:fixture.mountingManager
      uiManagerProvider:^{ return current.lock(); } beforeDeletingNativeSubtrees:nil];
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  XCTAssertTrue([observer currentRootForSurface:1] == root);
  auto replacement = std::make_shared<UIManager>(RuntimeExecutor{}, fixture.context);

  current = replacement;

  XCTAssertFalse(bool([observer currentRootForSurface:1]));
  [observer didMount:first owner:fixture.view(14)];
  XCTAssertFalse(bool([observer currentRootForSurface:1]));
  [observer invalidate];
  current = fixture.uiManager;
  [observer didMount:first owner:fixture.view(14)];
  XCTAssertFalse(bool([observer currentRootForSurface:1]));
}

- (void)testReplacementCoordinatorCanRestartNumberingWithoutReusingOldFamilies
{
  RuntimeFixture fixture;
  auto oldSheet = fixture.node(14);
  auto oldHost = fixture.node(13, {oldSheet}, true);
  auto oldRoot = fixture.mount({fixture.node(11, {fixture.node(12), oldHost})});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  fixture.mount({});
  fixture.uiManager->getShadowTreeRegistry().remove(1);
  auto tree = std::make_unique<ShadowTree>(1,
      LayoutConstraints{.minimumSize = {800, 600}, .maximumSize = {800, 600}},
      LayoutContext{}, *fixture.uiManager, *fixture.context);
  fixture.tree = tree.get();
  fixture.uiManager->getShadowTreeRegistry().add(std::move(tree));
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true);
  auto root = fixture.mount({fixture.node(11, {fixture.node(12), host})});
  XCTAssertFalse(bool([observer currentRootForSurface:1]));

  [observer didMount:first owner:fixture.view(14)];
  std::vector<PortalRetainedPaths> oldPaths{{&oldHost->getFamily(), {&oldSheet->getFamily()}}};
  std::vector<PortalRetainedPaths> paths{{&host->getFamily(), {&sheet->getFamily()}}};

  XCTAssertTrue([observer currentRootForSurface:1] == root);
  XCTAssertTrue(root != oldRoot);
  XCTAssertNil([observer hiddenBranchesForSurface:1 retainedPaths:oldPaths]);
  XCTAssertEqualObjects([observer hiddenBranchesForSurface:1 retainedPaths:paths],
                       ([NSSet setWithObject:fixture.view(12)]));
}

- (void)testConsecutiveWillMountRetiresTheOldMaskBeforeNativeDeletion
{
  RuntimeFixture fixture;
  auto background = fixture.node(12);
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true);
  auto area = fixture.node(11, {background, host});
  auto root = fixture.mount({area});
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *otherContext = [UIView new];
  UIView *deletedView = fixture.view(12);
  [ledger reconcileHiddenBranches:[NSSet setWithObjects:deletedView, otherContext, nil]
         beforeRestoringBranches:nil];
  std::weak_ptr<UIManager> current = fixture.uiManager;
  auto observer = [[BottomSheetPortalMountingObserver alloc]
      initWithMountingManager:fixture.mountingManager
      uiManagerProvider:^{ return current.lock(); }
      beforeDeletingNativeSubtrees:^(BottomSheetNativeViewDeletion *deletion) {
        [ledger retireHiddenBranchesForDeletion:deletion];
      }];
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  auto nextArea = area->clone({.children = std::make_shared<const Children>(Children{host})});
  auto nextRoot = root->ShadowNode::clone({.children =
      std::make_shared<const Children>(Children{nextArea})});
  auto mutations = calculateShadowViewMutations(*root, *nextRoot);
  MountingTransaction next(1, 2, std::move(mutations), {});

  [observer willMount:next];

  XCTAssertFalse(deletedView.accessibilityElementsHidden);
  XCTAssertTrue(otherContext.accessibilityElementsHidden);
  XCTAssertFalse(bool([observer currentRootForSurface:1]));
  fixture.mount({nextArea});
  deletedView.accessibilityElementsHidden = YES;
  [ledger reconcileHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertTrue(deletedView.accessibilityElementsHidden);
}

- (void)testAMissedPublicationCannotRetireNativeLeasesUsingOldBindings
{
  RuntimeFixture fixture;
  auto background = fixture.node(12);
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true);
  auto area = fixture.node(11, {background, host});
  fixture.mount({area});
  BottomSheetAccessibilityMaskLedger *ledger = [BottomSheetAccessibilityMaskLedger new];
  UIView *backgroundView = fixture.view(12);
  [ledger reconcileHiddenBranches:[NSSet setWithObject:backgroundView]
         beforeRestoringBranches:nil];
  std::weak_ptr<UIManager> current = fixture.uiManager;
  auto observer = [[BottomSheetPortalMountingObserver alloc]
      initWithMountingManager:fixture.mountingManager
      uiManagerProvider:^{ return current.lock(); }
      beforeDeletingNativeSubtrees:^(BottomSheetNativeViewDeletion *deletion) {
        [ledger retireHiddenBranchesForDeletion:deletion];
      }];
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  // A real completed mount was missed. Its registry may now contain recycled
  // instances, so pre-delete retirement must not infer current leases from tags.
  auto second = fixture.mount({area, fixture.node(2)});
  auto nextArea = area->clone({.children = std::make_shared<const Children>(Children{host})});
  auto nextRoot = second->ShadowNode::clone({.children =
      std::make_shared<const Children>(Children{nextArea})});
  auto mutations = calculateShadowViewMutations(*second, *nextRoot);
  MountingTransaction gap(1, 3, std::move(mutations), {});

  [observer willMount:gap];

  XCTAssertTrue(backgroundView.accessibilityElementsHidden);
  XCTAssertFalse(bool([observer currentRootForSurface:1]));
}

- (void)testFlatteningAParentPreservesMasksOnSurvivingRegisteredAndNativeOnlyChildren
{
  RuntimeFixture fixture;
  auto background = fixture.node(12);
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true);
  auto area = fixture.node(11, {background, host});
  auto root = fixture.mount({area});
  UIView *backgroundView = fixture.view(12);
  UIView *nativeChild = [UIView new];
  [backgroundView addSubview:nativeChild];
  auto ledger = [BottomSheetAccessibilityMaskLedger new];
  [ledger reconcileHiddenBranches:[NSSet setWithObjects:backgroundView, nativeChild, nil]
         beforeRestoringBranches:nil];
  std::weak_ptr<UIManager> current = fixture.uiManager;
  auto observer = [[BottomSheetPortalMountingObserver alloc]
      initWithMountingManager:fixture.mountingManager
      uiManagerProvider:^{ return current.lock(); }
      beforeDeletingNativeSubtrees:^(BottomSheetNativeViewDeletion *deletion) {
        [ledger retireHiddenBranchesForDeletion:deletion];
      }];
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  auto props = fixture.descriptor.cloneProps(PropsParserContext(1, *fixture.context),
      area->getProps(), RawProps(folly::dynamic::object("collapsable", true)));
  auto flattened = area->clone({.props = props});
  auto nextRoot = root->ShadowNode::clone({.children =
      std::make_shared<const Children>(Children{flattened})});
  auto mutations = calculateShadowViewMutations(*root, *nextRoot);
  MountingTransaction next(1, 2, std::move(mutations), {});

  [observer willMount:next];

  XCTAssertTrue(backgroundView.accessibilityElementsHidden);
  XCTAssertTrue(nativeChild.accessibilityElementsHidden);
  fixture.mount({flattened});
  XCTAssertNil(fixture.view(11));
  XCTAssertEqual(fixture.view(12), backgroundView);
  XCTAssertTrue(backgroundView.accessibilityElementsHidden);
  XCTAssertTrue(nativeChild.accessibilityElementsHidden);
  [ledger reconcileHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertFalse(backgroundView.accessibilityElementsHidden);
  XCTAssertFalse(nativeChild.accessibilityElementsHidden);
}

- (void)testFlattenedBackgroundParentTransfersItsMaskToTheSurvivingNativeFrontier
{
  RuntimeFixture fixture;
  auto backgroundControl = fixture.node(16);
  auto background = fixture.node(12, {backgroundControl});
  auto sheet = fixture.node(14);
  auto host = fixture.node(13, {sheet}, true);
  auto area = fixture.node(11, {background, host});
  auto root = fixture.mount({area});
  UIView *deletedParent = fixture.view(12);
  UIView *survivingControl = fixture.view(16);
  auto ledger = [BottomSheetAccessibilityMaskLedger new];
  [ledger reconcileHiddenBranches:[NSSet setWithObject:deletedParent]
         beforeRestoringBranches:nil];
  std::weak_ptr<UIManager> current = fixture.uiManager;
  auto observer = [[BottomSheetPortalMountingObserver alloc]
      initWithMountingManager:fixture.mountingManager
      uiManagerProvider:^{ return current.lock(); }
      beforeDeletingNativeSubtrees:^(BottomSheetNativeViewDeletion *deletion) {
        [ledger retireHiddenBranchesForDeletion:deletion];
      }];
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  auto props = fixture.descriptor.cloneProps(PropsParserContext(1, *fixture.context),
      background->getProps(), RawProps(folly::dynamic::object("collapsable", true)));
  auto flattenedBackground = background->clone({.props = props});
  auto nextArea = area->clone({.children =
      std::make_shared<const Children>(Children{flattenedBackground, host})});
  auto nextRoot = root->ShadowNode::clone({.children =
      std::make_shared<const Children>(Children{nextArea})});
  auto mutations = calculateShadowViewMutations(*root, *nextRoot);
  MountingTransaction next(1, 2, std::move(mutations), {});

  [observer willMount:next];

  XCTAssertFalse(deletedParent.accessibilityElementsHidden);
  XCTAssertTrue(survivingControl.accessibilityElementsHidden);
  fixture.mount({nextArea});
  XCTAssertNil(fixture.view(12));
  XCTAssertEqual(fixture.view(16), survivingControl);
  XCTAssertTrue(survivingControl.accessibilityElementsHidden);
  [ledger reconcileHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertFalse(survivingControl.accessibilityElementsHidden);
}

@end
