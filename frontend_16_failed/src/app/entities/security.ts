import {Securitycurrency} from './securitycurrency';
import {Assetclass} from './assetclass';
import {Stockexchange} from './stockexchange';
import {BaseID} from './base.id';
import {SecurityDerivedLink} from './security.derived.link';
import {HistoryquotePeriod} from './historyquote.period';
import {Securitysplit} from './dividend.split';
import {AssetclassType} from '../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../shared/types/special.investment.instruments';
import {DistributionFrequency} from '../shared/types/distribution.frequency';

export class Security extends Securitycurrency implements BaseID {
  currency?: string = null;
  isin?: string = null;
  tickerSymbol?: string = null;
  sVolume?: number = null;
  name: string = null;
  assetClass?: Assetclass = null;
  denomination?: number = null;
  stockexchange?: Stockexchange = null;
  productLink?: string = null;
  idTenantPrivate?: number = null;
  activeFromDate = null;
  activeToDate = null;
  distributionFrequency: string = null;
  leverageFactor?: number = null;
  idLinkSecuritycurrency?: number = null;
  formulaPrices?: string = null;
  idConnectorDividend?: string = null;
  urlDividendExtend?: string = null;
  dividendCurrency: string = null;
  retryDividendLoad?: number = null;
  idConnectorSplit?: string = null;
  urlSplitExtend?: string = null;
  retrySplitLoad?: number = null;
  dividendEarliestNextCheck: number;

  calculatedPrice: boolean;
  securityDerivedLinks: SecurityDerivedLink[];
  // Use it only for propose changes otherwise it is not set
  splitPropose: Securitysplit[];
  hpPropose: HistoryquotePeriod[];

  public static canHaveSplitConnector(assetClass: Assetclass, hasMarketValue: boolean): boolean {
    return hasMarketValue && assetClass ? !((assetClass.categoryType === AssetclassType[AssetclassType.CONVERTIBLE_BOND]
          || assetClass.categoryType === AssetclassType[AssetclassType.FIXED_INCOME])
        && assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.DIRECT_INVESTMENT])
      && (assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.ETF]
        || assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.MUTUAL_FUND]
        || assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.CFD]
        || assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.DIRECT_INVESTMENT]) : false;

  }

  public static canHaveDividendConnector(assetClass: Assetclass, distributionFrequency: DistributionFrequency,
                                         hasMarketValue: boolean): boolean {
    if (hasMarketValue && assetClass) {
      let canHaveDividend = false;
      if (distributionFrequency !== null && distributionFrequency !== DistributionFrequency.DF_NONE) {
        canHaveDividend = assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.ETF]
          || assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.PENSION_FUNDS]
          || assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.MUTUAL_FUND]
          || assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT];
        switch (AssetclassType[assetClass.categoryType]) {
          case AssetclassType.EQUITIES:
          case AssetclassType.REAL_ESTATE:
            canHaveDividend = canHaveDividend
              || assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.DIRECT_INVESTMENT];
            break;
          case AssetclassType.COMMODITIES:
            canHaveDividend = assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.ETF];
            break;
          case AssetclassType.CREDIT_DERIVATIVE:
          case AssetclassType.CURRENCY_PAIR:
            canHaveDividend = false;
            break;
        }
      }
      return canHaveDividend;
    }
    return false;
  }


  public getNewInstance(): Security {
    return new Security();
  }

}
