package com.example.cardwheel

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CardDisplay {
    fun money(value: Long) = NumberFormat.getNumberInstance(Locale.KOREA).format(value) + "원"
    fun date(value: Long?) = value?.let { SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(it)) } ?: "미설정"
    fun days(value: Long, now: Long = System.currentTimeMillis()): Long {
        fun day(time: Long): Long {
            val local = Calendar.getInstance().apply { timeInMillis = time }
            return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis / 86400000L
        }
        return day(value) - day(now)
    }
    fun countdown(value: Long?): String = value?.let {
        val left = days(it)
        when { left > 0 -> "D-$left"; left == 0L -> "D-day"; else -> "${-left}일 지남" }
    } ?: "일정 미설정"
    fun schedule(value: Long?) = if (value == null) "미설정" else "${date(value)} · ${countdown(value)}"
    fun progress(card: CardItem) = if (card.requiredSpend > 0)
        (card.currentSpend.toLong() * 100 / card.requiredSpend).coerceIn(0, 100).toInt() else 0
    fun status(card: CardItem): String = when {
        card.cancelled -> "해지 완료"
        card.rewardReceived -> "혜택 수령 완료"
        card.requiredSpend > 0 && card.currentSpend >= card.requiredSpend -> "실적 달성"
        card.spendDeadline != null && days(card.spendDeadline) < 0 -> "실적 마감"
        else -> "이벤트 진행 중"
    }
}
