package grafioschtrader.tax.germany;

import grafioschtrader.entities.Security;
import grafioschtrader.reportviews.germantax.GermanTaxKeys;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.FundTaxClass;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Decides how the German tax help treats an instrument. It reads the special instrument first, so that a bond ETF
 * stays a fund and is no other capital claim. An instrument that no rule covers is unclassified: the report lists its
 * bookings but puts them in no sum.
 */
public final class GermanTaxClassifier {

  /** The kind of an instrument. */
  public enum Kind {
    /** A share, ADR and GDR included when they are created as direct investment in an equity asset class. */
    SHARE,
    /** A bond or convertible bond held directly, taxed as another capital claim. */
    CLAIM,
    /** An investment fund with a fund class. */
    FUND,
    /** Everything else. */
    UNCLASSIFIED
  }

  /**
   * The result of the classification.
   *
   * @param kind          the kind
   * @param fundTaxClass  the fund class of a fund, otherwise null
   * @param reason        for an unclassified instrument the REASON_ name of {@link GermanTaxKeys}, otherwise null
   */
  public record Classification(Kind kind, FundTaxClass fundTaxClass, String reason) {
    public boolean isFund() {
      return kind == Kind.FUND;
    }
  }

  private GermanTaxClassifier() {
  }

  /**
   * Classifies a security.
   *
   * @param security the security with its asset class
   * @return the classification
   */
  public static Classification classify(Security security) {
    final SpecialInvestmentInstruments instrument = security.getAssetClass().getSpecialInvestmentInstrument();
    final AssetclassType category = security.getAssetClass().getCategoryType();
    switch (instrument) {
    case ETF, MUTUAL_FUND, PENSION_FUNDS:
      return security.getFundTaxClass() == null
          ? unclassified(GermanTaxKeys.REASON_FUND_WITHOUT_CLASS)
          : new Classification(Kind.FUND, security.getFundTaxClass(), null);
    case DIRECT_INVESTMENT:
      return classifyDirectInvestment(category);
    case ISSUER_RISK_PRODUCT:
      return unclassified(GermanTaxKeys.REASON_ISSUER_RISK_PRODUCT);
    case CFD, FOREX:
      return unclassified(GermanTaxKeys.REASON_MARGIN_INSTRUMENT);
    default:
      return unclassified(GermanTaxKeys.REASON_OTHER_INSTRUMENT);
    }
  }

  private static Classification classifyDirectInvestment(AssetclassType category) {
    if (category == AssetclassType.EQUITIES) {
      return new Classification(Kind.SHARE, null, null);
    }
    if (category == AssetclassType.FIXED_INCOME || category == AssetclassType.CONVERTIBLE_BOND) {
      return new Classification(Kind.CLAIM, null, null);
    }
    return unclassified(GermanTaxKeys.REASON_OTHER_INSTRUMENT);
  }

  private static Classification unclassified(String reason) {
    return new Classification(Kind.UNCLASSIFIED, null, reason);
  }
}
