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
  fromDate: Date = null;
  toDate: Date | string = null;

  public getId(): number {
    return this.idCorrelationSet;
  }
}

export interface CorrelationLimit {
  tenantLimit: TenantLimit;
  dailyConfiguration: string;
  monthlyConfiguration: string;
  annualConfiguration: string;
}

export enum SamplingPeriodType {
  DAILY_RETURNS = 0,
  MONTHLY_RETURNS = 1,
  ANNUAL_RETURNS = 2
}

export class CorrelationResultSet {
  correlationResult: CorrelationResult[];
}

export class CorrelationResult {
  firstAvailableDate: string;
  lastAvailableDate: string;
  correlationInstruments: CorrelationInstrument[];
}

export class CorrelationRollingResult {
  securitycurrencyList: Securitycurrency[];
  dates: string[];
  correlation: number[];
}

export class CorrelationInstrument {
  idSecuritycurrency: number;
  correlations: number[];
  annualizedReturn: number;
  standardDeviation: number;
  maxPercentageChange: number;
}
