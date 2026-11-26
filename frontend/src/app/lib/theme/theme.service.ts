import { DOCUMENT } from '@angular/common';
import { computed, DestroyRef, inject, Service, signal } from '@angular/core';
import { EffectiveTheme, ThemeHelper, ThemeMode } from './theme.helper';

/**
 * Owns the color scheme of the desktop hosts. The chosen mode is a browser preference stored in `localStorage`, so it
 * applies before login and in the pre-paint script of `index.html`, which reads the same key.
 *
 * The scheme is activated by the attribute `data-bs-theme` on `<html>` together with `style.colorScheme`: Bootstrap
 * switches natively on it, Optimus is configured with `darkModeSelector: '[data-bs-theme="dark"]'`, and every custom
 * dark rule uses the same selector. The attribute is written before the signals change, so an effect reading CSS
 * custom properties always sees the values of the new scheme.
 */
@Service()
export class ThemeService {
  /** Key under which the mode is stored as a raw string; the pre-paint script of both hosts reads it too. */
  static readonly STORAGE_KEY = 'gt.theme';

  /** Selector both hosts pass to Optimus as `darkModeSelector`; it matches the attribute this service writes. */
  static readonly DARK_MODE_SELECTOR = '[data-bs-theme="dark"]';

  private static readonly DARK_QUERY = '(prefers-color-scheme: dark)';

  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  private readonly modeState = signal<ThemeMode>('system');
  private readonly systemPrefersDark = signal(false);

  /** The mode the user chose; the theme menu marks this value, not the effective scheme. */
  readonly mode = this.modeState.asReadonly();

  /** The scheme applied to the document. */
  readonly effectiveTheme = computed<EffectiveTheme>(() =>
    ThemeHelper.resolveEffectiveTheme(this.modeState(), this.systemPrefersDark())
  );

  /** True while the dark scheme is applied. */
  readonly isDark = computed(() => this.effectiveTheme() === 'dark');

  constructor() {
    const mediaQuery = this.createMediaQuery();
    const prefersDark = mediaQuery?.matches ?? false;
    const mode = ThemeHelper.parseThemeMode(this.readStoredMode());
    // Re-applied at bootstrap, so a page whose pre-paint script failed is corrected here.
    this.applyToDocument(ThemeHelper.resolveEffectiveTheme(mode, prefersDark));
    this.systemPrefersDark.set(prefersDark);
    this.modeState.set(mode);
    this.registerListeners(mediaQuery);
  }

  /**
   * Chooses a mode, stores it and applies the resulting scheme. Storage failures are ignored; the mode then lives in
   * memory for the rest of the session.
   *
   * @param mode - The new mode
   */
  setMode(mode: ThemeMode): void {
    this.writeStoredMode(mode);
    this.apply(mode, this.systemPrefersDark());
  }

  private apply(mode: ThemeMode, prefersDark: boolean): void {
    this.applyToDocument(ThemeHelper.resolveEffectiveTheme(mode, prefersDark));
    this.systemPrefersDark.set(prefersDark);
    this.modeState.set(mode);
  }

  private applyToDocument(theme: EffectiveTheme): void {
    const root = this.document.documentElement;
    root.setAttribute('data-bs-theme', theme);
    root.style.colorScheme = theme;
  }

  private registerListeners(mediaQuery: MediaQueryList | null): void {
    const view = this.document.defaultView;
    const mediaListener = (event: MediaQueryListEvent): void => this.apply(this.modeState(), event.matches);
    const storageListener = (event: StorageEvent): void => {
      // A null key means another tab cleared the whole storage, which removes the stored mode as well.
      if (event.key === ThemeService.STORAGE_KEY || event.key === null) {
        this.apply(ThemeHelper.parseThemeMode(event.newValue), this.systemPrefersDark());
      }
    };
    mediaQuery?.addEventListener('change', mediaListener);
    view?.addEventListener('storage', storageListener);
    this.destroyRef.onDestroy(() => {
      mediaQuery?.removeEventListener('change', mediaListener);
      view?.removeEventListener('storage', storageListener);
    });
  }

  private createMediaQuery(): MediaQueryList | null {
    const view = this.document.defaultView;
    try {
      return view && typeof view.matchMedia === 'function' ? view.matchMedia(ThemeService.DARK_QUERY) : null;
    } catch {
      return null;
    }
  }

  private readStoredMode(): string | null {
    try {
      return this.document.defaultView?.localStorage.getItem(ThemeService.STORAGE_KEY) ?? null;
    } catch {
      return null;
    }
  }

  private writeStoredMode(mode: ThemeMode): void {
    try {
      this.document.defaultView?.localStorage.setItem(ThemeService.STORAGE_KEY, mode);
    } catch {
      // Storage disabled or full: the mode stays in memory.
    }
  }
}
