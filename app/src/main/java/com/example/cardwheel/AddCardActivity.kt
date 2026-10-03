package com.example.cardwheel

import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

class AddCardActivity : BaseActivity() {
    private val dateIds = listOf(R.id.tvIssueDate, R.id.tvSpendDeadline, R.id.tvRewardDate, R.id.tvCancelDate, R.id.tvNextEligibleDate)
    private val dateLabels = listOf("발급일", "실적 마감일", "혜택 지급일", "해지 가능일", "다음 신규 가능일")
    private val dates = arrayOfNulls<Long>(5)
    private val inputIds = listOf(R.id.etCompany, R.id.etCardName, R.id.etRequiredSpend, R.id.etCurrentSpend, R.id.etRewardAmount, R.id.etMemo)
    private var original: CardItem? = null
    private var ready = false
    private var saving = false
    private var baseline = ""
    private lateinit var saveModel: SaveCardViewModel
    private val cardId get() = intent.getIntExtra("cardId", -1)
    private fun input(id: Int) = findViewById<EditText>(id)
    private fun snapshot() = inputIds.joinToString("|") { input(it).text.toString() } + dates.joinToString("|")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setup(R.layout.activity_add_card)
        saveModel = ViewModelProvider(this)[SaveCardViewModel::class.java]
        saving = saveModel.state.value == 1
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = if (cardId == -1) "카드 등록" else "카드 수정"
        toolbar.setNavigationOnClickListener { leave() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { leave() }
        })
        dateIds.forEachIndexed { index, id ->
            findViewById<View>(id).setOnClickListener { pickDate(index) }
            findViewById<View>(id).setOnLongClickListener {
                if (ready && !saving) { dates[index] = null; renderDates() }
                true
            }
        }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
        if (savedInstanceState != null) {
            dates.indices.forEach { index -> dates[index] = savedInstanceState.getLong("date$index", Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE } }
        }
        if (cardId != -1) {
            enableForm(false)
            databaseWork({ dao.getById(cardId) }, failed = { finish() }) { existing ->
                if (existing == null) { finish(); return@databaseWork }
                original = existing
                if (savedInstanceState == null) {
                    val values = listOf(existing.company, existing.cardName, existing.requiredSpend.toString(), existing.currentSpend.toString(), existing.rewardAmount.toString(), existing.memo)
                    inputIds.forEachIndexed { index, id -> input(id).setText(values[index]) }
                    listOf(existing.issueDate, existing.spendDeadline, existing.rewardDate, existing.cancelDate, existing.nextEligibleDate).forEachIndexed { i, value -> dates[i] = value }
                }
                baseline = savedInstanceState?.getString("baseline") ?: snapshot()
                ready = true; enableForm(!saving); renderDates()
            }
        } else {
            ready = true; baseline = savedInstanceState?.getString("baseline") ?: snapshot(); renderDates()
        }
        saveModel.state.observe(this) { state ->
            saving = state == 1
            enableForm(ready && !saving)
            when (state) {
                2 -> { setResult(Activity.RESULT_OK); finish() }
                3 -> {
                    saveModel.state.value = 0
                    MaterialAlertDialogBuilder(this).setTitle("저장하지 못했어요")
                        .setMessage("입력한 내용은 유지됩니다. 잠시 후 다시 시도해 주세요.")
                        .setPositiveButton("확인", null).show()
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        dates.forEachIndexed { i, value -> outState.putLong("date$i", value ?: Long.MIN_VALUE) }
        outState.putString("baseline", baseline); super.onSaveInstanceState(outState)
    }
    private fun enableForm(value: Boolean) {
        (inputIds + dateIds + R.id.btnSave).forEach { findViewById<View>(it).isEnabled = value }
        findViewById<MaterialButton>(R.id.btnSave).text = if (saving) "저장 중…" else if (cardId == -1) "카드 등록하기" else "변경 사항 저장"
    }
    private fun renderDates() {
        dateIds.forEachIndexed { i, id -> findViewById<TextView>(id).text = getString(R.string.date_field, dateLabels[i], CardDisplay.date(dates[i])) }
    }
    private fun pickDate(index: Int) {
        if (!ready || saving) return
        val initial = Calendar.getInstance().apply { dates[index]?.let { timeInMillis = it } }
        val dialog = DatePickerDialog(this, { _, y, m, d ->
            dates[index] = Calendar.getInstance().apply { clear(); set(y, m, d) }.timeInMillis
            renderDates()
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH))
        dialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "날짜 지우기") { _, _ -> dates[index] = null; renderDates() }
        dialog.show()
    }
    private fun leave() {
        if (saving) return
        if (ready && snapshot() != baseline) {
            MaterialAlertDialogBuilder(this).setTitle("작성을 종료할까요?").setMessage("저장하지 않은 변경 사항이 사라집니다.")
                .setNegativeButton("계속 작성", null).setPositiveButton("나가기") { _, _ -> finish() }.show()
        } else finish()
    }
    private fun save() {
        if (!ready || saving) return
        val layoutIds = listOf(R.id.tilCompany, R.id.tilCardName, R.id.tilRequiredSpend, R.id.tilCurrentSpend, R.id.tilRewardAmount)
        layoutIds.forEach { findViewById<TextInputLayout>(it).error = null }
        var invalid: Int? = null
        fun error(index: Int, message: String) {
            findViewById<TextInputLayout>(layoutIds[index]).error = message
            if (invalid == null) invalid = inputIds[index]
        }
        val company = input(R.id.etCompany).text.toString().trim()
        val name = input(R.id.etCardName).text.toString().trim()
        if (company.isBlank()) error(0, "카드사를 입력해 주세요.")
        if (name.isBlank()) error(1, "카드명을 입력해 주세요.")
        fun amount(index: Int): Int {
            val raw = input(inputIds[index]).text.toString().trim()
            if (raw.isEmpty()) return 0
            val number = raw.toIntOrNull()
            if (number == null || number < 0) { error(index, "0 ~ 2,147,483,647 사이의 금액을 입력해 주세요."); return 0 }
            return number
        }
        val required = amount(2); val current = amount(3); val reward = amount(4)
        invalid?.let { input(it).requestFocus(); return }
        if (dates[0] != null && dates[1] != null && dates[1]!! < dates[0]!!) {
            MaterialAlertDialogBuilder(this).setMessage("실적 마감일은 발급일 이후로 선택해 주세요.").setPositiveButton("확인", null).show()
            return
        }
        val next = (original ?: CardItem(company = company, cardName = name)).copy(
            company = company, cardName = name, requiredSpend = required, currentSpend = current, rewardAmount = reward,
            memo = input(R.id.etMemo).text.toString().trim(), issueDate = dates[0], spendDeadline = dates[1],
            rewardDate = dates[2], cancelDate = dates[3], nextEligibleDate = dates[4]
        )
        val stableDao = dao
        val isNew = original == null
        saveModel.save { if (isNew) stableDao.insert(next) else stableDao.update(next) }
    }
}
