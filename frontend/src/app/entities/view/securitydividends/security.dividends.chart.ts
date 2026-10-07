import { Assetclass } from '../../assetclass';

/**
 * Income and costs of the dividends report, aggregated by the backend for the charts of the dividends view. All
 * amounts are in the tenant's main currency. Interest means distributions of instruments in the asset class category
 * FIXED_INCOME or CONVERTIBLE_BOND, dividends all other distributions.
 */
export interface SecurityDividendsChart {
  mainCurrency: string;
  /** One entry per year, ascending, without gaps between the first and the last year. */
  incomeYears: IncomeYear[];
  /** Distributions per year and asset class. */
  incomeAssetclasses: IncomeAssetclass[];
  /** The asset classes referenced by incomeAssetclasses, used to build their display names. */
  assetclasses: Assetclass[];
}

/**
 * Income and costs of one calendar year. The month arrays have twelve entries, index 0 is January.
 */
export interface IncomeYear {
  year: number;
  dividendNetMC: number;
  dividendTaxMC: number;
  interestNetMC: number;
  interestTaxMC: number;
  /** Negative when negative interest was charged. */
  cashInterestMC: number;
  /** Usually negative. */
  financeCostCfdMC: number;
  /** Usually negative. */
  financeCostForexMC: number;
  /** Positive amount. */
  feeMC: number;
  /** Net distributions and cash interest plus the (negative) finance costs; fees are not included. */
  netIncomeMC: number;
  /** Net distributions and cash interest per month. */
  netIncomeMonthMC: number[];
  /** Withholding tax on distributions per month. */
  taxMonthMC: number[];
  dividendNetMonthMC: number[];
  dividendTaxMonthMC: number[];
  interestNetMonthMC: number[];
  interestTaxMonthMC: number[];
  cashInterestMonthMC: number[];
}

/**
 * Distributions of one asset class in one calendar year.
 */
export interface IncomeAssetclass {
  year: number;
  idAssetClass: number;
  netMC: number;
  taxMC: number;
}
