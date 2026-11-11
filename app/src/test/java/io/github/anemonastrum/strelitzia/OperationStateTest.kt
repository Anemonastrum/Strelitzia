package io.github.anemonastrum.strelitzia

import org.junit.Assert.*
import org.junit.Test

class OperationStateTest {
    @Test fun newStateIsReadOnlyAndNeverAppliesAnything() {
        val state = OperationState()
        assertFalse(state.root); assertFalse(state.busy); assertTrue(state.policies.isEmpty())
        assertTrue(state.message.contains("Connect"))
    }
    @Test fun concurrentOperationIsRejectedAndFailureReleasesBusyState() {
        val started = OperationState().begin()!!
        assertTrue(started.busy)
        assertNull(started.begin())
        val failed = started.failed("read denied")
        assertFalse(failed.busy)
        assertTrue(failed.message.contains("read denied"))
        assertNotNull(failed.begin())
    }
    @Test fun presetExplainsMissingOrUnsafePolicies() {
        assertTrue(presetProblem(emptyList())!!.contains("policy0"))
        val policies = PRESET.map { (id, value) -> CpuPolicy(id, "$id", value, 300000, value, 300000, 2515000, listOf(value), "schedutil") }
        assertNull(presetProblem(policies))
        assertNotNull(presetProblem(policies.map { it.copy(minKhz = 2515000) }))
    }
}
