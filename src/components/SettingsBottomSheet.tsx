import React, { useState } from 'react';
import { ChevronRight, RotateCcw } from 'lucide-react';
import { LauncherPreferences } from '../types/launcher';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  prefs: LauncherPreferences;
  onUpdatePrefs: (updated: Partial<LauncherPreferences>) => void;
  onResetHistory: () => void;
  onResetLayout?: () => void;
  onCycleCategoryOrder?: () => void;
  onOpenWidgetPicker?: () => void;
  isRtl: boolean;
}

export const SettingsBottomSheet: React.FC<Props> = ({
  isOpen,
  onClose,
  prefs,
  onUpdatePrefs,
  onResetHistory,
  onResetLayout,
  onCycleCategoryOrder,
  onOpenWidgetPicker,
  isRtl,
}) => {
  const [confirmType, setConfirmType] = useState<'history' | 'layout' | null>(null);
  const [notifGranted, setNotifGranted] = useState(true);
  const [showNotifExplainer, setShowNotifExplainer] = useState(false);

  if (!isOpen) return null;

  const isLight = prefs.themeMode === 'light';
  const isAmoled = prefs.themeMode === 'amoled';

  const pageBg = isAmoled
    ? 'bg-black text-white'
    : isLight
    ? 'bg-[#F2F2F7] text-black'
    : 'bg-[#000000] text-white';

  const cardBg = isAmoled
    ? 'bg-[#111113] border-white/10'
    : isLight
    ? 'bg-white border-black/[0.06]'
    : 'bg-[#1C1C1E] border-white/10';

  const dividerColor = isLight ? 'border-[#3C3C43]/20' : 'border-white/12';

  const IosSwitch = ({
    checked,
    onChange,
    label,
  }: {
    checked: boolean;
    onChange: (next: boolean) => void;
    label: string;
  }) => (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={`w-[46px] h-[28px] rounded-full p-[2px] transition-colors duration-200 flex items-center shrink-0 ${
        checked ? 'bg-[#34C759]' : isLight ? 'bg-[#E5E5EA]' : 'bg-[#39393D]'
      }`}
    >
      <span
        className={`w-6 h-6 rounded-full bg-white shadow transition-transform duration-200 ${
          checked ? (isRtl ? '-translate-x-[18px]' : 'translate-x-[18px]') : 'translate-x-0'
        }`}
      />
    </button>
  );

  const SectionHeader = ({ children }: { children: React.ReactNode }) => (
    <div className="px-4 pb-1.5 pt-4 text-[11px] font-semibold uppercase tracking-wide opacity-55">
      {children}
    </div>
  );

  return (
    <div
      className={`absolute inset-0 z-40 flex flex-col overflow-hidden ${pageBg}`}
      dir={isRtl ? 'rtl' : 'ltr'}
      onClick={(e) => e.stopPropagation()}
    >
      {/* iOS Navigation Bar */}
      <div className={`px-4 pt-3 pb-2.5 flex items-center justify-between border-b ${dividerColor} shrink-0`}>
        <h2 className="text-[24px] font-bold tracking-tight">
          {isRtl ? 'تنظیمات' : 'Settings'}
        </h2>
        <button
          onClick={onClose}
          className="text-[16px] font-semibold text-[#007AFF] px-2 py-1 hover:opacity-80"
        >
          {isRtl ? 'تایید' : 'Done'}
        </button>
      </div>

      {/* Scrollable iOS Grouped List */}
      <div className="flex-1 overflow-y-auto px-4 pb-8 text-[14px]">
        {/* GENERAL */}
        <SectionHeader>{isRtl ? 'عمومی' : 'General'}</SectionHeader>
        <div className={`rounded-2xl border overflow-hidden ${cardBg}`}>
          <div className="px-4 py-3 flex items-center justify-between">
            <span>{isRtl ? 'تنظیم کیومرث به‌عنوان خانه پیش‌فرض' : 'Default Home Launcher'}</span>
            <span className="text-xs text-[#34C759] font-semibold">
              {isRtl ? 'فعال' : 'Active'}
            </span>
          </div>
        </div>

        {/* APPEARANCE */}
        <SectionHeader>{isRtl ? 'ظاهر و مواد شیشه‌ای' : 'Appearance'}</SectionHeader>
        <div className={`rounded-2xl border overflow-hidden divide-y ${dividerColor} ${cardBg}`}>
          <div className="px-4 py-3 space-y-2">
            <div className="flex items-center justify-between">
              <span>{isRtl ? 'پوسته' : 'Theme'}</span>
              <span className="text-xs opacity-55 capitalize">{prefs.themeMode}</span>
            </div>
            <div className="grid grid-cols-4 gap-1.5 text-[11px]">
              {[
                { id: 'system', label: isRtl ? 'سیستم' : 'System' },
                { id: 'light', label: isRtl ? 'روشن' : 'Light' },
                { id: 'dark', label: isRtl ? 'تیره' : 'Dark' },
                { id: 'amoled', label: 'AMOLED' },
              ].map((m) => (
                <button
                  key={m.id}
                  onClick={() => onUpdatePrefs({ themeMode: m.id as any })}
                  className={`py-1.5 rounded-lg font-medium border transition-all ${
                    prefs.themeMode === m.id
                      ? 'bg-[#007AFF] text-white border-[#007AFF]'
                      : `${dividerColor} opacity-75 hover:opacity-100`
                  }`}
                >
                  {m.label}
                </button>
              ))}
            </div>
          </div>

          <div className="px-4 py-3 space-y-2">
            <div className="flex items-center justify-between">
              <span>{isRtl ? 'شکل آیکون‌ها' : 'Icon Shape'}</span>
              <span className="text-xs opacity-55">{prefs.iconShape}</span>
            </div>
            <div className="grid grid-cols-4 gap-1.5 text-[11px]">
              {[
                { id: 'squircle', label: isRtl ? 'اسکویرکل ۲۲.۵٪' : 'Squircle' },
                { id: 'circle', label: isRtl ? 'دایره' : 'Circle' },
                { id: 'rounded-square', label: isRtl ? 'گرد' : 'Rounded' },
                { id: 'system', label: isRtl ? 'سیستم' : 'System' },
              ].map((s) => (
                <button
                  key={s.id}
                  onClick={() => onUpdatePrefs({ iconShape: s.id as any })}
                  className={`py-1.5 rounded-lg font-medium border transition-all ${
                    prefs.iconShape === s.id
                      ? 'bg-[#007AFF] text-white border-[#007AFF]'
                      : `${dividerColor} opacity-75 hover:opacity-100`
                  }`}
                >
                  {s.label}
                </button>
              ))}
            </div>
          </div>

          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'مواد شیشه‌ای (Frosted Blur)' : 'Frosted Blur Materials'}</span>
            <IosSwitch
              label="Frosted Blur"
              checked={prefs.drawerBlur !== false}
              onChange={(v) => onUpdatePrefs({ drawerBlur: v })}
            />
          </div>

          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'نمایش نام برنامه‌ها' : 'Show Icon Labels'}</span>
            <IosSwitch
              label="Show Icon Labels"
              checked={prefs.showIconLabels !== false}
              onChange={(v) => onUpdatePrefs({ showIconLabels: v })}
            />
          </div>

          <div className="px-4 py-3 space-y-1.5">
            <div className="flex items-center justify-between text-xs">
              <span>{isRtl ? 'تیرگی والپیپر' : 'Wallpaper Dim'}</span>
              <span className="font-mono opacity-60">{prefs.wallpaperDim ?? 0}%</span>
            </div>
            <input
              type="range"
              min={0}
              max={40}
              value={prefs.wallpaperDim ?? 0}
              onChange={(e) => onUpdatePrefs({ wallpaperDim: Number(e.target.value) })}
              className="w-full accent-[#007AFF]"
            />
          </div>

          <div className="px-4 py-3 space-y-1.5">
            <div className="flex items-center justify-between text-xs">
              <span>{isRtl ? 'تاری والپیپر' : 'Wallpaper Blur'}</span>
              <span className="font-mono opacity-60">{prefs.wallpaperBlur ?? 0} dp</span>
            </div>
            <input
              type="range"
              min={0}
              max={16}
              value={prefs.wallpaperBlur ?? 0}
              onChange={(e) => onUpdatePrefs({ wallpaperBlur: Number(e.target.value) })}
              className="w-full accent-[#007AFF]"
            />
          </div>
        </div>

        {/* HOME SCREEN */}
        <SectionHeader>{isRtl ? 'صفحه اصلی و داک' : 'Home Screen'}</SectionHeader>
        <div className={`rounded-2xl border overflow-hidden divide-y ${dividerColor} ${cardBg}`}>
          <div className="px-4 py-3 space-y-1.5">
            <div className="flex items-center justify-between text-xs">
              <span>{isRtl ? 'تعداد ستون شبکه' : 'Grid Columns'}</span>
              <span className="font-mono opacity-60">{prefs.gridColumns ?? 4} cols</span>
            </div>
            <input
              type="range"
              min={4}
              max={6}
              value={prefs.gridColumns ?? 4}
              onChange={(e) => onUpdatePrefs({ gridColumns: Number(e.target.value) as 4 | 5 | 6 })}
              className="w-full accent-[#007AFF]"
            />
          </div>

          <div className="px-4 py-3 space-y-1.5">
            <div className="flex items-center justify-between text-xs">
              <span>{isRtl ? 'ظرفیت داک' : 'Dock Capacity'}</span>
              <span className="font-mono opacity-60">{prefs.dockCapacity ?? 4} icons</span>
            </div>
            <input
              type="range"
              min={4}
              max={6}
              value={prefs.dockCapacity ?? 4}
              onChange={(e) => onUpdatePrefs({ dockCapacity: Number(e.target.value) as 4 | 5 | 6 })}
              className="w-full accent-[#007AFF]"
            />
          </div>

          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'کپسول جستجو بالای داک' : 'Search Pill Above Dock'}</span>
            <IosSwitch
              label="Search Pill"
              checked={prefs.showSearchPill !== false}
              onChange={(v) => onUpdatePrefs({ showSearchPill: v })}
            />
          </div>

          {onOpenWidgetPicker && (
            <button
              onClick={() => {
                onClose();
                onOpenWidgetPicker();
              }}
              className="w-full px-4 py-3 flex items-center justify-between text-start hover:bg-white/5"
            >
              <span>{isRtl ? 'افزودن ویجت به صفحه اصلی' : 'Add Home Widget'}</span>
              <ChevronRight className={`w-4 h-4 opacity-40 ${isRtl ? 'rotate-180' : ''}`} />
            </button>
          )}
        </div>

        {/* APP LIBRARY & TODAY */}
        <SectionHeader>{isRtl ? 'کتابخانه برنامه‌ها و نمای امروز' : 'App Library & Today'}</SectionHeader>
        <div className={`rounded-2xl border overflow-hidden divide-y ${dividerColor} ${cardBg}`}>
          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'نمایش صفحه App Library' : 'Show App Library Page'}</span>
            <IosSwitch
              label="Show App Library"
              checked={prefs.showAppLibrary !== false}
              onChange={(v) => onUpdatePrefs({ showAppLibrary: v })}
            />
          </div>
          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'نمایش صفحه Today View' : 'Show Today Widget Page'}</span>
            <IosSwitch
              label="Show Today View"
              checked={prefs.showTodayPage !== false}
              onChange={(v) => onUpdatePrefs({ showTodayPage: v })}
            />
          </div>
          {onCycleCategoryOrder && (
            <button
              onClick={onCycleCategoryOrder}
              className="w-full px-4 py-3 flex items-center justify-between text-start hover:bg-white/5"
            >
              <span>{isRtl ? 'تغییر ترتیب دسته‌بندی‌ها' : 'Reorder Library Categories'}</span>
              <ChevronRight className={`w-4 h-4 opacity-40 ${isRtl ? 'rotate-180' : ''}`} />
            </button>
          )}
        </div>

        {/* SPOTLIGHT */}
        <SectionHeader>{isRtl ? 'اسپات‌لایت (Spotlight)' : 'Spotlight'}</SectionHeader>
        <div className={`rounded-2xl border overflow-hidden divide-y ${dividerColor} ${cardBg}`}>
          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'پیشنهادهای Siri (۴×۲)' : 'Show Siri Suggestions'}</span>
            <IosSwitch
              label="Siri Suggestions"
              checked={prefs.spotlightSuggestions !== false}
              onChange={(v) => onUpdatePrefs({ spotlightSuggestions: v })}
            />
          </div>
          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'نمایش تنظیمات سیستم در جستجو' : 'Include System Settings'}</span>
            <IosSwitch
              label="Include Settings"
              checked={prefs.spotlightIncludeSettings !== false}
              onChange={(v) => onUpdatePrefs({ spotlightIncludeSettings: v })}
            />
          </div>
        </div>

        {/* NOTIFICATIONS & BADGES */}
        <SectionHeader>{isRtl ? 'اعلان‌ها و نشان‌ها' : 'Notifications & Badges'}</SectionHeader>
        <div className={`rounded-2xl border overflow-hidden divide-y ${dividerColor} ${cardBg}`}>
          <button
            onClick={() => setShowNotifExplainer((v) => !v)}
            className="w-full px-4 py-3 flex items-center justify-between text-start"
          >
            <span>{isRtl ? 'دسترسی اعلان‌ها (آفلاین)' : 'Notification Listener Access'}</span>
            <span className={`text-xs font-semibold ${notifGranted ? 'text-[#34C759]' : 'text-[#007AFF]'}`}>
              {notifGranted ? (isRtl ? 'متصل' : 'Granted') : (isRtl ? 'فعال‌سازی' : 'Grant')}
            </span>
          </button>
          {showNotifExplainer && (
            <div className="px-4 py-2.5 text-[11px] opacity-75 space-y-2 bg-[#007AFF]/10">
              <p>
                {isRtl
                  ? 'کیومرث تعداد اعلان‌های فعال را صرفاً در حافظه دستگاه شمارش می‌کند و صفر داده به شبکه ارسال می‌شود.'
                  : 'Notification counts are tallied strictly in-memory with zero network permissions.'}
              </p>
              <button
                onClick={() => setNotifGranted((v) => !v)}
                className="px-2.5 py-1 rounded-lg bg-[#007AFF] text-white font-semibold"
              >
                {notifGranted ? (isRtl ? 'شبیه‌سازی قطع' : 'Simulate Revoke') : (isRtl ? 'فعال‌سازی' : 'Enable')}
              </button>
            </div>
          )}
          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'نشان شمارشگر قرمز' : 'Notification Count Badges'}</span>
            <IosSwitch
              label="Notification Badges"
              checked={prefs.notificationBadges !== false}
              onChange={(v) => onUpdatePrefs({ notificationBadges: v })}
            />
          </div>
          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'نقطه آبی برای برنامه‌های جدید' : 'Blue Dot on New Apps'}</span>
            <IosSwitch
              label="New App Blue Dot"
              checked={prefs.newAppDots !== false}
              onChange={(v) => onUpdatePrefs({ newAppDots: v })}
            />
          </div>
        </div>

        {/* GESTURES & MOTION */}
        <SectionHeader>{isRtl ? 'ژست‌ها و حرکت ۱۲۰ هرتز' : 'Gestures & Motion'}</SectionHeader>
        <div className={`rounded-2xl border overflow-hidden divide-y ${dividerColor} ${cardBg}`}>
          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'کشیدن به پایین برای Spotlight' : 'Swipe Down for Spotlight'}</span>
            <IosSwitch
              label="Swipe Down Spotlight"
              checked={prefs.swipeDownSpotlightEnabled !== false}
              onChange={(v) => onUpdatePrefs({ swipeDownSpotlightEnabled: v })}
            />
          </div>
          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'جمع‌کردن دو انگشت (Page Overview)' : 'Pinch In for Overview'}</span>
            <IosSwitch
              label="Pinch Overview"
              checked={prefs.pinchOverviewEnabled !== false}
              onChange={(v) => onUpdatePrefs({ pinchOverviewEnabled: v })}
            />
          </div>
          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'دو ضربه برای قفل صفحه' : 'Double Tap Empty Space to Lock'}</span>
            <IosSwitch
              label="Double Tap Lock"
              checked={prefs.gestures.doubleTapLock}
              onChange={(v) => onUpdatePrefs({ gestures: { ...prefs.gestures, doubleTapLock: v } })}
            />
          </div>
          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'کاهش انیمیشن (Reduced Motion)' : 'Reduced Motion'}</span>
            <IosSwitch
              label="Reduced Motion"
              checked={Boolean(prefs.reducedMotion)}
              onChange={(v) => onUpdatePrefs({ reducedMotion: v })}
            />
          </div>
          <div className="px-4 py-2.5 flex items-center justify-between">
            <span>{isRtl ? 'نمایشگر فریم ۱۲۰ هرتز (HUD)' : '120 Hz Frame-Time HUD'}</span>
            <IosSwitch
              label="Motion HUD"
              checked={Boolean(prefs.showMotionHud)}
              onChange={(v) => onUpdatePrefs({ showMotionHud: v })}
            />
          </div>
        </div>

        {/* RESET & ABOUT */}
        <SectionHeader>{isRtl ? 'بازنشانی و درباره' : 'Reset & About'}</SectionHeader>
        <div className={`rounded-2xl border overflow-hidden divide-y ${dividerColor} ${cardBg}`}>
          {onResetLayout && (
            <button
              onClick={() => setConfirmType('layout')}
              className="w-full px-4 py-3 text-start text-[#FF3B30] font-medium flex items-center gap-2"
            >
              <RotateCcw className="w-4 h-4" />
              <span>{isRtl ? 'بازنشانی چیدمان صفحه اصلی' : 'Reset Home Screen Layout'}</span>
            </button>
          )}
          <button
            onClick={() => setConfirmType('history')}
            className="w-full px-4 py-3 text-start text-[#FF3B30] font-medium flex items-center gap-2"
          >
            <RotateCcw className="w-4 h-4" />
            <span>{isRtl ? 'پاک‌کردن تاریخچه جستجو و اجرا' : 'Clear Launch & Search History'}</span>
          </button>
          <div className="px-4 py-3 flex items-center justify-between text-xs opacity-70">
            <span>Kayumars 2.0 (iOS Edition)</span>
            <span className="font-mono">Rust + Nucleo · 0 Network</span>
          </div>
        </div>

        {confirmType && (
          <div className="mt-3 p-3 rounded-2xl border border-[#FF3B30]/40 bg-[#FF3B30]/10 space-y-2 text-xs">
            <p className="font-semibold text-[#FF3B30]">
              {confirmType === 'layout'
                ? isRtl
                  ? 'بازگردانی صفحات، پوشه‌ها و داک به حالت پیش‌فرض؟'
                  : 'Restore default pages, folders, and dock?'
                : isRtl
                ? 'پاک‌کردن تمام تاریخچه رتبه‌بندی محلی؟'
                : 'Clear all locally stored app ranking history?'}
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => {
                  if (confirmType === 'layout') onResetLayout?.();
                  else onResetHistory();
                  setConfirmType(null);
                }}
                className="flex-1 py-2 rounded-xl bg-[#FF3B30] text-white font-bold"
              >
                {isRtl ? 'تایید' : 'Confirm'}
              </button>
              <button
                onClick={() => setConfirmType(null)}
                className="px-4 py-2 rounded-xl border border-current/20"
              >
                {isRtl ? 'انصراف' : 'Cancel'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
