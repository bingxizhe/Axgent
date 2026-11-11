package org.autojs.autojs.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionState(
    val accessibility: Boolean,
    val allFiles: Boolean,
    val battery: Boolean,
    val notifications: Boolean,
    val overlay: Boolean
) {
    val essentialReady: Boolean get() = accessibility && allFiles
    val missing: List<String>
        get() {
            val out = mutableListOf<String>()
            if (!accessibility) out.add("无障碍")
            if (!allFiles) out.add("全文件访问")
            if (!battery) out.add("电池优化")
            if (!notifications) out.add("通知")
            if (!overlay) out.add("悬浮窗")
            return out
        }
}

fun readPermissionState(context: Context): PermissionState {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    val accessibility = enabled.split(':').any { it.startsWith(context.packageName + "/") }
    val allFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val battery = pm.isIgnoringBatteryOptimizations(context.packageName)
    val notifications = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else true
    return PermissionState(accessibility, allFiles, battery, notifications, Settings.canDrawOverlays(context))
}

private fun startSafe(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

fun openAccessibilitySettings(context: Context) {
    startSafe(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

fun openAllFilesSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            startSafe(context, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    } else {
        startSafe(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    }
}

fun openBatterySettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        startSafe(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private fun openNotificationSettings(context: Context) {
    startSafe(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}

fun openOverlaySettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        startSafe(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(readPermissionState(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        state = readPermissionState(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = readPermissionState(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val colors = AxTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = AxSpace.gutter, vertical = 16.dp)) {
            Text("准备 Axgent", style = MaterialTheme.typography.displaySmall, color = colors.text)
            Spacer(Modifier.height(8.dp))
            Text(
                "首次使用需要几项系统权限。缺权限时仍可进入，但任务可能无法观察界面、读写文件或在后台继续。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "随时可以在设置里调整；这里只做引导，不会上传权限数据。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint
            )
        }
        Column(
            Modifier.padding(horizontal = AxSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AxSection("系统权限") {
                PermissionCard(
                    title = "无障碍服务",
                    subtitle = "读取界面控件并执行点击、滑动、输入；GUI 任务必需。",
                    ready = state.accessibility,
                    action = "去开启",
                    onAction = { openAccessibilitySettings(context) }
                )
                AxDivider()
                PermissionCard(
                    title = "所有文件访问",
                    subtitle = "读取/写入手机文件、保存运行日志和技能；文件任务必需。",
                    ready = state.allFiles,
                    action = "去授权",
                    onAction = { openAllFilesSettings(context) }
                )
                AxDivider()
                PermissionCard(
                    title = "电池优化白名单",
                    subtitle = "避免系统在后台或息屏后冻结 Agent。",
                    ready = state.battery,
                    action = "去设置",
                    onAction = { openBatterySettings(context) }
                )
                AxDivider()
                PermissionCard(
                    title = "通知权限",
                    subtitle = "后台任务状态、暂停/终止和预览入口需要通知。",
                    ready = state.notifications,
                    action = "去开启",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings(context)
                        }
                    }
                )
                AxDivider()
                PermissionCard(
                    title = "悬浮窗预览",
                    subtitle = "任务后台运行时用悬浮窗看实时画面；不开启不影响任务本身。",
                    ready = state.overlay,
                    action = "去开启",
                    onAction = { openOverlaySettings(context) }
                )
            }
            if (!state.accessibility) {
                AxSecondaryButton("用 Shizuku / root 开启无障碍", {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { AgentStore.ensureAccessibilityByRoot() }
                        state = readPermissionState(context)
                        Toast.makeText(
                            context,
                            if (ok) "已通过 Shizuku/root 开启无障碍" else "开启失败：请安装并授权 Shizuku，或允许 root，之后再试",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }, modifier = Modifier.fillMaxWidth())
            }
            if (!state.essentialReady) {
                Text(
                    "建议先开启「无障碍服务」和「所有文件访问」，否则任务成功率会很低。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            AxPrimaryButton(if (state.essentialReady) "完成并进入" else "仍然进入", onFinish)
            Text(
                "稍后设置，先进入",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onFinish)
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    subtitle: String,
    ready: Boolean,
    action: String,
    onAction: () -> Unit
) {
    val colors = AxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !ready, onClick = onAction)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    if (ready) "已开启" else "未开启",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ready) colors.success else colors.warning
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            if (ready) "完成" else action,
            style = MaterialTheme.typography.labelMedium,
            color = if (ready) colors.textGhost else colors.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun PermissionBanner(missing: List<String>, onOpen: () -> Unit) {
    if (missing.isEmpty()) return
    val colors = AxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AxSpace.gutter, vertical = 4.dp)
            .clip(RoundedCornerShape(AxRadius.sm))
            .background(colors.warningContainer.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "权限未完成：${missing.joinToString(" / ")}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onWarningContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            "去设置",
            style = MaterialTheme.typography.labelMedium,
            color = colors.warning,
            modifier = Modifier.clickable(onClick = onOpen).padding(horizontal = 6.dp)
        )
    }
}

/** 虚拟屏运行时错误横幅：root 未授权时提供 KernelSU 入口，其他错误提供重试。 */
@Composable
fun VdBanner(message: String, onFix: () -> Unit) {
    if (message.isBlank()) return
    val colors = AxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AxSpace.gutter, vertical = 4.dp)
            .clip(RoundedCornerShape(AxRadius.sm))
            .background(colors.dangerContainer.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "虚拟屏不可用：${message.take(90)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onDangerContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (message.contains("root")) "去授权" else "重试",
            style = MaterialTheme.typography.labelMedium,
            color = colors.danger,
            modifier = Modifier.clickable(onClick = onFix).padding(horizontal = 6.dp)
        )
    }
}

/** 打开 KernelSU 授权页（失败则退回应用详情页）。 */
fun openKernelSu(context: Context) {
    val pkg = "me.weishu.kernelsu"
    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
    if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
            return
        } catch (_: Exception) {
        }
    }
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}
