package com.rcttabview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabBarVisibilityTest {
  private val visibility = TabBarVisibility()

  @Test
  fun `is visible by default`() {
    assertTrue(visibility.isVisible)
  }

  @Test
  fun `tabBarHidden hides the bar`() {
    visibility.hidden = true
    assertFalse(visibility.isVisible)
  }

  @Test
  fun `keyboard alone does not hide the bar`() {
    visibility.keyboardVisible = true
    assertTrue(visibility.isVisible)
  }

  @Test
  fun `keyboard hides the bar when hideOnKeyboard is set`() {
    visibility.hideOnKeyboard = true
    visibility.keyboardVisible = true
    assertFalse(visibility.isVisible)
  }

  @Test
  fun `bar returns when the keyboard is dismissed`() {
    visibility.hideOnKeyboard = true
    visibility.keyboardVisible = true
    visibility.keyboardVisible = false
    assertTrue(visibility.isVisible)
  }

  @Test
  fun `disabling hideOnKeyboard restores the bar while the keyboard is shown`() {
    visibility.hideOnKeyboard = true
    visibility.keyboardVisible = true
    visibility.hideOnKeyboard = false
    assertTrue(visibility.isVisible)
  }

  @Test
  fun `tabBarHidden keeps the bar hidden after the keyboard is dismissed`() {
    visibility.hidden = true
    visibility.hideOnKeyboard = true
    visibility.keyboardVisible = true
    visibility.keyboardVisible = false
    assertFalse(visibility.isVisible)
  }
}
