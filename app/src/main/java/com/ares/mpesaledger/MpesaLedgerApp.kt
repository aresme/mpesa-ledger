package com.ares.mpesaledger

import android.app.Application
import com.ares.mpesaledger.notify.BillReminderScheduler
import com.ares.mpesaledger.notify.Notifications

class MpesaLedgerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        BillReminderScheduler.schedule(this)
        AppLog.d { "app started (debug=${BuildConfig.DEBUG}, sdk=${android.os.Build.VERSION.SDK_INT})" }
    }
}
