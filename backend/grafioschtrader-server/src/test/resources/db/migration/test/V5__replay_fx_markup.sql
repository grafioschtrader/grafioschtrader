-- Completed replays distinguish paid FX markup from missing tariff coverage.
ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS fx_markup_paid DOUBLE NULL;
ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS fx_uncovered_conversions INT NULL;
