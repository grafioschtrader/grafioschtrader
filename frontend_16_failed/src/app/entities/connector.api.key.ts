import {BaseID} from './base.id';

export class ConnectorApiKey implements BaseID {
  idProvider: string = null;
  apiKey: string = null;
  subscriptionType: SubscriptionType | string = null;

  public getId(): string {
    return this.idProvider;
  }
}

export interface SubscriptionTypeReadableName {
  readableName: string;
  subscriptionTypes: SubscriptionType[];
}

export enum SubscriptionType {
  EOD_HISTORICAL_DATA_ALL_IN_ONE = 11,
  EOD_HISTORICAL_DATA_ALL_WORLD = 12,
  EOD_HISTORICAL_DATA_CALENDAR_DATA_FEED = 13,
  STOCK_DATA_ORG_BASIC = 21,
  STOCK_DATA_ORG_STANDARD_OR_PRO = 22,
  FINNHUB_FREE = 31,
  FINNHUB_BASIC = 32,
  FINNHUB_STANDARD_OR_PROFESSIONAL = 33,
  FINNHUB_ALL_IN_ONE = 34,
  ALPHA_VANTAGE_FREE = 41,
  ALPHA_VANTAGE_PREMIUM = 42,
  CRYPTOCOMPARE_FREE = 51,
  CRYPTOCOMPARE_OTHERS = 52,
  CURRENCY_CONVERTER_FREE = 61,
  CURRENCY_CONVERTER_OTHERS = 62,
  TWELVEDATA_FREE = 71,
  TWELVEDATA_GROW_55 = 72,
  TWELVEDATA_GROW_144 = 73,
  TWELVEDATA_GROW_377 = 74
}
