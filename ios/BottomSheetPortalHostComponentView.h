#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

// The container `BottomSheetProvider` renders its portals into. Its siblings
// are the screen behind the sheets, so while a sheet in it is modal it hides
// them from VoiceOver.
@interface BottomSheetPortalHostComponentView : RCTViewComponentView

// Registers whether `sheet`, a portal sheet inside this host, currently blocks
// the screen behind it. The host hides its siblings while any registered sheet
// does.
- (void)setSheet:(UIView *)sheet isModal:(BOOL)isModal;

@end

NS_ASSUME_NONNULL_END
