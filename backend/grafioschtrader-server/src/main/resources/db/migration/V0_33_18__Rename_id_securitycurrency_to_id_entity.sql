ALTER TABLE gt_net_supplier_detail CHANGE COLUMN IF EXISTS id_securitycurrency id_entity INT;
UPDATE security SET active_to_date = "2032-12-31" WHERE active_to_date = "2025-12-31" ;

-- Child table for historical price data settings per supplier detail
DROP TABLE IF EXISTS gt_net_supplier_detail_hist;
CREATE TABLE gt_net_supplier_detail_hist (
  id_gt_net_supplier_detail INT NOT NULL,
  retry_history_load SMALLINT NOT NULL DEFAULT 0,
  history_min_date DATE NULL,
  history_max_date DATE NULL,
  ohl_percentage DOUBLE NULL,
  PRIMARY KEY (id_gt_net_supplier_detail),
  CONSTRAINT fk_supplier_detail_hist FOREIGN KEY (id_gt_net_supplier_detail)
    REFERENCES gt_net_supplier_detail (id_gt_net_supplier_detail) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Child table for intraday (last price) settings per supplier detail
DROP TABLE IF EXISTS gt_net_supplier_detail_last;
CREATE TABLE gt_net_supplier_detail_last (
  id_gt_net_supplier_detail INT NOT NULL,
  retry_intra_load SMALLINT NOT NULL DEFAULT 0,
  s_timestamp TIMESTAMP NULL,
  PRIMARY KEY (id_gt_net_supplier_detail),
  CONSTRAINT fk_supplier_detail_last FOREIGN KEY (id_gt_net_supplier_detail)
    REFERENCES gt_net_supplier_detail (id_gt_net_supplier_detail) ON DELETE CASCADE
) ENGINE=InnoDB;
