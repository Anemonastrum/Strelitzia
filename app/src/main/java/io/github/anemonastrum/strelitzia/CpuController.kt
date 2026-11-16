package io.github.anemonastrum.strelitzia

import java.io.File

data class ApplyReport(val success: Boolean, val details: String)

class CpuController(
    private val shell: (String) -> ShellResult = RootShell()::run,
    private val readFile: (String) -> String? = { path ->
        runCatching { File(path).inputStream().use { String(it.readNBytes(65536), Charsets.UTF_8) } }.getOrNull()
    },
    private val listPolicies: () -> List<Int> = {
        File(CPU_BASE).listFiles().orEmpty().mapNotNull {
            Regex("policy([0-9]+)").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
        }
    },
) {
    var connected = false
        private set
    private val baseline = linkedMapOf<Int, Long>()
    var indeterminate = false
        private set

    private fun runRoot(command: String): ShellResult {
        if (indeterminate) return ShellResult(-2, "", "Root commands blocked; state indeterminate. Reboot device before retrying.")
        return shell(command).also {
            if (it.exitCode == -2) { indeterminate = true; connected = false }
        }
    }

    @Synchronized fun connect(): Boolean {
        val result = runRoot("id -u")
        connected = result.exitCode == 0 && result.stdout.trim() == "0"
        if (connected) refresh()
        return connected
    }

    private fun read(id: Int, field: String): String? {
        require(field in setOf("related_cpus", "scaling_cur_freq", "scaling_min_freq", "scaling_max_freq",
            "cpuinfo_min_freq", "cpuinfo_max_freq", "scaling_available_frequencies", "scaling_governor"))
        val path = "${policyPath(id)}/$field"
        readFile(path)?.let { return it.trim() }
        if (!connected) return null
        val result = runRoot("cat $path")
        return result.stdout.trim().takeIf { result.exitCode == 0 }
    }

    private fun policy(id: Int): CpuPolicy = CpuPolicy(id, read(id, "related_cpus") ?: "Unavailable",
        read(id, "scaling_cur_freq")?.toLongOrNull(), read(id, "scaling_min_freq")?.toLongOrNull(),
        read(id, "scaling_max_freq")?.toLongOrNull(), read(id, "cpuinfo_min_freq")?.toLongOrNull(),
        read(id, "cpuinfo_max_freq")?.toLongOrNull(), parseFrequencies(read(id, "scaling_available_frequencies") ?: ""),
        read(id, "scaling_governor"))

    @Synchronized fun refresh(): List<CpuPolicy> {
        var ids = listPolicies().filter { it >= 0 }.distinct().sorted()
        if (ids.isEmpty() && connected) {
            val result = runRoot("printf '%s\n' $CPU_BASE/policy[0-9]*")
            if (result.exitCode == 0) ids = result.stdout.lineSequence().mapNotNull {
                Regex("${Regex.escape(CPU_BASE)}/policy([0-9]+)").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull()
            }.distinct().sorted().toList()
        }
        // ponytail: cap discovery to 256 policies; raise only for devices exposing more clusters.
        return ids.take(256).map(::policy).also { policies ->
            if (connected) policies.forEach { p -> p.maxKhz?.let { baseline.putIfAbsent(p.id, it) } }
        }
    }

    private fun write(id: Int, khz: Long): ShellResult {
        require(khz > 0)
        return runRoot("printf '%s\n' $khz > ${policyPath(id)}/scaling_max_freq")
    }

    @Synchronized fun apply(targets: Map<Int, Long>): ApplyReport {
        if (!connected) return ApplyReport(false, "Root not connected")
        if (targets.isEmpty() || targets.keys.any { it < 0 }) return ApplyReport(false, "Invalid policy selection")
        val current = refresh().associateBy { it.id }
        for ((id, value) in targets) {
            val p = current[id] ?: return ApplyReport(false, "policy$id unavailable; nothing written")
            validateTarget(p, value)?.let { return ApplyReport(false, "policy$id: $it; nothing written") }
        }
        val attempted = mutableListOf<Int>()
        val details = mutableListOf<String>()
        var failure: String? = null
        for ((id, value) in targets) {
            // Re-read before each write: a power service can change minimums during a preset.
            val fresh = policy(id)
            val invalid = validateTarget(fresh, value)
            if (invalid != null) { failure = "policy$id: $invalid"; break }
            if (fresh.maxKhz == value) { details += "policy$id already $value kHz"; continue }
            attempted += id
            val result = write(id, value)
            val observed = if (indeterminate) null else read(id, "scaling_max_freq")?.toLongOrNull()
            details += "policy$id requested $value kHz; observed ${observed ?: "unavailable"} kHz"
            if (result.exitCode != 0 || observed != value) {
                failure = "policy$id failed: ${result.stderr.ifBlank { "read-back mismatch" }}"
                break
            }
        }
        if (failure != null) {
            details += failure
            if (indeterminate) return ApplyReport(false, (details + "State indeterminate: rollback skipped. Root commands blocked. Reboot device before retrying.").joinToString("\n"))
            for (id in attempted.asReversed()) {
                val old = current.getValue(id).maxKhz!!
                val fresh = policy(id)
                val unsafe = validateTarget(fresh, old)
                if (unsafe != null) { details += "policy$id restore failed: $unsafe"; continue }
                if (fresh.maxKhz == old) { details += "policy$id restored: already $old kHz"; continue }
                val result = write(id, old)
                val observed = if (indeterminate) null else read(id, "scaling_max_freq")?.toLongOrNull()
                details += if (result.exitCode == 0 && observed == old) "policy$id restored to $old kHz"
                else "policy$id restore failed; observed ${observed ?: "unavailable"} kHz: ${result.stderr}"
            }
        }
        return ApplyReport(failure == null, details.joinToString("\n"))
    }

    @Synchronized fun restoreSession(): ApplyReport = apply(baseline.toMap())
}
