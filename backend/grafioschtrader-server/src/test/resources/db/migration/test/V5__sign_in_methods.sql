-- SECTION-BEGIN sign-in-password-reset
ALTER TABLE user ADD COLUMN IF NOT EXISTS auth_version INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS user_password_reset (
  id_user INT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expiry_date DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id_user),
  UNIQUE KEY UK_UserPasswordReset_Hash (token_hash),
  CONSTRAINT FK_UserPasswordReset_User FOREIGN KEY (id_user) REFERENCES user (id_user) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT IGNORE INTO globalparameters (property_name, property_string, changed_by_system, input_rule)
VALUES ('g.public.base.url', 'none', 0,
  'pattern:^(none|https://[^ ?#]+|http://(localhost|127[.]0[.]0[.]1)(:[0-9]+)?(/[^ ?#]*)?)$');
UPDATE globalparameters n JOIN globalparameters o ON o.property_name = 'gt.mcp.public.base.url'
SET n.property_string = o.property_string, n.changed_by_system = o.changed_by_system
WHERE n.property_name = 'g.public.base.url' AND n.property_string = 'none';
DELETE FROM globalparameters WHERE property_name = 'gt.mcp.public.base.url';
-- SECTION-END sign-in-password-reset

-- SECTION-BEGIN sign-in-totp
CREATE TABLE IF NOT EXISTS user_totp (
  id_user INT NOT NULL,
  secret VARCHAR(512) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  last_used_step BIGINT NULL,
  enabled_at DATETIME NULL,
  pending_secret VARCHAR(512) NULL,
  pending_created_at DATETIME NULL,
  PRIMARY KEY (id_user),
  CONSTRAINT FK_UserTotp_User FOREIGN KEY (id_user) REFERENCES user (id_user) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS user_recovery_code (
  id_user_recovery_code INT NOT NULL AUTO_INCREMENT,
  id_user INT NOT NULL,
  code_hash CHAR(64) NOT NULL,
  used_at DATETIME NULL,
  PRIMARY KEY (id_user_recovery_code),
  UNIQUE KEY UK_UserRecoveryCode_UserHash (id_user, code_hash),
  CONSTRAINT FK_UserRecoveryCode_User FOREIGN KEY (id_user) REFERENCES user (id_user) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
-- SECTION-END sign-in-totp

-- SECTION-BEGIN sign-in-passkeys
-- The WebAuthn user handle of a user: 32 random bytes, created on the first passkey registration and kept for the life
-- of the user. It is no secret, but it must not reveal the account, which is why it is not the user id.
ALTER TABLE user ADD COLUMN IF NOT EXISTS passkey_user_handle VARBINARY(64) NULL;
DROP INDEX IF EXISTS UK_User_PasskeyUserHandle ON user;
CREATE UNIQUE INDEX UK_User_PasskeyUserHandle ON user (passkey_user_handle);

CREATE TABLE IF NOT EXISTS user_passkey (
  id_user_passkey INT NOT NULL AUTO_INCREMENT,
  id_user INT NOT NULL,
  label VARCHAR(40) NOT NULL,
  credential_id VARBINARY(1023) NOT NULL,
  public_key BLOB NOT NULL,
  signature_count BIGINT NOT NULL,
  uv_initialized TINYINT(1) NOT NULL,
  backup_eligible TINYINT(1) NOT NULL,
  backup_state TINYINT(1) NOT NULL,
  transports VARCHAR(100) NULL,
  attestation_object BLOB NOT NULL,
  attestation_client_data_json BLOB NULL,
  created_at DATETIME NOT NULL,
  last_used_at DATETIME NULL,
  PRIMARY KEY (id_user_passkey),
  KEY FK_UserPasskey_User (id_user),
  UNIQUE KEY UK_UserPasskey_Credential (credential_id),
  CONSTRAINT FK_UserPasskey_User FOREIGN KEY (id_user) REFERENCES user (id_user) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- At most 10 passkeys per user, and 10 registrations or deletions per user and day.
INSERT IGNORE INTO entity_limit
 (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
 created_by, last_modified_by, last_modified_time, version)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value,
 0, 0, CURRENT_TIMESTAMP, 0
FROM (SELECT 'UserPasskey' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
 CAST(NULL AS SIGNED) AS count_scope, 2 AS owner_scope,
 CAST(NULL AS CHAR) AS property_name, 10 AS default_value) x;
INSERT IGNORE INTO entity_limit
 (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
 created_by, last_modified_by, last_modified_time, version)
SELECT 1, 'UserPasskey', NULL, NULL, NULL, 10, 0, 0, CURRENT_TIMESTAMP, 0;
-- SECTION-END sign-in-passkeys
