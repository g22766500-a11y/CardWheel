package com.example.cardwheel

internal enum class CardSort(val label: String) {
    NEWEST("최근 등록순"), DEADLINE("실적 마감일순"), REWARD_DATE("혜택 지급일순"),
    REWARD_AMOUNT("혜택 금액 높은순"), ISSUER("카드사 이름순"), NAME("카드 이름순"), STATUS("진행 상태순");

    fun apply(cards: List<CardItem>): List<CardItem> {
        val comparator: Comparator<CardItem> = when (this) {
            NEWEST -> compareByDescending { it.id }
            DEADLINE -> compareBy { it.spendDeadline ?: Long.MAX_VALUE }
            REWARD_DATE -> compareBy { it.rewardDate ?: Long.MAX_VALUE }
            REWARD_AMOUNT -> compareByDescending { it.rewardAmount }
            ISSUER -> compareBy<CardItem> { it.company.trim().lowercase(java.util.Locale.ROOT) }.thenBy { it.cardName }
            NAME -> compareBy { it.cardName.trim().lowercase(java.util.Locale.ROOT) }
            STATUS -> compareBy { when { it.withdrawn -> 4; it.cancelled -> 3; it.rewardReceived -> 2;
                it.requiredSpend > 0 && it.currentSpend >= it.requiredSpend -> 1; else -> 0 } }
        }
        return cards.sortedWith(comparator.thenByDescending { it.id })
    }
}
