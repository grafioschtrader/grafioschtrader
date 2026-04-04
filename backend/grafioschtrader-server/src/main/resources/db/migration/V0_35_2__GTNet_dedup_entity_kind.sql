-- Remove duplicate gt_net_entity rows (same id_gt_net + entity_kind).
-- For each duplicate group, keep the row with the LOWEST id_gt_net_entity.
-- gt_net_config_entity has ON DELETE CASCADE from V0_35_1, so dependent rows are cleaned up automatically.

DELIMITER //
CREATE OR REPLACE PROCEDURE DeduplicateGtNetEntity()
BEGIN
  -- Delete duplicate rows where a lower-ID row exists for the same (id_gt_net, entity_kind)
  DELETE e FROM gt_net_entity e
  INNER JOIN (
    SELECT id_gt_net, entity_kind, MIN(id_gt_net_entity) AS keep_id
    FROM gt_net_entity
    GROUP BY id_gt_net, entity_kind
    HAVING COUNT(*) > 1
  ) dups ON e.id_gt_net = dups.id_gt_net
       AND e.entity_kind = dups.entity_kind
       AND e.id_gt_net_entity > dups.keep_id;
END //
DELIMITER ;
CALL DeduplicateGtNetEntity();
DROP PROCEDURE IF EXISTS DeduplicateGtNetEntity;

-- Add unique constraint to prevent future duplicates
DROP INDEX IF EXISTS UQ_gt_net_entity_kind ON gt_net_entity;
ALTER TABLE gt_net_entity ADD UNIQUE UQ_gt_net_entity_kind (id_gt_net, entity_kind);

-- Create missing gt_net_config_entity rows for entities that have accept_request > 0
-- but no config entity yet (e.g., peer gt16o3.duckdns.org).
INSERT IGNORE INTO gt_net_config_entity (id_gt_net_entity, exchange, consumer_usage, supplier_log, consumer_log)
SELECT e.id_gt_net_entity, 0, 0, 1, 1
FROM gt_net_entity e
LEFT JOIN gt_net_config_entity ce ON e.id_gt_net_entity = ce.id_gt_net_entity
WHERE ce.id_gt_net_entity IS NULL
  AND e.accept_request > 0;
