#import <Foundation/Foundation.h>
#import <XCTest/XCTest.h>

#import "../../../ios/BottomSheetPresentationPolicy.h"

@interface BottomSheetPresentationPolicyTests : XCTestCase

@end

@implementation BottomSheetPresentationPolicyTests

- (void)testAccessibilityActionInsideBSelectsItsPortalContext
{
  BottomSheetPresentationContextIdentity *contextB = [BottomSheetPresentationContextIdentity new];
  BottomSheetAccessibilityActionOrigin *origin = [BottomSheetAccessibilityActionOrigin insideContexts:@[
    [[BottomSheetInputContextCandidate alloc] initWithIdentity:contextB active:YES],
  ]];

  BottomSheetActiveInputContext *context =
      [BottomSheetAccessibilityInputContextResolver contextForActionOrigin:origin
                                    hasForegroundWindowModalPresentation:NO];

  XCTAssertEqual(context.kind, BottomSheetActiveInputContextKindPortal);
  XCTAssertEqualObjects(context.portalContextIdentity, contextB);
}

- (void)testInactiveInnerActionContextUsesItsActiveEnclosingContext
{
  BottomSheetPresentationContextIdentity *contextA = [BottomSheetPresentationContextIdentity new];
  BottomSheetPresentationContextIdentity *contextB = [BottomSheetPresentationContextIdentity new];
  BottomSheetAccessibilityActionOrigin *origin = [BottomSheetAccessibilityActionOrigin insideContexts:@[
    [[BottomSheetInputContextCandidate alloc] initWithIdentity:contextB active:NO],
    [[BottomSheetInputContextCandidate alloc] initWithIdentity:contextA active:YES],
  ]];

  BottomSheetActiveInputContext *context =
      [BottomSheetAccessibilityInputContextResolver contextForActionOrigin:origin
                                    hasForegroundWindowModalPresentation:NO];

  XCTAssertEqual(context.kind, BottomSheetActiveInputContextKindPortal);
  XCTAssertEqualObjects(context.portalContextIdentity, contextA);
}

- (void)testKnownOutsideAccessibilityActionSelectsHost
{
  BottomSheetActiveInputContext *context =
      [BottomSheetAccessibilityInputContextResolver
          contextForActionOrigin:[BottomSheetAccessibilityActionOrigin knownOutside]
          hasForegroundWindowModalPresentation:NO];

  XCTAssertEqual(context.kind, BottomSheetActiveInputContextKindHost);
  XCTAssertNil(context.portalContextIdentity);
}

- (void)testActionInsideContextsWithoutActivePresentationSelectsHost
{
  BottomSheetPresentationContextIdentity *contextA = [BottomSheetPresentationContextIdentity new];
  BottomSheetAccessibilityActionOrigin *origin = [BottomSheetAccessibilityActionOrigin insideContexts:@[
    [[BottomSheetInputContextCandidate alloc] initWithIdentity:contextA active:NO],
  ]];

  BottomSheetActiveInputContext *context =
      [BottomSheetAccessibilityInputContextResolver contextForActionOrigin:origin
                                    hasForegroundWindowModalPresentation:NO];

  XCTAssertEqual(context.kind, BottomSheetActiveInputContextKindHost);
  XCTAssertNil(context.portalContextIdentity);
}

- (void)testForegroundWindowModalOverridesKnownOutsideActionOrigin
{
  BottomSheetActiveInputContext *context =
      [BottomSheetAccessibilityInputContextResolver
          contextForActionOrigin:[BottomSheetAccessibilityActionOrigin knownOutside]
          hasForegroundWindowModalPresentation:YES];

  XCTAssertEqual(context.kind, BottomSheetActiveInputContextKindWindowModal);
  XCTAssertNil(context.portalContextIdentity);
}

- (void)testActiveInnerContextWinsOverItsActiveEnclosingContext
{
  BottomSheetPresentationContextIdentity *contextA = [BottomSheetPresentationContextIdentity new];
  BottomSheetPresentationContextIdentity *contextB = [BottomSheetPresentationContextIdentity new];
  BottomSheetAccessibilityActionOrigin *origin = [BottomSheetAccessibilityActionOrigin insideContexts:@[
    [[BottomSheetInputContextCandidate alloc] initWithIdentity:contextB active:YES],
    [[BottomSheetInputContextCandidate alloc] initWithIdentity:contextA active:YES],
  ]];

  BottomSheetActiveInputContext *context =
      [BottomSheetAccessibilityInputContextResolver contextForActionOrigin:origin
                                    hasForegroundWindowModalPresentation:NO];

  XCTAssertEqual(context.kind, BottomSheetActiveInputContextKindPortal);
  XCTAssertEqualObjects(context.portalContextIdentity, contextB);
}

- (void)testUnmappableActionOriginDoesNotInventAnInputContext
{
  BottomSheetActiveInputContext *context =
      [BottomSheetAccessibilityInputContextResolver
          contextForActionOrigin:[BottomSheetAccessibilityActionOrigin unmappable]
          hasForegroundWindowModalPresentation:NO];

  XCTAssertNil(context);
}

- (void)testAbsentActionOriginDoesNotInventAnInputContext
{
  BottomSheetActiveInputContext *context =
      [BottomSheetAccessibilityInputContextResolver
          contextForActionOrigin:[BottomSheetAccessibilityActionOrigin absent]
          hasForegroundWindowModalPresentation:NO];

  XCTAssertNil(context);
}

- (void)testForegroundWindowModalOverridesInsidePortalActionOrigin
{
  BottomSheetPresentationContextIdentity *contextB = [BottomSheetPresentationContextIdentity new];
  BottomSheetAccessibilityActionOrigin *origin = [BottomSheetAccessibilityActionOrigin insideContexts:@[
    [[BottomSheetInputContextCandidate alloc] initWithIdentity:contextB active:YES],
  ]];

  BottomSheetActiveInputContext *context =
      [BottomSheetAccessibilityInputContextResolver contextForActionOrigin:origin
                                    hasForegroundWindowModalPresentation:YES];

  XCTAssertEqual(context.kind, BottomSheetActiveInputContextKindWindowModal);
  XCTAssertNil(context.portalContextIdentity);
}

- (void)testPureResolverFiltersInactiveCandidate
{
  BottomSheetPresentationIdentity *lowerIdentity = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *upperIdentity = [BottomSheetPresentationIdentity new];
  NSArray *candidates = @[
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:lowerIdentity active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:upperIdentity active:NO eligible:YES],
  ];

  BottomSheetPresentationIdentity *topIdentity =
      [BottomSheetPresentationResolver topPresentationIdentityFromCandidates:candidates
                                                               orderProvider:^BottomSheetPresentationOrder(
                                                                   BottomSheetPresentationIdentity *first,
                                                                   BottomSheetPresentationIdentity *second) {
    return first == upperIdentity ? BottomSheetPresentationOrderAbove
                                  : BottomSheetPresentationOrderBelow;
  }];

  XCTAssertEqualObjects(topIdentity, lowerIdentity);
}

- (void)testPureResolverReturnsNoTopForUnknownRelationship
{
  BottomSheetPresentationIdentity *firstIdentity = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *secondIdentity = [BottomSheetPresentationIdentity new];
  NSArray *candidates = @[
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:firstIdentity active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:secondIdentity active:YES eligible:YES],
  ];

  BottomSheetPresentationIdentity *topIdentity =
      [BottomSheetPresentationResolver topPresentationIdentityFromCandidates:candidates
                                                               orderProvider:^BottomSheetPresentationOrder(
                                                                   BottomSheetPresentationIdentity *first,
                                                                   BottomSheetPresentationIdentity *second) {
    return BottomSheetPresentationOrderUnknown;
  }];

  XCTAssertNil(topIdentity);
}

- (void)testEscapeResolverAttemptsForTopAndConsumesLower
{
  BottomSheetPresentationIdentity *lowerIdentity = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *topIdentity = [BottomSheetPresentationIdentity new];

  XCTAssertEqual(
      [BottomSheetPresentationEscapeResolver routeForCallerIdentity:topIdentity
                                             topPresentationIdentity:topIdentity],
      BottomSheetPresentationEscapeRouteAttemptLocal);
  XCTAssertEqual(
      [BottomSheetPresentationEscapeResolver routeForCallerIdentity:lowerIdentity
                                             topPresentationIdentity:topIdentity],
      BottomSheetPresentationEscapeRouteConsume);
}

- (void)testEscapeResolverPassesThroughWithoutTop
{
  BottomSheetPresentationIdentity *callerIdentity = [BottomSheetPresentationIdentity new];

  XCTAssertEqual(
      [BottomSheetPresentationEscapeResolver routeForCallerIdentity:callerIdentity
                                             topPresentationIdentity:nil],
      BottomSheetPresentationEscapeRoutePassThrough);
}

- (void)testOwnershipDecisionRetainsUniqueVisualTop
{
  BottomSheetPresentationIdentity *lower = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *upper = [BottomSheetPresentationIdentity new];
  NSArray *candidates = @[
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:upper active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:lower active:YES eligible:YES],
  ];
  BottomSheetPresentationOwnershipDecision *decision =
      [BottomSheetPresentationResolver ownershipFromCandidates:candidates
                                                orderProvider:^BottomSheetPresentationOrder(
                                                    BottomSheetPresentationIdentity *first,
                                                    BottomSheetPresentationIdentity *second) {
    return first == upper ? BottomSheetPresentationOrderAbove : BottomSheetPresentationOrderBelow;
  }];

  XCTAssertEqualObjects(decision.topPresentationIdentity, upper);
  XCTAssertEqualObjects(decision.retainedPresentationIdentities, (@[upper]));
}

- (void)testUnknownTopRetainsEveryValidActiveIdentityAndPassesEscapeThrough
{
  BottomSheetPresentationIdentity *first = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *second = [BottomSheetPresentationIdentity new];
  NSArray *candidates = @[
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:first active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:second active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc]
        initWithIdentity:[BottomSheetPresentationIdentity new] active:NO eligible:YES],
    [[BottomSheetPresentationCandidate alloc]
        initWithIdentity:[BottomSheetPresentationIdentity new] active:YES eligible:NO],
  ];
  BottomSheetPresentationOwnershipDecision *decision =
      [BottomSheetPresentationResolver ownershipFromCandidates:candidates
                                                orderProvider:^BottomSheetPresentationOrder(
                                                    BottomSheetPresentationIdentity *a,
                                                    BottomSheetPresentationIdentity *b) {
    return BottomSheetPresentationOrderUnknown;
  }];

  XCTAssertNil(decision.topPresentationIdentity);
  XCTAssertEqualObjects(decision.retainedPresentationIdentities, (@[first, second]));
  XCTAssertEqual(
      [BottomSheetPresentationEscapeResolver routeForCallerIdentity:second
                                          topPresentationIdentity:decision.topPresentationIdentity],
      BottomSheetPresentationEscapeRoutePassThrough);
}

- (void)testNoValidActiveCandidatesLeaveNoTopOrRetainedPresentation
{
  NSArray *candidates = @[
    [[BottomSheetPresentationCandidate alloc]
        initWithIdentity:[BottomSheetPresentationIdentity new] active:NO eligible:YES],
    [[BottomSheetPresentationCandidate alloc]
        initWithIdentity:[BottomSheetPresentationIdentity new] active:YES eligible:NO],
  ];
  BottomSheetPresentationOwnershipDecision *decision =
      [BottomSheetPresentationResolver ownershipFromCandidates:candidates
                                                orderProvider:^BottomSheetPresentationOrder(
                                                    BottomSheetPresentationIdentity *first,
                                                    BottomSheetPresentationIdentity *second) {
    return BottomSheetPresentationOrderUnknown;
  }];

  XCTAssertNil(decision.topPresentationIdentity);
  XCTAssertEqualObjects(decision.retainedPresentationIdentities, @[]);
}

- (void)testOneValidActivePresentationHasTopAndRetainedIdentityWithoutPairwiseEvidence
{
  BottomSheetPresentationIdentity *identity = [BottomSheetPresentationIdentity new];
  NSArray *candidates = @[
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:identity active:YES eligible:YES],
  ];
  BottomSheetPresentationOwnershipDecision *decision =
      [BottomSheetPresentationResolver ownershipFromCandidates:candidates
                                                orderProvider:^BottomSheetPresentationOrder(
                                                    BottomSheetPresentationIdentity *first,
                                                    BottomSheetPresentationIdentity *second) {
    return BottomSheetPresentationOrderUnknown;
  }];

  XCTAssertEqualObjects(decision.topPresentationIdentity, identity);
  XCTAssertEqualObjects(decision.retainedPresentationIdentities, (@[identity]));
}

- (void)testUnknownOrderAmongLowerPresentationsDoesNotPreventProvedTop
{
  BottomSheetPresentationIdentity *first = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *top = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *third = [BottomSheetPresentationIdentity new];
  NSArray *candidates = @[
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:first active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:top active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:third active:YES eligible:YES],
  ];
  BottomSheetPresentationOwnershipDecision *decision =
      [BottomSheetPresentationResolver ownershipFromCandidates:candidates
                                                orderProvider:^BottomSheetPresentationOrder(
                                                    BottomSheetPresentationIdentity *a,
                                                    BottomSheetPresentationIdentity *b) {
    if (a == top) return BottomSheetPresentationOrderAbove;
    if (b == top) return BottomSheetPresentationOrderBelow;
    return BottomSheetPresentationOrderUnknown;
  }];

  XCTAssertEqualObjects(decision.topPresentationIdentity, top);
  XCTAssertEqualObjects(decision.retainedPresentationIdentities, (@[top]));
}

- (void)testUnknownTopRetainsAlsoTheActivePresentationBelowBothContenders
{
  BottomSheetPresentationIdentity *lower = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *first = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *second = [BottomSheetPresentationIdentity new];
  NSArray *candidates = @[
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:lower active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:first active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:second active:YES eligible:YES],
  ];
  BottomSheetPresentationOwnershipDecision *decision =
      [BottomSheetPresentationResolver ownershipFromCandidates:candidates
                                                orderProvider:^BottomSheetPresentationOrder(
                                                    BottomSheetPresentationIdentity *a,
                                                    BottomSheetPresentationIdentity *b) {
    if (a == lower) return BottomSheetPresentationOrderBelow;
    if (b == lower) return BottomSheetPresentationOrderAbove;
    return BottomSheetPresentationOrderUnknown;
  }];

  XCTAssertNil(decision.topPresentationIdentity);
  XCTAssertEqualObjects(decision.retainedPresentationIdentities, (@[lower, first, second]));
}

- (void)testConflictingOrderEvidenceDoesNotManufactureUniqueTop
{
  BottomSheetPresentationIdentity *first = [BottomSheetPresentationIdentity new];
  BottomSheetPresentationIdentity *second = [BottomSheetPresentationIdentity new];
  NSArray *candidates = @[
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:first active:YES eligible:YES],
    [[BottomSheetPresentationCandidate alloc] initWithIdentity:second active:YES eligible:YES],
  ];
  BottomSheetPresentationOwnershipDecision *decision =
      [BottomSheetPresentationResolver ownershipFromCandidates:candidates
                                                orderProvider:^BottomSheetPresentationOrder(
                                                    BottomSheetPresentationIdentity *a,
                                                    BottomSheetPresentationIdentity *b) {
    return BottomSheetPresentationOrderAbove;
  }];

  XCTAssertNil(decision.topPresentationIdentity);
  XCTAssertEqualObjects(decision.retainedPresentationIdentities, (@[first, second]));
}

@end
