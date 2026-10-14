package com.ares.mpesaledger.data

import org.json.JSONArray
import org.json.JSONObject

/** User rules as portable JSON: {"version":1,"rules":[{"keyword":"NAIVAS","category":"Groceries"}]}. */
object RulesJson {
    fun encode(rules: List<CategoryRuleEntity>): String = JSONObject()
        .put("version", 1)
        .put("app", "M-Pesa Ledger")
        .put("rules", JSONArray(rules.filter { it.isUser }.map { JSONObject().put("keyword", it.keyword).put("category", it.category) }))
        .toString(2)

    /** Returns (keyword, category) pairs; blank or malformed entries are skipped. */
    fun decode(json: String): List<Pair<String, String>> {
        val rules = JSONObject(json).optJSONArray("rules") ?: JSONArray()
        return (0 until rules.length()).mapNotNull { i ->
            val o = rules.optJSONObject(i) ?: return@mapNotNull null
            val keyword = o.optString("keyword").trim().uppercase()
            val category = o.optString("category").trim()
            if (keyword.isEmpty() || category.isEmpty()) null else keyword to category
        }
    }
}
