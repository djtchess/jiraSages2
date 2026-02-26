import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ThemeMode = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly storageKey = 'sages2-theme-mode';
  private readonly mode$ = new BehaviorSubject<ThemeMode>('system');
  private readonly resolvedTheme$ = new BehaviorSubject<ResolvedTheme>('light');

  readonly themeMode$ = this.mode$.asObservable();
  readonly activeTheme$ = this.resolvedTheme$.asObservable();

  initTheme(): void {
    const storedMode = (localStorage.getItem(this.storageKey) as ThemeMode | null) ?? 'system';
    const safeMode: ThemeMode = ['light', 'dark', 'system'].includes(storedMode) ? storedMode : 'system';
    this.setTheme(safeMode);

    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (this.mode$.value === 'system') {
        this.applyTheme('system');
      }
    });
  }

  setTheme(mode: ThemeMode): void {
    this.mode$.next(mode);
    localStorage.setItem(this.storageKey, mode);
    this.applyTheme(mode);
  }

  toggleTheme(): void {
    const current = this.getActiveTheme();
    this.setTheme(current === 'dark' ? 'light' : 'dark');
  }

  getThemeMode(): ThemeMode {
    return this.mode$.value;
  }

  getActiveTheme(): ResolvedTheme {
    return this.resolvedTheme$.value;
  }

  private applyTheme(mode: ThemeMode): void {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const resolved: ResolvedTheme = mode === 'system' ? (prefersDark ? 'dark' : 'light') : mode;

    const root = document.documentElement;
    root.setAttribute('data-theme', resolved);
    document.body.classList.toggle('dark-mode', resolved === 'dark');

    this.resolvedTheme$.next(resolved);
  }
}
