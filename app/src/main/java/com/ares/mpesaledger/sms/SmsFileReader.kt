package com.ares.mpesaledger.sms

import android.content.ContentResolver
import android.net.Uri
import com.ares.mpesaledger.data.TermuxJson
import com.ares.mpesaledger.parser.RawSms
import com.ares.mpesaledger.parser.SmsText

/**
 * Import from a SAF document: Termux JSON if the content starts with '[' (file
 * extensions and MIME types from file managers are unreliable), otherwise plain text
 * with blank-line-separated messages.
 */
object SmsFileReader {
    fun read(resolver: ContentResolver, uri: Uri): List<RawSms> {
        val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Could not open the selected file")
        return parse(text)
    }

    fun parse(text: String): List<RawSms> =
        if (text.trimStart().startsWith("[")) TermuxJson.parse(text)
        else SmsText.splitBlocks(text).map { RawSms(it) }
}
