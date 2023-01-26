# Security fill gap of EOD
# ALTER TABLE security DROP INDEX IF EXISTS I_Securty_Fill_EOD_gap_until; 
ALTER TABLE security DROP COLUMN IF EXISTS fill_eod_gap_until;
ALTER TABLE security ADD fill_eod_gap_until DATE NULL DEFAULT NULL AFTER retry_split_load; 
# ALTER TABLE security ADD KEY I_Securty_Fill_EOD_gap_until (fill_eod_gap_until);

# Message system - Connect messages
ALTER TABLE mail_in_out DROP CONSTRAINT IF EXISTS FK_MailInOut_GtNet;
ALTER TABLE mail_inbox DROP COLUMN IF EXISTS domain_from;
ALTER TABLE mail_sendbox DROP COLUMN IF EXISTS domain_to;
ALTER TABLE mail_in_out DROP COLUMN IF EXISTS reply_to;
ALTER TABLE mail_in_out ADD reply_to INT NULL AFTER id_role_to; 
ALTER TABLE mail_in_out DROP COLUMN IF EXISTS id_gt_net;
ALTER TABLE mail_in_out ADD id_gt_net INT NULL AFTER id_role_to; 
ALTER TABLE mail_in_out DROP COLUMN IF EXISTS message_com_type;
ALTER TABLE mail_in_out ADD message_com_type TINYINT(4) NOT NULL DEFAULT '0' AFTER reply_to; 
ALTER TABLE mail_in_out DROP COLUMN IF EXISTS id_entity;
ALTER TABLE mail_in_out ADD id_entity VARCHAR(20) NULL AFTER reply_to; 
ALTER TABLE mail_in_out ADD CONSTRAINT FK_MailInOut_GtNet FOREIGN KEY (id_gt_net) REFERENCES gt_net(id_gt_net) ON DELETE RESTRICT ON UPDATE RESTRICT; 