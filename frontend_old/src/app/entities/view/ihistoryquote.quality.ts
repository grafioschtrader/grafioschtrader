export interface IHistoryquoteQuality {
  minDate: string;
  missingStart: number;
  missingEnd: number;
  maxDate: string;
  totalMissing: number;
  expectedTotal: number;
  qualityPercentage: number;
  toManyAsCalendar: number;
  quoteSaturday: number;
  quoteSunday: number;
  manualImported: number;
  connectorCreated: number;
  filledLinear: number;
  calculated: number;
  userModified: number;
}
