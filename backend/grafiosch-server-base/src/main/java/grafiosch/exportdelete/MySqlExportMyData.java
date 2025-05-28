package grafiosch.exportdelete;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import grafiosch.entities.User;

/**
 * Handles the export of a user's data into SQL INSERT statements. The user whose data is being exported will also be
 * granted roles with extensive permissions (ROLE_ADMIN, ROLE_ALLEDIT, ROLE_USER) in the exported statements.
 * 
 * This procedure allows you to say goodbye to a shared GT instance and start a new personal instance of GT. Please note
 * that the exported data may only be used on an initial GT database.
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
    StringBuilder sqlStatement = new StringBuilder();
    for (ExportDefinition exportDefinition : ExportDeleteHelper.exportDefinitions) {
      if (exportDefinition.isExport()) {
        sqlStatement.append(getDataAndCreateInsertStatement(exportDefinition));
      }
    }
    sqlStatement.append(createInsertUserRole(user.getIdUser()));
    return sqlStatement;
  }

  /**
   * Fetches data based on an {@link ExportDefinition} and creates SQL INSERT statements for that data.
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
      ResultSetMetaData metaData = jdbcTemplate.query(query, (resultSet, rowNum) -> resultSet.getMetaData(), idArray)
          .get(0);
      if (log.isDebugEnabled()) {
        log.debug(exportDefinition.table);
        for (int columnIndex = 1; columnIndex <= metaData.getColumnCount(); columnIndex++) {
          log.debug("Name: {}, Type: {}", metaData.getColumnName(columnIndex), metaData.getColumnType(columnIndex));
        }
      }
      sqlStatement.append(createInsertStatements(exportDefinition, metaData, rows));
    }

    return sqlStatement;
  }

  /**
   * Creates multi-row SQL INSERT statements from fetched data and its metadata. Handles various SQL data types and
   * formats them appropriately for an INSERT statement, including escaping special characters in strings and encoding
   * binary data as hex.
   *
   * @param exportDefinition The {@link ExportDefinition} for the current table.
   * @param metaData         The {@link ResultSetMetaData} for the fetched data.
   * @param rows             A list of rows, where each row is a map of column names to values.
   * @return A {@link StringBuilder} containing the formatted INSERT statements.
   * @throws SQLException if an error occurs while accessing metadata.
   */
  private StringBuilder createInsertStatements(ExportDefinition exportDefinition, ResultSetMetaData metaData,
      final List<Map<String, Object>> rows) throws SQLException {
    StringBuilder sqlStatement = new StringBuilder();

    sqlStatement.append(appendInsertToString(exportDefinition, metaData));

    for (int rowCounter = 0; rowCounter < rows.size(); rowCounter++) {
      Map<String, Object> row = rows.get(rowCounter);
      sqlStatement.append("(");
      for (int columnIndex = 1; columnIndex <= metaData.getColumnCount(); columnIndex++) {
        int columnType = metaData.getColumnType(columnIndex);
        Object value = row.get(metaData.getColumnName(columnIndex));
        if (value != null) {
          switch (columnType) {
          case Types.TIMESTAMP:
          case Types.DATE:
          case Types.TIME:
            sqlStatement.append("'").append(value).append("'");
            break;

          case Types.LONGVARCHAR:
          case Types.VARCHAR:
          case Types.CHAR:
            value = ((String) value).replace("'", "\\'").replaceAll("\n", "\\\\n").replaceAll("\r", "\\\\r");
            sqlStatement.append("'").append(value).append("'");
            break;
          case Types.LONGVARBINARY:
            byte[] bytes = (byte[]) value;
            sqlStatement.append("0x").append(Hex.encodeHexString(bytes));
            break;
          default: // Number
            sqlStatement.append(value);
          }
        } else {
          sqlStatement.append("NULL");
        }

        if (columnIndex < metaData.getColumnCount()) {
          sqlStatement.append(", ");
        }
      }

      if (rowCounter + 1 == rows.size()) {
        sqlStatement.append(")");
      } else if (rowCounter > 0 && rowCounter % 500 == 0) {
        sqlStatement.append(");\n");
        sqlStatement.append(appendInsertToString(exportDefinition, metaData));
      } else {
        sqlStatement.append("),\n");
      }

    }
    sqlStatement.append(";\n");

    return sqlStatement;
  }

  /**
   * Creates the "INSERT INTO `table_name` (`column1`, `column2`, ...) VALUES " part of an SQL INSERT statement.
   *
   * @param exportDefinition The {@link ExportDefinition} containing the table name.
   * @param metaData         The {@link ResultSetMetaData} used to get column names.
   * @return A {@link StringBuilder} with the initial part of the INSERT statement.
   * @throws SQLException if an error occurs while accessing column names from metadata.
   */
  private StringBuilder appendInsertToString(ExportDefinition exportDefinition, ResultSetMetaData metaData)
      throws SQLException {
    StringBuilder sqlStatement = new StringBuilder();
    sqlStatement.append("INSERT INTO `").append(exportDefinition.table).append("`(");
    for (int k = 1; k <= metaData.getColumnCount(); k++) {
      sqlStatement.append("`").append(metaData.getColumnName(k)).append("`, ");
    }
    sqlStatement.deleteCharAt(sqlStatement.length() - 1).deleteCharAt(sqlStatement.length() - 1).append(") VALUES \n");
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
