package grafioschtrader.exportdelete;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import grafioschtrader.GlobalConstants;

/**
 * Deletes the private data of the main tenant of a user in the application
 * context.
 *
 * @author Hugo Graf
 *
 */
public class MySqlDeleteMyData extends MyDataExportDeleteDefinition {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  public MySqlDeleteMyData(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, DELETE_USE);
  }

  public void deleteMyData() {
    for (int i = exportDefinitions.length - 1; i >= 0; i--) {
      ExportDefinition exportDefinition = exportDefinitions[i];
      if (exportDefinition.isDelete()) {
        deleteSingleEntityData(exportDefinition);
      }
    }

    // When successful delete then change created by of shared entities to system
    // user
    for (int i = exportDefinitions.length - 1; i >= 0; i--) {
      ExportDefinition exportDefinition = exportDefinitions[i];
      if (exportDefinition.isChangeUserId()) {
        String updateQuery = "update " + exportDefinition.table + " set created_by = " + GlobalConstants.SYSTEM_ID_USER
            + " where created_by = " + user.getIdUser();
        jdbcTemplate.update(updateQuery);
      }
    }
  }

  private void deleteSingleEntityData(ExportDefinition exportDefinition) {
    String query = getQuery(exportDefinition);
    Object[] idArray = getParamArrayOfWhereForIdTenant(exportDefinition, query);
    log.info("Execute: query={}, param={}", query, idArray);
    jdbcTemplate.update(query, idArray);
  }

}
