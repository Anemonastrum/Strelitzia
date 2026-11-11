package io.github.anemonastrum.strelitzia

import org.junit.Assert.*
import org.junit.Test

class CpuPolicyTest {
    private val policy = CpuPolicy(0, "0 1", 1286000, 600000, 1920000, 300000, 2515000,
        listOf(300000, 600000, 1286000, 1920000, 2515000), "schedutil")

    @Test fun parsesSortedUniquePositiveFrequencies() {
        assertEquals(listOf(1286000L, 1920000L), parseFrequencies("1920000 1286000 1920000"))
        assertEquals(emptyList<Long>(), parseFrequencies("-1 0 junk 999999999999999999999"))
    }
    @Test fun rejectsUnsupportedAndUnsafeTargets() {
        assertNotNull(validateTarget(policy, 1000000))
        assertNotNull(validateTarget(policy, 300000))
        assertNotNull(validateTarget(policy.copy(hardwareMaxKhz = 1286000), 1920000))
        assertNotNull(validateTarget(policy.copy(availableKhz = emptyList()), 1286000))
        assertNotNull(validateTarget(policy.copy(minKhz = null), 1286000))
        assertNotNull(validateTarget(policy.copy(hardwareMinKhz = null), 1286000))
        assertNotNull(validateTarget(policy.copy(hardwareMaxKhz = null), 1286000))
        assertNotNull(validateTarget(policy.copy(id = -1), 1286000))
        assertNull(validateTarget(policy, 600000))
        assertNull(validateTarget(policy, 2515000))
    }
    @Test fun presetSelectsExactCapsForSpecifiedPolicies() {
        assertEquals(mapOf(0 to 1286000L, 2 to 1920000L, 5 to 1920000L, 7 to 2515000L), PRESET)
        assertEquals("/sys/devices/system/cpu/cpufreq/policy7", policyPath(7))
        assertThrows(IllegalArgumentException::class.java) { policyPath(-1) }
    }
}
