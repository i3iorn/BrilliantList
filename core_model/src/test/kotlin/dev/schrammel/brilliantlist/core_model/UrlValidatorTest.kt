package dev.schrammel.brilliantlist.core_model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The link validation is the gate between the journal and the system's
 * ACTION_VIEW intent. Only http and https may pass.
 */
class UrlValidatorTest {

    @Test
    fun acceptsHttpAndHttps() {
        assertTrue(UrlValidator.isValid("http://example.com"))
        assertTrue(UrlValidator.isValid("https://example.com"))
        assertTrue(UrlValidator.isValid("https://example.com/path?query=1#fragment"))
        assertTrue(UrlValidator.isValid("HTTP://EXAMPLE.COM"))
    }

    @Test
    fun rejectsOtherSchemes() {
        assertFalse(UrlValidator.isValid("ftp://example.com"))
        assertFalse(UrlValidator.isValid("mailto:someone@example.com"))
        assertFalse(UrlValidator.isValid("javascript:alert(1)"))
        assertFalse(UrlValidator.isValid("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(UrlValidator.isValid("file:///etc/passwd"))
        assertFalse(UrlValidator.isValid("content://com.example/data"))
        assertFalse(UrlValidator.isValid("tel:+123456789"))
        assertFalse(UrlValidator.isValid("sms:+123456789"))
    }

    @Test
    fun rejectsEmptyOrMalformedAddresses() {
        assertFalse(UrlValidator.isValid(""))
        assertFalse(UrlValidator.isValid("   "))
        assertFalse(UrlValidator.isValid("not a url"))
        assertFalse(UrlValidator.isValid("https://"))
        assertFalse(UrlValidator.isValid("://example.com"))
    }

    @Test
    fun trimsWhitespace() {
        assertTrue(UrlValidator.isValid("  https://example.com  "))
    }
}
