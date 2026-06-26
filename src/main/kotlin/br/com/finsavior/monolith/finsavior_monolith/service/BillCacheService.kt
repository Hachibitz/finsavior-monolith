package br.com.finsavior.monolith.finsavior_monolith.service

import com.github.benmanes.caffeine.cache.Cache as CaffeineCache
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentMap

/**
 * Centralizes per-user cache eviction for the bill tables. Kept separate from
 * [BillService] so it can be reused by the fixed-bill scheduler without creating
 * a circular dependency. Supports both the default ConcurrentMap cache and the
 * Caffeine cache (see CacheConfig).
 */
@Service
class BillCacheService(
    private val cacheManager: CacheManager
) {

    companion object {
        val BILL_CACHES = listOf("mainTable", "cardTable", "cardExpenses", "assetsTable", "paymentCardTable")
    }

    @Suppress("UNCHECKED_CAST")
    fun evictUserCaches(userId: Long) {
        val prefix = "$userId-"
        for (cacheName in BILL_CACHES) {
            val nativeCache = cacheManager.getCache(cacheName)?.nativeCache ?: continue
            val backingMap: ConcurrentMap<Any, Any>? = when (nativeCache) {
                is CaffeineCache<*, *> -> (nativeCache as CaffeineCache<Any, Any>).asMap()
                is ConcurrentMap<*, *> -> nativeCache as ConcurrentMap<Any, Any>
                else -> null
            }
            if (backingMap == null) continue

            backingMap.keys
                .filter { it.toString().startsWith(prefix) }
                .forEach { backingMap.remove(it) }
        }
    }
}
