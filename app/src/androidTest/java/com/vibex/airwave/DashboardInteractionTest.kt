package com.vibex.airwave

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.vibex.airwave.ble.LiveReading
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercise the real dashboard controls; packets are injected without a Bluetooth connection. */
class DashboardInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: AirwaveViewModel
    private var previousCharts = emptySet<String>()
    private val cards = listOf("Flow", "Mask pressure", "Leak", "Respiratory rate", "Tidal volume", "Minute ventilation")
    private val charts = listOf("Flow · L/min", "Mask pressure · cmH₂O", "Leak · L/min",
        "Respiratory rate · breaths/min", "Tidal volume · mL", "Minute ventilation · L/min")

    @Before fun seedDashboard() {
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[AirwaveViewModel::class.java]
            previousCharts = model.enabledCharts
            model.useSimulation(false)
            model.window = 30
            model.additionalChartNames.forEach { model.setChartEnabled(it, true) }
            packet(0, .5f)
            model.frozen = null // Publish the seeded frame immediately.
        }
    }

    @After fun restoreSettings() {
        compose.runOnIdle {
            model.additionalChartNames.forEach { model.setChartEnabled(it, it in previousCharts) }
            model.clearDisplay()
        }
    }

    private fun packet(second: Int, flow: Float) {
        model.acceptLiveReading(LiveReading("2026-10-17T00:00:%02d.000Z".format(second), 40,
            listOf(flow), listOf(5f), emptyMap()))
    }

    private fun control(text: String) = compose.onNode(hasText(text) and hasClickAction())
    private fun actionLabel(label: String) = SemanticsMatcher("click action labeled $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }
    private fun assertSharedAction(label: String) {
        // Six cards and six charts share one frozen view, including offscreen charts.
        compose.onAllNodes(actionLabel(label)).assertCountEquals(12)
    }

    @Test fun everyWindowChipUpdatesSelectionAndAllChartAxes() {
        val windows = listOf(30 to "30 sec", 60 to "60 sec", 300 to "5 min", 900 to "15 min", 3600 to "60 min", 30 to "30 sec")
        for ((seconds, label) in windows) {
            control(label).performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(seconds, model.window) }
            val axis = if (seconds >= 300) "−${seconds / 60} min" else "−${seconds}s"
            compose.onAllNodesWithText(axis, useUnmergedTree = true).assertCountEquals(6)
            windows.map { it.second }.distinct().filter { it != label }.forEach {
                control(it).assertIsNotSelected()
            }
        }
    }

    @Test fun tappingEachReadingFreezesAndResumesTheSharedView() {
        for (card in cards) {
            control(card).performScrollTo().performClick()
            assertSharedAction("Resume charts and readings")
            compose.runOnIdle { assertNotNull(model.frozen) }
            control(card).performClick()
            assertSharedAction("Freeze charts and readings")
            compose.runOnIdle { assertNull(model.frozen) }
        }
    }

    @Test fun chartsAndCardsShareSnapshotAndResumeCatchesUpToIncomingData() {
        control("Flow").performScrollTo().performClick()
        val frozenEnd = compose.runOnIdle { model.chartEnd }
        compose.runOnIdle { packet(1, 1f) }
        // New acquisition must not change the visible card or frozen time axis.
        control("Flow").assertTextContains("30.0")
        compose.runOnIdle { assertEquals(frozenEnd, model.chartEnd, 0f) }
        compose.onAllNodesWithText("Frozen", useUnmergedTree = true).assertCountEquals(6)
        control(charts.first()).performScrollTo().performClick()
        assertSharedAction("Freeze charts and readings")
        control("Flow").assertTextContains("60.0")
        compose.runOnIdle { assertTrue(model.chartEnd > frozenEnd) }
        compose.onAllNodesWithText("Now", useUnmergedTree = true).assertCountEquals(6)
        // Each optional chart must be wired to the same action, not just the first chart.
        for (chart in charts) {
            control(chart).performScrollTo().performClick()
            assertSharedAction("Resume charts and readings")
            control("Mask pressure").performScrollTo().performClick()
            assertSharedAction("Freeze charts and readings")
        }
    }
}
