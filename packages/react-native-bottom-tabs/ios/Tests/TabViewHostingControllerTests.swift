import SwiftUI
import XCTest

@testable import react_native_bottom_tabs

final class TabViewHostingControllerTests: XCTestCase {
  private func makeHost() -> TabViewHostingController {
    TabViewHostingController(rootView: TabViewImpl(
      props: TabViewProps(),
      onSelect: { _ in },
      onLongPress: { _ in },
      onLayout: { _ in },
      onTabBarMeasured: { _ in }
    ))
  }

  func testHasNotAppearedUntilAnAppearanceTransitionRuns() {
    let host = makeHost()

    XCTAssertFalse(host.hasAppeared)
  }

  func testRecordsAnAppearance() {
    let host = makeHost()

    host.beginAppearanceTransition(true, animated: false)
    host.endAppearanceTransition()

    XCTAssertTrue(host.hasAppeared)
  }

  func testForgetsTheAppearanceOnceItDisappears() {
    let host = makeHost()
    host.beginAppearanceTransition(true, animated: false)
    host.endAppearanceTransition()

    host.beginAppearanceTransition(false, animated: false)
    host.endAppearanceTransition()

    XCTAssertFalse(host.hasAppeared)
  }
}
