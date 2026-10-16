#import "BottomSheetPresentationOwnership.h"

#import "BottomSheetAccessibilityMaskLedger.h"
#import "BottomSheetPresentationModalIsolationAdapter.h"

#import <math.h>
#import <objc/runtime.h>

static void BottomSheetAssertMainThread(void)
{
  NSCAssert(NSThread.isMainThread, @"Presentation ownership must be accessed on the main thread");
}

@implementation BottomSheetPresentationUIKitOrderResolver

+ (nullable NSArray<UIView *> *)pathFromWindow:(UIWindow *)window toAnchor:(UIView *)anchor
{
  if (anchor == window || anchor.window != window) {
    return nil;
  }

  NSMutableArray<UIView *> *reversePath = [NSMutableArray arrayWithObject:anchor];
  UIView *current = anchor;
  while (current != window) {
    UIView *parent = current.superview;
    if (parent == nil || [parent.subviews indexOfObjectIdenticalTo:current] == NSNotFound) {
      return nil;
    }
    [reversePath addObject:parent];
    current = parent;
  }
  return [[reversePath reverseObjectEnumerator] allObjects];
}

+ (BOOL)isAnchor:(UIView *)anchor attachedToWindow:(UIWindow *)window
{
  BottomSheetAssertMainThread();
  return [self pathFromWindow:window toAnchor:anchor] != nil;
}

+ (BottomSheetPresentationOrder)orderOfAnchor:(UIView *)first
                                     relativeTo:(UIView *)second
                                       inWindow:(UIWindow *)window
{
  BottomSheetAssertMainThread();
  if (first == second) {
    return BottomSheetPresentationOrderUnknown;
  }

  NSArray<UIView *> *firstPath = [self pathFromWindow:window toAnchor:first];
  NSArray<UIView *> *secondPath = [self pathFromWindow:window toAnchor:second];
  if (firstPath == nil || secondPath == nil) {
    return BottomSheetPresentationOrderUnknown;
  }

  NSUInteger commonCount = 0;
  NSUInteger shortestCount = MIN(firstPath.count, secondPath.count);
  while (commonCount < shortestCount && firstPath[commonCount] == secondPath[commonCount]) {
    commonCount++;
  }

  if (commonCount == firstPath.count) {
    return BottomSheetPresentationOrderBelow;
  }
  if (commonCount == secondPath.count) {
    return BottomSheetPresentationOrderAbove;
  }
  if (commonCount == 0) {
    return BottomSheetPresentationOrderUnknown;
  }

  UIView *commonAncestor = firstPath[commonCount - 1];
  UIView *firstBranch = firstPath[commonCount];
  UIView *secondBranch = secondPath[commonCount];
  CGFloat firstZPosition = firstBranch.layer.zPosition;
  CGFloat secondZPosition = secondBranch.layer.zPosition;
  if (!isfinite(firstZPosition) || !isfinite(secondZPosition)) {
    return BottomSheetPresentationOrderUnknown;
  }
  if (firstZPosition > secondZPosition) {
    return BottomSheetPresentationOrderAbove;
  }
  if (firstZPosition < secondZPosition) {
    return BottomSheetPresentationOrderBelow;
  }

  NSUInteger firstIndex = [commonAncestor.subviews indexOfObjectIdenticalTo:firstBranch];
  NSUInteger secondIndex = [commonAncestor.subviews indexOfObjectIdenticalTo:secondBranch];
  if (firstIndex == NSNotFound || secondIndex == NSNotFound || firstIndex == secondIndex) {
    return BottomSheetPresentationOrderUnknown;
  }
  return firstIndex > secondIndex ? BottomSheetPresentationOrderAbove
                                  : BottomSheetPresentationOrderBelow;
}

@end

@class BottomSheetPresentationCoordinator;

@interface BottomSheetPresentationController ()
@property (nonatomic, strong, readwrite) BottomSheetPresentationIdentity *identity;
@property (nonatomic, readwrite, getter=isTopPresentation) BOOL topPresentation;
@property (nonatomic, weak) UIView *anchor;
@property (nonatomic, weak) BottomSheetPresentationCoordinator *coordinator;
@property (nonatomic) BottomSheetPresentationMode mode;
@property (nonatomic, getter=isModal) BOOL modal;
@property (nonatomic, getter=isActive) BOOL active;
@property (nonatomic, getter=isInvalidated) BOOL invalidated;
@property (nonatomic) NSInteger hierarchyMutationDepth;
@property (nonatomic, weak) BottomSheetPresentationCoordinator *mutationCoordinator;
- (void)reconcileRegistration;
@end

@interface BottomSheetPresentationCandidateRecord : NSObject
@property (nonatomic, strong) BottomSheetPresentationIdentity *identity;
@property (nonatomic, weak) BottomSheetPresentationController *controller;
@property (nonatomic, weak) UIView *anchor;
@property (nonatomic, getter=isActive) BOOL active;
@end

@implementation BottomSheetPresentationCandidateRecord
@end

@interface BottomSheetPresentationModalIsolationAdapter ()
@property (nonatomic, strong) NSHashTable<UIView *> *boundaries;
@property (nonatomic, strong) NSHashTable<UIView *> *pendingReleasedBoundaries;
@property (nonatomic, strong) BottomSheetAccessibilityMaskLedger *maskLedger;

@end

@implementation BottomSheetPresentationModalIsolationAdapter

- (instancetype)init
{
  if (self = [super init]) {
    _boundaries = [NSHashTable weakObjectsHashTable];
    _pendingReleasedBoundaries = [NSHashTable weakObjectsHashTable];
    _maskLedger = [BottomSheetAccessibilityMaskLedger new];
  }
  return self;
}

- (void)registerBoundary:(UIView *)boundary
{
  [self.pendingReleasedBoundaries removeObject:boundary];
  [self.boundaries addObject:boundary];
}

- (void)unregisterBoundary:(UIView *)boundary
{
  [self.boundaries removeObject:boundary];
  [self.pendingReleasedBoundaries addObject:boundary];
}

- (void)reconcileTopBoundary:(nullable UIView *)topBoundary
{
  NSMutableSet<UIView *> *hiddenBranches = [NSMutableSet new];
  UIWindow *window = topBoundary.window;
  NSArray<UIView *> *topPath = window == nil
      ? nil
      : [BottomSheetPresentationUIKitOrderResolver pathFromWindow:window toAnchor:topBoundary];
  for (NSUInteger index = 0; index + 1 < topPath.count; index++) {
    UIView *parent = topPath[index];
    UIView *pathChild = topPath[index + 1];
    for (UIView *sibling in parent.subviews) {
      if (sibling != pathChild) {
        [hiddenBranches addObject:sibling];
      }
    }
  }

  [self reconcileHiddenBranches:hiddenBranches modalBoundary:topBoundary];
}

- (void)reconcileHiddenBranches:(NSSet<UIView *> *)hiddenBranches
                modalBoundary:(nullable UIView *)topBoundary
{
  if (topBoundary != nil && !topBoundary.accessibilityViewIsModal) {
    topBoundary.accessibilityViewIsModal = YES;
  }

  // Establish the complete new isolation path before releasing either the old
  // modal boundary or any branch hidden for the previous Top. This mirrors the
  // boundary transfer order and prevents a synchronous Top swap from exposing
  // lower presentations or application content between writes.
  [self.maskLedger reconcileHiddenBranches:hiddenBranches
                  beforeRestoringBranches:^{
    NSArray<UIView *> *boundaries = self.boundaries.allObjects;
    for (UIView *boundary in boundaries) {
      if (boundary != topBoundary && boundary.accessibilityViewIsModal) {
        boundary.accessibilityViewIsModal = NO;
      }
    }

    [self clearReleasedBoundariesKeeping:topBoundary];
  }];
}

- (void)clearReleasedBoundariesKeeping:(nullable UIView *)topBoundary
{
  for (UIView *boundary in self.pendingReleasedBoundaries.allObjects) {
    if (boundary == topBoundary) continue;
    if (boundary.accessibilityViewIsModal) boundary.accessibilityViewIsModal = NO;
    [self.pendingReleasedBoundaries removeObject:boundary];
  }
}

- (void)invalidate
{
  [self reconcileTopBoundary:nil];
  [self.boundaries removeAllObjects];
  [self.pendingReleasedBoundaries removeAllObjects];
}

- (BOOL)reconcilePortalPresentations:(NSArray<BottomSheetPortalPresentation *> *)presentations
                      orderProvider:(BottomSheetPresentationOrderProvider)orderProvider
                          ifCurrent:(BOOL (NS_NOESCAPE ^)(void))isCurrent
{
  BottomSheetPortalIsolation *isolation = [BottomSheetPortalIsolation
      projectionFromPresentations:presentations ?: @[] orderProvider:orderProvider];
  if (presentations == nil || isolation == nil || (isCurrent != nil && !isCurrent())) {
    // Unregistered anchors cannot remain modal in their next attachment. This
    // retires their private flag without restoring any held background lease.
    [self clearReleasedBoundariesKeeping:nil];
    return NO;
  }
  [self reconcileHiddenBranches:isolation.hiddenBranches modalBoundary:nil];
  return YES;
}

@end

@interface BottomSheetPresentationCoordinator ()
@property (nonatomic, weak) UIWindow *window;
@property (nonatomic, strong) NSMutableDictionary<BottomSheetPresentationIdentity *, BottomSheetPresentationCandidateRecord *> *records;
@property (nonatomic, strong, nullable) BottomSheetPresentationIdentity *topPresentationIdentity;
@property (nonatomic, strong, nullable) BottomSheetPresentationIdentity *escapeOwnerIdentity;
@property (nonatomic, strong) BottomSheetPresentationModalIsolationAdapter *modalIsolationAdapter;
@property (nonatomic) NSInteger hierarchyMutationDepth;
@property (nonatomic) BOOL hierarchyMutationDirty;
@property (nonatomic, getter=isReconciliationInProgress) BOOL reconciliationInProgress;
@property (nonatomic) BOOL reconciliationDirty;

+ (nullable instancetype)coordinatorForWindow:(UIWindow *)window createIfNeeded:(BOOL)createIfNeeded;
- (void)updateController:(BottomSheetPresentationController *)controller
                  anchor:(UIView *)anchor
                  active:(BOOL)active;
- (void)removeController:(BottomSheetPresentationController *)controller;
- (void)beginHierarchyMutation;
- (void)endHierarchyMutation;
- (void)recomputeTopPresentation;
- (void)recomputeTopPresentationOnce;
- (BottomSheetPresentationEscapeRoute)escapeRouteForController:
    (BottomSheetPresentationController *)controller actionOrigin:(nullable id)origin;
@end

static const void *BottomSheetPresentationCoordinatorAssociationKey =
    &BottomSheetPresentationCoordinatorAssociationKey;

// Weak addresses of the existing per-window authority, for retiring leases even
// after a deleted view has detached or migrated. No Active/order state is copied.
static NSHashTable<BottomSheetPresentationCoordinator *> *BottomSheetLivePresentationCoordinators(void)
{
  static NSHashTable<BottomSheetPresentationCoordinator *> *coordinators;
  static dispatch_once_t once;
  dispatch_once(&once, ^{ coordinators = [NSHashTable weakObjectsHashTable]; });
  return coordinators;
}

@implementation BottomSheetPresentationCoordinator

+ (nullable instancetype)coordinatorForWindow:(UIWindow *)window createIfNeeded:(BOOL)createIfNeeded
{
  BottomSheetAssertMainThread();
  BottomSheetPresentationCoordinator *coordinator =
      objc_getAssociatedObject(window, BottomSheetPresentationCoordinatorAssociationKey);
  if (coordinator == nil && createIfNeeded) {
    coordinator = [BottomSheetPresentationCoordinator new];
    coordinator.window = window;
    coordinator.records = [NSMutableDictionary new];
    coordinator.modalIsolationAdapter = [BottomSheetPresentationModalIsolationAdapter new];
    [BottomSheetLivePresentationCoordinators() addObject:coordinator];
    objc_setAssociatedObject(
        window,
        BottomSheetPresentationCoordinatorAssociationKey,
        coordinator,
        OBJC_ASSOCIATION_RETAIN_NONATOMIC);
  }
  return coordinator;
}

+ (nullable BottomSheetPresentationIdentity *)topPresentationIdentityInWindow:(UIWindow *)window
{
  BottomSheetAssertMainThread();
  BottomSheetPresentationCoordinator *coordinator =
      [self coordinatorForWindow:window createIfNeeded:NO];
  [coordinator recomputeTopPresentation];
  return coordinator.topPresentationIdentity;
}

+ (void)retireNativeViewDeletion:(BottomSheetNativeViewDeletion *)deletion
{
  BottomSheetAssertMainThread();
  // Only leases already owned for these exact native instances are released.
  // Other windows/contexts and their masks remain unchanged.
  for (BottomSheetPresentationCoordinator *coordinator in
       BottomSheetLivePresentationCoordinators().allObjects) {
    [coordinator.modalIsolationAdapter.maskLedger retireHiddenBranchesForDeletion:deletion];
  }
}

- (void)updateController:(BottomSheetPresentationController *)controller
                  anchor:(UIView *)anchor
                  active:(BOOL)active
{
  BottomSheetAssertMainThread();
  BottomSheetPresentationCandidateRecord *record = self.records[controller.identity];
  if (record == nil) {
    record = [BottomSheetPresentationCandidateRecord new];
    record.identity = controller.identity;
    record.controller = controller;
    self.records[controller.identity] = record;
  }
  UIView *previousAnchor = record.anchor;
  if (previousAnchor != anchor) {
    if (previousAnchor != nil) {
      [self.modalIsolationAdapter unregisterBoundary:previousAnchor];
    }
    [self.modalIsolationAdapter registerBoundary:anchor];
  }
  record.anchor = anchor;
  record.active = active;
  [self recomputeTopPresentation];
}

- (void)removeController:(BottomSheetPresentationController *)controller
{
  BottomSheetAssertMainThread();
  BottomSheetPresentationCandidateRecord *record = self.records[controller.identity];
  UIView *anchor = record.anchor;
  if (anchor != nil) {
    [self.modalIsolationAdapter unregisterBoundary:anchor];
  }
  record.controller.topPresentation = NO;
  [self.records removeObjectForKey:controller.identity];

  [self recomputeTopPresentation];
}

- (void)beginHierarchyMutation
{
  BottomSheetAssertMainThread();
  self.hierarchyMutationDepth++;
}

- (void)endHierarchyMutation
{
  BottomSheetAssertMainThread();
  NSCAssert(self.hierarchyMutationDepth > 0, @"Unbalanced presentation hierarchy mutation");
  self.hierarchyMutationDepth--;
  if (self.hierarchyMutationDepth == 0 && self.hierarchyMutationDirty) {
    self.hierarchyMutationDirty = NO;
    [self recomputeTopPresentation];
  }
}

- (void)recomputeTopPresentation
{
  // Completed Fabric mounts and owned presentation lifecycle changes feed this
  // receiver. The existing hierarchy batch also groups isolation updates when
  // Top is unchanged; native writes bypassing those events are not observed.
  BottomSheetAssertMainThread();
  if (self.hierarchyMutationDepth > 0) {
    self.hierarchyMutationDirty = YES;
    return;
  }
  if (self.isReconciliationInProgress) {
    self.reconciliationDirty = YES;
    return;
  }

  self.reconciliationInProgress = YES;
  do {
    self.reconciliationDirty = NO;
    [self recomputeTopPresentationOnce];
  } while (self.reconciliationDirty && self.hierarchyMutationDepth == 0);
  self.reconciliationInProgress = NO;
}

- (void)recomputeTopPresentationOnce
{
  UIWindow *window = self.window;
  if (window == nil) {
    self.escapeOwnerIdentity = nil;
    [self.modalIsolationAdapter reconcileTopBoundary:nil];
    self.topPresentationIdentity = nil;
    return;
  }

  for (BottomSheetPresentationIdentity *identity in self.records.allKeys) {
    BottomSheetPresentationCandidateRecord *record = self.records[identity];
    if (record.controller == nil || record.anchor == nil) {
      UIView *anchor = record.anchor;
      if (anchor != nil) {
        [self.modalIsolationAdapter unregisterBoundary:anchor];
      }
      record.controller.topPresentation = NO;
      [self.records removeObjectForKey:identity];
    }
  }

  if (self.records.count == 0) {
    self.escapeOwnerIdentity = nil;
    [self.modalIsolationAdapter reconcileTopBoundary:nil];
    self.topPresentationIdentity = nil;
    if (objc_getAssociatedObject(window, BottomSheetPresentationCoordinatorAssociationKey) == self) {
      objc_setAssociatedObject(
          window,
          BottomSheetPresentationCoordinatorAssociationKey,
          nil,
          OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }
    return;
  }

  NSMutableArray<BottomSheetPresentationCandidate *> *candidates = [NSMutableArray new];
  NSMutableDictionary<BottomSheetPresentationIdentity *, UIView *> *anchors = [NSMutableDictionary new];
  for (BottomSheetPresentationCandidateRecord *record in self.records.objectEnumerator) {
    UIView *anchor = record.anchor;
    BOOL eligible = anchor != nil &&
        [BottomSheetPresentationUIKitOrderResolver isAnchor:anchor attachedToWindow:window];
    [candidates addObject:[[BottomSheetPresentationCandidate alloc]
                              initWithIdentity:record.identity
                                       active:record.isActive
                                     eligible:eligible]];
    if (anchor != nil) {
      anchors[record.identity] = anchor;
    }
  }

  BottomSheetPresentationOrderProvider orderProvider = ^BottomSheetPresentationOrder(
                                                                   BottomSheetPresentationIdentity *first,
                                                                   BottomSheetPresentationIdentity *second) {
    UIView *firstAnchor = anchors[first];
    UIView *secondAnchor = anchors[second];
    if (firstAnchor == nil || secondAnchor == nil) {
      return BottomSheetPresentationOrderUnknown;
    }
    return [BottomSheetPresentationUIKitOrderResolver orderOfAnchor:firstAnchor
                                                          relativeTo:secondAnchor
                                                            inWindow:window];
  };
  BottomSheetPresentationIdentity *nextTopIdentity =
      [BottomSheetPresentationResolver topPresentationIdentityFromCandidates:candidates
                                                               orderProvider:orderProvider];

  if (self.escapeOwnerIdentity != nextTopIdentity) {
    self.escapeOwnerIdentity = nil;
  }
  UIView *nextTopBoundary = nextTopIdentity == nil ? nil : anchors[nextTopIdentity];
  BOOL hasWindowModalPresentation = NO;
  BOOL portalSourcesAreAvailable = YES;
  NSMutableArray<BottomSheetPortalPresentation *> *portalPresentations = [NSMutableArray new];
  for (BottomSheetPresentationCandidate *candidate in candidates) {
    if (!candidate.isActive || !candidate.isEligible) continue;
    BottomSheetPresentationCandidateRecord *record = self.records[candidate.identity];
    if (record.controller.mode == BottomSheetPresentationModeNativeOverlay) {
      hasWindowModalPresentation = YES;
      continue;
    }
    BottomSheetPortalPresentation *presentation = [BottomSheetPortalPresentation
        presentationForIdentity:candidate.identity anchor:anchors[candidate.identity]
        active:candidate.isActive eligible:candidate.isEligible];
    if (presentation == nil) portalSourcesAreAvailable = NO;
    else [portalPresentations addObject:presentation];
  }
  if (hasWindowModalPresentation) {
    // Preserve the existing window-modal path until its composed portal join is
    // validated. Every transfer still uses this authority's one mask ledger.
    [self.modalIsolationAdapter reconcileTopBoundary:nextTopBoundary];
  } else {
    [self.modalIsolationAdapter reconcilePortalPresentations:
        portalSourcesAreAvailable ? portalPresentations : nil
        orderProvider:orderProvider ifCurrent:^BOOL {
          return !self.reconciliationDirty && self.hierarchyMutationDepth == 0;
        }];
  }

  NSArray<BottomSheetPresentationCandidateRecord *> *records = self.records.allValues;
  for (BottomSheetPresentationCandidateRecord *record in records) {
    if (record.identity != nextTopIdentity) {
      record.controller.topPresentation = NO;
    }
  }
  self.topPresentationIdentity = nextTopIdentity;
  if (nextTopIdentity != nil) {
    self.records[nextTopIdentity].controller.topPresentation = YES;
  }
  self.escapeOwnerIdentity = nextTopIdentity;
}

- (void)dealloc
{
  self.escapeOwnerIdentity = nil;
  [self.modalIsolationAdapter invalidate];
  for (BottomSheetPresentationCandidateRecord *record in self.records.objectEnumerator) {
    record.controller.topPresentation = NO;
  }
}

- (BottomSheetPresentationEscapeRoute)escapeRouteForController:
    (BottomSheetPresentationController *)controller actionOrigin:(id)origin
{
  BottomSheetAssertMainThread();
  if (self.hierarchyMutationDepth || self.isReconciliationInProgress) {
    return BottomSheetPresentationEscapeRoutePassThrough;
  }
  self.reconciliationInProgress = YES;
  self.reconciliationDirty = NO;
  BottomSheetPresentationEscapeRoute route = BottomSheetPresentationEscapeRoutePassThrough;
  @try {
    BOOL hasWindowModalPresentation = NO;
    BOOL sourcesAreAvailable = YES;
    NSMutableArray<BottomSheetPortalPresentation *> *presentations = [NSMutableArray new];
    NSMutableDictionary<BottomSheetPresentationIdentity *, UIView *> *anchors = [NSMutableDictionary new];
    UIWindow *window = self.window;
    for (BottomSheetPresentationCandidateRecord *record in self.records.allValues) {
      UIView *anchor = record.anchor;
      if (record.controller == nil || anchor == nil ||
          (!record.isActive && record.identity != controller.identity)) continue;
      BOOL eligible = [BottomSheetPresentationUIKitOrderResolver isAnchor:anchor attachedToWindow:window];
      if (!eligible) continue;
      anchors[record.identity] = anchor;
      if (record.controller.mode == BottomSheetPresentationModeNativeOverlay) {
        if (record.isActive) hasWindowModalPresentation = YES;
        continue;
      }
      BottomSheetPortalPresentation *presentation = [BottomSheetPortalPresentation
          presentationForIdentity:record.identity anchor:anchor active:record.isActive eligible:eligible];
      if (presentation) [presentations addObject:presentation];
      else sourcesAreAvailable = NO;
    }
    if (hasWindowModalPresentation) {
      route = [BottomSheetPresentationEscapeResolver routeForCallerIdentity:controller.identity
          topPresentationIdentity:self.escapeOwnerIdentity];
    } else if (sourcesAreAvailable) {
      route = [BottomSheetPortalIsolation escapeRouteForCallerIdentity:controller.identity
          actionOrigin:origin presentations:presentations
          orderProvider:^BottomSheetPresentationOrder(BottomSheetPresentationIdentity *a,
                                                      BottomSheetPresentationIdentity *b) {
            return [BottomSheetPresentationUIKitOrderResolver orderOfAnchor:anchors[a]
                relativeTo:anchors[b] inWindow:window];
          }];
    }
    if (self.reconciliationDirty || self.hierarchyMutationDepth || controller.isInvalidated ||
        controller.coordinator != self || self.window != window) {
      route = BottomSheetPresentationEscapeRoutePassThrough;
    }
  } @finally {
    self.reconciliationInProgress = NO;
    if (self.reconciliationDirty) [self recomputeTopPresentation];
  }
  return route;
}

@end

@implementation BottomSheetPresentationController

- (instancetype)initWithAnchor:(UIView *)anchor
{
  BottomSheetAssertMainThread();
  if (self = [super init]) {
    _identity = [BottomSheetPresentationIdentity new];
    _anchor = anchor;
    _mode = BottomSheetPresentationModePortal;
  }
  return self;
}

- (void)updateModal:(BOOL)modal active:(BOOL)active mode:(BottomSheetPresentationMode)mode
{
  BottomSheetAssertMainThread();
  if (self.isInvalidated) {
    return;
  }

  self.mode = mode;
  self.modal = modal;
  self.active = active;
  if (self.hierarchyMutationDepth > 0) {
    return;
  }
  [self reconcileRegistration];
}

- (void)reconcileRegistration
{
  UIView *anchor = self.anchor;
  UIWindow *window = self.isModal ? anchor.window : nil;
  BottomSheetPresentationCoordinator *nextCoordinator = window == nil
      ? nil
      : [BottomSheetPresentationCoordinator coordinatorForWindow:window createIfNeeded:YES];

  BottomSheetPresentationCoordinator *previousCoordinator = self.coordinator;
  if (previousCoordinator != nextCoordinator) {
    [previousCoordinator removeController:self];
    self.coordinator = nextCoordinator;
  }

  if (nextCoordinator != nil && anchor != nil) {
    [nextCoordinator updateController:self anchor:anchor active:self.isActive];
  } else {
    self.topPresentation = NO;
  }
}

- (void)beginHierarchyMutation
{
  BottomSheetAssertMainThread();
  if (self.isInvalidated) {
    return;
  }
  self.hierarchyMutationDepth++;
  if (self.hierarchyMutationDepth == 1) {
    BottomSheetPresentationCoordinator *coordinator = self.coordinator;
    self.mutationCoordinator = coordinator;
    [coordinator beginHierarchyMutation];
  }
}

- (void)endHierarchyMutation
{
  BottomSheetAssertMainThread();
  if (self.isInvalidated) {
    return;
  }
  NSCAssert(self.hierarchyMutationDepth > 0, @"Unbalanced presentation hierarchy mutation");
  self.hierarchyMutationDepth--;
  if (self.hierarchyMutationDepth > 0) {
    return;
  }

  BottomSheetPresentationCoordinator *previousCoordinator = self.mutationCoordinator;
  self.mutationCoordinator = nil;
  UIView *anchor = self.anchor;
  UIWindow *nextWindow = self.isModal ? anchor.window : nil;
  if (previousCoordinator != nil && previousCoordinator.window == nextWindow) {
    [previousCoordinator updateController:self anchor:anchor active:self.isActive];
    [previousCoordinator endHierarchyMutation];
    return;
  }

  if (previousCoordinator != nil) {
    [previousCoordinator removeController:self];
    self.coordinator = nil;
    [previousCoordinator endHierarchyMutation];
  }
  [self reconcileRegistration];
}

- (void)reconcilePresentationOrder
{
  BottomSheetAssertMainThread();
  if (self.isInvalidated) {
    return;
  }
  if (self.hierarchyMutationDepth > 0) {
    return;
  }
  [self updateModal:self.isModal active:self.isActive mode:self.mode];
}

- (BottomSheetPresentationEscapeRoute)routeForVoiceOverEscape
{
  BottomSheetAssertMainThread();
  if (self.isInvalidated) {
    return BottomSheetPresentationEscapeRoutePassThrough;
  }

  id origin = UIAccessibilityFocusedElement(UIAccessibilityNotificationVoiceOverIdentifier);
  [self reconcileRegistration];
  BottomSheetPresentationCoordinator *coordinator = self.coordinator;
  UIView *anchor = self.anchor;
  UIWindow *window = self.isModal ? anchor.window : nil;
  if (coordinator == nil || window == nil || coordinator.window != window) {
    return BottomSheetPresentationEscapeRoutePassThrough;
  }

  [coordinator recomputeTopPresentation];
  return [coordinator escapeRouteForController:self actionOrigin:origin];
}

- (void)invalidate
{
  BottomSheetAssertMainThread();
  if (self.isInvalidated) {
    return;
  }
  self.invalidated = YES;
  BottomSheetPresentationCoordinator *mutationCoordinator = self.mutationCoordinator;
  self.mutationCoordinator = nil;
  self.hierarchyMutationDepth = 0;
  BottomSheetPresentationCoordinator *coordinator = self.coordinator;
  self.coordinator = nil;
  [coordinator removeController:self];
  if (mutationCoordinator != nil) {
    [mutationCoordinator endHierarchyMutation];
  }
  self.topPresentation = NO;
  self.anchor = nil;
}

- (void)dealloc
{
  if (NSThread.isMainThread) {
    [self invalidate];
  }
}

@end
