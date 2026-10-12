package grafioschtrader.connector.securitysearch;

import java.util.Optional;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Derives the instrument type from the type of the Deutsche Börse search ({@code Aktie}, {@code ETP}, {@code Anleihe}).
 * An {@code ETP} may be an ETF, an ETC or an ETN - Xetra-Gold is one - so it stays open; another provider of the same
 * listing, such as Yahoo, may name it.
 */
public final class XetraInstrumentTypeMapper {

  private XetraInstrumentTypeMapper() {
  }

  /**
   * Derives the instrument type of one Deutsche Börse listing.
   *
   * @param type the instrument type of the search, may be null
   * @return the derived type, or empty when it is not unambiguous
   */
  public static Optional<InstrumentType> map(String type) {
    if (type == null) {
      return Optional.empty();
    }
    return switch (type) {
    case "Aktie" -> Optional
        .of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    case "Anleihe" -> Optional
        .of(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    default -> Optional.empty();
    };
  }
}
