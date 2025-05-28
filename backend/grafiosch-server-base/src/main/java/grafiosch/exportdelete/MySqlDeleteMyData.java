package grafiosch.exportdelete;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import grafiosch.BaseConstants;
import grafiosch.entities.User;

/**
 * Deletes the private data of the main tenant of a user in the application context. This class iterates through
 * {@link ExportDefinition}s to perform delete operations and optionally update 'created_by' fields to a system user.
 */
public class MySqlDeleteMyData {

  private JdbcTemplate jdbcTemplate;
  private User user;
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /**
   * Constructs a MySqlDeleteMyData instance.
   *
   * @param jdbcTemplate The Spring JDBC template.
   * @param user         The user whose data will be deleted.
   */
  public MySqlDeleteMyData(JdbcTemplate jdbcTemplate, User user) {
    this.jdbcTemplate = jdbcTemplate;
    this.user = user;
  }

  /**
   * Executes the data deletion process. It iterates through the {@link ExportDeleteHelper#exportDefinitions} array in
   * reverse order. For each definition, if it's marked for deletion ({@link ExportDefinition#isDelete()}) or for
   * changing user ID ({@link ExportDefinition#isChangeUserId()}), it prepares and executes an update/delete query.
   * Afterwards, it performs a second pass to change 'created_by' fields for definitions marked with
   * {@link ExportDefinition#isChangeUserIdForCreatedBy()}.
   */
  public void deleteMyData() {
    for (int i = ExportDeleteHelper.exportDefinitions.length - 1; i >= 0; i--) {
      ExportDefinition exportDefinition = ExportDeleteHelper.exportDefinitions[i];
      if (exportDefinition.isDelete()) {
        prepareAndExecuteUpdateQuery(exportDefinition);
      } else if (exportDefinition.isChangeUserId()) {
        prepareAndExecuteUpdateQuery(exportDefinition);
      }
    }

    // When successful delete then change created by of shared entities to system user
    for (int i = ExportDeleteHelper.exportDefinitions.length - 1; i >= 0; i--) {
      ExportDefinition exportDefinition = ExportDeleteHelper.exportDefinitions[i];
      if (exportDefinition.isChangeUserIdForCreatedBy()) {
        String updateQuery = "update " + exportDefinition.table + " set created_by = " + BaseConstants.SYSTEM_ID_USER
            + " where created_by = " + user.getIdUser();
        jdbcTemplate.update(updateQuery);
      }
    }
  }

  /**
   * Prepares and executes an update (DELETE or UPDATE) query based on an {@link ExportDefinition}.
   *
   * @param exportDefinition The definition guiding the query generation and execution.
   */
  private void prepareAndExecuteUpdateQuery(ExportDefinition exportDefinition) {
    String query = ExportDeleteHelper.getQuery(exportDefinition, ExportDefinition.DELETE_USE, user);
    Object[] idArray = ExportDeleteHelper.getParamArrayOfStatementForIdTenantOrIdUser(exportDefinition, query, user);
    log.info("Execute: query={}, param={}", query, idArray);
    jdbcTemplate.update(query, idArray);
  }

}
