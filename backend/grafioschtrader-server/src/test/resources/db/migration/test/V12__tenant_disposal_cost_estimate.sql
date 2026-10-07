-- Mirrors the tenant switch of the disposal cost estimate of V0_38_1 (GitHub issue #258). On, as in production.
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS disposal_cost_estimate TINYINT(1) NOT NULL DEFAULT 1
  COMMENT 'Tenant opt-out of the disposal cost estimate; effective only while gt.disposal.cost.estimate is 1';
