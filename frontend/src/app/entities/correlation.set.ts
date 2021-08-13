import {BaseID} from './base.id';
import {Currencypair} from './currencypair';
import {Security} from './security';

export class CorrelationSet implements BaseID {
  idCorrelationSet: number = null;
  name: string = null;
  note: string = null;
  samplingPeriod: SamplingPeriodType | string = null;
  rolling: number;
  securitycurrencyList: (Security | Currencypair)[];
  startDate: Date;
  endDate: Date | string;

  public getId(): number {
    return this.idCorrelationSet;
  }
}

export enum SamplingPeriodType {
  Daily = 0,
  Monthly = 1,
  Annual = 2
}

export class CorrelationResultSet {
  correlationResult: CorrelationResult[];
}

export class CorrelationResult {
  firstAvailableDate: string;
  lastAvailableDate: string;
  correlationInstruments: CorrelationInstrument[];

}

export class CorrelationInstrument {
  idSecuritycurrency: number;
  correlations: number[];
  annualizedReturn: number;
  standardDeviation: number;
  maxPercentageChange: number;
}
