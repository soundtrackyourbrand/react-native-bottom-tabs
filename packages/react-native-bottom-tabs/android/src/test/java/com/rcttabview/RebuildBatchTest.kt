package com.rcttabview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RebuildBatchTest {
  private val batch = RebuildBatch()

  @Test
  fun `rebuilds run at once outside a batch`() {
    assertTrue(batch.shouldRebuildNow())
  }

  @Test
  fun `rebuilds requested inside a batch are held back`() {
    batch.begin()
    assertFalse(batch.shouldRebuildNow())
    assertFalse(batch.shouldRebuildNow())
  }

  @Test
  fun `ending a batch releases one rebuild for any number held back`() {
    batch.begin()
    batch.shouldRebuildNow()
    batch.shouldRebuildNow()
    assertTrue(batch.end())
    assertFalse(batch.end())
  }

  @Test
  fun `ending a batch without requests releases nothing`() {
    batch.begin()
    assertFalse(batch.end())
  }

  @Test
  fun `rebuilds run at once again after a batch`() {
    batch.begin()
    batch.shouldRebuildNow()
    batch.end()
    assertTrue(batch.shouldRebuildNow())
  }
}
