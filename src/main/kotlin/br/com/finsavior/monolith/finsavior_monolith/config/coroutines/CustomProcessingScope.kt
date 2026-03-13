package br.com.finsavior.monolith.finsavior_monolith.config.coroutines

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.stereotype.Component

@Component
class CustomProcessingScope : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Default + job

    @PreDestroy
    fun cleanup() {
        job.cancel()
    }
}
