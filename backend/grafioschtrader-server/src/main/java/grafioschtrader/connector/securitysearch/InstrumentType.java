package grafioschtrader.connector.securitysearch;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Asset class type and financial instrument derived from the raw instrument type of a data provider. The financial
 * instrument is always set; the asset class type may stay open when the provider names the kind of instrument but not
 * what it invests in, e.g. Yahoo's {@code ETF}.
 *
 * @param categoryType                asset class type, null when not unambiguous
 * @param specialInvestmentInstrument financial instrument, never null
 */
public record InstrumentType(AssetclassType categoryType, SpecialInvestmentInstruments specialInvestmentInstrument) {

  public InstrumentType {
    if (specialInvestmentInstrument == null) {
      throw new IllegalArgumentException("The financial instrument of a derived instrument type is required");
    }
  }
}
