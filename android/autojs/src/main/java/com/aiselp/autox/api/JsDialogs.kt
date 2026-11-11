package com.aiselp.autox.api

import android.content.Context
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.aiselp.autox.api.ui.ComposeElement
import com.aiselp.autox.api.ui.Render
import com.aiselp.autox.engine.EventLoopQueue
import com.aiselp.autox.ui.material3.theme.AppTheme
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.reference.V8ValueFunction
import com.caoccao.javet.values.reference.V8ValueObject
import com.github.aiselp.autox.activity.TransparentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class JsDialogs(
    val eventLoopQueue: EventLoopQueue,
    val context: Context,
    val scope: CoroutineScope
) : NativeApi {
    override val moduleId: String = ID
    override fun install(v8Runtime: V8Runtime, global: V8ValueObject): NativeApi.BindingMode {
        return NativeApi.BindingMode.ObjectBind
    }

    override fun recycle(v8Runtime: V8Runtime, global: V8ValueObject) {
    }

    @V8Function
    fun showDialog(
        element: ComposeElement,
        listener: V8ValueObject?
    ): AppDialogBuilder {
        val securePolicy = listener?.getString("securePolicy")
        val builder = object : AppDialogBuilder(scope) {
            val el: ComposeElement = element
            val dismissListener = listener?.get<V8ValueFunction>("onDismiss")?.let {
                eventLoopQueue.createV8Callback(it)
            }

            @Composable
            override fun Render() {
                el.Render()
            }

            override val dismissOnBackPress: Boolean =
                listener?.getBoolean("dismissOnBackPress") ?: super.dismissOnBackPress
            override val dismissOnClickOutside: Boolean =
                listener?.getBoolean("dismissOnClickOutside") ?: super.dismissOnClickOutside
            override val securePolicy: SecureFlagPolicy = when (securePolicy) {
                "SecureOn" -> SecureFlagPolicy.SecureOn
                "SecureOff" -> SecureFlagPolicy.SecureOff
                else -> super.securePolicy
            }

            override fun onDismiss() {
                dismissListener?.invoke()
                dismissListener?.close()
            }
        }
        TransparentActivity.requestNewActivity(context) {
            builder.build(it)
        }
        return builder
    }

    @OptIn(DelicateCoroutinesApi::class)
    abstract class AppDialogBuilder(val scope: CoroutineScope = GlobalScope) {
        var show by mutableStateOf(true)
        open val dismissOnBackPress = true
        open val dismissOnClickOutside = true
        open val securePolicy: SecureFlagPolicy = SecureFlagPolicy.Inherit

        abstract fun onDismiss()
        fun dismiss() = scope.launch(Dispatchers.Main) {
            show = false
        }

        @Composable
        open fun Render() {
        }

        open fun build(activity: AppCompatActivity) {
            activity.setContent {
                if (!show) {
                    LaunchedEffect(Unit) {
                        onDismiss()
                        activity.finish()
                    }
                    return@setContent
                }
                AppTheme(dynamicColor = false) {
                    Dialog(
                        onDismissRequest = { show = false },
                        properties = DialogProperties(
                            dismissOnBackPress = dismissOnBackPress,
                            dismissOnClickOutside = dismissOnClickOutside,
                            securePolicy = securePolicy,
                        )
                    ) {
                        Render()
                    }
                }
            }
        }
    }

    companion object {
        const val ID = "dialogs"
    }
}