package com.example.cardwheel

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Size depends only on available width and font settings, never the selected page. */
class WalletCardViewport @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val pagePadding = 16f * density
        val fontSpace = (resources.configuration.fontScale - 1f).coerceAtLeast(0f) * 110f * density
        val height = ((width - pagePadding).coerceAtLeast(0f) / 1.586f + pagePadding + fontSpace).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
}
