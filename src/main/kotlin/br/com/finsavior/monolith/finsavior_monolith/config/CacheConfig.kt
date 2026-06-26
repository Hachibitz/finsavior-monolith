package br.com.finsavior.monolith.finsavior_monolith.config

import com.github.benmanes.caffeine.cache.Caffeine
import br.com.finsavior.monolith.finsavior_monolith.service.BillCacheService
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {

    /**
     * Bounded, self-expiring cache for the bill-table loaders. Previously the
     * default ConcurrentMap cache grew without limit (entries were only removed
     * on writes by the same user), which is a memory risk under many concurrent
     * users. Caffeine bounds the size and adds a TTL so stale entries self-heal.
     */
    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager(*BillCacheService.BILL_CACHES.toTypedArray())
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(Duration.ofMinutes(30))
        )
        return cacheManager
    }
}
