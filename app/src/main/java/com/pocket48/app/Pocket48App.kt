package com.pocket48.app

import android.app.Application
import com.pocket48.app.data.api.Pocket48Api
import com.pocket48.app.data.store.MemberStore

class Pocket48App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    val pocket48Api: Pocket48Api by lazy { Pocket48Api(cacheDir) }
    val memberStore: MemberStore by lazy { MemberStore(this) }

    companion object {
        lateinit var instance: Pocket48App
            private set
    }
}
