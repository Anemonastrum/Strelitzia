package io.github.anemonastrum.strelitzia

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RootShellTest {
    private fun shell(timeout: Long = 1000) = RootShell(timeoutMs = timeout, launch = { ProcessBuilder("sh", "-c", it).start() })
    @Test fun capturesExitAndSeparateStreams() {
        val r = shell().run("printf hello; printf denied >&2; exit 7")
        assertEquals(7, r.exitCode); assertEquals("hello", r.stdout); assertEquals("denied", r.stderr)
        assertEquals(0, shell().run(":").exitCode)
    }
    @Test fun missingSuIsReported() {
        val r = RootShell(launch = { throw IOException("missing su") }).run("id -u")
        assertNotEquals(0, r.exitCode); assertTrue(r.stderr.contains("missing su"))
    }
    @Test fun timeoutKillsProcessAndReturnsBoundedly() {
        val start = System.nanoTime()
        val r = shell(100).run("exec sleep 10")
        assertNotEquals(0, r.exitCode); assertTrue(r.stderr.contains("timed out"))
        assertTrue((System.nanoTime() - start) / 1_000_000 < 3000)
    }
    @Test fun floodIsBoundedAndNotSuccessful() {
        val r = shell().run("yes x")
        assertNotEquals(0, r.exitCode)
        assertTrue(r.stdout.length <= 65536)
    }
}
