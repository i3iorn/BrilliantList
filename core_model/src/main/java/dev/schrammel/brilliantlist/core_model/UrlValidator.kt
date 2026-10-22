package dev.schrammel.brilliantlist.core_model

/**
 * Validates web addresses for the LINK entry type.
 *
 * Only http and https are allowed. An address with another scheme
 * (intent://, javascript:, file://) could make the system do something the
 * user did not expect when the app hands it to ACTION_VIEW.
 */
object UrlValidator {
    fun isValid(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val uri = try {
            java.net.URI(trimmed)
        } catch (e: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }
}
