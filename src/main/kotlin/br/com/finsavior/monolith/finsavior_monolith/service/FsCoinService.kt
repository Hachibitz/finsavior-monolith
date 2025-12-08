package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.InsufficientFsCoinsException
import br.com.finsavior.monolith.finsavior_monolith.model.entity.UserFsCoin
import br.com.finsavior.monolith.finsavior_monolith.model.enums.AnalysisTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.UserFsCoinRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FsCoinService(
    private val userFsCoinRepository: UserFsCoinRepository,
    private val userService: UserService,
    @param:Value("\${fscoins-per-ad}") private val coinsPerAd: Long,
    @param:Value("\${fscoin-cost-for-monthly-analysis}") private val fsCoinCostForMonthlyAnalysis: Long,
    @param:Value("\${fscoin-cost-for-trimester-analysis}") private val fsCoinCostForTrimesterAnalysis: Long,
    @param:Value("\${fscoin-cost-for-yearly-analysis}") private val fsCoinCostForYearlyAnalysis: Long,
) {

    private fun currentUserId(): Long {
        return userService.getUserByContext().id!!
    }

    @Transactional(readOnly = true)
    fun getBalance(userId: Long? = null): Long {
        val finalUserId = userId ?: currentUserId()
        val record = userFsCoinRepository.findByUserId(finalUserId)
        return record?.balance ?: 0L
    }

    @Transactional
    fun earnCoins(userId: Long? = null): Long {
        val finalUserId = userId ?: currentUserId()
        val record = userFsCoinRepository.findByUserId(finalUserId)
            ?: UserFsCoin(userId = finalUserId, balance = 0L)
        record.balance += coinsPerAd
        userFsCoinRepository.save(record)
        return coinsPerAd
    }

    @Transactional
    fun spendCoins(amount: Long, userId: Long? = null) {
        val finalUserId = userId ?: currentUserId()
        val record = userFsCoinRepository.findByUserId(finalUserId)
            ?: throw InsufficientFsCoinsException("User does not have any FsCoins")

        if (record.balance < amount) {
            throw InsufficientFsCoinsException("User does not have enough FsCoins")
        }

        record.balance -= amount
        userFsCoinRepository.save(record)
    }

    fun hasEnoughCoinsForAnalysis(
        analysisType: AnalysisTypeEnum,
        userId: Long? = null,
        coinsCostForAnalysis: Long? = null
    ): Boolean {
        val finalUserId = userId ?: currentUserId()
        val record = userFsCoinRepository.findByUserId(finalUserId)
            ?: throw InsufficientFsCoinsException("User does not have any FsCoins")

        val requiredCoins = coinsCostForAnalysis ?: getCoinsCostForAnalysis(analysisType)

        return record.balance >= requiredCoins
    }

    fun getCoinsCostForAnalysis(
        analysisType: AnalysisTypeEnum
    ): Long {
        return when (analysisType) {
            AnalysisTypeEnum.MONTH -> fsCoinCostForMonthlyAnalysis
            AnalysisTypeEnum.TRIMESTER -> fsCoinCostForTrimesterAnalysis
            AnalysisTypeEnum.ANNUAL -> fsCoinCostForYearlyAnalysis
        }
    }
}
