package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.entity.TermsAndPrivacy
import br.com.finsavior.monolith.finsavior_monolith.repository.TermsAndPrivacyRepository
import mu.KLogger
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class TermsAndPrivacyService(
    private val termsAndPrivacyRepository: TermsAndPrivacyRepository
) {

    val log: KLogger = KotlinLogging.logger {}

    public fun getTerms(): String {
        try {
            val terms: TermsAndPrivacy? = termsAndPrivacyRepository.getCurrentTerm()
            return terms!!.content
        } catch (e: Exception) {
            logError("getTerms", "Falha ao buscar os termos, tente novamente mais tarde. ", e)
            throw RuntimeException("Falha ao buscar os termos, tente novamente mais tarde. ${e.message}")
        }
    }

    public fun getPrivacyPolicy(): String {
        try {
            val privacy: TermsAndPrivacy? = termsAndPrivacyRepository.getCurrentPrivacyPolicy()
            return privacy!!.content
        } catch (e: Exception) {
            logError("getPrivacyPolicy", "Falha ao buscar a política de privacidade, tente novamente mais tarde. ", e)
            throw RuntimeException("Falha ao buscar a política de privacidade, tente novamente mais tarde. ${e.message}")
        }
    }

    private fun logError(method: String, message: String, exception: Exception) {
        log.error("method: {}, message: {}, erro: {}", method, message, exception.message)
    }
}