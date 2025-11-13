/**
 * Base task types that are part of the reusable grafiosch-base framework.
 * These task types are not specific to Grafioschtrader and can be used
 * in any application built on the grafiosch-base framework.
 *
 * Corresponds to backend: grafiosch-base/src/main/java/grafiosch/types/TaskTypeBase.java
 */
export enum TaskTypeBase {
  /** Deletes all expired token of the registration process with its created user */
  TOKEN_USER_REGISTRATION_PURGE = 15,

  /** Moves shared entities from one user to another user by changing field created_by */
  MOVE_CREATED_BY_USER_TO_OTHER_USER = 31
}
