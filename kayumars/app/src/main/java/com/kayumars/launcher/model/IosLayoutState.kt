package com.kayumars.launcher.model

import android.content.SharedPreferences
import com.kayumars.launcher.bridge.AppEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * IosLayoutState — Persistent JSON layout model for Kayumars iOS 17/18 launcher (Kotlin shell).
 */
class IosLayoutState {

    data class HomeSlot(
        var kind: String = KIND_APP,
        var pkg: String = "",
        var folderId: String = "",
        var folderTitle: String = "Folder",
        val apps: MutableList<String> = mutableListOf()
    ) {
        val isFolder: Boolean get() = kind == KIND_FOLDER

        companion object {
            fun app(pkg: String) = HomeSlot(kind = KIND_APP, pkg = pkg)
            fun folder(id: String, title: String, pkgs: List<String>) =
                HomeSlot(kind = KIND_FOLDER, folderId = id, folderTitle = title, apps = pkgs.toMutableList())
        }
    }

    data class WidgetSlot(
        val id: String,
        val kind: String,
        val appWidgetId: Int = -1,
        val label: String = "Widget",
        var span: String = SPAN_MEDIUM
    )

    val pages = mutableListOf<MutableList<HomeSlot>>()
    val dock = mutableListOf<String>()
    val todayWidgets = mutableListOf<WidgetSlot>()
    var homeWidget: WidgetSlot? = null
    val mutedBadgePkgs = mutableSetOf<String>()
    val newBadgePkgs = mutableSetOf<String>()
    val hiddenLibraryPkgs = mutableSetOf<String>()
    val libraryCategoryOrder = mutableListOf<String>()
    var initialized = false

    fun save(prefs: SharedPreferences) {
        try {
            val root = JSONObject().apply {
                put("initialized", initialized)
                put("pages", JSONArray().apply {
                    pages.forEach { page ->
                        put(JSONArray().apply {
                            page.forEach { s ->
                                put(JSONObject().apply {
                                    put("kind", s.kind)
                                    if (s.isFolder) {
                                        put("folderId", s.folderId)
                                        put("folderTitle", s.folderTitle)
                                        put("apps", JSONArray().apply { s.apps.forEach { put(it) } })
                                    } else {
                                        put("pkg", s.pkg)
                                    }
                                })
                            }
                        })
                    }
                })
                put("dock", JSONArray().apply { dock.forEach { put(it) } })
                put("todayWidgets", JSONArray().apply {
                    todayWidgets.forEach { w ->
                        put(JSONObject().apply {
                            put("id", w.id)
                            put("kind", w.kind)
                            put("appWidgetId", w.appWidgetId)
                            put("label", w.label)
                            put("span", w.span)
                        })
                    }
                })
                homeWidget?.let { hw ->
                    put("homeWidget", JSONObject().apply {
                        put("id", hw.id)
                        put("kind", hw.kind)
                        put("appWidgetId", hw.appWidgetId)
                        put("label", hw.label)
                        put("span", hw.span)
                    })
                }
                put("mutedBadgePkgs", JSONArray().apply { mutedBadgePkgs.forEach { put(it) } })
                put("newBadgePkgs", JSONArray().apply { newBadgePkgs.forEach { put(it) } })
                put("hiddenLibraryPkgs", JSONArray().apply { hiddenLibraryPkgs.forEach { put(it) } })
                put("libraryCategoryOrder", JSONArray().apply { libraryCategoryOrder.forEach { put(it) } })
            }
            prefs.edit().putString(PREF_STATE_JSON, root.toString()).apply()
        } catch (_: Exception) {}
    }

    fun seedIfNeeded(installed: List<AppEntry>, pageCapacity: Int, dockCapacity: Int) {
        if (initialized) {
            pruneMissingPackages(installed)
            return
        }
        pages.clear()
        dock.clear()
        val page0 = mutableListOf<HomeSlot>()
        val page1 = mutableListOf<HomeSlot>()
        val dockSet = mutableSetOf<String>()

        for (app in installed) {
            if (dock.size >= dockCapacity) break
            val low = "${app.pkg} ${app.label}".lowercase()
            if (low.contains("dialer") || low.contains("phone") || low.contains("messag") ||
                low.contains("chrome") || low.contains("camera") || low.contains("contacts")
            ) {
                if (dockSet.add(app.pkg)) dock.add(app.pkg)
            }
        }
        for (app in installed) {
            if (dock.size >= dockCapacity) break
            if (dockSet.add(app.pkg)) dock.add(app.pkg)
        }

        val utilitiesFolder = mutableListOf<String>()
        for (app in installed) {
            if (dockSet.contains(app.pkg)) continue
            val low = "${app.pkg} ${app.label}".lowercase()
            if (utilitiesFolder.size < 4 && (low.contains("calc") || low.contains("clock") ||
                        low.contains("file") || low.contains("setting") || low.contains("note"))
            ) {
                utilitiesFolder.add(app.pkg)
                continue
            }
            if (page0.size < (pageCapacity - 4).coerceAtLeast(8)) {
                page0.add(HomeSlot.app(app.pkg))
            } else if (page1.size < (pageCapacity - 6).coerceAtLeast(8)) {
                page1.add(HomeSlot.app(app.pkg))
            }
        }
        if (utilitiesFolder.size >= 2) {
            page0.add(HomeSlot.folder("folder_utilities", "Utilities", utilitiesFolder))
        } else {
            utilitiesFolder.forEach { if (page0.size < pageCapacity) page0.add(HomeSlot.app(it)) }
        }
        pages.add(page0)
        if (page1.isNotEmpty()) pages.add(page1)
        ensureDefaultTodayWidgets()
        ensureDefaultCategoryOrder()
        initialized = true
    }

    fun pruneMissingPackages(installed: List<AppEntry>) {
        if (installed.isEmpty()) return
        val valid = installed.map { it.pkg }.toSet()
        pages.forEach { page ->
            val it = page.iterator()
            while (it.hasNext()) {
                val s = it.next()
                if (s.isFolder) {
                    s.apps.retainAll(valid)
                    if (s.apps.isEmpty()) it.remove()
                } else if (!valid.contains(s.pkg)) {
                    it.remove()
                }
            }
        }
        dock.retainAll(valid)
        if (pages.isEmpty()) pages.add(mutableListOf())
    }

    fun containsOnHomeOrDock(pkg: String): Boolean {
        if (dock.contains(pkg)) return true
        return pages.any { page ->
            page.any { s -> if (s.isFolder) s.apps.contains(pkg) else s.pkg == pkg }
        }
    }

    fun placeOnFirstFreePage(pkg: String, maxSlotsPerPage: Int, markAsNew: Boolean): Int {
        if (containsOnHomeOrDock(pkg)) return 0
        if (markAsNew) newBadgePkgs.add(pkg)
        pages.forEachIndexed { idx, page ->
            if (page.size < maxSlotsPerPage) {
                page.add(HomeSlot.app(pkg))
                return idx
            }
        }
        pages.add(mutableListOf(HomeSlot.app(pkg)))
        return pages.lastIndex
    }

    fun removeAppFromHome(pkg: String) {
        dock.remove(pkg)
        pages.forEach { page ->
            val it = page.iterator()
            while (it.hasNext()) {
                val s = it.next()
                if (s.isFolder) {
                    s.apps.remove(pkg)
                    if (s.apps.size == 1) {
                        s.kind = KIND_APP
                        s.pkg = s.apps.first()
                        s.apps.clear()
                    } else if (s.apps.isEmpty()) {
                        it.remove()
                    }
                } else if (s.pkg == pkg) {
                    it.remove()
                }
            }
        }
        if (pages.isEmpty()) pages.add(mutableListOf())
    }

    fun findFolderById(folderId: String?): HomeSlot? {
        if (folderId == null) return null
        return pages.asSequence().flatten().firstOrNull { it.isFolder && it.folderId == folderId }
    }

    fun removeFromFolder(folderId: String, pkg: String) {
        pages.forEach { page ->
            val idx = page.indexOfFirst { it.isFolder && it.folderId == folderId }
            if (idx >= 0) {
                val s = page[idx]
                s.apps.remove(pkg)
                if (s.apps.size == 1) {
                    page[idx] = HomeSlot.app(s.apps.first())
                } else if (s.apps.isEmpty()) {
                    page.removeAt(idx)
                }
                return
            }
        }
    }

    fun mergeSlotsIntoFolder(pageIdx: Int, fromPos: Int, toPos: Int, defaultFolderTitle: String): Boolean {
        val page = pages.getOrNull(pageIdx) ?: return false
        if (fromPos !in page.indices || toPos !in page.indices || fromPos == toPos) return false
        val src = page[fromPos]
        val dst = page[toPos]
        if (src.isFolder) return false
        if (dst.isFolder) {
            if (!dst.apps.contains(src.pkg)) dst.apps.add(src.pkg)
            page.removeAt(fromPos)
            return true
        }
        val combined = mutableListOf(dst.pkg).apply { if (src.pkg != dst.pkg) add(src.pkg) }
        page[toPos] = HomeSlot.folder("f_${System.currentTimeMillis()}", defaultFolderTitle, combined)
        page.removeAt(fromPos)
        return true
    }

    fun reorderSlot(pageIdx: Int, fromPos: Int, toPos: Int) {
        val page = pages.getOrNull(pageIdx) ?: return
        if (fromPos !in page.indices || toPos !in page.indices || fromPos == toPos) return
        val moved = page.removeAt(fromPos)
        page.add(toPos, moved)
    }

    fun moveSlotToPage(fromPage: Int, fromPos: Int, toPage: Int, maxSlotsPerPage: Int) {
        if (fromPage == toPage) return
        val src = pages.getOrNull(fromPage) ?: return
        val dst = pages.getOrNull(toPage) ?: return
        if (fromPos !in src.indices || dst.size >= maxSlotsPerPage) return
        dst.add(src.removeAt(fromPos))
    }

    fun addToDock(pkg: String, maxDock: Int) {
        if (pkg.isEmpty() || dock.contains(pkg)) return
        if (dock.size >= maxDock && dock.isNotEmpty()) {
            val bumped = dock.removeAt(dock.lastIndex)
            placeOnFirstFreePage(bumped, 24, false)
        }
        dock.add(pkg)
    }

    fun ensureDefaultTodayWidgets() {
        if (todayWidgets.isNotEmpty()) return
        todayWidgets.add(WidgetSlot("w_clock", WIDGET_CLOCK, -1, "Clock & Date", SPAN_MEDIUM))
        todayWidgets.add(WidgetSlot("w_suggestions", WIDGET_SUGGESTIONS, -1, "App Suggestions", SPAN_MEDIUM))
        todayWidgets.add(WidgetSlot("w_status", WIDGET_STATUS, -1, "Offline Vault Status", SPAN_SMALL))
    }

    fun ensureDefaultCategoryOrder() {
        if (libraryCategoryOrder.isNotEmpty()) return
        libraryCategoryOrder.addAll(listOf("SUGGESTIONS", "PRODUCTIVITY", "SOCIAL", "CREATIVITY", "UTILITIES", "OTHER"))
    }

    fun cycleLibraryCategoryOrder() {
        ensureDefaultCategoryOrder()
        if (libraryCategoryOrder.size > 2) {
            val second = libraryCategoryOrder.removeAt(1)
            libraryCategoryOrder.add(second)
        }
    }

    companion object {
        const val KIND_APP = "app"
        const val KIND_FOLDER = "folder"
        const val WIDGET_CLOCK = "clock"
        const val WIDGET_SUGGESTIONS = "suggestions"
        const val WIDGET_STATUS = "status"
        const val WIDGET_APPWIDGET = "appwidget"
        const val SPAN_SMALL = "small"
        const val SPAN_MEDIUM = "medium"
        const val SPAN_LARGE = "large"
        private const val PREF_STATE_JSON = "ios_layout_state_v2"

        fun load(prefs: SharedPreferences): IosLayoutState {
            val state = IosLayoutState()
            val raw = prefs.getString(PREF_STATE_JSON, null)
            if (raw.isNullOrEmpty()) {
                state.ensureDefaultCategoryOrder()
                state.ensureDefaultTodayWidgets()
                return state
            }
            try {
                val root = JSONObject(raw)
                state.initialized = root.optBoolean("initialized", false)
                val pagesArr = root.optJSONArray("pages")
                if (pagesArr != null) {
                    for (p in 0 until pagesArr.length()) {
                        val pArr = pagesArr.optJSONArray(p)
                        val pageSlots = mutableListOf<HomeSlot>()
                        if (pArr != null) {
                            for (i in 0 until pArr.length()) {
                                val o = pArr.optJSONObject(i) ?: continue
                                if (o.optString("kind", KIND_APP) == KIND_FOLDER) {
                                    val fa = o.optJSONArray("apps")
                                    val apps = mutableListOf<String>()
                                    if (fa != null) {
                                        for (j in 0 until fa.length()) {
                                            val pk = fa.optString(j, "")
                                            if (pk.isNotEmpty()) apps.add(pk)
                                        }
                                    }
                                    if (apps.isNotEmpty()) {
                                        pageSlots.add(HomeSlot.folder(o.optString("folderId", "f_${p}_$i"), o.optString("folderTitle", "Folder"), apps))
                                    }
                                } else {
                                    val pk = o.optString("pkg", "")
                                    if (pk.isNotEmpty()) pageSlots.add(HomeSlot.app(pk))
                                }
                            }
                        }
                        state.pages.add(pageSlots)
                    }
                }
                root.optJSONArray("dock")?.let { d ->
                    for (i in 0 until d.length()) d.optString(i, "").takeIf { it.isNotEmpty() }?.let { state.dock.add(it) }
                }
                root.optJSONArray("todayWidgets")?.let { wa ->
                    for (i in 0 until wa.length()) {
                        wa.optJSONObject(i)?.let { wo ->
                            state.todayWidgets.add(
                                WidgetSlot(
                                    wo.optString("id", "w_$i"),
                                    wo.optString("kind", WIDGET_CLOCK),
                                    wo.optInt("appWidgetId", -1),
                                    wo.optString("label", "Widget"),
                                    wo.optString("span", SPAN_MEDIUM)
                                )
                            )
                        }
                    }
                }
                root.optJSONObject("homeWidget")?.let { hw ->
                    state.homeWidget = WidgetSlot(
                        hw.optString("id", "hw_0"),
                        hw.optString("kind", WIDGET_CLOCK),
                        hw.optInt("appWidgetId", -1),
                        hw.optString("label", "Clock & Date"),
                        hw.optString("span", SPAN_MEDIUM)
                    )
                }
                readSet(root.optJSONArray("mutedBadgePkgs"), state.mutedBadgePkgs)
                readSet(root.optJSONArray("newBadgePkgs"), state.newBadgePkgs)
                readSet(root.optJSONArray("hiddenLibraryPkgs"), state.hiddenLibraryPkgs)
                root.optJSONArray("libraryCategoryOrder")?.let { ca ->
                    for (i in 0 until ca.length()) ca.optString(i, "").takeIf { it.isNotEmpty() }?.let { state.libraryCategoryOrder.add(it) }
                }
            } catch (_: Exception) {
                state.pages.clear()
                state.dock.clear()
            }
            state.ensureDefaultCategoryOrder()
            if (!state.initialized && state.todayWidgets.isEmpty()) state.ensureDefaultTodayWidgets()
            if (state.pages.isEmpty()) state.pages.add(mutableListOf())
            return state
        }

        private fun readSet(arr: JSONArray?, out: MutableSet<String>) {
            if (arr == null) return
            for (i in 0 until arr.length()) arr.optString(i, "").takeIf { it.isNotEmpty() }?.let { out.add(it) }
        }
    }
}
