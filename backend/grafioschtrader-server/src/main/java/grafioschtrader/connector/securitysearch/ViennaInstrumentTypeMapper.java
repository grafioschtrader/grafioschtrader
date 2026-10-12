package grafioschtrader.connector.securitysearch;

import java.util.Optional;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Derives the instrument type from the kind of page Wiener Börse shows for an ISIN: a share ("AKTIE"), a bond
 * ("Anleihe") or a fund with data of OeKB ("Fondsdaten"), whose asset class type stays open.
 */
public final class ViennaInstrumentTypeMapper {

  private ViennaInstrumentTypeMapper() {
  }

  /**
   * Derives the instrument type of one Vienna listing.
   *
   * @param type the kind of page found by {@link ViennaSecuritySearch}, may be null
   * @return the derived type, or empty when it is not unambiguous
   */
  public static Optional<InstrumentType> map(String type) {
    if (type == null) {
      return Optional.empty();
    }
    return switch (type) {
    case ViennaSecuritySearch.TYPE_SHARE -> Optional
        .of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    case ViennaSecuritySearch.TYPE_BOND -> Optional
        .of(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    case ViennaSecuritySearch.TYPE_FUND -> Optional
        .of(new InstrumentType(null, SpecialInvestmentInstruments.MUTUAL_FUND));
    default -> Optional.empty();
    };
  }
}
