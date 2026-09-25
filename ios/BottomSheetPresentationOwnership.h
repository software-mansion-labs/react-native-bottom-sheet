#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, BottomSheetPresentationMode) {
  BottomSheetPresentationModePortal,
  BottomSheetPresentationModeNativeOverlay,
};

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

@interface BottomSheetPresentationResolver : NSObject

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

@interface BottomSheetPresentationUIKitOrderResolver : NSObject

+ (BottomSheetPresentationOrder)orderOfAnchor:(UIView *)first
                                     relativeTo:(UIView *)second
                                       inWindow:(UIWindow *)window;
+ (BOOL)isAnchor:(UIView *)anchor attachedToWindow:(UIWindow *)window;

@end

@interface BottomSheetPresentationController : NSObject

- (instancetype)initWithAnchor:(UIView *)anchor NS_DESIGNATED_INITIALIZER;
- (instancetype)init NS_UNAVAILABLE;

@property (nonatomic, strong, readonly) BottomSheetPresentationIdentity *identity;
@property (nonatomic, readonly, getter=isTopPresentation) BOOL topPresentation;

- (void)updateModal:(BOOL)modal
             active:(BOOL)active
               mode:(BottomSheetPresentationMode)mode;
- (void)beginHierarchyMutation;
- (void)endHierarchyMutation;
- (void)reconcilePresentationOrder;
- (BottomSheetPresentationEscapeRoute)routeForVoiceOverEscape;
- (void)invalidate;

@end

@interface BottomSheetPresentationIdentity : NSObject <NSCopying>
@end

@interface BottomSheetPresentationCoordinator : NSObject

+ (nullable BottomSheetPresentationIdentity *)topPresentationIdentityInWindow:(UIWindow *)window;

@end

NS_ASSUME_NONNULL_END
