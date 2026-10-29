#import <XCTest/XCTest.h>
#import "Support/BottomSheetPortalRuntimeFixture.h"
#import "Support/BottomSheetPortalProjectionTestHelper.h"
#import "../../../ios/BottomSheetAccessibilityMaskLedger.h"
#include <react/renderer/mounting/Differentiator.h>

using namespace facebook::react;
using namespace facebook::react::bottomsheet;
using namespace facebook::react::bottomsheet::tests;

@interface BottomSheetPortalMountingObserverTests : XCTestCase
@end
@implementation BottomSheetPortalMountingObserverTests

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
  XCTAssertEqualObjects(hiddenBranches(observer, 1, paths),
                       ([NSSet setWithObject:fixture.view(12)]));
}

- (void)testCompletedBatchValidatesEachAdditionalOwnerAgainstItsRootAndRegistry
{
  RuntimeFixture fixture;
  auto root = fixture.mount({fixture.node(14), fixture.node(15)});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  auto registry = fixture.mountingManager.componentViewRegistry;
  auto handle = fixture.descriptor.getComponentHandle();
  auto foreign = [registry dequeueComponentViewWithComponentHandle:handle tag:24];
  UIView *sameTag = [UIView new];
  sameTag.tag = 15;

  [observer didMount:first owner:fixture.view(15)];
  [observer didMount:first owner:foreign.view];
  [observer didMount:first owner:sameTag];

  XCTAssertTrue([observer currentRootForOwner:fixture.view(14)] == root);
  XCTAssertTrue([observer currentRootForOwner:fixture.view(15)] == root);
  XCTAssertFalse(bool([observer currentRootForOwner:foreign.view]));
  XCTAssertFalse(bool([observer currentRootForOwner:sameTag]));
  [registry enqueueComponentViewWithComponentHandle:handle tag:24 componentViewDescriptor:foreign];
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
  [observer didMount:first owner:fixture.view(16)];
  XCTAssertFalse(bool([observer currentRootForSurface:1]));
  [observer didMount:next owner:fixture.view(14)];
  std::vector<PortalRetainedPaths> paths{{&host->getFamily(), {&sheet->getFamily()}}};

  XCTAssertTrue([observer currentRootForSurface:1] == nextRoot);
  XCTAssertEqualObjects(hiddenBranches(observer, 1, paths),
                       ([NSSet setWithObjects:fixture.view(12), fixture.view(16), nil]));
}

- (void)testRepeatedOldCallbacksCannotRepublishAChangedRootAfterAnotherOwnerWithdraws
{
  RuntimeFixture fixture;
  auto owner = fixture.node(14);
  auto firstRoot = fixture.mount({owner});
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(14)];
  auto nextRoot = fixture.mount({owner, fixture.node(16)});

  // A late callback must validate membership in the changed applied root. Its
  // new owner keeps the old publication alive until the actual next completion.
  [observer didMount:first owner:fixture.view(16)];
  [observer withdrawOwner:fixture.view(14)];
  [observer didMount:first owner:fixture.view(16)];
  XCTAssertFalse(bool([observer currentRootForOwner:fixture.view(16)]));

  auto mutations = calculateShadowViewMutations(*firstRoot, *nextRoot);
  MountingTransaction next(1, 2, std::move(mutations), {});
  [observer didMount:next owner:fixture.view(16)];
  XCTAssertTrue([observer currentRootForOwner:fixture.view(16)] == nextRoot);
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
  XCTAssertEqualObjects(hiddenBranches(observer, 1, paths),
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
  XCTAssertNil(hiddenBranches(observer, 1, oldPaths));
  XCTAssertEqualObjects(hiddenBranches(observer, 1, paths),
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
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObjects:deletedView, otherContext, nil]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
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
  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
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
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObject:backgroundView]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
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
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObjects:backgroundView, nativeChild, nil]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
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
  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
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
  [ledger reconcilePortalHiddenBranches:[NSSet setWithObject:deletedParent]
         windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
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
  [ledger reconcilePortalHiddenBranches:[NSSet set] windowHiddenBranches:[NSSet set] beforeRestoringBranches:nil];
  XCTAssertFalse(survivingControl.accessibilityElementsHidden);
}

@end
