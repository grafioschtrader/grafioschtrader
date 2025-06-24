package grafioschtrader.alert;

import grafiosch.alert.IAlertType;
import grafiosch.types.TaskTypeBase;

/**
 * Enumeration defining alert types specific to the GrafioschTrader application.
 * This enum implements the IAlertType interface to provide standardized alert
 * type definitions for various system monitoring and notification scenarios.
 * 
 * <h3>Alert Categories</h3>
 * <p>
 * The alerts are primarily focused on data connector health monitoring:
 * </p>
 * <ul>
 *   <li><strong>Connector Health Alerts:</strong> Notifications when data connectors
 *       may be experiencing issues or have become unreliable</li>
 *   <li><strong>Service Availability Alerts:</strong> Warnings about potential
 *       service degradation or connectivity problems</li>
 * </ul>
 * 
 */
public enum AlertGTType implements IAlertType {

  /** Some historical connector may not work anymore */
  ALERT_CONNECTOR_EOD_MAY_NOT_WORK_ANYMORE((byte) 2),
  /** Some intraday connector may not work anymore */
  ALERT_CONNECTOR_INTRADAY_MAY_NOT_WORK_ANYMORE((byte) 3);

  private final Byte value;

  private AlertGTType(Byte value) {
    this.value = value;
  }

  @Override
  public Byte getValue() {
    return this.value;
  }

  @Override
  public Enum<TaskTypeBase>[] getValues() {
    return TaskTypeBase.values();
  }

  @Override
  public String getName() {
    return name();
  }

}
