package com.ares.mpesaledger

import android.app.Application

class MpesaLedgerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppLog.d { "app started (debug=${BuildConfig.DEBUG}, sdk=${android.os.Build.VERSION.SDK_INT})" }
    }
}
