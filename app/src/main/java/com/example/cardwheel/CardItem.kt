package com.example.cardwheel

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardItem(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val company: String,

    val cardName: String,

    // 현재 표시용 상태
    val status: String = "",

    // 이벤트 조건 금액
    val requiredSpend: Int = 0,

    // 현재 사용 금액
    val currentSpend: Int = 0,

    // 이벤트 혜택 금액
    val rewardAmount: Int = 0,

    // 카드 발급일
    val issueDate: Long? = null,

    // 실적 마감일
    val spendDeadline: Long? = null,

    // 혜택 지급 예정일
    val rewardDate: Long? = null,

    // 카드 해지 예정/가능일
    val cancelDate: Long? = null,

    // 다음 신규 발급 이벤트 참여 가능일
    val nextEligibleDate: Long? = null,

    // 혜택 수령 여부
    val rewardReceived: Boolean = false,

    // 카드 해지 여부
    val cancelled: Boolean = false,

    // 메모
    val memo: String = ""
)