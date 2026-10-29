#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import "BottomSheetPresentationPolicy.h"

@class BottomSheetPortalMountingObserver;

NS_ASSUME_NONNULL_BEGIN

// Resolve only UIView and standard virtual accessibility-container paths.
FOUNDATION_EXPORT UIView *_Nullable BottomSheetAccessibilityContainerView(id _Nullable origin);

// Ephemeral native-authority facts. No state is registered with this value.
@interface BottomSheetPortalPresentation : NSObject
+ (nullable instancetype)presentationForIdentity:(BottomSheetPresentationIdentity *)identity
                                         anchor:(UIView *)anchor
                                         active:(BOOL)active
                                       eligible:(BOOL)eligible;
- (instancetype)initWithIdentity:(BottomSheetPresentationIdentity *)identity
                           owner:(UIView *)owner
                        observer:(BottomSheetPortalMountingObserver *)observer
                          active:(BOOL)active
                        eligible:(BOOL)eligible NS_DESIGNATED_INITIALIZER;
- (instancetype)init NS_UNAVAILABLE;
@property (nonatomic, strong, readonly) BottomSheetPresentationIdentity *identity;
@property (nonatomic, strong, readonly) UIView *owner;
@property (nonatomic, strong, readonly) BottomSheetPortalMountingObserver *observer;
@property (nonatomic, readonly, getter=isActive) BOOL active;
@property (nonatomic, readonly, getter=isEligible) BOOL eligible;
@end

@interface BottomSheetPortalIsolation : NSObject
- (instancetype)init NS_UNAVAILABLE;
@property (nonatomic, copy, readonly) NSSet<UIView *> *hiddenBranches;
@property (nonatomic, copy, readonly) NSSet<BottomSheetPresentationIdentity *> *topPresentationIdentities;
// Retained portal identities with their logical (surface, context) scope.
@property (nonatomic, copy, readonly) NSDictionary<BottomSheetPresentationIdentity *, NSArray<NSNumber *> *> *retainedPresentationContexts;

// Read-only; nil rejects a non-current source, never expands to root/window.
+ (nullable instancetype)projectionFromPresentations:
    (NSArray<BottomSheetPortalPresentation *> *)presentations
                                      orderProvider:
    (BottomSheetPresentationOrderProvider)orderProvider;

// Read-only action-time selection. The caller supplies current origin facts;
// unavailable sources/origins pass through without expanding to another scope.
// A window-modal owner is the enclosing context, not the inner portal's Top.
// Inside it, fallback requires a current query with no active local context.
// Outside, absent or unsupported origins retain window-modal input ownership.
+ (BottomSheetPresentationEscapeRoute)escapeRouteForCallerIdentity:
    (BottomSheetPresentationIdentity *)callerIdentity
                                                    actionOrigin:(nullable id)origin
                                                   presentations:
    (nullable NSArray<BottomSheetPortalPresentation *> *)presentations
                                                  windowBoundary:(nullable UIView *)windowBoundary
                                                  windowIdentity:(nullable BottomSheetPresentationIdentity *)windowIdentity
                                                   orderProvider:
    (BottomSheetPresentationOrderProvider)orderProvider;
@end

NS_ASSUME_NONNULL_END
