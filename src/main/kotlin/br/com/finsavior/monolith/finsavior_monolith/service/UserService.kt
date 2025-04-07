package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.assync.producer.DeleteAccountProducer
import br.com.finsavior.monolith.finsavior_monolith.exception.DeleteUserException
import br.com.finsavior.monolith.finsavior_monolith.exception.PasswordUpdateException
import br.com.finsavior.monolith.finsavior_monolith.exception.ProfileChangeException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ChangePasswordRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.DeleteAccountRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ProfileDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.UpdateProfileRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.entity.UserTransactionManager
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CommonEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.UserAccountDeleteStatusEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toUserProfileDTO
import br.com.finsavior.monolith.finsavior_monolith.repository.ExternalUserRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.UserTransactionManagerRepository
import mu.KLogger
import mu.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val userTransactionManagerRepository: UserTransactionManagerRepository,
    private val externalUserRepository: ExternalUserRepository,
    private val deleteAccountProducer: DeleteAccountProducer,
    private val passwordEncoder: PasswordEncoder,
    @Lazy private val billService: BillService
) {

    private val log: KLogger = KotlinLogging.logger {}

    companion object {
        const val MAX_PROFILE_IMAGE_SIZE_KB = 5120L
    }

    fun getUserByContext(): User {
        val authentication = SecurityContextHolder.getContext().authentication
        val user: User? = userRepository.findByUsername(authentication.name)
        return user ?: throw RuntimeException("User not found")
    }

    fun changeAccountPassword(request: ChangePasswordRequestDTO) {
        val user: User = getUserByContext()

        if (request.currentPassword == request.newPassword) {
            log.error("Erro na alteração de senha. A senha atual não pode ser igual a anterior.")
            throw PasswordUpdateException("A senha atual não pode ser igual a anterior.")
        }

        val isPasswordValid: Boolean = passwordEncoder.matches(request.currentPassword, user.password)
        if (isPasswordValid) {
            user.password = passwordEncoder.encode(request.newPassword)
            userRepository.save(user)
        } else {
            log.error("Erro na alteração de senha. Senha atual incorreta.")
            throw PasswordUpdateException("Erro na alteração de senha. Senha atual incorreta.")
        }
    }

    fun uploadProfilePicture(profileData: MultipartFile) {
        if ((profileData.size / 1024) > MAX_PROFILE_IMAGE_SIZE_KB) {
            throw ProfileChangeException("Tamanho máximo do arquivo excedido: 5MB", HttpStatus.BAD_REQUEST)
        }

        try {
            val user: User = getUserByContext()
            user.userProfile!!.profilePicture = profileData.bytes
            userRepository.save(user)
        } catch (e: Exception) {
            log.error("Failed to save file: ${e.message}")
            throw ProfileChangeException("Failed to save file: ${e.message}", HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    fun getProfileData(): ProfileDataDTO {
        val user: User = getUserByContext()

        val profilePictureBytes: ByteArray? = user.userProfile!!.profilePicture
        var profilePictureBase64: String? = null

        if (profilePictureBytes != null) {
            profilePictureBase64 = Base64.getEncoder().encodeToString(profilePictureBytes)
        }

        return user.toUserProfileDTO()
    }

    fun updateProfile(profilePicture: MultipartFile?, updateProfileRequest: UpdateProfileRequestDTO) {
        try {
            if (profilePicture != null) {
                uploadProfilePicture(profilePicture)
            }

            val user: User = getUserByContext()

            if (updateProfileRequest.firstName != null) {
                user.firstName = updateProfileRequest.firstName
            }

            if (updateProfileRequest.lastName != null) {
                user.lastName = updateProfileRequest.lastName
            }

            user.userProfile!!.name = "${user.firstName} ${user.lastName}"

            userRepository.save(user)
        } catch (e: Exception) {
            log.error("c={}, m={}, msg={}", this.javaClass.getSimpleName(), "updateProfile", e.message)
            throw ProfileChangeException("Erro ao atualizar perfil: ${e.message}", HttpStatus.BAD_REQUEST)
        }
    }

    fun deleteAccountProcessStart(deleteAccountRequestDTO: DeleteAccountRequestDTO) {
        val user = validateDeleteAccountRequest(deleteAccountRequestDTO)
        var userTransactionManager: UserTransactionManager? = userTransactionManagerRepository.findByUserId(user.id!!)

        if (userTransactionManager != null) {
            if(userTransactionManager.audit != null) {
                userTransactionManager.audit!!.updateDtm = LocalDateTime.now()
                userTransactionManager.audit!!.updateId = CommonEnum.APP_ID.name
            } else {
                userTransactionManager.audit = Audit()
            }
            userTransactionManager.statusId = UserAccountDeleteStatusEnum.IN_PROCESS.id
        } else {
            userTransactionManager = UserTransactionManager(
                userId = user.id,
                statusId = UserAccountDeleteStatusEnum.IN_PROCESS.id,
                audit = Audit()
            )
        }

        userTransactionManagerRepository.save(userTransactionManager)

        try {
            deleteAccountProducer.sendMessage(deleteAccountRequestDTO)
            log.info(
                "Exclusão do usuário ${deleteAccountRequestDTO.username} enviada para a fila com sucesso."
            )
        } catch (e: Exception) {
            log.error("method: {}, message: {}, error: {}", "deleteAccount", "falha no envio para a fila", e.message)
            throw DeleteUserException("Erro na exclusão, tente novamente em alguns minutos.", e)
        }
    }

    fun updatePlanByEmail(email: String, planId: String) {
        val user = userRepository.findByEmail(email) ?: return
        val newPlan = getPlanTypeByPlanId(planId)

        user.userPlan!!.plan = planRepository.findById(newPlan.id).get()
        userRepository.save(user)
    }

    fun downgradeToFree(email: String?) {
        email ?: return
        val user = userRepository.findByEmail(email) ?: return
        user.userPlan!!.plan = planRepository.findById(PlanTypeEnum.FREE.id).get()
        userRepository.save(user)
    }

    @Transactional
    fun deleteAccount(request: DeleteAccountRequestDTO) {
        val user: User? = userRepository.findByUsername(request.username)

        if (user != null) {
            val userId: Long = user.id!!
            val userTransactionManager: UserTransactionManager = userTransactionManagerRepository.findByUserId(userId)!!

            try {
                user.roles.clear()
                userRepository.save(user)
                billService.deleteAllUserData(userId)
                userRepository.delete(user)
                externalUserRepository.deleteByUserId(userId)
                updateUserDeleteStatus(userTransactionManager, UserAccountDeleteStatusEnum.FINISHED.id)
                log.info("Account and respective data deleted successfully. ${user.username}")
            } catch (e: Exception) {
                log.error("Falha na exclusão de dados da conta: {}", e.message)
                updateUserDeleteStatus(userTransactionManager, UserAccountDeleteStatusEnum.FAILED.id)
                throw DeleteUserException(e.message?:"Erro na exclusão de dados da conta")
            }
        } else {
            log.error("Usuário ${request.username} não encontrado")
            throw DeleteUserException("Erro na exclusão: usuário não encontrado.")
        }
    }

    private fun getPlanTypeByPlanId(priceId: String): PlanTypeEnum =
        when (priceId) {
            PlanTypeEnum.STRIPE_BASIC_MONTHLY.id -> PlanTypeEnum.STRIPE_BASIC_MONTHLY
            PlanTypeEnum.STRIPE_BASIC_ANNUAL.id -> PlanTypeEnum.STRIPE_BASIC_ANNUAL
            PlanTypeEnum.STRIPE_PLUS_MONTHLY.id -> PlanTypeEnum.STRIPE_PLUS_MONTHLY
            PlanTypeEnum.STRIPE_PLUS_ANNUAL.id -> PlanTypeEnum.STRIPE_PLUS_ANNUAL
            PlanTypeEnum.STRIPE_PREMIUM_MONTHLY.id -> PlanTypeEnum.STRIPE_PREMIUM_MONTHLY
            PlanTypeEnum.STRIPE_PREMIUM_ANNUAL.id -> PlanTypeEnum.STRIPE_PREMIUM_ANNUAL
            else -> PlanTypeEnum.FREE
        }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private fun updateUserDeleteStatus(userTransactionManager: UserTransactionManager, statusId: Int) {
        userTransactionManager.statusId = statusId
        if(userTransactionManager.audit != null) {
            userTransactionManager.audit!!.updateDtm = LocalDateTime.now()
            userTransactionManager.audit!!.updateId = CommonEnum.APP_ID.name
        } else {
            userTransactionManager.audit = Audit()
        }
        userTransactionManagerRepository.save(userTransactionManager)
    }

    private fun validateDeleteAccountRequest(request: DeleteAccountRequestDTO): User {
        if (!request.confirmation) throw DeleteUserException("Erro na exclusão: confirmação necessária.")

        val user: User = getUserByContext()

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw DeleteUserException("Senha incorreta")
        }

        val userDelete: UserTransactionManager? = userTransactionManagerRepository.findByUserId(user.id!!)
        var accountDeleteStatus: UserAccountDeleteStatusEnum? = null

        if (userDelete != null) {
            accountDeleteStatus = UserAccountDeleteStatusEnum.fromId(userDelete.statusId)
        }

        if (accountDeleteStatus != null && accountDeleteStatus == UserAccountDeleteStatusEnum.IN_PROCESS) throw DeleteUserException(
            "Erro na exclusão: já existe uma solicitação em andamento."
        )

        return user
    }
}