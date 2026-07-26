-- Tenant reference to the Grafioschtrader import platform holding the GT authored import templates
-- (receipt PDFs, transaction CSV export) plus the per-import-session switch to use that platform's
-- templates instead of the securities account's trading platform mapping.

ALTER TABLE tenant ADD COLUMN IF NOT EXISTS id_gt_import_platform INT NULL;
ALTER TABLE tenant DROP FOREIGN KEY IF EXISTS FK_Tenant_GtImportPlatform;
ALTER TABLE tenant ADD CONSTRAINT FK_Tenant_GtImportPlatform FOREIGN KEY (id_gt_import_platform)
  REFERENCES imp_trans_platform (id_trans_imp_platform) ON DELETE SET NULL;

ALTER TABLE imp_trans_head ADD COLUMN IF NOT EXISTS use_gt_platform TINYINT(1) NOT NULL DEFAULT 0;

-- Seed the shared "Grafioschtrader" import platform and its templates. The platform is global (no tenant
-- assignment) and has no CSV import implementation, so the generic import parses its templates. It is NOT linked
-- to any tenant here; a tenant opts in through tenant.id_gt_import_platform in the tenant settings.
INSERT INTO imp_trans_platform (name, id_csv_imp_impl, created_by, creation_time, last_modified_by, last_modified_time, version)
SELECT 'Grafioschtrader', NULL, 1, NOW(), 1, NOW(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM imp_trans_platform WHERE name = 'Grafioschtrader');

SET @id_gt_platform = (SELECT MIN(id_trans_imp_platform) FROM imp_trans_platform WHERE name = 'Grafioschtrader');

-- The eight GT templates (receipt PDFs for buy/sell, dividend/interest, financing cost, plus the transaction CSV
-- export), German and English. Re-runnable via the unique key (platform, format, category, language, valid_since).
INSERT INTO imp_trans_template
  (id_trans_imp_platform, template_format_type, template_purpose, template_category, template_as_txt, valid_since, template_language, created_by, creation_time, last_modified_by, last_modified_time, version)
VALUES
  (@id_gt_platform, 0, 'Grafioschtrader Kauf/Verkauf Beleg', 0, 'TRANSAKTIONSBELEG Grafioschtrader\nKunde Mustermann\nDepot Musterdepot\nKonto Musterkonto\nBörsentransaktion: {transType|P|N} Referenz: GT-00000000\nDatum {date|P} Uhrzeit {time|P}\nMusterwertpapier AG ISIN: {isin|P}\nTitelwährung {cin|SL|N}\nAnzahl {units|SL|N}\nPreis {quotation|SL|N}\nMarchzinsen {ac|SL|N|O}\nKommission {tc1|SL|N|O}\nAbgaben und Steuern {tt1|SL|N|O}\nDevisenkurs {cex|SL|N|O}\nVerrechnungswährung {cac|SL|N}\n[Zu Ihren Lasten|Zu Ihren Gunsten] CHF {ta|SL|N}\nVielen Dank für Ihren Auftrag.\n[END]\ntransType=ACCUMULATE|Kauf\ntransType=REDUCE|Verkauf\ndateFormat=dd.MM.yyyy\ntimeFormat=HH:mm:ss\noverRuleSeparators=All<\'|.>\ncalcRounding=0.05,JPY=1\ntemplatePurpose=Grafioschtrader Kauf/Verkauf Beleg\n', '2000-01-01', 'de', 1, NOW(), 1, NOW(), 0),
  (@id_gt_platform, 0, 'Grafioschtrader buy/sell receipt', 0, 'TRANSACTION RECEIPT Grafioschtrader\nCustomer Mustermann\nSecurities account Sampledepot\nCash account Sampleaccount\nStock exchange transaction: {transType|P|N} Reference: GT-00000000\nDate {date|P} Time {time|P}\nSample Security Inc ISIN: {isin|P}\nInstrument currency {cin|SL|N}\nQuantity {units|SL|N}\nPrice {quotation|SL|N}\nAccrued interest {ac|SL|N|O}\nCommission {tc1|SL|N|O}\nTaxes and duties {tt1|SL|N|O}\nCurrency exchange rate {cex|SL|N|O}\nSettlement currency {cac|SL|N}\n[Debited to your account|Credited to your account] CHF {ta|SL|N}\nThank you for your order.\n[END]\ntransType=ACCUMULATE|Buy\ntransType=REDUCE|Sell\ndateFormat=dd.MM.yyyy\ntimeFormat=HH:mm:ss\noverRuleSeparators=All<\'|.>\ncalcRounding=0.05,JPY=1\ntemplatePurpose=Grafioschtrader buy/sell receipt\n', '2000-01-01', 'en', 1, NOW(), 1, NOW(), 0),
  (@id_gt_platform, 0, 'Grafioschtrader Dividende/Zins Beleg', 11, 'TRANSAKTIONSBELEG Grafioschtrader\nKunde Mustermann\nDepot Musterdepot\nKonto Musterkonto\n{transType|P|N} Referenz: GT-00000000\nDatum {date|P} Uhrzeit {time|P}\nEx-Datum {exdiv|SL|N|O}\nMusterwertpapier AG ISIN: {isin|P}\nTitelwährung {cin|SL|N}\nAnzahl {units|SL|N}\nAusschüttung pro Einheit {quotation|SL|N}\nAbgaben und Steuern {tt1|SL|N|O}\nDevisenkurs {cex|SL|N|O}\nVerrechnungswährung {cac|SL|N}\nGutschrift CHF {ta|SL|N}\nVielen Dank für Ihren Auftrag.\n[END]\ntransType=DIVIDEND|Dividende,Zins\ndateFormat=dd.MM.yyyy\ntimeFormat=HH:mm:ss\noverRuleSeparators=All<\'|.>\ncalcRounding=0.05,JPY=1\ntemplatePurpose=Grafioschtrader Dividende/Zins Beleg\n', '2000-01-01', 'de', 1, NOW(), 1, NOW(), 0),
  (@id_gt_platform, 0, 'Grafioschtrader dividend/interest receipt', 11, 'TRANSACTION RECEIPT Grafioschtrader\nCustomer Mustermann\nSecurities account Sampledepot\nCash account Sampleaccount\n{transType|P|N} Reference: GT-00000000\nDate {date|P} Time {time|P}\nEx-date {exdiv|SL|N|O}\nSample Security Inc ISIN: {isin|P}\nInstrument currency {cin|SL|N}\nQuantity {units|SL|N}\nDistribution per unit {quotation|SL|N}\nTaxes and duties {tt1|SL|N|O}\nCurrency exchange rate {cex|SL|N|O}\nSettlement currency {cac|SL|N}\nCredit CHF {ta|SL|N}\nThank you for your order.\n[END]\ntransType=DIVIDEND|Dividend,Interest\ndateFormat=dd.MM.yyyy\ntimeFormat=HH:mm:ss\noverRuleSeparators=All<\'|.>\ncalcRounding=0.05,JPY=1\ntemplatePurpose=Grafioschtrader dividend/interest receipt\n', '2000-01-01', 'en', 1, NOW(), 1, NOW(), 0),
  (@id_gt_platform, 0, 'Grafioschtrader Finanzierungskosten Beleg', 22, 'TRANSAKTIONSBELEG Grafioschtrader\nKunde Mustermann\nDepot Musterdepot\nKonto Musterkonto\n{transType|P|N} Referenz: GT-00000000\nDatum {date|P} Uhrzeit {time|P}\nMusterwertpapier AG ISIN: {isin|P}\nKommission {tc1|SL|N|O}\nAbgaben und Steuern {tt1|SL|N|O}\nVerrechnungswährung {cac|SL|N}\nBelastung CHF {ta|SL|N}\nVielen Dank für Ihren Auftrag.\n[END]\ntransType=FINANCE_COST|Finanzierungskosten\ndateFormat=dd.MM.yyyy\ntimeFormat=HH:mm:ss\noverRuleSeparators=All<\'|.>\ncalcRounding=0.05,JPY=1\ntemplatePurpose=Grafioschtrader Finanzierungskosten Beleg\n', '2000-01-01', 'de', 1, NOW(), 1, NOW(), 0),
  (@id_gt_platform, 0, 'Grafioschtrader financing cost receipt', 22, 'TRANSACTION RECEIPT Grafioschtrader\nCustomer Mustermann\nSecurities account Sampledepot\nCash account Sampleaccount\n{transType|P|N} Reference: GT-00000000\nDate {date|P} Time {time|P}\nSample Security Inc ISIN: {isin|P}\nCommission {tc1|SL|N|O}\nTaxes and duties {tt1|SL|N|O}\nSettlement currency {cac|SL|N}\nCharge CHF {ta|SL|N}\nThank you for your order.\n[END]\ntransType=FINANCE_COST|Financing\ndateFormat=dd.MM.yyyy\ntimeFormat=HH:mm:ss\noverRuleSeparators=All<\'|.>\ncalcRounding=0.05,JPY=1\ntemplatePurpose=Grafioschtrader financing cost receipt\n', '2000-01-01', 'en', 1, NOW(), 1, NOW(), 0),
  (@id_gt_platform, 1, 'Grafioschtrader Transaktionsexport (deutsch)', 20, 'datetime=Datum\norder=Order\ntransType=Transaktion\nsymbol=Symbol\nsn=Name\nisin=ISIN\nunits=Anzahl\nquotation=Kurs\nac=Marchzinsen\ntc1=Kosten\ntt1=Steuern\ncin=Titelwährung\nta=Nettobetrag\ncac=Währung\ncex=Wechselkurs\nsf1=Marker\n[END]\ntemplateId=2\ndelimiterField=;\ndateFormat=dd.MM.yyyy HH:mm\ntransType=ACCUMULATE|Kauf\ntransType=REDUCE|Verkauf\ntransType=DIVIDEND|Dividende\ntransType=FEE|Gebühr\ntransType=INTEREST_CASHACCOUNT|Zins\ntransType=DEPOSIT|Einzahlung\ntransType=WITHDRAWAL|Auszahlung\ntransType=FINANCE_COST|Finanzierungskosten\noverRuleSeparators=All<\'|.>\ncalcRounding=0.05,JPY=1\nignoreLineByFieldValue=sf1||MARGIN\ntemplatePurpose=Grafioschtrader Transaktionsexport (deutsch)\n', '2000-01-01', 'de', 1, NOW(), 1, NOW(), 0),
  (@id_gt_platform, 1, 'Grafioschtrader transaction export (english)', 20, 'datetime=Date\norder=Order\ntransType=Transaction\nsymbol=Symbol\nsn=Name\nisin=ISIN\nunits=Quantity\nquotation=Unit price\nac=Accrued interest\ntc1=Costs\ntt1=Taxes\ncin=Instrument currency\nta=Net Amount\ncac=Currency\ncex=Exchange rate\nsf1=Marker\n[END]\ntemplateId=1\ndelimiterField=;\ndateFormat=dd.MM.yyyy HH:mm\ntransType=ACCUMULATE|Buy\ntransType=REDUCE|Sell\ntransType=DIVIDEND|Dividend\ntransType=FEE|Fee\ntransType=INTEREST_CASHACCOUNT|Interest\ntransType=DEPOSIT|Deposit\ntransType=WITHDRAWAL|Withdrawal\ntransType=FINANCE_COST|Financing\noverRuleSeparators=All<\'|.>\ncalcRounding=0.05,JPY=1\nignoreLineByFieldValue=sf1||MARGIN\ntemplatePurpose=Grafioschtrader transaction export (english)\n', '2000-01-01', 'en', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE
  template_as_txt = VALUES(template_as_txt),
  template_purpose = VALUES(template_purpose),
  last_modified_time = NOW();


-- ---------------------------------------------------------------------------------------------------------------
-- Trading calendar derived from an index (stockexchange.id_index_upd_calendar)
--
-- The procedure gains two IN parameters:
--   _idStockexchangeFilter  NULL = every index-linked exchange (weekly cron), otherwise a single exchange.
--   _fullRebuild            0 = incremental append after the newest user row (unchanged legacy behaviour),
--                           1 = discard every connector-derived row and re-derive the whole calendar. Needed
--                               after the index history was wiped and reloaded, because the incremental mode
--                               never touches days before the newest create_type = 5 (ADD_MODIFIED_USER) row
--                               and would leave them derived from the discarded quote set.
--
-- The full rebuild keeps the user's manual entries and is bounded by the index's first and last quote, so no
-- non-trading day is fabricated outside the range the index actually covers.
--
-- Also fixes the missing WHERE clause on the max_calendar_upd_date update, which previously wrote the current
-- cursor row's date to every stockexchange row.
-- ---------------------------------------------------------------------------------------------------------------
DELIMITER //
CREATE OR REPLACE PROCEDURE updCalendarStockexchangeByIndex(IN _idStockexchangeFilter INT UNSIGNED,
    IN _fullRebuild TINYINT)
BEGIN
  DECLARE done BOOLEAN DEFAULT FALSE;
  DECLARE _idStockexchange INT UNSIGNED;
  DECLARE _idSecurity INT UNSIGNED;
  DECLARE _fromDate DATE;
  DECLARE _toDate DATE;
  DECLARE cur CURSOR FOR SELECT id_stockexchange, id_index_upd_calendar FROM stockexchange
     WHERE (SELECT COUNT(*) FROM historyquote WHERE id_securitycurrency = id_index_upd_calendar) > 4000
       AND (_idStockexchangeFilter IS NULL OR id_stockexchange = _idStockexchangeFilter);
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done := TRUE;
  OPEN cur;
  testLoop: LOOP
    FETCH cur INTO _idStockexchange, _idSecurity;
    IF done THEN
      LEAVE testLoop;
    END IF;

    IF _fullRebuild THEN
      SELECT MIN(date), MAX(date) INTO _fromDate, _toDate FROM historyquote WHERE id_securitycurrency = _idSecurity;
      -- Drop every connector-derived row, the user's manual entries stay untouched.
      DELETE FROM trading_days_minus WHERE id_stockexchange = _idStockexchange AND create_type <> 5;
      INSERT INTO trading_days_minus (id_stockexchange, trading_date_minus)
        SELECT _idStockexchange, tdp.trading_date FROM trading_days_plus tdp
         WHERE tdp.trading_date BETWEEN _fromDate AND _toDate
           AND NOT EXISTS (SELECT 1 FROM historyquote hq
                            WHERE hq.id_securitycurrency = _idSecurity AND hq.date = tdp.trading_date)
           AND NOT EXISTS (SELECT 1 FROM trading_days_minus tdm
                            WHERE tdm.id_stockexchange = _idStockexchange
                              AND tdm.trading_date_minus = tdp.trading_date);
    ELSE
      DELETE FROM trading_days_minus WHERE id_stockexchange = _idStockexchange AND trading_date_minus >
        (SELECT IFNULL(MAX(trading_date_minus), "1999-12-31") AS fromDate FROM trading_days_minus WHERE id_stockexchange = _idStockexchange AND create_type = 5);
      INSERT INTO trading_days_minus (id_stockexchange, trading_date_minus)
       SELECT _idStockexchange, tsp.trading_date AS trandingDate FROM trading_days_plus tsp LEFT JOIN (SELECT DISTINCT hq.date AS date FROM historyquote hq  WHERE hq.id_securitycurrency = _idSecurity) AS a ON tsp.trading_date = a.date WHERE a.date IS NULL AND
       tsp.trading_date > (SELECT IFNULL(MAX(trading_date_minus), "1999-12-31") AS fromDate FROM trading_days_minus WHERE id_stockexchange = _idStockexchange AND create_type = 5)
       AND tsp.trading_date < CURDATE();
    END IF;

    UPDATE stockexchange SET max_calendar_upd_date = (SELECT MAX(date) FROM historyquote WHERE id_securitycurrency = _idSecurity)
      WHERE id_stockexchange = _idStockexchange;
  END LOOP testLoop;
  CLOSE cur;
END //
DELIMITER ;

-- ---------------------------------------------------------------------------------------------------------------
-- Trading days up to the end of 2030
--
-- trading_days_plus holds every candidate trading day; the exchange specific closures are subtracted from it via
-- trading_days_minus. The table ended in 2028, so any calendar calculation beyond that had no days to work with.
-- ---------------------------------------------------------------------------------------------------------------
INSERT IGNORE INTO trading_days_plus (trading_date)
SELECT DATE('2029-01-01' + INTERVAL seq DAY)
FROM seq_0_to_1000
WHERE DATE('2029-01-01' + INTERVAL seq DAY) <= '2030-12-31'
  AND WEEKDAY(DATE('2029-01-01' + INTERVAL seq DAY)) < 5
  AND DATE_FORMAT(DATE('2029-01-01' + INTERVAL seq DAY), '%m-%d') NOT IN ('01-01', '12-25');


-- ---------------------------------------------------------------------------------------------------------------
-- Trading calendar derived from holiday rules (stockexchange.id_trading_calendar_rule_set)
--
-- The second source for a trading calendar next to the reference index above. A rule set describes the closures of
-- an exchange as recurring rules -- fixed dates, nth weekday, Easter and Hijri relative -- plus the date level
-- corrections that no rule can express. It replaces the former classpath files calendar/rules/*.yaml, which could
-- only be changed with a release; the rules are shared data now and every user may maintain them.
--
-- A set may extend another one and contribute only its deviations, which is why the German regional venues and the
-- Swiss venues do not repeat the Xetra and SIX calendars.
--
-- An exchange uses either the index or a rule set. The link below therefore clears id_index_upd_calendar wherever a
-- rule set is assigned. Generating trading_days_minus from the rules is not part of this migration.
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE stockexchange DROP FOREIGN KEY IF EXISTS FK_Stockexchange_TradingCalendarRuleSet;

CREATE TABLE IF NOT EXISTS trading_calendar_rule_set (
  id_trading_calendar_rule_set INT(11) NOT NULL AUTO_INCREMENT,
  mic CHAR(4) DEFAULT NULL,
  name VARCHAR(64) NOT NULL,
  id_extends_rule_set INT(11) DEFAULT NULL,
  rule_yaml TEXT DEFAULT NULL,
  created_by INT(11) NOT NULL,
  creation_time TIMESTAMP NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  last_modified_by INT(11) NOT NULL,
  last_modified_time TIMESTAMP NOT NULL DEFAULT current_timestamp(),
  version INT(11) NOT NULL,
  PRIMARY KEY (id_trading_calendar_rule_set),
  UNIQUE KEY trading_calendar_rule_set_name (name),
  UNIQUE KEY trading_calendar_rule_set_mic (mic),
  CONSTRAINT FK_TradingCalendarRuleSet_Extends FOREIGN KEY (id_extends_rule_set)
    REFERENCES trading_calendar_rule_set (id_trading_calendar_rule_set)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

ALTER TABLE stockexchange ADD COLUMN IF NOT EXISTS id_trading_calendar_rule_set INT(11) DEFAULT NULL;
ALTER TABLE stockexchange ADD CONSTRAINT FK_Stockexchange_TradingCalendarRuleSet
  FOREIGN KEY (id_trading_calendar_rule_set) REFERENCES trading_calendar_rule_set (id_trading_calendar_rule_set);

-- The rule sets migrated from the former classpath files calendar/rules/*.yaml and
-- calendar/rule-overrides.yaml. mic, name and the former 'sameAs' reference are columns now;
-- the YAML holds note, authoritative range, rules and the date level corrections.
INSERT INTO trading_calendar_rule_set (mic, name, rule_yaml, created_by, creation_time,
    last_modified_by, last_modified_time, version)
VALUES
  ('AIXK', 'Astana International Exchange', 'authoritativeFrom: 2017
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: Fixed0101NextMonday2017, type: FIXED, month: 1, day: 1, observance: NEXT_MONDAY}
  - {name: Fixed0102NearestWeekday2017, type: FIXED, month: 1, day: 2, observance: NEAREST_WEEKDAY}
  - {name: Fixed0308NextMonday2017, type: FIXED, month: 3, day: 8, observance: NEXT_MONDAY}
  - {name: Fixed0321NextMonday2017, type: FIXED, month: 3, day: 21, observance: NEXT_MONDAY}
  - {name: Fixed0322NearestWeekday2017, type: FIXED, month: 3, day: 22, observance: NEAREST_WEEKDAY}
  - {name: Fixed0323NearestWeekday2017, type: FIXED, month: 3, day: 23, observance: NEAREST_WEEKDAY}
  - {name: Fixed0501NextMonday2017, type: FIXED, month: 5, day: 1, observance: NEXT_MONDAY}
  - {name: Fixed0507NextMonday2017, type: FIXED, month: 5, day: 7, observance: NEXT_MONDAY}
  - {name: Fixed0509NearestWeekday2017, type: FIXED, month: 5, day: 9, observance: NEAREST_WEEKDAY}
  - {name: Fixed0706NextMonday2017, type: FIXED, month: 7, day: 6, observance: NEXT_MONDAY}
  - {name: Fixed0830NextMonday2017, type: FIXED, month: 8, day: 30, observance: NEXT_MONDAY}
  - {name: Fixed1216NextMonday2017, type: FIXED, month: 12, day: 16, observance: NEXT_MONDAY}
  - {name: Fixed1025NextMonday2022, type: FIXED, month: 10, day: 25, observance: NEXT_MONDAY, validFrom: 2022}
  - {name: Fixed1201NextMonday2017, type: FIXED, month: 12, day: 1, observance: NEXT_MONDAY, validTo: 2021}
additionalClosures:
  - 2017-01-03
  - 2017-09-01
  - 2018-03-09
  - 2018-04-30
  - 2018-05-08
  - 2018-08-21
  - 2018-08-31
  - 2018-12-31
  - 2019-01-07
  - 2019-03-25
  - 2019-05-10
  - 2019-12-17
  - 2020-01-03
  - 2020-01-07
  - 2020-03-24
  - 2020-03-25
  - 2020-07-31
  - 2020-12-17
  - 2020-12-18
  - 2021-01-04
  - 2021-01-07
  - 2021-03-24
  - 2021-07-20
  - 2021-12-17
  - 2022-01-04
  - 2022-01-07
  - 2022-03-07
  - 2022-05-10
  - 2022-08-29
  - 2022-10-24
  - 2023-01-03
  - 2023-06-28
  - 2023-07-07
  - 2024-03-25
  - 2024-05-08
  - 2025-01-03
  - 2025-01-07
  - 2025-03-25
  - 2025-06-06
  - 2026-01-07
  - 2026-03-24
  - 2026-03-25
  - 2026-05-27
  - 2027-01-04
  - 2027-01-07
  - 2027-03-24
  - 2028-01-04
  - 2028-01-07
  - 2028-05-05
  - 2029-04-24
  - 2030-01-07
  - 2030-03-25', 1, NOW(), 1, NOW(), 0),
  ('ASEX', 'Athens Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffsetMinus22000, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffsetPlus12000, type: EASTER_RELATIVE, offset: 1}
  - {name: Fixed1225NearestWeekday2005, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY, validFrom: 2005}
  - {name: Fixed1224NextMonday2009, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY, validFrom: 2009}
  - {name: Fixed0501NearestWeekday2012, type: FIXED, month: 5, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2012, validTo: 2021}
  - {name: Fixed1226NearestWeekday2011, type: FIXED, month: 12, day: 26, observance: NEAREST_WEEKDAY, validFrom: 2011, validTo: 2020}
  - {name: Fixed0501NearestWeekday2023, type: FIXED, month: 5, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2023}
  - {name: Fixed0501NearestWeekday2005, type: FIXED, month: 5, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2005, validTo: 2009}
additionalClosures:
  - 2000-01-06
  - 2000-03-13
  - 2000-04-28
  - 2000-05-01
  - 2000-06-19
  - 2000-08-15
  - 2000-12-25
  - 2000-12-26
  - 2001-01-01
  - 2001-02-26
  - 2001-05-01
  - 2001-06-04
  - 2001-08-15
  - 2001-12-25
  - 2001-12-26
  - 2002-01-01
  - 2002-03-18
  - 2002-03-25
  - 2002-05-01
  - 2002-05-03
  - 2002-05-06
  - 2002-05-07
  - 2002-06-24
  - 2002-08-15
  - 2002-10-28
  - 2002-12-25
  - 2002-12-26
  - 2003-01-01
  - 2003-01-06
  - 2003-03-10
  - 2003-03-25
  - 2003-04-25
  - 2003-04-28
  - 2003-05-01
  - 2003-06-16
  - 2003-08-15
  - 2003-10-28
  - 2003-12-25
  - 2003-12-26
  - 2004-01-01
  - 2004-01-06
  - 2004-02-23
  - 2004-03-25
  - 2004-05-31
  - 2004-08-13
  - 2004-10-28
  - 2005-01-06
  - 2005-03-14
  - 2005-04-29
  - 2005-06-20
  - 2005-08-15
  - 2005-10-28
  - 2006-01-06
  - 2006-03-06
  - 2006-04-21
  - 2006-04-24
  - 2006-06-12
  - 2006-08-15
  - 2006-12-26
  - 2007-01-01
  - 2007-02-19
  - 2007-05-28
  - 2007-08-15
  - 2007-12-26
  - 2008-01-01
  - 2008-03-04
  - 2008-03-05
  - 2008-03-10
  - 2008-03-25
  - 2008-04-25
  - 2008-04-28
  - 2008-06-16
  - 2008-08-15
  - 2008-10-28
  - 2008-12-26
  - 2009-01-01
  - 2009-01-06
  - 2009-03-02
  - 2009-03-25
  - 2009-04-17
  - 2009-04-20
  - 2009-06-08
  - 2009-10-28
  - 2010-01-01
  - 2010-01-06
  - 2010-02-15
  - 2010-03-25
  - 2010-05-24
  - 2010-10-28
  - 2011-01-06
  - 2011-03-07
  - 2011-03-25
  - 2011-06-13
  - 2011-08-15
  - 2011-10-28
  - 2012-01-06
  - 2012-02-27
  - 2012-04-13
  - 2012-04-16
  - 2012-06-04
  - 2012-08-15
  - 2013-01-01
  - 2013-03-18
  - 2013-03-25
  - 2013-05-03
  - 2013-05-06
  - 2013-05-07
  - 2013-06-24
  - 2013-08-15
  - 2013-10-28
  - 2014-01-01
  - 2014-01-06
  - 2014-03-03
  - 2014-03-25
  - 2014-06-09
  - 2014-08-15
  - 2014-10-28
  - 2014-12-31
  - 2015-01-01
  - 2015-01-06
  - 2015-02-23
  - 2015-03-25
  - 2015-04-10
  - 2015-04-13
  - 2015-06-01
  - 2015-06-29
  - 2015-06-30
  - 2015-07-01
  - 2015-07-02
  - 2015-07-03
  - 2015-07-06
  - 2015-07-07
  - 2015-07-08
  - 2015-07-09
  - 2015-07-10
  - 2015-07-13
  - 2015-07-14
  - 2015-07-15
  - 2015-07-16
  - 2015-07-17
  - 2015-07-20
  - 2015-07-21
  - 2015-07-22
  - 2015-07-23
  - 2015-07-24
  - 2015-07-27
  - 2015-07-28
  - 2015-07-29
  - 2015-07-30
  - 2015-07-31
  - 2015-10-28
  - 2016-01-01
  - 2016-01-06
  - 2016-03-14
  - 2016-04-29
  - 2016-05-03
  - 2016-06-20
  - 2016-08-15
  - 2016-10-28
  - 2017-01-06
  - 2017-02-27
  - 2017-06-05
  - 2017-08-15
  - 2018-01-01
  - 2018-02-19
  - 2018-04-06
  - 2018-04-09
  - 2018-05-28
  - 2018-08-15
  - 2019-01-01
  - 2019-03-11
  - 2019-03-25
  - 2019-04-26
  - 2019-04-29
  - 2019-06-17
  - 2019-08-15
  - 2019-10-28
  - 2020-01-01
  - 2020-01-06
  - 2020-03-02
  - 2020-03-25
  - 2020-04-17
  - 2020-04-20
  - 2020-06-08
  - 2020-10-28
  - 2021-01-01
  - 2021-01-06
  - 2021-03-15
  - 2021-03-25
  - 2021-05-03
  - 2021-06-21
  - 2021-10-28
  - 2022-01-06
  - 2022-03-07
  - 2022-03-25
  - 2022-04-22
  - 2022-04-25
  - 2022-06-13
  - 2022-08-15
  - 2022-10-28
  - 2023-01-06
  - 2023-02-27
  - 2023-04-14
  - 2023-04-17
  - 2023-06-05
  - 2023-08-15
  - 2023-12-26
  - 2024-01-01
  - 2024-03-18
  - 2024-03-25
  - 2024-05-03
  - 2024-05-06
  - 2024-06-24
  - 2024-08-15
  - 2024-10-28
  - 2024-12-26
  - 2025-01-01
  - 2025-01-06
  - 2025-03-03
  - 2025-03-25
  - 2025-06-09
  - 2025-08-15
  - 2025-10-28
  - 2025-12-26
  - 2026-01-01
  - 2026-01-06
  - 2026-02-23
  - 2026-03-25
  - 2026-04-10
  - 2026-04-13
  - 2026-06-01
  - 2026-10-28
  - 2027-01-01
  - 2027-01-06
  - 2027-03-15
  - 2027-03-25
  - 2027-05-03
  - 2027-06-21
  - 2027-10-28
  - 2028-01-06
  - 2028-02-28
  - 2028-06-05
  - 2028-08-15
  - 2028-12-26
  - 2029-01-01
  - 2029-02-19
  - 2029-04-06
  - 2029-04-09
  - 2029-05-28
  - 2029-08-15
  - 2029-12-26
  - 2030-01-01
  - 2030-03-11
  - 2030-03-25
  - 2030-04-26
  - 2030-04-29
  - 2030-06-17
  - 2030-08-15
  - 2030-10-28
  - 2030-12-26', 1, NOW(), 1, NOW(), 0),
  ('BIVA', 'Bolsa Institucional de Valores', 'note: >
  The Mexican exchanges close on the national holidays. Constitution Day, Benito Juarez''s birthday and Revolution Day
  were moved to a fixed Monday by the 2006 labour law reform, which is why each has a pre-2006 fixed-date variant and a
  later nth-weekday variant. Presidential inauguration day is a closure once every six years.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: ConstitutionDay, type: FIXED, month: 2, day: 5, validTo: 2005}
  - {name: ConstitutionDayMonday, type: NTH_WEEKDAY, month: 2, nth: 1, dayOfWeek: MONDAY, validFrom: 2006}
  - {name: BenitoJuarez, type: FIXED, month: 3, day: 21, validTo: 2005}
  - {name: BenitoJuarezMonday, type: NTH_WEEKDAY, month: 3, nth: 3, dayOfWeek: MONDAY, validFrom: 2006}
  - {name: MaundyThursday, type: EASTER_RELATIVE, offset: -3}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: IndependenceDay, type: FIXED, month: 9, day: 16}
  - {name: RevolutionDay, type: FIXED, month: 11, day: 20, validTo: 2005}
  - {name: RevolutionDayMonday, type: NTH_WEEKDAY, month: 11, nth: 3, dayOfWeek: MONDAY, validFrom: 2006}
  - {name: InaugurationDay, type: EXPLICIT_DATES, dates: [2000-12-01, 2006-12-01, 2012-12-01, 2018-12-01, 2024-10-01]}
  - {name: GuadalupeDay, type: FIXED, month: 12, day: 12}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
additionalClosures:
  - 2006-03-21
  - 2006-11-02
  - 2007-11-02
  - 2009-11-02
  - 2010-09-17
  - 2010-11-02
  - 2011-11-02
  - 2012-11-02
  - 2015-11-02
  - 2016-11-02
  - 2017-11-02
  - 2018-11-02
  - 2020-11-02
  - 2021-11-02
  - 2022-11-02
  - 2023-11-02
  - 2026-11-02
  - 2027-11-02
  - 2028-11-02
  - 2029-11-02
openDates:
  - 2000-12-01
  - 2006-02-06
  - 2006-03-20
  - 2006-12-01
  - 2024-10-01', 1, NOW(), 1, NOW(), 0),
  ('BVMF', 'B3 Brasil Bolsa Balcao', 'note: >
  B3 closes on the national holidays plus the municipal holidays of Sao Paulo, where the exchange is located --
  Revolucao Constitucionalista on 9 July and Consciencia Negra on 20 November, the latter becoming a national holiday
  in 2024. Carnival closes the exchange on the Monday and Tuesday before Ash Wednesday, both derived from Easter.
  Brazilian holidays are not transferred when they fall on a weekend.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: CarnivalMonday, type: EASTER_RELATIVE, offset: -48}
  - {name: CarnivalTuesday, type: EASTER_RELATIVE, offset: -47}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: Tiradentes, type: FIXED, month: 4, day: 21}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: CorpusChristi, type: EASTER_RELATIVE, offset: 60}
  - {name: RevolucaoConstitucionalista, type: FIXED, month: 7, day: 9}
  - {name: Independence, type: FIXED, month: 9, day: 7}
  - {name: NossaSenhoraAparecida, type: FIXED, month: 10, day: 12}
  - {name: Finados, type: FIXED, month: 11, day: 2}
  - {name: ProclamacaoDaRepublica, type: FIXED, month: 11, day: 15}
  - {name: ConscienciaNegra, type: FIXED, month: 11, day: 20}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31}
additionalClosures:
  - 2000-01-25
  - 2000-12-29
  - 2001-01-25
  - 2001-12-24
  - 2002-01-25
  - 2002-12-24
  - 2003-12-24
  - 2004-12-24
  - 2005-01-25
  - 2005-12-30
  - 2006-01-25
  - 2006-12-29
  - 2007-01-25
  - 2007-12-24
  - 2008-01-25
  - 2008-12-24
  - 2009-12-24
  - 2010-01-25
  - 2010-12-24
  - 2011-01-25
  - 2011-12-30
  - 2012-01-25
  - 2012-12-24
  - 2013-01-25
  - 2013-12-24
  - 2014-06-12
  - 2014-12-24
  - 2015-12-24
  - 2016-01-25
  - 2016-12-30
  - 2017-01-25
  - 2017-12-29
  - 2018-01-25
  - 2018-12-24
  - 2019-01-25
  - 2019-12-24
  - 2020-12-24
  - 2021-01-25
  - 2021-12-24
  - 2022-12-30
  - 2023-12-29
  - 2024-12-24
  - 2025-12-24
  - 2026-12-24
  - 2027-12-24
  - 2028-12-29
  - 2029-12-24
  - 2030-12-24
openDates:
  - 2000-11-20
  - 2001-11-20
  - 2002-11-20
  - 2003-11-20
  - 2020-07-09
  - 2020-11-20
  - 2023-11-20
  - 2024-07-09
  - 2025-07-09
  - 2026-07-09
  - 2027-07-09
  - 2029-07-09
  - 2030-07-09', 1, NOW(), 1, NOW(), 0),
  ('CMES', 'CME Globex', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffsetMinus22000, type: EASTER_RELATIVE, offset: -2}
  - {name: Fixed1225NearestWeekday2000, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Fixed0101NearestWeekday2012, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2012, validTo: 2021}
  - {name: Fixed0101NearestWeekday2006, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2006, validTo: 2010}
  - {name: Fixed0101NearestWeekday2023, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2023, validTo: 2027}
additionalClosures:
  - 2001-01-01
  - 2002-01-01
  - 2003-01-01
  - 2004-01-01
  - 2004-06-11
  - 2007-01-02
  - 2018-12-05
  - 2025-01-09
  - 2029-01-01
  - 2030-01-01', 1, NOW(), 1, NOW(), 0),
  ('HAMA', 'Boerse Hamburg', 'note: >
  German regional exchange following the Xetra trading calendar. Regional venues have historically kept slightly
  longer hours than Xetra but close on the same days; any divergence the comparison report surfaces belongs here as
  an explicit deviation rule.
additionalClosures:
  - 2007-05-28
  - 2014-10-03
  - 2015-05-25
  - 2016-05-16
  - 2016-10-03
  - 2017-06-05
  - 2017-10-03
  - 2017-10-31
  - 2018-05-21
  - 2018-10-03
  - 2019-06-10
  - 2019-10-03
  - 2020-06-01
  - 2021-05-24', 1, NOW(), 1, NOW(), 0),
  ('IEPA', 'ICE Endex', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffsetMinus22000, type: EASTER_RELATIVE, offset: -2}
  - {name: Fixed1225NearestWeekday2000, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Fixed0101NearestWeekday2012, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2012, validTo: 2021}
  - {name: Fixed0101NearestWeekday2006, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2006, validTo: 2010}
  - {name: Fixed0101NearestWeekday2023, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2023, validTo: 2027}
additionalClosures:
  - 2001-01-01
  - 2002-01-01
  - 2003-01-01
  - 2004-01-01
  - 2004-06-11
  - 2007-01-02
  - 2012-10-29
  - 2018-12-05
  - 2025-01-09
  - 2029-01-01
  - 2030-01-01', 1, NOW(), 1, NOW(), 0),
  ('MISX', 'Moscow Exchange', 'note: >
  Russian holidays are fixed dates, but the government decrees every autumn which working days are transferred so that
  a holiday falling on a weekend produces a long weekend elsewhere. Those transfers cannot be derived and would have to
  be enumerated per year; the fixed dates below therefore over-report trading days in most years.
  GT''s reference index for this exchange stops delivering in October 2022, so the stored calendar marks every weekday
  since then as a closure -- 656 false entries at the time of writing, growing every week. This exchange is the
  clearest illustration of the failure mode the index method has, and also of the limits of the rule approach; it is a
  reasonable candidate for being maintained manually.
rules:
  - {name: NewYearHolidays, type: FIXED, month: 1, day: 1}
  - {name: NewYearHolidayJan2, type: FIXED, month: 1, day: 2}
  - {name: NewYearHolidayJan3, type: FIXED, month: 1, day: 3}
  - {name: NewYearHolidayJan4, type: FIXED, month: 1, day: 4}
  - {name: NewYearHolidayJan5, type: FIXED, month: 1, day: 5}
  - {name: NewYearHolidayJan6, type: FIXED, month: 1, day: 6}
  - {name: OrthodoxChristmas, type: FIXED, month: 1, day: 7}
  - {name: NewYearHolidayJan8, type: FIXED, month: 1, day: 8}
  - {name: DefenderOfTheFatherland, type: FIXED, month: 2, day: 23}
  - {name: InternationalWomensDay, type: FIXED, month: 3, day: 8}
  - {name: SpringAndLabourDay, type: FIXED, month: 5, day: 1}
  - {name: VictoryDay, type: FIXED, month: 5, day: 9}
  - {name: RussiaDay, type: FIXED, month: 6, day: 12}
  - {name: UnityDay, type: FIXED, month: 11, day: 4, validFrom: 2005}
  - {name: OctoberRevolutionDay, type: FIXED, month: 11, day: 7, validTo: 2004}
additionalClosures:
  - 2001-12-31
  - 2002-02-25
  - 2002-05-02
  - 2002-05-03
  - 2002-05-10
  - 2002-11-08
  - 2002-12-12
  - 2002-12-13
  - 2002-12-31
  - 2003-02-24
  - 2003-03-10
  - 2003-05-02
  - 2003-06-13
  - 2003-12-12
  - 2003-12-31
  - 2004-05-03
  - 2004-05-04
  - 2004-05-10
  - 2004-06-14
  - 2004-11-08
  - 2004-12-13
  - 2004-12-31
  - 2005-01-10
  - 2005-03-07
  - 2005-05-02
  - 2005-05-10
  - 2005-06-13
  - 2006-01-09
  - 2006-02-24
  - 2006-05-08
  - 2006-11-06
  - 2007-04-30
  - 2007-06-11
  - 2007-11-05
  - 2007-12-31
  - 2008-02-25
  - 2008-03-10
  - 2008-05-02
  - 2008-06-13
  - 2008-09-18
  - 2008-10-10
  - 2008-10-27
  - 2008-11-03
  - 2009-01-09
  - 2009-03-09
  - 2009-05-11
  - 2010-02-22
  - 2010-05-03
  - 2010-05-10
  - 2010-06-14
  - 2010-11-05
  - 2010-12-31
  - 2011-01-10
  - 2011-03-07
  - 2011-05-02
  - 2011-06-13
  - 2012-03-09
  - 2012-04-30
  - 2012-06-11
  - 2012-11-05
  - 2012-12-31
  - 2013-12-31
  - 2014-03-10
  - 2014-06-13
  - 2014-12-31
  - 2015-03-09
  - 2015-05-04
  - 2015-05-11
  - 2015-12-31
  - 2016-05-02
  - 2016-05-03
  - 2016-06-13
  - 2017-05-08
  - 2017-11-06
  - 2018-11-05
  - 2018-12-31
  - 2019-12-31
  - 2020-02-24
  - 2020-03-09
  - 2020-05-11
  - 2020-06-24
  - 2020-07-01
  - 2020-12-31
  - 2021-05-03
  - 2021-12-31
  - 2022-03-07
  - 2022-05-02
  - 2022-05-03
  - 2022-05-10
  - 2022-06-13
  - 2024-12-31
  - 2025-02-24
  - 2025-03-10
  - 2025-12-31
  - 2026-03-09
  - 2026-05-11
  - 2026-12-31
  - 2027-05-03
  - 2027-05-10
  - 2027-06-14
  - 2027-12-31
  - 2028-11-06
  - 2029-11-05
  - 2029-12-31
  - 2030-02-25
  - 2030-12-31
openDates:
  - 2000-01-04
  - 2000-01-05
  - 2000-01-06
  - 2000-11-07
  - 2001-01-03
  - 2001-01-04
  - 2001-01-05
  - 2001-11-07
  - 2002-01-03
  - 2002-01-04
  - 2002-01-08
  - 2003-01-08
  - 2004-01-05
  - 2004-01-06
  - 2004-01-08
  - 2012-01-03
  - 2012-01-04
  - 2012-01-05
  - 2012-01-06
  - 2013-01-08
  - 2014-01-06
  - 2014-01-08
  - 2015-01-05
  - 2015-01-06
  - 2015-01-08
  - 2016-01-04
  - 2016-01-05
  - 2016-01-06
  - 2017-01-03
  - 2017-01-04
  - 2017-01-05
  - 2017-01-06
  - 2018-01-03
  - 2018-01-04
  - 2018-01-05
  - 2019-01-03
  - 2019-01-04
  - 2019-01-08
  - 2020-01-03
  - 2020-01-06
  - 2020-01-08
  - 2021-01-04
  - 2021-01-05
  - 2021-01-06
  - 2021-01-08
  - 2022-01-03
  - 2022-01-04
  - 2022-01-05
  - 2022-01-06
  - 2023-01-03
  - 2023-01-04
  - 2023-01-05
  - 2023-01-06
  - 2024-01-03
  - 2024-01-04
  - 2024-01-05
  - 2024-01-08
  - 2025-01-03
  - 2025-01-06
  - 2025-01-08
  - 2026-01-05
  - 2026-01-06
  - 2026-01-08
  - 2027-01-05
  - 2027-01-06
  - 2027-01-08
  - 2028-01-04
  - 2028-01-05
  - 2028-01-06
  - 2029-01-03
  - 2029-01-04
  - 2029-01-05
  - 2030-01-03
  - 2030-01-04
  - 2030-01-08', 1, NOW(), 1, NOW(), 0),
  ('OTXB', 'Berner Kantonalbank OTC-X', 'note: >
  OTC-X is an over-the-counter platform operated by Berner Kantonalbank rather than a regulated exchange, but it trades
  on Swiss banking days and therefore follows the SIX calendar. GT assigns it the same reference index (3493).', 1, NOW(), 1, NOW(), 0),
  ('XAMS', 'Euronext Amsterdam', 'note: >
  Amsterdam adds no national closure to the Euronext base calendar. Koningsdag and Bevrijdingsdag are Dutch public
  holidays but the exchange stays open on them.
additionalClosures:
  - 2000-06-01
  - 2001-04-30
  - 2001-12-31
openDates:
  - 2002-05-20
  - 2003-06-09
  - 2004-05-31', 1, NOW(), 1, NOW(), 0),
  ('XASX', 'Australian Securities Exchange', 'note: >
  The ASX follows the New South Wales holiday calendar because the exchange is in Sydney. Australia Day and Anzac Day
  differ in how they are transferred: Australia Day always moves to the following Monday, whereas Anzac Day on 25 April
  is not transferred by the exchange at all. Queen''s or King''s Birthday is the second Monday of June in New South Wales.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1, observance: NEXT_MONDAY}
  - {name: AustraliaDay, type: FIXED, month: 1, day: 26, observance: NEXT_MONDAY}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: AnzacDay, type: FIXED, month: 4, day: 25}
  - {name: MonarchsBirthday, type: NTH_WEEKDAY, month: 6, nth: 2, dayOfWeek: MONDAY}
  - {name: LabourDay, type: NTH_WEEKDAY, month: 10, nth: 1, dayOfWeek: MONDAY}
  - {name: Christmas, type: FIXED, month: 12, day: 25, observance: NEXT_MONDAY}
  - {name: BoxingDay, type: FIXED, month: 12, day: 26, observance: NEXT_MONDAY}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24, halfDay: true}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31, halfDay: true}

  # Second substitute in the years where 25 December falls on a weekend, so both Christmas and Boxing Day are
  # compensated instead of both resolving to the same Monday.
  - name: ChristmasSecondSubstitute
    type: EXPLICIT_DATES
    dates:
      - 2004-12-28
      - 2005-12-27
      - 2010-12-28
      - 2011-12-27
      - 2015-12-28
      - 2016-12-27
      - 2021-12-28
      - 2022-12-27
      - 2027-12-28

  # National day of mourning for Queen Elizabeth II.
  - {name: NationalMourning, type: EXPLICIT_DATES, dates: [2022-09-22]}
additionalClosures:
  - 2010-04-26
  - 2011-04-26
openDates:
  - 2000-10-02
  - 2001-10-01
  - 2002-10-07
  - 2003-10-06
  - 2004-10-04
  - 2005-10-03
  - 2006-10-02
  - 2007-10-01
  - 2008-10-06
  - 2009-10-05
  - 2010-10-04
  - 2011-10-03
  - 2012-10-01
  - 2013-10-07
  - 2014-10-06
  - 2015-10-05
  - 2016-10-03
  - 2017-10-02
  - 2018-10-01
  - 2019-10-07
  - 2020-10-05
  - 2021-10-04
  - 2022-10-03
  - 2023-10-02
  - 2024-10-07
  - 2025-10-06
  - 2026-10-05
  - 2027-10-04
  - 2028-10-02
  - 2029-10-01
  - 2030-10-07', 1, NOW(), 1, NOW(), 0),
  ('XBDA', 'Bermuda Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffsetMinus22000, type: EASTER_RELATIVE, offset: -2}
  - {name: Fixed0101NextMonday2000, type: FIXED, month: 1, day: 1, observance: NEXT_MONDAY}
  - {name: Fixed1111NextMonday2000, type: FIXED, month: 11, day: 11, observance: NEXT_MONDAY}
  - {name: Fixed1225NextMonday2000, type: FIXED, month: 12, day: 25, observance: NEXT_MONDAY}
  - {name: Fixed1226NearestWeekday2000, type: FIXED, month: 12, day: 26, observance: NEAREST_WEEKDAY}
  - {name: Fixed1226NextMonday2000, type: FIXED, month: 12, day: 26, observance: NEXT_MONDAY}
  - {name: Nth091Monday2000, type: NTH_WEEKDAY, month: 9, nth: 1, dayOfWeek: MONDAY}
  - {name: Fixed0524NextMonday2000, type: FIXED, month: 5, day: 24, observance: NEXT_MONDAY, validTo: 2017}
  - {name: Nth063Monday2018, type: NTH_WEEKDAY, month: 6, nth: 3, dayOfWeek: MONDAY, validFrom: 2018}
  - {name: Nth054Friday2021, type: NTH_WEEKDAY, month: 5, nth: 4, dayOfWeek: FRIDAY, validFrom: 2021}
additionalClosures:
  - 2000-06-12
  - 2000-08-03
  - 2000-08-04
  - 2001-06-11
  - 2001-08-02
  - 2001-08-03
  - 2002-06-10
  - 2002-08-01
  - 2002-08-02
  - 2003-06-16
  - 2003-07-31
  - 2003-08-01
  - 2004-06-14
  - 2004-07-29
  - 2004-07-30
  - 2004-12-28
  - 2005-06-13
  - 2005-07-28
  - 2005-07-29
  - 2005-12-27
  - 2006-06-12
  - 2006-08-03
  - 2006-08-04
  - 2007-01-05
  - 2007-06-11
  - 2007-08-02
  - 2007-08-03
  - 2008-06-16
  - 2008-07-31
  - 2008-08-01
  - 2009-07-30
  - 2009-07-31
  - 2010-07-29
  - 2010-07-30
  - 2010-12-28
  - 2011-07-28
  - 2011-07-29
  - 2011-12-27
  - 2012-08-02
  - 2012-08-03
  - 2013-08-01
  - 2013-08-02
  - 2014-07-31
  - 2014-08-01
  - 2015-07-30
  - 2015-07-31
  - 2016-07-28
  - 2016-07-29
  - 2016-12-27
  - 2017-08-03
  - 2017-08-04
  - 2018-05-25
  - 2018-08-02
  - 2018-08-03
  - 2019-05-24
  - 2019-08-01
  - 2019-08-02
  - 2019-11-04
  - 2020-05-29
  - 2020-07-30
  - 2020-07-31
  - 2021-07-29
  - 2021-07-30
  - 2021-10-18
  - 2021-12-28
  - 2022-07-28
  - 2022-07-29
  - 2022-09-19
  - 2022-12-27
  - 2023-05-08
  - 2023-08-03
  - 2023-08-04
  - 2024-08-01
  - 2024-08-02
  - 2025-07-31
  - 2025-08-01
  - 2026-07-30
  - 2026-07-31
  - 2027-07-29
  - 2027-07-30
  - 2027-12-28
  - 2028-08-03
  - 2028-08-04
  - 2029-08-02
  - 2029-08-03
  - 2030-08-01
  - 2030-08-02', 1, NOW(), 1, NOW(), 0),
  ('XBEL', 'Belgrade Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: Fixed0101NextMonday2000, type: FIXED, month: 1, day: 1, observance: NEXT_MONDAY}
  - {name: Fixed0102NearestWeekday2000, type: FIXED, month: 1, day: 2, observance: NEAREST_WEEKDAY}
  - {name: Fixed0215NextMonday2000, type: FIXED, month: 2, day: 15, observance: NEXT_MONDAY}
  - {name: Fixed0216NearestWeekday2000, type: FIXED, month: 2, day: 16, observance: NEAREST_WEEKDAY}
  - {name: Fixed0501NextMonday2000, type: FIXED, month: 5, day: 1, observance: NEXT_MONDAY}
  - {name: Fixed0502NearestWeekday2000, type: FIXED, month: 5, day: 2, observance: NEAREST_WEEKDAY}
  - {name: Fixed0107NearestWeekday2019, type: FIXED, month: 1, day: 7, observance: NEAREST_WEEKDAY, validFrom: 2019, validTo: 2028}
  - {name: Fixed1111NearestWeekday2007, type: FIXED, month: 11, day: 11, observance: NEAREST_WEEKDAY, validFrom: 2007, validTo: 2016}
  - {name: Fixed1111NearestWeekday2001, type: FIXED, month: 11, day: 11, observance: NEAREST_WEEKDAY, validFrom: 2001, validTo: 2005}
  - {name: Fixed1111NearestWeekday2018, type: FIXED, month: 11, day: 11, observance: NEAREST_WEEKDAY, validFrom: 2018, validTo: 2022}
  - {name: Fixed1231NearestWeekday2021, type: FIXED, month: 12, day: 31, observance: NEAREST_WEEKDAY, validFrom: 2021, validTo: 2025}
additionalClosures:
  - 2000-01-07
  - 2000-04-28
  - 2001-04-13
  - 2001-04-16
  - 2002-01-07
  - 2002-05-03
  - 2002-05-06
  - 2003-01-07
  - 2003-04-25
  - 2003-04-28
  - 2004-01-07
  - 2004-02-17
  - 2004-04-09
  - 2004-04-12
  - 2005-01-07
  - 2005-04-29
  - 2005-05-03
  - 2006-01-03
  - 2006-04-21
  - 2006-04-24
  - 2007-04-06
  - 2007-04-09
  - 2008-01-07
  - 2008-04-25
  - 2008-04-28
  - 2009-01-07
  - 2009-02-17
  - 2009-04-17
  - 2009-04-20
  - 2010-01-07
  - 2010-04-02
  - 2010-04-05
  - 2011-01-07
  - 2011-04-22
  - 2011-04-25
  - 2011-05-03
  - 2012-01-03
  - 2012-04-13
  - 2012-04-16
  - 2013-01-07
  - 2013-05-03
  - 2013-05-06
  - 2014-01-07
  - 2014-04-18
  - 2014-04-21
  - 2015-01-07
  - 2015-02-17
  - 2015-04-10
  - 2015-04-13
  - 2016-01-07
  - 2016-04-29
  - 2016-05-03
  - 2017-01-03
  - 2017-04-14
  - 2017-04-17
  - 2018-04-06
  - 2018-04-09
  - 2019-04-26
  - 2019-04-29
  - 2020-04-17
  - 2020-04-20
  - 2021-01-08
  - 2021-04-30
  - 2022-04-22
  - 2022-04-25
  - 2022-05-03
  - 2023-01-03
  - 2023-01-04
  - 2023-01-05
  - 2023-04-14
  - 2023-04-17
  - 2023-12-29
  - 2024-05-03
  - 2024-05-06
  - 2024-11-11
  - 2025-01-03
  - 2025-01-06
  - 2025-04-18
  - 2025-04-21
  - 2025-11-10
  - 2025-11-11
  - 2026-02-17
  - 2026-04-10
  - 2026-04-13
  - 2026-11-11
  - 2027-04-30
  - 2027-11-11
  - 2028-04-14
  - 2028-04-17
  - 2029-04-06
  - 2029-04-09
  - 2029-11-12
  - 2030-01-07
  - 2030-04-26
  - 2030-04-29
  - 2030-11-11', 1, NOW(), 1, NOW(), 0),
  ('XBER', 'Berlin Stock Exchange', 'note: >
  German regional exchange following the Xetra trading calendar. Regional venues have historically kept slightly
  longer hours than Xetra but close on the same days; any divergence the comparison report surfaces belongs here as
  an explicit deviation rule.
additionalClosures:
  - 2007-05-28
  - 2014-10-03
  - 2015-05-25
  - 2016-05-16
  - 2016-10-03
  - 2017-06-05
  - 2017-10-03
  - 2017-10-31
  - 2018-05-21
  - 2018-10-03
  - 2019-06-10
  - 2019-10-03
  - 2020-06-01
  - 2021-05-24', 1, NOW(), 1, NOW(), 0),
  ('XBKK', 'Stock Exchange of Thailand', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: Fixed0101NextMonday2000, type: FIXED, month: 1, day: 1, observance: NEXT_MONDAY}
  - {name: Fixed0406NextMonday2000, type: FIXED, month: 4, day: 6, observance: NEXT_MONDAY}
  - {name: Fixed0413NextMonday2000, type: FIXED, month: 4, day: 13, observance: NEXT_MONDAY}
  - {name: Fixed0414NearestWeekday2000, type: FIXED, month: 4, day: 14, observance: NEAREST_WEEKDAY}
  - {name: Fixed0414NextMonday2000, type: FIXED, month: 4, day: 14, observance: NEXT_MONDAY}
  - {name: Fixed0415NearestWeekday2000, type: FIXED, month: 4, day: 15, observance: NEAREST_WEEKDAY}
  - {name: Fixed0501NextMonday2000, type: FIXED, month: 5, day: 1, observance: NEXT_MONDAY}
  - {name: Fixed0812NextMonday2000, type: FIXED, month: 8, day: 12, observance: NEXT_MONDAY}
  - {name: Fixed1023NextMonday2000, type: FIXED, month: 10, day: 23, observance: NEXT_MONDAY}
  - {name: Fixed1205NextMonday2000, type: FIXED, month: 12, day: 5, observance: NEXT_MONDAY}
  - {name: Fixed1210NextMonday2000, type: FIXED, month: 12, day: 10, observance: NEXT_MONDAY}
  - {name: Fixed0101NearestWeekday2001, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2001}
  - {name: Fixed1231NextMonday2004, type: FIXED, month: 12, day: 31, observance: NEXT_MONDAY, validFrom: 2004}
  - {name: Fixed0505NextMonday2000, type: FIXED, month: 5, day: 5, observance: NEXT_MONDAY, validTo: 2016}
  - {name: Fixed0728NextMonday2017, type: FIXED, month: 7, day: 28, observance: NEXT_MONDAY, validFrom: 2017}
  - {name: Fixed1013NextMonday2017, type: FIXED, month: 10, day: 13, observance: NEXT_MONDAY, validFrom: 2017}
  - {name: Fixed0504NextMonday2019, type: FIXED, month: 5, day: 4, observance: NEXT_MONDAY, validFrom: 2019}
  - {name: Fixed0603NextMonday2019, type: FIXED, month: 6, day: 3, observance: NEXT_MONDAY, validFrom: 2019}
additionalClosures:
  - 2000-05-18
  - 2001-01-02
  - 2001-02-08
  - 2001-07-05
  - 2001-12-31
  - 2002-02-26
  - 2002-04-16
  - 2002-05-27
  - 2002-07-01
  - 2002-07-25
  - 2002-12-30
  - 2002-12-31
  - 2003-02-17
  - 2003-05-15
  - 2003-07-01
  - 2003-07-14
  - 2004-01-02
  - 2004-03-05
  - 2004-06-02
  - 2004-07-01
  - 2004-08-02
  - 2005-02-23
  - 2005-05-23
  - 2005-07-01
  - 2005-07-22
  - 2006-02-13
  - 2006-04-19
  - 2006-05-12
  - 2006-06-12
  - 2006-06-13
  - 2006-07-11
  - 2006-09-20
  - 2007-01-02
  - 2007-03-05
  - 2007-05-31
  - 2007-07-30
  - 2007-12-24
  - 2008-02-21
  - 2008-05-19
  - 2008-07-01
  - 2008-07-17
  - 2009-01-02
  - 2009-02-09
  - 2009-05-08
  - 2009-07-01
  - 2009-07-06
  - 2009-07-07
  - 2010-03-01
  - 2010-05-20
  - 2010-05-21
  - 2010-05-28
  - 2010-07-01
  - 2010-07-26
  - 2010-08-13
  - 2011-02-18
  - 2011-05-16
  - 2011-05-17
  - 2011-07-01
  - 2011-07-15
  - 2012-01-03
  - 2012-03-07
  - 2012-04-09
  - 2012-06-04
  - 2012-08-02
  - 2013-02-25
  - 2013-04-16
  - 2013-05-24
  - 2013-07-01
  - 2013-07-22
  - 2013-12-30
  - 2014-02-14
  - 2014-05-13
  - 2014-07-01
  - 2014-07-11
  - 2014-08-11
  - 2015-01-02
  - 2015-03-04
  - 2015-05-04
  - 2015-06-01
  - 2015-07-01
  - 2015-07-30
  - 2016-02-22
  - 2016-05-06
  - 2016-05-20
  - 2016-07-01
  - 2016-07-18
  - 2016-07-19
  - 2017-01-03
  - 2017-02-13
  - 2017-05-10
  - 2017-07-10
  - 2017-10-26
  - 2018-01-02
  - 2018-03-01
  - 2018-05-29
  - 2018-07-27
  - 2019-02-19
  - 2019-04-16
  - 2019-05-20
  - 2019-07-16
  - 2020-02-10
  - 2020-05-06
  - 2020-07-06
  - 2021-02-12
  - 2021-02-26
  - 2021-05-26
  - 2021-07-26
  - 2021-09-24
  - 2021-10-22
  - 2022-02-16
  - 2022-05-16
  - 2022-07-13
  - 2022-07-29
  - 2022-10-14
  - 2023-01-03
  - 2023-03-06
  - 2023-05-05
  - 2023-08-01
  - 2023-12-29
  - 2024-01-02
  - 2024-02-26
  - 2024-04-12
  - 2024-04-16
  - 2024-05-22
  - 2024-07-22
  - 2025-02-12
  - 2025-05-12
  - 2025-06-02
  - 2025-07-10
  - 2025-08-11
  - 2026-01-02
  - 2026-03-03
  - 2026-06-01
  - 2026-07-29
  - 2027-02-22
  - 2027-05-20
  - 2027-07-19
  - 2028-02-10
  - 2028-05-08
  - 2028-07-06
  - 2029-01-02
  - 2029-02-27
  - 2029-05-28
  - 2029-07-25
  - 2030-04-16', 1, NOW(), 1, NOW(), 0),
  ('XBOG', 'Bolsa de Valores de Colombia', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffsetMinus22000, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffsetMinus32000, type: EASTER_RELATIVE, offset: -3}
  - {name: Fixed1231NearestWeekday2000, type: FIXED, month: 12, day: 31, observance: NEAREST_WEEKDAY}
  - {name: Nth083Monday2000, type: NTH_WEEKDAY, month: 8, nth: 3, dayOfWeek: MONDAY}
  - {name: Nth111Monday2000, type: NTH_WEEKDAY, month: 11, nth: 1, dayOfWeek: MONDAY}
  - {name: Fixed0101NearestWeekday2024, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2024}
additionalClosures:
  - 2000-01-10
  - 2000-03-20
  - 2000-05-01
  - 2000-06-05
  - 2000-06-26
  - 2000-07-03
  - 2000-07-20
  - 2000-08-07
  - 2000-10-16
  - 2000-11-13
  - 2000-12-08
  - 2000-12-25
  - 2000-12-29
  - 2001-01-08
  - 2001-03-19
  - 2001-05-01
  - 2001-05-28
  - 2001-06-18
  - 2001-06-25
  - 2001-07-02
  - 2001-07-20
  - 2001-08-07
  - 2001-10-15
  - 2001-11-12
  - 2001-12-25
  - 2002-01-01
  - 2002-01-07
  - 2002-03-25
  - 2002-05-01
  - 2002-05-13
  - 2002-06-03
  - 2002-06-10
  - 2002-07-01
  - 2002-08-07
  - 2002-10-14
  - 2002-11-11
  - 2002-12-25
  - 2003-01-01
  - 2003-01-06
  - 2003-03-24
  - 2003-05-01
  - 2003-06-02
  - 2003-06-23
  - 2003-06-30
  - 2003-08-07
  - 2003-10-13
  - 2003-11-17
  - 2003-12-08
  - 2003-12-25
  - 2004-01-01
  - 2004-01-12
  - 2004-03-22
  - 2004-05-24
  - 2004-06-14
  - 2004-06-21
  - 2004-07-05
  - 2004-07-20
  - 2004-10-18
  - 2004-11-15
  - 2004-12-08
  - 2005-01-10
  - 2005-03-21
  - 2005-05-09
  - 2005-05-30
  - 2005-06-06
  - 2005-07-04
  - 2005-07-20
  - 2005-10-17
  - 2005-11-14
  - 2005-12-08
  - 2006-01-09
  - 2006-03-20
  - 2006-05-01
  - 2006-05-29
  - 2006-06-19
  - 2006-06-26
  - 2006-07-03
  - 2006-07-20
  - 2006-08-07
  - 2006-10-16
  - 2006-11-13
  - 2006-12-08
  - 2006-12-25
  - 2006-12-29
  - 2007-01-08
  - 2007-03-19
  - 2007-05-01
  - 2007-05-21
  - 2007-06-11
  - 2007-06-18
  - 2007-07-02
  - 2007-07-20
  - 2007-08-07
  - 2007-10-15
  - 2007-11-12
  - 2007-12-25
  - 2008-01-01
  - 2008-01-07
  - 2008-03-24
  - 2008-05-01
  - 2008-05-05
  - 2008-05-26
  - 2008-06-02
  - 2008-06-30
  - 2008-08-07
  - 2008-10-13
  - 2008-11-17
  - 2008-12-08
  - 2008-12-25
  - 2009-01-01
  - 2009-01-12
  - 2009-03-23
  - 2009-05-01
  - 2009-05-25
  - 2009-06-15
  - 2009-06-22
  - 2009-06-29
  - 2009-07-20
  - 2009-08-07
  - 2009-10-12
  - 2009-11-16
  - 2009-12-08
  - 2009-12-25
  - 2010-01-01
  - 2010-01-11
  - 2010-03-22
  - 2010-05-17
  - 2010-06-07
  - 2010-06-14
  - 2010-07-05
  - 2010-07-20
  - 2010-10-18
  - 2010-11-15
  - 2010-12-08
  - 2011-01-10
  - 2011-03-21
  - 2011-06-06
  - 2011-06-27
  - 2011-07-04
  - 2011-07-20
  - 2011-10-17
  - 2011-11-14
  - 2011-12-08
  - 2012-01-09
  - 2012-03-19
  - 2012-05-01
  - 2012-05-21
  - 2012-06-11
  - 2012-06-18
  - 2012-07-02
  - 2012-07-20
  - 2012-08-07
  - 2012-10-15
  - 2012-11-12
  - 2012-12-25
  - 2013-01-01
  - 2013-01-07
  - 2013-03-25
  - 2013-05-01
  - 2013-05-13
  - 2013-06-03
  - 2013-06-10
  - 2013-07-01
  - 2013-08-07
  - 2013-10-14
  - 2013-11-11
  - 2013-12-25
  - 2014-01-01
  - 2014-01-06
  - 2014-03-24
  - 2014-05-01
  - 2014-06-02
  - 2014-06-23
  - 2014-06-30
  - 2014-08-07
  - 2014-10-13
  - 2014-11-17
  - 2014-12-08
  - 2014-12-25
  - 2015-01-01
  - 2015-01-12
  - 2015-03-23
  - 2015-05-01
  - 2015-05-18
  - 2015-06-08
  - 2015-06-15
  - 2015-06-29
  - 2015-07-20
  - 2015-08-07
  - 2015-10-12
  - 2015-11-16
  - 2015-12-08
  - 2015-12-25
  - 2016-01-01
  - 2016-01-11
  - 2016-03-21
  - 2016-05-09
  - 2016-05-30
  - 2016-06-06
  - 2016-07-04
  - 2016-07-20
  - 2016-10-17
  - 2016-11-14
  - 2016-12-08
  - 2017-01-09
  - 2017-03-20
  - 2017-05-01
  - 2017-05-29
  - 2017-06-19
  - 2017-06-26
  - 2017-07-03
  - 2017-07-20
  - 2017-08-07
  - 2017-10-16
  - 2017-11-13
  - 2017-12-08
  - 2017-12-25
  - 2017-12-29
  - 2018-01-08
  - 2018-03-19
  - 2018-05-01
  - 2018-05-14
  - 2018-06-04
  - 2018-06-11
  - 2018-07-02
  - 2018-07-20
  - 2018-08-07
  - 2018-10-15
  - 2018-11-12
  - 2018-12-25
  - 2019-01-01
  - 2019-01-07
  - 2019-03-25
  - 2019-05-01
  - 2019-06-03
  - 2019-06-24
  - 2019-07-01
  - 2019-08-07
  - 2019-10-14
  - 2019-11-11
  - 2019-12-25
  - 2020-01-01
  - 2020-01-06
  - 2020-03-23
  - 2020-05-01
  - 2020-05-25
  - 2020-06-15
  - 2020-06-22
  - 2020-06-29
  - 2020-07-20
  - 2020-08-07
  - 2020-10-12
  - 2020-11-16
  - 2020-12-08
  - 2020-12-25
  - 2021-01-01
  - 2021-01-11
  - 2021-03-22
  - 2021-05-17
  - 2021-06-07
  - 2021-06-14
  - 2021-07-05
  - 2021-07-20
  - 2021-10-18
  - 2021-11-15
  - 2021-12-08
  - 2022-01-10
  - 2022-03-21
  - 2022-05-30
  - 2022-06-20
  - 2022-06-27
  - 2022-07-04
  - 2022-07-20
  - 2022-10-17
  - 2022-11-14
  - 2022-12-08
  - 2023-01-09
  - 2023-03-20
  - 2023-05-01
  - 2023-05-22
  - 2023-06-12
  - 2023-06-19
  - 2023-07-03
  - 2023-07-20
  - 2023-08-07
  - 2023-10-16
  - 2023-11-13
  - 2023-12-08
  - 2023-12-25
  - 2023-12-29
  - 2024-01-08
  - 2024-03-25
  - 2024-05-01
  - 2024-05-13
  - 2024-06-03
  - 2024-06-10
  - 2024-07-01
  - 2024-08-07
  - 2024-10-14
  - 2024-11-11
  - 2024-12-25
  - 2025-01-06
  - 2025-03-24
  - 2025-05-01
  - 2025-06-02
  - 2025-06-23
  - 2025-06-30
  - 2025-08-07
  - 2025-10-13
  - 2025-11-17
  - 2025-12-08
  - 2025-12-25
  - 2026-01-12
  - 2026-03-23
  - 2026-05-01
  - 2026-05-18
  - 2026-06-08
  - 2026-06-15
  - 2026-06-29
  - 2026-07-20
  - 2026-08-07
  - 2026-10-12
  - 2026-11-16
  - 2026-12-08
  - 2026-12-25
  - 2027-01-11
  - 2027-03-22
  - 2027-05-10
  - 2027-05-31
  - 2027-06-07
  - 2027-07-05
  - 2027-07-20
  - 2027-10-18
  - 2027-11-15
  - 2027-12-08
  - 2028-01-10
  - 2028-03-20
  - 2028-05-01
  - 2028-05-29
  - 2028-06-19
  - 2028-06-26
  - 2028-07-03
  - 2028-07-20
  - 2028-08-07
  - 2028-10-16
  - 2028-11-13
  - 2028-12-08
  - 2028-12-25
  - 2028-12-29
  - 2029-01-08
  - 2029-03-19
  - 2029-05-01
  - 2029-05-14
  - 2029-06-04
  - 2029-06-11
  - 2029-07-02
  - 2029-07-20
  - 2029-08-07
  - 2029-10-15
  - 2029-11-12
  - 2029-12-25
  - 2030-01-07
  - 2030-03-25
  - 2030-05-01
  - 2030-06-03
  - 2030-06-24
  - 2030-07-01
  - 2030-08-07
  - 2030-10-14
  - 2030-11-11
  - 2030-12-25', 1, NOW(), 1, NOW(), 0),
  ('XBOM', 'Bombay Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2026
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffsetMinus22000, type: EASTER_RELATIVE, offset: -2}
additionalClosures:
  - 2000-01-26
  - 2000-03-17
  - 2000-03-20
  - 2000-04-14
  - 2000-05-01
  - 2000-08-15
  - 2000-09-01
  - 2000-10-02
  - 2000-12-25
  - 2001-01-01
  - 2001-01-26
  - 2001-03-06
  - 2001-04-05
  - 2001-05-01
  - 2001-08-15
  - 2001-08-22
  - 2001-10-02
  - 2001-10-26
  - 2001-11-16
  - 2001-11-30
  - 2001-12-17
  - 2001-12-25
  - 2002-03-25
  - 2002-05-01
  - 2002-08-15
  - 2002-09-10
  - 2002-10-02
  - 2002-10-15
  - 2002-11-06
  - 2002-11-19
  - 2002-12-25
  - 2003-02-13
  - 2003-03-14
  - 2003-03-18
  - 2003-04-14
  - 2003-05-01
  - 2003-08-15
  - 2003-10-02
  - 2003-11-26
  - 2003-12-25
  - 2004-01-01
  - 2004-01-26
  - 2004-02-02
  - 2004-03-02
  - 2004-04-14
  - 2004-04-26
  - 2004-10-13
  - 2004-10-22
  - 2004-11-15
  - 2004-11-26
  - 2005-01-21
  - 2005-01-26
  - 2005-04-14
  - 2005-07-28
  - 2005-08-15
  - 2005-09-07
  - 2005-10-12
  - 2005-11-03
  - 2005-11-04
  - 2005-11-15
  - 2006-01-11
  - 2006-01-26
  - 2006-02-09
  - 2006-03-15
  - 2006-04-06
  - 2006-04-11
  - 2006-05-01
  - 2006-08-15
  - 2006-10-02
  - 2006-10-24
  - 2006-10-25
  - 2006-12-25
  - 2007-01-01
  - 2007-01-26
  - 2007-01-30
  - 2007-02-16
  - 2007-03-27
  - 2007-05-01
  - 2007-05-02
  - 2007-08-15
  - 2007-10-02
  - 2007-12-21
  - 2007-12-25
  - 2008-03-06
  - 2008-03-20
  - 2008-04-14
  - 2008-04-18
  - 2008-05-01
  - 2008-05-19
  - 2008-08-15
  - 2008-09-03
  - 2008-10-02
  - 2008-10-09
  - 2008-10-30
  - 2008-11-13
  - 2008-11-27
  - 2008-12-09
  - 2008-12-25
  - 2009-01-08
  - 2009-01-26
  - 2009-02-23
  - 2009-03-10
  - 2009-03-11
  - 2009-04-03
  - 2009-04-07
  - 2009-04-14
  - 2009-04-30
  - 2009-05-01
  - 2009-09-21
  - 2009-09-28
  - 2009-10-02
  - 2009-10-13
  - 2009-10-19
  - 2009-11-02
  - 2009-12-25
  - 2009-12-28
  - 2010-01-01
  - 2010-01-26
  - 2010-02-12
  - 2010-03-01
  - 2010-03-24
  - 2010-04-14
  - 2010-09-10
  - 2010-11-17
  - 2010-12-17
  - 2011-01-26
  - 2011-03-02
  - 2011-04-12
  - 2011-04-14
  - 2011-08-15
  - 2011-08-31
  - 2011-09-01
  - 2011-10-06
  - 2011-10-27
  - 2011-11-07
  - 2011-11-10
  - 2011-12-06
  - 2012-01-26
  - 2012-02-20
  - 2012-03-08
  - 2012-04-05
  - 2012-05-01
  - 2012-08-15
  - 2012-08-20
  - 2012-09-19
  - 2012-10-02
  - 2012-10-24
  - 2012-11-14
  - 2012-11-28
  - 2012-12-25
  - 2013-03-27
  - 2013-04-19
  - 2013-04-24
  - 2013-05-01
  - 2013-08-09
  - 2013-08-15
  - 2013-09-09
  - 2013-10-02
  - 2013-10-16
  - 2013-11-04
  - 2013-11-14
  - 2013-12-25
  - 2014-02-27
  - 2014-03-17
  - 2014-04-08
  - 2014-04-14
  - 2014-04-24
  - 2014-05-01
  - 2014-07-29
  - 2014-08-15
  - 2014-08-29
  - 2014-10-02
  - 2014-10-03
  - 2014-10-06
  - 2014-10-15
  - 2014-10-23
  - 2014-10-24
  - 2014-11-04
  - 2014-11-06
  - 2014-12-25
  - 2015-01-26
  - 2015-02-17
  - 2015-03-06
  - 2015-04-02
  - 2015-04-14
  - 2015-05-01
  - 2015-09-17
  - 2015-09-25
  - 2015-10-02
  - 2015-10-22
  - 2015-11-11
  - 2015-11-12
  - 2015-11-25
  - 2015-12-25
  - 2016-01-26
  - 2016-03-07
  - 2016-03-24
  - 2016-04-14
  - 2016-04-15
  - 2016-04-19
  - 2016-07-06
  - 2016-08-15
  - 2016-09-05
  - 2016-09-13
  - 2016-10-11
  - 2016-10-12
  - 2016-10-31
  - 2016-11-14
  - 2017-01-26
  - 2017-02-24
  - 2017-03-13
  - 2017-04-04
  - 2017-05-01
  - 2017-06-26
  - 2017-08-15
  - 2017-08-25
  - 2017-10-02
  - 2017-10-19
  - 2017-10-20
  - 2017-12-25
  - 2018-01-26
  - 2018-02-13
  - 2018-03-02
  - 2018-03-29
  - 2018-05-01
  - 2018-08-15
  - 2018-08-22
  - 2018-09-13
  - 2018-09-20
  - 2018-10-02
  - 2018-10-18
  - 2018-11-07
  - 2018-11-08
  - 2018-11-23
  - 2018-12-25
  - 2019-03-04
  - 2019-03-21
  - 2019-04-17
  - 2019-04-29
  - 2019-05-01
  - 2019-06-05
  - 2019-08-12
  - 2019-08-15
  - 2019-09-02
  - 2019-09-10
  - 2019-10-02
  - 2019-10-08
  - 2019-10-21
  - 2019-10-28
  - 2019-11-12
  - 2019-12-25
  - 2020-02-21
  - 2020-03-10
  - 2020-04-02
  - 2020-04-06
  - 2020-04-14
  - 2020-05-01
  - 2020-05-25
  - 2020-10-02
  - 2020-11-16
  - 2020-11-30
  - 2020-12-25
  - 2021-01-26
  - 2021-03-11
  - 2021-03-29
  - 2021-04-14
  - 2021-04-21
  - 2021-05-13
  - 2021-07-21
  - 2021-08-19
  - 2021-09-10
  - 2021-10-15
  - 2021-11-04
  - 2021-11-05
  - 2021-11-19
  - 2022-01-26
  - 2022-03-01
  - 2022-03-18
  - 2022-04-14
  - 2022-05-03
  - 2022-08-09
  - 2022-08-15
  - 2022-08-31
  - 2022-10-05
  - 2022-10-24
  - 2022-10-26
  - 2022-11-08
  - 2023-01-26
  - 2023-03-07
  - 2023-03-30
  - 2023-04-04
  - 2023-04-14
  - 2023-05-01
  - 2023-06-29
  - 2023-08-15
  - 2023-09-19
  - 2023-10-02
  - 2023-10-24
  - 2023-11-14
  - 2023-11-27
  - 2023-12-25
  - 2024-01-22
  - 2024-01-26
  - 2024-03-08
  - 2024-03-25
  - 2024-04-11
  - 2024-04-17
  - 2024-05-01
  - 2024-05-20
  - 2024-06-17
  - 2024-07-17
  - 2024-08-15
  - 2024-10-02
  - 2024-11-01
  - 2024-11-15
  - 2024-11-20
  - 2024-12-25
  - 2025-02-26
  - 2025-03-14
  - 2025-03-31
  - 2025-04-10
  - 2025-04-14
  - 2025-05-01
  - 2025-08-15
  - 2025-08-27
  - 2025-10-02
  - 2025-10-21
  - 2025-10-22
  - 2025-11-05
  - 2025-12-25
  - 2026-01-15
  - 2026-01-26
  - 2026-03-03
  - 2026-03-26
  - 2026-03-31
  - 2026-04-14
  - 2026-05-01
  - 2026-05-28
  - 2026-06-26
  - 2026-09-14
  - 2026-10-02
  - 2026-10-20
  - 2026-11-10
  - 2026-11-24
  - 2026-12-25', 1, NOW(), 1, NOW(), 0),
  ('XBRA', 'Bratislava Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffsetMinus22000, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffsetPlus12000, type: EASTER_RELATIVE, offset: 1}
  - {name: Fixed1224NextMonday2000, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY}
  - {name: Fixed1225NearestWeekday2000, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Fixed1226NearestWeekday2011, type: FIXED, month: 12, day: 26, observance: NEAREST_WEEKDAY, validFrom: 2011, validTo: 2020}
  - {name: Fixed1231NearestWeekday2023, type: FIXED, month: 12, day: 31, observance: NEAREST_WEEKDAY, validFrom: 2023}
  - {name: Fixed0101NearestWeekday2024, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2024}
  - {name: Fixed0101NearestWeekday2018, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2018, validTo: 2022}
additionalClosures:
  - 2000-01-06
  - 2000-05-01
  - 2000-05-08
  - 2000-07-05
  - 2000-08-29
  - 2000-09-01
  - 2000-09-15
  - 2000-11-01
  - 2000-11-17
  - 2000-12-26
  - 2001-01-01
  - 2001-05-01
  - 2001-05-08
  - 2001-07-05
  - 2001-08-29
  - 2001-11-01
  - 2001-12-26
  - 2002-01-01
  - 2002-05-01
  - 2002-05-08
  - 2002-07-05
  - 2002-08-29
  - 2002-11-01
  - 2002-12-26
  - 2003-01-01
  - 2003-01-06
  - 2003-05-01
  - 2003-05-08
  - 2003-08-29
  - 2003-09-01
  - 2003-09-15
  - 2003-11-17
  - 2003-12-26
  - 2004-01-01
  - 2004-01-06
  - 2004-07-05
  - 2004-09-01
  - 2004-09-15
  - 2004-11-01
  - 2004-11-17
  - 2005-01-06
  - 2005-07-05
  - 2005-08-29
  - 2005-09-01
  - 2005-09-15
  - 2005-11-01
  - 2005-11-17
  - 2006-01-06
  - 2006-05-01
  - 2006-05-08
  - 2006-07-05
  - 2006-08-29
  - 2006-09-01
  - 2006-09-15
  - 2006-11-01
  - 2006-11-17
  - 2006-12-26
  - 2007-01-01
  - 2007-05-01
  - 2007-05-08
  - 2007-07-05
  - 2007-08-29
  - 2007-11-01
  - 2007-12-26
  - 2008-01-01
  - 2008-05-01
  - 2008-05-08
  - 2008-08-29
  - 2008-09-01
  - 2008-09-15
  - 2008-11-17
  - 2008-12-26
  - 2009-01-01
  - 2009-01-06
  - 2009-05-01
  - 2009-05-08
  - 2009-09-01
  - 2009-09-15
  - 2009-11-17
  - 2010-01-01
  - 2010-01-06
  - 2010-07-05
  - 2010-09-01
  - 2010-09-15
  - 2010-11-01
  - 2010-11-17
  - 2011-01-06
  - 2011-07-05
  - 2011-08-29
  - 2011-09-01
  - 2011-09-15
  - 2011-11-01
  - 2011-11-17
  - 2012-01-06
  - 2012-05-01
  - 2012-05-08
  - 2012-07-05
  - 2012-08-29
  - 2012-11-01
  - 2013-01-01
  - 2013-05-01
  - 2013-05-08
  - 2013-07-05
  - 2013-08-29
  - 2013-11-01
  - 2014-01-01
  - 2014-01-06
  - 2014-05-01
  - 2014-05-08
  - 2014-08-29
  - 2014-09-01
  - 2014-09-15
  - 2014-11-17
  - 2015-01-01
  - 2015-01-06
  - 2015-05-01
  - 2015-05-08
  - 2015-09-01
  - 2015-09-15
  - 2015-11-17
  - 2016-01-01
  - 2016-01-06
  - 2016-07-05
  - 2016-08-29
  - 2016-09-01
  - 2016-09-15
  - 2016-11-01
  - 2016-11-17
  - 2017-01-06
  - 2017-05-01
  - 2017-05-08
  - 2017-07-05
  - 2017-08-29
  - 2017-09-01
  - 2017-09-15
  - 2017-11-01
  - 2017-11-17
  - 2018-01-02
  - 2018-05-01
  - 2018-05-08
  - 2018-07-05
  - 2018-08-29
  - 2018-10-30
  - 2018-11-01
  - 2018-12-31
  - 2019-05-01
  - 2019-05-08
  - 2019-07-05
  - 2019-08-29
  - 2019-11-01
  - 2019-12-31
  - 2020-01-06
  - 2020-05-01
  - 2020-05-08
  - 2020-09-01
  - 2020-09-15
  - 2020-11-17
  - 2020-12-31
  - 2021-01-06
  - 2021-07-05
  - 2021-09-01
  - 2021-09-15
  - 2021-11-01
  - 2021-11-17
  - 2022-01-06
  - 2022-07-05
  - 2022-08-29
  - 2022-09-01
  - 2022-09-15
  - 2022-11-01
  - 2022-11-17
  - 2023-01-06
  - 2023-05-01
  - 2023-05-08
  - 2023-07-05
  - 2023-08-29
  - 2023-09-01
  - 2023-09-15
  - 2023-11-01
  - 2023-11-17
  - 2023-12-26
  - 2024-05-01
  - 2024-05-08
  - 2024-07-05
  - 2024-08-29
  - 2024-11-01
  - 2024-12-26
  - 2025-01-06
  - 2025-05-01
  - 2025-05-08
  - 2025-08-29
  - 2025-09-15
  - 2025-11-17
  - 2025-12-26
  - 2026-01-06
  - 2026-05-01
  - 2026-05-08
  - 2026-09-15
  - 2026-11-17
  - 2027-01-06
  - 2027-07-05
  - 2027-09-15
  - 2027-11-01
  - 2027-11-17
  - 2028-01-06
  - 2028-05-01
  - 2028-05-08
  - 2028-07-05
  - 2028-08-29
  - 2028-09-15
  - 2028-11-01
  - 2028-11-17
  - 2028-12-26
  - 2029-05-01
  - 2029-05-08
  - 2029-07-05
  - 2029-08-29
  - 2029-11-01
  - 2029-12-26
  - 2030-05-01
  - 2030-05-08
  - 2030-07-05
  - 2030-08-29
  - 2030-11-01
  - 2030-12-26', 1, NOW(), 1, NOW(), 0),
  ('XBRN', 'BX Swiss', 'note: >
  BX Swiss follows the SIX trading calendar. GT already assigns both venues the same reference index (3493), so the two
  calendars are expected to agree.', 1, NOW(), 1, NOW(), 0),
  ('XBRU', 'Euronext Brussels', 'note: >
  Brussels follows the Euronext base calendar. Belgian national day on 21 July and Armistice on 11 November are public
  holidays on which the exchange nevertheless trades.
additionalClosures:
  - 2000-06-01
  - 2000-07-21
  - 2001-12-31
openDates:
  - 2002-05-20
  - 2003-06-09
  - 2004-05-31', 1, NOW(), 1, NOW(), 0),
  ('XBSE', 'Bucharest Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: Fixed0101NearestWeekday2012, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2012, validTo: 2021}
  - {name: Fixed0102NearestWeekday2012, type: FIXED, month: 1, day: 2, observance: NEAREST_WEEKDAY, validFrom: 2012, validTo: 2021}
  - {name: Fixed0501NearestWeekday2012, type: FIXED, month: 5, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2012, validTo: 2021}
  - {name: Fixed1130NearestWeekday2003, type: FIXED, month: 11, day: 30, observance: NEAREST_WEEKDAY, validFrom: 2003, validTo: 2012}
  - {name: Fixed1201NearestWeekday2003, type: FIXED, month: 12, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2003, validTo: 2012}
  - {name: Fixed1225NearestWeekday2011, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY, validFrom: 2011, validTo: 2020}
  - {name: Fixed1226NearestWeekday2011, type: FIXED, month: 12, day: 26, observance: NEAREST_WEEKDAY, validFrom: 2011, validTo: 2020}
  - {name: Fixed0501NearestWeekday2023, type: FIXED, month: 5, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2023}
  - {name: Fixed0101NearestWeekday2006, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2006, validTo: 2010}
  - {name: Fixed0101NearestWeekday2023, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2023, validTo: 2027}
  - {name: Fixed0501NearestWeekday2005, type: FIXED, month: 5, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2005, validTo: 2009}
  - {name: Fixed1130NearestWeekday2014, type: FIXED, month: 11, day: 30, observance: NEAREST_WEEKDAY, validFrom: 2014, validTo: 2018}
  - {name: Fixed1130NearestWeekday2025, type: FIXED, month: 11, day: 30, observance: NEAREST_WEEKDAY, validFrom: 2025, validTo: 2029}
  - {name: Fixed1225NearestWeekday2005, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY, validFrom: 2005, validTo: 2009}
  - {name: Fixed1225NearestWeekday2022, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY, validFrom: 2022, validTo: 2026}
additionalClosures:
  - 2000-01-24
  - 2000-04-28
  - 2000-05-01
  - 2000-06-01
  - 2000-06-19
  - 2000-08-15
  - 2000-11-30
  - 2000-12-01
  - 2000-12-25
  - 2000-12-26
  - 2001-01-01
  - 2001-01-02
  - 2001-01-24
  - 2001-04-13
  - 2001-04-16
  - 2001-05-01
  - 2001-06-01
  - 2001-06-04
  - 2001-08-15
  - 2001-11-30
  - 2001-12-25
  - 2001-12-26
  - 2002-01-01
  - 2002-01-02
  - 2002-01-24
  - 2002-05-01
  - 2002-05-03
  - 2002-05-06
  - 2002-06-24
  - 2002-08-15
  - 2002-12-25
  - 2002-12-26
  - 2003-01-01
  - 2003-01-02
  - 2003-01-24
  - 2003-04-25
  - 2003-04-28
  - 2003-05-01
  - 2003-06-16
  - 2003-08-15
  - 2003-12-25
  - 2003-12-26
  - 2004-01-01
  - 2004-01-02
  - 2004-04-09
  - 2004-04-12
  - 2004-05-31
  - 2004-06-01
  - 2005-01-24
  - 2005-04-29
  - 2005-06-01
  - 2005-06-20
  - 2005-08-15
  - 2006-01-24
  - 2006-04-21
  - 2006-04-24
  - 2006-06-01
  - 2006-06-12
  - 2006-08-15
  - 2006-12-26
  - 2007-01-02
  - 2007-01-24
  - 2007-04-06
  - 2007-04-09
  - 2007-05-28
  - 2007-06-01
  - 2007-08-15
  - 2007-12-26
  - 2008-01-02
  - 2008-01-24
  - 2008-04-25
  - 2008-04-28
  - 2008-06-16
  - 2008-08-15
  - 2008-12-26
  - 2009-01-02
  - 2009-04-17
  - 2009-04-20
  - 2009-06-01
  - 2009-06-08
  - 2010-04-02
  - 2010-04-05
  - 2010-05-24
  - 2010-06-01
  - 2011-01-24
  - 2011-04-22
  - 2011-04-25
  - 2011-06-01
  - 2011-06-13
  - 2011-08-15
  - 2012-01-24
  - 2012-04-13
  - 2012-04-16
  - 2012-06-01
  - 2012-06-04
  - 2012-08-15
  - 2013-01-24
  - 2013-05-03
  - 2013-05-06
  - 2013-06-24
  - 2013-08-15
  - 2014-01-24
  - 2014-04-18
  - 2014-04-21
  - 2014-06-09
  - 2014-08-15
  - 2015-04-10
  - 2015-04-13
  - 2015-06-01
  - 2015-12-01
  - 2016-04-29
  - 2016-06-01
  - 2016-06-20
  - 2016-08-15
  - 2016-12-01
  - 2017-01-24
  - 2017-04-14
  - 2017-04-17
  - 2017-06-01
  - 2017-06-05
  - 2017-08-15
  - 2017-12-01
  - 2018-01-24
  - 2018-04-06
  - 2018-04-09
  - 2018-05-28
  - 2018-06-01
  - 2018-08-15
  - 2019-01-24
  - 2019-04-26
  - 2019-04-29
  - 2019-06-17
  - 2019-08-15
  - 2020-01-24
  - 2020-04-17
  - 2020-04-20
  - 2020-06-01
  - 2020-06-08
  - 2020-11-30
  - 2020-12-01
  - 2021-05-03
  - 2021-06-01
  - 2021-06-21
  - 2021-11-30
  - 2021-12-01
  - 2022-01-24
  - 2022-04-22
  - 2022-04-25
  - 2022-06-01
  - 2022-06-13
  - 2022-08-15
  - 2022-11-30
  - 2022-12-01
  - 2023-01-24
  - 2023-04-14
  - 2023-04-17
  - 2023-06-01
  - 2023-06-05
  - 2023-08-15
  - 2023-11-30
  - 2023-12-01
  - 2023-12-26
  - 2024-01-02
  - 2024-01-24
  - 2024-05-03
  - 2024-05-06
  - 2024-06-24
  - 2024-08-15
  - 2024-12-26
  - 2025-01-02
  - 2025-01-24
  - 2025-04-18
  - 2025-04-21
  - 2025-06-09
  - 2025-08-15
  - 2025-12-26
  - 2026-01-02
  - 2026-04-10
  - 2026-04-13
  - 2026-06-01
  - 2026-12-01
  - 2027-05-03
  - 2027-06-01
  - 2027-06-21
  - 2027-12-01
  - 2028-01-24
  - 2028-04-14
  - 2028-04-17
  - 2028-06-01
  - 2028-06-05
  - 2028-08-15
  - 2028-12-01
  - 2028-12-25
  - 2028-12-26
  - 2029-01-01
  - 2029-01-02
  - 2029-01-24
  - 2029-04-06
  - 2029-04-09
  - 2029-05-28
  - 2029-06-01
  - 2029-08-15
  - 2029-12-25
  - 2029-12-26
  - 2030-01-01
  - 2030-01-02
  - 2030-01-24
  - 2030-04-26
  - 2030-04-29
  - 2030-06-17
  - 2030-08-15
  - 2030-12-25
  - 2030-12-26', 1, NOW(), 1, NOW(), 0),
  ('XBUD', 'Budapest Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffsetPlus12000, type: EASTER_RELATIVE, offset: 1}
  - {name: EasterOffsetPlus502000, type: EASTER_RELATIVE, offset: 50}
  - {name: Fixed1224NextMonday2000, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY}
  - {name: Fixed1225NearestWeekday2000, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: EasterOffsetMinus22012, type: EASTER_RELATIVE, offset: -2, validFrom: 2012}
  - {name: Fixed1226NearestWeekday2011, type: FIXED, month: 12, day: 26, observance: NEAREST_WEEKDAY, validFrom: 2011, validTo: 2020}
  - {name: Fixed1231NearestWeekday2023, type: FIXED, month: 12, day: 31, observance: NEAREST_WEEKDAY, validFrom: 2023}
  - {name: Fixed0101NearestWeekday2024, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2024}
  - {name: Fixed0101NearestWeekday2018, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2018, validTo: 2022}
additionalClosures:
  - 2000-03-15
  - 2000-05-01
  - 2000-10-23
  - 2000-11-01
  - 2000-12-26
  - 2001-01-01
  - 2001-03-15
  - 2001-03-16
  - 2001-04-30
  - 2001-05-01
  - 2001-08-20
  - 2001-10-22
  - 2001-10-23
  - 2001-11-01
  - 2001-11-02
  - 2001-12-26
  - 2001-12-31
  - 2002-01-01
  - 2002-03-15
  - 2002-05-01
  - 2002-08-19
  - 2002-08-20
  - 2002-10-23
  - 2002-11-01
  - 2002-12-26
  - 2003-01-01
  - 2003-05-01
  - 2003-05-02
  - 2003-08-20
  - 2003-10-23
  - 2003-10-24
  - 2003-12-26
  - 2004-01-01
  - 2004-01-02
  - 2004-03-15
  - 2004-08-20
  - 2004-11-01
  - 2005-03-14
  - 2005-03-15
  - 2005-10-31
  - 2005-11-01
  - 2006-03-15
  - 2006-05-01
  - 2006-10-23
  - 2006-11-01
  - 2006-12-26
  - 2007-01-01
  - 2007-03-15
  - 2007-03-16
  - 2007-04-30
  - 2007-05-01
  - 2007-08-20
  - 2007-10-22
  - 2007-10-23
  - 2007-11-01
  - 2007-11-02
  - 2007-12-26
  - 2007-12-31
  - 2008-01-01
  - 2008-05-01
  - 2008-05-02
  - 2008-08-20
  - 2008-10-23
  - 2008-10-24
  - 2008-12-26
  - 2009-01-01
  - 2009-01-02
  - 2009-05-01
  - 2009-08-20
  - 2009-08-21
  - 2009-10-23
  - 2010-01-01
  - 2010-03-15
  - 2010-08-20
  - 2010-11-01
  - 2011-03-14
  - 2011-03-15
  - 2011-10-31
  - 2011-11-01
  - 2012-03-15
  - 2012-03-16
  - 2012-04-30
  - 2012-05-01
  - 2012-08-20
  - 2012-10-22
  - 2012-10-23
  - 2012-11-01
  - 2012-11-02
  - 2012-12-31
  - 2013-01-01
  - 2013-03-15
  - 2013-05-01
  - 2013-08-19
  - 2013-08-20
  - 2013-10-23
  - 2013-11-01
  - 2013-12-27
  - 2013-12-31
  - 2014-01-01
  - 2014-05-01
  - 2014-05-02
  - 2014-08-20
  - 2014-10-23
  - 2014-10-24
  - 2014-12-31
  - 2015-01-01
  - 2015-01-02
  - 2015-05-01
  - 2015-08-20
  - 2015-08-21
  - 2015-10-23
  - 2015-12-31
  - 2016-01-01
  - 2016-03-14
  - 2016-03-15
  - 2016-10-31
  - 2016-11-01
  - 2017-03-15
  - 2017-05-01
  - 2017-10-23
  - 2017-11-01
  - 2018-03-15
  - 2018-03-16
  - 2018-04-30
  - 2018-05-01
  - 2018-08-20
  - 2018-10-22
  - 2018-10-23
  - 2018-11-01
  - 2018-11-02
  - 2018-12-31
  - 2019-03-15
  - 2019-05-01
  - 2019-08-19
  - 2019-08-20
  - 2019-10-23
  - 2019-11-01
  - 2019-12-27
  - 2019-12-31
  - 2020-05-01
  - 2020-08-20
  - 2020-08-21
  - 2020-10-23
  - 2020-12-31
  - 2021-03-15
  - 2021-08-20
  - 2021-11-01
  - 2022-03-14
  - 2022-03-15
  - 2022-10-31
  - 2022-11-01
  - 2023-03-15
  - 2023-05-01
  - 2023-10-23
  - 2023-11-01
  - 2023-12-26
  - 2024-03-15
  - 2024-05-01
  - 2024-08-19
  - 2024-08-20
  - 2024-10-23
  - 2024-11-01
  - 2024-12-26
  - 2024-12-27
  - 2025-05-01
  - 2025-05-02
  - 2025-08-20
  - 2025-10-23
  - 2025-10-24
  - 2025-12-26
  - 2026-01-02
  - 2026-05-01
  - 2026-08-20
  - 2026-08-21
  - 2026-10-23
  - 2027-03-15
  - 2027-08-20
  - 2027-11-01
  - 2028-03-15
  - 2028-05-01
  - 2028-10-23
  - 2028-11-01
  - 2028-12-26
  - 2029-03-15
  - 2029-03-16
  - 2029-04-30
  - 2029-05-01
  - 2029-08-20
  - 2029-10-22
  - 2029-10-23
  - 2029-11-01
  - 2029-11-02
  - 2029-12-26
  - 2030-03-15
  - 2030-05-01
  - 2030-08-19
  - 2030-08-20
  - 2030-10-23
  - 2030-11-01
  - 2030-12-26
  - 2030-12-27', 1, NOW(), 1, NOW(), 0),
  ('XBUE', 'Buenos Aires Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffsetMinus22000, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffsetMinus32000, type: EASTER_RELATIVE, offset: -3}
  - {name: Nth083Monday2012, type: NTH_WEEKDAY, month: 8, nth: 3, dayOfWeek: MONDAY, validFrom: 2012}
  - {name: Nth063Monday2000, type: NTH_WEEKDAY, month: 6, nth: 3, dayOfWeek: MONDAY, validTo: 2011}
  - {name: Nth083Monday2000, type: NTH_WEEKDAY, month: 8, nth: 3, dayOfWeek: MONDAY, validTo: 2010}
  - {name: Nth102Monday2008, type: NTH_WEEKDAY, month: 10, nth: 2, dayOfWeek: MONDAY, validFrom: 2008, validTo: 2016}
  - {name: Fixed0101NearestWeekday2012, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2012, validTo: 2016}
  - {name: Fixed0709NearestWeekday2012, type: FIXED, month: 7, day: 9, observance: NEAREST_WEEKDAY, validFrom: 2012, validTo: 2016}
additionalClosures:
  - 2000-04-03
  - 2000-05-01
  - 2000-05-25
  - 2000-10-16
  - 2000-11-06
  - 2000-12-08
  - 2000-12-25
  - 2001-01-01
  - 2001-04-02
  - 2001-05-01
  - 2001-05-25
  - 2001-07-09
  - 2001-10-15
  - 2001-11-06
  - 2001-12-25
  - 2002-01-01
  - 2002-01-07
  - 2002-01-08
  - 2002-01-09
  - 2002-01-10
  - 2002-01-11
  - 2002-01-14
  - 2002-01-15
  - 2002-01-16
  - 2002-04-01
  - 2002-04-22
  - 2002-04-23
  - 2002-04-24
  - 2002-04-25
  - 2002-04-26
  - 2002-05-01
  - 2002-07-09
  - 2002-10-14
  - 2002-11-06
  - 2002-12-25
  - 2003-01-01
  - 2003-03-31
  - 2003-05-01
  - 2003-07-09
  - 2003-10-13
  - 2003-11-06
  - 2003-12-08
  - 2003-12-25
  - 2004-01-01
  - 2004-04-05
  - 2004-05-25
  - 2004-07-09
  - 2004-10-11
  - 2004-12-08
  - 2005-05-25
  - 2005-10-10
  - 2005-12-08
  - 2006-03-24
  - 2006-05-01
  - 2006-05-25
  - 2006-10-16
  - 2006-11-06
  - 2006-12-08
  - 2006-12-25
  - 2007-01-01
  - 2007-04-02
  - 2007-05-01
  - 2007-05-25
  - 2007-07-09
  - 2007-10-15
  - 2007-11-06
  - 2007-12-25
  - 2008-01-01
  - 2008-03-24
  - 2008-04-02
  - 2008-05-01
  - 2008-07-09
  - 2008-11-06
  - 2008-12-08
  - 2008-12-25
  - 2009-01-01
  - 2009-03-24
  - 2009-04-02
  - 2009-05-01
  - 2009-05-25
  - 2009-07-09
  - 2009-07-10
  - 2009-11-06
  - 2009-12-08
  - 2009-12-25
  - 2010-01-01
  - 2010-03-24
  - 2010-05-24
  - 2010-05-25
  - 2010-07-09
  - 2010-10-27
  - 2010-11-22
  - 2010-12-08
  - 2011-03-07
  - 2011-03-08
  - 2011-03-24
  - 2011-03-25
  - 2011-05-25
  - 2011-08-22
  - 2011-11-28
  - 2011-12-08
  - 2011-12-09
  - 2012-02-20
  - 2012-02-21
  - 2012-02-27
  - 2012-04-02
  - 2012-04-30
  - 2012-05-01
  - 2012-05-25
  - 2012-06-20
  - 2012-09-24
  - 2012-11-06
  - 2012-11-26
  - 2012-12-25
  - 2013-01-31
  - 2013-02-11
  - 2013-02-12
  - 2013-02-20
  - 2013-04-01
  - 2013-04-02
  - 2013-05-01
  - 2013-06-20
  - 2013-06-21
  - 2013-11-06
  - 2013-11-25
  - 2013-12-25
  - 2014-03-03
  - 2014-03-04
  - 2014-03-24
  - 2014-04-02
  - 2014-05-01
  - 2014-05-02
  - 2014-06-20
  - 2014-11-06
  - 2014-11-24
  - 2014-12-08
  - 2014-12-25
  - 2014-12-26
  - 2015-02-16
  - 2015-02-17
  - 2015-03-23
  - 2015-03-24
  - 2015-05-01
  - 2015-05-25
  - 2015-11-06
  - 2015-11-27
  - 2015-12-07
  - 2015-12-08
  - 2015-12-25
  - 2016-02-08
  - 2016-02-09
  - 2016-05-25
  - 2016-06-17
  - 2016-06-20
  - 2016-11-28
  - 2016-12-08
  - 2016-12-09
  - 2017-02-27
  - 2017-02-28
  - 2017-03-24
  - 2017-05-01
  - 2017-05-25
  - 2017-06-20
  - 2017-10-16
  - 2017-11-06
  - 2017-11-20
  - 2017-12-08
  - 2017-12-25
  - 2018-01-01
  - 2018-02-12
  - 2018-02-13
  - 2018-04-02
  - 2018-04-30
  - 2018-05-01
  - 2018-05-25
  - 2018-06-20
  - 2018-07-09
  - 2018-10-15
  - 2018-11-19
  - 2018-11-30
  - 2018-12-24
  - 2018-12-25
  - 2018-12-31
  - 2019-01-01
  - 2019-03-04
  - 2019-03-05
  - 2019-04-02
  - 2019-05-01
  - 2019-06-17
  - 2019-06-20
  - 2019-07-08
  - 2019-07-09
  - 2019-10-14
  - 2019-11-18
  - 2019-12-25
  - 2020-01-01
  - 2020-02-24
  - 2020-02-25
  - 2020-03-24
  - 2020-04-02
  - 2020-05-01
  - 2020-05-25
  - 2020-06-17
  - 2020-07-09
  - 2020-10-12
  - 2020-11-23
  - 2020-12-08
  - 2020-12-25
  - 2021-01-01
  - 2021-02-15
  - 2021-02-16
  - 2021-03-24
  - 2021-05-25
  - 2021-06-17
  - 2021-07-09
  - 2021-10-11
  - 2021-11-22
  - 2021-12-08
  - 2022-02-28
  - 2022-03-01
  - 2022-03-24
  - 2022-05-25
  - 2022-06-17
  - 2022-06-20
  - 2022-10-10
  - 2022-11-21
  - 2022-12-08
  - 2023-02-20
  - 2023-02-21
  - 2023-03-24
  - 2023-05-01
  - 2023-05-25
  - 2023-06-20
  - 2023-10-16
  - 2023-11-20
  - 2023-12-08
  - 2023-12-25
  - 2024-01-01
  - 2024-02-12
  - 2024-02-13
  - 2024-04-02
  - 2024-05-01
  - 2024-06-17
  - 2024-06-20
  - 2024-07-09
  - 2024-10-14
  - 2024-11-18
  - 2024-12-25
  - 2025-01-01
  - 2025-03-03
  - 2025-03-04
  - 2025-03-24
  - 2025-04-02
  - 2025-05-01
  - 2025-06-17
  - 2025-06-20
  - 2025-07-09
  - 2025-10-13
  - 2025-11-17
  - 2025-12-08
  - 2025-12-25
  - 2026-01-01
  - 2026-02-16
  - 2026-02-17
  - 2026-03-24
  - 2026-05-01
  - 2026-05-25
  - 2026-06-17
  - 2026-07-09
  - 2026-10-12
  - 2026-11-23
  - 2026-12-08
  - 2026-12-25
  - 2027-01-01
  - 2027-02-08
  - 2027-02-09
  - 2027-03-24
  - 2027-04-02
  - 2027-05-25
  - 2027-06-17
  - 2027-07-09
  - 2027-10-11
  - 2027-11-22
  - 2027-12-08
  - 2028-02-28
  - 2028-02-29
  - 2028-03-24
  - 2028-05-01
  - 2028-05-25
  - 2028-06-20
  - 2028-10-16
  - 2028-11-20
  - 2028-12-08
  - 2028-12-25
  - 2029-01-01
  - 2029-02-12
  - 2029-02-13
  - 2029-04-02
  - 2029-05-01
  - 2029-05-25
  - 2029-06-20
  - 2029-07-09
  - 2029-10-15
  - 2029-11-19
  - 2029-12-25
  - 2030-01-01
  - 2030-03-04
  - 2030-03-05
  - 2030-04-02
  - 2030-05-01
  - 2030-06-17
  - 2030-06-20
  - 2030-07-09
  - 2030-10-14
  - 2030-11-18
  - 2030-12-25', 1, NOW(), 1, NOW(), 0),
  ('XCAI', 'The Egyptian Exchange', 'note: >
  Egypt is the one exchange where the Islamic calendar drives the holidays, which is why the HIJRI rule type exists.
  The arithmetic Hijri calendar the JDK provides can be a day either side of the officially announced date, because the
  start of a month is fixed by the sighting of the moon; offsetTolerance widens each rule to that span. This
  deliberately over-reports by design -- a wider rule that certainly contains the true closure is more useful to the
  comparison report than a narrow one that misses it, and the report will show exactly how much noise it costs.
  The Egyptian working week ran Sunday to Thursday, so Friday and Saturday were the weekend. GT''s trading calendar
  treats Saturday and Sunday as the weekend for every exchange, which means Sundays are wrongly excluded and Fridays
  wrongly included for this venue. That mismatch is structural and no rule file can repair it; it deserves recording as
  a finding of its own.
rules:
  - {name: RevolutionDayJanuary, type: FIXED, month: 1, day: 25, validFrom: 2012}
  - {name: CopticChristmas, type: FIXED, month: 1, day: 7}
  - {name: SinaiLiberationDay, type: FIXED, month: 4, day: 25}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: RevolutionDayJune, type: FIXED, month: 6, day: 30, validFrom: 2014}
  - {name: RevolutionDayJuly, type: FIXED, month: 7, day: 23}
  - {name: ArmedForcesDay, type: FIXED, month: 10, day: 6}
  - {name: IslamicNewYear, type: HIJRI, month: 1, day: 1, offsetTolerance: 1}
  - {name: ProphetsBirthday, type: HIJRI, month: 3, day: 12, offsetTolerance: 1}
  - {name: EidAlFitr, type: HIJRI, month: 10, day: 1, offsetTolerance: 1}
  - {name: EidAlFitrSecondDay, type: HIJRI, month: 10, day: 2, offsetTolerance: 1}
  - {name: EidAlFitrThirdDay, type: HIJRI, month: 10, day: 3, offsetTolerance: 1}
  - {name: ArafatDay, type: HIJRI, month: 12, day: 9, offsetTolerance: 1}
  - {name: EidAlAdha, type: HIJRI, month: 12, day: 10, offsetTolerance: 1}
  - {name: EidAlAdhaSecondDay, type: HIJRI, month: 12, day: 11, offsetTolerance: 1}
  - {name: EidAlAdhaThirdDay, type: HIJRI, month: 12, day: 12, offsetTolerance: 1}', 1, NOW(), 1, NOW(), 0),
  ('XCBF', 'Cboe Futures Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffsetMinus22000, type: EASTER_RELATIVE, offset: -2}
  - {name: Fixed0704NearestWeekday2000, type: FIXED, month: 7, day: 4, observance: NEAREST_WEEKDAY}
  - {name: Fixed1225NearestWeekday2000, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Nth013Monday2000, type: NTH_WEEKDAY, month: 1, nth: 3, dayOfWeek: MONDAY}
  - {name: Nth023Monday2000, type: NTH_WEEKDAY, month: 2, nth: 3, dayOfWeek: MONDAY}
  - {name: Nth05LastMonday2000, type: NTH_WEEKDAY, month: 5, nth: -1, dayOfWeek: MONDAY}
  - {name: Nth091Monday2000, type: NTH_WEEKDAY, month: 9, nth: 1, dayOfWeek: MONDAY}
  - {name: Nth114Thursday2000, type: NTH_WEEKDAY, month: 11, nth: 4, dayOfWeek: THURSDAY}
  - {name: Fixed0101NearestWeekday2012, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2012, validTo: 2021}
  - {name: Fixed0619NearestWeekday2022, type: FIXED, month: 6, day: 19, observance: NEAREST_WEEKDAY, validFrom: 2022}
  - {name: Fixed0101NearestWeekday2006, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2006, validTo: 2010}
  - {name: Fixed0101NearestWeekday2023, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY, validFrom: 2023, validTo: 2027}
additionalClosures:
  - 2001-01-01
  - 2002-01-01
  - 2003-01-01
  - 2004-01-01
  - 2004-06-11
  - 2007-01-02
  - 2012-10-29
  - 2012-10-30
  - 2018-12-05
  - 2025-01-09
  - 2029-01-01
  - 2030-01-01', 1, NOW(), 1, NOW(), 0),
  ('XCSE', 'Nasdaq Copenhagen', 'note: >
  Denmark closes on Maundy Thursday and on Store Bededag, the fourth Friday after Easter, which was abolished as a
  public holiday with effect from 2024. Constitution Day on 5 June and Christmas Eve are exchange closures even though
  they are only half public holidays in Denmark.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: MaundyThursday, type: EASTER_RELATIVE, offset: -3}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: StoreBededag, type: EASTER_RELATIVE, offset: 26, validTo: 2023}
  - {name: Ascension, type: EASTER_RELATIVE, offset: 39}
  - {name: WhitMonday, type: EASTER_RELATIVE, offset: 50}
  - {name: ConstitutionDay, type: FIXED, month: 6, day: 5}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
  - {name: StStephen, type: FIXED, month: 12, day: 26}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31}
additionalClosures:
  - 2009-05-22
  - 2010-05-14
  - 2011-06-03
  - 2012-05-18
  - 2013-05-10
  - 2014-05-30
  - 2015-05-15
  - 2016-05-06
  - 2017-05-26
  - 2018-05-11
  - 2019-05-31
  - 2020-05-22
  - 2021-05-14
  - 2022-05-27
  - 2023-05-19
  - 2024-05-10
  - 2025-05-30
  - 2026-05-15
  - 2027-05-07
  - 2028-05-26
  - 2029-05-11
  - 2030-05-31', 1, NOW(), 1, NOW(), 0),
  ('XCYS', 'Cyprus Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: Fixed1224NextMonday, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY}
  - {name: Fixed1225NearestWeekday, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed0815None, type: FIXED, month: 8, day: 15}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed1001None, type: FIXED, month: 10, day: 1}
  - {name: Fixed0106None, type: FIXED, month: 1, day: 6}
  - {name: Fixed0325None, type: FIXED, month: 3, day: 25}
  - {name: Fixed0401None, type: FIXED, month: 4, day: 1}
  - {name: Fixed1028None, type: FIXED, month: 10, day: 28}
additionalClosures:
  - 2000-03-13
  - 2000-04-28
  - 2000-05-02
  - 2000-06-19
  - 2001-02-26
  - 2001-04-17
  - 2001-06-04
  - 2002-03-18
  - 2002-05-03
  - 2002-05-06
  - 2002-05-07
  - 2002-06-24
  - 2003-03-10
  - 2003-04-25
  - 2003-04-28
  - 2003-04-29
  - 2003-06-16
  - 2004-02-23
  - 2004-04-13
  - 2004-05-31
  - 2005-03-14
  - 2005-04-29
  - 2005-05-02
  - 2005-05-03
  - 2005-06-20
  - 2006-03-06
  - 2006-04-21
  - 2006-04-24
  - 2006-04-25
  - 2006-06-12
  - 2007-02-19
  - 2007-04-10
  - 2007-05-28
  - 2008-03-10
  - 2008-04-25
  - 2008-04-28
  - 2008-04-29
  - 2008-06-16
  - 2009-03-02
  - 2009-04-17
  - 2009-04-20
  - 2009-04-21
  - 2009-06-08
  - 2010-02-15
  - 2010-04-06
  - 2010-05-24
  - 2011-03-07
  - 2011-04-26
  - 2011-06-13
  - 2012-02-27
  - 2012-04-13
  - 2012-04-16
  - 2012-04-17
  - 2012-06-04
  - 2013-03-18
  - 2013-05-03
  - 2013-05-06
  - 2013-05-07
  - 2013-06-24
  - 2014-03-03
  - 2014-04-22
  - 2014-06-09
  - 2015-02-23
  - 2015-04-10
  - 2015-04-13
  - 2015-04-14
  - 2015-06-01
  - 2016-03-14
  - 2016-04-29
  - 2016-05-02
  - 2016-05-03
  - 2016-06-20
  - 2017-02-27
  - 2017-04-18
  - 2017-06-05
  - 2018-02-19
  - 2018-04-06
  - 2018-04-09
  - 2018-04-10
  - 2018-05-28
  - 2019-03-11
  - 2019-04-26
  - 2019-04-29
  - 2019-04-30
  - 2019-06-17
  - 2020-03-02
  - 2020-04-17
  - 2020-04-20
  - 2020-04-21
  - 2020-06-08
  - 2021-03-15
  - 2021-04-30
  - 2021-05-03
  - 2021-05-04
  - 2021-06-21
  - 2022-03-07
  - 2022-04-22
  - 2022-04-25
  - 2022-04-26
  - 2022-06-13
  - 2023-02-27
  - 2023-04-14
  - 2023-04-17
  - 2023-04-18
  - 2023-06-05
  - 2024-03-18
  - 2024-05-03
  - 2024-05-06
  - 2024-05-07
  - 2024-06-24
  - 2025-03-03
  - 2025-04-22
  - 2025-06-09
  - 2026-02-23
  - 2026-04-10
  - 2026-04-13
  - 2026-04-14
  - 2026-06-01
  - 2027-03-15
  - 2027-04-30
  - 2027-05-03
  - 2027-05-04
  - 2027-06-21
  - 2028-02-28
  - 2028-04-18
  - 2028-06-05
  - 2029-02-19
  - 2029-04-06
  - 2029-04-09
  - 2029-04-10
  - 2029-05-28
  - 2030-03-11
  - 2030-04-26
  - 2030-04-29
  - 2030-04-30
  - 2030-06-17', 1, NOW(), 1, NOW(), 0),
  ('XDUS', 'Duesseldorf Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: Fixed1224NextMonday, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY}
  - {name: Fixed1225NearestWeekday, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
additionalClosures:
  - 2007-05-28
  - 2014-10-03
  - 2015-05-25
  - 2016-05-16
  - 2016-10-03
  - 2017-06-05
  - 2017-10-03
  - 2017-10-31
  - 2018-05-21
  - 2018-10-03
  - 2019-06-10
  - 2019-10-03
  - 2020-06-01
  - 2021-05-24', 1, NOW(), 1, NOW(), 0),
  ('XEEE', 'European Energy Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: Fixed1224NextMonday, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY}
  - {name: Fixed1225NearestWeekday, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}', 1, NOW(), 1, NOW(), 0),
  ('XETR', 'Xetra', 'note: >
  Xetra trades on the German federal holidays only; state holidays such as Fronleichnam or Reformationstag are not
  closures. Tag der Deutschen Einheit on 3 October is a public holiday but Xetra remains open, so it is absent here.
  24 and 31 December were shortened sessions in the early years and have been full closures since 2001; they are
  modelled as closures throughout and the comparison report will show whether the earlier years disagree.
  Whit Monday is deliberately absent. It is a German public holiday and was assumed to be a closure, but the
  comparison report contradicted that in 19 of 27 years, and a direct check confirms it: on Whit Monday 2024 62 of
  GT''s Xetra instruments delivered a price against 63 on the preceding ordinary Monday, whereas Good Friday 2024
  yielded 1. Xetra trades on Whit Monday.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
  - {name: StStephen, type: FIXED, month: 12, day: 26}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31}
additionalClosures:
  - 2007-05-28
  - 2014-10-03
  - 2015-05-25
  - 2016-05-16
  - 2016-10-03
  - 2017-06-05
  - 2017-10-03
  - 2017-10-31
  - 2018-05-21
  - 2018-10-03
  - 2019-06-10
  - 2019-10-03
  - 2020-06-01
  - 2021-05-24', 1, NOW(), 1, NOW(), 0),
  ('XEUR', 'Eurex', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: Fixed1224NextMonday, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY}
  - {name: Fixed1225NearestWeekday, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}', 1, NOW(), 1, NOW(), 0),
  ('XFRA', 'Frankfurt Stock Exchange', 'note: >
  German regional exchange following the Xetra trading calendar. Regional venues have historically kept slightly
  longer hours than Xetra but close on the same days; any divergence the comparison report surfaces belongs here as
  an explicit deviation rule.
additionalClosures:
  - 2007-05-28
  - 2014-10-03
  - 2015-05-25
  - 2016-05-16
  - 2016-10-03
  - 2017-06-05
  - 2017-10-03
  - 2017-10-31
  - 2018-05-21
  - 2018-10-03
  - 2019-06-10
  - 2019-10-03
  - 2020-06-01
  - 2021-05-24', 1, NOW(), 1, NOW(), 0),
  ('XHEL', 'Nasdaq Helsinki', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: EasterOffset39, type: EASTER_RELATIVE, offset: 39}
  - {name: Fixed1224NextMonday, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY}
  - {name: Fixed1225NearestWeekday, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed1206None, type: FIXED, month: 12, day: 6}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed0106None, type: FIXED, month: 1, day: 6}
additionalClosures:
  - 2000-06-23
  - 2001-06-22
  - 2002-06-21
  - 2003-06-20
  - 2004-06-25
  - 2005-06-24
  - 2006-06-23
  - 2007-06-22
  - 2008-06-20
  - 2009-06-19
  - 2010-06-25
  - 2011-06-24
  - 2012-06-22
  - 2013-06-21
  - 2014-06-20
  - 2015-06-19
  - 2016-06-24
  - 2017-06-23
  - 2018-06-22
  - 2019-06-21
  - 2020-06-19
  - 2021-06-25
  - 2022-06-24
  - 2023-06-23
  - 2024-06-21
  - 2025-06-20
  - 2026-06-19
  - 2027-06-25
  - 2028-06-23
  - 2029-06-22
  - 2030-06-21', 1, NOW(), 1, NOW(), 0),
  ('XHKG', 'Hong Kong Stock Exchange', 'note: >
  Hong Kong combines a Gregorian calendar (New Year, Labour Day, Establishment Day, National Day, Christmas) with the
  Christian Easter cycle and with Chinese lunisolar festivals. Only the first two groups can be calculated; Lunar New
  Year, Ching Ming, Buddha''s Birthday, Tuen Ng, the day after Mid-Autumn and Chung Yeung follow the Chinese calendar,
  which the JDK cannot compute, so they are enumerated.
  LIMITATION: the enumerated lunisolar dates cover 2018 to 2028 only. For 2000 to 2017 this file will under-report
  closures and the comparison report is expected to show a large "rules miss a real closure" block for those years.
  That gap is a deliberate, visible outcome of the evaluation rather than an oversight -- it is the concrete cost of
  the rule approach at this exchange.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1, observance: NEXT_MONDAY}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: LabourDay, type: FIXED, month: 5, day: 1, observance: NEXT_MONDAY}
  - {name: EstablishmentDay, type: FIXED, month: 7, day: 1, observance: NEXT_MONDAY}
  - {name: NationalDay, type: FIXED, month: 10, day: 1, observance: NEXT_MONDAY}
  - {name: Christmas, type: FIXED, month: 12, day: 25, observance: NEXT_MONDAY}
  - {name: BoxingDay, type: FIXED, month: 12, day: 26, observance: NEXT_MONDAY}

  - name: LunarNewYear
    type: EXPLICIT_DATES
    dates: [2018-02-16, 2018-02-19, 2019-02-05, 2019-02-06, 2019-02-07, 2020-01-27, 2020-01-28,
            2021-02-12, 2021-02-15, 2022-02-01, 2022-02-02, 2022-02-03, 2023-01-23, 2023-01-24, 2023-01-25,
            2024-02-12, 2024-02-13, 2025-01-29, 2025-01-30, 2025-01-31, 2026-02-17, 2026-02-18, 2026-02-19,
            2027-02-08, 2027-02-09, 2028-01-26, 2028-01-27, 2028-01-28]
  - name: ChingMing
    type: EXPLICIT_DATES
    dates: [2018-04-05, 2019-04-05, 2020-04-06, 2021-04-06, 2022-04-05, 2023-04-05, 2024-04-04,
            2025-04-04, 2026-04-06, 2027-04-05, 2028-04-04]
  - name: BuddhasBirthday
    type: EXPLICIT_DATES
    dates: [2018-05-22, 2019-05-13, 2021-05-19, 2022-05-09, 2023-05-26, 2024-05-15, 2025-05-05,
            2026-05-25, 2027-05-13, 2028-05-02]
  - name: TuenNg
    type: EXPLICIT_DATES
    dates: [2018-06-18, 2019-06-07, 2020-06-25, 2021-06-14, 2022-06-03, 2023-06-22, 2024-06-10,
            2026-06-19, 2027-06-09]
  - name: DayAfterMidAutumn
    type: EXPLICIT_DATES
    dates: [2018-09-25, 2019-09-14, 2020-10-02, 2021-09-22, 2022-09-12, 2023-09-30, 2024-09-18,
            2025-10-07, 2026-09-26, 2027-09-16, 2028-10-04]
  - name: ChungYeung
    type: EXPLICIT_DATES
    dates: [2018-10-17, 2019-10-07, 2020-10-26, 2021-10-14, 2022-10-04, 2023-10-23, 2024-10-11,
            2025-10-29, 2026-10-19, 2027-10-08, 2028-10-26]
additionalClosures:
  - 2000-02-04
  - 2000-02-07
  - 2000-04-04
  - 2000-05-11
  - 2000-06-06
  - 2000-09-13
  - 2000-10-06
  - 2001-01-24
  - 2001-01-25
  - 2001-01-26
  - 2001-04-05
  - 2001-04-30
  - 2001-06-25
  - 2001-07-06
  - 2001-07-25
  - 2001-10-02
  - 2001-10-25
  - 2002-02-12
  - 2002-02-13
  - 2002-02-14
  - 2002-04-05
  - 2002-05-20
  - 2002-10-14
  - 2003-01-31
  - 2003-02-03
  - 2003-05-08
  - 2003-06-04
  - 2003-09-12
  - 2004-01-22
  - 2004-01-23
  - 2004-04-05
  - 2004-05-26
  - 2004-06-22
  - 2004-09-29
  - 2004-10-22
  - 2005-02-09
  - 2005-02-10
  - 2005-02-11
  - 2005-04-05
  - 2005-05-16
  - 2005-09-19
  - 2005-10-11
  - 2005-12-27
  - 2006-01-30
  - 2006-01-31
  - 2006-04-05
  - 2006-05-05
  - 2006-05-31
  - 2006-10-30
  - 2007-02-19
  - 2007-02-20
  - 2007-04-05
  - 2007-05-24
  - 2007-06-19
  - 2007-09-26
  - 2007-10-19
  - 2008-02-07
  - 2008-02-08
  - 2008-04-04
  - 2008-05-12
  - 2008-06-09
  - 2008-08-06
  - 2008-08-22
  - 2008-09-15
  - 2008-10-07
  - 2009-01-26
  - 2009-01-27
  - 2009-01-28
  - 2009-05-28
  - 2009-10-26
  - 2010-02-15
  - 2010-02-16
  - 2010-04-06
  - 2010-05-21
  - 2010-06-16
  - 2010-09-23
  - 2011-02-03
  - 2011-02-04
  - 2011-04-05
  - 2011-05-10
  - 2011-06-06
  - 2011-09-13
  - 2011-09-29
  - 2011-10-05
  - 2011-12-27
  - 2012-01-23
  - 2012-01-24
  - 2012-01-25
  - 2012-04-04
  - 2012-10-02
  - 2012-10-23
  - 2013-02-11
  - 2013-02-12
  - 2013-02-13
  - 2013-04-04
  - 2013-05-17
  - 2013-06-12
  - 2013-08-14
  - 2013-09-20
  - 2013-10-14
  - 2014-01-31
  - 2014-02-03
  - 2014-05-06
  - 2014-06-02
  - 2014-09-09
  - 2014-10-02
  - 2015-02-19
  - 2015-02-20
  - 2015-04-07
  - 2015-05-25
  - 2015-09-03
  - 2015-09-28
  - 2015-10-21
  - 2016-02-08
  - 2016-02-09
  - 2016-02-10
  - 2016-04-04
  - 2016-06-09
  - 2016-08-02
  - 2016-09-16
  - 2016-10-10
  - 2016-10-21
  - 2016-12-27
  - 2017-01-30
  - 2017-01-31
  - 2017-04-04
  - 2017-05-03
  - 2017-05-30
  - 2017-08-23
  - 2017-10-05
  - 2020-04-30
  - 2020-10-13
  - 2021-10-13
  - 2022-12-27
  - 2023-07-17
  - 2024-09-06
  - 2026-04-07
  - 2028-05-29
  - 2029-02-13
  - 2029-02-14
  - 2029-02-15
  - 2029-04-04
  - 2029-05-21
  - 2029-10-16
  - 2030-02-04
  - 2030-02-05
  - 2030-02-06
  - 2030-04-05
  - 2030-05-09
  - 2030-06-05
  - 2030-09-13
openDates:
  - 2000-01-03
  - 2000-07-03
  - 2004-05-03
  - 2005-01-03
  - 2005-10-03
  - 2006-07-03
  - 2009-12-28
  - 2010-05-03
  - 2011-01-03
  - 2011-10-03
  - 2015-12-28
  - 2016-10-03
  - 2017-07-03
  - 2020-04-06
  - 2020-12-28
  - 2021-05-03
  - 2022-01-03
  - 2022-10-03
  - 2023-07-03
  - 2026-12-28
  - 2027-05-03
  - 2028-01-03
  - 2028-07-03', 1, NOW(), 1, NOW(), 0),
  ('XICE', 'Nasdaq Iceland', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset-3, type: EASTER_RELATIVE, offset: -3}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: EasterOffset39, type: EASTER_RELATIVE, offset: 39}
  - {name: EasterOffset50, type: EASTER_RELATIVE, offset: 50}
  - {name: Fixed1224NextMonday, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY}
  - {name: Fixed1225NearestWeekday, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Nth081Monday, type: NTH_WEEKDAY, month: 8, nth: 1, dayOfWeek: MONDAY}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed0617None, type: FIXED, month: 6, day: 17}
additionalClosures:
  - 2001-04-19
  - 2002-04-25
  - 2003-04-24
  - 2004-04-22
  - 2005-04-21
  - 2006-04-20
  - 2007-04-19
  - 2008-04-24
  - 2009-04-23
  - 2010-04-22
  - 2012-04-19
  - 2013-04-25
  - 2014-04-24
  - 2015-04-23
  - 2016-04-21
  - 2017-04-20
  - 2018-04-19
  - 2019-04-25
  - 2020-04-23
  - 2021-04-22
  - 2022-04-21
  - 2023-04-20
  - 2024-04-25
  - 2025-04-24
  - 2026-04-23
  - 2027-04-22
  - 2028-04-20
  - 2029-04-19
  - 2030-04-25', 1, NOW(), 1, NOW(), 0),
  ('XIDX', 'Indonesia Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed1225None, type: FIXED, month: 12, day: 25}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed0817None, type: FIXED, month: 8, day: 17}
additionalClosures:
  - 2000-06-01
  - 2001-05-24
  - 2002-02-12
  - 2002-02-22
  - 2002-03-15
  - 2002-05-09
  - 2002-10-04
  - 2002-12-05
  - 2002-12-06
  - 2002-12-09
  - 2002-12-10
  - 2002-12-24
  - 2002-12-26
  - 2002-12-30
  - 2003-02-12
  - 2003-03-03
  - 2003-04-02
  - 2003-05-15
  - 2003-05-16
  - 2003-05-30
  - 2003-08-18
  - 2003-09-22
  - 2003-11-24
  - 2003-11-25
  - 2003-11-26
  - 2003-11-27
  - 2003-11-28
  - 2003-12-24
  - 2003-12-26
  - 2004-01-22
  - 2004-02-02
  - 2004-02-23
  - 2004-03-22
  - 2004-04-05
  - 2004-05-03
  - 2004-05-20
  - 2004-06-03
  - 2004-07-05
  - 2004-09-13
  - 2004-09-20
  - 2004-11-15
  - 2004-11-16
  - 2004-11-17
  - 2004-11-18
  - 2004-11-19
  - 2004-12-24
  - 2005-01-21
  - 2005-02-09
  - 2005-02-10
  - 2005-03-11
  - 2005-04-22
  - 2005-05-05
  - 2005-05-24
  - 2005-09-02
  - 2005-11-02
  - 2005-11-03
  - 2005-11-04
  - 2005-11-07
  - 2005-11-08
  - 2005-12-26
  - 2005-12-30
  - 2006-01-10
  - 2006-01-31
  - 2006-03-30
  - 2006-03-31
  - 2006-04-10
  - 2006-05-25
  - 2006-05-26
  - 2006-08-18
  - 2006-08-21
  - 2006-10-23
  - 2006-10-24
  - 2006-10-25
  - 2006-10-26
  - 2006-10-27
  - 2006-12-29
  - 2007-03-19
  - 2007-05-17
  - 2007-05-18
  - 2007-06-01
  - 2007-10-12
  - 2007-10-15
  - 2007-10-16
  - 2007-12-20
  - 2007-12-21
  - 2007-12-24
  - 2008-01-10
  - 2008-01-11
  - 2008-02-07
  - 2008-02-08
  - 2008-03-07
  - 2008-03-20
  - 2008-05-01
  - 2008-05-20
  - 2008-07-30
  - 2008-08-18
  - 2008-09-30
  - 2008-10-01
  - 2008-10-02
  - 2008-10-03
  - 2008-10-09
  - 2008-10-10
  - 2008-12-08
  - 2008-12-29
  - 2009-01-02
  - 2009-01-26
  - 2009-03-09
  - 2009-03-26
  - 2009-04-09
  - 2009-05-21
  - 2009-07-08
  - 2009-07-20
  - 2009-09-18
  - 2009-09-21
  - 2009-09-22
  - 2009-09-23
  - 2009-11-27
  - 2009-12-18
  - 2009-12-24
  - 2010-02-26
  - 2010-03-16
  - 2010-05-13
  - 2010-05-28
  - 2010-09-08
  - 2010-09-09
  - 2010-09-10
  - 2010-09-13
  - 2010-09-14
  - 2010-11-17
  - 2010-12-07
  - 2010-12-24
  - 2011-02-03
  - 2011-02-15
  - 2011-05-17
  - 2011-06-02
  - 2011-06-29
  - 2011-08-29
  - 2011-08-30
  - 2011-08-31
  - 2011-09-01
  - 2011-09-02
  - 2011-12-26
  - 2012-01-23
  - 2012-03-23
  - 2012-05-17
  - 2012-05-18
  - 2012-08-20
  - 2012-08-21
  - 2012-08-22
  - 2012-10-26
  - 2012-11-15
  - 2012-11-16
  - 2012-12-24
  - 2013-01-24
  - 2013-03-12
  - 2013-05-09
  - 2013-06-06
  - 2013-08-05
  - 2013-08-06
  - 2013-08-07
  - 2013-08-08
  - 2013-08-09
  - 2013-10-14
  - 2013-10-15
  - 2013-11-05
  - 2013-12-26
  - 2014-01-14
  - 2014-01-31
  - 2014-03-31
  - 2014-04-09
  - 2014-05-01
  - 2014-05-15
  - 2014-05-27
  - 2014-05-29
  - 2014-07-09
  - 2014-07-28
  - 2014-07-29
  - 2014-07-30
  - 2014-07-31
  - 2014-08-01
  - 2014-12-26
  - 2015-02-19
  - 2015-05-01
  - 2015-05-14
  - 2015-06-02
  - 2015-07-16
  - 2015-07-17
  - 2015-07-20
  - 2015-07-21
  - 2015-09-24
  - 2015-10-14
  - 2015-12-09
  - 2015-12-24
  - 2016-02-08
  - 2016-03-09
  - 2016-05-05
  - 2016-05-06
  - 2016-07-04
  - 2016-07-05
  - 2016-07-06
  - 2016-07-07
  - 2016-07-08
  - 2016-09-12
  - 2016-12-12
  - 2016-12-26
  - 2017-01-02
  - 2017-02-15
  - 2017-03-28
  - 2017-04-19
  - 2017-04-24
  - 2017-05-01
  - 2017-05-11
  - 2017-05-25
  - 2017-06-01
  - 2017-06-23
  - 2017-06-26
  - 2017-06-27
  - 2017-06-28
  - 2017-06-29
  - 2017-06-30
  - 2017-09-01
  - 2017-09-21
  - 2017-12-01
  - 2017-12-26
  - 2018-02-16
  - 2018-05-01
  - 2018-05-10
  - 2018-05-29
  - 2018-06-01
  - 2018-06-11
  - 2018-06-12
  - 2018-06-13
  - 2018-06-14
  - 2018-06-15
  - 2018-06-18
  - 2018-06-19
  - 2018-08-22
  - 2018-09-11
  - 2018-11-20
  - 2018-12-24
  - 2019-02-05
  - 2019-03-07
  - 2019-04-03
  - 2019-04-17
  - 2019-05-01
  - 2019-05-30
  - 2019-06-03
  - 2019-06-04
  - 2019-06-05
  - 2019-06-06
  - 2019-06-07
  - 2019-12-24
  - 2020-03-25
  - 2020-05-01
  - 2020-05-07
  - 2020-05-21
  - 2020-05-22
  - 2020-05-25
  - 2020-06-01
  - 2020-07-31
  - 2020-08-20
  - 2020-08-21
  - 2020-10-28
  - 2020-10-29
  - 2020-10-30
  - 2020-12-09
  - 2020-12-24
  - 2021-02-12
  - 2021-03-11
  - 2021-05-12
  - 2021-05-13
  - 2021-05-14
  - 2021-05-26
  - 2021-06-01
  - 2021-07-20
  - 2021-08-11
  - 2021-10-20
  - 2022-02-01
  - 2022-02-28
  - 2022-03-03
  - 2022-05-02
  - 2022-05-03
  - 2022-05-16
  - 2022-05-26
  - 2022-06-01
  - 2023-01-23
  - 2023-03-22
  - 2023-03-23
  - 2023-04-19
  - 2023-04-20
  - 2023-04-21
  - 2023-04-24
  - 2023-04-25
  - 2023-05-01
  - 2023-05-18
  - 2023-06-01
  - 2023-06-02
  - 2023-06-28
  - 2023-06-29
  - 2023-06-30
  - 2023-07-19
  - 2023-09-28
  - 2023-12-26
  - 2024-02-08
  - 2024-02-09
  - 2024-02-14
  - 2024-03-11
  - 2024-03-12
  - 2024-04-08
  - 2024-04-09
  - 2024-04-10
  - 2024-04-11
  - 2024-04-12
  - 2024-04-15
  - 2024-05-01
  - 2024-05-09
  - 2024-05-10
  - 2024-05-23
  - 2024-05-24
  - 2024-06-17
  - 2024-06-18
  - 2024-09-16
  - 2024-11-27
  - 2024-12-26
  - 2025-01-27
  - 2025-01-28
  - 2025-01-29
  - 2025-03-28
  - 2025-03-31
  - 2025-04-01
  - 2025-04-02
  - 2025-04-03
  - 2025-04-04
  - 2025-04-07
  - 2025-05-01
  - 2025-05-12
  - 2025-05-13
  - 2025-05-29
  - 2025-05-30
  - 2025-06-06
  - 2025-06-09
  - 2025-06-27
  - 2025-09-05
  - 2025-12-26
  - 2026-02-17
  - 2026-05-01
  - 2026-05-14
  - 2026-06-01
  - 2027-05-06
  - 2027-06-01
  - 2028-01-26
  - 2028-05-01
  - 2028-05-25
  - 2028-06-01
  - 2029-02-13
  - 2029-05-01
  - 2029-05-10
  - 2029-06-01
  - 2030-05-01
  - 2030-05-30', 1, NOW(), 1, NOW(), 0),
  ('XIST', 'Borsa Istanbul', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: Fixed0830None, type: FIXED, month: 8, day: 30}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed0423None, type: FIXED, month: 4, day: 23}
  - {name: Fixed1029None, type: FIXED, month: 10, day: 29}
  - {name: Fixed0519None, type: FIXED, month: 5, day: 19}
additionalClosures:
  - 2000-01-10
  - 2000-03-16
  - 2000-03-17
  - 2000-12-27
  - 2000-12-28
  - 2000-12-29
  - 2001-03-05
  - 2001-03-06
  - 2001-03-07
  - 2001-03-08
  - 2001-12-17
  - 2001-12-18
  - 2002-01-04
  - 2002-02-22
  - 2002-02-25
  - 2002-12-05
  - 2002-12-06
  - 2003-02-10
  - 2003-02-11
  - 2003-02-12
  - 2003-02-13
  - 2003-02-14
  - 2003-11-21
  - 2003-11-24
  - 2003-11-25
  - 2003-11-26
  - 2003-11-27
  - 2003-11-28
  - 2004-01-23
  - 2004-02-02
  - 2004-02-03
  - 2004-02-04
  - 2004-11-15
  - 2004-11-16
  - 2004-12-30
  - 2004-12-31
  - 2005-01-20
  - 2005-01-21
  - 2005-11-03
  - 2005-11-04
  - 2006-01-09
  - 2006-01-10
  - 2006-01-11
  - 2006-01-12
  - 2006-01-13
  - 2006-10-23
  - 2006-10-24
  - 2006-10-25
  - 2007-01-02
  - 2007-01-03
  - 2007-10-12
  - 2007-12-20
  - 2007-12-21
  - 2008-09-30
  - 2008-10-01
  - 2008-10-02
  - 2008-12-08
  - 2008-12-09
  - 2008-12-10
  - 2008-12-11
  - 2009-05-01
  - 2009-09-21
  - 2009-09-22
  - 2009-11-27
  - 2009-11-30
  - 2010-09-09
  - 2010-09-10
  - 2010-11-16
  - 2010-11-17
  - 2010-11-18
  - 2010-11-19
  - 2011-08-31
  - 2011-09-01
  - 2011-11-07
  - 2011-11-08
  - 2011-11-09
  - 2012-05-01
  - 2012-08-20
  - 2012-08-21
  - 2012-10-25
  - 2012-10-26
  - 2013-05-01
  - 2013-08-08
  - 2013-08-09
  - 2013-10-15
  - 2013-10-16
  - 2013-10-17
  - 2013-10-18
  - 2014-05-01
  - 2014-07-28
  - 2014-07-29
  - 2014-07-30
  - 2014-10-06
  - 2014-10-07
  - 2015-05-01
  - 2015-07-17
  - 2015-09-24
  - 2015-09-25
  - 2016-07-05
  - 2016-07-06
  - 2016-07-07
  - 2016-09-12
  - 2016-09-13
  - 2016-09-14
  - 2016-09-15
  - 2017-05-01
  - 2017-06-26
  - 2017-06-27
  - 2017-09-01
  - 2017-09-04
  - 2018-05-01
  - 2018-06-15
  - 2018-08-21
  - 2018-08-22
  - 2018-08-23
  - 2018-08-24
  - 2019-05-01
  - 2019-06-04
  - 2019-06-05
  - 2019-06-06
  - 2019-07-15
  - 2019-08-12
  - 2019-08-13
  - 2019-08-14
  - 2020-05-01
  - 2020-05-25
  - 2020-05-26
  - 2020-07-15
  - 2020-07-31
  - 2020-08-03
  - 2021-05-13
  - 2021-05-14
  - 2021-07-15
  - 2021-07-20
  - 2021-07-21
  - 2021-07-22
  - 2021-07-23
  - 2022-05-02
  - 2022-05-03
  - 2022-05-04
  - 2022-07-11
  - 2022-07-12
  - 2022-07-15
  - 2023-02-08
  - 2023-02-09
  - 2023-02-10
  - 2023-02-13
  - 2023-02-14
  - 2023-04-21
  - 2023-05-01
  - 2023-06-28
  - 2023-06-29
  - 2023-06-30
  - 2024-04-10
  - 2024-04-11
  - 2024-04-12
  - 2024-05-01
  - 2024-06-17
  - 2024-06-18
  - 2024-06-19
  - 2024-07-15
  - 2025-03-31
  - 2025-04-01
  - 2025-05-01
  - 2025-06-06
  - 2025-06-09
  - 2025-07-15
  - 2026-03-20
  - 2026-05-01
  - 2026-05-27
  - 2026-05-28
  - 2026-05-29
  - 2026-07-15
  - 2027-03-09
  - 2027-03-10
  - 2027-03-11
  - 2027-05-17
  - 2027-05-18
  - 2027-07-15
  - 2028-02-28
  - 2028-05-01
  - 2028-05-05
  - 2028-05-08
  - 2029-02-14
  - 2029-02-15
  - 2029-02-16
  - 2029-04-24
  - 2029-04-25
  - 2029-04-26
  - 2029-04-27
  - 2029-05-01
  - 2030-02-04
  - 2030-02-05
  - 2030-02-06
  - 2030-04-15
  - 2030-04-16
  - 2030-05-01
  - 2030-07-15', 1, NOW(), 1, NOW(), 0),
  ('XJSE', 'Johannesburg Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: Fixed1225NextMonday, type: FIXED, month: 12, day: 25, observance: NEXT_MONDAY}
  - {name: Fixed1226NearestWeekday, type: FIXED, month: 12, day: 26, observance: NEAREST_WEEKDAY}
  - {name: Fixed0321SundayToMonday, type: FIXED, month: 3, day: 21, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501SundayToMonday, type: FIXED, month: 5, day: 1, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0809SundayToMonday, type: FIXED, month: 8, day: 9, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0924SundayToMonday, type: FIXED, month: 9, day: 24, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0101SundayToMonday, type: FIXED, month: 1, day: 1, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0427SundayToMonday, type: FIXED, month: 4, day: 27, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0616SundayToMonday, type: FIXED, month: 6, day: 16, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed1216SundayToMonday, type: FIXED, month: 12, day: 16, observance: SUNDAY_TO_MONDAY}
additionalClosures:
  - 2004-04-14
  - 2006-03-01
  - 2008-05-02
  - 2009-04-22
  - 2011-05-18
  - 2011-12-27
  - 2014-05-07
  - 2016-08-03
  - 2016-12-27
  - 2019-05-08', 1, NOW(), 1, NOW(), 0),
  ('XKAR', 'Pakistan Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed0814None, type: FIXED, month: 8, day: 14}
  - {name: Fixed1225None, type: FIXED, month: 12, day: 25}
  - {name: Fixed0205None, type: FIXED, month: 2, day: 5}
  - {name: Fixed0323None, type: FIXED, month: 3, day: 23}
additionalClosures:
  - 2000-11-09
  - 2001-11-09
  - 2002-01-01
  - 2002-02-22
  - 2002-02-25
  - 2002-03-25
  - 2002-05-23
  - 2002-10-10
  - 2002-12-03
  - 2002-12-05
  - 2002-12-06
  - 2003-02-11
  - 2003-02-12
  - 2003-02-13
  - 2003-02-14
  - 2003-03-13
  - 2003-03-14
  - 2003-04-10
  - 2003-05-15
  - 2003-11-26
  - 2003-11-27
  - 2003-11-28
  - 2004-01-01
  - 2004-02-02
  - 2004-02-03
  - 2004-02-04
  - 2004-03-01
  - 2004-03-02
  - 2004-05-03
  - 2004-11-09
  - 2004-11-11
  - 2004-11-15
  - 2004-11-16
  - 2004-11-17
  - 2005-01-20
  - 2005-01-21
  - 2005-04-22
  - 2005-08-18
  - 2005-10-28
  - 2005-11-01
  - 2005-11-03
  - 2005-11-04
  - 2005-11-09
  - 2006-01-10
  - 2006-01-11
  - 2006-01-12
  - 2006-01-13
  - 2006-02-08
  - 2006-02-09
  - 2006-04-11
  - 2006-04-12
  - 2006-10-20
  - 2006-10-23
  - 2006-10-24
  - 2006-10-25
  - 2006-10-26
  - 2006-10-27
  - 2006-11-09
  - 2007-01-01
  - 2007-01-02
  - 2007-01-29
  - 2007-01-30
  - 2007-10-10
  - 2007-10-12
  - 2007-10-15
  - 2007-10-16
  - 2007-11-09
  - 2007-12-20
  - 2007-12-21
  - 2007-12-28
  - 2008-02-18
  - 2008-03-21
  - 2008-09-26
  - 2008-10-01
  - 2008-10-02
  - 2008-10-03
  - 2008-12-08
  - 2008-12-09
  - 2008-12-10
  - 2008-12-11
  - 2009-01-07
  - 2009-01-08
  - 2009-03-10
  - 2009-09-21
  - 2009-09-22
  - 2009-09-23
  - 2009-11-09
  - 2009-11-27
  - 2009-11-30
  - 2009-12-28
  - 2010-01-01
  - 2010-09-10
  - 2010-09-13
  - 2010-11-09
  - 2010-11-17
  - 2010-11-18
  - 2010-11-19
  - 2010-12-16
  - 2010-12-17
  - 2011-02-16
  - 2011-08-26
  - 2011-08-31
  - 2011-09-01
  - 2011-09-02
  - 2011-10-24
  - 2011-11-07
  - 2011-11-08
  - 2011-11-09
  - 2011-11-11
  - 2011-12-05
  - 2011-12-06
  - 2012-08-17
  - 2012-08-20
  - 2012-08-21
  - 2012-08-22
  - 2012-09-21
  - 2012-10-26
  - 2012-10-29
  - 2012-11-09
  - 2013-01-25
  - 2013-08-02
  - 2013-08-08
  - 2013-08-09
  - 2013-10-15
  - 2013-10-16
  - 2013-10-17
  - 2013-10-18
  - 2013-11-14
  - 2013-11-15
  - 2014-01-14
  - 2014-07-25
  - 2014-07-29
  - 2014-07-30
  - 2014-07-31
  - 2014-08-01
  - 2014-10-06
  - 2014-10-07
  - 2014-10-08
  - 2014-11-03
  - 2014-11-04
  - 2015-07-17
  - 2015-07-20
  - 2015-07-21
  - 2015-09-24
  - 2015-09-25
  - 2015-10-23
  - 2015-12-24
  - 2016-07-01
  - 2016-07-05
  - 2016-07-06
  - 2016-07-07
  - 2016-07-08
  - 2016-09-12
  - 2016-09-13
  - 2016-09-14
  - 2016-10-11
  - 2016-10-12
  - 2016-12-12
  - 2017-06-23
  - 2017-06-26
  - 2017-06-27
  - 2017-06-28
  - 2017-09-01
  - 2017-09-04
  - 2017-12-01
  - 2018-06-08
  - 2018-06-15
  - 2018-06-18
  - 2018-07-25
  - 2018-08-21
  - 2018-08-22
  - 2018-08-23
  - 2018-09-20
  - 2018-09-21
  - 2018-11-21
  - 2019-05-31
  - 2019-06-04
  - 2019-06-05
  - 2019-06-06
  - 2019-06-07
  - 2019-08-12
  - 2019-08-13
  - 2019-08-15
  - 2019-09-09
  - 2019-09-10
  - 2020-05-22
  - 2020-05-25
  - 2020-05-26
  - 2020-05-27
  - 2020-07-31
  - 2020-10-30
  - 2021-05-07
  - 2021-05-10
  - 2021-05-11
  - 2021-05-12
  - 2021-05-13
  - 2021-05-14
  - 2021-07-20
  - 2021-07-21
  - 2021-07-22
  - 2021-08-18
  - 2021-08-19
  - 2021-10-19
  - 2022-04-29
  - 2022-05-02
  - 2022-05-03
  - 2022-05-04
  - 2022-05-05
  - 2022-07-08
  - 2022-07-11
  - 2022-07-12
  - 2022-08-08
  - 2022-08-09
  - 2022-11-09
  - 2023-04-14
  - 2023-04-21
  - 2023-04-24
  - 2023-04-25
  - 2023-06-28
  - 2023-06-29
  - 2023-06-30
  - 2023-07-28
  - 2023-09-29
  - 2023-11-09
  - 2024-02-08
  - 2024-04-05
  - 2024-04-10
  - 2024-04-11
  - 2024-04-12
  - 2024-05-28
  - 2024-06-17
  - 2024-06-18
  - 2024-06-19
  - 2024-07-16
  - 2024-07-17
  - 2024-09-17
  - 2025-03-28
  - 2025-03-31
  - 2025-04-01
  - 2025-04-02
  - 2025-05-28
  - 2025-06-09
  - 2025-09-05
  - 2026-05-28
  - 2026-11-09
  - 2027-05-28
  - 2027-11-09
  - 2028-11-09
  - 2029-05-28
  - 2029-11-09
  - 2030-05-28', 1, NOW(), 1, NOW(), 0),
  ('XKLS', 'Bursa Malaysia', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: Fixed0201SundayToMonday, type: FIXED, month: 2, day: 1, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501SundayToMonday, type: FIXED, month: 5, day: 1, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed1225SundayToMonday, type: FIXED, month: 12, day: 25, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0101SundayToMonday, type: FIXED, month: 1, day: 1, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0831SundayToMonday, type: FIXED, month: 8, day: 31, observance: SUNDAY_TO_MONDAY}
additionalClosures:
  - 2000-01-10
  - 2000-02-07
  - 2000-03-16
  - 2000-04-06
  - 2000-06-14
  - 2000-12-27
  - 2000-12-28
  - 2001-01-24
  - 2001-01-25
  - 2001-03-05
  - 2001-03-26
  - 2001-06-04
  - 2001-12-17
  - 2001-12-18
  - 2002-02-11
  - 2002-02-12
  - 2002-02-13
  - 2002-03-15
  - 2002-04-25
  - 2002-05-27
  - 2002-11-04
  - 2002-12-05
  - 2002-12-06
  - 2003-01-31
  - 2003-02-03
  - 2003-02-04
  - 2003-02-12
  - 2003-03-04
  - 2003-05-14
  - 2003-05-15
  - 2003-10-24
  - 2003-11-24
  - 2003-11-25
  - 2003-11-26
  - 2004-01-21
  - 2004-01-22
  - 2004-01-23
  - 2004-02-03
  - 2004-02-23
  - 2004-05-03
  - 2004-05-04
  - 2004-11-11
  - 2004-11-12
  - 2004-11-15
  - 2004-11-16
  - 2005-01-21
  - 2005-02-09
  - 2005-02-10
  - 2005-02-11
  - 2005-04-21
  - 2005-05-23
  - 2005-11-01
  - 2005-11-03
  - 2005-11-04
  - 2006-01-10
  - 2006-01-30
  - 2006-01-31
  - 2006-02-02
  - 2006-04-11
  - 2006-05-12
  - 2006-10-23
  - 2006-10-24
  - 2006-10-25
  - 2007-01-02
  - 2007-02-19
  - 2007-02-20
  - 2007-04-26
  - 2007-05-02
  - 2007-10-15
  - 2007-11-08
  - 2007-12-20
  - 2008-01-10
  - 2008-01-23
  - 2008-02-07
  - 2008-02-08
  - 2008-03-20
  - 2008-05-19
  - 2008-07-03
  - 2008-10-01
  - 2008-10-02
  - 2008-10-27
  - 2008-12-08
  - 2008-12-29
  - 2009-01-26
  - 2009-01-27
  - 2009-02-09
  - 2009-03-09
  - 2009-09-21
  - 2009-09-22
  - 2009-11-27
  - 2009-12-18
  - 2010-02-15
  - 2010-02-16
  - 2010-02-26
  - 2010-05-28
  - 2010-09-10
  - 2010-09-16
  - 2010-11-05
  - 2010-11-17
  - 2010-12-07
  - 2010-12-31
  - 2011-01-20
  - 2011-02-03
  - 2011-02-04
  - 2011-02-15
  - 2011-05-17
  - 2011-08-30
  - 2011-09-01
  - 2011-09-16
  - 2011-10-26
  - 2011-11-07
  - 2011-11-28
  - 2012-01-23
  - 2012-01-24
  - 2012-02-06
  - 2012-02-07
  - 2012-04-11
  - 2012-08-20
  - 2012-08-21
  - 2012-09-17
  - 2012-10-26
  - 2012-11-13
  - 2012-11-15
  - 2013-01-24
  - 2013-01-28
  - 2013-02-11
  - 2013-02-12
  - 2013-05-24
  - 2013-08-08
  - 2013-08-09
  - 2013-09-16
  - 2013-10-15
  - 2013-11-05
  - 2014-01-14
  - 2014-01-17
  - 2014-01-31
  - 2014-02-03
  - 2014-05-13
  - 2014-07-15
  - 2014-07-28
  - 2014-07-29
  - 2014-09-16
  - 2014-10-06
  - 2014-10-22
  - 2015-02-03
  - 2015-02-19
  - 2015-02-20
  - 2015-05-04
  - 2015-07-17
  - 2015-09-16
  - 2015-09-24
  - 2015-10-14
  - 2015-11-10
  - 2015-12-24
  - 2016-01-25
  - 2016-02-08
  - 2016-02-09
  - 2016-06-22
  - 2016-07-06
  - 2016-07-07
  - 2016-09-12
  - 2016-09-16
  - 2016-10-03
  - 2016-12-12
  - 2017-01-30
  - 2017-02-09
  - 2017-04-24
  - 2017-05-10
  - 2017-06-12
  - 2017-06-26
  - 2017-06-27
  - 2017-09-01
  - 2017-09-04
  - 2017-09-22
  - 2017-10-18
  - 2017-12-01
  - 2018-01-31
  - 2018-02-16
  - 2018-05-09
  - 2018-05-10
  - 2018-05-11
  - 2018-05-29
  - 2018-06-15
  - 2018-08-22
  - 2018-09-10
  - 2018-09-11
  - 2018-09-17
  - 2018-11-06
  - 2018-11-20
  - 2019-01-21
  - 2019-02-05
  - 2019-02-06
  - 2019-05-20
  - 2019-05-22
  - 2019-06-05
  - 2019-06-06
  - 2019-07-30
  - 2019-08-12
  - 2019-09-02
  - 2019-09-09
  - 2019-09-16
  - 2019-10-28
  - 2020-01-27
  - 2020-05-07
  - 2020-05-11
  - 2020-05-25
  - 2020-05-26
  - 2020-06-08
  - 2020-07-31
  - 2020-08-20
  - 2020-09-16
  - 2020-10-29
  - 2021-01-28
  - 2021-02-12
  - 2021-04-29
  - 2021-05-13
  - 2021-05-14
  - 2021-05-26
  - 2021-06-07
  - 2021-07-20
  - 2021-08-10
  - 2021-09-16
  - 2021-10-19
  - 2021-11-04
  - 2022-01-18
  - 2022-02-02
  - 2022-04-19
  - 2022-05-03
  - 2022-05-04
  - 2022-06-06
  - 2022-07-11
  - 2022-09-16
  - 2022-10-10
  - 2022-10-24
  - 2023-01-23
  - 2023-01-24
  - 2023-04-21
  - 2023-04-24
  - 2023-05-04
  - 2023-06-05
  - 2023-06-29
  - 2023-07-19
  - 2023-09-28
  - 2023-11-13
  - 2024-01-25
  - 2024-02-12
  - 2024-03-27
  - 2024-04-10
  - 2024-04-11
  - 2024-05-22
  - 2024-06-03
  - 2024-06-17
  - 2024-07-08
  - 2024-09-16
  - 2024-10-31
  - 2025-01-29
  - 2025-01-30
  - 2025-02-11
  - 2025-03-18
  - 2025-03-31
  - 2025-04-01
  - 2025-05-12
  - 2025-06-02
  - 2025-06-27
  - 2025-09-05
  - 2025-09-16
  - 2025-10-20
  - 2026-02-17
  - 2026-02-18
  - 2026-03-23
  - 2026-05-27
  - 2026-06-01
  - 2026-06-17
  - 2026-08-25
  - 2026-09-16
  - 2026-11-09
  - 2027-01-22
  - 2027-02-08
  - 2027-02-24
  - 2027-03-09
  - 2027-03-10
  - 2027-05-17
  - 2027-05-20
  - 2027-06-07
  - 2027-09-16
  - 2027-10-28
  - 2028-01-26
  - 2028-01-27
  - 2028-02-09
  - 2028-02-14
  - 2028-02-28
  - 2028-05-05
  - 2028-05-09
  - 2028-05-25
  - 2028-08-03
  - 2028-10-17
  - 2029-01-30
  - 2029-02-13
  - 2029-02-14
  - 2029-02-15
  - 2029-04-24
  - 2029-05-14
  - 2029-05-28
  - 2029-07-24
  - 2029-09-17
  - 2029-11-05
  - 2030-01-21
  - 2030-02-04
  - 2030-02-05
  - 2030-05-03
  - 2030-09-16', 1, NOW(), 1, NOW(), 0),
  ('XKRX', 'Korea Exchange', 'note: >
  Korea mixes Gregorian national days with three lunisolar festivals -- Seollal, Buddha''s Birthday and Chuseok -- each
  of which closes the exchange for up to three days. The Korea Exchange additionally closes on the last business day of
  the year and on days of a nationwide election, neither of which is derivable.
  LIMITATION: the enumerated lunisolar dates cover 2018 to 2028; earlier years are not filled in. GT has no reference
  index assigned to this exchange either, so its stored calendar is empty and there is nothing to compare against.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: IndependenceMovementDay, type: FIXED, month: 3, day: 1}
  - {name: ChildrensDay, type: FIXED, month: 5, day: 5}
  - {name: MemorialDay, type: FIXED, month: 6, day: 6}
  - {name: LiberationDay, type: FIXED, month: 8, day: 15}
  - {name: NationalFoundationDay, type: FIXED, month: 10, day: 3}
  - {name: HangulDay, type: FIXED, month: 10, day: 9, validFrom: 2013}
  - {name: Christmas, type: FIXED, month: 12, day: 25}

  - name: Seollal
    type: EXPLICIT_DATES
    dates: [2018-02-15, 2018-02-16, 2019-02-04, 2019-02-05, 2019-02-06, 2020-01-24, 2020-01-27,
            2021-02-11, 2021-02-12, 2022-01-31, 2022-02-01, 2022-02-02, 2023-01-23, 2023-01-24,
            2024-02-09, 2024-02-12, 2025-01-28, 2025-01-29, 2025-01-30, 2026-02-16, 2026-02-17, 2026-02-18,
            2027-02-08, 2027-02-09, 2028-01-26, 2028-01-27, 2028-01-28]
  - name: BuddhasBirthday
    type: EXPLICIT_DATES
    dates: [2018-05-22, 2019-05-12, 2020-04-30, 2021-05-19, 2022-05-08, 2023-05-29, 2024-05-15,
            2025-05-05, 2026-05-24, 2027-05-13, 2028-05-02]
  - name: Chuseok
    type: EXPLICIT_DATES
    dates: [2018-09-24, 2018-09-25, 2018-09-26, 2019-09-12, 2019-09-13, 2020-09-30, 2020-10-01, 2020-10-02,
            2021-09-20, 2021-09-21, 2022-09-09, 2022-09-12, 2023-09-28, 2023-09-29, 2024-09-16, 2024-09-17,
            2024-09-18, 2025-10-06, 2025-10-07, 2025-10-08, 2026-09-24, 2026-09-25, 2027-09-14, 2027-09-15,
            2027-09-16, 2028-10-02, 2028-10-03, 2028-10-04]
  - name: YearEndClosure
    type: EXPLICIT_DATES
    dates: [2018-12-31, 2019-12-31, 2020-12-31, 2021-12-31, 2022-12-30, 2023-12-29, 2024-12-31,
            2025-12-31, 2026-12-31, 2027-12-31, 2028-12-29]
additionalClosures:
  - 2000-01-03
  - 2000-02-04
  - 2000-04-05
  - 2000-04-13
  - 2000-05-01
  - 2000-05-11
  - 2000-07-17
  - 2000-09-11
  - 2000-09-12
  - 2000-09-13
  - 2000-12-27
  - 2000-12-28
  - 2000-12-29
  - 2001-01-23
  - 2001-01-24
  - 2001-01-25
  - 2001-04-05
  - 2001-05-01
  - 2001-07-17
  - 2001-10-01
  - 2001-10-02
  - 2001-12-31
  - 2002-02-11
  - 2002-02-12
  - 2002-02-13
  - 2002-04-05
  - 2002-05-01
  - 2002-06-13
  - 2002-07-01
  - 2002-07-17
  - 2002-09-20
  - 2002-12-19
  - 2002-12-31
  - 2003-01-31
  - 2003-05-01
  - 2003-05-08
  - 2003-07-17
  - 2003-09-10
  - 2003-09-11
  - 2003-09-12
  - 2003-12-31
  - 2004-01-21
  - 2004-01-22
  - 2004-01-23
  - 2004-04-05
  - 2004-04-15
  - 2004-05-26
  - 2004-09-27
  - 2004-09-28
  - 2004-09-29
  - 2004-12-31
  - 2005-02-08
  - 2005-02-09
  - 2005-02-10
  - 2005-04-05
  - 2005-09-19
  - 2005-12-30
  - 2006-01-30
  - 2006-05-01
  - 2006-05-31
  - 2006-07-17
  - 2006-10-05
  - 2006-10-06
  - 2006-12-29
  - 2007-02-19
  - 2007-05-01
  - 2007-05-24
  - 2007-07-17
  - 2007-09-24
  - 2007-09-25
  - 2007-09-26
  - 2007-12-19
  - 2007-12-31
  - 2008-02-06
  - 2008-02-07
  - 2008-02-08
  - 2008-04-09
  - 2008-05-01
  - 2008-05-12
  - 2008-09-15
  - 2008-12-31
  - 2009-01-26
  - 2009-01-27
  - 2009-05-01
  - 2009-10-02
  - 2009-12-31
  - 2010-02-15
  - 2010-05-21
  - 2010-06-02
  - 2010-09-21
  - 2010-09-22
  - 2010-09-23
  - 2010-12-31
  - 2011-02-02
  - 2011-02-03
  - 2011-02-04
  - 2011-05-10
  - 2011-09-12
  - 2011-09-13
  - 2011-12-30
  - 2012-01-23
  - 2012-01-24
  - 2012-04-11
  - 2012-05-01
  - 2012-05-28
  - 2012-10-01
  - 2012-12-19
  - 2012-12-31
  - 2013-02-11
  - 2013-05-01
  - 2013-05-17
  - 2013-09-18
  - 2013-09-19
  - 2013-09-20
  - 2013-12-31
  - 2014-01-30
  - 2014-01-31
  - 2014-05-01
  - 2014-05-06
  - 2014-06-04
  - 2014-09-08
  - 2014-09-09
  - 2014-09-10
  - 2014-12-31
  - 2015-02-18
  - 2015-02-19
  - 2015-02-20
  - 2015-05-01
  - 2015-05-25
  - 2015-08-14
  - 2015-09-28
  - 2015-09-29
  - 2015-12-31
  - 2016-02-08
  - 2016-02-09
  - 2016-02-10
  - 2016-04-13
  - 2016-05-06
  - 2016-09-14
  - 2016-09-15
  - 2016-09-16
  - 2016-12-30
  - 2017-01-27
  - 2017-01-30
  - 2017-05-01
  - 2017-05-03
  - 2017-05-09
  - 2017-10-02
  - 2017-10-04
  - 2017-10-05
  - 2017-10-06
  - 2017-12-29
  - 2018-05-01
  - 2018-05-07
  - 2018-06-13
  - 2019-05-01
  - 2019-05-06
  - 2020-04-15
  - 2020-05-01
  - 2020-08-17
  - 2021-08-16
  - 2021-09-22
  - 2021-10-04
  - 2021-10-11
  - 2022-03-09
  - 2022-06-01
  - 2022-10-10
  - 2023-05-01
  - 2023-10-02
  - 2024-04-10
  - 2024-05-01
  - 2024-05-06
  - 2024-10-01
  - 2025-01-27
  - 2025-03-03
  - 2025-05-01
  - 2025-05-06
  - 2025-06-03
  - 2026-03-02
  - 2026-05-01
  - 2026-05-25
  - 2026-06-03
  - 2026-08-17
  - 2026-10-05
  - 2027-05-03
  - 2027-08-16
  - 2027-10-04
  - 2027-10-11
  - 2027-12-27
  - 2028-05-01
  - 2028-10-05
  - 2029-02-12
  - 2029-02-13
  - 2029-02-14
  - 2029-05-01
  - 2029-05-07
  - 2029-05-21
  - 2029-09-21
  - 2029-09-24
  - 2029-12-31
  - 2030-02-04
  - 2030-02-05
  - 2030-05-01
  - 2030-05-06
  - 2030-05-09
  - 2030-09-11
  - 2030-09-12
  - 2030-09-13
  - 2030-12-31', 1, NOW(), 1, NOW(), 0),
  ('XLIM', 'Bolsa de Valores de Lima', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset-3, type: EASTER_RELATIVE, offset: -3}
  - {name: Fixed0728SundayToMonday, type: FIXED, month: 7, day: 28, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed0830None, type: FIXED, month: 8, day: 30}
  - {name: Fixed1101None, type: FIXED, month: 11, day: 1}
  - {name: Fixed1225None, type: FIXED, month: 12, day: 25}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed0629None, type: FIXED, month: 6, day: 29}
  - {name: Fixed1008None, type: FIXED, month: 10, day: 8}
  - {name: Fixed0729None, type: FIXED, month: 7, day: 29}
  - {name: Fixed1208None, type: FIXED, month: 12, day: 8}
additionalClosures:
  - 2001-12-31
  - 2002-12-31
  - 2003-12-31
  - 2004-12-31
  - 2007-12-31
  - 2009-01-02
  - 2009-07-27
  - 2012-10-01
  - 2012-10-02
  - 2015-01-02
  - 2015-07-27
  - 2015-10-09
  - 2016-11-17
  - 2016-11-18
  - 2018-04-13
  - 2022-12-09
  - 2024-06-07
  - 2024-07-23
  - 2024-08-06
  - 2024-12-09
  - 2025-07-23
  - 2025-08-06
  - 2025-12-09
  - 2026-07-23
  - 2026-08-06
  - 2026-12-09
  - 2027-06-07
  - 2027-07-23
  - 2027-08-06
  - 2027-12-09
  - 2028-06-07
  - 2029-06-07
  - 2029-07-23
  - 2029-08-06
  - 2030-06-07
  - 2030-07-23
  - 2030-08-06
  - 2030-12-09', 1, NOW(), 1, NOW(), 0),
  ('XLIS', 'Euronext Lisbon', 'note: >
  Lisbon follows the Euronext base calendar and adds nothing to it.
  An earlier version of this file carried the Portuguese national and religious holidays -- Carnival Tuesday, Liberty
  Day, Portugal Day, Corpus Christi, Republic Day, All Saints, Restoration of Independence and the Immaculate
  Conception -- on the assumption that public holidays are exchange closures. The comparison report contradicted every
  one of them, and a direct check settled it: on Carnival Tuesday 2018 the PSI-20 traded between 5356 and 5415 on a
  volume of 49.8 million, an ordinary session rather than a carried-forward price. Euronext Lisbon trades on Portuguese
  public holidays, so all of those rules were removed.
additionalClosures:
  - 2000-03-07
  - 2000-04-25
  - 2000-06-13
  - 2000-06-22
  - 2000-08-15
  - 2000-10-05
  - 2000-11-01
  - 2000-12-01
  - 2000-12-08
  - 2001-02-27
  - 2001-04-25
  - 2001-06-13
  - 2001-06-14
  - 2001-08-15
  - 2001-10-05
  - 2001-11-01
  - 2001-12-24
  - 2002-02-12
  - 2002-04-25
  - 2002-05-30
  - 2002-06-10
  - 2002-08-15
  - 2002-11-01
  - 2002-12-24
openDates:
  - 2000-06-12
  - 2001-06-04
  - 2002-05-20
  - 2003-06-09
  - 2004-05-31', 1, NOW(), 1, NOW(), 0),
  ('XLIT', 'Nasdaq Vilnius', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: EasterOffset39, type: EASTER_RELATIVE, offset: 39}
  - {name: Fixed1101SundayToMonday, type: FIXED, month: 11, day: 1, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed0815None, type: FIXED, month: 8, day: 15}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed0216None, type: FIXED, month: 2, day: 16}
  - {name: Fixed0706None, type: FIXED, month: 7, day: 6}
  - {name: Fixed1102None, type: FIXED, month: 11, day: 2}
  - {name: Fixed1224None, type: FIXED, month: 12, day: 24}
  - {name: Fixed0311None, type: FIXED, month: 3, day: 11}
  - {name: Fixed0624None, type: FIXED, month: 6, day: 24}
additionalClosures:
  - 2023-12-25', 1, NOW(), 1, NOW(), 0),
  ('XLJU', 'Ljubljana Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: Fixed1224NextMonday, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY}
  - {name: Fixed1225NearestWeekday, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Fixed1231NextMonday, type: FIXED, month: 12, day: 31, observance: NEXT_MONDAY}
  - {name: Fixed0101NearestWeekday, type: FIXED, month: 1, day: 1, observance: NEAREST_WEEKDAY}
  - {name: Fixed0501SundayToMonday, type: FIXED, month: 5, day: 1, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed1031SundayToMonday, type: FIXED, month: 10, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0208None, type: FIXED, month: 2, day: 8}
  - {name: Fixed0502None, type: FIXED, month: 5, day: 2}
  - {name: Fixed0815None, type: FIXED, month: 8, day: 15}
  - {name: Fixed1101None, type: FIXED, month: 11, day: 1}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0427None, type: FIXED, month: 4, day: 27}
  - {name: Fixed0625None, type: FIXED, month: 6, day: 25}
additionalClosures:
  - 2001-01-02
  - 2002-01-02
  - 2003-01-02
  - 2004-01-02
  - 2007-01-02
  - 2008-01-02
  - 2009-01-02
  - 2017-02-02
  - 2017-02-03
  - 2018-01-02
  - 2019-01-02
  - 2020-01-02
  - 2023-08-14
  - 2024-01-02
  - 2025-01-02
  - 2026-01-02
  - 2029-01-02
  - 2030-01-02', 1, NOW(), 1, NOW(), 0),
  ('XLON', 'London Stock Exchange', 'note: >
  UK bank holidays falling on a weekend are replaced by a substitute day on the following Monday. Christmas and Boxing
  Day are the awkward case: when 25 December is a Saturday the substitutes are Monday 27 and Tuesday 28, which the
  NEXT_MONDAY observance cannot express because both rules then resolve to the same Monday. Those years are corrected
  by the explicit list below, and the comparison report is the check that no year was missed.
  The early May bank holiday was moved from the first Monday to Friday 8 May in 2020 for the 75th anniversary of VE
  Day, which is handled as a one-off rather than by changing the recurring rule.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1, observance: NEXT_MONDAY}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: EarlyMayBankHoliday, type: NTH_WEEKDAY, month: 5, nth: 1, dayOfWeek: MONDAY, validTo: 2019}
  - {name: EarlyMayBankHolidayResumed, type: NTH_WEEKDAY, month: 5, nth: 1, dayOfWeek: MONDAY, validFrom: 2021}
  - {name: SpringBankHoliday, type: NTH_WEEKDAY, month: 5, nth: -1, dayOfWeek: MONDAY}
  - {name: SummerBankHoliday, type: NTH_WEEKDAY, month: 8, nth: -1, dayOfWeek: MONDAY}
  - {name: Christmas, type: FIXED, month: 12, day: 25, observance: NEXT_MONDAY}
  - {name: BoxingDay, type: FIXED, month: 12, day: 26, observance: NEXT_MONDAY}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24, halfDay: true}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31, halfDay: true}

  # Second substitute day in the years where 25 December falls on a Saturday or Sunday, so that both Christmas and
  # Boxing Day are compensated rather than collapsing onto one Monday.
  - name: ChristmasSecondSubstitute
    type: EXPLICIT_DATES
    dates:
      - 2004-12-28
      - 2005-12-27
      - 2010-12-28
      - 2011-12-27
      - 2015-12-28
      - 2016-12-27
      - 2021-12-28
      - 2022-12-27
      - 2027-12-28

  # Royal and commemorative one-off closures, plus the 2020 VE Day transfer.
  - name: RoyalAndCommemorative
    type: EXPLICIT_DATES
    dates:
      - 2002-06-03
      - 2002-06-04
      - 2011-04-29
      - 2012-06-04
      - 2012-06-05
      - 2020-05-08
      - 2022-06-02
      - 2022-06-03
      - 2022-09-19
      - 2023-05-08
openDates:
  - 2002-05-27
  - 2012-05-28
  - 2022-05-30', 1, NOW(), 1, NOW(), 0),
  ('XLUX', 'Luxembourg Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: Fixed1225SundayToMonday, type: FIXED, month: 12, day: 25, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}', 1, NOW(), 1, NOW(), 0),
  ('XMAD', 'Bolsa de Madrid', 'note: >
  Bolsas y Mercados Espanoles closes on the national holidays plus the two Madrid regional days, Dia de la Comunidad on
  2 May and Virgen de la Almudena on 9 November, which apply because the trading floor is in Madrid. Spanish holidays
  are not transferred when they fall on a weekend.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: Epiphany, type: FIXED, month: 1, day: 6}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: Assumption, type: FIXED, month: 8, day: 15}
  - {name: NationalDay, type: FIXED, month: 10, day: 12}
  - {name: AllSaints, type: FIXED, month: 11, day: 1}
  - {name: ConstitutionDay, type: FIXED, month: 12, day: 6}
  - {name: ImmaculateConception, type: FIXED, month: 12, day: 8}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
  - {name: StStephen, type: FIXED, month: 12, day: 26}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24, halfDay: true}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31, halfDay: true}
additionalClosures:
  - 2001-12-24
  - 2001-12-31
  - 2002-12-24
  - 2002-12-31
  - 2003-12-24
  - 2003-12-31
  - 2004-08-16
  - 2004-12-24
  - 2004-12-31
  - 2007-12-24
  - 2007-12-31
  - 2008-12-24
  - 2008-12-31
  - 2009-12-24
  - 2009-12-31
  - 2010-12-24
  - 2010-12-31
  - 2021-12-24
  - 2021-12-31
openDates:
  - 2005-08-15
  - 2005-10-12
  - 2005-11-01
  - 2005-12-06
  - 2005-12-08
  - 2006-08-15
  - 2006-10-12
  - 2006-11-01
  - 2006-12-06
  - 2006-12-08
  - 2007-08-15
  - 2007-10-12
  - 2007-11-01
  - 2007-12-06
  - 2008-08-15
  - 2008-12-08
  - 2009-01-06
  - 2009-10-12
  - 2009-12-08
  - 2010-01-06
  - 2010-10-12
  - 2010-11-01
  - 2010-12-06
  - 2010-12-08
  - 2011-01-06
  - 2011-08-15
  - 2011-10-12
  - 2011-11-01
  - 2011-12-06
  - 2011-12-08
  - 2012-01-06
  - 2012-08-15
  - 2012-10-12
  - 2012-11-01
  - 2012-12-06
  - 2013-08-15
  - 2013-11-01
  - 2013-12-06
  - 2014-01-06
  - 2014-08-15
  - 2014-12-08
  - 2015-01-06
  - 2015-10-12
  - 2015-12-08
  - 2016-01-06
  - 2016-08-15
  - 2016-10-12
  - 2016-11-01
  - 2016-12-06
  - 2016-12-08
  - 2017-01-06
  - 2017-08-15
  - 2017-10-12
  - 2017-11-01
  - 2017-12-06
  - 2017-12-08
  - 2018-08-15
  - 2018-10-12
  - 2018-11-01
  - 2018-12-06
  - 2019-08-15
  - 2019-11-01
  - 2019-12-06
  - 2020-01-06
  - 2020-10-12
  - 2020-12-08
  - 2021-01-06
  - 2021-10-12
  - 2021-11-01
  - 2021-12-06
  - 2021-12-08
  - 2022-01-06
  - 2022-08-15
  - 2022-10-12
  - 2022-11-01
  - 2022-12-06
  - 2022-12-08
  - 2023-01-06
  - 2023-08-15
  - 2023-10-12
  - 2023-11-01
  - 2023-12-06
  - 2023-12-08
  - 2024-08-15
  - 2024-11-01
  - 2024-12-06
  - 2025-01-06
  - 2025-08-15
  - 2025-12-08
  - 2026-01-06
  - 2026-10-12
  - 2026-12-08
  - 2027-01-06
  - 2027-10-12
  - 2027-11-01
  - 2027-12-06
  - 2027-12-08
  - 2028-01-06
  - 2028-08-15
  - 2028-10-12
  - 2028-11-01
  - 2028-12-06
  - 2028-12-08
  - 2029-08-15
  - 2029-10-12
  - 2029-11-01
  - 2029-12-06
  - 2030-08-15
  - 2030-11-01
  - 2030-12-06', 1, NOW(), 1, NOW(), 0),
  ('XMIL', 'Borsa Italiana Milano', 'note: >
  Borsa Italiana closes only on a small set of national holidays; the many Italian religious holidays that fall on a
  weekday are trading days. Assumption Day on 15 August is the notable closure. Italian holidays are not transferred
  when they fall on a weekend.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: Assumption, type: FIXED, month: 8, day: 15}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
  - {name: StStephen, type: FIXED, month: 12, day: 26}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24, halfDay: true}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31, halfDay: true}
additionalClosures:
  - 2001-12-24
  - 2001-12-31
  - 2002-12-24
  - 2002-12-31
  - 2003-12-24
  - 2003-12-31
  - 2004-12-24
  - 2004-12-31
  - 2007-12-24
  - 2007-12-31
  - 2008-12-24
  - 2008-12-31
  - 2009-12-24
  - 2009-12-31
  - 2010-12-24
  - 2010-12-31
  - 2012-12-24
  - 2012-12-31
  - 2013-12-24
  - 2013-12-31
  - 2014-12-24
  - 2014-12-31
  - 2015-12-24
  - 2015-12-31
  - 2018-12-24
  - 2018-12-31
  - 2019-12-24
  - 2019-12-31
  - 2020-12-24
  - 2020-12-31
  - 2021-12-24
  - 2021-12-31
  - 2024-12-24
  - 2024-12-31
  - 2025-12-24
  - 2025-12-31
  - 2026-12-24
  - 2026-12-31
  - 2027-12-24
  - 2027-12-31
  - 2029-12-24
  - 2029-12-31
  - 2030-12-24
  - 2030-12-31', 1, NOW(), 1, NOW(), 0),
  ('XMSM', 'Euronext Dublin', 'note: >
  Dublin joined Euronext in 2018 but keeps the Irish bank holiday calendar, which differs enough from the Euronext base
  that it is defined independently rather than inherited. Saint Brigid''s Day became a public holiday in 2023 and falls
  on the first Monday of February, except when 1 February is itself a Friday.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1, observance: NEXT_MONDAY}
  - {name: StBrigid, type: NTH_WEEKDAY, month: 2, nth: 1, dayOfWeek: MONDAY, validFrom: 2023}
  - {name: StPatrick, type: FIXED, month: 3, day: 17, observance: NEXT_MONDAY}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: MayBankHoliday, type: NTH_WEEKDAY, month: 5, nth: 1, dayOfWeek: MONDAY}
  - {name: JuneBankHoliday, type: NTH_WEEKDAY, month: 6, nth: 1, dayOfWeek: MONDAY}
  - {name: AugustBankHoliday, type: NTH_WEEKDAY, month: 8, nth: 1, dayOfWeek: MONDAY}
  - {name: OctoberBankHoliday, type: NTH_WEEKDAY, month: 10, nth: -1, dayOfWeek: MONDAY}
  - {name: Christmas, type: FIXED, month: 12, day: 25, observance: NEXT_MONDAY}
  - {name: StStephen, type: FIXED, month: 12, day: 26, observance: NEXT_MONDAY}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24, halfDay: true}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31, halfDay: true}
additionalClosures:
  - 2001-12-24
  - 2002-12-24
  - 2003-12-24
  - 2004-12-24
  - 2004-12-28
  - 2005-12-27
  - 2008-05-01
  - 2009-05-01
  - 2010-12-28
  - 2011-12-27
  - 2016-12-27
  - 2018-03-02
  - 2019-05-01
  - 2020-05-01
  - 2021-12-28
  - 2022-12-27
  - 2024-05-01
  - 2025-05-01
  - 2026-05-01
  - 2027-12-28
  - 2029-05-01
  - 2030-05-01
openDates:
  - 2000-08-07
  - 2000-10-30
  - 2001-03-19
  - 2001-08-06
  - 2001-10-29
  - 2002-03-18
  - 2002-08-05
  - 2002-10-28
  - 2003-03-17
  - 2003-08-04
  - 2003-10-27
  - 2004-03-17
  - 2004-08-02
  - 2004-10-25
  - 2005-03-17
  - 2005-08-01
  - 2005-10-31
  - 2006-03-17
  - 2006-08-07
  - 2006-10-30
  - 2007-03-19
  - 2007-08-06
  - 2007-10-29
  - 2008-03-17
  - 2008-08-04
  - 2008-10-27
  - 2009-03-17
  - 2009-08-03
  - 2009-10-26
  - 2010-03-17
  - 2010-08-02
  - 2010-10-25
  - 2011-03-17
  - 2011-08-01
  - 2011-10-31
  - 2012-03-19
  - 2012-08-06
  - 2012-10-29
  - 2013-03-18
  - 2013-08-05
  - 2013-10-28
  - 2014-03-17
  - 2014-08-04
  - 2014-10-27
  - 2015-03-17
  - 2015-08-03
  - 2015-10-26
  - 2016-03-17
  - 2016-08-01
  - 2016-10-31
  - 2017-03-17
  - 2017-08-07
  - 2017-10-30
  - 2018-03-19
  - 2018-08-06
  - 2018-10-29
  - 2019-03-18
  - 2019-05-06
  - 2019-06-03
  - 2019-08-05
  - 2019-10-28
  - 2020-03-17
  - 2020-05-04
  - 2020-06-01
  - 2020-08-03
  - 2020-10-26
  - 2021-03-17
  - 2021-06-07
  - 2021-08-02
  - 2021-10-25
  - 2022-03-17
  - 2022-06-06
  - 2022-08-01
  - 2022-10-31
  - 2023-02-06
  - 2023-03-17
  - 2023-06-05
  - 2023-08-07
  - 2023-10-30
  - 2024-02-05
  - 2024-03-18
  - 2024-06-03
  - 2024-08-05
  - 2024-10-28
  - 2025-02-03
  - 2025-03-17
  - 2025-06-02
  - 2025-08-04
  - 2025-10-27
  - 2026-02-02
  - 2026-03-17
  - 2026-06-01
  - 2026-08-03
  - 2026-10-26
  - 2027-02-01
  - 2027-03-17
  - 2027-06-07
  - 2027-08-02
  - 2027-10-25
  - 2028-02-07
  - 2028-03-17
  - 2028-06-05
  - 2028-08-07
  - 2028-10-30
  - 2029-02-05
  - 2029-03-19
  - 2029-06-04
  - 2029-08-06
  - 2029-10-29
  - 2030-02-04
  - 2030-03-18
  - 2030-06-03
  - 2030-08-05
  - 2030-10-28', 1, NOW(), 1, NOW(), 0),
  ('XMUN', 'Boerse Muenchen', 'note: >
  German regional exchange following the Xetra trading calendar. Regional venues have historically kept slightly
  longer hours than Xetra but close on the same days; any divergence the comparison report surfaces belongs here as
  an explicit deviation rule.
additionalClosures:
  - 2007-05-28
  - 2014-10-03
  - 2015-05-25
  - 2016-05-16
  - 2016-10-03
  - 2017-06-05
  - 2017-10-03
  - 2017-10-31
  - 2018-05-21
  - 2018-10-03
  - 2019-06-10
  - 2019-10-03
  - 2020-06-01
  - 2021-05-24', 1, NOW(), 1, NOW(), 0),
  ('XNAS', 'NASDAQ Stock Exchange', 'note: >
  NASDAQ follows the NYSE holiday calendar, including the unscheduled closures. GT points both exchanges at the same
  reference index (id_index_upd_calendar 3498), so the two calendars are expected to agree completely.', 1, NOW(), 1, NOW(), 0),
  ('XNYS', 'New York Stock Exchange', 'note: >
  NYSE observance rule: a holiday falling on a Saturday is observed on the preceding Friday, one falling on a Sunday on
  the following Monday. New Year''s Day is the documented exception -- the exchange does not close on 31 December for a
  New Year that falls on a Saturday, which is why it uses SUNDAY_TO_MONDAY rather than NEAREST_WEEKDAY.
  Juneteenth became a federal holiday in June 2021, but 19 June 2021 was a Saturday and the exchange did not close;
  the first observed closure was in 2022.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1, observance: SUNDAY_TO_MONDAY}
  - {name: MartinLutherKing, type: NTH_WEEKDAY, month: 1, nth: 3, dayOfWeek: MONDAY}
  - {name: WashingtonsBirthday, type: NTH_WEEKDAY, month: 2, nth: 3, dayOfWeek: MONDAY}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: MemorialDay, type: NTH_WEEKDAY, month: 5, nth: -1, dayOfWeek: MONDAY}
  - {name: Juneteenth, type: FIXED, month: 6, day: 19, validFrom: 2022, observance: NEAREST_WEEKDAY}
  - {name: IndependenceDay, type: FIXED, month: 7, day: 4, observance: NEAREST_WEEKDAY}
  - {name: LaborDay, type: NTH_WEEKDAY, month: 9, nth: 1, dayOfWeek: MONDAY}
  - {name: Thanksgiving, type: NTH_WEEKDAY, month: 11, nth: 4, dayOfWeek: THURSDAY}
  - {name: Christmas, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}

  # Shortened sessions (13:00 close). Recorded for completeness; never emitted as closures.
  - {name: DayAfterThanksgiving, type: NTH_WEEKDAY, month: 11, nth: 4, dayOfWeek: FRIDAY, halfDay: true}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24, halfDay: true}

  # Unscheduled closures: terror attack, national days of mourning, hurricane Sandy.
  - name: UnscheduledClosure
    type: EXPLICIT_DATES
    dates:
      - 2001-09-11
      - 2001-09-12
      - 2001-09-13
      - 2001-09-14
      - 2004-06-11
      - 2007-01-02
      - 2012-10-29
      - 2012-10-30
      - 2018-12-05
      - 2025-01-09', 1, NOW(), 1, NOW(), 0),
  ('XNZE', 'New Zealand Exchange', 'note: >
  New Zealand transfers holidays falling on a weekend to the following Monday, and Christmas and New Year each carry a
  second day that is transferred as well. Waitangi Day and Anzac Day were only given weekend transfer ("Mondayisation")
  from 2014, which the two rule variants express. Matariki was introduced in 2022 and follows the Maori lunar calendar,
  so its dates are announced in advance rather than computed.
  Auckland Anniversary Day is a regional holiday on which the exchange closes.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1, observance: NEXT_MONDAY}
  - {name: DayAfterNewYear, type: FIXED, month: 1, day: 2, observance: NEXT_MONDAY}
  - {name: AucklandAnniversary, type: EXPLICIT_DATES, dates: [
      2000-01-31, 2001-01-29, 2002-01-28, 2003-01-27, 2004-02-02, 2005-01-31, 2006-01-30, 2007-01-29,
      2008-01-28, 2009-01-26, 2010-02-01, 2011-01-31, 2012-01-30, 2013-01-28, 2014-01-27, 2015-01-26,
      2016-02-01, 2017-01-30, 2018-01-29, 2019-01-28, 2020-01-27, 2021-02-01, 2022-01-31, 2023-01-30,
      2024-01-29, 2025-01-27, 2026-02-02, 2027-02-01, 2028-01-31]}
  - {name: WaitangiDay, type: FIXED, month: 2, day: 6, validTo: 2013}
  - {name: WaitangiDayMondayised, type: FIXED, month: 2, day: 6, validFrom: 2014, observance: NEXT_MONDAY}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: AnzacDay, type: FIXED, month: 4, day: 25, validTo: 2013}
  - {name: AnzacDayMondayised, type: FIXED, month: 4, day: 25, validFrom: 2014, observance: NEXT_MONDAY}
  - {name: MonarchsBirthday, type: NTH_WEEKDAY, month: 6, nth: 1, dayOfWeek: MONDAY}
  - {name: Matariki, type: EXPLICIT_DATES, dates: [
      2022-06-24, 2023-07-14, 2024-06-28, 2025-06-20, 2026-07-10, 2027-06-25, 2028-07-14]}
  - {name: LabourDay, type: NTH_WEEKDAY, month: 10, nth: 4, dayOfWeek: MONDAY}
  - {name: Christmas, type: FIXED, month: 12, day: 25, observance: NEXT_MONDAY}
  - {name: BoxingDay, type: FIXED, month: 12, day: 26, observance: NEXT_MONDAY}

  # Second substitute where 25 December falls on a weekend, and the matching pair for 1 and 2 January.
  - name: ChristmasAndNewYearSecondSubstitute
    type: EXPLICIT_DATES
    dates:
      - 2004-12-28
      - 2005-01-04
      - 2005-12-27
      - 2010-12-28
      - 2011-01-04
      - 2011-12-27
      - 2015-12-28
      - 2016-01-04
      - 2016-12-27
      - 2021-12-28
      - 2022-01-04
      - 2022-12-27
      - 2027-12-28
      - 2028-01-04
additionalClosures:
  - 2000-01-04
  - 2006-01-03
  - 2012-01-03
  - 2017-01-03
  - 2022-09-26
  - 2023-01-03
  - 2029-07-06
  - 2030-06-21
openDates:
  - 2000-01-31
  - 2001-01-29
  - 2002-01-28
  - 2003-01-27
  - 2004-02-02
  - 2005-01-31
  - 2006-01-30
  - 2007-01-29
  - 2008-01-28
  - 2009-01-26
  - 2010-02-01
  - 2011-01-31
  - 2012-01-30
  - 2013-01-28
  - 2014-01-27
  - 2015-01-26
  - 2016-02-01
  - 2017-01-30
  - 2018-01-29
  - 2019-01-28
  - 2020-01-27
  - 2021-02-01
  - 2022-01-31
  - 2023-01-30
  - 2024-01-29
  - 2025-01-27
  - 2026-02-02
  - 2027-02-01
  - 2028-01-31', 1, NOW(), 1, NOW(), 0),
  ('XOSL', 'Oslo Boers', 'note: >
  Norway closes on Maundy Thursday, on Constitution Day on 17 May and on Christmas Eve. Norwegian holidays are not
  transferred when they fall on a weekend.
  GT''s reference index for this exchange only starts in June 2008, which is why the stored calendar currently marks
  every weekday from 2000 to 2008 as a closure; that block is the clearest case the comparison report should expose.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: MaundyThursday, type: EASTER_RELATIVE, offset: -3}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: ConstitutionDay, type: FIXED, month: 5, day: 17}
  - {name: Ascension, type: EASTER_RELATIVE, offset: 39}
  - {name: WhitMonday, type: EASTER_RELATIVE, offset: 50}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
  - {name: StStephen, type: FIXED, month: 12, day: 26}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31}', 1, NOW(), 1, NOW(), 0),
  ('XPAR', 'Euronext Paris', 'note: >
  Serves as the base calendar for the Euronext venues. The four common closures are New Year, Good Friday, Easter
  Monday, Labour Day, Christmas and Boxing Day; national holidays are added by the individual venue files.
  Whit Monday was a Euronext closure until 2004 and the markets have been open on it since 2005, which the validTo
  expresses. 24 and 31 December are shortened sessions rather than closures.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: WhitMonday, type: EASTER_RELATIVE, offset: 50, validTo: 2004}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
  - {name: BoxingDay, type: FIXED, month: 12, day: 26}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24, halfDay: true}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31, halfDay: true}
additionalClosures:
  - 2000-07-14
  - 2001-12-31
openDates:
  - 2002-05-20
  - 2003-06-09
  - 2004-05-31', 1, NOW(), 1, NOW(), 0),
  ('XPHS', 'Philippine Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
additionalClosures:
  - 2002-01-01
  - 2002-02-25
  - 2002-03-28
  - 2002-04-08
  - 2002-05-01
  - 2002-06-12
  - 2002-07-15
  - 2002-10-31
  - 2002-11-01
  - 2002-12-06
  - 2002-12-24
  - 2002-12-25
  - 2002-12-30
  - 2002-12-31
  - 2003-01-01
  - 2003-02-25
  - 2003-04-07
  - 2003-04-17
  - 2003-05-01
  - 2003-05-02
  - 2003-06-13
  - 2003-08-22
  - 2003-11-26
  - 2003-12-24
  - 2003-12-25
  - 2003-12-26
  - 2003-12-31
  - 2004-01-01
  - 2004-01-02
  - 2004-02-25
  - 2004-04-07
  - 2004-04-08
  - 2004-05-10
  - 2004-11-01
  - 2004-11-15
  - 2004-11-29
  - 2004-12-03
  - 2004-12-24
  - 2004-12-27
  - 2004-12-30
  - 2004-12-31
  - 2005-02-25
  - 2005-03-23
  - 2005-03-24
  - 2005-05-02
  - 2005-06-13
  - 2005-07-25
  - 2005-08-29
  - 2005-10-31
  - 2005-11-01
  - 2005-11-04
  - 2005-11-28
  - 2005-12-26
  - 2005-12-30
  - 2006-04-13
  - 2006-05-01
  - 2006-06-12
  - 2006-07-24
  - 2006-08-21
  - 2006-09-28
  - 2006-09-29
  - 2006-10-24
  - 2006-11-01
  - 2006-12-01
  - 2006-12-25
  - 2006-12-26
  - 2007-01-01
  - 2007-04-05
  - 2007-04-09
  - 2007-05-01
  - 2007-05-14
  - 2007-06-11
  - 2007-08-20
  - 2007-08-27
  - 2007-10-12
  - 2007-10-29
  - 2007-11-01
  - 2007-11-02
  - 2007-11-30
  - 2007-12-24
  - 2007-12-25
  - 2007-12-31
  - 2008-01-01
  - 2008-02-25
  - 2008-03-20
  - 2008-04-07
  - 2008-05-01
  - 2008-06-09
  - 2008-08-18
  - 2008-08-25
  - 2008-10-01
  - 2008-12-01
  - 2008-12-25
  - 2008-12-26
  - 2008-12-29
  - 2008-12-30
  - 2008-12-31
  - 2009-01-01
  - 2009-01-02
  - 2009-04-06
  - 2009-04-09
  - 2009-05-01
  - 2009-06-12
  - 2009-07-17
  - 2009-08-05
  - 2009-08-21
  - 2009-08-31
  - 2009-09-07
  - 2009-09-21
  - 2009-11-02
  - 2009-11-30
  - 2009-12-24
  - 2009-12-25
  - 2009-12-30
  - 2009-12-31
  - 2010-01-01
  - 2010-04-01
  - 2010-04-09
  - 2010-05-03
  - 2010-05-10
  - 2010-06-14
  - 2010-06-30
  - 2010-08-30
  - 2010-09-10
  - 2010-10-25
  - 2010-11-01
  - 2010-11-16
  - 2010-11-29
  - 2010-12-24
  - 2010-12-27
  - 2010-12-31
  - 2011-04-21
  - 2011-06-20
  - 2011-08-29
  - 2011-08-30
  - 2011-09-27
  - 2011-10-31
  - 2011-11-01
  - 2011-11-07
  - 2011-11-30
  - 2011-12-30
  - 2012-01-23
  - 2012-04-05
  - 2012-04-09
  - 2012-05-01
  - 2012-06-12
  - 2012-08-07
  - 2012-08-20
  - 2012-08-21
  - 2012-08-27
  - 2012-10-26
  - 2012-11-01
  - 2012-11-02
  - 2012-11-30
  - 2012-12-24
  - 2012-12-25
  - 2012-12-31
  - 2013-01-01
  - 2013-03-28
  - 2013-04-09
  - 2013-05-01
  - 2013-05-13
  - 2013-06-12
  - 2013-08-09
  - 2013-08-19
  - 2013-08-20
  - 2013-08-21
  - 2013-08-26
  - 2013-10-15
  - 2013-10-28
  - 2013-11-01
  - 2013-12-24
  - 2013-12-25
  - 2013-12-30
  - 2013-12-31
  - 2014-01-01
  - 2014-01-31
  - 2014-04-09
  - 2014-04-17
  - 2014-05-01
  - 2014-06-12
  - 2014-07-16
  - 2014-07-29
  - 2014-08-21
  - 2014-08-25
  - 2014-09-19
  - 2014-10-06
  - 2014-12-08
  - 2014-12-24
  - 2014-12-25
  - 2014-12-26
  - 2014-12-30
  - 2014-12-31
  - 2015-01-01
  - 2015-01-02
  - 2015-01-15
  - 2015-01-16
  - 2015-02-19
  - 2015-04-02
  - 2015-04-09
  - 2015-05-01
  - 2015-06-12
  - 2015-07-17
  - 2015-08-21
  - 2015-08-31
  - 2015-09-25
  - 2015-11-18
  - 2015-11-19
  - 2015-11-30
  - 2015-12-24
  - 2015-12-25
  - 2015-12-30
  - 2015-12-31
  - 2016-01-01
  - 2016-02-08
  - 2016-02-25
  - 2016-03-24
  - 2016-05-09
  - 2016-07-06
  - 2016-08-29
  - 2016-09-12
  - 2016-10-31
  - 2016-11-01
  - 2016-11-30
  - 2016-12-26
  - 2016-12-30
  - 2017-01-02
  - 2017-04-13
  - 2017-04-28
  - 2017-05-01
  - 2017-06-12
  - 2017-06-26
  - 2017-08-21
  - 2017-08-28
  - 2017-09-01
  - 2017-09-12
  - 2017-10-16
  - 2017-10-31
  - 2017-11-01
  - 2017-11-30
  - 2017-12-08
  - 2017-12-25
  - 2017-12-26
  - 2018-01-01
  - 2018-01-02
  - 2018-02-16
  - 2018-03-29
  - 2018-04-09
  - 2018-05-01
  - 2018-05-14
  - 2018-06-12
  - 2018-06-15
  - 2018-08-21
  - 2018-08-27
  - 2018-11-01
  - 2018-11-02
  - 2018-11-30
  - 2018-12-24
  - 2018-12-25
  - 2018-12-31
  - 2019-01-01
  - 2019-02-05
  - 2019-02-25
  - 2019-04-09
  - 2019-04-18
  - 2019-05-01
  - 2019-05-13
  - 2019-06-05
  - 2019-06-12
  - 2019-08-12
  - 2019-08-21
  - 2019-08-26
  - 2019-11-01
  - 2019-12-24
  - 2019-12-25
  - 2019-12-30
  - 2019-12-31
  - 2020-01-01
  - 2020-02-25
  - 2020-04-09
  - 2020-05-01
  - 2020-05-25
  - 2020-06-12
  - 2020-07-31
  - 2020-08-21
  - 2020-08-31
  - 2020-11-02
  - 2020-11-30
  - 2020-12-08
  - 2020-12-24
  - 2020-12-25
  - 2020-12-30
  - 2020-12-31
  - 2021-01-01
  - 2021-02-12
  - 2021-02-25
  - 2021-04-01
  - 2021-04-09
  - 2021-05-13
  - 2021-07-20
  - 2021-08-30
  - 2021-11-01
  - 2021-11-30
  - 2021-12-08
  - 2021-12-24
  - 2021-12-30
  - 2021-12-31
  - 2022-02-01
  - 2022-04-14
  - 2022-05-03
  - 2022-05-09
  - 2022-08-29
  - 2022-10-31
  - 2022-11-01
  - 2022-11-30
  - 2022-12-08
  - 2022-12-30
  - 2023-01-02
  - 2023-02-24
  - 2023-04-06
  - 2023-04-10
  - 2023-04-21
  - 2023-05-01
  - 2023-06-12
  - 2023-06-28
  - 2023-08-21
  - 2023-08-28
  - 2023-10-30
  - 2023-11-01
  - 2023-11-02
  - 2023-11-27
  - 2023-12-08
  - 2023-12-25
  - 2023-12-26
  - 2024-01-01
  - 2024-02-09
  - 2024-03-28
  - 2024-04-09
  - 2024-04-10
  - 2024-05-01
  - 2024-06-12
  - 2024-06-17
  - 2024-08-23
  - 2024-08-26
  - 2024-11-01
  - 2024-12-24
  - 2024-12-25
  - 2024-12-30
  - 2024-12-31
  - 2025-01-01
  - 2025-01-29
  - 2025-04-01
  - 2025-04-09
  - 2025-04-17
  - 2025-05-01
  - 2025-05-12
  - 2025-06-06
  - 2025-06-12
  - 2025-08-21
  - 2025-08-25
  - 2025-10-31
  - 2025-12-08
  - 2025-12-24
  - 2025-12-25
  - 2025-12-30
  - 2025-12-31
  - 2026-01-01
  - 2026-02-17
  - 2026-03-20
  - 2026-04-02
  - 2026-04-09
  - 2026-05-01
  - 2026-05-27
  - 2026-06-12
  - 2026-08-21
  - 2026-08-31
  - 2026-11-30
  - 2026-12-08
  - 2026-12-24
  - 2026-12-25
  - 2026-12-30
  - 2026-12-31
  - 2027-01-01
  - 2027-03-10
  - 2027-03-25
  - 2027-04-09
  - 2027-05-17
  - 2027-08-30
  - 2027-11-01
  - 2027-11-30
  - 2027-12-08
  - 2027-12-24
  - 2027-12-30
  - 2027-12-31
  - 2028-01-26
  - 2028-04-13
  - 2028-05-01
  - 2028-06-12
  - 2028-08-21
  - 2028-08-28
  - 2028-11-01
  - 2028-11-30
  - 2028-12-08
  - 2028-12-25
  - 2029-01-01
  - 2029-02-13
  - 2029-03-29
  - 2029-04-09
  - 2029-05-01
  - 2029-06-12
  - 2029-08-21
  - 2029-08-27
  - 2029-11-01
  - 2029-11-02
  - 2029-11-30
  - 2029-12-24
  - 2029-12-25
  - 2029-12-31
  - 2030-01-01
  - 2030-04-09
  - 2030-04-18
  - 2030-05-01
  - 2030-06-12
  - 2030-08-21
  - 2030-08-26
  - 2030-11-01
  - 2030-12-24
  - 2030-12-25
  - 2030-12-30
  - 2030-12-31', 1, NOW(), 1, NOW(), 0),
  ('XPRA', 'Prague Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: Fixed1224NextMonday, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY}
  - {name: Fixed1225NearestWeekday, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Fixed0705SundayToMonday, type: FIXED, month: 7, day: 5, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed0508None, type: FIXED, month: 5, day: 8}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed0706None, type: FIXED, month: 7, day: 6}
  - {name: Fixed0928None, type: FIXED, month: 9, day: 28}
  - {name: Fixed1028None, type: FIXED, month: 10, day: 28}
  - {name: Fixed1117None, type: FIXED, month: 11, day: 17}
additionalClosures:
  - 2002-08-14
  - 2004-01-02
  - 2005-01-03
  - 2013-03-29
  - 2014-04-18
  - 2015-04-03
  - 2016-03-25
  - 2017-04-14
  - 2018-03-30
  - 2019-04-19
  - 2020-04-10
  - 2021-04-02
  - 2022-04-15
  - 2023-04-07
  - 2024-03-29
  - 2025-04-18
  - 2026-04-03
  - 2027-03-26
  - 2028-04-14
  - 2029-03-30
  - 2030-04-19', 1, NOW(), 1, NOW(), 0),
  ('XRIS', 'Nasdaq Riga', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: EasterOffset39, type: EASTER_RELATIVE, offset: 39}
  - {name: Fixed0504NextMonday, type: FIXED, month: 5, day: 4, observance: NEXT_MONDAY}
  - {name: Fixed1118NextMonday, type: FIXED, month: 11, day: 18, observance: NEXT_MONDAY}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0623SundayToMonday, type: FIXED, month: 6, day: 23, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed1224None, type: FIXED, month: 12, day: 24}
  - {name: Fixed0624None, type: FIXED, month: 6, day: 24}
additionalClosures:
  - 2018-05-09
  - 2023-05-05
  - 2023-07-10
  - 2023-12-25
  - 2024-12-23
  - 2024-12-30
  - 2025-05-02
  - 2025-11-17
  - 2028-07-10', 1, NOW(), 1, NOW(), 0),
  ('XSAU', 'Saudi Exchange', 'authoritativeFrom: 2021
authoritativeThrough: 2029
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: WeeklyFriday, type: WEEKLY, dayOfWeek: FRIDAY}
  - {name: Fixed0222None, type: FIXED, month: 2, day: 22}
  - {name: Fixed0923None, type: FIXED, month: 9, day: 23}
additionalClosures:
  - 2021-05-13
  - 2021-07-19
  - 2021-07-20
  - 2021-07-21
  - 2021-07-22
  - 2022-04-28
  - 2022-05-02
  - 2022-05-03
  - 2022-05-04
  - 2022-05-05
  - 2022-07-07
  - 2022-07-11
  - 2022-07-12
  - 2022-07-13
  - 2023-04-18
  - 2023-04-19
  - 2023-04-20
  - 2023-04-24
  - 2023-04-25
  - 2023-06-27
  - 2023-06-28
  - 2023-06-29
  - 2024-04-08
  - 2024-04-09
  - 2024-04-10
  - 2024-04-11
  - 2024-04-15
  - 2024-06-13
  - 2024-06-17
  - 2024-06-18
  - 2024-06-19
  - 2024-06-20
  - 2025-03-27
  - 2025-03-31
  - 2025-04-01
  - 2025-04-02
  - 2025-06-04
  - 2025-06-05
  - 2025-06-09
  - 2026-03-17
  - 2026-03-18
  - 2026-03-19
  - 2026-03-23
  - 2026-05-25
  - 2026-05-26
  - 2026-05-27
  - 2026-05-28
  - 2027-03-08
  - 2027-03-09
  - 2027-03-10
  - 2027-03-11
  - 2027-05-17
  - 2027-05-18
  - 2027-05-19
  - 2027-05-20
  - 2028-02-28
  - 2028-02-29
  - 2028-03-01
  - 2028-03-02
  - 2028-05-03
  - 2028-05-04
  - 2028-05-08
  - 2028-05-09
  - 2029-02-12
  - 2029-02-13
  - 2029-02-14
  - 2029-02-15
  - 2029-04-23
  - 2029-04-24
  - 2029-04-25
  - 2029-04-26', 1, NOW(), 1, NOW(), 0),
  ('XSES', 'Singapore Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2026
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: Fixed0501SundayToMonday, type: FIXED, month: 5, day: 1, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed1225SundayToMonday, type: FIXED, month: 12, day: 25, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0101SundayToMonday, type: FIXED, month: 1, day: 1, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0809SundayToMonday, type: FIXED, month: 8, day: 9, observance: SUNDAY_TO_MONDAY}
additionalClosures:
  - 2000-02-07
  - 2000-03-16
  - 2000-05-18
  - 2000-10-26
  - 2000-12-27
  - 2001-01-24
  - 2001-01-25
  - 2001-03-06
  - 2001-05-07
  - 2001-11-14
  - 2001-12-17
  - 2002-02-12
  - 2002-02-13
  - 2002-05-27
  - 2002-11-04
  - 2002-12-06
  - 2003-02-03
  - 2003-02-12
  - 2003-05-15
  - 2003-10-24
  - 2003-11-25
  - 2004-01-22
  - 2004-01-23
  - 2004-02-02
  - 2004-06-02
  - 2004-11-11
  - 2004-11-15
  - 2005-01-21
  - 2005-02-09
  - 2005-02-10
  - 2005-05-23
  - 2005-11-01
  - 2005-11-03
  - 2006-01-10
  - 2006-01-30
  - 2006-01-31
  - 2006-05-12
  - 2006-10-24
  - 2007-01-02
  - 2007-02-19
  - 2007-02-20
  - 2007-05-31
  - 2007-11-08
  - 2007-12-20
  - 2008-02-07
  - 2008-02-08
  - 2008-05-19
  - 2008-10-01
  - 2008-10-27
  - 2008-12-08
  - 2009-01-26
  - 2009-01-27
  - 2009-09-21
  - 2009-11-27
  - 2010-02-15
  - 2010-02-16
  - 2010-05-28
  - 2010-09-10
  - 2010-11-05
  - 2010-11-17
  - 2011-02-03
  - 2011-02-04
  - 2011-05-17
  - 2011-08-30
  - 2011-10-26
  - 2011-11-07
  - 2012-01-23
  - 2012-01-24
  - 2012-08-20
  - 2012-10-26
  - 2012-11-13
  - 2013-02-11
  - 2013-02-12
  - 2013-05-24
  - 2013-08-08
  - 2013-10-15
  - 2014-01-31
  - 2014-05-13
  - 2014-07-28
  - 2014-10-06
  - 2014-10-22
  - 2015-02-19
  - 2015-02-20
  - 2015-06-01
  - 2015-07-17
  - 2015-08-07
  - 2015-09-11
  - 2015-09-24
  - 2015-11-10
  - 2016-02-08
  - 2016-02-09
  - 2016-07-06
  - 2016-09-12
  - 2017-01-30
  - 2017-05-10
  - 2017-06-26
  - 2017-09-01
  - 2017-10-18
  - 2018-02-16
  - 2018-05-29
  - 2018-06-15
  - 2018-08-22
  - 2018-11-06
  - 2019-02-05
  - 2019-02-06
  - 2019-05-20
  - 2019-06-05
  - 2019-08-12
  - 2019-10-28
  - 2020-01-27
  - 2020-05-07
  - 2020-05-25
  - 2020-07-10
  - 2020-07-31
  - 2021-02-12
  - 2021-05-13
  - 2021-05-26
  - 2021-07-20
  - 2021-11-04
  - 2022-02-01
  - 2022-02-02
  - 2022-05-03
  - 2022-05-16
  - 2022-07-11
  - 2022-10-24
  - 2023-01-23
  - 2023-01-24
  - 2023-06-02
  - 2023-06-29
  - 2023-09-01
  - 2023-11-13
  - 2024-02-12
  - 2024-04-10
  - 2024-05-22
  - 2024-06-17
  - 2024-10-31
  - 2025-01-29
  - 2025-01-30
  - 2025-03-31
  - 2025-05-12
  - 2025-10-20
  - 2026-02-17
  - 2026-02-18
  - 2026-05-27
  - 2026-06-01
  - 2026-11-09', 1, NOW(), 1, NOW(), 0),
  ('XSGO', 'Santiago Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: Fixed0918SundayToMonday, type: FIXED, month: 9, day: 18, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed0815None, type: FIXED, month: 8, day: 15}
  - {name: Fixed0919None, type: FIXED, month: 9, day: 19}
  - {name: Fixed1101None, type: FIXED, month: 11, day: 1}
  - {name: Fixed1225None, type: FIXED, month: 12, day: 25}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed0521None, type: FIXED, month: 5, day: 21}
  - {name: Fixed1208None, type: FIXED, month: 12, day: 8}
additionalClosures:
  - 2000-06-19
  - 2000-06-26
  - 2000-10-09
  - 2001-06-11
  - 2001-07-02
  - 2001-10-15
  - 2002-05-27
  - 2003-06-16
  - 2004-06-07
  - 2004-06-28
  - 2004-10-11
  - 2005-05-23
  - 2005-06-27
  - 2005-10-10
  - 2006-06-12
  - 2006-06-26
  - 2006-10-09
  - 2007-06-04
  - 2007-07-02
  - 2007-09-17
  - 2007-10-15
  - 2008-07-16
  - 2008-10-31
  - 2009-06-29
  - 2009-07-16
  - 2009-10-12
  - 2010-06-28
  - 2010-07-16
  - 2010-09-17
  - 2010-09-20
  - 2010-10-11
  - 2011-06-27
  - 2011-10-10
  - 2011-10-31
  - 2012-07-02
  - 2012-07-16
  - 2012-09-17
  - 2012-10-15
  - 2012-11-02
  - 2013-07-16
  - 2013-09-20
  - 2013-10-31
  - 2014-07-16
  - 2014-10-31
  - 2015-06-29
  - 2015-07-16
  - 2015-10-12
  - 2016-06-27
  - 2016-10-10
  - 2016-10-31
  - 2017-01-02
  - 2017-04-19
  - 2017-06-26
  - 2017-10-09
  - 2017-10-27
  - 2018-01-16
  - 2018-07-02
  - 2018-07-16
  - 2018-09-17
  - 2018-10-15
  - 2018-11-02
  - 2019-07-16
  - 2019-09-20
  - 2019-10-31
  - 2020-06-29
  - 2020-07-16
  - 2020-10-12
  - 2021-06-21
  - 2021-06-28
  - 2021-07-16
  - 2021-10-11
  - 2022-06-21
  - 2022-06-27
  - 2022-09-16
  - 2022-10-10
  - 2022-10-31
  - 2023-06-21
  - 2023-06-26
  - 2023-10-09
  - 2023-10-27
  - 2024-06-20
  - 2024-07-16
  - 2024-09-20
  - 2024-10-31
  - 2025-06-20
  - 2025-07-16
  - 2025-10-31
  - 2026-06-29
  - 2026-07-16
  - 2026-10-12
  - 2027-06-21
  - 2027-06-28
  - 2027-07-16
  - 2027-10-11
  - 2028-06-20
  - 2028-06-26
  - 2028-10-09
  - 2028-10-27
  - 2029-06-20
  - 2029-07-02
  - 2029-07-16
  - 2029-09-17
  - 2029-10-15
  - 2029-11-02
  - 2030-06-21
  - 2030-07-16
  - 2030-09-20
  - 2030-10-31', 1, NOW(), 1, NOW(), 0),
  ('XSHE', 'Shenzhen Stock Exchange', 'note: >
  Shenzhen and Shanghai observe an identical calendar, both set by the State Council. GT currently has no reference
  index assigned to Shenzhen at all, so its stored calendar is empty and the comparison report has nothing to compare
  against -- which is itself a useful finding, because it means standing orders on this exchange are today never
  adjusted for a holiday.', 1, NOW(), 1, NOW(), 0),
  ('XSHG', 'Shanghai Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2026
note: >
  Exact weekday closures from the Shanghai Stock Exchange annual holiday notices and subsequent adjustments. Mainland
  China''s festival dates are partly predictable, but the State Council and the exchange decide the surrounding bridge
  days annually. Weekend make-up working days are not trading days and therefore do not appear here. Append the next
  official SSE schedule and advance authoritativeThrough each year; do not extrapolate future bridge days.
rules:
  - name: SseSchedule2000
    type: EXPLICIT_DATES
    dates: [2000-01-03, 2000-01-31, 2000-02-01, 2000-02-02, 2000-02-03, 2000-02-04, 2000-02-07,
            2000-02-08, 2000-02-09, 2000-02-10, 2000-02-11, 2000-05-01, 2000-05-02, 2000-05-03,
            2000-05-04, 2000-05-05, 2000-10-02, 2000-10-03, 2000-10-04, 2000-10-05, 2000-10-06]
  - name: SseSchedule2001
    type: EXPLICIT_DATES
    dates: [2001-01-01, 2001-01-22, 2001-01-23, 2001-01-24, 2001-01-25, 2001-01-26, 2001-01-29,
            2001-01-30, 2001-01-31, 2001-02-01, 2001-02-02, 2001-05-01, 2001-05-02, 2001-05-03,
            2001-05-04, 2001-05-07, 2001-10-01, 2001-10-02, 2001-10-03, 2001-10-04, 2001-10-05]
  - name: SseSchedule2002
    type: EXPLICIT_DATES
    dates: [2002-01-01, 2002-01-02, 2002-01-03, 2002-02-11, 2002-02-12, 2002-02-13, 2002-02-14,
            2002-02-15, 2002-02-18, 2002-02-19, 2002-02-20, 2002-02-21, 2002-02-22, 2002-05-01,
            2002-05-02, 2002-05-03, 2002-05-06, 2002-05-07, 2002-09-30, 2002-10-01, 2002-10-02,
            2002-10-03, 2002-10-04, 2002-10-07]
  - name: SseSchedule2003
    type: EXPLICIT_DATES
    dates: [2003-01-01, 2003-01-30, 2003-01-31, 2003-02-03, 2003-02-04, 2003-02-05, 2003-02-06,
            2003-02-07, 2003-05-01, 2003-05-02, 2003-05-05, 2003-05-06, 2003-05-07, 2003-05-08,
            2003-05-09, 2003-10-01, 2003-10-02, 2003-10-03, 2003-10-06, 2003-10-07]
  - name: SseSchedule2004
    type: EXPLICIT_DATES
    dates: [2004-01-01, 2004-01-19, 2004-01-20, 2004-01-21, 2004-01-22, 2004-01-23, 2004-01-26,
            2004-01-27, 2004-01-28, 2004-05-03, 2004-05-04, 2004-05-05, 2004-05-06, 2004-05-07,
            2004-10-01, 2004-10-04, 2004-10-05, 2004-10-06, 2004-10-07]
  - name: SseSchedule2005
    type: EXPLICIT_DATES
    dates: [2005-01-03, 2005-02-07, 2005-02-08, 2005-02-09, 2005-02-10, 2005-02-11, 2005-02-14,
            2005-02-15, 2005-05-02, 2005-05-03, 2005-05-04, 2005-05-05, 2005-05-06, 2005-10-03,
            2005-10-04, 2005-10-05, 2005-10-06, 2005-10-07]
  - name: SseSchedule2006
    type: EXPLICIT_DATES
    dates: [2006-01-02, 2006-01-03, 2006-01-26, 2006-01-27, 2006-01-30, 2006-01-31, 2006-02-01,
            2006-02-02, 2006-02-03, 2006-05-01, 2006-05-02, 2006-05-03, 2006-05-04, 2006-05-05,
            2006-10-02, 2006-10-03, 2006-10-04, 2006-10-05, 2006-10-06]
  - name: SseSchedule2007
    type: EXPLICIT_DATES
    dates: [2007-01-01, 2007-01-02, 2007-01-03, 2007-02-19, 2007-02-20, 2007-02-21, 2007-02-22,
            2007-02-23, 2007-05-01, 2007-05-02, 2007-05-03, 2007-05-04, 2007-05-07, 2007-10-01,
            2007-10-02, 2007-10-03, 2007-10-04, 2007-10-05, 2007-12-31]
  - name: SseSchedule2008
    type: EXPLICIT_DATES
    dates: [2008-01-01, 2008-02-06, 2008-02-07, 2008-02-08, 2008-02-11, 2008-02-12, 2008-04-04,
            2008-05-01, 2008-05-02, 2008-06-09, 2008-09-15, 2008-09-29, 2008-09-30, 2008-10-01,
            2008-10-02, 2008-10-03]
  - name: SseSchedule2009
    type: EXPLICIT_DATES
    dates: [2009-01-01, 2009-01-02, 2009-01-26, 2009-01-27, 2009-01-28, 2009-01-29, 2009-01-30,
            2009-04-06, 2009-05-01, 2009-05-28, 2009-05-29, 2009-10-01, 2009-10-02, 2009-10-05,
            2009-10-06, 2009-10-07, 2009-10-08]
  - name: SseSchedule2010
    type: EXPLICIT_DATES
    dates: [2010-01-01, 2010-02-15, 2010-02-16, 2010-02-17, 2010-02-18, 2010-02-19, 2010-04-05,
            2010-05-03, 2010-06-14, 2010-06-15, 2010-06-16, 2010-09-22, 2010-09-23, 2010-09-24,
            2010-10-01, 2010-10-04, 2010-10-05, 2010-10-06, 2010-10-07]
  - name: SseSchedule2011
    type: EXPLICIT_DATES
    dates: [2011-01-03, 2011-02-02, 2011-02-03, 2011-02-04, 2011-02-07, 2011-02-08, 2011-04-04,
            2011-04-05, 2011-05-02, 2011-06-06, 2011-09-12, 2011-10-03, 2011-10-04, 2011-10-05,
            2011-10-06, 2011-10-07]
  - name: SseSchedule2012
    type: EXPLICIT_DATES
    dates: [2012-01-02, 2012-01-03, 2012-01-23, 2012-01-24, 2012-01-25, 2012-01-26, 2012-01-27,
            2012-04-02, 2012-04-03, 2012-04-04, 2012-04-30, 2012-05-01, 2012-06-22, 2012-10-01,
            2012-10-02, 2012-10-03, 2012-10-04, 2012-10-05]
  - name: SseSchedule2013
    type: EXPLICIT_DATES
    dates: [2013-01-01, 2013-01-02, 2013-01-03, 2013-02-11, 2013-02-12, 2013-02-13, 2013-02-14,
            2013-02-15, 2013-04-04, 2013-04-05, 2013-04-29, 2013-04-30, 2013-05-01, 2013-06-10,
            2013-06-11, 2013-06-12, 2013-09-19, 2013-09-20, 2013-10-01, 2013-10-02, 2013-10-03,
            2013-10-04, 2013-10-07]
  - name: SseSchedule2014
    type: EXPLICIT_DATES
    dates: [2014-01-01, 2014-01-31, 2014-02-03, 2014-02-04, 2014-02-05, 2014-02-06, 2014-04-07,
            2014-05-01, 2014-05-02, 2014-06-02, 2014-09-08, 2014-10-01, 2014-10-02, 2014-10-03,
            2014-10-06, 2014-10-07]
  - name: SseSchedule2015
    type: EXPLICIT_DATES
    dates: [2015-01-01, 2015-01-02, 2015-02-18, 2015-02-19, 2015-02-20, 2015-02-23, 2015-02-24,
            2015-04-06, 2015-05-01, 2015-06-22, 2015-09-03, 2015-09-04, 2015-10-01, 2015-10-02,
            2015-10-05, 2015-10-06, 2015-10-07]
  - name: SseSchedule2016
    type: EXPLICIT_DATES
    dates: [2016-01-01, 2016-02-08, 2016-02-09, 2016-02-10, 2016-02-11, 2016-02-12, 2016-04-04,
            2016-05-02, 2016-06-09, 2016-06-10, 2016-09-15, 2016-09-16, 2016-10-03, 2016-10-04,
            2016-10-05, 2016-10-06, 2016-10-07]
  - name: SseSchedule2017
    type: EXPLICIT_DATES
    dates: [2017-01-02, 2017-01-27, 2017-01-30, 2017-01-31, 2017-02-01, 2017-02-02, 2017-04-03,
            2017-04-04, 2017-05-01, 2017-05-29, 2017-05-30, 2017-10-02, 2017-10-03, 2017-10-04,
            2017-10-05, 2017-10-06]
  - name: SseSchedule2018
    type: EXPLICIT_DATES
    dates: [2018-01-01, 2018-02-15, 2018-02-16, 2018-02-19, 2018-02-20, 2018-02-21, 2018-04-05,
            2018-04-06, 2018-04-30, 2018-05-01, 2018-06-18, 2018-09-24, 2018-10-01, 2018-10-02,
            2018-10-03, 2018-10-04, 2018-10-05, 2018-12-31]
  - name: SseSchedule2019
    type: EXPLICIT_DATES
    dates: [2019-01-01, 2019-02-04, 2019-02-05, 2019-02-06, 2019-02-07, 2019-02-08, 2019-04-05,
            2019-05-01, 2019-05-02, 2019-05-03, 2019-06-07, 2019-09-13, 2019-10-01, 2019-10-02,
            2019-10-03, 2019-10-04, 2019-10-07]
  - name: SseSchedule2020
    type: EXPLICIT_DATES
    dates: [2020-01-01, 2020-01-24, 2020-01-27, 2020-01-28, 2020-01-29, 2020-01-30, 2020-01-31,
            2020-04-06, 2020-05-01, 2020-05-04, 2020-05-05, 2020-06-25, 2020-06-26, 2020-10-01,
            2020-10-02, 2020-10-05, 2020-10-06, 2020-10-07, 2020-10-08]
  - name: SseSchedule2021
    type: EXPLICIT_DATES
    dates: [2021-01-01, 2021-02-11, 2021-02-12, 2021-02-15, 2021-02-16, 2021-02-17, 2021-04-05,
            2021-05-03, 2021-05-04, 2021-05-05, 2021-06-14, 2021-09-20, 2021-09-21, 2021-10-01,
            2021-10-04, 2021-10-05, 2021-10-06, 2021-10-07]
  - name: SseSchedule2022
    type: EXPLICIT_DATES
    dates: [2022-01-03, 2022-01-31, 2022-02-01, 2022-02-02, 2022-02-03, 2022-02-04, 2022-04-04,
            2022-04-05, 2022-05-02, 2022-05-03, 2022-05-04, 2022-06-03, 2022-09-12, 2022-10-03,
            2022-10-04, 2022-10-05, 2022-10-06, 2022-10-07]
  - name: SseSchedule2023
    type: EXPLICIT_DATES
    dates: [2023-01-02, 2023-01-23, 2023-01-24, 2023-01-25, 2023-01-26, 2023-01-27, 2023-04-05,
            2023-05-01, 2023-05-02, 2023-05-03, 2023-06-22, 2023-06-23, 2023-09-29, 2023-10-02,
            2023-10-03, 2023-10-04, 2023-10-05, 2023-10-06]
  - name: SseSchedule2024
    type: EXPLICIT_DATES
    dates: [2024-01-01, 2024-02-09, 2024-02-12, 2024-02-13, 2024-02-14, 2024-02-15, 2024-02-16,
            2024-04-04, 2024-04-05, 2024-05-01, 2024-05-02, 2024-05-03, 2024-06-10, 2024-09-16,
            2024-09-17, 2024-10-01, 2024-10-02, 2024-10-03, 2024-10-04, 2024-10-07]
  - name: SseSchedule2025
    type: EXPLICIT_DATES
    dates: [2025-01-01, 2025-01-28, 2025-01-29, 2025-01-30, 2025-01-31, 2025-02-03, 2025-02-04,
            2025-04-04, 2025-05-01, 2025-05-02, 2025-05-05, 2025-06-02, 2025-10-01, 2025-10-02,
            2025-10-03, 2025-10-06, 2025-10-07, 2025-10-08]
  - name: SseSchedule2026
    type: EXPLICIT_DATES
    dates: [2026-01-01, 2026-01-02, 2026-02-16, 2026-02-17, 2026-02-18, 2026-02-19, 2026-02-20,
            2026-02-23, 2026-04-06, 2026-05-01, 2026-05-04, 2026-05-05, 2026-06-19, 2026-09-25,
            2026-10-01, 2026-10-02, 2026-10-05, 2026-10-06, 2026-10-07]', 1, NOW(), 1, NOW(), 0),
  ('XSTO', 'Nasdaq Stockholm', 'note: >
  The Nordic exchanges close on Maundy Thursday, which the continental European venues trade through. Midsummer Eve is
  the Friday between 19 and 25 June, expressed as the last Friday before 26 June; the exchange is closed that day.
  Swedish holidays are not transferred when they fall on a weekend. Since 2005 the Swedish national day on 6 June is a
  public holiday and an exchange closure, replacing Whit Monday which was dropped in the same reform.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: Epiphany, type: FIXED, month: 1, day: 6}
  - {name: MaundyThursday, type: EASTER_RELATIVE, offset: -3}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: Ascension, type: EASTER_RELATIVE, offset: 39}
  - {name: WhitMonday, type: EASTER_RELATIVE, offset: 50, validTo: 2004}
  - {name: NationalDay, type: FIXED, month: 6, day: 6, validFrom: 2005}
  - {name: MidsummerEve, type: EXPLICIT_DATES, dates: [
      2000-06-23, 2001-06-22, 2002-06-21, 2003-06-20, 2004-06-25, 2005-06-24, 2006-06-23, 2007-06-22,
      2008-06-20, 2009-06-19, 2010-06-25, 2011-06-24, 2012-06-22, 2013-06-21, 2014-06-20, 2015-06-19,
      2016-06-24, 2017-06-23, 2018-06-22, 2019-06-21, 2020-06-19, 2021-06-25, 2022-06-24, 2023-06-23,
      2024-06-21, 2025-06-20, 2026-06-19, 2027-06-25, 2028-06-23]}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
  - {name: StStephen, type: FIXED, month: 12, day: 26}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31}
additionalClosures:
  - 2029-06-22
  - 2030-06-21
openDates:
  - 2000-04-20
  - 2001-04-12
  - 2002-03-28
  - 2003-04-17
  - 2004-04-08
  - 2005-03-24
  - 2006-04-13
  - 2007-04-05
  - 2008-03-20
  - 2009-04-09
  - 2010-04-01
  - 2011-04-21
  - 2012-04-05
  - 2013-03-28
  - 2014-04-17
  - 2015-04-02
  - 2016-03-24
  - 2017-04-13
  - 2018-03-29
  - 2019-04-18
  - 2020-04-09
  - 2021-04-01
  - 2022-04-14
  - 2023-04-06
  - 2024-03-28
  - 2025-04-17
  - 2026-04-02
  - 2027-03-25
  - 2028-04-13
  - 2029-03-29
  - 2030-04-18', 1, NOW(), 1, NOW(), 0),
  ('XSTU', 'Stuttgart Stock Exchange', 'note: >
  German regional exchange following the Xetra trading calendar. Regional venues have historically kept slightly
  longer hours than Xetra but close on the same days; any divergence the comparison report surfaces belongs here as
  an explicit deviation rule.', 1, NOW(), 1, NOW(), 0),
  ('XSWX', 'SIX Swiss Exchange', 'note: >
  Swiss holidays are not transferred when they fall on a weekend, so every rule uses observance NONE. SIX closes on the
  two half-days around Christmas and New Year entirely rather than shortening them. Cantonal holidays that are not
  federal -- Fronleichnam, Mariae Himmelfahrt, Allerheiligen -- are not exchange closures and are deliberately absent.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: Berchtoldstag, type: FIXED, month: 1, day: 2}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: Ascension, type: EASTER_RELATIVE, offset: 39}
  - {name: WhitMonday, type: EASTER_RELATIVE, offset: 50}
  - {name: NationalDay, type: FIXED, month: 8, day: 1}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
  - {name: StStephen, type: FIXED, month: 12, day: 26}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31}', 1, NOW(), 1, NOW(), 0),
  ('XTAE', 'Tel Aviv Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  # TASE used a Sunday-Thursday week through 2025 and introduced Friday sessions in 2026.
  - {name: WeeklyFriday, type: WEEKLY, dayOfWeek: FRIDAY, validTo: 2025}
additionalClosures:
  - 2000-03-21
  - 2000-04-19
  - 2000-04-20
  - 2000-04-25
  - 2000-04-26
  - 2000-05-09
  - 2000-05-10
  - 2000-06-08
  - 2000-08-10
  - 2000-10-09
  - 2001-04-25
  - 2001-04-26
  - 2001-05-28
  - 2001-09-17
  - 2001-09-18
  - 2001-09-19
  - 2001-09-26
  - 2001-09-27
  - 2001-10-01
  - 2001-10-02
  - 2001-10-08
  - 2001-10-09
  - 2002-02-26
  - 2002-03-27
  - 2002-03-28
  - 2002-04-02
  - 2002-04-03
  - 2002-04-16
  - 2002-04-17
  - 2002-05-16
  - 2002-07-18
  - 2002-09-16
  - 2003-03-18
  - 2003-04-16
  - 2003-04-17
  - 2003-04-22
  - 2003-04-23
  - 2003-05-06
  - 2003-05-07
  - 2003-06-05
  - 2003-08-07
  - 2003-10-06
  - 2004-04-05
  - 2004-04-06
  - 2004-04-12
  - 2004-04-26
  - 2004-04-27
  - 2004-05-25
  - 2004-05-26
  - 2004-07-27
  - 2004-09-15
  - 2004-09-16
  - 2004-09-29
  - 2004-09-30
  - 2004-10-06
  - 2004-10-07
  - 2005-05-11
  - 2005-05-12
  - 2005-06-13
  - 2005-10-03
  - 2005-10-04
  - 2005-10-05
  - 2005-10-12
  - 2005-10-13
  - 2005-10-17
  - 2005-10-18
  - 2005-10-24
  - 2005-10-25
  - 2006-03-14
  - 2006-04-12
  - 2006-04-13
  - 2006-04-18
  - 2006-04-19
  - 2006-05-02
  - 2006-05-03
  - 2006-06-01
  - 2006-08-03
  - 2006-10-02
  - 2007-04-02
  - 2007-04-03
  - 2007-04-09
  - 2007-04-23
  - 2007-04-24
  - 2007-05-22
  - 2007-05-23
  - 2007-07-24
  - 2007-09-12
  - 2007-09-13
  - 2007-09-26
  - 2007-09-27
  - 2007-10-03
  - 2007-10-04
  - 2008-05-07
  - 2008-05-08
  - 2008-06-09
  - 2008-09-29
  - 2008-09-30
  - 2008-10-01
  - 2008-10-08
  - 2008-10-09
  - 2008-10-13
  - 2008-10-14
  - 2008-10-20
  - 2008-10-21
  - 2009-03-10
  - 2009-04-08
  - 2009-04-09
  - 2009-04-14
  - 2009-04-15
  - 2009-04-28
  - 2009-04-29
  - 2009-05-28
  - 2009-07-30
  - 2009-09-28
  - 2010-03-29
  - 2010-03-30
  - 2010-04-05
  - 2010-04-19
  - 2010-04-20
  - 2010-05-18
  - 2010-05-19
  - 2010-07-20
  - 2010-09-08
  - 2010-09-09
  - 2010-09-22
  - 2010-09-23
  - 2010-09-29
  - 2010-09-30
  - 2011-04-18
  - 2011-04-19
  - 2011-04-25
  - 2011-05-09
  - 2011-05-10
  - 2011-06-07
  - 2011-06-08
  - 2011-08-09
  - 2011-09-28
  - 2011-09-29
  - 2011-10-12
  - 2011-10-13
  - 2011-10-19
  - 2011-10-20
  - 2012-03-08
  - 2012-04-12
  - 2012-04-25
  - 2012-04-26
  - 2012-09-17
  - 2012-09-18
  - 2012-09-25
  - 2012-09-26
  - 2012-10-01
  - 2012-10-08
  - 2013-03-25
  - 2013-03-26
  - 2013-04-01
  - 2013-04-15
  - 2013-04-16
  - 2013-05-14
  - 2013-05-15
  - 2013-07-16
  - 2013-09-04
  - 2013-09-05
  - 2013-09-18
  - 2013-09-19
  - 2013-09-25
  - 2013-09-26
  - 2014-04-14
  - 2014-04-15
  - 2014-04-21
  - 2014-05-05
  - 2014-05-06
  - 2014-06-03
  - 2014-06-04
  - 2014-08-05
  - 2014-09-24
  - 2014-09-25
  - 2014-10-08
  - 2014-10-09
  - 2014-10-15
  - 2014-10-16
  - 2015-03-05
  - 2015-04-09
  - 2015-04-22
  - 2015-04-23
  - 2015-09-14
  - 2015-09-15
  - 2015-09-22
  - 2015-09-23
  - 2015-09-28
  - 2015-10-05
  - 2016-03-24
  - 2016-04-28
  - 2016-05-11
  - 2016-05-12
  - 2016-10-03
  - 2016-10-04
  - 2016-10-11
  - 2016-10-12
  - 2016-10-17
  - 2016-10-24
  - 2017-04-10
  - 2017-04-11
  - 2017-04-17
  - 2017-05-01
  - 2017-05-02
  - 2017-05-30
  - 2017-05-31
  - 2017-08-01
  - 2017-09-20
  - 2017-09-21
  - 2017-10-04
  - 2017-10-05
  - 2017-10-11
  - 2017-10-12
  - 2018-03-01
  - 2018-04-05
  - 2018-04-18
  - 2018-04-19
  - 2018-09-10
  - 2018-09-11
  - 2018-09-18
  - 2018-09-19
  - 2018-09-24
  - 2018-10-01
  - 2019-03-21
  - 2019-04-09
  - 2019-04-25
  - 2019-05-08
  - 2019-05-09
  - 2019-09-17
  - 2019-09-30
  - 2019-10-01
  - 2019-10-08
  - 2019-10-09
  - 2019-10-14
  - 2019-10-21
  - 2020-03-02
  - 2020-03-10
  - 2020-04-08
  - 2020-04-09
  - 2020-04-14
  - 2020-04-15
  - 2020-04-28
  - 2020-04-29
  - 2020-05-28
  - 2020-07-30
  - 2020-09-28
  - 2021-03-23
  - 2021-04-14
  - 2021-04-15
  - 2021-05-17
  - 2021-09-06
  - 2021-09-07
  - 2021-09-08
  - 2021-09-15
  - 2021-09-16
  - 2021-09-20
  - 2021-09-21
  - 2021-09-27
  - 2021-09-28
  - 2022-03-17
  - 2022-04-21
  - 2022-05-04
  - 2022-05-05
  - 2022-09-26
  - 2022-09-27
  - 2022-10-04
  - 2022-10-05
  - 2022-10-10
  - 2022-10-17
  - 2022-11-01
  - 2023-03-07
  - 2023-04-05
  - 2023-04-06
  - 2023-04-11
  - 2023-04-12
  - 2023-04-25
  - 2023-04-26
  - 2023-05-25
  - 2023-07-27
  - 2023-09-25
  - 2024-04-22
  - 2024-04-23
  - 2024-04-29
  - 2024-05-13
  - 2024-05-14
  - 2024-06-11
  - 2024-06-12
  - 2024-08-13
  - 2024-10-02
  - 2024-10-03
  - 2024-10-16
  - 2024-10-17
  - 2024-10-23
  - 2024-10-24
  - 2025-04-30
  - 2025-05-01
  - 2025-06-02
  - 2025-09-22
  - 2025-09-23
  - 2025-09-24
  - 2025-10-01
  - 2025-10-02
  - 2025-10-06
  - 2025-10-07
  - 2025-10-13
  - 2025-10-14
  - 2026-01-02
  - 2026-03-03
  - 2026-04-01
  - 2026-04-02
  - 2026-04-07
  - 2026-04-08
  - 2026-04-21
  - 2026-04-22
  - 2026-05-21
  - 2026-05-22
  - 2026-07-23
  - 2026-09-11
  - 2026-09-18
  - 2026-09-21
  - 2026-09-25
  - 2026-10-02
  - 2027-03-23
  - 2027-04-21
  - 2027-04-22
  - 2027-04-27
  - 2027-04-28
  - 2027-05-11
  - 2027-05-12
  - 2027-06-10
  - 2027-06-11
  - 2027-08-12
  - 2027-10-01
  - 2027-10-08
  - 2027-10-11
  - 2027-10-15
  - 2027-10-22
  - 2028-04-10
  - 2028-04-11
  - 2028-04-17
  - 2028-05-01
  - 2028-05-02
  - 2028-05-30
  - 2028-05-31
  - 2028-08-01
  - 2028-09-20
  - 2028-09-21
  - 2028-09-22
  - 2028-09-29
  - 2028-10-04
  - 2028-10-05
  - 2028-10-11
  - 2028-10-12
  - 2029-03-01
  - 2029-03-30
  - 2029-04-05
  - 2029-04-06
  - 2029-04-18
  - 2029-04-19
  - 2029-09-10
  - 2029-09-11
  - 2029-09-18
  - 2029-09-19
  - 2029-09-24
  - 2029-10-01
  - 2030-03-19
  - 2030-04-17
  - 2030-04-18
  - 2030-04-23
  - 2030-04-24
  - 2030-05-07
  - 2030-05-08
  - 2030-06-06
  - 2030-06-07
  - 2030-08-08
  - 2030-09-27
  - 2030-10-04
  - 2030-10-07
  - 2030-10-11
  - 2030-10-18', 1, NOW(), 1, NOW(), 0),
  ('XTAI', 'Taiwan Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: Fixed0228None, type: FIXED, month: 2, day: 28}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed1010None, type: FIXED, month: 10, day: 10}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
additionalClosures:
  - 2000-02-03
  - 2000-02-04
  - 2000-02-07
  - 2000-02-08
  - 2000-02-09
  - 2000-04-04
  - 2000-06-06
  - 2000-09-12
  - 2001-01-22
  - 2001-01-23
  - 2001-01-24
  - 2001-01-25
  - 2001-01-26
  - 2001-04-05
  - 2001-06-25
  - 2001-10-01
  - 2002-02-07
  - 2002-02-08
  - 2002-02-11
  - 2002-02-12
  - 2002-02-13
  - 2002-02-14
  - 2002-02-15
  - 2002-04-05
  - 2002-09-06
  - 2003-01-29
  - 2003-01-30
  - 2003-01-31
  - 2003-02-03
  - 2003-02-04
  - 2003-02-05
  - 2003-06-04
  - 2003-09-11
  - 2004-01-19
  - 2004-01-20
  - 2004-01-21
  - 2004-01-22
  - 2004-01-23
  - 2004-01-26
  - 2004-06-22
  - 2004-08-24
  - 2004-08-25
  - 2004-09-28
  - 2004-10-25
  - 2005-02-04
  - 2005-02-07
  - 2005-02-08
  - 2005-02-09
  - 2005-02-10
  - 2005-02-11
  - 2005-04-05
  - 2005-07-18
  - 2005-08-05
  - 2005-09-01
  - 2006-01-26
  - 2006-01-27
  - 2006-01-30
  - 2006-01-31
  - 2006-02-01
  - 2006-02-02
  - 2006-04-05
  - 2006-05-31
  - 2006-10-06
  - 2006-10-09
  - 2007-02-15
  - 2007-02-16
  - 2007-02-19
  - 2007-02-20
  - 2007-02-21
  - 2007-02-22
  - 2007-02-23
  - 2007-04-05
  - 2007-04-06
  - 2007-06-18
  - 2007-06-19
  - 2007-09-18
  - 2007-09-24
  - 2007-09-25
  - 2008-02-04
  - 2008-02-05
  - 2008-02-06
  - 2008-02-07
  - 2008-02-08
  - 2008-02-11
  - 2008-04-04
  - 2008-07-28
  - 2008-09-29
  - 2009-01-02
  - 2009-01-22
  - 2009-01-23
  - 2009-01-26
  - 2009-01-27
  - 2009-01-28
  - 2009-01-29
  - 2009-01-30
  - 2009-05-28
  - 2009-05-29
  - 2009-08-07
  - 2010-02-11
  - 2010-02-12
  - 2010-02-15
  - 2010-02-16
  - 2010-02-17
  - 2010-02-18
  - 2010-02-19
  - 2010-04-05
  - 2010-06-16
  - 2010-09-22
  - 2011-01-31
  - 2011-02-01
  - 2011-02-02
  - 2011-02-03
  - 2011-02-04
  - 2011-02-07
  - 2011-04-04
  - 2011-04-05
  - 2011-06-06
  - 2011-09-12
  - 2012-01-19
  - 2012-01-20
  - 2012-01-23
  - 2012-01-24
  - 2012-01-25
  - 2012-01-26
  - 2012-01-27
  - 2012-02-27
  - 2012-04-04
  - 2012-08-02
  - 2012-12-31
  - 2013-02-07
  - 2013-02-08
  - 2013-02-11
  - 2013-02-12
  - 2013-02-13
  - 2013-02-14
  - 2013-02-15
  - 2013-04-04
  - 2013-04-05
  - 2013-06-12
  - 2013-08-21
  - 2013-09-19
  - 2013-09-20
  - 2014-01-28
  - 2014-01-29
  - 2014-01-30
  - 2014-01-31
  - 2014-02-03
  - 2014-02-04
  - 2014-04-04
  - 2014-06-02
  - 2014-07-23
  - 2014-09-08
  - 2015-01-02
  - 2015-02-16
  - 2015-02-17
  - 2015-02-18
  - 2015-02-19
  - 2015-02-20
  - 2015-02-23
  - 2015-02-27
  - 2015-04-03
  - 2015-04-06
  - 2015-06-19
  - 2015-07-10
  - 2015-09-28
  - 2015-09-29
  - 2015-10-09
  - 2016-02-04
  - 2016-02-05
  - 2016-02-08
  - 2016-02-09
  - 2016-02-10
  - 2016-02-11
  - 2016-02-12
  - 2016-02-29
  - 2016-04-04
  - 2016-04-05
  - 2016-05-02
  - 2016-06-09
  - 2016-06-10
  - 2016-07-08
  - 2016-09-15
  - 2016-09-16
  - 2016-09-27
  - 2016-09-28
  - 2017-01-02
  - 2017-01-25
  - 2017-01-26
  - 2017-01-27
  - 2017-01-30
  - 2017-01-31
  - 2017-02-01
  - 2017-02-27
  - 2017-04-03
  - 2017-04-04
  - 2017-05-29
  - 2017-05-30
  - 2017-10-04
  - 2017-10-09
  - 2018-02-13
  - 2018-02-14
  - 2018-02-15
  - 2018-02-16
  - 2018-02-19
  - 2018-02-20
  - 2018-04-04
  - 2018-04-05
  - 2018-04-06
  - 2018-06-18
  - 2018-09-24
  - 2018-12-31
  - 2019-01-31
  - 2019-02-01
  - 2019-02-04
  - 2019-02-05
  - 2019-02-06
  - 2019-02-07
  - 2019-02-08
  - 2019-03-01
  - 2019-04-04
  - 2019-04-05
  - 2019-06-07
  - 2019-08-09
  - 2019-09-13
  - 2019-09-30
  - 2019-10-11
  - 2020-01-21
  - 2020-01-22
  - 2020-01-23
  - 2020-01-24
  - 2020-01-27
  - 2020-01-28
  - 2020-01-29
  - 2020-04-02
  - 2020-04-03
  - 2020-06-25
  - 2020-06-26
  - 2020-10-01
  - 2020-10-02
  - 2020-10-09
  - 2021-02-08
  - 2021-02-09
  - 2021-02-10
  - 2021-02-11
  - 2021-02-12
  - 2021-02-15
  - 2021-02-16
  - 2021-03-01
  - 2021-04-02
  - 2021-04-05
  - 2021-04-30
  - 2021-06-14
  - 2021-09-20
  - 2021-09-21
  - 2021-10-11
  - 2021-12-31
  - 2022-01-27
  - 2022-01-28
  - 2022-01-31
  - 2022-02-01
  - 2022-02-02
  - 2022-02-03
  - 2022-04-04
  - 2022-04-05
  - 2022-05-02
  - 2022-06-03
  - 2022-09-09
  - 2023-01-02
  - 2023-01-19
  - 2023-01-20
  - 2023-01-23
  - 2023-01-24
  - 2023-01-25
  - 2023-01-26
  - 2023-01-27
  - 2023-02-27
  - 2023-04-03
  - 2023-04-04
  - 2023-04-05
  - 2023-06-22
  - 2023-06-23
  - 2023-08-03
  - 2023-09-29
  - 2023-10-09
  - 2024-02-06
  - 2024-02-07
  - 2024-02-08
  - 2024-02-09
  - 2024-02-12
  - 2024-02-13
  - 2024-02-14
  - 2024-04-04
  - 2024-04-05
  - 2024-06-10
  - 2024-07-24
  - 2024-07-25
  - 2024-09-17
  - 2024-10-02
  - 2024-10-03
  - 2025-01-23
  - 2025-01-24
  - 2025-01-27
  - 2025-01-28
  - 2025-01-29
  - 2025-01-30
  - 2025-01-31
  - 2025-04-03
  - 2025-04-04
  - 2025-05-30
  - 2025-09-29
  - 2025-10-06
  - 2025-10-24
  - 2025-12-25
  - 2026-02-12
  - 2026-02-13
  - 2026-02-16
  - 2026-02-17
  - 2026-02-18
  - 2026-02-19
  - 2026-02-20
  - 2026-02-27
  - 2026-04-03
  - 2026-04-06
  - 2026-06-19
  - 2026-09-25
  - 2026-09-28
  - 2026-10-09
  - 2026-10-26
  - 2026-12-25
  - 2027-02-04
  - 2027-02-05
  - 2027-02-08
  - 2027-02-09
  - 2027-02-10
  - 2027-03-01
  - 2027-04-05
  - 2027-04-30
  - 2027-06-09
  - 2027-09-15
  - 2027-09-28
  - 2027-10-11
  - 2027-10-25
  - 2027-12-31
  - 2028-01-24
  - 2028-01-25
  - 2028-01-26
  - 2028-01-27
  - 2028-01-28
  - 2028-04-03
  - 2028-04-04
  - 2028-05-29
  - 2028-09-28
  - 2028-10-03
  - 2028-10-25
  - 2028-12-25
  - 2029-02-09
  - 2029-02-12
  - 2029-02-13
  - 2029-02-14
  - 2029-02-15
  - 2029-04-03
  - 2029-04-04
  - 2029-06-15
  - 2029-09-21
  - 2029-09-28
  - 2029-10-25
  - 2029-12-25
  - 2029-12-31
  - 2030-01-31
  - 2030-02-01
  - 2030-02-04
  - 2030-02-05
  - 2030-02-06
  - 2030-04-04
  - 2030-04-05
  - 2030-06-05
  - 2030-09-12
  - 2030-09-27
  - 2030-10-25
  - 2030-12-25', 1, NOW(), 1, NOW(), 0),
  ('XTAL', 'Nasdaq Tallinn', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: EasterOffset39, type: EASTER_RELATIVE, offset: 39}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0623SundayToMonday, type: FIXED, month: 6, day: 23, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed0820None, type: FIXED, month: 8, day: 20}
  - {name: Fixed1224None, type: FIXED, month: 12, day: 24}
  - {name: Fixed0224None, type: FIXED, month: 2, day: 24}
  - {name: Fixed0624None, type: FIXED, month: 6, day: 24}
additionalClosures:
  - 2023-12-25', 1, NOW(), 1, NOW(), 0),
  ('XTKS', 'Tokyo Stock Exchange', 'note: >
  Japan is the most rule-friendly of the Asian exchanges. Most holidays are fixed dates or, under the Happy Monday
  system, the n-th Monday of a month; Coming of Age Day and Marine Day moved to a Monday in 2000 and 2003 respectively,
  and Respect for the Aged Day in 2003, which the validity ranges express.
  Two things cannot be calculated. The equinox days are astronomical and are announced each February by the National
  Astronomical Observatory, so they are enumerated. The substitute-holiday rule, under which a holiday falling on a
  Sunday moves to the next non-holiday day, is applied here as a plain NEXT_MONDAY, which is correct except where two
  holidays are adjacent.
  The 2019 imperial transition and the 2020 and 2021 Olympic transfers are one-offs.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1, observance: NEXT_MONDAY}
  - {name: NewYearHolidayJan2, type: FIXED, month: 1, day: 2}
  - {name: NewYearHolidayJan3, type: FIXED, month: 1, day: 3}
  - {name: ComingOfAgeDay, type: FIXED, month: 1, day: 15, validTo: 1999, observance: NEXT_MONDAY}
  - {name: ComingOfAgeDayMonday, type: NTH_WEEKDAY, month: 1, nth: 2, dayOfWeek: MONDAY, validFrom: 2000}
  - {name: NationalFoundationDay, type: FIXED, month: 2, day: 11, observance: NEXT_MONDAY}
  - {name: EmperorsBirthdayFebruary, type: FIXED, month: 2, day: 23, validFrom: 2020, observance: NEXT_MONDAY}
  - {name: ShowaDay, type: FIXED, month: 4, day: 29, observance: NEXT_MONDAY}
  - {name: ConstitutionDay, type: FIXED, month: 5, day: 3, observance: NEXT_MONDAY}
  - {name: GreeneryDay, type: FIXED, month: 5, day: 4, validFrom: 2007, observance: NEXT_MONDAY}
  - {name: ChildrensDay, type: FIXED, month: 5, day: 5, observance: NEXT_MONDAY}
  - {name: MarineDay, type: FIXED, month: 7, day: 20, validTo: 2002, observance: NEXT_MONDAY}
  - {name: MarineDayMonday, type: NTH_WEEKDAY, month: 7, nth: 3, dayOfWeek: MONDAY, validFrom: 2003}
  - {name: MountainDay, type: FIXED, month: 8, day: 11, validFrom: 2016, observance: NEXT_MONDAY}
  - {name: RespectForTheAgedDay, type: FIXED, month: 9, day: 15, validTo: 2002, observance: NEXT_MONDAY}
  - {name: RespectForTheAgedDayMonday, type: NTH_WEEKDAY, month: 9, nth: 3, dayOfWeek: MONDAY, validFrom: 2003}
  - {name: HealthAndSportsDay, type: NTH_WEEKDAY, month: 10, nth: 2, dayOfWeek: MONDAY, validTo: 2019}
  - {name: SportsDay, type: NTH_WEEKDAY, month: 10, nth: 2, dayOfWeek: MONDAY, validFrom: 2022}
  - {name: CultureDay, type: FIXED, month: 11, day: 3, observance: NEXT_MONDAY}
  - {name: LabourThanksgivingDay, type: FIXED, month: 11, day: 23, observance: NEXT_MONDAY}
  - {name: EmperorsBirthdayDecember, type: FIXED, month: 12, day: 23, validTo: 2018, observance: NEXT_MONDAY}
  - {name: YearEndHolidayDec31, type: FIXED, month: 12, day: 31}

  - name: VernalEquinox
    type: EXPLICIT_DATES
    dates: [2000-03-20, 2001-03-20, 2002-03-21, 2003-03-21, 2004-03-20, 2005-03-20, 2006-03-21,
            2007-03-21, 2008-03-20, 2009-03-20, 2010-03-22, 2011-03-21, 2012-03-20, 2013-03-20,
            2014-03-21, 2015-03-21, 2016-03-21, 2017-03-20, 2018-03-21, 2019-03-21, 2020-03-20,
            2021-03-20, 2022-03-21, 2023-03-21, 2024-03-20, 2025-03-20, 2026-03-20, 2027-03-22, 2028-03-20]
  - name: AutumnalEquinox
    type: EXPLICIT_DATES
    dates: [2000-09-23, 2001-09-24, 2002-09-23, 2003-09-23, 2004-09-23, 2005-09-23, 2006-09-23,
            2007-09-24, 2008-09-23, 2009-09-23, 2010-09-23, 2011-09-23, 2012-09-22, 2013-09-23,
            2014-09-23, 2015-09-23, 2016-09-22, 2017-09-23, 2018-09-24, 2019-09-23, 2020-09-22,
            2021-09-23, 2022-09-23, 2023-09-23, 2024-09-23, 2025-09-23, 2026-09-23, 2027-09-23, 2028-09-22]

  # Imperial transition in 2019 and the holiday transfers made for the Tokyo Olympics.
  - name: ImperialAndOlympicTransfers
    type: EXPLICIT_DATES
    dates: [2019-04-30, 2019-05-01, 2019-05-02, 2019-10-22,
            2020-07-23, 2020-07-24, 2020-08-10,
            2021-07-22, 2021-07-23, 2021-08-09]
additionalClosures:
  - 2000-05-04
  - 2001-05-04
  - 2004-05-04
  - 2005-03-21
  - 2005-05-04
  - 2006-05-04
  - 2008-05-06
  - 2009-05-06
  - 2009-09-22
  - 2014-05-06
  - 2015-05-06
  - 2015-09-22
  - 2020-05-06
  - 2020-10-01
  - 2025-05-06
  - 2026-05-06
  - 2026-09-22
  - 2029-03-20
  - 2029-09-24
  - 2030-03-20
  - 2030-09-23
openDates:
  - 2000-05-01
  - 2000-12-25
  - 2001-05-07
  - 2001-09-17
  - 2001-11-05
  - 2002-07-22
  - 2002-11-25
  - 2006-02-13
  - 2006-05-01
  - 2006-12-25
  - 2007-05-07
  - 2007-11-05
  - 2012-02-13
  - 2012-05-07
  - 2012-11-05
  - 2013-11-25
  - 2017-02-13
  - 2017-05-01
  - 2017-12-25
  - 2018-05-07
  - 2018-08-13
  - 2018-11-05
  - 2019-11-25
  - 2020-07-20
  - 2020-08-11
  - 2021-07-19
  - 2021-08-11
  - 2023-02-13
  - 2023-05-01
  - 2024-11-25
  - 2028-05-01
  - 2029-05-07
  - 2029-08-13
  - 2029-11-05
  - 2030-02-25
  - 2030-11-25', 1, NOW(), 1, NOW(), 0),
  ('XTSX', 'Toronto Stock Exchange', 'note: >
  The Toronto exchange follows the Ontario statutory holidays. Family Day was introduced in Ontario in 2008 and falls
  on the third Monday of February. Victoria Day is the Monday before 25 May, which is the last Monday on or before
  24 May and cannot be expressed as an nth-weekday rule, so it is enumerated. The National Day for Truth and
  Reconciliation created in 2021 is a federal holiday on which the exchange nevertheless trades.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1, observance: NEXT_MONDAY}
  - {name: FamilyDay, type: NTH_WEEKDAY, month: 2, nth: 3, dayOfWeek: MONDAY, validFrom: 2008}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: VictoriaDay, type: EXPLICIT_DATES, dates: [
      2000-05-22, 2001-05-21, 2002-05-20, 2003-05-19, 2004-05-24, 2005-05-23, 2006-05-22, 2007-05-21,
      2008-05-19, 2009-05-18, 2010-05-24, 2011-05-23, 2012-05-21, 2013-05-20, 2014-05-19, 2015-05-18,
      2016-05-23, 2017-05-22, 2018-05-21, 2019-05-20, 2020-05-18, 2021-05-24, 2022-05-23, 2023-05-22,
      2024-05-20, 2025-05-19, 2026-05-18, 2027-05-24, 2028-05-22]}
  - {name: CanadaDay, type: FIXED, month: 7, day: 1, observance: NEXT_MONDAY}
  - {name: CivicHoliday, type: NTH_WEEKDAY, month: 8, nth: 1, dayOfWeek: MONDAY}
  - {name: LabourDay, type: NTH_WEEKDAY, month: 9, nth: 1, dayOfWeek: MONDAY}
  - {name: Thanksgiving, type: NTH_WEEKDAY, month: 10, nth: 2, dayOfWeek: MONDAY}
  - {name: Christmas, type: FIXED, month: 12, day: 25, observance: NEXT_MONDAY}
  - {name: BoxingDay, type: FIXED, month: 12, day: 26, observance: NEXT_MONDAY}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24, halfDay: true}
additionalClosures:
  - 2001-09-11
  - 2001-09-12
  - 2004-12-28
  - 2005-12-27
  - 2010-12-28
  - 2011-12-27
  - 2016-12-27
  - 2021-12-28
  - 2022-12-27
  - 2027-12-28
  - 2029-05-21
  - 2030-05-20', 1, NOW(), 1, NOW(), 0),
  ('XVIE', 'Wiener Boerse', 'note: >
  The Vienna exchange closes on the Austrian public holidays including the religious ones that Xetra trades through --
  Corpus Christi, Assumption, All Saints and the Immaculate Conception. Austrian holidays are not transferred when they
  fall on a weekend.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: Epiphany, type: FIXED, month: 1, day: 6}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: Ascension, type: EASTER_RELATIVE, offset: 39}
  - {name: WhitMonday, type: EASTER_RELATIVE, offset: 50}
  - {name: CorpusChristi, type: EASTER_RELATIVE, offset: 60}
  - {name: Assumption, type: FIXED, month: 8, day: 15}
  - {name: NationalDay, type: FIXED, month: 10, day: 26}
  - {name: AllSaints, type: FIXED, month: 11, day: 1}
  - {name: ImmaculateConception, type: FIXED, month: 12, day: 8}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
  - {name: StStephen, type: FIXED, month: 12, day: 26}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31}
additionalClosures:
  - 2000-12-29
  - 2005-12-30
  - 2006-12-29
  - 2011-12-30
openDates:
  - 2019-05-30
  - 2019-06-20
  - 2019-08-15
  - 2019-11-01
  - 2020-01-06
  - 2020-05-21
  - 2020-06-11
  - 2020-12-08
  - 2021-01-06
  - 2021-05-13
  - 2021-06-03
  - 2021-11-01
  - 2021-12-08
  - 2022-01-06
  - 2022-05-26
  - 2022-06-16
  - 2022-08-15
  - 2022-11-01
  - 2022-12-08
  - 2023-01-06
  - 2023-05-18
  - 2023-05-29
  - 2023-06-08
  - 2023-08-15
  - 2023-11-01
  - 2023-12-08
  - 2024-05-09
  - 2024-05-20
  - 2024-05-30
  - 2024-08-15
  - 2024-11-01
  - 2025-01-06
  - 2025-05-29
  - 2025-06-09
  - 2025-06-19
  - 2025-08-15
  - 2025-12-08
  - 2026-01-06
  - 2026-05-14
  - 2026-05-25
  - 2026-06-04
  - 2026-12-08
  - 2027-01-06
  - 2027-05-06
  - 2027-05-17
  - 2027-05-27
  - 2027-11-01
  - 2027-12-08
  - 2028-01-06
  - 2028-05-25
  - 2028-06-05
  - 2028-06-15
  - 2028-08-15
  - 2028-11-01
  - 2028-12-08
  - 2029-05-10
  - 2029-05-21
  - 2029-05-31
  - 2029-08-15
  - 2029-11-01
  - 2030-05-30
  - 2030-06-10
  - 2030-06-20
  - 2030-08-15
  - 2030-11-01', 1, NOW(), 1, NOW(), 0),
  ('XWAR', 'Gielda Papierow Wartosciowych Warsaw', 'note: >
  Warsaw closes on the Polish national and religious holidays including Corpus Christi. Epiphany was restored as a
  public holiday in 2011 after being abolished in 1960, and the exchange has been closed on it since. Polish holidays
  are not transferred when they fall on a weekend.
rules:
  - {name: NewYear, type: FIXED, month: 1, day: 1}
  - {name: Epiphany, type: FIXED, month: 1, day: 6, validFrom: 2011}
  - {name: GoodFriday, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterMonday, type: EASTER_RELATIVE, offset: 1}
  - {name: LabourDay, type: FIXED, month: 5, day: 1}
  - {name: ConstitutionDay, type: FIXED, month: 5, day: 3}
  - {name: CorpusChristi, type: EASTER_RELATIVE, offset: 60}
  - {name: Assumption, type: FIXED, month: 8, day: 15}
  - {name: AllSaints, type: FIXED, month: 11, day: 1}
  - {name: IndependenceDay, type: FIXED, month: 11, day: 11}
  - {name: ChristmasEve, type: FIXED, month: 12, day: 24}
  - {name: Christmas, type: FIXED, month: 12, day: 25}
  - {name: StStephen, type: FIXED, month: 12, day: 26}
  - {name: NewYearsEve, type: FIXED, month: 12, day: 31}
additionalClosures:
  - 2005-04-08
  - 2008-05-02
  - 2009-01-02
  - 2013-04-16
  - 2018-01-02
  - 2018-11-12
openDates:
  - 2001-12-31
  - 2002-12-31
  - 2003-12-31
  - 2004-12-24
  - 2004-12-31
  - 2008-12-31
  - 2009-12-31
  - 2010-12-31', 1, NOW(), 1, NOW(), 0),
  ('XZAG', 'Zagreb Stock Exchange', 'authoritativeFrom: 2000
authoritativeThrough: 2030
note: >
  Predictable closure patterns inferred from the exchange_calendars schedule. Historical, lunar and announced
  exceptions are listed below as additionalClosures and openDates.
rules:
  - {name: EasterOffset-2, type: EASTER_RELATIVE, offset: -2}
  - {name: EasterOffset1, type: EASTER_RELATIVE, offset: 1}
  - {name: Fixed1224NextMonday, type: FIXED, month: 12, day: 24, observance: NEXT_MONDAY}
  - {name: Fixed1225NearestWeekday, type: FIXED, month: 12, day: 25, observance: NEAREST_WEEKDAY}
  - {name: Fixed1231SundayToMonday, type: FIXED, month: 12, day: 31, observance: SUNDAY_TO_MONDAY}
  - {name: Fixed0501None, type: FIXED, month: 5, day: 1}
  - {name: Fixed0815None, type: FIXED, month: 8, day: 15}
  - {name: Fixed1101None, type: FIXED, month: 11, day: 1}
  - {name: Fixed1226None, type: FIXED, month: 12, day: 26}
  - {name: Fixed0101None, type: FIXED, month: 1, day: 1}
  - {name: Fixed0622None, type: FIXED, month: 6, day: 22}
  - {name: Fixed0106None, type: FIXED, month: 1, day: 6}
  - {name: Fixed0805None, type: FIXED, month: 8, day: 5}
additionalClosures:
  - 2000-05-30
  - 2001-05-30
  - 2002-05-30
  - 2002-06-25
  - 2002-10-08
  - 2003-06-19
  - 2003-06-25
  - 2003-10-08
  - 2004-06-10
  - 2004-06-25
  - 2004-10-08
  - 2005-05-26
  - 2006-06-15
  - 2007-06-07
  - 2007-06-25
  - 2007-10-08
  - 2008-05-22
  - 2008-06-25
  - 2008-10-08
  - 2009-06-11
  - 2009-06-25
  - 2009-10-08
  - 2010-06-03
  - 2010-06-25
  - 2010-10-08
  - 2011-06-23
  - 2012-06-07
  - 2012-06-25
  - 2012-10-08
  - 2013-05-30
  - 2013-06-25
  - 2013-10-08
  - 2014-06-19
  - 2014-06-25
  - 2014-10-08
  - 2015-06-04
  - 2015-06-25
  - 2015-10-08
  - 2016-05-26
  - 2017-06-15
  - 2018-05-31
  - 2018-06-25
  - 2018-10-08
  - 2019-06-20
  - 2019-06-25
  - 2019-10-08
  - 2020-06-11
  - 2020-11-18
  - 2021-06-03
  - 2021-11-18
  - 2022-05-30
  - 2022-06-16
  - 2022-11-18
  - 2022-12-29
  - 2022-12-30
  - 2023-05-30
  - 2023-06-08
  - 2024-04-17
  - 2024-05-30
  - 2024-11-18
  - 2025-05-30
  - 2025-06-19
  - 2025-11-18
  - 2026-06-04
  - 2026-11-18
  - 2027-05-27
  - 2027-11-18
  - 2028-05-30
  - 2028-06-15
  - 2029-05-30
  - 2029-05-31
  - 2030-05-30
  - 2030-06-20
  - 2030-11-18', 1, NOW(), 1, NOW(), 0),
  ('ZKBX', 'Zuercher Kantonalbank', 'note: >
  An issuer platform rather than a regulated exchange, but it trades on Swiss banking days and therefore follows the
  SIX calendar. GT has no reference index assigned to it, so its stored calendar consists only of rows left over from
  the original data seed and has not been updated since; the comparison report will show that as a near-total
  disagreement, which reflects the stale stored data rather than the rules.', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE rule_yaml = VALUES(rule_yaml), name = VALUES(name),
  last_modified_time = NOW();

-- A venue that follows another exchange's calendar keeps only its own deviations and inherits the
-- rules of the referenced set. Resolved by id after the insert, so the row order does not matter.
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XETR'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'HAMA';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XSWX'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'OTXB';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XPAR'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'XAMS';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XETR'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'XBER';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XSWX'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'XBRN';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XPAR'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'XBRU';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XETR'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'XFRA';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XPAR'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'XLIS';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XETR'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'XMUN';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XNYS'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'XNAS';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XSHG'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'XSHE';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XETR'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'XSTU';
UPDATE trading_calendar_rule_set c JOIN trading_calendar_rule_set p ON p.mic = 'XSWX'
  SET c.id_extends_rule_set = p.id_trading_calendar_rule_set WHERE c.mic = 'ZKBX';

-- Assign the rule set to the exchange carrying the same MIC and switch that exchange over to it. The index is
-- cleared because the two calendar sources are mutually exclusive; exchanges without a matching rule set keep
-- deriving their calendar from their index. max_calendar_upd_date is reset so the rebuild below starts at the
-- beginning of the period and replaces the old index-derived rows with rule-derived ones, rather than only
-- appending a tail.
UPDATE stockexchange se JOIN trading_calendar_rule_set t ON se.mic = t.mic
  SET se.id_trading_calendar_rule_set = t.id_trading_calendar_rule_set,
      se.id_index_upd_calendar = NULL,
      se.max_calendar_upd_date = NULL;

-- Enqueue a one-shot background job that generates trading_days_minus from the rule sets for every rule-based
-- exchange. id_task 53 = CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET, execution_priority 20 = PRIO_NORMAL,
-- progress_state 0 = PROG_WAITING; a null entity means "all rule-based exchanges". Guarded so re-running the
-- migration does not queue a second job.
INSERT INTO task_data_change (id_task, execution_priority, entity, id_entity, creation_time, earliest_start_time,
    progress_state)
SELECT 53, 20, NULL, NULL, NOW(), NOW(), 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM task_data_change
    WHERE id_task = 53 AND entity IS NULL AND progress_state = 0);

-- Daily limit of create/update/delete operations a user with restricted editing rights may perform on the rule sets.
DELETE FROM globalparameters WHERE property_name = 'gt.limit.day.TradingCalendarRuleSet';
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.limit.day.TradingCalendarRuleSet', 4, 0, 'min:1,max:50');
