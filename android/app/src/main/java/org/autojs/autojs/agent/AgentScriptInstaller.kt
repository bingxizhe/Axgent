package org.autojs.autojs.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * 把 APK 内置的 assets/agent-src 自动部署到 /sdcard/phone-agent/src。
 *
 * 首次安装、App 更新（lastUpdateTime 变化）或目标缺失时执行；config/local.js
 * 不随 APK 打包，因此不会覆盖设备上的本地密钥配置。
 */
object AgentScriptInstaller {
    private const val TAG = "AxgentScriptInstaller"
    private const val ASSET_ROOT = "agent-src"
    private const val TARGET_ROOT = "/sdcard/phone-agent/src"
    private const val VERSION_FILE = ".axgent-src-version"

    fun ensureInstalled(context: Context): Boolean {
        val appContext = context.applicationContext
        val targetDir = File(TARGET_ROOT)
        val marker = File(targetDir, VERSION_FILE)
        val main = File(targetDir, "main.js")
        val version = appUpdateVersion(appContext)

        if (main.exists() && marker.exists() && marker.readText().trim() == version) return true

        if (!canWriteExternal(appContext)) {
            Log.i(TAG, "skip: all-files access not granted")
            return false
        }

        return try {
            targetDir.mkdirs()
            copyAssetTree(appContext, ASSET_ROOT, targetDir)
            marker.writeText(version)
            Log.i(TAG, "agent src deployed: $TARGET_ROOT ($version)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "deploy failed: ${e.message}")
            false
        }
    }

    private fun appUpdateVersion(context: Context): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
    } catch (_: Exception) {
        "unknown"
    }

    private fun canWriteExternal(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun copyAssetTree(context: Context, assetPath: String, outDir: File) {
        val children = context.assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            // AssetManager.list 对普通文件也返回空数组；空目录则 open 会抛异常。
            outDir.parentFile?.mkdirs()
            try {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outDir).use { output -> input.copyTo(output) }
                }
            } catch (_: FileNotFoundException) {
                outDir.mkdirs()
            }
            return
        }
        outDir.mkdirs()
        for (child in children) {
            copyAssetTree(context, "$assetPath/$child", File(outDir, child))
        }
    }
}
