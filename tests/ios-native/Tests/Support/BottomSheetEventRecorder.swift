import CoreGraphics
import ReactNativeBottomSheet
import UIKit
import XCTest

@MainActor
final class BottomSheetEventRecorder: NSObject, @preconcurrency BottomSheetHostingViewDelegate {
  struct PositionSample {
    let position: CGFloat
    let isModalScrimVisible: Bool
    let isPresentationBoundaryModal: Bool?
    let dismissAccessibilityFrame: CGRect?
  }

  var changedIndices: [Int] = []
  var settledIndices: [Int] = []
  var positionSamples: [PositionSample] = []
  var didSettleExpectation: XCTestExpectation?
  weak var adapter: BottomSheetHostingViewDelegate?
  weak var observedPresentationBoundary: UIView?

  func reset() {
    changedIndices.removeAll()
    settledIndices.removeAll()
    positionSamples.removeAll()
  }

  func bottomSheetHostingView(_ view: BottomSheetHostingView, didChangeIndex index: Int) {
    adapter?.bottomSheetHostingView(view, didChangeIndex: index)
    changedIndices.append(index)
  }

  func bottomSheetHostingView(_ view: BottomSheetHostingView, didSettle index: Int) {
    adapter?.bottomSheetHostingView(view, didSettle: index)
    settledIndices.append(index)
    didSettleExpectation?.fulfill()
  }

  func bottomSheetHostingView(
    _ view: BottomSheetHostingView,
    didChangePosition position: CGFloat,
    index: CGFloat
  ) {
    adapter?.bottomSheetHostingView(view, didChangePosition: position, index: index)
    positionSamples.append(
      PositionSample(
        position: position,
        isModalScrimVisible: view.isModalAccessibilityActive,
        isPresentationBoundaryModal: observedPresentationBoundary?.accessibilityViewIsModal,
        dismissAccessibilityFrame: findDismiss(in: view)?.accessibilityFrame
      )
    )
  }

  func bottomSheetHostingView(_ view: BottomSheetHostingView, didReportError message: String) {
    adapter?.bottomSheetHostingView(view, didReportError: message)
    XCTFail("Production host reported an error: \(message)")
  }

  func bottomSheetHostingViewDidLayout(_ view: BottomSheetHostingView) {
    adapter?.bottomSheetHostingViewDidLayout(view)
  }
}
