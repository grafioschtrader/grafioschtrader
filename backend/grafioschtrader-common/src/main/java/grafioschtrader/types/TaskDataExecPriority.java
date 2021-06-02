package grafioschtrader.types;

import java.util.Arrays;

public enum TaskDataExecPriority {
  PRIO_VERY_LOW((byte) 40), PRIO_LOW((byte) 30), PRIO_NORMAL((byte) 20), PRIO_HIGH((byte) 10), PRIO_VERY_HIGH((byte) 5);

  private final Byte value;

  private TaskDataExecPriority(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static TaskDataExecPriority getTaskDataExecPriorityByValue(byte value) {
    return Arrays.stream(TaskDataExecPriority.values()).filter(e -> e.getValue().equals(value)).findFirst()
        .orElse(null);
  }
}
