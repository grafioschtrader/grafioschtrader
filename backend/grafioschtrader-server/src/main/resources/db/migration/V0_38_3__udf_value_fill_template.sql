-- =============================================================================
-- Automatic filling of user-defined security fields, template rules
--
-- A user-defined security field gets a fill source that computes its value instead of the user typing it. The fill
-- source, its rule and a server-maintained rule version live on udf_metadata_security, because every placeholder of
-- a rule reads a security attribute. udf_data_fill records per field and security whether a value was written or
-- adopted automatically, or whether the user took the cell over; a value without such a row is manual.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS and CREATE TABLE IF NOT EXISTS.
-- =============================================================================

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

-- =============================================================================
-- Refresh interval of the fill source RESOLVER
--
-- A value a resolver found, and the answer that a source has none, are looked up again after this many days. An
-- administrator's value is kept.
-- =============================================================================

INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.udf.fill.resolver.refresh.days', 30, 0, 'min:1,max:365');

-- =============================================================================
-- Language model requests of the user-defined field fill
--
-- Every round trip to a language model reserves a row before the call, so that a failed call or a crash still
-- counts against the budgets: the daily budget per user (DAY_CUD pseudo entity AIRequest) and the instance budget
-- gt.ai.daily.requests.instance. The rows are an operational log, deleted after gt.ai.request.log.retention.days.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS and INSERT IGNORE.
-- =============================================================================

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

-- =============================================================================
-- Value proposals of a language model
--
-- A field with the fill source LLM gets values proposed by a language model, each with the excerpt of the source
-- that names it. A proposal is verified against the fetched source before the user sees it and is never written
-- into udf_data before the user accepts it. gt.ai.suggest.max.per.run bounds the securities of one paid run.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS and INSERT IGNORE.
-- =============================================================================

ALTER TABLE udf_data_fill ADD COLUMN IF NOT EXISTS evidence VARCHAR(500) NULL
  COMMENT 'Excerpt of the source that names the proposed value';
ALTER TABLE udf_data_fill ADD COLUMN IF NOT EXISTS as_of_date DATE NULL
  COMMENT 'Date the source gives for the value, when it occurs in the source';
ALTER TABLE udf_data_fill ADD COLUMN IF NOT EXISTS verify_note VARCHAR(100) NULL
  COMMENT 'Reason of a DISCARDED or NOT_FOUND row';

INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.ai.suggest.max.per.run', 50, 0, 'min:1,max:500');

-- =============================================================================
-- CornèrTrader commission tiers use the portfolio total, including cash
--
-- The single-security transaction report cannot supply fixedAssets (the securities account's market value).
-- portfolioTotal already reads hold_daily_total at the last available close before the hypothetical sale.
-- Replace the variable in all periods and comments: the estimator detects required inputs in the entire YAML.
-- Keep rates, minimum fees, custody rules and administrator edits; only the old explanatory sentences are corrected.
-- Select the shared plan by either translation, not by an installation-specific id. Account overrides are untouched.
-- Idempotent: an updated plan no longer contains fixedAssets.
-- =============================================================================
UPDATE trading_platform_plan tpp
SET tpp.fee_model_yaml = REPLACE(
  REPLACE(REPLACE(REPLACE(REPLACE(tpp.fee_model_yaml,
    'Gesamt-Depotwert ab. Wenn fixedAssets=0 oder nicht gesetzt ist,',
    'Portfolio-Gesamtwert inklusive Kontoguthaben ab.'),
    'wird immer Consistency (teuerster Tarif) angewendet.',
    'Massgeblich ist der letzte verfügbare Tageswert vor dem Verkauf.'),
    'Für korrekte Ergebnisse muss fixedAssets dem tatsächlichen',
    'Fehlt dieser Tageswert, bleibt die Schätzung unvollständig.'),
    'Depotwert entsprechen!',
    'portfolioTotal ist in der Währung des Portfolios angegeben.'),
  'fixedAssets', 'portfolioTotal')
WHERE tpp.fee_model_yaml LIKE '%fixedAssets%'
  AND EXISTS (
    SELECT 1 FROM multilinguestrings mss
    WHERE mss.id_string = tpp.platform_plan_name_nls
      AND ((mss.language = 'de' AND mss.text = 'CornèrTrader Transaktionsbetrag')
        OR (mss.language = 'en' AND mss.text = 'CornèrTrader Transactions value')));

-- =============================================================================
-- CornèrTrader FX: offline cash conversion and automatic conversion after a trade
--
-- Source checked 2026-10-15: https://www.cornertrader.ch/de/preise/ lists 0.5% for both kinds of conversion.
-- Apply the same tariff to earlier dates as an explicit assumption, starting with the commission model in 2009.
-- The portfolio report uses TRANSFER for cash plus net sale proceeds converted to its main currency; TRADE applies
-- when a security settles in another currency. Expressions return percentages (0.5 means 0.5%, not 50%).
-- Preserve existing FX definitions, including quoted keys and CRLF documents. A second run changes nothing.
-- =============================================================================
UPDATE trading_platform_plan tpp
SET tpp.fee_model_yaml = CONCAT(tpp.fee_model_yaml, CHAR(10),
'fx:
  periods:
    - validFrom: "2009-01-01"
      validTo: "2026-10-14"
      status: ASSUMED
      source: "https://www.cornertrader.ch/de/preise/"
      note: "The 0.5% tariff checked on 2026-10-15 is assumed for earlier dates; historical rates are not verified."
      rules:
        - name: "Same currency"
          condition: "payCurrency == receiveCurrency"
          expression: "0"
        - name: "CornèrTrader currency conversion (historical assumption)"
          condition: "kind == ''TRADE'' || kind == ''TRANSFER''"
          expression: "0.5"
    - validFrom: "2026-10-15"
      status: VERIFIED
      source: "https://www.cornertrader.ch/de/preise/"
      note: "Checked on 2026-10-15: offline cash conversion and automatic currency conversion following trades."
      rules:
        - name: "Same currency"
          condition: "payCurrency == receiveCurrency"
          expression: "0"
        - name: "CornèrTrader currency conversion"
          condition: "kind == ''TRADE'' || kind == ''TRANSFER''"
          expression: "0.5"
')
WHERE tpp.fee_model_yaml IS NOT NULL AND TRIM(tpp.fee_model_yaml) <> ''
  AND tpp.fee_model_yaml NOT REGEXP '(?m)^[[:blank:]]*["'']?fx["'']?[[:blank:]]*:'
  AND EXISTS (
    SELECT 1 FROM multilinguestrings mss
    WHERE mss.id_string = tpp.platform_plan_name_nls
      AND ((mss.language = 'de' AND mss.text = 'CornèrTrader Transaktionsbetrag')
        OR (mss.language = 'en' AND mss.text = 'CornèrTrader Transactions value')));

-- =============================================================================
-- Language models in order of priority
--
-- The administrator lists the language models in gt.ai.providers, comma-separated and each optionally with a model,
-- for example gemini:gemini-2.5-flash,openai. A technical failure of one moves on to the next. The value none switches
-- every model feature off. Their API keys are connector API keys; only the Ollama endpoint stays a deployment property.
-- =============================================================================

-- A provider list with models needs more than the former 30 characters of a string value
ALTER TABLE globalparameters MODIFY COLUMN property_string varchar(100) DEFAULT NULL;
-- The value 'none' switches the models off and gives the row its string type, which the edit dialog needs. input_rule
-- holds at most 100 characters; without IGNORE a longer rule fails instead of being cut off.
INSERT INTO globalparameters (property_name, property_string, changed_by_system, input_rule)
  VALUES ('gt.ai.providers', 'none', 0,
    'pattern:^(none|((anthropic|openai|gemini|ollama)(:[A-Za-z0-9._:/-]+)?(,(?!$)|$))+)$')
  ON DUPLICATE KEY UPDATE input_rule = VALUES(input_rule), property_string = IFNULL(property_string, 'none');

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
