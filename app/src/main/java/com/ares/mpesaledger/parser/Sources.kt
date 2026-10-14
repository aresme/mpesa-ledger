package com.ares.mpesaledger.parser

/**
 * SMS senders the app can read. M-Pesa is always on; the others are switchable in Settings.
 * [addresses] are the exact sender names seen on Kenyan phones (matched case-insensitively,
 * ignoring spaces, dashes and underscores).
 */
enum class Source(val key: String, val label: String, val description: String, val addresses: List<String>) {
    MPESA("mpesa", "M-Pesa", "Payments, transfers, Fuliza and M-Shwari", listOf("MPESA", "M-PESA")),
    GLOBALPAY("globalpay", "M-PESA GlobalPay", "Card payments to websites and apps (counted as money out)", listOf("Globalpay")),
    SAFARICOM("safaricom", "Safaricom", "Data bundles and airtime recharges (not counted again: they're paid via M-Pesa)", listOf("Safaricom")),
    OKOA("okoa", "Okoa Jahazi", "Airtime borrowed on credit and its repayments", listOf("Okoa Jahazi")),
    KCB("kcb", "KCB", "Money received into your KCB account", listOf("KCB")),
    ZIIDI("ziidi", "Ziidi", "Withdrawals from your Ziidi money market fund", listOf("ZIIDI")),
    ETOPUP("etopup", "E-top-up", "Airtime you receive", listOf("ETOPUP"));

    companion object {
        private fun norm(address: String) = address.uppercase().filter { it.isLetterOrDigit() }

        fun of(address: String?): Source? {
            val n = address?.let(::norm) ?: return null
            return entries.firstOrNull { s -> s.addresses.any { norm(it) == n } }
        }
    }
}
