import React, { useState, useMemo, Component, ErrorInfo, ReactNode } from 'react';
import { generate500Apps } from './data/appsFixture';
import { KayumarsSimulationEngine } from './engine/kayumarsCore';
import { LauncherSimulator } from './components/LauncherSimulator';
import { BenchmarkRunner } from './components/BenchmarkRunner';
import { ArchitectureGuide } from './components/ArchitectureGuide';
import { CodeInspector } from './components/CodeInspector';
import { ReleaseSection } from './components/ReleaseSection';
import { OemUiResearchLab } from './components/OemUiResearchLab';
import { OemUiProfile } from './data/oemUiResearch';
import { AppEntry } from './types/launcher';
import { Zap, Download, ShieldCheck, AlertTriangle, RotateCcw } from 'lucide-react';
import JSZip from 'jszip';
import { KAYUMARS_CODEBASE } from './data/codebase';

interface ErrorBoundaryState {
  hasError: boolean;
  message: string;
}

class SimulatorErrorBoundary extends Component<{ children: ReactNode }, ErrorBoundaryState> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { hasError: false, message: '' };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, message: error?.message || 'Unexpected runtime error' };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Kayumars UI ErrorBoundary caught error:', error, info);
  }

  private handleResetAndReload = () => {
    try {
      localStorage.removeItem('kayumars_ios_sim_layout_v2');
      localStorage.removeItem('kayumars_history_v2');
      localStorage.removeItem('kayumars_history');
    } catch {
      // ignore storage access issues
    }
    if (typeof window !== 'undefined') {
      window.location.reload();
    }
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-6">
          <div className="max-w-md w-full rounded-2xl bg-slate-900 border border-slate-800 p-6 space-y-4 text-center">
            <div className="w-12 h-12 rounded-2xl bg-rose-500/15 text-rose-400 flex items-center justify-center mx-auto">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <div className="space-y-1.5">
              <h2 className="text-base font-bold">بروز خطای غیرمنتظره در رابط شبیه‌ساز</h2>
              <p className="text-xs text-slate-400 font-mono break-words">{this.state.message}</p>
            </div>
            <button
              type="button"
              onClick={this.handleResetAndReload}
              className="w-full py-2.5 px-4 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 text-xs font-bold flex items-center justify-center gap-2 transition-colors"
            >
              <RotateCcw className="w-4 h-4" />
              <span>بازنشانی داده‌های ذخیره‌شده و بارگذاری مجدد</span>
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

export default function App() {
  const [activeTab, setActiveTab] = useState<'launcher' | 'research' | 'release' | 'benchmarks' | 'architecture' | 'code'>('launcher');
  const [apps, setApps] = useState<AppEntry[]>(() => generate500Apps());
  const [isExporting, setIsExporting] = useState(false);
  const [presetTrigger, setPresetTrigger] = useState<{ nonce: number; profile: OemUiProfile } | null>(null);

  // Initialize engine singleton with 500 apps fixture
  const engine = useMemo(() => new KayumarsSimulationEngine(apps), []);

  const handleApplyOemPreset = (profile: OemUiProfile) => {
    engine.updatePreferences(profile.simulatorPreset.prefs);
    setPresetTrigger({ nonce: Date.now(), profile });
    setActiveTab('launcher');
  };

  const handleAppLaunched = (pkg: string) => {
    // Engine automatically logs and updates local storage
  };

  const handleAddApp = (newApp: AppEntry) => {
    setApps((prev) => [newApp, ...prev]);
    engine.replaceIndex([newApp, ...apps]);
  };

  const handleRemoveApp = (pkg: string) => {
    setApps((prev) => prev.filter((a) => a.package !== pkg));
    engine.replaceIndex(apps.filter((a) => a.package !== pkg));
  };

  const handleQuickDownload = async () => {
    setIsExporting(true);
    try {
      const zip = new JSZip();
      const rootFolder = zip.folder('kayumars');
      for (const file of KAYUMARS_CODEBASE) {
        rootFolder?.file(file.path, file.content);
      }
      const content = await zip.generateAsync({ type: 'blob' });
      const url = URL.createObjectURL(content);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'kayumars-android-rust-launcher.zip';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (e) {
      console.error(e);
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <SimulatorErrorBoundary>
      <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-cyan-500/30 selection:text-cyan-200">
      {/* Universal Top Bar Contract: Zone 1 (Brand) - Zone 2 (Nav Links) - Zone 3 (1 Primary Action) */}
      <header className="sticky top-0 z-50 bg-slate-950/90 backdrop-blur-md border-b border-slate-800/80 px-6 py-3.5">
        <div className="max-w-7xl mx-auto flex items-center justify-between gap-8">
          {/* Zone 1: Single text wordmark */}
          <div className="flex items-center gap-3 whitespace-nowrap shrink-0">
            <span className="w-8 h-8 rounded-xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 font-bold text-sm">
              ک
            </span>
            <div className="flex items-baseline gap-2">
              <span className="text-lg font-bold tracking-tight text-slate-100 font-serif">
                کیومرث
              </span>
              <span className="text-xs text-slate-400 font-mono">Kayumars</span>
            </div>
          </div>

          {/* Zone 2: Clean single-line navigation links */}
          <nav className="hidden md:flex items-center gap-5 text-xs font-medium text-slate-400">
            <button
              onClick={() => setActiveTab('launcher')}
              className={`whitespace-nowrap transition-colors hover:text-slate-100 ${
                activeTab === 'launcher' ? 'text-cyan-400 font-semibold' : ''
              }`}
            >
              شبیه‌ساز لانچر
            </button>
            <button
              onClick={() => setActiveTab('research')}
              className={`whitespace-nowrap transition-colors hover:text-slate-100 ${
                activeTab === 'research' ? 'text-cyan-400 font-semibold' : ''
              }`}
            >
              تحلیل رابط‌های گوشی‌ها
            </button>
            <button
              onClick={() => setActiveTab('release')}
              className={`whitespace-nowrap transition-colors hover:text-slate-100 flex items-center gap-1.5 ${
                activeTab === 'release' ? 'text-cyan-400 font-semibold' : ''
              }`}
            >
              <span>فایل نصبی (Release APK)</span>
              <span className="text-[10px] px-1.5 py-0.2 rounded bg-emerald-950 text-emerald-400 font-mono">2.78M</span>
            </button>
            <button
              onClick={() => setActiveTab('benchmarks')}
              className={`whitespace-nowrap transition-colors hover:text-slate-100 ${
                activeTab === 'benchmarks' ? 'text-cyan-400 font-semibold' : ''
              }`}
            >
              بودجه‌ها و تست بار
            </button>
            <button
              onClick={() => setActiveTab('architecture')}
              className={`whitespace-nowrap transition-colors hover:text-slate-100 ${
                activeTab === 'architecture' ? 'text-cyan-400 font-semibold' : ''
              }`}
            >
              معماری ۹۰/۱۰
            </button>
            <button
              onClick={() => setActiveTab('code')}
              className={`whitespace-nowrap transition-colors hover:text-slate-100 ${
                activeTab === 'code' ? 'text-cyan-400 font-semibold' : ''
              }`}
            >
              کدهای منبع
            </button>
          </nav>

          {/* Zone 3: Direct Download Action */}
          <div className="flex items-center gap-2.5 shrink-0">
            <a
              href="/release/kayumars-release.apk"
              download="kayumars-release.apk"
              className="flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold text-slate-950 bg-cyan-400 hover:bg-cyan-300 rounded-xl transition-all shadow-md shadow-cyan-500/20 whitespace-nowrap shrink-0"
            >
              <Download className="w-3.5 h-3.5" />
              <span>دانلود APK</span>
            </a>
            <button
              onClick={handleQuickDownload}
              disabled={isExporting}
              className="hidden sm:flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-slate-300 hover:text-slate-100 bg-slate-900 hover:bg-slate-800 border border-slate-800 rounded-xl transition-all whitespace-nowrap shrink-0 disabled:opacity-50"
            >
              <Download className="w-3.5 h-3.5" />
              <span>سورس (.ZIP)</span>
            </button>
          </div>
        </div>
      </header>

      {/* Hero Headline & Baseline Strip */}
      <section className="border-b border-slate-900 bg-gradient-to-b from-slate-900/40 to-transparent py-8 px-6">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div>
            <div className="flex items-center gap-2 text-xs text-slate-400 mb-2">
              <span>هسته Rust با ماتچر Nucleo</span>
              <span aria-hidden="true">·</span>
              <span>پوسته باریک کاتلین</span>
              <span aria-hidden="true">·</span>
              <span>بدون مجوز اینترنت (Offline)</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-100 tracking-tight text-balance">
              سریع‌ترین لانچر اندروید: معماری هیبریدی کیومرث (کیومرث)
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 mt-1 max-w-2xl leading-relaxed">
              صفر توقف زباله‌روب (Zero-GC)، جستجوی فازی زیر ۵ میلی‌ثانیه برای ۵۰۰ برنامه و الگوریتم رتبه‌بندی هوشمند فراوانی × تازگی.
            </p>
          </div>

          {/* Live System Badges */}
          <div className="flex items-center gap-3 text-xs shrink-0">
            <div className="px-3 py-2 rounded-xl bg-slate-900/80 border border-slate-800 flex items-center gap-2">
              <Zap className="w-4 h-4 text-cyan-400" />
              <div>
                <span className="text-[10px] text-slate-400 block">تاخیر جستجو</span>
                <span className="font-mono text-cyan-400 font-bold tabular-nums">&lt; 1 ms</span>
              </div>
            </div>
            <div className="px-3 py-2 rounded-xl bg-slate-900/80 border border-slate-800 flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-emerald-400" />
              <div>
                <span className="text-[10px] text-slate-400 block">مجوز شبکه</span>
                <span className="font-mono text-emerald-400 font-bold">صفر مطلق</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Mobile Tab Switcher */}
      <div className="md:hidden flex border-b border-slate-800 bg-slate-900/50 p-2 overflow-x-auto text-xs gap-1">
        <button
          onClick={() => setActiveTab('launcher')}
          className={`px-3 py-1.5 rounded-lg whitespace-nowrap ${
            activeTab === 'launcher' ? 'bg-cyan-950 text-cyan-300 font-medium' : 'text-slate-400'
          }`}
        >
          شبیه‌ساز
        </button>
        <button
          onClick={() => setActiveTab('research')}
          className={`px-3 py-1.5 rounded-lg whitespace-nowrap ${
            activeTab === 'research' ? 'bg-cyan-950 text-cyan-300 font-medium' : 'text-slate-400'
          }`}
        >
          تحلیل رابط‌ها
        </button>
        <button
          onClick={() => setActiveTab('release')}
          className={`px-3 py-1.5 rounded-lg whitespace-nowrap ${
            activeTab === 'release' ? 'bg-cyan-950 text-cyan-300 font-medium' : 'text-slate-400'
          }`}
        >
          فایل نصبی APK
        </button>
        <button
          onClick={() => setActiveTab('benchmarks')}
          className={`px-3 py-1.5 rounded-lg whitespace-nowrap ${
            activeTab === 'benchmarks' ? 'bg-cyan-950 text-cyan-300 font-medium' : 'text-slate-400'
          }`}
        >
          بودجه‌ها
        </button>
        <button
          onClick={() => setActiveTab('architecture')}
          className={`px-3 py-1.5 rounded-lg whitespace-nowrap ${
            activeTab === 'architecture' ? 'bg-cyan-950 text-cyan-300 font-medium' : 'text-slate-400'
          }`}
        >
          فرمول KISS
        </button>
        <button
          onClick={() => setActiveTab('code')}
          className={`px-3 py-1.5 rounded-lg whitespace-nowrap ${
            activeTab === 'code' ? 'bg-cyan-950 text-cyan-300 font-medium' : 'text-slate-400'
          }`}
        >
          کدها
        </button>
      </div>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl mx-auto w-full p-6 lg:p-8">
        {activeTab === 'launcher' && (
          <LauncherSimulator
            engine={engine}
            installedApps={apps}
            onAppLaunched={handleAppLaunched}
            onAddApp={handleAddApp}
            onRemoveApp={handleRemoveApp}
            presetTrigger={presetTrigger}
          />
        )}

        {activeTab === 'research' && (
          <OemUiResearchLab onApplyPresetInSimulator={handleApplyOemPreset} />
        )}

        {activeTab === 'release' && <ReleaseSection />}

        {activeTab === 'benchmarks' && <BenchmarkRunner engine={engine} />}

        {activeTab === 'architecture' && <ArchitectureGuide />}

        {activeTab === 'code' && <CodeInspector />}
      </main>

      {/* Clean Footer */}
      <footer className="border-t border-slate-900 bg-slate-950/80 px-6 py-6 text-xs text-slate-500 mt-auto">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-slate-400">کیومرث (Kayumars)</span>
            <span>·</span>
            <span>پروژه سیستم‌های ترکیبی کاتلین و راست</span>
            <span>·</span>
            <span>مجوز آپاچی ۲.۰</span>
          </div>

          <div className="flex items-center gap-4 text-[11px] font-mono text-slate-400">
            <span>NDK r27c</span>
            <span>UniFFI 0.28</span>
            <span>Nucleo 0.2</span>
            <span>Macrobenchmark 1.3</span>
          </div>
        </div>
      </footer>
      </div>
    </SimulatorErrorBoundary>
  );
}
