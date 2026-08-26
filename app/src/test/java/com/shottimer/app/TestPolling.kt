package com.shottimer.app

/**
 * ViewModels in this app fire off Room writes via `viewModelScope.launch { repository.x(...) }` -
 * fire-and-forget from the caller's perspective. Room's suspend DAO calls hop through Room's own
 * real background executor even when the launching coroutine runs on a test's virtual-time
 * dispatcher, so `advanceUntilIdle()` alone can return before that write has actually landed.
 * Polls with a real (non-virtual) short sleep for [predicate] to hold instead of asserting
 * immediately - bounded, so a genuine bug still fails the test rather than hanging it.
 */
fun <T> pollUntil(timeoutMs: Long = 2_000L, intervalMs: Long = 20L, predicate: (T) -> Boolean, probe: () -> T): T {
    val deadline = System.currentTimeMillis() + timeoutMs
    var last: T
    do {
        last = probe()
        if (predicate(last)) return last
        Thread.sleep(intervalMs)
    } while (System.currentTimeMillis() < deadline)
    return last
}
