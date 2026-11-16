package io.github.anemonastrum.strelitzia

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

class RootShell(
    private val timeoutMs: Long = 15_000,
    private val launch: (String) -> Process = { ProcessBuilder("su", "-c", it).start() },
) {
    private var blocked = false

    @Synchronized fun run(command: String): ShellResult {
        if (blocked) return ShellResult(-2, "", "Root commands blocked: prior command state indeterminate. Reboot device before retrying.")
        val process = try { launch(command) } catch (e: Exception) {
            return ShellResult(-1, "", e.message ?: "Cannot start su")
        }
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        fun drain(input: InputStream, destination: ByteArrayOutputStream) {
            // Poll only bytes already available: descendants may retain pipes after su exits.
            while (input.available() > 0) {
                if (System.nanoTime() >= deadline) throw java.io.IOException("Root command timed out")
                val size = input.read(buffer, 0, minOf(buffer.size, input.available()))
                if (size <= 0) break
                if (destination.size() + size > 65536) throw java.io.IOException("Root output exceeded 64 KiB")
                destination.write(buffer, 0, size)
            }
        }
        try {
            process.outputStream.close()
            while (true) {
                drain(process.inputStream, out)
                drain(process.errorStream, err)
                if (process.waitFor(10, TimeUnit.MILLISECONDS)) {
                    drain(process.inputStream, out)
                    drain(process.errorStream, err)
                    return ShellResult(process.exitValue(), out.toString("UTF-8"), err.toString("UTF-8"))
                }
                if (System.nanoTime() >= deadline) throw java.io.IOException("Root command timed out")
            }
        } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
            // Killing su does not prove its daemon-owned privileged command has stopped.
            blocked = true
            return ShellResult(-2, out.toString("UTF-8"), "${e.message ?: "Root command failed"}; state indeterminate. Root commands blocked. Reboot device before retrying.")
        } finally {
            process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
    }
}
