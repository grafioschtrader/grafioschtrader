-- GitHub issue #235: portable-host counterpart of the application delivery-attempt migration.
ALTER TABLE gt_net_message_attempt
  ADD COLUMN IF NOT EXISTS attempt_status TINYINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS try_count INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_attempt_timestamp DATETIME DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS last_error VARCHAR(1000) DEFAULT NULL;

UPDATE gt_net_message_attempt
   SET attempt_status = CASE WHEN has_send = 1 THEN 3 ELSE 0 END,
       try_count = CASE WHEN has_send = 1 THEN GREATEST(try_count, 1) ELSE try_count END,
       last_attempt_timestamp = CASE
         WHEN has_send = 1 THEN COALESCE(last_attempt_timestamp, send_timestamp)
         ELSE last_attempt_timestamp
       END;

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
