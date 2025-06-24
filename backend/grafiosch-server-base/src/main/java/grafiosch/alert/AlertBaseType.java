package grafiosch.alert;

import grafiosch.types.TaskTypeBase;

public enum AlertBaseType implements IAlertType {
  /** A background has produced a timeout */
  ALERT_GET_ZOMBIE_BACKGROUND_JOB((byte) 1);

  private final Byte value;

  private AlertBaseType(Byte value) {
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
    return this.getName();
  }
}
