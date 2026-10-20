package grafioschtrader.evalex;

/** A typed missing-input or invalid-data result, propagated through the EvalEx function contract. */
public class IndicatorUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private final grafioschtrader.types.IndicatorCellStatus status;

  public IndicatorUnavailableException(grafioschtrader.types.IndicatorCellStatus status) {
    super(status.name());
    this.status = status;
  }

  public grafioschtrader.types.IndicatorCellStatus getStatus() {
    return status;
  }

  /**
   * @param indicator the indicator that could not be computed, for example SMA
   * @param period    the requested period
   * @param available the number of observations actually loaded
   */
  public IndicatorUnavailableException(String indicator, int period, int available) {
    this(grafioschtrader.types.IndicatorCellStatus.NO_HISTORY);
  }

}
