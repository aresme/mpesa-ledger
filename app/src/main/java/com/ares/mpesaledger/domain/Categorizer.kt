package com.ares.mpesaledger.domain

import com.ares.mpesaledger.parser.Kind

/** keyword -> category. Keywords are stored and matched uppercase. */
data class CategoryRule(val keyword: String, val category: String, val isUser: Boolean)

object DefaultRules {
    val all: List<CategoryRule> = listOf(
        "KPLC" to "Electricity",
        "NAIVAS" to "Groceries", "QUICKMART" to "Groceries",
        "CARREFOUR" to "Groceries", "CHANDARANA" to "Groceries",
        "SAFARICOM HOME" to "Internet", "ZUKU" to "Internet", "FAIBA" to "Internet",
        "KABARAK" to "School",
        "TOTALENERGIES" to "Fuel", "RUBIS" to "Fuel", "SHELL" to "Fuel",
        "JAVA HOUSE" to "Food out", "KFC" to "Food out",
        "UBER" to "Transport", "BOLT" to "Transport",
    ).map { (keyword, category) -> CategoryRule(keyword, category, isUser = false) }
}

/**
 * Substring match of the rule keyword against "$counterparty $account", case-insensitive.
 * User rules are tried before defaults; within each group the longest keyword wins, so
 * "SAFARICOM HOME" beats a hypothetical "SAFARICOM".
 */
class Categorizer(rules: List<CategoryRule>) {

    private val ordered: List<CategoryRule> = rules
        .map { it.copy(keyword = it.keyword.uppercase()) }
        .sortedWith(compareBy<CategoryRule> { !it.isUser }.thenByDescending { it.keyword.length })

    fun categorize(kind: Kind, counterparty: String, account: String?): String {
        val haystack = "$counterparty ${account.orEmpty()}".uppercase()
        return ordered.firstOrNull { it.keyword in haystack }?.category ?: fallback(kind)
    }

    companion object {
        const val UNCATEGORIZED = "Uncategorized"

        fun fallback(kind: Kind): String = when (kind) {
            Kind.RECEIVED -> "Income"
            Kind.DEPOSIT -> "Cash in"
            Kind.WITHDRAW -> "Cash out"
            Kind.AIRTIME -> "Airtime"
            Kind.SENT -> "People"
            Kind.FULIZA -> "Fuliza"
            Kind.SAVINGS -> "Savings"
            Kind.PAYBILL, Kind.TILL -> UNCATEGORIZED
        }
    }
}
