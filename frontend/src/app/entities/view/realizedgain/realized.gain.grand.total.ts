import type { Portfolio } from '../../portfolio';
import type { Security } from '../../security';
import type { CostLot } from '../security.transaction.position';

/**
 * One sale of the realized gain report: a sale of a security or the close of a margin position. Amounts without the
 * suffix MC are in the currency of the security.
 */
export interface RealizedGainPosition {
  idTransaction: number;
  security: Security;
  securityCurrency: string;
  idSecurityaccount: number;
  /** Local ISO date time of the sale. */
  transactionTime: string;
  /** Units as booked in the transaction. */
  units: number;
  /** Absent for the close of a margin position. */
  proceeds?: number;
  /** Absent for the close of a margin position. */
  costBase?: number;
  gainLoss: number;
  gainLossPercentage?: number;
  /** Absent when the rate of the security currency on the day of the sale is missing. */
  gainLossMC?: number;
  exchangeRate?: number;
  /** Name of the cost basis method, absent for the close of a margin position. */
  costBasisMethod?: string;
  /** Only with FIFO or LIFO: the purchases the sale consumed. */
  consumedLots?: CostLot[];
  /** True when the purchases did not cover all units of the sale. */
  costBasisIncomplete?: boolean;
  /** True for the sale of a security account transfer or of an ISIN change. */
  systemBooking: boolean;
}

/** The sales of one calendar year with their sums in the main currency. */
export interface RealizedGainYearGroup {
  year: number;
  yearGainMC: number;
  /** A positive amount. */
  yearLossMC: number;
  yearNetMC: number;
  systemGainMC: number;
  /** A positive amount. */
  systemLossMC: number;
  systemNetMC: number;
  saleCount: number;
  systemBookingCount: number;
  /** True when a sale of the year has no cost for all of its units or no exchange rate. */
  incomplete: boolean;
  positions: RealizedGainPosition[];
}

/** The realized gains of a tenant per year and their totals. */
export interface RealizedGainGrandTotal {
  mainCurrency: string;
  /** Name of the cost basis method, or MIXED when the security accounts calculate by different methods. */
  appliedMethod: string;
  grandGainMC: number;
  grandLossMC: number;
  grandNetMC: number;
  grandSystemNetMC: number;
  /** ISO country code of the tenant; the German tax help needs DE and the main currency EUR. */
  tenantCountry?: string;
  incomplete: boolean;
  /** The newest year first. */
  years: RealizedGainYearGroup[];
  portfolioList: Portfolio[];
}
