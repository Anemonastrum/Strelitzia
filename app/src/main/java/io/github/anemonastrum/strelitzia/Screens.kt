package io.github.anemonastrum.strelitzia

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.ui.*
import java.math.BigDecimal

private fun mhz(khz: Long?): String = khz?.let { BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString() + " MHz" } ?: "Unavailable"

@Composable
fun StrelitziaApp(model: KernelViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Map<Int, Long>?>(null) }
    var restore by remember { mutableStateOf(false) }
    val state = model.state
    val dark = isSystemInDarkTheme()
    GlasmioTheme(isDark = dark) {
        val colors = GlasmioTheme.colors
        val gradient = if (dark) listOf(Color(0xFF232039), colors.groupedBackground, Color(0xFF142C2B))
            else listOf(Color(0xFFF4E8FE), colors.groupedBackground, Color(0xFFE3F3ED))
        GlasmioBackdropHost(
            modifier = Modifier.fillMaxSize(),
            sampledContent = { Box(Modifier.fillMaxSize().background(Brush.linearGradient(gradient))) },
            overlayContent = {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("STRELITZIA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp)
                        Text(listOf("CPU settings", "Your device", "Developer")[page], style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        when (page) {
                            0 -> {
                                Text("Less heat. Your limits.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Group {
                                    Text(if (state.root) "Root connected" else "Read-only mode", fontWeight = FontWeight.SemiBold)
                                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Action(if (state.root) "Refresh CPU" else "Connect root", !state.busy) {
                                        if (state.root) model.refresh() else model.connect()
                                    }
                                    if (!state.root) TextButton(onClick = model::refresh, enabled = !state.busy) { Text("Refresh read-only data") }
                                }
                                Group {
                                    Text("Your preset", style = MaterialTheme.typography.titleLarge)
                                    Text("0 · 1286 MHz\n2 · 1920 MHz\n5 · 1920 MHz\n7 · 2515 MHz", style = MaterialTheme.typography.bodyLarge)
                                    val reason = presetProblem(state.policies)
                                    reason?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Action("Apply preset", state.root && !state.busy && reason == null) { pending = PRESET.toMap() }
                                    TextButton(onClick = { restore = true }, enabled = state.root && !state.busy && state.policies.isNotEmpty()) { Text("Restore session caps") }
                                    Text("Session values are captured when root connects, not factory defaults.", style = MaterialTheme.typography.bodySmall)
                                }
                                if (state.policies.isEmpty()) Group { Text("No CPU policies readable. Connect root or refresh. This kernel may not expose cpufreq.") }
                                state.policies.forEach { p -> key(p.id) { PolicyCard(p, state.root && !state.busy) { pending = mapOf(p.id to it) } } }
                                Text("Caps are limits, not fixed clocks. Thermal and power services can override them. Nothing applies at boot.", style = MaterialTheme.typography.bodySmall)
                            }
                            1 -> {
                                Text("Hardware, software, and CPU policies.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Group { model.device.forEach { (label, value) -> Detail(label, value) } }
                                Group {
                                    Text("CPU topology", style = MaterialTheme.typography.titleLarge)
                                    if (state.policies.isEmpty()) Text("Policy data unavailable")
                                    state.policies.forEach { p ->
                                        Detail("Policy ${p.id} · CPUs ${p.relatedCpus}", "${p.governor ?: "Governor unavailable"}\nHardware ${mhz(p.hardwareMinKhz)} – ${mhz(p.hardwareMaxKhz)}\n${p.availableKhz.size} exposed frequencies")
                                    }
                                    Action("Refresh", !state.busy, model::refresh)
                                }
                            }
                            else -> {
                                Group {
                                    Text("A", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                                    Text("Anemonastrum", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text("Developer · Strelitzia kernel manager")
                                    WebLink("GitHub profile", "https://github.com/Anemonastrum")
                                    WebLink("Source & APK builds", "https://github.com/Anemonastrum/Strelitzia")
                                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                                }
                                Group {
                                    Text("Built with care", style = MaterialTheme.typography.titleLarge)
                                    Text("Glasmio supplies glass controls and materials. MeiloX inspires the layout. System fonts; no Apple assets.")
                                    WebLink("Glasmio · Apache-2.0", "https://github.com/NEORUAA/Glasmio")
                                    WebLink("MeiloX · design reference", "https://github.com/MeiloX/MeiloX")
                                    Text("No analytics. No accounts. No boot service.", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // Large text uses a taller labeled glass selector rather than clipping fixed-height icon tabs.
                    if (LocalDensity.current.fontScale > 1.3f) {
                        GlasmioSegmentedControl(items = listOf(0 to "CPU", 1 to "Device", 2 to "Profile"), selected = page,
                            onSelected = { page = it }, height = (52 * LocalDensity.current.fontScale).dp,
                            modifier = Modifier.fillMaxWidth().padding(12.dp))
                    } else {
                        GlasmioNavigationBar(
                            items = listOf(GlasmioTabItem(0, "CPU", SfSymbol.Gearshape), GlasmioTabItem(1, "Device", SfSymbol.InfoCircle), GlasmioTabItem(2, "Profile", SfSymbol.PersonFilled)),
                            selectedKey = page, onSelected = { page = it }, onExpand = {},
                            compactProgress = remember { mutableFloatStateOf(0f) }, compactSize = 48.dp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            },
        )
        if (pending != null || restore) {
            AlertDialog(onDismissRequest = { pending = null; restore = false },
                title = { Text(if (restore) "Restore session caps?" else "Apply CPU caps?") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        pending?.forEach { (id, value) -> Text("Policy $id: ${mhz(value)}") }
                        Text("Root access changes maximum CPU limits, not fixed clocks. Settings can affect performance and stability. Thermal services may override them. Nothing applies at boot.")
                    }
                },
                confirmButton = { TextButton(enabled = !state.busy, onClick = {
                    val targets = pending; val doRestore = restore
                    pending = null; restore = false
                    if (doRestore) model.restore() else targets?.let(model::apply)
                }) { Text("Confirm") } },
                dismissButton = { TextButton(onClick = { pending = null; restore = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    GlasmioCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun Action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    // Glasmio's fixed button height clips accessibility text; use native wrapping button at large scales.
    if (LocalDensity.current.fontScale > 1.3f) Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
    else GlasmioButton(onClick = onClick, enabled = enabled, size = GlasmioButtonSize.Large, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

@Composable
private fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PolicyCard(policy: CpuPolicy, enabled: Boolean, apply: (Long) -> Unit) {
    var selected by remember(policy.id, policy.maxKhz, policy.availableKhz) { mutableStateOf(policy.maxKhz) }
    var choosing by remember { mutableStateOf(false) }
    Group {
        Text("Policy ${policy.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("CPUs ${policy.relatedCpus} · ${policy.governor ?: "Governor unavailable"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Detail("Current clock", mhz(policy.currentKhz))
        Detail("Current limits", "${mhz(policy.minKhz)} – ${mhz(policy.maxKhz)}")
        Action("Maximum: ${mhz(selected)}", enabled && policy.availableKhz.isNotEmpty()) { choosing = true }
        val validationError: String? = selected?.let { validateTarget(policy, it) }
        val reasonText = if (selected == null) "No frequency selected" else validationError
        reasonText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Action("Apply to policy ${policy.id}", enabled && validationError == null && selected != null && selected != policy.maxKhz) { apply(selected!!) }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text("Policy ${policy.id} maximum") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                policy.availableKhz.forEach { value ->
                    TextButton(onClick = { selected = value; choosing = false }, enabled = validateTarget(policy, value) == null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(mhz(value)) }
                }
            }
        }, confirmButton = { TextButton(onClick = { choosing = false }) { Text("Close") } })
}

@Composable
private fun WebLink(label: String, url: String) {
    val context = LocalContext.current
    TextButton(onClick = {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (e: ActivityNotFoundException) { Toast.makeText(context, "No browser installed", Toast.LENGTH_SHORT).show() }
    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}
