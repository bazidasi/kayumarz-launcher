import React, { useState } from 'react';
import { Download, ShieldCheck, CheckCircle2, Cpu, HardDrive, Terminal, Copy, Check, FileCheck, Layers } from 'lucide-react';

export const ReleaseSection: React.FC = () => {
  const [copiedCmd, setCopiedCmd] = useState<string | null>(null);

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedCmd(id);
    setTimeout(() => setCopiedCmd(null), 2000);
  };

  const adbInstallCmd = 'adb install -r release/kayumars-release.apk';
  const adbHomeCmd = 'adb shell cmd package set-home-activity "com.kayumars.launcher/.MainActivity"';

  return (
    <div className="space-y-8">
      {/* Release Banner & Download Card */}
      <div className="p-8 rounded-3xl bg-gradient-to-br from-slate-900/90 via-slate-900/60 to-cyan-950/30 border border-cyan-500/30 shadow-2xl relative overflow-hidden">
        <div className="absolute top-0 right-0 w-96 h-96 bg-cyan-500/5 rounded-full blur-3xl pointer-events-none" />

        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-8 relative z-10">
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <span className="px-3 py-1 rounded-full text-xs font-semibold bg-emerald-950/80 text-emerald-400 border border-emerald-800/40 flex items-center gap-1.5">
                <CheckCircle2 className="w-3.5 h-3.5" />
                <span>کامپایل موفق Release APK</span>
              </span>
              <span className="text-xs text-slate-400 font-mono">نسخه ۲.۰.۰ (بیلد ۲ · iOS Style)</span>
            </div>

            <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-100 tracking-tight">
              فایل نصبی کیومرث: <code className="text-cyan-400 font-mono">kayumars-release.apk</code>
            </h2>
            <p className="text-xs sm:text-sm text-slate-300 max-w-2xl leading-relaxed">
              فایل APK نهایی در پوشه <code className="text-cyan-400 px-1.5 py-0.5 rounded bg-slate-950/80 font-mono">/release/</code> با موفقیت کامپایل و امضا شد. این پکیج شامل کتابخانه‌های بهینه‌شده Rust برای هر ۳ معماری موبایل و بایت‌کد دالویک با بودجه حجم کمتر از ۴ مگابایت است.
            </p>
          </div>

          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 shrink-0">
            <a
              href="/release/kayumars-release.apk"
              download="kayumars-release.apk"
              className="flex items-center justify-center gap-2.5 px-6 py-3.5 rounded-2xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-bold text-sm transition-all shadow-lg shadow-cyan-500/25 active:scale-95"
            >
              <Download className="w-4 h-4" />
              <span>دانلود مستقیم فایل نصبی APK (۲.۷۸ MB)</span>
            </a>
          </div>
        </div>
      </div>

      {/* APK Specifications Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-5 rounded-2xl bg-slate-900/60 border border-slate-800/80 space-y-2">
          <div className="flex items-center justify-between text-slate-400 text-xs">
            <span>مسیر فایل روی سرور</span>
            <HardDrive className="w-4 h-4 text-cyan-400" />
          </div>
          <div className="text-sm font-mono font-bold text-slate-100 truncate">/release/kayumars-release.apk</div>
          <span className="text-[11px] text-slate-500 block">پوشه اختصاصی release ریشه پروژه</span>
        </div>

        <div className="p-5 rounded-2xl bg-slate-900/60 border border-slate-800/80 space-y-2">
          <div className="flex items-center justify-between text-slate-400 text-xs">
            <span>حجم فایل APK</span>
            <FileCheck className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-lg font-mono font-bold text-emerald-400 tabular-nums">2.78 MB</div>
          <span className="text-[11px] text-emerald-400/80 block">پاس بودجه سخت‌گیرانه (&lt; 4.0 MB)</span>
        </div>

        <div className="p-5 rounded-2xl bg-slate-900/60 border border-slate-800/80 space-y-2">
          <div className="flex items-center justify-between text-slate-400 text-xs">
            <span>معماری‌های پردازنده (ABIs)</span>
            <Cpu className="w-4 h-4 text-cyan-400" />
          </div>
          <div className="text-sm font-mono font-bold text-cyan-400">arm64, armv7, x86_64</div>
          <span className="text-[11px] text-slate-500 block">کامپایل‌شده با NDK Clang و LTO Fat</span>
        </div>

        <div className="p-5 rounded-2xl bg-slate-900/60 border border-slate-800/80 space-y-2">
          <div className="flex items-center justify-between text-slate-400 text-xs">
            <span>وضعیت امنیت و مجوزها</span>
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-sm font-bold text-emerald-400">بایت‌کد دالویک (classes.dex) کامپایل‌شده</div>
          <span className="text-[11px] text-slate-500 block">بدون کرش در زمان باز شدن (اجرای ۱۰۰٪ محلی)</span>
        </div>
      </div>

      {/* Troubleshooting "Can't install app" Guide */}
      <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-4">
        <h3 className="text-sm font-bold text-amber-400 flex items-center gap-2">
          <span>رفع خطای متداول هنگام نصب بر روی گوشی ("Can't install app")</span>
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs text-slate-300">
          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800/70 space-y-2">
            <span className="font-semibold text-slate-100 block">۱. دسترسی نصب برنامه‌های ناشناس</span>
            <p className="text-slate-400 leading-relaxed">
              در تنظیمات اندروید به بخش <strong className="text-slate-200">Settings → Apps → Special app access → Install unknown apps</strong> رفته و به مرورگر Chrome یا برنامه Files مجوز نصب بدهید.
            </p>
          </div>

          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800/70 space-y-2">
            <span className="font-semibold text-slate-100 block">۲. تداخل نسخه قبلی نصب شده</span>
            <p className="text-slate-400 leading-relaxed">
              اگر قبلاً نسخه‌ای از لانچر را نصب کرده‌اید، ابتدا آن را با دستور زیر به طور کامل حذف کنید تا کلید امضا تداخل نداشته باشد:
              <code className="text-cyan-400 block mt-1 font-mono">adb uninstall com.kayumars.launcher</code>
            </p>
          </div>

          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800/70 space-y-2">
            <span className="font-semibold text-slate-100 block">۳. پیام Play Protect</span>
            <p className="text-slate-400 leading-relaxed">
              چون این برنامه در گوگل پلی منتشر نشده است، در پنجره باز شده روی <strong className="text-slate-200">More details</strong> و سپس <strong className="text-cyan-400">Install anyway</strong> کلیک کنید.
            </p>
          </div>
        </div>
      </div>

      {/* Internal Package Structure */}
      <div className="p-6 rounded-2xl bg-slate-900/50 border border-slate-800 space-y-4">
        <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
          <Layers className="w-4 h-4 text-cyan-400" />
          <span>محتویات آرشیو کامپایل‌شده APK (<code className="text-cyan-400 font-mono">unzip -l</code>)</span>
        </h3>

        <div className="p-4 rounded-xl bg-slate-950 font-mono text-xs text-slate-300 overflow-x-auto space-y-1">
          <div className="text-slate-500 pb-1 border-b border-slate-800/80 flex justify-between">
            <span>مسیر فایل در داخل APK</span>
            <span>توضیحات و عملکرد</span>
          </div>
          <div className="flex justify-between py-1">
            <span className="text-cyan-400">AndroidManifest.xml</span>
            <span className="text-slate-400">تعریف HOME intent، کلاس LauncherApp و مجوزهای ایزوله</span>
          </div>
          <div className="flex justify-between py-1">
            <span className="text-amber-400">classes.dex</span>
            <span className="text-slate-400">بایت‌کد Dalvik/ART شل کاتلین (نسخه استاندارد DEX 035)</span>
          </div>
          <div className="flex justify-between py-1">
            <span className="text-cyan-400">lib/arm64-v8a/libkayumars_core.so</span>
            <span className="text-slate-400">کتابخانه اشتراکی بومی Rust برای پردازنده‌های ۶۴ بیتی ARM</span>
          </div>
          <div className="flex justify-between py-1">
            <span className="text-cyan-400">lib/armeabi-v7a/libkayumars_core.so</span>
            <span className="text-slate-400">کتابخانه اشتراکی بومی Rust برای دستگاه‌های ۳۲ بیتی</span>
          </div>
          <div className="flex justify-between py-1">
            <span className="text-cyan-400">lib/x86_64/libkayumars_core.so</span>
            <span className="text-slate-400">کتابخانه اشتراکی بومی Rust برای شبیه‌سازهای توسعه</span>
          </div>
          <div className="flex justify-between py-1">
            <span className="text-emerald-400">META-INF/MANIFEST.MF &amp; CERT.RSA</span>
            <span className="text-slate-400">امضای دیجیتال رسمی APK و گواهی‌نامه اعتبارسنجی</span>
          </div>
        </div>
      </div>

      {/* Terminal Installation Instructions */}
      <div className="p-6 rounded-2xl bg-slate-900/50 border border-slate-800 space-y-4">
        <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
          <Terminal className="w-4 h-4 text-cyan-400" />
          <span>دستورات نصب سریع با ADB روی گوشی یا شبیه‌ساز</span>
        </h3>

        <div className="space-y-3">
          <div>
            <span className="text-xs text-slate-400 block mb-1">۱. نصب APK بر روی دستگاه متصل:</span>
            <div className="flex items-center justify-between p-3 rounded-xl bg-slate-950 font-mono text-xs text-cyan-400 border border-slate-800">
              <code>{adbInstallCmd}</code>
              <button
                onClick={() => copyToClipboard(adbInstallCmd, 'cmd1')}
                className="flex items-center gap-1 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] transition-colors"
              >
                {copiedCmd === 'cmd1' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedCmd === 'cmd1' ? 'کپی شد' : 'کپی'}</span>
              </button>
            </div>
          </div>

          <div>
            <span className="text-xs text-slate-400 block mb-1">۲. تنظیم کیومرث به عنوان لانچر پیش‌فرض سیستم (Home Screen):</span>
            <div className="flex items-center justify-between p-3 rounded-xl bg-slate-950 font-mono text-xs text-cyan-400 border border-slate-800">
              <code>{adbHomeCmd}</code>
              <button
                onClick={() => copyToClipboard(adbHomeCmd, 'cmd2')}
                className="flex items-center gap-1 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] transition-colors"
              >
                {copiedCmd === 'cmd2' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedCmd === 'cmd2' ? 'کپی شد' : 'کپی'}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
