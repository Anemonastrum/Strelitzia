package io.github.anemonastrum.strelitzia

import org.junit.Assert.*
import org.junit.Test

class CpuControllerTest {
    private class Device {
        val caps = linkedMapOf(0 to 2515000L, 2 to 2515000L, 5 to 2515000L, 7 to 2515000L)
        val writes = mutableListOf<Pair<Int, Long>>()
        var minimum = 300000L
        var deny = -1
        var mismatch = false
        var denyRestore = false
        var root = true
        fun read(path: String): String? {
            val match = Regex(".*/policy([0-9]+)/([a-z_]+)").matchEntire(path) ?: return null
            val id = match.groupValues[1].toInt()
            if (id !in caps) return null
            return when (match.groupValues[2]) {
                "scaling_max_freq" -> caps[id].toString()
                "scaling_min_freq" -> minimum.toString()
                "cpuinfo_min_freq" -> "300000"
                "cpuinfo_max_freq" -> "2515000"
                "scaling_available_frequencies" -> "300000 1286000 1920000 2515000"
                "scaling_cur_freq" -> "1286000"
                "related_cpus" -> id.toString()
                "scaling_governor" -> "schedutil"
                else -> null
            }
        }
        fun execute(command: String): ShellResult {
            if (command == "id -u") return ShellResult(if (root) 0 else 1, if (root) "0\n" else "", "denied")
            val m = Regex("printf '%s\\n' ([0-9]+) > /sys/devices/system/cpu/cpufreq/policy([0-9]+)/scaling_max_freq").matchEntire(command)
                ?: error("Unexpected command: $command")
            val id = m.groupValues[2].toInt(); val value = m.groupValues[1].toLong()
            writes.add(id to value)
            if (id == deny || (denyRestore && value == 2515000L)) return ShellResult(1, "", "denied")
            caps[id] = if (mismatch && value != 2515000L) 1920000 else value
            return ShellResult(0, "", "")
        }
        fun controller() = CpuController(::execute, ::read, { caps.keys.toList() })
    }
    @Test fun noRootNeverWrites() {
        val d = Device(); val c = d.controller()
        assertFalse(c.apply(mapOf(0 to 1286000L)).success); assertTrue(d.writes.isEmpty())
        d.root = false; assertFalse(c.connect()); assertTrue(d.writes.isEmpty())
    }
    @Test fun exactPresetWritesAndVerifiesEveryPolicy() {
        val d = Device(); val c = d.controller(); assertTrue(c.connect())
        assertTrue(c.apply(PRESET).success)
        assertEquals(mapOf(0 to 1286000L, 2 to 1920000L, 5 to 1920000L, 7 to 2515000L), d.caps)
    }
    @Test fun missingPolicyPreventsAllPresetWrites() {
        val d = Device(); d.caps.remove(5); val c = d.controller(); c.connect()
        assertFalse(c.apply(PRESET).success); assertTrue(d.writes.isEmpty())
    }
    @Test fun staleMinimumAndInvalidPolicyRejectBeforeWrite() {
        val d = Device(); val c = d.controller(); c.connect(); c.refresh(); d.minimum = 1920000
        assertFalse(c.apply(mapOf(0 to 1286000L)).success)
        assertFalse(c.apply(mapOf(-1 to 1286000L)).success); assertTrue(d.writes.isEmpty())
    }
    @Test fun partialFailureRestoresChangedPoliciesAndStops() {
        val d = Device(); val c = d.controller(); c.connect(); d.deny = 2
        val report = c.apply(PRESET)
        assertFalse(report.success); assertEquals(2515000L, d.caps[0]); assertFalse(d.writes.any { it.first == 5 })
        assertTrue(report.details.contains("restored"))
    }
    @Test fun failedRestoreIsVisible() {
        val d = Device(); val c = d.controller(); c.connect(); d.deny = 2; d.denyRestore = true
        val report = c.apply(PRESET)
        assertFalse(report.success); assertTrue(report.details.contains("restore failed")); assertEquals(1286000L, d.caps[0])
    }
    @Test fun mismatchedReadbackIsNotSuccess() {
        val d = Device(); val c = d.controller(); c.connect(); d.mismatch = true
        val report = c.apply(mapOf(0 to 1286000L))
        assertFalse(report.success); assertTrue(report.details.contains("1920000")); assertEquals(2515000L, d.caps[0])
    }
    @Test fun indeterminateWriteBlocksRollbackAndLaterWrites() {
        val d = Device()
        var commands = 0
        val c = CpuController({ command ->
            commands++
            if (command.contains(" > ")) ShellResult(-2, "", "state indeterminate") else d.execute(command)
        }, d::read, { d.caps.keys.toList() })
        c.connect()
        val report = c.apply(PRESET)
        assertFalse(report.success)
        assertTrue(report.details.contains("indeterminate"))
        assertFalse(c.connected)
        val before = commands
        assertFalse(c.apply(PRESET).success)
        assertFalse(c.restoreSession().success)
        assertFalse(c.connect())
        assertEquals(before, commands)
    }
    @Test fun restoreUsesSessionBaselineNotLastRefresh() {
        val d = Device(); val c = d.controller(); c.connect(); c.refresh()
        c.apply(mapOf(0 to 1286000L)); c.refresh()
        assertTrue(c.restoreSession().success); assertEquals(2515000L, d.caps[0])
    }
}
