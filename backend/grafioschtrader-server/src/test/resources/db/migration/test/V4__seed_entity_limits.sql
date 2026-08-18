-- V1 contains the entity_limit schema, but the generated V2 snapshot may predate exporting its MAX defaults.
-- Keep this migration idempotent: nv.bat now dumps only limit_type = 0 into V2, after which these inserts simply
-- find the same unique keys already present. Role and user limits are owned by the integration/E2E tests.

INSERT IGNORE INTO `entity_limit`
  (`limit_type`, `entity_name`, `relation_entity_name`, `count_scope`, `owner_scope`, `limit_value`,
   `created_by`, `last_modified_by`, `last_modified_time`, `version`)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope,
       COALESCE((SELECT gp.property_int FROM `globalparameters` gp WHERE gp.property_name = x.property_name),
                x.default_value),
       0, 0, CURRENT_TIMESTAMP, 0
  FROM (
                  SELECT 'Transaction'      AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
                         CAST(NULL AS SIGNED) AS count_scope, 0 AS owner_scope,
                         'gt.max.transaction' AS property_name, 5000 AS default_value
        UNION ALL SELECT 'Cashaccount',     NULL, NULL, 0, 'gt.max.cash.account',     30
        UNION ALL SELECT 'Portfolio',       NULL, NULL, 0, 'gt.max.portfolio',        20
        UNION ALL SELECT 'Securityaccount', NULL, NULL, 0, 'gt.max.security.account', 20
        UNION ALL SELECT 'Watchlist',       NULL, NULL, 0, 'gt.max.watchlist',        30
        UNION ALL SELECT 'CorrelationSet',  NULL, NULL, 0, 'gt.max.correlation.set',  10
        UNION ALL SELECT 'StandingOrder',   NULL, NULL, 0, 'gt.max.standing.order',   50
        UNION ALL SELECT 'SimulationTenant', NULL, NULL, 0, 'gt.max.simulation.environments', 5
        UNION ALL SELECT 'Watchlist',      'Securitycurrency', 1, 0, 'gt.max.watchlist.length',        200
        UNION ALL SELECT 'Watchlist',      'Securitycurrency', 0, 0, 'gt.max.securities.currencies',  2000
        UNION ALL SELECT 'CorrelationSet', 'Securitycurrency', 1, 0, 'gt.max.correlation.instruments',  20
        UNION ALL SELECT 'Security', 'Securitysplit',      1, 1, 'gt.max.instrument.splits',               20
        UNION ALL SELECT 'Security', 'HistoryquotePeriod', 1, 1, 'gt.max.instrument.historyquote.periods', 20
        UNION ALL SELECT 'Security',            NULL, NULL, 2, NULL,  2000
        UNION ALL SELECT 'Currencypair',        NULL, NULL, 2, NULL,   500
        UNION ALL SELECT 'Assetclass',          NULL, NULL, 2, NULL,   200
        UNION ALL SELECT 'Stockexchange',       NULL, NULL, 2, NULL,   100
        UNION ALL SELECT 'GTNetSecurityImport', NULL, NULL, 2, NULL, 20000
        UNION ALL SELECT 'ImportTransactionHead', NULL,                   NULL, 0, NULL,   20
        UNION ALL SELECT 'ImportTransactionPos',  NULL,                   NULL, 0, NULL, 8000
        UNION ALL SELECT 'ImportTransactionHead', 'ImportTransactionPos',    1, 0, NULL, 2000
        UNION ALL SELECT 'TaxYearCorrection',     NULL,                   NULL, 0, NULL, 1000
        UNION ALL SELECT 'GTNetSecurityImpHead',  NULL,                   NULL, 0, NULL,  200
        UNION ALL SELECT 'GTNetSecurityImpHead',  'GTNetSecurityImpPos',     0, 0, NULL, 1000
        UNION ALL SELECT 'GTNetSecurityImpHead',  'GTNetSecurityImpPos',     1, 0, NULL,  200
        UNION ALL SELECT 'ShareInvite',           NULL,                   NULL, 0, NULL,   20
  ) x;
