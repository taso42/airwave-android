package com.vibex.airwave

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DashboardAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun simulateWithoutBluetooth() {
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[AirwaveViewModel::class.java].useSimulation(true)
        }
    }

    @Test fun settingsSwitchesHaveNamesAndToggleActions() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Keep streaming in background")
            .performScrollTo().assertHasClickAction().assertIsToggleable()
        compose.onNodeWithContentDescription("Simulated data")
            .performScrollTo().assertHasClickAction().assertIsToggleable().assertIsOn()
        compose.onNodeWithContentDescription("Simulated data").performClick().assertIsOff()
    }

    @Test fun readingAnnouncesFrozenStateAndResumeAction() {
        val flow = compose.onNodeWithText("Flow")
        flow.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not frozen"))
        flow.performClick()
        flow.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Frozen"))
        flow.assert(SemanticsMatcher("Resume action") { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label == "Resume charts and readings"
        })
        flow.performClick()
        flow.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not frozen"))
    }
}
