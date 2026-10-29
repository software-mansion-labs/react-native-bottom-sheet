#import "BottomSheetPresentationPolicy.h"

@implementation BottomSheetPresentationIdentity

- (id)copyWithZone:(NSZone *)zone
{
  return self;
}

@end

@implementation BottomSheetPresentationCandidate

- (instancetype)initWithIdentity:(BottomSheetPresentationIdentity *)identity
                           active:(BOOL)active
                         eligible:(BOOL)eligible
{
  if (self = [super init]) {
    _identity = identity;
    _active = active;
    _eligible = eligible;
  }
  return self;
}

@end

@interface BottomSheetPresentationOwnershipDecision ()

- (instancetype)initWithTopPresentationIdentity:(nullable BottomSheetPresentationIdentity *)topIdentity
                  retainedPresentationIdentities:(NSArray<BottomSheetPresentationIdentity *> *)retainedIdentities;

@end

@implementation BottomSheetPresentationOwnershipDecision

- (instancetype)initWithTopPresentationIdentity:(nullable BottomSheetPresentationIdentity *)topIdentity
                  retainedPresentationIdentities:(NSArray<BottomSheetPresentationIdentity *> *)retainedIdentities
{
  if (self = [super init]) {
    _topPresentationIdentity = topIdentity;
    _retainedPresentationIdentities = [retainedIdentities copy];
  }
  return self;
}

@end

@implementation BottomSheetPresentationResolver

+ (BottomSheetPresentationOwnershipDecision *)ownershipFromCandidates:
                                                 (NSArray<BottomSheetPresentationCandidate *> *)candidates
                                                      orderProvider:
                                                          (BottomSheetPresentationOrderProvider)orderProvider
{
  NSMutableArray<BottomSheetPresentationCandidate *> *eligibleCandidates = [NSMutableArray new];
  NSMutableArray<BottomSheetPresentationIdentity *> *activeIdentities = [NSMutableArray new];
  for (BottomSheetPresentationCandidate *candidate in candidates) {
    if (candidate.isActive && candidate.isEligible) {
      [eligibleCandidates addObject:candidate];
      [activeIdentities addObject:candidate.identity];
    }
  }

  BottomSheetPresentationIdentity *topIdentity = nil;
  for (BottomSheetPresentationCandidate *candidate in eligibleCandidates) {
    BOOL isAboveEveryOtherCandidate = YES;
    for (BottomSheetPresentationCandidate *other in eligibleCandidates) {
      if (candidate == other) {
        continue;
      }
      if (orderProvider(candidate.identity, other.identity) != BottomSheetPresentationOrderAbove) {
        isAboveEveryOtherCandidate = NO;
        break;
      }
    }

    if (isAboveEveryOtherCandidate) {
      if (topIdentity != nil) {
        topIdentity = nil;
        break;
      }
      topIdentity = candidate.identity;
    }
  }

  // Retained identities neither prove Top nor create an iOS registration-order Escape owner.
  return [[BottomSheetPresentationOwnershipDecision alloc]
      initWithTopPresentationIdentity:topIdentity
      retainedPresentationIdentities:topIdentity == nil ? activeIdentities : @[topIdentity]];
}

+ (nullable BottomSheetPresentationIdentity *)topPresentationIdentityFromCandidates:
                                                   (NSArray<BottomSheetPresentationCandidate *> *)candidates
                                                                    orderProvider:
                                                                        (BottomSheetPresentationOrderProvider)orderProvider
{
  return [self ownershipFromCandidates:candidates orderProvider:orderProvider].topPresentationIdentity;
}

@end

@implementation BottomSheetPresentationEscapeResolver

+ (BottomSheetPresentationEscapeRoute)routeForCallerIdentity:
                                          (BottomSheetPresentationIdentity *)callerIdentity
                                               topPresentationIdentity:
                                                   (nullable BottomSheetPresentationIdentity *)topPresentationIdentity
{
  if (topPresentationIdentity == nil) {
    return BottomSheetPresentationEscapeRoutePassThrough;
  }
  return callerIdentity == topPresentationIdentity ? BottomSheetPresentationEscapeRouteAttemptLocal
                                                   : BottomSheetPresentationEscapeRouteConsume;
}

@end
