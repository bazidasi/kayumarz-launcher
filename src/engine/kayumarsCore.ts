import { AppEntry, SearchResult, HistoryEvent, ContactEntry, SettingEntry, LauncherPreferences } from '../types/launcher';
import { CONTACTS_FIXTURE, SYSTEM_SETTINGS_FIXTURE, DEFAULT_PREFERENCES } from '../data/appsFixture';

interface NormalizedApp {
  entry: AppEntry;
  normLabel: string;
  normPackage: string;
}

interface NormalizedContact {
  entry: ContactEntry;
  normName: string;
  phoneDigits: string;
}

interface NormalizedSetting {
  entry: SettingEntry;
  normTitle: string;
  normDescription: string;
}

function normalizePreferences(raw: unknown): LauncherPreferences {
  const src = raw && typeof raw === 'object' ? (raw as Partial<LauncherPreferences>) : {};
  const rawScopes = src.searchScopes && typeof src.searchScopes === 'object' ? src.searchScopes : {};
  const rawGestures = src.gestures && typeof src.gestures === 'object' ? src.gestures : {};
  return {
    ...DEFAULT_PREFERENCES,
    ...src,
    dockPackages: Array.isArray(src.dockPackages)
      ? src.dockPackages.filter((p): p is string => typeof p === 'string')
      : [...DEFAULT_PREFERENCES.dockPackages],
    hiddenPackages: Array.isArray(src.hiddenPackages)
      ? src.hiddenPackages.filter((p): p is string => typeof p === 'string')
      : [...DEFAULT_PREFERENCES.hiddenPackages],
    mutedBadgePackages: Array.isArray(src.mutedBadgePackages)
      ? src.mutedBadgePackages.filter((p): p is string => typeof p === 'string')
      : [...(DEFAULT_PREFERENCES.mutedBadgePackages || [])],
    searchScopes: {
      ...DEFAULT_PREFERENCES.searchScopes,
      ...rawScopes,
    },
    gestures: {
      ...DEFAULT_PREFERENCES.gestures,
      ...rawGestures,
    },
  };
}

export class KayumarsSimulationEngine {
  private apps: NormalizedApp[] = [];
  private contacts: NormalizedContact[] = [];
  private settings: NormalizedSetting[] = [];
  private packageToIdx: Map<string, number> = new Map();
  private launchCounts: Map<string, number> = new Map();
  private recentEvents: HistoryEvent[] = [];
  private totalLaunches: number = 0;
  private readonly maxEvents: number = 10000;
  private readonly compactRetain: number = 2000;
  private preferences: LauncherPreferences = normalizePreferences(DEFAULT_PREFERENCES);

  constructor(initialApps: AppEntry[] = []) {
    this.loadFromStorage();
    this.initContactsAndSettings();
    if (initialApps.length > 0) {
      this.replaceIndex(initialApps);
    }
  }

  private initContactsAndSettings() {
    this.contacts = CONTACTS_FIXTURE.map((c) => ({
      entry: c,
      normName: c.name.trim().normalize('NFC').toLowerCase(),
      phoneDigits: c.phone.replace(/\D/g, ''),
    }));

    this.settings = SYSTEM_SETTINGS_FIXTURE.map((s) => ({
      entry: s,
      normTitle: s.title.trim().normalize('NFC').toLowerCase(),
      normDescription: s.description.trim().normalize('NFC').toLowerCase(),
    }));
  }

  public getPreferences(): LauncherPreferences {
    return { ...this.preferences };
  }

  public updatePreferences(newPrefs: Partial<LauncherPreferences>): LauncherPreferences {
    this.preferences = normalizePreferences({
      ...this.preferences,
      ...newPrefs,
      searchScopes: {
        ...this.preferences.searchScopes,
        ...(newPrefs.searchScopes && typeof newPrefs.searchScopes === 'object' ? newPrefs.searchScopes : {}),
      },
      gestures: {
        ...this.preferences.gestures,
        ...(newPrefs.gestures && typeof newPrefs.gestures === 'object' ? newPrefs.gestures : {}),
      },
    });
    this.saveToStorage();
    return this.getPreferences();
  }

  public toggleHideApp(pkg: string): boolean {
    const isHidden = this.preferences.hiddenPackages.includes(pkg);
    if (isHidden) {
      this.preferences.hiddenPackages = this.preferences.hiddenPackages.filter((p) => p !== pkg);
    } else {
      this.preferences.hiddenPackages = [...this.preferences.hiddenPackages, pkg];
    }
    this.saveToStorage();
    return !isHidden;
  }

  public isAppHidden(pkg: string): boolean {
    return this.preferences.hiddenPackages.includes(pkg);
  }

  /**
   * Replaces the in-memory index atomically (simulates ArcSwap).
   */
  public replaceIndex(entries: AppEntry[]): number {
    const t0 = performance.now();
    const newApps: NormalizedApp[] = new Array(entries.length);
    const newMap = new Map<string, number>();

    for (let i = 0; i < entries.length; i++) {
      const e = entries[i];
      const normLabel = e.label.trim().normalize('NFC').toLowerCase();
      const normPkg = e.package.toLowerCase();
      newApps[i] = { entry: e, normLabel, normPackage: normPkg };
      newMap.set(e.package, i);
    }

    this.apps = newApps;
    this.packageToIdx = newMap;
    const elapsed = performance.now() - t0;
    return elapsed;
  }

  /**
   * Records a launch event into the append-only ring buffer.
   */
  public logLaunch(packageName: string, timestampMs: number = Date.now()): void {
    this.totalLaunches++;
    const current = this.launchCounts.get(packageName) || 0;
    this.launchCounts.set(packageName, current + 1);

    this.recentEvents.push({ package: packageName, timestamp_ms: timestampMs });

    if (this.recentEvents.length >= this.maxEvents) {
      this.compact();
    }
    this.saveToStorage();
  }

  private compact(): void {
    if (this.recentEvents.length > this.compactRetain) {
      this.recentEvents = this.recentEvents.slice(this.recentEvents.length - this.compactRetain);
    }
  }

  public computeRankingMultiplier(
    launches: number,
    totalLaunches: number,
    totalInstalled: number,
    recentPos?: number
  ): number {
    const smoothing = Math.max(1, totalInstalled);
    const freqWeight = (launches + 1.0) / (totalLaunches + smoothing);
    const pos = recentPos ? Math.max(1, recentPos) : totalInstalled + 5;
    const recencyWeight = 1.0 / pos;
    return freqWeight * recencyWeight;
  }

  private getRecencyMap(): Map<string, number> {
    const recency = new Map<string, number>();
    let order = 1;
    for (let i = this.recentEvents.length - 1; i >= 0; i--) {
      const pkg = this.recentEvents[i].package;
      if (!recency.has(pkg)) {
        recency.set(pkg, order++);
      }
    }
    return recency;
  }

  private fuzzyMatch(query: string, target: string): number {
    if (!query) return 0;
    if (query === target) return 1000;
    if (target.startsWith(query)) return 500 + (100 - target.length);

    const subIdx = target.indexOf(query);
    if (subIdx !== -1) {
      return 300 - subIdx * 5;
    }

    const words = target.split(/[\s._-]+/);
    if (words.length > 1) {
      const initials = words.map((w) => w[0]).join('');
      if (initials.startsWith(query)) {
        return 400 + (50 - initials.length);
      }
    }

    let qIdx = 0;
    let score = 0;
    let consecutive = 0;
    let lastMatchIdx = -1;

    for (let tIdx = 0; tIdx < target.length && qIdx < query.length; tIdx++) {
      if (query[qIdx] === target[tIdx]) {
        score += 10;
        if (lastMatchIdx === tIdx - 1) {
          consecutive++;
          score += consecutive * 5;
        } else {
          consecutive = 0;
        }

        if (tIdx === 0 || /[\s._-]/.test(target[tIdx - 1])) {
          score += 25;
        }

        lastMatchIdx = tIdx;
        qIdx++;
      }
    }

    return qIdx === query.length ? score : 0;
  }

  /**
   * Unified Search across Apps, Contacts, and Settings with sub-5ms speed.
   */
  public search(
    query: string,
    maxResults: number = 14,
    includeHidden: boolean = false
  ): { results: SearchResult[]; latencyMs: number } {
    const t0 = performance.now();
    const trimmed = query.trim().normalize('NFC').toLowerCase();

    if (!trimmed) {
      const top = this.topApps(maxResults, includeHidden);
      const latencyMs = performance.now() - t0;
      return { results: top, latencyMs };
    }

    const results: SearchResult[] = [];
    const recency = this.getRecencyMap();
    const totalInstalled = this.apps.length;
    const scopes = this.preferences.searchScopes;

    // 1. Search Apps (if enabled)
    if (scopes.apps) {
      for (let i = 0; i < this.apps.length; i++) {
        const app = this.apps[i];
        if (!includeHidden && this.preferences.hiddenPackages.includes(app.entry.package)) {
          continue;
        }

        let rawScore = this.fuzzyMatch(trimmed, app.normLabel);
        if (rawScore === 0 && app.normPackage.includes(trimmed)) {
          rawScore = 30;
        }

        if (rawScore > 0) {
          const launches = this.launchCounts.get(app.entry.package) || 0;
          const recentPos = recency.get(app.entry.package);
          const multiplier = this.computeRankingMultiplier(
            launches,
            this.totalLaunches,
            totalInstalled,
            recentPos
          );
          const finalScore = rawScore * (1.0 + multiplier * 100.0);

          results.push({
            package: app.entry.package,
            label: app.entry.label,
            score: finalScore,
            kind: 'App',
            category: app.entry.category,
            iconColor: app.entry.iconColor,
            launches,
            recentRank: recentPos,
            detail: app.entry.package,
          });
        }
      }
    }

    // 2. Search Contacts (if enabled)
    if (scopes.contacts) {
      const cleanDigits = trimmed.replace(/\D/g, '');
      for (const c of this.contacts) {
        let score = this.fuzzyMatch(trimmed, c.normName);
        if (score === 0 && cleanDigits && c.phoneDigits.includes(cleanDigits)) {
          score = 250;
        }

        if (score > 0) {
          results.push({
            package: `contact.${c.entry.id}`,
            label: c.entry.name,
            score: score * 1.05, // Slight boost for direct contact matches
            kind: 'Contact',
            category: 'social',
            iconColor: c.entry.color,
            launches: 0,
            detail: c.entry.phone,
            actionTarget: `tel:${c.entry.phone}`,
          });
        }
      }
    }

    // 3. Search Settings (if enabled)
    if (scopes.settings) {
      for (const s of this.settings) {
        let score = this.fuzzyMatch(trimmed, s.normTitle);
        if (score === 0 && s.normDescription.includes(trimmed)) {
          score = 180;
        }

        if (score > 0) {
          results.push({
            package: `setting.${s.entry.id}`,
            label: s.entry.title,
            score: score * 0.95,
            kind: 'Setting',
            category: 'system',
            iconColor: '#64748B',
            launches: 0,
            detail: s.entry.description,
            actionTarget: s.entry.action,
          });
        }
      }
    }

    // Sort descending by score
    results.sort((a, b) => b.score - a.score);

    const takeN = Math.min(results.length, maxResults);
    const finalResults = results.slice(0, takeN);
    const latencyMs = performance.now() - t0;

    return { results: finalResults, latencyMs };
  }

  /**
   * Returns top applications when search bar is empty (KISS ranking).
   */
  public topApps(maxResults: number = 14, includeHidden: boolean = false): SearchResult[] {
    const recency = this.getRecencyMap();
    const totalInstalled = this.apps.length;

    const filtered = includeHidden
      ? this.apps
      : this.apps.filter((a) => !this.preferences.hiddenPackages.includes(a.entry.package));

    const scored = filtered.map((app) => {
      const launches = this.launchCounts.get(app.entry.package) || 0;
      const recentPos = recency.get(app.entry.package);
      const mult = this.computeRankingMultiplier(launches, this.totalLaunches, totalInstalled, recentPos);
      return {
        app,
        score: mult * 1000.0,
        launches,
        recentRank: recentPos,
      };
    });

    scored.sort((a, b) => {
      if (b.score !== a.score) return b.score - a.score;
      return a.app.entry.label.localeCompare(b.app.entry.label);
    });

    return scored.slice(0, maxResults).map((s) => ({
      package: s.app.entry.package,
      label: s.app.entry.label,
      score: Math.round(s.score * 10) / 10,
      kind: 'App',
      category: s.app.entry.category,
      iconColor: s.app.entry.iconColor,
      launches: s.launches,
      recentRank: s.recentRank,
      detail: s.app.entry.package,
    }));
  }

  public getStats() {
    return {
      totalInstalled: this.apps.length,
      totalLaunches: this.totalLaunches,
      recentEventsCount: this.recentEvents.length,
      hiddenCount: this.preferences.hiddenPackages.length,
    };
  }

  public resetHistory() {
    this.launchCounts.clear();
    this.recentEvents = [];
    this.totalLaunches = 0;
    try {
      localStorage.removeItem('kayumars_history');
      localStorage.removeItem('kayumars_history_v2');
    } catch (_) {}
  }

  public exportBackup(): string {
    return JSON.stringify({
      version: 2,
      exportDate: new Date().toISOString(),
      preferences: this.preferences,
      launchCounts: Array.from(this.launchCounts.entries()),
      totalLaunches: this.totalLaunches,
    }, null, 2);
  }

  public importBackup(jsonString: string): boolean {
    try {
      const data = JSON.parse(jsonString);
      if (!data || typeof data !== 'object') return false;
      if (data.preferences) {
        this.preferences = normalizePreferences(data.preferences);
      }
      if (Array.isArray(data.launchCounts)) {
        const validCounts = data.launchCounts.filter(
          (pair: unknown): pair is [string, number] =>
            Array.isArray(pair) && pair.length >= 2 && typeof pair[0] === 'string' && typeof pair[1] === 'number' && Number.isFinite(pair[1])
        );
        this.launchCounts = new Map(validCounts);
      }
      if (typeof data.totalLaunches === 'number' && Number.isFinite(data.totalLaunches) && data.totalLaunches >= 0) {
        this.totalLaunches = data.totalLaunches;
      }
      this.saveToStorage();
      return true;
    } catch (_) {
      return false;
    }
  }

  private saveToStorage() {
    try {
      const data = {
        totalLaunches: this.totalLaunches,
        counts: Array.from(this.launchCounts.entries()),
        events: this.recentEvents.slice(-200),
        preferences: this.preferences,
      };
      localStorage.setItem('kayumars_history_v2', JSON.stringify(data));
    } catch (_) {}
  }

  private loadFromStorage() {
    try {
      const raw = localStorage.getItem('kayumars_history_v2') || localStorage.getItem('kayumars_history');
      if (!raw) return;
      const data = JSON.parse(raw);
      if (!data || typeof data !== 'object') return;
      this.totalLaunches =
        typeof data.totalLaunches === 'number' && Number.isFinite(data.totalLaunches) && data.totalLaunches >= 0
          ? data.totalLaunches
          : 0;
      if (Array.isArray(data.counts)) {
        const validCounts = data.counts.filter(
          (pair: unknown): pair is [string, number] =>
            Array.isArray(pair) && pair.length >= 2 && typeof pair[0] === 'string' && typeof pair[1] === 'number' && Number.isFinite(pair[1])
        );
        this.launchCounts = new Map(validCounts);
      } else {
        this.launchCounts = new Map();
      }
      if (Array.isArray(data.events)) {
        this.recentEvents = data.events.filter(
          (ev: unknown): ev is HistoryEvent =>
            Boolean(ev) &&
            typeof ev === 'object' &&
            typeof (ev as HistoryEvent).package === 'string' &&
            typeof (ev as HistoryEvent).timestamp_ms === 'number'
        );
      } else {
        this.recentEvents = [];
      }
      if (data.preferences) {
        this.preferences = normalizePreferences(data.preferences);
      }
    } catch (_) {}
  }
}
