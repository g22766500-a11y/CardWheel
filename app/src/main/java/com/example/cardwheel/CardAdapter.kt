package com.example.cardwheel

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.progressindicator.LinearProgressIndicator

class CardAdapter(private val onClick: (CardItem) -> Unit) : RecyclerView.Adapter<CardAdapter.Holder>() {
    private var items: List<CardItem> = emptyList()
    init { setHasStableIds(true) }
    override fun getItemId(position: Int) = items[position].id.toLong()
    fun submitItems(next: List<CardItem>) {
        val old = items
        val copy = next.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = copy.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = old[oldPosition].id == copy[newPosition].id
            override fun areContentsTheSame(oldPosition: Int, newPosition: Int) = old[oldPosition] == copy[newPosition]
        })
        items = copy
        diff.dispatchUpdatesTo(this)
    }
    class Holder(view: View) : RecyclerView.ViewHolder(view)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = items[position]
        with(holder.itemView) {
            val palette = IssuerCatalog.palette(card.company)
            val content = findViewById<View>(R.id.cardContent)
            content.alpha = if (card.cancelled) 0.88f else 1f
            findViewById<View>(R.id.cardChip).alpha = if (card.cancelled) 0.42f else 1f
            content.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, if (card.cancelled) intArrayOf(Color.parseColor("#475569"), Color.parseColor("#334155")) else intArrayOf(Color.parseColor(palette.start), Color.parseColor(palette.end))
            ).apply { cornerRadius = 16f * resources.displayMetrics.density }
            val foreground = Color.parseColor(if (palette.light && !card.cancelled) "#322A16" else "#FFFFFF")
            val secondary = Color.parseColor(if (card.cancelled) "#F1F5F9" else if (palette.light) "#514322" else "#E2E8F6")
            listOf(R.id.tvCardName, R.id.tvProgress).forEach { findViewById<TextView>(it).setTextColor(foreground) }
            listOf(R.id.tvBrand, R.id.tvCompany, R.id.tvReward).forEach { findViewById<TextView>(it).setTextColor(secondary) }
            findViewById<LinearProgressIndicator>(R.id.progressSpend).apply {
                setIndicatorColor(if (palette.light && !card.cancelled) foreground else Color.WHITE)
                trackColor = Color.parseColor(if (palette.light && !card.cancelled) "#D2AB47" else "#55FFFFFF")
            }
            findViewById<TextView>(R.id.tvBrand).apply {
                text = context.getString(if (card.cancelled) R.string.card_cancelled_badge else R.string.item_card_text_3)
                textSize = if (card.cancelled) 11f else 9f
                letterSpacing = if (card.cancelled) 0f else 0.12f
                background = if (card.cancelled) GradientDrawable().apply {
                    setColor(Color.parseColor("#33FFFFFF"))
                    cornerRadius = 8f * resources.displayMetrics.density
                } else null
                val horizontal = if (card.cancelled) (8 * resources.displayMetrics.density).toInt() else 0
                val vertical = if (card.cancelled) (4 * resources.displayMetrics.density).toInt() else 0
                setPadding(horizontal, vertical, horizontal, vertical)
            }
            findViewById<TextView>(R.id.tvCompany).text = card.company
            findViewById<TextView>(R.id.tvCardName).text = card.cardName
            findViewById<TextView>(R.id.tvProgress).text = if (card.requiredSpend > 0) "실적 ${CardDisplay.progress(card)}%" else "실적 조건 미설정"
            findViewById<LinearProgressIndicator>(R.id.progressSpend).progress = CardDisplay.progress(card)
            findViewById<TextView>(R.id.tvReward).text = context.getString(R.string.card_reward, CardDisplay.money(card.rewardAmount.toLong()))
            contentDescription = "${card.company} ${card.cardName}, ${CardDisplay.status(card)}, 상세 보기"
            setOnClickListener { onClick(card) }
        }
    }
}
