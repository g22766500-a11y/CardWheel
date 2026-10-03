package com.example.cardwheel

import android.content.Intent
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.view.HapticFeedbackConstants
import android.os.Build
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : BaseActivity() {
    private val cards = mutableListOf<CardItem>()
    private lateinit var pager: ViewPager2
    private lateinit var adapter: CardAdapter
    private var selectedId = -1
    private var measuredWidth = 0
    private var pendingCards: List<CardItem>? = null
    private var hasLoadedCards = false
    private val pageHaptics = PageHaptics()
    private var restoringCards = false
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
        (pager.getChildAt(0) as RecyclerView).isNestedScrollingEnabled = false
        pager.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val width = right - left
            if (width > 0 && width != measuredWidth) resizePager(width)
        }
        adapter = CardAdapter { startActivity(Intent(this, CardDetailActivity::class.java).putExtra("cardId", it.id)) }
        pager.adapter = adapter
        pager.setPageTransformer { page, position ->
            page.alpha = 1f - 0.15f * kotlin.math.abs(position).coerceAtMost(1f)
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                cards.getOrNull(position)?.let { selectedId = it.id }
                showSelection()
            }
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING && !restoringCards) {
                    pageHaptics.start(cards.getOrNull(pager.currentItem)?.id ?: -1)
                }
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val settledId = cards.getOrNull(pager.currentItem)?.id ?: -1
                    if (pageHaptics.settle(settledId)) {
                        pager.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34)
                            HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
                    }
                    pendingCards?.let { loaded -> pendingCards = null; displayCards(loaded) }
                }
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
            if (pager.scrollState == ViewPager2.SCROLL_STATE_IDLE) displayCards(loaded)
            else pendingCards = loaded
        }
    }
    private fun displayCards(loaded: List<CardItem>) {
            findViewById<View>(R.id.btnRetry).visibility = View.GONE
            if (hasLoadedCards && cards == loaded) { showSelection(); return }
            hasLoadedCards = true
            val targetId = selectedId
            restoringCards = true
            cards.clear(); cards.addAll(loaded); adapter.submitItems(loaded)
            resizePager(pager.width)
            val empty = cards.isEmpty()
            findViewById<View>(R.id.emptyState).visibility = if (empty) View.VISIBLE else View.GONE
            findViewById<View>(R.id.walletContent).visibility = if (empty) View.GONE else View.VISIBLE
            pager.setCurrentItem(cards.indexOfFirst { it.id == targetId }.coerceAtLeast(0), false)
            selectedId = cards.getOrNull(pager.currentItem)?.id ?: -1
            restoringCards = false
            findViewById<TextView>(R.id.tvSummary).text = getString(R.string.wallet_summary, cards.size, CardDisplay.money(cards.filter { !it.cancelled && !it.rewardReceived }.sumOf { it.rewardAmount.toLong() }))
            showSelection()
    }
    private fun showSelection() {
        val card = cards.getOrNull(pager.currentItem)
        findViewById<TextView>(R.id.tvPage).text = if (card == null) "" else "${pager.currentItem + 1} / ${cards.size}"
        findViewById<TextView>(R.id.tvSelected).text = card?.let {
            "${CardDisplay.status(it)}\n실적 마감 ${CardDisplay.schedule(it.spendDeadline)}"
        } ?: ""
    }
    private fun resizePager(width: Int) {
        if (width <= 0) return
        measuredWidth = width
        val sample = LayoutInflater.from(this).inflate(R.layout.item_card, pager, false)
        val content = sample.findViewById<View>(R.id.cardContent)
        val verticalPadding = sample.paddingTop + sample.paddingBottom
        val contentWidth = (width - sample.paddingLeft - sample.paddingRight).coerceAtLeast(1)
        val examples = cards.ifEmpty { listOf(CardItem(company = "카드사", cardName = "카드명")) }
        val height = examples.maxOf { card ->
            sample.findViewById<TextView>(R.id.tvCompany).text = card.company
            sample.findViewById<TextView>(R.id.tvCardName).text = card.cardName
            sample.findViewById<TextView>(R.id.tvProgress).text = if (card.requiredSpend > 0)
                "실적 ${CardDisplay.progress(card)}%" else "실적 조건 미설정"
            sample.findViewById<TextView>(R.id.tvReward).text = getString(R.string.card_reward, CardDisplay.money(card.rewardAmount.toLong()))
            content.measure(View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            content.measuredHeight + verticalPadding
        }
        if (height > 0 && pager.layoutParams.height != height) {
            pager.layoutParams = pager.layoutParams.apply { this.height = height }
        }
    }
}
