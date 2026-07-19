package com.pocket48.app

import android.app.Application
import android.util.Log
import com.pocket48.app.data.api.Pocket48Api
import com.pocket48.app.data.store.CursorIndexStore
import com.pocket48.app.data.store.DownloadStore
import com.pocket48.app.data.store.HistoryStore
import com.pocket48.app.data.store.MemberStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Pocket48App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 启动时后台静默检查 members.json 远程更新 (不阻塞 UI, 失败静默)
        // 复用 appScope (SupervisorJob + Dispatchers.IO), 与 savePlayPosition 同模式
        appScope.launch {
            runCatching { memberStore.checkAndUpdateFromRemote() }
                .onFailure { Log.w(TAG, "members.json remote check failed", it) }
        }
    }

    val pocket48Api: Pocket48Api by lazy { Pocket48Api(cacheDir) }
    val memberStore: MemberStore by lazy { MemberStore(this) }
    val historyStore: HistoryStore by lazy { HistoryStore(this) }
    val downloadStore: DownloadStore by lazy { DownloadStore(this) }
    val cursorIndexStore: CursorIndexStore by lazy { CursorIndexStore(this) }

    /**
     * 应用级 CoroutineScope, 不绑定任何 ViewModel/Activity 生命周期
     *
     * 用途:
     * - 在退出播放页时保存播放位置 (savePlayPosition)
     * - 退出 ViewModel 后写入 cursor 索引 (seedCursorIndex)
     * - 启动时后台检查 members.json 远程更新
     *
     * 原因: 若用 viewModelScope, NavBackStackEntry 销毁时 viewModel 会被清理,
     * viewModelScope 随之取消, 已 launch 的任务可能未完成就被打断
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "Pocket48App"
        lateinit var instance: Pocket48App
            private set
    }
}
