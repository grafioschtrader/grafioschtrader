ALTER TABLE tenant MODIFY COLUMN tenant_kind_type TINYINT NULL;
ALTER TABLE tenant MODIFY COLUMN currency CHAR(3) NULL;
ALTER TABLE user ADD COLUMN IF NOT EXISTS home_tenant_read_only TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS tenant_access (
  id_tenant_access INT NOT NULL AUTO_INCREMENT,
  id_user INT NOT NULL,
  id_tenant INT NOT NULL,
  access_level TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id_tenant_access),
  UNIQUE KEY uq_tenant_access_user_tenant (id_user, id_tenant),
  CONSTRAINT fk_tenant_access_user FOREIGN KEY (id_user) REFERENCES user (id_user) ON DELETE CASCADE,
  CONSTRAINT fk_tenant_access_tenant FOREIGN KEY (id_tenant) REFERENCES tenant (id_tenant) ON DELETE CASCADE
);
