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
