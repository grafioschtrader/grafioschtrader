-- Frankfurter (https://frankfurter.dev) is added as an alternative to the Pacific Exchange Rate Service
-- (fx.sauder.ubc.ca), which throttles hard and regularly answers "Server is too busy.".
--
-- Roughly a quarter of the currency pairs currently served by "fxubc" is moved over, so that the load is
-- spread across two providers instead of replacing one single point of failure by another. MOD on the primary
-- key is used rather than RAND() as in V0_31_2: it keeps this migration idempotent, because a repeated run
-- selects exactly the same rows.
--
-- The default connector for newly created currency pairs (gt.currency.history.connector) is intentionally left
-- untouched, and no connector is registered here - programmed connectors are Spring beans, only the assignment
-- of an instrument to a connector lives in the database.

-- The join against currencypair is not redundant: both connectors serve currency pairs only, and a
-- securitycurrency row carrying this connector id without a currencypair child must be left alone.
UPDATE securitycurrency sc
  JOIN currencypair cp ON cp.id_securitycurrency = sc.id_securitycurrency
   SET sc.id_connector_history = 'gt.datafeed.frankfurter',
       -- The connector identifies the pair by its currencies (FeedIdentifier.CURRENCY), so no URL extension exists.
       sc.url_history_extend   = NULL,
       -- Clear the failure counter so the moved pairs are picked up again by the next historical price update.
       sc.retry_history_load   = 0
 WHERE sc.id_connector_history = 'gt.datafeed.fxubc'
   AND MOD(sc.id_securitycurrency, 4) = 0;


-- ---------------------------------------------------------------------------------------------------------------
-- Issue #206: generalized role- and user-aware limit model.
--
-- The anti-flooding limits used to be spread over four mechanisms whose configuration lived in "globalparameters"
-- under opaque property names, plus the "user_entity_change_limit" table. They are replaced by a single
-- "entity_limit" table that carries the limit key, an optional role or user scope and the value. Only the
-- configuration moves; the runtime counters ("user_entity_change_count" and the live count(*) queries) stay.
--
-- The seeded value is COALESCE(<existing globalparameters row>, <fresh-install default>), never the bare default: a
-- globalparameters row was an override of a compiled Java default and installations differ, so seeding the default
-- would silently re-tune every installation that ever changed a limit. The literals below are the Java defaults from
-- LimitKeyConfig; EntityLimitSeedGuardTest fails the build when the two drift apart.
-- ---------------------------------------------------------------------------------------------------------------

-- 1. The configuration table itself.
--
-- Exactly one row may exist per (limit key, scope). MariaDB treats NULLs as distinct in a UNIQUE index, so the
-- uniqueness has to be built over persistent generated columns that fold the nullable parts into a non-null value.
-- id_role and id_user are mutually exclusive; both NULL makes the row the default ("ALL") row of its key.
CREATE TABLE IF NOT EXISTS `entity_limit` (
  `id_entity_limit` int(11) NOT NULL AUTO_INCREMENT,
  -- grafiosch.types.LimitType: 0 = MAX, 1 = DAY_CUD, 2 = DAY_READ
  `limit_type` tinyint(4) NOT NULL,
  `entity_name` varchar(40) NOT NULL,
  `relation_entity_name` varchar(40) DEFAULT NULL,
  -- grafiosch.types.CountScope: 0 = ALL, 1 = SINGLE
  `count_scope` tinyint(4) DEFAULT NULL,
  -- grafiosch.types.OwnerScope: 0 = TENANT, 1 = GLOBAL, 2 = CREATOR
  `owner_scope` tinyint(4) DEFAULT NULL,
  `id_role` int(11) DEFAULT NULL,
  `id_user` int(11) DEFAULT NULL,
  `limit_value` int(11) NOT NULL,
  `valid_until` date DEFAULT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  `uk_relation_entity_name` varchar(40) AS (COALESCE(`relation_entity_name`, '')) PERSISTENT,
  `uk_count_scope` tinyint(4) AS (COALESCE(`count_scope`, -1)) PERSISTENT,
  `uk_owner_scope` tinyint(4) AS (COALESCE(`owner_scope`, -1)) PERSISTENT,
  `uk_id_role` int(11) AS (COALESCE(`id_role`, 0)) PERSISTENT,
  `uk_id_user` int(11) AS (COALESCE(`id_user`, 0)) PERSISTENT,
  PRIMARY KEY (`id_entity_limit`),
  UNIQUE KEY `El_unique` (`limit_type`,`entity_name`,`uk_relation_entity_name`,`uk_count_scope`,`uk_owner_scope`,
    `uk_id_role`,`uk_id_user`),
  -- The resolver loads every row of a (limit_type, entity_name) pair and filters the remaining key parts in Java.
  KEY `El_lookup` (`limit_type`,`entity_name`),
  -- ON DELETE CASCADE, unlike FK_UserEntityChangeLimit_User which had no ON DELETE clause at all and relied entirely
  -- on the explicit ExportDeleteHelper entry.
  CONSTRAINT `FK_EntityLimit_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE,
  CONSTRAINT `FK_EntityLimit_Role` FOREIGN KEY (`id_role`) REFERENCES `role` (`id_role`) ON DELETE RESTRICT,
  CONSTRAINT `CK_EntityLimit_Scope` CHECK (`id_role` IS NULL OR `id_user` IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- 2. The counter table has to accept the same entity names as the new configuration table.
ALTER TABLE `user_entity_change_count` MODIFY `entity_name` varchar(40) NOT NULL;

-- 3. Tell securities the GTNet import created apart from those it merely matched. Both cases write
-- id_securitycurrency on the staging row and nothing else distinguishes them, so without this flag the
-- GTNetSecurityImport cap and the ordinary Security cap would count the same rows. Existing rows stay 0 because
-- there is no reliable way to backfill them.
ALTER TABLE `gt_net_security_imp_pos`
  ADD COLUMN IF NOT EXISTS `security_created_by_import` TINYINT NOT NULL DEFAULT 0;

-- 4. The MAX default rows. Entries without a property_name are the new lifetime caps on shared data, which have no
-- predecessor in globalparameters, so for them the default is the value.
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
        -- Pseudo entity: counts tenant rows by id_parent_tenant, not expressible as a plain Tenant cap.
        UNION ALL SELECT 'SimulationTenant', NULL, NULL, 0, 'gt.max.simulation.environments', 5
        -- Instruments in one watchlist versus instruments over all watchlists of the tenant. Nothing in the former
        -- property names gt.max.watchlist.length and gt.max.securities.currencies said which of the two was which.
        UNION ALL SELECT 'Watchlist',      'Securitycurrency', 1, 0, 'gt.max.watchlist.length',        200
        UNION ALL SELECT 'Watchlist',      'Securitycurrency', 0, 0, 'gt.max.securities.currencies',  2000
        UNION ALL SELECT 'CorrelationSet', 'Securitycurrency', 1, 0, 'gt.max.correlation.instruments',  20
        -- An instrument is shared data, so its splits and periods are counted globally rather than per tenant.
        UNION ALL SELECT 'Security', 'Securitysplit',      1, 1, 'gt.max.instrument.splits',               20
        UNION ALL SELECT 'Security', 'HistoryquotePeriod', 1, 1, 'gt.max.instrument.historyquote.periods', 20
        -- New lifetime caps on shared data, counted by the current created_by. Shared data previously had only a
        -- daily rate limit, so rows accumulated without bound over time.
        UNION ALL SELECT 'Security',            NULL, NULL, 2, NULL,  2000
        UNION ALL SELECT 'Currencypair',        NULL, NULL, 2, NULL,   500
        UNION ALL SELECT 'Assetclass',          NULL, NULL, 2, NULL,   200
        UNION ALL SELECT 'Stockexchange',       NULL, NULL, 2, NULL,   100
        UNION ALL SELECT 'GTNetSecurityImport', NULL, NULL, 2, NULL, 20000
        -- Tenant private staging tables. They never had a cap: the generic create path skips the daily check for
        -- every TenantBaseID, and no MAX key existed, so a tenant could grow them without any bound. The numbers are
        -- ceilings on abuse, not constraints on normal use. imp_trans_pos is bounded on both levels, because a tenant
        -- total alone still allows one absurdly large import set and a per head cap alone allows unbounded heads.
        UNION ALL SELECT 'ImportTransactionHead', NULL,                   NULL, 0, NULL,   20
        UNION ALL SELECT 'ImportTransactionPos',  NULL,                   NULL, 0, NULL, 8000
        UNION ALL SELECT 'ImportTransactionHead', 'ImportTransactionPos',    1, 0, NULL, 2000
        UNION ALL SELECT 'TaxYearCorrection',     NULL,                   NULL, 0, NULL, 1000
        -- gt_net_security_imp_pos has no tenant column, so even its tenant total is a nested key on the head.
        UNION ALL SELECT 'GTNetSecurityImpHead',  NULL,                   NULL, 0, NULL,  200
        UNION ALL SELECT 'GTNetSecurityImpHead',  'GTNetSecurityImpPos',     0, 0, NULL, 1000
        UNION ALL SELECT 'GTNetSecurityImpHead',  'GTNetSecurityImpPos',     1, 0, NULL,  200
        -- Pseudo entity of the library layer: read-only viewer accounts an owner invited into their own tenant.
        -- POST /api/tenant/share creates an enabled ROLE_USER and sends a mail for every unknown e-mail, and nothing
        -- else bounded how many of those one tenant may produce.
        UNION ALL SELECT 'ShareInvite',           NULL,                   NULL, 0, NULL,   20
  ) x;

-- 4b. Role scoped MAX rows. Only the two GTNet staging tables are role scoped, because they are the tables a
-- stranger can drive hardest: ROLE_LIMITEDIT is exactly the role UserServiceImpl.createUser hands to every
-- self-registered user. A single ceiling shared with an administrator is the wrong instrument for that.
--
-- This has to stay a statement of its own rather than extra rows in the list of step 4. EntityLimitSeedGuardTest
-- tells the default block and the role block apart by block, and selecting FROM role writes no row at all when the
-- role is missing instead of one silently becoming a default row. The ALL rows of step 4 stay the outer bound: they
-- cannot be deleted, so removing a role row falls back to 200 / 1000 / 200 and never to unlimited.
INSERT IGNORE INTO `entity_limit`
  (`limit_type`, `entity_name`, `relation_entity_name`, `count_scope`, `owner_scope`, `id_role`, `limit_value`,
   `created_by`, `last_modified_by`, `last_modified_time`, `version`)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, r.id_role, x.limit_value,
       0, 0, CURRENT_TIMESTAMP, 0
  FROM `role` r
  JOIN (
                  SELECT 'GTNetSecurityImpHead' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
                         CAST(NULL AS SIGNED) AS count_scope, 0 AS owner_scope, 50 AS limit_value
        UNION ALL SELECT 'GTNetSecurityImpHead', 'GTNetSecurityImpPos', 0, 0, 250
        UNION ALL SELECT 'GTNetSecurityImpHead', 'GTNetSecurityImpPos', 1, 0,  50
  ) x
 WHERE r.rolename = 'ROLE_LIMITEDIT';

-- 5. The daily rows, seeded for ROLE_LIMITEDIT only. That is exactly the current effective scope, because the daily
-- check was hardcoded behind isLimitedEditingUser: ALLEDIT and ADMIN stay unthrottled until an administrator adds a
-- row for them, and making that possible is the point of this change. Selecting FROM role rather than using a scalar
-- subquery means no row is written at all when the role is missing, instead of one silently becoming a default row.
INSERT IGNORE INTO `entity_limit`
  (`limit_type`, `entity_name`, `id_role`, `limit_value`,
   `created_by`, `last_modified_by`, `last_modified_time`, `version`)
SELECT x.limit_type, x.entity_name, r.id_role,
       COALESCE((SELECT gp.property_int FROM `globalparameters` gp WHERE gp.property_name = x.property_name),
                x.default_value),
       0, 0, CURRENT_TIMESTAMP, 0
  FROM `role` r
  JOIN (
                  SELECT 1 AS limit_type, 'Assetclass' AS entity_name,
                         'gt.limit.day.Assetclass' AS property_name, 10 AS default_value
        UNION ALL SELECT 1, 'Stockexchange',             'gt.limit.day.Stockexchange',             10
        UNION ALL SELECT 1, 'Security',                  'gt.limit.day.Security',                  50
        UNION ALL SELECT 1, 'Currencypair',              'gt.limit.day.Currencypair',              15
        UNION ALL SELECT 1, 'Historyquote',              'gt.limit.day.Historyquote',              15
        UNION ALL SELECT 1, 'HistoryquoteLegacy',        'gt.limit.day.HistoryquoteLegacy',        15
        UNION ALL SELECT 1, 'ImportTransactionTemplate', 'gt.limit.day.ImportTransactionTemplate', 10
        UNION ALL SELECT 1, 'ImportTransactionPlatform', 'gt.limit.day.ImportTransactionPlatform',  3
        UNION ALL SELECT 1, 'TradingPlatformPlan',       'gt.limit.day.TradingPlatformPlan',        3
        UNION ALL SELECT 1, 'TradingCalendarRuleSet',    'gt.limit.day.TradingCalendarRuleSet',     4
        UNION ALL SELECT 1, 'UDFMetadataSecurity',       'gt.limit.day.UDFMetadataSecurity',       20
        -- Lowered from 150: this is the only ceiling on securities created per day by a GTNet import, because that
        -- path substitutes this pseudo entity for the ordinary Security budget and never consumes DAY_CUD|Security.
        UNION ALL SELECT 1, 'GTNetSecurityImport',       'gt.limit.day.GTNetSecurityImport',       60
        UNION ALL SELECT 1, 'GenericConnectorDef',       'gt.limit.day.GenericConnectorDef',       10
        UNION ALL SELECT 1, 'RiskFreeRateMapping',       'gt.limit.day.RiskFreeRateMapping',        2
        -- Bulk writes on shared tables. The trading calendar is budgeted in stock exchange years touched, not in
        -- trading_days_minus rows: it is always edited or copied a year at a time, and a full copy spans 31 years.
        -- The price uploads and the gap filler need no key of their own — they are accounted as one edit of the
        -- security or currency pair they belong to and consume that instrument's budget above.
        -- NULL property name: this budget never existed in globalparameters, so there is nothing to carry over and
        -- nothing for step 7 to clean up.
        UNION ALL SELECT 1, 'TradingDaysMinusYear',      NULL,                                     60
        -- Tenant private staging tables. The MAX rows of step 4 bound how much staging data may exist, these bound
        -- how fast it may be produced. None of the four reaches the generic daily path - three are TenantBaseID and
        -- GTNetSecurityImpPosResource does not extend UpdateCreate at all - so each is checked at its own write site.
        -- NULL property name: none of these budgets ever existed in globalparameters.
        UNION ALL SELECT 1, 'ImportTransactionHead',     NULL,                                     12
        UNION ALL SELECT 1, 'ImportTransactionPos',      NULL,                                   5000
        UNION ALL SELECT 1, 'GTNetSecurityImpHead',      NULL,                                      3
        UNION ALL SELECT 1, 'GTNetSecurityImpPos',       NULL,                                    100
        -- Library layer. The application never overrode these, but an installation may have.
        UNION ALL SELECT 1, 'MailSendRecv',              'g.limit.day.MailSendRecv',              200
        UNION ALL SELECT 1, 'MailSettingForward',        'g.limit.day.MailSettingForward',         12
        UNION ALL SELECT 1, 'UDFMetadataGeneral',        'g.limit.day.UDFMetadataGeneral',         20
        UNION ALL SELECT 1, 'ProposeUserTask',           'g.limit.day.ProposeUserTask',            10
        -- The GTNet peer administration surface. Its update endpoints carry @PreAuthorize("hasRole('ADMIN')") but the
        -- inherited create mappings do not, so these tables are writable by any authenticated user and had no budget
        -- at all. EntityLimitSeedGuardTest is what surfaced them.
        UNION ALL SELECT 1, 'GTNet',                     NULL,                                      5
        UNION ALL SELECT 1, 'GTNetConfig',               NULL,                                      3
        UNION ALL SELECT 1, 'GTNetConfigEntity',         NULL,                                     10
        UNION ALL SELECT 1, 'GTNetMessage',              NULL,                                     20
        UNION ALL SELECT 1, 'GTNetMessageAnswer',        NULL,                                     20
        -- The only member of the read family. A HistoryquoteRead row written as DAY_CUD would be invisible to the
        -- read resolver.
        UNION ALL SELECT 2, 'HistoryquoteRead',          'gt.limit.day.HistoryquoteRead',         250
  ) x
 WHERE r.rolename = 'ROLE_LIMITEDIT';

-- 5b. Bring an already seeded GTNetSecurityImport row down to the lowered ceiling. Step 5 is INSERT IGNORE, so a
-- database that ran an earlier version of this script keeps the 150 it wrote then and never sees the 60 above -
-- which is exactly the number that bounds how many securities a GTNet import may create per day. Restricted to
-- created_by = 0 and to the old literal, so a value an administrator deliberately tuned is left alone. Idempotent in
-- both directions: on a fresh install step 5 inserts 60 and this matches no row, and a re-run matches no row either.
UPDATE `entity_limit`
   SET `limit_value` = 60
 WHERE `entity_name` = 'GTNetSecurityImport'
   AND `limit_type` = 1
   AND `created_by` = 0
   AND `limit_value` = 150;

-- 6. Carry over the per-user exceptions. The limit type follows the entity name: a row naming a pseudo entity of the
-- read family becomes DAY_READ, everything else DAY_CUD. Migrating a HistoryquoteRead row as DAY_CUD would make the
-- read resolver miss it and silently drop that read exception.
--
-- Guarded by an INFORMATION_SCHEMA check because step 8 drops the source table, so a repeated run finds it gone.
DELIMITER //
CREATE OR REPLACE PROCEDURE MigrateUserEntityChangeLimit()
BEGIN
    IF EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_entity_change_limit') THEN
        INSERT IGNORE INTO `entity_limit`
          (`limit_type`, `entity_name`, `id_user`, `limit_value`, `valid_until`,
           `created_by`, `creation_time`, `last_modified_by`, `last_modified_time`, `version`)
        SELECT CASE WHEN uecl.entity_name = 'HistoryquoteRead' THEN 2 ELSE 1 END,
               uecl.entity_name, uecl.id_user, uecl.day_limit, uecl.until_date,
               uecl.created_by, uecl.creation_time, uecl.last_modified_by, uecl.last_modified_time, 0
          FROM `user_entity_change_limit` uecl;
    END IF;
END //
DELIMITER ;
CALL MigrateUserEntityChangeLimit();
DROP PROCEDURE IF EXISTS MigrateUserEntityChangeLimit;

-- 7. Retire the migrated configuration rows. Enumerated rather than swept with a LIKE pattern on purpose:
-- g.max.limit.request.exceeded.count and g.max.security.breach.count carry "max" in their names but are user-lockout
-- thresholds rather than entity caps (they were renamed from gt.max.* in V0_33_7), and a blanket sweep would migrate
-- them by mistake. gt.limit.day.MailSendbox and gt.limit.day.ProposeUserTask are dead keys that no code ever read:
-- MailSendRecvResource and ProposeUserTaskResource both resolve the g. prefixed variant.
DELETE FROM `globalparameters` WHERE `property_name` IN (
  'gt.max.transaction', 'gt.max.cash.account', 'gt.max.portfolio', 'gt.max.security.account', 'gt.max.watchlist',
  'gt.max.watchlist.length', 'gt.max.securities.currencies', 'gt.max.correlation.set',
  'gt.max.correlation.instruments', 'gt.max.instrument.splits', 'gt.max.instrument.historyquote.periods',
  'gt.max.simulation.environments', 'gt.max.standing.order',
  'gt.limit.day.Assetclass', 'gt.limit.day.Stockexchange', 'gt.limit.day.Security', 'gt.limit.day.Currencypair',
  'gt.limit.day.Historyquote', 'gt.limit.day.HistoryquoteLegacy', 'gt.limit.day.ImportTransactionTemplate',
  'gt.limit.day.ImportTransactionPlatform', 'gt.limit.day.TradingPlatformPlan', 'gt.limit.day.TradingCalendarRuleSet',
  'gt.limit.day.UDFMetadataSecurity', 'gt.limit.day.GTNetSecurityImport', 'gt.limit.day.GenericConnectorDef',
  'gt.limit.day.RiskFreeRateMapping', 'gt.limit.day.HistoryquoteRead',
  'g.limit.day.MailSendRecv', 'g.limit.day.MailSettingForward', 'g.limit.day.UDFMetadataGeneral',
  'g.limit.day.ProposeUserTask',
  'gt.limit.day.MailSendbox', 'gt.limit.day.ProposeUserTask');

-- 8. The per-user table is fully superseded by the id_user rows of entity_limit.
DROP TABLE IF EXISTS `user_entity_change_limit`;
