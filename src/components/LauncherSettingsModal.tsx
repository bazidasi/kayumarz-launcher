import React, { useState } from 'react';
import {
  X,
  Sliders,
  EyeOff,
  Clock,
  LayoutGrid,
  Palette,
  Phone,
  Settings as SettingsIcon,
  ShieldCheck,
  Smartphone,
  Download,
  Upload,
  RotateCcw,
  Sparkles,
  Check,
  Layers,
  ArrowUpDown
} from 'lucide-react';
import { LauncherPreferences, AppEntry } from '../types/launcher';
import { KayumarsSimulationEngine } from '../engine/kayumarsCore';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  engine: KayumarsSimulationEngine;
  installedApps: AppEntry[];
  onPreferencesChange: (newPrefs: LauncherPreferences) => void;
}

export const LauncherSettingsModal: React.FC<Props> = ({
  isOpen,
  onClose,
  engine,
  installedApps,
  onPreferencesChange,
}) => {
  const [prefs, setPrefs] = useState<LauncherPreferences>(() => engine.getPreferences());
  const [activeTab, setActiveTab] = useState<'home' | 'search' | 'appearance' | 'dock' | 'backup'>('home');
  const [backupJson, setBackupJson] = useState('');
  const [importStatus, setImportStatus] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleToggle = (key: keyof LauncherPreferences, value: any) => {
    const updated = engine.updatePreferences({ [key]: value });
    setPrefs(updated);
    onPreferencesChange(updated);
  };

  const handleSearchScopeToggle = (scope: 'apps' | 'contacts' | 'settings') => {
    const updated = engine.updatePreferences({
      searchScopes: {
        ...prefs.searchScopes,
        [scope]: !prefs.searchScopes[scope],
      },
    });
    setPrefs(updated);
    onPreferencesChange(updated);
  };

  const handleGestureToggle = (gesture: 'doubleTapLock' | 'swipeUpDrawer' | 'swipeDownNotifications') => {
    const updated = engine.updatePreferences({
      gestures: {
        ...prefs.gestures,
        [gesture]: !prefs.gestures[gesture],
      },
    });
    setPrefs(updated);
    onPreferencesChange(updated);
  };

  const handleExport = () => {
    const data = engine.exportBackup();
    setBackupJson(data);
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'kayumars-settings-backup.json';
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleImport = () => {
    if (!backupJson) return;
    const success = engine.importBackup(backupJson);
    if (success) {
      const updated = engine.getPreferences();
      setPrefs(updated);
      onPreferencesChange(updated);
      setImportStatus('تنظیمات با موفقیت بازیابی شد ✅');
      setTimeout(() => setImportStatus(null), 3000);
    } else {
      setImportStatus('خطا در فایل پشتیبان ❌');
      setTimeout(() => setImportStatus(null), 3000);
    }
  };

  const handleReset = () => {
    if (window.confirm('آیا مایل به بازنشانی تمام تنظیمات به حالت اولیه هستید؟')) {
      const updated = engine.updatePreferences({
        showClockWidget: true,
        showDock: true,
        hiddenPackages: [],
        iconStyle: 'default',
        layoutMode: 'list',
        wallpaper: 'oled-black',
        searchScopes: { apps: true, contacts: true, settings: true },
        gestures: { doubleTapLock: true, swipeUpDrawer: true, swipeDownNotifications: true },
        oneHandedMode: false,
      });
      setPrefs(updated);
      onPreferencesChange(updated);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in">
      <div className="bg-slate-900 border border-slate-800 rounded-3xl w-full max-w-2xl max-h-[85vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="p-5 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <span className="w-8 h-8 rounded-xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Sliders className="w-4 h-4" />
            </span>
            <div>
              <h2 className="text-base font-bold text-slate-100">تنظیمات مینیمال کیومرث</h2>
              <span className="text-[11px] text-slate-400">کمتر داشته باش، اما عالی انجام بده</span>
            </div>
          </div>

          <button
            onClick={onClose}
            className="w-8 h-8 rounded-lg flex items-center justify-center text-slate-400 hover:text-slate-100 hover:bg-slate-800 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Navigation Tabs */}
        <div className="flex border-b border-slate-800 bg-slate-950/60 px-4 py-2 gap-2 overflow-x-auto text-xs">
          <button
            onClick={() => setActiveTab('home')}
            className={`px-3 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
              activeTab === 'home' ? 'bg-slate-800 text-cyan-400 font-medium' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            صفحه اصلی و ناوبری
          </button>
          <button
            onClick={() => setActiveTab('search')}
            className={`px-3 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
              activeTab === 'search' ? 'bg-slate-800 text-cyan-400 font-medium' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            جستجوی یکپارچه
          </button>
          <button
            onClick={() => setActiveTab('appearance')}
            className={`px-3 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
              activeTab === 'appearance' ? 'bg-slate-800 text-cyan-400 font-medium' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            شخصی‌سازی و آیکون‌ها
          </button>
          <button
            onClick={() => setActiveTab('dock')}
            className={`px-3 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
              activeTab === 'dock' ? 'bg-slate-800 text-cyan-400 font-medium' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            داک و ژست‌ها
          </button>
          <button
            onClick={() => setActiveTab('backup')}
            className={`px-3 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
              activeTab === 'backup' ? 'bg-slate-800 text-cyan-400 font-medium' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            پشتیبان‌گیری
          </button>
        </div>

        {/* Tab Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6 text-sm text-slate-300">
          {/* TAB 1: Home & Navigation */}
          {activeTab === 'home' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between p-3.5 bg-slate-950/70 rounded-xl border border-slate-800/80">
                <div>
                  <span className="font-semibold text-slate-100 block text-xs">نمایش ویجت ساعت و تاریخ مینیمال</span>
                  <span className="text-[11px] text-slate-400">ساعت دیجیتال بدون سربار انیمیشن در بالای صفحه خانه</span>
                </div>
                <input
                  type="checkbox"
                  checked={prefs.showClockWidget}
                  onChange={(e) => handleToggle('showClockWidget', e.target.checked)}
                  className="w-4 h-4 accent-cyan-400 cursor-pointer"
                />
              </div>

              <div className="flex items-center justify-between p-3.5 bg-slate-950/70 rounded-xl border border-slate-800/80">
                <div>
                  <span className="font-semibold text-slate-100 block text-xs">حالت ارگونومیک تک‌دست (One-Handed Mode)</span>
                  <span className="text-[11px] text-slate-400">انتقال نوار جستجو و تعاملات به بخش پایینی صفحه جهت دسترسی راحت شست</span>
                </div>
                <input
                  type="checkbox"
                  checked={prefs.oneHandedMode}
                  onChange={(e) => handleToggle('oneHandedMode', e.target.checked)}
                  className="w-4 h-4 accent-cyan-400 cursor-pointer"
                />
              </div>

              {/* Hidden Apps Section */}
              <div className="p-4 bg-slate-950/70 rounded-xl border border-slate-800/80 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <EyeOff className="w-4 h-4 text-cyan-400" />
                    <span className="font-semibold text-slate-100 text-xs">مدیریت برنامه‌های مخفی ({prefs.hiddenPackages.length})</span>
                  </div>
                  <span className="text-[11px] text-slate-400">حذف برنامه‌های کم‌استفاده از کشو</span>
                </div>

                <div className="max-h-48 overflow-y-auto divide-y divide-slate-800/40 rounded-lg border border-slate-800/60 p-2 text-xs">
                  {installedApps.slice(0, 30).map((app) => {
                    const isHidden = engine.isAppHidden(app.package);
                    return (
                      <div key={app.package} className="py-1.5 px-2 flex items-center justify-between">
                        <span className="truncate">{app.label}</span>
                        <button
                          onClick={() => {
                            engine.toggleHideApp(app.package);
                            setPrefs(engine.getPreferences());
                            onPreferencesChange(engine.getPreferences());
                          }}
                          className={`px-2 py-0.5 rounded text-[10px] font-medium transition-colors ${
                            isHidden
                              ? 'bg-amber-950/80 text-amber-300 border border-amber-800/50'
                              : 'bg-slate-800 text-slate-400 hover:text-slate-200'
                          }`}
                        >
                          {isHidden ? 'مخفی شده' : 'نمایش'}
                        </button>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: Unified Search */}
          {activeTab === 'search' && (
            <div className="space-y-4">
              <p className="text-xs text-slate-400 leading-relaxed">
                جستجوی یکپارچه کیومرث می‌تواند علاوه بر برنامه‌ها، مخاطبین تلفن و میانبرهای تنظیمات سریع اندروید را مستقیماً در نوار جستجو پیدا کند. هر حوزه به صورت اختیاری قابل خاموش شدن است:
              </p>

              <div className="flex items-center justify-between p-3.5 bg-slate-950/70 rounded-xl border border-slate-800/80">
                <div className="flex items-center gap-2.5">
                  <Layers className="w-4 h-4 text-blue-400" />
                  <div>
                    <span className="font-semibold text-slate-100 block text-xs">جستجوی برنامه‌ها (Apps Search)</span>
                    <span className="text-[11px] text-slate-400">جستجوی فازی Nucleo بین تمام برنامه‌های نصب‌شده</span>
                  </div>
                </div>
                <input
                  type="checkbox"
                  checked={prefs.searchScopes.apps}
                  onChange={() => handleSearchScopeToggle('apps')}
                  className="w-4 h-4 accent-cyan-400 cursor-pointer"
                />
              </div>

              <div className="flex items-center justify-between p-3.5 bg-slate-950/70 rounded-xl border border-slate-800/80">
                <div className="flex items-center gap-2.5">
                  <Phone className="w-4 h-4 text-emerald-400" />
                  <div>
                    <span className="font-semibold text-slate-100 block text-xs">جستجوی مخاطبین (Contacts Search)</span>
                    <span className="text-[11px] text-slate-400">یافتن شماره‌ها و اسامی مخاطبین جهت تماس مستقیم</span>
                  </div>
                </div>
                <input
                  type="checkbox"
                  checked={prefs.searchScopes.contacts}
                  onChange={() => handleSearchScopeToggle('contacts')}
                  className="w-4 h-4 accent-cyan-400 cursor-pointer"
                />
              </div>

              <div className="flex items-center justify-between p-3.5 bg-slate-950/70 rounded-xl border border-slate-800/80">
                <div className="flex items-center gap-2.5">
                  <SettingsIcon className="w-4 h-4 text-amber-400" />
                  <div>
                    <span className="font-semibold text-slate-100 block text-xs">جستجوی تنظیمات سیستم (Android Settings)</span>
                    <span className="text-[11px] text-slate-400">دسترسی فوری به تنظیمات وای‌فای، باتری، بلوتوث و صفحه نمایش</span>
                  </div>
                </div>
                <input
                  type="checkbox"
                  checked={prefs.searchScopes.settings}
                  onChange={() => handleSearchScopeToggle('settings')}
                  className="w-4 h-4 accent-cyan-400 cursor-pointer"
                />
              </div>
            </div>
          )}

          {/* TAB 3: Appearance & Personalization */}
          {activeTab === 'appearance' && (
            <div className="space-y-5">
              {/* Icon Pack Selection */}
              <div>
                <label className="text-xs font-semibold text-slate-200 block mb-2">استایل آیکون‌ها (Icon Pack)</label>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                  {[
                    { id: 'default', label: 'پیش‌فرض رنگی' },
                    { id: 'monochrome', label: 'مونوکروم مینیمال' },
                    { id: 'minimal-circle', label: 'دایره‌ای هندسی' },
                    { id: 'text-only', label: 'فقط تایپوگرافی' },
                  ].map((style) => (
                    <button
                      key={style.id}
                      onClick={() => handleToggle('iconStyle', style.id)}
                      className={`p-3 rounded-xl border text-center transition-all ${
                        prefs.iconStyle === style.id
                          ? 'border-cyan-400 bg-cyan-950/40 text-cyan-300 font-semibold shadow-sm'
                          : 'border-slate-800 bg-slate-950/60 text-slate-400 hover:text-slate-200'
                      }`}
                    >
                      {style.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Layout Mode */}
              <div>
                <label className="text-xs font-semibold text-slate-200 block mb-2">چیدمان کشوی برنامه‌ها (Layout Mode)</label>
                <div className="grid grid-cols-3 gap-2 text-xs">
                  {[
                    { id: 'list', label: 'فهرست تک‌ستونه (سریع‌ترین)' },
                    { id: 'grid-4x4', label: 'شبکه ۴ × ۴' },
                    { id: 'grid-4x5', label: 'شبکه ۴ × ۵' },
                  ].map((mode) => (
                    <button
                      key={mode.id}
                      onClick={() => handleToggle('layoutMode', mode.id)}
                      className={`p-3 rounded-xl border text-center transition-all ${
                        prefs.layoutMode === mode.id
                          ? 'border-cyan-400 bg-cyan-950/40 text-cyan-300 font-semibold shadow-sm'
                          : 'border-slate-800 bg-slate-950/60 text-slate-400 hover:text-slate-200'
                      }`}
                    >
                      {mode.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Minimal Wallpaper */}
              <div>
                <label className="text-xs font-semibold text-slate-200 block mb-2">والپیپر مینیمال پس‌زمینه (Wallpaper)</label>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                  {[
                    { id: 'oled-black', label: 'مشکی مطلق OLED', bg: 'bg-black' },
                    { id: 'dark-slate', label: 'طوسی تیره متالیک', bg: 'bg-slate-950' },
                    { id: 'nordic', label: 'آبی اقیانوسی سرد', bg: 'bg-slate-900' },
                    { id: 'warm-light', label: 'حالت روشن مینیمال', bg: 'bg-slate-100 text-slate-900' },
                  ].map((w) => (
                    <button
                      key={w.id}
                      onClick={() => handleToggle('wallpaper', w.id)}
                      className={`p-3 rounded-xl border flex items-center justify-between transition-all ${
                        prefs.wallpaper === w.id
                          ? 'border-cyan-400 bg-cyan-950/40 text-cyan-300 font-semibold'
                          : 'border-slate-800 bg-slate-950/60 text-slate-400 hover:text-slate-200'
                      }`}
                    >
                      <span>{w.label}</span>
                      <span className={`w-3.5 h-3.5 rounded-full border border-slate-700 ${w.bg}`} />
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: Dock & Gestures */}
          {activeTab === 'dock' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between p-3.5 bg-slate-950/70 rounded-xl border border-slate-800/80">
                <div>
                  <span className="font-semibold text-slate-100 block text-xs">نمایش داک برنامه‌های ثابت (Bottom Dock)</span>
                  <span className="text-[11px] text-slate-400">دسترسی سریع به ۴ برنامه پرکاربرد در پایین صفحه اصلی</span>
                </div>
                <input
                  type="checkbox"
                  checked={prefs.showDock}
                  onChange={(e) => handleToggle('showDock', e.target.checked)}
                  className="w-4 h-4 accent-cyan-400 cursor-pointer"
                />
              </div>

              {/* Gestures */}
              <div className="p-4 bg-slate-950/70 rounded-xl border border-slate-800/80 space-y-3">
                <span className="font-semibold text-slate-100 text-xs block">ژست‌های حرکتی سریع (Gestures)</span>

                <div className="space-y-2.5 text-xs">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="text-slate-200 block">دوبار تپ برای قفل صفحه (Double Tap to Sleep)</span>
                      <span className="text-[10px] text-slate-500">خاموش کردن صفحه با دو بار ضربه در فضای خالی</span>
                    </div>
                    <input
                      type="checkbox"
                      checked={prefs.gestures.doubleTapLock}
                      onChange={() => handleGestureToggle('doubleTapLock')}
                      className="w-4 h-4 accent-cyan-400 cursor-pointer"
                    />
                  </div>

                  <div className="flex items-center justify-between pt-2 border-t border-slate-800/60">
                    <div>
                      <span className="text-slate-200 block">کشیدن به بالا برای باز کردن کشو (Swipe Up for Drawer)</span>
                      <span className="text-[10px] text-slate-500">باز شدن سریع کشوی برنامه‌ها با سوایپ به سمت بالا</span>
                    </div>
                    <input
                      type="checkbox"
                      checked={prefs.gestures.swipeUpDrawer}
                      onChange={() => handleGestureToggle('swipeUpDrawer')}
                      className="w-4 h-4 accent-cyan-400 cursor-pointer"
                    />
                  </div>

                  <div className="flex items-center justify-between pt-2 border-t border-slate-800/60">
                    <div>
                      <span className="text-slate-200 block">کشیدن به پایین برای پنل اعلان‌ها (Swipe Down Notifications)</span>
                      <span className="text-[10px] text-slate-500">پایین کشیدن استاتوس بار با کشیدن انگشت به پایین</span>
                    </div>
                    <input
                      type="checkbox"
                      checked={prefs.gestures.swipeDownNotifications}
                      onChange={() => handleGestureToggle('swipeDownNotifications')}
                      className="w-4 h-4 accent-cyan-400 cursor-pointer"
                    />
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 5: Backup & Reset */}
          {activeTab === 'backup' && (
            <div className="space-y-5">
              <p className="text-xs text-slate-400 leading-relaxed">
                می‌توانید پیکربندی لانچر، فهرست برنامه‌های مخفی و اولویت‌های داک را در یک فایل استاندارد JSON ذخیره کنید و بر روی دستگاه‌های دیگر بازیابی نمایید:
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <button
                  onClick={handleExport}
                  className="flex items-center justify-center gap-2 p-3.5 rounded-xl bg-slate-950 border border-slate-800 hover:border-cyan-500/60 text-slate-100 text-xs font-semibold transition-colors"
                >
                  <Download className="w-4 h-4 text-cyan-400" />
                  <span>دانلود فایل پشتیبان (Export JSON)</span>
                </button>

                <button
                  onClick={handleImport}
                  className="flex items-center justify-center gap-2 p-3.5 rounded-xl bg-slate-950 border border-slate-800 hover:border-emerald-500/60 text-slate-100 text-xs font-semibold transition-colors"
                >
                  <Upload className="w-4 h-4 text-emerald-400" />
                  <span>بازیابی از متن یا فایل (Import)</span>
                </button>
              </div>

              {importStatus && (
                <div className="p-3 text-center text-xs font-semibold rounded-lg bg-slate-950 border border-slate-800 text-emerald-400">
                  {importStatus}
                </div>
              )}

              <textarea
                value={backupJson}
                onChange={(e) => setBackupJson(e.target.value)}
                placeholder="متن JSON پشتیبان را اینجا الصاق کنید..."
                className="w-full h-28 p-3 text-xs font-mono rounded-xl bg-slate-950 border border-slate-800 text-slate-300 placeholder:text-slate-600 focus:outline-none focus:border-cyan-500"
              />

              <div className="pt-4 border-t border-slate-800 flex justify-between items-center">
                <button
                  onClick={handleReset}
                  className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs text-red-400 bg-red-950/20 hover:bg-red-950/40 border border-red-900/40 transition-colors"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>بازنشانی به تنظیمات پیش‌فرض</span>
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="p-4 bg-slate-950 border-t border-slate-800 flex items-center justify-between text-xs">
          <span className="text-slate-500 font-mono">Kayumars v1.0.0 · 100% Offline &amp; Ad-Free</span>
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-bold transition-all"
          >
            تایید و ذخیره
          </button>
        </div>
      </div>
    </div>
  );
};
