import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Search,
  X,
  Zap,
  MessageCircle,
  Camera,
  Globe,
  Play,
  Instagram,
  Send,
  Mail,
  Settings,
  Shield,
  Music,
  MapPin,
  Calculator,
  Clock,
  Folder,
  Phone,
  Lock,
  Gamepad2,
  Image as ImageIcon,
  Shapes,
  ChevronRight,
  ChevronLeft,
  SlidersHorizontal,
  Share2,
  Trash2,
  Info,
  Pencil,
  Briefcase,
  Sparkles,
  Plus,
  Minus,
  Layers,
  BellOff,
  Bell,
  ArrowUpRight,
  Check,
  LayoutGrid,
  Cpu,
} from 'lucide-react';
import { KayumarsSimulationEngine } from '../engine/kayumarsCore';
import { SearchResult, AppEntry, LauncherPreferences } from '../types/launcher';
import { OemUiProfile } from '../data/oemUiResearch';
import { SettingsBottomSheet } from './SettingsBottomSheet';

interface Props {
  engine: KayumarsSimulationEngine;
  installedApps: AppEntry[];
  onAppLaunched: (pkg: string) => void;
  onAddApp: (app: AppEntry) => void;
  onRemoveApp: (pkg: string) => void;
  presetTrigger?: { nonce: number; profile: OemUiProfile } | null;
}

type HomeSlot =
  | { kind: 'app'; pkg: string }
  | { kind: 'folder'; folderId: string; title: string; apps: string[] };

type WidgetSpan = 'small' | 'medium' | 'large';
type WidgetKind = 'clock' | 'suggestions' | 'vault' | 'notes';

interface WidgetItem {
  id: string;
  kind: WidgetKind;
  label: string;
  faLabel: string;
  span: WidgetSpan;
}

interface IosSimLayout {
  pages: HomeSlot[][];
  dock: string[];
  todayWidgets: WidgetItem[];
  homeWidget: WidgetItem | null;
  newBadgePkgs: string[];
  categoryOrder: LibraryCategoryKey[];
}

type LibraryCategoryKey =
  | 'suggestions'
  | 'productivity'
  | 'social'
  | 'creativity'
  | 'utilities'
  | 'other';

interface ContextMenuTarget {
  app: AppEntry;
  source: 'home' | 'dock' | 'folder' | 'library' | 'spotlight';
  pageIdx?: number;
  slotIdx?: number;
  folderId?: string;
}

const IOS_LAYOUT_STORAGE_KEY = 'kayumars_ios_sim_layout_v2';

const DEFAULT_CATEGORY_ORDER: LibraryCategoryKey[] = [
  'suggestions',
  'productivity',
  'social',
  'creativity',
  'utilities',
  'other',
];

const CATEGORY_META: Record<LibraryCategoryKey, { en: string; fa: string }> = {
  suggestions: { en: 'Suggestions', fa: 'پیشنهادها' },
  productivity: { en: 'Productivity & Finance', fa: 'بهره‌وری و مالی' },
  social: { en: 'Social', fa: 'اجتماعی' },
  creativity: { en: 'Creativity', fa: 'خلاقیت و رسانه' },
  utilities: { en: 'Utilities', fa: 'ابزارها' },
  other: { en: 'Other', fa: 'سایر' },
};

const WORK_PROFILE_PKGS = new Set([
  'com.Slack',
  'com.github.android',
  'com.google.android.apps.docs',
  'notion.id',
]);

const DEFAULT_NOTIFICATION_COUNTS: Record<string, number> = {
  'org.telegram.messenger': 14,
  'com.google.android.apps.messaging': 3,
  'com.instagram.android': 27,
  'com.google.android.gm': 104,
  'com.discord': 6,
  'com.Slack': 2,
};

const VALID_WIDGET_KINDS = new Set<WidgetKind>(['clock', 'suggestions', 'vault', 'notes']);
const VALID_WIDGET_SPANS = new Set<WidgetSpan>(['small', 'medium', 'large']);

function sanitizeWidget(raw: unknown): WidgetItem | null {
  if (!raw || typeof raw !== 'object') return null;
  const w = raw as Partial<WidgetItem>;
  if (typeof w.id !== 'string' || !w.kind || !VALID_WIDGET_KINDS.has(w.kind)) return null;
  const span: WidgetSpan = w.span && VALID_WIDGET_SPANS.has(w.span) ? w.span : 'medium';
  return {
    id: w.id,
    kind: w.kind,
    label: typeof w.label === 'string' && w.label ? w.label : w.kind,
    faLabel: typeof w.faLabel === 'string' && w.faLabel ? w.faLabel : w.kind,
    span,
  };
}

function sanitizeLayout(raw: unknown): IosSimLayout {
  const defaults = createDefaultLayout();
  if (!raw || typeof raw !== 'object') return defaults;
  const parsed = raw as Partial<IosSimLayout>;

  let pages = defaults.pages;
  if (Array.isArray(parsed.pages) && parsed.pages.length > 0) {
    const cleanedPages = parsed.pages
      .filter((p): p is HomeSlot[] => Array.isArray(p))
      .map((page) =>
        page
          .map((slot): HomeSlot | null => {
            if (!slot || typeof slot !== 'object') return null;
            if (slot.kind === 'app' && typeof slot.pkg === 'string' && slot.pkg) {
              return { kind: 'app', pkg: slot.pkg };
            }
            if (slot.kind === 'folder' && typeof slot.folderId === 'string' && Array.isArray(slot.apps)) {
              const apps = slot.apps.filter((a): a is string => typeof a === 'string' && a.length > 0);
              if (apps.length === 0) return null;
              return {
                kind: 'folder',
                folderId: slot.folderId,
                title: typeof slot.title === 'string' && slot.title ? slot.title : 'Folder',
                apps,
              };
            }
            return null;
          })
          .filter((s): s is HomeSlot => s !== null)
      );
    if (cleanedPages.length > 0) {
      pages = cleanedPages;
    }
  }

  const dock = Array.isArray(parsed.dock)
    ? parsed.dock.filter((d): d is string => typeof d === 'string' && d.length > 0)
    : defaults.dock;

  const todayWidgets = Array.isArray(parsed.todayWidgets)
    ? parsed.todayWidgets.map(sanitizeWidget).filter((w): w is WidgetItem => w !== null)
    : defaults.todayWidgets;

  const homeWidget = parsed.homeWidget ? sanitizeWidget(parsed.homeWidget) : null;

  const newBadgePkgs = Array.isArray(parsed.newBadgePkgs)
    ? parsed.newBadgePkgs.filter((p): p is string => typeof p === 'string')
    : defaults.newBadgePkgs;

  const rawCats = Array.isArray(parsed.categoryOrder) ? parsed.categoryOrder : [];
  const validCats = rawCats.filter(
    (c): c is LibraryCategoryKey => typeof c === 'string' && c in CATEGORY_META
  );
  const categoryOrder: LibraryCategoryKey[] = Array.from(
    new Set<LibraryCategoryKey>([...validCats, ...DEFAULT_CATEGORY_ORDER])
  );

  return {
    pages,
    dock,
    todayWidgets,
    homeWidget,
    newBadgePkgs,
    categoryOrder,
  };
}

function createDefaultLayout(): IosSimLayout {
  return {
    pages: [
      [
        { kind: 'app', pkg: 'com.google.android.apps.maps' },
        { kind: 'app', pkg: 'com.google.android.youtube' },
        { kind: 'app', pkg: 'org.telegram.messenger' },
        { kind: 'app', pkg: 'com.instagram.android' },
        { kind: 'app', pkg: 'com.spotify.music' },
        { kind: 'app', pkg: 'com.google.android.gm' },
        { kind: 'app', pkg: 'com.android.settings' },
        { kind: 'app', pkg: 'com.Slack' },
        {
          kind: 'folder',
          folderId: 'f_social',
          title: 'Social',
          apps: [
            'com.discord',
            'ir.eitaa.messenger',
            'ir.nasim.messenger',
            'com.twitter.android',
            'org.thoughtcrime.securesms',
          ],
        },
        {
          kind: 'folder',
          folderId: 'f_tools',
          title: 'Utilities',
          apps: [
            'com.google.android.calculator',
            'com.google.android.deskclock',
            'com.google.android.apps.nbu.files',
            'com.x8bit.bitwarden',
          ],
        },
      ],
      [
        { kind: 'app', pkg: 'com.github.android' },
        { kind: 'app', pkg: 'notion.id' },
        { kind: 'app', pkg: 'com.google.android.apps.docs' },
        { kind: 'app', pkg: 'com.google.android.apps.photos' },
        { kind: 'app', pkg: 'com.microsoft.emmx' },
        { kind: 'app', pkg: 'com.netflix.mediaclient' },
      ],
    ],
    dock: [
      'com.google.android.dialer',
      'com.google.android.apps.messaging',
      'com.android.chrome',
      'com.android.camera',
    ],
    todayWidgets: [
      { id: 'w_clock', kind: 'clock', label: 'Clock & Date', faLabel: 'ساعت و تاریخ', span: 'medium' },
      { id: 'w_sugg', kind: 'suggestions', label: 'Siri Suggestions', faLabel: 'پیشنهادهای هوشمند', span: 'medium' },
      { id: 'w_vault', kind: 'vault', label: 'Offline Vault', faLabel: 'وضعیت هسته آفلاین', span: 'small' },
    ],
    homeWidget: null,
    newBadgePkgs: ['com.github.android'],
    categoryOrder: [...DEFAULT_CATEGORY_ORDER],
  };
}

function classifyLibraryCategory(app: AppEntry): Exclude<LibraryCategoryKey, 'suggestions'> {
  const low = `${app.package} ${app.label}`.toLowerCase();
  if (
    low.includes('telegram') ||
    low.includes('instagram') ||
    low.includes('discord') ||
    low.includes('messag') ||
    low.includes('dialer') ||
    low.includes('phone') ||
    low.includes('eitaa') ||
    low.includes('bale') ||
    low.includes('rubika') ||
    low.includes('signal') ||
    low.includes('twitter') ||
    low.includes('linkedin')
  ) {
    return 'social';
  }
  if (
    low.includes('camera') ||
    low.includes('youtube') ||
    low.includes('spotify') ||
    low.includes('music') ||
    low.includes('photo') ||
    low.includes('netflix') ||
    low.includes('vlc') ||
    low.includes('soundcloud')
  ) {
    return 'creativity';
  }
  if (
    low.includes('gmail') ||
    low.includes('mail') ||
    low.includes('slack') ||
    low.includes('notion') ||
    low.includes('obsidian') ||
    low.includes('github') ||
    low.includes('docs') ||
    low.includes('drive') ||
    low.includes('calendar') ||
    low.includes('stripe')
  ) {
    return 'productivity';
  }
  if (
    low.includes('calc') ||
    low.includes('clock') ||
    low.includes('files') ||
    low.includes('setting') ||
    low.includes('bitwarden') ||
    low.includes('chrome') ||
    low.includes('firefox') ||
    low.includes('edge') ||
    low.includes('maps')
  ) {
    return 'utilities';
  }
  return 'other';
}

function getAppGlyph(pkg: string, label: string) {
  const low = `${pkg} ${label}`.toLowerCase();
  if (low.includes('dialer') || low.includes('phone') || low.includes('call')) return Phone;
  if (low.includes('instagram')) return Instagram;
  if (low.includes('telegram') || low.includes('bale')) return Send;
  if (low.includes('messag') || low.includes('eitaa') || low.includes('discord') || low.includes('slack')) return MessageCircle;
  if (low.includes('camera')) return Camera;
  if (low.includes('youtube') || low.includes('netflix') || low.includes('vlc')) return Play;
  if (low.includes('edge') || low.includes('chrome') || low.includes('firefox') || low.includes('browser')) return Globe;
  if (low.includes('mail') || low.includes('gmail')) return Mail;
  if (low.includes('spotify') || low.includes('music') || low.includes('sound')) return Music;
  if (low.includes('map')) return MapPin;
  if (low.includes('calc')) return Calculator;
  if (low.includes('clock')) return Clock;
  if (low.includes('file') || low.includes('drive') || low.includes('docs')) return Folder;
  if (low.includes('vpn') || low.includes('shield') || low.includes('bitwarden')) return Shield;
  if (low.includes('setting')) return Settings;
  if (low.includes('game') || low.includes('retro')) return Gamepad2;
  if (low.includes('photo')) return ImageIcon;
  return Shapes;
}

function getQuickActions(app: AppEntry, isRtl: boolean): Array<{ id: string; label: string }> {
  const low = `${app.package} ${app.label}`.toLowerCase();
  if (low.includes('messag') || low.includes('telegram') || low.includes('slack')) {
    return [
      { id: 'compose', label: isRtl ? 'پیام جدید' : 'New Message' },
      { id: 'pinned', label: isRtl ? 'گفتگوهای سنجاق‌شده' : 'Pinned Threads' },
    ];
  }
  if (low.includes('camera')) {
    return [
      { id: 'selfie', label: isRtl ? 'ثبت سلفی' : 'Take Selfie' },
      { id: 'video', label: isRtl ? 'ضبط ویدیو' : 'Record Video' },
    ];
  }
  if (low.includes('setting')) {
    return [
      { id: 'wifi', label: isRtl ? 'وای‌فای و شبکه' : 'Wi-Fi & Network' },
      { id: 'bt', label: isRtl ? 'بلوتوث' : 'Bluetooth' },
      { id: 'battery', label: isRtl ? 'وضعیت باتری' : 'Battery Health' },
    ];
  }
  if (low.includes('dialer') || low.includes('phone')) {
    return [
      { id: 'recent', label: isRtl ? 'تماس‌های اخیر' : 'Recent Calls' },
      { id: 'fav', label: isRtl ? 'مخاطبین برگزیده' : 'Favorites' },
    ];
  }
  return [{ id: 'recent', label: isRtl ? 'باز کردن سریع' : 'Quick Launch' }];
}

export const LauncherSimulator: React.FC<Props> = ({
  engine,
  installedApps,
  onAppLaunched,
  onAddApp,
  onRemoveApp,
  presetTrigger,
}) => {
  const [prefs, setPrefs] = useState<LauncherPreferences>(() => engine.getPreferences());

  const [layout, setLayout] = useState<IosSimLayout>(() => {
    try {
      const raw = localStorage.getItem(IOS_LAYOUT_STORAGE_KEY);
      if (raw) {
        return sanitizeLayout(JSON.parse(raw));
      }
    } catch {
      // ignore storage errors
    }
    return createDefaultLayout();
  });

  // Pager index over active virtual pages: [Today?, ...HomePages, AppLibrary?]
  const showToday = prefs.showTodayPage !== false;
  const showLibrary = prefs.showAppLibrary !== false;
  const totalVirtualPages = (showToday ? 1 : 0) + layout.pages.length + (showLibrary ? 1 : 0);

  // Start directly on Home Page 0
  const [pagerIdx, setPagerIdx] = useState<number>(() => (showToday ? 1 : 0));

  // Interactive overlays & modal states
  const [isEditMode, setIsEditMode] = useState(false);
  const [spotlightOpen, setSpotlightOpen] = useState(false);
  const [spotlightQuery, setSpotlightQuery] = useState('');
  const [spotlightResults, setSpotlightResults] = useState<SearchResult[]>([]);
  const [latencyMs, setLatencyMs] = useState(0.54);

  const [settingsOpen, setSettingsOpen] = useState(false);
  const [overviewOpen, setOverviewOpen] = useState(false);
  const [widgetPickerOpen, setWidgetPickerOpen] = useState(false);
  const [widgetTargetHome, setWidgetTargetHome] = useState(false);
  const [lockOverlayOpen, setLockOverlayOpen] = useState(false);

  const [openFolderId, setOpenFolderId] = useState<{ pageIdx: number; folderId: string } | null>(null);
  const [folderSubPage, setFolderSubPage] = useState(0);
  const [expandedLibCategory, setExpandedLibCategory] = useState<LibraryCategoryKey | null>(null);
  const [libSearchQuery, setLibSearchQuery] = useState('');

  const [contextTarget, setContextTarget] = useState<ContextMenuTarget | null>(null);
  const [appInfoTarget, setAppInfoTarget] = useState<AppEntry | null>(null);

  // Drag-and-drop & hover-to-folder states
  const [dragItem, setDragItem] = useState<{ pageIdx: number; slotIdx: number } | null>(null);
  const [hoverSlotIdx, setHoverSlotIdx] = useState<number | null>(null);
  const [hoverFolderReady, setHoverFolderReady] = useState(false);

  const [pressedPkg, setPressedPkg] = useState<string | null>(null);
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const [simInstallCounter, setSimInstallCounter] = useState(1);
  const [hudStats, setHudStats] = useState({ frameMs: 8.3, drops: 0 });

  const spotlightInputRef = useRef<HTMLInputElement>(null);
  const hoverFolderTimerRef = useRef<any>(null);
  const longPressTimerRef = useRef<any>(null);
  const pagerTouchStartRef = useRef<{ x: number; y: number; t: number } | null>(null);
  const emptyTapRef = useRef<number>(0);

  const isRtl = prefs.localeMode === 'fa';
  const isLight = prefs.themeMode === 'light';
  const isAmoled = prefs.themeMode === 'amoled';
  const isBlur = prefs.drawerBlur !== false;
  const isReducedMotion = Boolean(prefs.reducedMotion);
  const gridCols = prefs.gridColumns ?? 4;
  const dockCap = prefs.dockCapacity ?? 4;

  const appMap = useMemo(() => {
    const m = new Map<string, AppEntry>();
    for (const a of installedApps) {
      if (!m.has(a.package)) m.set(a.package, a);
    }
    return m;
  }, [installedApps]);

  const showToast = (msg: string) => {
    setToastMsg(msg);
    setTimeout(() => setToastMsg(null), 2200);
  };

  const updatePreferences = (updated: Partial<LauncherPreferences>) => {
    const next = engine.updatePreferences(updated);
    setPrefs(next);
  };

  useEffect(() => {
    try {
      localStorage.setItem(IOS_LAYOUT_STORAGE_KEY, JSON.stringify(layout));
    } catch {
      // ignore storage errors
    }
  }, [layout]);

  // Clamp pagerIdx if Today/AppLibrary/pages count changes
  useEffect(() => {
    setPagerIdx((cur) => Math.max(0, Math.min(totalVirtualPages - 1, cur)));
  }, [totalVirtualPages]);

  // Sync Spotlight results with Rust Nucleo engine
  useEffect(() => {
    const { results, latencyMs: lat } = engine.search(spotlightQuery, 24);
    setSpotlightResults(results);
    setLatencyMs(lat);
  }, [spotlightQuery, engine, installedApps, prefs]);

  // Apply external OEM preset trigger if triggered from OEM Research Lab
  useEffect(() => {
    if (!presetTrigger) return;
    const next = engine.updatePreferences(presetTrigger.profile.simulatorPreset.prefs);
    setPrefs(next);
    setSettingsOpen(false);
    setSpotlightOpen(false);
    setOverviewOpen(false);
    setOpenFolderId(null);
    setContextTarget(null);
    showToast(`${presetTrigger.profile.uiName} applied`);
  }, [presetTrigger, engine]);

  // Dormant 120Hz HUD sampler
  useEffect(() => {
    if (!prefs.showMotionHud) return;
    let rafId = 0;
    let lastTs = 0;
    let drops = 0;
    let frames = 0;
    const tick = (ts: number) => {
      if (lastTs > 0) {
        const dt = ts - lastTs;
        frames++;
        if (dt > 16.9) drops++;
        if (frames % 8 === 0) {
          setHudStats({ frameMs: Math.round(dt * 10) / 10, drops });
        }
      }
      lastTs = ts;
      rafId = requestAnimationFrame(tick);
    };
    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [prefs.showMotionHud]);

  const homeStartVirtualIdx = showToday ? 1 : 0;
  const homeEndVirtualIdx = homeStartVirtualIdx + layout.pages.length - 1;
  const libraryVirtualIdx = showLibrary ? totalVirtualPages - 1 : -1;

  const activeVirtualKind: 'today' | 'home' | 'library' =
    showToday && pagerIdx === 0
      ? 'today'
      : showLibrary && pagerIdx === libraryVirtualIdx
      ? 'library'
      : 'home';
  const activeHomePageIdx = Math.max(0, Math.min(layout.pages.length - 1, pagerIdx - homeStartVirtualIdx));

  const goToToday = () => {
    if (!showToday) {
      updatePreferences({ showTodayPage: true });
    }
    setPagerIdx(0);
    setSpotlightOpen(false);
    setSettingsOpen(false);
    setOverviewOpen(false);
  };

  const goToHomePage = (homeIdx: number) => {
    const target = homeStartVirtualIdx + Math.max(0, Math.min(layout.pages.length - 1, homeIdx));
    setPagerIdx(target);
    setSpotlightOpen(false);
    setSettingsOpen(false);
    setOverviewOpen(false);
  };

  const goToLibrary = () => {
    if (!showLibrary) {
      updatePreferences({ showAppLibrary: true });
      setTimeout(() => setPagerIdx((prev) => Math.max(prev, totalVirtualPages)), 0);
    } else {
      setPagerIdx(libraryVirtualIdx);
    }
    setSpotlightOpen(false);
    setSettingsOpen(false);
    setOverviewOpen(false);
  };

  const openSpotlight = (presetQuery = '') => {
    setSpotlightQuery(presetQuery);
    setSpotlightOpen(true);
    setContextTarget(null);
    setOpenFolderId(null);
    setTimeout(() => spotlightInputRef.current?.focus(), 60);
  };

  const handleLaunchApp = (pkg: string) => {
    const app = appMap.get(pkg);
    const label = app?.label || pkg;
    setPressedPkg(pkg);
    // Clear blue "new app" dot on launch
    if (layout.newBadgePkgs.includes(pkg)) {
      setLayout((prev) => ({
        ...prev,
        newBadgePkgs: prev.newBadgePkgs.filter((p) => p !== pkg),
      }));
    }
    setTimeout(() => {
      setPressedPkg(null);
      engine.logLaunch(pkg, Date.now());
      onAppLaunched(pkg);
      showToast(isRtl ? `اجرا: ${label}` : `Launched ${label}`);
    }, 110);
  };

  const handleSimulateInstallApp = () => {
    const names = ['Notes Pro', 'Weather Radar', 'Swift Wallet', 'Podcast Plus', 'Code Pad', 'Focus Timer'];
    const colors = ['#007AFF', '#34C759', '#FF9500', '#AF52DE', '#FF2D55', '#5AC8FA'];
    const cats: Array<AppEntry['category']> = ['tools', 'utility', 'tools', 'media', 'dev', 'utility'];
    const idx = (simInstallCounter - 1) % names.length;
    const newApp: AppEntry = {
      package: `com.kayumars.sim.${names[idx].toLowerCase().replace(/\s+/g, '')}${simInstallCounter}`,
      label: `${names[idx]} ${simInstallCounter > names.length ? simInstallCounter : ''}`.trim(),
      user_serial: 0,
      flags: 0,
      category: cats[idx],
      iconColor: colors[idx],
    };
    setSimInstallCounter((c) => c + 1);
    onAddApp(newApp);

    // Auto-place in first Home page with space (<= gridCols * 5 = capacity), or create new page
    const pageCapacity = gridCols * 5;
    setLayout((prev) => {
      const pagesCopy = prev.pages.map((p) => [...p]);
      let placedPage = -1;
      for (let i = 0; i < pagesCopy.length; i++) {
        if (pagesCopy[i].length < pageCapacity) {
          pagesCopy[i].push({ kind: 'app', pkg: newApp.package });
          placedPage = i;
          break;
        }
      }
      if (placedPage === -1) {
        pagesCopy.push([{ kind: 'app', pkg: newApp.package }]);
        placedPage = pagesCopy.length - 1;
      }
      return {
        ...prev,
        pages: pagesCopy,
        newBadgePkgs: [...prev.newBadgePkgs, newApp.package],
      };
    });
    goToHomePage(0);
    showToast(isRtl ? `نصب شد: ${newApp.label}` : `Installed ${newApp.label} (Auto-placed)`);
  };

  const handleRemoveSlotFromPage = (pageIdx: number, slotIdx: number) => {
    setLayout((prev) => {
      const nextPages = prev.pages.map((p, idx) =>
        idx === pageIdx ? p.filter((_, sIdx) => sIdx !== slotIdx) : p
      );
      return { ...prev, pages: nextPages };
    });
  };

  const handleRemoveFromDock = (pkg: string) => {
    setLayout((prev) => ({
      ...prev,
      dock: prev.dock.filter((d) => d !== pkg),
    }));
  };

  const handleAddAppToHome = (pkg: string, targetPageIdx = activeHomePageIdx) => {
    setLayout((prev) => {
      const pagesCopy = prev.pages.map((p) => [...p]);
      const pIdx = Math.max(0, Math.min(pagesCopy.length - 1, targetPageIdx));
      if (!pagesCopy[pIdx].some((s) => s.kind === 'app' && s.pkg === pkg)) {
        pagesCopy[pIdx].push({ kind: 'app', pkg });
      }
      return { ...prev, pages: pagesCopy };
    });
    showToast(isRtl ? 'به صفحه اصلی اضافه شد' : 'Added to Home Screen');
  };

  const handleMoveToDock = (pkg: string) => {
    setLayout((prev) => {
      if (prev.dock.includes(pkg)) return prev;
      if (prev.dock.length >= dockCap) {
        showToast(isRtl ? `داک پر است (حداکثر ${dockCap})` : `Dock is full (max ${dockCap})`);
        return prev;
      }
      return { ...prev, dock: [...prev.dock, pkg] };
    });
  };

  const handleDeleteAppCompletely = (pkg: string) => {
    onRemoveApp(pkg);
    setLayout((prev) => ({
      ...prev,
      dock: prev.dock.filter((p) => p !== pkg),
      newBadgePkgs: prev.newBadgePkgs.filter((p) => p !== pkg),
      pages: prev.pages.map((p) =>
        p
          .map((slot) => {
            if (slot.kind === 'app') return slot.pkg === pkg ? null : slot;
            const remaining = slot.apps.filter((fp) => fp !== pkg);
            if (remaining.length === 0) return null;
            if (remaining.length === 1) return { kind: 'app' as const, pkg: remaining[0] };
            return { ...slot, apps: remaining };
          })
          .filter(Boolean) as HomeSlot[]
      ),
    }));
    setContextTarget(null);
    showToast(isRtl ? 'برنامه حذف شد' : 'App deleted');
  };

  const handleRemoveAppFromFolder = (pageIdx: number, folderId: string, pkg: string) => {
    setLayout((prev) => {
      let dissolved = false;
      const nextPages = prev.pages.map((p, idx) => {
        if (idx !== pageIdx) return p;
        return p
          .map((slot) => {
            if (slot.kind !== 'folder' || slot.folderId !== folderId) return slot;
            const remaining = slot.apps.filter((a) => a !== pkg);
            if (remaining.length <= 1) {
              dissolved = true;
              return remaining.length === 1 ? ({ kind: 'app', pkg: remaining[0] } as HomeSlot) : null;
            }
            return { ...slot, apps: remaining };
          })
          .filter(Boolean) as HomeSlot[];
      });
      if (dissolved) setOpenFolderId(null);
      return { ...prev, pages: nextPages };
    });
  };

  const handleRenameFolder = (pageIdx: number, folderId: string, newTitle: string) => {
    setLayout((prev) => ({
      ...prev,
      pages: prev.pages.map((p, idx) =>
        idx !== pageIdx
          ? p
          : p.map((s) => (s.kind === 'folder' && s.folderId === folderId ? { ...s, title: newTitle } : s))
      ),
    }));
  };

  const handleDropCell = (pageIdx: number, fromIdx: number, toIdx: number, mergeIntoFolder: boolean) => {
    if (fromIdx === toIdx) return;
    setLayout((prev) => {
      if (pageIdx < 0 || pageIdx >= prev.pages.length || !prev.pages[pageIdx]) return prev;
      const page = [...prev.pages[pageIdx]];
      const src = page[fromIdx];
      const dst = page[toIdx];
      if (!src || !dst) return prev;

      if (mergeIntoFolder && src.kind === 'app') {
        if (dst.kind === 'folder') {
          if (!dst.apps.includes(src.pkg)) {
            page[toIdx] = { ...dst, apps: [...dst.apps, src.pkg] };
            page.splice(fromIdx, 1);
          }
        } else if (dst.kind === 'app') {
          page[toIdx] = {
            kind: 'folder',
            folderId: `f_${Date.now()}`,
            title: isRtl ? 'پوشه' : 'Folder',
            apps: [dst.pkg, src.pkg],
          };
          page.splice(fromIdx, 1);
        }
      } else {
        const [moved] = page.splice(fromIdx, 1);
        const adjustedTo = Math.max(0, Math.min(page.length, toIdx));
        page.splice(adjustedTo, 0, moved);
      }

      const nextPages = prev.pages.map((p, idx) => (idx === pageIdx ? page : p));
      return { ...prev, pages: nextPages };
    });
  };

  const handleAddEmptyPage = () => {
    const newPageIdx = layout.pages.length;
    setLayout((prev) => ({
      ...prev,
      pages: [...prev.pages, []],
    }));
    setTimeout(() => setPagerIdx(homeStartVirtualIdx + newPageIdx), 0);
  };

  const handleRemoveEmptyPage = (pageIdx: number) => {
    setLayout((prev) => {
      if (prev.pages.length <= 1 || pageIdx < 0 || pageIdx >= prev.pages.length) return prev;
      if (prev.pages[pageIdx].length > 0) return prev;
      return {
        ...prev,
        pages: prev.pages.filter((_, idx) => idx !== pageIdx),
      };
    });
  };

  const handleAddWidget = (kind: WidgetKind, span: WidgetSpan) => {
    const labels: Record<WidgetKind, { en: string; fa: string }> = {
      clock: { en: 'Clock & Date', fa: 'ساعت و تاریخ' },
      suggestions: { en: 'Siri Suggestions', fa: 'پیشنهادهای هوشمند' },
      vault: { en: 'Offline Vault', fa: 'وضعیت هسته آفلاین' },
      notes: { en: 'Quick Note', fa: 'یادداشت سریع' },
    };
    const item: WidgetItem = {
      id: `w_${Date.now()}`,
      kind,
      label: labels[kind].en,
      faLabel: labels[kind].fa,
      span,
    };
    setLayout((prev) => {
      if (widgetTargetHome) {
        return { ...prev, homeWidget: item };
      }
      return { ...prev, todayWidgets: [...prev.todayWidgets, item] };
    });
    setWidgetPickerOpen(false);
    if (widgetTargetHome) {
      goToHomePage(0);
    } else {
      goToToday();
    }
  };

  const handleCycleWidgetSpan = (widgetId: string, isHome = false) => {
    const order: WidgetSpan[] = ['small', 'medium', 'large'];
    setLayout((prev) => {
      if (isHome && prev.homeWidget) {
        const nextSpan = order[(order.indexOf(prev.homeWidget.span) + 1) % order.length];
        return { ...prev, homeWidget: { ...prev.homeWidget, span: nextSpan } };
      }
      return {
        ...prev,
        todayWidgets: prev.todayWidgets.map((w) =>
          w.id === widgetId ? { ...w, span: order[(order.indexOf(w.span) + 1) % order.length] } : w
        ),
      };
    });
  };

  const handleRemoveWidget = (widgetId: string, isHome = false) => {
    setLayout((prev) => {
      if (isHome) return { ...prev, homeWidget: null };
      return { ...prev, todayWidgets: prev.todayWidgets.filter((w) => w.id !== widgetId) };
    });
  };

  const toggleMuteBadge = (pkg: string) => {
    const current = prefs.mutedBadgePackages || [];
    const next = current.includes(pkg) ? current.filter((p) => p !== pkg) : [...current, pkg];
    updatePreferences({ mutedBadgePackages: next });
  };

  const getBadgeCount = (pkg: string): number => {
    if (prefs.notificationBadges === false) return 0;
    if ((prefs.mutedBadgePackages || []).includes(pkg)) return 0;
    return DEFAULT_NOTIFICATION_COUNTS[pkg] || 0;
  };

  const iconShapeRadius = {
    squircle: '22.5%',
    circle: '9999px',
    'rounded-square': '18%',
    system: '12px',
  }[prefs.iconShape || 'squircle'];

  const renderAppTileIcon = (app: AppEntry, sizePx = 52) => {
    const Glyph = getAppGlyph(app.package, app.label);
    const isPressed = pressedPkg === app.package;
    const isInsta = app.package.includes('instagram');
    const isWork = WORK_PROFILE_PKGS.has(app.package);
    const badgeCount = getBadgeCount(app.package);

    return (
      <div
        className={`relative flex items-center justify-center shrink-0 transition-transform duration-150 shadow-md ${
          isPressed ? 'scale-90' : 'scale-100'
        }`}
        style={{
          width: `${sizePx}px`,
          height: `${sizePx}px`,
          borderRadius: iconShapeRadius,
          background: isInsta
            ? 'linear-gradient(135deg, #F58529 0%, #DD2A7B 55%, #8134AF 100%)'
            : app.iconColor || '#007AFF',
          color: '#FFFFFF',
        }}
      >
        <Glyph style={{ width: `${Math.round(sizePx * 0.48)}px`, height: `${Math.round(sizePx * 0.48)}px` }} />

        {/* Notification Count Badge (#FF3B30) */}
        {badgeCount > 0 && !isEditMode && (
          <span
            className="absolute -top-1 -end-1 min-w-[18px] h-[18px] px-1 rounded-full bg-[#FF3B30] text-white text-[10px] font-bold flex items-center justify-center shadow ring-1 ring-black/20 tabular-nums z-10"
          >
            {badgeCount > 99 ? '99+' : badgeCount}
          </span>
        )}

        {/* Work Profile Briefcase Badge */}
        {isWork && (
          <span
            className={`absolute -bottom-0.5 -end-0.5 w-[14px] h-[14px] rounded-full flex items-center justify-center shadow ring-1 ring-black/20 ${
              isLight ? 'bg-white text-[#007AFF]' : 'bg-[#1C1C1E] text-[#0A84FF]'
            }`}
          >
            <Briefcase className="w-2.5 h-2.5" />
          </span>
        )}
      </div>
    );
  };

  // App Library grouping
  const libraryGroups = useMemo(() => {
    const buckets: Record<LibraryCategoryKey, AppEntry[]> = {
      suggestions: [],
      productivity: [],
      social: [],
      creativity: [],
      utilities: [],
      other: [],
    };
    const topSug = engine.topApps(12);
    buckets.suggestions = topSug.map((r) => appMap.get(r.package)).filter(Boolean) as AppEntry[];

    for (const app of installedApps) {
      const cat = classifyLibraryCategory(app);
      buckets[cat].push(app);
    }
    return buckets;
  }, [installedApps, engine, appMap]);

  const activeFolderSlot = useMemo(() => {
    if (!openFolderId) return null;
    const page = layout.pages[openFolderId.pageIdx];
    const found = page?.find((s) => s.kind === 'folder' && s.folderId === openFolderId.folderId);
    return found && found.kind === 'folder' ? found : null;
  }, [openFolderId, layout.pages]);

  const spotlightSiriApps = useMemo(() => {
    return engine
      .topApps(8)
      .map((r) => appMap.get(r.package))
      .filter(Boolean) as AppEntry[];
  }, [engine, appMap]);

  const spotlightSplit = useMemo(() => {
    const trimmed = spotlightQuery.trim();
    if (!trimmed) {
      return { topHit: null, apps: [] as SearchResult[], settings: [] as SearchResult[], contacts: [] as SearchResult[] };
    }
    const filtered = spotlightResults.filter((r) =>
      prefs.spotlightIncludeSettings === false ? r.kind !== 'Setting' : true
    );
    const topHit = filtered[0] || null;
    const rest = filtered.slice(1);
    return {
      topHit,
      apps: rest.filter((r) => r.kind === 'App'),
      settings: rest.filter((r) => r.kind === 'Setting'),
      contacts: rest.filter((r) => r.kind === 'Contact'),
    };
  }, [spotlightQuery, spotlightResults, prefs.spotlightIncludeSettings]);

  const renderWidgetCard = (w: WidgetItem, isHome = false) => {
    const heightClass =
      w.span === 'small' ? 'min-h-[82px]' : w.span === 'large' ? 'min-h-[154px]' : 'min-h-[112px]';
    const cardSurface = isLight
      ? isBlur
        ? 'bg-white/75 backdrop-blur-2xl text-black border-black/[0.08]'
        : 'bg-white text-black border-black/10'
      : isAmoled
      ? 'bg-[#111113]/95 text-white border-white/10'
      : isBlur
      ? 'bg-[#1C1C1E]/75 backdrop-blur-2xl text-white border-white/15'
      : 'bg-[#1C1C1E]/95 text-white border-white/10';

    return (
      <div
        key={w.id}
        className={`rounded-[22px] border p-3.5 relative ${heightClass} ${cardSurface} transition-all`}
      >
        <div className="flex items-center justify-between mb-2">
          <span className="text-[11px] font-semibold uppercase tracking-wide opacity-65">
            {isRtl ? w.faLabel : w.label}
          </span>
          {isEditMode && (
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => handleCycleWidgetSpan(w.id, isHome)}
                className="px-1.5 py-0.5 rounded-md text-[10px] bg-[#007AFF]/20 text-[#007AFF] font-semibold"
              >
                {w.span}
              </button>
              <button
                type="button"
                onClick={() => handleRemoveWidget(w.id, isHome)}
                className="w-5 h-5 rounded-full bg-[#FF3B30] text-white flex items-center justify-center"
                aria-label="Remove widget"
              >
                <Minus className="w-3 h-3" />
              </button>
            </div>
          )}
        </div>

        {w.kind === 'clock' && (
          <div className="flex items-end justify-between">
            <div>
              <div className="text-[36px] leading-none font-light tracking-tight tabular-nums">12:24</div>
              <div className="text-xs opacity-60 mt-1">{isRtl ? 'دوشنبه ۲۶ آبان' : 'Monday, Nov 16'}</div>
            </div>
            <Clock className="w-6 h-6 text-[#007AFF] opacity-80" />
          </div>
        )}

        {w.kind === 'suggestions' && (
          <div className="grid grid-cols-4 gap-2 pt-1">
            {spotlightSiriApps.slice(0, w.span === 'large' ? 8 : 4).map((app) => (
              <button
                key={app.package}
                type="button"
                onClick={() => handleLaunchApp(app.package)}
                className="flex flex-col items-center gap-1"
              >
                {renderAppTileIcon(app, 38)}
                <span className="text-[10px] truncate w-full text-center opacity-85">{app.label}</span>
              </button>
            ))}
          </div>
        )}

        {w.kind === 'vault' && (
          <div className="flex items-center justify-between gap-2 text-xs">
            <div className="space-y-0.5">
              <div className="font-semibold text-[#34C759] flex items-center gap-1">
                <Shield className="w-3.5 h-3.5" />
                <span>{isRtl ? 'صفر دسترسی شبکه' : 'Zero Network Access'}</span>
              </div>
              <div className="text-[11px] opacity-65 tabular-nums">
                {installedApps.length} {isRtl ? 'برنامه ایندکس‌شده' : 'apps indexed'} · {latencyMs.toFixed(2)}ms
              </div>
            </div>
            <span className="px-2 py-1 rounded-lg bg-[#34C759]/15 text-[#34C759] font-mono text-[10px] font-bold">
              RUST
            </span>
          </div>
        )}

        {w.kind === 'notes' && (
          <p className="text-xs opacity-75 leading-relaxed">
            {isRtl
              ? 'یادداشت آفلاین کیومرث: تمام داده‌ها فقط روی دستگاه ذخیره می‌شوند.'
              : 'Offline Note: Zero telemetry, pure Nucleo fuzzy matching (< 1 ms).'}
          </p>
        )}
      </div>
    );
  };

  // Wallpaper Parallax Factor: 0.5x of virtual page progression
  const parallaxShift = -(pagerIdx * 4);
  const dimOpacity = Math.max(0, Math.min(0.4, (prefs.wallpaperDim ?? 0) / 100));
  const wallBlurPx = Math.max(0, Math.min(16, prefs.wallpaperBlur ?? 0));

  const labelShadowClass = isLight
    ? 'text-slate-900 font-medium'
    : 'text-white font-medium drop-shadow-[0_1px_2px_rgba(0,0,0,0.85)]';

  return (
    <div className="flex flex-col gap-6 items-center w-full">
      {/* iOS 17/18 LAUNCHER CONTROL & QUICK-JUMP STRIP */}
      <div className="w-full max-w-6xl bg-slate-900/90 border border-slate-800 rounded-2xl p-4 space-y-3 text-xs">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <span className="font-bold text-slate-100 text-sm">
                {isRtl ? 'کیومرث ۲.۰ — شبیه‌ساز کامل iOS (California Neutral)' : 'Kayumars 2.0 — California Neutral iOS Launcher'}
              </span>
              <span className="px-2 py-0.5 rounded-md bg-[#007AFF]/20 text-[#5AC8FA] font-mono text-[10px] font-semibold">
                v2.0
              </span>
            </div>
            <span className="text-slate-400 text-[11px] block mt-0.5">
              {isRtl
                ? 'صفحه Today + چند صفحه خانه + کتابخانه برنامه‌ها + اسپات‌لایت هسته Rust + پوشه و جابه‌جایی لمسی'
                : 'Today View · Multi-page Home + Folders · Frosted Dock · App Library · Nucleo Spotlight · Edit/Jiggle'}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={handleSimulateInstallApp}
              className="px-2.5 py-1.5 rounded-lg bg-[#34C759]/15 hover:bg-[#34C759]/25 text-[#34C759] border border-[#34C759]/30 font-semibold flex items-center gap-1 transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>{isRtl ? 'نصب برنامه آزمایشی (نقطه آبی)' : 'Install App (Auto-place + Dot)'}</span>
            </button>
            <button
              type="button"
              onClick={() => updatePreferences({ localeMode: isRtl ? 'en' : 'fa' })}
              className="px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-semibold"
            >
              {isRtl ? 'LTR (English)' : 'RTL (فارسی)'}
            </button>
            <span className="flex items-center gap-1 font-mono text-cyan-400 font-bold tabular-nums px-2">
              <Zap className="w-3.5 h-3.5" />
              <span>{latencyMs.toFixed(2)} ms</span>
            </span>
          </div>
        </div>

        {/* Surface Quick-Jump Chips */}
        <div className="flex flex-wrap gap-1.5">
          {[
            { id: 'today', label: isRtl ? '۱. نمای امروز (Today)' : '1. Today View', active: activeVirtualKind === 'today' && !spotlightOpen && !settingsOpen },
            { id: 'home', label: isRtl ? '۲. صفحه اصلی (Home)' : '2. Home Grid', active: activeVirtualKind === 'home' && !isEditMode && !spotlightOpen && !settingsOpen && !openFolderId && !contextTarget },
            { id: 'library', label: isRtl ? '۳. کتابخانه (App Library)' : '3. App Library', active: activeVirtualKind === 'library' && !spotlightOpen && !settingsOpen },
            { id: 'spotlight', label: isRtl ? '۴. جستجو (Spotlight)' : '4. Spotlight Search', active: spotlightOpen },
            { id: 'folder', label: isRtl ? '۵. پوشه (Folder)' : '5. Open Folder', active: Boolean(openFolderId) },
            { id: 'edit', label: isRtl ? '۶. حالت ویرایش (Jiggle)' : '6. Edit / Jiggle Mode', active: isEditMode },
            { id: 'context', label: isRtl ? '۷. منوی لمس طولانی' : '7. Context Menu', active: Boolean(contextTarget) },
            { id: 'overview', label: isRtl ? '۸. نمای کلی صفحات' : '8. Page Overview', active: overviewOpen },
            { id: 'settings', label: isRtl ? '۹. تنظیمات iOS' : '9. Settings', active: settingsOpen },
          ].map((btn) => (
            <button
              key={btn.id}
              type="button"
              onClick={() => {
                setContextTarget(null);
                setOpenFolderId(null);
                setOverviewOpen(false);
                setAppInfoTarget(null);
                if (btn.id === 'today') {
                  setIsEditMode(false);
                  goToToday();
                } else if (btn.id === 'home') {
                  setIsEditMode(false);
                  goToHomePage(0);
                } else if (btn.id === 'library') {
                  setIsEditMode(false);
                  goToLibrary();
                } else if (btn.id === 'spotlight') {
                  openSpotlight('tele');
                } else if (btn.id === 'folder') {
                  goToHomePage(0);
                  const folderSlot = layout.pages[0]?.find((s) => s.kind === 'folder');
                  if (folderSlot && folderSlot.kind === 'folder') {
                    setFolderSubPage(0);
                    setOpenFolderId({ pageIdx: 0, folderId: folderSlot.folderId });
                  }
                } else if (btn.id === 'edit') {
                  goToHomePage(0);
                  setIsEditMode((v) => !v);
                } else if (btn.id === 'context') {
                  goToHomePage(0);
                  const firstAppSlot = layout.pages[0]?.find((s) => s.kind === 'app');
                  if (firstAppSlot && firstAppSlot.kind === 'app') {
                    const a = appMap.get(firstAppSlot.pkg);
                    if (a) setContextTarget({ app: a, source: 'home', pageIdx: 0, slotIdx: 0 });
                  }
                } else if (btn.id === 'overview') {
                  setOverviewOpen((v) => !v);
                } else if (btn.id === 'settings') {
                  setSettingsOpen(true);
                }
              }}
              className={`px-2.5 py-1.5 rounded-lg text-[11px] font-medium transition-all ${
                btn.active
                  ? 'bg-[#007AFF] text-white font-semibold shadow-sm'
                  : 'bg-slate-800 hover:bg-slate-700 text-slate-300'
              }`}
            >
              {btn.label}
            </button>
          ))}
        </div>
      </div>

      {/* PHONE FRAME + ARCHITECTURE INSPECTOR */}
      <div className="w-full flex flex-col lg:flex-row items-center lg:items-start justify-center gap-8">
        {/* PHONE BEZEL */}
        <div className="w-full max-w-[390px] aspect-[9/19.3] rounded-[48px] p-2.5 bg-slate-900 border-[4px] border-slate-700/80 shadow-2xl relative flex flex-col overflow-hidden select-none">
          {/* Dynamic Island */}
          <div className="absolute top-3.5 left-1/2 -translate-x-1/2 w-[98px] h-[22px] bg-black rounded-full z-30 flex items-center justify-between px-3 pointer-events-none">
            <div className="w-2 h-2 rounded-full bg-slate-800" />
            <div className="w-1.5 h-1.5 rounded-full bg-blue-950" />
          </div>

          {/* SCREEN CANVAS */}
          <div
            className={`w-full h-full rounded-[38px] flex flex-col overflow-hidden relative ${
              isAmoled ? 'bg-black text-white' : isLight ? 'bg-[#F2F2F7] text-black' : 'bg-black text-white'
            }`}
            dir={isRtl ? 'rtl' : 'ltr'}
            onPointerDown={(e) => {
              pagerTouchStartRef.current = { x: e.clientX, y: e.clientY, t: performance.now() };
            }}
            onPointerUp={(e) => {
              const start = pagerTouchStartRef.current;
              pagerTouchStartRef.current = null;
              if (!start || spotlightOpen || settingsOpen || openFolderId || contextTarget || overviewOpen) return;
              const dx = e.clientX - start.x;
              const dy = e.clientY - start.y;
              // Horizontal page swipe
              if (Math.abs(dx) > 48 && Math.abs(dx) > Math.abs(dy) * 1.25) {
                const forward = isRtl ? dx > 0 : dx < 0;
                setPagerIdx((cur) => Math.max(0, Math.min(totalVirtualPages - 1, cur + (forward ? 1 : -1))));
                return;
              }
              // Swipe down from home center opens Spotlight
              if (
                dy > 58 &&
                Math.abs(dy) > Math.abs(dx) * 1.4 &&
                activeVirtualKind === 'home' &&
                prefs.swipeDownSpotlightEnabled !== false
              ) {
                openSpotlight('');
              }
            }}
          >
            {/* CALIFORNIA NEUTRAL WALLPAPER LAYER WITH 0.5x PARALLAX, BLUR & DIM */}
            {!isAmoled && (
              <div
                className={`absolute inset-0 pointer-events-none ${
                  isReducedMotion ? '' : 'transition-transform duration-300 ease-out'
                }`}
                style={{
                  transform: `scale(1.1) translateX(${isRtl ? -parallaxShift : parallaxShift}%)`,
                  filter: wallBlurPx > 0 ? `blur(${wallBlurPx}px)` : undefined,
                }}
              >
                <svg className="w-full h-full" viewBox="0 0 390 844" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
                  <defs>
                    <linearGradient id="iosWall" x1="0" y1="0" x2="1" y2="1">
                      {isLight ? (
                        <>
                          <stop offset="0%" stopColor="#DCE8FF" />
                          <stop offset="45%" stopColor="#F5E1EB" />
                          <stop offset="100%" stopColor="#D6F0EA" />
                        </>
                      ) : (
                        <>
                          <stop offset="0%" stopColor="#0E172A" />
                          <stop offset="48%" stopColor="#1B2A4A" />
                          <stop offset="100%" stopColor="#261C38" />
                        </>
                      )}
                    </linearGradient>
                    <radialGradient id="iosBlob1" cx="25%" cy="22%" r="55%">
                      <stop offset="0%" stopColor={isLight ? '#7FB3FF' : '#0A84FF'} stopOpacity="0.45" />
                      <stop offset="100%" stopColor="transparent" />
                    </radialGradient>
                    <radialGradient id="iosBlob2" cx="78%" cy="74%" r="55%">
                      <stop offset="0%" stopColor={isLight ? '#FF9EBB' : '#AF52DE'} stopOpacity="0.38" />
                      <stop offset="100%" stopColor="transparent" />
                    </radialGradient>
                  </defs>
                  <rect width="390" height="844" fill="url(#iosWall)" />
                  <rect width="390" height="844" fill="url(#iosBlob1)" />
                  <rect width="390" height="844" fill="url(#iosBlob2)" />
                </svg>
              </div>
            )}

            {/* Wallpaper Dim Overlay (0-40%) */}
            {dimOpacity > 0 && !isAmoled && (
              <div
                className="absolute inset-0 bg-black pointer-events-none"
                style={{ opacity: dimOpacity }}
              />
            )}

            {/* iOS Status Bar */}
            <div className="pt-3.5 px-6 pb-1 flex items-center justify-between text-[11px] font-semibold z-20 shrink-0">
              <span className="tabular-nums">12:24</span>
              <div className="flex items-center gap-1.5">
                <span className="text-[10px] font-mono opacity-80">5G</span>
                <span
                  className={`px-1.5 py-0.5 rounded-full text-[10px] tabular-nums ${
                    isLight ? 'bg-black/10 text-black' : 'bg-white/20 text-white'
                  }`}
                >
                  88%
                </span>
              </div>
            </div>

            {/* 120 Hz HUD */}
            {prefs.showMotionHud && (
              <div className="absolute top-10 end-3 z-50 px-2 py-0.5 rounded bg-black/80 border border-[#34C759]/50 font-mono text-[10px] text-[#34C759] tabular-nums pointer-events-none">
                {hudStats.frameMs.toFixed(1)}ms · drop {hudStats.drops}
              </div>
            )}

            {/* EDIT MODE TOP BAR */}
            {isEditMode && activeVirtualKind === 'home' && (
              <div className="px-4 py-1.5 flex items-center justify-between gap-2 z-20 shrink-0">
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => {
                      setWidgetTargetHome(true);
                      setWidgetPickerOpen(true);
                    }}
                    className="px-2.5 py-1 rounded-full bg-white/20 backdrop-blur-md text-[11px] font-semibold flex items-center gap-1"
                  >
                    <Plus className="w-3 h-3" />
                    <span>{isRtl ? 'ویجت' : 'Widget'}</span>
                  </button>
                  <button
                    type="button"
                    onClick={handleAddEmptyPage}
                    className="px-2.5 py-1 rounded-full bg-white/20 backdrop-blur-md text-[11px] font-semibold"
                  >
                    {isRtl ? '+ صفحه' : '+ Page'}
                  </button>
                  {layout.pages.length > 1 && layout.pages[activeHomePageIdx]?.length === 0 && (
                    <button
                      type="button"
                      onClick={() => handleRemoveEmptyPage(activeHomePageIdx)}
                      className="px-2 py-1 rounded-full bg-[#FF3B30]/85 text-white text-[11px] font-semibold"
                    >
                      {isRtl ? 'حذف صفحه' : 'Del Page'}
                    </button>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => setIsEditMode(false)}
                  className="px-3.5 py-1 rounded-full bg-[#007AFF] text-white text-[12px] font-semibold shadow"
                >
                  {isRtl ? 'تایید' : 'Done'}
                </button>
              </div>
            )}

            {/* ==================== PAGE CONTENT ==================== */}
            <div
              className="flex-1 flex flex-col overflow-hidden relative z-10"
              onClick={() => {
                if (activeVirtualKind !== 'home') return;
                const now = Date.now();
                if (now - emptyTapRef.current < 280 && prefs.gestures.doubleTapLock && !isEditMode) {
                  setLockOverlayOpen(true);
                }
                emptyTapRef.current = now;
              }}
              onContextMenu={(e) => {
                if (activeVirtualKind === 'home') {
                  e.preventDefault();
                  setIsEditMode((v) => !v);
                }
              }}
            >
              {/* ---------- TODAY VIEW (-1) ---------- */}
              {activeVirtualKind === 'today' && (
                <div className="flex-1 overflow-y-auto px-4 pt-2 pb-4 space-y-3" onClick={(e) => e.stopPropagation()}>
                  <div className="flex items-center justify-between">
                    <h2 className="text-[20px] font-bold tracking-tight">
                      {isRtl ? 'نمای امروز' : 'Today View'}
                    </h2>
                    <button
                      type="button"
                      onClick={() => setSettingsOpen(true)}
                      className="w-8 h-8 rounded-full bg-white/15 backdrop-blur-md flex items-center justify-center"
                      aria-label="Settings"
                    >
                      <Settings className="w-4 h-4" />
                    </button>
                  </div>

                  {/* Quick Spotlight Trigger */}
                  <button
                    type="button"
                    onClick={() => openSpotlight('')}
                    className={`w-full h-[38px] rounded-[12px] px-3 flex items-center gap-2 text-xs ${
                      isLight ? 'bg-black/[0.08] text-black/65' : 'bg-white/15 text-white/70'
                    }`}
                  >
                    <Search className="w-3.5 h-3.5" />
                    <span>{isRtl ? 'جستجوی اسپات‌لایت...' : 'Search'}</span>
                  </button>

                  {layout.todayWidgets.map((w) => renderWidgetCard(w, false))}

                  <div className="flex items-center gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => {
                        setWidgetTargetHome(false);
                        setWidgetPickerOpen(true);
                      }}
                      className="flex-1 py-2.5 rounded-full bg-[#007AFF] text-white text-xs font-semibold flex items-center justify-center gap-1 shadow"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      <span>{isRtl ? 'افزودن ویجت' : 'Add Widget'}</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setIsEditMode((v) => !v)}
                      className={`px-4 py-2.5 rounded-full text-xs font-semibold ${
                        isLight ? 'bg-black/10 text-black' : 'bg-white/15 text-white'
                      }`}
                    >
                      {isEditMode ? (isRtl ? 'تایید' : 'Done') : isRtl ? 'ویرایش' : 'Edit'}
                    </button>
                  </div>
                </div>
              )}

              {/* ---------- HOME PAGES (0..N) ---------- */}
              {activeVirtualKind === 'home' && (
                <div className="flex-1 flex flex-col justify-between px-4 pt-2 pb-1 overflow-y-auto">
                  <div className="space-y-3">
                    {/* Optional Home Widget on Page 0 */}
                    {activeHomePageIdx === 0 && layout.homeWidget && (
                      <div onClick={(e) => e.stopPropagation()}>{renderWidgetCard(layout.homeWidget, true)}</div>
                    )}

                    {/* Home App & Folder Grid */}
                    <div
                      className="grid gap-y-3.5 gap-x-2 pt-1"
                      style={{ gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))` }}
                    >
                      {(layout.pages[activeHomePageIdx] || []).map((slot, slotIdx) => {
                        const isDraggingThis = dragItem?.pageIdx === activeHomePageIdx && dragItem.slotIdx === slotIdx;
                        const isDropTarget = hoverSlotIdx === slotIdx && dragItem && dragItem.slotIdx !== slotIdx;
                        const staggerDelayMs = (slotIdx % 8) * 40;

                        if (slot.kind === 'folder') {
                          return (
                            <div
                              key={slot.folderId}
                              data-slot-idx={slotIdx}
                              className={`flex flex-col items-center gap-1 relative ${
                                isEditMode && !isReducedMotion ? 'animate-ios-jiggle' : ''
                              }`}
                              style={{ animationDelay: `${staggerDelayMs}ms` }}
                              onClick={(e) => e.stopPropagation()}
                              onPointerDown={() => {
                                if (isEditMode) {
                                  setDragItem({ pageIdx: activeHomePageIdx, slotIdx });
                                } else {
                                  clearTimeout(longPressTimerRef.current);
                                  longPressTimerRef.current = setTimeout(() => setIsEditMode(true), 420);
                                }
                              }}
                              onPointerMove={(e) => {
                                if (!dragItem) return;
                                const el = document.elementFromPoint(e.clientX, e.clientY)?.closest('[data-slot-idx]');
                                const idxAttr = el?.getAttribute('data-slot-idx');
                                if (idxAttr !== null && idxAttr !== undefined) {
                                  const targetIdx = Number(idxAttr);
                                  if (targetIdx !== hoverSlotIdx) {
                                    setHoverSlotIdx(targetIdx);
                                    clearTimeout(hoverFolderTimerRef.current);
                                    setHoverFolderReady(false);
                                    hoverFolderTimerRef.current = setTimeout(() => setHoverFolderReady(true), 150);
                                  }
                                }
                              }}
                              onPointerUp={() => {
                                clearTimeout(longPressTimerRef.current);
                                clearTimeout(hoverFolderTimerRef.current);
                                if (dragItem && hoverSlotIdx !== null && hoverSlotIdx !== dragItem.slotIdx) {
                                  handleDropCell(activeHomePageIdx, dragItem.slotIdx, hoverSlotIdx, hoverFolderReady);
                                }
                                setDragItem(null);
                                setHoverSlotIdx(null);
                                setHoverFolderReady(false);
                              }}
                            >
                              {isEditMode && (
                                <button
                                  type="button"
                                  onClick={() => handleRemoveSlotFromPage(activeHomePageIdx, slotIdx)}
                                  className="absolute -top-1.5 start-1 z-20 w-[20px] h-[20px] rounded-full bg-[#FF3B30] text-white flex items-center justify-center shadow"
                                  aria-label="Remove folder"
                                >
                                  <Minus className="w-3 h-3" />
                                </button>
                              )}

                              <button
                                type="button"
                                onClick={() => {
                                  if (!isEditMode) {
                                    setFolderSubPage(0);
                                    setOpenFolderId({ pageIdx: activeHomePageIdx, folderId: slot.folderId });
                                  }
                                }}
                                className={`w-[54px] h-[54px] p-1.5 grid grid-cols-3 gap-[2.5px] content-start transition-all ${
                                  isLight ? 'bg-white/65 border border-black/10' : 'bg-white/20 border border-white/15'
                                } ${isBlur ? 'backdrop-blur-xl' : ''} ${
                                  isDropTarget && hoverFolderReady ? 'ring-2 ring-[#007AFF] scale-105' : ''
                                } ${isDraggingThis ? 'opacity-65 scale-105' : ''}`}
                                style={{ borderRadius: '22.5%' }}
                              >
                                {slot.apps.slice(0, 9).map((fPkg) => {
                                  const fApp = appMap.get(fPkg);
                                  return (
                                    <span
                                      key={fPkg}
                                      className="w-full aspect-square rounded-[3px] flex items-center justify-center text-[7px] text-white font-bold"
                                      style={{ backgroundColor: fApp?.iconColor || '#007AFF' }}
                                    >
                                      {fApp?.label?.[0] || 'A'}
                                    </span>
                                  );
                                })}
                              </button>

                              {prefs.showIconLabels !== false && (
                                <span className={`text-[11px] truncate max-w-[66px] ${labelShadowClass}`}>
                                  {slot.title}
                                </span>
                              )}
                            </div>
                          );
                        }

                        const app = appMap.get(slot.pkg);
                        if (!app) return null;
                        const isNewDot = prefs.newAppDots !== false && layout.newBadgePkgs.includes(app.package);

                        return (
                          <div
                            key={app.package}
                            data-slot-idx={slotIdx}
                            className={`flex flex-col items-center gap-1 relative ${
                              isEditMode && !isReducedMotion ? 'animate-ios-jiggle' : ''
                            }`}
                            style={{ animationDelay: `${staggerDelayMs}ms` }}
                            onClick={(e) => e.stopPropagation()}
                            onPointerDown={() => {
                              clearTimeout(longPressTimerRef.current);
                              if (isEditMode) {
                                setDragItem({ pageIdx: activeHomePageIdx, slotIdx });
                              } else {
                                longPressTimerRef.current = setTimeout(() => {
                                  setContextTarget({
                                    app,
                                    source: 'home',
                                    pageIdx: activeHomePageIdx,
                                    slotIdx,
                                  });
                                }, 350);
                              }
                            }}
                            onPointerMove={(e) => {
                              if (!dragItem) return;
                              const el = document.elementFromPoint(e.clientX, e.clientY)?.closest('[data-slot-idx]');
                              const idxAttr = el?.getAttribute('data-slot-idx');
                              if (idxAttr !== null && idxAttr !== undefined) {
                                const targetIdx = Number(idxAttr);
                                if (targetIdx !== hoverSlotIdx) {
                                  setHoverSlotIdx(targetIdx);
                                  clearTimeout(hoverFolderTimerRef.current);
                                  setHoverFolderReady(false);
                                  hoverFolderTimerRef.current = setTimeout(() => setHoverFolderReady(true), 150);
                                }
                              }
                            }}
                            onPointerUp={() => {
                              clearTimeout(longPressTimerRef.current);
                              clearTimeout(hoverFolderTimerRef.current);
                              if (dragItem && hoverSlotIdx !== null && hoverSlotIdx !== dragItem.slotIdx) {
                                handleDropCell(activeHomePageIdx, dragItem.slotIdx, hoverSlotIdx, hoverFolderReady);
                              }
                              setDragItem(null);
                              setHoverSlotIdx(null);
                              setHoverFolderReady(false);
                            }}
                            onContextMenu={(e) => {
                              e.preventDefault();
                              e.stopPropagation();
                              clearTimeout(longPressTimerRef.current);
                              setContextTarget({ app, source: 'home', pageIdx: activeHomePageIdx, slotIdx });
                            }}
                          >
                            {isEditMode && (
                              <button
                                type="button"
                                onClick={() => handleRemoveSlotFromPage(activeHomePageIdx, slotIdx)}
                                className="absolute -top-1.5 start-1 z-20 w-[20px] h-[20px] rounded-full bg-[#FF3B30] text-white flex items-center justify-center shadow"
                                aria-label={`Remove ${app.label}`}
                              >
                                <Minus className="w-3 h-3" />
                              </button>
                            )}

                            <button
                              type="button"
                              onClick={() => {
                                if (!isEditMode && !dragItem) {
                                  handleLaunchApp(app.package);
                                }
                              }}
                              className={`rounded-[22.5%] focus:outline-none transition-transform ${
                                isDropTarget
                                  ? hoverFolderReady
                                    ? 'ring-2 ring-[#007AFF] scale-110'
                                    : 'ring-2 ring-white/50 scale-105'
                                  : ''
                              } ${isDraggingThis ? 'opacity-65 scale-105' : ''}`}
                            >
                              {renderAppTileIcon(app, gridCols >= 6 ? 42 : gridCols === 5 ? 46 : 52)}
                            </button>

                            {prefs.showIconLabels !== false && (
                              <div className="flex items-center justify-center gap-1 max-w-[68px]">
                                {isNewDot && <span className="w-1.5 h-1.5 rounded-full bg-[#007AFF] shrink-0" />}
                                <span className={`text-[11px] truncate ${labelShadowClass}`}>{app.label}</span>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  {/* Page Indicator Dots + Search Pill + Dock */}
                  <div className="space-y-2.5 pt-2 pb-1 shrink-0" onClick={(e) => e.stopPropagation()}>
                    {/* Page Dots + Overview trigger */}
                    <div className="flex items-center justify-center gap-2">
                      <div
                        className={`px-3 py-1 rounded-full flex items-center gap-1.5 ${
                          isLight ? 'bg-black/10' : 'bg-white/10'
                        } ${isBlur ? 'backdrop-blur-md' : ''}`}
                      >
                        {Array.from({ length: totalVirtualPages }).map((_, idx) => {
                          const active = idx === pagerIdx;
                          const isLibDot = showLibrary && idx === libraryVirtualIdx;
                          return (
                            <button
                              key={idx}
                              type="button"
                              onClick={() => setPagerIdx(idx)}
                              aria-label={`Go to page ${idx + 1}`}
                              className="p-0.5 flex items-center justify-center"
                            >
                              {isLibDot ? (
                                <LayoutGrid
                                  className={`w-2.5 h-2.5 ${active ? 'text-[#007AFF] opacity-100' : 'opacity-45'}`}
                                />
                              ) : (
                                <span
                                  className={`w-[5px] h-[5px] rounded-full transition-all ${
                                    active
                                      ? isLight
                                        ? 'bg-black scale-125'
                                        : 'bg-white scale-125'
                                      : isLight
                                      ? 'bg-black/30'
                                      : 'bg-white/35'
                                  }`}
                                />
                              )}
                            </button>
                          );
                        })}
                      </div>
                      {prefs.pinchOverviewEnabled !== false && (
                        <button
                          type="button"
                          onClick={() => setOverviewOpen(true)}
                          className={`p-1 rounded-full ${isLight ? 'bg-black/10 text-black/70' : 'bg-white/10 text-white/75'}`}
                          title={isRtl ? 'نمای کلی صفحات' : 'Page Overview'}
                        >
                          <Layers className="w-3 h-3" />
                        </button>
                      )}
                    </div>

                    {/* Optional Search Pill Above Dock (iOS 18 style) */}
                    {prefs.showSearchPill !== false && !isEditMode && (
                      <div className="flex justify-center">
                        <button
                          type="button"
                          onClick={() => openSpotlight('')}
                          className={`px-3.5 h-[28px] rounded-full flex items-center gap-1.5 text-[11px] font-medium border ${
                            isLight
                              ? 'bg-white/65 border-black/10 text-black/80'
                              : 'bg-white/15 border-white/15 text-white/90'
                          } ${isBlur ? 'backdrop-blur-xl' : ''}`}
                        >
                          <Search className="w-3 h-3 opacity-80" />
                          <span>{isRtl ? 'جستجو' : 'Search'}</span>
                        </button>
                      </div>
                    )}

                    {/* Frosted Capsule Dock (32px radius) */}
                    {prefs.showDock !== false && (
                      <div
                        className={`rounded-[30px] px-3 py-2.5 border flex items-center justify-around ${
                          isLight
                            ? isBlur
                              ? 'bg-white/70 backdrop-blur-2xl border-black/[0.08]'
                              : 'bg-white/90 border-black/10'
                            : isAmoled
                            ? 'bg-[#111113]/95 border-white/15'
                            : isBlur
                            ? 'bg-white/18 backdrop-blur-2xl border-white/15'
                            : 'bg-[#1C1C1E]/95 border-white/15'
                        }`}
                      >
                        {layout.dock.slice(0, dockCap).map((dPkg) => {
                          const app = appMap.get(dPkg);
                          if (!app) return null;
                          return (
                            <div
                              key={dPkg}
                              className={`relative ${isEditMode && !isReducedMotion ? 'animate-ios-jiggle' : ''}`}
                              onContextMenu={(e) => {
                                e.preventDefault();
                                e.stopPropagation();
                                setContextTarget({ app, source: 'dock' });
                              }}
                            >
                              {isEditMode && (
                                <button
                                  type="button"
                                  onClick={() => handleRemoveFromDock(dPkg)}
                                  className="absolute -top-1.5 -start-1.5 z-20 w-[18px] h-[18px] rounded-full bg-[#FF3B30] text-white flex items-center justify-center shadow"
                                  aria-label={`Remove ${app.label} from dock`}
                                >
                                  <Minus className="w-3 h-3" />
                                </button>
                              )}
                              <button
                                type="button"
                                onClick={() => !isEditMode && handleLaunchApp(app.package)}
                                className="focus:outline-none"
                              >
                                {renderAppTileIcon(app, dockCap >= 6 ? 42 : 48)}
                              </button>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* ---------- APP LIBRARY (+1) ---------- */}
              {activeVirtualKind === 'library' && (
                <div className="flex-1 flex flex-col px-4 pt-2 pb-4 overflow-hidden" onClick={(e) => e.stopPropagation()}>
                  <div className="flex items-center gap-2 mb-3 shrink-0">
                    <div
                      className={`flex-1 h-[40px] rounded-[12px] px-3 flex items-center gap-2 ${
                        isLight ? 'bg-black/[0.08]' : 'bg-[#2C2C2E]/90'
                      }`}
                    >
                      <Search className="w-4 h-4 opacity-55 shrink-0" />
                      <input
                        value={libSearchQuery}
                        onChange={(e) => setLibSearchQuery(e.target.value)}
                        placeholder={isRtl ? 'کتابخانه برنامه‌ها' : 'App Library'}
                        className="flex-1 bg-transparent text-sm focus:outline-none placeholder:opacity-55"
                      />
                      {libSearchQuery && (
                        <button type="button" onClick={() => setLibSearchQuery('')}>
                          <X className="w-3.5 h-3.5 opacity-60" />
                        </button>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={() => setSettingsOpen(true)}
                      className={`w-10 h-10 rounded-xl flex items-center justify-center ${
                        isLight ? 'bg-black/[0.08]' : 'bg-white/10'
                      }`}
                      aria-label="Open Settings"
                    >
                      <Settings className="w-4 h-4" />
                    </button>
                  </div>

                  {libSearchQuery.trim() ? (
                    <div className="flex-1 overflow-y-auto space-y-1">
                      {installedApps
                        .filter((a) => a.label.toLowerCase().includes(libSearchQuery.trim().toLowerCase()))
                        .slice(0, 30)
                        .map((app) => (
                          <button
                            key={app.package}
                            type="button"
                            onClick={() => handleLaunchApp(app.package)}
                            onContextMenu={(e) => {
                              e.preventDefault();
                              setContextTarget({ app, source: 'library' });
                            }}
                            className={`w-full px-3 py-2 rounded-xl flex items-center gap-3 text-start ${
                              isLight ? 'hover:bg-black/5' : 'hover:bg-white/10'
                            }`}
                          >
                            {renderAppTileIcon(app, 38)}
                            <div className="min-w-0 flex-1">
                              <div className="text-sm font-medium truncate">{app.label}</div>
                              <div className="text-[11px] opacity-55 truncate">{app.package}</div>
                            </div>
                          </button>
                        ))}
                    </div>
                  ) : (
                    <div className="flex-1 overflow-y-auto grid grid-cols-2 gap-3 content-start pb-2">
                      {layout.categoryOrder.map((catKey) => {
                        const catApps = libraryGroups[catKey];
                        const largeThree = catApps.slice(0, 3);
                        const miniFour = catApps.slice(3, 7);
                        return (
                          <button
                            key={catKey}
                            type="button"
                            onClick={() => setExpandedLibCategory(catKey)}
                            className={`rounded-[16px] p-2.5 border text-start flex flex-col gap-1.5 transition-transform active:scale-95 ${
                              isLight
                                ? 'bg-white/75 border-black/[0.06]'
                                : isAmoled
                                ? 'bg-[#111113] border-white/10'
                                : 'bg-white/12 backdrop-blur-xl border-white/12'
                            }`}
                          >
                            <div className="grid grid-cols-2 gap-2 aspect-square w-full">
                              {largeThree.map((app) => (
                                <div key={app.package} className="flex items-center justify-center">
                                  {renderAppTileIcon(app, 48)}
                                </div>
                              ))}
                              {/* 2x2 Mini Cluster in 4th quadrant */}
                              <div
                                className={`rounded-[14px] p-1 grid grid-cols-2 gap-1 ${
                                  isLight ? 'bg-black/[0.06]' : 'bg-white/10'
                                }`}
                              >
                                {miniFour.map((mApp) => (
                                  <span
                                    key={mApp.package}
                                    className="w-full aspect-square rounded-[5px] flex items-center justify-center text-[8px] font-bold text-white"
                                    style={{ backgroundColor: mApp.iconColor || '#007AFF' }}
                                  >
                                    {mApp.label?.[0] || 'A'}
                                  </span>
                                ))}
                              </div>
                            </div>
                            <span className="text-[11px] font-semibold truncate px-0.5">
                              {isRtl ? CATEGORY_META[catKey]?.fa || catKey : CATEGORY_META[catKey]?.en || catKey}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* ==================== EXPANDED APP LIBRARY CATEGORY MODAL ==================== */}
            {expandedLibCategory && (
              <div
                className={`absolute inset-0 z-30 flex flex-col p-4 ${
                  isLight ? 'bg-white/85 text-black' : 'bg-black/80 text-white'
                } ${isBlur ? 'backdrop-blur-2xl' : ''}`}
                onClick={() => setExpandedLibCategory(null)}
              >
                <div className="pt-4 pb-3 flex items-center justify-between" onClick={(e) => e.stopPropagation()}>
                  <h3 className="text-[22px] font-bold">
                    {isRtl ? CATEGORY_META[expandedLibCategory].fa : CATEGORY_META[expandedLibCategory].en}
                  </h3>
                  <button
                    type="button"
                    onClick={() => setExpandedLibCategory(null)}
                    className="px-3 py-1 rounded-full bg-[#007AFF] text-white text-xs font-semibold"
                  >
                    {isRtl ? 'بستن' : 'Close'}
                  </button>
                </div>
                <div
                  className="flex-1 overflow-y-auto grid grid-cols-4 gap-3 content-start"
                  onClick={(e) => e.stopPropagation()}
                >
                  {libraryGroups[expandedLibCategory].slice(0, 32).map((app) => (
                    <button
                      key={app.package}
                      type="button"
                      onClick={() => handleLaunchApp(app.package)}
                      onContextMenu={(e) => {
                        e.preventDefault();
                        setContextTarget({ app, source: 'library' });
                      }}
                      className="flex flex-col items-center gap-1"
                    >
                      {renderAppTileIcon(app, 50)}
                      <span className="text-[10px] truncate w-full text-center">{app.label}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* ==================== ZOOM-MORPH FOLDER MODAL ==================== */}
            {activeFolderSlot && openFolderId && (
              <div
                className={`absolute inset-0 z-30 flex flex-col items-center justify-center p-5 ${
                  isLight ? 'bg-white/60 text-black' : 'bg-black/65 text-white'
                } ${isBlur ? 'backdrop-blur-2xl' : ''}`}
                onClick={() => setOpenFolderId(null)}
              >
                <input
                  value={activeFolderSlot.title}
                  onChange={(e) => handleRenameFolder(openFolderId.pageIdx, activeFolderSlot.folderId, e.target.value)}
                  onClick={(e) => e.stopPropagation()}
                  className="text-[26px] font-semibold bg-transparent text-center focus:outline-none border-b border-transparent focus:border-[#007AFF] mb-4 max-w-[260px]"
                />
                <div
                  className={`w-full rounded-[32px] p-5 border ${
                    isLight ? 'bg-white/80 border-black/10' : 'bg-[#1C1C1E]/85 border-white/15'
                  }`}
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="grid grid-cols-3 gap-4 min-h-[210px] content-start">
                    {activeFolderSlot.apps
                      .slice(folderSubPage * 9, folderSubPage * 9 + 9)
                      .map((fPkg) => {
                        const app = appMap.get(fPkg);
                        if (!app) return null;
                        return (
                          <div key={fPkg} className="flex flex-col items-center gap-1 relative">
                            <button
                              type="button"
                              onClick={() => handleRemoveAppFromFolder(openFolderId.pageIdx, activeFolderSlot.folderId, fPkg)}
                              className="absolute -top-1 -end-0.5 w-4 h-4 rounded-full bg-[#FF3B30] text-white flex items-center justify-center text-[9px] shadow"
                              title={isRtl ? 'خروج از پوشه' : 'Remove from folder'}
                            >
                              <Minus className="w-2.5 h-2.5" />
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setOpenFolderId(null);
                                handleLaunchApp(fPkg);
                              }}
                            >
                              {renderAppTileIcon(app, 52)}
                            </button>
                            <span className="text-[11px] truncate max-w-[70px]">{app.label}</span>
                          </div>
                        );
                      })}
                  </div>

                  {activeFolderSlot.apps.length > 9 && (
                    <div className="flex items-center justify-center gap-3 pt-3">
                      <button
                        type="button"
                        onClick={() => setFolderSubPage((p) => Math.max(0, p - 1))}
                        className="p-1 rounded-full bg-white/10"
                      >
                        <ChevronLeft className="w-4 h-4" />
                      </button>
                      <span className="text-xs opacity-65 tabular-nums">
                        {folderSubPage + 1} / {Math.ceil(activeFolderSlot.apps.length / 9)}
                      </span>
                      <button
                        type="button"
                        onClick={() =>
                          setFolderSubPage((p) =>
                            Math.min(Math.ceil(activeFolderSlot.apps.length / 9) - 1, p + 1)
                          )
                        }
                        className="p-1 rounded-full bg-white/10"
                      >
                        <ChevronRight className="w-4 h-4" />
                      </button>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* ==================== SPOTLIGHT SEARCH OVERLAY ==================== */}
            {spotlightOpen && (
              <div
                className={`absolute inset-0 z-30 flex flex-col px-4 pt-9 pb-4 ${
                  isLight
                    ? isBlur
                      ? 'bg-[#F2F2F7]/85 backdrop-blur-2xl text-black'
                      : 'bg-[#F2F2F7] text-black'
                    : isAmoled
                    ? 'bg-black/95 text-white'
                    : isBlur
                    ? 'bg-[#121214]/82 backdrop-blur-2xl text-white'
                    : 'bg-[#121214] text-white'
                }`}
              >
                {/* Top Spotlight Search Row */}
                <div className="flex items-center gap-2 shrink-0">
                  <div
                    className={`flex-1 h-[46px] rounded-[14px] px-3 flex items-center gap-2 ${
                      isLight ? 'bg-[#E5E5EA]' : 'bg-[#2C2C2E]'
                    }`}
                  >
                    <Search className="w-4 h-4 opacity-55 shrink-0" />
                    <input
                      ref={spotlightInputRef}
                      value={spotlightQuery}
                      onChange={(e) => setSpotlightQuery(e.target.value)}
                      placeholder={isRtl ? 'جستجو در برنامه‌ها، مخاطبین و تنظیمات' : 'Search apps, contacts & settings'}
                      className="flex-1 bg-transparent text-sm focus:outline-none placeholder:opacity-50"
                    />
                    {spotlightQuery && (
                      <button type="button" onClick={() => setSpotlightQuery('')}>
                        <X className="w-4 h-4 opacity-60" />
                      </button>
                    )}
                  </div>
                  <button
                    type="button"
                    onClick={() => setSettingsOpen(true)}
                    className={`w-10 h-10 rounded-xl flex items-center justify-center ${
                      isLight ? 'bg-black/[0.07]' : 'bg-white/10'
                    }`}
                    aria-label="Settings"
                  >
                    <Settings className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setSpotlightOpen(false)}
                    className="text-sm font-semibold text-[#007AFF] px-1"
                  >
                    {isRtl ? 'لغو' : 'Cancel'}
                  </button>
                </div>

                {/* Nucleo Latency Micro-Header */}
                <div className="flex items-center justify-between text-[10px] opacity-55 pt-2 pb-1 font-mono">
                  <span>Rust Nucleo · {installedApps.length} apps</span>
                  <span className="text-[#34C759] font-semibold">{latencyMs.toFixed(2)} ms</span>
                </div>

                <div className="flex-1 overflow-y-auto space-y-4 pt-1">
                  {/* Empty Query: Siri Suggestions 4x2 */}
                  {!spotlightQuery.trim() && prefs.spotlightSuggestions !== false && (
                    <div
                      className={`rounded-2xl p-3 border ${
                        isLight ? 'bg-white/85 border-black/[0.06]' : 'bg-[#1C1C1E]/90 border-white/10'
                      }`}
                    >
                      <div className="text-[11px] font-semibold uppercase tracking-wide opacity-55 mb-2.5 flex items-center gap-1.5">
                        <Sparkles className="w-3.5 h-3.5 text-[#007AFF]" />
                        <span>{isRtl ? 'پیشنهادهای Siri' : 'Siri Suggestions'}</span>
                      </div>
                      <div className="grid grid-cols-4 gap-3">
                        {spotlightSiriApps.map((app) => (
                          <button
                            key={app.package}
                            type="button"
                            onClick={() => handleLaunchApp(app.package)}
                            className="flex flex-col items-center gap-1"
                          >
                            {renderAppTileIcon(app, 44)}
                            <span className="text-[10px] truncate w-full text-center">{app.label}</span>
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Active Query Results */}
                  {spotlightQuery.trim() && (
                    <>
                      {/* TOP HIT (72dp) */}
                      {spotlightSplit.topHit && (
                        <div>
                          <div className="text-[11px] font-semibold uppercase opacity-55 px-1 pb-1">
                            {isRtl ? 'بهترین نتیجه' : 'Top Hit'}
                          </div>
                          <button
                            type="button"
                            onClick={() => {
                              if (spotlightSplit.topHit?.kind === 'App') {
                                handleLaunchApp(spotlightSplit.topHit.package);
                              } else {
                                showToast(spotlightSplit.topHit?.label || '');
                              }
                            }}
                            className={`w-full h-[72px] rounded-2xl px-3.5 flex items-center gap-3 border ${
                              isLight ? 'bg-white border-black/[0.06]' : 'bg-[#1C1C1E] border-white/10'
                            }`}
                          >
                            {renderAppTileIcon(
                              {
                                package: spotlightSplit.topHit.package,
                                label: spotlightSplit.topHit.label,
                                user_serial: 0,
                                flags: 0,
                                iconColor: spotlightSplit.topHit.iconColor,
                              },
                              46
                            )}
                            <div className="flex-1 min-w-0 text-start">
                              <div className="text-[15px] font-semibold truncate">{spotlightSplit.topHit.label}</div>
                              <div className="text-[11px] opacity-55 truncate">
                                {spotlightSplit.topHit.kind} · {spotlightSplit.topHit.detail || spotlightSplit.topHit.package}
                              </div>
                            </div>
                            <span className="px-2.5 py-1 rounded-full bg-[#007AFF]/15 text-[#007AFF] text-[11px] font-semibold flex items-center gap-1">
                              <span>{isRtl ? 'باز کردن' : 'Open'}</span>
                              <ArrowUpRight className="w-3 h-3" />
                            </span>
                          </button>
                        </div>
                      )}

                      {/* APPLICATIONS (>6 => 4-col grid, <=6 => 56px rows) */}
                      {spotlightSplit.apps.length > 0 && (
                        <div>
                          <div className="text-[11px] font-semibold uppercase opacity-55 px-1 pb-1">
                            {isRtl ? 'برنامه‌ها' : 'Applications'} ({spotlightSplit.apps.length})
                          </div>
                          {spotlightSplit.apps.length > 6 ? (
                            <div
                              className={`rounded-2xl p-3 grid grid-cols-4 gap-3 border ${
                                isLight ? 'bg-white border-black/[0.06]' : 'bg-[#1C1C1E] border-white/10'
                              }`}
                            >
                              {spotlightSplit.apps.slice(0, 12).map((r) => (
                                <button
                                  key={r.package}
                                  type="button"
                                  onClick={() => handleLaunchApp(r.package)}
                                  className="flex flex-col items-center gap-1"
                                >
                                  {renderAppTileIcon(
                                    { package: r.package, label: r.label, user_serial: 0, flags: 0, iconColor: r.iconColor },
                                    44
                                  )}
                                  <span className="text-[10px] truncate w-full text-center">{r.label}</span>
                                </button>
                              ))}
                            </div>
                          ) : (
                            <div
                              className={`rounded-2xl border overflow-hidden divide-y ${
                                isLight ? 'bg-white border-black/[0.06] divide-black/10' : 'bg-[#1C1C1E] border-white/10 divide-white/10'
                              }`}
                            >
                              {spotlightSplit.apps.map((r) => (
                                <button
                                  key={r.package}
                                  type="button"
                                  onClick={() => handleLaunchApp(r.package)}
                                  className="w-full h-[56px] px-3.5 flex items-center gap-3 text-start hover:bg-white/5"
                                >
                                  {renderAppTileIcon(
                                    { package: r.package, label: r.label, user_serial: 0, flags: 0, iconColor: r.iconColor },
                                    36
                                  )}
                                  <div className="flex-1 min-w-0">
                                    <div className="text-sm font-medium truncate">{r.label}</div>
                                    <div className="text-[11px] opacity-55 truncate">{r.package}</div>
                                  </div>
                                </button>
                              ))}
                            </div>
                          )}
                        </div>
                      )}

                      {/* SYSTEM SETTINGS */}
                      {spotlightSplit.settings.length > 0 && (
                        <div>
                          <div className="text-[11px] font-semibold uppercase opacity-55 px-1 pb-1">
                            {isRtl ? 'تنظیمات سیستم' : 'Settings'}
                          </div>
                          <div
                            className={`rounded-2xl border overflow-hidden divide-y ${
                              isLight ? 'bg-white border-black/[0.06] divide-black/10' : 'bg-[#1C1C1E] border-white/10 divide-white/10'
                            }`}
                          >
                            {spotlightSplit.settings.map((r) => (
                              <button
                                key={r.package}
                                type="button"
                                onClick={() => setSettingsOpen(true)}
                                className="w-full h-[52px] px-3.5 flex items-center gap-3 text-start"
                              >
                                <span className="w-8 h-8 rounded-lg bg-[#64748B] text-white flex items-center justify-center">
                                  <SlidersHorizontal className="w-4 h-4" />
                                </span>
                                <div className="flex-1 min-w-0">
                                  <div className="text-sm font-medium truncate">{r.label}</div>
                                  <div className="text-[11px] opacity-55 truncate">{r.detail}</div>
                                </div>
                              </button>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* CONTACTS */}
                      {spotlightSplit.contacts.length > 0 && (
                        <div>
                          <div className="text-[11px] font-semibold uppercase opacity-55 px-1 pb-1">
                            {isRtl ? 'مخاطبین' : 'Contacts'}
                          </div>
                          <div
                            className={`rounded-2xl border overflow-hidden divide-y ${
                              isLight ? 'bg-white border-black/[0.06] divide-black/10' : 'bg-[#1C1C1E] border-white/10 divide-white/10'
                            }`}
                          >
                            {spotlightSplit.contacts.map((r) => (
                              <button
                                key={r.package}
                                type="button"
                                onClick={() => showToast(`Call ${r.label}`)}
                                className="w-full h-[52px] px-3.5 flex items-center gap-3 text-start"
                              >
                                <span
                                  className="w-8 h-8 rounded-full text-white flex items-center justify-center font-bold text-xs"
                                  style={{ backgroundColor: r.iconColor || '#007AFF' }}
                                >
                                  {r.label?.[0] || '?'}
                                </span>
                                <div className="flex-1 min-w-0">
                                  <div className="text-sm font-medium truncate">{r.label}</div>
                                  <div className="text-[11px] opacity-55 truncate">{r.detail}</div>
                                </div>
                              </button>
                            ))}
                          </div>
                        </div>
                      )}
                    </>
                  )}
                </div>
              </div>
            )}

            {/* ==================== BLURRED 240PX CONTEXT MENU ==================== */}
            {contextTarget && (
              <div
                className="absolute inset-0 z-40 bg-black/60 backdrop-blur-md flex flex-col items-center justify-center p-5 gap-3"
                onClick={() => setContextTarget(null)}
              >
                {/* Lifted Icon Preview (1.06x) */}
                <div className="scale-[1.06] flex flex-col items-center gap-1 pointer-events-none">
                  {renderAppTileIcon(contextTarget.app, 58)}
                  <span className="text-xs font-semibold text-white">{contextTarget.app.label}</span>
                </div>

                {/* 240px iOS Context Card */}
                <div
                  className={`w-[240px] rounded-[14px] overflow-hidden border divide-y shadow-2xl text-[13px] ${
                    isLight
                      ? 'bg-white/95 border-black/10 divide-black/10 text-black'
                      : 'bg-[#2C2C2E]/95 border-white/15 divide-white/12 text-white'
                  }`}
                  onClick={(e) => e.stopPropagation()}
                >
                  {/* Dynamic Quick Actions */}
                  {getQuickActions(contextTarget.app, isRtl).map((qa) => (
                    <button
                      key={qa.id}
                      type="button"
                      onClick={() => {
                        showToast(`${contextTarget.app.label}: ${qa.label}`);
                        setContextTarget(null);
                      }}
                      className="w-full px-3.5 py-2.5 flex items-center justify-between hover:bg-white/10"
                    >
                      <span>{qa.label}</span>
                      <Sparkles className="w-3.5 h-3.5 text-[#007AFF]" />
                    </button>
                  ))}

                  <button
                    type="button"
                    onClick={() => {
                      const p = contextTarget.app.package;
                      setContextTarget(null);
                      handleLaunchApp(p);
                    }}
                    className="w-full px-3.5 py-2.5 flex items-center justify-between hover:bg-white/10"
                  >
                    <span>{isRtl ? 'باز کردن' : 'Open'}</span>
                    <ArrowUpRight className="w-4 h-4 opacity-70" />
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      setAppInfoTarget(contextTarget.app);
                      setContextTarget(null);
                    }}
                    className="w-full px-3.5 py-2.5 flex items-center justify-between hover:bg-white/10"
                  >
                    <span>{isRtl ? 'اطلاعات برنامه' : 'App Info'}</span>
                    <Info className="w-4 h-4 opacity-70" />
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      showToast(isRtl ? `اشتراک‌گذاری ${contextTarget.app.label}` : `Shared ${contextTarget.app.label}`);
                      setContextTarget(null);
                    }}
                    className="w-full px-3.5 py-2.5 flex items-center justify-between hover:bg-white/10"
                  >
                    <span>{isRtl ? 'اشتراک‌گذاری' : 'Share App'}</span>
                    <Share2 className="w-4 h-4 opacity-70" />
                  </button>

                  {prefs.notificationBadges !== false && (
                    <button
                      type="button"
                      onClick={() => {
                        toggleMuteBadge(contextTarget.app.package);
                        setContextTarget(null);
                      }}
                      className="w-full px-3.5 py-2.5 flex items-center justify-between hover:bg-white/10"
                    >
                      <span>
                        {(prefs.mutedBadgePackages || []).includes(contextTarget.app.package)
                          ? isRtl
                            ? 'فعال‌کردن نشان'
                            : 'Show Badge'
                          : isRtl
                          ? 'بی‌صدا کردن نشان'
                          : 'Mute Badge'}
                      </span>
                      {(prefs.mutedBadgePackages || []).includes(contextTarget.app.package) ? (
                        <Bell className="w-4 h-4 opacity-70" />
                      ) : (
                        <BellOff className="w-4 h-4 opacity-70" />
                      )}
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={() => {
                      setContextTarget(null);
                      goToHomePage(0);
                      setIsEditMode(true);
                    }}
                    className="w-full px-3.5 py-2.5 flex items-center justify-between hover:bg-white/10"
                  >
                    <span>{isRtl ? 'ویرایش صفحه اصلی' : 'Edit Home Screen'}</span>
                    <Pencil className="w-4 h-4 opacity-70" />
                  </button>

                  {contextTarget.source === 'home' && contextTarget.slotIdx !== undefined ? (
                    <>
                      <button
                        type="button"
                        onClick={() => {
                          handleMoveToDock(contextTarget.app.package);
                          setContextTarget(null);
                        }}
                        className="w-full px-3.5 py-2.5 flex items-center justify-between hover:bg-white/10"
                      >
                        <span>{isRtl ? 'افزودن به داک' : 'Pin to Dock'}</span>
                        <Plus className="w-4 h-4 opacity-70" />
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          handleRemoveSlotFromPage(contextTarget.pageIdx ?? 0, contextTarget.slotIdx!);
                          setContextTarget(null);
                        }}
                        className="w-full px-3.5 py-2.5 flex items-center justify-between hover:bg-white/10"
                      >
                        <span>{isRtl ? 'حذف از صفحه اصلی' : 'Remove from Home'}</span>
                        <Minus className="w-4 h-4 opacity-70" />
                      </button>
                    </>
                  ) : contextTarget.source === 'dock' ? (
                    <button
                      type="button"
                      onClick={() => {
                        handleRemoveFromDock(contextTarget.app.package);
                        setContextTarget(null);
                      }}
                      className="w-full px-3.5 py-2.5 flex items-center justify-between hover:bg-white/10"
                    >
                      <span>{isRtl ? 'حذف از داک' : 'Remove from Dock'}</span>
                      <Minus className="w-4 h-4 opacity-70" />
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => {
                        handleAddAppToHome(contextTarget.app.package);
                        setContextTarget(null);
                      }}
                      className="w-full px-3.5 py-2.5 flex items-center justify-between hover:bg-white/10"
                    >
                      <span>{isRtl ? 'افزودن به صفحه اصلی' : 'Add to Home Screen'}</span>
                      <Plus className="w-4 h-4 opacity-70" />
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={() => handleDeleteAppCompletely(contextTarget.app.package)}
                    className="w-full px-3.5 py-2.5 flex items-center justify-between text-[#FF3B30] font-medium hover:bg-[#FF3B30]/10"
                  >
                    <span>{isRtl ? 'حذف کامل برنامه' : 'Delete App'}</span>
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}

            {/* ==================== PAGE OVERVIEW SWITCHER ==================== */}
            {overviewOpen && (
              <div
                className="absolute inset-0 z-40 bg-black/75 backdrop-blur-2xl flex flex-col p-5 text-white"
                onClick={() => setOverviewOpen(false)}
              >
                <div className="pt-4 pb-3 flex items-center justify-between" onClick={(e) => e.stopPropagation()}>
                  <h3 className="text-lg font-bold">{isRtl ? 'نمای کلی صفحات' : 'Pages Overview'}</h3>
                  <button
                    type="button"
                    onClick={() => setOverviewOpen(false)}
                    className="px-3 py-1 rounded-full bg-[#007AFF] text-xs font-semibold"
                  >
                    {isRtl ? 'تایید' : 'Done'}
                  </button>
                </div>

                <div
                  className="flex-1 overflow-y-auto grid grid-cols-2 gap-3 content-start"
                  onClick={(e) => e.stopPropagation()}
                >
                  {layout.pages.map((pageSlots, pIdx) => (
                    <button
                      key={pIdx}
                      type="button"
                      onClick={() => goToHomePage(pIdx)}
                      className={`rounded-2xl p-3 border text-start space-y-2 ${
                        activeVirtualKind === 'home' && activeHomePageIdx === pIdx
                          ? 'border-[#007AFF] bg-[#007AFF]/15'
                          : 'border-white/15 bg-white/10'
                      }`}
                    >
                      <div className="flex items-center justify-between text-xs font-semibold">
                        <span>{isRtl ? `صفحه ${pIdx + 1}` : `Page ${pIdx + 1}`}</span>
                        <span className="opacity-60 tabular-nums">{pageSlots.length}</span>
                      </div>
                      <div className="grid grid-cols-4 gap-1">
                        {pageSlots.slice(0, 8).map((s, idx) => (
                          <span
                            key={idx}
                            className="h-4 rounded-[4px] bg-white/25"
                          />
                        ))}
                      </div>
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={handleAddEmptyPage}
                    className="rounded-2xl p-3 border border-dashed border-white/30 flex flex-col items-center justify-center gap-1 min-h-[88px] text-xs opacity-80 hover:opacity-100"
                  >
                    <Plus className="w-5 h-5 text-[#007AFF]" />
                    <span>{isRtl ? 'افزودن صفحه' : 'New Page'}</span>
                  </button>
                </div>
              </div>
            )}

            {/* ==================== WIDGET PICKER SHEET ==================== */}
            {widgetPickerOpen && (
              <div
                className="absolute inset-0 z-40 bg-black/65 backdrop-blur-xl flex items-end"
                onClick={() => setWidgetPickerOpen(false)}
              >
                <div
                  className={`w-full rounded-t-[28px] p-5 space-y-3 border-t ${
                    isLight ? 'bg-[#F2F2F7] text-black border-black/10' : 'bg-[#1C1C1E] text-white border-white/15'
                  }`}
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="flex items-center justify-between">
                    <h3 className="text-base font-bold">
                      {widgetTargetHome
                        ? isRtl
                          ? 'افزودن ویجت به صفحه اصلی'
                          : 'Add Home Screen Widget'
                        : isRtl
                        ? 'افزودن ویجت به نمای امروز'
                        : 'Add Today Widget'}
                    </h3>
                    <button
                      type="button"
                      onClick={() => setWidgetPickerOpen(false)}
                      className="text-xs font-semibold text-[#007AFF]"
                    >
                      {isRtl ? 'بستن' : 'Close'}
                    </button>
                  </div>
                  <div className="space-y-2">
                    {[
                      { kind: 'clock' as const, en: 'Clock & Date', fa: 'ساعت و تاریخ' },
                      { kind: 'suggestions' as const, en: 'Siri App Suggestions', fa: 'پیشنهادهای هوشمند' },
                      { kind: 'vault' as const, en: 'Offline Rust Vault', fa: 'وضعیت هسته آفلاین' },
                      { kind: 'notes' as const, en: 'Quick Offline Note', fa: 'یادداشت سریع آفلاین' },
                    ].map((item) => (
                      <div
                        key={item.kind}
                        className={`p-3 rounded-2xl border flex items-center justify-between ${
                          isLight ? 'bg-white border-black/10' : 'bg-[#2C2C2E] border-white/10'
                        }`}
                      >
                        <span className="text-xs font-semibold">{isRtl ? item.fa : item.en}</span>
                        <div className="flex items-center gap-1">
                          {(['small', 'medium', 'large'] as const).map((sp) => (
                            <button
                              key={sp}
                              type="button"
                              onClick={() => handleAddWidget(item.kind, sp)}
                              className="px-2 py-1 rounded-lg bg-[#007AFF] text-white text-[10px] font-semibold capitalize"
                            >
                              {sp[0].toUpperCase()}
                            </button>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {/* ==================== APP INFO SHEET ==================== */}
            {appInfoTarget && (
              <div
                className="absolute inset-0 z-40 bg-black/65 backdrop-blur-xl flex items-center justify-center p-5"
                onClick={() => setAppInfoTarget(null)}
              >
                <div
                  className={`w-full rounded-2xl p-4 space-y-3 border ${
                    isLight ? 'bg-white text-black border-black/10' : 'bg-[#1C1C1E] text-white border-white/15'
                  }`}
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="flex items-center gap-3">
                    {renderAppTileIcon(appInfoTarget, 48)}
                    <div className="min-w-0">
                      <div className="font-bold text-sm truncate">{appInfoTarget.label}</div>
                      <div className="text-[11px] font-mono opacity-60 truncate">{appInfoTarget.package}</div>
                    </div>
                  </div>
                  <div className="text-xs space-y-1 opacity-80">
                    <div>Category: {appInfoTarget.category || 'utility'}</div>
                    <div>Network Permissions: 0 (Offline Enforced)</div>
                  </div>
                  <button
                    type="button"
                    onClick={() => setAppInfoTarget(null)}
                    className="w-full py-2 rounded-xl bg-[#007AFF] text-white text-xs font-semibold"
                  >
                    {isRtl ? 'بستن' : 'Close'}
                  </button>
                </div>
              </div>
            )}

            {/* ==================== DOUBLE-TAP LOCK OVERLAY ==================== */}
            {lockOverlayOpen && (
              <button
                type="button"
                onClick={() => setLockOverlayOpen(false)}
                className="absolute inset-0 z-50 bg-black/95 text-white flex flex-col items-center justify-center gap-3"
              >
                <Lock className="w-10 h-10 text-[#007AFF]" />
                <div className="text-3xl font-light tabular-nums">12:24</div>
                <span className="text-xs opacity-60">
                  {isRtl ? 'برای باز کردن قفل کلیک کنید (Accessibility Lock)' : 'Tap to unlock (KayumarsLockService)'}
                </span>
              </button>
            )}

            {/* ==================== FULL-SCREEN iOS SETTINGS ==================== */}
            <SettingsBottomSheet
              isOpen={settingsOpen}
              onClose={() => setSettingsOpen(false)}
              prefs={prefs}
              onUpdatePrefs={updatePreferences}
              onResetHistory={() => {
                engine.resetHistory();
                showToast(isRtl ? 'تاریخچه پاک شد' : 'History cleared');
              }}
              onResetLayout={() => {
                setLayout(createDefaultLayout());
                showToast(isRtl ? 'چیدمان پیش‌فرض بازگردانی شد' : 'Layout restored');
              }}
              onCycleCategoryOrder={() => {
                setLayout((prev) => {
                  const [first, ...rest] = prev.categoryOrder;
                  return { ...prev, categoryOrder: [...rest, first] };
                });
                showToast(isRtl ? 'ترتیب دسته‌بندی تغییر کرد' : 'Library categories reordered');
              }}
              onOpenWidgetPicker={() => {
                setWidgetTargetHome(true);
                setWidgetPickerOpen(true);
              }}
              isRtl={isRtl}
            />

            {/* Toast Pill */}
            {toastMsg && (
              <div className="absolute bottom-24 left-1/2 -translate-x-1/2 z-50 px-3.5 py-1.5 rounded-full bg-black/85 border border-white/15 text-white text-xs font-medium shadow-xl whitespace-nowrap flex items-center gap-1.5">
                <Check className="w-3.5 h-3.5 text-[#34C759]" />
                <span>{toastMsg}</span>
              </div>
            )}

            {/* iOS Home Indicator */}
            <div className="pb-2 pt-1 flex justify-center shrink-0 z-20 pointer-events-none">
              <div className={`w-28 h-1 rounded-full ${isLight ? 'bg-black/25' : 'bg-white/35'}`} />
            </div>
          </div>
        </div>

        {/* ==================== RIGHT SIDEBAR: RUST CORE & ARCHITECTURE INSPECTOR ==================== */}
        <div className="w-full max-w-md space-y-4 text-xs">
          <div className="p-5 rounded-2xl bg-slate-900/75 border border-slate-800 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
                <Cpu className="w-4 h-4 text-[#0A84FF]" />
                <span>{isRtl ? 'موتور بومی راست + لایه iOS' : 'Rust Nucleo Core × California Neutral UI'}</span>
              </h3>
              <span className="px-2 py-0.5 rounded bg-emerald-950 text-emerald-400 font-mono text-[10px]">
                0 B Net
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2.5 text-[11px]">
              <div className="p-3 rounded-xl bg-slate-950/90 border border-slate-800/80">
                <span className="text-slate-400 block">{isRtl ? 'صفحه فعال' : 'Active Surface'}</span>
                <span className="font-mono font-bold text-slate-100 capitalize">
                  {spotlightOpen
                    ? 'Spotlight'
                    : settingsOpen
                    ? 'iOS Settings'
                    : activeVirtualKind === 'home'
                    ? `Home Page ${activeHomePageIdx + 1}/${layout.pages.length}`
                    : activeVirtualKind}
                </span>
              </div>
              <div className="p-3 rounded-xl bg-slate-950/90 border border-slate-800/80">
                <span className="text-slate-400 block">{isRtl ? 'برنامه‌های ایندکس' : 'Indexed Apps'}</span>
                <span className="font-mono font-bold text-cyan-400 tabular-nums">{installedApps.length}</span>
              </div>
              <div className="p-3 rounded-xl bg-slate-950/90 border border-slate-800/80">
                <span className="text-slate-400 block">{isRtl ? 'چیدمان شبکه' : 'Home Grid'}</span>
                <span className="font-mono font-bold text-slate-100 tabular-nums">
                  {gridCols} cols · Dock {dockCap}
                </span>
              </div>
              <div className="p-3 rounded-xl bg-slate-950/90 border border-slate-800/80">
                <span className="text-slate-400 block">{isRtl ? 'تیرگی / تاری' : 'Wallpaper FX'}</span>
                <span className="font-mono font-bold text-slate-100 tabular-nums">
                  {prefs.wallpaperDim ?? 0}% dim · {prefs.wallpaperBlur ?? 0}dp blur
                </span>
              </div>
            </div>

            <div className="p-3 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5 text-[11px] text-slate-300">
              <div className="font-semibold text-slate-100">
                {isRtl ? 'راهنمای تعامل سریع در شبیه‌ساز:' : 'Interactive Controls:'}
              </div>
              <ul className="space-y-1 text-slate-400 list-disc ps-4">
                <li>
                  {isRtl
                    ? 'به چپ/راست بکشید یا روی نقاط پایین بزنید تا بین نمای امروز، صفحات خانه و App Library جابه‌جا شوید.'
                    : 'Swipe left/right or click page dots to navigate Today View, Home pages, and App Library.'}
                </li>
                <li>
                  {isRtl
                    ? 'روی مرکز صفحه به پایین بکشید یا دکمه Search را بزنید تا Spotlight باز شود.'
                    : 'Swipe down from Home or tap the Search pill to open Nucleo Spotlight.'}
                </li>
                <li>
                  {isRtl
                    ? 'کلیک راست (یا لمس طولانی) روی آیکون‌ها منوی تار ۲۴۰px را باز می‌کند؛ در حالت ویرایش، نگه‌داشتن و رها کردن آیکون روی آیکون دیگر پوشه می‌سازد.'
                    : 'Right-click (or hold) any icon for the 240px frosted menu; in Edit mode, drag onto another icon to merge into a Folder.'}
                </li>
              </ul>
            </div>
          </div>

          {/* KIS Ranking Formula Live Card */}
          <div className="p-5 rounded-2xl bg-slate-900/75 border border-slate-800 space-y-2.5">
            <h4 className="font-bold text-slate-200 text-xs uppercase tracking-wider">
              {isRtl ? 'فرمول رتبه‌بندی KISS (هسته Rust دست‌نخورده)' : 'Rust Core Ranking Formula (Untouched core/)'}
            </h4>
            <div className="p-3 rounded-xl bg-slate-950 font-mono text-[11px] text-cyan-300 overflow-x-auto">
              score = nucleo_fuzzy × ((launches + 1) / (total + N)) × (1 / recency)
            </div>
            <p className="text-[11px] text-slate-400 leading-relaxed">
              {isRtl
                ? 'پوسته iOS ۲.۰ تمامی انیمیشن‌های فنری ۱۲۰ هرتز، پوشه‌ها، داک شیشه‌ای و کتابخانه برنامه‌ها را روی همان موتور جستجوی صفر-GC راست اجرا می‌کند.'
                : 'Kayumars 2.0 pairs California Neutral iOS materials, spring physics, and folders directly on top of the unchanged zero-GC Rust fuzzy search & ranking engine.'}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
