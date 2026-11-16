import React, { useState } from 'react';
import {
  Activity,
  BatteryCharging,
  Cpu,
  Hand,
  Sliders,
  Smartphone,
  Sparkles,
  Zap,
} from 'lucide-react';
import {
  ACTION_LABELS,
  ALL_V1_GESTURES,
  DispatchedCommand,
  GestureFsmState,
  GestureKind,
  GestureProfilePreset,
  GestureRule,
  IconGestureBinding,
  SemanticActionType,
} from '../engine/gestureEngine';
import { AppEntry } from '../types/launcher';

interface Props {
  preset: GestureProfilePreset;
  rules: Record<GestureKind, GestureRule>;
  iconGestures: Record<string, Partial<Record<GestureKind, IconGestureBinding>>>;
  installedApps: AppEntry[];
  fsmState: GestureFsmState;
  twoFingerMode: boolean;
  onToggleTwoFingerMode: () => void;
  dispatchLog: DispatchedCommand[];
  onChangePreset: (preset: GestureProfilePreset) => void;
  onUpdateRule: (kind: GestureKind, patch: Partial<GestureRule>) => void;
  onUpdateIconGesture: (
    pkg: string,
    kind: GestureKind,
    binding: IconGestureBinding | null
  ) => void;
  onTriggerTestGesture: (kind: GestureKind, targetPkg?: string) => void;
  isRtl: boolean;
}

const PRESETS: Array<{
  id: GestureProfilePreset;
  title: string;
  faTitle: string;
  badge: string;
  batteryDesc: string;
}> = [
  {
    id: 'BalancedDefault',
    title: 'Balanced (v1.0)',
    faTitle: 'متعادل (۱۲ ژست)',
    badge: '< 0.2% CPU',
    batteryDesc: 'All 12 gestures · Multi-touch Home-only',
  },
  {
    id: 'BatterySaver',
    title: 'Battery Saver',
    faTitle: 'ذخیره باتری',
    badge: 'Ultra Low',
    batteryDesc: 'Single-touch only · Zero multi-touch checks',
  },
  {
    id: 'OneHanded',
    title: 'One-Handed',
    faTitle: 'یک‌دستی',
    badge: 'Ergonomic',
    batteryDesc: 'Swipe Down → Search · Shorter distance',
  },
  {
    id: 'PowerUser',
    title: 'Power User',
    faTitle: 'حرفه‌ای',
    badge: 'Fast 220ms',
    batteryDesc: 'Rapid Double-Tap + Search Swipe',
  },
];

const SELECTABLE_ACTIONS: SemanticActionType[] = [
  'LaunchTarget',
  'LockScreen',
  'ExpandNotifications',
  'ExpandQuickSettings',
  'OpenAppDrawer',
  'OpenSearch',
  'NextPage',
  'PreviousPage',
  'OpenOverview',
  'ReturnHome',
  'OpenLauncherSettings',
  'OpenEditMenu',
  'MoveIcon',
  'None',
];

const ICON_GESTURE_SLOTS: GestureKind[] = [
  'SingleTap',
  'DoubleTap',
  'SwipeUp',
  'SwipeDown',
  'SwipeLeft',
  'SwipeRight',
];

export const GestureEnginePanel: React.FC<Props> = ({
  preset,
  rules,
  iconGestures,
  installedApps,
  fsmState,
  twoFingerMode,
  onToggleTwoFingerMode,
  dispatchLog,
  onChangePreset,
  onUpdateRule,
  onUpdateIconGesture,
  onTriggerTestGesture,
  isRtl,
}) => {
  const [subTab, setSubTab] = useState<'overview' | 'rules' | 'icons'>('overview');
  const [selectedIconPkg, setSelectedIconPkg] = useState<string>('org.telegram.messenger');

  const enabledCount = ALL_V1_GESTURES.filter((k) => rules[k]?.enabled).length;
  const multiEnabledCount = ALL_V1_GESTURES.filter(
    (k) => rules[k]?.enabled && rules[k]?.isMultiTouch
  ).length;

  const curatedIconApps = [
    installedApps.find((a) => a.package === 'org.telegram.messenger'),
    installedApps.find((a) => a.package === 'com.android.camera'),
    installedApps.find((a) => a.package === 'com.google.android.dialer'),
    installedApps.find((a) => a.package === 'com.microsoft.emmx'),
    installedApps.find((a) => a.package === 'com.google.android.youtube'),
    installedApps.find((a) => a.package === 'com.instagram.android'),
  ].filter(Boolean) as AppEntry[];

  const activeIconMap = iconGestures[selectedIconPkg] || {};

  return (
    <div className="flex-1 max-w-md w-full space-y-4 text-xs">
      {/* Architecture Header & Battery Telemetry */}
      <div className="p-4 rounded-2xl bg-slate-900/90 border border-slate-800 space-y-3">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Hand className="w-4 h-4" />
            </div>
            <div>
              <span className="font-bold text-slate-100 block text-sm">
                موتور ژست‌های هیبریدی کیومرث (v1.0)
              </span>
              <span className="text-[10px] text-slate-400 font-mono">
                Rust gesture_engine + Decoupled Android Executor
              </span>
            </div>
          </div>
          <span className="px-2 py-1 rounded-lg bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 font-mono text-[10px] font-bold">
            Event-Driven
          </span>
        </div>

        <div className="grid grid-cols-3 gap-2 text-[11px] font-mono">
          <div className="p-2 rounded-xl bg-slate-950 border border-slate-800/80">
            <span className="text-slate-400 block text-[10px]">FSM State</span>
            <span className="text-cyan-400 font-bold truncate block">{fsmState}</span>
          </div>
          <div className="p-2 rounded-xl bg-slate-950 border border-slate-800/80">
            <span className="text-slate-400 block text-[10px]">Active Detectors</span>
            <span className="text-slate-200 font-bold">{enabledCount} / 12</span>
          </div>
          <div className="p-2 rounded-xl bg-slate-950 border border-slate-800/80">
            <span className="text-slate-400 block text-[10px]">Battery Mode</span>
            <span className="text-emerald-400 font-bold">
              {multiEnabledCount === 0 ? '0% Multi' : 'Home-Gated'}
            </span>
          </div>
        </div>

        {/* Sub-tab Switcher */}
        <div className="grid grid-cols-3 gap-1.5 bg-slate-950 p-1 rounded-xl border border-slate-800">
          {[
            { id: 'overview', label: 'تست و پروفایل', icon: Activity },
            { id: 'rules', label: '۱۲ ژست پایه', icon: Sliders },
            { id: 'icons', label: 'ژست آیکون‌ها', icon: Sparkles },
          ].map((t) => {
            const Icon = t.icon;
            const active = subTab === t.id;
            return (
              <button
                key={t.id}
                onClick={() => setSubTab(t.id as any)}
                className={`py-1.5 px-2 rounded-lg flex items-center justify-center gap-1.5 text-[11px] font-medium transition-all ${
                  active
                    ? 'bg-cyan-400 text-slate-950 font-bold'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                <span>{t.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* TAB 1: PROFILE PRESETS, 2-FINGER SIMULATOR & LIVE DISPATCH LOG */}
      {subTab === 'overview' && (
        <div className="space-y-3">
          {/* Profile Selector */}
          <div className="p-4 rounded-2xl bg-slate-900/85 border border-slate-800 space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-200 flex items-center gap-1.5">
                <BatteryCharging className="w-4 h-4 text-emerald-400" />
                <span>پروفیل مصرف انرژی و حساسیت</span>
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2">
              {PRESETS.map((p) => {
                const active = preset === p.id;
                return (
                  <button
                    key={p.id}
                    onClick={() => onChangePreset(p.id)}
                    className={`p-2.5 rounded-xl border text-start transition-all ${
                      active
                        ? 'border-cyan-400 bg-cyan-500/15 text-slate-100'
                        : 'border-slate-800 bg-slate-950/70 text-slate-400 hover:border-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-1">
                      <span className="font-bold text-[11px] text-slate-100">{p.faTitle}</span>
                      <span className="text-[9px] font-mono text-cyan-400">{p.badge}</span>
                    </div>
                    <span className="text-[10px] text-slate-400 block mt-1 leading-tight">
                      {p.batteryDesc}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Interactive 12-Gesture Quick Simulator Grid */}
          <div className="p-4 rounded-2xl bg-slate-900/85 border border-slate-800 space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-200 flex items-center gap-1.5">
                <Smartphone className="w-4 h-4 text-cyan-400" />
                <span>آزمایش سریع ۱۲ ژست v1.0 روی گوشی</span>
              </span>
              <button
                onClick={onToggleTwoFingerMode}
                className={`px-2.5 py-1 rounded-lg text-[10px] font-mono font-bold border transition-all ${
                  twoFingerMode
                    ? 'bg-amber-400 text-slate-950 border-amber-300'
                    : 'bg-slate-800 text-slate-300 border-slate-700'
                }`}
              >
                {twoFingerMode ? '2-Finger ON' : 'Shift = 2-Finger'}
              </button>
            </div>
            <p className="text-[11px] text-slate-400 leading-relaxed">
              مستقیماً روی صفحه گوشی بکشید/دوبار بزنید (با دکمه 2-Finger یا نگه داشتن Shift برای ژست‌های دو انگشتی) یا از دکمه‌های تست زیر استفاده کنید:
            </p>
            <div className="grid grid-cols-3 gap-1.5">
              {ALL_V1_GESTURES.map((kind) => {
                const rule = rules[kind];
                return (
                  <button
                    key={kind}
                    disabled={!rule.enabled}
                    onClick={() => onTriggerTestGesture(kind)}
                    className={`p-2 rounded-xl border text-start transition-all ${
                      rule.enabled
                        ? 'bg-slate-950 border-slate-800 hover:border-cyan-500/60 hover:bg-slate-900'
                        : 'bg-slate-950/40 border-slate-900 opacity-40 cursor-not-allowed'
                    }`}
                  >
                    <span className="text-[10px] font-bold text-slate-200 block truncate">
                      {isRtl ? rule.faLabel : rule.label}
                    </span>
                    <span className="text-[9px] font-mono text-cyan-400 block truncate mt-0.5">
                      → {ACTION_LABELS[rule.action].en}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Live Rust Recognizer -> Kotlin Executor Log */}
          <div className="p-4 rounded-2xl bg-slate-900/85 border border-slate-800 space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-200 flex items-center gap-1.5">
                <Cpu className="w-4 h-4 text-emerald-400" />
                <span>لاگ زنده تشخیص Rust → اجرای Android</span>
              </span>
              <span className="text-[10px] font-mono text-slate-500">Zero Background Loop</span>
            </div>
            {dispatchLog.length === 0 ? (
              <div className="text-[11px] text-slate-500 py-3 text-center bg-slate-950/60 rounded-xl border border-slate-800/70">
                روی صفحه گوشی یا آیکون تلگرام ژست اجرا کنید تا رویداد ثبت شود.
              </div>
            ) : (
              <div className="space-y-1.5 max-h-40 overflow-y-auto pr-1">
                {dispatchLog.slice(0, 6).map((item) => (
                  <div
                    key={item.id}
                    className="px-2.5 py-1.5 rounded-lg bg-slate-950 border border-slate-800/80 flex items-center justify-between gap-2 text-[10px] font-mono"
                  >
                    <div className="min-w-0">
                      <span className="text-cyan-400 font-bold">{item.gesture}</span>
                      <span className="text-slate-500 mx-1">@{item.zone}</span>
                      {item.targetPackage && (
                        <span className="text-amber-300 truncate">
                          ({item.targetPackage.split('.').pop()})
                        </span>
                      )}
                    </div>
                    <span className="text-emerald-400 font-semibold shrink-0">
                      {item.shortcutLabel || ACTION_LABELS[item.action].en}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 2: ALL 12 GESTURE RULES & THRESHOLD TUNING */}
      {subTab === 'rules' && (
        <div className="p-4 rounded-2xl bg-slate-900/85 border border-slate-800 space-y-2.5 max-h-[540px] overflow-y-auto">
          <div className="flex items-center justify-between pb-1 border-b border-slate-800">
            <span className="font-semibold text-slate-200">
              تنظیم حساسیت، حداقل فاصله و سرعت تشخیص
            </span>
            <span className="text-[10px] font-mono text-slate-400">12 Rules</span>
          </div>

          {ALL_V1_GESTURES.map((kind) => {
            const r = rules[kind];
            return (
              <div
                key={kind}
                className="p-3 rounded-xl bg-slate-950 border border-slate-800/80 space-y-2"
              >
                <div className="flex items-center justify-between gap-2">
                  <div>
                    <span className="font-bold text-slate-100 block">
                      {isRtl ? r.faLabel : r.label}
                    </span>
                    <span className="text-[10px] font-mono text-slate-500">
                      {r.isMultiTouch ? 'Multi-Touch (Home-only)' : 'Single-Touch'}
                    </span>
                  </div>
                  <label className="flex items-center gap-1.5 cursor-pointer">
                    <span className="text-[10px] text-slate-400">
                      {r.enabled ? 'فعال' : 'خاموش'}
                    </span>
                    <input
                      type="checkbox"
                      checked={r.enabled}
                      onChange={(e) => onUpdateRule(kind, { enabled: e.target.checked })}
                      className="accent-cyan-400 w-3.5 h-3.5"
                    />
                  </label>
                </div>

                {r.enabled && (
                  <div className="space-y-2 pt-1">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-[10px] text-slate-400 shrink-0">Action:</span>
                      <select
                        value={r.action}
                        onChange={(e) =>
                          onUpdateRule(kind, { action: e.target.value as SemanticActionType })
                        }
                        className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-2 py-1 text-[11px] text-slate-100 outline-none"
                      >
                        {SELECTABLE_ACTIONS.map((a) => (
                          <option key={a} value={a}>
                            {isRtl ? ACTION_LABELS[a].fa : ACTION_LABELS[a].en}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-[10px]">
                      <div>
                        <div className="flex justify-between text-slate-400">
                          <span>حساسیت</span>
                          <span className="font-mono text-cyan-400">
                            {r.sensitivity.toFixed(2)}x
                          </span>
                        </div>
                        <input
                          type="range"
                          min="0.5"
                          max="2.0"
                          step="0.1"
                          value={r.sensitivity}
                          onChange={(e) =>
                            onUpdateRule(kind, { sensitivity: parseFloat(e.target.value) })
                          }
                          className="w-full accent-cyan-400"
                        />
                      </div>

                      <div>
                        <div className="flex justify-between text-slate-400">
                          <span>مهلت تشخیص</span>
                          <span className="font-mono text-emerald-400">{r.timeoutMs} ms</span>
                        </div>
                        <input
                          type="range"
                          min="150"
                          max="800"
                          step="20"
                          value={r.timeoutMs}
                          onChange={(e) =>
                            onUpdateRule(kind, { timeoutMs: parseInt(e.target.value, 10) })
                          }
                          className="w-full accent-emerald-400"
                        />
                      </div>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* TAB 3: PER-ICON DEDICATED GESTURES (e.g., Telegram) */}
      {subTab === 'icons' && (
        <div className="p-4 rounded-2xl bg-slate-900/85 border border-slate-800 space-y-3">
          <div>
            <span className="font-semibold text-slate-200 block">
              ژست‌های اختصاصی برای هر آیکون (Per-Icon Gestures)
            </span>
            <span className="text-[11px] text-slate-400 block mt-0.5">
              مثلاً روی آیکون تلگرام در صفحه اصلی گوشی بکشید یا دابل‌تپ بزنید:
            </span>
          </div>

          <div className="flex flex-wrap gap-1.5">
            {curatedIconApps.map((app) => {
              const active = selectedIconPkg === app.package;
              return (
                <button
                  key={app.package}
                  onClick={() => setSelectedIconPkg(app.package)}
                  className={`px-2.5 py-1.5 rounded-lg text-[11px] font-medium border transition-all ${
                    active
                      ? 'bg-cyan-400 text-slate-950 border-cyan-300 font-bold'
                      : 'bg-slate-950 text-slate-300 border-slate-800 hover:border-slate-700'
                  }`}
                >
                  {app.label}
                </button>
              );
            })}
          </div>

          <div className="space-y-1.5">
            {ICON_GESTURE_SLOTS.map((kind) => {
              const binding = activeIconMap[kind];
              const ruleLabel = rules[kind].label;
              return (
                <div
                  key={kind}
                  className="p-2.5 rounded-xl bg-slate-950 border border-slate-800/80 flex items-center justify-between gap-2"
                >
                  <div className="min-w-0">
                    <span className="font-bold text-slate-200 block text-[11px]">
                      {ruleLabel}
                    </span>
                    <span className="text-[10px] text-cyan-400 font-mono truncate block">
                      {binding
                        ? isRtl
                          ? binding.faLabel
                          : binding.label
                        : kind === 'SingleTap'
                        ? 'Default: Open App'
                        : 'Not assigned'}
                    </span>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      onClick={() => onTriggerTestGesture(kind, selectedIconPkg)}
                      className="px-2 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-[10px] text-slate-200 flex items-center gap-1"
                      title="Simulate on this icon"
                    >
                      <Zap className="w-3 h-3 text-cyan-400" />
                      <span>تست</span>
                    </button>
                    {kind !== 'SingleTap' && (
                      <select
                        value={binding?.label || binding?.action || 'default'}
                        onChange={(e) => {
                          const val = e.target.value;
                          if (val === 'default') {
                            onUpdateIconGesture(selectedIconPkg, kind, null);
                          } else {
                            onUpdateIconGesture(selectedIconPkg, kind, {
                              kind,
                              action:
                                val === 'LockScreen' || val === 'OpenSearch'
                                  ? (val as SemanticActionType)
                                  : 'LaunchShortcut',
                              shortcutId: val.toLowerCase().replace(/\s+/g, '_'),
                              label: val,
                              faLabel: val,
                            });
                          }
                        }}
                        className="bg-slate-900 border border-slate-700 rounded-lg px-1.5 py-1 text-[10px] text-slate-200 max-w-[120px]"
                      >
                        <option value="default">Default</option>
                        <option value="Saved Messages">Saved Messages</option>
                        <option value="New Message">New Message</option>
                        <option value="Search">Search</option>
                        <option value="Contacts">Contacts</option>
                        <option value="Settings">App Settings</option>
                        <option value="OpenSearch">Launcher Search</option>
                        <option value="LockScreen">Lock Screen</option>
                      </select>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};
