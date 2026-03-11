import {Security} from '../../entities/security';

export interface SecurityAction {
  idSecurityAction: number;
  securityOld: Security;
  securityNew: Security;
  isinOld: string;
  isinNew: string;
  actionDate: string;
  note: string;
  fromFactor: number;
  toFactor: number;
  affectedCount: number;
  appliedCount: number;
  createdBy: number;
  creationTime: string;
}

export interface SecurityActionApplication {
  idSecurityActionApp: number;
  securityAction: SecurityAction;
  idTransactionSell: number;
  idTransactionBuy: number;
  appliedTime: string;
  reversed: boolean;
}

export interface SecurityTransfer {
  idSecurityTransfer: number;
  security: Security;
  idSecurityaccountSource: number;
  idSecurityaccountTarget: number;
  transferDate: string;
  units: number;
  quotation: number;
  idTransactionSell: number;
  idTransactionBuy: number;
  note: string;
  creationTime: string;
  reversible: boolean;
}

export interface SecurityActionTreeData {
  systemActions: SecurityAction[];
  appliedByCurrentTenant: { [key: number]: SecurityActionApplication };
  clientTransfers: SecurityTransfer[];
}
