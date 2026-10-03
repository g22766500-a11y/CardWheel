package com.example.cardwheel

import org.junit.Assert.*
import org.junit.Test

class PageHapticsTest {
    @Test fun onlyCompletedUserPageChangesProduceOneTick() {
        val haptics = PageHaptics()
        assertFalse(haptics.settle(1)) // Initial load / programmatic selection.
        haptics.start(1)
        assertFalse(haptics.settle(1)) // Cancelled / short swipe.
        haptics.start(1)
        assertTrue(haptics.settle(2))
        assertFalse(haptics.settle(2)) // Duplicate idle callback.
        haptics.start(2)
        assertTrue(haptics.settle(1)) // Backward swipe.
        haptics.start(-1)
        assertFalse(haptics.settle(1)) // Empty / loading state.
    }
}
