package br.com.finsavior.monolith.finsavior_monolith.service

import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender,
) {

    fun sendPasswordRecoveryEmail(email: String, resetPasswordUrl: String) {
        val message = SimpleMailMessage()
        message.setTo(email)
        message.subject = "Redefinição de senha"
        message.text = "Clique no link para redefinir sua senha: $resetPasswordUrl"
        mailSender.send(message)
    }
}