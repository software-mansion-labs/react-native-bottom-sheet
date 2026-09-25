import ReactNativeBottomSheet
import UIKit
import XCTest

@MainActor
final class BottomSheetPresentationLifecycleTests: XCTestCase {
  func testOpeningClaimsOwnershipBeforeContentBecomesReachable() async throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal], initialIndices: [0])
    defer { fixture.tearDown() }
    let boundary = try XCTUnwrap(fixture.host.superview)
    let settle = expectation(description: "opening settles through the real spring")
    settle.assertForOverFulfill = true
    fixture.events.didSettleExpectation = settle

    XCTAssertFalse(boundary.accessibilityViewIsModal)
    XCTAssertEqual(fixture.host.sheetContainer.alpha, 0)

    fixture.setIndex(1, forPresentationAt: 0)

    XCTAssertTrue(boundary.accessibilityViewIsModal)
    XCTAssertEqual(fixture.host.sheetContainer.alpha, 0)
    XCTAssertNotNil(fixture.host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))

    await fulfillment(of: [settle], timeout: 2.0)

    XCTAssertEqual(fixture.events.settledIndices, [1])
    XCTAssertTrue(boundary.accessibilityViewIsModal)
    XCTAssertEqual(fixture.host.sheetContainer.alpha, 1)
  }

  func testClosingUpperRetainsCombinedOwnershipUntilSettle() async throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .portal])
    defer { fixture.tearDown() }
    let lowerHost = fixture.hosts[0]
    let upperHost = fixture.hosts[1]
    let lowerEvents = fixture.eventRecorders[0]
    let upperEvents = fixture.eventRecorders[1]
    let lowerBoundary = try XCTUnwrap(lowerHost.superview)
    let upperBoundary = try XCTUnwrap(upperHost.superview)
    let background = UIView()
    background.isAccessibilityElement = true
    background.accessibilityLabel = "application background"
    fixture.rootViewController.view.insertSubview(background, at: 0)
    let retainedDismiss = try XCTUnwrap(findDismiss(in: upperHost))
    let settle = expectation(description: "upper closes through the real spring")
    settle.assertForOverFulfill = true
    upperEvents.didSettleExpectation = settle
    upperEvents.observedPresentationBoundary = upperBoundary
    upperEvents.positionSamples.removeAll()

    XCTAssertFalse(lowerBoundary.accessibilityViewIsModal)
    XCTAssertTrue(upperBoundary.accessibilityViewIsModal)
    XCTAssertFalse(lowerBoundary.isDescendant(of: upperBoundary))
    XCTAssertFalse(background.isDescendant(of: upperBoundary))

    XCTAssertTrue(upperHost.accessibilityPerformEscape())

    XCTAssertNil(findDismiss(in: upperHost))
    XCTAssertFalse(retainedDismiss.accessibilityActivate())
    XCTAssertTrue(upperHost.accessibilityPerformEscape())
    XCTAssertTrue(lowerHost.accessibilityPerformEscape())
    XCTAssertEqual(upperEvents.changedIndices, [0])
    XCTAssertEqual(lowerEvents.changedIndices, [])
    XCTAssertTrue(upperBoundary.accessibilityViewIsModal)
    XCTAssertFalse(lowerBoundary.accessibilityViewIsModal)
    XCTAssertNotNil(upperHost.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))

    await fulfillment(of: [settle], timeout: 2.0)

    XCTAssertEqual(upperEvents.settledIndices, [0])
    XCTAssertEqual(lowerEvents.settledIndices, [])
    XCTAssertFalse(upperBoundary.accessibilityViewIsModal)
    XCTAssertTrue(lowerBoundary.accessibilityViewIsModal)
    XCTAssertGreaterThan(upperEvents.positionSamples.count, 1)
    XCTAssertTrue(
      upperEvents.positionSamples.allSatisfy { $0.isPresentationBoundaryModal == true }
    )
    XCTAssertTrue(upperHost.accessibilityPerformEscape())
    XCTAssertEqual(upperEvents.changedIndices, [0])
    XCTAssertEqual(lowerEvents.changedIndices, [])

    XCTAssertTrue(lowerHost.accessibilityPerformEscape())
    XCTAssertEqual(lowerEvents.changedIndices, [0])
    XCTAssertTrue(lowerBoundary.accessibilityViewIsModal)
  }

  func testClosingRetargetedOpenNeverReleasesOwnership() async throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal])
    defer { fixture.tearDown() }
    let boundary = try XCTUnwrap(fixture.host.superview)
    let settle = expectation(description: "retargeted opening settles through the real spring")
    settle.assertForOverFulfill = true
    fixture.events.didSettleExpectation = settle
    fixture.events.observedPresentationBoundary = boundary

    XCTAssertTrue(fixture.host.accessibilityPerformEscape())
    fixture.host.setDetentIndex(1)

    XCTAssertTrue(boundary.accessibilityViewIsModal)
    XCTAssertEqual(fixture.events.changedIndices, [0])

    await fulfillment(of: [settle], timeout: 2.0)

    XCTAssertEqual(fixture.events.settledIndices, [1])
    XCTAssertTrue(boundary.accessibilityViewIsModal)
    XCTAssertTrue(
      fixture.events.positionSamples.allSatisfy { $0.isPresentationBoundaryModal == true }
    )
  }

  func testActiveDetachTransfersOwnershipSynchronously() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .portal])
    defer { fixture.tearDown() }
    let lowerBoundary = try XCTUnwrap(fixture.hosts[0].superview)
    let upperBoundary = try XCTUnwrap(fixture.hosts[1].superview)

    fixture.detachComponent(at: 1)

    XCTAssertTrue(lowerBoundary.accessibilityViewIsModal)
    XCTAssertFalse(upperBoundary.accessibilityViewIsModal)
    XCTAssertFalse(fixture.hosts[1].accessibilityPerformEscape())
  }

  func testClosingDetachTransfersOwnershipBeforeSettle() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .portal])
    defer { fixture.tearDown() }
    let lowerBoundary = try XCTUnwrap(fixture.hosts[0].superview)
    let upperBoundary = try XCTUnwrap(fixture.hosts[1].superview)

    XCTAssertTrue(fixture.hosts[1].accessibilityPerformEscape())
    XCTAssertTrue(upperBoundary.accessibilityViewIsModal)

    fixture.detachComponent(at: 1)

    XCTAssertTrue(lowerBoundary.accessibilityViewIsModal)
    XCTAssertFalse(upperBoundary.accessibilityViewIsModal)
    XCTAssertFalse(fixture.hosts[1].accessibilityPerformEscape())
    XCTAssertEqual(fixture.eventRecorders[0].changedIndices, [])
  }

  func testRecyclingCurrentTopDestroysItsIdentityAndPromotesLower() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .portal])
    defer { fixture.tearDown() }
    let lowerBoundary = try XCTUnwrap(fixture.hosts[0].superview)
    let upperBoundary = try XCTUnwrap(fixture.hosts[1].superview)

    fixture.recycleComponent(at: 1)

    XCTAssertTrue(lowerBoundary.accessibilityViewIsModal)
    XCTAssertFalse(upperBoundary.accessibilityViewIsModal)
    XCTAssertNotNil(
      BottomSheetPresentationCoordinator.topPresentationIdentity(in: fixture.window)
    )
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
