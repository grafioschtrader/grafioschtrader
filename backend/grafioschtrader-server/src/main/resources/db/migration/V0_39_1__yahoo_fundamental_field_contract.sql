-- Discard Yahoo values mapped before their currency and share basis were established. In particular, BVPS used
-- the reporting currency rather than the quote currency, and SPS used a potentially incomplete share count.
-- Otherwise a successful run of another source could select these old Yahoo candidates after a Yahoo failure.
-- The next successful Yahoo load rebuilds them. Other sources and Yahoo EPS/check evidence are retained.
DELETE FROM security_fundamental WHERE source = 1 AND metric IN (3, 4, 6, 7);
