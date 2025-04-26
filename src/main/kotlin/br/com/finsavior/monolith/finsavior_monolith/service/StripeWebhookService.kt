package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.assync.producer.WebhookProducer
import br.com.finsavior.monolith.finsavior_monolith.exception.PlanChangeException
import br.com.finsavior.monolith.finsavior_monolith.exception.WebhookException
import br.com.finsavior.monolith.finsavior_monolith.integration.client.StripeClient
import br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripeCheckoutSessionCompletedEvent
import br.com.finsavior.monolith.finsavior_monolith.model.dto.WebhookRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.EventTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toExternalUser
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toWebhookRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.repository.ExternalUserRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import br.com.finsavior.monolith.finsavior_monolith.service.strategy.WebhookService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stripe.net.Webhook
import mu.KLogger
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StripeWebhookService(
    private val userService: UserService,
    private val webhookProducer: WebhookProducer,
    @Value("\${stripe.webhook-secret}") private val endpointSecret: String,
    private val stripeClient: StripeClient,
    private val externalUserRepository: ExternalUserRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService
) : WebhookService {

    private val log: KLogger = KotlinLogging.logger {}

    override fun sendMessage(
        webhookRequestDTO: WebhookRequestDTO
    ) {
        try {
            webhookProducer.sendMessage(webhookRequestDTO)
        } catch (e: Exception) {
            WebhookException(e.message.toString(), e)
        }
    }

    @Transactional
    override fun processWebhook(webhookRequestDTO: WebhookRequestDTO) {
        runCatching {
            val user: User = userRepository.findById(webhookRequestDTO.userId!!)
                .orElseThrow { IllegalArgumentException("Usuário não encontrado") }
            val previousExternalUser = externalUserRepository.findByUserId(user.id)
            val alreadyUsedTrial = previousExternalUser?.isTrialUsed == true

            when (webhookRequestDTO.eventType) {
                EventTypeEnum.CHECKOUT_SESSION_COMPLETED -> {
                    userService.updatePlanByEmail(webhookRequestDTO.email!!, webhookRequestDTO.planType!!.id)

                    val subscription = stripeClient.getSubscription(webhookRequestDTO.subscriptionId!!)
                    val isTrialInThisSession = subscription.trialStart != null

                    val finalTrialStatus = alreadyUsedTrial || isTrialInThisSession

                    previousExternalUser?.let { externalUserRepository.delete(it) }

                    externalUserRepository.save(
                        webhookRequestDTO.toExternalUser().apply { this.isTrialUsed = finalTrialStatus }
                    )
                }

                EventTypeEnum.CUSTOMER_SUBSCRIPTION_DELETED -> {
                    userService.downgradeToFree(webhookRequestDTO.email)

                    previousExternalUser?.let { externalUserRepository.delete(it) }

                    externalUserRepository.save(
                        webhookRequestDTO.toExternalUser().apply { this.isTrialUsed = alreadyUsedTrial }
                    )
                }

                EventTypeEnum.INVOICE_PAYMENT_FAILED -> {
                    emailService.sendInvoicePaymentFailedEmail(webhookRequestDTO.email!!)
                }

                EventTypeEnum.CUSTOMER_SUBSCRIPTION_UPDATED -> {
                    if (webhookRequestDTO.planType == null) {
                        throw IllegalArgumentException("PlanType é obrigatório para atualização de assinatura")
                    }

                    val currentUserPlanId = user.userPlan?.plan?.id
                    val newPlanType = webhookRequestDTO.planType!!.id

                    if (currentUserPlanId == newPlanType) {
                        log.info("Plano já está atualizado para ${webhookRequestDTO.planType}")
                        return
                    }

                    if (webhookRequestDTO.planType == PlanTypeEnum.FREE) {
                        userService.downgradeToFree(webhookRequestDTO.email)
                    } else {
                        userService.updatePlanByEmail(webhookRequestDTO.email!!, webhookRequestDTO.planType!!.id)
                    }

                    previousExternalUser?.let { externalUserRepository.delete(it) }

                    externalUserRepository.save(
                        webhookRequestDTO.toExternalUser().apply { this.isTrialUsed = alreadyUsedTrial }
                    )
                }

                else -> throw PlanChangeException("Event type not supported: ${webhookRequestDTO.eventType}")
            }
        }.getOrElse { e ->
            throw PlanChangeException(e.message.toString(), e)
        }
    }


    fun processWebhookStart(payload: String, sigHeader: String): ResponseEntity<String> {
        return runCatching {
            val event = Webhook.constructEvent(payload, sigHeader, endpointSecret)
            val eventType = event.type
            val mapper = jacksonObjectMapper()

            val webhookRequestDTO: WebhookRequestDTO = when (eventType) {
                EventTypeEnum.CHECKOUT_SESSION_COMPLETED.value -> {
                    val dto = mapper.readValue(payload, StripeCheckoutSessionCompletedEvent::class.java)
                    val session = dto.data.session

                    val subscriptionId = session.subscriptionId
                    val subscription = stripeClient.getSubscription(subscriptionId)
                    val productId = subscription.items.data.firstOrNull()?.price?.product

                    val planType = PlanTypeEnum.fromProductId(productId ?: "")
                    val email = session.customerDetails.email ?: throw IllegalStateException("Email não encontrado")
                    val user = userRepository.findByEmail(email) ?: throw IllegalArgumentException("Usuário não encontrado")

                    session.toWebhookRequestDTO(eventType, planType!!, subscriptionId, user.id)
                }

                EventTypeEnum.CUSTOMER_SUBSCRIPTION_UPDATED.value -> {
                    val dto = mapper.readTree(payload)
                    val subscription = dto["data"]["object"]
                    val subscriptionId = subscription["id"].asText()
                    val customerId = subscription["customer"].asText()
                    val email = stripeClient.getCustomer(customerId).email
                    val user = userRepository.findByEmail(email!!) ?: throw IllegalArgumentException("Usuário não encontrado")
                    val productId = subscription["items"]["data"][0]["price"]["product"].asText()

                    val planType = PlanTypeEnum.fromProductId(productId ?: "")

                    WebhookRequestDTO(
                        eventType = EventTypeEnum.fromValue(eventType),
                        email = email,
                        userId = user.id,
                        planType = planType,
                        subscriptionId = subscriptionId,
                        externalUserId = customerId
                    )
                }

                EventTypeEnum.CUSTOMER_SUBSCRIPTION_DELETED.value -> {
                    val dto = mapper.readTree(payload)
                    val subscription = dto["data"]["object"]
                    val subscriptionId = subscription["id"].asText()
                    val customerId = subscription["customer"].asText()
                    val email = stripeClient.getCustomer(customerId).email
                    val user = userRepository.findByEmail(email!!) ?: throw IllegalArgumentException("Usuário não encontrado")

                    WebhookRequestDTO(
                        eventType = EventTypeEnum.fromValue(eventType),
                        email = email,
                        userId = user.id,
                        planType = PlanTypeEnum.FREE, // downgrade automático
                        subscriptionId = subscriptionId,
                        externalUserId = customerId
                    )
                }

                EventTypeEnum.INVOICE_PAYMENT_FAILED.value -> {
                    val dto = mapper.readTree(payload)
                    val subscriptionId = dto["data"]["object"]["subscription"].asText()
                    val subscription = stripeClient.getSubscription(subscriptionId)
                    val customerId = subscription.customer
                    val email = stripeClient.getCustomer(customerId).email
                    val user = userRepository.findByEmail(email!!) ?: throw IllegalArgumentException("Usuário não encontrado")
                    val productId = subscription.items.data.firstOrNull()?.price?.product
                    val planType = PlanTypeEnum.fromProductId(productId ?: "")

                    WebhookRequestDTO(
                        eventType = EventTypeEnum.fromValue(eventType),
                        email = email,
                        userId = user.id,
                        planType = planType,
                        subscriptionId = subscriptionId,
                        externalUserId = customerId
                    )
                }

                else -> throw IllegalArgumentException("Evento do Stripe não tratado: $eventType")
            }

            sendMessage(webhookRequestDTO)

            log.info("Webhook recebido e enviado para fila: tipo=$eventType, email=${webhookRequestDTO.email}")
            ResponseEntity.ok("")
        }.getOrElse { e ->
            log.error("Erro ao processar webhook: ${e.message}", e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature or processing error")
        }
    }
}