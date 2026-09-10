package com.rcttabview

import android.app.Activity
import android.os.Looper
import com.facebook.react.uimanager.ThemedReactContext
import java.lang.ref.WeakReference

/**
 * Builds a tab bar for an Activity ahead of time, while the main thread is idle during startup,
 * so mounting the real one does not pay Material's inflation inside the frame that shows the
 * first screen. Call [prewarm] from the Activity, e.g. posted right after `onCreate`:
 *
 * ```kotlin
 * Handler(Looper.getMainLooper()).post { RCTTabViewPrewarm.prewarm(this) }
 * ```
 */
object RCTTabViewPrewarm {
  private var activity: WeakReference<Activity>? = null
  private var view: ReactBottomNavigationView? = null

  /** Call on the main thread; [itemCount] placeholder tabs are inflated up front. */
  @JvmStatic
  @JvmOverloads
  fun prewarm(activity: Activity, itemCount: Int = 5) {
    check(Looper.myLooper() == Looper.getMainLooper()) { "prewarm must run on the main thread" }
    val prebuilt = ReactBottomNavigationView(activity)
    prebuilt.prewarmItems(itemCount)
    this.activity = WeakReference(activity)
    view = prebuilt
  }

  /** The prebuilt view for [context]'s Activity, handed out at most once. */
  internal fun take(context: ThemedReactContext): ReactBottomNavigationView? {
    val prebuilt = view ?: return null
    view = null
    val owner = activity?.get()
    activity = null
    return if (owner != null && owner === context.currentActivity) prebuilt else null
  }
}
