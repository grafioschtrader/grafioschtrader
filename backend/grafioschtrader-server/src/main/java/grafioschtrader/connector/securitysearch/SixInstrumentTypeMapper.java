package grafioschtrader.connector.securitysearch;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Derives asset class type and financial instrument from the raw instrument types of SIX. The mapping is deliberately
 * explicit and conservative: a combination it does not know leaves the values open, so the user chooses the asset class
 * instead of receiving a guess. An ETF whose asset class SIX names in no known way keeps only its financial instrument.
 *
 * <p>
 * The inputs are the SIX product line of the search ({@code DB} and {@code FB} bonds of domestic and foreign issuers,
 * {@code ET} exchange traded products, otherwise
 * shares) and the raw attributes of the master data: {@code securityType} of bonds and shares, {@code general.secType}
 * and {@code general.assetClass} of exchange traded products.
 * </p>
 */
public final class SixInstrumentTypeMapper {

  /** Registered, bearer and participation shares. */
  private static final Set<String> SHARE_SECURITY_TYPES = Set.of("RS", "BS", "PS");
  private static final String BOND_SECURITY_TYPE = "BO";
  private static final String ETF_SEC_TYPE = "EF";

  private SixInstrumentTypeMapper() {
  }

  /**
   * Derives the instrument type of one SIX listing.
   *
   * @param productLine the SIX product line, e.g. {@code DB}, {@code ET} or {@code BC}; may be null
   * @param attributes  raw attributes of the master data; may be empty when the master data could not be fetched
   * @return the derived type, or empty when it is not unambiguous
   */
  public static Optional<InstrumentType> map(String productLine, Map<String, String> attributes) {
    if (productLine == null) {
      return Optional.empty();
    }
    return switch (productLine) {
    case "DB", "FB" -> mapBond(attributes.get("securityType"));
    case "ET" -> mapExchangeTraded(attributes.get("general.secType"), attributes.get("general.assetClass"));
    default -> attributes.get("securityType") != null && SHARE_SECURITY_TYPES.contains(attributes.get("securityType"))
        ? Optional.of(new InstrumentType(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT))
        : Optional.empty();
    };
  }

  private static Optional<InstrumentType> mapBond(String securityType) {
    return securityType == null || BOND_SECURITY_TYPE.equals(securityType)
        ? Optional.of(new InstrumentType(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT))
        : Optional.empty();
  }

  private static Optional<InstrumentType> mapExchangeTraded(String secType, String assetClass) {
    if (!ETF_SEC_TYPE.equals(secType)) {
      return Optional.empty();
    }
    AssetclassType categoryType = assetClass == null ? null : mapFundAssetClass(assetClass.toLowerCase(Locale.ROOT));
    return Optional.of(new InstrumentType(categoryType, SpecialInvestmentInstruments.ETF));
  }

  /** Maps the leading word of the SIX fund asset class, e.g. "Equity Strategy" or "Bond Developed Markets". */
  private static AssetclassType mapFundAssetClass(String assetClass) {
    if (assetClass.startsWith("equit")) {
      return AssetclassType.EQUITIES;
    } else if (assetClass.startsWith("bond") || assetClass.startsWith("fixed income")) {
      return AssetclassType.FIXED_INCOME;
    } else if (assetClass.startsWith("commodit")) {
      return AssetclassType.COMMODITIES;
    } else if (assetClass.startsWith("money market")) {
      return AssetclassType.MONEY_MARKET;
    } else if (assetClass.startsWith("real estate")) {
      return AssetclassType.REAL_ESTATE;
    }
    return null;
  }
}
