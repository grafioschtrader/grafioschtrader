import {Expose} from 'class-transformer';

export class HistoryquotePeriod {
  @Expose() idHistoryquotePeriod: number = null;
  @Expose() idSecuritycurrency?: number;
  @Expose() fromDate = null;
  @Expose() price: number = null;
  toDate: null;
}
