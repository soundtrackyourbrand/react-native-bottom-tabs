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
    Self.detachStaleViewControllers(in: view)
    wrapper.addSubview(view)
    return wrapper
  }

  func updateUIView(_ uiView: PlatformView, context: Context) {}

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
