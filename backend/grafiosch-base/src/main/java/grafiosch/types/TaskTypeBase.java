package grafiosch.types;

public enum TaskTypeBase implements ITaskType {

  /** Deletes all expired token of the registration process with its created user */
  TOKEN_USER_REGISTRATION_PURGE((byte) 15),

  /** Checks and updates the online/busy status of all configured GTNet servers. */
  GTNET_SERVER_STATUS_CHECK((byte) 20),

  /** Aggregates GTNet exchange log entries from shorter to longer periods. */
  GTNET_EXCHANGE_LOG_AGGREGATION((byte) 22),

  /** Synchronizes GTNetExchange configurations with GTNet peers to update GTNetSupplierDetail entries. */
  GTNET_EXCHANGE_SYNC((byte) 23),

  /** Broadcasts settings changes (maxLimit, acceptRequest, serverState, dailyRequestLimit) to all GTNet peers. */
  GTNET_SETTINGS_BROADCAST((byte) 24),

  /** Delivers pending future-oriented GTNet messages and handles cleanup. Runs every 5 hours and on message send. */
  GTNET_FUTURE_MESSAGE_DELIVERY((byte) 25),

  /** Delivers pending GTNet admin messages to multiple targets. Created when admin sends message via multi-select. */
  GTNET_ADMIN_MESSAGE_DELIVERY((byte) 26),

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
