-- Store calendar/business times and UTC instants without session-zone conversion.
-- Generated from information_schema.COLUMNS; preserve NULL/default/comment and last-change semantics.
-- Earlier application sessions used the server's global zone. Convert there to retain the digits users saw.
-- The pool now starts every connection in UTC, including the Flyway connection.
-- Reapplying is harmless: DATETIME -> DATETIME does not reinterpret stored values.
-- The legacy import-platform default is a zero date. Preserve it even on servers now rejecting zero dates.
SET @gt_time_zone_saved_sql_mode = @@sql_mode;
SET sql_mode = TRIM(BOTH ',' FROM REPLACE(REPLACE(CONCAT(',', @@sql_mode, ','),
  ',NO_ZERO_DATE,', ','), ',NO_ZERO_IN_DATE,', ','));
SET time_zone = @@global.time_zone;

-- Creation times never change on UPDATE; other ON UPDATE clauses are retained.
ALTER TABLE `algo_message_alert` MODIFY COLUMN `alert_time` DATETIME NOT NULL DEFAULT current_timestamp() ON UPDATE CURRENT_TIMESTAMP();
ALTER TABLE `assetclass` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `assetclass` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `bankrupt_security` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `bankrupt_security` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `dividend` MODIFY COLUMN `create_modify_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `entity_limit` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `entity_limit` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `generic_connector_def` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `generic_connector_def` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `globalparameters` MODIFY COLUMN `property_date_time` DATETIME NULL DEFAULT NULL;
ALTER TABLE `gt_net` MODIFY COLUMN `last_modified_time` DATETIME NULL DEFAULT current_timestamp() ON UPDATE CURRENT_TIMESTAMP();
ALTER TABLE `gt_net_config` MODIFY COLUMN `supplier_last_update` DATETIME NULL DEFAULT NULL;
ALTER TABLE `gt_net_config` MODIFY COLUMN `handshake_timestamp` DATETIME NULL DEFAULT NULL COMMENT 'UTC timestamp when first successful handshake completed';
ALTER TABLE `gt_net_config` MODIFY COLUMN `reconnect_requested_time` DATETIME NULL DEFAULT NULL COMMENT 'UTC instant of the last first contact refused because a handshake with this peer already exists';
ALTER TABLE `gt_net_exchange_log` MODIFY COLUMN `timestamp` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `gt_net_lastprice` MODIFY COLUMN `timestamp` DATETIME NULL DEFAULT NULL;
ALTER TABLE `gt_net_message` MODIFY COLUMN `timestamp` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `gt_net_message_attempt` MODIFY COLUMN `send_timestamp` DATETIME NULL DEFAULT NULL COMMENT 'When successfully delivered (UTC)';
ALTER TABLE `gt_net_supplier_detail_last` MODIFY COLUMN `s_timestamp` DATETIME NULL DEFAULT NULL;
ALTER TABLE `historyquote` MODIFY COLUMN `create_modify_time` DATETIME NOT NULL DEFAULT current_timestamp() ON UPDATE CURRENT_TIMESTAMP();
ALTER TABLE `historyquote_update_log` MODIFY COLUMN `update_timestamp` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `hold_cashaccount_balance` MODIFY COLUMN `valid_timestamp` DATETIME NOT NULL DEFAULT current_timestamp() ON UPDATE CURRENT_TIMESTAMP();
ALTER TABLE `hold_cashaccount_deposit` MODIFY COLUMN `valid_timestamp` DATETIME NOT NULL DEFAULT current_timestamp() ON UPDATE CURRENT_TIMESTAMP();
ALTER TABLE `hold_securityaccount_security` MODIFY COLUMN `valid_timestamp` DATETIME NOT NULL DEFAULT current_timestamp() ON UPDATE CURRENT_TIMESTAMP();
ALTER TABLE `imp_trans_platform` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `imp_trans_platform` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT '0000-00-00 00:00:00';
ALTER TABLE `imp_trans_pos` MODIFY COLUMN `transaction_time` DATETIME NULL DEFAULT NULL;
ALTER TABLE `imp_trans_template` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `imp_trans_template` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `propose_request` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `propose_request` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `risk_free_rate_mapping` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `risk_free_rate_mapping` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `security` MODIFY COLUMN `div_earliest_next_check` DATETIME NULL DEFAULT NULL;
ALTER TABLE `securitycurrency` MODIFY COLUMN `full_load_timestamp` DATETIME NULL DEFAULT NULL;
ALTER TABLE `securitycurrency` MODIFY COLUMN `s_timestamp` DATETIME NOT NULL DEFAULT current_timestamp() ON UPDATE CURRENT_TIMESTAMP();
ALTER TABLE `securitycurrency` MODIFY COLUMN `gt_net_last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `securitycurrency` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `securitycurrency` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `securitysplit` MODIFY COLUMN `create_modify_time` DATETIME NOT NULL DEFAULT current_timestamp() ON UPDATE CURRENT_TIMESTAMP();
ALTER TABLE `security_action` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `security_action_application` MODIFY COLUMN `applied_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `security_transfer` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `stockexchange` MODIFY COLUMN `last_direct_price_update` DATETIME NOT NULL DEFAULT (current_timestamp() + interval -72 hour);
ALTER TABLE `stockexchange` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `stockexchange` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `task_data_change` MODIFY COLUMN `earliest_start_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `task_data_change` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `task_data_change` MODIFY COLUMN `exec_start_time` DATETIME NULL DEFAULT NULL;
ALTER TABLE `task_data_change` MODIFY COLUMN `exec_end_time` DATETIME NULL DEFAULT NULL;
ALTER TABLE `tax_upload` MODIFY COLUMN `upload_date` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `trading_calendar_rule_set` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `trading_calendar_rule_set` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `trading_platform_plan` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `trading_platform_plan` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `transaction` MODIFY COLUMN `transaction_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `user` MODIFY COLUMN `last_role_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `user` MODIFY COLUMN `creation_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `user` MODIFY COLUMN `last_modified_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `watchlist` MODIFY COLUMN `last_timestamp` DATETIME NULL DEFAULT NULL;

-- Only these columns were exclusively filled by the database clock: preserve their actual UTC instant.
SET time_zone = '+00:00';
ALTER TABLE `mail_send_recv` MODIFY COLUMN `send_recv_time` DATETIME NOT NULL DEFAULT current_timestamp();
ALTER TABLE `standing_order_failure` MODIFY COLUMN `created_at` DATETIME NOT NULL DEFAULT current_timestamp();

SET sql_mode = @gt_time_zone_saved_sql_mode;

-- The Weka transaction-cost prototype is gone; the YAML fee models estimate transaction costs.
ALTER TABLE securityaccount DROP COLUMN IF EXISTS weka_model;
