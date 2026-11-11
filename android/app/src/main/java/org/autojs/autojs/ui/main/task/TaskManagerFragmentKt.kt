package org.autojs.autojs.ui.main.task

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import com.aiselp.autox.ui.material3.MainFloatingActionButton
import com.stardust.autojs.servicecomponents.EngineController
import org.autojs.autojs.ui.widget.fillMaxSize

class TaskManagerFragmentKt : Fragment() {

    private val taskListRecyclerView by lazy {
        TaskListRecyclerView(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                this@TaskManagerFragmentKt.Content()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Content() {
        Scaffold(
            floatingActionButton = {
                MainFloatingActionButton(
                    onClick = { EngineController.stopAllScript() }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                    )
                }
            },
        ) {
            val refresh = remember { mutableStateOf(false) }
            PullToRefreshBox(
                modifier = Modifier
                    .padding(it)
                    .fillMaxSize(),
                isRefreshing = refresh.value,
                onRefresh = {
                    refresh.value = true
                    taskListRecyclerView.refresh()
                    taskListRecyclerView.postDelayed({
                        refresh.value = false
                    }, 200)
                }
            ) {
                AndroidView(
                    factory = {
                        taskListRecyclerView.fillMaxSize()
                    },
                )
            }
        }
    }

    fun refresh() = taskListRecyclerView.refresh()
}