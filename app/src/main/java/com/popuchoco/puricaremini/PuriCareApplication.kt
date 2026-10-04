package com.popuchoco.puricaremini

import android.app.Application

class PuriCareApplication : Application() {
    val ble: BleManager by lazy { BleManager(applicationContext) }
}
