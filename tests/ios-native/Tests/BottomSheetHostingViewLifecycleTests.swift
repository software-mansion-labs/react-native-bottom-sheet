import ReactNativeBottomSheet
import UIKit
import XCTest

@MainActor
final class BottomSheetHostingViewLifecycleTests: XCTestCase {
  func testScrollOnlyPanCompletionRetriesDeferredPresentationReconciliation() throws {
    let host = BottomSheetHostingView(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
    host.modal = true
    host.animateIn = false
    host.setDetents([
      ["value": 0.0, "kind": "points", "programmatic": false],
      ["value": 320.0, "kind": "points", "programmatic": false],
    ])
    host.setDetentIndex(1)
    let scrollView = UIScrollView(frame: CGRect(x: 0, y: 0, width: 390, height: 320))
    scrollView.contentSize = CGSize(width: 390, height: 1000)
    host.mountChildComponentView(scrollView, atIndex: 0)
    host.setNeedsLayout()
    host.layoutIfNeeded()
    defer { host.resetSheetState() }
    var stationaryNotifications = 0
    let onChange: @convention(block) (Bool) -> Void = { _ in
      if !(host.value(forKey: "isAccessibilityTransitionRunning") as! Bool) {
        stationaryNotifications += 1
      }
    }
    host.setValue(onChange, forKey: "presentationActiveDidChange")
    defer { host.setValue(nil, forKey: "presentationActiveDidChange") }

    for terminal in [UIGestureRecognizer.State.ended, .cancelled, .failed] {
      let pan = LifecyclePanGesture()
      pan.probeState = .began
      host.perform(NSSelectorFromString("handlePan:"), with: pan)
      XCTAssertTrue(try XCTUnwrap(host.value(forKey: "isAccessibilityTransitionRunning") as? Bool))
      pan.probeState = terminal
      let before = stationaryNotifications
      host.perform(NSSelectorFromString("handlePan:"), with: pan)
      XCTAssertFalse(try XCTUnwrap(host.value(forKey: "isAccessibilityTransitionRunning") as? Bool))
      XCTAssertEqual(stationaryNotifications, before + 1, "terminal pan state: \(terminal)")
    }
  }

  func testNonanimatedPortalWaitsForIsolationCommitBeforeBecomingAccessible() {
    let host = BottomSheetHostingView(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
    host.modal = true
    host.setValue(true, forKey: "defersAccessibilityPresentation")
    host.animateIn = false
    host.setDetents([
      ["value": 0.0, "kind": "points", "programmatic": false],
      ["value": 320.0, "kind": "points", "programmatic": false],
    ])
    host.setDetentIndex(1)
    host.setNeedsLayout()
    host.layoutIfNeeded()
    XCTAssertTrue(host.accessibilityElementsHidden)
    host.perform(NSSelectorFromString("commitAccessibilityPresentation"))
    XCTAssertFalse(host.accessibilityElementsHidden)
    host.resetSheetState()
    XCTAssertTrue(host.accessibilityElementsHidden)
  }

  func testPortalAccessibilityIsRevealedOnlyAfterSettleAndIsolationCommit() throws {
    let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
    let controller = UIViewController()
    window.rootViewController = controller
    window.isHidden = false
    let host = BottomSheetHostingView(frame: window.bounds)
    host.modal = true
    controller.view.addSubview(host)
    host.setValue(true, forKey: "defersAccessibilityPresentation")
    host.animateIn = false
    host.setDetents([
      ["value": 0.0, "kind": "points", "programmatic": false],
      ["value": 320.0, "kind": "points", "programmatic": false],
    ])
    host.setDetentIndex(0)
    host.setNeedsLayout()
    host.layoutIfNeeded()
    defer {
      host.resetSheetState()
      window.isHidden = true
    }

    host.setDetentIndex(1)
    XCTAssertNotNil(host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))
    try BottomSheetAccessibilityObservation.begin(
      forDismiss: XCTUnwrap(host.subviews.first { $0.accessibilityLabel == "Dismiss" }),
      voiceOverRunning: true,
      focused: false
    )
    host.perform(NSSelectorFromString("commitAccessibilityPresentation"))
    XCTAssertTrue(host.accessibilityElementsHidden)
    XCTAssertEqual(BottomSheetAccessibilityObservation.screenChangedNotificationCount(), 0)
    host.animationDidStop(CAAnimation(), finished: true)
    XCTAssertTrue(host.accessibilityElementsHidden, "new masks must precede revealing the portal")
    XCTAssertEqual(BottomSheetAccessibilityObservation.screenChangedNotificationCount(), 0)
    host.perform(NSSelectorFromString("commitAccessibilityPresentation"))
    XCTAssertFalse(host.accessibilityElementsHidden)
    XCTAssertEqual(
      BottomSheetAccessibilityObservation.screenChangedNotificationCount(),
      0,
      "the presentation authority notifies after the complete interface is committed"
    )
    host.perform(NSSelectorFromString("commitAccessibilityPresentation"))
    XCTAssertEqual(
      BottomSheetAccessibilityObservation.screenChangedNotificationCount(),
      0,
      "the host must not send a second notification independently of its authority"
    )
    XCTAssertEqual(BottomSheetAccessibilityObservation.layoutChangedNotificationCount(), 0)

    host.setDetentIndex(0)
    XCTAssertFalse(host.accessibilityElementsHidden, "closing keeps the current interface")
    host.animationDidStop(CAAnimation(), finished: true)
    XCTAssertTrue(host.accessibilityElementsHidden)
  }

  func testNativeOverlayNotifiesOnceAfterEachSettledInterfaceChange() throws {
    let fixture = BottomSheetHostFixture(presentations: [.nativeOverlay], initialIndices: [0])
    defer { fixture.tearDown() }
    let host = fixture.host
    BottomSheetAccessibilityObservation.begin(forDismiss: host, voiceOverRunning: true, focused: false)

    fixture.setIndex(1, forPresentationAt: 0)
    XCTAssertEqual(BottomSheetAccessibilityObservation.screenChangedNotificationCount(), 0)
    host.animationDidStop(CAAnimation(), finished: true)
    XCTAssertTrue(try XCTUnwrap(host.superview).accessibilityViewIsModal)
    XCTAssertEqual(BottomSheetAccessibilityObservation.screenChangedNotificationCount(), 1)

    fixture.setIndex(0, forPresentationAt: 0)
    XCTAssertTrue(try XCTUnwrap(host.superview).accessibilityViewIsModal)
    XCTAssertEqual(BottomSheetAccessibilityObservation.screenChangedNotificationCount(), 1)
    host.animationDidStop(CAAnimation(), finished: true)
    XCTAssertFalse(try XCTUnwrap(host.superview).accessibilityViewIsModal)
    XCTAssertEqual(BottomSheetAccessibilityObservation.screenChangedNotificationCount(), 2)
  }

  override func tearDown() {
    BottomSheetAccessibilityObservation.end()
    super.tearDown()
  }

  func testAnimatedOpenToOpenCommitsFreshFrameAndOneFocusedNotification() async throws {
    let fixture = BottomSheetHostFixture()
    defer { fixture.tearDown() }
    let host = fixture.host
    let events = fixture.events
    host.setDetents([
      ["value": 0.0, "kind": "points", "programmatic": false],
      ["value": 320.0, "kind": "points", "programmatic": false],
      ["value": 600.0, "kind": "points", "programmatic": false],
    ])
    let dismiss = try XCTUnwrap(findDismiss(in: host))
    BottomSheetAccessibilityObservation.begin(
      forDismiss: dismiss,
      voiceOverRunning: true,
      focused: true
    )
    XCTAssertTrue(UIAccessibility.isVoiceOverRunning)
    XCTAssertTrue(dismiss.accessibilityElementIsFocused())
    UIAccessibility.post(notification: .layoutChanged, argument: nil)
    XCTAssertEqual(BottomSheetAccessibilityObservation.layoutChangedNotificationCount(), 1)
    BottomSheetAccessibilityObservation.resetNotificationCount()
    let settle = expectation(description: "open-to-open spring settles")
    settle.assertForOverFulfill = true
    events.didSettleExpectation = settle
    events.positionSamples.removeAll()

    host.setDetentIndex(2)

    XCTAssertEqual(BottomSheetAccessibilityObservation.layoutChangedNotificationCount(), 0)
    XCTAssertNotNil(host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))

    await fulfillment(of: [settle], timeout: 2.0)

    assertDismissFrame(dismiss, in: host, sheetPosition: 600)
    XCTAssertEqual(BottomSheetAccessibilityObservation.layoutChangedNotificationCount(), 1)
    XCTAssertGreaterThan(events.positionSamples.count, 1)
    for sample in events.positionSamples {
      let sampledFrame = try XCTUnwrap(sample.dismissAccessibilityFrame)
      let expected = expectedDismissFrame(in: host, sheetPosition: sample.position)
      assertRect(sampledFrame, equals: expected)
    }

    host.setNeedsLayout()
    host.layoutIfNeeded()
    XCTAssertEqual(
      BottomSheetAccessibilityObservation.layoutChangedNotificationCount(),
      1,
      "repeated stable layout must not emit another notification"
    )
  }

  func testImmediateReanchorResizeAndInsetRefreshCommitOnlyChangedGeometry() throws {
    let fixture = BottomSheetHostFixture()
    defer { fixture.tearDown() }
    let host = fixture.host
    let content = UIView(frame: host.bounds)
    let contentHeightMarker = ContentHeightMarker(frame: CGRect(x: 0, y: 320, width: 1, height: 0))
    content.addSubview(contentHeightMarker)
    host.mountChildComponentView(content, atIndex: 0)
    host.animateContentHeight = false
    host.setDetents([
      ["value": 0.0, "kind": "points", "programmatic": false],
      ["value": 0.0, "kind": "content", "programmatic": false],
    ])
    host.contentRegionStateDidCommit(host.contentRegionInset)
    host.contentRegionLayoutDidApply()
    host.layoutIfNeeded()
    let dismiss = try XCTUnwrap(findDismiss(in: host))
    BottomSheetAccessibilityObservation.begin(
      forDismiss: dismiss,
      voiceOverRunning: true,
      focused: true
    )

    contentHeightMarker.frame.origin.y = 480

    XCTAssertNil(host.sheetContainer.layer.animation(forKey: "bottomSheetSettle"))
    assertDismissFrame(dismiss, in: host, sheetPosition: 480)
    XCTAssertEqual(BottomSheetAccessibilityObservation.layoutChangedNotificationCount(), 1)

    BottomSheetAccessibilityObservation.resetNotificationCount()
    host.frame = CGRect(x: 15, y: 30, width: 360, height: 700)
    host.setNeedsLayout()
    host.layoutIfNeeded()

    XCTAssertEqual(dismiss.frame, CGRect(x: 0, y: 0, width: 360, height: 220))
    assertDismissFrame(dismiss, in: host, sheetPosition: 480)
    XCTAssertEqual(BottomSheetAccessibilityObservation.layoutChangedNotificationCount(), 1)

    BottomSheetAccessibilityObservation.resetNotificationCount()
    let frameBeforeInsetRefresh = dismiss.accessibilityFrame
    host.extendUnderStatusBar.toggle()
    host.layoutIfNeeded()
    let frameAfterInsetRefresh = dismiss.accessibilityFrame

    assertDismissFrame(dismiss, in: host, sheetPosition: 480)
    XCTAssertEqual(
      BottomSheetAccessibilityObservation.layoutChangedNotificationCount(),
      frameBeforeInsetRefresh == frameAfterInsetRefresh ? 0 : 1
    )
  }

  func testInsetAndResizeCoalesceIntoOneFinalStableGeometryCommit() throws {
    let window = BottomSheetSafeAreaWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
    let rootViewController = UIViewController()
    let host = BottomSheetHostingView(frame: window.bounds)
    defer {
      host.removeFromSuperview()
      window.isHidden = true
      window.rootViewController = nil
    }
    window.overriddenSafeAreaInsets = UIEdgeInsets(top: 40, left: 0, bottom: 0, right: 0)
    window.rootViewController = rootViewController
    rootViewController.view.frame = window.bounds
    rootViewController.view.addSubview(host)
    let content = UIView(frame: host.bounds)
    content.addSubview(ContentHeightMarker(frame: CGRect(x: 0, y: 780, width: 1, height: 0)))
    host.mountChildComponentView(content, atIndex: 0)
    host.modal = true
    host.animateIn = false
    host.animateContentHeight = false
    host.setScrimOpacities([0, 1])
    host.setDetents([
      ["value": 0.0, "kind": "points", "programmatic": false],
      ["value": 0.0, "kind": "content", "programmatic": false],
    ])
    host.setDetentIndex(1)
    window.makeKeyAndVisible()
    host.setNeedsLayout()
    host.layoutIfNeeded()
    let dismiss = try XCTUnwrap(findDismiss(in: host))
    BottomSheetAccessibilityObservation.begin(
      forDismiss: dismiss,
      voiceOverRunning: true,
      focused: true
    )

    window.overriddenSafeAreaInsets = UIEdgeInsets(top: 80, left: 0, bottom: 0, right: 0)
    host.safeAreaInsetsDidChange()
    host.frame = CGRect(x: 15, y: 20, width: 360, height: 800)
    host.setNeedsLayout()
    host.layoutIfNeeded()

    assertDismissFrame(dismiss, in: host, sheetPosition: 740)
    XCTAssertEqual(
      BottomSheetAccessibilityObservation.layoutChangedNotificationCount(),
      1,
      "inset and bounds changes in one relayout must publish only the final stable frame"
    )
  }

  func testStableGeometryNotificationRequiresVoiceOverAndDismissFocus() async throws {
    let fixture = BottomSheetHostFixture()
    defer { fixture.tearDown() }
    let host = fixture.host
    let events = fixture.events
    host.setDetents([
      ["value": 0.0, "kind": "points", "programmatic": false],
      ["value": 320.0, "kind": "points", "programmatic": false],
      ["value": 600.0, "kind": "points", "programmatic": false],
    ])
    let dismiss = try XCTUnwrap(findDismiss(in: host))

    BottomSheetAccessibilityObservation.begin(
      forDismiss: dismiss,
      voiceOverRunning: false,
      focused: true
    )
    let voiceOverOffSettle = expectation(description: "VoiceOver-off transition settles")
    events.didSettleExpectation = voiceOverOffSettle
    host.setDetentIndex(2)
    await fulfillment(of: [voiceOverOffSettle], timeout: 2.0)
    XCTAssertEqual(BottomSheetAccessibilityObservation.layoutChangedNotificationCount(), 0)

    BottomSheetAccessibilityObservation.end()
    BottomSheetAccessibilityObservation.begin(
      forDismiss: dismiss,
      voiceOverRunning: true,
      focused: false
    )
    let unfocusedSettle = expectation(description: "unfocused transition settles")
    events.didSettleExpectation = unfocusedSettle
    host.setDetentIndex(1)
    await fulfillment(of: [unfocusedSettle], timeout: 2.0)
    XCTAssertEqual(BottomSheetAccessibilityObservation.layoutChangedNotificationCount(), 0)
  }

  func testClosingAndReopenNeverReuseOrNotifyStaleDismissGeometry() async throws {
    let fixture = BottomSheetHostFixture()
    defer { fixture.tearDown() }
    let host = fixture.host
    let events = fixture.events
    let retainedDismiss = try XCTUnwrap(findDismiss(in: host))
    BottomSheetAccessibilityObservation.begin(
      forDismiss: retainedDismiss,
      voiceOverRunning: true,
      focused: true
    )
    let closeSettle = expectation(description: "close settles")
    events.didSettleExpectation = closeSettle

    XCTAssertTrue(retainedDismiss.accessibilityActivate())
    XCTAssertNil(findDismiss(in: host))
    XCTAssertTrue(host.isModalAccessibilityActive)
    XCTAssertEqual(BottomSheetAccessibilityObservation.layoutChangedNotificationCount(), 0)

    await fulfillment(of: [closeSettle], timeout: 2.0)

    XCTAssertFalse(host.isModalAccessibilityActive)
    XCTAssertEqual(BottomSheetAccessibilityObservation.layoutChangedNotificationCount(), 0)

    let reopenSettle = expectation(description: "reopen settles")
    events.didSettleExpectation = reopenSettle
    host.setDetentIndex(1)
    await fulfillment(of: [reopenSettle], timeout: 2.0)

    let reopenedDismiss = try XCTUnwrap(findDismiss(in: host))
    XCTAssertTrue(reopenedDismiss === retainedDismiss)
    assertDismissFrame(reopenedDismiss, in: host, sheetPosition: 320)
    XCTAssertEqual(BottomSheetAccessibilityObservation.layoutChangedNotificationCount(), 0)
  }

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
      events.positionSamples.dropLast().allSatisfy(\.isModalScrimVisible),
      "modal scrim must remain visible for every nonterminal spring sample"
    )
    XCTAssertEqual(events.positionSamples.last?.position ?? .nan, 0, accuracy: 0.5)
    XCTAssertEqual(events.positionSamples.last?.isModalScrimVisible, false)
  }
}

private final class ContentHeightMarker: UIView {
  /// Match the nativeID exposed by Fabric's content-height marker.
  @objc var nativeId: String {
    "swm-bottom-sheet-content-height-marker"
  }
}

private final class BottomSheetSafeAreaWindow: UIWindow {
  var overriddenSafeAreaInsets = UIEdgeInsets.zero

  override var safeAreaInsets: UIEdgeInsets {
    overriddenSafeAreaInsets
  }
}

private final class LifecyclePanGesture: UIPanGestureRecognizer {
  var probeState: UIGestureRecognizer.State = .possible

  override var state: UIGestureRecognizer.State {
    get { probeState }
    set { probeState = newValue }
  }

  override func location(in _: UIView?) -> CGPoint {
    CGPoint(x: 20, y: 20)
  }

  override func velocity(in _: UIView?) -> CGPoint {
    CGPoint(x: 0, y: -100)
  }
}
