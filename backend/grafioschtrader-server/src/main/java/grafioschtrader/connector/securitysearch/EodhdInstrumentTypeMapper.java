package grafioschtrader.connector.securitysearch;

import java.util.Locale;
import java.util.Optional;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Derives asset class type and financial instrument from the {@code Type} of an EODHD search entry. Only types with an
 * unambiguous meaning are mapped; an ETF and a fund keep the asset class type open, since EODHD does not say what they
 * hold.
 */
public final class EodhdInstrumentTypeMapper {

  private EodhdInstrumentTypeMapper() {
  }

  /**
   * Derives the instrument type.
   *
   * @param type the EODHD type, e.g. {@code Common Stock}, {@code ETF} or {@code FUND}; may be null
   * @return the derived type, or empty when it is not unambiguous
   */
  public static Optional<InstrumentType> map(String type) {
    if (type == null) {
      return Optional.empty();
    }
    return switch (type.trim().toUpperCase(Locale.ROOT)) {
    case "COMMON STOCK" -> Optional
        .of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    case "ETF" -> Optional.of(new InstrumentType(null, SpecialInvestmentInstruments.ETF));
    case "FUND" -> Optional.of(new InstrumentType(null, SpecialInvestmentInstruments.MUTUAL_FUND));
    case EodhdSecuritySearch.TYPE_INDEX -> Optional
        .of(new InstrumentType(null, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES));
    default -> Optional.empty();
    };
  }
}
