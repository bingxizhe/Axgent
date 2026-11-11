package com.github.aiselp.autox.debug

import android.content.Context
import android.os.Debug
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.aiselp.autox.ui.material3.components.BaseDialog
import com.aiselp.autox.ui.material3.components.ComposeDialog
import com.aiselp.autox.ui.material3.components.DialogTitle
import com.stardust.autojs.servicecomponents.ScriptServiceConnection.Companion.GlobalConnection
import kotlinx.coroutines.delay
import org.autojs.autoxjs.R


object MemoryInformation {

    fun getMemoryInfo(
        memoryInfo: Debug.MemoryInfo = Debug.MemoryInfo()
    ): Debug.MemoryInfo {
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo
    }

    fun memoryInfoDialog(context: Context): ComposeDialog {
        return ComposeDialog(context) {
            BaseDialog(
                title = { DialogTitle(stringResource(R.string.memory_usage)) },
                positiveText = stringResource(R.string.ok),
                onPositiveClick = {
                    dismiss()
                },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    var memoryInfo by remember { mutableStateOf(getMemoryInfo()) }
                    var scriptMemoryInfo: Debug.MemoryInfo? by remember { mutableStateOf(null) }
                    LaunchedEffect(Unit) {
                        scriptMemoryInfo = GlobalConnection.getMemoryInfo()
                        repeat(9999) {
                            delay(3000)
                            memoryInfo = getMemoryInfo()
                            scriptMemoryInfo = GlobalConnection.getMemoryInfo()
                        }
                    }
                    Info("app process", memoryInfo)
                    HorizontalDivider()
                    scriptMemoryInfo?.let { Info("script process", it) }
                }
            }
        }
    }
}

@Composable
private fun Info(name: String, memoryInfo: Debug.MemoryInfo) {
    Column {
        Row {
            Text(text = name, fontSize = 18.sp, color = Color(0xFF4CAF50))
        }

        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
            Text(text = "dalvik: %.2f MB".format((memoryInfo.dalvikPss / 1024.0)))
            Text(text = "native: %.2f MB".format((memoryInfo.nativePss / 1024.0)))
            Text(text = "other: %.2f MB".format((memoryInfo.otherPss / 1024.0)))
            Text(text = "total: %.2f MB".format((memoryInfo.totalPss / 1024.0)), color = Color(0xFF2196F3))
        }
    }
}