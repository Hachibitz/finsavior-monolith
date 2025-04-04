package br.com.finsavior.monolith.finsavior_monolith.config

import br.com.finsavior.monolith.finsavior_monolith.security.CustomAuthenticationProvider
import br.com.finsavior.monolith.finsavior_monolith.security.JWTAuthenticationFilter
import br.com.finsavior.monolith.finsavior_monolith.security.TokenProvider
import br.com.finsavior.monolith.finsavior_monolith.security.UserSecurityDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@EnableWebSecurity
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val customAuthenticationProvider: CustomAuthenticationProvider,
    private val tokenProvider: TokenProvider,
    private val userSecurityDetails: UserSecurityDetails,
    @Value("\${allowed_origin}") private val allowedOrigin: String,
) {

    @Autowired
    fun configureGlobal(auth: AuthenticationManagerBuilder) {
        auth.authenticationProvider(customAuthenticationProvider)
    }

    companion object {
        val ALLOWED_ORIGINS = mutableListOf<String>(
            "http://localhost:8100",
        )
    }

    init {
        ALLOWED_ORIGINS.add(allowedOrigin)
    }

    @Bean
    fun authenticationManager(): AuthenticationManager {
        return ProviderManager(listOf(customAuthenticationProvider))
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.cors { }
            .authorizeHttpRequests { authorize ->
                authorize
                    // Permite acesso a todos os recursos estáticos
                    .requestMatchers(
                        "/",
                        "/index.html",
                        "/favicon.ico",
                        "/*.js",
                        "/*.css",
                        "/*.png",
                        "/*.jpg",
                        "/*.gif",
                        "/*.svg",
                        "/assets/**",
                        "/static/**"
                    ).permitAll()
                    // Permite endpoints públicos
                    .requestMatchers(
                        "/resources/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/**",
                        "/auth/refresh-token",
                        "/auth/login-google",
                        "/auth/login-auth",
                        "/auth/signup",
                        "/auth/password-recovery",
                        "/auth/reset-password",
                        "/auth/validate-token",
                        "/terms-and-privacy/**"
                    ).permitAll()
                    // Endpoints que requerem role ADMIN
                    .requestMatchers(
                        "/user/test-producer",
                    ).hasRole("ADMIN")
                    // Endpoints que requerem USER ou ADMIN
                    .requestMatchers(
                        "/bill/**",
                        "/user/**",
                        "/ai-advice/**",
                        "/payment/**"
                    ).hasAnyRole("USER", "ADMIN")
                    // Todas as outras requisições exigem autenticação
                    .anyRequest().authenticated()
            }
            .logout { logout ->
                logout.logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout")
                    .permitAll()
            }
            .csrf { csrf ->
                csrf.disable()
            }
            .securityContext { securityContext ->
                securityContext.securityContextRepository(HttpSessionSecurityContextRepository())
            }

        http.sessionManagement { session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            session.sessionFixation().migrateSession()
        }

        http.addFilterBefore(JWTAuthenticationFilter(tokenProvider, userSecurityDetails), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsFilter(): CorsFilter {
        val corsConfiguration = CorsConfiguration()
        corsConfiguration.allowedOrigins = ALLOWED_ORIGINS
        corsConfiguration.allowedMethods = listOf(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()
        )
        corsConfiguration.allowedHeaders = listOf(
            "Authorization",
            "Cache-Control",
            "Content-Type",
            "ngrok-skip-browser-warning"
        )
        corsConfiguration.exposedHeaders = listOf("Set-Cookie")
        corsConfiguration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", corsConfiguration)
        }

        return CorsFilter(source)
    }
}