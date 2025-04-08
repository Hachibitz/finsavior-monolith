package br.com.finsavior.monolith.finsavior_monolith.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.io.File

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value ("\${finsavior.host-url}") private val finsaviorHostUrl: String,
    @Value ("\${spring.mail.username}") private val appEmail: String,
) {

    fun sendPasswordRecoveryEmail(email: String, token: String) {
        val message = MimeMessageHelper(mailSender.createMimeMessage(), true)
        message.setFrom(appEmail)
        message.setTo(email)
        message.setSubject("Redefinição de senha")
        message.setText(buildPasswordRecoveryEmailContent(token), true)

        val logoFile = FileSystemResource(File("src/main/resources/logo/logo v2 complete.png"))
        message.addInline("logo", logoFile)

        mailSender.send(message.mimeMessage)
    }

    fun sendInvoicePaymentFailedEmail(email: String) {
        val message = MimeMessageHelper(mailSender.createMimeMessage(), true)
        message.setFrom(appEmail)
        message.setTo(email)
        message.setSubject("Falha no pagamento da assinatura")
        message.setText(buildPaymentFailedEmailContent(), true)

        val logoFile = FileSystemResource(File("src/main/resources/logo/logo v2 complete.png"))
        message.addInline("logo", logoFile)

        mailSender.send(message.mimeMessage)
    }

    private fun buildPaymentFailedEmailContent(): String {
        return """
        <html>
        <body>
            <div style="text-align: center;">
                <img src="cid:logo" alt="Logo" style="width: 150px;"/>
                <h2>Falha no Pagamento</h2>
                <p>Olá,</p>
                <p>Notamos que houve uma falha ao processar o pagamento da sua assinatura.</p>
                <p>Isso pode ocorrer por diversos motivos, como cartão expirado ou saldo insuficiente.</p>
                <p>Por favor, acesse sua conta para atualizar suas informações de pagamento e evitar a interrupção do seu plano:</p>
                <a href="$finsaviorHostUrl/my-account">Atualizar pagamento</a>
                <p>Se você tiver qualquer dúvida, entre em contato conosco.</p>
                <br/>
                <p>Equipe FinSavior</p>
            </div>
        </body>
        </html>
    """.trimIndent()
    }

    private fun buildPasswordRecoveryEmailContent(token: String): String {
        return """
            <html>
            <body>
                <div style="text-align: center;">
                    <img src="cid:logo" alt="Logo" style="width: 150px;"/>
                    <h2>Redefinição de Senha</h2>
                    <p>Olá,</p>
                    <p>Você solicitou a redefinição de sua senha. Por favor, insira o token abaixo na tela de redefinição de senha e escolha uma nova senha.</p>
                    <div style="border: 1px solid #ccc; padding: 10px; display: inline-block; margin: 20px 0;">
                        <strong>$token</strong>
                    </div>
                    <p><button onclick="copyToken()">Copiar Token</button></p>
                    <p>Ou clique no link abaixo para redefinir sua senha:</p>
                    <a href="$finsaviorHostUrl/password-forgotten/$token">Redefinir Senha</a>
                    <p>Se você não solicitou a redefinição de senha, por favor ignore este email.</p>
                </div>
                <script>
                    function copyToken() {
                        navigator.clipboard.writeText('$token').then(function() {
                            alert('Token copiado para a área de transferência');
                        }, function(err) {
                            console.error('Erro ao copiar token: ', err);
                        });
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}