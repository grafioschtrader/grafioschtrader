package grafioschtrader.types;

import java.util.Arrays;

/**
 * Whether a domestic paying agent withholds the tax on the capital income of the accounts of a portfolio. It decides
 * where the German tax help shows the income of the portfolio: the tax return has to declare the income without
 * domestic withholding, the income with withholding is informational only. A portfolio without this value has not been
 * assigned yet and appears in no block of the tax return.
 */
public enum TaxWithholding {

  /** A domestic paying agent withholds the tax, the bank certificate is authoritative. */
  DOMESTIC_WITHHOLDING((byte) 0),
  /** No domestic withholding, the income has to be declared. */
  NO_DOMESTIC_WITHHOLDING((byte) 1);

  private final Byte value;

  private TaxWithholding(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static TaxWithholding getTaxWithholdingByValue(byte value) {
    return Arrays.stream(TaxWithholding.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }
}
