import { Expose } from 'class-transformer';

export class HistoryquotePeriod {
  @Expose() idHistoryquotePeriod: number | null = null;
  @Expose() idSecuritycurrency?: number;
  @Expose() fromDate: string | Date | null = null;
  @Expose() price: number | null = null;
  toDate: null;
}
