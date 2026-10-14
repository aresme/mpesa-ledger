package com.ares.mpesaledger

import android.content.Context

/**
 * Manual dependency injection: one instance per process, owned by [MpesaLedgerApp].
 * Repositories, the database and parsers get wired up here as they're added.
 */
class AppContainer(val appContext: Context)
