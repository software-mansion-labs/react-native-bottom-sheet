#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@class BottomSheetPresentationIdentity;

typedef NS_ENUM(NSInteger, BottomSheetPresentationOrder) {
  BottomSheetPresentationOrderUnknown,
  BottomSheetPresentationOrderBelow,
  BottomSheetPresentationOrderAbove,
};

typedef NS_ENUM(NSInteger, BottomSheetPresentationEscapeRoute) {
  BottomSheetPresentationEscapeRoutePassThrough,
  BottomSheetPresentationEscapeRouteAttemptLocal,
  BottomSheetPresentationEscapeRouteConsume,
};

typedef BottomSheetPresentationOrder (^BottomSheetPresentationOrderProvider)(
    BottomSheetPresentationIdentity *first,
    BottomSheetPresentationIdentity *second);

@interface BottomSheetPresentationCandidate : NSObject

- (instancetype)initWithIdentity:(BottomSheetPresentationIdentity *)identity
                           active:(BOOL)active
                         eligible:(BOOL)eligible NS_DESIGNATED_INITIALIZER;
- (instancetype)init NS_UNAVAILABLE;

@property (nonatomic, strong, readonly) BottomSheetPresentationIdentity *identity;
@property (nonatomic, readonly, getter=isActive) BOOL active;
@property (nonatomic, readonly, getter=isEligible) BOOL eligible;

@end

/** Neutral retained presentations; unresolved Top does not create an iOS Escape owner. */
@interface BottomSheetPresentationOwnershipDecision : NSObject

- (instancetype)init NS_UNAVAILABLE;

@property (nonatomic, strong, readonly, nullable) BottomSheetPresentationIdentity *topPresentationIdentity;
@property (nonatomic, copy, readonly) NSArray<BottomSheetPresentationIdentity *> *retainedPresentationIdentities;

@end

@interface BottomSheetPresentationResolver : NSObject

/**
 * Uses current native membership/order facts of one authority-established group.
 * Candidate eligibility validates native participation, not dismissal availability.
 * Unknown Top retains all valid Active identities; native isolation applies them separately.
 * This policy does not discover a local boundary or change existing Escape execution.
 */
+ (BottomSheetPresentationOwnershipDecision *)ownershipFromCandidates:
                                                 (NSArray<BottomSheetPresentationCandidate *> *)candidates
                                                      orderProvider:
                                                          (BottomSheetPresentationOrderProvider)orderProvider;

+ (nullable BottomSheetPresentationIdentity *)topPresentationIdentityFromCandidates:
                                                   (NSArray<BottomSheetPresentationCandidate *> *)candidates
                                                                    orderProvider:
                                                                        (BottomSheetPresentationOrderProvider)orderProvider;

@end

@interface BottomSheetPresentationEscapeResolver : NSObject

+ (BottomSheetPresentationEscapeRoute)routeForCallerIdentity:
                                          (BottomSheetPresentationIdentity *)callerIdentity
                                               topPresentationIdentity:
                                                   (nullable BottomSheetPresentationIdentity *)topPresentationIdentity;

@end

@interface BottomSheetPresentationIdentity : NSObject <NSCopying>
@end

@interface BottomSheetPresentationContextIdentity : NSObject <NSCopying>
@end

/** Validated Active state supplied by the shared presentation authority. */
@interface BottomSheetInputContextCandidate : NSObject

- (instancetype)initWithIdentity:(BottomSheetPresentationContextIdentity *)identity
                           active:(BOOL)active NS_DESIGNATED_INITIALIZER;
- (instancetype)init NS_UNAVAILABLE;

@property (nonatomic, strong, readonly) BottomSheetPresentationContextIdentity *identity;
@property (nonatomic, readonly, getter=isActive) BOOL active;

@end

typedef NS_ENUM(NSInteger, BottomSheetAccessibilityActionOriginState) {
  BottomSheetAccessibilityActionOriginStateInside,
  BottomSheetAccessibilityActionOriginStateKnownOutside,
  BottomSheetAccessibilityActionOriginStateAbsent,
  BottomSheetAccessibilityActionOriginStateUnmappable,
};

/** Current action-origin facts; an unresolved origin is distinct from absence. */
@interface BottomSheetAccessibilityActionOrigin : NSObject

- (instancetype)init NS_UNAVAILABLE;

/** Containing contexts are validated in the input window, innermost first. */
+ (instancetype)insideContexts:(NSArray<BottomSheetInputContextCandidate *> *)contexts;
+ (instancetype)knownOutside;
+ (instancetype)absent;
+ (instancetype)unmappable;

@property (nonatomic, readonly) BottomSheetAccessibilityActionOriginState state;
@property (nonatomic, copy, readonly) NSArray<BottomSheetInputContextCandidate *> *containingContexts;

@end

typedef NS_ENUM(NSInteger, BottomSheetActiveInputContextKind) {
  BottomSheetActiveInputContextKindPortal,
  BottomSheetActiveInputContextKindWindowModal,
  BottomSheetActiveInputContextKindHost,
};

/** A context decision, before local Top, dismissal eligibility, or action execution. */
@interface BottomSheetActiveInputContext : NSObject

- (instancetype)init NS_UNAVAILABLE;

@property (nonatomic, readonly) BottomSheetActiveInputContextKind kind;
@property (nonatomic, strong, readonly, nullable) BottomSheetPresentationContextIdentity *portalContextIdentity;

@end

@interface BottomSheetAccessibilityInputContextResolver : NSObject

/**
 * Selects an existing iOS accessibility action's context from current validated origin facts.
 * An absent or unmappable origin yields no context decision or execution/fallback policy.
 * Native adaptation supplies the origin and Active foreground nativeOverlay flag separately.
 * This policy does not query focus, discover boundaries, or add keyboard/physical Escape handling.
 */
+ (nullable BottomSheetActiveInputContext *)contextForActionOrigin:(BottomSheetAccessibilityActionOrigin *)origin
                  hasForegroundWindowModalPresentation:(BOOL)hasForegroundWindowModalPresentation;

@end

NS_ASSUME_NONNULL_END
