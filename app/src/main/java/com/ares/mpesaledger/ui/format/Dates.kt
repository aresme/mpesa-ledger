package com.ares.mpesaledger.ui.format

import com.ares.mpesaledger.parser.MpesaParser
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// All display dates are Nairobi wall time, matching how they were parsed.
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val DAY_YEAR = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val FULL = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a", Locale.ENGLISH)

fun YearMonth.label(): String = format(MONTH)
fun YearMonth.shortLabel(): String = format(MONTH_SHORT)

fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(MpesaParser.NAIROBI).toLocalDate()

fun nowMonth(): YearMonth = YearMonth.now(MpesaParser.NAIROBI)

fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now(MpesaParser.NAIROBI)): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(if (date.year == today.year) DAY else DAY_YEAR)
}

fun timeLabel(millis: Long): String = Instant.ofEpochMilli(millis).atZone(MpesaParser.NAIROBI).format(TIME)

fun fullLabel(millis: Long): String = Instant.ofEpochMilli(millis).atZone(MpesaParser.NAIROBI).format(FULL)
