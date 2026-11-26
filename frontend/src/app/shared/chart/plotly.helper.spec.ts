import { describe, expect, it } from 'vitest';
import { PlotlyHelper, PlotlyThemeSurface } from './plotly.helper';

const DARK: PlotlyThemeSurface = {
  textColor: '#ffffff',
  mutedColor: '#a1a1aa',
  gridColor: '#3f3f46',
  surfaceBg: '#18181b',
  rangesliderBg: '#52525b'
};

describe('PlotlyHelper.applyThemeToLayout', () => {
  it('themes the shared chrome and keeps the accents chosen by the producer', () => {
    const layout: Record<string, any> = {
      font: { family: 'sans-serif', size: 11 },
      paper_bgcolor: '#ffffff',
      xaxis: { range: ['2024-01-01', '2024-06-30'], gridcolor: '#eeeeee' },
      yaxis2: { overlaying: 'y', side: 'right', tickfont: { color: 'rgb(148, 103, 189)' } },
      shapes: [{ type: 'line', line: { color: '#ff0000' } }]
    };
    PlotlyHelper.applyThemeToLayout(layout, DARK);

    expect(layout['paper_bgcolor']).toBe('rgba(0,0,0,0)');
    expect(layout['plot_bgcolor']).toBe('rgba(0,0,0,0)');
    expect(layout['font']).toEqual({ family: 'sans-serif', size: 11, color: '#ffffff' });
    expect(layout['xaxis'].range).toEqual(['2024-01-01', '2024-06-30']);
    expect(layout['xaxis'].gridcolor).toBe('#3f3f46');
    expect(layout['yaxis2'].tickfont.color).toBe('rgb(148, 103, 189)');
    expect(layout['yaxis2'].zerolinecolor).toBe('#3f3f46');
    expect(layout['shapes'][0].line.color).toBe('#ff0000');
  });

  it('gives an existing range slider the background on which the selection mask stays visible', () => {
    const layout: Record<string, any> = { xaxis: { rangeslider: { visible: true, thickness: 0.05 } }, xaxis2: {} };
    PlotlyHelper.applyThemeToLayout(layout, DARK);
    expect(layout['xaxis'].rangeslider).toEqual({
      visible: true,
      thickness: 0.05,
      bgcolor: '#52525b',
      bordercolor: '#3f3f46'
    });
    expect(layout['xaxis2'].rangeslider).toBeUndefined();
  });

  it('creates the default axes so Plotly does not fall back to its light grid', () => {
    const layout: Record<string, any> = {};
    PlotlyHelper.applyThemeToLayout(layout, DARK);
    expect(layout['xaxis'].gridcolor).toBe('#3f3f46');
    expect(layout['yaxis'].linecolor).toBe('#3f3f46');
  });

  it('leaves the legend color to the layout font', () => {
    expect(PlotlyHelper.getLegendUnderChart(10).font?.color).toBeUndefined();
  });
});
