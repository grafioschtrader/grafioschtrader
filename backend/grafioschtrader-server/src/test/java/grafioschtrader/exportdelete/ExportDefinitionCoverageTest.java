package grafioschtrader.exportdelete;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import grafiosch.exportdelete.ExportDefinition;
import grafiosch.exportdelete.ExportDeleteHelper;
import grafioschtrader.rest.GTIntegrationTestContext;

/**
 * Guards the obligation that every table of the schema appears in an {@link ExportDefinition} array, either the library
 * one in {@link ExportDeleteHelper} or the application one in {@link MyDataExportDeleteDefinition}. A table in no array
 * is missing from "export my data" and survives the deletion of the account; with a non-cascading foreign key to
 * {@code user} or {@code tenant} it even makes the deletion fail.
 *
 * <p>
 * A table deliberately left out of both arrays is listed in {@link #OMITTED_TABLES} or matched by
 * {@link #OMITTED_PREFIXES}, together with the reason. That list is the written decision the {@code new-entity} skill
 * asks for. The arrays are read after {@code GTStartUp} has merged them, the tables from {@code information_schema} of
 * the test database.
 */
@GTIntegrationTestContext
class ExportDefinitionCoverageTest {

  private static final Map<String, String> OMITTED_PREFIXES = Map.of("hold_",
      "holdings are rebuilt from the transactions on import and cascade with their tenant");

  private static final Map<String, String> OMITTED_TABLES = Map.ofEntries(
      entry("algo_alert_state", "derived crossing state, cascades with its strategy"),
      entry("connector_apikey", "API keys of the instance, administered by the administrator"),
      entry("ecb_exchange_rates", "exchange rates loaded by the system"),
      entry("gt_net", "GTNet peer configuration of the instance"),
      entry("gt_net_config", "GTNet peer configuration of the instance"),
      entry("gt_net_config_entity", "GTNet peer configuration of the instance"),
      entry("gt_net_entity", "GTNet peer configuration of the instance"),
      entry("gt_net_exchange_log", "GTNet traffic log of the instance"),
      entry("gt_net_historyquote", "price data received over GTNet"),
      entry("gt_net_instrument", "instrument pool shared over GTNet"),
      entry("gt_net_instrument_currencypair", "instrument pool shared over GTNet"),
      entry("gt_net_instrument_security", "instrument pool shared over GTNet"),
      entry("gt_net_lastprice", "price data received over GTNet"),
      entry("gt_net_maintenance_window", "GTNet maintenance announcements of the peers"),
      entry("gt_net_message", "GTNet messages between the instances"),
      entry("gt_net_message_answer", "GTNet message answer rules of the instance"),
      entry("gt_net_message_attempt", "GTNet delivery attempts between the instances"),
      entry("gt_net_message_param", "GTNet messages between the instances"),
      entry("gt_net_security_imp_gap", "references a GTNet peer, which is not exported; cascades with its position"),
      entry("gt_net_supplier_detail", "GTNet supplier statistics of the instance"),
      entry("gt_net_supplier_detail_hist", "GTNet supplier statistics of the instance"),
      entry("gt_net_supplier_detail_last", "GTNet supplier statistics of the instance"),
      entry("historyquote_quality", "derived quality statistics of the price data"),
      entry("historyquote_update_log", "system log of the price updates per stock exchange"),
      entry("mail_entity", "duplicate guard of system messages, refers to no user"),
      entry("mail_send_recv_read_del", "read and hide markers of a user, cascade with the user"),
      entry("security_fundamental", "derived data rebuilt by the weekly fundamental data load"),
      entry("tenant_access", "grants to accounts of other users, cascades with user and tenant"),
      entry("verificationtoken", "one-time registration token, cascades with its user"));

  private static final String TABLES = """
      SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'""";

  // Foreign keys to a user or tenant that do not let the deletion of the referenced row cascade.
  private static final String RESTRICTING_KEYS = """
      SELECT DISTINCT k.TABLE_NAME, k.COLUMN_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS r
      JOIN information_schema.KEY_COLUMN_USAGE k ON k.CONSTRAINT_SCHEMA = r.CONSTRAINT_SCHEMA
      AND k.CONSTRAINT_NAME = r.CONSTRAINT_NAME AND k.TABLE_NAME = r.TABLE_NAME
      WHERE r.CONSTRAINT_SCHEMA = DATABASE() AND r.REFERENCED_TABLE_NAME IN ('user', 'tenant')
      AND r.DELETE_RULE NOT IN ('CASCADE', 'SET NULL')""";

  private record RestrictingKey(String table, String column) {
    @Override
    public String toString() {
      return table + "." + column;
    }
  }

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  @DisplayName("Every table is in an export definition array or omitted with a reason")
  void everyTableIsCoveredOrOmitted() {
    Set<String> uncovered = new TreeSet<>(schemaTables());
    uncovered.removeAll(definedTables(ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE));
    uncovered.removeIf(ExportDefinitionCoverageTest::isOmitted);
    assertThat(uncovered).as("""
        Tables in no ExportDefinition array: add them to MyDataExportDeleteDefinition or ExportDeleteHelper, or list \
        them with a reason in OMITTED_TABLES (see the new-entity skill)""").isEmpty();
  }

  /**
   * A non-cascading foreign key to {@code user} or {@code tenant} aborts the deletion of the account unless a
   * definition resolves it first. An owner column ({@code id_user}, {@code id_tenant}) needs a {@code DELETE_USE}
   * definition of its table, {@code created_by} one with {@code CHANGE_USER_ID_FOR_CREATED_BY}. Any other column points
   * at a user who does not own the row and needs a {@code DELETE_USE} statement that names both the table and the
   * column, either deleting the row or clearing the reference.
   */
  @Test
  @DisplayName("Every non-cascading key to user or tenant is resolved before the account is deleted")
  void restrictingKeysAreResolved() {
    List<RestrictingKey> unresolved = jdbc
        .query(RESTRICTING_KEYS, (rs, _) -> new RestrictingKey(rs.getString(1), rs.getString(2))).stream()
        .filter(key -> !isResolved(key)).sorted(Comparator.comparing(RestrictingKey::toString)).toList();
    assertThat(unresolved).as("""
        Non-cascading foreign keys to user/tenant that no ExportDefinition resolves; deleting the account fails on \
        them""").isEmpty();
  }

  @Test
  @DisplayName("Every table named by an export definition exists")
  void definitionsNameExistingTables() {
    Set<String> unknown = new TreeSet<>(definedTables(ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE));
    unknown.removeAll(schemaTables());
    assertThat(unknown).as("ExportDefinition tables not present in the schema").isEmpty();
  }

  @Test
  @DisplayName("Every omitted table exists and is in no export definition array")
  void omittedTablesAreGenuine() {
    Set<String> defined = definedTables(ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE);
    assertThat(schemaTables()).as("omitted tables missing from the schema").containsAll(OMITTED_TABLES.keySet());
    assertThat(defined).as("tables both omitted and defined").doesNotContainAnyElementsOf(OMITTED_TABLES.keySet());
  }

  private List<String> schemaTables() {
    return jdbc.queryForList(TABLES, String.class);
  }

  private static Set<String> definedTables(int usage) {
    return Arrays.stream(ExportDeleteHelper.exportDefinitions).filter(definition -> (definition.usage & usage) != 0)
        .map(definition -> definition.table).collect(Collectors.toSet());
  }

  private static boolean isResolved(RestrictingKey key) {
    return switch (key.column()) {
    case "id_user", "id_tenant" -> definedTables(ExportDefinition.DELETE_USE).contains(key.table());
    case "created_by" -> definedTables(ExportDefinition.CHANGE_USER_ID_FOR_CREATED_BY).contains(key.table());
    default -> Arrays.stream(ExportDeleteHelper.exportDefinitions)
        .filter(definition -> (definition.usage & ExportDefinition.DELETE_USE) != 0 && definition.sqlStatement != null)
        .anyMatch(definition -> definition.sqlStatement.contains(key.table())
            && definition.sqlStatement.matches("(?s).*\\b" + key.column() + "\\s*=\\s*\\?.*"));
    };
  }

  private static boolean isOmitted(String table) {
    return OMITTED_TABLES.containsKey(table) || OMITTED_PREFIXES.keySet().stream().anyMatch(table::startsWith);
  }
}
