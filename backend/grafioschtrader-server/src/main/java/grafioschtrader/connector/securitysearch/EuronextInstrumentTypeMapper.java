package grafioschtrader.connector.securitysearch;

import java.util.Optional;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Derives asset class type and financial instrument from the type of a Euronext listing, which
 * {@link EuronextSecuritySearch} reports as {@code Type/Sub type} of the general factsheet block, e.g. {@code Bonds/Oat},
 * {@code Stock/Ordinary Shares} or {@code ETP/Etf}. An ETF keeps the asset class type open; an ETC, ETN or any other
 * type stays open altogether.
 */
public final class EuronextInstrumentTypeMapper {

  private EuronextInstrumentTypeMapper() {
  }

  /**
   * Derives the instrument type.
   *
   * @param type the Euronext type with sub type, may be null
   * @return the derived type, or empty when it is not unambiguous
   */
  public static Optional<InstrumentType> map(String type) {
    if (type == null) {
      return Optional.empty();
    }
    if (type.startsWith(EuronextSecuritySearch.TYPE_STOCK)) {
      return Optional.of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    }
    if (type.startsWith(EuronextSecuritySearch.TYPE_BOND)) {
      return Optional
          .of(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    }
    if (type.equalsIgnoreCase(EuronextSecuritySearch.TYPE_ETP + "/Etf")) {
      return Optional.of(new InstrumentType(null, SpecialInvestmentInstruments.ETF));
    }
    return Optional.empty();
  }
}
