#import <XCTest/XCTest.h>
#import "Support/BottomSheetPortalRuntimeFixture.h"
#import "../../../ios/BottomSheetPortalIsolation.h"
#import "../../../ios/BottomSheetPresentationOwnership.h"
#import "../../../ios/BottomSheetPresentationModalIsolationAdapter.h"
#import "../../../ios/BottomSheetPortalNativeContainer.h"
#include <react/renderer/mounting/Differentiator.h>

using namespace facebook::react;
using namespace facebook::react::bottomsheet;
using namespace facebook::react::bottomsheet::tests;

// Structural overrides are rejected before the hierarchy dispatches a getter.
@interface UnsupportedPortalNativeContainer : UIView
@property (nonatomic, copy) void (^onReadSubviews)(void);
@end
@implementation UnsupportedPortalNativeContainer
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

@interface BottomSheetPortalIsolationIntegrationTests : XCTestCase
@end
@implementation BottomSheetPortalIsolationIntegrationTests

- (void)testWindowContextFallbackRequiresAValidQueryOutsideActiveLocalContexts
{
  RuntimeFixture fixture;
  fixture.mount({fixture.node(2), fixture.node(21, {fixture.node(22),
      fixture.node(23, {fixture.node(24), fixture.node(28)}, true)})});
  UIWindow *window = [[UIWindow alloc] initWithFrame:CGRectMake(0, 0, 800, 600)];
  UIView *overlay = [UIView new];
  UIView *outside = [UIView new];
  [window addSubview:outside];
  [window addSubview:overlay];
  [overlay addSubview:fixture.viewport];
  auto observer = fixture.observer();
  MountingTransaction first(1, 1, {}, {});
  [observer didMount:first owner:fixture.view(24)];
  [observer didMount:first owner:fixture.view(28)];
  auto overlayIdentity = [BottomSheetPresentationIdentity new];
  auto firstIdentity = [BottomSheetPresentationIdentity new];
  auto secondIdentity = [BottomSheetPresentationIdentity new];
  NSArray *presentations = @[
    [[BottomSheetPortalPresentation alloc] initWithIdentity:firstIdentity owner:fixture.view(24)
        observer:observer active:YES eligible:YES],
    [[BottomSheetPortalPresentation alloc] initWithIdentity:secondIdentity owner:fixture.view(28)
        observer:observer active:YES eligible:YES]];
  BottomSheetPresentationOrderProvider unknown = ^BottomSheetPresentationOrder(
      BottomSheetPresentationIdentity *a, BottomSheetPresentationIdentity *b) {
    return BottomSheetPresentationOrderUnknown;
  };
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:overlayIdentity
      actionOrigin:fixture.view(24) presentations:presentations windowBoundary:overlay
      windowIdentity:overlayIdentity orderProvider:unknown], BottomSheetPresentationEscapeRoutePassThrough,
      @"Unresolved local Top must not turn into dismissal of its enclosing overlay");
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:overlayIdentity
      actionOrigin:fixture.view(2) presentations:presentations windowBoundary:overlay
      windowIdentity:overlayIdentity orderProvider:unknown], BottomSheetPresentationEscapeRouteAttemptLocal,
      @"A valid query can establish that the action belongs to the enclosing context");
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:firstIdentity
      actionOrigin:fixture.view(2) presentations:presentations windowBoundary:overlay
      windowIdentity:overlayIdentity orderProvider:unknown], BottomSheetPresentationEscapeRouteConsume);

  [observer invalidate];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:overlayIdentity
      actionOrigin:fixture.view(24) presentations:presentations windowBoundary:overlay
      windowIdentity:overlayIdentity orderProvider:unknown], BottomSheetPresentationEscapeRoutePassThrough);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:overlayIdentity
      actionOrigin:fixture.view(2) presentations:presentations windowBoundary:overlay
      windowIdentity:overlayIdentity orderProvider:unknown], BottomSheetPresentationEscapeRoutePassThrough,
      @"Unavailable local facts do not prove that an origin is outside the local context");
  for (id origin in @[outside, NSNull.null, [NSObject new]]) {
    XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:overlayIdentity
        actionOrigin:origin == NSNull.null ? nil : origin presentations:nil windowBoundary:overlay
        windowIdentity:overlayIdentity orderProvider:unknown], BottomSheetPresentationEscapeRouteAttemptLocal,
        @"A foreground overlay still blocks underlying, absent and unsupported origins");
  }
  [fixture.viewport removeFromSuperview];
}

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
      windowBoundary:nil orderProvider:order ifCurrent:nil]);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(42).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(2).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(4).accessibilityElementsHidden);
  XCTAssertFalse(modalOwner.accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:modalIdentity
      actionOrigin:modalOwner presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:order],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:applicationIdentity
      actionOrigin:fixture.view(2) presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:order],
      BottomSheetPresentationEscapeRoutePassThrough);

  auto inactiveModal = [BottomSheetPortalPresentation presentationForIdentity:modalIdentity
      anchor:modalOwner active:NO eligible:YES];
  XCTAssertNotNil(inactiveModal);
  if (!inactiveModal) return;
  NSArray *releasedModal = @[application, inactiveModal];
  XCTAssertTrue([adapter reconcilePortalPresentations:releasedModal
      windowBoundary:nil orderProvider:order ifCurrent:nil]);
  XCTAssertFalse(fixture.view(12).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(42).accessibilityElementsHidden);

  XCTAssertTrue([adapter reconcilePortalPresentations:presentations
      windowBoundary:nil orderProvider:order ifCurrent:nil]);
  auto modalView = (RCTModalHostViewComponentView *)fixture.view(30);
  UIView<RCTComponentViewProtocol> *panel = (UIView<RCTComponentViewProtocol> *)fixture.view(11);
  [modalView unmountChildComponentView:panel index:1];
  XCTAssertFalse([adapter reconcilePortalPresentations:presentations
      windowBoundary:nil orderProvider:order ifCurrent:nil]);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(42).accessibilityElementsHidden);
  [modalView mountChildComponentView:panel index:1];
  XCTAssertTrue([adapter reconcilePortalPresentations:presentations
      windowBoundary:nil orderProvider:order ifCurrent:nil]);
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
  [adapter reconcilePortalPresentations:@[presentation] windowBoundary:nil orderProvider:order ifCurrent:nil];
  XCTAssertTrue(oldBackground.accessibilityElementsHidden);

  auto nextArea = area->clone({.children = std::make_shared<const Children>(
      Children{background, fixture.node(16), host})});
  auto nextRoot = firstRoot->ShadowNode::clone({.children =
      std::make_shared<const Children>(Children{outside, nextArea})});
  auto mutations = calculateShadowViewMutations(*firstRoot, *nextRoot);
  MountingTransaction next(1, 2, std::move(mutations), {});
  [observer willMount:next];
  XCTAssertFalse([adapter reconcilePortalPresentations:@[presentation]
      windowBoundary:nil orderProvider:order ifCurrent:nil]);
  XCTAssertTrue(oldBackground.accessibilityElementsHidden);

  fixture.mount({outside, nextArea});
  [observer didMount:next owner:owner];
  XCTAssertTrue([adapter reconcilePortalPresentations:@[presentation]
      windowBoundary:nil orderProvider:order ifCurrent:nil]);

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
  [adapter reconcilePortalPresentations:@[presentation] windowBoundary:nil orderProvider:order ifCurrent:nil];
  boundary.accessibilityViewIsModal = YES;
  [adapter unregisterBoundary:boundary];
  MountingTransaction inFlight(1, 2, {}, {});
  [observer willMount:inFlight];

  [adapter reconcilePortalPresentations:@[presentation] windowBoundary:nil orderProvider:order ifCurrent:nil];

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
  [adapter reconcilePortalPresentations:presentations windowBoundary:nil orderProvider:known ifCurrent:nil];
  XCTAssertTrue(fixture.view(14).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:fixture.view(14) presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:known],
      BottomSheetPresentationEscapeRouteConsume);

  [adapter reconcilePortalPresentations:presentations windowBoundary:nil orderProvider:unknown
      ifCurrent:^BOOL { return NO; }];
  XCTAssertTrue(fixture.view(14).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);

  [adapter reconcilePortalPresentations:presentations windowBoundary:nil orderProvider:unknown ifCurrent:nil];
  XCTAssertFalse(fixture.view(14).accessibilityElementsHidden);
  XCTAssertFalse(fixture.view(15).accessibilityElementsHidden);
  XCTAssertTrue(fixture.view(12).accessibilityElementsHidden);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityB
      actionOrigin:fixture.view(15) presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:unknown],
      BottomSheetPresentationEscapeRoutePassThrough);

  MountingTransaction inFlight(1, 2, {}, {});
  [observer willMount:inFlight];
  XCTAssertFalse([adapter reconcilePortalPresentations:presentations
      windowBoundary:nil orderProvider:known ifCurrent:nil]);
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
        windowBoundary:nil orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
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
      windowBoundary:nil orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
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
      actionOrigin:fixture.view(14) presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityB
      actionOrigin:fixture.view(24) presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  UIView *nativeChild = [UIView new];
  [fixture.view(14) addSubview:nativeChild];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:nativeChild presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  UIAccessibilityElement *outer = [[UIAccessibilityElement alloc] initWithAccessibilityContainer:nativeChild];
  UIAccessibilityElement *inner = [[UIAccessibilityElement alloc] initWithAccessibilityContainer:outer];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:inner presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  [fixture.view(24) addSubview:nativeChild];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityB
      actionOrigin:inner presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:inner presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRouteConsume);
  [nativeChild removeFromSuperview];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityB
      actionOrigin:inner presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRoutePassThrough);
  outer.accessibilityContainer = inner;
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:inner presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRoutePassThrough);
  outer.accessibilityContainer = nil;
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:fixture.view(2) presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRoutePassThrough);

  // Missing or unsupported UIKit origins cannot select another panel's Top.
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:nil presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRoutePassThrough);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:[NSObject new] presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRoutePassThrough);
  NSArray *inactivePresentations = @[
    [[BottomSheetPortalPresentation alloc] initWithIdentity:identityA owner:fixture.view(14)
        observer:observer active:NO eligible:YES],
    presentations[1]];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:fixture.view(14) presentations:inactivePresentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
      BottomSheetPresentationEscapeRoutePassThrough);

  [fixture.view(14) addSubview:nativeChild];
  reads = 0;
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:identityA
      actionOrigin:nativeChild presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder],
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
        actionOrigin:nativeChild presentations:presentations windowBoundary:nil windowIdentity:nil orderProvider:globalOrder];
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
      actionOrigin:inner.view(24) presentations:wholeOuter windowBoundary:nil windowIdentity:nil orderProvider:order],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:outerA
      actionOrigin:inner.view(24) presentations:wholeOuter windowBoundary:nil windowIdentity:nil orderProvider:order],
      BottomSheetPresentationEscapeRouteConsume);
  NSArray *inactiveInner = @[wholeOuter[0], wholeOuter[1],
      [[BottomSheetPortalPresentation alloc] initWithIdentity:innerTop owner:inner.view(24)
          observer:innerObserver active:NO eligible:YES]];
  XCTAssertEqual([BottomSheetPortalIsolation escapeRouteForCallerIdentity:outerA
      actionOrigin:inner.view(22) presentations:inactiveInner windowBoundary:nil windowIdentity:nil orderProvider:order],
      BottomSheetPresentationEscapeRouteAttemptLocal);

  UnsupportedPortalNativeContainer *backgroundAccessor = [UnsupportedPortalNativeContainer new];
  [outer.view(11) addSubview:backgroundAccessor];
  __block NSUInteger backgroundReads = 0;
  backgroundAccessor.onReadSubviews = ^{ backgroundReads++; };
  auto rejectedBackgroundAccessor = [BottomSheetPortalIsolation
      projectionFromPresentations:presentations orderProvider:order];
  XCTAssertNil(rejectedBackgroundAccessor);
  XCTAssertEqual(backgroundReads, 0);
  [backgroundAccessor removeFromSuperview];
  UnsupportedPortalNativeContainer *unsafeBridge = [UnsupportedPortalNativeContainer new];
  [bridge removeFromSuperview];
  [coveredOuter addSubview:unsafeBridge];
  [unsafeBridge addSubview:inner.viewport];
  __block NSUInteger readCount = 0;
  unsafeBridge.onReadSubviews = ^{ readCount++; };
  auto rejectedBridgeAccessor = [BottomSheetPortalIsolation
      projectionFromPresentations:presentations orderProvider:order];
  XCTAssertNil(rejectedBridgeAccessor);
  XCTAssertEqual(readCount, 0);
}

@end
