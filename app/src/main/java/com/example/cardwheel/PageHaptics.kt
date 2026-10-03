package com.example.cardwheel

/** Initial selection, cancelled drags and repeated idle events stay silent. */
internal class PageHaptics {
    private var active = false
    private var startId = -1
    fun start(cardId: Int) {
        if (!active) { startId = cardId; active = true }
    }
    fun settle(cardId: Int): Boolean {
        val changed = active && startId != -1 && cardId != -1 && startId != cardId
        active = false
        return changed
    }
}
