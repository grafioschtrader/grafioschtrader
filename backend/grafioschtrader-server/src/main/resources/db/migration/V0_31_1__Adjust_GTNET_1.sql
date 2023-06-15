ALTER TABLE gt_net DROP COLUMN IF EXISTS accept_lastprice_request;
ALTER TABLE gt_net ADD accept_lastprice_request BOOLEAN NOT NULL DEFAULT FALSE AFTER lastprice_server_state; 