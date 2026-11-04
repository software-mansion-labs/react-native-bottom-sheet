#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

// The in-flow wrapper around the sheet's content: a plain view natively. Its
// own component type only so its shadow node can report the card's scale (see
// BottomSheetCardContent).
@interface BottomSheetContentWrapperComponentView : RCTViewComponentView

@end

NS_ASSUME_NONNULL_END
