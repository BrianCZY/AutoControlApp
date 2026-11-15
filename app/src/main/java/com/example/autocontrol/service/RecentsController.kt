package com.example.autocontrol.service

import android.app.Activity
import android.app.ActivityManager
import android.util.Log
import com.example.autocontrol.AutoControlApplication
import com.example.autocontrol.data.ControlState

/**
 * 控制应用是否出现在系统「最近任务」列表里。
 *
 * 判定依据是设置页的开关 [ControlState.hideFromRecents]（默认隐藏）。
 *
 * 为什么不再用「电池优化白名单」作为条件：
 * 白名单在绝大多数机器上默认是 false（需要用户手动点系统授权框才会加入），
 * 拿它当门控会导致隐藏逻辑**永远不生效**——这正是上一版的实际表现
 * （实测华为 P40 上新装应用不在白名单，最近任务里一直显示）。
 * 白名单与「要不要留在最近任务」本来就是两件不相关的事，不应耦合。
 *
 * 为什么不用 manifest 的 `android:excludeFromRecents="true"`：
 * 那是静态属性，一装定终身，用户无法恢复显示。
 * 这里用 [ActivityManager.AppTask.setExcludeFromRecents]（API 21+）在运行时切换，
 * 每次进入 App 按最新配置同步一次。
 *
 * 注意：隐藏后用户在最近任务里划不到本应用，因此 [AutoControlService.onTaskRemoved]
 * 基本不会再被触发。保活依赖心跳闹钟，不受影响。
 */
object RecentsController {

    private const val TAG = "RecentsController"

    /** 按当前配置是否应隐藏 */
    fun shouldHide(activity: Activity): Boolean {
        val app = activity.applicationContext as? AutoControlApplication ?: return false
        return app.settingsRepository.state.value.hideFromRecents
    }

    /**
     * 按当前配置同步一次最近任务可见性。
     *
     * 调用时机：onCreate（首次进入）、onResume（可能刚从设置页改过开关）。
     * 全部包在 runCatching 里：个别 ROM 会限制该 API，失败也不应影响 App 正常使用。
     */
    fun sync(activity: Activity) {
        val hide = shouldHide(activity)
        runCatching {
            val am = activity.getSystemService(ActivityManager::class.java) ?: return
            // 只处理本 Activity 所在的那一个任务，避免误改同应用的其他任务
            val task = am.appTasks.firstOrNull { it.taskInfo.taskId == activity.taskId }
            if (task == null) {
                Log.w(TAG, "未找到当前任务（taskId=${activity.taskId}），跳过最近任务设置")
                return
            }
            task.setExcludeFromRecents(hide)
            Log.d(
                TAG,
                "最近任务可见性已同步：${if (hide) "隐藏" else "显示"}"
            )
        }.onFailure { Log.w(TAG, "设置最近任务可见性失败（该 ROM 可能不支持）", it) }
    }
}
