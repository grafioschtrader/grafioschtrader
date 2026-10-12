package grafioschtrader.connector.securitysearch;

import java.util.Optional;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Derives asset class type and financial instrument from the type label of a Boursorama search hit: {@code Action}
 * share, {@code ETF} without asset class type, {@code BND} bond.
 */
public final class BoursoramaInstrumentTypeMapper {

  private BoursoramaInstrumentTypeMapper() {
  }

  /**
   * Derives the instrument type.
   *
   * @param type the Boursorama type label, may be null
   * @return the derived type, or empty when it is not unambiguous
   */
  public static Optional<InstrumentType> map(String type) {
    if (type == null) {
      return Optional.empty();
    }
    return switch (type) {
    case BoursoramaSecuritySearch.TYPE_SHARE -> Optional
        .of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    case BoursoramaSecuritySearch.TYPE_ETF -> Optional.of(new InstrumentType(null, SpecialInvestmentInstruments.ETF));
    case BoursoramaSecuritySearch.TYPE_BOND -> Optional
        .of(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    default -> Optional.empty();
    };
  }
}
