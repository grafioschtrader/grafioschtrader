import { describe, expect, it } from 'vitest';
import { Rgb, ThemeHelper } from '../../lib/theme/theme.helper';
import { HeatmapColors } from './heatmap.colors';

/** Converts the `hsl(h, s%, l%)` strings of HeatmapColors the way a browser does. */
function hslToRgb(hsl: string): Rgb {
  const m = /^hsl\(([\d.]+), ([\d.]+)%, ([\d.]+)%\)$/.exec(hsl);
  if (!m) throw new Error('no hsl: ' + hsl);
  const h = +m[1];
  const s = +m[2] / 100;
  const l = +m[3] / 100;
  const k = (n: number): number => (n + h / 30) % 12;
  const a = s * Math.min(l, 1 - l);
  const f = (n: number): number => Math.round((l - a * Math.max(-1, Math.min(k(n) - 3, 9 - k(n), 1))) * 255);
  return [f(0), f(8), f(4)];
}

const LIGHT_TEXT: Rgb = [0x33, 0x41, 0x55];
const DARK_TEXT: Rgb = [255, 255, 255];

describe('HeatmapColors contrast against the inherited text', () => {
  it('keeps every correlation background at 4.5:1 or more in both schemes', () => {
    for (let i = -100; i <= 100; i++) {
      const v = i / 100;
      expect(
        ThemeHelper.contrastRatio(hslToRgb(HeatmapColors.correlationCellBackground(v, false)), LIGHT_TEXT)
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        ThemeHelper.contrastRatio(hslToRgb(HeatmapColors.correlationCellBackground(v, true)), DARK_TEXT)
      ).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('keeps every seasonality background at 4.5:1 or more in both schemes', () => {
    for (let i = 0; i <= 200; i++) {
      const t = i / 200;
      for (const positive of [true, false]) {
        expect(
          ThemeHelper.contrastRatio(hslToRgb(HeatmapColors.seasonalityCellBackground(positive, t, false)), LIGHT_TEXT)
        ).toBeGreaterThanOrEqual(4.5);
        expect(
          ThemeHelper.contrastRatio(hslToRgb(HeatmapColors.seasonalityCellBackground(positive, t, true)), DARK_TEXT)
        ).toBeGreaterThanOrEqual(4.5);
      }
    }
  });

  it('still separates small seasonality ranks from the most extreme one', () => {
    for (const isDark of [false, true]) {
      const third = HeatmapColors.seasonalityCellBackground(true, 1 / 3, isDark);
      const twoThirds = HeatmapColors.seasonalityCellBackground(true, 2 / 3, isDark);
      const full = HeatmapColors.seasonalityCellBackground(true, 1, isDark);
      expect(new Set([third, twoThirds, full]).size).toBe(3);
    }
  });

  it('keeps deviation and scale backgrounds at 4.5:1 or more in both schemes', () => {
    for (let i = 0; i <= 100; i++) {
      for (const hue of [0, 120]) {
        expect(
          ThemeHelper.contrastRatio(hslToRgb(HeatmapColors.deviationCellBackground(hue, i / 100, false)), LIGHT_TEXT)
        ).toBeGreaterThanOrEqual(4.5);
        expect(
          ThemeHelper.contrastRatio(hslToRgb(HeatmapColors.deviationCellBackground(hue, i / 100, true)), DARK_TEXT)
        ).toBeGreaterThanOrEqual(4.5);
      }
    }
    for (let hue = 0; hue <= 120; hue++) {
      expect(
        ThemeHelper.contrastRatio(hslToRgb(HeatmapColors.scaleCellBackground(hue, false)), LIGHT_TEXT)
      ).toBeGreaterThanOrEqual(4.5);
      expect(
        ThemeHelper.contrastRatio(hslToRgb(HeatmapColors.scaleCellBackground(hue, true)), DARK_TEXT)
      ).toBeGreaterThanOrEqual(4.5);
    }
  });
});
