package org.autojs.autojs.external.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.aiselp.autox.ui.material3.theme.M3Theme
import com.stardust.app.GlobalAppContext.post
import com.stardust.toast
import com.stardust.view.accessibility.AccessibilityService
import com.stardust.view.accessibility.LayoutInspector.CaptureAvailableListener
import com.stardust.view.accessibility.NodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.tool.AccessibilityServiceTool
import org.autojs.autojs.ui.floating.FloatyWindowManger.addWindow
import org.autojs.autojs.ui.floating.FullScreenFloatyWindow
import org.autojs.autoxjs.R

abstract class LayoutInspectTileService : TileService(), CaptureAvailableListener {
    private var mCapturing = false
    private val scope = CoroutineScope(Dispatchers.Main)
    val dialog by lazy {
        val dialog = ComponentDialog(this)
        dialog.setTitle("加载中")
        dialog.setContentView(ComposeView(this).apply {
            setContent {
                M3Theme {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        LinearProgressIndicator()
                    }
                }
            }
        })
        dialog
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        AutoJs.getInstance().layoutInspector.addCaptureAvailableListener(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening")
        inactive()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.d(TAG, "onDestroy")
        AutoJs.getInstance().layoutInspector.removeCaptureAvailableListener(
            this
        )
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "onClick")
        scope.launch(Dispatchers.Main) {
            delay(500)
            dialog.dismiss()
            if (AccessibilityService.instance == null) {
                toast(
                    this@LayoutInspectTileService,
                    R.string.text_no_accessibility_permission_to_capture
                )
                AccessibilityServiceTool.goToAccessibilitySetting()
                inactive()
                return@launch
            }
            mCapturing = true
            delay(200)
            AutoJs.getInstance().layoutInspector.captureCurrentWindow()
        }
        this.showDialog(dialog)
    }

    protected fun inactive() {
        val qsTile = qsTile ?: return
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    override fun onCaptureAvailable(capture: NodeInfo?) {
        Log.d(javaClass.name, "onCaptureAvailable: capturing = $mCapturing")
        if (!mCapturing) {
            return
        }
        mCapturing = false
        post {
            val window = onCreateWindow(capture)
            if (!addWindow(applicationContext, window)) {
                inactive()
            }
        }
    }

    protected abstract fun onCreateWindow(capture: NodeInfo?): FullScreenFloatyWindow

    companion object {
        private const val TAG = "LayoutInspectTileService"
    }
}
