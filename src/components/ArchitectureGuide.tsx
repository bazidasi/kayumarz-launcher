import React, { useState } from 'react';
import { Layers, Sliders, Calculator, Cpu, Sparkles, BookOpen } from 'lucide-react';

export const ArchitectureGuide: React.FC = () => {
  // Interactive KISS Calculator state
  const [fuzzyScore, setFuzzyScore] = useState<number>(500);
  const [appLaunches, setAppLaunches] = useState<number>(8);
  const [totalLaunches, setTotalLaunches] = useState<number>(45);
  const [totalInstalled, setTotalInstalled] = useState<number>(500);
  const [recentPos, setRecentPos] = useState<number>(1);

  // Exact math from rank.rs
  const smoothingApps = Math.max(1, totalInstalled);
  const freqNumerator = appLaunches + 1.0;
  const freqDenominator = totalLaunches + smoothingApps;
  const freqWeight = freqNumerator / freqDenominator;

  const recencyWeight = 1.0 / Math.max(1, recentPos);
  const multiplier = freqWeight * recencyWeight;
  const finalScore = fuzzyScore * (1.0 + multiplier * 100.0);

  return (
    <div className="space-y-10">
      {/* 90/10 Hybrid Architecture Diagram */}
      <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800">
        <div className="flex items-center gap-2 mb-2">
          <Layers className="w-5 h-5 text-cyan-400" />
          <h2 className="text-base font-bold text-slate-100">معماری هیبریدی ۹۰/۱۰: هسته راست (Rust) + پوسته کاتلین (Kotlin)</h2>
        </div>
        <p className="text-xs text-slate-400 mb-6 leading-relaxed">
          کیومرث با این شعار طراحی شده است: <strong className="text-slate-200">«سریع به دلیل کمتر انجام دادن، سریع با قدرت Rust»</strong>. کاتلین تنها رابط ورودی و خروجی فریم است و هیچ منطق جستجو، وزن‌دهی یا مرتب‌سازی در ماشین مجازی جاوا اجرا نمی‌شود.
        </p>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Kotlin Shell Box */}
          <div className="p-5 rounded-xl bg-slate-950/80 border border-slate-800 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-amber-400 font-mono">🤖 KOTLIN SHELL (~10% Code)</span>
              <span className="text-[10px] text-slate-400">فقط چسب لایه UI</span>
            </div>
            <ul className="text-xs text-slate-300 space-y-2 list-disc list-inside leading-relaxed">
              <li>
                <strong className="text-slate-100">کلاس خالی Application:</strong> کلاس <code className="text-cyan-400">LauncherApp</code> کاملاً خالی است تا Cold Start کمتر از ۲۰۰ میلی‌ثانیه محقق شود.
              </li>
              <li>
                <strong className="text-slate-100">عدم فراخوانی PackageManager روی ترد UI:</strong> اسکن اپلیکیشن‌ها منحصراً در <code className="text-cyan-400">AppIndexer</code> در پس‌زمینه انجام شده و آرایه مستقیماً به حافظه Rust پاس داده می‌شود.
              </li>
              <li>
                <strong className="text-slate-100">پل UniFFI سبک:</strong> متد <code className="text-cyan-400">Core.search()</code> تنها متدی است که اجازه اجرا روی ترد اصلی را دارد (اجرا زیر ۵ میلی‌ثانیه بدون هیچ تخصیص GC).
              </li>
              <li>
                <strong className="text-slate-100">رابط کاربر تک‌صفحه‌ای:</strong> فقط یک <code className="text-cyan-400">EditText</code> و یک <code className="text-cyan-400">RecyclerView</code> با اندازه ثابت (<code className="text-cyan-400">setHasFixedSize(true)</code>) برای حذف کامل لگ اسکرول.
              </li>
            </ul>
          </div>

          {/* Rust Core Box */}
          <div className="p-5 rounded-xl bg-slate-950/80 border border-slate-800 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-cyan-400 font-mono">🦀 RUST CORE (~90% Logic)</span>
              <span className="text-[10px] text-slate-400">کل منطق سیستمی</span>
            </div>
            <ul className="text-xs text-slate-300 space-y-2 list-disc list-inside leading-relaxed">
              <li>
                <strong className="text-slate-100">موتور فازی Nucleo (از ادیتور Helix):</strong> جستجوی فازی حروف با نرمال‌سازی UTF-8 NFC بدون تخصیص حافظه در مسیر بحرانی (Zero-Allocation Hot Path).
              </li>
              <li>
                <strong className="text-slate-100">تعویض اتمیک ایندکس (ArcSwap):</strong> جایگزینی ایندکس ۵۰۰ برنامه در زمان باورنکردنی کمتر از ۱ میلی‌ثانیه بدون هیچ Lock مزاحم.
              </li>
              <li>
                <strong className="text-slate-100">رتبه‌بندی KISS:</strong> فرمول <code className="text-cyan-400">fuzzy × frequency × recency</code> برای اولویت‌بخشی هوشمند به اپ‌های پراستفاده.
              </li>
              <li>
                <strong className="text-slate-100">ذخیره‌سازی سریع Bincode:</strong> ذخیره اتمیک وضعیت تاریخچه و اپ‌ها روی دیسک در کمتر از ۱۰ میلی‌ثانیه با دی‌بانس ۲ ثانیه‌ای.
              </li>
            </ul>
          </div>
        </div>
      </div>

      {/* Interactive KISS Math Playground */}
      <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-6">
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <div>
            <div className="flex items-center gap-2">
              <Calculator className="w-5 h-5 text-cyan-400" />
              <h3 className="text-base font-bold text-slate-100">محاسبه‌گر تعاملی فرمول رتبه‌بندی KISS</h3>
            </div>
            <p className="text-xs text-slate-400 mt-1">
              فرمول دقیق پیاده‌سازی شده در <code className="text-cyan-400">core/src/rank.rs</code>
            </p>
          </div>

          <div className="text-right">
            <span className="text-[11px] text-slate-400 block">امتیاز نهایی جستجو</span>
            <span className="text-2xl font-bold font-mono text-cyan-400 tabular-nums">
              {finalScore.toFixed(1)}
            </span>
          </div>
        </div>

        {/* Formula Display Box */}
        <div className="p-4 rounded-xl bg-slate-950/70 border border-slate-800/80 font-mono text-xs text-slate-300 overflow-x-auto leading-relaxed">
          <div className="text-cyan-400 mb-1 font-semibold"># فرمول محاسبه:</div>
          <div>امتیاز_نهایی = امتیاز_فازی × [ ۱ + (ضریب_فراوانی × ضریب_تازگی) × ۱۰۰ ]</div>
          <div className="text-slate-500 mt-1 text-[11px]">
            ضریب_فراوانی = (تعداد_اجرای_اپ + ۱) / (کل_اجراها + تعداد_کل_اپ‌ها)
            <br />
            ضریب_تازگی = ۱.۰ / رتبه_آخرین_اجرا (۱ = آخرین اپلیکیشن باز شده)
          </div>
        </div>

        {/* Sliders Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-2">
          {/* Slider 1: Fuzzy Match Score */}
          <div className="space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-slate-300">امتیاز تطابق فازی Nucleo (Fuzzy Score)</span>
              <span className="font-mono text-cyan-400 font-semibold tabular-nums">{fuzzyScore}</span>
            </div>
            <input
              type="range"
              min="50"
              max="1000"
              step="25"
              value={fuzzyScore}
              onChange={(e) => setFuzzyScore(Number(e.target.value))}
              className="w-full accent-cyan-400"
            />
            <span className="text-[10px] text-slate-500">تطابق دقیق پیشوند = ۱۰۰۰، زیررشته = ۵۰۰، فازی = ۳۰۰</span>
          </div>

          {/* Slider 2: App Launches */}
          <div className="space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-slate-300">تعداد دفعات اجرای این برنامه (App Launches)</span>
              <span className="font-mono text-cyan-400 font-semibold tabular-nums">{appLaunches} بار</span>
            </div>
            <input
              type="range"
              min="0"
              max="50"
              step="1"
              value={appLaunches}
              onChange={(e) => setAppLaunches(Number(e.target.value))}
              className="w-full accent-cyan-400"
            />
            <span className="text-[10px] text-slate-500">تعداد دفعاتی که کاربر این برنامه مشخص را از لانچر اجرا کرده است</span>
          </div>

          {/* Slider 3: Total Launches */}
          <div className="space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-slate-300">مجموع کل اجراهای تمام برنامه‌ها (Total Lifetime)</span>
              <span className="font-mono text-slate-300 font-semibold tabular-nums">{totalLaunches}</span>
            </div>
            <input
              type="range"
              min="1"
              max="200"
              step="1"
              value={totalLaunches}
              onChange={(e) => setTotalLaunches(Number(e.target.value))}
              className="w-full accent-cyan-400"
            />
            <span className="text-[10px] text-slate-500">مجموع اجراهای ثبت‌شده در بافر حلقوی تاریخچه</span>
          </div>

          {/* Slider 4: Recent Position */}
          <div className="space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-slate-300">موقعیت تازگی آخرین اجرا (Recent Rank)</span>
              <span className="font-mono text-emerald-400 font-semibold tabular-nums">
                {recentPos === 1 ? 'رتبه ۱ (همین الان)' : `رتبه ${recentPos}`}
              </span>
            </div>
            <input
              type="range"
              min="1"
              max="20"
              step="1"
              value={recentPos}
              onChange={(e) => setRecentPos(Number(e.target.value))}
              className="w-full accent-cyan-400"
            />
            <span className="text-[10px] text-slate-500">۱ = اخیراً‌ترین برنامه، مقادیر بالاتر = برنامه‌هایی که در گذشته اجرا شده‌اند</span>
          </div>
        </div>

        {/* Live Multiplier Result Summary */}
        <div className="p-4 rounded-xl bg-slate-950/40 border border-slate-800 flex flex-wrap items-center justify-between gap-4 text-xs">
          <div>
            <span className="text-slate-400 block text-[11px]">ضریب فراوانی (Frequency)</span>
            <span className="font-mono text-slate-200 font-semibold tabular-nums">
              {(freqWeight * 100).toFixed(3)}%
            </span>
          </div>
          <div>
            <span className="text-slate-400 block text-[11px]">ضریب تازگی (Recency)</span>
            <span className="font-mono text-slate-200 font-semibold tabular-nums">
              {recencyWeight.toFixed(3)}
            </span>
          </div>
          <div>
            <span className="text-slate-400 block text-[11px]">ضریب ترکیبی تقویت (Boost Multiplier)</span>
            <span className="font-mono text-cyan-400 font-bold tabular-nums">
              +{(multiplier * 100).toFixed(2)}%
            </span>
          </div>
          <div>
            <span className="text-slate-400 block text-[11px]">نتیجه رتبه‌بندی</span>
            <span className="text-emerald-400 font-medium">
              {finalScore > fuzzyScore * 1.5 ? '🔥 اولویت بسیار بالا' : 'متعادل'}
            </span>
          </div>
        </div>
      </div>

      {/* Quiet Precision M3 Design System Tokens */}
      <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-4">
        <div className="flex items-center gap-2 mb-2">
          <Sparkles className="w-5 h-5 text-cyan-400" />
          <h2 className="text-base font-bold text-slate-100">زبان طراحی «دقت آرام» (Quiet Precision M3)</h2>
        </div>
        <p className="text-xs text-slate-400 leading-relaxed">
          حفظ بودجه‌های عملکرد در سطح UI با همان سخت‌گیری هسته Rust اعمال می‌شود: حذف سایه‌های پرهزینه رندرینگ، تکیه بر خط محیطی ۱dp، سطوح نیمه‌شفاف برای تنفس والپیپر دستگاه، و انیمیشن‌های مینیمال ۱۲۰ تا ۱۵۰ میلی‌ثانیه بدون هیچ فریم لگی.
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800">
            <span className="text-cyan-400 font-mono font-bold block mb-1">Search Pill</span>
            <ul className="text-[11px] text-slate-400 space-y-1">
              <li>ارتفاع: ۵۲ dp</li>
              <li>انحنای گوشه: ۲۸ dp</li>
              <li>حاشیه افقی: ۱۶ dp</li>
              <li>خط بیرونی: ۱ dp بدون سایه</li>
            </ul>
          </div>

          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800">
            <span className="text-cyan-400 font-mono font-bold block mb-1">Results Row</span>
            <ul className="text-[11px] text-slate-400 space-y-1">
              <li>ارتفاع سطر: ۶۴ dp</li>
              <li>آیکون: ۴۰ dp</li>
              <li>عنوان: ۱۶ sp (Medium)</li>
              <li>توضیح: ۱۲ sp (۶۰٪ Alpha)</li>
            </ul>
          </div>

          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800">
            <span className="text-cyan-400 font-mono font-bold block mb-1">Top-Apps Grid</span>
            <ul className="text-[11px] text-slate-400 space-y-1">
              <li>چیدمان: ۴ ستون × ۲ سطر</li>
              <li>ارتفاع سلول: ۸۸ dp</li>
              <li>آیکون: ۴۸ dp</li>
              <li>سوئیچ اتمیک هنگام تایپ</li>
            </ul>
          </div>

          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800">
            <span className="text-cyan-400 font-mono font-bold block mb-1">M3 Motion</span>
            <ul className="text-[11px] text-slate-400 space-y-1">
              <li>ظهور نتایج: ۱۲۰ ms</li>
              <li>تغییر مقیاس لمس: ۰.۹۶ → ۱.۰ (۱۵۰ ms)</li>
              <li>جابجایی کیبورد: ۲۰۰ ms</li>
              <li>شیب حرکت: PathInterpolator M3</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
};
