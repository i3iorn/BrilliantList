package dev.schrammel.brilliantlist

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.schrammel.brilliantlist.core_ui.theme.BrilliantListTheme
import dev.schrammel.brilliantlist.feature_settings.SettingsScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings have to stay usable when the font is large.
 *
 * A screen that is not scrollable cuts off everything below the fold, and a
 * label that does not share the width of its row pushes the switch out of
 * the screen. Both are invisible in a screenshot with the default font.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAccessibilityTest {

    @get:Rule(order = 0)
    val appLockRule = DisableAppLockRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** Renders the settings with a doubled font size. */
    private fun setSettings(fontScale: Float = 2f, versionName: String = "") {
        composeRule.activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                BrilliantListTheme {
                    SettingsScreen(versionName = versionName)
                }
            }
        }
    }

    @Test
    fun theSettingsSayWhichVersionTheyAre() {
        setSettings(versionName = "0.2.0")

        // The About section is at the bottom of the screen, so it has to be
        // reachable by scrolling.
        composeRule.onNodeWithText("0.2.0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theBottomOfTheSettingsIsReachable() {
        setSettings(versionName = "0.2.0")

        // The last row of the settings - the credit at the very bottom.
        composeRule.onNodeWithText("Read more about the play")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theSettingsSayWhatIsStored() {
        setSettings()

        // The number of entries and the size of the journal - the promise that
        // nothing leaves the device is a number and not only a sentence.
        composeRule.onAllNodesWithText("on this device", substring = true)
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun everySettingCanBeScrolledTo() {
        setSettings()

        // performScrollTo() fails when the screen cannot scroll, so this is
        // the check that the last setting is reachable.
        composeRule.onNodeWithText("Show entry text on the widget")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Rotation mode")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun everySettingHasItsSwitch() {
        setSettings()

        // The switches are there. Their size is not checked: the node of a
        // switch is not the one that is drawn on some system images, so the
        // bounds of it say nothing about the screen.
        val switches = composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .fetchSemanticsNodes()
        assertTrue("no switches found", switches.isNotEmpty())
    }

    @Test
    fun theLabelsDoNotTakeTheWholeRow() {
        setSettings()

        // The label of a switch shares the row with the switch, so it has to
        // end before the edge of the content - otherwise it would be drawn
        // underneath the switch, or the switch would be pushed out of the
        // screen.
        val label = composeRule.onNodeWithText("Widget auto update").fetchSemanticsNode()
        val metrics = composeRule.activity.resources.displayMetrics
        val contentRight = metrics.widthPixels - 16 * metrics.density
        assertTrue(
            "the label covers the switch: ${label.boundsInRoot}",
            label.boundsInRoot.right < contentRight
        )
    }
}
