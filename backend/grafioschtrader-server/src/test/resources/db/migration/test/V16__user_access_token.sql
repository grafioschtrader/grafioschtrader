-- SECTION-BEGIN user_access_token
-- Personal access tokens of the reusable library, used by the MCP server for AI assistants. Only the SHA-256 hash of a
-- token is stored. Tokens are never exported; the account deletion removes them, and so does the foreign key.
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
 KEY FK_UserAccessToken_User (id_user),
 UNIQUE KEY UK_UserAccessToken_Hash (token_hash),
 UNIQUE KEY UK_UserAccessToken_Name (id_user, token_name),
 CONSTRAINT FK_UserAccessToken_User FOREIGN KEY (id_user) REFERENCES user(id_user) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- The foreign key gets an index of its own, so that UK_UserAccessToken_Name never becomes the index the constraint
-- depends on; otherwise MariaDB refuses to drop or change that unique key later. Covers tables created before the
-- index was declared above.
CREATE INDEX IF NOT EXISTS FK_UserAccessToken_User ON user_access_token (id_user);

-- At most 5 tokens per user, expired ones included until the purge task removes them 30 days after expiry.
INSERT IGNORE INTO entity_limit
 (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
 created_by, last_modified_by, last_modified_time, version)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value,
 0, 0, CURRENT_TIMESTAMP, 0
FROM (SELECT 'UserAccessToken' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
 CAST(NULL AS SIGNED) AS count_scope, 2 AS owner_scope,
 CAST(NULL AS CHAR) AS property_name, 5 AS default_value) x;

-- Creating and revoking tokens per user and day.
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
