#!/bin/bash
# Runs once when the MariaDB data volume is first initialized
# (mounted into /docker-entrypoint-initdb.d/ by docker-compose.yml).
#
# The Grafioschtrader Flyway migrations create triggers and stored procedures
# with a hardcoded DEFINER=`grafioschtrader`@`localhost` (e.g. V0_10_0__init.sql).
# In this container setup the application connects as ${MARIADB_USER}@'%', so:
#  1. the application user needs the SET USER privilege to create objects
#     whose definer differs from the current user, and
#  2. the definer account grafioschtrader@localhost must exist with privileges
#     on the database, because triggers/procedures execute with its rights.
mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" <<-EOSQL
	CREATE USER IF NOT EXISTS 'grafioschtrader'@'localhost' IDENTIFIED BY '${MARIADB_PASSWORD}';
	GRANT ALL PRIVILEGES ON \`${MARIADB_DATABASE}\`.* TO 'grafioschtrader'@'localhost';
	GRANT SET USER ON *.* TO '${MARIADB_USER}'@'%';
	FLUSH PRIVILEGES;
EOSQL
