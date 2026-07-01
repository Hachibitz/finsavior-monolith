package br.com.finsavior.monolith.finsavior_monolith.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "google.play")
class GooglePlayProperties {
    var packageName: String = "br.com.finsavior"
    /** JSON content of the Google Play service account (Android Publisher API). */
    var serviceAccountJson: String = ""
    /** Shared secret for Real-Time Developer Notifications (optional). */
    var rtdnVerificationToken: String = ""
}
