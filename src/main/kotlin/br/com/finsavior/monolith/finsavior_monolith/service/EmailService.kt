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
    @Value ("\${finsavior.reset-password-url}") private val resetPasswordUrl: String,
) {

    fun sendPasswordRecoveryEmail(email: String, token: String) {
        val message = MimeMessageHelper(mailSender.createMimeMessage(), true)
        message.setTo(email)
        message.setSubject("Redefinição de senha")
        message.setText(buildEmailContent(token), true)

        val logoFile = FileSystemResource(File("src/main/resources/logo/logo v2 complete.png"))
        message.addInline("logo", logoFile)

        mailSender.send(message.mimeMessage)
    }

    private fun buildEmailContent(token: String): String {
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
                    <a href="$resetPasswordUrl$token">Redefinir Senha</a>
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