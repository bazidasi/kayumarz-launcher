export type SearchResultKind = 'App' | 'Contact' | 'Setting';

export interface AppEntry {
  package: string;
  label: string;
  user_serial: number;
  flags: number;
  category?: 'system' | 'social' | 'tools' | 'media' | 'dev' | 'utility';
  iconColor?: string;
  isPinned?: boolean;
}

export interface ContactEntry {
  id: string;
  name: string;
  phone: string;
  color?: string;
}

export interface SettingEntry {
  id: string;
  title: string;
  description: string;
  action: string;
  iconName: string;
}

export interface SearchResult {
  package: string;
  label: string;
  score: number;
  kind: SearchResultKind;
  category?: string;
  iconColor?: string;
  launches: number;
  recentRank?: number;
  detail?: string;
  actionTarget?: string;
}

export interface HistoryEvent {
  package: string;
  timestamp_ms: number;
}

export type ClockStyle = 'editorial' | 'dot_matrix' | 'magazine';
export type IconTintStyle = 'full_color' | 'monochrome_dot' | 'monet_tint' | 'frosted_glass';

export interface LauncherPreferences {
  showClockWidget: boolean;
  showDock: boolean;
  dockPackages: string[];
  hiddenPackages: string[];
  iconStyle: 'default' | 'monochrome' | 'minimal-circle' | 'text-only';
  iconShape: 'circle' | 'rounded-square' | 'squircle' | 'system';
  layoutMode: 'list' | 'grid-4x4' | 'grid-4x5';
  wallpaper: 'oled-black' | 'dark-slate' | 'nordic' | 'warm-light' | 'dynamic-nature';
  searchScopes: {
    apps: boolean;
    contacts: boolean;
    settings: boolean;
  };
  gestures: {
    doubleTapLock: boolean;
    swipeUpDrawer: boolean;
    swipeDownNotifications: boolean;
  };
  oneHandedMode: boolean;
  theme: 'dark' | 'light' | 'amoled';
  themeMode: 'system' | 'light' | 'dark' | 'amoled';
  accentMode: 'dynamic' | 'fixed';
  showTopAppsGrid: boolean;
  localeMode: 'fa' | 'en';
  drawerBlur?: boolean;
  reducedMotion?: boolean;
  showMotionHud?: boolean;
  clockStyle?: ClockStyle;
  iconTintStyle?: IconTintStyle;
  edgePanelEnabled?: boolean;
  liveCapsuleEnabled?: boolean;
  gridColumns?: 4 | 5 | 6;
  dockCapacity?: 4 | 5 | 6;
  showSearchPill?: boolean;
  showIconLabels?: boolean;
  showTodayPage?: boolean;
  showAppLibrary?: boolean;
  wallpaperDim?: number;
  wallpaperBlur?: number;
  notificationBadges?: boolean;
  newAppDots?: boolean;
  mutedBadgePackages?: string[];
  spotlightSuggestions?: boolean;
  spotlightIncludeSettings?: boolean;
  pinchOverviewEnabled?: boolean;
  swipeDownSpotlightEnabled?: boolean;
}

export interface PerfMetrics {
  coldStartMs: number;
  searchLatencyMs: number;
  jankPercentage: number;
  idleMemoryMb: number;
  apkSizeMb: number;
  networkPermissions: number;
  totalApps: number;
  totalLaunches: number;
}
