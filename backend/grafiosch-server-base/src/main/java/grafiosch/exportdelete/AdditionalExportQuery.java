package grafiosch.exportdelete;

/**
 * Describes a ready-to-run {@code SELECT} whose result rows are appended to the personal-data export
 * ({@code gt_data.sql}) as additional {@code INSERT} statements.
 *
 * <p>
 * This is the bridge that lets application-specific {@link IExportMyDataAddon} implementations contribute extra rows
 * which cannot be expressed through the static {@link ExportDefinition} mechanism — for example, rows whose selection
 * depends on a runtime decision (a registry lookup, a feature flag) rather than on a fixed SQL predicate.
 * </p>
 *
 * <p>
 * The {@link #selectSql} must be a complete statement starting with {@code SELECT ... FROM ...} with all identifiers
 * inlined and <b>no bind parameters</b> ({@code ?} placeholders). It is executed verbatim and its rows are turned into
 * {@code INSERT INTO <tableName> ...} statements, so its projection must match the target table's columns (typically
 * {@code <alias>.*}).
 * </p>
 */
public class AdditionalExportQuery {

  private final String tableName;
  private final String selectSql;

  /**
   * Creates an additional export query.
   *
   * @param tableName the target table the selected rows belong to; used as the {@code INSERT INTO} target
   * @param selectSql the complete {@code SELECT ... FROM ...} statement (no bind parameters) producing rows whose
   *                  columns match {@code tableName}
   */
  public AdditionalExportQuery(String tableName, String selectSql) {
    this.tableName = tableName;
    this.selectSql = selectSql;
  }

  public String getTableName() {
    return tableName;
  }

  public String getSelectSql() {
    return selectSql;
  }
}
