package com.example.cardwheel

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
