package br.com.finsavior.monolith.finsavior_monolith.assync.consumer

import br.com.finsavior.monolith.finsavior_monolith.model.dto.DeleteAccountRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.service.UserService
import mu.KLogger
import mu.KotlinLogging
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service

@Service
class DeleteAccountConsumer(
    private val userService: UserService
) {

    private val log: KLogger = KotlinLogging.logger {}

    @RabbitListener(queues = ["\${rabbitmq.queue.delete-account:delete.account.queue}"])
    fun listen(message: DeleteAccountRequestDTO) {
        log.info("Message consumed: $message")
        userService.deleteAccount(message)
    }
}