package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.InsufficientFsCoinsException
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
        record.balance = record.balance + COINS_PER_AD
        userFsCoinRepository.save(record)
        return COINS_PER_AD
    }

    @Transactional
    fun spendCoins(amount: Long, userId: Long? = null) {
        val finalUserId = userId ?: currentUserId()
        val record = userFsCoinRepository.findByUserId(finalUserId)
            ?: throw InsufficientFsCoinsException("User does not have any FsCoins")

        if (record.balance < amount) {
            throw InsufficientFsCoinsException("User does not have any FsCoins")
        }

        record.balance -= amount
        userFsCoinRepository.save(record)
    }
}
