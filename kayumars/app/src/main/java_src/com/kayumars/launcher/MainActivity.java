package com.kayumars.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.app.role.RoleManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.kayumars.launcher.bridge.Core;
import com.kayumars.launcher.history.LaunchLogger;
import com.kayumars.launcher.model.IosLayoutState;
import com.kayumars.launcher.notify.KayumarsNotificationListener;
import com.kayumars.launcher.pkg.AppIndexer;
import com.kayumars.launcher.pkg.AppMetadataHelper;
import com.kayumars.launcher.pkg.PackageChangeReceiver;
import com.kayumars.launcher.service.KayumarsLockService;
import com.kayumars.launcher.ui.motion.BackInvokedMotion;
import com.kayumars.launcher.ui.motion.BlurGate;
import com.kayumars.launcher.ui.motion.MotionDebug;
import com.kayumars.launcher.ui.motion.MotionSpec;
import com.kayumars.launcher.ui.motion.SpringPreset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainActivity — iOS 17/18 "California Neutral" Home Screen Launcher Controller for Kayumars (کیومرث).
 * Preserves Rust Core search/ranking/history while implementing:
 * - Multi-page Home ([Today (-1)] | [Home 0..N] | [App Library (+1)]) with 0.5x wallpaper parallax
 * - Jiggle / Edit Mode with staggered 1.5° tilt & page add/remove
 * - Drag & Drop reordering, Dock drop target, edge page switching, 150ms hover-to-Folder
 * - Zoom-morphed Folders with 3x3 preview, 3-col inner paginated grid & inline rename
 * - Blurred 240dp Context Menus with LauncherApps dynamic quick actions
 * - Spotlight Search overlay backed by Rust Core.search / Core.topApps
 * - App Library 2-column category tiles & expanded category browser
 * - Today View with AppWidgetHost + offline widgets & widget picker
 * - Full-screen iOS Settings, Notification Badges, Work Profile badges, RTL & Predictive Back
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "kayumars_prefs";
    private static final String PREF_THEME = "theme_mode"; // system, light, dark, amoled
    private static final String PREF_ICON_SHAPE = "icon_shape"; // squircle, circle, rounded_square, system
    private static final String PREF_SHOW_LABELS = "ios_show_labels";
    private static final String PREF_SHOW_SEARCH_PILL = "ios_show_search_pill";
    private static final String PREF_SHOW_TODAY = "ios_show_today_page";
    private static final String PREF_SHOW_LIBRARY = "ios_show_library_page";
    private static final String PREF_GRID_COLUMNS = "ios_grid_columns"; // 4..6
    private static final String PREF_DOCK_CAPACITY = "ios_dock_capacity"; // 4..6
    private static final String PREF_WALLPAPER_DIM = "ios_wallpaper_dim"; // 0..40
    private static final String PREF_WALLPAPER_BLUR = "ios_wallpaper_blur"; // 0..16
    private static final String PREF_NOTIF_BADGES = "ios_notif_badges";
    private static final String PREF_NEW_DOT = "ios_new_app_dot";
    private static final String PREF_SPOTLIGHT_SUGGESTIONS = "ios_spotlight_suggestions";
    private static final String PREF_SPOTLIGHT_SETTINGS = "ios_spotlight_settings";
    private static final String PREF_GESTURE_LOCK = "ios_gesture_double_tap_lock";
    private static final String PREF_GESTURE_PINCH = "ios_gesture_pinch_overview";
    private static final String PREF_GESTURE_SWIPE_SPOTLIGHT = "ios_gesture_swipe_spotlight";
    private static final String PREF_MOTION_DEBUG = "motion_debug_hud";
    private static final String PREF_HOME_DISMISSED = "home_prompt_dismissed";

    private static final int REQ_ROLE_HOME = 1001;
    private static final int REQ_BIND_APPWIDGET = 1002;
    private static final int REQ_CONFIGURE_APPWIDGET = 1003;
    private static final int APPWIDGET_HOST_ID = 0x4B4159;

    private static final int ROW_TYPE_HEADER = 0;
    private static final int ROW_TYPE_TOP_HIT = 1;
    private static final int ROW_TYPE_APP = 2;
    private static final int ROW_TYPE_SETTING = 3;

    // Preferences & State
    private SharedPreferences prefs;
    private IosLayoutState layoutState;
    private String themeMode = "system";
    private String iconShape = "squircle";
    private boolean showLabels = true;
    private boolean showSearchPill = true;
    private boolean showTodayPage = true;
    private boolean showLibraryPage = true;
    private int gridColumns = 4;
    private int dockCapacity = 4;
    private int wallpaperDimPct = 0;
    private int wallpaperBlurDp = 0;
    private boolean notifBadgesEnabled = true;
    private boolean newDotEnabled = true;
    private boolean spotlightSuggestionsEnabled = true;
    private boolean spotlightSettingsEnabled = true;
    private boolean gestureDoubleTapLock = true;
    private boolean gesturePinchOverview = true;
    private boolean gestureSwipeSpotlight = true;

    // Runtime Data
    private final List<AppIndexer.AppModel> allApps = new ArrayList<AppIndexer.AppModel>();
    private final Map<String, AppIndexer.AppModel> appByPkg = new HashMap<String, AppIndexer.AppModel>();
    private final Set<String> knownPackages = new HashSet<String>();
    private final Set<String> workProfilePackages = new HashSet<String>();
    private final List<SettingItem> systemSettings = new ArrayList<SettingItem>();
    private final List<SpotlightRow> spotlightRows = new ArrayList<SpotlightRow>();
    private final List<AppIndexer.AppModel> spotlightGridApps = new ArrayList<AppIndexer.AppModel>();
    private final List<AppIndexer.AppModel> spotlightSuggestionApps = new ArrayList<AppIndexer.AppModel>();
    private final List<AppIndexer.AppModel> folderVisibleApps = new ArrayList<AppIndexer.AppModel>();
    private final List<AppIndexer.AppModel> expandedLibraryApps = new ArrayList<AppIndexer.AppModel>();

    private final LruCache<String, Drawable> iconCache = new LruCache<String, Drawable>(180);
    private final ExecutorService iconExecutor = Executors.newFixedThreadPool(2);
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // Views
    private View rootLayout;
    private View wallpaperBlurLayer;
    private View wallpaperDimScrim;
    private View editModeBar;
    private TextView btnAddPage;
    private TextView btnRemovePage;
    private TextView btnDoneEdit;
    private FrameLayout homePager;
    private LinearLayout pageDotsRow;
    private View btnOpenSearch;
    private FrameLayout pageDock;
    private LinearLayout dockRow;
    private View contextScrimOverlay;

    // Folder Modal Views
    private View cardFolderOverlay;
    private View folderModalCard;
    private EditText folderTitleInput;
    private GridView folderAppsGrid;
    private View folderPagerBar;
    private TextView btnFolderPrev;
    private TextView tvFolderPage;
    private TextView btnFolderNext;
    private SimpleCellAdapter folderAdapter;

    // Context Menu Views
    private View menuContextOverlay;
    private LinearLayout menuContextCard;
    private TextView menuAppTitle;
    private LinearLayout menuShortcutsContainer;
    private LinearLayout menuActionsContainer;

    // Spotlight Views
    private View overlaySpotlight;
    private View spotlightSheet;
    private EditText spotlightInput;
    private View btnSpotlightClear;
    private View btnSpotlightCancel;
    private View btnOpenSettings;
    private View spotlightSuggestionsBlock;
    private GridView spotlightSuggestionsGrid;
    private View spotlightAppsGridBlock;
    private GridView spotlightAppsGrid;
    private ListView spotlightResultsList;
    private View spotlightEmptyState;
    private TextView tvSpotlightEmpty;
    private SimpleCellAdapter spotlightSuggestionsAdapter;
    private SimpleCellAdapter spotlightGridAdapter;
    private SpotlightResultsAdapter spotlightResultsAdapter;

    // Overview, Widget Picker & Settings Views
    private View overlayPageOverview;
    private LinearLayout overviewPagesRow;
    private View btnOverviewDone;
    private View sheetWidgetPicker;
    private LinearLayout widgetPickerList;
    private View btnWidgetPickerClose;
    private View pageSettings;
    private LinearLayout settingsSectionsContainer;
    private View btnSettingsDone;

    // Drag Overlay & Debug HUD
    private View dragDropRing;
    private FrameLayout dragSnapshotView;
    private ImageView dragSnapshotIcon;
    private TextView motionDebugHud;

    // Pager & Mode State
    private final List<PageSpec> virtualPages = new ArrayList<PageSpec>();
    private int currentVirtualIndex = -1;
    private boolean isEditMode = false;
    private boolean isSpotlightOpen = false;
    private boolean isSettingsOpen = false;
    private boolean isContextMenuOpen = false;
    private boolean isFolderOpen = false;
    private boolean isOverviewOpen = false;
    private boolean isWidgetPickerOpen = false;
    private String openFolderId = null;
    private int openFolderPage = 0;
    private String expandedLibraryCategory = null;
    private String widgetPickerTarget = "today"; // today or home
    private View liftedContextAnchor = null;

    // Touch, Drag, Jiggle & Motion Infrastructure
    private int touchSlopPx = 24;
    private boolean isPagerDragging = false;
    private boolean isSpotlightPullDragging = false;
    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private float pagerStartScrollX = 0f;
    private long lastEmptyTapMs = 0L;
    private VelocityTracker pagerVelocityTracker;
    private ScaleGestureDetector pinchDetector;
    private final List<ObjectAnimator> jiggleAnimators = new ArrayList<ObjectAnimator>();

    // Drag & Drop State
    private boolean isDraggingItem = false;
    private boolean dragFromDock = false;
    private int dragSourcePageIdx = -1;
    private int dragSourceSlotPos = -1;
    private String dragAppPkg = null;
    private boolean dragIsFolder = false;
    private int dragHoverSlotPos = -1;
    private boolean dragHoverDock = false;
    private final Runnable folderMergeTrigger = new Runnable() {
        @Override
        public void run() {
            if (!isDraggingItem || dragIsFolder || dragHoverSlotPos < 0) return;
            showMergeRingAtHomeSlot(dragSourcePageIdx, dragHoverSlotPos);
        }
    };
    private int edgeSwitchDir = 0;
    private final Runnable edgePageSwitchRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isDraggingItem || edgeSwitchDir == 0) return;
            int homeIdx = getCurrentHomePageIndex();
            int nextHome = homeIdx + edgeSwitchDir;
            if (nextHome >= 0 && nextHome < layoutState.pages.size()) {
                snapToVirtualPage(homePageIndexToVirtual(nextHome), 0f);
                dragSourcePageIdx = nextHome;
            }
        }
    };

    // System Receivers, Widgets, Motion & Back Stack
    private PackageChangeReceiver packageReceiver;
    private BroadcastReceiver timeTickReceiver;
    private boolean receiversRegistered = false;
    private MotionDebug motionDebug;
    private BackInvokedMotion backInvokedMotion;
    private AppWidgetManager appWidgetManager;
    private AppWidgetHost appWidgetHost;
    private AppWidgetProviderInfo pendingBindInfo;
    private int pendingBindWidgetId = -1;

    private static final class SettingItem {
        final String title;
        final String subtitle;
        final String action;
        SettingItem(String title, String subtitle, String action) {
            this.title = title;
            this.subtitle = subtitle;
            this.action = action;
        }
    }

    private static final class SpotlightRow {
        final int type;
        final String header;
        final AppIndexer.AppModel app;
        final SettingItem setting;
        SpotlightRow(int type, String header, AppIndexer.AppModel app, SettingItem setting) {
            this.type = type;
            this.header = header;
            this.app = app;
            this.setting = setting;
        }
    }

    private static final class PageSpec {
        static final int TYPE_TODAY = 0;
        static final int TYPE_HOME = 1;
        static final int TYPE_LIBRARY = 2;
        final int type;
        final int homePageIndex;
        View view;
        PageSpec(int type, int homePageIndex) {
            this.type = type;
            this.homePageIndex = homePageIndex;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        applySavedThemeBeforeInflation();
        super.onCreate(savedInstanceState);
        configureSystemBars();

        setContentView(R.layout.activity_main);
        Core.init(this);
        layoutState = IosLayoutState.load(prefs);
        loadPreferences();

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)) {
            try {
                appWidgetManager = AppWidgetManager.getInstance(this);
                appWidgetHost = new AppWidgetHost(this, APPWIDGET_HOST_ID);
            } catch (Throwable ignored) {
                appWidgetManager = null;
                appWidgetHost = null;
            }
        }
        touchSlopPx = ViewConfiguration.get(this).getScaledTouchSlop();

        bindViews();
        initSystemSettingsList();
        initAdapters();
        initMotionInfra();
        setupListeners();
        applyWallpaperVisuals();
        applySearchPillVisibility();

        registerDynamicReceivers();
        KayumarsNotificationListener.setBadgeUpdateListener(new KayumarsNotificationListener.BadgeUpdateListener() {
            @Override
            public void onBadgesUpdated() {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        rebuildAllVisiblePages();
                    }
                });
            }
        });

        scanInstalledApps(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            if (appWidgetHost != null) appWidgetHost.startListening();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            if (appWidgetHost != null) appWidgetHost.stopListening();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (motionDebug != null && prefs.getBoolean(PREF_MOTION_DEBUG, false)) {
            motionDebug.setEnabled(true);
        }
        if (allApps.isEmpty()) {
            scanInstalledApps(false);
        } else {
            rebuildAllVisiblePages();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (motionDebug != null) motionDebug.stop();
        cancelJiggleAnimations();
        Core.saveNow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing system Home returns cleanly to Home Page 0
        dismissAllOverlaysToHome();
    }

    @Override
    public void onBackPressed() {
        if (!handlePredictiveBackStep()) {
            // Launcher never exits on Home back press
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        if (backInvokedMotion != null) backInvokedMotion.unregister();
        if (motionDebug != null) motionDebug.stop();
        MotionSpec.cancelAll();
        cancelJiggleAnimations();
        KayumarsNotificationListener.setBadgeUpdateListener(null);
        unregisterDynamicReceivers();
        iconExecutor.shutdownNow();
    }

    // ------------------------------------------------------------------------
    // Theme & Preferences
    // ------------------------------------------------------------------------

    private void applySavedThemeBeforeInflation() {
        themeMode = prefs.getString(PREF_THEME, "system");
        if ("amoled".equals(themeMode)) {
            setTheme(R.style.Theme_Kayumars_Amoled);
        } else if ("light".equals(themeMode)) {
            setTheme(R.style.Theme_Kayumars_Light);
        } else if ("dark".equals(themeMode)) {
            setTheme(R.style.Theme_Kayumars_Dark);
        } else {
            int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            setTheme(night == Configuration.UI_MODE_NIGHT_YES
                    ? R.style.Theme_Kayumars_Dark : R.style.Theme_Kayumars_Light);
        }
    }

    private void configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController ctrl = getWindow().getInsetsController();
            if (ctrl != null) {
                boolean lightTheme = "light".equals(themeMode);
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ctrl.setSystemBarsAppearance(lightTheme ? mask : 0, mask);
            }
        }
    }

    private void loadPreferences() {
        iconShape = prefs.getString(PREF_ICON_SHAPE, "squircle");
        showLabels = prefs.getBoolean(PREF_SHOW_LABELS, true);
        showSearchPill = prefs.getBoolean(PREF_SHOW_SEARCH_PILL, true);
        showTodayPage = prefs.getBoolean(PREF_SHOW_TODAY, true);
        showLibraryPage = prefs.getBoolean(PREF_SHOW_LIBRARY, true);
        gridColumns = Math.max(4, Math.min(6, prefs.getInt(PREF_GRID_COLUMNS, 4)));
        dockCapacity = Math.max(4, Math.min(6, prefs.getInt(PREF_DOCK_CAPACITY, 4)));
        wallpaperDimPct = Math.max(0, Math.min(40, prefs.getInt(PREF_WALLPAPER_DIM, 0)));
        wallpaperBlurDp = Math.max(0, Math.min(16, prefs.getInt(PREF_WALLPAPER_BLUR, 0)));
        notifBadgesEnabled = prefs.getBoolean(PREF_NOTIF_BADGES, true);
        newDotEnabled = prefs.getBoolean(PREF_NEW_DOT, true);
        spotlightSuggestionsEnabled = prefs.getBoolean(PREF_SPOTLIGHT_SUGGESTIONS, true);
        spotlightSettingsEnabled = prefs.getBoolean(PREF_SPOTLIGHT_SETTINGS, true);
        gestureDoubleTapLock = prefs.getBoolean(PREF_GESTURE_LOCK, true);
        gesturePinchOverview = prefs.getBoolean(PREF_GESTURE_PINCH, true);
        gestureSwipeSpotlight = prefs.getBoolean(PREF_GESTURE_SWIPE_SPOTLIGHT, true);
    }

    private int getPageCapacity() {
        return gridColumns * 6;
    }

    // ------------------------------------------------------------------------
    // View Binding & Initialization
    // ------------------------------------------------------------------------

    private void bindViews() {
        rootLayout = findViewById(R.id.root_layout);
        wallpaperBlurLayer = findViewById(R.id.wallpaper_blur_layer);
        wallpaperDimScrim = findViewById(R.id.wallpaper_dim_scrim);
        editModeBar = findViewById(R.id.edit_mode_bar);
        btnAddPage = findViewById(R.id.btn_add_page);
        btnRemovePage = findViewById(R.id.btn_remove_page);
        btnDoneEdit = findViewById(R.id.btn_done_edit);
        homePager = findViewById(R.id.home_pager);
        pageDotsRow = findViewById(R.id.page_dots_row);
        btnOpenSearch = findViewById(R.id.btn_open_search);
        pageDock = findViewById(R.id.page_dock);
        dockRow = findViewById(R.id.dock_row);
        contextScrimOverlay = findViewById(R.id.context_scrim_overlay);

        cardFolderOverlay = findViewById(R.id.card_folder);
        folderModalCard = findViewById(R.id.folder_modal_card);
        folderTitleInput = findViewById(R.id.folder_title_input);
        folderAppsGrid = findViewById(R.id.folder_apps_grid);
        folderPagerBar = findViewById(R.id.folder_pager_bar);
        btnFolderPrev = findViewById(R.id.btn_folder_prev);
        tvFolderPage = findViewById(R.id.tv_folder_page);
        btnFolderNext = findViewById(R.id.btn_folder_next);

        menuContextOverlay = findViewById(R.id.menu_context);
        menuContextCard = findViewById(R.id.menu_context_card);
        menuAppTitle = findViewById(R.id.menu_app_title);
        menuShortcutsContainer = findViewById(R.id.menu_shortcuts_container);
        menuActionsContainer = findViewById(R.id.menu_actions_container);

        overlaySpotlight = findViewById(R.id.overlay_spotlight);
        spotlightSheet = findViewById(R.id.spotlight_sheet);
        spotlightInput = findViewById(R.id.spotlight_input);
        btnSpotlightClear = findViewById(R.id.btn_spotlight_clear);
        btnSpotlightCancel = findViewById(R.id.btn_spotlight_cancel);
        btnOpenSettings = findViewById(R.id.btn_open_settings);
        spotlightSuggestionsBlock = findViewById(R.id.spotlight_suggestions_block);
        spotlightSuggestionsGrid = findViewById(R.id.spotlight_suggestions_grid);
        spotlightAppsGridBlock = findViewById(R.id.spotlight_apps_grid_block);
        spotlightAppsGrid = findViewById(R.id.spotlight_apps_grid);
        spotlightResultsList = findViewById(R.id.spotlight_results_list);
        spotlightEmptyState = findViewById(R.id.spotlight_empty_state);
        tvSpotlightEmpty = findViewById(R.id.tv_spotlight_empty);

        overlayPageOverview = findViewById(R.id.overlay_page_overview);
        overviewPagesRow = findViewById(R.id.overview_pages_row);
        btnOverviewDone = findViewById(R.id.btn_overview_done);

        sheetWidgetPicker = findViewById(R.id.sheet_widget_picker);
        widgetPickerList = findViewById(R.id.widget_picker_list);
        btnWidgetPickerClose = findViewById(R.id.btn_widget_picker_close);

        pageSettings = findViewById(R.id.page_settings);
        settingsSectionsContainer = findViewById(R.id.settings_sections_container);
        btnSettingsDone = findViewById(R.id.btn_settings_done);

        dragDropRing = findViewById(R.id.drag_drop_ring);
        dragSnapshotView = findViewById(R.id.drag_snapshot_view);
        dragSnapshotIcon = findViewById(R.id.drag_snapshot_icon);
        motionDebugHud = findViewById(R.id.motion_debug_hud);

        // Edge-to-edge WindowInsets padding on home shell, dock, and overlay sheets
        rootLayout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                View homeShell = findViewById(R.id.home_shell);
                if (homeShell != null) {
                    homeShell.setPadding(0, top, 0, bottom);
                }
                if (spotlightSheet != null) {
                    int padH = Math.round(MotionSpec.dp(MainActivity.this, 16f));
                    spotlightSheet.setPadding(padH, top + Math.round(MotionSpec.dp(MainActivity.this, 12f)), padH, bottom);
                }
                if (pageSettings != null) {
                    pageSettings.setPadding(0, top, 0, bottom);
                }
                return insets;
            }
        });
    }

    private void initSystemSettingsList() {
        systemSettings.clear();
        systemSettings.add(new SettingItem(getString(R.string.settings), "Kayumars iOS Preferences", "kayumars://settings"));
        systemSettings.add(new SettingItem("Wi-Fi & Internet", "Wireless & cellular settings", Settings.ACTION_WIFI_SETTINGS));
        systemSettings.add(new SettingItem("Bluetooth", "Connected audio & devices", Settings.ACTION_BLUETOOTH_SETTINGS));
        systemSettings.add(new SettingItem("Display & Brightness", "Theme, font & screen", Settings.ACTION_DISPLAY_SETTINGS));
        systemSettings.add(new SettingItem("Notifications", "App alerts & listener access", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        systemSettings.add(new SettingItem("Accessibility", "Double-tap lock & motion", Settings.ACTION_ACCESSIBILITY_SETTINGS));
        systemSettings.add(new SettingItem("Default Home App", "RoleManager home selection", Settings.ACTION_HOME_SETTINGS));
    }

    private void initAdapters() {
        folderAdapter = new SimpleCellAdapter(folderVisibleApps, true);
        folderAppsGrid.setAdapter(folderAdapter);

        spotlightSuggestionsAdapter = new SimpleCellAdapter(spotlightSuggestionApps, false);
        spotlightSuggestionsGrid.setAdapter(spotlightSuggestionsAdapter);

        spotlightGridAdapter = new SimpleCellAdapter(spotlightGridApps, false);
        spotlightAppsGrid.setAdapter(spotlightGridAdapter);

        spotlightResultsAdapter = new SpotlightResultsAdapter();
        spotlightResultsList.setAdapter(spotlightResultsAdapter);
    }

    private void initMotionInfra() {
        motionDebug = new MotionDebug(motionDebugHud);
        if (prefs.getBoolean(PREF_MOTION_DEBUG, false)) {
            motionDebug.setEnabled(true);
        }

        // Apply single-pass RenderEffect strictly to dedicated backdrop layer; foreground cards use translucent frosted drawables
        BlurGate.applyWallpaperBlur(wallpaperBlurLayer, wallpaperBlurDp);

        backInvokedMotion = new BackInvokedMotion(this, new BackInvokedMotion.Host() {
            @Override
            public View getActiveFrontSurface() {
                if (isSettingsOpen) return pageSettings;
                if (isContextMenuOpen) return menuContextCard;
                if (isFolderOpen) return folderModalCard;
                if (isWidgetPickerOpen) return sheetWidgetPicker;
                if (isSpotlightOpen) return spotlightSheet;
                if (isOverviewOpen) return overlayPageOverview;
                return homePager;
            }

            @Override
            public boolean hasActiveOverlay() {
                return hasAnyDismissibleBackTarget();
            }

            @Override
            public void onCommitPredictiveBack() {
                handlePredictiveBackStep();
            }
        });
        backInvokedMotion.register();

        pinchDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (!gesturePinchOverview) return false;
                float factor = detector.getScaleFactor();
                if (factor < 0.84f && !isOverviewOpen && !isSpotlightOpen && !isSettingsOpen && !isFolderOpen) {
                    openPageOverview();
                    return true;
                } else if (factor > 1.15f && isOverviewOpen) {
                    closePageOverview();
                    return true;
                }
                return false;
            }
        });
    }

    private void setupListeners() {
        btnOpenSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSpotlight();
            }
        });

        btnDoneEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setEditMode(false);
            }
        });

        btnAddPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutState.pages.add(new ArrayList<IosLayoutState.HomeSlot>());
                layoutState.save(prefs);
                rebuildVirtualPager(homePageIndexToVirtual(layoutState.pages.size() - 1));
                updateEditModeTopBar();
            }
        });

        btnRemovePage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int homeIdx = getCurrentHomePageIndex();
                if (homeIdx >= 0 && homeIdx < layoutState.pages.size() && layoutState.pages.size() > 1) {
                    if (layoutState.pages.get(homeIdx).isEmpty()) {
                        layoutState.pages.remove(homeIdx);
                        layoutState.save(prefs);
                        int targetHome = Math.min(homeIdx, layoutState.pages.size() - 1);
                        rebuildVirtualPager(homePageIndexToVirtual(targetHome));
                        updateEditModeTopBar();
                    }
                }
            }
        });

        contextScrimOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isContextMenuOpen) dismissIosContextMenu();
                else if (isFolderOpen) closeFolder();
                else if (isOverviewOpen) closePageOverview();
            }
        });

        // Folder interactions
        cardFolderOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFolder();
            }
        });
        folderModalCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Consume tap inside modal
            }
        });
        folderTitleInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveOpenFolderTitle();
                    hideKeyboard(v);
                    return true;
                }
                return false;
            }
        });
        btnFolderPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (openFolderPage > 0) {
                    openFolderPage--;
                    refreshOpenFolderGrid();
                }
            }
        });
        btnFolderNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IosLayoutState.HomeSlot f = layoutState.findFolderById(openFolderId);
                if (f != null && (openFolderPage + 1) * 9 < f.apps.size()) {
                    openFolderPage++;
                    refreshOpenFolderGrid();
                }
            }
        });
        folderAppsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < folderVisibleApps.size()) {
                    AppIndexer.AppModel app = folderVisibleApps.get(position);
                    closeFolder();
                    launchAppWithFeedback(view, app.pkg, false);
                }
            }
        });
        folderAppsGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < folderVisibleApps.size()) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showAppContextMenu(view, folderVisibleApps.get(position), false, false, openFolderId);
                    return true;
                }
                return false;
            }
        });

        // Spotlight listeners
        btnSpotlightCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSpotlight();
            }
        });
        btnSpotlightClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                spotlightInput.setText("");
            }
        });
        btnOpenSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettingsPage();
            }
        });
        spotlightInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString() : "";
                btnSpotlightClear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                runSpotlightSearch(q);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        spotlightInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                    for (SpotlightRow row : spotlightRows) {
                        if (row.type == ROW_TYPE_TOP_HIT || row.type == ROW_TYPE_APP) {
                            launchAppWithFeedback(v, row.app.pkg, true);
                            closeSpotlight();
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        spotlightSuggestionsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < spotlightSuggestionApps.size()) {
                    launchAppWithFeedback(view, spotlightSuggestionApps.get(position).pkg, true);
                    closeSpotlight();
                }
            }
        });
        spotlightSuggestionsGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < spotlightSuggestionApps.size()) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showAppContextMenu(view, spotlightSuggestionApps.get(position), false, false, null);
                    return true;
                }
                return false;
            }
        });
        spotlightAppsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < spotlightGridApps.size()) {
                    launchAppWithFeedback(view, spotlightGridApps.get(position).pkg, true);
                    closeSpotlight();
                }
            }
        });
        spotlightResultsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < spotlightRows.size()) {
                    SpotlightRow row = spotlightRows.get(position);
                    if (row.type == ROW_TYPE_SETTING && row.setting != null) {
                        openSystemSettingAction(row.setting.action);
                    } else if (row.app != null) {
                        launchAppWithFeedback(view, row.app.pkg, true);
                        closeSpotlight();
                    }
                }
            }
        });

        menuContextOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissIosContextMenu();
            }
        });
        btnOverviewDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePageOverview();
            }
        });
        sheetWidgetPicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeWidgetPicker();
            }
        });
        btnWidgetPickerClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeWidgetPicker();
            }
        });
        btnSettingsDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSettingsPage();
            }
        });

        setupPagerTouchHandling();
    }

    // ------------------------------------------------------------------------
    // Multi-Page Horizontal Workspace ([Today] | [Home 0..N] | [App Library])
    // ------------------------------------------------------------------------

    private int homePageIndexToVirtual(int homeIdx) {
        return (showTodayPage ? 1 : 0) + homeIdx;
    }

    private int getCurrentHomePageIndex() {
        if (currentVirtualIndex < 0 || currentVirtualIndex >= virtualPages.size()) return 0;
        PageSpec spec = virtualPages.get(currentVirtualIndex);
        return spec.type == PageSpec.TYPE_HOME ? spec.homePageIndex : -1;
    }

    private void rebuildVirtualPager(int targetVirtualIdx) {
        virtualPages.clear();
        homePager.removeAllViews();

        if (showTodayPage) {
            PageSpec today = new PageSpec(PageSpec.TYPE_TODAY, -1);
            today.view = buildTodayPageView();
            virtualPages.add(today);
            homePager.addView(today.view);
        }

        if (layoutState.pages.isEmpty()) {
            layoutState.pages.add(new ArrayList<IosLayoutState.HomeSlot>());
        }
        for (int i = 0; i < layoutState.pages.size(); i++) {
            PageSpec home = new PageSpec(PageSpec.TYPE_HOME, i);
            home.view = buildHomePageView(i);
            virtualPages.add(home);
            homePager.addView(home.view);
        }

        if (showLibraryPage) {
            PageSpec lib = new PageSpec(PageSpec.TYPE_LIBRARY, -1);
            lib.view = buildLibraryPageView();
            virtualPages.add(lib);
            homePager.addView(lib.view);
        }

        int fallbackHome = showTodayPage ? 1 : 0;
        if (targetVirtualIdx < 0 || targetVirtualIdx >= virtualPages.size()) {
            targetVirtualIdx = Math.min(fallbackHome, Math.max(0, virtualPages.size() - 1));
        }
        currentVirtualIndex = targetVirtualIdx;
        positionPagesAtOffset(0f, false);
        rebuildPageDots();
        rebuildDock();
        updateEditModeTopBar();
    }

    private void rebuildAllVisiblePages() {
        rebuildVirtualPager(currentVirtualIndex);
    }

    private void positionPagesAtOffset(float dragDeltaPx, boolean animate) {
        final int width = homePager.getWidth() > 0
                ? homePager.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        boolean rtl = homePager.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int dirSign = rtl ? -1 : 1;

        for (int i = 0; i < virtualPages.size(); i++) {
            View pv = virtualPages.get(i).view;
            if (pv == null) continue;
            float targetX = ((i - currentVirtualIndex) * width) * dirSign + dragDeltaPx;
            if (animate) {
                SpringPreset.animate(pv, View.TRANSLATION_X, targetX, SpringPreset.PAGE_SNAP, 0f, null);
            } else {
                SpringPreset.cancelForView(pv);
                pv.setTranslationX(targetX);
            }
        }
        applyWallpaperParallax(dragDeltaPx, width);
        updateDockAndPillForPage();
    }

    private void snapToVirtualPage(int newIdx, float velocityPx) {
        if (virtualPages.isEmpty()) return;
        currentVirtualIndex = Math.max(0, Math.min(virtualPages.size() - 1, newIdx));
        final int width = homePager.getWidth() > 0
                ? homePager.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        boolean rtl = homePager.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int dirSign = rtl ? -1 : 1;

        for (int i = 0; i < virtualPages.size(); i++) {
            View pv = virtualPages.get(i).view;
            if (pv == null) continue;
            float targetX = ((i - currentVirtualIndex) * width) * dirSign;
            SpringPreset.animate(pv, View.TRANSLATION_X, targetX, SpringPreset.PAGE_SNAP,
                    i == currentVirtualIndex ? velocityPx : 0f, null);
        }
        applyWallpaperParallax(0f, width);
        rebuildPageDots();
        updateDockAndPillForPage();
        updateEditModeTopBar();
    }

    private void applyWallpaperParallax(float dragDeltaPx, int pageWidth) {
        try {
            if (rootLayout.getWindowToken() == null || virtualPages.size() <= 1) return;
            float frac = currentVirtualIndex - (pageWidth > 0 ? (dragDeltaPx / pageWidth) : 0f);
            float norm = Math.max(0f, Math.min(1f, frac / Math.max(1, virtualPages.size() - 1)));
            // 0.5x parallax travel centered around 0.5
            float offset = 0.25f + (norm * 0.5f);
            WallpaperManager wm = WallpaperManager.getInstance(this);
            wm.setWallpaperOffsets(rootLayout.getWindowToken(), offset, 0.5f);
        } catch (Exception ignored) {}
    }

    private void updateDockAndPillForPage() {
        if (currentVirtualIndex < 0 || currentVirtualIndex >= virtualPages.size()) return;
        int type = virtualPages.get(currentVirtualIndex).type;
        boolean onHome = (type == PageSpec.TYPE_HOME);
        pageDock.setVisibility(onHome ? View.VISIBLE : View.GONE);
        pageDotsRow.setVisibility(virtualPages.size() > 1 ? View.VISIBLE : View.GONE);
        btnOpenSearch.setVisibility(onHome && showSearchPill ? View.VISIBLE : View.GONE);
    }

    private void rebuildPageDots() {
        pageDotsRow.removeAllViews();
        int dotSize = Math.round(MotionSpec.dp(this, 6f));
        int margin = Math.round(MotionSpec.dp(this, 4f));
        for (int i = 0; i < virtualPages.size(); i++) {
            final int targetIdx = i;
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_dot_blue);
            boolean active = (i == currentVirtualIndex);
            dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    active ? getColor(R.color.page_dot_active) : getColor(R.color.page_dot_inactive)));
            dot.setScaleX(active ? 1.25f : 0.9f);
            dot.setScaleY(active ? 1.25f : 0.9f);
            dot.setContentDescription(getString(R.string.page_indicator_desc, i + 1, virtualPages.size()));
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    snapToVirtualPage(targetIdx, 0f);
                }
            });
            pageDotsRow.addView(dot);
        }
    }

    private void setupPagerTouchHandling() {
        homePager.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                if (pinchDetector != null) pinchDetector.onTouchEvent(ev);
                if (isDraggingItem) {
                    handleDragMoveOrUp(ev);
                    return true;
                }
                if (isSpotlightOpen || isSettingsOpen || isFolderOpen || isContextMenuOpen) {
                    return false;
                }

                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        touchDownX = ev.getRawX();
                        touchDownY = ev.getRawY();
                        isPagerDragging = false;
                        isSpotlightPullDragging = false;
                        if (pagerVelocityTracker != null) pagerVelocityTracker.recycle();
                        pagerVelocityTracker = VelocityTracker.obtain();
                        pagerVelocityTracker.addMovement(ev);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (ev.getPointerCount() >= 2) return true;
                        if (pagerVelocityTracker != null) pagerVelocityTracker.addMovement(ev);
                        float dx = ev.getRawX() - touchDownX;
                        float dy = ev.getRawY() - touchDownY;

                        if (!isPagerDragging && !isSpotlightPullDragging) {
                            if (Math.abs(dx) > touchSlopPx && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                                isPagerDragging = true;
                            } else if (gestureSwipeSpotlight && dy > touchSlopPx * 1.5f
                                    && Math.abs(dy) > Math.abs(dx) * 1.2f
                                    && touchDownY > MotionSpec.dp(MainActivity.this, 52f)
                                    && getCurrentHomePageIndex() >= 0) {
                                isSpotlightPullDragging = true;
                                openSpotlight();
                                return true;
                            }
                        }

                        if (isPagerDragging) {
                            // Resistance at boundaries
                            float resisted = dx;
                            boolean rtl = homePager.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                            float logicalDx = rtl ? -dx : dx;
                            if ((currentVirtualIndex == 0 && logicalDx > 0)
                                    || (currentVirtualIndex == virtualPages.size() - 1 && logicalDx < 0)) {
                                resisted = dx * 0.28f;
                            }
                            positionPagesAtOffset(resisted, false);
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isPagerDragging) {
                            float dxUp = ev.getRawX() - touchDownX;
                            float vx = 0f;
                            if (pagerVelocityTracker != null) {
                                pagerVelocityTracker.computeCurrentVelocity(1000);
                                vx = pagerVelocityTracker.getXVelocity();
                            }
                            boolean rtl = homePager.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                            float logicalDx = rtl ? -dxUp : dxUp;
                            float logicalVx = rtl ? -vx : vx;
                            int w = Math.max(1, homePager.getWidth());
                            int target = currentVirtualIndex;
                            if (logicalDx < -w * 0.22f || logicalVx < -650f) {
                                target = Math.min(virtualPages.size() - 1, currentVirtualIndex + 1);
                            } else if (logicalDx > w * 0.22f || logicalVx > 650f) {
                                target = Math.max(0, currentVirtualIndex - 1);
                            }
                            snapToVirtualPage(target, vx);
                        } else if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                            float dxTap = ev.getRawX() - touchDownX;
                            float dyTap = ev.getRawY() - touchDownY;
                            if (Math.hypot(dxTap, dyTap) < touchSlopPx && getCurrentHomePageIndex() >= 0) {
                                long now = System.currentTimeMillis();
                                if (now - lastEmptyTapMs < 280L) {
                                    lastEmptyTapMs = 0L;
                                    if (gestureDoubleTapLock && !isEditMode) {
                                        handleDoubleTapLock();
                                    }
                                } else {
                                    lastEmptyTapMs = now;
                                }
                            }
                        }
                        if (pagerVelocityTracker != null) {
                            pagerVelocityTracker.recycle();
                            pagerVelocityTracker = null;
                        }
                        isPagerDragging = false;
                        isSpotlightPullDragging = false;
                        return true;
                }
                return false;
            }
        });
    }

    // ------------------------------------------------------------------------
    // Home Page, Dock, Badges, Shape & Drag-and-Drop
    // ------------------------------------------------------------------------

    private View buildHomePageView(final int homePageIndex) {
        View page = LayoutInflater.from(this).inflate(R.layout.page_home, homePager, false);
        FrameLayout homeWidgetSlot = page.findViewById(R.id.home_widget_slot);
        View promptCard = page.findViewById(R.id.home_prompt_card);
        View btnSetHome = page.findViewById(R.id.btn_set_home);
        View btnDismissPrompt = page.findViewById(R.id.btn_dismiss_home_prompt);
        GridLayout grid = page.findViewById(R.id.home_grid_layout);
        TextView emptyHint = page.findViewById(R.id.home_empty_hint);

        grid.setColumnCount(gridColumns);

        // Optional Home Widget on Home Page 0
        if (homePageIndex == 0 && layoutState.homeWidget != null) {
            homeWidgetSlot.setVisibility(View.VISIBLE);
            homeWidgetSlot.removeAllViews();
            homeWidgetSlot.addView(buildWidgetCardView(layoutState.homeWidget, true));
        } else {
            homeWidgetSlot.setVisibility(View.GONE);
        }

        // Default Home role prompt banner on Home Page 0
        if (homePageIndex == 0 && shouldShowDefaultHomePrompt()) {
            promptCard.setVisibility(View.VISIBLE);
            btnSetHome.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { requestDefaultHomeRole(); }
            });
            btnDismissPrompt.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs.edit().putBoolean(PREF_HOME_DISMISSED, true).apply();
                    v.setVisibility(View.GONE);
                    rebuildVirtualPager(currentVirtualIndex);
                }
            });
        }

        List<IosLayoutState.HomeSlot> slots = homePageIndex < layoutState.pages.size()
                ? layoutState.pages.get(homePageIndex) : Collections.<IosLayoutState.HomeSlot>emptyList();

        emptyHint.setVisibility(slots.isEmpty() ? View.VISIBLE : View.GONE);
        if (isEditMode && slots.isEmpty()) {
            emptyHint.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openSettingsPage(); }
            });
        }

        for (int i = 0; i < slots.size(); i++) {
            final IosLayoutState.HomeSlot slot = slots.get(i);
            final int slotPos = i;
            View cell = createHomeSlotCellView(slot, homePageIndex, slotPos, false);

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = getResources().getDimensionPixelSize(R.dimen.ios_cell_height);
            glp.columnSpec = GridLayout.spec(i % gridColumns, 1f);
            glp.rowSpec = GridLayout.spec(i / gridColumns);
            cell.setLayoutParams(glp);
            grid.addView(cell);
        }

        // Long-press empty home background enters Edit Mode
        page.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!isEditMode) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    setEditMode(true);
                    return true;
                }
                return false;
            }
        });

        return page;
    }

    private void rebuildDock() {
        dockRow.removeAllViews();
        int max = Math.min(dockCapacity, layoutState.dock.size());
        for (int i = 0; i < max; i++) {
            String pkg = layoutState.dock.get(i);
            IosLayoutState.HomeSlot slot = IosLayoutState.HomeSlot.app(pkg);
            View cell = createHomeSlotCellView(slot, -1, i, true);
            View labelRow = cell.findViewById(R.id.label_row);
            if (labelRow != null) labelRow.setVisibility(View.GONE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            cell.setLayoutParams(lp);
            dockRow.addView(cell);
        }
    }

    private View createHomeSlotCellView(
            final IosLayoutState.HomeSlot slot,
            final int pageIdx,
            final int slotPos,
            final boolean isDock) {
        final View cell = LayoutInflater.from(this).inflate(R.layout.item_home_cell, null, false);
        ImageView iconView = cell.findViewById(R.id.app_icon);
        GridLayout folderPreview = cell.findViewById(R.id.folder_preview_grid);
        ImageView badgeMinus = cell.findViewById(R.id.badge_minus);
        TextView badgeNotif = cell.findViewById(R.id.badge_notif_count);
        ImageView badgeWork = cell.findViewById(R.id.badge_work_profile);
        View badgeNewDot = cell.findViewById(R.id.badge_new_dot);
        TextView labelView = cell.findViewById(R.id.app_label);
        View labelRow = cell.findViewById(R.id.label_row);

        applyIconShapeClip(iconView);
        if (!showLabels && !isDock) {
            labelRow.setVisibility(View.GONE);
        }

        if (slot.isFolder()) {
            iconView.setVisibility(View.GONE);
            folderPreview.setVisibility(View.VISIBLE);
            labelView.setText(slot.folderTitle != null ? slot.folderTitle : getString(R.string.folder_default_name));
            populateFolderPreviewGrid(folderPreview, slot.apps);

            // Sum notification badges across apps inside folder
            int folderNotifs = 0;
            for (String fPkg : slot.apps) {
                folderNotifs += KayumarsNotificationListener.getBadgeCount(
                        fPkg, notifBadgesEnabled, layoutState.mutedBadgePkgs);
            }
            if (folderNotifs > 0 && !isEditMode) {
                badgeNotif.setVisibility(View.VISIBLE);
                badgeNotif.setText(folderNotifs > 99 ? "99+" : String.valueOf(folderNotifs));
            }
        } else {
            iconView.setVisibility(View.VISIBLE);
            folderPreview.setVisibility(View.GONE);
            AppIndexer.AppModel app = appByPkg.get(slot.pkg);
            String label = app != null ? app.label : slot.pkg;
            labelView.setText(label);
            loadAppIconAsync(slot.pkg, iconView);

            int notifCount = KayumarsNotificationListener.getBadgeCount(
                    slot.pkg, notifBadgesEnabled, layoutState.mutedBadgePkgs);
            if (notifCount > 0 && !isEditMode) {
                badgeNotif.setVisibility(View.VISIBLE);
                badgeNotif.setText(notifCount > 99 ? "99+" : String.valueOf(notifCount));
            }
            if (workProfilePackages.contains(slot.pkg)) {
                badgeWork.setVisibility(View.VISIBLE);
            }
            if (newDotEnabled && layoutState.newBadgePkgs.contains(slot.pkg)) {
                badgeNewDot.setVisibility(View.VISIBLE);
            }
        }

        // Edit / Jiggle Mode badge & animation
        if (isEditMode) {
            badgeMinus.setVisibility(View.VISIBLE);
            badgeNotif.setVisibility(View.GONE);
            startCellJiggle(cell, slotPos + (isDock ? 20 : pageIdx * 7));
            badgeMinus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (slot.isFolder()) {
                        if (pageIdx >= 0 && pageIdx < layoutState.pages.size()) {
                            layoutState.pages.get(pageIdx).remove(slot);
                        }
                        if (layoutState.pages.isEmpty()) layoutState.pages.add(new ArrayList<IosLayoutState.HomeSlot>());
                    } else if (isDock) {
                        layoutState.dock.remove(slot.pkg);
                    } else {
                        layoutState.removeAppFromHome(slot.pkg);
                    }
                    layoutState.save(prefs);
                    rebuildVirtualPager(currentVirtualIndex);
                }
            });
        }

        // Tap & Long-Press/Drag interactions
        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isEditMode) {
                    if (slot.isFolder()) openFolderModal(slot.folderId, cell);
                    return;
                }
                if (slot.isFolder()) {
                    openFolderModal(slot.folderId, cell);
                } else {
                    layoutState.newBadgePkgs.remove(slot.pkg);
                    layoutState.save(prefs);
                    launchAppWithFeedback(v, slot.pkg, false);
                }
            }
        });

        setupCellTouchDragAndMenu(cell, slot, pageIdx, slotPos, isDock);
        return cell;
    }

    private void populateFolderPreviewGrid(GridLayout grid, List<String> pkgs) {
        grid.removeAllViews();
        int miniPx = Math.round(MotionSpec.dp(this, 14f));
        int marginPx = Math.round(MotionSpec.dp(this, 1f));
        int count = Math.min(9, pkgs.size());
        for (int i = 0; i < count; i++) {
            ImageView iv = new ImageView(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = miniPx;
            lp.height = miniPx;
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            loadAppIconAsync(pkgs.get(i), iv);
            grid.addView(iv);
        }
    }

    private void setupCellTouchDragAndMenu(
            final View cell,
            final IosLayoutState.HomeSlot slot,
            final int pageIdx,
            final int slotPos,
            final boolean isDock) {
        final Runnable[] lpRunner = new Runnable[1];
        final boolean[] movedPastSlop = new boolean[1];
        final boolean[] longTriggered = new boolean[1];
        final float[] downXY = new float[2];

        cell.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downXY[0] = ev.getRawX();
                        downXY[1] = ev.getRawY();
                        movedPastSlop[0] = false;
                        longTriggered[0] = false;
                        if (lpRunner[0] != null) uiHandler.removeCallbacks(lpRunner[0]);
                        lpRunner[0] = new Runnable() {
                            @Override
                            public void run() {
                                if (movedPastSlop[0]) return;
                                longTriggered[0] = true;
                                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                if (isEditMode) {
                                    beginCellDrag(v, slot, pageIdx, slotPos, isDock, downXY[0], downXY[1]);
                                } else if (!slot.isFolder()) {
                                    AppIndexer.AppModel model = appByPkg.get(slot.pkg);
                                    if (model != null) {
                                        showAppContextMenu(v, model, true, isDock, null);
                                    }
                                } else {
                                    // Long-press folder opens Edit Mode & allows drag
                                    setEditMode(true);
                                }
                            }
                        };
                        uiHandler.postDelayed(lpRunner[0], 350L);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (isDraggingItem) {
                            handleDragMoveOrUp(ev);
                            return true;
                        }
                        if (Math.hypot(ev.getRawX() - downXY[0], ev.getRawY() - downXY[1]) > touchSlopPx) {
                            movedPastSlop[0] = true;
                            if (lpRunner[0] != null) uiHandler.removeCallbacks(lpRunner[0]);
                            if (longTriggered[0] && !isEditMode) {
                                // Long-press then immediate drag transitions from context menu into drag
                                dismissIosContextMenu();
                                beginCellDrag(v, slot, pageIdx, slotPos, isDock, ev.getRawX(), ev.getRawY());
                                handleDragMoveOrUp(ev);
                                return true;
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (lpRunner[0] != null) uiHandler.removeCallbacks(lpRunner[0]);
                        if (isDraggingItem) {
                            handleDragMoveOrUp(ev);
                            return true;
                        }
                        if (longTriggered[0]) return true;
                        break;
                }
                return false;
            }
        });
    }

    private void beginCellDrag(
            View originCell,
            IosLayoutState.HomeSlot slot,
            int pageIdx,
            int slotPos,
            boolean isDock,
            float rawX,
            float rawY) {
        isDraggingItem = true;
        dragFromDock = isDock;
        dragSourcePageIdx = pageIdx;
        dragSourceSlotPos = slotPos;
        dragIsFolder = slot.isFolder();
        dragAppPkg = slot.isFolder() ? null : slot.pkg;
        dragHoverSlotPos = -1;
        dragHoverDock = false;

        if (!isEditMode) {
            setEditMode(true);
        }

        if (slot.isFolder() && !slot.apps.isEmpty()) {
            loadAppIconAsync(slot.apps.get(0), dragSnapshotIcon);
        } else if (slot.pkg != null) {
            loadAppIconAsync(slot.pkg, dragSnapshotIcon);
        }

        dragSnapshotView.setVisibility(View.VISIBLE);
        dragSnapshotView.setScaleX(1.12f);
        dragSnapshotView.setScaleY(1.12f);
        positionDragSnapshot(rawX, rawY);
    }

    private void positionDragSnapshot(float rawX, float rawY) {
        int[] loc = new int[2];
        rootLayout.getLocationOnScreen(loc);
        float x = rawX - loc[0] - dragSnapshotView.getWidth() * 0.5f;
        float y = rawY - loc[1] - dragSnapshotView.getHeight() * 0.5f;
        dragSnapshotView.setTranslationX(x);
        dragSnapshotView.setTranslationY(y);
    }

    private void handleDragMoveOrUp(MotionEvent ev) {
        if (!isDraggingItem) return;
        float rawX = ev.getRawX();
        float rawY = ev.getRawY();

        if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            positionDragSnapshot(rawX, rawY);
            evaluateDragTargets(rawX, rawY);
            return;
        }

        // ACTION_UP / ACTION_CANCEL -> Commit drop
        uiHandler.removeCallbacks(folderMergeTrigger);
        uiHandler.removeCallbacks(edgePageSwitchRunnable);
        dragDropRing.setVisibility(View.GONE);
        dragSnapshotView.setVisibility(View.GONE);

        if (layoutState.pages.isEmpty()) {
            layoutState.pages.add(new ArrayList<IosLayoutState.HomeSlot>());
        }
        int currentHomeIdx = Math.max(0, Math.min(layoutState.pages.size() - 1, getCurrentHomePageIndex()));
        if (dragHoverDock && !dragIsFolder && dragAppPkg != null) {
            if (!dragFromDock) {
                layoutState.removeAppFromHome(dragAppPkg);
                layoutState.addToDock(dragAppPkg, dockCapacity);
            }
        } else if (dragHoverSlotPos >= 0) {
            if (dragFromDock && dragAppPkg != null) {
                layoutState.dock.remove(dragAppPkg);
                List<IosLayoutState.HomeSlot> targetPage = layoutState.pages.get(currentHomeIdx);
                targetPage.add(
                        Math.min(dragHoverSlotPos, targetPage.size()),
                        IosLayoutState.HomeSlot.app(dragAppPkg));
            } else if (!dragIsFolder && dragSourcePageIdx == currentHomeIdx
                    && dragHoverSlotPos != dragSourceSlotPos
                    && isHoverDirectlyOverIcon(currentHomeIdx, dragHoverSlotPos, rawX, rawY)) {
                layoutState.mergeSlotsIntoFolder(
                        currentHomeIdx, dragSourceSlotPos, dragHoverSlotPos,
                        getString(R.string.folder_default_name));
            } else if (dragSourcePageIdx == currentHomeIdx) {
                layoutState.reorderSlot(currentHomeIdx, dragSourceSlotPos, dragHoverSlotPos);
            } else if (dragSourcePageIdx >= 0 && dragSourcePageIdx < layoutState.pages.size()) {
                layoutState.moveSlotToPage(dragSourcePageIdx, dragSourceSlotPos, currentHomeIdx, getPageCapacity());
            }
        } else if (dragFromDock && dragAppPkg != null) {
            // Dragged out of dock onto Home page
            layoutState.dock.remove(dragAppPkg);
            layoutState.pages.get(currentHomeIdx).add(IosLayoutState.HomeSlot.app(dragAppPkg));
        }

        layoutState.save(prefs);
        isDraggingItem = false;
        rebuildVirtualPager(currentVirtualIndex);
    }

    private void evaluateDragTargets(float rawX, float rawY) {
        int[] dockLoc = new int[2];
        pageDock.getLocationOnScreen(dockLoc);
        boolean overDock = rawY >= dockLoc[1] - MotionSpec.dp(this, 12f)
                && rawY <= dockLoc[1] + pageDock.getHeight();
        dragHoverDock = overDock;

        if (overDock) {
            uiHandler.removeCallbacks(folderMergeTrigger);
            dragDropRing.setVisibility(View.GONE);
            dragHoverSlotPos = -1;
            return;
        }

        // Screen edge page switch after 500ms
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int edgeMargin = Math.round(MotionSpec.dp(this, 28f));
        int newEdge = 0;
        if (rawX < edgeMargin) newEdge = -1;
        else if (rawX > screenW - edgeMargin) newEdge = 1;
        if (newEdge != edgeSwitchDir) {
            edgeSwitchDir = newEdge;
            uiHandler.removeCallbacks(edgePageSwitchRunnable);
            if (edgeSwitchDir != 0) {
                uiHandler.postDelayed(edgePageSwitchRunnable, 500L);
            }
        }

        int homeIdx = getCurrentHomePageIndex();
        if (homeIdx < 0) return;
        GridLayout grid = getHomeGridView(currentVirtualIndex);
        if (grid == null) return;

        int foundPos = -1;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            int[] cl = new int[2];
            child.getLocationOnScreen(cl);
            if (rawX >= cl[0] && rawX <= cl[0] + child.getWidth()
                    && rawY >= cl[1] && rawY <= cl[1] + child.getHeight()) {
                foundPos = i;
                break;
            }
        }

        if (foundPos != dragHoverSlotPos) {
            dragHoverSlotPos = foundPos;
            uiHandler.removeCallbacks(folderMergeTrigger);
            dragDropRing.setVisibility(View.GONE);
            if (!dragIsFolder && foundPos >= 0 && foundPos != dragSourceSlotPos) {
                uiHandler.postDelayed(folderMergeTrigger, 150L);
            }
        }
    }

    private boolean isHoverDirectlyOverIcon(int pageIdx, int slotPos, float rawX, float rawY) {
        GridLayout grid = getHomeGridView(homePageIndexToVirtual(pageIdx));
        if (grid == null || slotPos < 0 || slotPos >= grid.getChildCount()) return false;
        View child = grid.getChildAt(slotPos);
        int[] cl = new int[2];
        child.getLocationOnScreen(cl);
        float cx = cl[0] + child.getWidth() * 0.5f;
        float cy = cl[1] + MotionSpec.dp(this, 34f);
        return Math.hypot(rawX - cx, rawY - cy) < MotionSpec.dp(this, 26f);
    }

    private void showMergeRingAtHomeSlot(int pageIdx, int slotPos) {
        GridLayout grid = getHomeGridView(homePageIndexToVirtual(pageIdx));
        if (grid == null || slotPos < 0 || slotPos >= grid.getChildCount()) return;
        View child = grid.getChildAt(slotPos);
        View iconFrame = child.findViewById(R.id.icon_frame);
        if (iconFrame == null) return;
        int[] il = new int[2];
        int[] rl = new int[2];
        iconFrame.getLocationOnScreen(il);
        rootLayout.getLocationOnScreen(rl);
        dragDropRing.setVisibility(View.VISIBLE);
        dragDropRing.setTranslationX(il[0] - rl[0] - MotionSpec.dp(this, 4f));
        dragDropRing.setTranslationY(il[1] - rl[1] - MotionSpec.dp(this, 4f));
    }

    private GridLayout getHomeGridView(int virtualIdx) {
        if (virtualIdx < 0 || virtualIdx >= virtualPages.size()) return null;
        PageSpec spec = virtualPages.get(virtualIdx);
        if (spec.type != PageSpec.TYPE_HOME || spec.view == null) return null;
        return spec.view.findViewById(R.id.home_grid_layout);
    }

    // ------------------------------------------------------------------------
    // Edit / Jiggle Mode
    // ------------------------------------------------------------------------

    private void setEditMode(boolean enabled) {
        if (isEditMode == enabled) return;
        isEditMode = enabled;
        editModeBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) {
            cancelJiggleAnimations();
        }
        rebuildVirtualPager(currentVirtualIndex);
    }

    private void updateEditModeTopBar() {
        if (!isEditMode) {
            editModeBar.setVisibility(View.GONE);
            return;
        }
        editModeBar.setVisibility(View.VISIBLE);
        int homeIdx = getCurrentHomePageIndex();
        boolean showRemove = homeIdx >= 0 && homeIdx < layoutState.pages.size()
                && layoutState.pages.size() > 1
                && layoutState.pages.get(homeIdx).isEmpty();
        btnRemovePage.setVisibility(showRemove ? View.VISIBLE : View.GONE);
    }

    private void startCellJiggle(View cell, int index) {
        if (MotionSpec.isReducedMotion(this)) return;
        cell.setPivotX(cell.getWidth() > 0 ? cell.getWidth() * 0.5f : MotionSpec.dp(this, 36f));
        cell.setPivotY(cell.getHeight() > 0 ? cell.getHeight() * 0.5f : MotionSpec.dp(this, 40f));
        ObjectAnimator anim = ObjectAnimator.ofFloat(
                cell,
                View.ROTATION,
                -MotionSpec.JIGGLE_ANGLE_DEG,
                MotionSpec.JIGGLE_ANGLE_DEG);
        anim.setDuration(MotionSpec.JIGGLE_PERIOD_MS);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setStartDelay((index % 12) * MotionSpec.JIGGLE_STAGGER_MS);
        anim.start();
        jiggleAnimators.add(anim);
    }

    private void cancelJiggleAnimations() {
        for (ObjectAnimator anim : jiggleAnimators) {
            anim.cancel();
        }
        jiggleAnimators.clear();
    }

    // ------------------------------------------------------------------------
    // Folder Zoom-Morph Modal
    // ------------------------------------------------------------------------

    private void openFolderModal(String folderId, View anchorCell) {
        IosLayoutState.HomeSlot folder = layoutState.findFolderById(folderId);
        if (folder == null) return;
        isFolderOpen = true;
        openFolderId = folderId;
        openFolderPage = 0;

        folderTitleInput.setText(folder.folderTitle);
        refreshOpenFolderGrid();

        contextScrimOverlay.setVisibility(View.VISIBLE);
        contextScrimOverlay.setAlpha(0f);
        contextScrimOverlay.animate().alpha(1f).setDuration(MotionSpec.DURATION_FAST_MS).start();

        cardFolderOverlay.setVisibility(View.VISIBLE);
        folderModalCard.setScaleX(0.45f);
        folderModalCard.setScaleY(0.45f);
        folderModalCard.setAlpha(0f);
        SpringPreset.animateScale(folderModalCard, 1f, SpringPreset.FOLDER_SPRING, 0f, null);
        folderModalCard.animate().alpha(1f).setDuration(MotionSpec.DURATION_FAST_MS).start();
    }

    private void refreshOpenFolderGrid() {
        IosLayoutState.HomeSlot folder = layoutState.findFolderById(openFolderId);
        if (folder == null) {
            closeFolder();
            return;
        }
        folderVisibleApps.clear();
        int totalPages = Math.max(1, (folder.apps.size() + 8) / 9);
        if (openFolderPage >= totalPages) openFolderPage = totalPages - 1;
        int start = openFolderPage * 9;
        int end = Math.min(folder.apps.size(), start + 9);
        for (int i = start; i < end; i++) {
            AppIndexer.AppModel model = appByPkg.get(folder.apps.get(i));
            if (model != null) folderVisibleApps.add(model);
        }
        folderAdapter.notifyDataSetChanged();

        if (totalPages > 1) {
            folderPagerBar.setVisibility(View.VISIBLE);
            tvFolderPage.setText(getString(R.string.folder_page_indicator, openFolderPage + 1, totalPages));
        } else {
            folderPagerBar.setVisibility(View.GONE);
        }
    }

    private void saveOpenFolderTitle() {
        if (openFolderId == null) return;
        IosLayoutState.HomeSlot folder = layoutState.findFolderById(openFolderId);
        if (folder != null) {
            String nextTitle = folderTitleInput.getText().toString().trim();
            if (!nextTitle.isEmpty()) {
                folder.folderTitle = nextTitle;
                layoutState.save(prefs);
            }
        }
    }

    private void closeFolder() {
        if (!isFolderOpen) return;
        saveOpenFolderTitle();
        hideKeyboard(folderTitleInput);
        isFolderOpen = false;
        openFolderId = null;

        SpringPreset.animateScale(folderModalCard, 0.75f, SpringPreset.SNAP, 0f, new Runnable() {
            @Override
            public void run() {
                cardFolderOverlay.setVisibility(View.GONE);
            }
        });
        contextScrimOverlay.animate().alpha(0f).setDuration(MotionSpec.DURATION_INSTANT_MS)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (!isContextMenuOpen && !isOverviewOpen) {
                            contextScrimOverlay.setVisibility(View.GONE);
                        }
                    }
                }).start();
        rebuildVirtualPager(currentVirtualIndex);
    }

    // ------------------------------------------------------------------------
    // Long-Press Blurred Context Menu (240dp frosted + LauncherApps Quick Actions)
    // ------------------------------------------------------------------------

    private void showAppContextMenu(
            final View anchorView,
            final AppIndexer.AppModel app,
            final boolean onHome,
            final boolean onDock,
            final String insideFolderId) {
        isContextMenuOpen = true;
        liftedContextAnchor = anchorView;
        if (anchorView != null) {
            SpringPreset.animateScale(anchorView, 1.06f, SpringPreset.MENU_SPRING, 0f, null);
            anchorView.setElevation(MotionSpec.dp(this, 6f));
        }

        contextScrimOverlay.setVisibility(View.VISIBLE);
        contextScrimOverlay.setAlpha(0f);
        contextScrimOverlay.animate().alpha(1f).setDuration(MotionSpec.DURATION_FAST_MS).start();

        menuAppTitle.setText(app.label);
        menuShortcutsContainer.removeAllViews();
        menuActionsContainer.removeAllViews();

        // Up to 4 dynamic LauncherApps shortcuts
        List<AppMetadataHelper.ShortcutEntry> shortcuts = AppMetadataHelper.queryShortcuts(this, app.pkg, 4);
        for (final AppMetadataHelper.ShortcutEntry sc : shortcuts) {
            addContextMenuRow(menuShortcutsContainer, sc.label, sc.icon, 0, false, new Runnable() {
                @Override public void run() {
                    dismissIosContextMenu();
                    if (AppMetadataHelper.launchShortcut(MainActivity.this, sc)) {
                        LaunchLogger.recordOnly(MainActivity.this, app.pkg);
                    } else {
                        launchAppWithFeedback(anchorView, app.pkg, false);
                    }
                }
            });
        }

        // Standard Actions
        addContextMenuRow(menuActionsContainer, getString(R.string.open_app), null, R.drawable.ic_glyph_open, false, new Runnable() {
            @Override public void run() {
                dismissIosContextMenu();
                launchAppWithFeedback(anchorView, app.pkg, false);
            }
        });

        addContextMenuRow(menuActionsContainer, getString(R.string.app_info), null, R.drawable.ic_glyph_info, false, new Runnable() {
            @Override public void run() {
                dismissIosContextMenu();
                openAppInfo(app.pkg);
            }
        });

        addContextMenuRow(menuActionsContainer, getString(R.string.share_app), null, R.drawable.ic_glyph_share, false, new Runnable() {
            @Override public void run() {
                dismissIosContextMenu();
                shareAppDetails(app);
            }
        });

        if (insideFolderId != null) {
            addContextMenuRow(menuActionsContainer, getString(R.string.remove_from_home), null, R.drawable.ic_glyph_minus, false, new Runnable() {
                @Override public void run() {
                    layoutState.removeFromFolder(insideFolderId, app.pkg);
                    layoutState.save(prefs);
                    dismissIosContextMenu();
                    refreshOpenFolderGrid();
                    rebuildVirtualPager(currentVirtualIndex);
                }
            });
        } else if (onHome) {
            addContextMenuRow(menuActionsContainer, getString(R.string.edit_home_screen), null, R.drawable.ic_glyph_edit, false, new Runnable() {
                @Override public void run() {
                    dismissIosContextMenu();
                    setEditMode(true);
                }
            });
            addContextMenuRow(menuActionsContainer, getString(R.string.remove_from_home), null, R.drawable.ic_glyph_minus, false, new Runnable() {
                @Override public void run() {
                    if (onDock) layoutState.dock.remove(app.pkg);
                    else layoutState.removeAppFromHome(app.pkg);
                    layoutState.save(prefs);
                    dismissIosContextMenu();
                    rebuildVirtualPager(currentVirtualIndex);
                }
            });
        } else {
            addContextMenuRow(menuActionsContainer, getString(R.string.add_to_home), null, R.drawable.ic_glyph_plus, false, new Runnable() {
                @Override public void run() {
                    int pg = layoutState.placeOnFirstFreePage(app.pkg, getPageCapacity(), false);
                    layoutState.save(prefs);
                    dismissIosContextMenu();
                    rebuildVirtualPager(homePageIndexToVirtual(pg));
                }
            });
        }

        // Destructive red Delete / Uninstall
        addContextMenuRow(menuActionsContainer, getString(R.string.uninstall_app), null, R.drawable.ic_glyph_delete, true, new Runnable() {
            @Override public void run() {
                dismissIosContextMenu();
                requestUninstallApp(app.pkg);
            }
        });

        // Anchor context menu cleanly within viewport bounds
        positionMenuNearAnchor(anchorView);

        menuContextOverlay.setVisibility(View.VISIBLE);
        menuContextCard.setScaleX(0.84f);
        menuContextCard.setScaleY(0.84f);
        menuContextCard.setAlpha(0f);
        SpringPreset.animateScale(menuContextCard, 1f, SpringPreset.MENU_SPRING, 0f, null);
        menuContextCard.animate().alpha(1f).setDuration(MotionSpec.DURATION_INSTANT_MS).start();
    }

    private void positionMenuNearAnchor(View anchorView) {
        ViewGroup.LayoutParams rawLp = menuContextCard.getLayoutParams();
        if (!(rawLp instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) rawLp;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int menuW = getResources().getDimensionPixelSize(R.dimen.ios_menu_width);
        int margin = Math.round(MotionSpec.dp(this, 16f));

        if (anchorView == null) {
            lp.leftMargin = (screenW - menuW) / 2;
            lp.topMargin = screenH / 3;
            menuContextCard.setLayoutParams(lp);
            return;
        }
        int[] loc = new int[2];
        anchorView.getLocationOnScreen(loc);
        int x = Math.max(margin, Math.min(screenW - menuW - margin, loc[0]));
        int estimatedMenuH = Math.round(MotionSpec.dp(this, 320f));
        int y = loc[1] + anchorView.getHeight() + Math.round(MotionSpec.dp(this, 8f));
        if (y + estimatedMenuH > screenH - margin) {
            y = Math.max(margin * 2, loc[1] - estimatedMenuH);
        }
        lp.leftMargin = x;
        lp.topMargin = y;
        menuContextCard.setLayoutParams(lp);
    }

    private void addContextMenuRow(
            LinearLayout parent,
            String title,
            Drawable customIcon,
            int glyphRes,
            boolean destructive,
            final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.ios_menu_row_height));
        int padH = Math.round(MotionSpec.dp(this, 16f));
        row.setPadding(padH, 6, padH, 6);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
        tv.setTextColor(destructive ? getColor(R.color.system_red) : getColor(R.color.label_primary));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(tv, tlp);

        if (customIcon != null || glyphRes != 0) {
            ImageView iv = new ImageView(this);
            int sz = Math.round(MotionSpec.dp(this, 18f));
            iv.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
            if (customIcon != null) {
                iv.setImageDrawable(customIcon);
            } else {
                iv.setImageResource(glyphRes);
                iv.setColorFilter(destructive ? getColor(R.color.system_red) : getColor(R.color.label_primary));
            }
            row.addView(iv);
        }

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        parent.addView(row);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, getResources().getDimensionPixelSize(R.dimen.ios_hairline))));
        divider.setBackgroundColor(getColor(R.color.separator));
        parent.addView(divider);
    }

    private void dismissIosContextMenu() {
        if (!isContextMenuOpen) return;
        isContextMenuOpen = false;
        if (liftedContextAnchor != null) {
            SpringPreset.animateScale(liftedContextAnchor, 1f, SpringPreset.SNAP, 0f, null);
            liftedContextAnchor.setElevation(0f);
            liftedContextAnchor = null;
        }
        menuContextOverlay.setVisibility(View.GONE);
        if (!isFolderOpen && !isOverviewOpen) {
            contextScrimOverlay.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------------
    // Spotlight Search Overlay (Rust Core.search & Core.topApps)
    // ------------------------------------------------------------------------

    private void openSpotlight() {
        if (isSpotlightOpen) return;
        isSpotlightOpen = true;
        spotlightInput.setText("");
        overlaySpotlight.setVisibility(View.VISIBLE);
        overlaySpotlight.setAlpha(0f);
        spotlightSheet.setTranslationY(-MotionSpec.dp(this, 36f));

        overlaySpotlight.animate().alpha(1f).setDuration(MotionSpec.DURATION_FAST_MS).start();
        SpringPreset.animate(spotlightSheet, View.TRANSLATION_Y, 0f, SpringPreset.SPOTLIGHT_GLIDE, 0f, null);

        runSpotlightSearch("");
        spotlightInput.requestFocus();
        spotlightInput.postDelayed(new Runnable() {
            @Override public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(spotlightInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 80L);
    }

    private void closeSpotlight() {
        if (!isSpotlightOpen) return;
        isSpotlightOpen = false;
        hideKeyboard(spotlightInput);
        overlaySpotlight.animate().alpha(0f).setDuration(MotionSpec.DURATION_FAST_MS)
                .withEndAction(new Runnable() {
                    @Override public void run() { overlaySpotlight.setVisibility(View.GONE); }
                }).start();
    }

    private void runSpotlightSearch(String query) {
        String trimmed = query == null ? "" : query.trim();
        spotlightRows.clear();
        spotlightGridApps.clear();
        spotlightSuggestionApps.clear();

        if (trimmed.isEmpty()) {
            spotlightAppsGridBlock.setVisibility(View.GONE);
            spotlightEmptyState.setVisibility(View.GONE);

            if (spotlightSuggestionsEnabled) {
                List<AppIndexer.AppModel> tops = fetchTopAppsFromRustOrFallback(8);
                spotlightSuggestionApps.addAll(tops);
                spotlightSuggestionsAdapter.notifyDataSetChanged();
                spotlightSuggestionsBlock.setVisibility(tops.isEmpty() ? View.GONE : View.VISIBLE);
            } else {
                spotlightSuggestionsBlock.setVisibility(View.GONE);
            }

            if (spotlightSettingsEnabled) {
                spotlightRows.add(new SpotlightRow(ROW_TYPE_HEADER, getString(R.string.spotlight_settings), null, null));
                for (int i = 0; i < Math.min(4, systemSettings.size()); i++) {
                    spotlightRows.add(new SpotlightRow(ROW_TYPE_SETTING, null, null, systemSettings.get(i)));
                }
            }
            spotlightResultsAdapter.notifyDataSetChanged();
            return;
        }

        spotlightSuggestionsBlock.setVisibility(View.GONE);

        List<AppIndexer.AppModel> matchedApps = queryAppsThroughCore(trimmed, 24);
        List<SettingItem> matchedSettings = new ArrayList<SettingItem>();
        if (spotlightSettingsEnabled) {
            String lowQ = trimmed.toLowerCase(Locale.ROOT);
            for (SettingItem si : systemSettings) {
                if (si.title.toLowerCase(Locale.ROOT).contains(lowQ)
                        || si.subtitle.toLowerCase(Locale.ROOT).contains(lowQ)) {
                    matchedSettings.add(si);
                }
            }
        }

        if (matchedApps.isEmpty() && matchedSettings.isEmpty()) {
            spotlightAppsGridBlock.setVisibility(View.GONE);
            spotlightResultsList.setVisibility(View.GONE);
            spotlightEmptyState.setVisibility(View.VISIBLE);
            tvSpotlightEmpty.setText(getString(R.string.no_results, trimmed));
            return;
        }

        spotlightEmptyState.setVisibility(View.GONE);
        spotlightResultsList.setVisibility(View.VISIBLE);

        // Top Hit (72dp row)
        if (!matchedApps.isEmpty()) {
            spotlightRows.add(new SpotlightRow(ROW_TYPE_HEADER, getString(R.string.spotlight_top_hit), null, null));
            spotlightRows.add(new SpotlightRow(ROW_TYPE_TOP_HIT, null, matchedApps.get(0), null));
        }

        // Remaining Applications: 4-col grid when > 6 matches, else 56dp rows
        if (matchedApps.size() > 6) {
            for (int i = 1; i < Math.min(matchedApps.size(), 9); i++) {
                spotlightGridApps.add(matchedApps.get(i));
            }
            spotlightGridAdapter.notifyDataSetChanged();
            spotlightAppsGridBlock.setVisibility(View.VISIBLE);
        } else {
            spotlightAppsGridBlock.setVisibility(View.GONE);
            if (matchedApps.size() > 1) {
                spotlightRows.add(new SpotlightRow(ROW_TYPE_HEADER, getString(R.string.spotlight_applications), null, null));
                for (int i = 1; i < matchedApps.size(); i++) {
                    spotlightRows.add(new SpotlightRow(ROW_TYPE_APP, null, matchedApps.get(i), null));
                }
            }
        }

        if (!matchedSettings.isEmpty()) {
            spotlightRows.add(new SpotlightRow(ROW_TYPE_HEADER, getString(R.string.spotlight_settings), null, null));
            for (SettingItem si : matchedSettings) {
                spotlightRows.add(new SpotlightRow(ROW_TYPE_SETTING, null, null, si));
            }
        }
        spotlightResultsAdapter.notifyDataSetChanged();
    }

    private List<AppIndexer.AppModel> fetchTopAppsFromRustOrFallback(int maxCount) {
        List<AppIndexer.AppModel> out = new ArrayList<AppIndexer.AppModel>();
        String pipe = Core.topApps(maxCount);
        if (pipe != null && !pipe.isEmpty()) {
            String[] lines = pipe.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 1) {
                    AppIndexer.AppModel model = appByPkg.get(parts[0].trim());
                    if (model != null && !layoutState.hiddenLibraryPkgs.contains(model.pkg)) {
                        out.add(model);
                    }
                }
                if (out.size() >= maxCount) break;
            }
        }
        if (out.isEmpty()) {
            SharedPreferences hist = getSharedPreferences(LaunchLogger.PREFS_HISTORY, MODE_PRIVATE);
            List<AppIndexer.AppModel> sorted = new ArrayList<AppIndexer.AppModel>(allApps);
            Collections.sort(sorted, new Comparator<AppIndexer.AppModel>() {
                @Override
                public int compare(AppIndexer.AppModel a, AppIndexer.AppModel b) {
                    int ca = hist.getInt("cnt_" + a.pkg, 0);
                    int cb = hist.getInt("cnt_" + b.pkg, 0);
                    if (ca != cb) return Integer.compare(cb, ca);
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
            for (AppIndexer.AppModel a : sorted) {
                if (!layoutState.hiddenLibraryPkgs.contains(a.pkg)) {
                    out.add(a);
                    if (out.size() >= maxCount) break;
                }
            }
        }
        return out;
    }

    private List<AppIndexer.AppModel> queryAppsThroughCore(String query, int maxCount) {
        List<AppIndexer.AppModel> out = new ArrayList<AppIndexer.AppModel>();
        String pipe = Core.search(query, maxCount);
        if (pipe != null && !pipe.isEmpty()) {
            String[] lines = pipe.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 1) {
                    AppIndexer.AppModel model = appByPkg.get(parts[0].trim());
                    if (model != null && !layoutState.hiddenLibraryPkgs.contains(model.pkg)) {
                        out.add(model);
                    }
                }
                if (out.size() >= maxCount) break;
            }
        }
        if (out.isEmpty()) {
            String lowQ = query.toLowerCase(Locale.ROOT);
            List<AppIndexer.AppModel> starts = new ArrayList<AppIndexer.AppModel>();
            List<AppIndexer.AppModel> contains = new ArrayList<AppIndexer.AppModel>();
            for (AppIndexer.AppModel a : allApps) {
                if (layoutState.hiddenLibraryPkgs.contains(a.pkg)) continue;
                String lowL = a.label.toLowerCase(Locale.ROOT);
                String lowP = a.pkg.toLowerCase(Locale.ROOT);
                if (lowL.startsWith(lowQ)) starts.add(a);
                else if (lowL.contains(lowQ) || lowP.contains(lowQ)) contains.add(a);
            }
            out.addAll(starts);
            out.addAll(contains);
            if (out.size() > maxCount) {
                return out.subList(0, maxCount);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------
    // App Library Page (2-Column Category Tiles + Expanded Category Grid)
    // ------------------------------------------------------------------------

    private View buildLibraryPageView() {
        View page = LayoutInflater.from(this).inflate(R.layout.page_library, homePager, false);
        final EditText libSearch = page.findViewById(R.id.library_search_input);
        final View libClear = page.findViewById(R.id.library_search_clear);
        final View categoriesScroll = page.findViewById(R.id.library_categories_scroll);
        final GridLayout categoriesGrid = page.findViewById(R.id.library_categories_grid);
        final View expandedContainer = page.findViewById(R.id.library_expanded_container);
        final TextView expandedTitle = page.findViewById(R.id.library_expanded_title);
        final View btnBack = page.findViewById(R.id.btn_library_back);
        final GridView expandedGrid = page.findViewById(R.id.library_expanded_grid);

        final SimpleCellAdapter libExpandedAdapter = new SimpleCellAdapter(expandedLibraryApps, true);
        expandedGrid.setAdapter(libExpandedAdapter);
        expandedGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                if (pos < expandedLibraryApps.size()) {
                    launchAppWithFeedback(view, expandedLibraryApps.get(pos).pkg, false);
                }
            }
        });
        expandedGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
                if (pos < expandedLibraryApps.size()) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showAppContextMenu(view, expandedLibraryApps.get(pos), false, false, null);
                    return true;
                }
                return false;
            }
        });

        // Bucket apps into categories
        final Map<String, List<AppIndexer.AppModel>> buckets = new HashMap<String, List<AppIndexer.AppModel>>();
        for (AppMetadataHelper.LibraryCategory c : AppMetadataHelper.LibraryCategory.values()) {
            buckets.put(c.key, new ArrayList<AppIndexer.AppModel>());
        }
        buckets.get("SUGGESTIONS").addAll(fetchTopAppsFromRustOrFallback(8));
        for (AppIndexer.AppModel a : allApps) {
            if (layoutState.hiddenLibraryPkgs.contains(a.pkg)) continue;
            AppMetadataHelper.LibraryCategory cat = AppMetadataHelper.classifyApp(a);
            buckets.get(cat.key).add(a);
        }

        layoutState.ensureDefaultCategoryOrder();
        categoriesGrid.removeAllViews();
        int tileIndex = 0;
        for (final String catKey : layoutState.libraryCategoryOrder) {
            final AppMetadataHelper.LibraryCategory cat = AppMetadataHelper.LibraryCategory.fromKey(catKey);
            final List<AppIndexer.AppModel> catApps = buckets.get(catKey);
            if (catApps == null || catApps.isEmpty()) continue;

            View tile = LayoutInflater.from(this).inflate(R.layout.item_library_tile, categoriesGrid, false);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            glp.columnSpec = GridLayout.spec(tileIndex % 2, 1f);
            glp.rowSpec = GridLayout.spec(tileIndex / 2);
            tile.setLayoutParams(glp);
            tileIndex++;

            View card = tile.findViewById(R.id.lib_tile_card);
            TextView lbl = tile.findViewById(R.id.lib_tile_label);
            lbl.setText(getString(cat.titleRes));

            int[] largeIds = {R.id.lib_icon_0, R.id.lib_icon_1, R.id.lib_icon_2};
            int[] miniIds = {R.id.lib_mini_0, R.id.lib_mini_1, R.id.lib_mini_2, R.id.lib_mini_3};

            for (int j = 0; j < largeIds.length; j++) {
                ImageView iv = tile.findViewById(largeIds[j]);
                applyIconShapeClip(iv);
                if (j < catApps.size()) {
                    loadAppIconAsync(catApps.get(j).pkg, iv);
                    final AppIndexer.AppModel targetApp = catApps.get(j);
                    iv.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            launchAppWithFeedback(v, targetApp.pkg, false);
                        }
                    });
                } else {
                    iv.setVisibility(View.INVISIBLE);
                }
            }
            for (int j = 0; j < miniIds.length; j++) {
                ImageView mv = tile.findViewById(miniIds[j]);
                applyIconShapeClip(mv);
                int idx = 3 + j;
                if (idx < catApps.size()) {
                    loadAppIconAsync(catApps.get(idx).pkg, mv);
                } else {
                    mv.setVisibility(View.INVISIBLE);
                }
            }

            tile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    expandedLibraryCategory = catKey;
                    expandedLibraryApps.clear();
                    expandedLibraryApps.addAll(catApps);
                    libExpandedAdapter.notifyDataSetChanged();
                    expandedTitle.setText(getString(cat.titleRes));
                    categoriesScroll.setVisibility(View.GONE);
                    expandedContainer.setVisibility(View.VISIBLE);
                }
            });
            categoriesGrid.addView(tile);
        }

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                expandedLibraryCategory = null;
                expandedContainer.setVisibility(View.GONE);
                categoriesScroll.setVisibility(View.VISIBLE);
                libSearch.setText("");
            }
        });

        libClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { libSearch.setText(""); }
        });

        libSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString().trim() : "";
                libClear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                if (q.isEmpty()) {
                    expandedLibraryCategory = null;
                    expandedContainer.setVisibility(View.GONE);
                    categoriesScroll.setVisibility(View.VISIBLE);
                } else {
                    expandedLibraryCategory = "SEARCH";
                    expandedLibraryApps.clear();
                    expandedLibraryApps.addAll(queryAppsThroughCore(q, 48));
                    libExpandedAdapter.notifyDataSetChanged();
                    expandedTitle.setText(getString(R.string.app_library_title));
                    categoriesScroll.setVisibility(View.GONE);
                    expandedContainer.setVisibility(View.VISIBLE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        return page;
    }

    // ------------------------------------------------------------------------
    // Today View (Leftmost Page) & Widget Picker / AppWidgetHost
    // ------------------------------------------------------------------------

    private View buildTodayPageView() {
        View page = LayoutInflater.from(this).inflate(R.layout.page_today, homePager, false);
        TextView headline = page.findViewById(R.id.today_date_headline);
        View btnAdd = page.findViewById(R.id.btn_today_add_widget);
        LinearLayout container = page.findViewById(R.id.today_widgets_container);
        View emptyHint = page.findViewById(R.id.today_empty_hint);

        SimpleDateFormat df = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
        headline.setText(df.format(new Date()));

        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWidgetPicker("today");
            }
        });

        container.removeAllViews();
        emptyHint.setVisibility(layoutState.todayWidgets.isEmpty() ? View.VISIBLE : View.GONE);
        int gutter = Math.round(MotionSpec.dp(this, 8f));
        for (int i = 0; i < layoutState.todayWidgets.size(); i++) {
            final IosLayoutState.WidgetSlot w = layoutState.todayWidgets.get(i);
            View card = buildWidgetCardView(w, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    resolveWidgetHeightPx(w.span));
            lp.bottomMargin = gutter;
            card.setLayoutParams(lp);
            container.addView(card);
        }
        return page;
    }

    private int resolveWidgetHeightPx(String span) {
        if (IosLayoutState.SPAN_SMALL.equals(span)) return Math.round(MotionSpec.dp(this, 96f));
        if (IosLayoutState.SPAN_LARGE.equals(span)) return Math.round(MotionSpec.dp(this, 210f));
        return Math.round(MotionSpec.dp(this, 146f));
    }

    private View buildWidgetCardView(final IosLayoutState.WidgetSlot w, final boolean isHomeSlot) {
        FrameLayout card = new FrameLayout(this);
        card.setBackgroundResource(R.drawable.bg_library_tile);
        int pad = Math.round(MotionSpec.dp(this, 14f));
        card.setPadding(pad, pad, pad, pad);

        if (IosLayoutState.WIDGET_APPWIDGET.equals(w.kind) && w.appWidgetId > 0 && appWidgetManager != null) {
            try {
                AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(w.appWidgetId);
                if (info != null && appWidgetHost != null) {
                    AppWidgetHostView hostView = appWidgetHost.createView(this, w.appWidgetId, info);
                    if (hostView != null) {
                        hostView.setAppWidget(w.appWidgetId, info);
                        card.addView(hostView, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    }
                }
            } catch (Throwable ignored) {}
        } else if (IosLayoutState.WIDGET_CLOCK.equals(w.kind)) {
            LinearLayout ll = new LinearLayout(this);
            ll.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(this);
            title.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            title.setTextSize(34f);
            title.setTextColor(getColor(R.color.label_primary));
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            TextView sub = new TextView(this);
            sub.setText(new SimpleDateFormat("EEEE · MMMM d", Locale.getDefault()).format(new Date()));
            sub.setTextSize(14f);
            sub.setTextColor(getColor(R.color.label_secondary));
            ll.addView(title);
            ll.addView(sub);
            card.addView(ll);
        } else if (IosLayoutState.WIDGET_SUGGESTIONS.equals(w.kind)) {
            LinearLayout ll = new LinearLayout(this);
            ll.setOrientation(LinearLayout.VERTICAL);
            TextView hdr = new TextView(this);
            hdr.setText(R.string.widget_suggestions_title);
            hdr.setTextSize(13f);
            hdr.setTextColor(getColor(R.color.label_secondary));
            hdr.setTypeface(hdr.getTypeface(), android.graphics.Typeface.BOLD);
            ll.addView(hdr);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            List<AppIndexer.AppModel> tops = fetchTopAppsFromRustOrFallback(4);
            for (final AppIndexer.AppModel a : tops) {
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(android.view.Gravity.CENTER);
                ImageView iv = new ImageView(this);
                int isz = Math.round(MotionSpec.dp(this, 44f));
                iv.setLayoutParams(new LinearLayout.LayoutParams(isz, isz));
                applyIconShapeClip(iv);
                loadAppIconAsync(a.pkg, iv);
                TextView tv = new TextView(this);
                tv.setText(a.label);
                tv.setMaxLines(1);
                tv.setTextSize(11f);
                tv.setTextColor(getColor(R.color.label_primary));
                item.addView(iv);
                item.addView(tv);
                item.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { launchAppWithFeedback(v, a.pkg, false); }
                });
                row.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Math.round(MotionSpec.dp(this, 10f));
            ll.addView(row, rlp);
            card.addView(ll);
        } else {
            LinearLayout ll = new LinearLayout(this);
            ll.setOrientation(LinearLayout.VERTICAL);
            TextView hdr = new TextView(this);
            hdr.setText(R.string.widget_status_title);
            hdr.setTextSize(16f);
            hdr.setTextColor(getColor(R.color.label_primary));
            hdr.setTypeface(hdr.getTypeface(), android.graphics.Typeface.BOLD);
            TextView body = new TextView(this);
            body.setText(getString(R.string.widget_status_body) + " · " + allApps.size() + " apps");
            body.setTextSize(13f);
            body.setTextColor(getColor(R.color.label_secondary));
            ll.addView(hdr);
            ll.addView(body);
            card.addView(ll);
        }

        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showWidgetOptionsDialog(w, isHomeSlot);
                return true;
            }
        });
        return card;
    }

    private void showWidgetOptionsDialog(final IosLayoutState.WidgetSlot w, final boolean isHomeSlot) {
        String[] options = {
                getString(R.string.widget_resize_small),
                getString(R.string.widget_resize_medium),
                getString(R.string.widget_resize_large),
                getString(R.string.remove_from_home)
        };
        new AlertDialog.Builder(this)
                .setTitle(w.label)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) w.span = IosLayoutState.SPAN_SMALL;
                        else if (which == 1) w.span = IosLayoutState.SPAN_MEDIUM;
                        else if (which == 2) w.span = IosLayoutState.SPAN_LARGE;
                        else if (which == 3) {
                            if (w.appWidgetId > 0 && appWidgetHost != null) {
                                try { appWidgetHost.deleteAppWidgetId(w.appWidgetId); } catch (Exception ignored) {}
                            }
                            if (isHomeSlot) layoutState.homeWidget = null;
                            else layoutState.todayWidgets.remove(w);
                        }
                        layoutState.save(prefs);
                        rebuildVirtualPager(currentVirtualIndex);
                    }
                })
                .show();
    }

    private void openWidgetPicker(String target) {
        widgetPickerTarget = target;
        isWidgetPickerOpen = true;
        sheetWidgetPicker.setVisibility(View.VISIBLE);
        widgetPickerList.removeAllViews();

        addPickerOptionRow(getString(R.string.widget_clock_title), "Offline Digital Clock", new Runnable() {
            @Override public void run() {
                addBuiltInWidget(IosLayoutState.WIDGET_CLOCK, getString(R.string.widget_clock_title));
            }
        });
        addPickerOptionRow(getString(R.string.widget_suggestions_title), "4x Rust Top Apps", new Runnable() {
            @Override public void run() {
                addBuiltInWidget(IosLayoutState.WIDGET_SUGGESTIONS, getString(R.string.widget_suggestions_title));
            }
        });
        addPickerOptionRow(getString(R.string.widget_status_title), "Zero-Network Privacy Vault", new Runnable() {
            @Override public void run() {
                addBuiltInWidget(IosLayoutState.WIDGET_STATUS, getString(R.string.widget_status_title));
            }
        });

        // System AppWidgetProviders
        if (appWidgetManager != null) {
            try {
                List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
                PackageManager pm = getPackageManager();
                for (int i = 0; i < Math.min(20, providers.size()); i++) {
                    final AppWidgetProviderInfo info = providers.get(i);
                    String label = info.loadLabel(pm);
                    addPickerOptionRow(label != null ? label : info.provider.getPackageName(),
                            info.minWidth + "x" + info.minHeight + "dp",
                            new Runnable() {
                                @Override public void run() {
                                    bindAndroidAppWidget(info);
                                }
                            });
                }
            } catch (Exception ignored) {}
        }
    }

    private void addPickerOptionRow(String title, String sub, final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_settings_card);
        int pad = Math.round(MotionSpec.dp(this, 14f));
        row.setPadding(pad, pad, pad, pad);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16f);
        tvTitle.setTextColor(getColor(R.color.label_primary));
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);

        TextView tvSub = new TextView(this);
        tvSub.setText(sub);
        tvSub.setTextSize(12f);
        tvSub.setTextColor(getColor(R.color.label_secondary));

        row.addView(tvTitle);
        row.addView(tvSub);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                closeWidgetPicker();
                onClick.run();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(MotionSpec.dp(this, 8f));
        widgetPickerList.addView(row, lp);
    }

    private void addBuiltInWidget(String kind, String label) {
        IosLayoutState.WidgetSlot slot = new IosLayoutState.WidgetSlot(
                "w_" + System.currentTimeMillis(), kind, -1, label, IosLayoutState.SPAN_MEDIUM);
        if ("home".equals(widgetPickerTarget)) {
            layoutState.homeWidget = slot;
        } else {
            layoutState.todayWidgets.add(slot);
        }
        layoutState.save(prefs);
        rebuildVirtualPager(currentVirtualIndex);
    }

    private void bindAndroidAppWidget(AppWidgetProviderInfo info) {
        if (appWidgetHost == null || appWidgetManager == null) return;
        int id = appWidgetHost.allocateAppWidgetId();
        boolean bound = appWidgetManager.bindAppWidgetIdIfAllowed(id, info.provider);
        if (bound) {
            finishAppWidgetBind(id, info);
        } else {
            pendingBindInfo = info;
            pendingBindWidgetId = id;
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            startActivityForResult(intent, REQ_BIND_APPWIDGET);
        }
    }

    private void finishAppWidgetBind(int id, AppWidgetProviderInfo info) {
        String label = info.loadLabel(getPackageManager());
        IosLayoutState.WidgetSlot slot = new IosLayoutState.WidgetSlot(
                "aw_" + id, IosLayoutState.WIDGET_APPWIDGET, id,
                label != null ? label : "Widget", IosLayoutState.SPAN_MEDIUM);
        if ("home".equals(widgetPickerTarget)) {
            layoutState.homeWidget = slot;
        } else {
            layoutState.todayWidgets.add(slot);
        }
        layoutState.save(prefs);
        rebuildVirtualPager(currentVirtualIndex);
    }

    private void closeWidgetPicker() {
        isWidgetPickerOpen = false;
        sheetWidgetPicker.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------------
    // Pinch-In Page Overview Switcher
    // ------------------------------------------------------------------------

    private void openPageOverview() {
        if (isOverviewOpen) return;
        isOverviewOpen = true;
        contextScrimOverlay.setVisibility(View.VISIBLE);
        overlayPageOverview.setVisibility(View.VISIBLE);
        overviewPagesRow.removeAllViews();

        int cardW = Math.round(MotionSpec.dp(this, 132f));
        int cardH = Math.round(MotionSpec.dp(this, 196f));
        int margin = Math.round(MotionSpec.dp(this, 10f));

        for (int i = 0; i < layoutState.pages.size(); i++) {
            final int homeIdx = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(android.view.Gravity.CENTER);
            card.setBackgroundResource(R.drawable.bg_library_tile);

            TextView title = new TextView(this);
            title.setText("Page " + (i + 1));
            title.setTextColor(getColor(R.color.label_primary));
            title.setTextSize(15f);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);

            TextView sub = new TextView(this);
            sub.setText(layoutState.pages.get(i).size() + " items");
            sub.setTextColor(getColor(R.color.label_secondary));
            sub.setTextSize(12f);

            card.addView(title);
            card.addView(sub);
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    closePageOverview();
                    snapToVirtualPage(homePageIndexToVirtual(homeIdx), 0f);
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            lp.setMargins(margin, 0, margin, 0);
            overviewPagesRow.addView(card, lp);
        }
    }

    private void closePageOverview() {
        if (!isOverviewOpen) return;
        isOverviewOpen = false;
        overlayPageOverview.setVisibility(View.GONE);
        if (!isContextMenuOpen && !isFolderOpen) {
            contextScrimOverlay.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------------
    // Full-Screen iOS Grouped Settings Page
    // ------------------------------------------------------------------------

    private void openSettingsPage() {
        isSettingsOpen = true;
        buildSettingsUi();
        pageSettings.setVisibility(View.VISIBLE);
        pageSettings.setTranslationY(MotionSpec.dp(this, 48f));
        pageSettings.setAlpha(0f);
        SpringPreset.animate(pageSettings, View.TRANSLATION_Y, 0f, SpringPreset.GLIDE, 0f, null);
        pageSettings.animate().alpha(1f).setDuration(MotionSpec.DURATION_FAST_MS).start();
    }

    private void closeSettingsPage() {
        if (!isSettingsOpen) return;
        isSettingsOpen = false;
        pageSettings.animate().alpha(0f).setDuration(MotionSpec.DURATION_FAST_MS)
                .withEndAction(new Runnable() {
                    @Override public void run() { pageSettings.setVisibility(View.GONE); }
                }).start();
    }

    private void buildSettingsUi() {
        settingsSectionsContainer.removeAllViews();

        // 1. GENERAL
        LinearLayout generalCard = addSettingsSection(getString(R.string.settings_section_general));
        addSettingsItem(generalCard, getString(R.string.set_home_prompt), "", new Runnable() {
            @Override public void run() { requestDefaultHomeRole(); }
        }, true);

        // 2. APPEARANCE
        LinearLayout appCard = addSettingsSection(getString(R.string.settings_section_appearance));
        addSettingsItem(appCard, getString(R.string.theme_title), formatThemeLabel(themeMode), new Runnable() {
            @Override public void run() { showThemeChoiceDialog(); }
        }, false);
        addSettingsItem(appCard, getString(R.string.icon_shape_title), formatShapeLabel(iconShape), new Runnable() {
            @Override public void run() { showIconShapeDialog(); }
        }, false);
        addSettingsSwitch(appCard, getString(R.string.blur_materials_title), null, BlurGate.isUserBlurEnabled(this), new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                BlurGate.setUserBlurEnabled(MainActivity.this, checked);
                applyWallpaperVisuals();
            }
        }, false);
        addSettingsSwitch(appCard, getString(R.string.show_icon_labels_title), null, showLabels, new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                showLabels = checked;
                prefs.edit().putBoolean(PREF_SHOW_LABELS, checked).apply();
                rebuildVirtualPager(currentVirtualIndex);
            }
        }, false);
        addSettingsSlider(appCard, getString(R.string.wallpaper_dim_title, wallpaperDimPct), 40, wallpaperDimPct, new SliderChanged() {
            @Override public void onChanged(int val, TextView titleTv) {
                wallpaperDimPct = val;
                titleTv.setText(getString(R.string.wallpaper_dim_title, val));
                prefs.edit().putInt(PREF_WALLPAPER_DIM, val).apply();
                applyWallpaperVisuals();
            }
        }, false);
        addSettingsSlider(appCard, getString(R.string.wallpaper_blur_title, wallpaperBlurDp), 16, wallpaperBlurDp, new SliderChanged() {
            @Override public void onChanged(int val, TextView titleTv) {
                wallpaperBlurDp = val;
                titleTv.setText(getString(R.string.wallpaper_blur_title, val));
                prefs.edit().putInt(PREF_WALLPAPER_BLUR, val).apply();
                applyWallpaperVisuals();
            }
        }, true);

        // 3. HOME SCREEN
        LinearLayout homeCard = addSettingsSection(getString(R.string.settings_section_home));
        addSettingsSlider(homeCard, getString(R.string.home_columns_title, gridColumns), 2, gridColumns - 4, new SliderChanged() {
            @Override public void onChanged(int val, TextView titleTv) {
                gridColumns = 4 + val;
                titleTv.setText(getString(R.string.home_columns_title, gridColumns));
                prefs.edit().putInt(PREF_GRID_COLUMNS, gridColumns).apply();
                rebuildVirtualPager(currentVirtualIndex);
            }
        }, false);
        addSettingsSlider(homeCard, getString(R.string.dock_capacity_title, dockCapacity), 2, dockCapacity - 4, new SliderChanged() {
            @Override public void onChanged(int val, TextView titleTv) {
                dockCapacity = 4 + val;
                titleTv.setText(getString(R.string.dock_capacity_title, dockCapacity));
                prefs.edit().putInt(PREF_DOCK_CAPACITY, dockCapacity).apply();
                rebuildDock();
            }
        }, false);
        addSettingsSwitch(homeCard, getString(R.string.show_search_pill_title), null, showSearchPill, new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                showSearchPill = checked;
                prefs.edit().putBoolean(PREF_SHOW_SEARCH_PILL, checked).apply();
                applySearchPillVisibility();
            }
        }, false);
        addSettingsItem(homeCard, getString(R.string.widget_add_to_home),
                layoutState.homeWidget != null ? layoutState.homeWidget.label : "", new Runnable() {
            @Override public void run() {
                closeSettingsPage();
                openWidgetPicker("home");
            }
        }, false);
        addSettingsItem(homeCard, getString(R.string.reset_home_layout), "", new Runnable() {
            @Override public void run() { confirmResetHomeLayout(); }
        }, true);

        // 4. APP LIBRARY & TODAY
        LinearLayout libCard = addSettingsSection(getString(R.string.settings_section_library));
        addSettingsSwitch(libCard, getString(R.string.show_app_library_title), null, showLibraryPage, new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                showLibraryPage = checked;
                prefs.edit().putBoolean(PREF_SHOW_LIBRARY, checked).apply();
                rebuildVirtualPager(homePageIndexToVirtual(0));
            }
        }, false);
        addSettingsSwitch(libCard, getString(R.string.show_today_page_title), null, showTodayPage, new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                showTodayPage = checked;
                prefs.edit().putBoolean(PREF_SHOW_TODAY, checked).apply();
                rebuildVirtualPager(homePageIndexToVirtual(0));
            }
        }, false);
        addSettingsItem(libCard, getString(R.string.cycle_library_order), "", new Runnable() {
            @Override public void run() {
                layoutState.cycleLibraryCategoryOrder();
                layoutState.save(prefs);
                rebuildVirtualPager(currentVirtualIndex);
                Toast.makeText(MainActivity.this, R.string.cycle_library_order, Toast.LENGTH_SHORT).show();
            }
        }, true);

        // 5. SPOTLIGHT
        LinearLayout spotCard = addSettingsSection(getString(R.string.settings_section_spotlight));
        addSettingsSwitch(spotCard, getString(R.string.spotlight_suggestions_title), null, spotlightSuggestionsEnabled, new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                spotlightSuggestionsEnabled = checked;
                prefs.edit().putBoolean(PREF_SPOTLIGHT_SUGGESTIONS, checked).apply();
            }
        }, false);
        addSettingsSwitch(spotCard, getString(R.string.spotlight_search_settings_title), null, spotlightSettingsEnabled, new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                spotlightSettingsEnabled = checked;
                prefs.edit().putBoolean(PREF_SPOTLIGHT_SETTINGS, checked).apply();
            }
        }, false);
        addSettingsItem(spotCard, getString(R.string.reset_history), "", new Runnable() {
            @Override public void run() { confirmResetHistory(); }
        }, true);

        // 6. NOTIFICATIONS & BADGES
        LinearLayout badgeCard = addSettingsSection(getString(R.string.settings_section_badges));
        boolean accessGranted = KayumarsNotificationListener.isNotificationAccessGranted(this);
        addSettingsItem(badgeCard,
                accessGranted ? getString(R.string.notification_access_granted) : getString(R.string.notification_access_btn),
                accessGranted ? "Active" : "Tap",
                new Runnable() {
                    @Override public void run() { showNotificationExplainerDialog(); }
                }, false);
        addSettingsSwitch(badgeCard, getString(R.string.notification_badges_title), null, notifBadgesEnabled, new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                notifBadgesEnabled = checked;
                prefs.edit().putBoolean(PREF_NOTIF_BADGES, checked).apply();
                rebuildVirtualPager(currentVirtualIndex);
            }
        }, false);
        addSettingsSwitch(badgeCard, getString(R.string.new_app_dot_title), null, newDotEnabled, new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                newDotEnabled = checked;
                prefs.edit().putBoolean(PREF_NEW_DOT, checked).apply();
                rebuildVirtualPager(currentVirtualIndex);
            }
        }, false);
        addSettingsItem(badgeCard, getString(R.string.mute_app_badges_title, layoutState.mutedBadgePkgs.size()), "", new Runnable() {
            @Override public void run() { showMuteAppsDialog(); }
        }, true);

        // 7. GESTURES & MOTION
        LinearLayout gestCard = addSettingsSection(getString(R.string.settings_section_gestures));
        addSettingsSwitch(gestCard, getString(R.string.gesture_swipe_down_spotlight), null, gestureSwipeSpotlight, new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                gestureSwipeSpotlight = checked;
                prefs.edit().putBoolean(PREF_GESTURE_SWIPE_SPOTLIGHT, checked).apply();
            }
        }, false);
        addSettingsSwitch(gestCard, getString(R.string.gesture_pinch_overview), null, gesturePinchOverview, new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                gesturePinchOverview = checked;
                prefs.edit().putBoolean(PREF_GESTURE_PINCH, checked).apply();
            }
        }, false);
        addSettingsSwitch(gestCard, getString(R.string.gesture_double_tap_lock),
                KayumarsLockService.isEnabled(this) ? null : getString(R.string.gesture_lock_hint),
                gestureDoubleTapLock, new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                gestureDoubleTapLock = checked;
                prefs.edit().putBoolean(PREF_GESTURE_LOCK, checked).apply();
            }
        }, false);
        addSettingsItem(gestCard, getString(R.string.lock_service_setup_btn),
                KayumarsLockService.isEnabled(this) ? "On" : "Off", new Runnable() {
            @Override public void run() { openSystemSettingAction(Settings.ACTION_ACCESSIBILITY_SETTINGS); }
        }, false);
        addSettingsSwitch(gestCard, getString(R.string.motion_debug_title), null,
                prefs.getBoolean(PREF_MOTION_DEBUG, false), new SwitchChanged() {
            @Override public void onChanged(boolean checked) {
                prefs.edit().putBoolean(PREF_MOTION_DEBUG, checked).apply();
                if (motionDebug != null) motionDebug.setEnabled(checked);
            }
        }, true);

        // 8. ABOUT
        LinearLayout aboutCard = addSettingsSection(getString(R.string.settings_section_about));
        addSettingsItem(aboutCard, getString(R.string.about_version), getString(R.string.offline_badge), new Runnable() {
            @Override public void run() {}
        }, false);
        addSettingsItem(aboutCard, getString(R.string.about_engine), "", new Runnable() {
            @Override public void run() {}
        }, true);
    }

    private interface SwitchChanged { void onChanged(boolean checked); }
    private interface SliderChanged { void onChanged(int value, TextView titleTv); }

    private LinearLayout addSettingsSection(String headerTitle) {
        View section = LayoutInflater.from(this).inflate(R.layout.row_settings_section, settingsSectionsContainer, false);
        TextView hdr = section.findViewById(R.id.settings_section_header);
        LinearLayout card = section.findViewById(R.id.settings_section_card);
        hdr.setText(headerTitle);
        settingsSectionsContainer.addView(section);
        return card;
    }

    private void addSettingsItem(LinearLayout card, String title, String value, final Runnable onClick, boolean isLast) {
        View row = LayoutInflater.from(this).inflate(R.layout.row_settings_item, card, false);
        TextView tvTitle = row.findViewById(R.id.settings_item_title);
        TextView tvVal = row.findViewById(R.id.settings_item_value);
        View div = row.findViewById(R.id.settings_item_divider);
        tvTitle.setText(title);
        tvVal.setText(value);
        div.setVisibility(isLast ? View.GONE : View.VISIBLE);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onClick.run(); }
        });
        card.addView(row);
    }

    private void addSettingsSwitch(LinearLayout card, String title, String sub, boolean checked, final SwitchChanged cb, boolean isLast) {
        View row = LayoutInflater.from(this).inflate(R.layout.row_settings_switch, card, false);
        TextView tvTitle = row.findViewById(R.id.settings_switch_title);
        TextView tvSub = row.findViewById(R.id.settings_switch_subtitle);
        Switch sw = row.findViewById(R.id.settings_switch_toggle);
        View div = row.findViewById(R.id.settings_switch_divider);
        tvTitle.setText(title);
        if (sub != null && !sub.isEmpty()) {
            tvSub.setText(sub);
            tvSub.setVisibility(View.VISIBLE);
        }
        sw.setChecked(checked);
        div.setVisibility(isLast ? View.GONE : View.VISIBLE);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cb.onChanged(isChecked);
            }
        });
        card.addView(row);
    }

    private void addSettingsSlider(LinearLayout card, String title, int max, int progress, final SliderChanged cb, boolean isLast) {
        View row = LayoutInflater.from(this).inflate(R.layout.row_settings_slider, card, false);
        final TextView tvTitle = row.findViewById(R.id.settings_slider_title);
        SeekBar seek = row.findViewById(R.id.settings_slider_seek);
        View div = row.findViewById(R.id.settings_slider_divider);
        tvTitle.setText(title);
        seek.setMax(max);
        seek.setProgress(Math.max(0, Math.min(max, progress)));
        div.setVisibility(isLast ? View.GONE : View.VISIBLE);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (fromUser) cb.onChanged(p, tvTitle);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(row);
    }

    private String formatThemeLabel(String m) {
        if ("light".equals(m)) return getString(R.string.theme_light);
        if ("dark".equals(m)) return getString(R.string.theme_dark);
        if ("amoled".equals(m)) return getString(R.string.theme_amoled);
        return getString(R.string.theme_system);
    }

    private String formatShapeLabel(String s) {
        if ("circle".equals(s)) return getString(R.string.icon_shape_circle);
        if ("rounded_square".equals(s)) return getString(R.string.icon_shape_rounded_square);
        if ("system".equals(s)) return getString(R.string.icon_shape_system);
        return getString(R.string.icon_shape_squircle);
    }

    private void showThemeChoiceDialog() {
        final String[] keys = {"system", "light", "dark", "amoled"};
        String[] labels = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark),
                getString(R.string.theme_amoled)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        prefs.edit().putString(PREF_THEME, keys[which]).apply();
                        recreate();
                    }
                }).show();
    }

    private void showIconShapeDialog() {
        final String[] keys = {"squircle", "circle", "rounded_square", "system"};
        String[] labels = {
                getString(R.string.icon_shape_squircle),
                getString(R.string.icon_shape_circle),
                getString(R.string.icon_shape_rounded_square),
                getString(R.string.icon_shape_system)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.icon_shape_title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        iconShape = keys[which];
                        prefs.edit().putString(PREF_ICON_SHAPE, iconShape).apply();
                        buildSettingsUi();
                        rebuildVirtualPager(currentVirtualIndex);
                    }
                }).show();
    }

    private void showNotificationExplainerDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_explainer_title)
                .setMessage(R.string.notification_explainer_body)
                .setPositiveButton(R.string.enable, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        openSystemSettingAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMuteAppsDialog() {
        if (allApps.isEmpty()) return;
        final String[] labels = new String[allApps.size()];
        final boolean[] checked = new boolean[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) {
            AppIndexer.AppModel a = allApps.get(i);
            labels[i] = a.label;
            checked[i] = layoutState.mutedBadgePkgs.contains(a.pkg);
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.mute_app_badges_title, layoutState.mutedBadgePkgs.size()))
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        String pkg = allApps.get(which).pkg;
                        if (isChecked) layoutState.mutedBadgePkgs.add(pkg);
                        else layoutState.mutedBadgePkgs.remove(pkg);
                    }
                })
                .setPositiveButton(R.string.done, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        layoutState.save(prefs);
                        buildSettingsUi();
                        rebuildVirtualPager(currentVirtualIndex);
                    }
                })
                .show();
    }

    private void confirmResetHomeLayout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_home_layout)
                .setMessage(R.string.reset_home_layout_confirm)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        layoutState.initialized = false;
                        layoutState.seedIfNeeded(allApps, getPageCapacity(), dockCapacity);
                        layoutState.save(prefs);
                        rebuildVirtualPager(homePageIndexToVirtual(0));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmResetHistory() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_history)
                .setMessage(R.string.reset_history_confirm)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        LaunchLogger.resetHistory(MainActivity.this);
                        scanInstalledApps(false);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------------
    // Helpers: Wallpaper Visuals, Shapes, Indexing, Launch, Predictive Back
    // ------------------------------------------------------------------------

    private void applyWallpaperVisuals() {
        int alpha = Math.round((wallpaperDimPct / 100f) * 255f);
        wallpaperDimScrim.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
        BlurGate.applyWallpaperBlur(wallpaperBlurLayer, wallpaperBlurDp);
    }

    private void applySearchPillVisibility() {
        updateDockAndPillForPage();
    }

    private void applyIconShapeClip(View v) {
        if (v == null) return;
        if ("system".equals(iconShape)) {
            v.setClipToOutline(false);
            v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            return;
        }
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                float radius;
                if ("circle".equals(iconShape)) radius = Math.min(w, h) * 0.5f;
                else if ("rounded_square".equals(iconShape)) radius = Math.min(w, h) * 0.16f;
                else radius = Math.min(w, h) * 0.225f; // iOS 22.5% squircle
                outline.setRoundRect(0, 0, w, h, radius);
            }
        });
        v.setClipToOutline(true);
    }

    private void scanInstalledApps(final boolean detectNewlyInstalled) {
        AppIndexer.scanAsync(this, new AppIndexer.Callback() {
            @Override
            public void onScanComplete(final List<AppIndexer.AppModel> scanned) {
                Collections.sort(scanned, new Comparator<AppIndexer.AppModel>() {
                    @Override public int compare(AppIndexer.AppModel a, AppIndexer.AppModel b) {
                        return a.label.compareToIgnoreCase(b.label);
                    }
                });
                final Set<String> workSet = AppMetadataHelper.detectWorkProfilePackages(MainActivity.this);
                final String jsonPayload = buildCoreIndexJson(scanned);
                Core.replaceIndex(jsonPayload);

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        Set<String> prevKnown = new HashSet<String>(knownPackages);
                        allApps.clear();
                        allApps.addAll(scanned);
                        appByPkg.clear();
                        knownPackages.clear();
                        workProfilePackages.clear();
                        workProfilePackages.addAll(workSet);

                        for (AppIndexer.AppModel a : scanned) {
                            appByPkg.put(a.pkg, a);
                            knownPackages.add(a.pkg);
                            if (detectNewlyInstalled && !prevKnown.isEmpty() && !prevKnown.contains(a.pkg)) {
                                layoutState.placeOnFirstFreePage(a.pkg, getPageCapacity(), true);
                            }
                        }

                        layoutState.seedIfNeeded(scanned, getPageCapacity(), dockCapacity);
                        layoutState.save(prefs);
                        rebuildVirtualPager(currentVirtualIndex);
                    }
                });
            }
        });
    }

    private static String buildCoreIndexJson(List<AppIndexer.AppModel> apps) {
        StringBuilder sb = new StringBuilder(apps.size() * 48);
        sb.append("[");
        for (int i = 0; i < apps.size(); i++) {
            AppIndexer.AppModel a = apps.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"package\":\"").append(escapeJson(a.pkg))
                    .append("\",\"label\":\"").append(escapeJson(a.label)).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void loadAppIconAsync(final String pkg, final ImageView target) {
        if (pkg == null || target == null) return;
        target.setTag(pkg);
        Drawable cached = iconCache.get(pkg);
        if (cached != null) {
            target.setImageDrawable(cached);
            return;
        }
        target.setImageDrawable(null);
        if (iconExecutor.isShutdown() || isFinishing() || isDestroyed()) return;
        try {
            iconExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Drawable d = null;
                    try {
                        d = getPackageManager().getApplicationIcon(pkg);
                    } catch (Exception ignored) {}
                    final Drawable loaded = d;
                    if (loaded != null) iconCache.put(pkg, loaded);
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (isFinishing() || isDestroyed()) return;
                            if (pkg.equals(target.getTag()) && loaded != null) {
                                target.setImageDrawable(loaded);
                            }
                        }
                    });
                }
            });
        } catch (Exception ignored) {}
    }

    private void launchAppWithFeedback(View sourceView, String pkg, boolean clipReveal) {
        if (sourceView != null) {
            sourceView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            SpringPreset.animateScale(sourceView, 0.94f, SpringPreset.SNAP, 0f, null);
        }
        LaunchLogger.launchAppWithTransition(this, sourceView, pkg, clipReveal);
    }

    private void handleDoubleTapLock() {
        if (KayumarsLockService.lockScreenNow()) return;
        Toast.makeText(this, R.string.gesture_lock_hint, Toast.LENGTH_SHORT).show();
    }

    private boolean hasAnyDismissibleBackTarget() {
        if (isSettingsOpen || isContextMenuOpen || isFolderOpen || isWidgetPickerOpen
                || expandedLibraryCategory != null || isSpotlightOpen || isEditMode || isOverviewOpen) {
            return true;
        }
        int home0 = homePageIndexToVirtual(0);
        return currentVirtualIndex != home0;
    }

    private boolean handlePredictiveBackStep() {
        if (isSettingsOpen) { closeSettingsPage(); return true; }
        if (isContextMenuOpen) { dismissIosContextMenu(); return true; }
        if (isFolderOpen) { closeFolder(); return true; }
        if (isWidgetPickerOpen) { closeWidgetPicker(); return true; }
        if (expandedLibraryCategory != null) {
            expandedLibraryCategory = null;
            rebuildVirtualPager(currentVirtualIndex);
            return true;
        }
        if (isSpotlightOpen) { closeSpotlight(); return true; }
        if (isEditMode) { setEditMode(false); return true; }
        if (isOverviewOpen) { closePageOverview(); return true; }
        int home0 = homePageIndexToVirtual(0);
        if (currentVirtualIndex != home0) {
            snapToVirtualPage(home0, 0f);
            return true;
        }
        return false;
    }

    private void dismissAllOverlaysToHome() {
        isSettingsOpen = false;
        pageSettings.setVisibility(View.GONE);
        dismissIosContextMenu();
        if (isFolderOpen) {
            isFolderOpen = false;
            cardFolderOverlay.setVisibility(View.GONE);
        }
        closeWidgetPicker();
        if (isSpotlightOpen) {
            isSpotlightOpen = false;
            overlaySpotlight.setVisibility(View.GONE);
        }
        if (isOverviewOpen) closePageOverview();
        if (isEditMode) setEditMode(false);
        snapToVirtualPage(homePageIndexToVirtual(0), 0f);
    }

    private boolean shouldShowDefaultHomePrompt() {
        if (prefs.getBoolean(PREF_HOME_DISMISSED, false)) return false;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return ri == null || ri.activityInfo == null || !getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    private void requestDefaultHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE_HOME);
                return;
            }
        }
        openSystemSettingAction(Settings.ACTION_HOME_SETTINGS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE_HOME) {
            rebuildVirtualPager(currentVirtualIndex);
        } else if (requestCode == REQ_BIND_APPWIDGET && resultCode == RESULT_OK && pendingBindInfo != null) {
            finishAppWidgetBind(pendingBindWidgetId, pendingBindInfo);
            pendingBindInfo = null;
        }
    }

    private void openAppInfo(String pkg) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private void shareAppDetails(AppIndexer.AppModel app) {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, app.label + " (" + app.pkg + ")");
            startActivity(Intent.createChooser(send, getString(R.string.share_app)));
        } catch (Exception ignored) {}
    }

    private void requestUninstallApp(String pkg) {
        try {
            Intent del = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
            del.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(del);
        } catch (Exception ignored) {}
    }

    private void openSystemSettingAction(String action) {
        if ("kayumars://settings".equals(action)) {
            closeSpotlight();
            openSettingsPage();
            return;
        }
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private void hideKeyboard(View v) {
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void registerDynamicReceivers() {
        if (receiversRegistered) return;
        packageReceiver = new PackageChangeReceiver(new PackageChangeReceiver.Listener() {
            @Override public void onPackagesChanged() {
                scanInstalledApps(true);
            }
        });
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageReceiver, pf, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(packageReceiver, pf);
            }
        } catch (Exception ignored) {}

        timeTickReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (showTodayPage && currentVirtualIndex == 0) {
                    rebuildVirtualPager(0);
                }
            }
        };
        try {
            IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(timeTickReceiver, tf, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(timeTickReceiver, tf);
            }
        } catch (Exception ignored) {}
        receiversRegistered = true;
    }

    private void unregisterDynamicReceivers() {
        if (!receiversRegistered) return;
        try { if (packageReceiver != null) unregisterReceiver(packageReceiver); } catch (Exception ignored) {}
        try { if (timeTickReceiver != null) unregisterReceiver(timeTickReceiver); } catch (Exception ignored) {}
        receiversRegistered = false;
    }

    // ------------------------------------------------------------------------
    // List & Grid Adapters
    // ------------------------------------------------------------------------

    private final class SimpleCellAdapter extends BaseAdapter {
        private final List<AppIndexer.AppModel> items;
        private final boolean darkLabelFallback;

        SimpleCellAdapter(List<AppIndexer.AppModel> items, boolean darkLabelFallback) {
            this.items = items;
            this.darkLabelFallback = darkLabelFallback;
        }

        @Override public int getCount() { return items.size(); }
        @Override public AppIndexer.AppModel getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null
                    ? convertView
                    : LayoutInflater.from(MainActivity.this).inflate(R.layout.item_home_cell, parent, false);
            AppIndexer.AppModel app = items.get(position);
            ImageView icon = v.findViewById(R.id.app_icon);
            TextView label = v.findViewById(R.id.app_label);
            TextView badgeNotif = v.findViewById(R.id.badge_notif_count);
            ImageView badgeWork = v.findViewById(R.id.badge_work_profile);

            applyIconShapeClip(icon);
            loadAppIconAsync(app.pkg, icon);
            label.setText(app.label);
            if (!darkLabelFallback) {
                label.setTextColor(getColor(R.color.label_primary));
                label.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
            }
            int cnt = KayumarsNotificationListener.getBadgeCount(app.pkg, notifBadgesEnabled, layoutState.mutedBadgePkgs);
            if (cnt > 0) {
                badgeNotif.setVisibility(View.VISIBLE);
                badgeNotif.setText(cnt > 99 ? "99+" : String.valueOf(cnt));
            } else {
                badgeNotif.setVisibility(View.GONE);
            }
            badgeWork.setVisibility(workProfilePackages.contains(app.pkg) ? View.VISIBLE : View.GONE);
            return v;
        }
    }

    private final class SpotlightResultsAdapter extends BaseAdapter {
        @Override public int getCount() { return spotlightRows.size(); }
        @Override public SpotlightRow getItem(int position) { return spotlightRows.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null
                    ? convertView
                    : LayoutInflater.from(MainActivity.this).inflate(R.layout.item_spotlight_row, parent, false);
            SpotlightRow row = spotlightRows.get(position);
            TextView sectionHdr = v.findViewById(R.id.spotlight_section_title);
            View card = v.findViewById(R.id.spotlight_row_card);
            ImageView icon = v.findViewById(R.id.spotlight_row_icon);
            TextView title = v.findViewById(R.id.spotlight_row_title);
            TextView sub = v.findViewById(R.id.spotlight_row_subtitle);
            TextView action = v.findViewById(R.id.spotlight_row_action);
            View chevron = v.findViewById(R.id.spotlight_row_chevron);

            if (row.type == ROW_TYPE_HEADER) {
                sectionHdr.setVisibility(View.VISIBLE);
                sectionHdr.setText(row.header);
                card.setVisibility(View.GONE);
                return v;
            }

            sectionHdr.setVisibility(View.GONE);
            card.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams clp = card.getLayoutParams();
            if (clp != null) {
                clp.height = getResources().getDimensionPixelSize(
                        row.type == ROW_TYPE_TOP_HIT ? R.dimen.ios_spotlight_top_hit_height : R.dimen.ios_list_row_height);
                card.setLayoutParams(clp);
            }

            if (row.type == ROW_TYPE_SETTING && row.setting != null) {
                icon.setImageResource(R.drawable.ic_glyph_settings);
                icon.setColorFilter(getColor(R.color.system_blue));
                title.setText(row.setting.title);
                sub.setText(row.setting.subtitle);
                sub.setVisibility(View.VISIBLE);
                action.setVisibility(View.GONE);
                chevron.setVisibility(View.VISIBLE);
            } else if (row.app != null) {
                icon.clearColorFilter();
                applyIconShapeClip(icon);
                loadAppIconAsync(row.app.pkg, icon);
                title.setText(row.app.label);
                sub.setText(row.type == ROW_TYPE_TOP_HIT ? getString(R.string.spotlight_top_hit) : row.app.pkg);
                sub.setVisibility(View.VISIBLE);
                action.setVisibility(row.type == ROW_TYPE_TOP_HIT ? View.VISIBLE : View.GONE);
                chevron.setVisibility(row.type == ROW_TYPE_TOP_HIT ? View.GONE : View.VISIBLE);
            }
            return v;
        }
    }
}
