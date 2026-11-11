package org.autojs.autojs.agent

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * 后台镜像预览悬浮窗。
 *
 * 这不是隔离的 Android VirtualDisplay，只是把任务侧最新截图显示出来；
 * 真正的虚拟屏在 M5b spike 中验证。关闭悬浮窗不会停止任务。
 */
object AgentPreviewOverlay {
    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private var image: ImageView? = null
    private var title: TextView? = null
    private var status: TextView? = null
    private var pauseButton: TextView? = null
    @Volatile private var lastMtime = -1L
    private var lastBitmap: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())
    // 预览每 120ms 轮询一次；文件读取和 Bitmap 解码必须离开主线程。
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = true
    @Volatile private var generation = 0
    private var rendering = false

    private data class RenderData(
        val suppressed: Boolean,
        val meta: JSONObject?,
        val paused: Boolean,
        val bitmap: Bitmap?,
        val mtime: Long
    )

    private val poll: Runnable = object : Runnable {
        override fun run() {
            val self = this
            if (closed || root == null || rendering) return
            rendering = true
            val gen = generation
            worker.execute {
                val data = try {
                    if (closed || gen != generation) null else readRenderData()
                } catch (_: Exception) {
                    null
                }
                handler.post {
                    rendering = false
                    if (data == null || closed || gen != generation || root == null) {
                        data?.bitmap?.recycle()
                    } else {
                        applyRenderData(data)
                        handler.postDelayed(self, 120)
                    }
                }
            }
        }
    }

    fun show(context: Context) {
        if (root != null) return
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "需要悬浮窗权限才能打开后台预览", Toast.LENGTH_LONG).show()
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                }
            }
            return
        }
        try {
            val w = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm = w
            val v = build(context)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val lp = WindowManager.LayoutParams(
                dp(context, 284),
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(context, 12)
                y = dp(context, 160)
            }
            w.addView(v, lp)
            root = v
            closed = false
            generation++
            rendering = false
            try { AgentPaths.previewFlagFile.writeText("1") } catch (_: Exception) {}
            handler.post(poll)
        } catch (e: Exception) {
            root = null
            Toast.makeText(context, "预览打开失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun hide() {
        closed = true
        generation++
        handler.removeCallbacks(poll)
        try {
            val manager = wm
            val view = root
            if (manager != null && view != null) manager.removeView(view)
        } catch (_: Exception) {
        }
        root = null
        image = null
        title = null
        status = null
        pauseButton = null
        lastMtime = -1L
        lastBitmap?.recycle()
        lastBitmap = null
        try { AgentPaths.previewFlagFile.delete() } catch (_: Exception) {}
        try { AgentPaths.previewSuppressFile.delete() } catch (_: Exception) {}
    }

    fun isShowing(): Boolean = root != null

    private fun build(context: Context): LinearLayout {
        val bg = GradientDrawable().apply {
            cornerRadius = dp(context, 14).toFloat()
            setColor(0xF2181B20.toInt())
            setStroke(dp(context, 1), 0xFF3A404A.toInt())
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 8))
        }

        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        title = TextView(context).apply {
            text = "后台预览"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(context, 2), 0, 0, 0)
        }
        head.addView(title, LinearLayout.LayoutParams(0, dp(context, 28), 1f))
        val close = TextView(context).apply {
            text = "×"
            setTextColor(0xFFB8C4D0.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setOnClickListener { hide() }
        }
        head.addView(close, LinearLayout.LayoutParams(dp(context, 30), dp(context, 28)))
        panel.addView(head)

        image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        panel.addView(
            image,
            LinearLayout.LayoutParams(dp(context, 268), dp(context, 210)).apply {
                topMargin = dp(context, 4)
            }
        )

        val metaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 3), 0, 0)
        }
        status = TextView(context).apply {
            text = "等待画面…"
            setTextColor(0xFF9AA7B5.toInt())
            textSize = 10f
        }
        metaRow.addView(status, LinearLayout.LayoutParams(0, dp(context, 30), 1f))
        pauseButton = TextView(context).apply {
            text = "暂停"
            setTextColor(0xFF7BD6FF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(context, 6), 0, dp(context, 6), 0)
            setOnClickListener {
                if (AgentPaths.pausedFile.exists()) {
                    AgentControl.resume()
                    AgentStore.onExternalResume()
                } else {
                    AgentControl.pause()
                    AgentStore.onExternalPause()
                }
                // 先给出即时反馈，真实状态由下一次后台轮询校正。
                pauseButton?.text = if (pauseButton?.text == "暂停") "继续" else "暂停"
                handler.removeCallbacks(poll)
                handler.post(poll)
            }
        }
        metaRow.addView(pauseButton, LinearLayout.LayoutParams(dp(context, 54), dp(context, 32)))
        val stop = TextView(context).apply {
            text = "终止"
            setTextColor(0xFFFF8A80.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(context, 6), 0, dp(context, 6), 0)
            setOnClickListener {
                AgentControl.stop()
                AgentStore.onExternalStop()
                hide()
                AgentTaskService.stop(context)
            }
        }
        metaRow.addView(stop, LinearLayout.LayoutParams(dp(context, 54), dp(context, 32)))
        panel.addView(metaRow)
        return panel
    }

    private fun readRenderData(): RenderData {
        if (AgentPaths.previewSuppressFile.exists()) {
            return RenderData(true, null, false, null, lastMtime)
        }
        val meta = readMeta()
        val paused = AgentPaths.pausedFile.exists()
        val file = AgentPaths.previewFile
        var bitmap: Bitmap? = null
        var mtime = lastMtime
        if (file.isFile) {
            val m = file.lastModified()
            if (m != lastMtime) {
                bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) mtime = m
            }
        }
        return RenderData(false, meta, paused, bitmap, mtime)
    }

    private fun applyRenderData(data: RenderData) {
        val view = root ?: return
        view.visibility = if (data.suppressed) View.INVISIBLE else View.VISIBLE
        if (data.suppressed) return
        val meta = data.meta
        val step = meta?.optInt("step", 0) ?: 0
        title?.text = if (step > 0) "后台预览 · 第 $step 步" else "后台预览"
        val err = meta?.optString("error", "").orEmpty()
        status?.text = when {
            err.isNotEmpty() -> "画面暂不可用：${err.take(28)}"
            data.paused -> "已暂停 · 画面保持"
            meta?.optBoolean("ended", false) == true -> "任务已结束"
            else -> "镜像预览 · ${meta?.optString("tool", "").orEmpty().ifBlank { "运行中" }}"
        }
        pauseButton?.text = if (data.paused) "继续" else "暂停"

        val bmp = data.bitmap ?: return
        image?.setImageBitmap(bmp)
        lastMtime = data.mtime
        lastBitmap?.recycle()
        lastBitmap = bmp
    }

    private fun readMeta(): JSONObject? {
        return try {
            if (!AgentPaths.previewMetaFile.exists()) null
            else JSONObject(AgentPaths.previewMetaFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
