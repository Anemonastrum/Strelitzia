package io.github.anemonastrum.strelitzia

const val CPU_BASE = "/sys/devices/system/cpu/cpufreq"
val PRESET = linkedMapOf(0 to 1286000L, 2 to 1920000L, 5 to 1920000L, 7 to 2515000L)

data class CpuPolicy(
    val id: Int, val relatedCpus: String, val currentKhz: Long?, val minKhz: Long?,
    val maxKhz: Long?, val hardwareMinKhz: Long?, val hardwareMaxKhz: Long?,
    val availableKhz: List<Long>, val governor: String?,
)

fun policyPath(id: Int): String {
    require(id >= 0) { "Invalid policy ID" }
    return "$CPU_BASE/policy$id"
}

fun parseFrequencies(raw: String): List<Long> = raw.trim().split(Regex("\\s+"))
    .mapNotNull { token -> token.takeIf { it.matches(Regex("[0-9]+")) }?.toLongOrNull()?.takeIf { it > 0 } }
    .distinct().sorted()

fun validateTarget(policy: CpuPolicy, khz: Long): String? = when {
    policy.id < 0 -> "Invalid policy ID"
    policy.availableKhz.isEmpty() -> "Supported frequency list unavailable"
    policy.minKhz == null || policy.maxKhz == null || policy.hardwareMinKhz == null || policy.hardwareMaxKhz == null -> "Frequency bounds unavailable"
    policy.minKhz <= 0 || policy.hardwareMinKhz <= 0 || policy.hardwareMaxKhz < policy.hardwareMinKhz -> "Invalid frequency bounds"
    khz <= 0 || khz !in policy.availableKhz -> "Frequency not supported by kernel"
    khz < policy.minKhz -> "Below current minimum frequency"
    khz < policy.hardwareMinKhz || khz > policy.hardwareMaxKhz -> "Outside hardware frequency bounds"
    else -> null
}

fun presetProblem(policies: List<CpuPolicy>): String? {
    for ((id, khz) in PRESET) {
        val policy = policies.firstOrNull { it.id == id } ?: return "policy$id unavailable"
        validateTarget(policy, khz)?.let { return "policy$id: $it" }
    }
    return null
}
