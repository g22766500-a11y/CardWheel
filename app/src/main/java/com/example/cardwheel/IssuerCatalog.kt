package com.example.cardwheel

import java.util.Locale

internal object IssuerCatalog {
    data class Palette(val start: String, val end: String, val light: Boolean = false)
    private data class Issuer(val name: String, val aliases: List<String>, val palette: Palette)
    private val issuers = listOf(
        Issuer("신한카드", listOf("신한", "shinhancard"), Palette("#234BA9", "#122859")),
        Issuer("KB국민카드", listOf("국민", "국민카드", "kb", "kb국민", "kbcard"), Palette("#FFDE73", "#F3C447", true)),
        Issuer("삼성카드", listOf("삼성", "samsungcard"), Palette("#17539A", "#142F59")),
        Issuer("현대카드", listOf("현대", "hyundaicard"), Palette("#454851", "#202127")),
        Issuer("롯데카드", listOf("롯데", "lottecard"), Palette("#B92336", "#701B30")),
        Issuer("우리카드", listOf("우리", "wooricard"), Palette("#086AA4", "#123C6D")),
        Issuer("하나카드", listOf("하나", "hanacard"), Palette("#007F79", "#004C50")),
        Issuer("NH농협카드", listOf("농협", "농협카드", "nh", "nh농협", "nhcard"), Palette("#26763E", "#154A35")),
        Issuer("BC카드", listOf("bc", "비씨", "비씨카드"), Palette("#BC253C", "#751B35")),
        Issuer("IBK기업은행", listOf("기업", "기업은행", "기업카드", "ibk", "ibk기업", "ibk기업카드"), Palette("#17588C", "#123B60")),
        Issuer("씨티카드", listOf("씨티", "씨티은행", "citi", "citicard"), Palette("#20539E", "#162F5B")),
        Issuer("우체국", listOf("우체국카드"), Palette("#A9481F", "#6F321E"))
    )
    val names: List<String> = issuers.map { it.name }
    private val fallback = Palette("#344789", "#15233F")
    private fun normalize(value: String) = value.filterNot { it.isWhitespace() }.lowercase(Locale.ROOT)
    fun palette(company: String): Palette {
        val key = normalize(company)
        return issuers.firstOrNull { normalize(it.name) == key || it.aliases.any { alias -> normalize(alias) == key } }?.palette ?: fallback
    }
}
