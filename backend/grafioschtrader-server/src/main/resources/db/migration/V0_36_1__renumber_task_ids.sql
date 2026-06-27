-- Renumber task_data_change.id_task into the new TaskTypeBase (1-29) / TaskTypeExtended (30+) ranges
-- (GitHub issue #205). This is the single forward remap that corrects every persisted id_task value.
-- Because Flyway applies migrations in version order, this migration runs AFTER every earlier
-- migration on both upgrades and fresh installs, so the old-numbered rows that earlier migrations
-- insert (e.g. V0_33_5/6, V0_35_5, V0_36_0) are corrected here as the final step. No per-migration
-- date mapping is needed.
--
-- Mapping (old -> new):
--   TaskTypeBase:     MOVE_CREATED_BY_USER_TO_OTHER_USER 31 -> 80 (system 80+ band, not user-creatable)
--                     (15, 20, 22, 23, 24, 25, 26, 29 already inside 1-29 -> unchanged)
--   TaskTypeExtended: 0->30 1->31 2->32 3->33 4->34 5->35 6->36 7->37 8->38 9->39 10->40 11->41
--                     12->42 13->43 14->44 16->45 17->46 18->47 19->48 21->49 27->51 28->52 51->90
--                     (100 unchanged. ALGO_ALARM_INDICATOR_EVALUATION never reached production, so its
--                      former value 22 is intentionally NOT remapped and stays as base
--                      GTNET_EXCHANGE_LOG_AGGREGATION.)
--
-- The CASE switches on the ORIGINAL per-row value and assigns once, so every row is updated exactly
-- once inside a single transaction; the old/new range overlap (e.g. new 31 vs old base 31) is harmless
-- within one statement. A sentinel in globalparameters makes a manual re-execution a no-op (Flyway
-- itself only runs a versioned migration once).

DELIMITER //

DROP PROCEDURE IF EXISTS RenumberTaskDataChangeIds //

CREATE PROCEDURE RenumberTaskDataChangeIds()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM globalparameters
                 WHERE property_name = 'g.migration.task_id_renumber_done') THEN

    UPDATE task_data_change
    SET id_task = CASE id_task
      WHEN 0  THEN 30
      WHEN 1  THEN 31
      WHEN 2  THEN 32
      WHEN 3  THEN 33
      WHEN 4  THEN 34
      WHEN 5  THEN 35
      WHEN 6  THEN 36
      WHEN 7  THEN 37
      WHEN 8  THEN 38
      WHEN 9  THEN 39
      WHEN 10 THEN 40
      WHEN 11 THEN 41
      WHEN 12 THEN 42
      WHEN 13 THEN 43
      WHEN 14 THEN 44
      WHEN 16 THEN 45
      WHEN 17 THEN 46
      WHEN 18 THEN 47
      WHEN 19 THEN 48
      WHEN 21 THEN 49
      WHEN 27 THEN 51
      WHEN 28 THEN 52
      WHEN 51 THEN 90
      WHEN 31 THEN 80
      ELSE id_task
    END;

    INSERT INTO globalparameters (property_name, property_int, changed_by_system)
      VALUES ('g.migration.task_id_renumber_done', 1, 1);
  END IF;
END //

DELIMITER ;

CALL RenumberTaskDataChangeIds();
DROP PROCEDURE IF EXISTS RenumberTaskDataChangeIds;
