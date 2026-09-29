-- Transaction costs are derived from the YAML fee models; the manually entered cheapest cost was never read.
ALTER TABLE securityaccount DROP COLUMN IF EXISTS lowest_transaction_cost;

-- Regular coupon terms for simulations, taken from the naming convention "<coupon in percent> <name>" that the
-- yield-to-maturity UDF also relies on. The day count follows the currency of the issue as in
-- CouponDayCount.defaultForCurrency. Only bonds without a stored coupon rate are touched, an existing day count is
-- kept, and frequencies the replay coupon schedule cannot handle are skipped.
UPDATE security s
JOIN assetclass a ON a.id_asset_class = s.id_asset_class
SET s.simulation_metadata = JSON_MERGE_PATCH(COALESCE(s.simulation_metadata, '{}'),
  JSON_OBJECT('bondTerms', JSON_OBJECT(
    'couponRate', REPLACE(TRIM(REGEXP_SUBSTR(s.name, '^[0-9]{1,2}([.,][0-9]{1,4})? ')), ',', '.') + 0,
    'couponDayCount', COALESCE(JSON_VALUE(s.simulation_metadata, '$.bondTerms.couponDayCount'),
      IF(UPPER(s.currency) = 'CHF', 'THIRTY_E_360', 'ACT_ACT_ICMA')))))
WHERE a.category_type IN (1, 6)
  AND a.spec_invest_instrument = 0
  AND s.dist_frequency IN (1, 2, 4, 12)
  AND JSON_VALUE(s.simulation_metadata, '$.bondTerms.couponRate') IS NULL
  AND s.name REGEXP '^[0-9]{1,2}([.,][0-9]{1,4})? ';
