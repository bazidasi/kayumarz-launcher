package com.kayumars.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * mergeSlotsIntoFolder must leave the new folder at toPos in the FINAL list
 * (same convention as reorderSlot), so the folder occupies the grid cell where
 * the drop target was displayed, with no duplicated package.
 */
class IosLayoutStateMergeTest {

    private fun stateWithPage(vararg pkgs: String): IosLayoutState {
        val state = IosLayoutState()
        state.pages.add(pkgs.map { IosLayoutState.HomeSlot.app(it) }.toMutableList())
        return state
    }

    @Test
    fun mergeForward_folderLandsAtTargetIndex() {
        val state = stateWithPage("a", "b", "c", "d")
        assertTrue(state.mergeSlotsIntoFolder(0, 0, 2, "Folder"))
        val page = state.pages[0]
        assertEquals(3, page.size)
        assertEquals(listOf("b", "d"), page.filter { !it.isFolder }.map { it.pkg })
        assertTrue(page[2].isFolder)
        assertEquals(listOf("c", "a"), page[2].apps)
    }

    @Test
    fun mergeBackward_folderLandsAtTargetIndex() {
        val state = stateWithPage("a", "b", "c", "d")
        assertTrue(state.mergeSlotsIntoFolder(0, 2, 0, "Folder"))
        val page = state.pages[0]
        assertEquals(3, page.size)
        assertTrue(page[0].isFolder)
        assertEquals(listOf("a", "c"), page[0].apps)
        assertEquals(listOf("b", "d"), page.drop(1).map { it.pkg })
    }

    @Test
    fun mergeAdjacentForward_folderLandsAtTargetIndex() {
        val state = stateWithPage("a", "b", "c")
        assertTrue(state.mergeSlotsIntoFolder(0, 0, 1, "Folder"))
        val page = state.pages[0]
        assertEquals(2, page.size)
        assertEquals("c", page[0].pkg)
        assertTrue(page[1].isFolder)
        assertEquals(listOf("b", "a"), page[1].apps)
    }

    @Test
    fun mergeAdjacentBackward_folderLandsAtTargetIndex() {
        val state = stateWithPage("a", "b", "c")
        assertTrue(state.mergeSlotsIntoFolder(0, 1, 0, "Folder"))
        val page = state.pages[0]
        assertEquals(2, page.size)
        assertTrue(page[0].isFolder)
        assertEquals(listOf("a", "b"), page[0].apps)
        assertEquals("c", page[1].pkg)
    }

    @Test
    fun mergeIntoExistingFolder_addsAppAndRemovesSource() {
        val state = IosLayoutState()
        val folder = IosLayoutState.HomeSlot.folder("f1", "Folder", listOf("c", "x"))
        state.pages.add(
            mutableListOf(IosLayoutState.HomeSlot.app("a"), IosLayoutState.HomeSlot.app("b"), folder)
        )
        assertTrue(state.mergeSlotsIntoFolder(0, 0, 2, "Folder"))
        val page = state.pages[0]
        assertEquals(2, page.size)
        assertEquals("b", page[0].pkg)
        assertTrue(page[1].isFolder)
        assertEquals(listOf("c", "x", "a"), page[1].apps)
    }
}