package grafioschtrader.types;

import java.util.Locale;
import java.util.Map;

/**
 * Day-count conventions supported by regular fixed-rate replay coupons.
 *
 * The convention is a term of the bond prospectus and cannot be derived with certainty from the other data of a
 * security. It follows the market and currency of the issue rather than the domicile of the issuer: CHF bonds on SIX
 * accrue on 30E/360 regardless of whether the issuer is Swiss, while EUR, GBP and USD government bonds and most EUR
 * corporates accrue on Actual/Actual ICMA. {@link #defaultForCurrency(String)} therefore offers that market usage as a
 * default, which an explicitly stored convention always overrides. The two conventions differ by at most one or two
 * days of accrued interest, which is immaterial to a simulation.
 */
public enum CouponDayCount {
  ACT_ACT_ICMA, THIRTY_E_360;

  /** Convention used for every currency without its own entry in {@link #CURRENCY_DEFAULTS}. */
  public static final CouponDayCount FALLBACK = ACT_ACT_ICMA;

  /** Currencies whose bond market deviates from {@link #FALLBACK}, keyed by ISO 4217 code. */
  public static final Map<String, CouponDayCount> CURRENCY_DEFAULTS = Map.of("CHF", THIRTY_E_360);

  /**
   * Returns the usual day-count convention of bonds issued in the given currency.
   *
   * @param currency ISO 4217 currency code of the security, case-insensitive
   * @return the market default, or null when no currency is given
   */
  public static CouponDayCount defaultForCurrency(String currency) {
    if (currency == null || currency.isBlank()) {
      return null;
    }
    return CURRENCY_DEFAULTS.getOrDefault(currency.trim().toUpperCase(Locale.ROOT), FALLBACK);
  }
}
