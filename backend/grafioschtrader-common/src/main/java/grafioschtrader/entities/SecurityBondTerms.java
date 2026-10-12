package grafioschtrader.entities;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import grafiosch.common.DynamicFormField;
import grafiosch.dynamic.model.DynamicFormPropertyHelps;
import grafioschtrader.types.CouponDayCount;
import jakarta.validation.constraints.DecimalMin;

/** Regular fixed-rate coupon assumptions persisted inside {@link Security#simulationMetadata}. */
public class SecurityBondTerms implements Serializable {

  private static final long serialVersionUID = 1L;

  /** A number directly followed by a percent sign anywhere in the name, e.g. "voestalpine3.75%GreenN.24-29/1". */
  private static final Pattern COUPON_PERCENT_IN_NAME = Pattern.compile("(?<![\\d.,])(\\d{1,4}(?:[.,]\\d+)?)\\s*%");

  /**
   * A number at the start of the name followed by a blank, e.g. "2.875 CFFL 10-30". The blank keeps an issuer name like
   * "3M" from being read as a coupon.
   */
  private static final Pattern COUPON_PREFIX_OF_NAME = Pattern.compile("^\\s*(\\d{1,4}(?:[.,]\\d+)?)(?=\\s|%|$)");

  @DynamicFormField(uiOrder = "1.1", integerLimit = 4, fractionLimit = 8)
  @DecimalMin("0")
  private Double couponRate;

  /**
   * Day-count convention of the accrued interest. Optional: when it is null, the simulation uses
   * {@link CouponDayCount#defaultForCurrency(String)} with the currency of the security, and the edit dialog proposes
   * the same default.
   */
  @DynamicFormField(uiOrder = "1.2", helps = DynamicFormPropertyHelps.SELECT_OPTIONS)
  private CouponDayCount couponDayCount;

  /**
   * Reads the annual coupon rate from the name of a bond or convertible bond. A number marked with a percent sign wins
   * wherever it stands, e.g. "voestalpine3.75%GreenN.24-29/1" or "0% Sonova Holding AG 19-29". Without a percent
   * sign only a number at the start of the name counts, e.g. "2.875 CFFL 10-30", because an unmarked number further on
   * is usually a part of the term, e.g. "Royal Bank 0.20 21-31". The frontend counterpart is
   * {@code extractCouponRateFromSecurityName} of the bond terms dialog, keep both in step.
   *
   * @param name the security name, may be null
   * @return the coupon rate in percent, e.g. 3.75, or null when the name carries none
   */
  public static Double couponRateFromName(String name) {
    if (name == null) {
      return null;
    }
    Matcher matcher = COUPON_PERCENT_IN_NAME.matcher(name);
    if (!matcher.find()) {
      matcher = COUPON_PREFIX_OF_NAME.matcher(name);
      if (!matcher.find()) {
        return null;
      }
    }
    return Double.parseDouble(matcher.group(1).replace(',', '.'));
  }

  public Double getCouponRate() {
    return couponRate;
  }

  public void setCouponRate(Double couponRate) {
    this.couponRate = couponRate;
  }

  public CouponDayCount getCouponDayCount() {
    return couponDayCount;
  }

  public void setCouponDayCount(CouponDayCount couponDayCount) {
    this.couponDayCount = couponDayCount;
  }
}
