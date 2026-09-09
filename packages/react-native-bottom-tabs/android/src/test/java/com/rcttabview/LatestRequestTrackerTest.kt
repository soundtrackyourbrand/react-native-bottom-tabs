package com.rcttabview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestRequestTrackerTest {
  private val tracker = LatestRequestTracker<Int>()

  @Test
  fun `the only request for a key is the latest`() {
    val id = tracker.begin(0)
    assertTrue(tracker.isLatest(0, id))
  }

  @Test
  fun `a newer request supersedes an older one for the same key`() {
    val first = tracker.begin(0)
    val second = tracker.begin(0)
    assertFalse(tracker.isLatest(0, first))
    assertTrue(tracker.isLatest(0, second))
  }

  @Test
  fun `requests for other keys do not supersede each other`() {
    val home = tracker.begin(0)
    val search = tracker.begin(1)
    assertTrue(tracker.isLatest(0, home))
    assertTrue(tracker.isLatest(1, search))
  }

  @Test
  fun `an id is never the latest for a key it was not issued for`() {
    val home = tracker.begin(0)
    assertFalse(tracker.isLatest(1, home))
  }
}
