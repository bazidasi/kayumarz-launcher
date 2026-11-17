package com.kayumars.launcher

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import com.kayumars.launcher.bridge.AppEntry
import com.kayumars.launcher.bridge.Core
import com.kayumars.launcher.databinding.ActivityMainBinding
import com.kayumars.launcher.history.LaunchLogger
import com.kayumars.launcher.model.IosLayoutState
import com.kayumars.launcher.notify.KayumarsNotificationListener
import com.kayumars.launcher.pkg.AppIndexer
import com.kayumars.launcher.pkg.AppMetadataHelper
import com.kayumars.launcher.pkg.PackageChangeReceiver
import com.kayumars.launcher.service.KayumarsLockService
import com.kayumars.launcher.ui.BackdropBlur
import com.kayumars.launcher.ui.IconCache
import com.kayumars.launcher.ui.motion.BackInvokedMotion
import com.kayumars.launcher.ui.motion.MotionDebug
import com.kayumars.launcher.ui.motion.MotionSpec
import com.kayumars.launcher.ui.motion.SpringPreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * MainActivity — iOS 17/18 "California Neutral" Home Screen Launcher Controller (Kotlin shell).
 * Synchronized with Java runtime + Rust Core search/ranking engine.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var iconCache: IconCache
    private lateinit var layoutState: IosLayoutState
    private lateinit var backdropBlur: BackdropBlur

    private var themeMode = "system"
    private var iconShape = "squircle"
    private var showLabels = true
    private var showSearchPill = true
    private var showTodayPage = true
    private var showLibraryPage = true
    private var gridColumns = 4
    private var dockCapacity = 4
    private var wallpaperDimPct = 0
    private var notifBadgesEnabled = true
    private var newDotEnabled = true
    private var spotlightSuggestionsEnabled = true
    private var spotlightSettingsEnabled = true
    private var gestureDoubleTapLock = true
    private var gesturePinchOverview = true
    private var gestureSwipeSpotlight = true

    private val allApps = mutableListOf<AppEntry>()
    private val appByPkg = mutableMapOf<String, AppEntry>()
    private val knownPackages = mutableSetOf<String>()
    private val workProfilePackages = mutableSetOf<String>()
    private val systemSettings = mutableListOf<SettingItem>()
    private val spotlightRows = mutableListOf<SpotlightRow>()
    private val spotlightGridApps = mutableListOf<AppEntry>()
    private val spotlightSuggestionApps = mutableListOf<AppEntry>()
    private val folderVisibleApps = mutableListOf<AppEntry>()
    private val expandedLibraryApps = mutableListOf<AppEntry>()

    private lateinit var folderAdapter: SimpleCellAdapter
    private lateinit var spotlightSuggestionsAdapter: SimpleCellAdapter
    private lateinit var spotlightGridAdapter: SimpleCellAdapter
    private lateinit var spotlightResultsAdapter: SpotlightResultsAdapter

    private val virtualPages = mutableListOf<PageSpec>()
    private var currentVirtualIndex = 0
    private var isEditMode = false
    private var isSpotlightOpen = false
    private var isSettingsOpen = false
    private var isContextMenuOpen = false
    private var isFolderOpen = false
    private var isOverviewOpen = false
    private var isWidgetPickerOpen = false
    private var openFolderId: String? = null
    private var openFolderPage = 0
    private var expandedLibraryCategory: String? = null
    private var widgetPickerTarget = "today"
    private var liftedContextAnchor: View? = null

    private var touchSlopPx = 24
    private var isPagerDragging = false
    private var isSpotlightPullDragging = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastEmptyTapMs = 0L
    private var pagerVelocityTracker: VelocityTracker? = null
    private var pinchDetector: ScaleGestureDetector? = null
    private val jiggleAnimators = mutableListOf<ObjectAnimator>()

    private var isDraggingItem = false
    private var dragFromDock = false
    private var dragSourcePageIdx = -1
    private var dragSourceSlotPos = -1
    private var dragAppPkg: String? = null
    private var dragIsFolder = false
    private var dragHoverSlotPos = -1
    private var dragHoverDock = false
    private var edgeSwitchDir = 0

    private val uiHandler = Handler(Looper.getMainLooper())
    // Live clock TextViews with their formats; refreshed in place on TIME_TICK (no rebuild).
    private val clockBindings = mutableListOf<Pair<TextView, SimpleDateFormat>>()
    private val folderMergeTrigger = Runnable {
        if (isDraggingItem && !dragIsFolder && dragHoverSlotPos >= 0) {
            showMergeRingAtHomeSlot(dragSourcePageIdx, dragHoverSlotPos)
        }
    }
    private val edgePageSwitchRunnable = Runnable {
        if (!isDraggingItem || edgeSwitchDir == 0) return@Runnable
        val homeIdx = getCurrentHomePageIndex()
        val nextHome = homeIdx + edgeSwitchDir
        if (nextHome in layoutState.pages.indices) {
            snapToVirtualPage(homePageIndexToVirtual(nextHome), 0f)
            dragSourcePageIdx = nextHome
        }
    }

    private var packageReceiver: PackageChangeReceiver? = null
    private var timeTickReceiver: BroadcastReceiver? = null
    private var receiversRegistered = false
    private var motionDebug: MotionDebug? = null
    private var backInvokedMotion: BackInvokedMotion? = null
    private var appWidgetManager: AppWidgetManager? = null
    private var appWidgetHost: AppWidgetHost? = null
    private var pendingBindInfo: AppWidgetProviderInfo? = null
    private var pendingBindWidgetId = -1

    private data class SettingItem(
        val title: String,
        val subtitle: String,
        val action: String,
        val iconRes: Int = 0
    )
    private data class SpotlightRow(
        val type: Int,
        val header: String? = null,
        val app: AppEntry? = null,
        val setting: SettingItem? = null
    )
    private class PageSpec(val type: Int, val homePageIndex: Int) {
        var view: View? = null
        companion object {
            const val TYPE_TODAY = 0
            const val TYPE_HOME = 1
            const val TYPE_LIBRARY = 2
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        applySavedThemeBeforeInflation()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // SDK 37 PhoneWindow.getInsetsController NPEs while decor is null; must run after setContentView.
        configureSystemBars()

        Core.init(this)
        iconCache = IconCache(this)
        layoutState = IosLayoutState.load(prefs)
        loadPreferences()
        backdropBlur = BackdropBlur(this, binding.homeShell, binding.backdropBlurView)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        touchSlopPx = ViewConfiguration.get(this).scaledTouchSlop

        setupWindowInsets()
        initSystemSettingsList()
        initAdapters()
        initMotionInfra()
        setupListeners()
        applyWallpaperVisuals()
        updateDockAndPillForPage()

        registerDynamicReceivers()
        KayumarsNotificationListener.setBadgeUpdateListener {
            uiHandler.post { updateBadgesInPlace() }
        }
        scanInstalledApps(false)
        CrashReporter.showIfPresent(this)
    }

    override fun onStart() {
        super.onStart()
        try { appWidgetHost?.startListening() } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try { appWidgetHost?.stopListening() } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean(PREF_MOTION_DEBUG, false)) motionDebug?.setEnabled(true)
        if (allApps.isEmpty()) scanInstalledApps(false) else rebuildVirtualPager(currentVirtualIndex)
    }

    override fun onPause() {
        super.onPause()
        motionDebug?.stop()
        cancelJiggleAnimations()
        Core.saveNow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dismissAllOverlaysToHome()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!handlePredictiveBackStep()) {
            // Home launcher remains foregrounded
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backInvokedMotion?.unregister()
        motionDebug?.stop()
        MotionSpec.cancelAll()
        cancelJiggleAnimations()
        KayumarsNotificationListener.setBadgeUpdateListener(null)
        unregisterDynamicReceivers()
    }

    private fun applySavedThemeBeforeInflation() {
        themeMode = prefs.getString(PREF_THEME, "system") ?: "system"
        applyNightModeForTheme(themeMode)
        when (themeMode) {
            "amoled" -> setTheme(R.style.Theme_Kayumars_Amoled)
            "light" -> setTheme(R.style.Theme_Kayumars_Light)
            "dark" -> setTheme(R.style.Theme_Kayumars_Dark)
            else -> {
                val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                setTheme(
                    if (night == Configuration.UI_MODE_NIGHT_YES) R.style.Theme_Kayumars_Dark
                    else R.style.Theme_Kayumars_Light
                )
            }
        }
    }

    // values/ vs values-night/ colors only switch via the resolved night mode, not setTheme().
    private fun applyNightModeForTheme(mode: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark", "amoled" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun configureSystemBars() {
        val resolvedLight = themeMode == "light" ||
            (themeMode == "system" &&
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO)
        setSystemBarsLight(resolvedLight)
    }

    private fun setSystemBarsLight(light: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val ctrl = window.insetsController ?: return
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            ctrl.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (light) {
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.homeShell.setPadding(0, bars.top, 0, bars.bottom)
            val padH = MotionSpec.dp(this, 16f).roundToInt()
            binding.overlaySpotlight.spotlightSheet.setPadding(
                padH, bars.top + MotionSpec.dp(this, 12f).roundToInt(), padH, bars.bottom
            )
            binding.pageSettings.root.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun loadPreferences() {
        iconShape = prefs.getString(PREF_ICON_SHAPE, "squircle") ?: "squircle"
        iconCache.setShape(iconShape)
        showLabels = prefs.getBoolean(PREF_SHOW_LABELS, true)
        showSearchPill = prefs.getBoolean(PREF_SHOW_SEARCH_PILL, true)
        showTodayPage = prefs.getBoolean(PREF_SHOW_TODAY, true)
        showLibraryPage = prefs.getBoolean(PREF_SHOW_LIBRARY, true)
        gridColumns = prefs.getInt(PREF_GRID_COLUMNS, 4).coerceIn(4, 6)
        dockCapacity = prefs.getInt(PREF_DOCK_CAPACITY, 4).coerceIn(4, 6)
        wallpaperDimPct = prefs.getInt(PREF_WALLPAPER_DIM, 0).coerceIn(0, 40)
        notifBadgesEnabled = prefs.getBoolean(PREF_NOTIF_BADGES, true)
        newDotEnabled = prefs.getBoolean(PREF_NEW_DOT, true)
        spotlightSuggestionsEnabled = prefs.getBoolean(PREF_SPOTLIGHT_SUGGESTIONS, true)
        spotlightSettingsEnabled = prefs.getBoolean(PREF_SPOTLIGHT_SETTINGS, true)
        gestureDoubleTapLock = prefs.getBoolean(PREF_GESTURE_LOCK, true)
        gesturePinchOverview = prefs.getBoolean(PREF_GESTURE_PINCH, true)
        gestureSwipeSpotlight = prefs.getBoolean(PREF_GESTURE_SWIPE_SPOTLIGHT, true)
    }

    private fun getPageCapacity() = gridColumns * 6

    private fun initSystemSettingsList() {
        systemSettings.clear()
        systemSettings.add(SettingItem(getString(R.string.settings), "Kayumars iOS Preferences", "kayumars://settings", R.drawable.ic_glyph_settings))
        systemSettings.add(SettingItem("Wi-Fi & Internet", "Wireless & cellular settings", Settings.ACTION_WIFI_SETTINGS, R.drawable.ic_cat_internet))
        systemSettings.add(SettingItem("Bluetooth", "Connected audio & devices", Settings.ACTION_BLUETOOTH_SETTINGS, R.drawable.ic_cat_comm))
        systemSettings.add(SettingItem("Display & Brightness", "Theme, font & screen", Settings.ACTION_DISPLAY_SETTINGS, R.drawable.ic_cat_media))
        systemSettings.add(SettingItem("Notifications", "App alerts & listener access", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, R.drawable.ic_glyph_bell))
        systemSettings.add(SettingItem("Accessibility", "Double-tap lock & motion", Settings.ACTION_ACCESSIBILITY_SETTINGS, R.drawable.ic_visibility_off))
        systemSettings.add(SettingItem("Default Home App", "RoleManager home selection", Settings.ACTION_HOME_SETTINGS, R.drawable.ic_home_badge))
    }

    private fun initAdapters() {
        folderAdapter = SimpleCellAdapter(folderVisibleApps, false)
        binding.cardFolder.folderAppsGrid.adapter = folderAdapter

        spotlightSuggestionsAdapter = SimpleCellAdapter(spotlightSuggestionApps, false)
        binding.overlaySpotlight.spotlightSuggestionsGrid.adapter = spotlightSuggestionsAdapter

        spotlightGridAdapter = SimpleCellAdapter(spotlightGridApps, false)
        binding.overlaySpotlight.spotlightAppsGrid.adapter = spotlightGridAdapter

        spotlightResultsAdapter = SpotlightResultsAdapter()
        binding.overlaySpotlight.spotlightResultsList.adapter = spotlightResultsAdapter
    }

    private fun initMotionInfra() {
        motionDebug = MotionDebug(binding.motionDebugHud).apply {
            if (prefs.getBoolean(PREF_MOTION_DEBUG, false)) setEnabled(true)
        }

        backInvokedMotion = BackInvokedMotion(
            activity = this,
            surfaceProvider = {
                when {
                    isSettingsOpen -> binding.pageSettings.root
                    isContextMenuOpen -> binding.menuContext.menuContextCard
                    isFolderOpen -> binding.cardFolder.folderModalCard
                    isWidgetPickerOpen -> binding.sheetWidgetPicker.root
                    isSpotlightOpen -> binding.overlaySpotlight.spotlightSheet
                    isOverviewOpen -> binding.overlayPageOverview.root
                    else -> binding.homePager
                }
            },
            hasActiveOverlay = { hasAnyDismissibleBackTarget() },
            onCommitBack = { handlePredictiveBackStep() }
        ).apply { register() }

        pinchDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!gesturePinchOverview) return false
                val f = detector.scaleFactor
                if (f < 0.84f && !isOverviewOpen && !isSpotlightOpen && !isSettingsOpen && !isFolderOpen) {
                    openPageOverview()
                    return true
                } else if (f > 1.15f && isOverviewOpen) {
                    closePageOverview()
                    return true
                }
                return false
            }
        })
    }

    private fun setupListeners() {
        binding.btnOpenSearch.setOnClickListener { openSpotlight() }
        binding.btnDoneEdit.setOnClickListener { setEditMode(false) }
        binding.btnAddPage.setOnClickListener {
            layoutState.pages.add(mutableListOf())
            layoutState.save(prefs)
            rebuildVirtualPager(homePageIndexToVirtual(layoutState.pages.lastIndex))
            updateEditModeTopBar()
        }
        binding.btnRemovePage.setOnClickListener {
            val hi = getCurrentHomePageIndex()
            if (hi in layoutState.pages.indices && layoutState.pages.size > 1 && layoutState.pages[hi].isEmpty()) {
                layoutState.pages.removeAt(hi)
                layoutState.save(prefs)
                rebuildVirtualPager(homePageIndexToVirtual(hi.coerceAtMost(layoutState.pages.lastIndex)))
                updateEditModeTopBar()
            }
        }
        binding.contextScrimOverlay.setOnClickListener {
            when {
                isContextMenuOpen -> dismissIosContextMenu()
                isFolderOpen -> closeFolder()
                isOverviewOpen -> closePageOverview()
            }
        }

        // Folder modal listeners
        binding.cardFolder.root.setOnClickListener { closeFolder() }
        binding.cardFolder.folderModalCard.setOnClickListener { /* consume */ }
        binding.cardFolder.folderAppsGrid.setOnItemClickListener { _, view, pos, _ ->
            folderVisibleApps.getOrNull(pos)?.let {
                closeFolder()
                launchAppWithFeedback(view, it.pkg, false)
            }
        }
        binding.cardFolder.folderAppsGrid.setOnItemLongClickListener { _, view, pos, _ ->
            val app = folderVisibleApps.getOrNull(pos) ?: return@setOnItemLongClickListener false
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showAppContextMenu(view, app, onHome = false, onDock = false, insideFolderId = openFolderId)
            true
        }

        // Spotlight listeners
        binding.overlaySpotlight.btnSpotlightCancel.setOnClickListener { closeSpotlight() }
        binding.overlaySpotlight.btnSpotlightClear.setOnClickListener { binding.overlaySpotlight.spotlightInput.setText("") }
        binding.overlaySpotlight.spotlightInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString().orEmpty()
                binding.overlaySpotlight.btnSpotlightClear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                runSpotlightSearch(q)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.overlaySpotlight.spotlightInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val first = spotlightRows.firstOrNull { it.type == ROW_TYPE_TOP_HIT || it.type == ROW_TYPE_APP }
                if (first?.app != null) {
                    launchAppWithFeedback(v, first.app.pkg, true)
                    closeSpotlight()
                    return@setOnEditorActionListener true
                }
            }
            false
        }
        binding.overlaySpotlight.spotlightSuggestionsGrid.setOnItemClickListener { _, view, pos, _ ->
            spotlightSuggestionApps.getOrNull(pos)?.let {
                launchAppWithFeedback(view, it.pkg, true)
                closeSpotlight()
            }
        }
        binding.overlaySpotlight.spotlightAppsGrid.setOnItemClickListener { _, view, pos, _ ->
            spotlightGridApps.getOrNull(pos)?.let {
                launchAppWithFeedback(view, it.pkg, true)
                closeSpotlight()
            }
        }
        binding.overlaySpotlight.spotlightResultsList.setOnItemClickListener { _, view, pos, _ ->
            val row = spotlightRows.getOrNull(pos) ?: return@setOnItemClickListener
            if (row.type == ROW_TYPE_SETTING && row.setting != null) {
                openSystemSettingAction(row.setting.action)
            } else if (row.app != null) {
                launchAppWithFeedback(view, row.app.pkg, true)
                closeSpotlight()
            }
        }

        binding.menuContext.root.setOnClickListener { dismissIosContextMenu() }
        binding.overlayPageOverview.btnOverviewDone.setOnClickListener { closePageOverview() }
        binding.sheetWidgetPicker.root.setOnClickListener { closeWidgetPicker() }
        binding.sheetWidgetPicker.btnWidgetPickerClose.setOnClickListener { closeWidgetPicker() }
        binding.pageSettings.btnSettingsDone.setOnClickListener { closeSettingsPage() }

        setupPagerTouchHandling()
    }

    private fun homePageIndexToVirtual(homeIdx: Int) = (if (showTodayPage) 1 else 0) + homeIdx

    private fun getCurrentHomePageIndex(): Int {
        val spec = virtualPages.getOrNull(currentVirtualIndex) ?: return 0
        return if (spec.type == PageSpec.TYPE_HOME) spec.homePageIndex else -1
    }

    private fun rebuildVirtualPager(targetVirtualIdx: Int) {
        virtualPages.clear()
        clockBindings.clear()
        binding.homePager.removeAllViews()

        if (showTodayPage) {
            val today = PageSpec(PageSpec.TYPE_TODAY, -1).apply { view = buildTodayPageView() }
            virtualPages.add(today)
            binding.homePager.addView(today.view)
        }
        if (layoutState.pages.isEmpty()) layoutState.pages.add(mutableListOf())
        layoutState.pages.indices.forEach { i ->
            val home = PageSpec(PageSpec.TYPE_HOME, i).apply { view = buildHomePageView(i) }
            virtualPages.add(home)
            binding.homePager.addView(home.view)
        }
        if (showLibraryPage) {
            val lib = PageSpec(PageSpec.TYPE_LIBRARY, -1).apply { view = buildLibraryPageView() }
            virtualPages.add(lib)
            binding.homePager.addView(lib.view)
        }

        val fallback = if (showTodayPage) 1 else 0
        currentVirtualIndex = if (targetVirtualIdx in virtualPages.indices) targetVirtualIdx
        else fallback.coerceAtMost((virtualPages.size - 1).coerceAtLeast(0))

        positionPagesAtOffset(0f, false)
        rebuildPageDots()
        rebuildDock()
        updateEditModeTopBar()
        binding.homePager.post { balanceHomeGrids() }
    }

    private fun positionPagesAtOffset(dragDeltaPx: Float, animate: Boolean) {
        val width = if (binding.homePager.width > 0) binding.homePager.width else resources.displayMetrics.widthPixels
        val dirSign = if (binding.homePager.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
        virtualPages.forEachIndexed { idx, spec ->
            val pv = spec.view ?: return@forEachIndexed
            val targetX = ((idx - currentVirtualIndex) * width) * dirSign + dragDeltaPx
            if (animate) {
                SpringPreset.animate(pv, DynamicAnimation.TRANSLATION_X, targetX, SpringPreset.PAGE_SNAP)
            } else {
                SpringPreset.cancelForView(pv)
                pv.translationX = targetX
            }
        }
        applyWallpaperParallax(dragDeltaPx, width)
        updateDockAndPillForPage()
    }

    private fun snapToVirtualPage(newIdx: Int, velocityPx: Float) {
        if (virtualPages.isEmpty()) return
        currentVirtualIndex = newIdx.coerceIn(0, virtualPages.lastIndex)
        val width = if (binding.homePager.width > 0) binding.homePager.width else resources.displayMetrics.widthPixels
        val dirSign = if (binding.homePager.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
        virtualPages.forEachIndexed { idx, spec ->
            val pv = spec.view ?: return@forEachIndexed
            val targetX = ((idx - currentVirtualIndex) * width) * dirSign.toFloat()
            SpringPreset.animate(
                pv, DynamicAnimation.TRANSLATION_X, targetX, SpringPreset.PAGE_SNAP,
                if (idx == currentVirtualIndex) velocityPx else 0f
            )
        }
        applyWallpaperParallax(0f, width)
        rebuildPageDots()
        updateDockAndPillForPage()
        updateEditModeTopBar()
    }

    private fun applyWallpaperParallax(dragDeltaPx: Float, pageWidth: Int) {
        try {
            if (binding.rootLayout.windowToken == null || virtualPages.size <= 1) return
            val frac = currentVirtualIndex - if (pageWidth > 0) dragDeltaPx / pageWidth else 0f
            val norm = (frac / (virtualPages.size - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            WallpaperManager.getInstance(this).setWallpaperOffsets(binding.rootLayout.windowToken, 0.25f + norm * 0.5f, 0.5f)
        } catch (_: Exception) {}
    }

    private fun updateDockAndPillForPage() {
        val type = virtualPages.getOrNull(currentVirtualIndex)?.type ?: PageSpec.TYPE_HOME
        val onHome = type == PageSpec.TYPE_HOME
        binding.pageDock.root.visibility = if (onHome) View.VISIBLE else View.GONE
        // iOS shows page dots only on the Home Screen, not on Today / App Library.
        binding.pageDotsRow.visibility = if (onHome && virtualPages.size > 1) View.VISIBLE else View.GONE
        binding.btnOpenSearch.visibility = if (onHome && showSearchPill) View.VISIBLE else View.GONE
    }

    private fun rebuildPageDots() {
        binding.pageDotsRow.removeAllViews()
        val dotSize = MotionSpec.dp(this, 6f).roundToInt()
        val margin = MotionSpec.dp(this, 4f).roundToInt()
        virtualPages.indices.forEach { i ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { setMargins(margin, 0, margin, 0) }
                setBackgroundResource(R.drawable.bg_dot_blue)
                val active = i == currentVirtualIndex
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    getColor(if (active) R.color.page_dot_active else R.color.page_dot_inactive)
                )
                scaleX = if (active) 1.25f else 0.9f
                scaleY = if (active) 1.25f else 0.9f
                contentDescription = getString(R.string.page_indicator_desc, i + 1, virtualPages.size)
                setOnClickListener { snapToVirtualPage(i, 0f) }
            }
            binding.pageDotsRow.addView(dot)
        }
    }

    private fun setupPagerTouchHandling() {
        // A child (icon cell / card / button) consumed DOWN, so seed the pager drag state
        // with the original DOWN coordinates when the intercept layout steals the swipe.
        binding.homePager.onHorizontalDragStart = { x, y ->
            touchDownX = x
            touchDownY = y
            isPagerDragging = true
            isSpotlightPullDragging = false
            pagerVelocityTracker?.recycle()
            pagerVelocityTracker = VelocityTracker.obtain()
        }
        binding.homePager.setOnTouchListener { _, ev ->
            pinchDetector?.onTouchEvent(ev)
            if (isDraggingItem) {
                handleDragMoveOrUp(ev)
                return@setOnTouchListener true
            }
            if (isSpotlightOpen || isSettingsOpen || isFolderOpen || isContextMenuOpen) return@setOnTouchListener false

            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = ev.rawX
                    touchDownY = ev.rawY
                    isPagerDragging = false
                    isSpotlightPullDragging = false
                    pagerVelocityTracker?.recycle()
                    pagerVelocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (ev.pointerCount >= 2) return@setOnTouchListener true
                    pagerVelocityTracker?.addMovement(ev)
                    val dx = ev.rawX - touchDownX
                    val dy = ev.rawY - touchDownY
                    if (!isPagerDragging && !isSpotlightPullDragging) {
                        if (abs(dx) > touchSlopPx && abs(dx) > abs(dy) * 1.15f) {
                            isPagerDragging = true
                        } else if (gestureSwipeSpotlight && dy > touchSlopPx * 1.5f &&
                            abs(dy) > abs(dx) * 1.2f &&
                            touchDownY > MotionSpec.dp(this, 52f) && getCurrentHomePageIndex() >= 0
                        ) {
                            isSpotlightPullDragging = true
                            openSpotlight()
                            return@setOnTouchListener true
                        }
                    }
                    if (isPagerDragging) {
                        val rtl = binding.homePager.layoutDirection == View.LAYOUT_DIRECTION_RTL
                        val logicalDx = if (rtl) -dx else dx
                        val resisted = if ((currentVirtualIndex == 0 && logicalDx > 0) ||
                            (currentVirtualIndex == virtualPages.lastIndex && logicalDx < 0)
                        ) dx * 0.28f else dx
                        positionPagesAtOffset(resisted, false)
                        return@setOnTouchListener true
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isPagerDragging) {
                        val dxUp = ev.rawX - touchDownX
                        var vx = 0f
                        pagerVelocityTracker?.let {
                            it.computeCurrentVelocity(1000)
                            vx = it.xVelocity
                        }
                        val rtl = binding.homePager.layoutDirection == View.LAYOUT_DIRECTION_RTL
                        val logicalDx = if (rtl) -dxUp else dxUp
                        val logicalVx = if (rtl) -vx else vx
                        val w = binding.homePager.width.coerceAtLeast(1)
                        var target = currentVirtualIndex
                        if (logicalDx < -w * 0.22f || logicalVx < -650f) target = (currentVirtualIndex + 1).coerceAtMost(virtualPages.lastIndex)
                        else if (logicalDx > w * 0.22f || logicalVx > 650f) target = (currentVirtualIndex - 1).coerceAtLeast(0)
                        snapToVirtualPage(target, vx)
                    } else if (ev.actionMasked == MotionEvent.ACTION_UP) {
                        if (hypot(ev.rawX - touchDownX, ev.rawY - touchDownY) < touchSlopPx && getCurrentHomePageIndex() >= 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastEmptyTapMs < 280L) {
                                lastEmptyTapMs = 0L
                                if (gestureDoubleTapLock && !isEditMode) handleDoubleTapLock()
                            } else lastEmptyTapMs = now
                        }
                    }
                    pagerVelocityTracker?.recycle()
                    pagerVelocityTracker = null
                    isPagerDragging = false
                    isSpotlightPullDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun buildHomePageView(homePageIndex: Int): View {
        val page = LayoutInflater.from(this).inflate(R.layout.page_home, binding.homePager, false)
        val homeWidgetSlot = page.findViewById<FrameLayout>(R.id.home_widget_slot)
        val promptCard = page.findViewById<View>(R.id.home_prompt_card)
        val btnSetHome = page.findViewById<View>(R.id.btn_set_home)
        val btnDismiss = page.findViewById<View>(R.id.btn_dismiss_home_prompt)
        val grid = page.findViewById<GridLayout>(R.id.home_grid_layout)
        val emptyHint = page.findViewById<View>(R.id.home_empty_hint)

        grid.columnCount = gridColumns
        val hw = layoutState.homeWidget
        if (homePageIndex == 0 && hw != null) {
            homeWidgetSlot.visibility = View.VISIBLE
            homeWidgetSlot.removeAllViews()
            homeWidgetSlot.addView(buildWidgetCardView(hw, true))
        } else homeWidgetSlot.visibility = View.GONE

        if (homePageIndex == 0 && shouldShowDefaultHomePrompt()) {
            promptCard.visibility = View.VISIBLE
            btnSetHome.setOnClickListener { requestDefaultHomeRole() }
            btnDismiss.setOnClickListener {
                prefs.edit().putBoolean(PREF_HOME_DISMISSED, true).apply()
                rebuildVirtualPager(currentVirtualIndex)
            }
        }

        val slots = layoutState.pages.getOrNull(homePageIndex) ?: emptyList()
        emptyHint.visibility = if (slots.isEmpty()) View.VISIBLE else View.GONE
        slots.forEachIndexed { idx, slot ->
            val cell = createHomeSlotCellView(slot, homePageIndex, idx, false)
            val glp = GridLayout.LayoutParams().apply {
                width = 0
                height = resources.getDimensionPixelSize(R.dimen.ios_cell_height)
                columnSpec = GridLayout.spec(idx % gridColumns, 1f)
                rowSpec = GridLayout.spec(idx / gridColumns)
            }
            grid.addView(cell, glp)
        }
        page.setOnLongClickListener {
            if (!isEditMode) {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                setEditMode(true)
                true
            } else false
        }
        return page
    }

    /**
     * iOS centres the icon block between the widget area and the dock instead of
     * pinning it to the top of a full-height grid.
     */
    private fun balanceHomeGrids() {
        val cellH = resources.getDimensionPixelSize(R.dimen.ios_cell_height)
        val maxPad = MotionSpec.dp(this, 28f).roundToInt()
        virtualPages.forEach { spec ->
            if (spec.type != PageSpec.TYPE_HOME) return@forEach
            val grid = spec.view?.findViewById<GridLayout>(R.id.home_grid_layout) ?: return@forEach
            if (grid.height <= 0 || grid.childCount == 0) return@forEach
            val rows = (grid.childCount + gridColumns - 1) / gridColumns
            val pad = ((grid.height - rows * cellH) / 2).coerceIn(0, maxPad)
            grid.setPadding(0, pad, 0, 0)
        }
    }

    private fun rebuildDock() {
        binding.pageDock.dockRow.removeAllViews()
        val max = min(dockCapacity, layoutState.dock.size)
        for (i in 0 until max) {
            val cell = createHomeSlotCellView(IosLayoutState.HomeSlot.app(layoutState.dock[i]), -1, i, true)
            cell.findViewById<View>(R.id.label_row)?.visibility = View.GONE
            binding.pageDock.dockRow.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun createHomeSlotCellView(slot: IosLayoutState.HomeSlot, pageIdx: Int, slotPos: Int, isDock: Boolean): View {
        val cell = LayoutInflater.from(this).inflate(R.layout.item_home_cell, null, false)
        val iconView = cell.findViewById<ImageView>(R.id.app_icon)
        val folderPreview = cell.findViewById<GridLayout>(R.id.folder_preview_grid)
        val badgeMinus = cell.findViewById<ImageView>(R.id.badge_minus)
        val badgeNotif = cell.findViewById<TextView>(R.id.badge_notif_count)
        val badgeWork = cell.findViewById<ImageView>(R.id.badge_work_profile)
        val badgeNewDot = cell.findViewById<View>(R.id.badge_new_dot)
        val labelView = cell.findViewById<TextView>(R.id.app_label)
        val labelRow = cell.findViewById<View>(R.id.label_row)

        // Package list for in-place badge refresh (see updateBadgeViews).
        badgeNotif.tag = if (slot.isFolder) slot.apps.toList() else listOf(slot.pkg)
        if (!showLabels && !isDock) labelRow.visibility = View.GONE

        if (slot.isFolder) {
            iconView.visibility = View.GONE
            folderPreview.visibility = View.VISIBLE
            labelView.text = slot.folderTitle
            populateFolderPreviewGrid(folderPreview, slot.apps)
            val totalNotif = slot.apps.sumOf {
                KayumarsNotificationListener.getBadgeCount(it, notifBadgesEnabled, layoutState.mutedBadgePkgs)
            }
            if (totalNotif > 0 && !isEditMode) {
                badgeNotif.visibility = View.VISIBLE
                badgeNotif.text = if (totalNotif > 99) "99+" else totalNotif.toString()
            }
        } else {
            iconView.visibility = View.VISIBLE
            folderPreview.visibility = View.GONE
            labelView.text = appByPkg[slot.pkg]?.label ?: slot.pkg
            loadAppIconAsync(slot.pkg, iconView)
            val cnt = KayumarsNotificationListener.getBadgeCount(slot.pkg, notifBadgesEnabled, layoutState.mutedBadgePkgs)
            if (cnt > 0 && !isEditMode) {
                badgeNotif.visibility = View.VISIBLE
                badgeNotif.text = if (cnt > 99) "99+" else cnt.toString()
            }
            if (workProfilePackages.contains(slot.pkg)) badgeWork.visibility = View.VISIBLE
            if (newDotEnabled && layoutState.newBadgePkgs.contains(slot.pkg)) badgeNewDot.visibility = View.VISIBLE
        }

        if (isEditMode) {
            badgeMinus.visibility = View.VISIBLE
            badgeNotif.visibility = View.GONE
            startCellJiggle(cell, slotPos + if (isDock) 20 else pageIdx * 7)
            badgeMinus.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                when {
                    slot.isFolder -> {
                        layoutState.pages.getOrNull(pageIdx)?.remove(slot)
                        if (layoutState.pages.isEmpty()) layoutState.pages.add(mutableListOf())
                    }
                    isDock -> layoutState.dock.remove(slot.pkg)
                    else -> layoutState.removeAppFromHome(slot.pkg)
                }
                layoutState.save(prefs)
                rebuildVirtualPager(currentVirtualIndex)
            }
        }

        cell.setOnClickListener {
            if (slot.isFolder) {
                openFolderModal(slot.folderId)
            } else if (!isEditMode) {
                layoutState.newBadgePkgs.remove(slot.pkg)
                layoutState.save(prefs)
                launchAppWithFeedback(it, slot.pkg, false)
            }
        }
        setupCellTouchDragAndMenu(cell, slot, pageIdx, slotPos, isDock)
        return cell
    }

    private fun populateFolderPreviewGrid(grid: GridLayout, pkgs: List<String>) {
        grid.removeAllViews()
        val miniPx = MotionSpec.dp(this, 14f).roundToInt()
        val marginPx = MotionSpec.dp(this, 1f).roundToInt()
        pkgs.take(9).forEach { pkg ->
            val iv = ImageView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = miniPx; height = miniPx; setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            loadAppIconAsync(pkg, iv)
            grid.addView(iv)
        }
    }

    private fun setupCellTouchDragAndMenu(
        cell: View,
        slot: IosLayoutState.HomeSlot,
        pageIdx: Int,
        slotPos: Int,
        isDock: Boolean
    ) {
        var lpRunner: Runnable? = null
        var movedPastSlop = false
        var longTriggered = false
        var downX = 0f
        var downY = 0f

        cell.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; movedPastSlop = false; longTriggered = false
                    lpRunner?.let { uiHandler.removeCallbacks(it) }
                    lpRunner = Runnable {
                        if (movedPastSlop) return@Runnable
                        longTriggered = true
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        if (isEditMode) {
                            beginCellDrag(slot, pageIdx, slotPos, isDock, downX, downY)
                        } else if (!slot.isFolder) {
                            appByPkg[slot.pkg]?.let { showAppContextMenu(v, it, true, isDock, null) }
                        } else setEditMode(true)
                    }
                    uiHandler.postDelayed(lpRunner!!, 350L)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingItem) { handleDragMoveOrUp(ev); return@setOnTouchListener true }
                    if (hypot(ev.rawX - downX, ev.rawY - downY) > touchSlopPx) {
                        movedPastSlop = true
                        lpRunner?.let { uiHandler.removeCallbacks(it) }
                        if (longTriggered && !isEditMode) {
                            dismissIosContextMenu()
                            beginCellDrag(slot, pageIdx, slotPos, isDock, ev.rawX, ev.rawY)
                            handleDragMoveOrUp(ev)
                            return@setOnTouchListener true
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    lpRunner?.let { uiHandler.removeCallbacks(it) }
                    if (isDraggingItem) { handleDragMoveOrUp(ev); return@setOnTouchListener true }
                    if (longTriggered) return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun beginCellDrag(
        slot: IosLayoutState.HomeSlot,
        pageIdx: Int,
        slotPos: Int,
        isDock: Boolean,
        rawX: Float,
        rawY: Float
    ) {
        isDraggingItem = true; dragFromDock = isDock; dragSourcePageIdx = pageIdx
        dragSourceSlotPos = slotPos; dragIsFolder = slot.isFolder
        dragAppPkg = if (slot.isFolder) null else slot.pkg
        dragHoverSlotPos = -1; dragHoverDock = false
        if (!isEditMode) setEditMode(true)

        val previewPkg = if (slot.isFolder) slot.apps.firstOrNull() else slot.pkg
        if (previewPkg != null) loadAppIconAsync(previewPkg, binding.dragSnapshotIcon)

        binding.dragSnapshotView.visibility = View.VISIBLE
        binding.dragSnapshotView.scaleX = 1.12f
        binding.dragSnapshotView.scaleY = 1.12f
        positionDragSnapshot(rawX, rawY)
    }

    private fun positionDragSnapshot(rawX: Float, rawY: Float) {
        val loc = IntArray(2)
        binding.rootLayout.getLocationOnScreen(loc)
        binding.dragSnapshotView.translationX = rawX - loc[0] - binding.dragSnapshotView.width * 0.5f
        binding.dragSnapshotView.translationY = rawY - loc[1] - binding.dragSnapshotView.height * 0.5f
    }

    private fun handleDragMoveOrUp(ev: MotionEvent) {
        if (!isDraggingItem) return
        val rawX = ev.rawX; val rawY = ev.rawY
        if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
            positionDragSnapshot(rawX, rawY)
            evaluateDragTargets(rawX, rawY)
            return
        }
        uiHandler.removeCallbacks(folderMergeTrigger)
        uiHandler.removeCallbacks(edgePageSwitchRunnable)
        binding.dragDropRing.visibility = View.GONE
        binding.dragSnapshotView.visibility = View.GONE

        val curHome = getCurrentHomePageIndex().coerceAtLeast(0)
        val pkg = dragAppPkg
        when {
            dragHoverDock && !dragIsFolder && pkg != null -> if (!dragFromDock) {
                layoutState.removeAppFromHome(pkg)
                layoutState.addToDock(pkg, dockCapacity)
            }
            dragHoverSlotPos >= 0 -> when {
                dragFromDock && pkg != null -> {
                    layoutState.dock.remove(pkg)
                    val p = layoutState.pages[curHome]
                    p.add(dragHoverSlotPos.coerceAtMost(p.size), IosLayoutState.HomeSlot.app(pkg))
                }
                !dragIsFolder && dragSourcePageIdx == curHome && dragHoverSlotPos != dragSourceSlotPos &&
                    isHoverDirectlyOverIcon(curHome, dragHoverSlotPos, rawX, rawY) -> {
                    layoutState.mergeSlotsIntoFolder(curHome, dragSourceSlotPos, dragHoverSlotPos, getString(R.string.folder_default_name))
                }
                dragSourcePageIdx == curHome -> layoutState.reorderSlot(curHome, dragSourceSlotPos, dragHoverSlotPos)
                dragSourcePageIdx >= 0 -> layoutState.moveSlotToPage(dragSourcePageIdx, dragSourceSlotPos, curHome, getPageCapacity())
            }
            dragFromDock && pkg != null -> {
                layoutState.dock.remove(pkg)
                layoutState.pages[curHome].add(IosLayoutState.HomeSlot.app(pkg))
            }
        }
        layoutState.save(prefs)
        isDraggingItem = false
        rebuildVirtualPager(currentVirtualIndex)
    }

    private fun evaluateDragTargets(rawX: Float, rawY: Float) {
        val dockLoc = IntArray(2)
        binding.pageDock.root.getLocationOnScreen(dockLoc)
        val overDock = rawY >= dockLoc[1] - MotionSpec.dp(this, 12f) && rawY <= dockLoc[1] + binding.pageDock.root.height
        dragHoverDock = overDock
        if (overDock) {
            uiHandler.removeCallbacks(folderMergeTrigger)
            binding.dragDropRing.visibility = View.GONE
            dragHoverSlotPos = -1
            return
        }

        val screenW = resources.displayMetrics.widthPixels
        val edgeMargin = MotionSpec.dp(this, 28f).roundToInt()
        val newEdge = if (rawX < edgeMargin) -1 else if (rawX > screenW - edgeMargin) 1 else 0
        if (newEdge != edgeSwitchDir) {
            edgeSwitchDir = newEdge
            uiHandler.removeCallbacks(edgePageSwitchRunnable)
            if (edgeSwitchDir != 0) uiHandler.postDelayed(edgePageSwitchRunnable, 500L)
        }

        val grid = getHomeGridView(currentVirtualIndex) ?: return
        var found = -1
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            val cl = IntArray(2)
            child.getLocationOnScreen(cl)
            if (rawX >= cl[0] && rawX <= cl[0] + child.width && rawY >= cl[1] && rawY <= cl[1] + child.height) {
                found = i; break
            }
        }
        if (found != dragHoverSlotPos) {
            dragHoverSlotPos = found
            uiHandler.removeCallbacks(folderMergeTrigger)
            binding.dragDropRing.visibility = View.GONE
            if (!dragIsFolder && found >= 0 && found != dragSourceSlotPos) {
                uiHandler.postDelayed(folderMergeTrigger, 150L)
            }
        }
    }

    private fun isHoverDirectlyOverIcon(pageIdx: Int, slotPos: Int, rawX: Float, rawY: Float): Boolean {
        val grid = getHomeGridView(homePageIndexToVirtual(pageIdx)) ?: return false
        if (slotPos !in 0 until grid.childCount) return false
        val child = grid.getChildAt(slotPos)
        val cl = IntArray(2)
        child.getLocationOnScreen(cl)
        return hypot(rawX - (cl[0] + child.width * 0.5f), rawY - (cl[1] + MotionSpec.dp(this, 34f))) < MotionSpec.dp(this, 26f)
    }

    private fun showMergeRingAtHomeSlot(pageIdx: Int, slotPos: Int) {
        val grid = getHomeGridView(homePageIndexToVirtual(pageIdx)) ?: return
        if (slotPos !in 0 until grid.childCount) return
        val iconFrame = grid.getChildAt(slotPos).findViewById<View>(R.id.icon_frame) ?: return
        val il = IntArray(2); val rl = IntArray(2)
        iconFrame.getLocationOnScreen(il)
        binding.rootLayout.getLocationOnScreen(rl)
        binding.dragDropRing.visibility = View.VISIBLE
        binding.dragDropRing.translationX = il[0] - rl[0] - MotionSpec.dp(this, 4f)
        binding.dragDropRing.translationY = il[1] - rl[1] - MotionSpec.dp(this, 4f)
    }

    private fun getHomeGridView(virtualIdx: Int): GridLayout? {
        val spec = virtualPages.getOrNull(virtualIdx) ?: return null
        if (spec.type != PageSpec.TYPE_HOME) return null
        return spec.view?.findViewById(R.id.home_grid_layout)
    }

    private fun setEditMode(enabled: Boolean) {
        if (isEditMode == enabled) return
        isEditMode = enabled
        // Cell drag gestures need horizontal moves; never intercept them in edit mode.
        binding.homePager.interceptEnabled = !enabled
        binding.editModeBar.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) cancelJiggleAnimations()
        rebuildVirtualPager(currentVirtualIndex)
    }

    private fun updateEditModeTopBar() {
        if (!isEditMode) { binding.editModeBar.visibility = View.GONE; return }
        binding.editModeBar.visibility = View.VISIBLE
        val hi = getCurrentHomePageIndex()
        val showRemove = hi in layoutState.pages.indices && layoutState.pages.size > 1 && layoutState.pages[hi].isEmpty()
        binding.btnRemovePage.visibility = if (showRemove) View.VISIBLE else View.GONE
    }

    private fun startCellJiggle(cell: View, index: Int) {
        if (MotionSpec.isReducedMotion(this)) return
        cell.pivotX = if (cell.width > 0) cell.width * 0.5f else MotionSpec.dp(this, 36f)
        cell.pivotY = if (cell.height > 0) cell.height * 0.5f else MotionSpec.dp(this, 40f)
        val anim = ObjectAnimator.ofFloat(cell, View.ROTATION, -MotionSpec.JIGGLE_ANGLE_DEG, MotionSpec.JIGGLE_ANGLE_DEG).apply {
            duration = MotionSpec.JIGGLE_PERIOD_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            startDelay = (index % 12) * MotionSpec.JIGGLE_STAGGER_MS
            start()
        }
        jiggleAnimators.add(anim)
    }

    private fun cancelJiggleAnimations() {
        jiggleAnimators.forEach { it.cancel() }
        jiggleAnimators.clear()
    }

    private fun openFolderModal(folderId: String) {
        val folder = layoutState.findFolderById(folderId) ?: return
        isFolderOpen = true; openFolderId = folderId; openFolderPage = 0
        binding.cardFolder.folderTitleInput.setText(folder.folderTitle)
        refreshOpenFolderGrid()

        binding.contextScrimOverlay.visibility = View.VISIBLE
        binding.contextScrimOverlay.alpha = 0f
        binding.contextScrimOverlay.animate().alpha(1f).setDuration(MotionSpec.DURATION_FAST_MS).start()

        binding.cardFolder.root.visibility = View.VISIBLE
        binding.cardFolder.folderModalCard.scaleX = 0.45f
        binding.cardFolder.folderModalCard.scaleY = 0.45f
        binding.cardFolder.folderModalCard.alpha = 0f
        SpringPreset.animateScale(binding.cardFolder.folderModalCard, 1f, SpringPreset.FOLDER_SPRING)
        binding.cardFolder.folderModalCard.animate().alpha(1f).setDuration(MotionSpec.DURATION_FAST_MS).start()
        refreshBackdrop()
    }

    private fun refreshOpenFolderGrid() {
        val folder = layoutState.findFolderById(openFolderId)
        if (folder == null) { closeFolder(); return }
        folderVisibleApps.clear()
        val totalPages = ((folder.apps.size + 8) / 9).coerceAtLeast(1)
        if (openFolderPage >= totalPages) openFolderPage = totalPages - 1
        val start = openFolderPage * 9
        val end = min(folder.apps.size, start + 9)
        for (i in start until end) appByPkg[folder.apps[i]]?.let { folderVisibleApps.add(it) }
        folderAdapter.notifyDataSetChanged()
        val dots = binding.cardFolder.folderPageDots
        dots.removeAllViews()
        if (totalPages > 1) {
            binding.cardFolder.folderPagerBar.visibility = View.VISIBLE
            val dotSize = MotionSpec.dp(this, 6f).roundToInt()
            val margin = MotionSpec.dp(this, 4f).roundToInt()
            for (p in 0 until totalPages) {
                val active = p == openFolderPage
                dots.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { setMargins(margin, 0, margin, 0) }
                    setBackgroundResource(R.drawable.bg_dot_blue)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(
                        getColor(if (active) R.color.page_dot_active else R.color.page_dot_inactive)
                    )
                    scaleX = if (active) 1.25f else 0.9f
                    scaleY = if (active) 1.25f else 0.9f
                    contentDescription = getString(R.string.page_indicator_desc, p + 1, totalPages)
                    setOnClickListener { openFolderPage = p; refreshOpenFolderGrid() }
                })
            }
        } else {
            binding.cardFolder.folderPagerBar.visibility = View.GONE
        }
    }

    private fun closeFolder() {
        if (!isFolderOpen) return
        isFolderOpen = false; openFolderId = null
        SpringPreset.animateScale(binding.cardFolder.folderModalCard, 0.75f, SpringPreset.SNAP) {
            binding.cardFolder.root.visibility = View.GONE
        }
        binding.contextScrimOverlay.animate().alpha(0f).setDuration(MotionSpec.DURATION_INSTANT_MS)
            .withEndAction { if (!isContextMenuOpen && !isOverviewOpen) binding.contextScrimOverlay.visibility = View.GONE }
            .start()
        refreshBackdrop()
        rebuildVirtualPager(currentVirtualIndex)
    }

    private fun showAppContextMenu(
        anchorView: View,
        app: AppEntry,
        onHome: Boolean,
        onDock: Boolean,
        insideFolderId: String?
    ) {
        isContextMenuOpen = true
        liftedContextAnchor = anchorView
        SpringPreset.animateScale(anchorView, 1.06f, SpringPreset.MENU_SPRING)
        anchorView.elevation = MotionSpec.dp(this, 6f)

        binding.contextScrimOverlay.visibility = View.VISIBLE
        binding.contextScrimOverlay.alpha = 0f
        binding.contextScrimOverlay.animate().alpha(1f).setDuration(MotionSpec.DURATION_FAST_MS).start()

        binding.menuContext.menuAppTitle.text = app.label
        binding.menuContext.menuShortcutsContainer.removeAllViews()
        binding.menuContext.menuActionsContainer.removeAllViews()

        AppMetadataHelper.queryShortcuts(this, app.pkg, 4).forEach { sc ->
            addContextMenuRow(binding.menuContext.menuShortcutsContainer, sc.label, sc.icon, 0, false) {
                dismissIosContextMenu()
                if (AppMetadataHelper.launchShortcut(this, sc)) Core.logLaunch(app.pkg, System.currentTimeMillis())
                else launchAppWithFeedback(anchorView, app.pkg, false)
            }
        }
        addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.open_app), null, R.drawable.ic_glyph_open, false) {
            dismissIosContextMenu(); launchAppWithFeedback(anchorView, app.pkg, false)
        }
        addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.app_info), null, R.drawable.ic_glyph_info, false) {
            dismissIosContextMenu(); openAppInfo(app.pkg)
        }
        addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.share_app), null, R.drawable.ic_glyph_share, false) {
            dismissIosContextMenu(); shareAppDetails(app)
        }
        when {
            insideFolderId != null -> addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.remove_from_home), null, R.drawable.ic_glyph_minus, false) {
                layoutState.removeFromFolder(insideFolderId, app.pkg); layoutState.save(prefs)
                dismissIosContextMenu(); refreshOpenFolderGrid(); rebuildVirtualPager(currentVirtualIndex)
            }
            onHome -> {
                addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.edit_home_screen), null, R.drawable.ic_glyph_edit, false) {
                    dismissIosContextMenu(); setEditMode(true)
                }
                addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.remove_from_home), null, R.drawable.ic_glyph_minus, false) {
                    if (onDock) layoutState.dock.remove(app.pkg) else layoutState.removeAppFromHome(app.pkg)
                    layoutState.save(prefs); dismissIosContextMenu(); rebuildVirtualPager(currentVirtualIndex)
                }
            }
            else -> addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.add_to_home), null, R.drawable.ic_glyph_plus, false) {
                val pg = layoutState.placeOnFirstFreePage(app.pkg, getPageCapacity(), false)
                layoutState.save(prefs); dismissIosContextMenu(); rebuildVirtualPager(homePageIndexToVirtual(pg))
            }
        }
        addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.uninstall_app), null, R.drawable.ic_glyph_delete, true) {
            dismissIosContextMenu(); requestUninstallApp(app.pkg)
        }

        positionMenuNearAnchor(anchorView)
        binding.menuContext.root.visibility = View.VISIBLE
        binding.menuContext.menuContextCard.scaleX = 0.84f
        binding.menuContext.menuContextCard.scaleY = 0.84f
        binding.menuContext.menuContextCard.alpha = 0f
        SpringPreset.animateScale(binding.menuContext.menuContextCard, 1f, SpringPreset.MENU_SPRING)
        binding.menuContext.menuContextCard.animate().alpha(1f).setDuration(MotionSpec.DURATION_INSTANT_MS).start()
        refreshBackdrop()
    }

    private fun positionMenuNearAnchor(anchorView: View) {
        val lp = binding.menuContext.menuContextCard.layoutParams as FrameLayout.LayoutParams
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val menuW = resources.getDimensionPixelSize(R.dimen.ios_menu_width)
        val margin = MotionSpec.dp(this, 16f).roundToInt()
        val loc = IntArray(2)
        anchorView.getLocationOnScreen(loc)
        // Margins are absolute; pin gravity to TOP|LEFT so RTL START->RIGHT doesn't mirror them.
        lp.gravity = Gravity.TOP or Gravity.LEFT
        lp.leftMargin = loc[0].coerceIn(margin, (screenW - menuW - margin).coerceAtLeast(margin))
        val estH = MotionSpec.dp(this, 320f).roundToInt()
        var y = loc[1] + anchorView.height + MotionSpec.dp(this, 8f).roundToInt()
        if (y + estH > screenH - margin) y = max(margin * 2, loc[1] - estH)
        lp.topMargin = y
        binding.menuContext.menuContextCard.layoutParams = lp
    }

    private fun addContextMenuRow(
        parent: LinearLayout,
        title: String,
        customIcon: Drawable?,
        glyphRes: Int,
        destructive: Boolean,
        onClick: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = resources.getDimensionPixelSize(R.dimen.ios_menu_row_height)
            val padH = MotionSpec.dp(this@MainActivity, 16f).roundToInt()
            setPadding(padH, 6, padH, 6)
            setOnClickListener { onClick() }
        }
        val tv = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(getColor(if (destructive) R.color.system_red else R.color.label_primary))
        }
        row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (customIcon != null || glyphRes != 0) {
            val sz = MotionSpec.dp(this, 18f).roundToInt()
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(sz, sz)
                if (customIcon != null) setImageDrawable(customIcon)
                else {
                    setImageResource(glyphRes)
                    setColorFilter(getColor(if (destructive) R.color.system_red else R.color.label_primary))
                }
            }
            row.addView(iv)
        }
        parent.addView(row)
        parent.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.ios_hairline).coerceAtLeast(1)
            )
            setBackgroundColor(getColor(R.color.separator))
        })
    }

    private fun dismissIosContextMenu() {
        if (!isContextMenuOpen) return
        isContextMenuOpen = false
        liftedContextAnchor?.let {
            SpringPreset.animateScale(it, 1f, SpringPreset.SNAP)
            it.elevation = 0f
        }
        liftedContextAnchor = null
        binding.menuContext.root.visibility = View.GONE
        if (!isFolderOpen && !isOverviewOpen) binding.contextScrimOverlay.visibility = View.GONE
        refreshBackdrop()
    }

    /**
     * The workspace blurs once for every overlay on top of it; the scrim only
     * dims when there is no blur to separate the layers (snapshot fallback
     * keeps working, the blur itself is the separation).
     */
    private fun refreshBackdrop() {
        val anyOverlay = isSpotlightOpen || isFolderOpen || isContextMenuOpen ||
            isOverviewOpen || isWidgetPickerOpen
        if (anyOverlay) backdropBlur.show() else backdropBlur.hide()
        binding.contextScrimOverlay.setBackgroundColor(
            if (anyOverlay) Color.TRANSPARENT else getColor(R.color.scrim_dim_60)
        )
    }

    private fun openSpotlight() {
        if (isSpotlightOpen) return
        isSpotlightOpen = true
        resetSurfaceTransform(binding.overlaySpotlight.spotlightSheet)
        binding.overlaySpotlight.spotlightInput.setText("")
        binding.overlaySpotlight.root.visibility = View.VISIBLE
        binding.overlaySpotlight.root.alpha = 0f
        binding.overlaySpotlight.spotlightSheet.translationY = -MotionSpec.dp(this, 36f)
        binding.overlaySpotlight.root.animate().alpha(1f).setDuration(MotionSpec.DURATION_FAST_MS).start()
        SpringPreset.animate(binding.overlaySpotlight.spotlightSheet, DynamicAnimation.TRANSLATION_Y, 0f, SpringPreset.SPOTLIGHT_GLIDE)
        runSpotlightSearch("")
        binding.overlaySpotlight.spotlightInput.requestFocus()
        binding.overlaySpotlight.spotlightInput.postDelayed({
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(binding.overlaySpotlight.spotlightInput, InputMethodManager.SHOW_IMPLICIT)
        }, 80L)
        setSystemBarsLight(false) // Spotlight sheet is dark in both themes.
        refreshBackdrop()
    }

    private fun closeSpotlight() {
        if (!isSpotlightOpen) return
        isSpotlightOpen = false
        hideKeyboard(binding.overlaySpotlight.spotlightInput)
        binding.overlaySpotlight.root.animate().alpha(0f).setDuration(MotionSpec.DURATION_FAST_MS)
            .withEndAction { binding.overlaySpotlight.root.visibility = View.GONE; configureSystemBars(); refreshBackdrop() }.start()
    }

    private fun runSpotlightSearch(query: String) {
        val trimmed = query.trim()
        spotlightRows.clear(); spotlightGridApps.clear(); spotlightSuggestionApps.clear()

        if (trimmed.isEmpty()) {
            binding.overlaySpotlight.spotlightAppsGridBlock.visibility = View.GONE
            binding.overlaySpotlight.spotlightEmptyState.visibility = View.GONE
            if (spotlightSuggestionsEnabled) {
                val tops = fetchTopAppsFromCoreOrFallback(8)
                spotlightSuggestionApps.addAll(tops)
                spotlightSuggestionsAdapter.notifyDataSetChanged()
                if (tops.isNotEmpty()) fitSpotlightGrid(binding.overlaySpotlight.spotlightSuggestionsGrid)
                binding.overlaySpotlight.spotlightSuggestionsBlock.visibility = if (tops.isEmpty()) View.GONE else View.VISIBLE
            } else binding.overlaySpotlight.spotlightSuggestionsBlock.visibility = View.GONE
            if (spotlightSettingsEnabled) {
                spotlightRows.add(SpotlightRow(ROW_TYPE_HEADER, getString(R.string.spotlight_settings)))
                systemSettings.take(4).forEach { spotlightRows.add(SpotlightRow(ROW_TYPE_SETTING, setting = it)) }
            }
            spotlightResultsAdapter.notifyDataSetChanged()
            return
        }

        binding.overlaySpotlight.spotlightSuggestionsBlock.visibility = View.GONE
        val matchedApps = queryAppsThroughCore(trimmed, 24)
        val matchedSettings = if (spotlightSettingsEnabled) {
            val lq = trimmed.lowercase(Locale.ROOT)
            systemSettings.filter { it.title.lowercase(Locale.ROOT).contains(lq) || it.subtitle.lowercase(Locale.ROOT).contains(lq) }
        } else emptyList()

        if (matchedApps.isEmpty() && matchedSettings.isEmpty()) {
            binding.overlaySpotlight.spotlightAppsGridBlock.visibility = View.GONE
            binding.overlaySpotlight.spotlightResultsList.visibility = View.GONE
            binding.overlaySpotlight.spotlightEmptyState.visibility = View.VISIBLE
            binding.overlaySpotlight.tvSpotlightEmpty.text = getString(R.string.no_results, trimmed)
            return
        }

        binding.overlaySpotlight.spotlightEmptyState.visibility = View.GONE
        binding.overlaySpotlight.spotlightResultsList.visibility = View.VISIBLE
        if (matchedApps.isNotEmpty()) {
            spotlightRows.add(SpotlightRow(ROW_TYPE_HEADER, getString(R.string.spotlight_top_hit)))
            spotlightRows.add(SpotlightRow(ROW_TYPE_TOP_HIT, app = matchedApps.first()))
        }
        if (matchedApps.size > 6) {
            spotlightGridApps.addAll(matchedApps.drop(1).take(8))
            spotlightGridAdapter.notifyDataSetChanged()
            fitSpotlightGrid(binding.overlaySpotlight.spotlightAppsGrid)
            binding.overlaySpotlight.spotlightAppsGridBlock.visibility = View.VISIBLE
        } else {
            binding.overlaySpotlight.spotlightAppsGridBlock.visibility = View.GONE
            if (matchedApps.size > 1) {
                spotlightRows.add(SpotlightRow(ROW_TYPE_HEADER, getString(R.string.spotlight_applications)))
                matchedApps.drop(1).forEach { spotlightRows.add(SpotlightRow(ROW_TYPE_APP, app = it)) }
            }
        }
        if (matchedSettings.isNotEmpty()) {
            spotlightRows.add(SpotlightRow(ROW_TYPE_HEADER, getString(R.string.spotlight_settings)))
            matchedSettings.forEach { spotlightRows.add(SpotlightRow(ROW_TYPE_SETTING, setting = it)) }
        }
        spotlightResultsAdapter.notifyDataSetChanged()
    }

    /**
     * GridView's wrap_content only measures one row; reserve the full row height
     * so a second suggestion row never clips its labels.
     */
    private fun fitSpotlightGrid(grid: GridView) {
        val rows = ((grid.adapter?.count ?: 0) + 3) / 4
        if (rows <= 0) return
        val rowH = MotionSpec.dp(this, 86f).toInt()
        grid.layoutParams = grid.layoutParams.apply {
            height = rows * rowH + grid.paddingTop + grid.paddingBottom +
                grid.verticalSpacing * (rows - 1)
        }
    }

    private fun fetchTopAppsFromCoreOrFallback(maxCount: Int): List<AppEntry> {
        val coreRes = Core.topApps(maxCount).mapNotNull { appByPkg[it.pkg] }
            .filterNot { layoutState.hiddenLibraryPkgs.contains(it.pkg) }
        if (coreRes.isNotEmpty()) return coreRes.take(maxCount)
        return allApps.filterNot { layoutState.hiddenLibraryPkgs.contains(it.pkg) }.take(maxCount)
    }

    private fun queryAppsThroughCore(query: String, maxCount: Int): List<AppEntry> {
        val coreRes = Core.search(query, maxCount).mapNotNull { appByPkg[it.pkg] }
            .filterNot { layoutState.hiddenLibraryPkgs.contains(it.pkg) }
        if (coreRes.isNotEmpty()) return coreRes.take(maxCount)
        val lq = query.lowercase(Locale.ROOT)
        return allApps.filterNot { layoutState.hiddenLibraryPkgs.contains(it.pkg) }
            .filter { it.label.lowercase(Locale.ROOT).contains(lq) || it.pkg.lowercase(Locale.ROOT).contains(lq) }
            .take(maxCount)
    }

    private fun buildLibraryPageView(): View {
        val page = LayoutInflater.from(this).inflate(R.layout.page_library, binding.homePager, false)
        val libSearch = page.findViewById<EditText>(R.id.library_search_input)
        val libClear = page.findViewById<View>(R.id.library_search_clear)
        val categoriesScroll = page.findViewById<View>(R.id.library_categories_scroll)
        val categoriesGrid = page.findViewById<GridLayout>(R.id.library_categories_grid)
        val expandedContainer = page.findViewById<View>(R.id.library_expanded_container)
        val expandedTitle = page.findViewById<TextView>(R.id.library_expanded_title)
        val btnBack = page.findViewById<View>(R.id.btn_library_back)
        val expandedGrid = page.findViewById<GridView>(R.id.library_expanded_grid)

        val adapter = SimpleCellAdapter(expandedLibraryApps, true)
        expandedGrid.adapter = adapter
        expandedGrid.setOnItemClickListener { _, view, pos, _ ->
            expandedLibraryApps.getOrNull(pos)?.let { launchAppWithFeedback(view, it.pkg, false) }
        }
        expandedGrid.setOnItemLongClickListener { _, view, pos, _ ->
            val app = expandedLibraryApps.getOrNull(pos) ?: return@setOnItemLongClickListener false
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showAppContextMenu(view, app, onHome = false, onDock = false, insideFolderId = null)
            true
        }

        // Rust core classifies the whole installed set; missing entries land in OTHER.
        val categoryByPkg = AppMetadataHelper.classifyApps(allApps)
        val buckets = mutableMapOf<String, MutableList<AppEntry>>()
        AppMetadataHelper.LibraryCategory.entries.forEach { buckets[it.key] = mutableListOf() }
        buckets["SUGGESTIONS"]?.addAll(fetchTopAppsFromCoreOrFallback(8))
        allApps.filterNot { layoutState.hiddenLibraryPkgs.contains(it.pkg) }.forEach { app ->
            buckets[categoryByPkg[app.pkg] ?: "OTHER"]?.add(app)
        }

        layoutState.ensureDefaultCategoryOrder()
        categoriesGrid.removeAllViews()
        var tileIdx = 0
        layoutState.libraryCategoryOrder.forEach { key ->
            val cat = AppMetadataHelper.LibraryCategory.fromKey(key)
            val catApps = buckets[key] ?: return@forEach
            if (catApps.isEmpty()) return@forEach

            val tile = LayoutInflater.from(this).inflate(R.layout.item_library_tile, categoriesGrid, false)
            val glp = GridLayout.LayoutParams().apply {
                width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(tileIdx % 3, 1f)
                rowSpec = GridLayout.spec(tileIdx / 3)
                val gap = MotionSpec.dp(this@MainActivity, 5f).roundToInt()
                setMargins(gap, gap, gap, gap)
            }
            tileIdx++
            tile.findViewById<TextView>(R.id.lib_tile_label).text = getString(cat.titleRes)
            tile.findViewById<TextView>(R.id.lib_tile_count).text = catApps.size.toString()

            val previewIds = intArrayOf(
                R.id.lib_preview_0, R.id.lib_preview_1, R.id.lib_preview_2, R.id.lib_preview_3
            )
            previewIds.forEachIndexed { j, id ->
                val iv = tile.findViewById<ImageView>(id)
                val target = catApps.getOrNull(j)
                if (target != null) loadAppIconAsync(target.pkg, iv) else iv.visibility = View.INVISIBLE
            }
            tile.setOnClickListener {
                expandedLibraryCategory = key
                expandedLibraryApps.clear(); expandedLibraryApps.addAll(catApps)
                adapter.notifyDataSetChanged()
                expandedTitle.text = getString(cat.titleRes)
                categoriesScroll.visibility = View.GONE
                expandedContainer.visibility = View.VISIBLE
            }
            categoriesGrid.addView(tile, glp)
        }

        btnBack.setOnClickListener {
            expandedLibraryCategory = null
            expandedContainer.visibility = View.GONE
            categoriesScroll.visibility = View.VISIBLE
            libSearch.setText("")
        }
        libClear.setOnClickListener { libSearch.setText("") }
        libSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim().orEmpty()
                libClear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                if (q.isEmpty()) {
                    expandedLibraryCategory = null
                    expandedContainer.visibility = View.GONE
                    categoriesScroll.visibility = View.VISIBLE
                } else {
                    expandedLibraryCategory = "SEARCH"
                    expandedLibraryApps.clear(); expandedLibraryApps.addAll(queryAppsThroughCore(q, 48))
                    adapter.notifyDataSetChanged()
                    expandedTitle.text = getString(R.string.app_library_title)
                    categoriesScroll.visibility = View.GONE
                    expandedContainer.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        return page
    }

    private fun buildTodayPageView(): View {
        val page = LayoutInflater.from(this).inflate(R.layout.page_today, binding.homePager, false)
        val headline = page.findViewById<TextView>(R.id.today_date_headline)
        val headlineFmt = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        headline.text = headlineFmt.format(Date())
        clockBindings.add(headline to headlineFmt)
        val container = page.findViewById<LinearLayout>(R.id.today_widgets_container)
        val emptyHint = page.findViewById<View>(R.id.today_empty_hint)
        emptyHint.visibility = if (layoutState.todayWidgets.isEmpty()) View.VISIBLE else View.GONE
        // The pill chrome is gone (iOS has none); long-press is the add-widget entry.
        emptyHint.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            openWidgetPicker("today")
            true
        }

        val gutter = MotionSpec.dp(this, 8f).roundToInt()
        layoutState.todayWidgets.forEach { w ->
            val card = buildWidgetCardView(w, false)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resolveWidgetHeightPx(w.span))
            lp.bottomMargin = gutter
            container.addView(card, lp)
        }
        return page
    }

    private fun resolveWidgetHeightPx(span: String): Int = when (span) {
        IosLayoutState.SPAN_SMALL -> MotionSpec.dp(this, 96f).roundToInt()
        IosLayoutState.SPAN_LARGE -> MotionSpec.dp(this, 210f).roundToInt()
        else -> MotionSpec.dp(this, 146f).roundToInt()
    }

    private fun buildWidgetCardView(w: IosLayoutState.WidgetSlot, isHomeSlot: Boolean): View {
        val card = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_today_card)
            val pad = MotionSpec.dp(this@MainActivity, 14f).roundToInt()
            setPadding(pad, pad, pad, pad)
        }

        when {
            w.kind == IosLayoutState.WIDGET_APPWIDGET && w.appWidgetId > 0 -> {
                val info = appWidgetManager?.getAppWidgetInfo(w.appWidgetId)
                val hostView = if (info != null) appWidgetHost?.createView(this, w.appWidgetId, info) else null
                if (hostView != null && info != null) {
                    hostView.setAppWidget(w.appWidgetId, info)
                    card.addView(hostView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }
            w.kind == IosLayoutState.WIDGET_CLOCK -> {
                val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateFmt = SimpleDateFormat("EEEE · MMMM d", Locale.getDefault())
                ll.addView(TextView(this).apply {
                    text = timeFmt.format(Date())
                    textSize = 34f
                    setTextColor(getColor(R.color.label_primary))
                    setTypeface(typeface, Typeface.BOLD)
                    clockBindings.add(this to timeFmt)
                })
                ll.addView(TextView(this).apply {
                    text = dateFmt.format(Date())
                    textSize = 14f
                    setTextColor(getColor(R.color.label_secondary))
                    clockBindings.add(this to dateFmt)
                })
                card.addView(ll)
            }
            w.kind == IosLayoutState.WIDGET_SUGGESTIONS -> {
                val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                ll.addView(TextView(this).apply {
                    setText(R.string.widget_suggestions_title)
                    textSize = 13f; setTextColor(getColor(R.color.label_secondary)); setTypeface(typeface, Typeface.BOLD)
                })
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                fetchTopAppsFromCoreOrFallback(4).forEach { a ->
                    val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                    val isz = MotionSpec.dp(this@MainActivity, 44f).roundToInt()
                    val iv = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(isz, isz) }
                    loadAppIconAsync(a.pkg, iv)
                    item.addView(iv)
                    item.addView(TextView(this).apply {
                        text = a.label; maxLines = 1; textSize = 11f; setTextColor(getColor(R.color.label_primary))
                    })
                    item.setOnClickListener { launchAppWithFeedback(it, a.pkg, false) }
                    row.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                ll.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = MotionSpec.dp(this@MainActivity, 10f).roundToInt()
                })
                card.addView(ll)
            }
            else -> {
                val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                ll.addView(TextView(this).apply {
                    setText(R.string.widget_status_title)
                    textSize = 16f; setTextColor(getColor(R.color.label_primary)); setTypeface(typeface, Typeface.BOLD)
                })
                ll.addView(TextView(this).apply {
                    text = "${getString(R.string.widget_status_body)} · ${allApps.size} apps"
                    textSize = 13f; setTextColor(getColor(R.color.label_secondary))
                })
                card.addView(ll)
            }
        }
        card.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showWidgetOptionsDialog(it, w, isHomeSlot)
            true
        }
        return card
    }

    /** iOS rounded context card for widget actions, replacing the stock AlertDialog. */
    private fun showWidgetOptionsDialog(anchorView: View, w: IosLayoutState.WidgetSlot, isHomeSlot: Boolean) {
        if (isContextMenuOpen) dismissIosContextMenu()
        isContextMenuOpen = true
        liftedContextAnchor = anchorView
        SpringPreset.animateScale(anchorView, 1.06f, SpringPreset.MENU_SPRING)
        anchorView.elevation = MotionSpec.dp(this, 6f)

        binding.menuContext.menuAppTitle.text = w.label
        binding.menuContext.menuShortcutsContainer.removeAllViews()
        binding.menuContext.menuActionsContainer.removeAllViews()

        fun choose(span: String) {
            dismissIosContextMenu()
            w.span = span
            layoutState.save(prefs)
            rebuildVirtualPager(currentVirtualIndex)
        }
        addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.widget_resize_small), null, 0, false) {
            choose(IosLayoutState.SPAN_SMALL)
        }
        addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.widget_resize_medium), null, 0, false) {
            choose(IosLayoutState.SPAN_MEDIUM)
        }
        addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.widget_resize_large), null, 0, false) {
            choose(IosLayoutState.SPAN_LARGE)
        }
        addContextMenuRow(binding.menuContext.menuActionsContainer, getString(R.string.remove_from_home), null, R.drawable.ic_glyph_minus, true) {
            dismissIosContextMenu()
            if (w.appWidgetId > 0) try { appWidgetHost?.deleteAppWidgetId(w.appWidgetId) } catch (_: Exception) {}
            if (isHomeSlot) layoutState.homeWidget = null else layoutState.todayWidgets.remove(w)
            layoutState.save(prefs)
            rebuildVirtualPager(currentVirtualIndex)
        }

        binding.contextScrimOverlay.visibility = View.VISIBLE
        positionMenuNearAnchor(anchorView)
        binding.menuContext.root.visibility = View.VISIBLE
        binding.menuContext.menuContextCard.scaleX = 0.84f
        binding.menuContext.menuContextCard.scaleY = 0.84f
        binding.menuContext.menuContextCard.alpha = 0f
        SpringPreset.animateScale(binding.menuContext.menuContextCard, 1f, SpringPreset.MENU_SPRING)
        binding.menuContext.menuContextCard.animate().alpha(1f).setDuration(MotionSpec.DURATION_INSTANT_MS).start()
        refreshBackdrop()
    }

    private fun openWidgetPicker(target: String) {
        widgetPickerTarget = target
        isWidgetPickerOpen = true
        resetSurfaceTransform(binding.sheetWidgetPicker.root)
        binding.sheetWidgetPicker.root.visibility = View.VISIBLE
        binding.sheetWidgetPicker.widgetPickerList.removeAllViews()
        addPickerOptionRow(getString(R.string.widget_clock_title), "Offline Digital Clock") {
            addBuiltInWidget(IosLayoutState.WIDGET_CLOCK, getString(R.string.widget_clock_title))
        }
        addPickerOptionRow(getString(R.string.widget_suggestions_title), "4x Rust Top Apps") {
            addBuiltInWidget(IosLayoutState.WIDGET_SUGGESTIONS, getString(R.string.widget_suggestions_title))
        }
        addPickerOptionRow(getString(R.string.widget_status_title), "Zero-Network Privacy Vault") {
            addBuiltInWidget(IosLayoutState.WIDGET_STATUS, getString(R.string.widget_status_title))
        }
        try {
            appWidgetManager?.installedProviders?.take(20)?.forEach { info ->
                val label = info.loadLabel(packageManager) ?: info.provider.packageName
                addPickerOptionRow(label, "${info.minWidth}x${info.minHeight}dp") { bindAndroidAppWidget(info) }
            }
        } catch (_: Exception) {}
        refreshBackdrop()
    }

    private fun addPickerOptionRow(title: String, sub: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_settings_card)
            val pad = MotionSpec.dp(this@MainActivity, 14f).roundToInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener { closeWidgetPicker(); onClick() }
        }
        row.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(getColor(R.color.label_primary)); setTypeface(typeface, Typeface.BOLD) })
        row.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(getColor(R.color.label_secondary)) })
        binding.sheetWidgetPicker.widgetPickerList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = MotionSpec.dp(this@MainActivity, 8f).roundToInt()
        })
    }

    private fun addBuiltInWidget(kind: String, label: String) {
        val slot = IosLayoutState.WidgetSlot("w_${System.currentTimeMillis()}", kind, -1, label, IosLayoutState.SPAN_MEDIUM)
        if (widgetPickerTarget == "home") layoutState.homeWidget = slot else layoutState.todayWidgets.add(slot)
        layoutState.save(prefs)
        rebuildVirtualPager(currentVirtualIndex)
    }

    private fun bindAndroidAppWidget(info: AppWidgetProviderInfo) {
        val host = appWidgetHost ?: return
        val mgr = appWidgetManager ?: return
        val id = host.allocateAppWidgetId()
        if (mgr.bindAppWidgetIdIfAllowed(id, info.provider)) {
            finishAppWidgetBind(id, info)
        } else {
            pendingBindInfo = info
            pendingBindWidgetId = id
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_BIND_APPWIDGET)
        }
    }

    private fun finishAppWidgetBind(id: Int, info: AppWidgetProviderInfo) {
        val label = info.loadLabel(packageManager) ?: "Widget"
        val slot = IosLayoutState.WidgetSlot("aw_$id", IosLayoutState.WIDGET_APPWIDGET, id, label, IosLayoutState.SPAN_MEDIUM)
        if (widgetPickerTarget == "home") layoutState.homeWidget = slot else layoutState.todayWidgets.add(slot)
        layoutState.save(prefs)
        rebuildVirtualPager(currentVirtualIndex)
    }

    private fun closeWidgetPicker() {
        isWidgetPickerOpen = false
        binding.sheetWidgetPicker.root.visibility = View.GONE
        refreshBackdrop()
    }

    private fun openPageOverview() {
        if (isOverviewOpen) return
        isOverviewOpen = true
        resetSurfaceTransform(binding.overlayPageOverview.root)
        binding.contextScrimOverlay.visibility = View.VISIBLE
        binding.overlayPageOverview.root.visibility = View.VISIBLE
        binding.overlayPageOverview.overviewPagesRow.removeAllViews()
        val cw = MotionSpec.dp(this, 132f).roundToInt()
        val ch = MotionSpec.dp(this, 196f).roundToInt()
        val margin = MotionSpec.dp(this, 10f).roundToInt()
        val cell = MotionSpec.dp(this, 26f).roundToInt()
        val cellPad = MotionSpec.dp(this, 4f).roundToInt()
        layoutState.pages.forEachIndexed { idx, slots ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_library_tile)
                setPadding(cellPad, cellPad, cellPad, cellPad)
                contentDescription = getString(R.string.page_indicator_desc, idx + 1, layoutState.pages.size)
                setOnClickListener { closePageOverview(); snapToVirtualPage(homePageIndexToVirtual(idx), 0f) }
            }
            // Real page content: the icons actually placed on that page, not a "Page N" label.
            val mini = GridLayout(this).apply { columnCount = 4; rowCount = 6 }
            slots.take(24).forEachIndexed { i, slot ->
                val pkg = if (slot.isFolder) slot.apps.firstOrNull() else slot.pkg
                if (pkg == null) return@forEachIndexed
                val iv = ImageView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = cell; height = cell
                        columnSpec = GridLayout.spec(i % 4)
                        rowSpec = GridLayout.spec(i / 4)
                        setMargins(3, 3, 3, 3)
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                loadAppIconAsync(pkg, iv)
                mini.addView(iv)
            }
            card.addView(mini)
            binding.overlayPageOverview.overviewPagesRow.addView(card, LinearLayout.LayoutParams(cw, ch).apply { setMargins(margin, 0, margin, 0) })
        }
        refreshBackdrop()
    }

    private fun closePageOverview() {
        if (!isOverviewOpen) return
        isOverviewOpen = false
        binding.overlayPageOverview.root.visibility = View.GONE
        if (!isContextMenuOpen && !isFolderOpen) binding.contextScrimOverlay.visibility = View.GONE
        refreshBackdrop()
    }

    private fun openSettingsPage() {
        isSettingsOpen = true
        resetSurfaceTransform(binding.pageSettings.root)
        buildSettingsUi()
        binding.pageSettings.root.visibility = View.VISIBLE
        binding.pageSettings.root.translationY = MotionSpec.dp(this, 48f)
        binding.pageSettings.root.alpha = 0f
        SpringPreset.animate(binding.pageSettings.root, DynamicAnimation.TRANSLATION_Y, 0f, SpringPreset.GLIDE)
        binding.pageSettings.root.animate().alpha(1f).setDuration(MotionSpec.DURATION_FAST_MS).start()
    }

    private fun closeSettingsPage() {
        if (!isSettingsOpen) return
        isSettingsOpen = false
        binding.pageSettings.root.animate().alpha(0f).setDuration(MotionSpec.DURATION_FAST_MS)
            .withEndAction { binding.pageSettings.root.visibility = View.GONE }.start()
    }

    private fun buildSettingsUi() {
        binding.pageSettings.settingsSectionsContainer.removeAllViews()
        val gen = addSettingsSection(getString(R.string.settings_section_general))
        addSettingsItem(gen, getString(R.string.set_home_prompt), "", isLast = true) { requestDefaultHomeRole() }

        val app = addSettingsSection(getString(R.string.settings_section_appearance))
        addSettingsItem(app, getString(R.string.theme_title), formatThemeLabel(themeMode)) { showThemeChoiceDialog() }
        addSettingsItem(app, getString(R.string.icon_shape_title), formatShapeLabel(iconShape)) { showIconShapeDialog() }
        addSettingsSwitch(app, getString(R.string.show_icon_labels_title), null, showLabels) { checked ->
            showLabels = checked; prefs.edit().putBoolean(PREF_SHOW_LABELS, checked).apply()
            rebuildVirtualPager(currentVirtualIndex)
        }
        addSettingsSlider(app, getString(R.string.wallpaper_dim_title, wallpaperDimPct), 40, wallpaperDimPct, isLast = true) { v, tv ->
            wallpaperDimPct = v; tv.text = getString(R.string.wallpaper_dim_title, v)
            prefs.edit().putInt(PREF_WALLPAPER_DIM, v).apply(); applyWallpaperVisuals()
        }

        val home = addSettingsSection(getString(R.string.settings_section_home))
        addSettingsSlider(home, getString(R.string.home_columns_title, gridColumns), 2, gridColumns - 4) { v, tv ->
            gridColumns = 4 + v; tv.text = getString(R.string.home_columns_title, gridColumns)
            prefs.edit().putInt(PREF_GRID_COLUMNS, gridColumns).apply(); rebuildVirtualPager(currentVirtualIndex)
        }
        addSettingsSlider(home, getString(R.string.dock_capacity_title, dockCapacity), 2, dockCapacity - 4) { v, tv ->
            dockCapacity = 4 + v; tv.text = getString(R.string.dock_capacity_title, dockCapacity)
            prefs.edit().putInt(PREF_DOCK_CAPACITY, dockCapacity).apply(); rebuildDock()
        }
        addSettingsSwitch(home, getString(R.string.show_search_pill_title), null, showSearchPill) { checked ->
            showSearchPill = checked; prefs.edit().putBoolean(PREF_SHOW_SEARCH_PILL, checked).apply(); updateDockAndPillForPage()
        }
        addSettingsItem(home, getString(R.string.widget_add_to_home), layoutState.homeWidget?.label.orEmpty()) {
            closeSettingsPage(); openWidgetPicker("home")
        }
        addSettingsItem(home, getString(R.string.reset_home_layout), "", isLast = true) { confirmResetHomeLayout() }

        val lib = addSettingsSection(getString(R.string.settings_section_library))
        addSettingsSwitch(lib, getString(R.string.show_app_library_title), null, showLibraryPage) { checked ->
            showLibraryPage = checked; prefs.edit().putBoolean(PREF_SHOW_LIBRARY, checked).apply()
            rebuildVirtualPager(homePageIndexToVirtual(0))
        }
        addSettingsSwitch(lib, getString(R.string.show_today_page_title), null, showTodayPage) { checked ->
            showTodayPage = checked; prefs.edit().putBoolean(PREF_SHOW_TODAY, checked).apply()
            rebuildVirtualPager(homePageIndexToVirtual(0))
        }
        addSettingsItem(lib, getString(R.string.cycle_library_order), "", isLast = true) {
            layoutState.cycleLibraryCategoryOrder(); layoutState.save(prefs)
            rebuildVirtualPager(currentVirtualIndex)
        }

        val spot = addSettingsSection(getString(R.string.settings_section_spotlight))
        addSettingsSwitch(spot, getString(R.string.spotlight_suggestions_title), null, spotlightSuggestionsEnabled) { checked ->
            spotlightSuggestionsEnabled = checked; prefs.edit().putBoolean(PREF_SPOTLIGHT_SUGGESTIONS, checked).apply()
        }
        addSettingsSwitch(spot, getString(R.string.spotlight_search_settings_title), null, spotlightSettingsEnabled) { checked ->
            spotlightSettingsEnabled = checked; prefs.edit().putBoolean(PREF_SPOTLIGHT_SETTINGS, checked).apply()
        }
        addSettingsItem(spot, getString(R.string.reset_history), "", isLast = true) { confirmResetHistory() }

        val badges = addSettingsSection(getString(R.string.settings_section_badges))
        val granted = KayumarsNotificationListener.isNotificationAccessGranted(this)
        addSettingsItem(badges, getString(if (granted) R.string.notification_access_granted else R.string.notification_access_btn), if (granted) "Active" else "Tap") {
            showNotificationExplainerDialog()
        }
        addSettingsSwitch(badges, getString(R.string.notification_badges_title), null, notifBadgesEnabled) { checked ->
            notifBadgesEnabled = checked; prefs.edit().putBoolean(PREF_NOTIF_BADGES, checked).apply(); rebuildVirtualPager(currentVirtualIndex)
        }
        addSettingsSwitch(badges, getString(R.string.new_app_dot_title), null, newDotEnabled) { checked ->
            newDotEnabled = checked; prefs.edit().putBoolean(PREF_NEW_DOT, checked).apply(); rebuildVirtualPager(currentVirtualIndex)
        }
        addSettingsItem(badges, getString(R.string.mute_app_badges_title, layoutState.mutedBadgePkgs.size), "", isLast = true) { showMuteAppsDialog() }

        val gest = addSettingsSection(getString(R.string.settings_section_gestures))
        addSettingsSwitch(gest, getString(R.string.gesture_swipe_down_spotlight), null, gestureSwipeSpotlight) { checked ->
            gestureSwipeSpotlight = checked; prefs.edit().putBoolean(PREF_GESTURE_SWIPE_SPOTLIGHT, checked).apply()
        }
        addSettingsSwitch(gest, getString(R.string.gesture_pinch_overview), null, gesturePinchOverview) { checked ->
            gesturePinchOverview = checked; prefs.edit().putBoolean(PREF_GESTURE_PINCH, checked).apply()
        }
        addSettingsSwitch(gest, getString(R.string.gesture_double_tap_lock), if (KayumarsLockService.isEnabled(this)) null else getString(R.string.gesture_lock_hint), gestureDoubleTapLock) { checked ->
            gestureDoubleTapLock = checked; prefs.edit().putBoolean(PREF_GESTURE_LOCK, checked).apply()
        }
        addSettingsItem(gest, getString(R.string.lock_service_setup_btn), if (KayumarsLockService.isEnabled(this)) "On" else "Off") {
            openSystemSettingAction(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        addSettingsSwitch(gest, getString(R.string.motion_debug_title), null, prefs.getBoolean(PREF_MOTION_DEBUG, false), isLast = true) { checked ->
            prefs.edit().putBoolean(PREF_MOTION_DEBUG, checked).apply(); motionDebug?.setEnabled(checked)
        }

        val about = addSettingsSection(getString(R.string.settings_section_about))
        addSettingsItem(about, getString(R.string.about_version), getString(R.string.offline_badge)) {}
        addSettingsItem(about, getString(R.string.about_engine), "", isLast = true) {}
    }

    private fun addSettingsSection(title: String): LinearLayout {
        val sec = LayoutInflater.from(this).inflate(R.layout.row_settings_section, binding.pageSettings.settingsSectionsContainer, false)
        sec.findViewById<TextView>(R.id.settings_section_header).text = title
        binding.pageSettings.settingsSectionsContainer.addView(sec)
        return sec.findViewById(R.id.settings_section_card)
    }

    private fun addSettingsItem(card: LinearLayout, title: String, value: String, isLast: Boolean = false, onClick: () -> Unit) {
        val row = LayoutInflater.from(this).inflate(R.layout.row_settings_item, card, false)
        row.findViewById<TextView>(R.id.settings_item_title).text = title
        row.findViewById<TextView>(R.id.settings_item_value).text = value
        row.findViewById<View>(R.id.settings_item_divider).visibility = if (isLast) View.GONE else View.VISIBLE
        row.setOnClickListener { onClick() }
        card.addView(row)
    }

    private fun addSettingsSwitch(card: LinearLayout, title: String, sub: String?, checked: Boolean, isLast: Boolean = false, onChanged: (Boolean) -> Unit) {
        val row = LayoutInflater.from(this).inflate(R.layout.row_settings_switch, card, false)
        row.findViewById<TextView>(R.id.settings_switch_title).text = title
        val subTv = row.findViewById<TextView>(R.id.settings_switch_subtitle)
        if (!sub.isNullOrEmpty()) { subTv.text = sub; subTv.visibility = View.VISIBLE }
        val sw = row.findViewById<SwitchCompat>(R.id.settings_switch_toggle)
        sw.isChecked = checked
        row.findViewById<View>(R.id.settings_switch_divider).visibility = if (isLast) View.GONE else View.VISIBLE
        sw.setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        card.addView(row)
    }

    private fun addSettingsSlider(card: LinearLayout, title: String, max: Int, progress: Int, isLast: Boolean = false, onChanged: (Int, TextView) -> Unit) {
        val row = LayoutInflater.from(this).inflate(R.layout.row_settings_slider, card, false)
        val tvTitle = row.findViewById<TextView>(R.id.settings_slider_title).apply { text = title }
        val seek = row.findViewById<SeekBar>(R.id.settings_slider_seek)
        seek.max = max
        seek.progress = progress.coerceIn(0, max)
        row.findViewById<View>(R.id.settings_slider_divider).visibility = if (isLast) View.GONE else View.VISIBLE
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChanged(p, tvTitle) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        card.addView(row)
    }

    private fun formatThemeLabel(m: String) = when (m) {
        "light" -> getString(R.string.theme_light)
        "dark" -> getString(R.string.theme_dark)
        "amoled" -> getString(R.string.theme_amoled)
        else -> getString(R.string.theme_system)
    }

    private fun formatShapeLabel(s: String) = when (s) {
        "circle" -> getString(R.string.icon_shape_circle)
        "rounded_square" -> getString(R.string.icon_shape_rounded_square)
        "system" -> getString(R.string.icon_shape_system)
        else -> getString(R.string.icon_shape_squircle)
    }

    private fun showThemeChoiceDialog() {
        val keys = arrayOf("system", "light", "dark", "amoled")
        val labels = arrayOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark), getString(R.string.theme_amoled))
        AlertDialog.Builder(this).setTitle(R.string.theme_title).setItems(labels) { _, which ->
            prefs.edit().putString(PREF_THEME, keys[which]).apply()
            applyNightModeForTheme(keys[which])
            recreate()
        }.show()
    }

    private fun showIconShapeDialog() {
        val keys = arrayOf("squircle", "circle", "rounded_square", "system")
        val labels = arrayOf(getString(R.string.icon_shape_squircle), getString(R.string.icon_shape_circle), getString(R.string.icon_shape_rounded_square), getString(R.string.icon_shape_system))
        AlertDialog.Builder(this).setTitle(R.string.icon_shape_title).setItems(labels) { _, which ->
            iconShape = keys[which]; prefs.edit().putString(PREF_ICON_SHAPE, iconShape).apply()
            iconCache.setShape(iconShape)
            buildSettingsUi(); rebuildVirtualPager(currentVirtualIndex)
        }.show()
    }

    private fun showNotificationExplainerDialog() {
        AlertDialog.Builder(this).setTitle(R.string.notification_explainer_title)
            .setMessage(R.string.notification_explainer_body)
            .setPositiveButton(R.string.enable) { _, _ -> openSystemSettingAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showMuteAppsDialog() {
        if (allApps.isEmpty()) return
        val labels = allApps.map { it.label }.toTypedArray()
        val checked = BooleanArray(allApps.size) { layoutState.mutedBadgePkgs.contains(allApps[it].pkg) }
        AlertDialog.Builder(this).setTitle(getString(R.string.mute_app_badges_title, layoutState.mutedBadgePkgs.size))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (isChecked) layoutState.mutedBadgePkgs.add(allApps[which].pkg)
                else layoutState.mutedBadgePkgs.remove(allApps[which].pkg)
            }
            .setPositiveButton(R.string.done) { _, _ ->
                layoutState.save(prefs); buildSettingsUi(); rebuildVirtualPager(currentVirtualIndex)
            }.show()
    }

    private fun confirmResetHomeLayout() {
        AlertDialog.Builder(this).setTitle(R.string.reset_home_layout).setMessage(R.string.reset_home_layout_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                layoutState.initialized = false
                layoutState.seedIfNeeded(allApps, getPageCapacity(), dockCapacity)
                layoutState.save(prefs); rebuildVirtualPager(homePageIndexToVirtual(0))
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirmResetHistory() {
        AlertDialog.Builder(this).setTitle(R.string.reset_history).setMessage(R.string.reset_history_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                Core.resetHistory()
                rebuildVirtualPager(currentVirtualIndex)
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun applyWallpaperVisuals() {
        val alpha = ((wallpaperDimPct / 100f) * 255f).roundToInt()
        binding.wallpaperDimScrim.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
    }

    private fun scanInstalledApps(detectNewlyInstalled: Boolean) {
        AppIndexer.scanAsync(this) { scanned ->
            val sorted = scanned.sortedBy { it.label.lowercase(Locale.ROOT) }
            val workSet = AppMetadataHelper.detectWorkProfilePackages(this)
            val prevKnown = knownPackages.toSet()

            allApps.clear(); allApps.addAll(sorted)
            appByPkg.clear(); knownPackages.clear()
            workProfilePackages.clear(); workProfilePackages.addAll(workSet)
            sorted.forEach { a ->
                appByPkg[a.pkg] = a; knownPackages.add(a.pkg)
                if (detectNewlyInstalled && prevKnown.isNotEmpty() && !prevKnown.contains(a.pkg)) {
                    layoutState.placeOnFirstFreePage(a.pkg, getPageCapacity(), true)
                }
            }
            layoutState.seedIfNeeded(sorted, getPageCapacity(), dockCapacity)
            layoutState.save(prefs)
            rebuildVirtualPager(currentVirtualIndex)
        }
    }

    private fun loadAppIconAsync(pkg: String, target: ImageView) {
        target.tag = pkg
        target.setImageDrawable(null)
        iconCache.loadIconAsync(pkg) { bmp ->
            if (target.tag == pkg) target.setImageBitmap(bmp)
        }
    }

    private fun launchAppWithFeedback(sourceView: View, pkg: String, clipReveal: Boolean) {
        sourceView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        SpringPreset.animateScale(sourceView, 0.94f, SpringPreset.SNAP)
        LaunchLogger.launchApp(this, pkg, sourceView, clipReveal)
    }

    private fun handleDoubleTapLock() {
        if (!KayumarsLockService.lockScreenNow()) {
            Toast.makeText(this, R.string.gesture_lock_hint, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasAnyDismissibleBackTarget(): Boolean {
        if (isSettingsOpen || isContextMenuOpen || isFolderOpen || isWidgetPickerOpen ||
            expandedLibraryCategory != null || isSpotlightOpen || isEditMode || isOverviewOpen
        ) return true
        return currentVirtualIndex != homePageIndexToVirtual(0)
    }

    // Predictive back scales/fades the active surface; restore it before re-showing.
    private fun resetSurfaceTransform(v: View) {
        v.scaleX = 1f
        v.scaleY = 1f
        v.alpha = 1f
    }

    private fun handlePredictiveBackStep(): Boolean {
        // homePager itself is the back surface when no overlay is open (page snap / edit mode).
        resetSurfaceTransform(binding.homePager)
        if (isSettingsOpen) { closeSettingsPage(); return true }
        if (isContextMenuOpen) { dismissIosContextMenu(); return true }
        if (isFolderOpen) { closeFolder(); return true }
        if (isWidgetPickerOpen) { closeWidgetPicker(); return true }
        if (expandedLibraryCategory != null) { expandedLibraryCategory = null; rebuildVirtualPager(currentVirtualIndex); return true }
        if (isSpotlightOpen) { closeSpotlight(); return true }
        if (isEditMode) { setEditMode(false); return true }
        if (isOverviewOpen) { closePageOverview(); return true }
        val home0 = homePageIndexToVirtual(0)
        if (currentVirtualIndex != home0) { snapToVirtualPage(home0, 0f); return true }
        return false
    }

    private fun dismissAllOverlaysToHome() {
        isSettingsOpen = false; binding.pageSettings.root.visibility = View.GONE
        dismissIosContextMenu()
        if (isFolderOpen) { isFolderOpen = false; binding.cardFolder.root.visibility = View.GONE }
        closeWidgetPicker()
        if (isSpotlightOpen) { isSpotlightOpen = false; binding.overlaySpotlight.root.visibility = View.GONE }
        if (isOverviewOpen) closePageOverview()
        if (isEditMode) setEditMode(false)
        snapToVirtualPage(homePageIndexToVirtual(0), 0f)
        refreshBackdrop()
    }

    private fun shouldShowDefaultHomePrompt(): Boolean {
        if (prefs.getBoolean(PREF_HOME_DISMISSED, false)) return false
        return try {
            val i = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val ri = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
            ri?.activityInfo?.packageName != packageName
        } catch (_: Exception) { false }
    }

    private fun requestDefaultHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(ROLE_SERVICE) as? RoleManager
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                @Suppress("DEPRECATION")
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE_HOME)
                return
            }
        }
        openSystemSettingAction(Settings.ACTION_HOME_SETTINGS)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ROLE_HOME) rebuildVirtualPager(currentVirtualIndex)
        else if (requestCode == REQ_BIND_APPWIDGET && resultCode == RESULT_OK) {
            pendingBindInfo?.let { finishAppWidgetBind(pendingBindWidgetId, it) }
            pendingBindInfo = null
        }
    }

    private fun openAppInfo(pkg: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
    }

    private fun shareAppDetails(app: AppEntry) {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${app.label} (${app.pkg})")
            }, getString(R.string.share_app)))
        } catch (_: Exception) {}
    }

    private fun requestUninstallApp(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
    }

    private fun openSystemSettingAction(action: String) {
        if (action == "kayumars://settings") { closeSpotlight(); openSettingsPage(); return }
        try { startActivity(Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
    }

    private fun hideKeyboard(v: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun updateClockTexts() {
        val now = Date()
        clockBindings.forEach { (tv, fmt) -> tv.text = fmt.format(now) }
    }

    private fun updateBadgesInPlace() {
        updateBadgeViews(binding.rootLayout)
    }

    private fun updateBadgeViews(v: View) {
        if (v is TextView && v.id == R.id.badge_notif_count) {
            val pkgs = (v.tag as? List<*>)?.filterIsInstance<String>() ?: return
            val cnt = pkgs.sumOf {
                KayumarsNotificationListener.getBadgeCount(it, notifBadgesEnabled, layoutState.mutedBadgePkgs)
            }
            if (cnt > 0 && !isEditMode) {
                v.visibility = View.VISIBLE
                v.text = if (cnt > 99) "99+" else cnt.toString()
            } else v.visibility = View.GONE
            return
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) updateBadgeViews(v.getChildAt(i))
        }
    }

    private fun registerDynamicReceivers() {
        if (receiversRegistered) return
        packageReceiver = PackageChangeReceiver { pkg ->
            iconCache.invalidate(pkg)
            // Full rescan + UI rebuild on the main thread (scanAsync callback), so installs
            // and uninstalls refresh the app list while the launcher is alive.
            scanInstalledApps(true)
        }
        val pf = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(packageReceiver, pf, RECEIVER_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(packageReceiver, pf)

        timeTickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateClockTexts()
            }
        }
        registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        receiversRegistered = true
    }

    private fun unregisterDynamicReceivers() {
        if (!receiversRegistered) return
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(timeTickReceiver) } catch (_: Exception) {}
        receiversRegistered = false
    }

    private inner class SimpleCellAdapter(
        private val items: List<AppEntry>,
        private val whiteLabelOverWallpaper: Boolean
    ) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: LayoutInflater.from(this@MainActivity).inflate(R.layout.item_home_cell, parent, false)
            val app = items[position]
            val icon = v.findViewById<ImageView>(R.id.app_icon)
            val label = v.findViewById<TextView>(R.id.app_label)
            val badgeNotif = v.findViewById<TextView>(R.id.badge_notif_count)
            val badgeWork = v.findViewById<ImageView>(R.id.badge_work_profile)
            loadAppIconAsync(app.pkg, icon)
            label.text = app.label
            if (!whiteLabelOverWallpaper) {
                label.setTextColor(getColor(R.color.label_primary))
                label.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
            val cnt = KayumarsNotificationListener.getBadgeCount(app.pkg, notifBadgesEnabled, layoutState.mutedBadgePkgs)
            badgeNotif.tag = listOf(app.pkg)
            if (cnt > 0) {
                badgeNotif.visibility = View.VISIBLE; badgeNotif.text = if (cnt > 99) "99+" else cnt.toString()
            } else badgeNotif.visibility = View.GONE
            badgeWork.visibility = if (workProfilePackages.contains(app.pkg)) View.VISIBLE else View.GONE
            return v
        }
    }

    private inner class SpotlightResultsAdapter : BaseAdapter() {
        override fun getCount() = spotlightRows.size
        override fun getItem(position: Int) = spotlightRows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: LayoutInflater.from(this@MainActivity).inflate(R.layout.item_spotlight_row, parent, false)
            val row = spotlightRows[position]
            val sectionHdr = v.findViewById<TextView>(R.id.spotlight_section_title)
            val card = v.findViewById<View>(R.id.spotlight_row_card)
            val icon = v.findViewById<ImageView>(R.id.spotlight_row_icon)
            val title = v.findViewById<TextView>(R.id.spotlight_row_title)
            val sub = v.findViewById<TextView>(R.id.spotlight_row_subtitle)
            val action = v.findViewById<TextView>(R.id.spotlight_row_action)
            val chevron = v.findViewById<View>(R.id.spotlight_row_chevron)

            if (row.type == ROW_TYPE_HEADER) {
                sectionHdr.visibility = View.VISIBLE; sectionHdr.text = row.header; card.visibility = View.GONE; return v
            }
            sectionHdr.visibility = View.GONE; card.visibility = View.VISIBLE
            card.layoutParams = card.layoutParams.apply {
                height = resources.getDimensionPixelSize(
                    if (row.type == ROW_TYPE_TOP_HIT) R.dimen.ios_spotlight_top_hit_height else R.dimen.ios_list_row_height
                )
            }
            if (row.type == ROW_TYPE_SETTING && row.setting != null) {
                icon.setImageResource(row.setting.iconRes.takeIf { it != 0 } ?: R.drawable.ic_glyph_settings)
                icon.setColorFilter(getColor(R.color.system_blue))
                title.text = row.setting.title
                sub.text = row.setting.subtitle; sub.visibility = View.VISIBLE
                action.visibility = View.GONE; chevron.visibility = View.VISIBLE
            } else if (row.app != null) {
                icon.clearColorFilter(); loadAppIconAsync(row.app.pkg, icon)
                title.text = row.app.label
                sub.text = if (row.type == ROW_TYPE_TOP_HIT) getString(R.string.spotlight_top_hit) else row.app.pkg
                sub.visibility = View.VISIBLE
                action.visibility = if (row.type == ROW_TYPE_TOP_HIT) View.VISIBLE else View.GONE
                chevron.visibility = if (row.type == ROW_TYPE_TOP_HIT) View.GONE else View.VISIBLE
            }
            return v
        }
    }

    companion object {
        private const val PREFS_NAME = "kayumars_prefs"
        private const val PREF_THEME = "theme_mode"
        private const val PREF_ICON_SHAPE = "icon_shape"
        private const val PREF_SHOW_LABELS = "ios_show_labels"
        private const val PREF_SHOW_SEARCH_PILL = "ios_show_search_pill"
        private const val PREF_SHOW_TODAY = "ios_show_today_page"
        private const val PREF_SHOW_LIBRARY = "ios_show_library_page"
        private const val PREF_GRID_COLUMNS = "ios_grid_columns"
        private const val PREF_DOCK_CAPACITY = "ios_dock_capacity"
        private const val PREF_WALLPAPER_DIM = "ios_wallpaper_dim"
        private const val PREF_NOTIF_BADGES = "ios_notif_badges"
        private const val PREF_NEW_DOT = "ios_new_app_dot"
        private const val PREF_SPOTLIGHT_SUGGESTIONS = "ios_spotlight_suggestions"
        private const val PREF_SPOTLIGHT_SETTINGS = "ios_spotlight_settings"
        private const val PREF_GESTURE_LOCK = "ios_gesture_double_tap_lock"
        private const val PREF_GESTURE_PINCH = "ios_gesture_pinch_overview"
        private const val PREF_GESTURE_SWIPE_SPOTLIGHT = "ios_gesture_swipe_spotlight"
        private const val PREF_MOTION_DEBUG = "motion_debug_hud"
        private const val PREF_HOME_DISMISSED = "home_prompt_dismissed"

        private const val REQ_ROLE_HOME = 1001
        private const val REQ_BIND_APPWIDGET = 1002
        private const val APPWIDGET_HOST_ID = 0x4B4159

        private const val ROW_TYPE_HEADER = 0
        private const val ROW_TYPE_TOP_HIT = 1
        private const val ROW_TYPE_APP = 2
        private const val ROW_TYPE_SETTING = 3
    }
}
