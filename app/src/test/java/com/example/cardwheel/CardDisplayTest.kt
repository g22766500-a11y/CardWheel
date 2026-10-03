package com.example.cardwheel

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CardDisplayTest {
    @Test fun progressHandlesZeroOverTargetAndLargeAmounts() {
        val base = CardItem(company = "테스트", cardName = "카드")
        assertEquals(0, CardDisplay.progress(base))
        assertEquals(100, CardDisplay.progress(base.copy(requiredSpend = 100, currentSpend = 150)))
        assertEquals(99, CardDisplay.progress(base.copy(requiredSpend = Int.MAX_VALUE, currentSpend = Int.MAX_VALUE - 1)))
    }
    @Test fun calendarDaysAreCorrectAcrossMidnightAndDaylightSaving() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            fun time(day: Int, hour: Int) = Calendar.getInstance().apply { clear(); set(2026, Calendar.MARCH, day, hour, 30) }.timeInMillis
            assertEquals(1L, CardDisplay.days(time(9, 0), time(8, 23)))
            assertEquals(1L, CardDisplay.days(time(9, 0), time(8, 0)))
            assertEquals(0L, CardDisplay.days(time(8, 23), time(8, 0)))
            assertEquals(-1L, CardDisplay.days(time(8, 0), time(9, 0)))
        } finally { TimeZone.setDefault(previous) }
    }
    @Test fun explicitCompletionStatesTakePriority() {
        val base = CardItem(company = "테스트", cardName = "카드", requiredSpend = 100, currentSpend = 100)
        assertEquals("실적 달성", CardDisplay.status(base))
        assertEquals("혜택 수령 완료", CardDisplay.status(base.copy(rewardReceived = true)))
        assertEquals("해지 완료", CardDisplay.status(base.copy(rewardReceived = true, cancelled = true)))
    }
}
