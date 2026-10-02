import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { PerformanceWindowDef } from '../service/holding.service';

/** Remembered presentation only; dates and comment are deliberately outside this contract. */
export interface PerformanceReportSettings {
  preset: string;
  sections: string[];
  title?: string;
  recipient?: string;
  sender?: string;
  additionalNotes?: string;
  language: string;
  numberFormat: string;
  annualYears: number;
  detailColumns: boolean;
}

/** Generation has no persistence side effect. */
export type PerformanceReportRequest = PerformanceReportSettings & PerformanceReportScope & { comment?: string };

export type PerformanceReportScope = Partial<PerformanceWindowDef> & { reportDate?: string };

export interface PerformanceReportOptions {
  presets: ValueKeyHtmlSelectOptions[];
  presetSections: Record<string, string[]>;
  sections: ValueKeyHtmlSelectOptions[];
  languages: ValueKeyHtmlSelectOptions[];
  numberFormats: ValueKeyHtmlSelectOptions[];
  dateSections: string[];
}

/** Prefer RFC 5987 names while accepting an older quoted filename. Strip any directory portion. */
export function performanceReportFilename(disposition: string | null): string {
  const extended = /filename\*=UTF-8''([^;]+)/i.exec(disposition || '');
  const quoted = /filename="([^"]+)"/i.exec(disposition || '');
  let name = quoted?.[1] || 'performance.pdf';
  if (extended) {
    try {
      name = decodeURIComponent(extended[1]);
    } catch {
      /* Use the ASCII fallback for a malformed header. */
    }
  }
  return (
    name
      .split(/[\\/]/)
      .pop()
      .replace(/[\x00-\x1f]/g, '_') || 'performance.pdf'
  );
}

/** An allow-list prevents a one-off comment or scope from leaking into remembered tenant settings. */
export function rememberedReportSettings(value: PerformanceReportSettings): PerformanceReportSettings {
  const {
    preset,
    sections,
    title,
    recipient,
    sender,
    additionalNotes,
    language,
    numberFormat,
    annualYears,
    detailColumns
  } = value;
  return {
    preset,
    sections,
    title,
    recipient,
    sender,
    additionalNotes,
    language,
    numberFormat,
    annualYears,
    detailColumns
  };
}

/** Reporting-date dialogs expose only the date sections declared by the server. */
export function reportOptionsForScope(
  options: PerformanceReportOptions,
  reportingDate: boolean
): PerformanceReportOptions {
  if (!reportingDate) {
    return options;
  }
  const allowed = new Set(options.dateSections);
  return {
    ...options,
    sections: options.sections.filter((s) => allowed.has(String(s.key))),
    presets: options.presets.filter(
      (p) => p.key === 'CUSTOM' || options.presetSections[String(p.key)]?.every((s) => allowed.has(s))
    )
  };
}
