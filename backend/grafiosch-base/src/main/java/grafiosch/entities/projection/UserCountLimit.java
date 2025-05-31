package grafiosch.entities.projection;

/**
 * Return of the SQL query about the CUD transactions made for a specific information class, such as “MailSendbox”.
 */
public interface UserCountLimit {
  /**
   * Return of the number of CUD (create, update, delete) transactions made by a user for a specific information class.
   */
  Integer getCudTrans();

  /**
   * Return of a possible changed limit pro for this day. Can be zero if there is no default in the DB.
   */
  Integer getDayLimit();
}
