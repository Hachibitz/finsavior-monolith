package br.com.finsavior.monolith.finsavior_monolith.assync.producer

import br.com.finsavior.monolith.finsavior_monolith.model.dto.DeleteAccountRequestDTO
import mu.KLogger
import mu.KotlinLogging
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class DeleteAccountProducer(
    private val rabbitTemplate: RabbitTemplate
) {

    private val log: KLogger = KotlinLogging.logger {}

    companion object {
        const val DELETE_ACCOUNT_QUEUE = "delete.account.queue"
    }

    fun sendMessage(message: DeleteAccountRequestDTO) {
        log.info("Sending message to: $DELETE_ACCOUNT_QUEUE")
        rabbitTemplate.convertAndSend(
            "", // exchange default
            DELETE_ACCOUNT_QUEUE,
            message
        )
    }
}