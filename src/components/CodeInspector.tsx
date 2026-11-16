import React, { useState } from 'react';
import { KAYUMARS_CODEBASE, CodeFile } from '../data/codebase';
import { FileCode, Download, Copy, Check, Folder, ChevronRight, Terminal, Shield, Zap } from 'lucide-react';
import JSZip from 'jszip';

export const CodeInspector: React.FC = () => {
  const [selectedFile, setSelectedFile] = useState<CodeFile>(KAYUMARS_CODEBASE[2]); // Default to core/src/lib.rs
  const [activeCategory, setActiveCategory] = useState<string>('all');
  const [copied, setCopied] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [searchFilter, setSearchFilter] = useState('');

  const categories = [
    { id: 'all', label: 'همه فایل‌ها' },
    { id: 'core', label: '🦀 هسته Rust' },
    { id: 'app', label: '🤖 پوسته Kotlin' },
    { id: 'scripts', label: '⚡ اسکریپت‌ها و Perf Gate' },
    { id: 'root', label: '⚙️ پیکربندی ریشه' },
  ];

  const filteredFiles = KAYUMARS_CODEBASE.filter((f) => {
    const matchesCat = activeCategory === 'all' || f.category === activeCategory;
    const matchesSearch =
      !searchFilter ||
      f.path.toLowerCase().includes(searchFilter.toLowerCase()) ||
      f.description.toLowerCase().includes(searchFilter.toLowerCase());
    return matchesCat && matchesSearch;
  });

  const handleCopy = () => {
    navigator.clipboard.writeText(selectedFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadZip = async () => {
    setIsExporting(true);
    try {
      const zip = new JSZip();
      const rootFolder = zip.folder('kayumars');

      for (const file of KAYUMARS_CODEBASE) {
        rootFolder?.file(file.path, file.content);
      }

      // Generate zip blob
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
      console.error('Failed to export zip', e);
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Bar for Code Inspector */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-5 rounded-2xl bg-slate-900/60 border border-slate-800">
        <div>
          <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
            <FileCode className="w-5 h-5 text-cyan-400" />
            <span>مرورگر کدهای منبع Kayumars</span>
            <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300 font-mono">
              {KAYUMARS_CODEBASE.length} فایل ثبت‌شده
            </span>
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            کدهای تولید شده شامل هسته کامل Rust، ماژول کاتلین، تست‌های ماکروبنچمارک و خط لوله CI
          </p>
        </div>

        <button
          onClick={handleDownloadZip}
          disabled={isExporting}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-semibold text-xs transition-all shadow-md shadow-cyan-500/20 disabled:opacity-50 shrink-0"
        >
          <Download className="w-4 h-4" />
          <span>{isExporting ? 'در حال فشرده‌سازی…' : 'دانلود کل سورس کد پروژه (.ZIP)'}</span>
        </button>
      </div>

      {/* Main Split: File Tree + Code Editor Viewer */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left: File Tree and Filters */}
        <div className="lg:col-span-4 space-y-4">
          {/* Category Tabs */}
          <div className="flex flex-wrap gap-1.5 p-1 bg-slate-900/80 rounded-xl border border-slate-800">
            {categories.map((c) => (
              <button
                key={c.id}
                onClick={() => setActiveCategory(c.id)}
                className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-colors ${
                  activeCategory === c.id
                    ? 'bg-slate-800 text-cyan-400 shadow-sm'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {c.label}
              </button>
            ))}
          </div>

          {/* Quick Search */}
          <input
            type="text"
            value={searchFilter}
            onChange={(e) => setSearchFilter(e.target.value)}
            placeholder="فیلتر نام فایل یا توضیح…"
            className="w-full px-3.5 py-2 text-xs rounded-xl bg-slate-900/60 border border-slate-800 text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-cyan-500"
          />

          {/* File List */}
          <div className="rounded-2xl bg-slate-900/50 border border-slate-800 divide-y divide-slate-800/60 max-h-[560px] overflow-y-auto">
            {filteredFiles.map((f) => {
              const isSelected = selectedFile.path === f.path;
              return (
                <div
                  key={f.path}
                  onClick={() => setSelectedFile(f)}
                  className={`p-3 cursor-pointer transition-colors flex items-start gap-3 text-left ${
                    isSelected ? 'bg-cyan-950/40 text-cyan-300' : 'hover:bg-slate-800/40 text-slate-300'
                  }`}
                >
                  <div className="mt-0.5 text-slate-400">
                    <ChevronRight className={`w-3.5 h-3.5 transition-transform ${isSelected ? 'rotate-90 text-cyan-400' : ''}`} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-mono font-medium truncate">{f.name}</span>
                      <span className="text-[10px] font-mono text-slate-500 uppercase shrink-0">
                        {f.language}
                      </span>
                    </div>
                    <div className="text-[11px] text-slate-500 truncate mt-0.5">{f.path}</div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Right: Code Viewer */}
        <div className="lg:col-span-8 rounded-2xl bg-slate-900/70 border border-slate-800 overflow-hidden flex flex-col">
          {/* Header of Code Box */}
          <div className="p-4 bg-slate-950/80 border-b border-slate-800 flex items-center justify-between gap-4">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono font-bold text-cyan-400">{selectedFile.path}</span>
                <span className="text-[10px] px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono uppercase">
                  {selectedFile.language}
                </span>
              </div>
              <p className="text-xs text-slate-400 mt-1 truncate">{selectedFile.description}</p>
            </div>

            <button
              onClick={handleCopy}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-700 bg-slate-800/60 hover:bg-slate-700 text-slate-200 text-xs transition-colors shrink-0"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'کپی شد' : 'کپی کد'}</span>
            </button>
          </div>

          {/* Syntax Code Display */}
          <div className="p-4 overflow-x-auto bg-slate-950 font-mono text-xs text-slate-200 leading-relaxed max-h-[600px] overflow-y-auto">
            <pre className="whitespace-pre">
              <code>{selectedFile.content}</code>
            </pre>
          </div>

          {/* Footer with engineering context */}
          <div className="p-3 bg-slate-950/90 border-t border-slate-800/80 text-[11px] text-slate-400 flex items-center justify-between">
            <span className="truncate">موقعیت در ریپازیتوری محلی: /kayumars/{selectedFile.path}</span>
            <span className="text-cyan-400 font-mono shrink-0">آماده کامپایل با NDK r27c</span>
          </div>
        </div>
      </div>
    </div>
  );
};
