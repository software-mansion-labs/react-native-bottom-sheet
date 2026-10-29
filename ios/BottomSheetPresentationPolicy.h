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

NS_ASSUME_NONNULL_END
