package com.example.cardwheel

import android.content.Intent
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : BaseActivity() {
    private val cards = mutableListOf<CardItem>()
    private lateinit var pager: ViewPager2
    private lateinit var adapter: CardAdapter
    private var selectedId = -1
    private val addCard = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedId = -1
            Snackbar.make(findViewById(R.id.root), R.string.card_saved, Snackbar.LENGTH_SHORT)
                .setAnchorView(R.id.fabAdd).show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setup(R.layout.activity_main)
        selectedId = savedInstanceState?.getInt("selectedId", -1) ?: -1
        pager = findViewById(R.id.viewPager)
        pager.layoutParams.height = ((360f + (resources.configuration.fontScale - 1f).coerceAtLeast(0f) * 240f) * resources.displayMetrics.density).toInt()
        adapter = CardAdapter { startActivity(Intent(this, CardDetailActivity::class.java).putExtra("cardId", it.id)) }
        pager.adapter = adapter
        pager.setPageTransformer { page, position ->
            page.scaleY = 1f - 0.04f * kotlin.math.abs(position).coerceAtMost(1f)
            page.alpha = 1f - 0.15f * kotlin.math.abs(position).coerceAtMost(1f)
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                cards.getOrNull(position)?.let { selectedId = it.id }
                showSelection()
            }
        })
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            addCard.launch(Intent(this, AddCardActivity::class.java))
        }
        findViewById<View>(R.id.btnRetry).setOnClickListener { loadCards() }
    }
    override fun onResume() { super.onResume(); loadCards() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selectedId", selectedId); super.onSaveInstanceState(outState)
    }
    private fun loadCards() {
        databaseWork({ dao.getAll() }, failed = { findViewById<View>(R.id.btnRetry).visibility = View.VISIBLE }) { loaded ->
            findViewById<View>(R.id.btnRetry).visibility = View.GONE
            val targetId = selectedId
            cards.clear(); cards.addAll(loaded); adapter.submitItems(loaded)
            val empty = cards.isEmpty()
            findViewById<View>(R.id.emptyState).visibility = if (empty) View.VISIBLE else View.GONE
            findViewById<View>(R.id.walletContent).visibility = if (empty) View.GONE else View.VISIBLE
            pager.setCurrentItem(cards.indexOfFirst { it.id == targetId }.coerceAtLeast(0), false)
            selectedId = cards.getOrNull(pager.currentItem)?.id ?: -1
            findViewById<TextView>(R.id.tvSummary).text = getString(R.string.wallet_summary, cards.size, CardDisplay.money(cards.filter { !it.cancelled && !it.rewardReceived }.sumOf { it.rewardAmount.toLong() }))
            showSelection()
        }
    }
    private fun showSelection() {
        val card = cards.getOrNull(pager.currentItem)
        findViewById<TextView>(R.id.tvPage).text = if (card == null) "" else "${pager.currentItem + 1} / ${cards.size}"
        findViewById<TextView>(R.id.tvSelected).text = card?.let {
            "${CardDisplay.status(it)}\n실적 마감 ${CardDisplay.schedule(it.spendDeadline)}"
        } ?: ""
    }
}
