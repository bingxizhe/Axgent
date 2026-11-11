package org.autojs.autojs.agent

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.io.File

class AgentChatActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        AgentStore.init(this)
        AgentStore.loadConfig()
        // 进程重启/升级后重新注册周期任务；scheduleAll 只读文件 + 设闹钟。
        try {
            AgentScheduleStore.scheduleAll(this)
        } catch (_: Exception) {
        }
        // 首次安装/升级后自动把 APK 内置的 agent-src 释放到 /sdcard/phone-agent/src。
        Thread {
            try {
                AgentScriptInstaller.ensureInstalled(applicationContext)
            } catch (_: Exception) {
            }
        }.start()
        // 清理残留的虚拟屏槽位（子 Agent 1..6 与父任务 7）：没有活动任务时回收。
        Thread {
            try {
                val anySlot = (1..AgentVdRuntime.PARENT_VIRTUAL_SLOT).any {
                    File("/data/local/tmp/vd_status_$it.json").exists()
                }
                if (anySlot && !AgentStore.isTaskActive()) {
                    AgentVdRuntime.stopAllSlots()
                }
            } catch (_: Exception) {
            }
        }.start()
        // 技能种子与 checkpoint 元数据预热都只读写普通文件，不触碰 Compose 状态。
        Thread {
            AgentSkillStore.ensureSeeded()
            AgentStore.prewarmRuns()
        }.start()
        if (AgentStore.config.virtualDisplay &&
            AgentStore.config.privilege.root && AgentStore.config.capabilities.root) {
            Thread {
                val vd = AgentVdRuntime.ensureServer(this)
                Handler(Looper.getMainLooper()).post {
                    AgentStore.setVdRuntimeError(if (vd.ok) null else vd.message)
                    if (vd.ok && (!AgentStore.isTaskActive() || !AgentStore.isScriptLaunched())) {
                        if (vd.status?.optString("status") == "running") {
                            Thread {
                                AgentVdRuntime.stopDisplay()
                                Handler(Looper.getMainLooper()).post { AgentStore.onExternalStop() }
                            }.start()
                        }
                    }
                }
            }.start()
        }
        setContent {
            AxgentTheme(
                themeMode = AgentStore.config.theme,
                accentId = AgentStore.config.accentId,
                customHex = AgentStore.config.customAccent,
                wallpaperAccent = AgentStore.config.wallpaperAccent
            ) {
                AxBackdrop {
                    AgentApp(openPreview = intent?.getBooleanExtra("open_preview", false) == true)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_preview", false)) {
            AgentPreviewOverlay.show(this)
        }
    }

    override fun onStart() {
        super.onStart()
        AgentTaskService.setAppVisible(true)
        maybeShowFluidCloudTip()
    }

    override fun onStop() {
        AgentTaskService.setAppVisible(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // 用户从“所有文件访问”授权页返回后，重新尝试部署内置脚本。
        Thread {
            try {
                AgentScriptInstaller.ensureInstalled(applicationContext)
            } catch (_: Exception) {
            }
        }.start()
        // 从 KernelSU 授权页返回时自动重试一次虚拟屏网关
        if (AgentStore.vdError != null && AgentStore.config.virtualDisplay) {
            retryVdRuntime(this)
        }
    }

    /** 流体云未开启时提示一次，入口在设置页。 */
    private fun maybeShowFluidCloudTip() {
        if (!AgentTaskService.supportsLiveUpdates()) return
        if (!AgentTaskService.isPromotionAllowed(this)) {
            val prefs = getSharedPreferences("agent_ui", MODE_PRIVATE)
            if (!prefs.getBoolean("fluid_tip_shown", false)) {
                prefs.edit().putBoolean("fluid_tip_shown", true).apply()
                Toast.makeText(
                    this,
                    "流体云未开启：可在 设置 → 流体云实时活动 打开，后台任务会显示在顶部胶囊",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

fun retryVdRuntime(context: android.content.Context) {
    Thread {
        val vd = AgentVdRuntime.ensureServer(context)
        Handler(Looper.getMainLooper()).post {
            AgentStore.setVdRuntimeError(if (vd.ok) null else vd.message)
        }
    }.start()
}
