import { describe, expect, it } from 'vitest';
import {
  performanceReportFilename,
  rememberedReportSettings,
  reportOptionsForScope,
  PerformanceReportOptions
} from './performance-report';

describe('performance report download', () => {
  it('prefers the UTF-8 portfolio name and tolerates malformed headers', () => {
    expect(performanceReportFilename(`attachment; filename="portfolio.pdf"; filename*=UTF-8''Verm%C3%B6gen.pdf`)).toBe(
      'Vermögen.pdf'
    );
    expect(performanceReportFilename(`attachment; filename="portfolio.pdf"; filename*=UTF-8''%broken`)).toBe(
      'portfolio.pdf'
    );
    expect(performanceReportFilename('attachment; filename="../portfolio.pdf"')).toBe('portfolio.pdf');
    expect(performanceReportFilename(null)).toBe('performance.pdf');
  });
  it('remembers presentation without dates, scope, checkbox or the one-off comment', () => {
    const form = {
      preset: 'CUSTOM',
      sections: ['PERFORMANCE_SUMMARY'],
      title: 'Report',
      language: 'de',
      numberFormat: 'de-CH',
      annualYears: 10,
      detailColumns: false,
      comment: 'Do not remember',
      idPortfolio: 12,
      dateFrom: '2025-01-01',
      reportDate: '2025-12-28',
      rememberSettings: true
    };
    expect(rememberedReportSettings(form)).not.toHaveProperty('comment');
    expect(rememberedReportSettings(form)).not.toHaveProperty('idPortfolio');
    expect(rememberedReportSettings(form)).not.toHaveProperty('dateFrom');
    expect(rememberedReportSettings(form)).not.toHaveProperty('reportDate');
    expect(rememberedReportSettings(form)).not.toHaveProperty('rememberSettings');
    expect(rememberedReportSettings(form).sections).toEqual(['PERFORMANCE_SUMMARY']);
  });

  it('limits reporting-date dialogs to server-declared date sections and compatible presets', () => {
    const options: PerformanceReportOptions = {
      sections: ['COVER', 'HOLDINGS', 'ALLOCATION', 'PERFORMANCE_SUMMARY'].map((key) => ({ key, value: key })),
      presets: ['CUSTOM', 'STATEMENT_OF_ASSETS', 'SHORT_REPORT'].map((key) => ({ key, value: key })),
      presetSections: {
        CUSTOM: [],
        STATEMENT_OF_ASSETS: ['COVER', 'HOLDINGS', 'ALLOCATION'],
        SHORT_REPORT: ['PERFORMANCE_SUMMARY']
      },
      dateSections: ['COVER', 'HOLDINGS', 'ALLOCATION'],
      languages: [],
      numberFormats: []
    };
    const statement = reportOptionsForScope(options, true);
    expect(statement.sections.map((s) => s.key)).toEqual(['COVER', 'HOLDINGS', 'ALLOCATION']);
    expect(statement.presets.map((p) => p.key)).toEqual(['CUSTOM', 'STATEMENT_OF_ASSETS']);
    expect(reportOptionsForScope(options, false)).toBe(options);
    expect(options.sections).toHaveLength(4);
  });
});
