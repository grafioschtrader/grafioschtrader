package grafioschtrader.types;

import java.util.Arrays;

/**
 * Decides which purchases a sale of a security consumes, and with that how the gain of a position is split between
 * realized and unrealized. The total return of a position is the same for every method.
 */
public enum CostBasisMethod {

  /** All purchases form one pool, a sale takes its proportional share of the pooled cost */
  AVERAGE_COST((byte) 0),
  /** A sale consumes the oldest purchases of its security account first */
  FIFO((byte) 1),
  /** A sale consumes the newest purchases of its security account first */
  LIFO((byte) 2);

  private final Byte value;

  private CostBasisMethod(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static CostBasisMethod getCostBasisMethodByValue(byte value) {
    return Arrays.stream(CostBasisMethod.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }

}
