package com.shottimer.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Points [Dispatchers.Main] (what every ViewModel's viewModelScope actually runs on) at a
 * TestDispatcher for the duration of a test, so `runTest(rule.dispatcher) { ... }` can drive a
 * ViewModel's coroutines with virtual time via explicit runCurrent()/advanceUntilIdle() calls
 * instead of racing real background threads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
