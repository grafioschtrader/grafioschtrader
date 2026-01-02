import {GTNetExchangeKindType} from './gtnet';

/**
 * Period types for GTNet exchange log aggregation.
 */
export enum GTNetExchangeLogPeriodType {
  INDIVIDUAL = 0,
  DAILY = 1,
  WEEKLY = 2,
  MONTHLY = 3,
  YEARLY = 4
}

/**
 * Node in the exchange log tree structure.
 */
export interface GTNetExchangeLogNode {
  label: string;
  periodType: GTNetExchangeLogPeriodType;
  periodStart: string;
  entitiesSent: number;
  entitiesUpdated: number;
  entitiesInResponse: number;
  requestCount: number;
  children?: GTNetExchangeLogNode[];
}

/**
 * Tree structure for GTNet exchange log display.
 * Represents log data for one GTNet, containing supplier and consumer statistics.
 */
export interface GTNetExchangeLogTree {
  idGtNet: number;
  domainRemoteName: string;
  supplierTotal: GTNetExchangeLogNode;
  consumerTotal: GTNetExchangeLogNode;
}

/**
 * Tab configuration for the exchange log tab menu.
 * Maps GTNetExchangeKindType to translation keys.
 */
export const EXCHANGE_LOG_TABS: {entityKind: GTNetExchangeKindType; labelKey: string}[] = [
  {entityKind: GTNetExchangeKindType.LAST_PRICE, labelKey: 'LAST_PRICE'},
  {entityKind: GTNetExchangeKindType.HISTORICAL_PRICES, labelKey: 'HISTORICAL_PRICES'}
];
