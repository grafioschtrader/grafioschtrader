package grafiosch.exportdelete;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import grafiosch.entities.Globalparameters;
import grafiosch.entities.MailSettingForward;
import grafiosch.entities.MultilanguageString;
import grafiosch.entities.ProposeChangeEntity;
import grafiosch.entities.ProposeChangeField;
import grafiosch.entities.ProposeRequest;
import grafiosch.entities.ProposeUserTask;
import grafiosch.entities.Role;
import grafiosch.entities.TenantBase;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFMetadata;
import grafiosch.entities.UDFMetadataGeneral;
import grafiosch.entities.User;
import grafiosch.entities.UserEntityChangeCount;
import grafiosch.entities.UserEntityChangeLimit;
import grafiosch.exportdelete.ExportDefinition.TENANT_USER;

/**
 * Helper class for export and delete operations. It provides utility methods to generate SQL queries and manage
 * {@link ExportDefinition}s. This class contains a predefined list of export definitions for various entities.
 */
public abstract class ExportDeleteHelper {

  private static final String SELECT_STR = "SELECT";
  private static final String DELETE_STR = "DELETE";
  public static final String UPDATE_STR = "UPDATE";

  private static String PROPOSE_USER_TASK_SEL = String.format(
      "t.* FROM %s t JOIN %s r ON t.id_propose_request = r.id_propose_request WHERE r.created_by = ?",
      ProposeUserTask.TABNAME, ProposeRequest.TABNAME);
  private static String PROPOSE_CHANGE_ENTITY_SEL = String.format(
      "e.* FROM %s e JOIN %s r ON e.id_propose_request = r.id_propose_request WHERE r.created_by = ?",
      ProposeChangeEntity.TABNAME, ProposeRequest.TABNAME);
  private static String PROPOSE_CHANGE_FIELD_SELDEL = String.format(
      "f.* FROM %s f INNER JOIN %s r ON f.id_propose_request = r.id_propose_request WHERE r.created_by = ?",
      ProposeChangeField.TABNAME, ProposeRequest.TABNAME);

  private static String UDF_METADATA_GENERAL_SELDEL = String.format(
      " umg.* FROM %s umg JOIN %s m ON umg.id_udf_metadata = m.id_udf_metadata WHERE m.id_user = ?",
      UDFMetadataGeneral.TABNAME, UDFMetadata.TABNAME);

  public static ExportDefinition[] exportDefinitions = new ExportDefinition[] {
      // Export -> it runs with the first element
      // Delete it runs backwards
      new ExportDefinition("flyway_schema_history", TENANT_USER.NONE, null, ExportDefinition.EXPORT_USE),
      new ExportDefinition(Globalparameters.TABNAME, TENANT_USER.NONE, null, ExportDefinition.EXPORT_USE),
      new ExportDefinition(MultilanguageString.TABNAME, TENANT_USER.NONE, null, ExportDefinition.EXPORT_USE),
      new ExportDefinition(MultilanguageString.MULTILINGUESTRINGS, TENANT_USER.NONE, null, ExportDefinition.EXPORT_USE),
      new ExportDefinition(TenantBase.TABNAME, TENANT_USER.ID_TENANT, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(User.TABNAME, TENANT_USER.ID_TENANT, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(Role.TABNAME, TENANT_USER.NONE, null, ExportDefinition.EXPORT_USE),
      new ExportDefinition(User.TABNAME_USER_ROLE, TENANT_USER.ID_USER, null, ExportDefinition.DELETE_USE),
      new ExportDefinition(ProposeRequest.TABNAME, TENANT_USER.CREATED_BY, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(ProposeUserTask.TABNAME, TENANT_USER.CREATED_BY, PROPOSE_USER_TASK_SEL,
          ExportDefinition.EXPORT_USE),
      new ExportDefinition(ProposeChangeEntity.TABNAME, TENANT_USER.CREATED_BY, PROPOSE_CHANGE_ENTITY_SEL,
          ExportDefinition.EXPORT_USE),
      new ExportDefinition(ProposeChangeField.TABNAME, TENANT_USER.CREATED_BY, PROPOSE_CHANGE_FIELD_SELDEL,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(UserEntityChangeLimit.TABNAME, TENANT_USER.ID_USER, null, ExportDefinition.DELETE_USE),
      new ExportDefinition(UserEntityChangeCount.TABNAME, TENANT_USER.ID_USER, null, ExportDefinition.DELETE_USE),
      new ExportDefinition(UDFMetadataGeneral.TABNAME, TENANT_USER.ID_USER, UDF_METADATA_GENERAL_SELDEL,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(UDFData.TABNAME, TENANT_USER.ID_USER, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      // TODO Delete all Mails of the user, nothing is exported
      // ...
      new ExportDefinition(MailSettingForward.TABNAME, TENANT_USER.ID_USER, null, ExportDefinition.DELETE_USE), };

  /**
   * Adds additional {@link ExportDefinition}s to the existing list.
   *
   * @param addED An array of {@link ExportDefinition}s to add.
   */
  public static void addExportDefinitions(ExportDefinition[] addED) {
    exportDefinitions = Stream.concat(Arrays.stream(exportDefinitions), Arrays.stream(addED))
        .toArray(ExportDefinition[]::new);
  }

  /**
   * Generates an SQL query (SELECT or DELETE) based on the provided {@link ExportDefinition}.
   *
   * @param exportDefinition The definition to use for query generation.
   * @param exportOrDelete   A flag indicating whether to generate an EXPORT (SELECT) or DELETE query (e.g.,
   *                         {@link ExportDefinition#EXPORT_USE} or {@link ExportDefinition#DELETE_USE}).
   * @param user             The user context for whom the query is being generated (used for tenant/user ID filtering).
   * @return The generated SQL query string.
   */
  static String getQuery(ExportDefinition exportDefinition, int exportOrDelete, User user) {
    String query = exportDefinition.sqlStatement;
    if (exportDefinition.tenantUser == TENANT_USER.ID_USER_DIFFERENT) {
      return query;
    } else if (query == null) {
      switch (exportDefinition.tenantUser) {
      case ID_TENANT:
        query = String.format("FROM %s WHERE id_tenant = %d", exportDefinition.table, user.getIdTenant());
        break;
      case ID_USER:
        query = String.format("FROM %s WHERE id_user = %d", exportDefinition.table, user.getIdUser());
        break;
      case CREATED_BY:
        query = String.format("FROM %s WHERE created_by = %d", exportDefinition.table, user.getIdUser());
        break;
      default:
        query = String.format("FROM %s", exportDefinition.table);
      }

      if ((exportOrDelete & ExportDefinition.EXPORT_USE) == ExportDefinition.EXPORT_USE) {
        query = " * " + query;
      }
    }
    if (query.startsWith(UPDATE_STR + " ")) {
      return query;
    } else {
      return ((exportOrDelete & ExportDefinition.EXPORT_USE) == ExportDefinition.EXPORT_USE ? SELECT_STR : DELETE_STR)
          + " " + query;
    }
  }

  /**
   * Counts the number of '?' placeholders in an SQL query string
   * and creates an array of parameters (User ID or Tenant ID) to substitute them.
   * This is typically used for prepared statements.
   *
   * @param exportDefinition The {@link ExportDefinition} related to the query.
   * @param query The SQL query string containing '?' placeholders.
   * @param user The user context, used to get the appropriate ID (User or Tenant).
   * @return An array of Objects (Integers) containing the IDs to be used as parameters for the query.
   */
  static Object[] getParamArrayOfStatementForIdTenantOrIdUser(ExportDefinition exportDefinition, String query,
      User user) {
    int countIdTenant = 0;
    Matcher matcher = Pattern.compile("=\\s*\\?(\\s|$)").matcher(query);
    while (matcher.find()) {
      countIdTenant++;
    }
    Object[] idTenatArray = new Integer[countIdTenant];
    Arrays.fill(idTenatArray,
        exportDefinition.tenantUser == TENANT_USER.ID_USER || exportDefinition.tenantUser == TENANT_USER.CREATED_BY
            ? user.getIdUser()
            : user.getIdTenant());
    return idTenatArray;
  }

}
