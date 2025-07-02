package grafiosch.types;

public enum TaskTypeBase implements ITaskType {

  /** Deletes all expired token of the registration process with its created user */
  TOKEN_USER_REGISTRATION_PURGE((byte) 15),

  // Task which used oldValueNumber or oldValueString can not created by the admin
  ///////////////////////////////////////////////////////////////////////////////
  /** Moves shared entities from one user to another user by changing field created_by */
  MOVE_CREATED_BY_USER_TO_OTHER_USER((byte) 31);

  private final Byte value;

  private TaskTypeBase(final Byte value) {
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

}
