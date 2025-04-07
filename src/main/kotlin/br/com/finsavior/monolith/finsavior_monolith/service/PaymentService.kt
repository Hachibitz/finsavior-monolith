package br.com.finsavior.monolith.finsavior_monolith.service

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
import br.com.finsavior.monolith.finsavior_monolith.repository.ExternalUserRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import com.stripe.model.checkout.Session
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

            val planChangeHistory: PlanChangeHistory = getPlanchangeHistory(externalUserDto, planId!!)
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

    fun createCheckoutSession(planType: String, email: String): CheckoutSessionDTO {
        val productId = getPlanIdByPlanType(planType)
        val priceId = getPriceIdByProductId(productId)

        val session = Session.create(
            SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl("$finsaviorHostUrl/main-page/subscription?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl("$finsaviorHostUrl/main-page/subscription")
                .setCustomerEmail(email)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build()
                )
                .setSubscriptionData(
                    SessionCreateParams.SubscriptionData.builder()
                        .setTrialPeriodDays(7)
                        .build()
                )
                .build()
        )

        return CheckoutSessionDTO(url = session.url)
    }

    fun updateSubscription(planType: String, email: String) {
        val user = userService.getUserByContext()
        val externalUser = externalUserRepository.findByUserId(user.id!!)
            ?: throw IllegalArgumentException("Usuário externo não encontrado")
        val subscriptionId = externalUser.subscriptionId ?: throw IllegalStateException("Usuário não possui assinatura ativa")

        val stripeSubscription = stripeClient.getSubscription(subscriptionId)
        val subscriptionItemId = stripeSubscription.items.data.firstOrNull()?.id
            ?: throw IllegalStateException("Subscription item não encontrado na assinatura do usuário")

        val planEnum = PlanTypeEnum.valueOf(planType)
        val productId = planEnum.id
        val prices = stripeClient.getPricesByProduct(productId)
        val newPriceId = prices.data.firstOrNull()?.id
            ?: throw IllegalStateException("Preço não encontrado para o plano $planType")

        stripeClient.updateSubscriptionPrice(subscriptionId, subscriptionItemId, newPriceId)
    }

    fun cancelSubscription(immediate: Boolean) {
        val user = userService.getUserByContext()

        val externalUser = externalUserRepository.findByUserId(user.id!!)
            ?: throw IllegalArgumentException("Usuário externo não encontrado")

        val subscriptionId = externalUser.subscriptionId
            ?: throw IllegalArgumentException("Usuário não possui assinatura ativa")

        if (immediate) {
            stripeClient.cancelSubscriptionImmediately(subscriptionId)
        } else {
            stripeClient.cancelAtPeriodEnd(subscriptionId, true)
        }
    }

    private fun getPriceIdByProductId(productId: String): String {
        val prices = stripeClient.getPricesByProduct(productId).data

        return prices.firstOrNull()?.id
            ?: throw IllegalStateException("Nenhum preço encontrado para o produto: $productId")
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
        val externalProvider: ExternalProvider? = Arrays.stream(ExternalProvider.entries.toTypedArray())
            .filter { provider -> provider.name == ExternalProvider.PAYPAL.name }
            .findFirst()
            .orElseThrow { IllegalArgumentException("Invalid external provider") }

        val externalUser: ExternalUser = ExternalUser(
            subscriptionId = request.subscriptionId,
            externalUserId = request.externalUserId,
            externalUserEmail = request.payer.externalEmailAddress,
            externalProvider = ExternalProvider.PAYPAL,
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

    private fun getPlanchangeHistory(externalUserdto: ExternalUserDTO, planId: String): PlanChangeHistory {
        return PlanChangeHistory(
            userId = externalUserdto.userId,
            externalUserId = externalUserdto.externalUserId,
            planId = planId,
            planType = PlanTypeEnum.fromProductId(externalUserdto.planId!!),
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