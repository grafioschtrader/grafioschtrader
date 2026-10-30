import { Security } from '../../entities/security';

export interface SecurityAction {
  idSecurityAction: number | null;
  securityOld: Security | null;
  securityNew: Security | null;
  isinOld: string;
  isinNew: string;
  actionDate: string;
  note: string;
  fromFactor: number;
  toFactor: number;
  affectedCount: number;
  appliedCount: number;
  createdBy: number | null;
  creationTime: string | null;
}

export interface SecurityActionApplication {
  idSecurityActionApp: number;
  securityAction: SecurityAction;
  idTransactionSell: number;
  idTransactionBuy: number;
  appliedTime: string;
  reversed: boolean;
  /** Securities accounts in which the ISIN change had already been carried out; only set when applying it */
  alreadyPerformedCount?: number;
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
