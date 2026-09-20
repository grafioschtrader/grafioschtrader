package grafioschtrader.types;

/** Defines the immutable opening ledger of a simulation environment. */
public enum SimulationInitializationMode {
  COPY_PORTFOLIO, MANUAL_CASH, LIQUIDATE_TO_CASH;

  // Dynamic form metadata derives the enum's input type from this backing field.
  private final String value = name();

  public String getValue() {
    return value;
  }
}
