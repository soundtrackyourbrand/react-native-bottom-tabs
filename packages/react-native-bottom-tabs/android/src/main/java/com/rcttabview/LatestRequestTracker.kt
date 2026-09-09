package com.rcttabview

/**
 * Hands out increasing ids for requests made per key and tells whether an id is still the
 * newest one issued for its key, so work that finishes asynchronously and out of order can
 * apply only the result of the most recent request.
 */
class LatestRequestTracker<K> {
  private val latestIds = mutableMapOf<K, Int>()
  private var nextId = 0

  fun begin(key: K): Int {
    val id = ++nextId
    latestIds[key] = id
    return id
  }

  fun isLatest(key: K, id: Int): Boolean = latestIds[key] == id
}
