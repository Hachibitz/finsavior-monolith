package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.config.properties.GooglePlayProperties
import br.com.finsavior.monolith.finsavior_monolith.config.properties.PlanIdProperties
import br.com.finsavior.monolith.finsavior_monolith.exception.PaymentException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GooglePlayProductCatalogResponse
import br.com.finsavior.monolith.finsavior_monolith.model.dto.PlayBillingSkuDto
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GooglePlayVerifySubscriptionRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GooglePlayVerifySubscriptionResponse
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ExternalUser
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.ExternalProvider
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.SubscriptionStatusEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.ExternalUserRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import mu.KLogger
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.util.Base64

@Service
class GooglePlayBillingService(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val externalUserRepository: ExternalUserRepository,
    private val planRepository: PlanRepository,
    private val properties: GooglePlayProperties,
    private val planIdProperties: PlanIdProperties
) {

    private val log: KLogger = KotlinLogging.logger {}

    private val publisher: AndroidPublisher? by lazy { buildPublisher() }

    fun getProductCatalog(): GooglePlayProductCatalogResponse =
        GooglePlayProductCatalogResponse(
            products = mapOf(
                PlanTypeEnum.PLAY_BASIC_MONTHLY.name to PlayBillingSkuDto(
                    planIdProperties.PLAY_BASIC_MONTHLY,
                    planIdProperties.PLAY_BASIC_MONTHLY_BASE_PLAN
                ),
                PlanTypeEnum.PLAY_BASIC_ANNUAL.name to PlayBillingSkuDto(
                    planIdProperties.PLAY_BASIC_ANNUAL,
                    planIdProperties.PLAY_BASIC_ANNUAL_BASE_PLAN
                ),
                PlanTypeEnum.PLAY_PLUS_MONTHLY.name to PlayBillingSkuDto(
                    planIdProperties.PLAY_PLUS_MONTHLY,
                    planIdProperties.PLAY_PLUS_MONTHLY_BASE_PLAN
                ),
                PlanTypeEnum.PLAY_PLUS_ANNUAL.name to PlayBillingSkuDto(
                    planIdProperties.PLAY_PLUS_ANNUAL,
                    planIdProperties.PLAY_PLUS_ANNUAL_BASE_PLAN
                ),
                PlanTypeEnum.PLAY_PREMIUM_MONTHLY.name to PlayBillingSkuDto(
                    planIdProperties.PLAY_PREMIUM_MONTHLY,
                    planIdProperties.PLAY_PREMIUM_MONTHLY_BASE_PLAN
                ),
                PlanTypeEnum.PLAY_PREMIUM_ANNUAL.name to PlayBillingSkuDto(
                    planIdProperties.PLAY_PREMIUM_ANNUAL,
                    planIdProperties.PLAY_PREMIUM_ANNUAL_BASE_PLAN
                )
            )
        )

    @Transactional
    fun verifyAndActivateSubscription(request: GooglePlayVerifySubscriptionRequest): GooglePlayVerifySubscriptionResponse {
        val user = userService.getUserByContext()
        val userId = user.id!!
        ensureNoConflictingProvider(userId, ExternalProvider.STRIPE)

        val packageName = request.packageName?.takeIf { it.isNotBlank() } ?: properties.packageName
        val subscription = fetchSubscription(packageName, request.purchaseToken)
        val planType = resolvePlanType(request.productId, subscription)
            ?: throw PaymentException("Produto Google Play não reconhecido: ${request.productId}")

        val status = mapSubscriptionStatus(subscription)
        applyPlan(user, planType, status)
        saveExternalUser(user, request.purchaseToken, subscription, planType)

        log.info { "Google Play subscription activated for user=$userId plan=${planType.name}" }
        return GooglePlayVerifySubscriptionResponse(
            planType = planType.name,
            subscriptionStatus = status.name
        )
    }

    @Transactional
    fun handleRtdnNotification(encodedData: String) {
        if (properties.serviceAccountJson.isBlank()) {
            log.warn { "Google Play RTDN received but service account is not configured" }
            return
        }

        val json = String(Base64.getDecoder().decode(encodedData))
        log.info { "Processing Google Play RTDN payload" }

        val purchaseToken = extractPurchaseToken(json) ?: run {
            log.warn { "RTDN payload without purchaseToken: $json" }
            return
        }

        val subscription = fetchSubscription(properties.packageName, purchaseToken)
        val externalUser = externalUserRepository.findBySubscriptionId(purchaseToken)
            ?: run {
                log.warn { "RTDN for unknown purchaseToken" }
                return
            }

        val user = userRepository.findById(externalUser.userId).orElse(null) ?: return
        val status = mapSubscriptionStatus(subscription)

        when {
            status == SubscriptionStatusEnum.INACTIVE -> downgradeUser(user)
            else -> {
                val productId = subscription.lineItems?.firstOrNull()?.productId
                val planType = productId?.let { PlanTypeEnum.fromPlayProductId(it) }
                if (planType != null) {
                    applyPlan(user, planType, status)
                } else {
                    user.userPlan?.subscriptionStatus = status
                    userRepository.save(user)
                }
            }
        }
    }

    private fun ensureNoConflictingProvider(userId: Long, blocked: ExternalProvider) {
        val existing = externalUserRepository.findByUserId(userId)
        if (existing != null && existing.externalProvider == blocked) {
            throw PaymentException(
                "Você já possui assinatura ativa por outro provedor. Cancele antes de assinar pela Google Play."
            )
        }
    }

    private fun fetchSubscription(packageName: String, purchaseToken: String): SubscriptionPurchaseV2 {
        val client = publisher ?: throw PaymentException("Google Play Billing não configurado no servidor.")
        return try {
            client.purchases().subscriptionsv2()
                .get(packageName, purchaseToken)
                .execute()
        } catch (e: Exception) {
            log.error(e) { "Failed to verify Google Play purchase" }
            throw PaymentException("Não foi possível validar a compra na Google Play.")
        }
    }

    private fun resolvePlanType(productId: String, subscription: SubscriptionPurchaseV2): PlanTypeEnum? {
        PlanTypeEnum.fromPlayProductId(productId)?.let { return it }
        val lineItemProduct = subscription.lineItems?.firstOrNull()?.productId
        return lineItemProduct?.let { PlanTypeEnum.fromPlayProductId(it) }
    }

    private fun mapSubscriptionStatus(subscription: SubscriptionPurchaseV2): SubscriptionStatusEnum {
        val state = subscription.subscriptionState ?: return SubscriptionStatusEnum.INACTIVE
        return when {
            state.contains("ACTIVE", ignoreCase = true) -> SubscriptionStatusEnum.ACTIVE
            state.contains("PAUSED", ignoreCase = true) -> SubscriptionStatusEnum.PAST_DUE
            state.contains("ON_HOLD", ignoreCase = true) -> SubscriptionStatusEnum.PAST_DUE
            state.contains("CANCELED", ignoreCase = true) -> SubscriptionStatusEnum.CANCELED_AT_PERIOD_END
            else -> SubscriptionStatusEnum.INACTIVE
        }
    }

    private fun applyPlan(user: User, planType: PlanTypeEnum, status: SubscriptionStatusEnum) {
        user.userPlan!!.plan = planRepository.findById(planType.id).orElseGet {
            planRepository.findById(PlanTypeEnum.FREE.id).get()
        }
        user.userPlan!!.subscriptionStatus = status
        userRepository.save(user)
    }

    private fun downgradeUser(user: User) {
        user.userPlan!!.plan = planRepository.findById(PlanTypeEnum.FREE.id).get()
        user.userPlan!!.subscriptionStatus = SubscriptionStatusEnum.INACTIVE
        userRepository.save(user)
        externalUserRepository.findByUserId(user.id!!)?.let { externalUserRepository.delete(it) }
    }

    private fun saveExternalUser(
        user: User,
        purchaseToken: String,
        subscription: SubscriptionPurchaseV2,
        planType: PlanTypeEnum
    ) {
        externalUserRepository.findByUserId(user.id!!)?.let { externalUserRepository.delete(it) }
        externalUserRepository.save(
            ExternalUser(
                subscriptionId = purchaseToken,
                externalUserId = subscription.latestOrderId,
                externalUserEmail = user.email,
                externalProvider = ExternalProvider.GOOGLE_PLAY,
                userId = user.id!!,
                audit = Audit()
            )
        )
        log.info { "Linked Google Play subscription for user=${user.id} plan=${planType.name}" }
    }

    private fun buildPublisher(): AndroidPublisher? {
        if (properties.serviceAccountJson.isBlank()) {
            log.warn { "google.play.service-account-json is empty — Google Play verification disabled" }
            return null
        }
        return try {
            val credentials: GoogleCredentials = ServiceAccountCredentials.fromStream(
                ByteArrayInputStream(properties.serviceAccountJson.toByteArray())
            ).createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))

            AndroidPublisher.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                HttpCredentialsAdapter(credentials)
            )
                .setApplicationName("FinSavior")
                .build()
        } catch (e: Exception) {
            log.error(e) { "Failed to initialize Google Play Android Publisher client" }
            null
        }
    }

    private fun extractPurchaseToken(json: String): String? {
        val tokenRegex = """"purchaseToken"\s*:\s*"([^"]+)"""".toRegex()
        return tokenRegex.find(json)?.groupValues?.get(1)
    }
}
