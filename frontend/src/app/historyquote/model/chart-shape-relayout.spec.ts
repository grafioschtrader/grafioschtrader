import { describe, expect, it } from 'vitest';
import { ChartShapeRelayout } from './chart-shape-relayout';

describe('ChartShapeRelayout', () => {
  it('accepts relayouts that draw, erase or move a shape', () => {
    expect(ChartShapeRelayout.touchesShapes({ shapes: [{ type: 'line' }] })).toBe(true);
    expect(ChartShapeRelayout.touchesShapes({ shapes: [] })).toBe(true);
    expect(ChartShapeRelayout.touchesShapes({ 'shapes[0].x0': '2024-01-02', 'shapes[0].x1': '2024-02-01' })).toBe(true);
  });

  it('rejects zoom, pan, autoscale and empty payloads', () => {
    expect(ChartShapeRelayout.touchesShapes({ 'xaxis.range[0]': '2024-01-01', 'xaxis.range[1]': '2024-06-30' })).toBe(
      false
    );
    expect(ChartShapeRelayout.touchesShapes({ 'xaxis.autorange': true, 'yaxis.autorange': true })).toBe(false);
    expect(ChartShapeRelayout.touchesShapes({ dragmode: 'pan' })).toBe(false);
    expect(ChartShapeRelayout.touchesShapes({})).toBe(false);
    expect(ChartShapeRelayout.touchesShapes(undefined)).toBe(false);
  });

  it('compares shape states by value', () => {
    expect(ChartShapeRelayout.sameShapes([{ type: 'line', x0: 1 }], [{ type: 'line', x0: 1 }])).toBe(true);
    expect(ChartShapeRelayout.sameShapes([{ type: 'line', x0: 1 }], [{ type: 'line', x0: 2 }])).toBe(false);
    expect(ChartShapeRelayout.sameShapes(undefined, [])).toBe(true);
  });
});
