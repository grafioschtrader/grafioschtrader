-- Sharing of user-defined fields. share_mode 0 = not shared, 1 = other users may read the values, 2 = other users may
-- read and write them. A shared field keeps one value per entity in udf_data_shared instead of the udf_data rows of
-- its owner. A user adopts a shared field with a row in udf_metadata_subscription and chooses the own ui_order there.
-- udf_metadata.id_user NULL marks a shared field whose owner deleted it while other users still use it.
-- TINYINT without a display width, because the JDBC driver returns TINYINT(1) as Boolean.
ALTER TABLE udf_metadata ADD COLUMN IF NOT EXISTS share_mode TINYINT NOT NULL DEFAULT 0
  COMMENT 'UDFShareMode: 0 not shared, 1 read, 2 read and write';

CREATE TABLE IF NOT EXISTS udf_metadata_subscription (
  id_udf_metadata_subscription int(11) NOT NULL AUTO_INCREMENT,
  id_udf_metadata int(11) NOT NULL,
  id_user int(11) NOT NULL,
  ui_order smallint(2) NOT NULL,
  PRIMARY KEY (id_udf_metadata_subscription),
  UNIQUE KEY UK_UdfMetadataSubscription_Field_User (id_udf_metadata, id_user),
  KEY FK_UdfMetadataSubscription_User (id_user),
  CONSTRAINT FK_UdfMetadataSubscription_Metadata FOREIGN KEY (id_udf_metadata)
    REFERENCES udf_metadata (id_udf_metadata) ON DELETE CASCADE,
  CONSTRAINT FK_UdfMetadataSubscription_User FOREIGN KEY (id_user) REFERENCES user (id_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS udf_data_shared (
  id_udf_metadata int(11) NOT NULL,
  id_entity int(11) NOT NULL,
  entity varchar(20) NOT NULL COMMENT 'Entity name as in udf_data.entity',
  field_value text NOT NULL COMMENT 'Canonical text of the value, true or false for a boolean',
  PRIMARY KEY (id_udf_metadata, id_entity),
  KEY I_UdfDataShared_Entity (entity, id_entity),
  CONSTRAINT FK_UdfDataShared_Metadata FOREIGN KEY (id_udf_metadata)
    REFERENCES udf_metadata (id_udf_metadata) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT IGNORE INTO entity_limit
  (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value, created_by, last_modified_by, version)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value, 0, 0, 0
FROM (SELECT 'UDFMetadataSubscription' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
             CAST(NULL AS SIGNED) AS count_scope, 2 AS owner_scope,
             CAST(NULL AS CHAR) AS property_name, 50 AS default_value) x;

INSERT IGNORE INTO entity_limit
  (limit_type, entity_name, limit_value, created_by, last_modified_by, version)
SELECT 1, 'UDFMetadataSubscription', 20, 0, 0, 0;
