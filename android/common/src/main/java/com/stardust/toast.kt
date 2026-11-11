package com.stardust

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun toast(context: Context, @StringRes resId: Int, isLongToast: Boolean = false) {
    toast(context, context.getString(resId), isLongToast)
}

fun toast(context: Context, text: String?, isLongToast: Boolean = false) {
    Toast.makeText(
        context.applicationContext,
        text,
        if (isLongToast) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    ).show()
}

suspend fun String.toast(context: Context, isLongToast: Boolean = false) =
    withContext(Dispatchers.Main) {
        Toast.makeText(
            context,
            this@toast,
            if (isLongToast) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }