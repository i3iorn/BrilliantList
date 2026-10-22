package dev.schrammel.brilliantlist

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.schrammel.brilliantlist.core_ui.theme.BrilliantListTheme
import dev.schrammel.brilliantlist.feature_entry.EntryScreen
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility checks against the real app.
 *
 * These are the regressions that are easy to introduce and invisible in a
 * screenshot: a missing content description, a touch target below 48 dp or a
 * control that cannot be reached with a screen reader.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @get:Rule(order = 0)
    val appLockRule = DisableAppLockRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * The journal is emptied before the tests.
     *
     * The tests run against the real app, so the entries of a previous run
     * would change what is on the screen.
     */
    @Before
    fun clearTheJournal() {
        runBlocking {
            EntryPointAccessors
                .fromApplication(
                    composeRule.activity.applicationContext,
                    AppEntryPoint::class.java
                )
                .entryRepository()
                .deleteAll()
        }
    }

    @Test
    fun captureActionsHaveContentDescriptions() {
        composeRule.onNodeWithContentDescription("Add photo").assertExists()
        composeRule.onNodeWithContentDescription("Record audio").assertExists()
        composeRule.onNodeWithContentDescription("Favorites").assertExists()
    }

    @Test
    fun captureActionsHaveLargeTouchTargets() {
        composeRule.onNodeWithContentDescription("Add photo").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Record audio").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Favorites").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun navigationIsLabelled() {
        val crisis = composeRule.activity.getString(R.string.nav_crisis)
        val settings = composeRule.activity.getString(R.string.nav_settings)
        // The app lock decides whether the journal is shown, so the tests have
        // to wait for it before they look at the navigation.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(crisis)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        // The navigation items announce their label.
        composeRule.onAllNodesWithText(crisis).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText(settings).onFirst().assertIsDisplayed()
        // The support action in the top bar has a content description.
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.nav_support)
        ).assertExists()
    }

    @Test
    fun emptyStateExplainsWhatToDo() {
        // A journal without entries has to say what to do.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Nothing here yet. Add your first brilliant thing.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Nothing here yet. Add your first brilliant thing.")
            .assertExists()
    }

    @Test
    fun theCaptureFieldIsReachable() {
        // Capture is a field, not a dialog: it has to be there right away.
        composeRule.onNodeWithText("Your brilliant thing").assertIsDisplayed()
    }

    @Test
    fun theEntriesScreenSurvivesADoubledFontScale() {
        // The capture field and the actions have to stay on the screen when
        // the font is large - a control that is pushed out of the screen
        // cannot be used at all.
        composeRule.activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 2f)
            ) {
                BrilliantListTheme {
                    EntryScreen()
                }
            }
        }

        composeRule.onNodeWithText("Your brilliant thing").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add photo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Record audio").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Favorites").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Recently deleted").assertIsDisplayed()
    }
}
