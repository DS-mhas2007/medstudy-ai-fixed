package com.example.data.remote

import com.example.core.AiResultJson
import com.example.core.QuotaPolicy
import com.example.core.ResultCache
import com.example.core.UsageLog
import com.example.data.local.AiCacheDao
import com.example.data.local.AiCacheEntity
import com.example.data.local.AiUsageDao
import com.example.data.local.AiUsageEntity
import com.example.data.local.MedStudyDatabase

/**
 * Room-backed response cache and request log used by [MedicalAiService].
 * Entries expire after [ttlMillis] and the cache keeps at most [maxEntries] (least recently used go first).
 */
class AiStore(
    private val cacheDao: AiCacheDao,
    private val usageDao: AiUsageDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = THIRTY_DAYS,
    private val maxEntries: Int = 300
) {
    constructor(database: MedStudyDatabase) : this(database.aiCacheDao(), database.aiUsageDao())

    val cache: ResultCache<MedicalAiResult> = object : ResultCache<MedicalAiResult> {
        override suspend fun get(key: String): MedicalAiResult? {
            val now = clock()
            val row = cacheDao.find(key, now - ttlMillis) ?: return null
            val decoded = AiResultJson.decode(row.payload)
            if (decoded == null) {
                // Unreadable (older format or corrupt): drop it so the next request refreshes it.
                cacheDao.delete(key)
                return null
            }
            cacheDao.touch(key, now)
            return decoded
        }

        override suspend fun put(key: String, value: MedicalAiResult) {
            if (value.isOfflineGenerated) return
            val now = clock()
            cacheDao.upsert(AiCacheEntity(key, AiResultJson.encode(value.copy(fromCache = false)), now, now))
            cacheDao.deleteOlderThan(now - ttlMillis)
            val extra = cacheDao.count() - maxEntries
            if (extra > 0) cacheDao.deleteOldest(extra)
        }
    }

    val usage: UsageLog = object : UsageLog {
        override suspend fun requestTimes(since: Long): List<Long> = usageDao.since(since)

        override suspend fun record(at: Long) {
            usageDao.insert(AiUsageEntity(requestedAt = at))
            usageDao.deleteBefore(at - 2 * DAY)
        }
    }

    /** Requests sent since the last quota reset (midnight Pacific time). */
    suspend fun requestsToday(): Int = usageDao.countSince(QuotaPolicy.quotaDayStart(clock()))

    suspend fun cacheSize(): Int = cacheDao.count()

    suspend fun clearCache() = cacheDao.clear()

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val THIRTY_DAYS = 30 * DAY
    }
}
