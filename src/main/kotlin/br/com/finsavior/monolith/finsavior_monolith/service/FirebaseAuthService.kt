package br.com.finsavior.monolith.finsavior_monolith.service

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import org.springframework.stereotype.Service

@Service
class FirebaseAuthService {

    fun validateFirebaseToken(idTokenString: String?): FirebaseToken {
        try {
            if (idTokenString == null) {
                throw IllegalArgumentException("Token nulo.")
            }
            return FirebaseAuth.getInstance().verifyIdToken(idTokenString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Falha ao verificar token Firebase", e)
        }
    }
}
