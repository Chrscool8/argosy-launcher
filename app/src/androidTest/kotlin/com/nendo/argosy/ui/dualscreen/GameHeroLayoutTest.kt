package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nendo.argosy.domain.model.PresentationStyle
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.UiScaleConfig
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameHeroLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun wideDisplayKeepsMetadataBesideCoverWithinDetailsPane() {
        compose.setContent {
            CompositionLocalProvider(LocalUiScale provides UiScaleConfig(aspectRatioClass = AspectRatioClass.WIDE)) {
                MaterialTheme {
                    GameHero(
                        detail = CompanionDetail(title = "Game title"),
                        style = PresentationStyle(),
                        coverMaxHeight = 240.dp,
                        modifier = Modifier.width(540.dp).testTag("hero"),
                        metadataModifier = Modifier.testTag("metadata")
                    )
                }
            }
        }

        val hero = compose.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithTag("metadata").fetchSemanticsNode().boundsInRoot
        assertTrue(metadata.left >= hero.left + hero.width / 3)
        compose.onNodeWithText("Game title").assertIsDisplayed()
    }

    @Test
    fun narrowHeroAtLargeScaleScrollsWithTheDetailsPage() {
        compose.setContent {
            CompositionLocalProvider(
                LocalUiScale provides UiScaleConfig(scale = 1.5f),
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.5f)
            ) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(220.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            GameHero(
                                detail = CompanionDetail(
                                    title = "A game with a long title",
                                    subtitle = "A handheld platform",
                                    stats = CompanionGameStats(
                                        releaseYear = 2001,
                                        genre = "Role-playing",
                                        communityRating = 85f,
                                        userRating = 8,
                                        playTimeMinutes = 120,
                                        timeToBeatMainSec = 3600,
                                        timeToBeatCompletionistSec = 7200,
                                        status = "finished"
                                    )
                                ),
                                style = PresentationStyle(),
                                coverMaxHeight = 240.dp
                            )
                            Text("Description section")
                        }
                    }
                }
            }
        }

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .assertCountEquals(1)
        compose.onNodeWithText("Description section").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A game with a long title").performScrollTo().assertIsDisplayed()
    }
}
