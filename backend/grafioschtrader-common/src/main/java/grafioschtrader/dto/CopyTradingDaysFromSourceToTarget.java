package grafioschtrader.dto;

import grafioschtrader.GlobalConstants;
import jakarta.validation.constraints.Min;

public class CopyTradingDaysFromSourceToTarget {
  public int sourceIdStockexchange;
  public int targetIdStockexchange;

  @Min(value = GlobalConstants.OLDEST_TRADING_YEAR)
  public int returnOrCopyYear;
  public boolean fullCopy;

  public CopyTradingDaysFromSourceToTarget() {
  }

  public CopyTradingDaysFromSourceToTarget(int sourceIdStockexchange, int targetIdStockexchange,
      @Min(2000) int returnOrCopyYear, boolean fullCopy) {
    super();
    this.sourceIdStockexchange = sourceIdStockexchange;
    this.targetIdStockexchange = targetIdStockexchange;
    this.returnOrCopyYear = returnOrCopyYear;
    this.fullCopy = fullCopy;
  }

}
