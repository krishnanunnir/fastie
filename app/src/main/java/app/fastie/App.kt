package app.fastie

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        C.init(this)
    }
}
