import ReactNativeBottomSheet
import XCTest

@MainActor
final class BottomSheetHostingViewLifecycleTests: XCTestCase {
  func testProgrammaticCloseKeepsModalBoundaryUntilSettle() async {
    let fixture = BottomSheetHostFixture()
    defer { fixture.tearDown() }

    let host = fixture.host
    let events = fixture.events
    let closedIndex = 0
    let settle = expectation(description: "real production spring settles closed")
    settle.assertForOverFulfill = true
    events.didSettleExpectation = settle
    events.positionSamples.removeAll()

    XCTAssertTrue(host.isModalAccessibilityActive)
    XCTAssertLessThan(host.currentContentOffsetY, host.bounds.height - 0.5)

    host.setDetentIndex(closedIndex)

    XCTAssertNotNil(host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))
    XCTAssertTrue(host.isModalAccessibilityActive)
    XCTAssertLessThan(host.currentContentOffsetY, host.bounds.height - 0.5)
    XCTAssertEqual(events.changedIndices, [], "programmatic close emits no index change")
    XCTAssertEqual(events.settledIndices, [])

    await fulfillment(of: [settle], timeout: 2.0)

    XCTAssertEqual(events.settledIndices, [closedIndex])
    XCTAssertEqual(events.changedIndices, [])
    XCTAssertEqual(host.currentContentOffsetY, host.bounds.height, accuracy: 0.5)
    XCTAssertNil(host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))
    XCTAssertFalse(host.isModalAccessibilityActive)

    XCTAssertGreaterThan(events.positionSamples.count, 1)
    XCTAssertTrue(
      events.positionSamples.dropLast().allSatisfy(\.isPresentationActive),
      "active presentation must remain a modal boundary for every nonterminal spring sample"
    )
    XCTAssertEqual(events.positionSamples.last?.position ?? .nan, 0, accuracy: 0.5)
    XCTAssertEqual(events.positionSamples.last?.isPresentationActive, false)
  }
}
