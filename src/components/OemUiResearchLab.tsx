import React, { useMemo, useState } from 'react';
import { ArrowLeftRight, CheckCircle2, Layers, Play, Sliders, Sparkles } from 'lucide-react';
import {
  OEM_UI_PROFILES,
  OemPhilosophy,
  OemUiProfile,
  UI_LAYER_TABS,
  UiLayerKey,
} from '../data/oemUiResearch';

interface Props {
  onApplyPresetInSimulator: (profile: OemUiProfile) => void;
}

const PHILOSOPHY_FILTERS: Array<{ key: 'all' | OemPhilosophy; label: string }> = [
  { key: 'all', label: 'همه برندها (۱۰)' },
  { key: 'minimal', label: 'مینیمال و استاک' },
  { key: 'modular', label: 'ماژولار و آزاد' },
  { key: 'ecosystem', label: 'پرآپشن و اکوسیستم' },
];

const LAYER_ROWS: Array<{ key: keyof OemUiProfile['layers']; title: string }> = [
  { key: 'lockAod', title: 'صفحه قفل و AOD' },
  { key: 'homeFolders', title: 'صفحه خانه، ویجت و پوشه' },
  { key: 'drawer', title: 'کشوی برنامه‌ها' },
  { key: 'controlCapsule', title: 'کنترل‌سنتر و کپسول زنده' },
  { key: 'themeIcons', title: 'موتور رنگ و آیکون' },
  { key: 'gesturesReach', title: 'ژست‌ها و ارگونومی یک‌دستی' },
];

function getLayerField(layer: UiLayerKey): keyof OemUiProfile['layers'] | null {
  switch (layer) {
    case 'lock_aod':
      return 'lockAod';
    case 'home_folders':
      return 'homeFolders';
    case 'drawer':
      return 'drawer';
    case 'control_capsule':
      return 'controlCapsule';
    case 'theme_icons':
      return 'themeIcons';
    case 'gestures_reach':
      return 'gesturesReach';
    default:
      return null;
  }
}

export const OemUiResearchLab: React.FC<Props> = ({ onApplyPresetInSimulator }) => {
  const [philosophyFilter, setPhilosophyFilter] = useState<'all' | OemPhilosophy>('all');
  const [activeLayer, setActiveLayer] = useState<UiLayerKey>('all');
  const [compareAId, setCompareAId] = useState<string>('one-ui-7');
  const [compareBId, setCompareBId] = useState<string>('nothing-os-3');

  const filteredProfiles = useMemo(() => {
    if (philosophyFilter === 'all') return OEM_UI_PROFILES;
    return OEM_UI_PROFILES.filter((p) => p.philosophy === philosophyFilter);
  }, [philosophyFilter]);

  const compareA = useMemo(
    () => OEM_UI_PROFILES.find((p) => p.id === compareAId) || OEM_UI_PROFILES[0],
    [compareAId]
  );
  const compareB = useMemo(
    () => OEM_UI_PROFILES.find((p) => p.id === compareBId) || OEM_UI_PROFILES[3],
    [compareBId]
  );

  const focusedLayerField = getLayerField(activeLayer);

  const adoptedCount = useMemo(
    () => OEM_UI_PROFILES.filter((p) => p.kayumarsStatus === 'adopted').length,
    []
  );
  const adaptedCount = useMemo(
    () => OEM_UI_PROFILES.filter((p) => p.kayumarsStatus === 'adapted').length,
    []
  );

  return (
    <div className="space-y-8 text-sm" dir="rtl">
      {/* Header & Grounded Summary */}
      <section className="bg-slate-900/90 border border-slate-800 rounded-2xl p-6 space-y-5">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div className="space-y-1.5">
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <span>پژوهش تطبیقی ۱۰ رابط کاربری موبایل</span>
              <span aria-hidden="true">·</span>
              <span>۶ لایه معماری</span>
              <span aria-hidden="true">·</span>
              <span>هم‌تراز با بودجه زیر ۴ مگابایت کیومرث</span>
            </div>
            <h2 className="text-xl font-bold text-slate-100">
              آزمایشگاه تحلیل رابط‌های کاربری گوشی‌ها و اعمال ایده‌های برتر در کیومرث
            </h2>
            <p className="text-xs text-slate-400 max-w-3xl leading-relaxed">
              بررسی ساختار واقعی شخصی‌سازی، موتور رنگ، پوشه‌های تعاملی، کپسول زنده و دسترسی یک‌دستی در ۱۰ سیستم مطرح بازار و انتقال مستقیم ایده‌های سبک‌وزن به لانچر کیومرث (شبیه‌ساز وب + کد اندروید).
            </p>
          </div>

          <div className="flex items-center gap-6 shrink-0 border-t lg:border-t-0 pt-3 lg:pt-0 border-slate-800 text-xs">
            <div>
              <span className="text-slate-400 block">برندهای بررسی‌شده</span>
              <span className="text-lg font-bold text-slate-100 font-mono tabular-nums">10 OEMs</span>
            </div>
            <div className="w-px h-8 bg-slate-800" />
            <div>
              <span className="text-slate-400 block">الگوی پیاده‌سازی‌شده</span>
              <span className="text-lg font-bold text-cyan-400 font-mono tabular-nums">
                {adoptedCount + adaptedCount}
              </span>
            </div>
          </div>
        </div>

        {/* Filter Row 1: Philosophy */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 pt-4 border-t border-slate-800/80">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs text-slate-400 flex items-center gap-1.5 ml-1">
              <Sliders className="w-3.5 h-3.5 text-cyan-400" />
              فلسفه طراحی:
            </span>
            {PHILOSOPHY_FILTERS.map((f) => (
              <button
                key={f.key}
                type="button"
                onClick={() => setPhilosophyFilter(f.key)}
                className={`px-3 py-1.5 rounded-lg text-xs transition-colors ${
                  philosophyFilter === f.key
                    ? 'bg-cyan-400 text-slate-950 font-semibold'
                    : 'bg-slate-950/70 text-slate-300 hover:text-slate-100 border border-slate-800'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {/* Filter Row 2: UI Layers */}
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-slate-400 flex items-center gap-1.5 ml-1">
            <Layers className="w-3.5 h-3.5 text-cyan-400" />
            تمرکز روی لایه:
          </span>
          {UI_LAYER_TABS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveLayer(tab.key)}
              className={`px-3 py-1.5 rounded-lg text-xs transition-colors ${
                activeLayer === tab.key
                  ? 'bg-slate-100 text-slate-950 font-semibold'
                  : 'bg-slate-950/60 text-slate-400 hover:text-slate-200 border border-slate-800/80'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </section>

      {/* Side-by-Side 2-Brand Diff Table */}
      <section className="bg-slate-900/90 border border-slate-800 rounded-2xl p-6 space-y-5">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1">
            <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
              <ArrowLeftRight className="w-4 h-4 text-cyan-400" />
              <span>مقایسه دوتایی ساختار لایه‌ها (Side-by-Side Diff)</span>
            </h3>
            <p className="text-xs text-slate-400">
              دو رابط کاربری را انتخاب کنید تا تفاوت معماری آن‌ها در تمام ۶ لایه دیده شود.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2 text-xs">
            <select
              aria-label="برند اول برای مقایسه"
              value={compareAId}
              onChange={(e) => setCompareAId(e.target.value)}
              className="bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:outline-none focus:border-cyan-400"
            >
              {OEM_UI_PROFILES.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.brand} — {p.uiName}
                </option>
              ))}
            </select>
            <span className="text-slate-500 font-mono">VS</span>
            <select
              aria-label="برند دوم برای مقایسه"
              value={compareBId}
              onChange={(e) => setCompareBId(e.target.value)}
              className="bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 focus:outline-none focus:border-cyan-400"
            >
              {OEM_UI_PROFILES.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.brand} — {p.uiName}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-xs border-collapse">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400">
                <th className="py-3 px-3 text-right font-semibold w-44">لایه رابط کاربری</th>
                <th className="py-3 px-3 text-right font-semibold text-cyan-300">
                  {compareA.brand} · {compareA.uiName}
                </th>
                <th className="py-3 px-3 text-right font-semibold text-slate-200">
                  {compareB.brand} · {compareB.uiName}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/80">
              {LAYER_ROWS.map((row) => (
                <tr key={row.key} className="align-top hover:bg-slate-950/40">
                  <td className="py-3 px-3 font-medium text-slate-300">{row.title}</td>
                  <td className="py-3 px-3 text-slate-300 leading-relaxed">{compareA.layers[row.key]}</td>
                  <td className="py-3 px-3 text-slate-300 leading-relaxed">{compareB.layers[row.key]}</td>
                </tr>
              ))}
              <tr className="align-top bg-slate-950/40">
                <td className="py-3 px-3 font-medium text-cyan-400">ایده منتقل‌شده به کیومرث</td>
                <td className="py-3 px-3 text-slate-200 leading-relaxed">{compareA.kayumarsTakeaway}</td>
                <td className="py-3 px-3 text-slate-200 leading-relaxed">{compareB.kayumarsTakeaway}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      {/* 10 OEM Single-Elevation Cards */}
      <section className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {filteredProfiles.map((profile) => {
          const isAdopted = profile.kayumarsStatus === 'adopted';
          return (
            <article
              key={profile.id}
              className="bg-slate-900/90 border border-slate-800 rounded-2xl p-5 flex flex-col justify-between gap-4"
            >
              <div className="space-y-3.5">
                {/* Metadata Line (Zero static pill badges — clean inline separators) */}
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="flex flex-wrap items-center gap-1.5 text-[11px] text-slate-400">
                      <span className="font-semibold text-slate-200">{profile.brand}</span>
                      <span aria-hidden="true">·</span>
                      <span>{profile.baseOs}</span>
                      <span aria-hidden="true">·</span>
                      <span>{profile.philosophyLabel}</span>
                    </div>
                    <h3 className="text-base font-bold text-slate-100 mt-0.5">{profile.uiName}</h3>
                  </div>

                  <span
                    className={`text-[11px] font-medium shrink-0 flex items-center gap-1 ${
                      isAdopted ? 'text-emerald-400' : 'text-cyan-400'
                    }`}
                  >
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>{profile.kayumarsStatusLabel}</span>
                  </span>
                </div>

                <p className="text-xs font-medium text-cyan-300/90 leading-relaxed">
                  {profile.tagline}
                </p>

                <p className="text-xs text-slate-300 leading-relaxed">{profile.summary}</p>

                {/* Signature Features Inline Text */}
                <div className="text-[11px] text-slate-400 font-mono flex flex-wrap gap-x-2 gap-y-1">
                  {profile.signatureFeatures.map((feat, idx) => (
                    <React.Fragment key={feat}>
                      {idx > 0 && <span aria-hidden="true">·</span>}
                      <span>{feat}</span>
                    </React.Fragment>
                  ))}
                </div>

                {/* Focused Layer OR Full 6-Layer Matrix */}
                <div className="pt-3 border-t border-slate-800/80 space-y-2.5 text-xs">
                  {focusedLayerField ? (
                    <div className="space-y-1">
                      <span className="text-[11px] font-semibold text-cyan-400 block">
                        {LAYER_ROWS.find((r) => r.key === focusedLayerField)?.title}
                      </span>
                      <p className="text-slate-200 leading-relaxed">
                        {profile.layers[focusedLayerField]}
                      </p>
                    </div>
                  ) : (
                    <div className="space-y-2">
                      {LAYER_ROWS.map((row) => (
                        <div key={row.key} className="grid grid-cols-[128px_1fr] gap-2 items-baseline">
                          <span className="text-[11px] text-slate-400 font-medium">{row.title}</span>
                          <span className="text-[11px] text-slate-200 leading-relaxed">
                            {profile.layers[row.key]}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Kayumars Engineering Takeaway */}
                <div className="pt-3 border-t border-slate-800/80 space-y-1 text-xs">
                  <div className="flex items-center gap-1.5 text-[11px] font-semibold text-emerald-400">
                    <Sparkles className="w-3.5 h-3.5" />
                    <span>سهم در معماری کیومرث</span>
                  </div>
                  <p className="text-xs text-slate-200 leading-relaxed">{profile.kayumarsTakeaway}</p>
                  {profile.omittedReason && (
                    <p className="text-[11px] text-slate-400 leading-relaxed pt-0.5">
                      چرا نسخه سنگین حذف شد: {profile.omittedReason}
                    </p>
                  )}
                </div>
              </div>

              {/* Action Button */}
              <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between gap-3">
                <span className="text-[11px] text-slate-500">
                  {profile.architecturalStrength.slice(0, 62)}…
                </span>
                <button
                  type="button"
                  onClick={() => onApplyPresetInSimulator(profile)}
                  className="px-3.5 py-2 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold text-xs flex items-center gap-1.5 transition-colors shrink-0"
                >
                  <Play className="w-3.5 h-3.5 fill-current" />
                  <span>{profile.simulatorPreset.label}</span>
                </button>
              </div>
            </article>
          );
        })}
      </section>
    </div>
  );
};
