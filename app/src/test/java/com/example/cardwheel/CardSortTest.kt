package com.example.cardwheel

import org.junit.Assert.*
import org.junit.Test

class CardSortTest {
    @Test fun allSortOrdersPlaceMissingDatesLastAndUseStableIdTies() {
        val a = CardItem(id=1, company="삼성카드", cardName="Z", spendDeadline=30, rewardDate=10, rewardAmount=100)
        val b = a.copy(id=2, company="KB국민카드", cardName="A", spendDeadline=10, rewardDate=30, rewardAmount=200, cancelled=true)
        val c = a.copy(id=3, spendDeadline=null, rewardDate=null, withdrawn=true)
        val cards = listOf(a,b,c)
        fun ids(sort: CardSort) = sort.apply(cards).map { it.id }
        assertEquals(listOf(3,2,1), ids(CardSort.NEWEST))
        assertEquals(listOf(2,1,3), ids(CardSort.DEADLINE))
        assertEquals(listOf(1,2,3), ids(CardSort.REWARD_DATE))
        assertEquals(listOf(2,3,1), ids(CardSort.REWARD_AMOUNT))
        assertEquals(listOf(2,3,1), ids(CardSort.ISSUER))
        assertEquals(listOf(2,3,1), ids(CardSort.NAME))
        assertEquals(listOf(1,2,3), ids(CardSort.STATUS))
        assertTrue(CardSort.NEWEST.apply(emptyList()).isEmpty())
    }
}
