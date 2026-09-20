package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import grafioschtrader.MigrationSections;

/** The averaging-down addition must extend the table rather than replace it. */
class AlgoAverageDownMigrationTest {
  @Test
  void migrationFenceUsesAnAdditiveColumn() throws Exception {
    String name = "algo_alert_state_schedule_delivery_simulation_opening_dashboard_and_tenant_fx_convention.sql";
    String production = MigrationSections.read("src/main/resources/db/migration/V0_37_0__" + name, "average-down");
    assertTrue(production.contains("ADD COLUMN IF NOT EXISTS algo_tranche_targets"));
    assertFalse(production.contains("DROP"));
    assertEquals(java.util.Map.of("t1", 6.0),
        AlgoTrancheTargets.read(AlgoTrancheTargets.write(java.util.Map.of("t1", 3.0)), 2));
  }
}
