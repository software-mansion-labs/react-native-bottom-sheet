import ReactNativeBottomSheet
import UIKit
import XCTest

@MainActor
final class BottomSheetPresentationModalIsolationTests: XCTestCase {
  // These manually created components have no completed Fabric source. A
  // portal must not turn that missing local boundary into window modality.
  func testUnmappedPortalDoesNotCreateWindowModalBoundary() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal])
    defer { fixture.tearDown() }

    let boundary = try XCTUnwrap(fixture.host.superview)

    XCTAssertFalse(boundary.accessibilityViewIsModal)
    XCTAssertFalse(boundary.accessibilityElementsHidden)
    XCTAssertTrue(fixture.host.isDescendant(of: boundary))
  }

  func testUnmappedPortalStackDoesNotCreateWindowModalBoundaries() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .portal])
    defer { fixture.tearDown() }

    let lowerBoundary = try XCTUnwrap(fixture.hosts[0].superview)
    let topBoundary = try XCTUnwrap(fixture.hosts[1].superview)

    XCTAssertFalse(lowerBoundary.accessibilityViewIsModal)
    XCTAssertFalse(topBoundary.accessibilityViewIsModal)
    XCTAssertTrue(fixture.hosts[1].isDescendant(of: topBoundary))
  }

  func testPortalAndNativeOverlayShareSheetBoundaryWithoutModalOverlayContainer() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .nativeOverlay])
    defer { fixture.tearDown() }

    let portalBoundary = try XCTUnwrap(fixture.hosts[0].superview)
    let overlayBoundary = try XCTUnwrap(fixture.hosts[1].superview)
    let overlayContainer = try XCTUnwrap(overlayBoundary.superview)

    XCTAssertFalse(portalBoundary.accessibilityViewIsModal)
    XCTAssertTrue(overlayBoundary.accessibilityViewIsModal)
    XCTAssertFalse(overlayContainer.accessibilityViewIsModal)
    XCTAssertTrue(fixture.hosts[1].isDescendant(of: overlayBoundary))
  }

  func testUnmappedSeparateNativeRootBranchesDoNotExpandPortalBoundaries() throws {
    let fixture = BottomSheetHostFixture(
      presentations: [.portal, .portal],
      separateNativeRootBranches: true
    )
    defer { fixture.tearDown() }

    let lowerBoundary = try XCTUnwrap(fixture.hosts[0].superview)
    let topBoundary = try XCTUnwrap(fixture.hosts[1].superview)
    let lowerBranch = try XCTUnwrap(lowerBoundary.superview)

    XCTAssertFalse(lowerBoundary.accessibilityViewIsModal)
    XCTAssertFalse(topBoundary.accessibilityViewIsModal)
    XCTAssertFalse(lowerBranch.accessibilityElementsHidden)
  }

  func testMultipleAttachedNativeOverlaysKeepOnlyNativeTopSheetBoundaryModal() throws {
    let fixture = BottomSheetHostFixture(presentations: [.nativeOverlay, .nativeOverlay])
    defer { fixture.tearDown() }

    let lowerBoundary = try XCTUnwrap(fixture.hosts[0].superview)
    let topBoundary = try XCTUnwrap(fixture.hosts[1].superview)
    let lowerContainer = try XCTUnwrap(lowerBoundary.superview)
    let topContainer = try XCTUnwrap(topBoundary.superview)

    XCTAssertFalse(lowerBoundary.accessibilityViewIsModal)
    XCTAssertTrue(topBoundary.accessibilityViewIsModal)
    XCTAssertFalse(lowerContainer.accessibilityViewIsModal)
    XCTAssertFalse(topContainer.accessibilityViewIsModal)
  }

  func testUnmappedPortalDoesNotBorrowModalityFromAnotherWindowOverlay() throws {
    let firstFixture = BottomSheetHostFixture(presentations: [.portal])
    let secondFixture = BottomSheetHostFixture(presentations: [.nativeOverlay])
    defer {
      secondFixture.tearDown()
      firstFixture.tearDown()
    }

    let firstBoundary = try XCTUnwrap(firstFixture.host.superview)
    let secondBoundary = try XCTUnwrap(secondFixture.host.superview)

    XCTAssertFalse(firstBoundary.accessibilityViewIsModal)
    XCTAssertTrue(secondBoundary.accessibilityViewIsModal)
  }

  func testOrderChangeDoesNotCreateBoundaryWithoutCurrentPortalSource() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .portal])
    defer { fixture.tearDown() }
    let firstBoundary = try XCTUnwrap(fixture.hosts[0].superview)
    let secondBoundary = try XCTUnwrap(fixture.hosts[1].superview)
    let firstBranch = try XCTUnwrap(firstBoundary.superview)
    let secondBranch = try XCTUnwrap(secondBoundary.superview)

    fixture.bringComponentToFrontDuringMount(at: 0)

    XCTAssertFalse(firstBoundary.accessibilityViewIsModal)
    XCTAssertFalse(secondBoundary.accessibilityViewIsModal)
    XCTAssertFalse(firstBranch.accessibilityElementsHidden)
    XCTAssertFalse(secondBranch.accessibilityElementsHidden)
  }

  func testRemovingUnmappedLowerPortalDoesNotCreateWindowBoundary() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .portal])
    defer { fixture.tearDown() }
    let lowerBoundary = try XCTUnwrap(fixture.hosts[0].superview)
    let topBoundary = try XCTUnwrap(fixture.hosts[1].superview)

    fixture.recycleComponent(at: 0)

    XCTAssertFalse(lowerBoundary.accessibilityViewIsModal)
    XCTAssertFalse(topBoundary.accessibilityViewIsModal)
  }

  func testNoTopRestoresLibraryBoundaryToBaselineFalse() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal])
    defer { fixture.tearDown() }
    let boundary = try XCTUnwrap(fixture.host.superview)

    fixture.recycleComponent(at: 0)

    XCTAssertFalse(boundary.accessibilityViewIsModal)
  }

  func testDynamicViewsAndLaterApplicationValuesRemainUntouched() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal, .portal])
    defer { fixture.tearDown() }
    let lowerBoundary = try XCTUnwrap(fixture.hosts[0].superview)
    let topBoundary = try XCTUnwrap(fixture.hosts[1].superview)
    let applicationBranch = UIView()
    applicationBranch.isAccessibilityElement = true
    applicationBranch.accessibilityLabel = "application sentinel"
    fixture.rootViewController.view.addSubview(applicationBranch)
    applicationBranch.accessibilityElementsHidden = true
    applicationBranch.accessibilityViewIsModal = true
    let dynamicTopContent = UIView()
    dynamicTopContent.isAccessibilityElement = true
    dynamicTopContent.accessibilityLabel = "dynamic top content"
    topBoundary.addSubview(dynamicTopContent)

    XCTAssertTrue(applicationBranch.accessibilityElementsHidden)
    XCTAssertTrue(applicationBranch.accessibilityViewIsModal)
    XCTAssertTrue(applicationBranch.isAccessibilityElement)
    XCTAssertFalse(dynamicTopContent.accessibilityElementsHidden)
    XCTAssertTrue(dynamicTopContent.isDescendant(of: topBoundary))
    XCTAssertFalse(lowerBoundary.accessibilityViewIsModal)
    XCTAssertFalse(topBoundary.accessibilityViewIsModal)

    dynamicTopContent.removeFromSuperview()
    XCTAssertFalse(topBoundary.accessibilityViewIsModal)
  }

  func testTransparentScrimDoesNotExpandUnmappedPortalBoundary() throws {
    let fixture = BottomSheetHostFixture(presentations: [.portal])
    defer { fixture.tearDown() }
    let boundary = try XCTUnwrap(fixture.host.superview)

    fixture.host.setScrimOpacities([0, 0])
    fixture.host.layoutIfNeeded()

    XCTAssertFalse(fixture.host.isModalAccessibilityActive)
    XCTAssertFalse(boundary.accessibilityViewIsModal)
  }
}
