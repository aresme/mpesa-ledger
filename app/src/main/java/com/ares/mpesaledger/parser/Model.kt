package com.ares.mpesaledger.parser

/** Which way money moved relative to the wallet. NONE is excluded from in/out totals. */
enum class Direction(val key: String) {
    IN("in"), OUT("out"), NONE("none");

    companion object {
        fun fromKey(key: String): Direction = entries.first { it.key == key }
    }
}

/** Transaction kinds, in the order the parser tries them (first match wins). */
enum class Kind(val key: String, val direction: Direction) {
    FULIZA("fuliza", Direction.NONE),
    RECEIVED("received", Direction.IN),
    PAYBILL("paybill", Direction.OUT),
    SENT("sent", Direction.OUT),
    TILL("till", Direction.OUT),
    WITHDRAW("withdraw", Direction.OUT),
    DEPOSIT("deposit", Direction.IN),
    AIRTIME("airtime", Direction.OUT),
    SAVINGS("savings", Direction.NONE),

    /** Paying back Fuliza. NONE: the funded payment was already counted as out. */
    FULIZA_REPAY("fuliza_repay", Direction.NONE),

    /** Money moved back from M-Shwari / KCB M-PESA / Ziidi savings to the wallet: not income. */
    SAVINGS_WITHDRAW("savings_out", Direction.NONE),

    // ---- Other senders (parser v4). Only GlobalPay counts as money out: it has no M-Pesa SMS.
    /** M-PESA GlobalPay virtual Visa card payment; forex charge is the fee. */
    GLOBALPAY("globalpay", Direction.OUT),
    /** Money into a bank account (separate from the M-Pesa wallet). */
    BANK_IN("bank_in", Direction.NONE),
    /** Airtime borrowed with Okoa Jahazi / Okoa Chap Chap (airtime credit, not money). */
    OKOA_BORROW("okoa", Direction.NONE),
    OKOA_REPAY("okoa_repay", Direction.NONE),
    /** Safaricom airtime recharge confirmation (money was already counted where it was paid). */
    AIRTIME_RECHARGE("airtime_recharge", Direction.NONE),
    /** Airtime received (e-top-up). */
    AIRTIME_RECEIVED("airtime_received", Direction.NONE),
    /** Safaricom data bundle bought: name and expiry only (price is in the M-Pesa / airtime record). */
    DATA_BUNDLE("data_bundle", Direction.NONE);

    companion object {
        fun fromKey(key: String): Kind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One parsed M-Pesa transaction. Amounts are Long cents; [timestamp] is epoch millis
 * (Africa/Nairobi wall time), null for Fuliza messages, which carry no date.
 */
data class ParsedTransaction(
    val code: String,
    val kind: Kind,
    val amountCents: Long,
    val feeCents: Long?,
    val balanceCents: Long?,
    val counterparty: String,
    val phone: String?,
    val account: String?,
    val timestamp: Long?,
    val raw: String,
) {
    val direction: Direction get() = kind.direction
}

sealed interface ParseResult {
    data class Transaction(val tx: ParsedTransaction) : ParseResult

    /** Has a transaction code but matches no known format. Never guessed at. */
    data class Unparsed(val code: String, val raw: String) : ParseResult

    /** No transaction code: promos, reminders, etc. Skipped silently. */
    data object NotTransaction : ParseResult
}

/**
 * One SMS as read from the inbox or a file. [smsId] and [date] are null for file imports;
 * [sender] is null when unknown (plain-text files), which means "M-Pesa".
 */
data class RawSms(val body: String, val smsId: Long? = null, val date: Long? = null, val sender: String? = null)
