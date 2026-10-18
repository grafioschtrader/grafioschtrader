-- Mirrors the fill source of the user-defined security fields of V0_38_3.
ALTER TABLE udf_metadata_security ADD COLUMN IF NOT EXISTS fill_source TINYINT NOT NULL DEFAULT 0
  COMMENT 'UDFFillSource: 0 manual, 1 template, 2 resolver, 3 language model';
ALTER TABLE udf_metadata_security ADD COLUMN IF NOT EXISTS fill_rule VARCHAR(1000) NULL
  COMMENT 'Rule of the fill source, for a template one or more lines with placeholders';
ALTER TABLE udf_metadata_security ADD COLUMN IF NOT EXISTS fill_rule_version INT NOT NULL DEFAULT 0
  COMMENT 'Incremented by the server whenever fill_source or fill_rule changes';
ALTER TABLE udf_metadata_security ADD COLUMN IF NOT EXISTS fill_period TINYINT NULL
  COMMENT 'UDFFillPeriod in months, 0 once; only for resolver and language model, null otherwise';

CREATE TABLE IF NOT EXISTS udf_data_fill (
  id_udf_metadata int(11) NOT NULL,
  id_entity int(11) NOT NULL,
  fill_source tinyint(4) NOT NULL COMMENT 'UDFFillSource that produced the row',
  fill_state tinyint(4) NOT NULL COMMENT 'UDFFillState: 0 applied, 1 not found, 2 locked, 3 proposed, 4 rejected, 5 discarded',
  rule_version int(11) NOT NULL COMMENT 'fill_rule_version of the field when the row was written',
  filled_value text DEFAULT NULL COMMENT 'The value the fill wrote or adopted',
  source_url varchar(2048) DEFAULT NULL,
  checked_time datetime NOT NULL COMMENT 'UTC time of the last definitive outcome',
  PRIMARY KEY (id_udf_metadata, id_entity),
  KEY FK_UdfDataFill_Security (id_entity),
  CONSTRAINT FK_UdfDataFill_UdfMetadataSecurity FOREIGN KEY (id_udf_metadata)
    REFERENCES udf_metadata_security (id_udf_metadata) ON DELETE CASCADE,
  CONSTRAINT FK_UdfDataFill_Security FOREIGN KEY (id_entity)
    REFERENCES security (id_securitycurrency) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- SECTION-BEGIN udf_fill_resolver_refresh_days
-- Mirrors the refresh interval of the fill source RESOLVER of V0_38_3.
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.udf.fill.resolver.refresh.days', 30, 0, 'min:1,max:365');

-- SECTION-BEGIN ai_request_log
-- Mirrors the language model request log, its two global parameters and the daily budget of V0_38_3.
CREATE TABLE IF NOT EXISTS ai_request_log (
  id_ai_request_log int(11) NOT NULL AUTO_INCREMENT,
  id_user int(11) NOT NULL,
  request_time datetime NOT NULL COMMENT 'UTC time of the reservation',
  purpose tinyint(4) NOT NULL COMMENT 'AIRequestPurpose: 1 propose rule, 2 suggest value',
  provider varchar(20) NOT NULL,
  model varchar(64) NOT NULL,
  status tinyint(4) NOT NULL COMMENT 'AIRequestStatus: 0 reserved, 1 succeeded, 2 failed',
  input_tokens int(11) DEFAULT NULL,
  output_tokens int(11) DEFAULT NULL,
  PRIMARY KEY (id_ai_request_log),
  KEY IdUserRequestTimeAIRL (id_user, request_time),
  KEY RequestTimeAIRL (request_time),
  CONSTRAINT FK_AIRequestLog_User FOREIGN KEY (id_user) REFERENCES user (id_user) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.ai.daily.requests.instance', 1000, 0, 'min:0,max:100000');
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.ai.request.log.retention.days', 90, 0, 'min:2,max:3650');

-- Daily budget of model round trips per user; an administrator's value is kept.
INSERT IGNORE INTO entity_limit (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value, created_by, last_modified_by, last_modified_time, version) SELECT 1, 'AIRequest', NULL, NULL, NULL, 500, 0, 0, CURRENT_TIMESTAMP, 0;

-- SECTION-BEGIN udf_fill_value_proposals
-- Mirrors the value proposals of a language model of V0_38_3.
ALTER TABLE udf_data_fill ADD COLUMN IF NOT EXISTS evidence VARCHAR(500) NULL
  COMMENT 'Excerpt of the source that names the proposed value';
ALTER TABLE udf_data_fill ADD COLUMN IF NOT EXISTS as_of_date DATE NULL
  COMMENT 'Date the source gives for the value, when it occurs in the source';
ALTER TABLE udf_data_fill ADD COLUMN IF NOT EXISTS verify_note VARCHAR(100) NULL
  COMMENT 'Reason of a DISCARDED or NOT_FOUND row';

INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.ai.suggest.max.per.run', 50, 0, 'min:1,max:500');

-- SECTION-BEGIN gt_ai_providers
-- Mirrors the language models in order of priority of V0_38_3; empty, so no model feature is offered in tests.
-- A provider list with models needs more than the former 30 characters of a string value
ALTER TABLE globalparameters MODIFY COLUMN property_string varchar(100) DEFAULT NULL;
-- The value 'none' switches the models off and gives the row its string type, which the edit dialog needs. input_rule
-- holds at most 100 characters; without IGNORE a longer rule fails instead of being cut off.
INSERT INTO globalparameters (property_name, property_string, changed_by_system, input_rule)
  VALUES ('gt.ai.providers', 'none', 0,
    'pattern:^(none|((anthropic|openai|gemini|ollama)(:[A-Za-z0-9._:/-]+)?(,(?!$)|$))+)$')
  ON DUPLICATE KEY UPDATE input_rule = VALUES(input_rule), property_string = IFNULL(property_string, 'none');

-- SECTION-BEGIN connector_apikey_length
-- An OpenAI project key has about 164 characters; encrypted with AES-256 and Base64-encoded it needs about 280
-- characters, more than the former 255. 512 holds a plain key of up to 351 characters.
ALTER TABLE connector_apikey MODIFY COLUMN api_key varchar(512) NOT NULL;

-- Home-tenant language-model credentials. Secrets never participate in data export or tenant transfer.
CREATE TABLE IF NOT EXISTS tenant_ai_provider (
  id_tenant_ai_provider INT NOT NULL AUTO_INCREMENT,
  id_tenant INT NOT NULL,
  provider VARCHAR(20) NOT NULL,
  model VARCHAR(64) NULL,
  priority TINYINT NOT NULL,
  api_key VARCHAR(512) NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id_tenant_ai_provider),
  UNIQUE KEY TenantProviderTAIP (id_tenant, provider),
  CONSTRAINT FK_TenantAIProvider_Tenant FOREIGN KEY (id_tenant) REFERENCES tenant (id_tenant) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

ALTER TABLE ai_request_log ADD COLUMN IF NOT EXISTS key_source TINYINT NOT NULL DEFAULT 2;
DROP INDEX IF EXISTS KeySourceRequestTimeAIRL ON ai_request_log;
ALTER TABLE ai_request_log ADD INDEX KeySourceRequestTimeAIRL (key_source, request_time);
ALTER TABLE tenant_ai_provider ADD COLUMN IF NOT EXISTS daily_request_limit INT NULL
  COMMENT 'Requests per UTC day the tenant allows over this key; null sets no tenant limit';
ALTER TABLE ai_request_log ADD COLUMN IF NOT EXISTS id_tenant_ai_provider INT NULL
  COMMENT 'The tenant key the request went over; null for an instance key. No foreign key: the log outlives a key';
DROP INDEX IF EXISTS TenantProviderRequestTimeAIRL ON ai_request_log;
ALTER TABLE ai_request_log ADD INDEX TenantProviderRequestTimeAIRL (id_tenant_ai_provider, request_time);

INSERT INTO globalparameters (property_name, property_string, changed_by_system, input_rule)
  VALUES ('gt.ai.tenant.providers', 'anthropic,openai,gemini', 0,
    'pattern:^(none|((anthropic|openai|gemini|ollama)(,(?!$)|$))+)$')
  ON DUPLICATE KEY UPDATE input_rule = VALUES(input_rule);
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.ai.instance.keys.propose.rule', 0, 0, 'enum:0,1')
  ON DUPLICATE KEY UPDATE input_rule = VALUES(input_rule);

INSERT IGNORE INTO entity_limit
  (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
   created_by, last_modified_by, last_modified_time, version)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value,
       0, 0, CURRENT_TIMESTAMP, 0
FROM (SELECT 'TenantAIProvider' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
             CAST(NULL AS SIGNED) AS count_scope, 0 AS owner_scope,
             CAST(NULL AS CHAR) AS property_name, 4 AS default_value) x;

INSERT IGNORE INTO entity_limit
  (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value,
   created_by, last_modified_by, last_modified_time, version)
SELECT 1, 'TenantAIProvider', NULL, NULL, NULL, 20, 0, 0, CURRENT_TIMESTAMP, 0;
