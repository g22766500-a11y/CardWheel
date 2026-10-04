package com.example.cardwheel

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class RemoteBackup(val revision: Long, val savedAt: Long?, val cards: List<CardItem>)

internal object BackupCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_CARDS = 500
    fun encode(cards: List<CardItem>): String {
        require(cards.size <= MAX_CARDS) { "카드는 최대 500개까지 백업할 수 있어요." }
        val array = JSONArray()
        cards.forEach { card -> array.put(JSONObject().apply {
            put("id", card.id); put("company", card.company); put("cardName", card.cardName)
            put("status", card.status); put("requiredSpend", card.requiredSpend)
            put("currentSpend", card.currentSpend); put("rewardAmount", card.rewardAmount)
            put("issueDate", card.issueDate ?: JSONObject.NULL); put("spendDeadline", card.spendDeadline ?: JSONObject.NULL)
            put("rewardDate", card.rewardDate ?: JSONObject.NULL); put("cancelDate", card.cancelDate ?: JSONObject.NULL)
            put("nextEligibleDate", card.nextEligibleDate ?: JSONObject.NULL)
            put("rewardReceived", card.rewardReceived); put("cancelled", card.cancelled); put("memo", card.memo)
        }) }
        val result = JSONObject().put("schemaVersion", 1).put("cards", array).toString()
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        // Validate outgoing snapshots too; old or corrupt rows must not overwrite a valid remote backup.
        decodeCards(JSONObject(result))
        return result
    }
    fun decodeCards(root: JSONObject): List<CardItem> {
        require(integer(root, "schemaVersion", 1, 1) == 1L)
        val array = root.get("cards") as? JSONArray ?: error("Invalid cards")
        require(array.length() <= MAX_CARDS)
        val ids = mutableSetOf<Int>()
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val id = integer(obj, "id", 1, Int.MAX_VALUE.toLong()).toInt()
            require(ids.add(id))
            CardItem(id, text(obj, "company", 100, true), text(obj, "cardName", 100, true),
                text(obj, "status", 200), integer(obj, "requiredSpend", 0, Int.MAX_VALUE.toLong()).toInt(),
                integer(obj, "currentSpend", 0, Int.MAX_VALUE.toLong()).toInt(), integer(obj, "rewardAmount", 0, Int.MAX_VALUE.toLong()).toInt(),
                date(obj, "issueDate"), date(obj, "spendDeadline"), date(obj, "rewardDate"), date(obj, "cancelDate"), date(obj, "nextEligibleDate"),
                obj.get("rewardReceived") as? Boolean ?: error("Invalid flag"), obj.get("cancelled") as? Boolean ?: error("Invalid flag"), text(obj, "memo", 2000))
        }.sortedByDescending { it.id }
    }
    fun remote(raw: String): RemoteBackup {
        val parser = JSONTokener(raw)
        val root = parser.nextValue() as? JSONObject ?: error("Invalid response")
        require(parser.nextClean() == '\u0000')
        val revision = integer(root, "revision", 0, Long.MAX_VALUE)
        val savedAt = date(root, "savedAt")
        val cards = decodeCards(root)
        require((revision == 0L && savedAt == null && cards.isEmpty()) || (revision > 0 && savedAt != null))
        return RemoteBackup(revision, savedAt, cards)
    }
    private fun text(root: JSONObject, key: String, max: Int, required: Boolean = false): String {
        val value = root.get(key) as? String ?: error("Invalid text")
        require(value.length <= max && (!required || value.isNotBlank()))
        return value
    }
    private fun integer(root: JSONObject, key: String, min: Long, max: Long): Long {
        val value = root.get(key)
        require(value is Int || value is Long)
        return (value as Number).toLong().also { require(it in min..max) }
    }
    private fun date(root: JSONObject, key: String): Long? {
        require(root.has(key))
        return if (root.isNull(key)) null else integer(root, key, -62135596800000L, 253402300799999L)
    }
}
