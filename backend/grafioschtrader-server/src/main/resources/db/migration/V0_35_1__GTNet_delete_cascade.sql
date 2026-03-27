-- Add ON DELETE CASCADE to foreign keys referencing gt_net and gt_net_message
-- to allow cascade deletion of a GTNet server entry and all its dependent data.

-- gt_net_exchange_log → gt_net
ALTER TABLE gt_net_exchange_log DROP FOREIGN KEY IF EXISTS FK_GtNetExchangeLog_GtNet;
ALTER TABLE gt_net_exchange_log ADD CONSTRAINT FK_GtNetExchangeLog_GtNet
  FOREIGN KEY (id_gt_net) REFERENCES gt_net(id_gt_net) ON DELETE CASCADE;

-- gt_net_message → gt_net
ALTER TABLE gt_net_message DROP FOREIGN KEY IF EXISTS FK_GtNetMessage_GtNet;
ALTER TABLE gt_net_message ADD CONSTRAINT FK_GtNetMessage_GtNet
  FOREIGN KEY (id_gt_net) REFERENCES gt_net(id_gt_net) ON DELETE CASCADE;

-- gt_net_message self-referential reply_to → SET NULL on delete
ALTER TABLE gt_net_message DROP FOREIGN KEY IF EXISTS FK_GtNetMessage_GtNetMessage;
ALTER TABLE gt_net_message ADD CONSTRAINT FK_GtNetMessage_GtNetMessage
  FOREIGN KEY (reply_to) REFERENCES gt_net_message(id_gt_net_message) ON DELETE SET NULL;

-- gt_net_message_param → gt_net_message
ALTER TABLE gt_net_message_param DROP FOREIGN KEY IF EXISTS FK_GTNetMessageParam_GTNetMessage;
ALTER TABLE gt_net_message_param ADD CONSTRAINT FK_GTNetMessageParam_GTNetMessage
  FOREIGN KEY (id_gt_net_message) REFERENCES gt_net_message(id_gt_net_message) ON DELETE CASCADE;

-- gt_net_config_entity → gt_net_entity (was ON UPDATE CASCADE only)
ALTER TABLE gt_net_config_entity DROP FOREIGN KEY IF EXISTS FK_GtNetConfigEntity_GtNetEntity;
ALTER TABLE gt_net_config_entity ADD CONSTRAINT FK_GtNetConfigEntity_GtNetEntity
  FOREIGN KEY (id_gt_net_entity) REFERENCES gt_net_entity(id_gt_net_entity) ON DELETE CASCADE ON UPDATE CASCADE;

-- gt_net_security_imp_gap → gt_net
ALTER TABLE gt_net_security_imp_gap DROP FOREIGN KEY IF EXISTS FK_GTNetSecurityImpGap_GTNet;
ALTER TABLE gt_net_security_imp_gap ADD CONSTRAINT FK_GTNetSecurityImpGap_GTNet
  FOREIGN KEY (id_gt_net) REFERENCES gt_net(id_gt_net) ON DELETE CASCADE;
