import {BaseID} from '../lib/entities/base.id';
import {ProposeTransientTransfer} from '../lib/entities/propose.transient.transfer';

/**
 * Shared base for end-of-day quote rows — the live {@link Historyquote} and the archived
 * {@link HistoryquoteLegacy}. Holds the price (OHLCV), the trading date, the creation provenance and the owning
 * security/currency-pair reference common to both. Extending {@link ProposeTransientTransfer} lets both participate in
 * the propose-change approval workflow.
 */
export abstract class HistoryquoteBase extends ProposeTransientTransfer implements BaseID {
  idSecuritycurrency?: number;
  date ? = null;
  close?: number = null;
  volume?: number = null;
  open?: number = null;
  high?: number = null;
  low?: number = null;
  createType?: string | HistoryquoteCreateType;

  abstract override getId(): number;
}

export enum HistoryquoteCreateType {
  CONNECTOR_CREATED = 0,
  FILLED_NON_TRADE_DAY = 1,
  MANUAL_IMPORTED = 2,
  FILLED_CLOSED_LINEAR_TRADING_DAY = 3,
  CALCULATED = 4,
  ADD_MODIFIED_USER = 5,
  FILL_GAP_BY_CONNECTOR
}
