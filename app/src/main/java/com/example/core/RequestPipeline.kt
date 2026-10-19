package com.example.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.TimeZone

/** What a request produced. [Ok.fromCache] is true when no network call was made. */
sealed interface PipelineResult<out T> {
    data class Ok<T>(val value: T, val fromCache: Boolean = false) : PipelineResult<T>
    data class Fail(val reason: String) : PipelineResult<Nothing>
}

interface ResultCache<T> {
    suspend fun get(key: String): T?
    suspend fun put(key: String, value: T)
}

/** Remembers when requests were sent, so pacing and the "requests today" counter survive restarts. */
interface UsageLog {
    suspend fun requestTimes(since: Long): List<Long>
    suspend fun record(at: Long)
}

object QuotaPolicy {
    const val WINDOW_MILLIS = 60_000L

    /** How long to wait so that at most [perMinute] requests fall inside any 60 second window. 0 = go now. */
    fun waitMillis(recent: List<Long>, now: Long, perMinute: Int): Long {
        if (perMinute <= 0) return 0L
        val inWindow = recent.filter { it > now - WINDOW_MILLIS }.sorted()
        if (inWindow.size < perMinute) return 0L
        val blocking = inWindow[inWindow.size - perMinute]
        return (blocking + WINDOW_MILLIS - now + 1).coerceAtLeast(0L)
    }

    /** Google resets daily quotas at midnight Pacific time. */
    fun quotaDayStart(now: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"))
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

/**
 * The layer between the screens and the network:
 *  1. identical request already answered  -> served from the cache, no quota used;
 *  2. identical request in flight          -> callers share the single call;
 *  3. otherwise paced to [perMinute] and computed; only successes are cached (a failure is never remembered).
 *
 * The shared call runs in [scope], so one caller leaving the screen does not cancel it for the others.
 */
class RequestPipeline<T : Any>(
    private val cache: ResultCache<T>?,
    private val usage: UsageLog?,
    private val scope: CoroutineScope,
    private val perMinute: Int = DEFAULT_PER_MINUTE,
    private val maxWaitMillis: Long = DEFAULT_MAX_WAIT_MILLIS,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) {
    private val lock = Any()
    private val inFlight = HashMap<String, Deferred<PipelineResult<T>>>()

    suspend fun run(key: String, compute: suspend () -> PipelineResult<T>): PipelineResult<T> {
        cache?.get(key)?.let { return PipelineResult.Ok(it, fromCache = true) }

        // Pair(deferred, startedByThisCall)
        val (deferred, mine) = synchronized(lock) {
            val existing = inFlight[key]
            if (existing != null) {
                existing to false
            } else {
                val fresh = scope.async(start = CoroutineStart.LAZY) {
                    try {
                        execute(key, compute)
                    } finally {
                        synchronized(lock) { inFlight.remove(key) }
                    }
                }
                inFlight[key] = fresh
                fresh to true
            }
        }
        if (mine) deferred.start()
        return deferred.await()
    }

    private suspend fun execute(key: String, block: suspend () -> PipelineResult<T>): PipelineResult<T> {
        // Another caller may have finished and cached the same answer while this one was queued.
        cache?.get(key)?.let { return PipelineResult.Ok(it, fromCache = true) }

        if (usage != null) {
            val wait = QuotaPolicy.waitMillis(usage.requestTimes(clock() - QuotaPolicy.WINDOW_MILLIS), clock(), perMinute)
            if (wait > maxWaitMillis) {
                return PipelineResult.Fail("وصلت لحد الطلبات في الدقيقة. انتظر نحو ${(wait + 999) / 1000} ثانية وحاول مرة أخرى.")
            }
            if (wait > 0) sleep(wait)
            usage.record(clock())
        }
        val result = block()
        if (result is PipelineResult.Ok && !result.fromCache) cache?.put(key, result.value)
        return result
    }

    companion object {
        const val DEFAULT_PER_MINUTE = 10
        const val DEFAULT_MAX_WAIT_MILLIS = 20_000L
    }
}
