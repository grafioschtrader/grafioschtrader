package grafioschtrader.connector.securitysearch;

import java.util.Optional;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Derives asset class type and financial instrument from the Consorsbank instrument type {@code TOOL_SECURITY_TYPE}.
 * An ETF keeps no asset class type, since Consorsbank files equity and bond ETFs alike under {@code ETF}.
 */
public final class ConsorsbankInstrumentTypeMapper {

  private ConsorsbankInstrumentTypeMapper() {
  }

  /**
   * Derives the instrument type.
   *
   * @param toolSecurityType the Consorsbank type, e.g. {@code STO}, {@code BON}, {@code ETF} or {@code FUN}; may be
   *                         null
   * @return the derived type, or empty when it is not unambiguous
   */
  public static Optional<InstrumentType> map(String toolSecurityType) {
    if (toolSecurityType == null) {
      return Optional.empty();
    }
    return switch (toolSecurityType) {
    case "STO" -> Optional
        .of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    case ConsorsbankSecuritySearch.TYPE_BOND -> Optional
        .of(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    case "ETF" -> Optional.of(new InstrumentType(null, SpecialInvestmentInstruments.ETF));
    case "FUN" -> Optional.of(new InstrumentType(null, SpecialInvestmentInstruments.MUTUAL_FUND));
    default -> Optional.empty();
    };
  }
}
