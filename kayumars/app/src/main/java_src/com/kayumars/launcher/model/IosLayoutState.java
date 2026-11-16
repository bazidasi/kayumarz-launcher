package com.kayumars.launcher.model;

import android.content.Context;
import android.content.SharedPreferences;
import com.kayumars.launcher.pkg.AppIndexer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * IosLayoutState — Persistent JSON layout model for Kayumars iOS 17/18 launcher:
 * - Multi-page Home grids (apps + folders)
 * - Frosted Dock slots (4–6 icons)
 * - Today View widgets + optional Home widgets
 * - Muted badge packages, blue new-app dots, hidden library apps, category order
 */
public final class IosLayoutState {

    public static final String KIND_APP = "app";
    public static final String KIND_FOLDER = "folder";

    public static final String WIDGET_CLOCK = "clock";
    public static final String WIDGET_SUGGESTIONS = "suggestions";
    public static final String WIDGET_STATUS = "status";
    public static final String WIDGET_APPWIDGET = "appwidget";

    public static final String SPAN_SMALL = "small";
    public static final String SPAN_MEDIUM = "medium";
    public static final String SPAN_LARGE = "large";

    private static final String PREF_STATE_JSON = "ios_layout_state_v2";

    public static final class HomeSlot {
        public String kind; // KIND_APP or KIND_FOLDER
        public String pkg;
        public String folderId;
        public String folderTitle;
        public final List<String> apps = new ArrayList<String>();

        public static HomeSlot app(String pkg) {
            HomeSlot s = new HomeSlot();
            s.kind = KIND_APP;
            s.pkg = pkg;
            return s;
        }

        public static HomeSlot folder(String id, String title, List<String> pkgs) {
            HomeSlot s = new HomeSlot();
            s.kind = KIND_FOLDER;
            s.folderId = id;
            s.folderTitle = title != null ? title : "Folder";
            if (pkgs != null) s.apps.addAll(pkgs);
            return s;
        }

        public boolean isFolder() {
            return KIND_FOLDER.equals(kind);
        }
    }

    public static final class WidgetSlot {
        public String id;
        public String kind;
        public int appWidgetId;
        public String label;
        public String span; // small, medium, large

        public WidgetSlot(String id, String kind, int appWidgetId, String label, String span) {
            this.id = id;
            this.kind = kind;
            this.appWidgetId = appWidgetId;
            this.label = label;
            this.span = span != null ? span : SPAN_MEDIUM;
        }
    }

    public final List<List<HomeSlot>> pages = new ArrayList<List<HomeSlot>>();
    public final List<String> dock = new ArrayList<String>();
    public final List<WidgetSlot> todayWidgets = new ArrayList<WidgetSlot>();
    public WidgetSlot homeWidget = null;
    public final Set<String> mutedBadgePkgs = new HashSet<String>();
    public final Set<String> newBadgePkgs = new HashSet<String>();
    public final Set<String> hiddenLibraryPkgs = new HashSet<String>();
    public final List<String> libraryCategoryOrder = new ArrayList<String>();
    public boolean initialized = false;

    public static IosLayoutState load(SharedPreferences prefs) {
        IosLayoutState state = new IosLayoutState();
        String raw = prefs.getString(PREF_STATE_JSON, null);
        if (raw == null || raw.isEmpty()) {
            state.ensureDefaultCategoryOrder();
            state.ensureDefaultTodayWidgets();
            return state;
        }
        try {
            JSONObject root = new JSONObject(raw);
            state.initialized = root.optBoolean("initialized", false);

            JSONArray pagesArr = root.optJSONArray("pages");
            if (pagesArr != null) {
                for (int p = 0; p < pagesArr.length(); p++) {
                    JSONArray pageArr = pagesArr.optJSONArray(p);
                    List<HomeSlot> pageSlots = new ArrayList<HomeSlot>();
                    if (pageArr != null) {
                        for (int i = 0; i < pageArr.length(); i++) {
                            JSONObject o = pageArr.optJSONObject(i);
                            if (o == null) continue;
                            String kind = o.optString("kind", KIND_APP);
                            if (KIND_FOLDER.equals(kind)) {
                                List<String> fApps = new ArrayList<String>();
                                JSONArray fa = o.optJSONArray("apps");
                                if (fa != null) {
                                    for (int j = 0; j < fa.length(); j++) {
                                        String pk = fa.optString(j, "");
                                        if (!pk.isEmpty()) fApps.add(pk);
                                    }
                                }
                                if (!fApps.isEmpty()) {
                                    pageSlots.add(HomeSlot.folder(
                                            o.optString("folderId", "f_" + p + "_" + i),
                                            o.optString("folderTitle", "Folder"),
                                            fApps));
                                }
                            } else {
                                String pk = o.optString("pkg", "");
                                if (!pk.isEmpty()) {
                                    pageSlots.add(HomeSlot.app(pk));
                                }
                            }
                        }
                    }
                    state.pages.add(pageSlots);
                }
            }

            JSONArray dockArr = root.optJSONArray("dock");
            if (dockArr != null) {
                for (int i = 0; i < dockArr.length(); i++) {
                    String pk = dockArr.optString(i, "");
                    if (!pk.isEmpty()) state.dock.add(pk);
                }
            }

            JSONArray wArr = root.optJSONArray("todayWidgets");
            if (wArr != null) {
                for (int i = 0; i < wArr.length(); i++) {
                    JSONObject wo = wArr.optJSONObject(i);
                    if (wo != null) {
                        state.todayWidgets.add(new WidgetSlot(
                                wo.optString("id", "w_" + i),
                                wo.optString("kind", WIDGET_CLOCK),
                                wo.optInt("appWidgetId", -1),
                                wo.optString("label", "Widget"),
                                wo.optString("span", SPAN_MEDIUM)));
                    }
                }
            }

            JSONObject hw = root.optJSONObject("homeWidget");
            if (hw != null) {
                state.homeWidget = new WidgetSlot(
                        hw.optString("id", "hw_0"),
                        hw.optString("kind", WIDGET_CLOCK),
                        hw.optInt("appWidgetId", -1),
                        hw.optString("label", "Clock & Date"),
                        hw.optString("span", SPAN_MEDIUM));
            }

            readSet(root.optJSONArray("mutedBadgePkgs"), state.mutedBadgePkgs);
            readSet(root.optJSONArray("newBadgePkgs"), state.newBadgePkgs);
            readSet(root.optJSONArray("hiddenLibraryPkgs"), state.hiddenLibraryPkgs);

            JSONArray catArr = root.optJSONArray("libraryCategoryOrder");
            if (catArr != null) {
                for (int i = 0; i < catArr.length(); i++) {
                    String c = catArr.optString(i, "");
                    if (!c.isEmpty()) state.libraryCategoryOrder.add(c);
                }
            }
        } catch (Exception ignored) {
            state.pages.clear();
            state.dock.clear();
        }
        state.ensureDefaultCategoryOrder();
        if (!state.initialized && state.todayWidgets.isEmpty()) {
            state.ensureDefaultTodayWidgets();
        }
        if (state.pages.isEmpty()) {
            state.pages.add(new ArrayList<HomeSlot>());
        }
        return state;
    }

    public void save(SharedPreferences prefs) {
        try {
            JSONObject root = new JSONObject();
            root.put("initialized", initialized);

            JSONArray pagesArr = new JSONArray();
            for (List<HomeSlot> page : pages) {
                JSONArray pArr = new JSONArray();
                for (HomeSlot s : page) {
                    JSONObject o = new JSONObject();
                    o.put("kind", s.kind);
                    if (s.isFolder()) {
                        o.put("folderId", s.folderId);
                        o.put("folderTitle", s.folderTitle);
                        JSONArray fa = new JSONArray();
                        for (String pk : s.apps) fa.put(pk);
                        o.put("apps", fa);
                    } else {
                        o.put("pkg", s.pkg);
                    }
                    pArr.put(o);
                }
                pagesArr.put(pArr);
            }
            root.put("pages", pagesArr);

            JSONArray dockArr = new JSONArray();
            for (String pk : dock) dockArr.put(pk);
            root.put("dock", dockArr);

            JSONArray wArr = new JSONArray();
            for (WidgetSlot w : todayWidgets) {
                JSONObject wo = new JSONObject();
                wo.put("id", w.id);
                wo.put("kind", w.kind);
                wo.put("appWidgetId", w.appWidgetId);
                wo.put("label", w.label);
                wo.put("span", w.span);
                wArr.put(wo);
            }
            root.put("todayWidgets", wArr);

            if (homeWidget != null) {
                JSONObject hw = new JSONObject();
                hw.put("id", homeWidget.id);
                hw.put("kind", homeWidget.kind);
                hw.put("appWidgetId", homeWidget.appWidgetId);
                hw.put("label", homeWidget.label);
                hw.put("span", homeWidget.span);
                root.put("homeWidget", hw);
            }

            root.put("mutedBadgePkgs", writeSet(mutedBadgePkgs));
            root.put("newBadgePkgs", writeSet(newBadgePkgs));
            root.put("hiddenLibraryPkgs", writeSet(hiddenLibraryPkgs));

            JSONArray catArr = new JSONArray();
            for (String c : libraryCategoryOrder) catArr.put(c);
            root.put("libraryCategoryOrder", catArr);

            prefs.edit().putString(PREF_STATE_JSON, root.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void seedIfNeeded(List<AppIndexer.AppModel> installed, int pageCapacity, int dockCapacity) {
        if (initialized) {
            pruneMissingPackages(installed);
            return;
        }
        pages.clear();
        dock.clear();

        List<HomeSlot> page0 = new ArrayList<HomeSlot>();
        List<HomeSlot> page1 = new ArrayList<HomeSlot>();

        Set<String> dockSet = new HashSet<String>();
        for (AppIndexer.AppModel app : installed) {
            if (dock.size() >= dockCapacity) break;
            String low = (app.pkg + " " + app.label).toLowerCase();
            if (low.contains("dialer") || low.contains("phone") || low.contains("messag")
                    || low.contains("chrome") || low.contains("camera") || low.contains("contacts")) {
                if (!dockSet.contains(app.pkg)) {
                    dock.add(app.pkg);
                    dockSet.add(app.pkg);
                }
            }
        }
        for (AppIndexer.AppModel app : installed) {
            if (dock.size() >= dockCapacity) break;
            if (!dockSet.contains(app.pkg)) {
                dock.add(app.pkg);
                dockSet.add(app.pkg);
            }
        }

        List<String> utilitiesFolder = new ArrayList<String>();
        for (AppIndexer.AppModel app : installed) {
            if (dockSet.contains(app.pkg)) continue;
            String low = (app.pkg + " " + app.label).toLowerCase();
            if (utilitiesFolder.size() < 4 && (low.contains("calc") || low.contains("clock")
                    || low.contains("file") || low.contains("setting") || low.contains("note"))) {
                utilitiesFolder.add(app.pkg);
                continue;
            }
            if (page0.size() < Math.max(8, pageCapacity - 4)) {
                page0.add(HomeSlot.app(app.pkg));
            } else if (page1.size() < Math.max(8, pageCapacity - 6)) {
                page1.add(HomeSlot.app(app.pkg));
            }
        }

        if (utilitiesFolder.size() >= 2) {
            page0.add(HomeSlot.folder("folder_utilities", "Utilities", utilitiesFolder));
        } else {
            for (String u : utilitiesFolder) {
                if (page0.size() < pageCapacity) page0.add(HomeSlot.app(u));
            }
        }

        pages.add(page0);
        if (!page1.isEmpty()) pages.add(page1);
        ensureDefaultTodayWidgets();
        ensureDefaultCategoryOrder();
        initialized = true;
    }

    public void pruneMissingPackages(List<AppIndexer.AppModel> installed) {
        if (installed == null || installed.isEmpty()) return;
        Set<String> valid = new HashSet<String>();
        for (AppIndexer.AppModel app : installed) valid.add(app.pkg);

        for (List<HomeSlot> page : pages) {
            Iterator<HomeSlot> it = page.iterator();
            while (it.hasNext()) {
                HomeSlot s = it.next();
                if (s.isFolder()) {
                    Iterator<String> fit = s.apps.iterator();
                    while (fit.hasNext()) {
                        if (!valid.contains(fit.next())) fit.remove();
                    }
                    if (s.apps.isEmpty()) it.remove();
                } else if (!valid.contains(s.pkg)) {
                    it.remove();
                }
            }
        }
        Iterator<String> dit = dock.iterator();
        while (dit.hasNext()) {
            if (!valid.contains(dit.next())) dit.remove();
        }
        if (pages.isEmpty()) pages.add(new ArrayList<HomeSlot>());
    }

    public boolean containsOnHomeOrDock(String pkg) {
        if (dock.contains(pkg)) return true;
        for (List<HomeSlot> page : pages) {
            for (HomeSlot s : page) {
                if (s.isFolder()) {
                    if (s.apps.contains(pkg)) return true;
                } else if (pkg.equals(s.pkg)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int placeOnFirstFreePage(String pkg, int maxSlotsPerPage, boolean markAsNew) {
        if (containsOnHomeOrDock(pkg)) return 0;
        if (markAsNew) newBadgePkgs.add(pkg);
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).size() < maxSlotsPerPage) {
                pages.get(i).add(HomeSlot.app(pkg));
                return i;
            }
        }
        List<HomeSlot> next = new ArrayList<HomeSlot>();
        next.add(HomeSlot.app(pkg));
        pages.add(next);
        return pages.size() - 1;
    }

    public void removeAppFromHome(String pkg) {
        dock.remove(pkg);
        for (List<HomeSlot> page : pages) {
            Iterator<HomeSlot> it = page.iterator();
            while (it.hasNext()) {
                HomeSlot s = it.next();
                if (s.isFolder()) {
                    s.apps.remove(pkg);
                    if (s.apps.size() == 1) {
                        String survivor = s.apps.get(0);
                        s.kind = KIND_APP;
                        s.pkg = survivor;
                        s.apps.clear();
                    } else if (s.apps.isEmpty()) {
                        it.remove();
                    }
                } else if (pkg.equals(s.pkg)) {
                    it.remove();
                }
            }
        }
        if (pages.isEmpty()) pages.add(new ArrayList<HomeSlot>());
    }

    public HomeSlot findFolderById(String folderId) {
        if (folderId == null) return null;
        for (List<HomeSlot> page : pages) {
            for (HomeSlot s : page) {
                if (s.isFolder() && folderId.equals(s.folderId)) return s;
            }
        }
        return null;
    }

    public void removeFromFolder(String folderId, String pkg) {
        for (List<HomeSlot> page : pages) {
            for (int i = 0; i < page.size(); i++) {
                HomeSlot s = page.get(i);
                if (s.isFolder() && folderId.equals(s.folderId)) {
                    s.apps.remove(pkg);
                    if (s.apps.size() == 1) {
                        page.set(i, HomeSlot.app(s.apps.get(0)));
                    } else if (s.apps.isEmpty()) {
                        page.remove(i);
                    }
                    return;
                }
            }
        }
    }

    public boolean mergeSlotsIntoFolder(int pageIdx, int fromPos, int toPos, String defaultFolderTitle) {
        if (pageIdx < 0 || pageIdx >= pages.size()) return false;
        List<HomeSlot> page = pages.get(pageIdx);
        if (fromPos < 0 || fromPos >= page.size() || toPos < 0 || toPos >= page.size() || fromPos == toPos) {
            return false;
        }
        HomeSlot src = page.get(fromPos);
        HomeSlot dst = page.get(toPos);
        // Never allow folders inside folders
        if (src.isFolder()) return false;
        if (dst.isFolder()) {
            if (!dst.apps.contains(src.pkg)) {
                dst.apps.add(src.pkg);
            }
            page.remove(fromPos);
            return true;
        }
        List<String> combined = new ArrayList<String>();
        combined.add(dst.pkg);
        if (!src.pkg.equals(dst.pkg)) combined.add(src.pkg);
        HomeSlot folder = HomeSlot.folder("f_" + System.currentTimeMillis(), defaultFolderTitle, combined);
        page.set(toPos, folder);
        page.remove(fromPos);
        return true;
    }

    public void reorderSlot(int pageIdx, int fromPos, int toPos) {
        if (pageIdx < 0 || pageIdx >= pages.size()) return;
        List<HomeSlot> page = pages.get(pageIdx);
        if (fromPos < 0 || fromPos >= page.size() || toPos < 0 || toPos >= page.size() || fromPos == toPos) {
            return;
        }
        HomeSlot moved = page.remove(fromPos);
        page.add(toPos, moved);
    }

    public void moveSlotToPage(int fromPage, int fromPos, int toPage, int maxSlotsPerPage) {
        if (fromPage == toPage) return;
        if (fromPage < 0 || fromPage >= pages.size()) return;
        if (toPage < 0 || toPage >= pages.size()) return;
        List<HomeSlot> src = pages.get(fromPage);
        List<HomeSlot> dst = pages.get(toPage);
        if (fromPos < 0 || fromPos >= src.size()) return;
        if (dst.size() >= maxSlotsPerPage) return;
        HomeSlot item = src.remove(fromPos);
        dst.add(item);
    }

    public void addToDock(String pkg, int maxDock) {
        if (pkg == null || pkg.isEmpty()) return;
        if (dock.contains(pkg)) return;
        if (dock.size() >= maxDock && !dock.isEmpty()) {
            String bumped = dock.remove(dock.size() - 1);
            placeOnFirstFreePage(bumped, 24, false);
        }
        dock.add(pkg);
    }

    public void ensureDefaultTodayWidgets() {
        if (!todayWidgets.isEmpty()) return;
        todayWidgets.add(new WidgetSlot("w_clock", WIDGET_CLOCK, -1, "Clock & Date", SPAN_MEDIUM));
        todayWidgets.add(new WidgetSlot("w_suggestions", WIDGET_SUGGESTIONS, -1, "App Suggestions", SPAN_MEDIUM));
        todayWidgets.add(new WidgetSlot("w_status", WIDGET_STATUS, -1, "Offline Vault Status", SPAN_SMALL));
    }

    public void ensureDefaultCategoryOrder() {
        if (!libraryCategoryOrder.isEmpty()) return;
        libraryCategoryOrder.add("SUGGESTIONS");
        libraryCategoryOrder.add("PRODUCTIVITY");
        libraryCategoryOrder.add("SOCIAL");
        libraryCategoryOrder.add("CREATIVITY");
        libraryCategoryOrder.add("UTILITIES");
        libraryCategoryOrder.add("OTHER");
    }

    public void cycleLibraryCategoryOrder() {
        ensureDefaultCategoryOrder();
        if (libraryCategoryOrder.size() > 2) {
            String second = libraryCategoryOrder.remove(1);
            libraryCategoryOrder.add(second);
        }
    }

    private static void readSet(JSONArray arr, Set<String> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "");
            if (!s.isEmpty()) out.add(s);
        }
    }

    private static JSONArray writeSet(Set<String> set) {
        JSONArray arr = new JSONArray();
        for (String s : set) arr.put(s);
        return arr;
    }
}
