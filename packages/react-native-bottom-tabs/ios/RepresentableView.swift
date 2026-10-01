import SwiftUI

/**
 Helper used to render UIView inside of SwiftUI.
 Wraps each view with an additional wrapper to avoid directly managing React Native views.
 This solves issues where the layout would have weird artifacts..
 */
struct RepresentableView: PlatformViewRepresentable {
  var view: PlatformView

#if os(macOS)

  func makeNSView(context: Context) -> PlatformView {
    let wrapper = NSView()
    wrapper.addSubview(view)
    return wrapper
  }

  func updateNSView(_ nsView: PlatformView, context: Context) {}

#else

  func makeUIView(context: Context) -> PlatformView {
    let wrapper = UIView()
    Self.rehost(view, in: wrapper)
    return wrapper
  }

  func updateUIView(_ uiView: PlatformView, context: Context) {}

  /**
   Moves the React Native `view` into `wrapper`, detaching the view controllers
   it carries from the host they were attached to.

   A controller detached while a transition of its own is in flight never
   finishes that transition, and UIKit throws the next time anything interrupts
   it. So while one is in flight the move waits for it to complete, and is
   skipped if another wrapper has taken the view by then.
   */
  static func rehost(_ view: UIView, in wrapper: UIView) {
    if let coordinator = transitioningStaleController(in: view)?.transitionCoordinator {
      coordinator.animate(alongsideTransition: nil) { [weak view, weak wrapper] _ in
        guard let view, let wrapper, view.superview !== wrapper else { return }
        rehost(view, in: wrapper)
      }
      return
    }
    detachStaleViewControllers(in: view)
    wrapper.addSubview(view)
  }

  /// The first still-parented controller in `view` with a transition in flight.
  static func transitioningStaleController(in view: UIView) -> UIViewController? {
    for subview in view.subviews {
      if let controller = subview.next as? UIViewController, controller.view === subview {
        if controller.parent != nil, controller.transitionCoordinator != nil {
          return controller
        }
      } else if let controller = transitioningStaleController(in: subview) {
        return controller
      }
    }
    return nil
  }

  /**
   When a tab is hidden, SwiftUI drops it from the view tree and discards its
   `UIHostingController`, but view controllers that libraries such as
   react-native-screens attached to that host (e.g. `RNSNavigationController`)
   stay parented to it. Those libraries only attach when the controller has no
   parent, so re-adding the same React Native view under a fresh host trips
   UIKit's hierarchy consistency check and crashes. Detaching the stale
   children first lets them re-attach to the new host on their own.
   */
  static func detachStaleViewControllers(in view: UIView) {
    for subview in view.subviews {
      if let controller = subview.next as? UIViewController, controller.view === subview {
        if controller.parent != nil {
          controller.willMove(toParent: nil)
          controller.removeFromParent()
        }
      } else {
        detachStaleViewControllers(in: subview)
      }
    }
  }

#endif
}
