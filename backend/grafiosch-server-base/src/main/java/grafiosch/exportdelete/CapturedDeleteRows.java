package grafiosch.exportdelete;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import grafiosch.entities.User;

/**
 * Retains deletion targets whose scope is stored in a child that the reverse dependency walk removes first. Each
 * instance belongs to one deletion; definitions remain stateless and know no application entity classes.
 */
public final class CapturedDeleteRows {
  private final JdbcTemplate jdbc;
  private final Map<ExportDefinition, List<Integer>> idsByDefinition = new HashMap<>();

  /**
   * Captures targets before the caller starts deleting any rows.
   *
   * @param jdbc        JDBC connection participating in the caller's transaction
   * @param definitions only the definitions eligible for this deletion scope
   * @param user        tenant/user context used to bind the selection queries
   */
  public CapturedDeleteRows(JdbcTemplate jdbc, ExportDefinition[] definitions, User user) {
    this.jdbc = jdbc;
    for (ExportDefinition definition : definitions) {
      if (definition.isDelete() && definition.deleteIdSelection != null) {
        Object[] arguments = ExportDeleteHelper.getParamArrayOfStatementForIdTenantOrIdUser(definition,
            definition.deleteIdSelection, user);
        idsByDefinition.put(definition, jdbc.queryForList(definition.deleteIdSelection, Integer.class, arguments));
      }
    }
  }

  /**
   * Deletes captured rows when their definition is reached in the ordinary reverse dependency order.
   *
   * @param definition current deletion definition
   * @return true if this definition was handled, including an empty selection; false for ordinary definitions
   */
  public boolean delete(ExportDefinition definition) {
    List<Integer> ids = idsByDefinition.get(definition);
    if (ids == null) {
      return false;
    }
    if (!ids.isEmpty()) {
      jdbc.batchUpdate("DELETE " + definition.sqlStatement, ids.stream().map(id -> new Object[] { id }).toList());
    }
    return true;
  }
}
