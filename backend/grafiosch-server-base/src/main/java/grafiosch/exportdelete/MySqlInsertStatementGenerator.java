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

/**
 * Utility class for generating SQL INSERT and DELETE statements from database table data. Used for exporting database
 * tables as self-contained SQL that can be re-imported to restore state.
 *
 * <p>Handles all SQL data types (timestamps, strings with escaping, binary as hex, numerics, nulls) and batches INSERT
 * statements in groups of 500 rows for efficiency.</p>
 */
public class MySqlInsertStatementGenerator {

  private static final Logger log = LoggerFactory.getLogger(MySqlInsertStatementGenerator.class);
  private static final int ROWS_PER_INSERT = 500;

  /**
   * Generates SQL INSERT statements for all rows in the specified table.
   *
   * @param jdbcTemplate the JDBC template for querying the database
   * @param tableName    the name of the table to export
   * @return a StringBuilder containing the INSERT statements, or empty if the table has no rows
   */
  public static StringBuilder generateInsertStatements(JdbcTemplate jdbcTemplate, String tableName) {
    StringBuilder sqlStatement = new StringBuilder();
    String query = "SELECT * FROM " + tableName;
    final List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
    if (!rows.isEmpty()) {
      ResultSetMetaData metaData = jdbcTemplate
          .query(query + " LIMIT 1", (resultSet, rowNum) -> resultSet.getMetaData()).get(0);
      try {
        sqlStatement.append(createInsertStatements(tableName, metaData, rows));
      } catch (SQLException e) {
        log.error("Error generating INSERT statements for table {}", tableName, e);
        throw new RuntimeException("Failed to generate INSERT statements for table " + tableName, e);
      }
    }
    return sqlStatement;
  }

  /**
   * Generates a SQL DELETE statement that removes all rows from the specified table.
   *
   * @param tableName the name of the table
   * @return a StringBuilder containing the DELETE statement
   */
  public static StringBuilder generateDeleteStatement(String tableName) {
    return new StringBuilder("DELETE FROM `").append(tableName).append("`;\n");
  }

  /**
   * Creates multi-row SQL INSERT statements from fetched data and its metadata. Handles various SQL data types and
   * formats them appropriately, including escaping special characters in strings and encoding binary data as hex.
   */
  static StringBuilder createInsertStatements(String tableName, ResultSetMetaData metaData,
      final List<Map<String, Object>> rows) throws SQLException {
    StringBuilder sqlStatement = new StringBuilder();
    sqlStatement.append(appendInsertToString(tableName, metaData));

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
          case Types.BLOB:
          case Types.BINARY:
          case Types.VARBINARY:
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
      } else if (rowCounter > 0 && rowCounter % ROWS_PER_INSERT == 0) {
        sqlStatement.append(");\n");
        sqlStatement.append(appendInsertToString(tableName, metaData));
      } else {
        sqlStatement.append("),\n");
      }
    }
    sqlStatement.append(";\n");
    return sqlStatement;
  }

  /**
   * Generates SQL INSERT statements for a table with a self-referencing foreign key. Rows where the self-referencing
   * column is NULL are inserted first, followed by rows that reference other rows in the same table. This avoids FK
   * violations without disabling constraint checks.
   *
   * @param jdbcTemplate     the JDBC template for querying the database
   * @param tableName        the name of the table to export
   * @param selfRefColumn    the self-referencing FK column name (e.g., "reply_to")
   * @return a StringBuilder containing the ordered INSERT statements
   */
  public static StringBuilder generateInsertStatementsWithSelfRef(JdbcTemplate jdbcTemplate, String tableName,
      String selfRefColumn) {
    StringBuilder sqlStatement = new StringBuilder();
    String baseQuery = "SELECT * FROM " + tableName;
    String queryNoRef = baseQuery + " WHERE `" + selfRefColumn + "` IS NULL";
    String queryWithRef = baseQuery + " WHERE `" + selfRefColumn + "` IS NOT NULL";

    ResultSetMetaData metaData = null;
    List<Map<String, Object>> rowsNoRef = jdbcTemplate.queryForList(queryNoRef);
    List<Map<String, Object>> rowsWithRef = jdbcTemplate.queryForList(queryWithRef);

    if (rowsNoRef.isEmpty() && rowsWithRef.isEmpty()) {
      return sqlStatement;
    }
    metaData = jdbcTemplate.query(baseQuery + " LIMIT 1", (resultSet, rowNum) -> resultSet.getMetaData()).get(0);

    try {
      if (!rowsNoRef.isEmpty()) {
        sqlStatement.append(createInsertStatements(tableName, metaData, rowsNoRef));
      }
      if (!rowsWithRef.isEmpty()) {
        sqlStatement.append(createInsertStatements(tableName, metaData, rowsWithRef));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to generate INSERT statements for table " + tableName, e);
    }
    return sqlStatement;
  }

  /**
   * Creates the "INSERT INTO `table_name` (`col1`, `col2`, ...) VALUES " prefix of an SQL INSERT statement.
   */
  private static StringBuilder appendInsertToString(String tableName, ResultSetMetaData metaData) throws SQLException {
    StringBuilder sqlStatement = new StringBuilder();
    sqlStatement.append("INSERT INTO `").append(tableName).append("`(");
    for (int k = 1; k <= metaData.getColumnCount(); k++) {
      sqlStatement.append("`").append(metaData.getColumnName(k)).append("`, ");
    }
    sqlStatement.deleteCharAt(sqlStatement.length() - 1).deleteCharAt(sqlStatement.length() - 1).append(") VALUES \n");
    return sqlStatement;
  }

}
