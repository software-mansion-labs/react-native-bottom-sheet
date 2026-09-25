import ReactNativeBottomSheet
import UIKit
import XCTest

@MainActor
final class BottomSheetAccessibleDismissalTests: XCTestCase {
  func testActivationRemovesDismissAtCloseCommitAndRejectsRetries() async throws {
    let fixture = BottomSheetHostFixture()
    defer { fixture.tearDown() }
    let host = fixture.host
    let events = fixture.events
    let dismiss = try XCTUnwrap(findDismiss(in: host))
    let settle = expectation(description: "activation closes through the real spring")
    settle.assertForOverFulfill = true
    events.didSettleExpectation = settle
    events.positionSamples.removeAll()

    XCTAssertTrue(host.isModalAccessibilityActive)
    XCTAssertTrue(dismiss.accessibilityActivate())
    XCTAssertEqual(events.changedIndices, [0])
    XCTAssertNil(findDismiss(in: host), "Dismiss must leave the accessibility tree at close commit")
    XCTAssertNotNil(host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))
    XCTAssertLessThan(host.currentContentOffsetY, host.bounds.height - 0.5)
    XCTAssertFalse(dismiss.isHidden, "the visual scrim remains during closing")
    XCTAssertTrue(host.isModalAccessibilityActive)
    XCTAssertTrue(host.hitTest(CGPoint(x: host.bounds.midX, y: 1), with: nil) === dismiss)
    XCTAssertEqual(events.settledIndices, [])
    XCTAssertFalse(dismiss.accessibilityActivate(), "activation during closing must report rejection")
    XCTAssertEqual(events.changedIndices, [0])

    await fulfillment(of: [settle], timeout: 2.0)

    XCTAssertNil(findDismiss(in: host))
    XCTAssertFalse(dismiss.accessibilityActivate(), "activation after closed settle must report rejection")
    XCTAssertEqual(events.changedIndices, [0])
    XCTAssertEqual(events.settledIndices, [0])
    XCTAssertEqual(host.currentContentOffsetY, host.bounds.height, accuracy: 0.5)
    XCTAssertNil(host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))
    XCTAssertFalse(host.isModalAccessibilityActive)
    XCTAssertTrue(dismiss.isHidden)
    XCTAssertNil(host.hitTest(CGPoint(x: host.bounds.midX, y: 1), with: nil))
    XCTAssertGreaterThan(events.positionSamples.count, 1)
    XCTAssertTrue(events.positionSamples.dropLast().allSatisfy(\.isPresentationActive))
    XCTAssertEqual(events.positionSamples.last?.isPresentationActive, false)
  }

  func testHostEscapeRemovesDismissAtCloseCommitAndConsumesRetries() async throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal])
    defer { fixture.tearDown() }
    let host = fixture.host
    let events = fixture.events
    let dismiss = try XCTUnwrap(findDismiss(in: host))
    let settle = expectation(description: "host Escape closes through the real spring")
    settle.assertForOverFulfill = true
    events.didSettleExpectation = settle
    events.positionSamples.removeAll()

    XCTAssertTrue(host.isModalAccessibilityActive)
    XCTAssertTrue(host.accessibilityPerformEscape())
    XCTAssertEqual(events.changedIndices, [0])
    XCTAssertNil(findDismiss(in: host), "Dismiss must leave the accessibility tree at Escape close commit")
    XCTAssertNotNil(host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))
    XCTAssertLessThan(host.currentContentOffsetY, host.bounds.height - 0.5)
    XCTAssertFalse(dismiss.isHidden)
    XCTAssertTrue(host.isModalAccessibilityActive)
    XCTAssertEqual(events.settledIndices, [])
    XCTAssertTrue(host.accessibilityPerformEscape(), "closing Top must keep consuming Escape")
    XCTAssertEqual(events.changedIndices, [0])

    await fulfillment(of: [settle], timeout: 2.0)

    XCTAssertNil(findDismiss(in: host))
    XCTAssertFalse(host.accessibilityPerformEscape())
    XCTAssertEqual(events.changedIndices, [0])
    XCTAssertEqual(events.settledIndices, [0])
    XCTAssertEqual(host.currentContentOffsetY, host.bounds.height, accuracy: 0.5)
    XCTAssertNil(host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))
    XCTAssertFalse(host.isModalAccessibilityActive)
    XCTAssertTrue(dismiss.isHidden)
    XCTAssertGreaterThan(events.positionSamples.count, 1)
    XCTAssertTrue(events.positionSamples.dropLast().allSatisfy(\.isPresentationActive))
    XCTAssertEqual(events.positionSamples.last?.isPresentationActive, false)
  }

  private func findDismiss(in view: UIView) -> UIView? {
    if view.isAccessibilityElement,
      view.accessibilityTraits.contains(.button),
      view.accessibilityLabel == "Dismiss"
    {
      return view
    }
    return view.subviews.lazy.compactMap { self.findDismiss(in: $0) }.first
  }
}
