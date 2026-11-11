package com.github.aiselp.autox.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

class TransparentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.run {
            val id = getLongExtra(TAG, -1)
            val r = requests.remove(id)
            r?.accept(this@TransparentActivity)
        } ?: finish()
    }

    companion object {
        private const val TAG = "TransparentActivity"
        private val REQUEST_ID = AtomicLong(1000)
        private val requests = ConcurrentHashMap<Long, Consumer<AppCompatActivity>>()

        fun requestNewActivity(context: Context, r: Consumer<AppCompatActivity>) {
            val id = REQUEST_ID.andIncrement
            val intent = Intent(context, TransparentActivity::class.java).also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                it.putExtra(TAG, id)
                requests[id] = r
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                requests.remove(id)
                throw e
            }
        }
    }
}