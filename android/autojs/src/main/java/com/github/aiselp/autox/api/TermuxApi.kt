package com.github.aiselp.autox.api

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.github.aiselp.autox.activity.TransparentActivity
import com.stardust.autojs.IndependentScriptService
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer


class TermuxApi(val context: Context) {
    private val callbacks = ConcurrentHashMap<Long, Consumer<ExecutionResult>>()
    private var disposable: Disposable? = null

    private fun onResult(result: ExecutionResult) {
        val callback = callbacks.remove(result.executionId)
        callback?.accept(result)
    }

    fun requestPermission(callback: Consumer<Boolean>?) {
        TransparentActivity.requestNewActivity(context) { activity ->
            val launcher =
                activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    activity.finish()
                    callback?.accept(granted)
                }
            launcher.launch(perm)
        }
    }

    fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun newCommand(path: String): Command {
        return Command(path)
    }

    fun runCommand(command: Command) {
        val intent = command.build()
        context.startService(intent)
    }

    fun runCommand(command: Command, callback: Consumer<ExecutionResult>) {
        val executionId = getNextExecutionId()
        val resultsServiceIntent = Intent(context, IndependentScriptService::class.java)
        resultsServiceIntent.putExtra(EXTRA_EXECUTION_ID, executionId)

        val pendingIntent = PendingIntent.getService(
            context, executionId.toInt(),
            resultsServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )

        if (disposable == null) {
            registerResultListener()
        }
        val intent = command.build()
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT, pendingIntent)
        callbacks.put(executionId, callback)
        try {
            context.startService(intent)
        } catch (e: Exception) {
            callbacks.remove(executionId)
            throw e
        }
    }

    fun registerResultListener() {
        synchronized(this) {
            if (disposable == null) {
                disposable = resultPublish.filter {
                    callbacks.containsKey(it.executionId)
                }.observeOn(Schedulers.computation())
                    .subscribe(::onResult)
            }
        }
    }

    fun recycle() {
        synchronized(this) {
            disposable?.dispose()
            disposable = null
            callbacks.clear()
        }
    }

    class Command(var path: String) {
        val intent = Intent(RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND)
        private val args = mutableListOf<String>()
        var workdir: String? = null
        var background = false
        var sessionAction: String? = null
        var label: String? = null
        var description: String? = null

        init {
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        }

        fun addArgument(vararg arg: String): Command {
            args.addAll(arg)
            return this
        }

        fun setArgument(vararg args: String): Command {
            this.args.clear()
            this.args.addAll(args)
            return this
        }

        fun build(): Intent {
            intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, path)
            intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, args.toTypedArray())
            workdir?.let {
                intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_WORKDIR, it)
            }
            sessionAction?.let {
                intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_SESSION_ACTION, it)
            }
            label?.let {
                intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, it)
            }
            description?.let {
                intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_DESCRIPTION, it)
            }
            intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_BACKGROUND, background)
            return intent
        }
    }

    object RUN_COMMAND_SERVICE {
        const val RUN_COMMAND_API_HELP_URL: String =
            "https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent"
        const val ACTION_RUN_COMMAND: String = "com.termux.RUN_COMMAND"
        const val EXTRA_COMMAND_PATH: String = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS: String = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS: String =
            "com.termux.RUN_COMMAND_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS"
        const val EXTRA_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS: String =
            "com.termux.RUN_COMMAND_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS"
        const val EXTRA_STDIN: String = "com.termux.RUN_COMMAND_STDIN"
        const val EXTRA_WORKDIR: String = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND: String = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL: String =
            "com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL"
        const val EXTRA_SESSION_ACTION: String = "com.termux.RUN_COMMAND_SESSION_ACTION"
        const val EXTRA_COMMAND_LABEL: String = "com.termux.RUN_COMMAND_COMMAND_LABEL"
        const val EXTRA_COMMAND_DESCRIPTION: String = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
        const val EXTRA_COMMAND_HELP: String = "com.termux.RUN_COMMAND_COMMAND_HELP"
        const val EXTRA_PENDING_INTENT: String = "com.termux.RUN_COMMAND_PENDING_INTENT"
        const val EXTRA_RESULT_DIRECTORY: String = "com.termux.RUN_COMMAND_RESULT_DIRECTORY"
        const val EXTRA_RESULT_SINGLE_FILE: String = "com.termux.RUN_COMMAND_RESULT_SINGLE_FILE"
        const val EXTRA_RESULT_FILE_BASENAME: String = "com.termux.RUN_COMMAND_RESULT_FILE_BASENAME"
        const val EXTRA_RESULT_FILE_OUTPUT_FORMAT: String =
            "com.termux.RUN_COMMAND_RESULT_FILE_OUTPUT_FORMAT"
        const val EXTRA_RESULT_FILE_ERROR_FORMAT: String =
            "com.termux.RUN_COMMAND_RESULT_FILE_ERROR_FORMAT"
        const val EXTRA_RESULT_FILES_SUFFIX: String = "com.termux.RUN_COMMAND_RESULT_FILES_SUFFIX"
    }

    data class ExecutionResult(
        val executionId: Long,
        val stdout: String?,
        val stdout_original_length: String?,
        val stderr: String?,
        val stderr_original_length: String?,
        val exitCode: Int,
        val errCode: Int,
        val errmsg: String?
    )


    companion object {
        private val EXECUTION_ID = AtomicLong(1000)
        private val resultPublish = PublishSubject.create<ExecutionResult>()
        const val EXTRA_EXECUTION_ID: String = "execution_id"

        fun getNextExecutionId(): Long {
            return EXECUTION_ID.getAndIncrement()
        }

        fun onHandleIntent(intent: Intent) {
            val longExtra = intent.getLongExtra(EXTRA_EXECUTION_ID, -1L)
            if (longExtra != -1L) {
                val resultBundle = intent.getBundleExtra("result") ?: return
                val result = ExecutionResult(
                    executionId = longExtra,
                    stdout = resultBundle.getString("stdout"),
                    stdout_original_length = resultBundle.getString("stdout_original_length"),
                    stderr = resultBundle.getString("stderr"),
                    stderr_original_length = resultBundle.getString("stderr_original_length"),
                    exitCode = resultBundle.getInt("exitCode", -1),
                    errCode = resultBundle.getInt("err", -1),
                    errmsg = resultBundle.getString("errmsg")
                )
                resultPublish.onNext(result)
            }
        }

        const val perm: String = "com.termux.permission.RUN_COMMAND"
    }
}