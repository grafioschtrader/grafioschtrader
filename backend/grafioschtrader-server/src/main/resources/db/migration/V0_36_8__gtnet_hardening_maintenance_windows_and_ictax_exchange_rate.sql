-- GitHub issue #234: the GTNet daily request limit was published but never enforced.
-- The two counters in gt_net_config now carry the UTC day they belong to, so they roll over on the first
-- charged request of a new day instead of relying on a reset job that never existed.
ALTER TABLE gt_net_config ADD COLUMN IF NOT EXISTS daily_req_limit_date DATE DEFAULT NULL;

-- Counters written before this migration belong to an unknown day; clear them so nobody starts the day
-- against a stale budget.
UPDATE gt_net_config SET daily_req_limit_count = NULL, daily_req_limit_remote_count = NULL
  WHERE daily_req_limit_date IS NULL;

-- Official year-end and annual mean exchange rates of the ICTax Kursliste. The Swiss Federal Tax
-- Administration derives them from SIX Financial Information closing spot rates, so they deviate
-- slightly from what any price connector delivers for the same currency pair.
-- Keyed on the tax year, not on the upload: an upload can be re-imported and a differential Kursliste
-- carries no exchange rates at all, which would drop the manual override or the whole year.
CREATE TABLE IF NOT EXISTS ictax_exchange_rate (
  id_ictax_exchange_rate    INT AUTO_INCREMENT PRIMARY KEY,
  id_tax_year               INT NOT NULL,
  currency                  VARCHAR(3) NOT NULL,
  denomination              INT NOT NULL DEFAULT 1,
  year_end_rate             DOUBLE DEFAULT NULL,
  annual_mean_rate          DOUBLE DEFAULT NULL,
  year_end_rate_override    DOUBLE DEFAULT NULL,
  annual_mean_rate_override DOUBLE DEFAULT NULL,
  UNIQUE KEY uk_ictax_exchange_rate (id_tax_year, currency),
  CONSTRAINT fk_ictax_exchange_rate_year FOREIGN KEY (id_tax_year)
    REFERENCES tax_year (id_tax_year) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- GitHub issue #237: a peer's announced maintenance windows and its announced shutdown date were
-- transported and stored as message parameters, but never read back. The receiving peer flipped its
-- server state the moment the announcement arrived and the next status check or inbound envelope
-- undid it again. Both dates now govern when the peer is contacted.

-- A peer may announce several windows, hence a table rather than a pair of columns on gt_net. The
-- window is evaluated at query time, so no background job has to open or close it on time.
CREATE TABLE IF NOT EXISTS gt_net_maintenance_window (
  id_gt_net_maintenance_window INT AUTO_INCREMENT PRIMARY KEY,
  id_gt_net                    INT      NOT NULL COMMENT 'The remote whose services are unavailable',
  id_gt_net_message            INT      NOT NULL COMMENT 'The received announcement this window was read from',
  from_date_time               DATETIME NOT NULL COMMENT 'UTC start of the window',
  to_date_time                 DATETIME NOT NULL COMMENT 'UTC end of the window',
  CONSTRAINT uk_gt_net_maintenance_window UNIQUE (id_gt_net, from_date_time, to_date_time),
  KEY idx_gt_net_maintenance_window_active (id_gt_net, to_date_time),
  KEY idx_gt_net_maintenance_window_message (id_gt_net_message),
  CONSTRAINT fk_maintenance_window_gtnet FOREIGN KEY (id_gt_net)
    REFERENCES gt_net (id_gt_net) ON DELETE CASCADE,
  CONSTRAINT fk_maintenance_window_message FOREIGN KEY (id_gt_net_message)
    REFERENCES gt_net_message (id_gt_net_message) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Announced maintenance windows of remote GTNet instances';

-- The announced day a peer goes out of operation. GNetFutureMessageDeliveryTask sets server_online to
-- 3 (SOS_OUT_OF_SERVICE) once it is reached and clears the date again.
ALTER TABLE gt_net ADD COLUMN IF NOT EXISTS close_start_date DATE DEFAULT NULL;

-- Nothing writes SS_MAINTENANCE (3) any more, so a row left in that state by the previous, date-blind
-- handler would stay there forever. Reset it to SS_NONE (0); the status check that runs at the next
-- startup re-evaluates every peer anyway.
UPDATE gt_net_entity SET server_state = 0 WHERE server_state = 3;

-- ---------------------------------------------------------------------------------------------------
-- GTNet baseline protocol hardening
-- ---------------------------------------------------------------------------------------------------

-- A redelivery must not insert a second row. Both delivery tasks re-send the same persisted message and
-- the envelope carries its id unchanged, so a retry after a lost response is byte-identical; until now
-- every such retry inserted a row, repeated the handler's side effects and charged the daily budget
-- again. The identity is peer-scoped, because id_source_gt_net_message is only unique within the
-- sending instance. Locally originated rows leave it NULL and MariaDB treats NULLs as distinct, so they
-- are unaffected by the key.

-- Answers that hang off a row about to be removed go first. reply_to is ON DELETE SET NULL, so deleting
-- the parent alone would leave the answer behind detached from any thread.
DELETE r FROM gt_net_message r
  JOIN gt_net_message m ON r.reply_to = m.id_gt_net_message
  JOIN (
    SELECT id_gt_net, send_recv, id_source_gt_net_message, MIN(id_gt_net_message) AS keep_id
      FROM gt_net_message
     WHERE id_source_gt_net_message IS NOT NULL
     GROUP BY id_gt_net, send_recv, id_source_gt_net_message
    HAVING COUNT(*) > 1
  ) d ON  m.id_gt_net                = d.id_gt_net
      AND m.send_recv                = d.send_recv
      AND m.id_source_gt_net_message = d.id_source_gt_net_message
      AND m.id_gt_net_message       <> d.keep_id;

-- Keep the oldest row of every triple: its side effects are the ones the peer was told about.
DELETE m FROM gt_net_message m
  JOIN (
    SELECT id_gt_net, send_recv, id_source_gt_net_message, MIN(id_gt_net_message) AS keep_id
      FROM gt_net_message
     WHERE id_source_gt_net_message IS NOT NULL
     GROUP BY id_gt_net, send_recv, id_source_gt_net_message
    HAVING COUNT(*) > 1
  ) d ON  m.id_gt_net                = d.id_gt_net
      AND m.send_recv                = d.send_recv
      AND m.id_source_gt_net_message = d.id_source_gt_net_message
      AND m.id_gt_net_message       <> d.keep_id;

-- ADD UNIQUE has no IF NOT EXISTS, so the index is dropped first to keep the script re-runnable.
DROP INDEX IF EXISTS uk_gt_net_message_source ON gt_net_message;
ALTER TABLE gt_net_message
  ADD UNIQUE uk_gt_net_message_source (id_gt_net, send_recv, id_source_gt_net_message);

-- A token refresh is committed by the answerer while it is still handling the request, so a response
-- lost on the way back leaves the initiator calling with a token that is no longer recognised - and the
-- refresh that would repair it is itself authenticated. The replaced token therefore stays acceptable
-- for a bounded window. VARCHAR(32) rather than 36, because DataHelper.generateGUID strips the dashes.
ALTER TABLE gt_net_config ADD COLUMN IF NOT EXISTS token_this_previous VARCHAR(32) DEFAULT NULL
  COMMENT 'The token replaced by the last rotation, accepted until token_this_previous_valid_until';
ALTER TABLE gt_net_config ADD COLUMN IF NOT EXISTS token_this_previous_valid_until DATETIME DEFAULT NULL
  COMMENT 'UTC instant after which token_this_previous is no longer accepted';

-- Serving price data to a peer now requires an accepted, unrevoked grant for that peer and that kind -
-- a gt_net_config_entity row with exchange = 1 - on top of this instance's own accept flag. Possession
-- of a token and a globally open entity is no longer sufficient.
--
-- A peer that already exchanges data may never have had a grant written, because gt_net_config_entity
-- was only ever a record of acceptance and never read as a gate. Such a peer is grandfathered in, keyed
-- on gt_net_supplier_detail, which exists for exactly those peers that have really exchanged data. A
-- peer with a completed handshake but no exchange history has to request a grant.

-- Step 1: the entity row the grant hangs on, for the syncable kinds only - 0 = LAST_PRICE,
-- 1 = HISTORICAL_PRICES. SECURITY_METADATA (2) keeps the accept-flag gate and gets no grant.
-- UQ_gt_net_entity_kind makes INSERT IGNORE the whole guard. accept_request 1 = AC_OPEN and
-- server_state 1 = SS_OPEN are re-synchronised from the peer's own DTO on its next message anyway.
INSERT IGNORE INTO gt_net_entity (id_gt_net, entity_kind, server_state, accept_request, max_limit)
  SELECT DISTINCT sd.id_gt_net, sd.entity_kind, 1, 1, 300
    FROM gt_net_supplier_detail sd
    JOIN gt_net_config c ON c.id_gt_net = sd.id_gt_net
   WHERE c.token_this IS NOT NULL
     AND c.token_remote IS NOT NULL
     AND sd.entity_kind IN (0, 1);

-- Step 2: the grant itself. Both defaults are written explicitly, because the column defaults of
-- gt_net_config_entity (exchange 0, consumer_usage 0) differ from the entity's own defaults (true, 10).
INSERT IGNORE INTO gt_net_config_entity
    (id_gt_net_entity, exchange, consumer_usage, supplier_log, consumer_log)
  SELECT DISTINCT e.id_gt_net_entity, 1, 10, 1, 1
    FROM gt_net_entity e
    JOIN gt_net_supplier_detail sd
      ON sd.id_gt_net = e.id_gt_net AND sd.entity_kind = e.entity_kind
    JOIN gt_net_config c ON c.id_gt_net = e.id_gt_net
   WHERE c.token_this IS NOT NULL
     AND c.token_remote IS NOT NULL
     AND e.entity_kind IN (0, 1);
