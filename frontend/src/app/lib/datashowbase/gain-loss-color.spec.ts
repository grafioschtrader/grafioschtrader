import { describe, expect, it } from 'vitest';
import { DataType } from '../dynamic-form/models/data.type';
import { GainLossColor } from './gain-loss-color';
import { ShowRecordConfigBase } from './show.record.config.base';

describe('GainLossColor', () => {
  it('treats only displayed values above zero as positive', () => {
    for (const text of ['1.00', "1'234.50", '0.01', '12 %', 5]) {
      expect(GainLossColor.isPositiveText(text)).toBe(true);
    }
    for (const text of ['0.00', '-0.00', '-3.50', '−3', '', '  ', null, undefined, 'n/a', 0]) {
      expect(GainLossColor.isPositiveText(text)).toBe(false);
    }
  });

  it('colors green only a flagged column with a non-negative, positive value', () => {
    const flagged = ShowRecordConfigBase.createColumnConfig(
      DataType.Numeric,
      'ytdChangePercentage',
      'YTD',
      true,
      true,
      {
        gainLossColor: true
      }
    );
    const plain = ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'units', 'HOLDING', true, true, {
      templateName: 'greenRed'
    });
    expect(GainLossColor.isGain(flagged, '2.50', false)).toBe(true);
    expect(GainLossColor.isGain(flagged, '0.00', false)).toBe(false);
    expect(GainLossColor.isGain(flagged, '2.50', true)).toBe(false);
    expect(GainLossColor.isGain(plain, '2.50', false)).toBe(false);
  });

  it('gives a flagged column without template the sign coloring template', () => {
    const flagged = ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'gain', 'GAIN', true, true, {
      gainLossColor: true
    });
    const ownTemplate = ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'gain', 'GAIN', true, true, {
      gainLossColor: true,
      templateName: 'owner'
    });
    expect(flagged.templateName).toBe('greenRed');
    expect(ownTemplate.templateName).toBe('owner');
  });
});
