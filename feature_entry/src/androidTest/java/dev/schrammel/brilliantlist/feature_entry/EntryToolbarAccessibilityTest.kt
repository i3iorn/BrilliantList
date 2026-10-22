package dev.schrammel.brilliantlist.feature_entry

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility checks for the capture toolbar.
 *
 * The strings are written out literally on purpose: the accessibility test
 * asserts the wording a screen reader actually announces.
 */
@RunWith(AndroidJUnit4::class)
class EntryToolbarAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setToolbar(fontScale: Float = 1f) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                MaterialTheme {
                    EntryToolbar(
                        isRecording = false,
                        onPickImage = {},
                        onToggleRecording = {},
                        onAddLink = {},
                        onPaste = {}
                    )
                }
            }
        }
    }

    @Test
    fun everyActionHasAContentDescription() {
        setToolbar()

        composeRule.onNodeWithContentDescription("Add photo").assertExists()
        composeRule.onNodeWithContentDescription("Record audio").assertExists()
        composeRule.onNodeWithContentDescription("Add link").assertExists()
        composeRule.onNodeWithContentDescription("Paste").assertExists()
    }

    @Test
    fun touchTargetsAreAtLeast48Dp() {
        setToolbar()

        composeRule.onNodeWithContentDescription("Add photo").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Add photo").assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Record audio").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Add link").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Paste").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun toolbarSurvivesADoubledFontScale() {
        setToolbar(fontScale = 2f)

        // Nothing may be clipped away at a large font scale.
        composeRule.onNodeWithContentDescription("Add photo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Record audio").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add link").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Paste").assertIsDisplayed()
    }
}
