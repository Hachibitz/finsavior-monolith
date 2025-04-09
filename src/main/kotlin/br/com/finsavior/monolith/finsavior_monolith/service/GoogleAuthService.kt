package br.com.finsavior.monolith.finsavior_monolith.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
@Deprecated("Utilizar FirebaseAuthService")
class GoogleAuthService {

    companion object {
        val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
    }

    @Value("\${google.client.id}")
    private val googleClientId: String? = null

    fun validateGoogleToken(idTokenString: String?): GoogleIdToken.Payload {
        try {
            val verifier = GoogleIdTokenVerifier.Builder(
                NetHttpTransport(),
                JSON_FACTORY
            ).setAudience(mutableListOf<String?>(googleClientId)).build()

            val idToken = verifier.verify(idTokenString)

            if (idToken != null) {
                return idToken.payload
            } else {
                throw IllegalArgumentException("Invalid ID token")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to verify ID token", e)
        }
    }
}