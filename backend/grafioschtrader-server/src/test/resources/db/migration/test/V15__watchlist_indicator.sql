-- Watchlist indicator definitions and their default limit.
CREATE TABLE IF NOT EXISTS watchlist_indicator_column (
 id_watchlist_indicator_column INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 id_tenant INT NOT NULL,
 id_watchlist INT NULL,
 label VARCHAR(40) NOT NULL,
 expression VARCHAR(500) NOT NULL,
 result_type TINYINT NOT NULL,
 id_benchmark INT NULL,
 decimals TINYINT NOT NULL DEFAULT 2,
 lower_mark DOUBLE NULL,
 upper_mark DOUBLE NULL,
 mark_colors TINYINT NOT NULL DEFAULT 0,
 ui_order SMALLINT NOT NULL,
 CONSTRAINT FK_WIC_Tenant FOREIGN KEY (id_tenant) REFERENCES tenant(id_tenant),
 CONSTRAINT FK_WIC_Watchlist FOREIGN KEY (id_watchlist) REFERENCES watchlist(id_watchlist) ON DELETE CASCADE,
 CONSTRAINT FK_WIC_Benchmark FOREIGN KEY (id_benchmark) REFERENCES securitycurrency(id_securitycurrency) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT IGNORE INTO entity_limit
 (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
 created_by, last_modified_by, last_modified_time, version)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value,
 0, 0, CURRENT_TIMESTAMP, 0
FROM (SELECT 'WatchlistIndicatorColumn' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
 CAST(NULL AS SIGNED) AS count_scope, 0 AS owner_scope,
 CAST(NULL AS CHAR) AS property_name, 20 AS default_value) x;

-- Bulk order changes count each affected column against the configurable daily budget.
INSERT IGNORE INTO entity_limit
 (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
 created_by, last_modified_by, last_modified_time, version)
SELECT 1, 'WatchlistIndicatorColumn', NULL, NULL, NULL, 200, 0, 0, CURRENT_TIMESTAMP, 0;

-- Fundamental base values of direct equities per security, metric and source, loaded weekly by the task
-- FUNDAMENTAL_DATA_LOAD and read by the watchlist indicator columns. Derived data: no export, no limit key.
-- metric, source, source_check and state hold the byte values of FundamentalMetric, FundamentalSource,
-- FundamentalSourceCheck and FundamentalState.
CREATE TABLE IF NOT EXISTS security_fundamental (
 id_securitycurrency INT NOT NULL,
 metric TINYINT NOT NULL,
 source TINYINT NOT NULL,
 value DOUBLE NOT NULL,
 currency CHAR(3) NULL,
 period_end DATE NULL,
 fetched_time DATETIME NOT NULL,
 source_check TINYINT NOT NULL,
 state TINYINT NOT NULL,
 PRIMARY KEY (id_securitycurrency, metric, source),
 CONSTRAINT FK_SecurityFundamental_Security FOREIGN KEY (id_securitycurrency)
   REFERENCES security (id_securitycurrency) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
