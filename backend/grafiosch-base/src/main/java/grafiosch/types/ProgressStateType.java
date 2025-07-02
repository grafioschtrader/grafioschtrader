package grafiosch.types;

import java.util.Arrays;

/**
 * Enumeration of possible progress states for background task execution.
 * 
 * <p>Represents the lifecycle states of a task from initial creation
 * through completion or failure, including special handling states
 * for interrupted and zombie processes.</p>
 */
public enum ProgressStateType {
  /** It is waiting to be processed */
  PROG_WAITING((byte) 0),
  
  /** Was processed */
  PROG_PROCESSED((byte) 1),

  /** The processing failed */
  PROG_FAILED((byte) 2),
  
  /** Task cannot be found */
  PROG_TASK_NOT_FOUND((byte) 3),
  
  /** Task is started but finished */
  PROG_RUNNING((byte) 4),

  /** Task was interrupted */
  PROG_INTERRUPTED((byte) 5),
  
  /** Task was interrupted */
  PROG_TIMEOUT((byte) 6),
  
  /** Task has a timeout but could not be stopped */
  PROG_ZOMBIE((byte) 7),
  
  /** At startup changed the state of Zombie to a cleaned state */
  PROG_ZOMBIE_CLEANED((byte) 8);

  private final Byte value;

  private ProgressStateType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static ProgressStateType getProgressStateTypeByValue(byte value) {
    return Arrays.stream(ProgressStateType.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }

}
