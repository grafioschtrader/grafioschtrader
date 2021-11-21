package grafioschtrader.exportdelete;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * User which export his data will get the role with most power.
 *
 * @author Hugo Graf
 *
 */
public class MySqlExportMyData extends MyDataExportDeleteDefinition {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  public MySqlExportMyData(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, EXPORT_USE);
  }

  public StringBuilder exportDataMyData() throws Exception {

    StringBuilder sqlStatement = new StringBuilder();

    for (ExportDefinition exportDefinition : exportDefinitions) {
      if (exportDefinition.isExport()) {
        sqlStatement.append(getDataAndCreateInsertStatement(exportDefinition));
      }
    }
    sqlStatement.append(createInsertUserRole(user.getIdUser()));
    return sqlStatement;
  }

  private StringBuilder getDataAndCreateInsertStatement(ExportDefinition exportDefinition) throws Exception {
    StringBuilder sqlStatement = new StringBuilder();
    String query = getQuery(exportDefinition);

    Object[] idArray = getParamArrayOfWhereForIdTenant(exportDefinition, query);

    log.debug("Execute: query={}, param={}", query, idArray);
    final List<Map<String, Object>> rows = jdbcTemplate.queryForList(query, idArray);
    if (!rows.isEmpty()) {
      query = query + " LIMIT 1";
      ResultSetMetaData metaData = ((List<ResultSetMetaData>) jdbcTemplate.query(query,
          (resultSet, rowNum) -> resultSet.getMetaData(), idArray)).get(0);
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

  private StringBuilder createInsertUserRole(Integer idUser) {
    return new StringBuilder(String.format(
        "INSERT INTO user_role (id_role, id_user) SELECT id_role, %d FROM role WHERE rolename IN('ROLE_ADMIN', 'ROLE_ALLEDIT', 'ROLE_USER');",
        idUser));
  }

}
