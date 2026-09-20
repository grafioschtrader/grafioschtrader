package grafioschtrader.repository;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import grafiosch.exportdelete.ExportDefinition;
import grafiosch.exportdelete.ExportDefinition.TENANT_USER;
import grafioschtrader.exportdelete.MyDataExportDeleteDefinition;

/** Removes tenant-owned application data in the same dependency order used by personal-data deletion. */
@Repository
public class SimulationCleanupRepository {
  @Autowired
  private JdbcTemplate jdbc;

  /** Deletes only the selected simulation's private data; shared strategy and user records are excluded. */
  public void deleteTenantData(Integer idTenant) {
    // Pending work must be removed while its referenced accounts and transactions can still be selected.
    for (String[] mapping : new String[][] { { "Tenant", "tenant", "id_tenant" },
        { "Portfolio", "portfolio", "id_portfolio" },
        { "Securityaccount", "securitycashaccount", "id_securitycash_account" },
        { "Cashaccount", "securitycashaccount", "id_securitycash_account" },
        { "Transaction", "transaction", "id_transaction" }, { "Watchlist", "watchlist", "id_watchlist" } }) {
      jdbc.update("DELETE FROM task_data_change WHERE entity = ? AND id_entity IN (SELECT " + mapping[2] + " FROM "
          + mapping[1] + " WHERE id_tenant = ?)", mapping[0], idTenant);
    }
    for (String table : Set.of("hold_securityaccount_security", "hold_cashaccount_balance", "hold_cashaccount_deposit",
        "algo_alert_state", "algo_alert_evaluation_state")) {
      jdbc.update("DELETE FROM " + table + " WHERE id_tenant = ?", idTenant);
    }
    ExportDefinition[] definitions = MyDataExportDeleteDefinition.exportDefinitions;
    for (int i = definitions.length - 1; i >= 0; i--) {
      ExportDefinition definition = definitions[i];
      if (!definition.isDelete())
        continue;
      boolean tenantScoped = definition.tenantUser == TENANT_USER.ID_TENANT || definition.tenantUser == TENANT_USER.NONE
          && definition.sqlStatement != null && definition.sqlStatement.contains("id_tenant = ?");
      if (!tenantScoped)
        continue;
      String sql = definition.sqlStatement;
      if (sql == null)
        sql = "FROM " + definition.table + " WHERE id_tenant = ?";
      if (!sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("UPDATE "))
        sql = "DELETE " + sql;
      Object[] arguments = new Object[(int) sql.chars().filter(c -> c == '?').count()];
      Arrays.fill(arguments, idTenant);
      jdbc.update(sql, arguments);
    }
  }
}
