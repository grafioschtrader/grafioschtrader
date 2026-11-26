-- Columns of V0_40_3__performance_benchmark.sql for the test and e2e databases until V1__schema.sql is regenerated
-- by the release procedure. Idempotent, so it stays harmless once V1 already contains them.
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS id_benchmark int(11) DEFAULT NULL
  COMMENT 'Benchmark of the client view and default of its portfolios';
ALTER TABLE portfolio ADD COLUMN IF NOT EXISTS id_benchmark int(11) DEFAULT NULL
  COMMENT 'Benchmark of this portfolio, overrides tenant.id_benchmark';

ALTER TABLE tenant DROP FOREIGN KEY IF EXISTS FK_Tenant_Benchmark;
ALTER TABLE tenant ADD CONSTRAINT FK_Tenant_Benchmark FOREIGN KEY (id_benchmark)
  REFERENCES securitycurrency (id_securitycurrency) ON DELETE SET NULL;
ALTER TABLE portfolio DROP FOREIGN KEY IF EXISTS FK_Portfolio_Benchmark;
ALTER TABLE portfolio ADD CONSTRAINT FK_Portfolio_Benchmark FOREIGN KEY (id_benchmark)
  REFERENCES securitycurrency (id_securitycurrency) ON DELETE SET NULL;
