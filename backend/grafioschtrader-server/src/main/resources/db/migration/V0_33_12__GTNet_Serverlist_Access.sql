-- Add serverlist_access_granted column to track if a remote has been granted access to our server list
ALTER TABLE gt_net_config ADD COLUMN serverlist_access_granted TINYINT(1) NOT NULL DEFAULT 0;
