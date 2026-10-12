package grafioschtrader.connector.securitysearch;

import java.util.Optional;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Derives asset class type and financial instrument from the onvista entity type of an instrument. onvista files many
 * ETFs under {@code FUND} (iShares Core MSCI World), so {@code FUND} becomes a fund without asset class type, which the
 * lookup treats as compatible with an ETF.
 */
public final class OnvistaInstrumentTypeMapper {

  private OnvistaInstrumentTypeMapper() {
  }

  /**
   * Derives the instrument type.
   *
   * @param entityType the onvista entity type, e.g. {@code STOCK}, {@code BOND}, {@code ETF} or {@code FUND}; may be
   *                   null
   * @return the derived type, or empty when it is not unambiguous
   */
  public static Optional<InstrumentType> map(String entityType) {
    if (entityType == null) {
      return Optional.empty();
    }
    return switch (entityType) {
    case "STOCK" -> Optional
        .of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    case OnvistaSecuritySearch.TYPE_BOND -> Optional
        .of(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    case "ETF" -> Optional.of(new InstrumentType(null, SpecialInvestmentInstruments.ETF));
    case "FUND" -> Optional.of(new InstrumentType(null, SpecialInvestmentInstruments.MUTUAL_FUND));
    default -> Optional.empty();
    };
  }
}
