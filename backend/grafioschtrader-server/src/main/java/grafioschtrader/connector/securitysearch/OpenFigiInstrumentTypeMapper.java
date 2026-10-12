package grafioschtrader.connector.securitysearch;

import java.util.Optional;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Derives the instrument type from OpenFIGI's {@code securityType2}. A share ({@code Common Stock}) and a corporate or
 * government bond ({@code Corp}, {@code Govt}) are unambiguous. An exchange traded product comes as {@code Mutual Fund}
 * - Xetra-Gold, an ETC, as well as an ETF - and stays open like every other type.
 */
public final class OpenFigiInstrumentTypeMapper {

  private OpenFigiInstrumentTypeMapper() {
  }

  /**
   * Derives the instrument type of one OpenFIGI listing.
   *
   * @param securityType2 OpenFIGI's {@code securityType2}, may be null
   * @return the derived type, or empty when it is not unambiguous
   */
  public static Optional<InstrumentType> map(String securityType2) {
    if (securityType2 == null) {
      return Optional.empty();
    }
    return switch (securityType2) {
    case "Common Stock" -> Optional
        .of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    case "Corp", "Govt" -> Optional
        .of(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    default -> Optional.empty();
    };
  }
}
