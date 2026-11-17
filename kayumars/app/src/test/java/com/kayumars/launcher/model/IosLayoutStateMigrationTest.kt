package com.kayumars.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Persisted-state migrations must be idempotent: running them on every load
 * cannot drop user widgets, reorder user categories, or duplicate rows.
 */
class IosLayoutStateMigrationTest {

    private fun slot(id: String, kind: String) =
        IosLayoutState.WidgetSlot(id, kind, -1, kind, IosLayoutState.SPAN_MEDIUM)

    @Test
    fun dedupeTodayWidgets_keepsFirstBuiltInAndAllBoundWidgets_idempotent() {
        val state = IosLayoutState()
        state.todayWidgets.add(slot("a", IosLayoutState.WIDGET_CLOCK))
        state.todayWidgets.add(slot("b", IosLayoutState.WIDGET_CLOCK))
        state.todayWidgets.add(slot("c", IosLayoutState.WIDGET_SUGGESTIONS))
        state.todayWidgets.add(
            IosLayoutState.WidgetSlot("d", IosLayoutState.WIDGET_APPWIDGET, 7, "W1", IosLayoutState.SPAN_MEDIUM)
        )
        state.todayWidgets.add(
            IosLayoutState.WidgetSlot("e", IosLayoutState.WIDGET_APPWIDGET, 8, "W2", IosLayoutState.SPAN_MEDIUM)
        )

        state.dedupeTodayWidgets()
        val first = state.todayWidgets.map { it.id }
        state.dedupeTodayWidgets()

        assertEquals(listOf("a", "c", "d", "e"), first)
        assertEquals(first, state.todayWidgets.map { it.id })
    }

    @Test
    fun dedupeTodayWidgets_doesNotReseedOrWipeLayout() {
        val state = IosLayoutState()
        state.todayWidgets.add(slot("only", IosLayoutState.WIDGET_STATUS))
        state.ensureDefaultTodayWidgets()
        assertEquals(listOf("only"), state.todayWidgets.map { it.id })
    }

    @Test
    fun ensureDefaultCategoryOrder_appendsMissingKeysOnce_keepingUserOrder() {
        val state = IosLayoutState()
        state.libraryCategoryOrder.clear()
        state.libraryCategoryOrder.addAll(listOf("OTHER", "SOCIAL"))

        state.ensureDefaultCategoryOrder()
        state.ensureDefaultCategoryOrder()

        assertEquals(
            listOf("OTHER", "SOCIAL", "SUGGESTIONS", "PRODUCTIVITY", "CREATIVITY", "GAMES", "UTILITIES"),
            state.libraryCategoryOrder
        )
    }
}
