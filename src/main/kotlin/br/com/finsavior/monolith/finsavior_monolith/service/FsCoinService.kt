package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.entity.UserFsCoin
import br.com.finsavior.monolith.finsavior_monolith.repository.UserFsCoinRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FsCoinService(
    private val userFsCoinRepository: UserFsCoinRepository,
    private val userService: UserService
) {

    companion object {
        private const val COINS_PER_AD = 10L //TODO() mover para config-server
    }

    private fun currentUserId(): Long {
        return userService.getUserByContext().id!!
    }

    @Transactional(readOnly = true)
    fun getBalance(): Long {
        val userId = currentUserId()
        val record = userFsCoinRepository.findByUserId(userId)
        return record?.balance ?: 0L
    }

    @Transactional
    fun earnCoins(): Long {
        val userId = currentUserId()
        val record = userFsCoinRepository.findByUserId(userId)
            ?: UserFsCoin(userId = userId, balance = 0L)
        record.balance = record.balance + COINS_PER_AD
        val saved = userFsCoinRepository.save(record)
        return COINS_PER_AD
    }
}
