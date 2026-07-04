#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

// Wrapper around the sheet content. It carries no behavior of its own; the
// bottom sheet host identifies it by this type and, in native-overlay mode,
// owns its Yoga geometry by pushing the overlay's measured width and the
// natively computed detent cap into its shadow state.
@interface BottomSheetContentWrapperComponentView : RCTViewComponentView

// Pushes the wrapper's target size (points) into its shadow state; the
// component descriptor forces the node's Yoga size from it. Pass CGSizeZero to
// clear, restoring the JS-provided styles.
- (void)updateFrameSize:(CGSize)size;

@end

NS_ASSUME_NONNULL_END
