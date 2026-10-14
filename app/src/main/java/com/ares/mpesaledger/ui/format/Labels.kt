package com.ares.mpesaledger.ui.format

import com.ares.mpesaledger.parser.Kind

/** Human labels for transaction kinds (Safaricom's own product names where they exist). */
fun kindLabel(key: String): String = when (Kind.fromKey(key)) {
    Kind.RECEIVED -> "Received"
    Kind.SENT -> "Sent"
    Kind.PAYBILL -> "Pay bill"
    Kind.TILL -> "Buy goods"
    Kind.WITHDRAW -> "Withdrawal"
    Kind.DEPOSIT -> "Deposit"
    Kind.AIRTIME -> "Airtime"
    Kind.SAVINGS -> "To savings"
    Kind.SAVINGS_WITHDRAW -> "From savings"
    Kind.FULIZA -> "Fuliza loan"
    Kind.FULIZA_REPAY -> "Fuliza repayment"
    null -> key
}
