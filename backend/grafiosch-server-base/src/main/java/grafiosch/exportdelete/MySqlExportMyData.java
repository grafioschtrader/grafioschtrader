package grafiosch.exportdelete;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import grafiosch.entities.User;

/**
 * Handles the export of a user's data into SQL INSERT statements. The user whose data is being exported will also be
 * granted roles with extensive permissions (ROLE_ADMIN, ROLE_ALLEDIT, ROLE_USER) in the exported statements.
 *
 * This procedure allows you to say goodbye to a shared instance and start a new personal instance of an application.
 * Please note that the exported data may only be used on an initial database.
 */
public class MySqlExportMyData {

  private JdbcTemplate jdbcTemplate;
  private User user;
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  public MySqlExportMyData(JdbcTemplate jdbcTemplate, User user) {
    this.jdbcTemplate = jdbcTemplate;
    this.user = user;
  }

  /**
   * Exports the user's data as a series of SQL INSERT statements. It iterates through
   * {@link ExportDeleteHelper#exportDefinitions}, and for each definition marked for export
   * ({@link ExportDefinition#isExport()}), it fetches the data and creates corresponding INSERT statements. Finally, it
   * appends INSERT statements to grant the user administrative roles.
   *
   * @return A {@link StringBuilder} containing the SQL INSERT statements for the exported data.
   * @throws Exception if an error occurs during data fetching or statement creation.
   */
  public StringBuilder exportDataMyData() throws Exception {
    return exportDataMyData(Collections.emptyList());
  }

  /**
   * Exports the user's data as a series of SQL INSERT statements, with additional rows contributed by
   * {@link IExportMyDataAddon} implementations. The additional rows are appended after the regular
   * {@link ExportDefinition}-driven export but before the user-role grant, so they stay within the
   * {@code NO_AUTO_VALUE_ON_ZERO} sql_mode wrapper and benefit from all parent rows already inserted.
   *
   * @param additionalExportQueries extra queries whose rows are appended as INSERT statements; may be empty
   * @return A {@link StringBuilder} containing the SQL INSERT statements for the exported data.
   * @throws Exception if an error occurs during data fetching or statement creation.
   */
  public StringBuilder exportDataMyData(List<AdditionalExportQuery> additionalExportQueries) throws Exception {
    StringBuilder sqlStatement = new StringBuilder();
    sqlStatement.append("SET @old_sql_mode = @@SESSION.sql_mode;\n");
    sqlStatement.append("SET SESSION sql_mode = CONCAT_WS(',', @@SESSION.sql_mode, 'NO_AUTO_VALUE_ON_ZERO');\n");
    for (ExportDefinition exportDefinition : ExportDeleteHelper.exportDefinitions) {
      if (exportDefinition.isExport()) {
        sqlStatement.append(getDataAndCreateInsertStatement(exportDefinition));
      }
    }
    for (AdditionalExportQuery additionalExportQuery : additionalExportQueries) {
      sqlStatement.append(
          getDataAndCreateInsertStatementRaw(additionalExportQuery.getTableName(), additionalExportQuery.getSelectSql()));
    }
    sqlStatement.append(createInsertUserRole(user.getIdUser()));
    sqlStatement.append("SET SESSION sql_mode = @old_sql_mode;\n");
    return sqlStatement;
  }

  /**
   * Fetches data based on an {@link ExportDefinition} and creates SQL INSERT statements for that data. Delegates to
   * {@link MySqlInsertStatementGenerator} for the actual SQL generation.
   *
   * @param exportDefinition The definition guiding data fetching and statement creation.
   * @return A {@link StringBuilder} containing INSERT statements for the fetched data.
   * @throws Exception if an error occurs during database query or metadata retrieval.
   */
  private StringBuilder getDataAndCreateInsertStatement(ExportDefinition exportDefinition) throws Exception {
    StringBuilder sqlStatement = new StringBuilder();
    String query = ExportDeleteHelper.getQuery(exportDefinition, ExportDefinition.EXPORT_USE, user);

    Object[] idArray = ExportDeleteHelper.getParamArrayOfStatementForIdTenantOrIdUser(exportDefinition, query, user);

    log.debug("Execute: query={}, param={}", query, idArray);
    final List<Map<String, Object>> rows = jdbcTemplate.queryForList(query, idArray);
    if (!rows.isEmpty()) {
      query = query + " LIMIT 1";
      java.sql.ResultSetMetaData metaData = jdbcTemplate
          .query(query, (resultSet, _) -> resultSet.getMetaData(), idArray).get(0);
      if (log.isDebugEnabled()) {
        log.debug(exportDefinition.table);
        for (int columnIndex = 1; columnIndex <= metaData.getColumnCount(); columnIndex++) {
          log.debug("Name: {}, Type: {}", metaData.getColumnName(columnIndex), metaData.getColumnType(columnIndex));
        }
      }
      sqlStatement.append(MySqlInsertStatementGenerator.createInsertStatements(exportDefinition.table, metaData, rows));
    }

    return sqlStatement;
  }

  /**
   * Runs a ready-to-use {@code SELECT} (no bind parameters) and turns its rows into INSERT statements for the given
   * table. Unlike {@link #getDataAndCreateInsertStatement(ExportDefinition)} this bypasses
   * {@link ExportDeleteHelper#getQuery} / parameter binding, because the SQL is already complete with all identifiers
   * inlined. Used for rows contributed by {@link IExportMyDataAddon}.
   *
   * @param tableName the target table the rows belong to (INSERT target)
   * @param sql       the complete {@code SELECT ... FROM ...} statement to execute
   * @return a {@link StringBuilder} with the INSERT statements, or empty if the query returned no rows
   * @throws Exception if an error occurs during the database query or metadata retrieval
   */
  private StringBuilder getDataAndCreateInsertStatementRaw(String tableName, String sql) throws Exception {
    StringBuilder sqlStatement = new StringBuilder();
    log.debug("Execute additional export query: table={}, query={}", tableName, sql);
    final List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
    if (!rows.isEmpty()) {
      java.sql.ResultSetMetaData metaData = jdbcTemplate
          .query(sql + " LIMIT 1", (resultSet, _) -> resultSet.getMetaData()).get(0);
      sqlStatement.append(MySqlInsertStatementGenerator.createInsertStatements(tableName, metaData, rows));
    }
    return sqlStatement;
  }

  /**
   * Creates an SQL INSERT statement to grant a user specific roles (ROLE_ADMIN, ROLE_ALLEDIT, ROLE_USER).
   *
   * @param idUser The ID of the user to whom the roles will be assigned.
   * @return A {@link StringBuilder} containing the SQL INSERT statement for user roles.
   */
  private StringBuilder createInsertUserRole(Integer idUser) {
    return new StringBuilder(String.format(
        "INSERT INTO user_role (id_role, id_user) SELECT id_role, %d FROM role WHERE rolename IN('ROLE_ADMIN', 'ROLE_ALLEDIT', 'ROLE_USER');",
        idUser));
  }

}
