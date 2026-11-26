/**
 * Color scheme the user chose. `system` follows the `prefers-color-scheme` media query of the operating system.
 */
export type ThemeMode = 'system' | 'light' | 'dark';

/**
 * Color scheme actually applied to the document after resolving `system` against the operating system preference.
 */
export type EffectiveTheme = 'light' | 'dark';

/** An RGB triple with channels in the range 0..255. */
export type Rgb = readonly [number, number, number];

/**
 * Pure helpers of the theme infrastructure. They hold no state and touch neither the DOM nor storage, so they are
 * shared by `ThemeService`, the color helpers of tables and calendars, and the unit tests.
 */
export class ThemeHelper {
  /**
   * Converts the raw value stored under the theme key into a mode.
   *
   * @param raw - The stored string, null when nothing is stored or storage is unavailable
   * @returns The mode; anything but the three known literals yields `system`
   */
  static parseThemeMode(raw: string | null): ThemeMode {
    return raw === 'light' || raw === 'dark' || raw === 'system' ? raw : 'system';
  }

  /**
   * Resolves the mode to the scheme that is applied to the document.
   *
   * @param mode - The chosen mode
   * @param systemPrefersDark - Whether the operating system currently prefers a dark scheme
   * @returns `dark` for an explicit dark mode or for `system` with a dark operating system, otherwise `light`
   */
  static resolveEffectiveTheme(mode: ThemeMode, systemPrefersDark: boolean): EffectiveTheme {
    if (mode === 'system') {
      return systemPrefersDark ? 'dark' : 'light';
    }
    return mode;
  }

  /**
   * Relative luminance of a color as defined by WCAG 2.x.
   *
   * @param rgb - Color with channels 0..255
   * @returns Luminance between 0 (black) and 1 (white)
   */
  static relativeLuminance(rgb: Rgb): number {
    const [r, g, b] = rgb.map((channel) => {
      const c = channel / 255;
      return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  /**
   * WCAG contrast ratio between two colors, independent of their order.
   *
   * @param a - First color
   * @param b - Second color
   * @returns Ratio between 1 (identical luminance) and 21 (black on white)
   */
  static contrastRatio(a: Rgb, b: Rgb): number {
    const la = ThemeHelper.relativeLuminance(a);
    const lb = ThemeHelper.relativeLuminance(b);
    return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
  }

  /**
   * Chooses black or white text for a background, whichever reaches the higher contrast ratio.
   *
   * @param rgb - Background color
   * @returns `#000000` or `#ffffff`
   */
  static contrastingForeground(rgb: Rgb): '#000000' | '#ffffff' {
    return ThemeHelper.contrastRatio(rgb, [0, 0, 0]) >= ThemeHelper.contrastRatio(rgb, [255, 255, 255])
      ? '#000000'
      : '#ffffff';
  }

  /**
   * Parses a hex color of the form `#rgb` or `#rrggbb`.
   *
   * @param hex - Color string with leading `#`
   * @returns The RGB triple, or null when the string is no valid hex color
   */
  static hexToRgb(hex: string): Rgb | null {
    const m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(hex.trim());
    if (!m) {
      return null;
    }
    const digits =
      m[1].length === 3
        ? m[1]
            .split('')
            .map((d) => d + d)
            .join('')
        : m[1];
    return [
      parseInt(digits.substring(0, 2), 16),
      parseInt(digits.substring(2, 4), 16),
      parseInt(digits.substring(4, 6), 16)
    ];
  }
}
