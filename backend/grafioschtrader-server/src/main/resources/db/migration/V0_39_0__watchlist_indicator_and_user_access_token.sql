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

-- SECTION-BEGIN user_access_token
-- Access tokens of the reusable library, used by the MCP server for AI assistants: personal tokens created in the token
-- dialog, and the tokens issued to OAuth clients, which belong to an OAuth authorization (the token family of one
-- consent). Only the SHA-256 hash of a token is stored. Neither table is exported; the account deletion and the foreign
-- keys remove them. Every statement can run again, also on a database that already has the stage 1 table.
CREATE TABLE IF NOT EXISTS oauth_authorization (
 id_oauth_authorization INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 id_user INT NOT NULL,
 client_id VARCHAR(255) NOT NULL,
 client_name VARCHAR(100) NOT NULL,
 scope TINYINT NOT NULL,
 resource VARCHAR(255) NOT NULL,
 created_at DATETIME NOT NULL,
 expires_at DATETIME NOT NULL,
 revoked_at DATETIME NULL,
 last_refresh_at DATETIME NULL,
 KEY FK_OAuthAuthorization_User (id_user),
 UNIQUE KEY UK_OAuthAuthorization_Client (id_user, client_id),
 CONSTRAINT FK_OAuthAuthorization_User FOREIGN KEY (id_user) REFERENCES user(id_user) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS user_access_token (
 id_user_access_token INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 id_user INT NOT NULL,
 token_name VARCHAR(40) NOT NULL,
 token_hash CHAR(64) NOT NULL,
 token_prefix VARCHAR(12) NOT NULL,
 scope TINYINT NOT NULL,
 expires_at DATETIME NOT NULL,
 last_used_at DATETIME NULL,
 created_at DATETIME NOT NULL,
 token_kind TINYINT NOT NULL DEFAULT 1,
 id_oauth_authorization INT NULL,
 consumed_at DATETIME NULL,
 audience VARCHAR(255) NULL,
 KEY FK_UserAccessToken_User (id_user),
 UNIQUE KEY UK_UserAccessToken_Hash (token_hash),
 CONSTRAINT FK_UserAccessToken_User FOREIGN KEY (id_user) REFERENCES user(id_user) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- A table created by the stage 1 script lacks the OAuth columns. The foreign key on id_user gets an index of its own
-- before UK_UserAccessToken_Name goes, otherwise MariaDB keeps that unique key as the index of the constraint and
-- refuses to drop it. Name uniqueness of personal tokens is checked by the application instead.
ALTER TABLE user_access_token ADD COLUMN IF NOT EXISTS token_kind TINYINT NOT NULL DEFAULT 1;
ALTER TABLE user_access_token ADD COLUMN IF NOT EXISTS id_oauth_authorization INT NULL;
ALTER TABLE user_access_token ADD COLUMN IF NOT EXISTS consumed_at DATETIME NULL;
ALTER TABLE user_access_token ADD COLUMN IF NOT EXISTS audience VARCHAR(255) NULL;
CREATE INDEX IF NOT EXISTS FK_UserAccessToken_User ON user_access_token (id_user);
DROP INDEX IF EXISTS UK_UserAccessToken_Name ON user_access_token;
CREATE INDEX IF NOT EXISTS FK_UserAccessToken_OAuthAuthorization ON user_access_token (id_oauth_authorization);
ALTER TABLE user_access_token DROP FOREIGN KEY IF EXISTS FK_UserAccessToken_OAuthAuthorization;
ALTER TABLE user_access_token ADD CONSTRAINT FK_UserAccessToken_OAuthAuthorization FOREIGN KEY (id_oauth_authorization)
 REFERENCES oauth_authorization (id_oauth_authorization) ON DELETE CASCADE;

-- At most 5 personal tokens per user, expired ones included until the purge task removes them 30 days after expiry.
INSERT IGNORE INTO entity_limit
 (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
 created_by, last_modified_by, last_modified_time, version)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value,
 0, 0, CURRENT_TIMESTAMP, 0
FROM (SELECT 'UserAccessToken' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
 CAST(NULL AS SIGNED) AS count_scope, 2 AS owner_scope,
 CAST(NULL AS CHAR) AS property_name, 5 AS default_value) x;

-- At most 10 authorized OAuth clients per user; a new consent for the same client replaces the old one.
INSERT IGNORE INTO entity_limit
 (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
 created_by, last_modified_by, last_modified_time, version)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value,
 0, 0, CURRENT_TIMESTAMP, 0
FROM (SELECT 'OAuthAuthorization' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
 CAST(NULL AS SIGNED) AS count_scope, 2 AS owner_scope,
 CAST(NULL AS CHAR) AS property_name, 10 AS default_value) x;

-- Creating and revoking personal tokens per user and day.
INSERT IGNORE INTO entity_limit
 (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
 created_by, last_modified_by, last_modified_time, version)
SELECT 1, 'UserAccessToken', NULL, NULL, NULL, 10, 0, 0, CURRENT_TIMESTAMP, 0;

-- Tool calls of the MCP server per user and UTC day, for every user until an administrator adds a role or user row.
INSERT IGNORE INTO entity_limit
 (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
 created_by, last_modified_by, last_modified_time, version)
SELECT 2, 'McpToolCall', NULL, NULL, NULL, 500, 0, 0, CURRENT_TIMESTAMP, 0;
-- SECTION-END user_access_token
-- SECTION-BEGIN mcp_global_parameters
-- The administrator opens the MCP server (gt.mcp.use, closed by default) and sets the public address of the instance
-- that the token dialog and every OAuth address derive from (gt.mcp.public.base.url, 'none' offers personal tokens
-- only). The deployment switch gt.use.mcp only loads the code. A value the administrator set is kept; the input rule
-- is brought up to date.
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.mcp.use', 0, 0, 'enum:0,1')
  ON DUPLICATE KEY UPDATE input_rule = VALUES(input_rule);
INSERT INTO globalparameters (property_name, property_string, changed_by_system, input_rule)
  VALUES ('gt.mcp.public.base.url', 'none', 0,
  'pattern:^(none|https://[^ ?#]+|http://(localhost|127[.]0[.]0[.]1)(:[0-9]+)?(/[^ ?#]*)?)$')
  ON DUPLICATE KEY UPDATE input_rule = VALUES(input_rule);
-- SECTION-END mcp_global_parameters
-- SECTION-BEGIN release_note
-- Release note shown on the login screen: issues #284 and #285 of V0.39.0, plus #280 and #198, which were delivered in
-- V0.38.x without a release note of their own. The leading DELETE keeps the insert idempotent against the unique key
-- (version, language).
DELETE FROM release_note WHERE version = '0.39.0';
INSERT INTO release_note (version, language, note) VALUES ('0.39.0', 'EN',
  'AI assistants (MCP): assistants such as Claude or ChatGPT can read your portfolios, holdings, performance, dividends and transactions, read-only, through a personal access token or OAuth once the administrator has opened the server. Watchlist indicator columns can use fundamental base values of equities (earnings, book value and sales per share, shares outstanding, net debt), loaded weekly from several sources and cross-checked; ratios such as the P/E or the market capitalisation are calculated with the current price. A new security can be filled from ISIN, currency, stock exchange and asset class: freely accessible data providers supply the master data, and every suitable connector is checked with a trial request. User-defined security fields can be filled automatically by a rule from the master data, a Wikipedia lookup or a language model, and refreshed periodically.');
INSERT INTO release_note (version, language, note) VALUES ('0.39.0', 'DE',
  'KI-Assistenten (MCP): Assistenten wie Claude oder ChatGPT können Portfolios, Bestände, Performance, Dividenden und Transaktionen lesen, ausschliesslich lesend, über ein persönliches Zugriffstoken oder OAuth, sobald der Administrator den Server freigegeben hat. Indikatorspalten der Watchlist können fundamentale Basiswerte von Aktien verwenden (Gewinn, Buchwert und Umsatz je Aktie, Aktienanzahl, Nettoverschuldung), die wöchentlich aus mehreren Quellen geladen und gegenseitig geprüft werden; Kennzahlen wie das KGV oder die Marktkapitalisierung werden mit dem aktuellen Kurs berechnet. Ein neues Wertpapier lässt sich aus ISIN, Währung, Börse und Anlageklasse ausfüllen: Frei zugängliche Datenanbieter liefern die Stammdaten, und jeder passende Konnektor wird mit einer Testabfrage geprüft. Benutzerdefinierte Zusatzfelder für Wertpapiere lassen sich automatisch über eine Regel aus den Stammdaten, eine Wikipedia-Abfrage oder ein Sprachmodell füllen und periodisch aktualisieren.');
-- SECTION-END release_note
