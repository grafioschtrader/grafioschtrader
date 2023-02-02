# This column is not needed -> found an other solution
ALTER TABLE security DROP COLUMN IF EXISTS fill_eod_gap_until;

# There may be duplicate EOD in historyquotes for a security and date. These must be removed.
CREATE TEMPORARY TABLE IF NOT EXISTS multihist AS (
SELECT h1.id_securitycurrency, h1.date, MAX(h1.create_type) as create_type
FROM historyquote h1 WHERE (h1.id_securitycurrency, h1.date) IN
(SELECT h.id_securitycurrency, h.date FROM historyquote h GROUP BY h.id_securitycurrency, h.date HAVING COUNT(*) > 1) 
GROUP BY h1.id_securitycurrency, h1.date);

DELETE h FROM historyquote h JOIN multihist m ON h.id_securitycurrency = m.id_securitycurrency AND h.date = m.date
AND h.create_type = m.create_type;
DROP TEMPORARY TABLE multihist;

# Remove the create_type from the index
ALTER TABLE historyquote DROP INDEX IF EXISTS IHistoryQuote_id_Date;
ALTER TABLE historyquote ADD UNIQUE IHistoryQuote_id_Date (id_securitycurrency, date);