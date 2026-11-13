package grafioschtrader.types;

import java.util.Arrays;

/**
 * The class of an investment fund for the German Investmentsteuergesetz. It decides the partial exemption (§ 20
 * InvStG) of the distributions, the advance lump sum and the gains from selling the fund units of a private investor.
 * The class follows from the investment conditions of the fund, not from its current equity share.
 */
public enum FundTaxClass {

  /** Equity fund, at least 51 % of the value is invested in equities. Partial exemption 30 %. */
  EQUITY_FUND((byte) 0, 0.30),
  /** Mixed fund, at least 25 % of the value is invested in equities. Partial exemption 15 %. */
  MIXED_FUND((byte) 1, 0.15),
  /** Real estate fund that invests mainly in Germany, at least 51 % real estate. Partial exemption 60 %. */
  DOMESTIC_REAL_ESTATE_FUND((byte) 2, 0.60),
  /** Real estate fund that invests mainly in real estate abroad. Partial exemption 80 %. */
  FOREIGN_REAL_ESTATE_FUND((byte) 3, 0.80),
  /** Any other fund. No partial exemption. */
  OTHER_FUND((byte) 4, 0.0);

  private final Byte value;
  private final double partialExemption;

  private FundTaxClass(final Byte value, final double partialExemption) {
    this.value = value;
    this.partialExemption = partialExemption;
  }

  public Byte getValue() {
    return this.value;
  }

  /** The share of the income that is exempt from tax for a private investor, 0.30 for 30 %. */
  public double getPartialExemption() {
    return partialExemption;
  }

  public static FundTaxClass getFundTaxClassByValue(byte value) {
    return Arrays.stream(FundTaxClass.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }
}
