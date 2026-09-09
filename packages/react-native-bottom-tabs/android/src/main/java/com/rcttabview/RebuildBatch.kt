package com.rcttabview

/**
 * Holds back rebuilds requested while a batch of changes is applied, so a rebuild that
 * would otherwise run after every change runs once when the batch ends.
 */
class RebuildBatch {
  private var batching = false
  private var pending = false

  /** Whether a rebuild requested now should run. Otherwise it runs when the batch ends. */
  fun shouldRebuildNow(): Boolean {
    if (batching) {
      pending = true
      return false
    }
    return true
  }

  fun begin() {
    batching = true
  }

  /** Ends the batch. True when a rebuild was held back and has to run now. */
  fun end(): Boolean {
    batching = false
    val rebuild = pending
    pending = false
    return rebuild
  }
}
