package dev.schrammel.brilliantlist

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    private fun setSettings(fontScale: Float = 2f) {
        composeRule.activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                BrilliantListTheme {
                    SettingsScreen()
                }
            }
        }
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
    fun switchesStayInsideTheScreen() {
        setSettings()

        val switches = composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .fetchSemanticsNodes()
        assertTrue("no switches found", switches.isNotEmpty())

        // The content has a padding of 16 dp on both sides, so a switch that
        // is pushed out of the row would reach the edge of the screen.
        val metrics = composeRule.activity.resources.displayMetrics
        val contentRight = metrics.widthPixels - 16 * metrics.density
        switches.forEach { switch ->
            assertTrue(
                "a switch is outside of the content: ${switch.boundsInRoot}",
                switch.boundsInRoot.right <= contentRight
            )
        }
    }

    @Test
    fun theLabelsDoNotTakeTheWholeRow() {
        setSettings()

        // The label of a switch shares the row with the switch, so it has to
        // end where the switch begins.
        val label = composeRule.onNodeWithText("Widget auto update").fetchSemanticsNode()
        val switch = composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .fetchSemanticsNodes()
            .first {
                it.boundsInRoot.width > 0 &&
                    it.boundsInRoot.top in label.boundsInRoot.top..label.boundsInRoot.bottom
            }
        assertTrue(
            "the label covers the switch: ${label.boundsInRoot} / ${switch.boundsInRoot}",
            label.boundsInRoot.right <= switch.boundsInRoot.left
        )
    }
}
