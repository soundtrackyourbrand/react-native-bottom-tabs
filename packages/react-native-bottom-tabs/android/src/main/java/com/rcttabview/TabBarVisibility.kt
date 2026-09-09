package com.rcttabview

/**
 * Combines the reasons the tab bar can be hidden: the `tabBarHidden` prop and, when
 * `tabBarHideOnKeyboard` is set, a visible keyboard.
 */
class TabBarVisibility {
  var hidden = false
  var hideOnKeyboard = false
  var keyboardVisible = false

  val isVisible: Boolean
    get() = !hidden && !(hideOnKeyboard && keyboardVisible)
}
