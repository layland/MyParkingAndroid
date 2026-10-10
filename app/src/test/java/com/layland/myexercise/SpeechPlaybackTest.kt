package com.layland.myexercise

import org.junit.Assert.*
import org.junit.Test

class SpeechPlaybackTest {
    @Test fun everyUtteranceHasADifferentId() {
        val registry = SpeechPlaybackRegistry()
        val ids = (1..100).map { registry.register().id }
        assertEquals(ids.size, ids.toSet().size)
    }
    @Test fun completionOfOneUtteranceDoesNotCompleteAnother() {
        val registry = SpeechPlaybackRegistry()
        val first = registry.register()
        val second = registry.register()
        registry.started(first.id); registry.finish(first.id, true)
        assertTrue(first.started.isCompleted)
        assertTrue(first.finished.isCompleted)
        assertFalse(second.started.isCompleted)
        assertFalse(second.finished.isCompleted)
        registry.finish(first.id, false)
        assertFalse(second.finished.isCompleted)
    }
    @Test fun rejectionAndStopReleaseOutstandingRequests() {
        val registry = SpeechPlaybackRegistry()
        val rejected = registry.register()
        registry.finish(rejected.id, false)
        assertTrue(rejected.started.isCompleted)
        assertTrue(rejected.finished.isCompleted)
        val active = registry.register()
        registry.stopAll()
        assertTrue(active.finished.isCompleted)
        val next = registry.register()
        registry.finish(active.id, true)
        assertFalse(next.finished.isCompleted)
    }
}
