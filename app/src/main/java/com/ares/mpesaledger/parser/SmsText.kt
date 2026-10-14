package com.ares.mpesaledger.parser

/** Plain-text SMS dumps: one message per block, blocks separated by blank lines. */
object SmsText {
    private val BLANK_LINE = Regex("""\r?\n\s*\r?\n""")

    fun splitBlocks(text: String): List<String> =
        text.split(BLANK_LINE).map { it.trim() }.filter { it.isNotEmpty() }
}
