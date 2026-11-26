import { describe, expect, it } from 'vitest';
import { ThemeHelper } from './theme.helper';

describe('ThemeHelper mode resolution', () => {
  it('accepts only the three stored literals and falls back to system', () => {
    expect(ThemeHelper.parseThemeMode('dark')).toBe('dark');
    expect(ThemeHelper.parseThemeMode('light')).toBe('light');
    expect(ThemeHelper.parseThemeMode('system')).toBe('system');
    expect(ThemeHelper.parseThemeMode(null)).toBe('system');
    expect(ThemeHelper.parseThemeMode('Dark')).toBe('system');
    expect(ThemeHelper.parseThemeMode('"dark"')).toBe('system');
  });

  it('follows the operating system only in system mode', () => {
    expect(ThemeHelper.resolveEffectiveTheme('system', true)).toBe('dark');
    expect(ThemeHelper.resolveEffectiveTheme('system', false)).toBe('light');
    expect(ThemeHelper.resolveEffectiveTheme('light', true)).toBe('light');
    expect(ThemeHelper.resolveEffectiveTheme('dark', false)).toBe('dark');
  });
});

describe('ThemeHelper contrast', () => {
  it('computes the WCAG reference ratios', () => {
    expect(ThemeHelper.contrastRatio([0, 0, 0], [255, 255, 255])).toBeCloseTo(21, 5);
    expect(ThemeHelper.contrastRatio([255, 255, 255], [255, 255, 255])).toBeCloseTo(1, 5);
    // #1e8e3e on white, the example of insufficient green text
    expect(ThemeHelper.contrastRatio([0x1e, 0x8e, 0x3e], [255, 255, 255])).toBeCloseTo(4.21, 2);
  });

  it('picks black on the dark-scheme yellow where an RGB-weight threshold picks white', () => {
    const yellow = ThemeHelper.hexToRgb('#b59b1d');
    expect(yellow).not.toBeNull();
    if (yellow) {
      expect(ThemeHelper.contrastRatio(yellow, [255, 255, 255])).toBeCloseTo(2.74, 2);
      expect(ThemeHelper.contrastRatio(yellow, [0, 0, 0])).toBeCloseTo(7.67, 2);
      expect(ThemeHelper.contrastingForeground(yellow)).toBe('#000000');
    }
    expect(ThemeHelper.contrastingForeground([0x1d, 0x4e, 0xd8])).toBe('#ffffff');
  });

  it('parses short and long hex colors and rejects anything else', () => {
    expect(ThemeHelper.hexToRgb('#fff')).toEqual([255, 255, 255]);
    expect(ThemeHelper.hexToRgb('#2563EB')).toEqual([0x25, 0x63, 0xeb]);
    expect(ThemeHelper.hexToRgb('blue')).toBeNull();
    expect(ThemeHelper.hexToRgb('#12345')).toBeNull();
  });
});
