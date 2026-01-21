-- Remove id_gt_net column from gt_net_instrument table
-- This column was redundant because each PUSH_OPEN instance has only one instrument pool
-- and all instruments belong to the local "myGtNet" entry

-- Drop the foreign key constraint first
ALTER TABLE gt_net_instrument DROP FOREIGN KEY FK_GtNetInstrument_GtNet;

-- Drop the index
ALTER TABLE gt_net_instrument DROP INDEX FK_GtNetInstrument_GtNet;

-- Drop the column
ALTER TABLE gt_net_instrument DROP COLUMN id_gt_net;
