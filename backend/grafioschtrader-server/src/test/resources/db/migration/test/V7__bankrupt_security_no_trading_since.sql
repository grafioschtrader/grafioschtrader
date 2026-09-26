-- The day from which a historical simulation no longer trades a marked instrument, kept apart from no_data_since,
-- which concerns the price maintenance only.
ALTER TABLE bankrupt_security ADD COLUMN IF NOT EXISTS no_trading_since DATE DEFAULT NULL AFTER no_data_since;
