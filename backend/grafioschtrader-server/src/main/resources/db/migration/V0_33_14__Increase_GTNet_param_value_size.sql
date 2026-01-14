-- Increase param_value column size to accommodate longer values like security names
-- The previous varchar(32) was too small for security metadata exchange

ALTER TABLE gt_net_message_param MODIFY param_value VARCHAR(255) NOT NULL;
