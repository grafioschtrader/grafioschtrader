-- German tax help. The class of an investment fund (0 = equity fund, 1 = mixed fund, 2 = real estate fund, 3 = foreign
-- real estate fund, 4 = other fund, NULL = not classified) decides the partial exemption of its income. A portfolio
-- states whether a domestic paying agent withholds the tax (0 = domestic withholding, 1 = none, NULL = not assigned
-- yet, which is the state of every existing portfolio). The base interest rate in percent of a tax year feeds the
-- advance lump sum of the funds. TINYINT without a display width, because the JDBC driver returns TINYINT(1) as Boolean.
ALTER TABLE security ADD COLUMN IF NOT EXISTS fund_tax_class TINYINT NULL DEFAULT NULL;
ALTER TABLE portfolio ADD COLUMN IF NOT EXISTS tax_withholding TINYINT NULL DEFAULT NULL;
ALTER TABLE tax_year ADD COLUMN IF NOT EXISTS fund_base_rate DOUBLE NULL DEFAULT NULL;
INSERT IGNORE INTO tax_country (country_code) VALUES ('DE');
-- Base interest rates of the Federal Ministry of Finance. A rate an administrator has already entered stays.
INSERT INTO tax_year (id_tax_country, tax_year, fund_base_rate)
  SELECT c.id_tax_country, r.tax_year, r.fund_base_rate FROM tax_country c
    JOIN (SELECT 2018 AS tax_year, 0.87 AS fund_base_rate UNION ALL SELECT 2019, 0.52 UNION ALL SELECT 2020, 0.07
      UNION ALL SELECT 2021, -0.45 UNION ALL SELECT 2022, -0.05 UNION ALL SELECT 2023, 2.55
      UNION ALL SELECT 2024, 2.29 UNION ALL SELECT 2025, 2.53 UNION ALL SELECT 2026, 3.20) r
    WHERE c.country_code = 'DE'
  ON DUPLICATE KEY UPDATE fund_base_rate = COALESCE(tax_year.fund_base_rate, VALUES(fund_base_rate));
