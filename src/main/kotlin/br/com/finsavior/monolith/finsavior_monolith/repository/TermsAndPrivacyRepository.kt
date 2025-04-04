package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.TermsAndPrivacy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TermsAndPrivacyRepository : JpaRepository<TermsAndPrivacy, Long> {
    @Query(
        value = ("SELECT * " +
                "FROM terms_and_privacy " +
                "WHERE del_fg <> 'S' " +
                "AND type = 'TERMS_AND_CONDITIONS' " +
                "AND validity_start_date < NOW() " +
                "ORDER BY validity_start_date DESC " +
                "limit 1"), nativeQuery = true
    )
    fun getCurrentTerm(): TermsAndPrivacy?

    @Query(
        value = ("SELECT * " +
                "FROM terms_and_privacy " +
                "WHERE del_fg <> 'S' " +
                "AND type = 'PRIVACY_POLICY' " +
                "AND validity_start_date < NOW() " +
                "ORDER BY validity_start_date DESC " +
                "limit 1"), nativeQuery = true
    )
    fun getCurrentPrivacyPolicy(): TermsAndPrivacy?
}