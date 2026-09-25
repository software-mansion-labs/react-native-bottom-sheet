#import "BottomSheetPresentationOwnership.h"

#import <math.h>
#import <objc/runtime.h>

static void BottomSheetAssertMainThread(void)
{
  NSCAssert(NSThread.isMainThread, @"Presentation ownership must be accessed on the main thread");
}

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

@implementation BottomSheetPresentationResolver

+ (nullable BottomSheetPresentationIdentity *)topPresentationIdentityFromCandidates:
                                                   (NSArray<BottomSheetPresentationCandidate *> *)candidates
                                                                    orderProvider:
                                                                        (BottomSheetPresentationOrderProvider)orderProvider
{
  NSMutableArray<BottomSheetPresentationCandidate *> *eligibleCandidates = [NSMutableArray new];
  for (BottomSheetPresentationCandidate *candidate in candidates) {
    if (candidate.isActive && candidate.isEligible) {
      [eligibleCandidates addObject:candidate];
    }
  }

  if (eligibleCandidates.count == 1) {
    return eligibleCandidates.firstObject.identity;
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
        return nil;
      }
      topIdentity = candidate.identity;
    }
  }
  return topIdentity;
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

@interface BottomSheetAccessibilityHiddenMutation : NSObject
@property (nonatomic) BOOL baseline;
@end

@implementation BottomSheetAccessibilityHiddenMutation
@end

@interface BottomSheetPresentationModalIsolationAdapter : NSObject
@property (nonatomic, strong) NSHashTable<UIView *> *boundaries;
@property (nonatomic, strong) NSHashTable<UIView *> *pendingReleasedBoundaries;
@property (nonatomic, strong)
    NSMapTable<UIView *, BottomSheetAccessibilityHiddenMutation *> *hiddenBranchMutations;

- (void)registerBoundary:(UIView *)boundary;
- (void)unregisterBoundary:(UIView *)boundary;
- (void)reconcileTopBoundary:(nullable UIView *)topBoundary;
- (void)invalidate;
@end

@implementation BottomSheetPresentationModalIsolationAdapter

- (instancetype)init
{
  if (self = [super init]) {
    _boundaries = [NSHashTable weakObjectsHashTable];
    _pendingReleasedBoundaries = [NSHashTable weakObjectsHashTable];
    _hiddenBranchMutations = [NSMapTable weakToStrongObjectsMapTable];
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
  if (topBoundary != nil && !topBoundary.accessibilityViewIsModal) {
    topBoundary.accessibilityViewIsModal = YES;
  }

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

  // Establish the complete new isolation path before releasing either the old
  // modal boundary or any branch hidden for the previous Top. This mirrors the
  // boundary transfer order and prevents a synchronous Top swap from exposing
  // lower presentations or application content between writes.
  for (UIView *branch in hiddenBranches) {
    BottomSheetAccessibilityHiddenMutation *mutation =
        [self.hiddenBranchMutations objectForKey:branch];
    if (mutation == nil) {
      mutation = [BottomSheetAccessibilityHiddenMutation new];
      mutation.baseline = branch.accessibilityElementsHidden;
      [self.hiddenBranchMutations setObject:mutation forKey:branch];
    }
    if (!branch.accessibilityElementsHidden) {
      branch.accessibilityElementsHidden = YES;
    }
  }

  NSArray<UIView *> *boundaries = self.boundaries.allObjects;
  for (UIView *boundary in boundaries) {
    if (boundary != topBoundary && boundary.accessibilityViewIsModal) {
      boundary.accessibilityViewIsModal = NO;
    }
  }

  NSArray<UIView *> *releasedBoundaries = self.pendingReleasedBoundaries.allObjects;
  for (UIView *boundary in releasedBoundaries) {
    if (boundary == topBoundary) {
      continue;
    }
    if (boundary.accessibilityViewIsModal) {
      boundary.accessibilityViewIsModal = NO;
    }
    [self.pendingReleasedBoundaries removeObject:boundary];
  }

  NSArray<UIView *> *previouslyHiddenBranches =
      self.hiddenBranchMutations.keyEnumerator.allObjects;
  for (UIView *branch in previouslyHiddenBranches) {
    if ([hiddenBranches containsObject:branch]) {
      continue;
    }
    BottomSheetAccessibilityHiddenMutation *mutation =
        [self.hiddenBranchMutations objectForKey:branch];
    // A later writer that made the branch visible wins. Restore only the
    // library's hidden write; a branch whose baseline was already hidden does
    // not need another setter invocation during cleanup.
    if (mutation != nil && branch.accessibilityElementsHidden && !mutation.baseline) {
      branch.accessibilityElementsHidden = NO;
    }
    [self.hiddenBranchMutations removeObjectForKey:branch];
  }
}

- (void)invalidate
{
  [self reconcileTopBoundary:nil];
  [self.boundaries removeAllObjects];
  [self.pendingReleasedBoundaries removeAllObjects];
  [self.hiddenBranchMutations removeAllObjects];
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
@end

static const void *BottomSheetPresentationCoordinatorAssociationKey =
    &BottomSheetPresentationCoordinatorAssociationKey;

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

  BottomSheetPresentationIdentity *nextTopIdentity =
      [BottomSheetPresentationResolver topPresentationIdentityFromCandidates:candidates
                                                               orderProvider:^BottomSheetPresentationOrder(
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
  }];

  if (self.escapeOwnerIdentity != nextTopIdentity) {
    self.escapeOwnerIdentity = nil;
  }
  UIView *nextTopBoundary = nextTopIdentity == nil ? nil : anchors[nextTopIdentity];
  [self.modalIsolationAdapter reconcileTopBoundary:nextTopBoundary];

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

  [self reconcileRegistration];
  BottomSheetPresentationCoordinator *coordinator = self.coordinator;
  UIView *anchor = self.anchor;
  UIWindow *window = self.isModal ? anchor.window : nil;
  if (coordinator == nil || window == nil || coordinator.window != window) {
    return BottomSheetPresentationEscapeRoutePassThrough;
  }

  [coordinator recomputeTopPresentation];
  return [BottomSheetPresentationEscapeResolver routeForCallerIdentity:self.identity
                                               topPresentationIdentity:coordinator.escapeOwnerIdentity];
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
