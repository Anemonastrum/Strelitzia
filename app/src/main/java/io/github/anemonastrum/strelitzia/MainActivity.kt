package io.github.anemonastrum.strelitzia

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val model: KernelViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StrelitziaApp(model) }
    }
    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}

class KernelViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = CpuController()
    var state by mutableStateOf(OperationState())
        private set
    var device by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    fun refresh() = operate { null }
    fun connect() = operate {
        if (controller.connect()) "Root connected. Changes require confirmation."
        else "Root denied or unavailable. Grant access in your root manager, then retry."
    }
    fun apply(targets: Map<Int, Long>) = operate { controller.apply(targets).let { (if (it.success) "Verified\n" else "Not applied successfully\n") + it.details } }
    fun restore() = operate { controller.restoreSession().let { (if (it.success) "Session caps restored\n" else "Restore incomplete\n") + it.details } }

    private fun operate(action: () -> String?) {
        // All entry points run on main; busy is set before launching, preventing queued duplicate writes.
        state = state.begin() ?: return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val message = action()
                    Triple(message, controller.refresh(), readDevice())
                }
                state = state.copy(root = controller.connected, policies = result.second,
                    message = result.first ?: state.message)
                device = result.third
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                state = state.failed(e.message ?: "Unknown error")
            } finally { state = state.copy(busy = false) }
        }
    }

    private fun readDevice(): List<Pair<String, String>> {
        val memory = ActivityManager.MemoryInfo()
        getApplication<Application>().getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        return listOf(
            "Manufacturer" to Build.MANUFACTURER,
            "Model" to Build.MODEL,
            "Device" to Build.DEVICE,
            "Android" to "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
            "Kernel" to runCatching { File("/proc/sys/kernel/osrelease").readText().trim() }.getOrDefault("Unavailable"),
            "SoC" to "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}",
            "Architecture" to Build.SUPPORTED_ABIS.joinToString(),
            "RAM" to String.format(Locale.US, "%.1f GB total · %.1f GB available", memory.totalMem / 1e9, memory.availMem / 1e9),
        )
    }
}
