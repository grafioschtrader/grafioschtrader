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

-- GitHub issue #234: UTC day the two daily request counters in gt_net_config belong to.
ALTER TABLE gt_net_config ADD COLUMN IF NOT EXISTS daily_req_limit_date DATE DEFAULT NULL;

-- GitHub issue #237: the announced maintenance windows of a remote, and the day a remote announced it goes out of
-- operation. Both live in grafiosch-base, so the next regeneration of V0_10_0 carries them and these two statements
-- become no-ops.
CREATE TABLE IF NOT EXISTS gt_net_maintenance_window (
  id_gt_net_maintenance_window INT NOT NULL AUTO_INCREMENT,
  id_gt_net                    INT NOT NULL,
  id_gt_net_message            INT NOT NULL,
  from_date_time               DATETIME NOT NULL,
  to_date_time                 DATETIME NOT NULL,
  PRIMARY KEY (id_gt_net_maintenance_window),
  UNIQUE KEY uk_gt_net_maintenance_window (id_gt_net, from_date_time, to_date_time),
  KEY idx_gt_net_maintenance_window_active (id_gt_net, to_date_time),
  KEY idx_gt_net_maintenance_window_message (id_gt_net_message),
  CONSTRAINT fk_maintenance_window_gtnet FOREIGN KEY (id_gt_net)
    REFERENCES gt_net (id_gt_net) ON DELETE CASCADE,
  CONSTRAINT fk_maintenance_window_message FOREIGN KEY (id_gt_net_message)
    REFERENCES gt_net_message (id_gt_net_message) ON DELETE CASCADE
);

ALTER TABLE gt_net ADD COLUMN IF NOT EXISTS close_start_date DATE DEFAULT NULL;

-- Nothing writes SS_MAINTENANCE (3) any more; a row left in that state would stay there forever.
UPDATE gt_net_entity SET server_state = 0 WHERE server_state = 3;
