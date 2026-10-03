package com.example.cardwheel

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

/** Decide the scroll axis before the vertical parent can cancel a card swipe. */
class WalletScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : NestedScrollView(context, attrs) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var startsInPager = false
    private var axis = 0 // 0 pending, 1 horizontal, 2 vertical

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x; startY = event.y; axis = 0
                val pager = findViewById<View>(R.id.viewPager)
                val location = IntArray(2)
                pager?.getLocationOnScreen(location)
                startsInPager = pager != null && pager.isShown &&
                    event.rawX >= location[0] && event.rawX < location[0] + pager.width &&
                    event.rawY >= location[1] && event.rawY < location[1] + pager.height
                val intercepted = super.onInterceptTouchEvent(event)
                return if (startsInPager) false else intercepted
            }
            MotionEvent.ACTION_MOVE -> if (startsInPager) {
                if (axis == 0) {
                    val dx = abs(event.x - startX)
                    val dy = abs(event.y - startY)
                    if (maxOf(dx, dy) > slop) axis = if (dx >= dy) 1 else 2
                }
                if (axis != 2) return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                startsInPager = false; axis = 0
            }
        }
        return super.onInterceptTouchEvent(event)
    }
}
