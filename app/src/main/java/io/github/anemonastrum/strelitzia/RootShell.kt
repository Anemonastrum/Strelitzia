package io.github.anemonastrum.strelitzia

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

class RootShell(
    private val timeoutMs: Long = 15_000,
    private val launch: (String) -> Process = { ProcessBuilder("su", "-c", it).start() },
) {
    fun run(command: String): ShellResult {
        val process = try { launch(command) } catch (e: Exception) {
            return ShellResult(-1, "", e.message ?: "Cannot start su")
        }
        val overflow = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "root-output").apply { isDaemon = true } }
        fun collect(input: InputStream): String {
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            input.use {
                while (true) {
                    val size = it.read(buffer)
                    if (size < 0) break
                    val allowed = minOf(size, 65536 - result.size())
                    result.write(buffer, 0, allowed)
                    if (allowed < size) { overflow.set(true); process.destroyForcibly(); break }
                }
            }
            return result.toString("UTF-8")
        }
        val out = pool.submit<String> { collect(process.inputStream) }
        val err = pool.submit<String> { collect(process.errorStream) }
        try {
            process.outputStream.close()
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return ShellResult(-1, "", "Root command timed out; inspect current caps before retrying")
            }
            val stdout = out.get(1, TimeUnit.SECONDS)
            val stderr = err.get(1, TimeUnit.SECONDS)
            return if (overflow.get()) ShellResult(-1, stdout, "Root output exceeded 64 KiB")
            else ShellResult(process.exitValue(), stdout, stderr)
        } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
            return ShellResult(-1, "", e.message ?: "Root command failed")
        } finally {
            process.destroyForcibly()
            out.cancel(true); err.cancel(true)
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            pool.shutdownNow()
        }
    }
}
