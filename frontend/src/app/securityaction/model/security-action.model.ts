import { Security } from '../../entities/security';

export type SecurityActionType = 'ISIN_CHANGE' | 'SPIN_OFF';
export type SpinOffTaxTreatmentDe = 'UNDETERMINED' | 'COST_SPLIT' | 'TAXABLE_DISTRIBUTION';
export type SpinOffDecision = 'BOOK' | 'SKIP';
export type SpinOffHistoryBasis = 'UNDETERMINED' | 'UNADJUSTED' | 'ADJUSTED';
export type SpinOffHistoryBasisOrigin = 'REFERENCE' | 'STEP' | 'ADMINISTRATOR';
/** Setting of the history basis in the edit dialog; AUTOMATIC returns the decision to the parent refresh. */
export type SpinOffHistoryBasisSetting = 'AUTOMATIC' | 'UNADJUSTED' | 'ADJUSTED';

export interface SecurityAction {
  idSecurityAction: number | null;
  actionType: SecurityActionType;
  securityOld: Security | null;
  securityNew: Security | null;
  isinOld: string | null;
  isinNew: string | null;
  actionDate: string;
  note: string | null;
  fromFactor: number | null;
  toFactor: number | null;
  costAllocation?: number | null;
  costAllocationSource?: string | null;
  taxTreatmentDe?: SpinOffTaxTreatmentDe | null;
  costAllocationDe?: number | null;
  /** Factor of the split the split connector reports on the action date, which GT drops; written by the server */
  providerSplitFactor?: number | null;
  historyBasis?: SpinOffHistoryBasis | null;
  historyFactor?: number | null;
  historyBasisOrigin?: SpinOffHistoryBasisOrigin | null;
  historyConnector?: string | null;
  historyUrlExtend?: string | null;
  historyBasisApplied?: boolean;
  affectedCount: number;
  appliedCount: number;
  createdBy: number | null;
  creationTime: string | null;
}

export interface SpinOffAllocationQuery {
  idSecurityOld: number;
  idSecurityNew: number;
  actionDate: string;
  fromFactor: number;
  toFactor: number;
}

export interface SpinOffAllocationProposal {
  priceDate: string | null;
  parentClose: number | null;
  childClose: number | null;
  fxChildToParent: number | null;
  proposedAllocation: number | null;
}

/** One split the split connector reports around the action date of a spin-off. */
export interface SpinOffProviderSplit {
  splitDate: string;
  fromFactor: number;
  toFactor: number;
  factor: number;
  /** True when GT drops the split because a spin-off is recorded on its date */
  dropped: boolean;
}

/** Price step of a series on the action date of a spin-off. */
export interface SpinOffPriceStep {
  x: number;
  mAdj: number;
  mRaw: number | null;
}

/** Provider splits and history basis of a spin-off parent around an action date. */
export interface SpinOffProviderSplits {
  splits: SpinOffProviderSplit[];
  splitsUnavailable: boolean;
  providerSplitFactor: number | null;
  spinOffFactor: number | null;
  historySourceIsSplitSource: boolean;
  historyBasis: SpinOffHistoryBasis | null;
  historyFactor: number | null;
  historyBasisOrigin: SpinOffHistoryBasisOrigin | null;
  historyConnector: string | null;
  historyUrlExtend: string | null;
  currentSource: boolean;
  storedStep: SpinOffPriceStep | null;
  deliveredStep: SpinOffPriceStep | null;
}

export interface SecurityActionApplication {
  idSecurityActionApp: number;
  securityAction: SecurityAction;
  idTransactionSell: number;
  idTransactionBuy: number;
  appliedTime: string;
  reversed: boolean;
  bookedAccounts?: number;
  skippedAccounts?: number;
  costAllocation?: number | null;
  /** Securities accounts in which the ISIN change had already been carried out; only set when applying it */
  alreadyPerformedCount?: number;
}

export interface SpinOffAccountDecision {
  idSecurityaccount: number;
  parentUnits: number;
  decision: SpinOffDecision;
}

export interface SpinOffPreviewAccount extends SpinOffAccountDecision {
  accountName: string;
  cashCurrency: string;
  childUnits: number;
  fraction: number;
  fxParentToCash: number;
  fxChildToCash: number;
  saleAmount: number;
  repurchaseAmount: number;
  childAmount: number;
  candidates: SpinOffCandidate[];
}

export interface SpinOffCandidate {
  idTransaction: number;
  transactionTime: string;
  child: boolean;
  type: string;
  units: number | null;
  quotation: number | null;
}

export interface SpinOffPreview {
  actionDate: string;
  childPriceDate: string;
  parentClose: number;
  parentCurrency: string;
  childClose: number;
  childCurrency: string;
  fxChildToParent: number;
  accounts: SpinOffPreviewAccount[];
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
