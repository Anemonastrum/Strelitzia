package io.github.anemonastrum.strelitzia

data class OperationState(
    val root: Boolean = false,
    val busy: Boolean = false,
    val policies: List<CpuPolicy> = emptyList(),
    val message: String = "Connect root to change CPU caps. Read-only information needs no root.",
) {
    fun begin(): OperationState? = if (busy) null else copy(busy = true)
    fun failed(reason: String): OperationState = copy(busy = false, message = "Operation failed: $reason")
}
