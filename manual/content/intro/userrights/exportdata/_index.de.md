---
title: "Daten exportieren"
date: 2018-01-13T22:54:47+01:00
draft: false
weight : 20
chapter: true
---
## Daten exportieren
````
# Drop database
mysql -u root -p -D grafioschtrader_s -e "DROP DATABASE grafioschtrader_s"
mysql -u root -p -e "create database grafioschtrader_s; GRANT ALL PRIVILEGES ON grafioschtrader_s.* TO grafioschtrader@localhost IDENTIFIED BY 'YOUR_PASSWORD'"
mysql -grafioschtrader -p --default-character-set=utf8 grafioschtrader_s < gt_ddl.sql
mysql -grafioschtrader -p --default-character-set=utf8 grafioschtrader_s < gt_data5691368078466942791.sql
````