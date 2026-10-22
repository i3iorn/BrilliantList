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
        assertEquals(CrisisRegion.US, CrisisCatalog.normalize("US"))
        assertEquals(CrisisRegion.GB, CrisisCatalog.normalize("GB"))
        assertEquals(CrisisRegion.CA, CrisisCatalog.normalize("CA"))
        assertEquals(CrisisRegion.AU, CrisisCatalog.normalize("AU"))
        assertEquals(CrisisRegion.DE, CrisisCatalog.normalize("DE"))
    }

    @Test
    fun normalizesCommonAliasesAndCasing() {
        assertEquals(CrisisRegion.US, CrisisCatalog.normalize("usa"))
        assertEquals(CrisisRegion.US, CrisisCatalog.normalize("United States"))
        assertEquals(CrisisRegion.GB, CrisisCatalog.normalize("UK"))
        assertEquals(CrisisRegion.GB, CrisisCatalog.normalize(" england "))
    }

    @Test
    fun unknownRegionsAreNotSilentlyAccepted() {
        assertNull(CrisisCatalog.normalize(""))
        assertNull(CrisisCatalog.normalize("   "))
        assertNull(CrisisCatalog.normalize("Atlantis"))
    }

    @Test
    fun everySupportedRegionHasItsOwnCatalog() {
        for (region in CrisisRegion.entries) {
            val resources = CrisisCatalog.resourcesFor(region)
            assertTrue("${region.code} has no resources", resources.isNotEmpty())
            assertNotEquals(
                "${region.code} falls back to the generic catalog",
                CrisisCatalog.fallbackResources,
                resources
            )
            assertTrue(
                "${region.code} has a resource without a phone number",
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
            CrisisCatalog.resourcesFor(CrisisRegion.US),
            CrisisCatalog.resourcesFor("United States")
        )
    }

    @Test
    fun textLinesAreNotOfferedAsPhoneCalls() {
        // A text service cannot be dialled, so it has to carry the TEXT type
        // and a message body. Otherwise the crisis screen would offer a call
        // that can never work.
        val textLines = CrisisRegion.entries
            .flatMap { CrisisCatalog.resourcesFor(it) }
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
        CrisisRegion.entries
            .flatMap { CrisisCatalog.resourcesFor(it) }
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
}
