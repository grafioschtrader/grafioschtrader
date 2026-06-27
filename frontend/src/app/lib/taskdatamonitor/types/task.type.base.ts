/**
 * Base task types that are part of the reusable grafiosch-base framework.
 * These task types are not specific to Grafioschtrader and can be used
 * in any application built on the grafiosch-base framework.
 *
 * Numbering: library normal tasks occupy 1-29; the application enum (TaskTypeExtended) owns 30-79.
 * System tasks that must not be user-created live in the shared 80+ band, above the maxUserCreateTask
 * threshold (see GitHub issue #205).
 *
 * Corresponds to backend: grafiosch-base/src/main/java/grafiosch/types/TaskTypeBase.java
 */
export enum TaskTypeBase {
  /** Deletes all expired token of the registration process with its created user */
  TOKEN_USER_REGISTRATION_PURGE = 15,

  /** Checks and updates the online/busy status of all configured GTNet servers. */
  GTNET_SERVER_STATUS_CHECK = 20,

  /** Aggregates GTNet exchange log entries from shorter to longer periods. */
  GTNET_EXCHANGE_LOG_AGGREGATION = 22,

  /** Synchronizes GTNetExchange configurations with GTNet peers to update GTNetSupplierDetail entries. */
  GTNET_EXCHANGE_SYNC = 23,

  /** Broadcasts settings changes (maxLimit, acceptRequest, serverState, dailyRequestLimit) to all GTNet peers. */
  GTNET_SETTINGS_BROADCAST = 24,

  /** Delivers pending future-oriented GTNet messages and handles cleanup. */
  GTNET_FUTURE_MESSAGE_DELIVERY = 25,

  /** Delivers pending GTNet admin messages to multiple targets. */
  GTNET_ADMIN_MESSAGE_DELIVERY = 26,

  /** Physically deletes role messages that every corresponding role member has marked as deleted. */
  MAIL_ROLE_MESSAGE_PURGE = 29,

  /** Moves shared entities from one user to another user by changing field created_by (system task, 80+ band) */
  MOVE_CREATED_BY_USER_TO_OTHER_USER = 80
}
