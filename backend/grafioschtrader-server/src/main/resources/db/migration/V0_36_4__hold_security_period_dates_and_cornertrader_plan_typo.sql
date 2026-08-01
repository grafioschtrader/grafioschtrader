-- Hold periods of hold_securityaccount_security must be keyed on the business date tt_date, not on
-- transaction_time.
--
-- transaction_time is a TIMESTAMP: it is stored as a UTC instant and rendered in the time zone of the
-- database session. Serving the same database from a host in another zone therefore shifts every
-- transaction, and transactions booked in the first hour of a day move to the previous day. tt_date is
-- derived once when the row is written and never re-derived, which makes it the only stable date.
--
-- Both procedures now emit tsDate as TIMESTAMP(tt_date, TIME(transaction_time)): the date part comes
-- from tt_date and is what the holding periods are keyed on, the time part only orders several events
-- of the same day so that a split (midnight) is still applied before the transactions of that day.
-- The temporary tables hold DATETIME instead of TIMESTAMP for the same reason - a TIMESTAMP column
-- would convert the value through the session time zone once more.

DELIMITER //

CREATE OR REPLACE PROCEDURE holdSecuritySplitMarginTransaction(IN `idSecurity` INT)
    NO SQL
BEGIN
DROP TEMPORARY TABLE IF EXISTS holdSecurityMarginSplit;
CREATE TEMPORARY TABLE holdSecurityMarginSplit (tsDate DATETIME NOT NULL)
SELECT t.id_tenant as idTenant, p.id_portfolio AS idPortfolio, t.id_security_account AS idSecurityaccount, TIMESTAMP(t.tt_date, TIME(t.transaction_time)) AS tsDate, IF(t.transaction_type = 4, 1, -1) * t.units *
t.asset_investment_value_2 as factorUnits, te.currency AS tenantCurrency, p.currency AS porfolioCurrency,
t.id_transaction AS idTransactionMargin
FROM transaction t JOIN security s ON s.id_securitycurrency = t.id_securitycurrency JOIN tenant te ON te.id_tenant = t.id_tenant
JOIN securitycashaccount sca ON sca.id_securitycash_account = t.id_security_account  JOIN portfolio p ON p.id_portfolio = sca.id_portfolio
WHERE t.transaction_type >= 4 AND t.transaction_type <= 5
AND  t.id_securitycurrency = idSecurity
UNION
SELECT t.id_tenant as idTenant, null AS idPortfolio, t.id_security_account AS idSecurityaccount, ss.split_date AS tsDate, ss.to_factor / ss.from_factor as factorUnits, null AS tenantCurrency, null AS porfolioCurrency, null AS idTransactionMargin
FROM security s JOIN securitysplit ss ON s.id_securitycurrency = ss.id_securitycurrency JOIN transaction t ON t.id_securitycurrency = s.id_securitycurrency
WHERE t.transaction_type >= 4 AND t.transaction_type <= 5
AND t.id_securitycurrency = idSecurity
GROUP BY t.id_tenant, t.id_security_account, ss.split_date;

SELECT t1.* FROM holdSecurityMarginSplit t1 JOIN (
SELECT t2.idTenant, t2.idSecurityaccount, MAX(IF(t2.idPortfolio IS NULL, t2.tsDate, NULL)) AS maxSplitDate, MIN(IF(t2.idPortfolio IS NULL, NULL, t2.tsDate)) AS minTransDate FROM holdSecurityMarginSplit t2
GROUP BY t2.idTenant, t2.idSecurityaccount) AS t3 ON t1.idTenant = t3.idTenant
AND t1.idSecurityaccount = t3.idSecurityaccount AND t3.maxSplitDate >= t3.minTransDate AND (t1.tsDate >= t3.minTransDate AND t1.idPortfolio IS NULL || t1.idPortfolio IS NOT NULL)
ORDER BY t1.idTenant, t1.idSecurityaccount, t1.tsDate;
END //

CREATE OR REPLACE PROCEDURE holdSecuritySplitTransaction(IN `idSecurity` INT)
    READS SQL DATA
BEGIN
DROP TEMPORARY TABLE IF EXISTS holdSecuritySplit;
CREATE TEMPORARY TABLE holdSecuritySplit (tsDate DATETIME NOT NULL)
SELECT t.id_tenant as idTenant, p.id_portfolio AS idPortfolio, t.id_security_account AS idSecurityaccount, TIMESTAMP(t.tt_date, TIME(t.transaction_time)) AS tsDate, SUM(IF(t.transaction_type = 4, 1, -1) * t.units) as factorUnits, te.currency AS tenantCurrency, p.currency AS porfolioCurrency, CAST(NULL AS int) AS idTransactionMargin
FROM transaction t JOIN security s ON s.id_securitycurrency = t.id_securitycurrency JOIN tenant te ON te.id_tenant = t.id_tenant
JOIN securitycashaccount sca ON sca.id_securitycash_account = t.id_security_account  JOIN portfolio p ON p.id_portfolio = sca.id_portfolio
WHERE t.transaction_type >= 4 AND t.transaction_type <= 5
AND  t.id_securitycurrency = idSecurity
GROUP BY idTenant, idSecurityaccount, tsDate
UNION
SELECT t.id_tenant as idTenant, null AS idPortfolio, t.id_security_account AS idSecurityaccount, ss.split_date AS tsDate, ss.to_factor / ss.from_factor as factorUnits, null AS tenantCurrency, null AS porfolioCurrency, CAST(NULL AS int) AS idTransactionMargin
FROM security s JOIN securitysplit ss ON s.id_securitycurrency = ss.id_securitycurrency JOIN transaction t ON t.id_securitycurrency = s.id_securitycurrency
WHERE t.transaction_type >= 4 AND t.transaction_type <= 5
AND t.id_securitycurrency = idSecurity
GROUP BY t.id_tenant, t.id_security_account, ss.split_date;

SELECT t1.* FROM holdSecuritySplit t1 JOIN (
SELECT t2.idTenant, t2.idSecurityaccount, MAX(IF(t2.idPortfolio IS NULL, t2.tsDate, NULL)) AS maxSplitDate, MIN(IF(t2.idPortfolio IS NULL, NULL, t2.tsDate)) AS minTransDate FROM holdSecuritySplit t2
GROUP BY t2.idTenant, t2.idSecurityaccount) AS t3 ON t1.idTenant = t3.idTenant
AND t1.idSecurityaccount = t3.idSecurityaccount AND t3.maxSplitDate >= t3.minTransDate AND (t1.tsDate >= t3.minTransDate AND t1.idPortfolio IS NULL || t1.idPortfolio IS NOT NULL)
ORDER BY t1.idTenant, t1.idSecurityaccount, t1.tsDate;
END //

DELIMITER ;

-- The existing rows of hold_securityaccount_security were written with from_hold_date taken from
-- transaction_time and have to be rebuilt, otherwise they keep starting a day early wherever the two
-- dates disagree and the daily HOLD_TABLE_CONSISTENCY_CHECK reports them as ORPHAN_DATE. The same
-- rebuild removes the inverted periods (to_hold_date before from_hold_date) that an intraday round trip
-- used to leave behind. On an installation whose two dates always agreed the rebuild is a no-op that
-- costs nothing but its runtime.
--
-- id_task 39 = REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT, execution_priority 20 = PRIO_NORMAL,
-- progress_state 0 = PROG_WAITING; a null entity means "all clients". Timestamps use UTC_TIMESTAMP()
-- because BackgroundWorker polls with LocalDateTime.now() and BaseConstants.TIME_ZONE is 'UTC' - NOW()
-- would be read as scheduled in the future on every host east of Greenwich. The NOT EXISTS guard makes
-- the migration idempotent and also skips the insert when such a rebuild is already waiting or running.
INSERT INTO task_data_change (id_task, execution_priority, entity, id_entity, earliest_start_time,
    creation_time, progress_state)
  SELECT 39, 20, NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
  FROM DUAL
  WHERE NOT EXISTS (
    SELECT 1 FROM task_data_change WHERE id_task = 39 AND entity IS NULL AND progress_state IN (0, 4));

-- The trading platform plan of Cornèr Bank AG was seeded with a missing 'r' in its name
-- ('CornèTrader'). Both translations are corrected here; the import platform of that bank and the
-- comments of the fee model already use the correct spelling. The update is scoped through
-- trading_platform_plan so that no unrelated multilinguestrings row can be hit, and it is idempotent
-- because a second run no longer finds the old text.
UPDATE multilinguestrings mss JOIN trading_platform_plan tpp ON tpp.platform_plan_name_nls = mss.id_string
SET mss.text = 'CornèrTrader Transaktionsbetrag'
WHERE mss.language = 'de' AND mss.text = 'CornèTrader Transaktionsbetrag';

UPDATE multilinguestrings mss JOIN trading_platform_plan tpp ON tpp.platform_plan_name_nls = mss.id_string
SET mss.text = 'CornèrTrader Transactions value'
WHERE mss.language = 'en' AND mss.text = 'CornèTrader Transactions value';
