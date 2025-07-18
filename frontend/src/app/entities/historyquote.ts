import {BaseID} from '../lib/entities/base.id';
import {ProposeTransientTransfer} from '../lib/entities/propose.transient.transfer';
import {Exclude} from 'class-transformer';

export class Historyquote extends ProposeTransientTransfer implements BaseID {
  idHistoryQuote?: number;
  idSecuritycurrency?: number;
  date ? = null;
  close?: number = null;
  volume?: number = null;
  open?: number = null;
  high?: number = null;
  low?: number = null;
  createType?: string | HistoryquoteCreateType;
  createModifyTime: string | Date;

  @Exclude()
  override getId(): number {
    return this.idHistoryQuote;
  }
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
