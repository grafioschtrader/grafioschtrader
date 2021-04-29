package grafioschtrader.dto;

public class ClosedMarginUnits {
  public boolean hasPosition;
  public Double closedUnits;

  public ClosedMarginUnits(boolean hasPosition, Double closedUnits) {
    this.hasPosition = hasPosition;
    this.closedUnits = closedUnits;
  }

}
