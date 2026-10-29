#import "BottomSheetNativeHierarchySnapshot.h"
#import <React/RCTFabricModalHostViewController.h>
#import <objc/runtime.h>

static NSMapTable *BottomSheetNativeFactTable(void)
{
  return [[NSMapTable alloc]
      initWithKeyOptions:NSPointerFunctionsStrongMemory | NSPointerFunctionsObjectPointerPersonality
            valueOptions:NSPointerFunctionsStrongMemory capacity:0];
}

static IMP BottomSheetGetterImplementation(Class type, SEL selector)
{
  Method method = class_getInstanceMethod(type, selector);
  return method ? method_getImplementation(method) : NULL;
}

@implementation BottomSheetNativeHierarchySnapshot {
  IMP _parentGetter;
  IMP _childrenGetter;
  IMP _tagGetter;
  IMP _nextResponderGetter;
  IMP _loadedViewGetter;
  IMP _modalDelegateGetter;
  NSMapTable<UIView *, id> *_parents;
  NSMapTable<UIView *, NSArray<UIView *> *> *_children;
  NSMapTable<UIView *, NSNumber *> *_tags;
  NSMapTable<UIView *, id> *_modalOwners;
  BOOL _valid;
}

- (instancetype)init
{
  NSCAssert(NSThread.isMainThread, @"Native hierarchy must be read on main");
  if (self = [super init]) {
    _parents = BottomSheetNativeFactTable();
    _children = BottomSheetNativeFactTable();
    _tags = BottomSheetNativeFactTable();
    _modalOwners = BottomSheetNativeFactTable();
    Class base = objc_getClass("UIView");
    // Accept installed base getters, including diagnostic wrappers. Their
    // identity must remain stable for this query; subclass overrides are rejected.
    _parentGetter = BottomSheetGetterImplementation(base, @selector(superview));
    _childrenGetter = BottomSheetGetterImplementation(base, @selector(subviews));
    _tagGetter = BottomSheetGetterImplementation(base, @selector(tag));
    _nextResponderGetter = BottomSheetGetterImplementation(base, @selector(nextResponder));
    _loadedViewGetter = BottomSheetGetterImplementation(UIViewController.class, @selector(viewIfLoaded));
    _modalDelegateGetter = BottomSheetGetterImplementation(RCTFabricModalHostViewController.class, @selector(delegate));
    _valid = _parentGetter != NULL && _childrenGetter != NULL && _tagGetter != NULL;
  }
  return self;
}

- (BOOL)isValid { return _valid; }

- (BOOL)canRead:(SEL)selector fromView:(UIView *)view implementation:(IMP)expected
{
  NSCAssert(NSThread.isMainThread, @"Native hierarchy must be read on main");
  if (!_valid) return NO;
  Method actual = class_getInstanceMethod(object_getClass(view), selector);
  Method base = class_getInstanceMethod(objc_getClass("UIView"), selector);
  if (!actual || !base || method_getImplementation(actual) != expected ||
      method_getImplementation(base) != expected) {
    _valid = NO;
    return NO;
  }
  return YES;
}

- (UIView *)parentOfView:(UIView *)view
{
  if (!view) return nil;
  if (![self canRead:@selector(superview) fromView:view implementation:_parentGetter]) return nil;
  id cached = [_parents objectForKey:view];
  if (!cached) {
    cached = view.superview ?: NSNull.null;
    [_parents setObject:cached forKey:view];
  }
  return cached == NSNull.null ? nil : cached;
}

- (NSInteger)tagOfView:(UIView *)view
{
  if (!view) return 0;
  if (![self canRead:@selector(tag) fromView:view implementation:_tagGetter]) return 0;
  NSNumber *cached = [_tags objectForKey:view];
  if (!cached) {
    cached = @(view.tag);
    [_tags setObject:cached forKey:view];
  }
  return cached.integerValue;
}

- (NSArray<UIView *> *)childrenOfView:(UIView *)view
{
  if (![self canRead:@selector(subviews) fromView:view implementation:_childrenGetter]) return nil;
  NSArray<UIView *> *cached = [_children objectForKey:view];
  if (!cached) {
    cached = [view.subviews copy];
    [_children setObject:cached forKey:view];
  }
  return cached;
}

- (BOOL)validate
{
  NSCAssert(NSThread.isMainThread, @"Native hierarchy must be read on main");
  for (UIView *view in _parents) {
    if (![self canRead:@selector(superview) fromView:view implementation:_parentGetter] ||
        (view.superview ?: NSNull.null) != [_parents objectForKey:view]) return NO;
  }
  for (UIView *view in _tags) {
    if (![self canRead:@selector(tag) fromView:view implementation:_tagGetter] ||
        view.tag != [_tags objectForKey:view].integerValue) return NO;
  }
  for (UIView *view in _children) {
    if (![self canRead:@selector(subviews) fromView:view implementation:_childrenGetter]) return NO;
    NSArray<UIView *> *current = view.subviews;
    NSArray<UIView *> *captured = [_children objectForKey:view];
    if (current.count != captured.count) return NO;
    for (NSUInteger index = 0; index < current.count; index++) {
      UIView *child = current[index];
      if (child != captured[index] ||
          ![self canRead:@selector(superview) fromView:child implementation:_parentGetter] ||
          child.superview != view) return NO;
    }
  }
  for (UIView *view in _modalOwners) {
    if (([self readModalOwnerOfContainer:view] ?: NSNull.null) != [_modalOwners objectForKey:view]) return NO;
  }
  return _valid;
}

- (UIView *)modalOwnerOfContainer:(UIView *)view
{
  NSCAssert(NSThread.isMainThread, @"Native hierarchy must be read on main");
  id cached = [_modalOwners objectForKey:view];
  if (!cached) {
    cached = [self readModalOwnerOfContainer:view] ?: NSNull.null;
    [_modalOwners setObject:cached forKey:view];
  }
  return cached == NSNull.null ? nil : cached;
}

- (UIView *)readModalOwnerOfContainer:(UIView *)view
{
  if (object_getClass(view) != UIView.class) return nil;
  if (![self canRead:@selector(nextResponder) fromView:view implementation:_nextResponderGetter]) return nil;
  UIResponder *responder = view.nextResponder;
  if (object_getClass(responder) != RCTFabricModalHostViewController.class) return nil;
  RCTFabricModalHostViewController *controller = (RCTFabricModalHostViewController *)responder;
  Method loadedView = class_getInstanceMethod(object_getClass(controller), @selector(viewIfLoaded));
  Method delegate = class_getInstanceMethod(RCTFabricModalHostViewController.class, @selector(delegate));
  if (!loadedView || !delegate || method_getImplementation(loadedView) != _loadedViewGetter ||
      method_getImplementation(delegate) != _modalDelegateGetter) {
    _valid = NO;
    return nil;
  }
  if (controller.viewIfLoaded != view) return nil;
  id owner = controller.delegate;
  // Its public header imports C++ dependencies; keep this physical-fact module
  // Objective-C and verify the stock runtime class without dispatching to it.
  Class modalOwnerClass = objc_getClass("RCTModalHostViewComponentView");
  for (Class type = object_getClass(owner); type; type = class_getSuperclass(type)) {
    if (type == modalOwnerClass) return owner;
  }
  return nil;
}
@end
