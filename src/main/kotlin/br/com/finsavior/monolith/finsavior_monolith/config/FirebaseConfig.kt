package br.com.finsavior.monolith.finsavior_monolith.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import mu.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.annotation.PostConstruct

@Configuration
class FirebaseConfig {

    private val log = KotlinLogging.logger {}

    @PostConstruct
    fun initializeFirebaseAdmin() {
        val externalFilePath = "/app/firebase/serviceAccountKey.json"
        val externalFile = File(externalFilePath)

        val serviceAccount: InputStream = if (externalFile.exists()) {
            log.info { "Carregando Firebase credentials de arquivo externo: $externalFilePath" }
            FileInputStream(externalFile)
        } else {
            log.info { "Arquivo externo não encontrado. Carregando Firebase credentials do classpath." }
            ClassPathResource("firebase/serviceAccountKey.json").inputStream
        }

        serviceAccount.use { stream ->
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build()
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
                log.info { "FirebaseAdmin inicializado com sucesso." }
            }
        }
    }
}
