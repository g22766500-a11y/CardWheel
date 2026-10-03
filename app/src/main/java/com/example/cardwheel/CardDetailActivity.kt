package com.example.cardwheel

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.view.View
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.materialswitch.MaterialSwitch

class CardDetailActivity : BaseActivity() {
    private var card: CardItem? = null
    private var binding = false
    private var busy = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setup(R.layout.activity_card_detail)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.btnEdit).setOnClickListener {
            card?.let { startActivity(Intent(this, AddCardActivity::class.java).putExtra("cardId", it.id)) }
        }
        findViewById<View>(R.id.btnDelete).setOnClickListener {
            val current = card ?: return@setOnClickListener
            MaterialAlertDialogBuilder(this).setTitle("카드를 삭제할까요?")
                .setMessage("${current.cardName}의 이벤트와 기록이 삭제됩니다. 이 작업은 되돌릴 수 없습니다.")
                .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                    if (!busy) {
                        setBusy(true)
                        databaseWork({ dao.delete(current) }, failed = { setBusy(false) }) { finish() }
                    }
                }.show()
        }
        findViewById<MaterialSwitch>(R.id.switchReward).setOnCheckedChangeListener { _, value ->
            if (!binding && !busy) card?.let { update(it.copy(rewardReceived = value)) }
        }
        findViewById<MaterialSwitch>(R.id.switchCancelled).setOnCheckedChangeListener { _, value ->
            if (!binding && !busy) card?.let { update(it.copy(cancelled = value)) }
        }
    }
    override fun onResume() {
        super.onResume()
        databaseWork({ dao.getById(intent.getIntExtra("cardId", -1)) }) { loaded ->
            if (loaded == null) finish() else { card = loaded; render(loaded); setBusy(false) }
        }
    }
    private fun setBusy(value: Boolean) {
        busy = value
        listOf(R.id.btnEdit, R.id.btnDelete, R.id.switchReward, R.id.switchCancelled).forEach {
            findViewById<View>(it).isEnabled = !value && card != null
        }
    }
    private fun update(next: CardItem) {
        setBusy(true)
        databaseWork({ dao.update(next) }, failed = { card?.let { render(it) }; setBusy(false) }) {
            card = next; render(next); setBusy(false)
        }
    }
    private fun render(item: CardItem) {
        fun text(id: Int, value: String) { findViewById<TextView>(id).text = value }
        text(R.id.tvCompany, item.company); text(R.id.tvCardName, item.cardName)
        text(R.id.tvStatus, CardDisplay.status(item))
        text(R.id.tvProgress, if (item.requiredSpend > 0) "${CardDisplay.progress(item)}% 달성" else "실적 조건 미설정")
        findViewById<LinearProgressIndicator>(R.id.progressSpend).progress = CardDisplay.progress(item)
        text(R.id.tvSpend, "${CardDisplay.money(item.currentSpend.toLong())} / ${CardDisplay.money(item.requiredSpend.toLong())}")
        text(R.id.tvRemaining, if (item.requiredSpend > 0) "목표까지 ${CardDisplay.money((item.requiredSpend.toLong() - item.currentSpend).coerceAtLeast(0))}" else "수정 화면에서 실적 조건을 입력해 주세요.")
        text(R.id.tvReward, CardDisplay.money(item.rewardAmount.toLong()))
        text(R.id.tvIssueDate, CardDisplay.date(item.issueDate))
        text(R.id.tvSpendDeadline, CardDisplay.schedule(item.spendDeadline))
        text(R.id.tvRewardDate, CardDisplay.schedule(item.rewardDate))
        text(R.id.tvCancelDate, CardDisplay.schedule(item.cancelDate))
        text(R.id.tvNextEligibleDate, CardDisplay.schedule(item.nextEligibleDate))
        text(R.id.tvMemo, item.memo.ifBlank { "등록한 메모가 없습니다." })
        binding = true
        findViewById<MaterialSwitch>(R.id.switchReward).isChecked = item.rewardReceived
        findViewById<MaterialSwitch>(R.id.switchCancelled).isChecked = item.cancelled
        binding = false
    }
}
