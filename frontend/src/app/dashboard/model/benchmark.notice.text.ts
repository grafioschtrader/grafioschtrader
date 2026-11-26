/** A condition of a benchmark comparison decided on the server; the code is an NLS key, the parameters its placeholders. */
export interface DashboardBenchmarkNotice {
  code: string;
  /** Named placeholder values; dates are ISO strings, a parameter named leg is an NLS key itself. */
  parameters: Record<string, string>;
}

/**
 * Translates the notices of a benchmark comparison for a dashboard card. Shared by the desktop and the mobile
 * application, which each supply their own translation and date formatting.
 *
 * @param notices Notices of the card, absent without a benchmark
 * @param translate Translation of a key with named parameters
 * @param formatDate Formatting of an ISO date for the reader
 * @returns one text per notice
 */
export function benchmarkNoticeTexts(
  notices: DashboardBenchmarkNotice[] | null | undefined,
  translate: (key: string, parameters?: Record<string, string>) => string,
  formatDate: (isoDate: string) => string
): string[] {
  return (notices ?? []).map((notice) => {
    const parameters: Record<string, string> = {};
    Object.entries(notice.parameters ?? {}).forEach(([name, value]) => {
      if (name === 'leg') {
        parameters[name] = translate(value);
      } else if (name.toLowerCase().endsWith('date')) {
        parameters[name] = formatDate(value);
      } else {
        parameters[name] = value;
      }
    });
    return translate(notice.code, parameters);
  });
}
