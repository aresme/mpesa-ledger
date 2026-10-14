package com.ares.mpesaledger.sms

import android.content.ContentResolver
import android.provider.Telephony
import com.ares.mpesaledger.parser.RawSms

/**
 * Reads M-Pesa messages from the SMS inbox. Read-only: the app never writes to the provider.
 *
 * The selection uses plain LIKE (case-insensitive for ASCII in SQLite) rather than SQL
 * functions: several OEM SMS providers run their query builder in strict mode and reject
 * function calls in the selection. Rows are re-checked in Kotlin with the exact rule
 * (sender equals MPESA ignoring case and dashes).
 */
class InboxReader(private val resolver: ContentResolver) {

    fun readMpesa(sinceDate: Long): List<RawSms> {
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val selection = "(${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?) AND ${Telephony.Sms.DATE} >= ?"
        val args = arrayOf("MPESA", "M-PESA", sinceDate.toString())
        val out = ArrayList<RawSms>()
        resolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, selection, args, "${Telephony.Sms.DATE} ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val address = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                if (!isMpesaSender(c.getString(address))) continue
                val text = c.getString(body) ?: continue
                out += RawSms(body = text, smsId = c.getLong(id), date = c.getLong(date))
            }
        }
        return out
    }

    companion object {
        fun isMpesaSender(address: String?): Boolean =
            address != null && address.replace("-", "").equals("MPESA", ignoreCase = true)
    }
}
