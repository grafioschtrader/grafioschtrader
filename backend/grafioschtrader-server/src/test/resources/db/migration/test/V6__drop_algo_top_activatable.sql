-- The activation switch of an AlgoTop is removed; the monitoring assignment decides the background evaluation.
ALTER TABLE algo_top DROP COLUMN IF EXISTS activatable;
