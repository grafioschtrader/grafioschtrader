package grafioschtrader.entities;

import java.io.Serializable;

import grafiosch.common.DynamicFormField;
import grafiosch.dynamic.model.DynamicFormPropertyHelps;
import grafioschtrader.types.CouponDayCount;
import jakarta.validation.constraints.DecimalMin;

/** Regular fixed-rate coupon assumptions persisted inside {@link Security#simulationMetadata}. */
public class SecurityBondTerms implements Serializable {

  private static final long serialVersionUID = 1L;

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
