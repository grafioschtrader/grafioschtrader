package grafioschtrader.alert;

import grafiosch.alert.IAlertType;
import grafiosch.types.TaskTypeBase;

public enum AlertGTType implements IAlertType {
 
  // Some historical connector may not work anymore
  ALERT_CONNECTOR_EOD_MAY_NOT_WORK_ANYMORE((byte) 2),
  // Some intraday connector may not work anymore
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
