package org.autojs.autojs.external.receiver

import android.content.Context
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.autojs.autojs.timing.IntentTask

class DynamicBroadcastReceivers(private val mContext: Context) {
    private val mActions: MutableSet<String?> = LinkedHashSet()
    private val mReceiverRegistries: MutableList<ReceiverRegistry> = ArrayList<ReceiverRegistry>()
    private val mDefaultActionReceiver = BaseBroadcastReceiver()
    private val mPackageActionReceiver = BaseBroadcastReceiver()


    init {
        val filter = createIntentFilter(StaticBroadcastReceiver.PACKAGE_ACTIONS)
        filter.addDataScheme("package")

        ContextCompat.registerReceiver(
            mContext,
            mDefaultActionReceiver,
            createIntentFilter(StaticBroadcastReceiver.ACTIONS),
            ContextCompat.RECEIVER_EXPORTED
        )
        ContextCompat.registerReceiver(
            mContext, mPackageActionReceiver, filter, ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun register(task: IntentTask) {
        register(mutableListOf(task.action!!), task.isLocal)
    }

    @Synchronized
    fun register(actions: MutableList<String>, local: Boolean) {
        val newActions = LinkedHashSet<String?>()
        for (action in actions) {
            if (!StaticBroadcastReceiver.ACTIONS.contains(action) && !StaticBroadcastReceiver.PACKAGE_ACTIONS.contains(
                    action
                ) && !mActions.contains(action)
            ) {
                newActions.add(action)
            }
        }
        if (newActions.isEmpty()) {
            return
        }
        val receiverRegistry = ReceiverRegistry(newActions, local)
        receiverRegistry.register()
        mReceiverRegistries.add(receiverRegistry)
    }

    @Synchronized
    fun unregister(action: String?) {
        if (!mActions.contains(action)) {
            return
        }
        mActions.remove(action)
        val iterator = mReceiverRegistries.iterator()
        while (iterator.hasNext()) {
            val receiverRegistry = iterator.next()
            if (!receiverRegistry.actions.contains(action)) {
                continue
            }
            receiverRegistry.actions.remove(action)
            receiverRegistry.unregister()
            if (!receiverRegistry.register()) {
                iterator.remove()
            }
            break
        }
    }

    @Synchronized
    fun unregisterAll() {
        for (registry in mReceiverRegistries) {
            registry.unregister()
        }
        mReceiverRegistries.clear()
        mContext.unregisterReceiver(mDefaultActionReceiver)
    }

    private inner class ReceiverRegistry(var actions: LinkedHashSet<String?>, var local: Boolean) {
        var receiver = BaseBroadcastReceiver()

        fun unregister() {
            if (local) {
                val broadcastManager = LocalBroadcastManager.getInstance(mContext)
                broadcastManager.unregisterReceiver(receiver)
            } else {
                mContext.unregisterReceiver(receiver)
            }
        }

        fun register(): Boolean {
            if (actions.isEmpty()) return false
            val intentFilter: IntentFilter = createIntentFilter(actions)
            if (local) {
                val broadcastManager = LocalBroadcastManager.getInstance(mContext)
                broadcastManager.registerReceiver(receiver, intentFilter)
            } else {
                ContextCompat.registerReceiver(mContext,receiver,intentFilter, ContextCompat.RECEIVER_EXPORTED)
            }
            Log.d(LOG_TAG, "register: $actions")
            return true
        }
    }

    companion object {
        const val ACTION_STARTUP: String = "org.autojs.autojs.action.startup"

        private const val LOG_TAG = "DynBroadcastReceivers"

        fun createIntentFilter(actions: MutableCollection<String?>): IntentFilter {
            val filter = IntentFilter()
            for (action in actions) {
                filter.addAction(action)
            }
            return filter
        }
    }
}
