-- ---------------------------------------------------------------------------
-- Issue #172: prefer GTNet peers that supply complete open/high/low data.
-- ---------------------------------------------------------------------------
-- Weight in percent, because globalparameters has no floating point column: 50 makes a peer reporting 100% OHL score
-- 1.5 times an otherwise identical close-only peer. 0 disables the preference and restores pure coverage x success
-- rate ordering. Applies to securities only; currency pairs never report an OHL percentage.
DELETE FROM globalparameters WHERE property_name = 'gt.gtnet.ohl.weight';
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.gtnet.ohl.weight', 50, 0, 'min:0,max:200');

-- ---------------------------------------------------------------------------
-- Issue #235: distinguish queued, retryable and terminal per-target GTNet delivery outcomes.
-- ---------------------------------------------------------------------------
ALTER TABLE gt_net_message_attempt
  ADD COLUMN IF NOT EXISTS attempt_status TINYINT NOT NULL DEFAULT 0
    COMMENT '0 queued, 1 waiting handshake, 2 retryable failure, 3 delivered, 4 peer retired, 5 expired',
  ADD COLUMN IF NOT EXISTS try_count INT NOT NULL DEFAULT 0
    COMMENT 'Number of actual HTTP transmissions',
  ADD COLUMN IF NOT EXISTS last_attempt_timestamp DATETIME DEFAULT NULL
    COMMENT 'UTC time of the latest actual HTTP transmission',
  ADD COLUMN IF NOT EXISTS last_error VARCHAR(1000) DEFAULT NULL
    COMMENT 'Sanitized diagnostic from the latest failed transmission';

-- Existing rows can only tell success from not-yet-successful. Preserve that fact without inventing failures.
UPDATE gt_net_message_attempt
   SET attempt_status = CASE WHEN has_send = 1 THEN 3 ELSE 0 END,
       try_count = CASE WHEN has_send = 1 THEN GREATEST(try_count, 1) ELSE try_count END,
       last_attempt_timestamp = CASE
         WHEN has_send = 1 THEN COALESCE(last_attempt_timestamp, send_timestamp)
         ELSE last_attempt_timestamp
       END;

-- An aggregate FAILED written by the old one-run counter was retryable. Rebuild the status from what is known.
UPDATE gt_net_message m
  JOIN (
    SELECT id_gt_net_message, MAX(has_send) AS any_delivered
      FROM gt_net_message_attempt
     GROUP BY id_gt_net_message
  ) a ON a.id_gt_net_message = m.id_gt_net_message
   SET m.delivery_status = CASE WHEN a.any_delivered = 1 THEN 1 ELSE 0 END;

DROP INDEX IF EXISTS idx_attempt_pending ON gt_net_message_attempt;
ALTER TABLE gt_net_message_attempt
  ADD INDEX idx_attempt_pending (attempt_status, id_gt_net_message);

-- ---------------------------------------------------------------------------------------------
-- FINANCE_COST gets its own running total, so that the fee column of the performance report holds
-- bank account and depot costs alone. The financing of a margin position is a cost of that
-- position, not of the bank account, and it is already reported through the securities result; it
-- was folded into the fee bucket only because it otherwise belonged to no category and the
-- breakdown then no longer added up to the balance. With this column the six categories add up
-- again while the fee figure means what its label says.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE hold_cashaccount_balance
  ADD COLUMN IF NOT EXISTS finance_cost DOUBLE NOT NULL DEFAULT 0
    COMMENT 'Running FINANCE_COST total, in the currency of the cash account';

-- ---------------------------------------------------------------------------------------------
-- One rebuild covers two reasons.
--
-- Issue #219 point 4: hold_cashaccount_balance.balance and the three hold_cashaccount_deposit
-- amounts are no longer rounded on write, because the incremental replay seeds its accumulator
-- from the stored value and a rounded seed offsets the whole remainder of the series against a
-- full rebuild. Existing rows still carry the rounded values and would keep seeding replays with
-- them.
--
-- And the finance_cost column above: existing rows carry FINANCE_COST inside fee, which no SQL can
-- split apart afterwards, so the running totals have to be accumulated again from the transactions.
--
-- A NULL id_entity means "all tenants" in
-- RebuildHolingAllTenantOrSingleTask; 39 = TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT,
-- 30 = TaskDataExecPriority.PRIO_LOW, 0 = progress state WAITING.
-- ---------------------------------------------------------------------------------------------
DELETE FROM task_data_change WHERE id_task = 39 AND id_entity IS NULL AND progress_state = 0;
INSERT INTO task_data_change (id_task, execution_priority, entity, id_entity, earliest_start_time,
    creation_time, progress_state)
  VALUES (39, 30, NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0);
