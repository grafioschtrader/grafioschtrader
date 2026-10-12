package grafioschtrader.connector.yahoo;

import java.util.Optional;

import grafioschtrader.connector.securitysearch.InstrumentType;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Derives the instrument type from Yahoo's {@code instrumentType} (chart) or {@code quoteType} (search). Yahoo tells an
 * ETF from a share but not what an ETF invests in, so an ETF keeps its asset class type open. Every other Yahoo type
 * (mutual fund, index, currency, future, crypto) stays open, because its GT counterpart is not unambiguous.
 */
public final class YahooInstrumentTypeMapper {

  private YahooInstrumentTypeMapper() {
  }

  /**
   * Derives the instrument type of one Yahoo listing.
   *
   * @param yahooType Yahoo's instrument or quote type, e.g. {@code EQUITY} or {@code ETF}; may be null
   * @return the derived type, or empty when it is not unambiguous
   */
  public static Optional<InstrumentType> map(String yahooType) {
    if (yahooType == null) {
      return Optional.empty();
    }
    return switch (yahooType) {
    case "EQUITY" -> Optional
        .of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    case "ETF" -> Optional.of(new InstrumentType(null, SpecialInvestmentInstruments.ETF));
    default -> Optional.empty();
    };
  }
}
