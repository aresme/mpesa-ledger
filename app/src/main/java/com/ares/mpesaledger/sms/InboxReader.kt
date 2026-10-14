package com.ares.mpesaledger.sms

import android.content.ContentResolver
import android.provider.Telephony
import com.ares.mpesaledger.parser.RawSms
import com.ares.mpesaledger.parser.Source

/**
 * Reads M-Pesa (and enabled financial senders') messages from the SMS inbox. Read-only: the app
 * never writes to the provider.
 *
 * The selection uses plain LIKE (case-insensitive for ASCII in SQLite) rather than SQL
 * functions: several OEM SMS providers run their query builder in strict mode and reject
 * function calls in the selection. Rows are re-checked in Kotlin with the exact rule
 * (sender equals MPESA ignoring case and dashes).
 */
class InboxReader(private val resolver: ContentResolver, private val enabledSources: () -> Set<Source>) {

    /** M-Pesa plus every other financial sender the user has switched on in Settings → Sources. */
    fun readMpesa(sinceDate: Long): List<RawSms> {
        val sources = enabledSources() + Source.MPESA
        val addresses = sources.flatMap { it.addresses }
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val selection = addresses.joinToString(" OR ", prefix = "(", postfix = ")") { "${Telephony.Sms.ADDRESS} LIKE ?" } +
            " AND ${Telephony.Sms.DATE} >= ?"
        val args = (addresses + sinceDate.toString()).toTypedArray()
        val out = ArrayList<RawSms>()
        resolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, selection, args, "${Telephony.Sms.DATE} ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val address = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val sender = c.getString(address)
                if (Source.of(sender) !in sources) continue
                val text = c.getString(body) ?: continue
                out += RawSms(body = text, smsId = c.getLong(id), date = c.getLong(date), sender = sender)
            }
        }
        return out
    }

    companion object {
        fun isMpesaSender(address: String?): Boolean =
            address != null && address.replace("-", "").equals("MPESA", ignoreCase = true)
    }
}
