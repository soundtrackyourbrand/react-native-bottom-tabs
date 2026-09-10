package com.rcttabview

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.transition.TransitionManager
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.MenuItemCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.forEachIndexed
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import coil3.size.Precision
import coil3.size.Size as CoilSize
import coil3.size.Scale
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.common.assets.ReactFontManager
import com.facebook.react.modules.core.ReactChoreographer
import com.facebook.react.views.text.ReactTypefaceUtils
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarMenuView
import com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_AUTO
import com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
import com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_UNLABELED
import com.google.android.material.transition.platform.MaterialFadeThrough
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Material rebuilds every item view each time the menu changes, so adding the tabs one by one
 * inflates 1 + 2 + … + n item views. This menu view holds the rebuild while a batch of changes
 * is applied and runs it once afterwards.
 */
@SuppressLint("RestrictedApi")
class BatchingBottomNavigationMenuView(context: Context) : BottomNavigationMenuView(context) {
  private val rebuilds = RebuildBatch()

  override fun buildMenuView() {
    if (rebuilds.shouldRebuildNow()) {
      super.buildMenuView()
    }
  }

  fun beginBatch() {
    rebuilds.begin()
  }

  fun endBatch() {
    if (rebuilds.end()) {
      buildMenuView()
    }
  }
}

class ExtendedBottomNavigationView(context: Context) : BottomNavigationView(context) {
  @SuppressLint("RestrictedApi")
  override fun createNavigationBarMenuView(context: Context): NavigationBarMenuView {
    return BatchingBottomNavigationMenuView(context)
  }

  /** Applies [block]'s menu changes with a single rebuild of the item views. */
  @SuppressLint("RestrictedApi")
  fun batchMenuChanges(block: () -> Unit) {
    val menuView = menuView as? BatchingBottomNavigationMenuView
    menuView?.beginBatch()
    try {
      block()
    } finally {
      menuView?.endBatch()
    }
  }

  override fun getMaxItemCount(): Int {
    return 100
  }
}

class ReactBottomNavigationView(context: Context) : LinearLayout(context) {
  private var bottomNavigation = ExtendedBottomNavigationView(context)
  private val tabBarVisibility = TabBarVisibility()
  val layoutHolder = FrameLayout(context)

  var onTabSelectedListener: ((key: String) -> Unit)? = null
  var onTabLongPressedListener: ((key: String) -> Unit)? = null
  var onNativeLayoutListener: ((width: Double, height: Double) -> Unit)? = null
  var onTabBarMeasuredListener: ((height: Int) -> Unit)? = null
  var disablePageAnimations = false
  var items: MutableList<TabInfo> = mutableListOf()
  private val iconSources: MutableMap<Int, ImageSource> = mutableMapOf()
  // Icon loads finish asynchronously and can complete out of order, so a menu item only takes
  // the result of the newest load started for it.
  private val iconLoads = LatestRequestTracker<Int>()
  private val drawableCache: MutableMap<ImageSource, Drawable> = mutableMapOf()

  private var isLayoutEnqueued = false
  private var selectedItem: String? = null
  private var activeTintColor: Int? = null
  private var inactiveTintColor: Int? = null
  private val checkedStateSet = intArrayOf(android.R.attr.state_checked)
  private val uncheckedStateSet = intArrayOf(-android.R.attr.state_checked)
  private var hapticFeedbackEnabled = false
  private var fontSize: Int? = null
  private var fontFamily: String? = null
  private var fontWeight: Int? = null
  private var labeled: Boolean? = null
  private var lastReportedSize: Size? = null
  private var hasCustomAppearance = false
  private var uiModeConfiguration: Int = Configuration.UI_MODE_NIGHT_UNDEFINED

  private companion object {
    /**
     * Icons are decoded off the main thread. The Coil loader is shared by all tab bars and
     * built on this thread the first time an icon is requested, so creating a tab bar costs
     * neither Coil's setup nor its class loading.
     */
    private val iconExecutor: ExecutorService =
      Executors.newSingleThreadExecutor { Thread(it, "rcttabview-icons") }

    private var sharedImageLoader: ImageLoader? = null

    /** Only called on [iconExecutor]. */
    private fun imageLoader(context: Context): ImageLoader =
      sharedImageLoader ?: ImageLoader.Builder(context.applicationContext)
        .components {
          add(SvgDecoder.Factory())
        }
        .build()
        .also { sharedImageLoader = it }
  }

  init {
    orientation = VERTICAL

    addView(
      layoutHolder, LayoutParams(
        LayoutParams.MATCH_PARENT,
        0,
      ).apply { weight = 1f }
    )
    layoutHolder.isSaveEnabled = false

    addView(bottomNavigation, LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.WRAP_CONTENT
    ))
    uiModeConfiguration = resources.configuration.uiMode

    post {
      addOnLayoutChangeListener { _, left, top, right, bottom,
                                  _, _, _, _ ->
        val newWidth = right - left
        val newHeight = bottom - top

        // Notify about tab bar height.
        onTabBarMeasuredListener?.invoke(Utils.convertPixelsToDp(context, bottomNavigation.height).toInt())

        if (newWidth != lastReportedSize?.width || newHeight != lastReportedSize?.height) {
          val dpWidth = Utils.convertPixelsToDp(context, layoutHolder.width)
          val dpHeight = Utils.convertPixelsToDp(context, layoutHolder.height)

          onNativeLayoutListener?.invoke(dpWidth, dpHeight)
          lastReportedSize = Size(newWidth, newHeight)
        }
      }
    }
  }

  private val layoutCallback = Choreographer.FrameCallback {
    isLayoutEnqueued = false
    refreshLayout()
  }

  private fun refreshLayout() {
    measure(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
    )
    layout(left, top, right, bottom)
  }

  fun applyDirection(dir: Int) {
      bottomNavigation.layoutDirection = dir   
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    // With `adjustResize` the keyboard shrinks this view through a layout commit, so the bar's
    // visibility is decided in that same measure pass. Hiding it from JS reaches the view at least
    // a frame later, and in between the bar renders lifted above the keyboard.
    if (tabBarVisibility.hideOnKeyboard) {
      tabBarVisibility.keyboardVisible =
        ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true
      applyTabBarVisibility()
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
  }

  override fun requestLayout() {
    super.requestLayout()
    @Suppress("SENSELESS_COMPARISON") // layoutCallback can be null here since this method can be called in init

    if (!isLayoutEnqueued && layoutCallback != null) {
      isLayoutEnqueued = true
      // we use NATIVE_ANIMATED_MODULE choreographer queue because it allows us to catch the current
      // looper loop instead of enqueueing the update in the next loop causing a one frame delay.
      ReactChoreographer
        .getInstance()
        .postFrameCallback(
          ReactChoreographer.CallbackType.NATIVE_ANIMATED_MODULE,
          layoutCallback,
        )
    }
  }

  fun setSelectedItem(value: String) {
    selectedItem = value
    setSelectedIndex(items.indexOfFirst { it.key == value })
  }

  override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
    if (child === layoutHolder || child === bottomNavigation) {
      super.addView(child, index, params)
      return
    }

    val container = createContainer()
    container.addView(child, params)
    layoutHolder.addView(container, index)

    val itemKey = items[index].key
    if (selectedItem == itemKey) {
      setSelectedIndex(index)
      refreshLayout()
    }
  }

  private fun createContainer(): FrameLayout {
    val container = FrameLayout(context).apply {
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
      isSaveEnabled = false
      visibility = GONE
      isEnabled = false
    }
    return container
  }

  private fun setSelectedIndex(itemId: Int) {
    bottomNavigation.selectedItemId = itemId
    if (!disablePageAnimations) {
      val fadeThrough = MaterialFadeThrough()
      TransitionManager.beginDelayedTransition(layoutHolder, fadeThrough)
    }

    layoutHolder.forEachIndexed { index, view ->
      if (itemId == index) {
        toggleViewVisibility(view, true)
      } else {
        toggleViewVisibility(view, false)
      }
    }

    layoutHolder.requestLayout()
    layoutHolder.invalidate()
  }

  private fun toggleViewVisibility(view: View, isVisible: Boolean) {
    check(view is ViewGroup) { "Native component tree is corrupted." }

    view.visibility = if (isVisible) VISIBLE else GONE
    view.isEnabled = isVisible
  }

  private fun onTabSelected(item: MenuItem) {
    val selectedItem = items[item.itemId]
    selectedItem.let {
      onTabSelectedListener?.invoke(selectedItem.key)
      emitHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
  }

  private fun onTabLongPressed(item: MenuItem) {
    val longPressedItem = items[item.itemId]
    longPressedItem.let {
      onTabLongPressedListener?.invoke(longPressedItem.key)
      emitHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
  }

  fun setTabBarHidden(isHidden: Boolean) {
    tabBarVisibility.hidden = isHidden
    applyTabBarVisibility()
  }

  fun setTabBarHideOnKeyboard(value: Boolean) {
    tabBarVisibility.hideOnKeyboard = value
    applyTabBarVisibility()
  }

  private fun applyTabBarVisibility() {
    bottomNavigation.visibility = if (tabBarVisibility.isVisible) VISIBLE else GONE
  }

  fun updateItems(items: MutableList<TabInfo>) {
    val removedItems = items.size < this.items.size
    this.items = items
    bottomNavigation.batchMenuChanges {
      // If an item got removed, let's re-add all items
      if (removedItems) {
        bottomNavigation.menu.clear()
      }
      items.forEachIndexed { index, item ->
        applyItem(index, item)
      }
      // Drop items beyond the current tabs: placeholders from a prewarm, or tabs that were removed
      val menu = bottomNavigation.menu
      for (i in menu.size() - 1 downTo items.size) {
        menu.removeItem(menu.getItem(i).itemId)
      }
    }
    // The item views exist once the batch has been applied. Styling them now, rather than in a
    // post, means the first frame already shows the final appearance instead of Material's
    // defaults for a frame.
    updateTextAppearance()
    updateTintColors()
  }

  private fun applyItem(index: Int, item: TabInfo) {
    val menuItem = getOrCreateItem(index, item.title)
    if (item.title !== menuItem.title) {
      menuItem.title = item.title
    }

    menuItem.isVisible = !item.hidden
    updateIconTintMode(menuItem, item)
    iconSources[index]?.let { loadMenuItemIcon(index, menuItem, it) }

    if (item.badge?.isNotEmpty() == true) {
      val badge = bottomNavigation.getOrCreateBadge(index)
      badge.isVisible = true
      // Set the badge text only if it's different than an empty space to show a small badge.
      // More context: https://github.com/callstackincubator/react-native-bottom-tabs/issues/422
      if (item.badge != " ") {
        badge.text = item.badge
      }
      // Apply badge colors if provided (Material will use its default theme colors otherwise)
      item.badgeBackgroundColor?.let { badge.backgroundColor = it }
      item.badgeTextColor?.let { badge.badgeTextColor = it }
    } else {
      bottomNavigation.removeBadge(index)
    }
    post {
      val itemView = bottomNavigation.findViewById<View>(menuItem.itemId)
      itemView?.let { view ->
        view.setOnLongClickListener {
          onTabLongPressed(menuItem)
          true
        }
        view.setOnClickListener {
          onTabSelected(menuItem)
        }

        view.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_content_container)
          ?.setTabTestID(item.testID)
      }
    }
  }

  private fun getOrCreateItem(index: Int, title: String): MenuItem {
    return bottomNavigation.menu.findItem(index) ?: bottomNavigation.menu.add(0, index, 0, title)
  }

  private fun View.setTabTestID(testId: String?) {
    tag = testId
    if (testId == null) {
      ViewCompat.setAccessibilityDelegate(this, null)
      return
    }

    ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
      override fun onInitializeAccessibilityNodeInfo(
        host: View,
        info: AccessibilityNodeInfoCompat
      ) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.viewIdResourceName = testId
      }
    })
  }

  private fun updateIconTintMode(menuItem: MenuItem, item: TabInfo) {
    MenuItemCompat.setIconTintMode(
      menuItem,
      if (item.iconRenderingMode == "original") PorterDuff.Mode.DST else null
    )
  }

  fun setIcons(icons: ReadableArray?) {
    if (icons == null || icons.size() == 0) {
      return
    }

    for (idx in 0 until icons.size()) {
      val source = icons.getMap(idx)
      val uri = source?.getString("uri")
      if (uri.isNullOrEmpty()) {
        continue
      }

      val imageSource = ImageSource(context, uri)
      this.iconSources[idx] = imageSource

      // Update existing item if exists.
      bottomNavigation.menu.findItem(idx)?.let { menuItem ->
        loadMenuItemIcon(idx, menuItem, imageSource)
      }
    }
  }

  fun setLabeled(labeled: Boolean?) {
    this.labeled = labeled
    bottomNavigation.labelVisibilityMode = when (labeled) {
      false -> {
        LABEL_VISIBILITY_UNLABELED
      }
      true -> {
        LABEL_VISIBILITY_LABELED
      }
      else -> {
        LABEL_VISIBILITY_AUTO
      }
    }
  }

  fun setRippleColor(color: ColorStateList) {
    bottomNavigation.itemRippleColor = color
  }

  private fun loadMenuItemIcon(index: Int, menuItem: MenuItem, imageSource: ImageSource) {
    val loadId = iconLoads.begin(index)
    getDrawable(imageSource) { drawable ->
      if (!iconLoads.isLatest(index, loadId)) {
        return@getDrawable
      }
      menuItem.icon = drawable
      items.getOrNull(index)?.let { updateIconTintMode(menuItem, it) }
    }
    if (menuItem.icon == null) {
      // The icon is arriving asynchronously. Material lays the label out differently for an item
      // without an icon, so reserve the icon's space to keep the label from moving once it lands.
      menuItem.icon = placeholderIcon()
    }
  }

  /**
   * Inflates [count] placeholder items so that a later [updateItems] only has to fill in titles
   * and icons instead of building the item views.
   */
  fun prewarmItems(count: Int) {
    bottomNavigation.batchMenuChanges {
      for (index in 0 until count) {
        bottomNavigation.menu.add(0, index, 0, "").icon = placeholderIcon()
      }
    }
  }

  private fun placeholderIcon(): Drawable {
    val size = bottomNavigation.itemIconSize
    return GradientDrawable().apply {
      setColor(Color.TRANSPARENT)
      setSize(size, size)
    }
  }

  /**
   * An XML drawable resource referenced by name (`{ uri: 'tab_home' }`), such as a vector
   * drawable, is inflated right here instead of going through Coil: it inflates in well under a
   * millisecond, keeps Material's tint, and being in the first frame avoids the labels moving once
   * icons arrive asynchronously. Bitmap drawables and SVGs in `res/raw` stay with Coil, which
   * decodes them off the main thread.
   */
  private fun inflateXmlDrawable(imageSource: ImageSource): Drawable? {
    val uri = imageSource.getUri(context) ?: return null
    if (uri.scheme != ContentResolver.SCHEME_ANDROID_RESOURCE) {
      return null
    }
    val resId = uri.lastPathSegment?.toIntOrNull() ?: return null
    val value = TypedValue()
    try {
      context.resources.getValue(resId, value, true)
    } catch (e: Resources.NotFoundException) {
      return null
    }
    if (value.string?.endsWith(".xml") != true) {
      return null
    }
    return ResourcesCompat.getDrawable(context.resources, resId, context.theme)?.mutate()
  }

  @SuppressLint("CheckResult")
  private fun getDrawable(imageSource: ImageSource, onDrawableReady: (Drawable?) -> Unit) {
    drawableCache[imageSource]?.let {
      onDrawableReady(it)
      return
    }
    inflateXmlDrawable(imageSource)?.let {
      drawableCache[imageSource] = it
      onDrawableReady(it)
      return
    }
    val iconSizePx = bottomNavigation.itemIconSize
    val request = ImageRequest.Builder(context)
      .data(imageSource.getUri(context))
      .size(CoilSize(iconSizePx, iconSizePx))
      .scale(Scale.FILL)
      .precision(Precision.EXACT)
      .target { drawable ->
        post {
          val stateDrawable = drawable.asDrawable(context.resources)
          drawableCache[imageSource] = stateDrawable
          onDrawableReady(stateDrawable)
        }
      }
      .listener(
        onError = { _, result ->
          Log.e("RCTTabView", "Error loading image: ${imageSource.uri}", result.throwable)
        }
      )
      .build()

    iconExecutor.execute {
      imageLoader(context).enqueue(request)
    }
  }

  fun setBarTintColor(color: Int?) {
    // Set the color, either using the active background color or a default color.
    val backgroundColor =
      color ?: Utils.getDefaultColorFor(context, android.R.attr.colorPrimary) ?: return

    // Apply the same color to both active and inactive states
    val colorDrawable = ColorDrawable(backgroundColor)

    bottomNavigation.itemBackground = colorDrawable
    bottomNavigation.backgroundTintList = ColorStateList.valueOf(backgroundColor)
    hasCustomAppearance = true
  }

  fun setActiveTintColor(color: Int?) {
    activeTintColor = color
    updateTintColors()
  }

  fun setInactiveTintColor(color: Int?) {
    inactiveTintColor = color
    updateTintColors()
  }

  fun setActiveIndicatorColor(color: ColorStateList) {
    bottomNavigation.itemActiveIndicatorColor = color
  }

  fun setFontSize(size: Int) {
    fontSize = size
    updateTextAppearance()
  }

  fun setFontFamily(family: String?) {
    fontFamily = family
    updateTextAppearance()
  }

  fun setFontWeight(weight: String?) {
    val fontWeight = ReactTypefaceUtils.parseFontWeight(weight)
    this.fontWeight = fontWeight
    updateTextAppearance()
  }

  private fun updateTextAppearance() {
    // Early return if there is no custom text appearance
    if (fontSize == null && fontFamily == null && fontWeight == null) {
      return
    }

    val typeface = if (fontFamily != null || fontWeight != null) {
      ReactFontManager.getInstance().getTypeface(
        fontFamily ?: "",
        Utils.getTypefaceStyle(fontWeight),
        context.assets
      )
    } else null
    val size = fontSize?.toFloat()?.takeIf { it > 0 }

    val menuView = bottomNavigation.getChildAt(0) as? ViewGroup ?: return
    for (i in 0 until menuView.childCount) {
      val item = menuView.getChildAt(i)
      val largeLabel =
        item.findViewById<TextView>(com.google.android.material.R.id.navigation_bar_item_large_label_view)
      val smallLabel =
        item.findViewById<TextView>(com.google.android.material.R.id.navigation_bar_item_small_label_view)

      listOf(largeLabel, smallLabel).forEach { label ->
        label?.apply {
          size?.let { size ->
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
          }
          typeface?.let { setTypeface(it) }
        }
      }
    }
  }

  private fun emitHapticFeedback(feedbackConstants: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hapticFeedbackEnabled) {
      this.performHapticFeedback(feedbackConstants)
    }
  }

  private fun updateTintColors() {
    // First let's check current item color.
    val currentItemTintColor = items.firstOrNull { it.key == selectedItem }?.activeTintColor

    // getDefaultColor will always return a valid color but to satisfy the compiler we need to check for null
    val colorPrimary = currentItemTintColor ?: activeTintColor ?: Utils.getDefaultColorFor(
      context,
      android.R.attr.colorPrimary
    ) ?: return
    val colorSecondary =
      inactiveTintColor ?: Utils.getDefaultColorFor(context, android.R.attr.textColorSecondary)
      ?: return
    val states = arrayOf(uncheckedStateSet, checkedStateSet)
    val colors = intArrayOf(colorSecondary, colorPrimary)

    ColorStateList(states, colors).apply {
      this@ReactBottomNavigationView.bottomNavigation.itemTextColor = this
      this@ReactBottomNavigationView.bottomNavigation.itemIconTintList = this
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration?) {
    super.onConfigurationChanged(newConfig)
    if (uiModeConfiguration == newConfig?.uiMode || hasCustomAppearance) {
      return
    }

    // User has hidden the bottom navigation bar, don't re-attach it.
    if (bottomNavigation.visibility == GONE) {
      return
    }

    // If appearance wasn't changed re-create the bottom navigation view when configuration changes.
    // React Native opts out ouf Activity re-creation when configuration changes, this workarounds that.
    // We also opt-out of this recreation when custom styles are used.
    removeView(bottomNavigation)
    bottomNavigation = ExtendedBottomNavigationView(context)
    addView(bottomNavigation)
    updateItems(items)
    setLabeled(this.labeled)
    this.selectedItem?.let { setSelectedItem(it) }
    uiModeConfiguration = newConfig?.uiMode ?: uiModeConfiguration
  }
}
