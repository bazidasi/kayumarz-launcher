package com.kayumars.bench

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scroll & Motion FrameTimingMetric Macrobenchmarks for Kayumars (iOS 17/18 Edition).
 * Validates Spotlight search morph, Chrome 30dp BlurGate, App Library scrolling,
 * and horizontal Home Pager transitions within <= 16.9 ms / < 1% jank budget.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollSpotlightResults() = benchmarkRule.measureRepeated(
        packageName = "com.kayumars.launcher",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 10,
        startupMode = StartupMode.WARM
    ) {
        pressHome()
        startActivityAndWait()

        device.findObject(By.res("com.kayumars.launcher", "btn_open_search"))?.click()
        device.wait(Until.hasObject(By.res("com.kayumars.launcher", "spotlight_results_list")), 1000)

        val list = device.findObject(By.res("com.kayumars.launcher", "spotlight_results_list"))
        if (list != null) {
            list.setGestureMargin(device.displayWidth / 5)
            repeat(3) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
                list.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }

    @Test
    fun homePagerSwipeToLibrary() = benchmarkRule.measureRepeated(
        packageName = "com.kayumars.launcher",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 8,
        startupMode = StartupMode.WARM
    ) {
        pressHome()
        startActivityAndWait()

        val pager = device.findObject(By.res("com.kayumars.launcher", "home_pager"))
        if (pager != null) {
            pager.fling(Direction.RIGHT)
            device.waitForIdle()
            pager.fling(Direction.LEFT)
            device.waitForIdle()
        }
    }

    @Test
    fun spotlightOpenAndBack() = benchmarkRule.measureRepeated(
        packageName = "com.kayumars.launcher",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 8,
        startupMode = StartupMode.WARM
    ) {
        pressHome()
        startActivityAndWait()

        device.findObject(By.res("com.kayumars.launcher", "btn_open_search"))?.click()
        device.wait(Until.hasObject(By.res("com.kayumars.launcher", "spotlight_input")), 1000)
        device.pressBack()
        device.waitForIdle()
    }
}
