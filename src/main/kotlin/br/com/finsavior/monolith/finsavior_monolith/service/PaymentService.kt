package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.PaymentErrorCode
import br.com.finsavior.monolith.finsavior_monolith.exception.PaymentException
import br.com.finsavior.monolith.finsavior_monolith.exception.PlanChangeException
import br.com.finsavior.monolith.finsavior_monolith.integration.client.StripeClient
import br.com.finsavior.monolith.finsavior_monolith.model.dto.CheckoutSessionDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ExternalUserDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.SubscriptionDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ExternalUser
import br.com.finsavior.monolith.finsavior_monolith.model.entity.PlanChangeHistory
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CommonEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.ExternalProvider
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.SubscriptionStatusEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.ExternalUserRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import com.stripe.model.Customer
import com.stripe.model.checkout.Session
import com.stripe.param.CustomerListParams
import com.stripe.param.checkout.SessionCreateParams
import mu.KLogger
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class PaymentService(
    @Lazy private val userService: UserService,
    private val externalUserRepository: ExternalUserRepository,
    private val planHistoryRepository: PlanHistoryRepository,
    private val stripeClient: StripeClient,
    private val userRepository: UserRepository,
    @Value ("\${finsavior.host-url}") private val finsaviorHostUrl: String,
) {

    val log: KLogger = KotlinLogging.logger {}

    @Transactional
    fun createSubscription(request: SubscriptionDTO) {
        try {
            val userId: Long = userService.getUserByContext().id!!
            saveExternalUser(request, userId)
            updatePlanHistory(request, userId)
        } catch (e: Exception) {
            log.error("Falha ao criar a subscrição: ${e.message}")
            throw PaymentException(e.message.toString(), e)
        }
    }

    fun updateUserPlan(externalUserDto: ExternalUserDTO) {
        try {
            val user: Optional<User> = userRepository.findById(externalUserDto.userId)
            val planId: String? = externalUserDto.planId

            val planChangeHistory: PlanChangeHistory = getPlanChangeHistory(externalUserDto, planId!!)
            setUserPlan(user.get(), planId)

            userRepository.save(user.get())

            planHistoryRepository.save(planChangeHistory)

            log.info(
                "method = updateUserPlan, message = Plano do user: {}, atualizado com sucesso!",
                externalUserDto.userId
            )
        } catch (e: Exception) {
            throw PlanChangeException(e.message!!, e)
        }
    }

    fun createCheckoutSession(
        planType: String,
        email: String,
        hostedCheckout: Boolean = false,
        preferredCurrency: String? = null
    ): CheckoutSessionDTO {
        val user = userService.getUserByContext()
        val productId = getPlanIdByPlanType(planType)
        val expectedInterval = if (planType.contains("ANNUAL")) "year" else "month"
        val selectedPrice = selectPriceForProduct(productId, expectedInterval, preferredCurrency)
        val priceId = selectedPrice.id
        val userId = user.id!!
        val externalUser = externalUserRepository.findByUserId(userId)
        ensureNoConflictingProvider(user, externalUser, ExternalProvider.GOOGLE_PLAY)

        val sessionBuilder = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomerEmail(email)
            .setClientReferenceId(userId.toString())
            .putMetadata("userId", userId.toString())
            .putMetadata("planType", planType)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build()
            )

        val normalizedPreferred = preferredCurrency?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (normalizedPreferred != null && supportsCurrency(selectedPrice, normalizedPreferred)) {
            sessionBuilder.setCurrency(normalizedPreferred)
        }

        if (externalUser?.isTrialUsed != true) {
            sessionBuilder.setSubscriptionData(
                SessionCreateParams.SubscriptionData.builder()
                    .setTrialPeriodDays(7)
                    .build()
            )
        }

        if (hostedCheckout) {
            sessionBuilder
                .setSuccessUrl("$finsaviorHostUrl?checkout=success&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl("$finsaviorHostUrl?checkout=cancel")
        } else {
            sessionBuilder
                .setUiMode(SessionCreateParams.UiMode.EMBEDDED)
                .setRedirectOnCompletion(SessionCreateParams.RedirectOnCompletion.NEVER)
        }

        val session = Session.create(sessionBuilder.build())

        return CheckoutSessionDTO(url = session.url, clientSecret = session.clientSecret)
    }

    fun createCustomerPortalSession(email: String): Map<String, String> {
        // The client-supplied email is ignored: the portal must always belong to
        // the authenticated user, otherwise anyone could open another customer's
        // Stripe billing portal just by guessing their e-mail.
        val ownEmail = userService.getUserByContext().email
        val customer = Customer.list(
            CustomerListParams.builder()
                .setEmail(ownEmail)
                .setLimit(1)
                .build()
        ).data.firstOrNull() ?: throw IllegalStateException("Cliente Stripe não encontrado para o usuário autenticado")

        val params = com.stripe.param.billingportal.SessionCreateParams.builder()
            .setCustomer(customer.id)
            .setReturnUrl("$finsaviorHostUrl/my-account")
            .build()

        val session = com.stripe.model.billingportal.Session.create(params)

        return mapOf("url" to session.url)
    }

    fun updateSubscription(planType: String, email: String) {
        val user = userService.getUserByContext()
        val externalUser = requireStripeSubscription(user)
        val subscriptionId = externalUser.subscriptionId ?: throw IllegalStateException("Usuário não possui assinatura ativa")

        val stripeSubscription = stripeClient.getSubscription(subscriptionId)
        val subscriptionItemId = stripeSubscription.items.data.firstOrNull()?.id
            ?: throw IllegalStateException("Subscription item não encontrado na assinatura do usuário")

        val planEnum = PlanTypeEnum.valueOf(planType)
        val productId = planEnum.id
        val expectedInterval = if (planType.contains("ANNUAL")) "year" else "month"
        val newPriceId = selectPriceForProduct(productId, expectedInterval, null).id

        stripeClient.updateSubscriptionPrice(subscriptionId, subscriptionItemId, newPriceId)
        
        user.userPlan?.subscriptionStatus = SubscriptionStatusEnum.ACTIVE
        userRepository.save(user)
    }

    fun cancelSubscription(immediate: Boolean) {
        if (immediate) {
            throw PaymentException(
                "Cancelamento imediato só é permitido durante a troca para a Google Play.",
                PaymentErrorCode.GENERIC
            )
        }

        val user = userService.getUserByContext()
        val externalUser = requireStripeSubscription(user)
        val subscriptionId = externalUser.subscriptionId
            ?: throw IllegalArgumentException("Usuário não possui assinatura ativa")

        stripeClient.cancelAtPeriodEnd(subscriptionId, true)
        user.userPlan?.subscriptionStatus = SubscriptionStatusEnum.CANCELED_AT_PERIOD_END
        userRepository.save(user)
    }

    fun reactivateSubscription() {
        val user = userService.getUserByContext()

        val externalUser = requireStripeSubscription(user)

        val subscriptionId = externalUser.subscriptionId
            ?: throw IllegalArgumentException("Usuário não possui assinatura ativa")

        stripeClient.cancelAtPeriodEnd(subscriptionId, false)
        
        user.userPlan?.subscriptionStatus = SubscriptionStatusEnum.ACTIVE
        userRepository.save(user)
        
        log.info("Assinatura reativada com sucesso para o usuário ${user.id}")
    }

    fun getPlans() =
        stripeClient.listPlans()

    fun cancelStripeImmediatelyForProviderSwitch(user: User, existingStripeExternalUser: ExternalUser? = null) {
        val externalUser = existingStripeExternalUser ?: requireStripeSubscription(user)

        if (externalUser.externalProvider != ExternalProvider.STRIPE) {
            throw IllegalArgumentException("Assinatura Stripe não encontrada para troca de provedor")
        }

        val subscriptionId = externalUser.subscriptionId
            ?: throw IllegalArgumentException("Usuário não possui assinatura ativa")

        stripeClient.deleteSubscriptionImmediately(subscriptionId)
        externalUserRepository.findByUserId(user.id!!)?.takeIf { it.externalProvider == ExternalProvider.STRIPE }?.let {
            externalUserRepository.delete(it)
        }
        log.info { "Stripe subscription canceled immediately for provider switch user=${user.id}" }
    }

    private fun selectPriceForProduct(
        productId: String,
        expectedInterval: String,
        preferredCurrency: String?
    ): br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripePrice {
        val activePrices = stripeClient.getPricesByProduct(productId, active = true)
            .data
            .filter { it.active != false }

        if (activePrices.isEmpty()) {
            throw IllegalStateException("Nenhum preço ativo encontrado para o produto: $productId")
        }

        val intervalMatches = activePrices.filter {
            it.recurring?.interval.equals(expectedInterval, ignoreCase = true)
        }.ifEmpty { activePrices }

        val normalizedCurrency = preferredCurrency?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (normalizedCurrency != null) {
            intervalMatches.firstOrNull { supportsCurrency(it, normalizedCurrency) }?.let { return it }
        }

        return intervalMatches.first()
    }

    private fun supportsCurrency(
        price: br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripePrice,
        normalizedCurrency: String
    ): Boolean =
        price.currency.equals(normalizedCurrency, ignoreCase = true) ||
            price.currency_options?.keys?.any { it.equals(normalizedCurrency, ignoreCase = true) } == true

    private fun ensureNoConflictingProvider(user: User, externalUser: ExternalUser?, blocked: ExternalProvider) {
        if (externalUser?.externalProvider == blocked && hasBlockingSubscription(user, externalUser)) {
            throw PaymentException(
                "Você já possui assinatura ativa pela Google Play. Gerencie ou cancele na Play Store antes de assinar na web.",
                PaymentErrorCode.PLAY_MANAGED_IN_STORE
            )
        }
    }

    private fun requireStripeSubscription(user: User): ExternalUser {
        val externalUser = externalUserRepository.findByUserId(user.id!!)
            ?: throw IllegalArgumentException("Usuário externo não encontrado")

        if (externalUser.externalProvider == ExternalProvider.GOOGLE_PLAY && hasBlockingSubscription(user, externalUser)) {
            throw PaymentException(
                "Assinaturas Google Play devem ser gerenciadas na Play Store.",
                PaymentErrorCode.PLAY_MANAGED_IN_STORE
            )
        }

        if (externalUser.externalProvider != ExternalProvider.STRIPE) {
            throw IllegalArgumentException("Assinatura Stripe não encontrada para o usuário")
        }

        return externalUser
    }

    private fun hasBlockingSubscription(user: User, externalUser: ExternalUser?): Boolean {
        if (externalUser == null) return false
        val planId = user.userPlan?.plan?.id
        val status = user.userPlan?.subscriptionStatus ?: SubscriptionStatusEnum.INACTIVE
        return planId != null && planId != PlanTypeEnum.FREE.id && status != SubscriptionStatusEnum.INACTIVE
    }

    private fun getPlanIdByPlanType(planType: String): String =
        when (planType) {
            PlanTypeEnum.STRIPE_BASIC_MONTHLY.name -> PlanTypeEnum.STRIPE_BASIC_MONTHLY.id
            PlanTypeEnum.STRIPE_BASIC_ANNUAL.name -> PlanTypeEnum.STRIPE_BASIC_ANNUAL.id
            PlanTypeEnum.STRIPE_PLUS_MONTHLY.name -> PlanTypeEnum.STRIPE_PLUS_MONTHLY.id
            PlanTypeEnum.STRIPE_PLUS_ANNUAL.name -> PlanTypeEnum.STRIPE_PLUS_ANNUAL.id
            PlanTypeEnum.STRIPE_PREMIUM_MONTHLY.name -> PlanTypeEnum.STRIPE_PREMIUM_MONTHLY.id
            PlanTypeEnum.STRIPE_PREMIUM_ANNUAL.name -> PlanTypeEnum.STRIPE_PREMIUM_ANNUAL.id
            else -> PlanTypeEnum.FREE.id
        }

    private fun saveExternalUser(request: SubscriptionDTO, userId: Long) {
        val externalUser = ExternalUser(
            subscriptionId = request.subscriptionId,
            externalUserId = request.externalUserId,
            externalUserEmail = request.payer.externalEmailAddress,
            externalProvider = ExternalProvider.STRIPE,
            userId = userId,
            audit = Audit()
        )

        externalUserRepository.save(externalUser)
    }

    private fun updatePlanHistory(request: SubscriptionDTO, userId: Long) {
        val planType: PlanTypeEnum? = getPlanTypeById(request.planId!!)

        val planChangeHistory = PlanChangeHistory(
            userId = userId,
            externalUserId = request.externalUserId,
            planId = request.planId,
            planType = planType!!,
            updateTime = LocalDateTime.now(),
            audit = Audit()
        )

        planHistoryRepository.save(planChangeHistory)
    }

    private fun getPlanTypeById(planTypeId: String): PlanTypeEnum? =
        Arrays.stream(PlanTypeEnum.entries.toTypedArray())
            .filter { planType -> planType.id == planTypeId }
            .findFirst()
            .orElse(null)

    private fun getPlanChangeHistory(externalUserdto: ExternalUserDTO, planId: String): PlanChangeHistory {
        return PlanChangeHistory(
            userId = externalUserdto.userId,
            externalUserId = externalUserdto.externalUserId,
            planId = planId,
            planType = PlanTypeEnum.fromProductId(externalUserdto.planId!!)!!,
            updateTime = LocalDateTime.now(),
            audit = Audit()
        )
    }

    private fun setUserPlan(user: User, planId: String) {
        user.userPlan!!.plan.id = planId
        user.userPlan!!.audit!!.updateDtm = LocalDateTime.now()
        user.userPlan!!.audit!!.updateId = CommonEnum.APP_ID.name
    }
}
