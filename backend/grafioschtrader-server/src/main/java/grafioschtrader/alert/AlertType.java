package grafioschtrader.alert;

import java.util.Arrays;

public enum AlertType {
  // A background has produced a timeout
  ALERT_GET_ZOMBIE_BACKGROUND_JOB(1), 
  // A EOD historical connector may not work anymore
  ALERT_CONNECTOR_EOD_MAY_NOT_WORK_ANYMODE(2);

  private final int value;

  private AlertType(int value) {
    this.value = value;
  }

  public static AlertType getAlertType(int value) {
    return Arrays.stream(AlertType.values()).filter(e -> e.getValue()== value).findFirst().orElse(null);
  }
  
  public int getValue() {
    return this.value;
  }
}
