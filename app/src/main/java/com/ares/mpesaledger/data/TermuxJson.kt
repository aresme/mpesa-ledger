package com.ares.mpesaledger.data

import com.ares.mpesaledger.parser.RawSms
import org.json.JSONArray

/**
 * `termux-sms-list` output: a JSON array of objects with `body` and a sender under
 * `number`, `sender` or `address`. Same filter as the prototype: keep messages whose
 * sender is blank or contains MPESA once dashes are dropped.
 */
object TermuxJson {
    fun parse(json: String): List<RawSms> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val sender = listOf("number", "sender", "address")
                .map { obj.optString(it, "") }.firstOrNull { it.isNotEmpty() }.orEmpty()
            if (sender.isNotEmpty() && "MPESA" !in sender.uppercase().replace("-", "")) return@mapNotNull null
            obj.optString("body", "").takeIf { it.isNotBlank() }?.let { RawSms(it) }
        }
    }
}
