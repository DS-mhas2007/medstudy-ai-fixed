package com.example.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RequestPipelineTest {
    private class MemCache : ResultCache<String> {
        val map = HashMap<String, String>()
        override suspend fun get(key: String) = synchronized(map) { map[key] }
        override suspend fun put(key: String, value: String) { synchronized(map) { map[key] = value } }
    }

    private class MemUsage : UsageLog {
        val times = ArrayList<Long>()
        override suspend fun requestTimes(since: Long) = synchronized(times) { times.filter { it >= since } }
        override suspend fun record(at: Long) { synchronized(times) { times.add(at) } }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun secondIdenticalRequestIsServedFromCacheWithoutComputing() = runBlocking {
        val calls = AtomicInteger()
        val pipeline = RequestPipeline<String>(MemCache(), MemUsage(), scope)
        val first = pipeline.run("k") { calls.incrementAndGet(); PipelineResult.Ok("answer") }
        val second = pipeline.run("k") { calls.incrementAndGet(); PipelineResult.Ok("other") }
        assertEquals(PipelineResult.Ok("answer", fromCache = false), first)
        assertEquals(PipelineResult.Ok("answer", fromCache = true), second)
        assertEquals(1, calls.get())
    }

    @Test
    fun failuresAreNeverCached() = runBlocking {
        val calls = AtomicInteger()
        val pipeline = RequestPipeline<String>(MemCache(), MemUsage(), scope)
        val first = pipeline.run("k") { calls.incrementAndGet(); PipelineResult.Fail("network") }
        val second = pipeline.run("k") { calls.incrementAndGet(); PipelineResult.Ok("ok") }
        assertTrue(first is PipelineResult.Fail)
        assertEquals(PipelineResult.Ok("ok", fromCache = false), second)
        assertEquals(2, calls.get())
    }

    @Test
    fun concurrentIdenticalRequestsShareOneCall() = runBlocking {
        val calls = AtomicInteger()
        val pipeline = RequestPipeline<String>(MemCache(), MemUsage(), scope)
        val results = (1..8).map {
            async(Dispatchers.Default) {
                pipeline.run("same") {
                    calls.incrementAndGet()
                    delay(150)
                    PipelineResult.Ok("shared")
                }
            }
        }.awaitAll()
        assertEquals(1, calls.get())
        assertTrue(results.all { it is PipelineResult.Ok && it.value == "shared" })
    }

    @Test
    fun differentKeysDoNotShare() = runBlocking {
        val calls = AtomicInteger()
        val pipeline = RequestPipeline<String>(MemCache(), MemUsage(), scope)
        pipeline.run("a") { calls.incrementAndGet(); PipelineResult.Ok("1") }
        pipeline.run("b") { calls.incrementAndGet(); PipelineResult.Ok("2") }
        assertEquals(2, calls.get())
    }

    @Test
    fun pacesWhenTheMinuteIsFull() = runBlocking {
        var now = 1_000_000L
        val slept = ArrayList<Long>()
        val usage = MemUsage().also { u -> repeat(3) { runBlocking { u.record(now - 10_000L) } } }
        val pipeline = RequestPipeline<String>(
            null, usage, scope, perMinute = 3, maxWaitMillis = 60_000,
            clock = { now }, sleep = { slept.add(it); now += it }
        )
        val r = pipeline.run("k") { PipelineResult.Ok("done") }
        assertTrue(r is PipelineResult.Ok)
        assertEquals(1, slept.size)
        assertEquals(50_001L, slept[0])
    }

    @Test
    fun refusesWhenTheWaitIsTooLong() = runBlocking {
        val now = 1_000_000L
        val usage = MemUsage().also { u -> repeat(2) { runBlocking { u.record(now - 1_000L) } } }
        val calls = AtomicInteger()
        val pipeline = RequestPipeline<String>(null, usage, scope, perMinute = 2, maxWaitMillis = 5_000, clock = { now })
        val r = pipeline.run("k") { calls.incrementAndGet(); PipelineResult.Ok("x") }
        assertTrue(r is PipelineResult.Fail)
        assertEquals(0, calls.get())
    }

    @Test
    fun quotaPolicyBoundaries() {
        assertEquals(0L, QuotaPolicy.waitMillis(emptyList(), 100_000, 10))
        assertEquals(0L, QuotaPolicy.waitMillis(listOf(1L, 2L), 100_000, 3))
        // 3 requests in the window, limit 3: wait until the oldest leaves the window
        assertEquals(10_001L, QuotaPolicy.waitMillis(listOf(50_000L, 60_000L, 70_000L), 100_000, 3).let { it })
        assertEquals(0L, QuotaPolicy.waitMillis(listOf(1L), 100_000, 0))
    }

    @Test
    fun quotaDayStartIsAtMostADayBeforeNow() {
        val now = 1_800_000_000_000L
        val start = QuotaPolicy.quotaDayStart(now)
        assertTrue(start <= now)
        assertTrue(now - start < 25L * 60 * 60 * 1000)
    }
}
