-- An indicator column of the watchlist indicator tab can be assigned to several watchlists instead of one or all.
-- The assignment moves from the nullable column watchlist_indicator_column.id_watchlist into a join table; a column
-- without any row in it applies to all watchlists of its tenant, as NULL did before.
CREATE TABLE IF NOT EXISTS watchlist_indicator_column_watchlist (
  id_watchlist_indicator_column INT NOT NULL,
  id_watchlist INT NOT NULL,
  PRIMARY KEY (id_watchlist_indicator_column, id_watchlist),
  CONSTRAINT FK_WICW_Column FOREIGN KEY (id_watchlist_indicator_column)
    REFERENCES watchlist_indicator_column(id_watchlist_indicator_column) ON DELETE CASCADE,
  CONSTRAINT FK_WICW_Watchlist FOREIGN KEY (id_watchlist) REFERENCES watchlist(id_watchlist) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DELIMITER //
CREATE OR REPLACE PROCEDURE MigrateWatchlistIndicatorColumnWatchlist()
BEGIN
  IF EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'watchlist_indicator_column' AND COLUMN_NAME = 'id_watchlist') THEN
    INSERT IGNORE INTO watchlist_indicator_column_watchlist (id_watchlist_indicator_column, id_watchlist)
      SELECT id_watchlist_indicator_column, id_watchlist FROM watchlist_indicator_column WHERE id_watchlist IS NOT NULL;
    ALTER TABLE watchlist_indicator_column DROP FOREIGN KEY IF EXISTS FK_WIC_Watchlist;
    ALTER TABLE watchlist_indicator_column DROP COLUMN id_watchlist;
  END IF;
END //
DELIMITER ;
CALL MigrateWatchlistIndicatorColumnWatchlist();
DROP PROCEDURE IF EXISTS MigrateWatchlistIndicatorColumnWatchlist;

-- Where the value of a column is shown on the indicator tab: as a table column, in the expanded row, or both.
ALTER TABLE watchlist_indicator_column ADD COLUMN IF NOT EXISTS show_in_table TINYINT(1) NOT NULL DEFAULT 1;
ALTER TABLE watchlist_indicator_column ADD COLUMN IF NOT EXISTS show_in_expansion TINYINT(1) NOT NULL DEFAULT 0;
