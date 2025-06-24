package grafiosch.exportdelete;

public class ExportDefinition {
  public String table;
  public TENANT_USER tenantUser;
  public String sqlStatement;
  public int usage;
  public static final int CHANGE_USER_ID = 0x08;
  /**
   * Shared data of the user to be deleted must be assigned to another user.
   */
  public static final int CHANGE_USER_ID_FOR_CREATED_BY = 0x04;
  public static final int DELETE_USE = 0x02;
  public static final int EXPORT_USE = 0x01;

  /**
   * Constructs a new ExportDefinition.
   *
   * @param table        The name of the database table.
   * @param tenantUser   Specifies how the table relates to a tenant or user.
   * @param sqlStatement A specific SQL statement to use (can be null).
   * @param usage        A bitmask indicating the type of operations.
   */
  public ExportDefinition(String table, TENANT_USER tenantUser, String sqlStatement, int usage) {
    this.table = table;
    this.tenantUser = tenantUser;
    this.sqlStatement = sqlStatement;
    this.usage = usage;
  }

  /**
   * Checks if this definition is intended for an export operation.
   *
   * @return True if the {@link #EXPORT_USE} flag is set, false otherwise.
   */
  public boolean isExport() {
    return (usage & EXPORT_USE) == EXPORT_USE;
  }

  /**
   * Checks if this definition is intended for a delete operation.
   *
   * @return True if the {@link #DELETE_USE} flag is set, false otherwise.
   */
  public boolean isDelete() {
    return (usage & DELETE_USE) == DELETE_USE;
  }

  /**
   * Checks if this definition requires changing the user ID in a 'created_by' field.
   *
   * @return True if the {@link #CHANGE_USER_ID_FOR_CREATED_BY} flag is set, false otherwise.
   */
  public boolean isChangeUserIdForCreatedBy() {
    return (usage & CHANGE_USER_ID_FOR_CREATED_BY) == CHANGE_USER_ID_FOR_CREATED_BY;
  }

  /**
   * Checks if this definition requires changing a user ID.
   *
   * @return True if the {@link #CHANGE_USER_ID} flag is set, false otherwise.
   */
  public boolean isChangeUserId() {
    return (usage & CHANGE_USER_ID) == CHANGE_USER_ID;
  }

  public static enum TENANT_USER {
    /** Use special SQL statement */
    NONE,
    /** Create statement with where clause which uses tenant id for selection */
    ID_TENANT,
    /** Create statement with where clause which uses id_user for selection */
    ID_USER,
    /** Used for shared entities which were created by this user */
    CREATED_BY,
    /** Used for statement where idUser is used but the table field has a different name */
    ID_USER_DIFFERENT
  }

}
