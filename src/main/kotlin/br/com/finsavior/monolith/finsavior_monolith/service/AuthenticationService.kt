package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.AuthTokenException
import br.com.finsavior.monolith.finsavior_monolith.exception.PasswordRecoveryException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.LoginRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.SignUpDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.SignUpResponseDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.entity.UserPlan
import br.com.finsavior.monolith.finsavior_monolith.model.entity.UserProfile
import br.com.finsavior.monolith.finsavior_monolith.model.enums.FlagEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.RoleEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toUser
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.RoleRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import br.com.finsavior.monolith.finsavior_monolith.security.TokenProvider
import br.com.finsavior.monolith.finsavior_monolith.security.UserSecurityDetails
import br.com.finsavior.monolith.finsavior_monolith.util.PasswordValidator
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.transaction.Transactional
import mu.KLogger
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthenticationService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val planRepository: PlanRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val tokenProvider: TokenProvider,
    private val emailService: EmailService,
    private val userSecurityDetails: UserSecurityDetails,
    private val firebaseAuthService: FirebaseAuthService
) {

    private val log: KLogger = KotlinLogging.logger {}

    companion object {
        @Value("\${finsavior.front.resetPasswordUrl}")
        lateinit var FINSAVIOR_RESET_PASSWORD_URL: String
    }

    fun login(loginRequest: LoginRequestDTO, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Map<String, String>> {
        var user = userRepository.findByUsername(loginRequest.username)

        if (user == null) {
            throw RuntimeException("Usuário não encontrado")
        }

        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(loginRequest.username, loginRequest.password)
        )

        SecurityContextHolder.getContext().authentication = authentication
        val accessToken = tokenProvider.generateToken(authentication)
        val refreshToken = tokenProvider.generateRefreshToken(loginRequest.username, loginRequest.rememberMe)

        val tokenCookie = Cookie("accessToken", accessToken)
        val refreshTokenCookie = Cookie("refreshToken", refreshToken)

        setCookieProperties(
            tokenCookie, refreshTokenCookie,
            loginRequest.rememberMe, request.serverName
        )
        response.addCookie(tokenCookie)
        response.addCookie(refreshTokenCookie)

        return ResponseEntity.ok(mapOf("accessToken" to accessToken, "refreshToken" to refreshToken))
    }

    fun validateLogin(loginRequest: LoginRequestDTO) {
        val user = userRepository.findByUsername(loginRequest.username)
            ?: throw RuntimeException("Usuário não encontrado")

        if (!passwordEncoder.matches(loginRequest.password, user.password)) {
            throw RuntimeException("Senha inválida")
        }
    }

    fun loginWithGoogle(
        idTokenString: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<MutableMap<String, String>> {
        try {
            val firebaseToken = firebaseAuthService.validateFirebaseToken(idTokenString)
            val email: String = firebaseToken.email
                ?: throw IllegalArgumentException("Email não disponível no token.")

            val user: User = userRepository.findByEmail(email)
                ?: throw IllegalArgumentException("Usuário não encontrado.")

            val authentication: Authentication = UsernamePasswordAuthenticationToken(
                user.username, null, userSecurityDetails.loadUserByUsername(user.username).authorities
            )
            SecurityContextHolder.getContext().authentication = authentication
            val accessToken: String = tokenProvider.generateToken(authentication)
            val refreshToken: String = tokenProvider.generateRefreshToken(user.username, true)

            val tokenCookie = Cookie("accessToken", accessToken)
            val refreshTokenCookie = Cookie("refreshToken", refreshToken)
            setCookieProperties(tokenCookie, refreshTokenCookie, true, request.serverName)

            val tokens: MutableMap<String, String> = HashMap<String, String>()
            tokens.put("accessToken", accessToken)
            tokens.put("refreshToken", refreshToken)

            response.addCookie(tokenCookie)
            response.addCookie(refreshTokenCookie)

            return ResponseEntity.ok<MutableMap<String, String>>(tokens)
        } catch (e: Exception) {
            throw AuthTokenException(e.message.toString(), HttpStatus.UNAUTHORIZED)
        }
    }

    fun refreshToken(refreshToken: String): ResponseEntity<String> {
        if (tokenProvider.validateToken(refreshToken)) {
            val username = tokenProvider.getUsernameFromToken(refreshToken)

            val newAccessToken = tokenProvider.generateToken(UsernamePasswordAuthenticationToken(username, null, emptyList()))
            return ResponseEntity.ok(newAccessToken)
        }

        throw RuntimeException("Token de refresh inválido ou expirado")
    }

    fun validateToken(token: String?): ResponseEntity<Boolean?> {
        if (token != null && tokenProvider.validateToken(token)) {
            return ResponseEntity.ok().body<Boolean?>(true)
        }
        throw AuthTokenException("Token não validado!", HttpStatus.UNAUTHORIZED)
    }

    fun passwordRecovery(email: String) {
        val user = userRepository.findByEmail(email)
            ?: throw IllegalArgumentException("Usuário não encontrado com o e-mail fornecido.")

        val token = generateRecoveryToken(user)
        emailService.sendPasswordRecoveryEmail(email, token)
    }

    fun resetPassword(token: String, newPassword: String) {
        val user = validateRecoveryToken(token)
            ?: throw IllegalArgumentException("Token inválido ou expirado.")

        if (!PasswordValidator.isValid(newPassword)) {
            throw PasswordRecoveryException("Senha não atende aos requisitos mínimos")
        }

        user.password = passwordEncoder.encode(newPassword)
        userRepository.save(user)
    }

    private fun generateRecoveryToken(user: User): String {
        return tokenProvider.generateToken(
            UsernamePasswordAuthenticationToken(user.username, null, emptyList())
        )
    }

    private fun validateRecoveryToken(token: String): User? {
        val username = tokenProvider.getUsernameFromToken(token)
        return userRepository.findByUsername(username)
    }

    private fun setCookieProperties(
        tokenCookie: Cookie, refreshTokenCookie: Cookie,
        isRememberMe: Boolean, serverName: String?
    ) {
        val fifteenMinutesInMs = 15 * 60
        tokenCookie.domain = serverName
        tokenCookie.path = "/"
        tokenCookie.maxAge = fifteenMinutesInMs

        refreshTokenCookie.domain = serverName
        refreshTokenCookie.path = "/"
        refreshTokenCookie.maxAge = fifteenMinutesInMs

        if (isRememberMe) {
            val thirtyDaysExpirationInMs = 43800 * 60
            refreshTokenCookie.maxAge = thirtyDaysExpirationInMs
        }
    }

    @Transactional
    fun signUp(signUpDTO: SignUpDTO): ResponseEntity<SignUpResponseDTO> {
        val validationErrors = signUpValidations(signUpDTO)
        if (validationErrors != null) {
            return ResponseEntity.badRequest().body(SignUpResponseDTO(validationErrors))
        }

        val user = getUserForRegistration(signUpDTO)
        val savedUser = userRepository.save(user)

        userInitConfig(savedUser)

        val confirmationToken = tokenProvider.generateToken(
            UsernamePasswordAuthenticationToken(savedUser.username, null, emptyList())
        )
        emailService.sendConfirmationEmail(savedUser.email, confirmationToken)

        return ResponseEntity.ok(SignUpResponseDTO("Cadastro realizado com sucesso!"))
    }

    fun confirmEmail(token: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Map<String, String>> {
        val username = tokenProvider.getUsernameFromToken(token)
        val user = userRepository.findByUsername(username)
            ?: throw IllegalArgumentException("Usuário não encontrado.")

        if (user.audit?.delFg == FlagEnum.S) {
            throw IllegalArgumentException("Conta já ativada.")
        }

        user.audit?.delFg = FlagEnum.S
        userRepository.save(user)

        return login(LoginRequestDTO(user.username, user.password, true), request, response)
    }

    private fun userInitConfig(user: User) {
        linkUserRole(user)
        linkFreePlanToUserSignUp(user)
        linkUserProfile(user)
        userRepository.save(user)
    }

    private fun linkUserRole(user: User) {
        val role = roleRepository.findByName(RoleEnum.ROLE_USER.name)
        user.roles = mutableSetOf(role).filterNotNull().toMutableSet()
    }

    private fun getUserForRegistration(request: SignUpDTO): User =
        request.toUser().apply {
            password = passwordEncoder.encode(request.password)
            audit = Audit(delFg = FlagEnum.N)
        }

    private fun linkFreePlanToUserSignUp(user: User) {
        val plan = planRepository.findById(PlanTypeEnum.FREE.id!!)
            .orElseThrow { IllegalArgumentException("Plano não encontrado") }
        user.userPlan = UserPlan(
            plan = plan,
            userId = user.id!!,
            audit = Audit()
        ).apply {
            user.userPlan = this
        }
    }

    private fun linkUserProfile(user: User) =
        user.apply { userProfile = UserProfile(
            userId = user.id,
            name = user.getFirstAndLastName(),
            email = user.email,
            profilePicture = null,
            audit = Audit()
        ) }

    private fun signUpValidations(request: SignUpDTO): String? {
        val userByEmail = userRepository.findByEmail(request.email)
        val userByUsername = userRepository.findByUsername(request.username)

        val resultBuilder = StringBuilder()

        resultBuilder.append(if (request.email != request.emailConfirmation) "Emails não conferem" else "")
        resultBuilder.append(if (userByEmail != null) "Email já cadastrado. \n" else "")
        resultBuilder.append(if (userByUsername != null) "Usuário já cadastrado. \n" else "")
        resultBuilder.append(if (request.password != request.passwordConfirmation) "As senhas não coincidem. \n" else "")
        resultBuilder.append(if (!isPasswordValid(request.password)) "Critérios da senha não atendidos. \n" else "")
        resultBuilder.append(if (!isEmailValid(request.email)) "Email inválido. \n" else "")
        resultBuilder.append(if (request.username.length < 4) "Usuário precisa ter 4 ou mais caracteres. \n" else "")
        resultBuilder.append(if (!containSymbols(request.username)) "Usuário não pode conter símbolos. \n" else "")
        resultBuilder.append(if (request.firstName.length < 2) "Nome precisa ter 2 ou mais caracteres. \n" else "")
        resultBuilder.append(if (!isValidName(request.firstName)) "Nome não pode conter símbolos. \n" else "")
        resultBuilder.append(if (request.lastName.length < 2) "Sobrenome precisa ter 2 ou mais caracteres. \n" else "")
        resultBuilder.append(if (!isValidName(request.lastName)) "Sobrenome não pode conter símbolos. \n" else "")

        return if (resultBuilder.isNotEmpty()) resultBuilder.toString().trim() else null
    }

    private fun isPasswordValid(newPassword: String): Boolean {
        val regex = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@\$!%*?&]).{8,}$"
        return newPassword.matches(regex.toRegex())
    }

    private fun isEmailValid(email: String): Boolean {
        val regex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
        return email.matches(regex.toRegex())
    }

    private fun containSymbols(username: String): Boolean {
        val regex = "^[a-zA-Z0-9_]+$"
        return username.matches(regex.toRegex())
    }

    private fun isValidName(name: String): Boolean {
        val regex = Regex("^[\\p{L} ]+$")
        return name.matches(regex)
    }
}