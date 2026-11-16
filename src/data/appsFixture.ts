import { AppEntry, ContactEntry, SettingEntry, LauncherPreferences } from '../types/launcher';

export const DEFAULT_PREFERENCES: LauncherPreferences = {
  showClockWidget: true,
  showDock: true,
  dockPackages: [
    'com.google.android.dialer',
    'com.google.android.apps.messaging',
    'com.android.chrome',
    'com.android.camera',
  ],
  hiddenPackages: [],
  iconStyle: 'default',
  layoutMode: 'list',
  wallpaper: 'oled-black',
  searchScopes: {
    apps: true,
    contacts: true,
    settings: true,
  },
  gestures: {
    doubleTapLock: true,
    swipeUpDrawer: true,
    swipeDownNotifications: true,
  },
  oneHandedMode: false,
  theme: 'dark',
  themeMode: 'system',
  accentMode: 'dynamic',
  iconShape: 'squircle',
  showTopAppsGrid: true,
  localeMode: 'en',
  drawerBlur: true,
  reducedMotion: false,
  showMotionHud: false,
  clockStyle: 'editorial',
  iconTintStyle: 'full_color',
  edgePanelEnabled: false,
  liveCapsuleEnabled: false,
  gridColumns: 4,
  dockCapacity: 4,
  showSearchPill: true,
  showIconLabels: true,
  showTodayPage: true,
  showAppLibrary: true,
  wallpaperDim: 0,
  wallpaperBlur: 0,
  notificationBadges: true,
  newAppDots: true,
  mutedBadgePackages: [],
  spotlightSuggestions: true,
  spotlightIncludeSettings: true,
  pinchOverviewEnabled: true,
  swipeDownSpotlightEnabled: true,
};

export const CONTACTS_FIXTURE: ContactEntry[] = [
  { id: 'c1', name: 'علی رضایی', phone: '+98 912 345 6789', color: '#3B82F6' },
  { id: 'c2', name: 'سارا محمدی', phone: '+98 935 123 4567', color: '#EC4899' },
  { id: 'c3', name: 'مریم احمدی', phone: '+98 919 876 5432', color: '#10B981' },
  { id: 'c4', name: 'رضا کریمی', phone: '+98 912 111 2233', color: '#F59E0B' },
  { id: 'c5', name: 'نیکی سلطانی', phone: '+98 936 999 8877', color: '#8B5CF6' },
  { id: 'c6', name: 'پشتیبانی فنی', phone: '+98 21 8888 0000', color: '#06B6D4' },
  { id: 'c7', name: 'خانه', phone: '+98 21 2200 1122', color: '#6366F1' },
  { id: 'c8', name: 'دفتر کار', phone: '+98 21 8877 6655', color: '#64748B' },
];

export const SYSTEM_SETTINGS_FIXTURE: SettingEntry[] = [
  { id: 's1', title: 'Hotspot and Tethering', description: 'Wi-Fi hotspot & USB tethering', action: 'android.settings.TETHER_SETTINGS', iconName: 'Wifi' },
  { id: 's2', title: 'Hide the badge on new apps', description: 'Smart Launcher notification badges', action: 'android.settings.NOTIFICATION_SETTINGS', iconName: 'Sliders' },
  { id: 's3', title: 'Wi-Fi & Network', description: 'Wireless networks and internet', action: 'android.settings.WIFI_SETTINGS', iconName: 'Wifi' },
  { id: 's4', title: 'Bluetooth', description: 'Paired audio devices', action: 'android.settings.BLUETOOTH_SETTINGS', iconName: 'Bluetooth' },
  { id: 's5', title: 'Battery & Optimization', description: 'Power usage & adaptive battery', action: 'android.intent.action.POWER_USAGE_SUMMARY', iconName: 'BatteryCharging' },
  { id: 's6', title: 'Display & Brightness', description: 'Dark theme, font size, adaptive', action: 'android.settings.DISPLAY_SETTINGS', iconName: 'Sun' },
  { id: 's7', title: 'Security & Privacy', description: 'Biometrics & device lock', action: 'android.settings.SECURITY_SETTINGS', iconName: 'Lock' },
];

const baseApps: Array<{ label: string; package: string; category: AppEntry['category']; color: string }> = [
  { label: 'Bale', package: 'ir.nasim.messenger', category: 'social', color: '#00BFA5' },
  { label: 'BinWiz VPN', package: 'com.binwiz.vpn', category: 'system', color: '#1D4ED8' },
  { label: 'Call Recorder', package: 'com.callrecorder.app', category: 'social', color: '#991B1B' },
  { label: 'Contacts', package: 'com.google.android.contacts', category: 'social', color: '#EF4444' },
  { label: 'Discord', package: 'com.discord', category: 'social', color: '#5865F2' },
  { label: 'Eitaa', package: 'ir.eitaa.messenger', category: 'social', color: '#F97316' },
  { label: 'Gmail', package: 'com.google.android.gm', category: 'social', color: '#EA4335' },
  { label: 'Instagram', package: 'com.instagram.android', category: 'social', color: '#E1306C' },
  { label: 'LinkedIn', package: 'com.linkedin.android', category: 'social', color: '#0A66C2' },
  { label: 'Meet', package: 'com.google.android.apps.meetings', category: 'social', color: '#FACC15' },
  { label: 'Messages', package: 'com.google.android.apps.messaging', category: 'social', color: '#2563EB' },
  { label: 'Phone', package: 'com.google.android.dialer', category: 'social', color: '#22C55E' },
  { label: 'Pinterest', package: 'com.pinterest', category: 'tools', color: '#E60023' },
  { label: 'Proton Mail', package: 'ch.protonmail.android', category: 'social', color: '#6D4AFF' },
  { label: 'Reddit', package: 'com.reddit.frontpage', category: 'tools', color: '#FF4500' },
  { label: 'Rubika', package: 'ir.resaneh1.iptv', category: 'social', color: '#0EA5E9' },
  { label: 'Telegram', package: 'org.telegram.messenger', category: 'social', color: '#24A1DE' },
  { label: 'Telewebion', package: 'com.telewebion.app', category: 'media', color: '#DC2626' },
  { label: 'Technolife', package: 'ir.technolife.app', category: 'tools', color: '#1E3A8A' },
  { label: 'Tenmin', package: 'com.tenmin.app', category: 'utility', color: '#818CF8' },
  { label: 'Edge', package: 'com.microsoft.emmx', category: 'tools', color: '#0284C7' },
  { label: 'YouTube', package: 'com.google.android.youtube', category: 'media', color: '#FF0000' },
  { label: 'Camera', package: 'com.android.camera', category: 'media', color: '#9CA3AF' },
  { label: 'Settings', package: 'com.android.settings', category: 'system', color: '#4B5563' },
  { label: 'Spotify', package: 'com.spotify.music', category: 'media', color: '#1DB954' },
  { label: 'Google Chrome', package: 'com.android.chrome', category: 'tools', color: '#4285F4' },
  { label: 'Google Maps', package: 'com.google.android.apps.maps', category: 'tools', color: '#34A853' },
  { label: 'Instagram', package: 'com.instagram.android', category: 'social', color: '#E1306C' },
  { label: 'Discord', package: 'com.discord', category: 'social', color: '#5865F2' },
  { label: 'Slack', package: 'com.Slack', category: 'tools', color: '#4A154B' },
  { label: 'Termux', package: 'com.termux', category: 'dev', color: '#000000' },
  { label: 'Helix Editor', package: 'com.helix.editor', category: 'dev', color: '#4361EE' },
  { label: 'Firefox', package: 'org.mozilla.firefox', category: 'tools', color: '#FF7139' },
  { label: 'Signal', package: 'org.thoughtcrime.securesms', category: 'social', color: '#3A76F0' },
  { label: 'Reddit', package: 'com.reddit.frontpage', category: 'social', color: '#FF4500' },
  { label: 'GitHub', package: 'com.github.android', category: 'dev', color: '#24292E' },
  { label: 'Netflix', package: 'com.netflix.mediaclient', category: 'media', color: '#E50914' },
  { label: 'Calculator', package: 'com.google.android.calculator', category: 'utility', color: '#00ACC1' },
  { label: 'Calendar', package: 'com.google.android.calendar', category: 'utility', color: '#1A73E8' },
  { label: 'Clock', package: 'com.google.android.deskclock', category: 'utility', color: '#3949AB' },
  { label: 'Files', package: 'com.google.android.apps.nbu.files', category: 'system', color: '#185ABC' },
  { label: 'Phone', package: 'com.google.android.dialer', category: 'system', color: '#0F9D58' },
  { label: 'Messages', package: 'com.google.android.apps.messaging', category: 'social', color: '#1A73E8' },
  { label: 'Google Keep', package: 'com.google.android.keep', category: 'tools', color: '#FBBC04' },
  { label: 'Google Drive', package: 'com.google.android.apps.docs', category: 'tools', color: '#0F9D58' },
  { label: 'Google Photos', package: 'com.google.android.apps.photos', category: 'media', color: '#EA4335' },
  { label: 'Twitter / X', package: 'com.twitter.android', category: 'social', color: '#1DA1F2' },
  { label: 'VLC Media Player', package: 'org.videolan.vlc', category: 'media', color: '#FF8800' },
  { label: 'SoundCloud', package: 'com.soundcloud.android', category: 'media', color: '#FF5500' },
  { label: 'Duolingo', package: 'com.duolingo', category: 'utility', color: '#58CC02' },
  { label: 'Notion', package: 'notion.id', category: 'tools', color: '#000000' },
  { label: 'Obsidian', package: 'md.obsidian', category: 'dev', color: '#7C3AED' },
  { label: 'Audible', package: 'com.audible.application', category: 'media', color: '#F7991C' },
  { label: 'Bitwarden', package: 'com.x8bit.bitwarden', category: 'tools', color: '#175DDC' },
  { label: 'Proton Mail', package: 'ch.protonmail.android', category: 'tools', color: '#6D4AFF' },
  { label: 'Tor Browser', package: 'org.torproject.torbrowser', category: 'tools', color: '#7D4698' },
  { label: 'Shazam', package: 'com.shazam.android', category: 'media', color: '#0088FF' },
  { label: 'Uber', package: 'com.ubercab', category: 'utility', color: '#000000' },
  { label: 'Airbnb', package: 'com.airbnb.android', category: 'utility', color: '#FF5A5F' },
  { label: 'Stripe Dashboard', package: 'com.stripe.android.dashboard', category: 'dev', color: '#635BFF' },
  { label: 'Brave Browser', package: 'com.brave.browser', category: 'tools', color: '#FB542B' },
  { label: 'DuckDuckGo', package: 'com.duckduckgo.mobile.android', category: 'tools', color: '#DE5833' },
  { label: 'Pocket Casts', package: 'au.com.shiftyjelly.pocketcasts', category: 'media', color: '#F43E37' },
  { label: 'KISS Launcher', package: 'fr.neamar.kiss', category: 'system', color: '#3F51B5' },
  { label: 'Kvaesitso', package: 'de.mm20.launcher2', category: 'system', color: '#00BCD4' },
  { label: 'RetroArch', package: 'com.retroarch', category: 'media', color: '#333333' },
  { label: 'AnkiDroid', package: 'com.ichi2.anki', category: 'utility', color: '#2196F3' },
  { label: 'Speedtest', package: 'org.zwanoo.android.speedtest', category: 'tools', color: '#141526' },
];

export function generate500Apps(): AppEntry[] {
  const result: AppEntry[] = [];
  const totalTarget = 500;

  for (const b of baseApps) {
    result.push({
      label: b.label,
      package: b.package,
      user_serial: 0,
      flags: b.category === 'system' ? 1 : 0,
      category: b.category,
      iconColor: b.color,
    });
  }

  const categories: Array<AppEntry['category']> = ['tools', 'social', 'media', 'dev', 'utility', 'system'];
  const prefixes = [
    'Alpha', 'Beta', 'Core', 'Fast', 'Hyper', 'Micro', 'Nano', 'Nova', 'Omni', 'Peak',
    'Quantum', 'Rapid', 'Smart', 'Swift', 'Ultra', 'Vortex', 'Zen', 'Aero', 'Apex', 'Atlas',
    'Binary', 'Cipher', 'Cosmo', 'Echo', 'Flux', 'Genesis', 'Helix', 'Horizon', 'Infinity', 'Matrix',
    'Nexus', 'Orbit', 'Pulse', 'Radix', 'Signal', 'Titan', 'Vector', 'Warp', 'Yield', 'Zephyr'
  ];
  const suffixes = [
    'Browser', 'Camera', 'Canvas', 'Client', 'Cloud', 'Connect', 'Console', 'Dashboard', 'Database',
    'Drive', 'Editor', 'Engine', 'Explorer', 'Hub', 'Lab', 'Link', 'Manager', 'Monitor', 'Network',
    'Node', 'Note', 'Pack', 'Pilot', 'Player', 'Portal', 'Reader', 'Scanner', 'Scope', 'Server',
    'Share', 'Studio', 'Suite', 'Terminal', 'Tool', 'Tracker', 'Vault', 'Viewer', 'Wave', 'Widget'
  ];
  const colors = [
    '#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#06B6D4', '#84CC16',
    '#6366F1', '#14B8A6', '#F97316', '#64748B', '#0EA5E9', '#D946EF', '#22C55E'
  ];

  let counter = 1;
  while (result.length < totalTarget) {
    const p = prefixes[counter % prefixes.length];
    const s = suffixes[(counter * 7) % suffixes.length];
    const cat = categories[counter % categories.length];
    const label = `${p} ${s}`;
    const pkg = `com.${p.toLowerCase()}.${s.toLowerCase()}.app${counter}`;
    const color = colors[counter % colors.length];

    result.push({
      label,
      package: pkg,
      user_serial: 0,
      flags: 0,
      category: cat,
      iconColor: color,
    });
    counter++;
  }

  return result;
}
