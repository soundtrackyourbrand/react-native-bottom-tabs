import Foundation
import React
import SwiftUI

@objcMembers
public final class TabInfo: NSObject {
  public let key: String
  public let title: String
  public let badge: String?
  public let sfSymbol: String
  public let focusedSfSymbol: String?
  public let activeTintColor: PlatformColor?
  public let iconRenderingMode: String?
  public let hidden: Bool
  public let testID: String?
  public let role: TabBarRole?
  public let preventsDefault: Bool

  public init(
    key: String,
    title: String,
    badge: String?,
    sfSymbol: String,
    focusedSfSymbol: String?,
    activeTintColor: PlatformColor?,
    iconRenderingMode: String?,
    hidden: Bool,
    testID: String?,
    role: String?,
    preventsDefault: Bool = false
  ) {
    self.key = key
    self.title = title
    self.badge = badge
    self.sfSymbol = sfSymbol
    self.focusedSfSymbol = focusedSfSymbol
    self.activeTintColor = activeTintColor
    self.iconRenderingMode = iconRenderingMode
    self.hidden = hidden
    self.testID = testID
    self.role = TabBarRole(rawValue: role ?? "")
    self.preventsDefault = preventsDefault
    super.init()
  }
}

@objc public protocol TabViewProviderDelegate {
  func onPageSelected(key: String, reactTag: NSNumber?)
  func onLongPress(key: String, reactTag: NSNumber?)
  func onTabBarMeasured(height: Int, reactTag: NSNumber?)
  func onLayout(size: CGSize, reactTag: NSNumber?)
}

@objc public class TabViewProvider: PlatformView {
  private var imageLoader: RCTImageLoaderProtocol?
  private weak var delegate: TabViewProviderDelegate?
  private var props = TabViewProps()
  private var hostingController: TabViewHostingController?
  private var appearanceFixScheduled = false
  private var coalescingKey: UInt16 = 0
  private var iconSize = CGSize(width: 27, height: 27)

  @objc var onPageSelected: RCTDirectEventBlock?

  @objc var onTabLongPress: RCTDirectEventBlock?
  @objc var onTabBarMeasured: RCTDirectEventBlock?
  @objc var onNativeLayout: RCTDirectEventBlock?

  @objc public var icons: NSArray? {
    didSet {
      loadIcons(icons, focused: false)
    }
  }

  @objc public var focusedIcons: NSArray? {
    didSet {
      loadIcons(focusedIcons, focused: true)
    }
  }

  @objc public var sidebarAdaptable: Bool = false {
    didSet {
      props.sidebarAdaptable = sidebarAdaptable
    }
  }

  @objc public var disablePageAnimations: Bool = false {
    didSet {
      props.disablePageAnimations = disablePageAnimations
    }
  }

  @objc public var labeled: Bool = false {
    didSet {
      props.labeled = labeled
    }
  }

  @objc public var selectedPage: NSString? {
    didSet {
      props.selectedPage = selectedPage as? String
    }
  }

  @objc public var hapticFeedbackEnabled: Bool = false {
    didSet {
      props.hapticFeedbackEnabled = hapticFeedbackEnabled
    }
  }

  @objc public var layoutDirection: NSString? {
    didSet {
      props.layoutDirection = layoutDirection as? String
    }
  }
  @objc public var scrollEdgeAppearance: NSString? {
    didSet {
      props.scrollEdgeAppearance = scrollEdgeAppearance as? String
    }
  }

  @objc public var minimizeBehavior: NSString? {
    didSet {
      props.minimizeBehavior = MinimizeBehavior(rawValue: minimizeBehavior as? String ?? "")
    }
  }

  @objc public var translucent: Bool = true {
    didSet {
      props.translucent = translucent
    }
  }

  @objc public var barTintColor: PlatformColor? {
    didSet {
      props.barTintColor = barTintColor
    }
  }

  @objc public var activeTintColor: PlatformColor? {
    didSet {
      props.activeTintColor = activeTintColor
    }
  }

  @objc public var inactiveTintColor: PlatformColor? {
    didSet {
      props.inactiveTintColor = inactiveTintColor
    }
  }

  @objc public var experimentalBakedTintColors: Bool = false {
    didSet {
      props.experimentalBakedTintColors = experimentalBakedTintColors
    }
  }

  @objc public var fontFamily: NSString? {
    didSet {
      props.fontFamily = fontFamily as? String
    }
  }

  @objc public var fontWeight: NSString? {
    didSet {
      props.fontWeight = fontWeight as? String
    }
  }

  @objc public var fontSize: NSNumber? {
    didSet {
      props.fontSize = fontSize as? Int
    }
  }

  @objc public var tabBarHidden: Bool = false {
    didSet {
      props.tabBarHidden = tabBarHidden
    }
  }

  @objc public var itemsData: [TabInfo] = [] {
    didSet {
      props.items = itemsData
    }
  }

  @objc public convenience init(delegate: TabViewProviderDelegate) {
    self.init()
    self.delegate = delegate
  }

  @objc public func setImageLoader(_ imageLoader: RCTImageLoader) {
    self.imageLoader = imageLoader
    loadIcons(icons, focused: false)
    loadIcons(focusedIcons, focused: true)
  }

  override public func didUpdateReactSubviews() {
    props.children = reactSubviews().map(IdentifiablePlatformView.init)
  }

#if os(macOS)
  override public func layout() {
    super.layout()
    setupView()
  }
#else
  override public func layoutSubviews() {
    super.layoutSubviews()
    setupView()
    ensureHostingControllerAppeared()
  }

  /**
   UIKit forwards appearance callbacks to a child controller added while its
   parent is on screen, but not to one added while the parent is still
   transitioning in, e.g. when the tab view mounts during a native-stack
   replace animation. The host then stays "disappeared" and its view keeps
   zero safe-area insets, so the tab bar and the tabs' navigation bars ignore
   the notch and home indicator. Runs the missing transition once the parent
   has settled.
   */
  private func ensureHostingControllerAppeared() {
    guard let host = hostingController, !host.hasAppeared, !appearanceFixScheduled, window != nil else {
      return
    }
    appearanceFixScheduled = true
    if let coordinator = host.parent?.transitionCoordinator {
      coordinator.animate(alongsideTransition: nil) { [weak self] _ in
        self?.appearanceFixScheduled = false
        self?.ensureHostingControllerAppeared()
      }
      return
    }
    // Deferred a run-loop turn so an appearance transition UIKit is about to
    // forward itself takes precedence over the manual one.
    DispatchQueue.main.async { [weak self] in
      guard let self, let host = self.hostingController else { return }
      self.appearanceFixScheduled = false
      guard !host.hasAppeared, self.window != nil, let parent = host.parent else { return }
      if parent.transitionCoordinator != nil {
        self.ensureHostingControllerAppeared()
        return
      }
      host.beginAppearanceTransition(true, animated: false)
      host.endAppearanceTransition()
    }
  }
#endif

  private func setupView() {
    if self.hostingController != nil {
      return
    }

    self.hostingController = TabViewHostingController(rootView: TabViewImpl(props: props) { key in
      self.delegate?.onPageSelected(key: key, reactTag: self.reactTag)
    } onLongPress: { key in
      self.delegate?.onLongPress(key: key, reactTag: self.reactTag)
    } onLayout: { size  in
      self.delegate?.onLayout(size: size, reactTag: self.reactTag)
    } onTabBarMeasured: { height in
      self.delegate?.onTabBarMeasured(height: height, reactTag: self.reactTag)
    })

    if let hostingController = self.hostingController, let parentViewController = reactViewController() {
      parentViewController.addChild(hostingController)
#if !os(macOS)
      hostingController.view.backgroundColor = .clear
#endif
      addSubview(hostingController.view)
      hostingController.view.translatesAutoresizingMaskIntoConstraints = false
      hostingController.view.pinEdges(to: self)
#if !os(macOS)
      hostingController.didMove(toParent: parentViewController)
#endif
    }
  }

  @objc(insertChild:atIndex:)
  public func insertChild(_ child: PlatformView, at index: Int) {
    guard index >= 0 && index <= props.children.count else {
      return
    }
    props.children.insert(IdentifiablePlatformView(child), at: index)
  }

  @objc(removeChildAtIndex:)
  public func removeChild(at index: Int) {
    guard index >= 0 && index < props.children.count else {
      return
    }
    props.children.remove(at: index)
  }

  private func loadIcons(_ icons: NSArray?, focused: Bool) {
    guard let imageLoader else { return }

    // TODO: Diff the arrays and update only changed items.
    // Now if the user passes `unfocusedIcon` we update every item.
    if let imageSources = icons as? [RCTImageSource?] {
      for (index, imageSource) in imageSources.enumerated() {
        guard let imageSource else { continue }
        // Images in the app bundle, including asset catalog images referenced by
        // name (`{ uri: 'tab_home' }`), are loaded right here so the first frame
        // shows them. The image loader only handles what has to be fetched or
        // decoded asynchronously.
        if let url = imageSource.request.url, url.isFileURL, let image = RCTImageFromLocalAssetURL(url) {
          setIcon(image, at: index, focused: focused)
          continue
        }
        imageLoader.loadImage(
          with: imageSource.request,
          size: imageSource.size,
          scale: imageSource.scale,
          clipped: true,
          resizeMode: RCTResizeMode.contain,
          progressBlock: { _, _ in },
          partialLoad: { _ in },
          completionBlock: { error, image in
            if error != nil {
              print("[TabView] Error loading image: \(error!.localizedDescription)")
              return
            }
            guard let image else { return }
            DispatchQueue.main.async { [weak self] in
              self?.setIcon(image, at: index, focused: focused)
            }
          })
      }
    }
  }

  private func setIcon(_ image: PlatformImage, at index: Int, focused: Bool) {
    var icon = image.resizeImageTo(size: iconSize)
    #if os(iOS)
      if props.experimentalBakedTintColors {
        icon = icon?.withRenderingMode(.alwaysTemplate)
      }
    #endif
    if focused {
      props.focusedIcons[index] = icon
    } else {
      props.icons[index] = icon
    }
    props.iconsRevision += 1
  }
}

#if os(macOS)
typealias TabViewHostingController = NSHostingController<TabViewImpl>
#else
/// Tracks appearance so `TabViewProvider` can detect a host UIKit never transitioned in.
final class TabViewHostingController: UIHostingController<TabViewImpl> {
  private(set) var hasAppeared = false

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    hasAppeared = true
  }

  override func viewDidDisappear(_ animated: Bool) {
    super.viewDidDisappear(animated)
    hasAppeared = false
  }
}
#endif
