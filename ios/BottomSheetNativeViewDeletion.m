#import "BottomSheetNativeViewDeletion.h"

@implementation BottomSheetNativeViewDeletion
- (instancetype)initWithDeletedViews:(NSSet<UIView *> *)deletedViews
          survivingRegisteredViews:(NSSet<UIView *> *)survivingRegisteredViews
{
  if (self = [super init]) {
    _deletedViews = [deletedViews copy];
    _survivingRegisteredViews = [survivingRegisteredViews copy];
  }
  return self;
}
@end
