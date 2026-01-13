-- Rename price_type column to entity_kind in gt_net_supplier_detail table
-- Both columns represent the same concept (LAST_PRICE=0, HISTORICAL_PRICES=1)
ALTER TABLE gt_net_supplier_detail CHANGE COLUMN price_type entity_kind TINYINT(1) NOT NULL;
