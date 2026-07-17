package com.pocket48.app

import android.app.Application
import com.pocket48.app.data.api.Pocket48Api
import com.pocket48.app.data.store.DownloadStore
import com.pocket48.app.data.store.HistoryStore
import com.pocket48.app.data.store.MemberStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class Pocket48App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    val pocket48Api: Pocket48Api by lazy { Pocket48Api(cacheDir) }
    val memberStore: MemberStore by lazy { MemberStore(this) }
    val historyStore: HistoryStore by lazy { HistoryStore(this) }
    val downloadStore: DownloadStore by lazy { DownloadStore(this) }

    /**
     * 应用级 CoroutineScope, 不绑定任何 ViewModel/Activity 生命周期
     *
     * 用途: 在退出播放页时保存播放位置 (savePlayPosition)
     * 原因: 若用 viewModelScope, NavBackStackEntry 销毁时 viewModel 会被清理,
     * viewModelScope 随之取消, 已 launch 的保存任务可能未完成就被打断, 导致进度丢失
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        lateinit var instance: Pocket48App
            private set
    }
}
