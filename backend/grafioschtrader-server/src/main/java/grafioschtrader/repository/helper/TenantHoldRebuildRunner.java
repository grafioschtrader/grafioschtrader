package grafioschtrader.repository.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a hold table rebuild for every tenant so that one tenant's data cannot cost every other tenant its rebuild.
 *
 * <p>
 * The rebuild of all tenants used to be a single transaction around a loop, and the per-tenant method was reached by a
 * self-invocation that never passed the transactional proxy. A single corrupt row therefore rolled back the rebuild of
 * every tenant and, because the exception left {@code RebuildHolingAllTenantOrSingleTask.doWork} immediately, the hold
 * tables that would have been rebuilt afterwards were not touched at all. Corrupt data does occur — a cash transfer
 * whose {@code con_id_transaction} points at its own row is not forbidden by any database constraint — so the failure
 * is isolated to the tenant that carries it.
 * </p>
 *
 * <p>
 * The rebuild is still reported as failed: the caller receives one exception naming every tenant that could not be
 * rebuilt, which shows up in the task administration. What changes is that the healthy tenants keep their fresh rows
 * and the failed ones keep the rows they had, because each tenant commits or rolls back on its own.
 * </p>
 */
public final class TenantHoldRebuildRunner {

  private static final Logger log = LoggerFactory.getLogger(TenantHoldRebuildRunner.class);

  private TenantHoldRebuildRunner() {
  }

  /**
   * Rebuilds one hold table tenant by tenant, continuing after a failure.
   *
   * @param idTenants     the tenants to rebuild, in processing order
   * @param rebuildTenant the per-tenant rebuild, which must be invoked through the repository proxy so that its own
   *                      {@code @Transactional} takes effect and a failure rolls back that tenant alone
   * @param table         the hold table being rebuilt, used in the log and in the summarising exception
   * @throws IllegalStateException if at least one tenant could not be rebuilt
   */
  public static void rebuildPerTenant(List<Integer> idTenants, Consumer<Integer> rebuildTenant, String table) {
    List<Integer> failedTenants = new ArrayList<>();
    for (Integer idTenant : idTenants) {
      try {
        rebuildTenant.accept(idTenant);
      } catch (RuntimeException ex) {
        failedTenants.add(idTenant);
        log.error("Rebuild of {} failed for tenant {}, continuing with the remaining tenants", table, idTenant, ex);
      }
    }
    if (!failedTenants.isEmpty()) {
      throw new IllegalStateException(
          "Rebuild of " + table + " failed for tenant(s) " + failedTenants + ", all other tenants were rebuilt");
    }
  }
}
