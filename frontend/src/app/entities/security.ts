import { Securitycurrency } from './securitycurrency';
import { Assetclass } from './assetclass';
import { Stockexchange } from './stockexchange';
import { BaseID } from '../lib/entities/base.id';
import { SecurityDerivedLink } from './security.derived.link';
import { HistoryquotePeriod } from './historyquote.period';
import { Securitysplit } from './dividend.split';
import { AssetclassType } from '../shared/types/assetclass.type';
import { SpecialInvestmentInstruments } from '../shared/types/special.investment.instruments';
import { DistributionFrequency } from '../shared/types/distribution.frequency';

export class Security extends Securitycurrency implements BaseID {
  currency?: string | null = null;
  isin?: string | null = null;
  tickerSymbol?: string | null = null;
  sVolume?: number | null = null;
  assetClass?: Assetclass | null = null;
  denomination?: number | null = null;
  stockexchange?: Stockexchange | null = null;
  productLink?: string | null = null;
  idTenantPrivate?: number | null = null;
  activeFromDate: string | number | Date | null = null;
  activeToDate: string | number | Date | null = null;
  distributionFrequency: string | null = null;
  issuerCountry?: string | null = null;
  simulationMetadata?: SecuritySimulationMetadata | null = null;
  leverageFactor?: number | null = null;
  /** Backend rule shared with historical simulation order eligibility. */
  simulationTradingExcluded?: boolean;
  idLinkSecuritycurrency?: number | null = null;
  formulaPrices?: string | null = null;
  idConnectorDividend?: string | null = null;
  urlDividendExtend?: string | null = null;
  dividendCurrency: string | null = null;
  retryDividendLoad?: number | null = null;
  idConnectorSplit?: string | null = null;
  urlSplitExtend?: string | null = null;
  retrySplitLoad?: number | null = null;
  dividendEarliestNextCheck: number;

  calculatedPrice: boolean;
  securityDerivedLinks: SecurityDerivedLink[];
  // Use it only for propose changes otherwise it is not set
  splitPropose: Securitysplit[];
  hpPropose: HistoryquotePeriod[];

  public static canHaveSplitConnector(assetClass: Assetclass | null | undefined, hasMarketValue: boolean): boolean {
    return hasMarketValue && assetClass
      ? !(
          (assetClass.categoryType === AssetclassType[AssetclassType.CONVERTIBLE_BOND] ||
            assetClass.categoryType === AssetclassType[AssetclassType.FIXED_INCOME]) &&
          assetClass.specialInvestmentInstrument ===
            SpecialInvestmentInstruments[SpecialInvestmentInstruments.DIRECT_INVESTMENT]
        ) &&
          (assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.ETF] ||
            assetClass.specialInvestmentInstrument ===
              SpecialInvestmentInstruments[SpecialInvestmentInstruments.MUTUAL_FUND] ||
            assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.CFD] ||
            assetClass.specialInvestmentInstrument ===
              SpecialInvestmentInstruments[SpecialInvestmentInstruments.DIRECT_INVESTMENT])
      : false;
  }

  public static canHaveDividendConnector(
    assetClass: Assetclass | null | undefined,
    distributionFrequency: DistributionFrequency | null,
    hasMarketValue: boolean
  ): boolean {
    if (hasMarketValue && assetClass) {
      let canHaveDividend = false;
      if (distributionFrequency !== null && distributionFrequency !== DistributionFrequency.DF_NONE) {
        canHaveDividend =
          assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.ETF] ||
          assetClass.specialInvestmentInstrument ===
            SpecialInvestmentInstruments[SpecialInvestmentInstruments.PENSION_FUNDS] ||
          assetClass.specialInvestmentInstrument ===
            SpecialInvestmentInstruments[SpecialInvestmentInstruments.MUTUAL_FUND] ||
          assetClass.specialInvestmentInstrument ===
            SpecialInvestmentInstruments[SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT];
        switch (AssetclassType[assetClass.categoryType as keyof typeof AssetclassType]) {
          case AssetclassType.EQUITIES:
          case AssetclassType.REAL_ESTATE:
            canHaveDividend =
              canHaveDividend ||
              assetClass.specialInvestmentInstrument ===
                SpecialInvestmentInstruments[SpecialInvestmentInstruments.DIRECT_INVESTMENT];
            break;
          case AssetclassType.COMMODITIES:
            canHaveDividend =
              assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.ETF];
            break;
          case AssetclassType.CREDIT_DERIVATIVE:
            canHaveDividend = false;
            break;
          case AssetclassType.CURRENCY_PAIR:
            canHaveDividend =
              assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.ETF];
            break;
        }
      }
      return canHaveDividend;
    }
    return false;
  }

  public static isBondDirectInvestment(assetClass: Assetclass | null | undefined): boolean {
    if (!assetClass) {
      return false;
    }
    const categoryType =
      typeof assetClass.categoryType === 'number' ? AssetclassType[assetClass.categoryType] : assetClass.categoryType;
    const specialInvestmentInstrument =
      typeof assetClass.specialInvestmentInstrument === 'number'
        ? SpecialInvestmentInstruments[assetClass.specialInvestmentInstrument]
        : assetClass.specialInvestmentInstrument;
    return (
      specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.DIRECT_INVESTMENT] &&
      (categoryType === AssetclassType[AssetclassType.FIXED_INCOME] ||
        categoryType === AssetclassType[AssetclassType.CONVERTIBLE_BOND])
    );
  }

  public static canHaveIssuerCountry(assetClass: Assetclass | null | undefined): boolean {
    if (!assetClass) {
      return false;
    }
    const specialInvestmentInstrument =
      typeof assetClass.specialInvestmentInstrument === 'number'
        ? SpecialInvestmentInstruments[assetClass.specialInvestmentInstrument]
        : assetClass.specialInvestmentInstrument;
    return ![
      SpecialInvestmentInstruments[SpecialInvestmentInstruments.CFD],
      SpecialInvestmentInstruments[SpecialInvestmentInstruments.FOREX],
      SpecialInvestmentInstruments[SpecialInvestmentInstruments.NON_INVESTABLE_INDICES]
    ].includes(specialInvestmentInstrument as string);
  }

  /**
   * Tells whether a distribution frequency is meaningful for the asset class. A non-investable index pays nothing, so
   * the field is fixed to DF_NONE there; the backend enforces the same rule on save.
   *
   * @param assetClass the selected asset class, may be null while none is chosen
   * @returns false only for a non-investable index
   */
  public static canHaveDistributionFrequency(assetClass: Assetclass | null | undefined): boolean {
    if (!assetClass) {
      return true;
    }
    const specialInvestmentInstrument =
      typeof assetClass.specialInvestmentInstrument === 'number'
        ? SpecialInvestmentInstruments[assetClass.specialInvestmentInstrument]
        : assetClass.specialInvestmentInstrument;
    return (
      specialInvestmentInstrument !== SpecialInvestmentInstruments[SpecialInvestmentInstruments.NON_INVESTABLE_INDICES]
    );
  }

  /**
   * Returns the ISO country code carried by the first two characters of an ISIN. The rest of the ISIN does not need
   * to have been entered yet: this helper is used for live form prefill while the user is typing.
   */
  public static issuerCountryFromIsin(isin: string): string | null {
    const match = /^\s*([A-Za-z]{2})/.exec(isin ?? '');
    return match ? match[1].toUpperCase() : null;
  }

  public getNewInstance(): Security {
    return new Security();
  }
}

export interface SecuritySimulationMetadata {
  bondTerms?: SecurityBondTerms | null;
}

export interface SecurityBondTerms {
  couponRate?: number | null;
  couponDayCount?: string | null;
}
