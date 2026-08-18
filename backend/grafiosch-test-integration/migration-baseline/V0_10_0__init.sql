/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `connector_apikey` (
  `id_provider` varchar(32) NOT NULL,
  `api_key` varchar(255) NOT NULL,
  `subscription_type` smallint(6) DEFAULT NULL,
  PRIMARY KEY (`id_provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `entity_limit` (
  `id_entity_limit` int(11) NOT NULL AUTO_INCREMENT,
  `limit_type` tinyint(4) NOT NULL,
  `entity_name` varchar(40) NOT NULL,
  `relation_entity_name` varchar(40) DEFAULT NULL,
  `count_scope` tinyint(4) DEFAULT NULL,
  `owner_scope` tinyint(4) DEFAULT NULL,
  `id_role` int(11) DEFAULT NULL,
  `id_user` int(11) DEFAULT NULL,
  `limit_value` int(11) NOT NULL,
  `valid_until` date DEFAULT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  `uk_relation_entity_name` varchar(40) GENERATED ALWAYS AS (coalesce(`relation_entity_name`,'')) STORED,
  `uk_count_scope` tinyint(4) GENERATED ALWAYS AS (coalesce(`count_scope`,-1)) STORED,
  `uk_owner_scope` tinyint(4) GENERATED ALWAYS AS (coalesce(`owner_scope`,-1)) STORED,
  `uk_id_role` int(11) GENERATED ALWAYS AS (coalesce(`id_role`,0)) STORED,
  `uk_id_user` int(11) GENERATED ALWAYS AS (coalesce(`id_user`,0)) STORED,
  PRIMARY KEY (`id_entity_limit`),
  UNIQUE KEY `El_unique` (`limit_type`,`entity_name`,`uk_relation_entity_name`,`uk_count_scope`,`uk_owner_scope`,`uk_id_role`,`uk_id_user`),
  KEY `El_lookup` (`limit_type`,`entity_name`),
  KEY `FK_EntityLimit_User` (`id_user`),
  KEY `FK_EntityLimit_Role` (`id_role`),
  CONSTRAINT `FK_EntityLimit_Role` FOREIGN KEY (`id_role`) REFERENCES `role` (`id_role`),
  CONSTRAINT `FK_EntityLimit_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE,
  CONSTRAINT `CK_EntityLimit_Scope` CHECK (`id_role` is null or `id_user` is null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `globalparameters` (
  `property_name` varchar(45) NOT NULL,
  `property_int` int(11) DEFAULT NULL,
  `property_string` varchar(30) DEFAULT NULL,
  `property_date` date DEFAULT NULL,
  `property_date_time` timestamp NULL DEFAULT NULL,
  `property_blob` blob DEFAULT NULL,
  `changed_by_system` tinyint(1) DEFAULT 0,
  `input_rule` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`property_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net` (
  `id_gt_net` int(11) NOT NULL AUTO_INCREMENT,
  `domain_remote_name` varchar(128) NOT NULL,
  `time_zone` varchar(50) NOT NULL,
  `spread_capability` tinyint(1) NOT NULL DEFAULT 0,
  `daily_req_limit` int(11) DEFAULT NULL,
  `allow_server_creation` tinyint(1) NOT NULL DEFAULT 0,
  `server_busy` tinyint(1) NOT NULL DEFAULT 0,
  `server_online` tinyint(1) NOT NULL DEFAULT 0,
  `last_modified_time` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id_gt_net`),
  UNIQUE KEY `domainRemoteName` (`domain_remote_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_config` (
  `id_gt_net` int(11) NOT NULL,
  `token_this` varchar(32) DEFAULT NULL,
  `token_remote` varchar(32) NOT NULL,
  `daily_req_limit_count` int(11) DEFAULT NULL,
  `daily_req_limit_remote_count` int(11) DEFAULT NULL,
  `supplier_last_update` timestamp NULL DEFAULT NULL,
  `serverlist_access_granted` tinyint(1) NOT NULL DEFAULT 0,
  `request_violation_count` tinyint(2) NOT NULL DEFAULT 0,
  `handshake_timestamp` timestamp NULL DEFAULT NULL COMMENT 'UTC timestamp when first successful handshake completed',
  `connection_timeout` tinyint(4) DEFAULT NULL,
  PRIMARY KEY (`id_gt_net`),
  CONSTRAINT `FK_GTNetConfig_GTNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_config_entity` (
  `id_gt_net_entity` int(11) NOT NULL,
  `exchange` tinyint(1) NOT NULL DEFAULT 0,
  `consumer_usage` int(11) NOT NULL DEFAULT 0,
  `supplier_log` tinyint(4) NOT NULL DEFAULT 1,
  `consumer_log` tinyint(4) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id_gt_net_entity`),
  CONSTRAINT `FK_GtNetConfigEntity_GtNetEntity` FOREIGN KEY (`id_gt_net_entity`) REFERENCES `gt_net_entity` (`id_gt_net_entity`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_entity` (
  `id_gt_net_entity` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net` int(11) NOT NULL,
  `entity_kind` tinyint(1) NOT NULL,
  `server_state` tinyint(1) NOT NULL,
  `accept_request` tinyint(1) NOT NULL,
  `max_limit` smallint(5) NOT NULL,
  PRIMARY KEY (`id_gt_net_entity`),
  UNIQUE KEY `UQ_gt_net_entity_kind` (`id_gt_net`,`entity_kind`),
  KEY `FK_GtNetEntity_GtNet` (`id_gt_net`),
  CONSTRAINT `FK_GtNetEntity_GtNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_exchange_log` (
  `id_gt_net_exchange_log` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net` int(11) NOT NULL,
  `entity_kind` tinyint(4) NOT NULL,
  `log_as_supplier` tinyint(1) NOT NULL,
  `period_type` tinyint(4) NOT NULL,
  `period_start` date NOT NULL,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `entities_sent` int(11) NOT NULL DEFAULT 0,
  `entities_updated` int(11) NOT NULL DEFAULT 0,
  `entities_in_response` int(11) NOT NULL DEFAULT 0,
  `request_count` int(11) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id_gt_net_exchange_log`),
  KEY `FK_GtNetExchangeLog_GtNet` (`id_gt_net`),
  CONSTRAINT `FK_GtNetExchangeLog_GtNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_message` (
  `id_gt_net_message` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net` int(11) NOT NULL,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `send_recv` tinyint(1) NOT NULL,
  `id_source_gt_net_message` int(11) DEFAULT NULL,
  `reply_to` int(11) DEFAULT NULL,
  `id_original_message` int(11) DEFAULT NULL COMMENT 'Reference to original announcement for cancellation messages',
  `message_code` tinyint(3) DEFAULT NULL,
  `message` varchar(1000) DEFAULT NULL,
  `visibility` tinyint(1) NOT NULL DEFAULT 0,
  `error_msg_code` varchar(50) DEFAULT NULL,
  `has_been_read` tinyint(1) NOT NULL DEFAULT 0,
  `wait_days_apply` smallint(4) NOT NULL DEFAULT 0,
  `delivery_status` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_gt_net_message`),
  KEY `FK_GtNetMessage_GtNet` (`id_gt_net`),
  KEY `FK_GtNetMessage_GtNetMessage` (`reply_to`),
  CONSTRAINT `FK_GtNetMessage_GtNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE,
  CONSTRAINT `FK_GtNetMessage_GtNetMessage` FOREIGN KEY (`reply_to`) REFERENCES `gt_net_message` (`id_gt_net_message`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_message_answer` (
  `id_gt_net_message_answer` int(11) NOT NULL AUTO_INCREMENT,
  `request_msg_code` tinyint(3) NOT NULL,
  `response_msg_code` tinyint(3) NOT NULL,
  `priority` tinyint(1) NOT NULL,
  `response_msg_conditional` varchar(256) DEFAULT NULL,
  `response_msg_message` varchar(1000) DEFAULT NULL,
  `wait_days_apply` tinyint(4) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_gt_net_message_answer`),
  UNIQUE KEY `Unique_GtNetMessageAnswer` (`response_msg_code`,`priority`,`request_msg_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_message_attempt` (
  `id_gt_net_message_attempt` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net` int(11) NOT NULL COMMENT 'Target GTNet instance to receive the message',
  `id_gt_net_message` int(11) NOT NULL COMMENT 'The broadcast message to deliver',
  `has_send` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Whether delivery succeeded',
  `send_timestamp` timestamp NULL DEFAULT NULL COMMENT 'When successfully delivered (UTC)',
  PRIMARY KEY (`id_gt_net_message_attempt`),
  UNIQUE KEY `uk_message_gtnet` (`id_gt_net_message`,`id_gt_net`),
  KEY `idx_attempt_message` (`id_gt_net_message`),
  KEY `idx_attempt_gtnet` (`id_gt_net`),
  KEY `idx_attempt_pending` (`has_send`,`id_gt_net_message`),
  CONSTRAINT `fk_attempt_gtnet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE,
  CONSTRAINT `fk_attempt_message` FOREIGN KEY (`id_gt_net_message`) REFERENCES `gt_net_message` (`id_gt_net_message`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Tracks per-target delivery status for future-oriented GTNet broadcast messages';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_message_param` (
  `id_gt_net_message` int(11) NOT NULL,
  `param_name` varchar(32) NOT NULL,
  `param_value` varchar(255) NOT NULL,
  PRIMARY KEY (`id_gt_net_message`,`param_name`),
  CONSTRAINT `FK_GTNetMessageParam_GTNetMessage` FOREIGN KEY (`id_gt_net_message`) REFERENCES `gt_net_message` (`id_gt_net_message`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_supplier_detail` (
  `id_gt_net_supplier_detail` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net` int(11) NOT NULL,
  `id_entity` int(11) DEFAULT NULL,
  `entity_kind` tinyint(1) NOT NULL,
  PRIMARY KEY (`id_gt_net_supplier_detail`),
  KEY `FK_GtNetSupplierDetail_SecurityCurrency` (`id_entity`),
  KEY `FK_GtNetSupplierDetail` (`id_gt_net`),
  CONSTRAINT `FK_GtNetSupplierDetail_GTNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE,
  CONSTRAINT `FK_GtNetSupplierDetail_SecurityCurrency` FOREIGN KEY (`id_entity`) REFERENCES `securitycurrency` (`id_securitycurrency`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mail_entity` (
  `id_mail_entity` int(11) NOT NULL AUTO_INCREMENT,
  `id_mail_send_recv` int(11) DEFAULT NULL,
  `message_com_type` tinyint(4) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `mark_date` date NOT NULL,
  `creation_date` date NOT NULL,
  PRIMARY KEY (`id_mail_entity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mail_send_recv` (
  `id_mail_send_recv` int(11) NOT NULL AUTO_INCREMENT,
  `send_recv` char(1) NOT NULL,
  `id_user_from` int(11) NOT NULL,
  `id_user_to` int(11) DEFAULT NULL,
  `id_role_to` int(11) DEFAULT NULL,
  `id_reply_to_remote` int(11) DEFAULT NULL,
  `id_reply_to_local` int(11) DEFAULT NULL,
  `reply_to_role_private` tinyint(1) NOT NULL DEFAULT 0,
  `subject` varchar(96) NOT NULL,
  `message` varchar(4096) NOT NULL,
  `send_recv_time` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id_mail_send_recv`),
  KEY `FK_MailInOut_Role` (`id_role_to`),
  KEY `id_reply_to_local` (`id_reply_to_local`),
  CONSTRAINT `FK_MailSendRecv_Role` FOREIGN KEY (`id_role_to`) REFERENCES `role` (`id_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mail_send_recv_read_del` (
  `id_mail_send_recv` int(11) NOT NULL,
  `id_user` int(11) NOT NULL,
  `has_been_read` tinyint(1) NOT NULL DEFAULT 0,
  `mark_hide_del` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_mail_send_recv`,`id_user`),
  KEY `FK_MailInBoxRead_User` (`id_user`),
  CONSTRAINT `FK_MailSendRecvReadDel_MailSendRecv` FOREIGN KEY (`id_mail_send_recv`) REFERENCES `mail_send_recv` (`id_mail_send_recv`) ON DELETE CASCADE,
  CONSTRAINT `FK_MailSendRecvReadDel_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mail_setting_forward` (
  `id_mail_setting_forward` int(11) NOT NULL AUTO_INCREMENT,
  `id_user` int(11) NOT NULL,
  `message_com_type` tinyint(4) NOT NULL,
  `message_target_type` tinyint(4) NOT NULL DEFAULT 0,
  `id_user_redirect` int(11) DEFAULT NULL,
  PRIMARY KEY (`id_mail_setting_forward`),
  KEY `FK_MailSettingForward_User` (`id_user`),
  KEY `FK_MailSettingForwardRedirect_User` (`id_user_redirect`),
  CONSTRAINT `FK_MailSettingForwardRedirect_User` FOREIGN KEY (`id_user_redirect`) REFERENCES `user` (`id_user`),
  CONSTRAINT `FK_MailSettingForward_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `multilinguestring` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `multilinguestrings` (
  `id_string` int(11) NOT NULL,
  `text` varchar(2000) NOT NULL,
  `language` varchar(2) NOT NULL,
  PRIMARY KEY (`id_string`,`language`),
  CONSTRAINT `FK_Multilinguestrings_Multilinguestring` FOREIGN KEY (`id_string`) REFERENCES `multilinguestring` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `propose_change_entity` (
  `id_propose_request` int(11) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `id_owner_entity` int(11) NOT NULL,
  PRIMARY KEY (`id_propose_request`),
  CONSTRAINT `FK_ProposeChangeEntity_ProposeRequest` FOREIGN KEY (`id_propose_request`) REFERENCES `propose_request` (`id_propose_request`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `propose_change_field` (
  `id_propose_field` int(11) NOT NULL AUTO_INCREMENT,
  `id_propose_request` int(11) NOT NULL,
  `field` varchar(40) NOT NULL,
  `value` blob NOT NULL,
  PRIMARY KEY (`id_propose_field`),
  KEY `FK_ProposeChangeField_ProposeRequest` (`id_propose_request`),
  CONSTRAINT `FK_ProposeChangeField_ProposeRequest` FOREIGN KEY (`id_propose_request`) REFERENCES `propose_request` (`id_propose_request`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `propose_request` (
  `id_propose_request` int(11) NOT NULL AUTO_INCREMENT,
  `dtype` varchar(1) NOT NULL,
  `entity` varchar(40) NOT NULL,
  `data_change_state` smallint(6) NOT NULL,
  `note_request` varchar(1000) DEFAULT NULL,
  `note_accept_reject` varchar(1000) DEFAULT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_propose_request`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `propose_user_task` (
  `id_propose_request` int(11) NOT NULL,
  `id_target_user` int(11) NOT NULL,
  `id_role_to` int(11) NOT NULL,
  `user_task_type` smallint(6) NOT NULL,
  PRIMARY KEY (`id_propose_request`),
  KEY `FK_ProposeUserTask_User` (`id_target_user`),
  CONSTRAINT `FK_ProposeUserTask_ProposeRequest` FOREIGN KEY (`id_propose_request`) REFERENCES `propose_request` (`id_propose_request`) ON DELETE CASCADE,
  CONSTRAINT `FK_ProposeUserTask_User` FOREIGN KEY (`id_target_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `release_note` (
  `id_release_note` int(11) NOT NULL AUTO_INCREMENT,
  `version` varchar(12) NOT NULL,
  `language` char(2) NOT NULL,
  `note` varchar(1024) NOT NULL,
  PRIMARY KEY (`id_release_note`),
  UNIQUE KEY `uk_version_language` (`version`,`language`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `role` (
  `id_role` int(11) NOT NULL AUTO_INCREMENT,
  `rolename` varchar(50) NOT NULL,
  PRIMARY KEY (`id_role`),
  UNIQUE KEY `rolename` (`id_role`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `task_data_change` (
  `id_task_data_change` int(11) NOT NULL AUTO_INCREMENT,
  `id_task` tinyint(2) NOT NULL,
  `execution_priority` tinyint(3) NOT NULL,
  `entity` varchar(40) DEFAULT NULL,
  `id_entity` int(11) DEFAULT NULL,
  `earliest_start_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `exec_start_time` timestamp NULL DEFAULT NULL,
  `exec_end_time` timestamp NULL DEFAULT NULL,
  `old_value_varchar` varchar(30) DEFAULT NULL,
  `old_value_number` double DEFAULT NULL,
  `progress_state` tinyint(1) NOT NULL,
  `failed_message_code` varchar(40) DEFAULT NULL,
  `failed_stack_trace` varchar(4096) DEFAULT NULL,
  PRIMARY KEY (`id_task_data_change`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tenant` (
  `id_tenant` int(11) NOT NULL AUTO_INCREMENT,
  `tenant_name` varchar(25) NOT NULL,
  `create_id_user` int(11) NOT NULL,
  `tenant_kind_type` tinyint(4) NOT NULL,
  `currency` char(3) NOT NULL,
  `exclude_div_tax` tinyint(1) NOT NULL DEFAULT 0,
  `id_watchlist_performance` int(11) DEFAULT NULL,
  `closed_until` date DEFAULT NULL,
  `id_parent_tenant` int(11) DEFAULT NULL,
  `id_algo_top` int(11) DEFAULT NULL,
  `country` varchar(2) DEFAULT NULL,
  `tax_export_settings` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`tax_export_settings`)),
  `id_gt_import_platform` int(11) DEFAULT NULL,
  PRIMARY KEY (`id_tenant`),
  KEY `FK_Tenant_GtImportPlatform` (`id_gt_import_platform`),
  CONSTRAINT `FK_Tenant_GtImportPlatform` FOREIGN KEY (`id_gt_import_platform`) REFERENCES `imp_trans_platform` (`id_trans_imp_platform`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tenant_access` (
  `id_tenant_access` int(11) NOT NULL AUTO_INCREMENT,
  `id_user` int(11) NOT NULL,
  `id_tenant` int(11) NOT NULL,
  `access_level` tinyint(4) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id_tenant_access`),
  UNIQUE KEY `uq_tenant_access_user_tenant` (`id_user`,`id_tenant`),
  KEY `fk_tenant_access_tenant` (`id_tenant`),
  CONSTRAINT `fk_tenant_access_tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`) ON DELETE CASCADE,
  CONSTRAINT `fk_tenant_access_user` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `udf_data` (
  `id_user` int(11) NOT NULL,
  `entity` varchar(20) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `json_values` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`json_values`)),
  PRIMARY KEY (`entity`,`id_entity`,`id_user`) USING BTREE,
  KEY `FK_udfData_user` (`id_user`),
  CONSTRAINT `FK_udfData_user` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `udf_metadata` (
  `id_udf_metadata` int(11) NOT NULL AUTO_INCREMENT,
  `id_user` int(11) DEFAULT NULL,
  `udf_special_type` tinyint(3) DEFAULT NULL,
  `description` varchar(24) NOT NULL,
  `description_help` varchar(80) DEFAULT NULL,
  `udf_data_type` tinyint(2) NOT NULL,
  `field_size` varchar(20) DEFAULT NULL,
  `ui_order` smallint(2) NOT NULL,
  PRIMARY KEY (`id_udf_metadata`),
  KEY `I_UDF_Metadata_IdUser` (`id_user`),
  CONSTRAINT `FK_udfMetadata_user` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `udf_metadata_general` (
  `id_udf_metadata` int(11) NOT NULL,
  `entity` varchar(20) NOT NULL,
  PRIMARY KEY (`id_udf_metadata`),
  CONSTRAINT `FK_MetadataGeneral_Metadata` FOREIGN KEY (`id_udf_metadata`) REFERENCES `udf_metadata` (`id_udf_metadata`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `udf_special_type_disable_user` (
  `id_user` int(11) NOT NULL,
  `udf_special_type` tinyint(3) NOT NULL,
  PRIMARY KEY (`id_user`,`udf_special_type`),
  CONSTRAINT `FK_UdfSpecialTypeDisableUser_user` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user` (
  `id_user` int(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` int(11) DEFAULT NULL,
  `nickname` varchar(30) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `locale` varchar(5) NOT NULL,
  `timezone_offset` int(11) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `ui_show_my_property` tinyint(1) NOT NULL DEFAULT 1,
  `security_breach_count` smallint(6) NOT NULL DEFAULT 0,
  `limit_request_exceed_count` smallint(6) NOT NULL DEFAULT 0,
  `last_role_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  `home_tenant_read_only` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_user`),
  UNIQUE KEY `nickname` (`nickname`),
  UNIQUE KEY `email` (`email`) USING BTREE,
  KEY `FK_User_Tenant` (`id_tenant`),
  CONSTRAINT `FK_User_Tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_entity_change_count` (
  `id_user` int(11) NOT NULL,
  `date` date NOT NULL,
  `entity_name` varchar(40) NOT NULL,
  `count_insert` int(11) NOT NULL,
  `count_update` int(11) NOT NULL,
  `count_delete` int(11) NOT NULL,
  PRIMARY KEY (`id_user`,`date`,`entity_name`) USING BTREE,
  CONSTRAINT `FK_UserEntityChangeCount_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_role` (
  `id_user` int(11) NOT NULL,
  `id_role` int(11) NOT NULL,
  PRIMARY KEY (`id_user`,`id_role`),
  KEY `role_id` (`id_role`),
  CONSTRAINT `FK_User_Roles_Roles` FOREIGN KEY (`id_role`) REFERENCES `role` (`id_role`),
  CONSTRAINT `FK_User_Roles_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `verificationtoken` (
  `id_verificationtoken` int(11) NOT NULL AUTO_INCREMENT,
  `expiry_date` datetime DEFAULT NULL,
  `token` varchar(255) DEFAULT NULL,
  `id_user` int(11) NOT NULL,
  PRIMARY KEY (`id_verificationtoken`),
  KEY `FK_Verify_User` (`id_user`),
  CONSTRAINT `FK_VerificationToken_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- ----------------------------------------------------------------------
-- Reference data. globalparameters is filtered to the library prefix `g.`: the `gt.` rows are
-- Grafioschtrader application config that no grafiosch-* code reads, and several of them hold
-- the dumping machine's own state (demo tenant ids, connector watermarks).
-- ----------------------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

/*!40000 ALTER TABLE `globalparameters` DISABLE KEYS */;
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_date_time`, `property_blob`, `changed_by_system`, `input_rule`) VALUES ('g.gnet.connection.timeout',30,NULL,NULL,NULL,NULL,0,'min:5,max:40');
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_date_time`, `property_blob`, `changed_by_system`, `input_rule`) VALUES ('g.gnet.del.message.recv',NULL,'LP=1,HP=5,SL=5',NULL,NULL,NULL,0,'pattern:^LP=([1-9]|10),HP=([1-9]|10),SL=([1-9]|10)$');
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_date_time`, `property_blob`, `changed_by_system`, `input_rule`) VALUES ('g.gnet.log.aggregate.days',NULL,'D=1,W=7,M=30,Y=365',NULL,NULL,NULL,0,'pattern:^D=\\d+,W=\\d+,M=\\d+,Y=\\d+$');
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_date_time`, `property_blob`, `changed_by_system`, `input_rule`) VALUES ('g.gnet.my.entry.id',4,NULL,NULL,NULL,NULL,1,NULL);
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_date_time`, `property_blob`, `changed_by_system`, `input_rule`) VALUES ('g.gnet.use',1,NULL,NULL,NULL,NULL,0,NULL);
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_date_time`, `property_blob`, `changed_by_system`, `input_rule`) VALUES ('g.gnet.use.log',1,NULL,NULL,NULL,NULL,0,NULL);
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_date_time`, `property_blob`, `changed_by_system`, `input_rule`) VALUES ('g.jwt.expiration.minutes',1440,NULL,NULL,NULL,NULL,0,NULL);
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_date_time`, `property_blob`, `changed_by_system`, `input_rule`) VALUES ('g.max.limit.request.exceeded.count',20,NULL,NULL,NULL,NULL,0,'min:1,max:99');
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_date_time`, `property_blob`, `changed_by_system`, `input_rule`) VALUES ('g.max.security.breach.count',5,NULL,NULL,NULL,NULL,0,NULL);
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_date_time`, `property_blob`, `changed_by_system`, `input_rule`) VALUES ('g.password.regex.properties',NULL,NULL,NULL,NULL,'de=Das Passwort besteht aus mindestens acht Zeichen mit mindestens einem Buchstaben und einer Zahl.\nregex=^(?\\=.*[A-Za-z])(?\\=.*\\\\d)[A-Za-z\\\\d]{8,}$\nforceRegex=false\nen=The password has at least eight characters with at least one letter and one number.\n\n\n\n\n',0,NULL);
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_date_time`, `property_blob`, `changed_by_system`, `input_rule`) VALUES ('g.task.data.days.preserve',10,NULL,NULL,NULL,NULL,0,NULL);
/*!40000 ALTER TABLE `globalparameters` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

/*!40000 ALTER TABLE `role` DISABLE KEYS */;
INSERT INTO `role` (`id_role`, `rolename`) VALUES (5,'ROLE_ADMIN');
INSERT INTO `role` (`id_role`, `rolename`) VALUES (6,'ROLE_ALLEDIT');
INSERT INTO `role` (`id_role`, `rolename`) VALUES (7,'ROLE_USER');
INSERT INTO `role` (`id_role`, `rolename`) VALUES (8,'ROLE_LIMITEDIT');
/*!40000 ALTER TABLE `role` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- ----------------------------------------------------------------------
-- Limit configuration, filtered to the library layer for the same reason as globalparameters above:
-- an application row names an entity this host does not have and a MAX key nothing registers here.
-- Only rows without a user are dumped - `user` is structure-only, so a per-user override would
-- reference a row that does not exist. Role scoped rows are kept, `role` is seeded above with the
-- master's own ids. A key with no row at all resolves as unlimited.
-- ----------------------------------------------------------------------

INSERT INTO `entity_limit` (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, id_role, limit_value, valid_until, created_by, last_modified_by, version) VALUES (1, 'GTNet', NULL, NULL, NULL, 8, 5, NULL, 0, 0, 0);
INSERT INTO `entity_limit` (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, id_role, limit_value, valid_until, created_by, last_modified_by, version) VALUES (1, 'GTNetConfig', NULL, NULL, NULL, 8, 3, NULL, 0, 0, 0);
INSERT INTO `entity_limit` (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, id_role, limit_value, valid_until, created_by, last_modified_by, version) VALUES (1, 'GTNetConfigEntity', NULL, NULL, NULL, 8, 10, NULL, 0, 0, 0);
INSERT INTO `entity_limit` (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, id_role, limit_value, valid_until, created_by, last_modified_by, version) VALUES (1, 'GTNetMessage', NULL, NULL, NULL, 8, 20, NULL, 0, 0, 0);
INSERT INTO `entity_limit` (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, id_role, limit_value, valid_until, created_by, last_modified_by, version) VALUES (1, 'GTNetMessageAnswer', NULL, NULL, NULL, 8, 20, NULL, 0, 0, 0);
INSERT INTO `entity_limit` (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, id_role, limit_value, valid_until, created_by, last_modified_by, version) VALUES (1, 'MailSendRecv', NULL, NULL, NULL, 8, 200, NULL, 0, 0, 0);
INSERT INTO `entity_limit` (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, id_role, limit_value, valid_until, created_by, last_modified_by, version) VALUES (1, 'MailSettingForward', NULL, NULL, NULL, 8, 12, NULL, 0, 0, 0);
INSERT INTO `entity_limit` (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, id_role, limit_value, valid_until, created_by, last_modified_by, version) VALUES (1, 'ProposeUserTask', NULL, NULL, NULL, 8, 10, NULL, 0, 0, 0);
INSERT INTO `entity_limit` (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, id_role, limit_value, valid_until, created_by, last_modified_by, version) VALUES (1, 'UDFMetadataGeneral', NULL, NULL, NULL, 8, 20, NULL, 0, 0, 0);

-- g.gnet.my.entry.id points at the dumping machine's own GTNet entry, but gt_net is dumped
-- structure-only. NULL - not a deleted row - is the fresh-install state: the key stays visible in
-- the global settings UI and isGTNetOperational() keeps every GTNet task from running.
UPDATE globalparameters SET property_int = NULL WHERE property_name = 'g.gnet.my.entry.id';
