package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.config.properties.GooglePlayProperties
import br.com.finsavior.monolith.finsavior_monolith.config.properties.PlanIdProperties
import br.com.finsavior.monolith.finsavior_monolith.exception.PaymentErrorCode
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
        ensureTokenNotLinkedToAnotherUser(userId, request.purchaseToken)

        // Package name always comes from server config — never trust the client here.
        val subscription = fetchSubscription(properties.packageName, request.purchaseToken)
        val planType = resolvePlanType(request.productId, subscription)
            ?: throw PaymentException(
                "Produto Google Play não reconhecido: ${request.productId}",
                PaymentErrorCode.PLAY_PRODUCT_UNKNOWN
            )

        val status = mapSubscriptionStatus(subscription)
        if (status == SubscriptionStatusEnum.INACTIVE) {
            throw PaymentException(
                "Esta assinatura não está ativa na Google Play.",
                PaymentErrorCode.PLAY_SUBSCRIPTION_INACTIVE
            )
        }

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
            log.warn { "RTDN payload without purchaseToken (ignored)" }
            return
        }

        val externalUser = externalUserRepository.findBySubscriptionId(purchaseToken)
            ?: resolveLinkedTokenMigration(purchaseToken)
            ?: run {
                log.warn { "RTDN for unknown purchaseToken (no linked token match) — ignored" }
                return
            }

        val user = userRepository.findById(externalUser.userId).orElse(null) ?: return
        val subscription = fetchSubscription(properties.packageName, externalUser.subscriptionId!!)
        applySubscriptionState(user, subscription)
    }

    /**
     * Plan upgrades/downgrades and resubscribes on the Play Store issue a NEW
     * purchase token; the verified subscription carries the previous one as
     * `linkedPurchaseToken`. When the incoming token is unknown locally, follow
     * that link to migrate the user's record to the new token so future RTDNs
     * (and cancellations) keep working without waiting for the app to re-verify.
     */
    private fun resolveLinkedTokenMigration(newToken: String): ExternalUser? {
        val subscription = runCatching { fetchSubscription(properties.packageName, newToken) }
            .getOrNull() ?: return null
        val linkedToken = subscription.linkedPurchaseToken ?: return null
        val externalUser = externalUserRepository.findBySubscriptionId(linkedToken) ?: return null

        externalUser.subscriptionId = newToken
        externalUser.externalUserId = subscription.latestOrderId
        externalUserRepository.save(externalUser)
        log.info { "RTDN: migrated user=${externalUser.userId} to new purchaseToken via linkedPurchaseToken" }
        return externalUser
    }

    private fun applySubscriptionState(user: User, subscription: SubscriptionPurchaseV2) {
        val status = mapSubscriptionStatus(subscription)
        if (status == SubscriptionStatusEnum.INACTIVE) {
            downgradeUser(user)
            return
        }
        val productId = subscription.lineItems?.firstOrNull()?.productId
        val planType = productId?.let { PlanTypeEnum.fromPlayProductId(it) }
        if (planType != null) {
            applyPlan(user, planType, status)
        } else {
            user.userPlan?.subscriptionStatus = status
            userRepository.save(user)
        }
    }

    private fun ensureNoConflictingProvider(userId: Long, blocked: ExternalProvider) {
        val existing = externalUserRepository.findByUserId(userId)
        if (existing != null && existing.externalProvider == blocked) {
            throw PaymentException(
                "Você já possui assinatura ativa por outro provedor. Cancele antes de assinar pela Google Play.",
                PaymentErrorCode.SUBSCRIPTION_OTHER_PROVIDER
            )
        }
    }

    private fun fetchSubscription(packageName: String, purchaseToken: String): SubscriptionPurchaseV2 {
        val client = publisher ?: throw PaymentException(
            "Google Play Billing não configurado no servidor.",
            PaymentErrorCode.PLAY_BILLING_NOT_CONFIGURED
        )
        return try {
            client.purchases().subscriptionsv2()
                .get(packageName, purchaseToken)
                .execute()
        } catch (e: Exception) {
            log.error(e) { "Failed to verify Google Play purchase" }
            throw PaymentException(
                "Não foi possível validar a compra na Google Play.",
                PaymentErrorCode.PLAY_VERIFY_FAILED
            )
        }
    }

    /**
     * The productId verified by Google (lineItems) is the source of truth; the
     * client-supplied productId is only a fallback for legacy responses without
     * line items. Trusting the client here would let a Basic purchase token
     * activate a Premium plan.
     */
    private fun resolvePlanType(requestedProductId: String, subscription: SubscriptionPurchaseV2): PlanTypeEnum? {
        val verifiedProductId = subscription.lineItems?.firstOrNull()?.productId
        if (verifiedProductId != null) {
            if (!verifiedProductId.equals(requestedProductId, ignoreCase = true)) {
                log.warn { "Play verify: client claimed productId=$requestedProductId but token belongs to $verifiedProductId" }
            }
            return PlanTypeEnum.fromPlayProductId(verifiedProductId)
        }
        return PlanTypeEnum.fromPlayProductId(requestedProductId)
    }

    private fun ensureTokenNotLinkedToAnotherUser(userId: Long, purchaseToken: String) {
        val existing = externalUserRepository.findBySubscriptionId(purchaseToken)
        if (existing != null && existing.userId != userId) {
            log.warn { "Play verify: purchaseToken already linked to user=${existing.userId}, rejected for user=$userId" }
            throw PaymentException(
                "Esta compra já está vinculada a outra conta FinSavior.",
                PaymentErrorCode.PLAY_PURCHASE_LINKED_TO_OTHER_ACCOUNT
            )
        }
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
