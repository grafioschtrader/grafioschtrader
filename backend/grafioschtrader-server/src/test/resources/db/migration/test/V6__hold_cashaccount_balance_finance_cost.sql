-- Test-database counterpart of the finance_cost part of the production migration V0_36_10. The test schema comes
-- from the generated V1__schema.sql, a dump of the development database, and the production V0_*.sql migrations
-- never run against grafioschtrader_t - so until nv.bat regenerates V1 from a migrated development database the
-- column is missing here, while the entity and the named queries already reference it. Same role as V3, V4 and
-- V5: repair what the generated dump predates, and go no-op once it no longer does.
--
-- No rebuild is enqueued, unlike in the production migration. grafioschtrader_t is bootstrapped from empty and
-- the hold tables are built by the tests themselves through TransactionJpaRepositoryImpl, so there is no legacy
-- row carrying FINANCE_COST inside fee that would have to be split apart.

ALTER TABLE hold_cashaccount_balance
  ADD COLUMN IF NOT EXISTS finance_cost DOUBLE NOT NULL DEFAULT 0
    COMMENT 'Running FINANCE_COST total, in the currency of the cash account';
