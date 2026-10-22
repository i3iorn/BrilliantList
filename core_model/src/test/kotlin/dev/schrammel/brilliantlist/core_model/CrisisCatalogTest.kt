package dev.schrammel.brilliantlist.core_model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crisis page is the most safety critical screen in the app, so the
 * region mapping is unit tested here (core_model is a pure JVM module, so
 * this runs on the JVM in CI).
 */
class CrisisCatalogTest {

    @Test
    fun normalizesCanonicalCodes() {
        assertEquals("US", CrisisCatalog.normalize("US"))
        assertEquals("GB", CrisisCatalog.normalize("GB"))
        assertEquals("CA", CrisisCatalog.normalize("CA"))
        assertEquals("AU", CrisisCatalog.normalize("AU"))
        assertEquals("DE", CrisisCatalog.normalize("DE"))
    }

    @Test
    fun normalizesCommonAliasesAndCasing() {
        assertEquals("US", CrisisCatalog.normalize("usa"))
        assertEquals("US", CrisisCatalog.normalize("United States"))
        assertEquals("GB", CrisisCatalog.normalize("UK"))
        assertEquals("GB", CrisisCatalog.normalize(" england "))
    }

    @Test
    fun unknownRegionsAreNotSilentlyAccepted() {
        assertNull(CrisisCatalog.normalize(""))
        assertNull(CrisisCatalog.normalize("   "))
        assertNull(CrisisCatalog.normalize("Atlantis"))
    }

    @Test
    fun everySupportedRegionHasItsOwnCatalog() {
        for (country in CrisisCatalog.countries) {
            val resources = CrisisCatalog.resourcesFor(country.code)
            assertTrue("${country.code} has no resources", resources.isNotEmpty())
            assertNotEquals(
                "${country.code} falls back to the generic catalog",
                CrisisCatalog.fallbackResources,
                resources
            )
            assertTrue(
                "${country.code} has a resource without a phone number",
                resources.all { it.phone.isNotBlank() }
            )
        }
    }

    @Test
    fun unknownRegionsFallBackToGenericResources() {
        assertEquals(CrisisCatalog.fallbackResources, CrisisCatalog.resourcesFor("Atlantis"))
    }

    @Test
    fun rawLookupMatchesNormalizedLookup() {
        assertEquals(
            CrisisCatalog.resourcesFor("US"),
            CrisisCatalog.resourcesFor("United States")
        )
    }

    @Test
    fun textLinesAreNotOfferedAsPhoneCalls() {
        // A text service cannot be dialled, so it has to carry the TEXT type
        // and a message body. Otherwise the crisis screen would offer a call
        // that can never work.
        val textLines = CrisisCatalog.countries
            .flatMap { CrisisCatalog.resourcesFor(it.code) }
            .filter { it.contact == CrisisContact.TEXT }

        assertTrue("no text service in the catalog", textLines.isNotEmpty())
        textLines.forEach { resource ->
            assertTrue(
                "${resource.name} has no message body",
                !resource.messageBody.isNullOrBlank()
            )
            assertTrue(
                "${resource.name} has no instruction",
                !resource.instruction.isNullOrBlank()
            )
        }
    }

    @Test
    fun callablesHaveANumber() {
        CrisisCatalog.countries
            .flatMap { CrisisCatalog.resourcesFor(it.code) }
            .filter { it.contact == CrisisContact.CALL }
            .forEach { resource ->
                assertTrue(
                    "${resource.name} cannot be called without a number",
                    resource.phone.isNotBlank()
                )
            }
    }

    @Test
    fun informationOnlyResourcesHaveNoNumber() {
        CrisisCatalog.fallbackResources
            .filter { it.contact == CrisisContact.INFO }
            .forEach { resource ->
                assertTrue(
                    "${resource.name} should not carry a number",
                    resource.phone.isBlank()
                )
            }
    }

    @Test
    fun swedenHasTheExpectedHelplines() {
        val resources = CrisisCatalog.resourcesFor("SE")
        assertTrue("SE has no resources", resources.isNotEmpty())

        // The emergency number is the first resource and the one that must
        // always be right.
        val emergency = resources.firstOrNull { it.isEmergency }
        assertTrue("SE has no emergency resource", emergency != null)
        assertEquals("112", emergency?.phone)

        // The Swedish helplines are plausible, well-known services.
        val expected = mapOf(
            "Mind Självmordslinjen" to "90101",
            "BRIS" to "116 111",
            "Jourhavande medmänniska" to "08-702 16 80",
            "1177 Vårdguiden" to "1177"
        )
        expected.forEach { (name, phone) ->
            val resource = resources.firstOrNull { it.name == name }
            assertTrue("SE is missing $name", resource != null)
            assertEquals("$name has the wrong number", phone, resource?.phone)
            assertEquals("$name should be callable", CrisisContact.CALL, resource?.contact)
            assertTrue("$name has no verified date", !resource?.verifiedAt.isNullOrBlank())
        }
    }
}
