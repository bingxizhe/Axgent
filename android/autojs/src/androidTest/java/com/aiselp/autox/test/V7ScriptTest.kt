package com.aiselp.autox.test

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.aiselp.autox.engine.NodeScriptEngine
import com.aiselp.autox.test.utils.createTestFile
import com.aiselp.autox.test.utils.openScriptAsset
import com.stardust.autojs.AutoJs
import com.stardust.autojs.ScriptResultViewer
import com.stardust.autojs.TestAutojs
import com.stardust.autojs.execution.ScriptExecution
import com.stardust.autojs.execution.ScriptExecutionListener
import com.stardust.autojs.script.ScriptFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Path

@RunWith(AndroidJUnit4::class)
@MediumTest
class V7ScriptTest {
    val application: Application = ApplicationProvider.getApplicationContext()

    private suspend fun runScript(file: Path, l: ScriptExecutionListener): ScriptExecution {
        init.join()
        return AutoJs.instance.scriptEngineService.execute(
            ScriptFile(file.toString()).toSource(), l
        )
    }

    private fun openNodeTestScript(name: String): Path {
        return openScriptAsset(application, "${v7AccessDir}/$name")
    }

    @Test
    fun bast_test(): Unit = runBlocking {
        val file =openNodeTestScript("base.mjs")
        val resultViewer = ScriptResultViewer()
        val execution = runScript(file, resultViewer)
        resultViewer.waitForSuccess(1000) { execution.engine.forceStop() }

        val resultViewer2 = ScriptResultViewer()
        val file2 = createTestFile("bast.mjs", "throw new Error('123')")
        val execution2 = runScript(file2, resultViewer2)
        resultViewer2.waitForFinish(1000) { execution2.engine.forceStop() }
        assert(resultViewer2.result.isCancelled)
    }

    @Test
    fun java_test(): Unit = runBlocking {
        val script = openNodeTestScript("java1.mjs")
        val resultViewer = ScriptResultViewer()
        val execution = runScript(script, resultViewer)
        resultViewer.waitForSuccess(1000) { execution.engine.forceStop() }
    }

    companion object {
        val v7AccessDir = "v7_test_script"
        val init = Job()

        init {
            val application: Application = ApplicationProvider.getApplicationContext()
            TestAutojs.init(application)
            NodeScriptEngine.initModuleResource(application, true)
            init.complete()
        }
    }
}