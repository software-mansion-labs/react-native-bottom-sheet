import ReactNativeBottomSheet
import UIKit
import XCTest

@MainActor
final class BottomSheetPresentationEscapeTests: XCTestCase {
  func testLowerEscapeIsConsumedWithoutClosingWhileUpperIsTop() {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .portal])
    defer { fixture.tearDown() }
    let lowerHost = fixture.hosts[0]
    let upperHost = fixture.hosts[1]
    let lowerEvents = fixture.eventRecorders[0]
    let upperEvents = fixture.eventRecorders[1]
    let lowerOffset = lowerHost.currentContentOffsetY
    let upperOffset = upperHost.currentContentOffsetY

    XCTAssertTrue(lowerHost.accessibilityPerformEscape())
    assertNoClose(lowerHost, events: lowerEvents, offset: lowerOffset)
    XCTAssertEqual(upperHost.currentContentOffsetY, upperOffset, accuracy: 0.5)

    XCTAssertTrue(upperHost.accessibilityPerformEscape())
    XCTAssertEqual(upperEvents.changedIndices, [0])
    XCTAssertEqual(upperEvents.settledIndices, [])
    XCTAssertNotNil(upperHost.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))
    XCTAssertEqual(lowerEvents.changedIndices, [])
  }

  func testDismissibleTopEscapeStartsExactlyOneClose() {
    let fixture = BottomSheetHostFixture(presentations: [.portal])
    defer { fixture.tearDown() }

    XCTAssertTrue(fixture.host.accessibilityPerformEscape())
    XCTAssertEqual(fixture.events.changedIndices, [0])
    XCTAssertEqual(fixture.events.settledIndices, [])
    XCTAssertNotNil(fixture.host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))
  }

  func testClosingTopEscapeIsConsumedWithoutRestartingClose() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal])
    defer { fixture.tearDown() }
    let host = fixture.host

    XCTAssertTrue(host.accessibilityPerformEscape())
    let closingAnimation = try XCTUnwrap(
      host.sheetContainer.layer.animation(forKey: "bottomSheetSettle")
    )

    XCTAssertTrue(host.accessibilityPerformEscape())
    XCTAssertEqual(fixture.events.changedIndices, [0])
    XCTAssertEqual(fixture.events.settledIndices, [])
    XCTAssertTrue(
      host.sheetContainer.layer.animation(forKey: "bottomSheetSettle") === closingAnimation
    )
  }

  func testNondismissibleTopConsumesEscapeWithoutClosing() {
    let fixture = BottomSheetHostFixture(presentations: [.portal])
    defer { fixture.tearDown() }
    fixture.host.setDetents([
      ["value": 320.0, "kind": "points", "programmatic": false]
    ])
    fixture.host.layoutIfNeeded()
    fixture.events.reset()
    let offset = fixture.host.currentContentOffsetY

    XCTAssertTrue(fixture.host.accessibilityPerformEscape())
    assertNoClose(fixture.host, events: fixture.events, offset: offset)
  }

  func testProgrammaticOnlyTopConsumesEscapeWithoutClosing() {
    let fixture = BottomSheetHostFixture(presentations: [.portal])
    defer { fixture.tearDown() }
    fixture.host.setDetents([
      ["value": 0.0, "kind": "points", "programmatic": true],
      ["value": 320.0, "kind": "points", "programmatic": false],
    ])
    fixture.host.layoutIfNeeded()
    fixture.events.reset()
    let offset = fixture.host.currentContentOffsetY

    XCTAssertTrue(fixture.host.accessibilityPerformEscape())
    assertNoClose(fixture.host, events: fixture.events, offset: offset)
  }

  func testNoPublishedTopPassesEscapeThrough() {
    let fixture = BottomSheetHostFixture()
    defer { fixture.tearDown() }
    let offset = fixture.host.currentContentOffsetY

    XCTAssertFalse(fixture.host.accessibilityPerformEscape())
    assertNoClose(fixture.host, events: fixture.events, offset: offset)
  }

  func testStaleAttachedOwnerConsumesAfterNativeOrderChanges() {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .portal])
    defer { fixture.tearDown() }
    fixture.bringComponentToFront(at: 0)
    let formerTop = fixture.hosts[1]
    let formerTopOffset = formerTop.currentContentOffsetY

    XCTAssertTrue(formerTop.accessibilityPerformEscape())
    assertNoClose(
      formerTop,
      events: fixture.eventRecorders[1],
      offset: formerTopOffset
    )

    XCTAssertTrue(fixture.hosts[0].accessibilityPerformEscape())
    XCTAssertEqual(fixture.eventRecorders[0].changedIndices, [0])
  }

  func testDetachedCallerPassesThroughWithoutConsultingFormerWindow() {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .portal])
    defer { fixture.tearDown() }
    let detachedHost = fixture.hosts[1]
    fixture.detachComponent(at: 1)
    let detachedOffset = detachedHost.currentContentOffsetY

    XCTAssertFalse(detachedHost.accessibilityPerformEscape())
    assertNoClose(
      detachedHost,
      events: fixture.eventRecorders[1],
      offset: detachedOffset
    )

    XCTAssertTrue(fixture.hosts[0].accessibilityPerformEscape())
    XCTAssertEqual(fixture.eventRecorders[0].changedIndices, [0])
  }

  func testDifferentWindowsRouteEscapeToIndependentTops() {
    let firstFixture = BottomSheetHostFixture(presentations: [.portal])
    let secondFixture = BottomSheetHostFixture(presentations: [.portal])
    defer {
      secondFixture.tearDown()
      firstFixture.tearDown()
    }

    XCTAssertTrue(firstFixture.host.accessibilityPerformEscape())
    XCTAssertEqual(firstFixture.events.changedIndices, [0])
    XCTAssertEqual(secondFixture.events.changedIndices, [])

    XCTAssertTrue(secondFixture.host.accessibilityPerformEscape())
    XCTAssertEqual(firstFixture.events.changedIndices, [0])
    XCTAssertEqual(secondFixture.events.changedIndices, [0])
  }

  func testPortalAndNativeOverlayShareTopEscapeRouting() {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .nativeOverlay])
    defer { fixture.tearDown() }
    let portalHost = fixture.hosts[0]
    let overlayHost = fixture.hosts[1]

    XCTAssertTrue(portalHost.accessibilityPerformEscape())
    XCTAssertEqual(fixture.eventRecorders[0].changedIndices, [])
    XCTAssertNil(portalHost.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))

    XCTAssertTrue(overlayHost.accessibilityPerformEscape())
    XCTAssertEqual(fixture.eventRecorders[1].changedIndices, [0])
    XCTAssertNotNil(overlayHost.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))
    XCTAssertEqual(fixture.eventRecorders[0].changedIndices, [])
  }

  private func assertNoClose(
    _ host: BottomSheetHostingView,
    events: BottomSheetEventRecorder,
    offset: CGFloat,
    file: StaticString = #filePath,
    line: UInt = #line
  ) {
    XCTAssertEqual(events.changedIndices, [], file: file, line: line)
    XCTAssertEqual(events.settledIndices, [], file: file, line: line)
    XCTAssertNil(
      host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"),
      file: file,
      line: line
    )
    XCTAssertEqual(host.currentContentOffsetY, offset, accuracy: 0.5, file: file, line: line)
  }
}
