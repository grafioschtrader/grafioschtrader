import {IHistoryquoteQuality} from '../../entities/view/ihistoryquote.quality';
import {Expose} from 'class-transformer';

export class HistoryquoteQualityIds {
  @Expose() idConnectorHistory: string;
  @Expose() idStockexchange: number;
  @Expose() categoryType: number;
  @Expose() specialInvestmentInstrument: number;
  @Expose() uniqueKey?: number;
}

export class HistoryquoteQualityGroup extends HistoryquoteQualityIds {
  name: string;
  numberOfSecurities: number;
  activeNowSecurities: number;
  connectorCreated: number;
  manualImported: number;
  filledLinear: number;
  qualityPercentage: number;
  childrendHqg: HistoryquoteQualityGroup[];
}

export class HistoryquoteQualityHead extends HistoryquoteQualityGroup {
  lastUpdate: string;
}

export enum HistoryquoteQualityGrouped {
  STOCKEXCHANGE_GROUPED,
  CONNECTOR_GROUPED
}

export interface IHistoryquoteQualityWithSecurityProp extends IHistoryquoteQuality {
  name: string;
  currency: string;
  activeFromDate: string;
  activeToDate: string;
  idSecurity: number;
  connectorCreated: number;
  filledNoTradeDay: number;
  manualImported: number;
  filledLinear: number;
}

export interface HisotryqouteLinearFilledSummary {
  message: string;
  requiredClosing: number;
  gapsTotalFilled: number;
  createdHistoryquotesStart: number;
  createdHistoryquotesEnd: number;
  createdHistoryquotes: number;
  movedWeekendDays: number;
  removedWeekendDays: number;
  warning: boolean;
}

export interface DeleteHistoryquotesSuccess {
  manualImported: number;
  filledLinear: number;
}
