import { describe, expect, it } from 'vitest';
import {
  buildPrintFigure,
  numberTransactionMarkers,
  PlotlyTraceLike,
  PRINT_FIGURE_SIZE,
  svgFromDataUrl,
  TRANSACTION_MARKER_META
} from './security-chart-pdf';

const line: PlotlyTraceLike = { x: ['2024-01-02', '2024-12-30'], y: [100, 120], type: 'scatter', mode: 'lines' };

/** A purchase trace and a sale trace, as the chart builds them: one trace per transaction type. */
function markerTraces(): PlotlyTraceLike[] {
  return [
    line,
    {
      meta: TRANSACTION_MARKER_META,
      x: ['2024-03-04', '2024-09-10', '2024-11-20'],
      y: [101, 110, null],
      text: ['Account A', 'Account A', 'Account B'],
      customdata: [
        [11, '2024-03-04T10:15:00'],
        [13, '2024-09-10T09:00:00'],
        [14, '2024-11-20T00:00:00']
      ]
    },
    {
      meta: TRANSACTION_MARKER_META,
      x: ['2024-06-03'],
      y: [115],
      text: ['Account A'],
      customdata: [[12, '2024-06-03T15:30:00']]
    }
  ];
}

describe('numberTransactionMarkers', () => {
  it('numbers the drawn markers in the order of their booking across all marker traces', () => {
    const { numbers, labels } = numberTransactionMarkers(markerTraces());

    // Booking order: 11 (March), 12 (June, second trace), 13 (September). 14 has no price and is not drawn.
    expect(numbers).toEqual([
      { idTransaction: 11, number: 1 },
      { idTransaction: 12, number: 2 },
      { idTransaction: 13, number: 3 }
    ]);
    expect(labels.get(1)).toEqual(['1', '3', '']);
    expect(labels.get(2)).toEqual(['2']);
    expect(labels.has(0)).toBe(false);
  });

  it('leaves out a marker outside the displayed date range and keeps the numbers consecutive', () => {
    const { numbers, labels } = numberTransactionMarkers(markerTraces(), ['2024-04-01 00:00', '2024-12-31']);

    expect(numbers).toEqual([
      { idTransaction: 12, number: 1 },
      { idTransaction: 13, number: 2 }
    ]);
    expect(labels.get(1)).toEqual(['', '2', '']);
  });

  it('orders two bookings of the same minute by their id and shows every marker without a usable range', () => {
    const traces: PlotlyTraceLike[] = [
      {
        meta: TRANSACTION_MARKER_META,
        x: ['2024-05-06', '2024-05-06'],
        y: [1, 1],
        customdata: [
          [22, '2024-05-06T08:00:00'],
          [21, '2024-05-06T08:00:00']
        ]
      }
    ];

    for (const range of [undefined, null, [1, 2], ['2024-01-01']]) {
      expect(numberTransactionMarkers(traces, range).numbers).toEqual([
        { idTransaction: 21, number: 1 },
        { idTransaction: 22, number: 2 }
      ]);
    }
  });

  it('ignores a point whose custom data is not a transaction id and a time', () => {
    const traces: PlotlyTraceLike[] = [
      {
        meta: TRANSACTION_MARKER_META,
        x: ['2024-05-06', '2024-05-07', '2024-05-08'],
        y: [1, 1, 1],
        customdata: [['x', '2024-05-06T08:00:00'], [31], [32, '2024-05-08T08:00:00']]
      }
    ];

    expect(numberTransactionMarkers(traces).numbers).toEqual([{ idTransaction: 32, number: 1 }]);
  });
});

describe('svgFromDataUrl', () => {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg"><text>Kurs 100 % für März</text></svg>';

  it('decodes the URL-encoded answer of Plotly, including a comma, a percent sign and umlauts', () => {
    expect(svgFromDataUrl('data:image/svg+xml,' + encodeURIComponent(svg))).toBe(svg);
  });

  it('decodes a base64 answer as UTF-8', () => {
    const bytes = new TextEncoder().encode(svg);
    const base64 = btoa(String.fromCharCode(...bytes));
    expect(svgFromDataUrl('data:image/svg+xml;base64,' + base64)).toBe(svg);
  });

  it('refuses anything that is not a data URL', () => {
    expect(() => svgFromDataUrl('<svg/>')).toThrow();
    expect(() => svgFromDataUrl('data:image/svg+xml')).toThrow();
  });
});

describe('buildPrintFigure', () => {
  const layout = {
    width: 1234,
    autosize: true,
    paper_bgcolor: '#101010',
    plot_bgcolor: '#101010',
    font: { family: 'Open Sans', size: 11, color: '#eeeeee' },
    xaxis: { range: ['2024-04-01', '2024-12-31'], rangeslider: { visible: true, thickness: 0.05 } },
    shapes: [{ type: 'line', x0: '2024-05-01', x1: '2024-06-01', y0: 100, y1: 110 }]
  };

  it('prints the numbers at the markers and keeps the zoom, the drawings and the other traces', () => {
    const traces = markerTraces();
    const figure = buildPrintFigure(traces, layout, numberTransactionMarkers(traces, layout.xaxis.range));

    expect(figure.data[0]).toEqual(line);
    expect(figure.data[1]['text']).toEqual(['', '2', '']);
    expect(figure.data[1]['mode']).toBe('markers+text');
    expect(figure.data[2]['text']).toEqual(['1']);
    expect(figure.layout['shapes']).toEqual(layout.shapes);
    expect(figure.layout['xaxis'].range).toEqual(['2024-04-01', '2024-12-31']);
  });

  it('sets the page size, white ground and a hidden range slider without changing the chart on screen', () => {
    const traces = markerTraces();
    const before = JSON.stringify({ traces, layout });

    const figure = buildPrintFigure(traces, layout, null);

    expect(figure.layout['width']).toBe(PRINT_FIGURE_SIZE.width);
    expect(figure.layout['height']).toBe(PRINT_FIGURE_SIZE.height);
    expect(figure.layout['autosize']).toBe(false);
    expect(figure.layout['paper_bgcolor']).toBe('#ffffff');
    expect(figure.layout['plot_bgcolor']).toBe('#ffffff');
    expect(figure.layout['font']['size']).toBe(11);
    expect(figure.layout['font']['color']).not.toBe('#eeeeee');
    expect(figure.layout['xaxis'].rangeslider).toEqual({ visible: false });
    expect(JSON.stringify({ traces, layout })).toBe(before);
  });

  it('makes translucent traces opaque, because the PDF conversion loses the text of a translucent group', () => {
    const traces: PlotlyTraceLike[] = [{ ...line, opacity: 0.75 }, { ...line, opacity: 1 }, { ...line }];

    const figure = buildPrintFigure(traces, layout, null);

    expect(figure.data.map((trace) => trace['opacity'])).toEqual([1, 1, undefined]);
    expect(traces[0]['opacity']).toBe(0.75);
  });

  it('prints grid, axis and legend of a dark screen layout in light colors and keeps ranges and accents', () => {
    const darkLayout = {
      ...layout,
      legend: { orientation: 'h', font: { size: 10, color: '#ffffff' } },
      xaxis: { ...layout.xaxis, gridcolor: '#3f3f46', linecolor: '#3f3f46', zerolinecolor: '#3f3f46' },
      yaxis: { range: [90, 130], gridcolor: '#3f3f46' },
      yaxis2: { overlaying: 'y', side: 'right', tickfont: { color: 'rgb(148, 103, 189)' }, gridcolor: '#3f3f46' }
    };
    const traces = markerTraces();

    const figure = buildPrintFigure(traces, darkLayout, numberTransactionMarkers(traces, darkLayout.xaxis.range));

    for (const axis of ['xaxis', 'yaxis', 'yaxis2']) {
      expect(figure.layout[axis].gridcolor).toBe('#e1e0d9');
      expect(figure.layout[axis].zerolinecolor).toBe('#e1e0d9');
    }
    expect(figure.layout['xaxis'].linecolor).toBe('#e1e0d9');
    expect(figure.layout['xaxis'].range).toEqual(['2024-04-01', '2024-12-31']);
    expect(figure.layout['yaxis'].range).toEqual([90, 130]);
    expect(figure.layout['yaxis2'].tickfont.color).toBe('rgb(148, 103, 189)');
    expect(figure.layout['legend']).toEqual({ orientation: 'h', font: { size: 10, color: '#2a3f5f' } });
    expect(figure.layout['paper_bgcolor']).toBe('#ffffff');
    expect(figure.data[2]['text']).toEqual(['1']);
    expect(darkLayout.yaxis.gridcolor).toBe('#3f3f46');
  });

  it('draws no numbers when none are asked for', () => {
    const figure = buildPrintFigure(markerTraces(), layout, null);

    expect(figure.data[1]['text']).toEqual(['Account A', 'Account A', 'Account B']);
    expect(figure.data[1]['mode']).toBeUndefined();
  });
});
