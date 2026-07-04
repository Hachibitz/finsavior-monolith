package br.com.finsavior.monolith.finsavior_monolith.service

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service
class RateLimitService {

    private val registry: RateLimiterRegistry
    private val limiters: MutableMap<String, RateLimiter> = ConcurrentHashMap()

    init {
        val config = RateLimiterConfig.custom()
            .limitForPeriod(3)
            .limitRefreshPeriod(Duration.ofHours(1))
            .timeoutDuration(Duration.ZERO)
            .build()
        registry = RateLimiterRegistry.of(config)
    }

    /** Default limiter: 3 requests per hour per key. */
    fun tryConsume(key: String): Boolean {
        val limiter = limiters.computeIfAbsent(key) { registry.rateLimiter(it) }
        return limiter.acquirePermission()
    }

    /** Custom limiter per key (e.g. login attempts, coin earning). */
    fun tryConsume(key: String, limitForPeriod: Int, refreshPeriod: Duration): Boolean {
        val limiter = limiters.computeIfAbsent(key) {
            registry.rateLimiter(
                it,
                RateLimiterConfig.custom()
                    .limitForPeriod(limitForPeriod)
                    .limitRefreshPeriod(refreshPeriod)
                    .timeoutDuration(Duration.ZERO)
                    .build()
            )
        }
        return limiter.acquirePermission()
    }
}
