-- ---------------------------------------------------------------------------
-- Rule based alerts: drop the speculative runtime tables and add the state that alert
-- evaluation actually needs, its background schedule, its durable delivery, the
-- The opening definition of a simulation environment, the personal
-- dashboard layout and the currency conversion convention of a client.
-- ---------------------------------------------------------------------------
-- Some blocks below are fenced with "-- SECTION-BEGIN <name>" / "-- SECTION-END <name>". Opt-in JDBC
-- tests extract exactly one fenced block and execute it against privately named scratch tables, so a
-- fence must never be widened to cover statements that name a table the extracting test does not
-- rename - it would then run against the real grafioschtrader_t rows. Append a new concern after the
-- existing fences with a fence of its own; never inside one. The same fences exist in the
-- test-database counterpart V7 and are kept identical to these.
-- algo_execution_state, algo_event_log, algo_recommendation and algo_simulation_result were created
-- ahead of the strategy modules, the rebalancing and the historical replay by V0_34_0. No code has
-- ever written or read a single row of them, they carry no foreign keys, and their shape does not
-- match what those features turned out to need - the execution state is keyed by AlgoTop rather than
-- by strategy, so it cannot hold two strategies on one instrument. They are removed here and
-- reintroduced below in the shape the code actually needs.
DROP TABLE IF EXISTS algo_execution_state;
DROP TABLE IF EXISTS algo_event_log;
DROP TABLE IF EXISTS algo_simulation_result;
-- algo_message_alert points at algo_recommendation through a plain INT column, without a constraint.
ALTER TABLE algo_message_alert DROP COLUMN IF EXISTS id_algo_recommendation;
DROP TABLE IF EXISTS algo_recommendation;

-- The opening date of a simulation belongs to the simulation environment and the end date of a run to
-- the run. Both were parked on the shared, editable AlgoTop, where an edit would silently redefine an
-- already recorded run. Nothing ever wrote them.
ALTER TABLE algo_top
  DROP COLUMN IF EXISTS simulation_start_date,
  DROP COLUMN IF EXISTS simulation_end_date;

-- AlgoTop.name is annotated @Size(max = 40) while the column stayed varchar(32), so a name of 33 to 40
-- characters passed validation and then failed on insert.
ALTER TABLE algo_top MODIFY COLUMN name VARCHAR(40) NOT NULL;

-- Portfolio-derived strategies use their dated holdings without a linked watchlist.
ALTER TABLE algo_top MODIFY COLUMN id_watchlist INT DEFAULT NULL;

-- Evaluation traverses AlgoTop -> AlgoAssetclass -> AlgoSecurity and skips a scope whose node is
-- deactivated. algo_top, algo_security and algo_strategy already carry the flag; the bucket did not.
ALTER TABLE algo_assetclass
  ADD COLUMN IF NOT EXISTS activatable TINYINT(1) NOT NULL DEFAULT 1;

-- ---------------------------------------------------------------------------
-- Crossing state for price, moving average and RSI alerts.
-- ---------------------------------------------------------------------------
-- An alert notifies on a transition, not on a level, so the previous side of the bound has to survive
-- both the evaluation and a restart. One row per tenant, strategy, instrument and bound, because a
-- strategy on an AlgoTop or on an asset class bucket is evaluated against many instruments
-- independently, and an absolute price alert has a lower and an upper bound that cross on their own.
-- config_fingerprint identifies the configuration the stored side was observed under: when it changes -
-- an edit, a reactivation - the next evaluation re-establishes a baseline instead of reporting a
-- crossing that never happened while the alert was off.
CREATE TABLE IF NOT EXISTS algo_alert_state (
  id_algo_alert_state INT NOT NULL AUTO_INCREMENT,
  id_tenant           INT NOT NULL,
  id_algo_strategy    INT NOT NULL,
  id_securitycurrency INT NOT NULL,
  bound_key           VARCHAR(24) NOT NULL
    COMMENT 'Which bound of the alert this row tracks, for example LOWER, UPPER, MA, RSI_LOWER',
  config_fingerprint  VARCHAR(64) NOT NULL
    COMMENT 'Identity of the configuration under which last_side was observed',
  last_side           TINYINT NOT NULL
    COMMENT '-1 below the bound, 0 exactly at it, 1 above it',
  last_value          DOUBLE DEFAULT NULL,
  last_evaluated      DATETIME DEFAULT NULL,
  PRIMARY KEY (id_algo_alert_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP INDEX IF EXISTS UK_AlertState ON algo_alert_state;
ALTER TABLE algo_alert_state
  ADD UNIQUE KEY UK_AlertState (id_tenant, id_algo_strategy, id_securitycurrency, bound_key);

ALTER TABLE algo_alert_state DROP FOREIGN KEY IF EXISTS FK_AlertState_AlgoStrategy;
ALTER TABLE algo_alert_state
  ADD CONSTRAINT FK_AlertState_AlgoStrategy FOREIGN KEY (id_algo_strategy)
    REFERENCES algo_strategy (id_algo_rule_strategy) ON DELETE CASCADE;

ALTER TABLE algo_alert_state DROP FOREIGN KEY IF EXISTS FK_AlertState_Securitycurrency;
ALTER TABLE algo_alert_state
  ADD CONSTRAINT FK_AlertState_Securitycurrency FOREIGN KEY (id_securitycurrency)
    REFERENCES securitycurrency (id_securitycurrency) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- Alarm identity, so that daily deduplication is an index instead of a table scan.
-- ---------------------------------------------------------------------------
-- Deduplication used to load the whole mail_entity table and filter it in Java on strategy plus date.
-- That collapsed every instrument of an AlgoTop into a single alarm per day and cost a full scan per
-- candidate alert. The identity below is the one an alert defines: tenant, strategy, instrument,
-- signal kind, bound direction and, once profit taking is configured, the tranche.
--
-- Every column of the unique key is NOT NULL on purpose. MariaDB treats NULLs in a unique index as
-- distinct, so a nullable direction or tranche would let the same signal be inserted without limit.
-- The sentinels are 0 for "no instrument", 0 for "no direction" and the empty string for "no tranche".
ALTER TABLE algo_message_alert
  ADD COLUMN IF NOT EXISTS alert_day DATE DEFAULT NULL
    COMMENT 'Application day of the alarm, the unit of daily deduplication';

-- Rows written before this migration carry neither a signal identity nor a reader, and the unique key
-- below would reject them as duplicates of one another. Guarding on alert_day rather than truncating
-- keeps the script safe to re-run: after the first run no row has a NULL day.
DELETE FROM algo_message_alert WHERE alert_day IS NULL;

ALTER TABLE algo_message_alert
  MODIFY COLUMN alert_day DATE NOT NULL,
  MODIFY COLUMN id_security_currency INT NOT NULL DEFAULT 0
    COMMENT '0 when the signal is not about a single instrument',
  MODIFY COLUMN alarm_type TINYINT NOT NULL DEFAULT 1
    COMMENT 'Signal kind, see grafioschtrader.types.AlgoSignalKind',
  ADD COLUMN IF NOT EXISTS signal_direction TINYINT NOT NULL DEFAULT 0
    COMMENT '-1 downward crossing, 1 upward crossing, 0 when the signal has no direction',
  ADD COLUMN IF NOT EXISTS tranche_key VARCHAR(32) NOT NULL DEFAULT ''
    COMMENT 'Identifies one profit taking tranche, empty for every alert signal',
  ADD COLUMN IF NOT EXISTS notified_at DATETIME DEFAULT NULL
    COMMENT 'When the notification for this alarm was delivered. NULL means the alarm is recorded but undelivered, which is what lets a retry tell a delivery failure from an alarm that was never raised';

DROP INDEX IF EXISTS UK_AlarmDay ON algo_message_alert;
ALTER TABLE algo_message_alert
  ADD UNIQUE KEY UK_AlarmDay (id_tenant, id_algo_strategy, id_security_currency, alarm_type,
                              signal_direction, tranche_key, alert_day);

-- ---------------------------------------------------------------------------
-- Lifetime caps for the rule based trading hierarchy and its alerts.
-- ---------------------------------------------------------------------------
-- These four tables had no cap of any kind. They are tenant private, and the generic create path skips the daily
-- check for every TenantBaseID, so nothing bounded how large a tenant could grow them - and ROLE_LIMIT_EDIT, which
-- every self registered user holds, reaches their write endpoints. The values are ceilings on abuse rather than
-- constraints on use. No property_name to read a former value from: the globalparameters limits were retired by
-- V0_36_7 and these keys never had one.
INSERT IGNORE INTO `entity_limit`
  (`limit_type`, `entity_name`, `relation_entity_name`, `count_scope`, `owner_scope`, `limit_value`,
   `created_by`, `last_modified_by`, `last_modified_time`, `version`)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value,
       0, 0, CURRENT_TIMESTAMP, 0
  FROM (
                  SELECT 'AlgoTop' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
                         CAST(NULL AS SIGNED) AS count_scope, 0 AS owner_scope,
                         CAST(NULL AS CHAR) AS property_name, 20 AS default_value
        UNION ALL SELECT 'AlgoAssetclass', NULL, NULL, 0, NULL,  200
        UNION ALL SELECT 'AlgoSecurity',   NULL, NULL, 0, NULL, 2000
        UNION ALL SELECT 'AlgoStrategy',   NULL, NULL, 0, NULL, 4000
  ) x;

-- SECTION-BEGIN alert-evaluation-schedule
-- Alert background scheduling. Internal state has one row per configured tenant/strategy/security pair.
CREATE TABLE IF NOT EXISTS algo_alert_evaluation_state (
  id_algo_alert_evaluation_state INT NOT NULL AUTO_INCREMENT,
  id_tenant INT NOT NULL,
  id_algo_strategy INT NOT NULL,
  id_securitycurrency INT NOT NULL,
  config_fingerprint VARCHAR(64) NOT NULL,
  last_attempt DATETIME(6) NULL,
  last_success DATETIME(6) NULL,
  quote_timestamp DATETIME(6) NULL,
  closing_attempt DATETIME(6) NULL,
  lease_until DATETIME(6) NULL,
  lease_token VARCHAR(36) NULL,
  outcome VARCHAR(20) NULL,
  reason VARCHAR(1000) NULL,
  PRIMARY KEY (id_algo_alert_evaluation_state),
  UNIQUE KEY UK_AlertEvaluationPair (id_tenant, id_algo_strategy, id_securitycurrency),
  CONSTRAINT FK_AlertEvaluation_Strategy FOREIGN KEY (id_algo_strategy)
    REFERENCES algo_strategy (id_algo_rule_strategy) ON DELETE CASCADE,
  CONSTRAINT FK_AlertEvaluation_Security FOREIGN KEY (id_securitycurrency)
    REFERENCES securitycurrency (id_securitycurrency) ON DELETE CASCADE,
  CONSTRAINT FK_AlertEvaluation_Tenant FOREIGN KEY (id_tenant)
    REFERENCES tenant (id_tenant) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- No direct user write path; row count is bounded by the existing limited alert hierarchy and its instrument scopes.
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
VALUES ('gt.algo.alarm.evaluation.interval.hours', 4, 0, 'min:2,max:6');

-- SECTION-END alert-evaluation-schedule

-- SECTION-BEGIN simulation-opening
-- The opening definition belongs to the simulation, not the shared strategy.
-- Existing environments have no provable opening definition and retain NULL metadata.
ALTER TABLE tenant
  ADD COLUMN IF NOT EXISTS simulation_start_date DATE DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS simulation_initialization_mode VARCHAR(24) DEFAULT NULL;
ALTER TABLE transaction
  ADD COLUMN IF NOT EXISTS simulation_opening TINYINT(1) NOT NULL DEFAULT 0;
-- SECTION-END simulation-opening
-- SECTION-BEGIN dashboard
-- Personal layout: one bounded document per user, independent of the active client.
CREATE TABLE IF NOT EXISTS user_dashboard (
  id_user INT NOT NULL,
  layout JSON NOT NULL,
  revision BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id_user),
  CONSTRAINT FK_UserDashboard_User FOREIGN KEY (id_user) REFERENCES user (id_user) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- SECTION-END dashboard


-- SECTION-BEGIN alert-delivery
-- Durable alert delivery. Existing unresolved alarms require an explicit retry.
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS delivery_status VARCHAR(255);
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS recipient_user_id INT;
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS recipient_email VARCHAR(255);
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS delivery_channels VARCHAR(255);
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS delivery_subject VARCHAR(255);
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS delivery_body TEXT;
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS internal_completed_at DATETIME;
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS external_completed_at DATETIME;
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS internal_message_id INT;
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS delivery_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS next_attempt_at DATETIME;
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS delivery_error VARCHAR(1000);
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS delivery_lease_token VARCHAR(255);
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS delivery_lease_until DATETIME;
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS context_name VARCHAR(255);
ALTER TABLE algo_message_alert ADD COLUMN IF NOT EXISTS security_name VARCHAR(255);
UPDATE algo_message_alert SET delivery_status = CASE WHEN notified_at IS NULL THEN 'REVIEW_REQUIRED' ELSE 'DELIVERED' END WHERE delivery_status IS NULL;
ALTER TABLE algo_message_alert MODIFY COLUMN delivery_status VARCHAR(255) NOT NULL DEFAULT 'PENDING';
CREATE INDEX IF NOT EXISTS IX_AlarmDeliveryDue ON algo_message_alert (delivery_status, next_attempt_at, delivery_lease_until);
INSERT IGNORE INTO entity_limit (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value, created_by, last_modified_by, last_modified_time, version) SELECT 1, 'AlgoAlertAction', NULL, NULL, NULL, 100, 0, 0, CURRENT_TIMESTAMP, 0;
-- SECTION-END alert-delivery

-- SECTION-BEGIN rebalancing-recommendation
-- The current rebalancing plan of an AlgoTop. One row per hierarchy node, replaced on every evaluation, so the
-- table holds a plan rather than a history; the start of the periodic interval is carried in checkpoint_date.
-- The tenant is part of the key because the plan is calculated for the tenant that holds the positions, while the
-- AlgoTop above it always belongs to the main tenant.
-- Dropped rather than altered: the table of the same name that V0_34_0 created was removed at the top of this script,
-- and re-running this fence has to arrive at the contracted shape whichever of the two it finds.
DROP TABLE IF EXISTS algo_recommendation;
CREATE TABLE IF NOT EXISTS algo_recommendation (
  id_algo_recommendation      INT NOT NULL AUTO_INCREMENT,
  id_tenant                   INT NOT NULL,
  id_algo_assetclass_security INT NOT NULL
    COMMENT 'The AlgoTop whose plan this line belongs to',
  id_algo_strategy            INT NOT NULL,
  level_type                  CHAR(1) NOT NULL
    COMMENT 'T top level, A asset class or named bucket, S instrument - the discriminator letters of the hierarchy',
  id_node                     INT NOT NULL
    COMMENT 'The AlgoTop, AlgoAssetclass or AlgoSecurity node this line describes',
  id_securitycurrency         INT DEFAULT NULL
    COMMENT 'Instrument of a security level line, NULL on the top and bucket levels',
  run_date                    DATE NOT NULL,
  valuation_date              DATE NOT NULL,
  currency                    VARCHAR(3) NOT NULL,
  target_percentage           DOUBLE DEFAULT NULL
    COMMENT 'Target share of total net equity in percentage points',
  actual_percentage           DOUBLE DEFAULT NULL,
  deviation_percentage        DOUBLE DEFAULT NULL
    COMMENT 'Actual minus target, in percentage points',
  target_amount               DOUBLE DEFAULT NULL,
  actual_amount               DOUBLE DEFAULT NULL,
  recommended_action          VARCHAR(20) NOT NULL
    COMMENT 'See grafioschtrader.types.AlgoRecommendationAction',
  recommended_amount          DOUBLE DEFAULT NULL,
  recommended_units           DOUBLE DEFAULT NULL,
  trigger_kind                VARCHAR(20) NOT NULL
    COMMENT 'See grafioschtrader.types.AlgoRebalancingTrigger',
  rationale                   VARCHAR(1000) DEFAULT NULL,
  PRIMARY KEY (id_algo_recommendation),
  UNIQUE KEY UK_AlgoRecommendation (id_tenant, id_algo_assetclass_security, level_type, id_node),
  CONSTRAINT FK_AlgoRecommendation_Tenant FOREIGN KEY (id_tenant)
    REFERENCES tenant (id_tenant) ON DELETE CASCADE,
  CONSTRAINT FK_AlgoRecommendation_AlgoTop FOREIGN KEY (id_algo_assetclass_security)
    REFERENCES algo_top_asset_security (id_algo_assetclass_security) ON DELETE CASCADE,
  CONSTRAINT FK_AlgoRecommendation_AlgoStrategy FOREIGN KEY (id_algo_strategy)
    REFERENCES algo_strategy (id_algo_rule_strategy) ON DELETE CASCADE,
  CONSTRAINT FK_AlgoRecommendation_Securitycurrency FOREIGN KEY (id_securitycurrency)
    REFERENCES securitycurrency (id_securitycurrency) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- SECTION-END rebalancing-recommendation

-- SECTION-BEGIN mean-reversion-execution
-- Strategy assignment is a logical reference: transactions export before strategies and remain valid when a strategy is removed.
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS id_algo_strategy INT DEFAULT NULL;
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS algo_fill_id VARCHAR(128) DEFAULT NULL;
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS algo_signal_id VARCHAR(255) DEFAULT NULL;
DROP INDEX IF EXISTS UK_AlgoFill ON transaction;
ALTER TABLE transaction ADD UNIQUE KEY UK_AlgoFill (id_tenant, algo_fill_id);
CREATE TABLE IF NOT EXISTS algo_execution_state (
  id_algo_execution_state INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id_tenant INT NOT NULL,
  id_algo_strategy INT NOT NULL,
  id_securitycurrency INT NOT NULL,
  lifecycle BIGINT NOT NULL,
  signed_units DOUBLE NOT NULL,
  initial_price DOUBLE NOT NULL,
  average_price DOUBLE NOT NULL,
  last_entry DATE DEFAULT NULL,
  last_exit DATE DEFAULT NULL,
  UNIQUE KEY UK_AlgoExecutionState (id_tenant, id_algo_strategy, id_securitycurrency, lifecycle),
  CONSTRAINT FK_AlgoExecutionStateTenant FOREIGN KEY (id_tenant) REFERENCES tenant(id_tenant) ON DELETE CASCADE,
  CONSTRAINT FK_AlgoExecutionStateStrategy FOREIGN KEY (id_algo_strategy) REFERENCES algo_strategy(id_algo_rule_strategy) ON DELETE CASCADE,
  CONSTRAINT FK_AlgoExecutionStateSecurity FOREIGN KEY (id_securitycurrency) REFERENCES securitycurrency(id_securitycurrency) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- InnoDB uses UK_AlgoRecommendation as the supporting index of FK_AlgoRecommendation_Tenant, so the
-- unique key cannot be replaced while that foreign key exists.
ALTER TABLE algo_recommendation DROP FOREIGN KEY IF EXISTS FK_AlgoRecommendation_Tenant;
DROP INDEX IF EXISTS UK_AlgoRecommendation ON algo_recommendation;
ALTER TABLE algo_recommendation ADD UNIQUE KEY UK_AlgoRecommendation
  (id_tenant, id_algo_assetclass_security, id_algo_strategy, level_type, id_node);
ALTER TABLE algo_recommendation
  ADD CONSTRAINT FK_AlgoRecommendation_Tenant FOREIGN KEY (id_tenant)
    REFERENCES tenant (id_tenant) ON DELETE CASCADE;
INSERT IGNORE INTO entity_limit (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
  created_by, last_modified_by, last_modified_time, version)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value,
  0, 0, CURRENT_TIMESTAMP, 0
FROM (SELECT 'AlgoExecutionState' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
  CAST(NULL AS SIGNED) AS count_scope, 0 AS owner_scope, CAST(NULL AS CHAR) AS property_name, 10000 AS default_value) x;
-- SECTION-END mean-reversion-execution

-- SECTION-BEGIN bankrupt-security
-- The instruments whose issuer no longer supplies price data. One missing closing price is not a local defect: the
-- period performance report drops a whole day as soon as a single held instrument has no quote for it, and once the
-- gap reaches the present there is no usable trading day left at all. A row here tells the end of day task to keep the
-- history of that instrument complete, carrying the last known price forward over the days no provider delivers.
-- Shared reference data with one row per instrument, so the instrument id is the functional key.
CREATE TABLE IF NOT EXISTS bankrupt_security (
  id_bankrupt_security INT NOT NULL AUTO_INCREMENT,
  id_securitycurrency  INT NOT NULL,
  no_data_since        DATE DEFAULT NULL,
  note                 VARCHAR(120) DEFAULT NULL,
  created_by           INT NOT NULL,
  creation_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_modified_by     INT NOT NULL,
  last_modified_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version              INT NOT NULL,
  PRIMARY KEY (id_bankrupt_security)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- The foreign key on id_securitycurrency is served by exactly this unique key, so InnoDB refuses to drop the index
-- while the constraint exists. On a repeated run of this script both are already in place, hence the constraint goes
-- first and is put back below.
ALTER TABLE bankrupt_security DROP FOREIGN KEY IF EXISTS FK_BankruptSecurity_Security;
DROP INDEX IF EXISTS UK_BankruptSecurity ON bankrupt_security;
ALTER TABLE bankrupt_security ADD UNIQUE KEY UK_BankruptSecurity (id_securitycurrency);
ALTER TABLE bankrupt_security
  ADD CONSTRAINT FK_BankruptSecurity_Security FOREIGN KEY (id_securitycurrency)
    REFERENCES security (id_securitycurrency) ON DELETE CASCADE;
-- Lifetime cap, counted by created_by like the instrument itself. No property_name to read a former value from: this
-- key never existed in globalparameters.
INSERT IGNORE INTO entity_limit (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
  created_by, last_modified_by, last_modified_time, version)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value,
  0, 0, CURRENT_TIMESTAMP, 0
FROM (SELECT 'BankruptSecurity' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
  CAST(NULL AS SIGNED) AS count_scope, 2 AS owner_scope, CAST(NULL AS CHAR) AS property_name, 200 AS default_value) x;
-- SECTION-END bankrupt-security

-- SECTION-BEGIN bankrupt-security-daily
-- The daily budget of the marker, for ROLE_LIMITEDIT only, in line with the rest of the shared reference data. Marking
-- an instrument is a rare act; the number bounds abuse rather than use.
INSERT IGNORE INTO entity_limit
  (limit_type, entity_name, id_role, limit_value, created_by, last_modified_by, last_modified_time, version)
SELECT x.limit_type, x.entity_name, r.id_role, x.default_value, 0, 0, CURRENT_TIMESTAMP, 0
  FROM role r
  JOIN (SELECT 1 AS limit_type, 'BankruptSecurity' AS entity_name, NULL AS property_name, 3 AS default_value) x
 WHERE r.rolename = 'ROLE_LIMITEDIT';
-- SECTION-END bankrupt-security-daily

-- SECTION-BEGIN scale-out
-- Profit taking sizes a tranche against the size the lifecycle was opened with, and decides whether a tranche is
-- still outstanding from how much of that size has already been given back. Both numbers are projections of the
-- ledger, rebuilt by every reconcile like signed_units and average_price, never written by a signal or a
-- notification. A restart, a corrected fill or a re-imported transaction therefore cannot leave a tranche half
-- executed, and a partially filled tranche simply re-proposes its remainder on the next evaluation.
ALTER TABLE algo_execution_state ADD COLUMN IF NOT EXISTS initial_units DOUBLE NOT NULL DEFAULT 0;
ALTER TABLE algo_execution_state ADD COLUMN IF NOT EXISTS realized_exit_units DOUBLE NOT NULL DEFAULT 0;
-- SECTION-END scale-out

-- The client chooses how the two cumulative flow rows of the Portfolios and Portfolio evaluation reach the main
-- currency. 0 keeps the accounting convention GT has always used: every fee and interest booking is converted with
-- the exchange rate of its own booking day. 1 converts them with the rate of the cut-off date instead, the way the
-- period performance revalues its running totals, so that the two evaluations report the same amount. The rate
-- movement on those bookings then no longer appears under Forex gain, because it is already contained in them.
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS fee_interest_fx_at_cut_off_date TINYINT(1) NOT NULL DEFAULT 0;

-- SECTION-BEGIN average-down
-- Frozen tranche quantities belong to executed fills and survive projection rebuilds and exports.
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS algo_tranche_targets TEXT DEFAULT NULL;
-- SECTION-END average-down

-- SECTION-BEGIN simulation-run
-- One historical replay of a simulation environment, and the audit trail of what it decided.
-- Both tables belong to the run and therefore to the simulation tenant, never to the editable AlgoTop above it: an
-- edit of the shared hierarchy must not silently redefine what an already recorded run means. The result row carries
-- the run definition, its progress and its metrics together, and there is at most one per environment - a repeat run
-- restores the opening state and replaces that row rather than appending a second copy of the previous one.
-- Dropped rather than altered: the tables of the same name that V0_34_0 created were removed at the top of this
-- script, and re-running this fence has to arrive at the contracted shape whichever of the two it finds. The event
-- log is dropped first because it references the result.
DROP TABLE IF EXISTS algo_event_log;
DROP TABLE IF EXISTS algo_simulation_result;
CREATE TABLE IF NOT EXISTS algo_simulation_result (
  id_simulation_result        INT NOT NULL AUTO_INCREMENT,
  id_tenant                   INT NOT NULL
    COMMENT 'The simulation environment the run belongs to, never the main tenant',
  id_algo_assetclass_security INT NOT NULL
    COMMENT 'The AlgoTop the environment was created from',
  opening_date                DATE NOT NULL
    COMMENT 'Immutable opening date of the environment, copied so that a later edit cannot redefine the run',
  end_date                    DATE NOT NULL,
  status                      VARCHAR(12) NOT NULL
    COMMENT 'See grafioschtrader.types.AlgoSimulationRunStatus',
  started_at                  DATETIME NOT NULL,
  finished_at                 DATETIME DEFAULT NULL,
  trading_days_total          INT NOT NULL DEFAULT 0,
  trading_days_done           INT NOT NULL DEFAULT 0,
  strategy_snapshot           MEDIUMTEXT NOT NULL
    COMMENT 'Effective configuration of every strategy of the hierarchy at submit time, so that later strategy edits cannot change what this run recorded',
  conventions                 VARCHAR(1000) NOT NULL
    COMMENT 'Space separated message keys of the price, cost and metric conventions the run was calculated under',
  total_return                DOUBLE DEFAULT NULL,
  dividend_payment_delay_days INT DEFAULT NULL,
  paid_dividends              DOUBLE DEFAULT NULL,
  dividend_receivables        DOUBLE DEFAULT NULL,
  annualized_return           DOUBLE DEFAULT NULL,
  max_drawdown                DOUBLE DEFAULT NULL
    COMMENT 'Largest peak to trough decline of the daily equity series, a negative decimal',
  sharpe_ratio                DOUBLE DEFAULT NULL,
  total_trades                INT DEFAULT NULL,
  winning_trades              INT DEFAULT NULL,
  losing_trades               INT DEFAULT NULL,
  failure_message             VARCHAR(500) DEFAULT NULL
    COMMENT 'Why a cancelled or failed run stopped; the metric columns stay NULL so a partial run cannot read as a complete one',
  PRIMARY KEY (id_simulation_result),
  UNIQUE KEY UK_AlgoSimulationResult (id_tenant),
  CONSTRAINT FK_AlgoSimulationResult_Tenant FOREIGN KEY (id_tenant)
    REFERENCES tenant (id_tenant) ON DELETE CASCADE,
  CONSTRAINT FK_AlgoSimulationResult_AlgoTop FOREIGN KEY (id_algo_assetclass_security)
    REFERENCES algo_top_asset_security (id_algo_assetclass_security) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS algo_event_log (
  id_algo_event        INT NOT NULL AUTO_INCREMENT,
  id_simulation_result INT NOT NULL,
  id_tenant            INT NOT NULL,
  id_algo_strategy     INT DEFAULT NULL
    COMMENT 'Strategy that produced the event, NULL for the run markers and for a rebalancing fill, which the ledger cannot carry an assignment for',
  id_securitycurrency  INT DEFAULT NULL,
  event_date           DATE NOT NULL
    COMMENT 'Closing day the decision was taken on, not the day the row was written',
  event_type           VARCHAR(30) NOT NULL
    COMMENT 'See grafioschtrader.types.AlgoEventType',
  rationale            VARCHAR(255) DEFAULT NULL
    COMMENT 'Bare message key such as MEAN_REVERSION_ENTRY or REBALANCE_BELOW_TARGET; the client resolves it',
  units                DOUBLE DEFAULT NULL,
  price                DOUBLE DEFAULT NULL
    COMMENT 'Closing price in the quotation units of the instrument',
  amount               DOUBLE DEFAULT NULL
    COMMENT 'Amount of the decision or fill in tenant currency',
  currency             VARCHAR(3) DEFAULT NULL,
  details              VARCHAR(1000) DEFAULT NULL,
  PRIMARY KEY (id_algo_event),
  KEY IX_AlgoEventLog_Run (id_simulation_result, event_date),
  CONSTRAINT FK_AlgoEventLog_Result FOREIGN KEY (id_simulation_result)
    REFERENCES algo_simulation_result (id_simulation_result) ON DELETE CASCADE,
  CONSTRAINT FK_AlgoEventLog_Tenant FOREIGN KEY (id_tenant)
    REFERENCES tenant (id_tenant) ON DELETE CASCADE,
  CONSTRAINT FK_AlgoEventLog_Strategy FOREIGN KEY (id_algo_strategy)
    REFERENCES algo_strategy (id_algo_rule_strategy) ON DELETE CASCADE,
  CONSTRAINT FK_AlgoEventLog_Securitycurrency FOREIGN KEY (id_securitycurrency)
    REFERENCES securitycurrency (id_securitycurrency) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- SECTION-END simulation-run

-- Lifetime cap of the replay audit trail, in the same family as the four hierarchy caps above and deliberately outside
-- the fence, because entity_limit is not a table a section extracting test renames. A run writes one row per decision
-- and trading day, so the ceiling bounds a pathological horizon rather than normal use. algo_simulation_result needs
-- no cap of its own: its unique key already allows exactly one row per environment.
INSERT IGNORE INTO `entity_limit`
  (`limit_type`, `entity_name`, `relation_entity_name`, `count_scope`, `owner_scope`, `limit_value`,
   `created_by`, `last_modified_by`, `last_modified_time`, `version`)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value,
       0, 0, CURRENT_TIMESTAMP, 0
  FROM (
                  SELECT 'AlgoEventLog' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
                         CAST(NULL AS SIGNED) AS count_scope, 0 AS owner_scope,
                         CAST(NULL AS CHAR) AS property_name, 200000 AS default_value
  ) x;

-- Ceiling of one historical replay, in trading days. Outside every fence, because globalparameters is not a table a
-- section extracting test renames. The value was a compiled-in 1500 and becomes an administrator setting: a run holds
-- a replay worker for its whole horizon, so how long one client may occupy the pool depends on the hardware of the
-- instance and on how many clients share it. The bounds keep the setting meaningful in both directions - below 300
-- trading days a replay is too short to say anything, above 7500 a single run outlives the history most instruments
-- have.
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
VALUES ('gt.simulation.max.run.trading.days', 3000, 0, 'min:300,max:7500');

-- Calendar-day fallback used only when the provider omits the dividend payment date.
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
VALUES ('gt.simulation.dividend.payment.delay.days', 16, 0, 'min:1,max:32');

-- Optional simulation taxation and regular coupon terms. Existing runs keep both features disabled.
-- SECTION-BEGIN simulation-tax
ALTER TABLE security ADD COLUMN IF NOT EXISTS issuer_country CHAR(2) NULL;
ALTER TABLE security ADD COLUMN IF NOT EXISTS simulation_metadata JSON NULL;

UPDATE security
SET issuer_country = UPPER(LEFT(isin, 2))
WHERE issuer_country IS NULL
  AND isin REGEXP '^[A-Za-z]{2}[A-Za-z0-9]{10}$'
  AND UPPER(LEFT(isin, 1)) <> 'X'
  AND UPPER(LEFT(isin, 2)) <> 'EU';
ALTER TABLE securityaccount ADD COLUMN IF NOT EXISTS tax_exempt_investor BOOLEAN NULL;
ALTER TABLE trading_platform_plan ADD COLUMN IF NOT EXISTS country_code CHAR(2) NULL;
ALTER TABLE tax_country ADD COLUMN IF NOT EXISTS tax_model_yaml MEDIUMTEXT NULL;

-- Current-rate, non-treaty simulation approximations for the ten largest economies by nominal GDP, plus CH, AT and
-- PL. They deliberately use timeless rules: dated periods would report irrelevant missing-period warnings because
-- every configured country is evaluated for every event. Existing administrator-authored models always win.
INSERT INTO tax_country (country_code, tax_model_yaml) VALUES
('US', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# Excludes treaties, refunds, REIT/RIC exceptions, portfolio-interest exemptions and investor-specific status.',
  '# Source: https://www.irs.gov/individuals/international-taxpayers/federal-income-tax-withholding-and-reporting-on-other-kinds-of-us-source-income-paid-to-nonresident-aliens',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "No generally applicable US investor transaction tax"',
  '      expression: "0"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "US"''',
  '      expression: "0"',
  '    - name: "US dividend statutory non-treaty rate"',
  '      condition: ''eventKind == "DIVIDEND"''',
  '      expression: "grossIncome * 0.30"',
  '    - name: "US interest statutory non-treaty rate"',
  '      expression: "grossIncome * 0.30"')),
('CN', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# Excludes treaty relief, special investor programmes and security-specific exemptions.',
  '# Source: https://tianjin.chinatax.gov.cn/11200000000/0300/030004/03000418/20260902152018507.shtml',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "Chinese transaction taxes need market and security details not captured by GT"',
  '      expression: "0"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "CN"''',
  '      expression: "0"',
  '    - name: "Chinese dividend statutory non-treaty rate"',
  '      condition: ''eventKind == "DIVIDEND"''',
  '      expression: "grossIncome * 0.20"',
  '    - name: "Chinese interest statutory non-treaty rate"',
  '      expression: "grossIncome * 0.20"')),
('DE', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# Dividend and exceptional bond rates include the 5.5% solidarity surcharge on 25% withholding.',
  '# Source: https://taxsummaries.pwc.com/germany/corporate/withholding-taxes',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "No generally applicable German securities transaction tax"',
  '      expression: "0"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "DE"''',
  '      expression: "0"',
  '    - name: "German dividend withholding plus solidarity surcharge"',
  '      condition: ''eventKind == "DIVIDEND"''',
  '      expression: "grossIncome * 0.26375"',
  '    - name: "German convertible-bond interest withholding plus solidarity surcharge"',
  '      condition: ''assetclass == "CONVERTIBLE_BOND"''',
  '      expression: "grossIncome * 0.26375"',
  '    - name: "Other German portfolio interest"',
  '      expression: "0"')),
('JP', CONCAT_WS(CHAR(10),
  '# 2026 listed-security non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# Uses the 15% national rate plus 2.1% reconstruction surtax. Excludes substantial holdings and special bonds.',
  '# Source: https://www.nta.go.jp/english/taxes/individual/pdf/incometax_2025/17.pdf',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "No generally applicable Japanese securities transaction tax"',
  '      expression: "0"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "JP"''',
  '      expression: "0"',
  '    - name: "Japanese listed-security withholding"',
  '      expression: "grossIncome * 0.15315"')),
('IN', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# Excludes surcharge, health and education cess, concessional bond rates and security-specific exemptions.',
  '# Source: https://taxsummaries.pwc.com/india/corporate/withholding-taxes',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "Indian transaction taxes need security and venue details not captured by GT"',
  '      expression: "0"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "IN"''',
  '      expression: "0"',
  '    - name: "Indian dividend statutory non-treaty rate"',
  '      condition: ''eventKind == "DIVIDEND"''',
  '      expression: "grossIncome * 0.20"',
  '    - name: "Indian interest statutory non-treaty rate"',
  '      expression: "grossIncome * 0.20"')),
('GB', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# SDRT excludes reliefs, newly listed shares, depositary receipts, clearance services and exempt instruments.',
  '# Income excludes REIT/PAIF distributions and quoted-Eurobond or other interest exemptions.',
  '# Sources: https://www.gov.uk/tax-buy-shares/buy-shares-electronically and https://www.gov.uk/hmrc-internal-manuals/international-manual/intm413210',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "UK sales are outside SDRT purchase estimate"',
  '      condition: ''eventKind == "SELL"''',
  '      expression: "0"',
  '    - name: "Purchase of a non-UK security"',
  '      condition: ''issuerCountry != "GB"''',
  '      expression: "0"',
  '    - name: "Non-direct instrument"',
  '      condition: ''instrument != "DIRECT_INVESTMENT"''',
  '      expression: "0"',
  '    - name: "Non-equity direct instrument"',
  '      condition: ''assetclass != "EQUITIES"''',
  '      expression: "0"',
  '    - name: "UK electronic share purchase SDRT"',
  '      expression: "cleanValue * 0.005"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "GB"''',
  '      expression: "0"',
  '    - name: "UK ordinary dividend withholding"',
  '      condition: ''eventKind == "DIVIDEND"''',
  '      expression: "0"',
  '    - name: "UK annual-interest statutory non-treaty rate"',
  '      expression: "grossIncome * 0.20"')),
('FR', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation for individuals. Applied timelessly. Checked 2026-09-13.',
  '# French FTT is omitted because issuer eligibility, market capitalisation, netting and exemptions are unavailable.',
  '# Sources: https://taxsummaries.pwc.com/france/corporate/withholding-taxes',
  '# and https://bofip.impots.gouv.fr/bofip/7575-PGP.html/identifiant=BOI-TCA-FIN-10-30-20250528',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "French FTT eligibility is not represented by current inputs"',
  '      expression: "0"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "FR"''',
  '      expression: "0"',
  '    - name: "French individual dividend statutory non-treaty rate"',
  '      condition: ''eventKind == "DIVIDEND"''',
  '      expression: "grossIncome * 0.128"',
  '    - name: "Ordinary French-source portfolio interest"',
  '      expression: "0"')),
('IT', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# Excludes treaty relief, government-bond rates and FTT eligibility, venue, netting and exemptions.',
  '# Source: https://taxsummaries.pwc.com/italy/corporate/withholding-taxes',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "Italian FTT eligibility is not represented by current inputs"',
  '      expression: "0"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "IT"''',
  '      expression: "0"',
  '    - name: "Italian dividend and securities-interest statutory rate"',
  '      expression: "grossIncome * 0.26"')),
('CA', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# Treats GT securities interest as ordinary arm-length portfolio debt. Participating or related debt is excluded.',
  '# Source: https://www.canada.ca/en/revenue-agency/services/forms-publications/publications/t4058/non-residents-income-tax.html',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "No generally applicable Canadian securities transaction tax"',
  '      expression: "0"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "CA"''',
  '      expression: "0"',
  '    - name: "Canadian dividend statutory non-treaty rate"',
  '      condition: ''eventKind == "DIVIDEND"''',
  '      expression: "grossIncome * 0.25"',
  '    - name: "Canadian arm-length portfolio interest"',
  '      expression: "0"')),
('BR', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# Excludes tax-haven rates, treaty relief and instrument- or payment-specific transactional taxes.',
  '# Source: https://taxsummaries.pwc.com/brazil/corporate/withholding-taxes',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "Brazilian transaction taxes need payment and instrument details not captured by GT"',
  '      expression: "0"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "BR"''',
  '      expression: "0"',
  '    - name: "Brazilian dividend statutory non-treaty rate"',
  '      condition: ''eventKind == "DIVIDEND"''',
  '      expression: "grossIncome * 0.10"',
  '    - name: "Brazilian interest statutory non-treaty rate"',
  '      expression: "grossIncome * 0.15"')),
('CH', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# Stamp duty models the client half where a Swiss securities dealer participates. Statutory exemptions remain.',
  '# Sources: https://www.estv.admin.ch/en/stamp-duty and https://www.estv.admin.ch/en/anticipatory-tax',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "Dealer outside Switzerland"',
  '      condition: ''dealerCountry != "CH"''',
  '      expression: "0"',
  '    - name: "CFD is outside the securities-transfer estimate"',
  '      condition: ''instrument == "CFD"''',
  '      expression: "0"',
  '    - name: "Forex is outside the securities-transfer estimate"',
  '      condition: ''instrument == "FOREX"''',
  '      expression: "0"',
  '    - name: "Non-investable index is outside the securities-transfer estimate"',
  '      condition: ''instrument == "NON_INVESTABLE_INDICES"''',
  '      expression: "0"',
  '    - name: "Explicitly exempt investor"',
  '      condition: "exemptInvestor == 1"',
  '      expression: "0"',
  '    - name: "Swiss security client share of transfer stamp duty"',
  '      condition: ''issuerCountry == "CH"''',
  '      expression: "cleanValue * 0.00075"',
  '    - name: "Foreign security client share of transfer stamp duty"',
  '      expression: "cleanValue * 0.0015"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "CH"''',
  '      expression: "0"',
  '    - name: "Swiss anticipatory tax on investment income"',
  '      expression: "grossIncome * 0.35"')),
('AT', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# Excludes treaty relief, refunds and security-specific exemptions.',
  '# Source: https://www.bmf.gv.at/en/topics/taxation/Income-Taxation-on-savings-and-investments/Capital-yields-in-the-strict-sense.html',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "No generally applicable Austrian securities transaction tax"',
  '      expression: "0"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "AT"''',
  '      expression: "0"',
  '    - name: "Austrian investment-income statutory rate"',
  '      expression: "grossIncome * 0.275"')),
('PL', CONCAT_WS(CHAR(10),
  '# 2026 non-treaty source-tax approximation. Applied timelessly. Checked 2026-09-13.',
  '# Excludes treaty and EU relief, beneficial-owner tests and pay-and-refund thresholds.',
  '# Source: https://www.podatki.gov.pl/en/corporate-income-tax/profits-subject-to-cit/',
  'version: 1',
  'transactionTaxes:',
  '  rules:',
  '    - name: "No generally applicable Polish exchange-traded securities transaction tax"',
  '      expression: "0"',
  'incomeWithholding:',
  '  rules:',
  '    - name: "Income from another issuer country"',
  '      condition: ''issuerCountry != "PL"''',
  '      expression: "0"',
  '    - name: "Polish dividend statutory non-treaty rate"',
  '      condition: ''eventKind == "DIVIDEND"''',
  '      expression: "grossIncome * 0.19"',
  '    - name: "Polish interest statutory non-treaty rate"',
  '      expression: "grossIncome * 0.20"'))
ON DUPLICATE KEY UPDATE tax_model_yaml =
  IF(tax_model_yaml IS NULL OR TRIM(tax_model_yaml) = '', VALUES(tax_model_yaml), tax_model_yaml);

ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS apply_tax_models BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS generate_bond_coupons BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS input_assumptions_json LONGTEXT NULL;
ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS tax_income_summary_json LONGTEXT NULL;
-- SECTION-END simulation-tax

-- SECTION-BEGIN rebalancing-checkpoint
-- The plan of an AlgoTop is replaced on every daily evaluation, so its run_date is always the newest day and cannot say
-- when the periodic interval started. Each plan carries the valuation day of the last checkpoint over instead; NULL
-- makes the next evaluation a checkpoint.
ALTER TABLE algo_recommendation ADD COLUMN IF NOT EXISTS checkpoint_date DATE DEFAULT NULL;
-- SECTION-END rebalancing-checkpoint

-- Portfolio rebalancing now triggers on class drift relative to the target investment budget.
ALTER TABLE algo_assetclass ADD COLUMN IF NOT EXISTS security_deviation_percentage double DEFAULT NULL;
ALTER TABLE algo_assetclass ADD COLUMN IF NOT EXISTS max_traded_securities_per_assetclass int DEFAULT NULL;

-- Do not create a flat parameter map for a JSON-only strategy: flat parameters take precedence over JSON.
INSERT IGNORE INTO algo_rule_strategy_param (id_algo_rule_strategy, param_name, param_value)
SELECT s.id_algo_rule_strategy, 'securityDeviationPercentage', '5'
FROM algo_strategy s WHERE s.algo_strategy_impl = 1
AND EXISTS (SELECT 1 FROM algo_rule_strategy_param p WHERE p.id_algo_rule_strategy = s.id_algo_rule_strategy);
INSERT IGNORE INTO algo_rule_strategy_param (id_algo_rule_strategy, param_name, param_value)
SELECT s.id_algo_rule_strategy, 'maxTradedSecuritiesPerAssetclass', '3'
FROM algo_strategy s WHERE s.algo_strategy_impl = 1
AND EXISTS (SELECT 1 FROM algo_rule_strategy_param p WHERE p.id_algo_rule_strategy = s.id_algo_rule_strategy);

UPDATE algo_strategy SET strategy_config = JSON_SET(strategy_config, '$.securityDeviationPercentage', 5)
WHERE algo_strategy_impl = 1 AND JSON_VALID(strategy_config)
AND JSON_CONTAINS_PATH(strategy_config, 'one', '$.securityDeviationPercentage') = 0;
UPDATE algo_strategy SET strategy_config = JSON_SET(strategy_config, '$.maxTradedSecuritiesPerAssetclass', 3)
WHERE algo_strategy_impl = 1 AND JSON_VALID(strategy_config)
AND JSON_CONTAINS_PATH(strategy_config, 'one', '$.maxTradedSecuritiesPerAssetclass') = 0;

-- ---------------------------------------------------------------------------
-- Release notes for v0.37.0, shown on the login screen. Only the dashboard is
-- announced: the rule-based trading and alert groundwork of this migration is
-- switched off through gt.use.algo / gt.use.alert and is released with v0.38.0.
-- The leading DELETE keeps the insert idempotent against the unique key
-- (version, language).
-- ---------------------------------------------------------------------------
DELETE FROM release_note WHERE version = '0.37.0';
INSERT INTO release_note (version, language, note) VALUES ('0.37.0', 'EN',
  'Personal dashboard: after login a configurable start page of cards shows the biggest winners and losers among the instruments you hold, the result of the last trading days, your unread messages, open data-change requests and, for administrators, pending limit-change requests. Cards can be added, removed, reordered, widened and configured individually, and each card leads to the screen that owns the full evaluation.');
INSERT INTO release_note (version, language, note) VALUES ('0.37.0', 'DE',
  'Persönliches Dashboard: Nach der Anmeldung zeigt eine konfigurierbare Startseite aus Karten die grössten Gewinner und Verlierer unter den gehaltenen Instrumenten, das Ergebnis der letzten Handelstage, ungelesene Nachrichten, offene Datenänderungsanträge und für Administratoren offene Anträge auf Limitenänderung. Karten lassen sich hinzufügen, entfernen, umordnen, in der Breite ändern und einzeln konfigurieren; jede Karte führt zur Ansicht mit der vollständigen Auswertung.');
