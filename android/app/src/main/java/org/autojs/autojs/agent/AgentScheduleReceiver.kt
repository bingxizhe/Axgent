package org.autojs.autojs.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AgentScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AgentScheduleStore.scheduleAll(context)
            return
        }
        if (intent.action != AgentScheduleStore.ACTION_FIRE) return
        val id = intent.getStringExtra(AgentScheduleStore.EXTRA_ID) ?: return
        // 任务文本以本地 schedules.json 为准，不信任 Intent 里的 extra：
        // 防止其他应用伪造广播塞入任意任务。
        val entry = AgentScheduleStore.load().firstOrNull { it.id == id }
        if (entry == null) {
            Log.i("AgentSchedule", "skip id=$id: schedule removed")
            return
        }
        val task = entry.task.trim()
        if (task.isEmpty()) return
        Log.i("AgentSchedule", "fire id=$id task=${task.take(40)}")
        try {
            AgentStore.init(context)
            AgentStore.loadConfig()
            // 已有任务在跑时不抢占：跳过本次触发，直接排下一次。
            if (AgentStore.isTaskActive()) {
                Log.i("AgentSchedule", "skip id=$id: task active")
                AgentScheduleStore.markFired(context, id)
                return
            }
            AgentTaskService.start(context)
            AgentStore.startTask(task)
            AgentScheduleStore.markFired(context, id)
        } catch (e: Exception) {
            Log.w("AgentSchedule", "start failed: ${e.message}")
        }
    }
}
