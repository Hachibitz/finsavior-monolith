package br.com.finsavior.monolith.finsavior_monolith.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "google.play")
class GooglePlayProperties {
    var packageName: String = "br.com.finsavior"
    /**
     * Path to the service account JSON file on disk (preferred in production —
     * avoids escaping issues when the JSON is embedded in a .properties file).
     */
    var serviceAccountJsonPath: String = ""
    /** JSON content of the Google Play service account (Android Publisher API). */
    var serviceAccountJson: String = ""
    /** Shared secret for Real-Time Developer Notifications (query-param fallback). */
    var rtdnVerificationToken: String = ""
    /**
     * Expected audience of the Pub/Sub push OIDC token. When set, RTDN requests
     * must carry a valid Google-signed JWT (preferred over the query token,
     * which can leak into proxy logs). E.g. the push endpoint URL itself.
     */
    var rtdnOidcAudience: String = ""
    /** Optional: restrict RTDN OIDC tokens to this service account e-mail. */
    var rtdnServiceAccountEmail: String = ""
}
