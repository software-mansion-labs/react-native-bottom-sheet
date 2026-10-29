#import "BottomSheetAccessibilityMaskLedger.h"

@interface BottomSheetAccessibilityHiddenMutation : NSObject
@property (nonatomic) BOOL baseline;
@property (nonatomic) BOOL portalRequired;
@property (nonatomic) BOOL windowRequired;
@end

@implementation BottomSheetAccessibilityHiddenMutation
@end

@interface BottomSheetAccessibilityMaskLedger ()
@property (nonatomic, strong)
    NSMapTable<UIView *, BottomSheetAccessibilityHiddenMutation *> *mutations;
@end

@implementation BottomSheetAccessibilityMaskLedger

- (void)retireHiddenBranchesForDeletion:(BottomSheetNativeViewDeletion *)deletion
{
  NSCAssert(NSThread.isMainThread, @"Accessibility masks must be accessed on the main thread");
  NSArray<UIView *> *leased = self.mutations.keyEnumerator.allObjects;
  NSMutableSet<UIView *> *retired = [NSMutableSet new];
  NSMapTable<UIView *, BottomSheetAccessibilityHiddenMutation *> *replacements =
      [NSMapTable strongToStrongObjectsMapTable];
  for (UIView *branch in leased) {
    for (UIView *owner = branch; owner; owner = owner.superview) {
      if ([deletion.survivingRegisteredViews containsObject:owner]) break;
      if ([deletion.deletedViews containsObject:owner]) {
        [retired addObject:branch];
        // A deleted masked ancestor may be the only mask hiding a surviving
        // registered subtree. Transfer to its native frontier before restoring
        // the dying instance, without masking descendants twice.
        for (UIView *survivor in deletion.survivingRegisteredViews) {
          if (![survivor isDescendantOfView:branch]) continue;
          BOOL coveredBySurvivingAncestor = NO;
          for (UIView *parent = survivor.superview; parent && parent != branch;
               parent = parent.superview) {
            if ([deletion.survivingRegisteredViews containsObject:parent]) {
              coveredBySurvivingAncestor = YES;
              break;
            }
          }
          if (!coveredBySurvivingAncestor) {
            BottomSheetAccessibilityHiddenMutation *requirements = [replacements objectForKey:survivor];
            if (!requirements) {
              requirements = [BottomSheetAccessibilityHiddenMutation new];
              [replacements setObject:requirements forKey:survivor];
            }
            BottomSheetAccessibilityHiddenMutation *previous = [self.mutations objectForKey:branch];
            requirements.portalRequired |= previous.portalRequired;
            requirements.windowRequired |= previous.windowRequired;
          }
        }
        break;
      }
    }
  }
  for (UIView *replacement in replacements.keyEnumerator) {
    [self maskBranch:replacement];
    BottomSheetAccessibilityHiddenMutation *mutation = [self.mutations objectForKey:replacement];
    BottomSheetAccessibilityHiddenMutation *requirements = [replacements objectForKey:replacement];
    mutation.portalRequired |= requirements.portalRequired;
    mutation.windowRequired |= requirements.windowRequired;
  }
  for (UIView *branch in retired) [self restoreBranch:branch];
}

- (void)maskBranch:(UIView *)branch
{
  BottomSheetAccessibilityHiddenMutation *mutation = [self.mutations objectForKey:branch];
  if (mutation == nil) {
    mutation = [BottomSheetAccessibilityHiddenMutation new];
    mutation.baseline = branch.accessibilityElementsHidden;
    [self.mutations setObject:mutation forKey:branch];
  }
  if (!branch.accessibilityElementsHidden) branch.accessibilityElementsHidden = YES;
}

- (void)restoreBranch:(UIView *)branch
{
  BottomSheetAccessibilityHiddenMutation *mutation = [self.mutations objectForKey:branch];
  // Preserve a later visible write and a baseline that was already hidden.
  // Remasking does not update the baseline; same-value writes are indistinguishable.
  if (mutation != nil && branch.accessibilityElementsHidden && !mutation.baseline) {
    branch.accessibilityElementsHidden = NO;
  }
  [self.mutations removeObjectForKey:branch];
}

- (instancetype)init
{
  if (self = [super init]) {
    _mutations = [NSMapTable weakToStrongObjectsMapTable];
  }
  return self;
}

- (void)reconcilePortalHiddenBranches:(NSSet<UIView *> *)portalHiddenBranches
                windowHiddenBranches:(NSSet<UIView *> *)windowHiddenBranches
             beforeRestoringBranches:(void (NS_NOESCAPE ^)(void))transferBoundaries
{
  NSCAssert(NSThread.isMainThread, @"Accessibility masks must be accessed on the main thread");
  for (BottomSheetAccessibilityHiddenMutation *mutation in self.mutations.objectEnumerator) {
    if (portalHiddenBranches != nil) mutation.portalRequired = NO;
    mutation.windowRequired = NO;
  }
  for (UIView *branch in portalHiddenBranches) {
    [self maskBranch:branch];
    [self.mutations objectForKey:branch].portalRequired = YES;
  }
  for (UIView *branch in windowHiddenBranches) {
    [self maskBranch:branch];
    [self.mutations objectForKey:branch].windowRequired = YES;
  }
  if (transferBoundaries != nil) {
    transferBoundaries();
  }

  NSArray<UIView *> *previouslyHiddenBranches = self.mutations.keyEnumerator.allObjects;
  for (UIView *branch in previouslyHiddenBranches) {
    BottomSheetAccessibilityHiddenMutation *mutation = [self.mutations objectForKey:branch];
    if (mutation.portalRequired || mutation.windowRequired) {
      continue;
    }
    [self restoreBranch:branch];
  }
}

@end
