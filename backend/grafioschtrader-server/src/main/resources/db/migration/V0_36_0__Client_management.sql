-- Issue #53: prevent GT from being abused as a data provider. Daily budget of distinct instruments per user whose
-- price history may be read over REST (HistoryquoteReadLimitService). Editable in the Globalparameters admin UI.
DELETE FROM globalparameters WHERE property_name = 'gt.limit.day.HistoryquoteRead';
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.limit.day.HistoryquoteRead', 250, 0, 'min:20,max:10000');

-- Issue #159: connectors wrote 0 instead of NULL into the nullable OHLCV columns. A non-positive
-- open/high/low/volume carries no information; normalize existing rows to NULL. Idempotent: the WHERE
-- clause matches nothing once the zeros are gone. historyquote_legacy must be cleaned as well, otherwise
-- supplementFromShadow would re-import old zeros into the live table. gt_net_historyquote is included
-- because its rows are served onward to GTNet peers.
UPDATE historyquote
  SET open   = IF(open   <= 0, NULL, open),
      high   = IF(high   <= 0, NULL, high),
      low    = IF(low    <= 0, NULL, low),
      volume = IF(volume <= 0, NULL, volume)
  WHERE open <= 0 OR high <= 0 OR low <= 0 OR volume <= 0;

UPDATE historyquote_legacy
  SET open   = IF(open   <= 0, NULL, open),
      high   = IF(high   <= 0, NULL, high),
      low    = IF(low    <= 0, NULL, low),
      volume = IF(volume <= 0, NULL, volume)
  WHERE open <= 0 OR high <= 0 OR low <= 0 OR volume <= 0;

UPDATE gt_net_historyquote
  SET open   = IF(open   <= 0, NULL, open),
      high   = IF(high   <= 0, NULL, high),
      low    = IF(low    <= 0, NULL, low),
      volume = IF(volume <= 0, NULL, volume)
  WHERE open <= 0 OR high <= 0 OR low <= 0 OR volume <= 0;

-- Issue #200: many-to-many access model so a portfolio advisor can manage several client tenants, and a client can be
-- limited to read-only access on their own tenant. tenant_access lists the additional tenants a user may enter, each
-- with an access level (0 = READ view-only, 1 = MANAGE full CRUD). The home tenant (user.id_tenant) is NOT listed here
-- and implies MANAGE unless user.home_tenant_read_only = 1. Existing users have no rows here and keep full access, so
-- the change is backward compatible.
CREATE TABLE IF NOT EXISTS tenant_access (
  id_tenant_access INT NOT NULL AUTO_INCREMENT,
  id_user          INT NOT NULL,
  id_tenant        INT NOT NULL,
  access_level     TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (id_tenant_access),
  UNIQUE KEY uq_tenant_access_user_tenant (id_user, id_tenant),
  CONSTRAINT fk_tenant_access_user   FOREIGN KEY (id_user)   REFERENCES user(id_user)     ON DELETE CASCADE,
  CONSTRAINT fk_tenant_access_tenant FOREIGN KEY (id_tenant) REFERENCES tenant(id_tenant) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- Read-only flag for a user's OWN home tenant (client accounts). Default 0 = full access, so existing users are
-- unaffected. Guarded ADD COLUMN keeps the migration safe to re-run (MariaDB has no ADD COLUMN IF NOT EXISTS guarantee
-- across all supported versions for this combination).
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'home_tenant_read_only');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE user ADD COLUMN home_tenant_read_only TINYINT(1) NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Issues #200 and #201: multi-client access. Release note shown on the login screen.
DELETE FROM release_note WHERE version = '0.36.0';
INSERT INTO release_note (version, language, note) VALUES ('0.36.0', 'EN',
  'Multi-client management: a portfolio advisor can create and switch between several client portfolios, each with an optional read-only client login. Any user can also share read access to their own portfolio, either by inviting a new read-only viewer or granting access to an existing user.');
INSERT INTO release_note (version, language, note) VALUES ('0.36.0', 'DE',
  'Mandantenverwaltung: Ein Portfolioberater kann mehrere Kundenportfolios anlegen und zwischen ihnen wechseln, jeweils mit optionalem schreibgeschütztem Kundenlogin. Zusätzlich kann jeder Benutzer Lesezugriff auf sein eigenes Portfolio freigeben – durch Einladung eines neuen schreibgeschützten Betrachters oder durch Erteilung des Zugriffs an einen bestehenden Benutzer.');

-- Some connectors stopped delivering price data recently, which drove their retry counters to the limit so the
-- instruments are no longer attempted. Enqueue task 21 (RESET_CONNECTOR_RETRY_COUNTERS) once to clear the history and
-- intraday retry counters on active instruments so the connectors are tried again on the next EOD/intraday run.
-- Timestamps use UTC_TIMESTAMP() (BaseConstants.TIME_ZONE='UTC'); NOW() would be read as scheduled in the future.
-- The NOT EXISTS guard makes the migration idempotent: a re-run does not enqueue a second pending task.
INSERT INTO task_data_change (id_task, execution_priority, entity, id_entity, earliest_start_time, creation_time,
    exec_start_time, exec_end_time, old_value_varchar, old_value_number, progress_state, failed_message_code)
  SELECT 21, 20, NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, 0, NULL
  FROM DUAL
  WHERE NOT EXISTS (
    SELECT 1 FROM task_data_change WHERE id_task = 21 AND progress_state = 0);
