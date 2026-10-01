import UIKit
import XCTest

@testable import react_native_bottom_tabs

final class RepresentableViewTests: XCTestCase {
  /// A view controller whose view sits inside the React Native view and that is still
  /// parented to a host SwiftUI has discarded, as react-native-screens leaves behind.
  private func staleChild(in reactView: UIView) -> UIViewController {
    let discardedHost = UIViewController()
    let child = UIViewController()
    discardedHost.addChild(child)
    reactView.addSubview(child.view)
    child.didMove(toParent: discardedHost)
    return child
  }

  func testDetachesAControllerStillParentedToADiscardedHost() {
    let reactView = UIView()
    let child = staleChild(in: reactView)

    RepresentableView.detachStaleViewControllers(in: reactView)

    XCTAssertNil(child.parent)
    XCTAssertIdentical(child.view.superview, reactView, "the view stays in place for the new host to adopt")
  }

  func testDetachesControllersNestedDeeperInTheViewTree() {
    let reactView = UIView()
    let container = UIView()
    reactView.addSubview(container)
    let child = staleChild(in: container)

    RepresentableView.detachStaleViewControllers(in: reactView)

    XCTAssertNil(child.parent)
  }

  func testLeavesAControllerWithoutAParentAlone() {
    let reactView = UIView()
    let child = UIViewController()
    reactView.addSubview(child.view)

    RepresentableView.detachStaleViewControllers(in: reactView)

    XCTAssertNil(child.parent)
    XCTAssertIdentical(child.view.superview, reactView)
  }

  func testDoesNotDescendIntoAControllersOwnViewTree() {
    let reactView = UIView()
    let child = staleChild(in: reactView)
    let grandchildHost = UIViewController()
    let grandchild = UIViewController()
    grandchildHost.addChild(grandchild)
    child.view.addSubview(grandchild.view)
    grandchild.didMove(toParent: grandchildHost)

    RepresentableView.detachStaleViewControllers(in: reactView)

    XCTAssertNil(child.parent)
    XCTAssertIdentical(grandchild.parent, grandchildHost, "controllers below a detached one are its own business")
  }

  func testWaitsForANavigationTransitionInFlightBeforeReHosting() {
    let window = UIWindow(frame: UIScreen.main.bounds)
    let discardedHost = UIViewController()
    window.rootViewController = discardedHost
    window.isHidden = false
    let reactView = UIView()
    discardedHost.view.addSubview(reactView)
    let navigation = UINavigationController(rootViewController: UIViewController())
    discardedHost.addChild(navigation)
    reactView.addSubview(navigation.view)
    navigation.didMove(toParent: discardedHost)
    // A navigation controller animates a push only once it has appeared
    RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.1))
    navigation.pushViewController(UIViewController(), animated: true)
    XCTAssertNotNil(navigation.transitionCoordinator)
    let wrapper = UIView()

    RepresentableView.rehost(reactView, in: wrapper)

    XCTAssertIdentical(navigation.parent, discardedHost, "detaching mid-transition leaves the transition unfinished")
    XCTAssertNotIdentical(reactView.superview, wrapper)

    let rehosted = expectation(for: NSPredicate { _, _ in reactView.superview === wrapper }, evaluatedWith: nil)
    wait(for: [rehosted], timeout: 5)
    XCTAssertNil(navigation.parent)
    XCTAssertNil(navigation.transitionCoordinator)
    XCTAssertEqual(navigation.viewControllers.count, 2)
  }
}
