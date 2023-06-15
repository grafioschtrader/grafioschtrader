import {BaseID} from './base.id';
import {Currencypair} from './currencypair';
import {Security} from './security';
import {Securitycurrency} from './securitycurrency';
import {TenantLimit} from './backend/tenant.limit';

export class CorrelationSet implements BaseID {
  idCorrelationSet: number = null;
  name: string = null;
  note: string = null;
  samplingPeriod: SamplingPeriodType | string = null;
  rolling: number = SamplingPeriodType.DAILY_RETURNS;
  securitycurrencyList: (Security | Currencypair)[];
  dateFrom: Date | string = null;
  dateTo: Date | string = null;
  adjustCurrency: boolean = null;

  public getId(): number {
    return this.idCorrelationSet;
  }
}

export interface CorrelationLimit {
  /**
   * Limit the number of correlation set
   */
  tenantLimit: TenantLimit;
  dailyConfiguration: string;
  monthlyConfiguration: string;
  annualConfiguration: string;
  requiredMinPeriods: number;
}

export enum SamplingPeriodType {
  DAILY_RETURNS = 0,
  MONTHLY_RETURNS = 1,
  ANNUAL_RETURNS = 2
}

export enum TimePeriodType {
  DAILY = 0,
  MONTHLY = 1,
  ANNUAL = 2
}

export interface CorrelationResult {
  firstAvailableDate: string;
  lastAvailableDate: string;
  correlationInstruments: CorrelationInstrument[];
  mmdhList: MinMaxDateHistoryquote[];
}

export interface CorrelationRollingResult {
  securitycurrencyList: Securitycurrency[];
  dates: string[];
  correlation: number[];
}

export interface MinMaxDateHistoryquote {
  idSecuritycurrency: number;
  minDate: string;
  maxDate: string;
}

export interface CorrelationInstrument {
  idSecuritycurrency: number;
  correlations: number[];
}
