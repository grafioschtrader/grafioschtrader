---
title: "Einstellungen"
date: 2021-03-14T22:54:47+01:00
draft: false
weight : 25
chapter: true
---
## Einstellungen
Die Einstellungen sind unter Menüpunkt **Einstellugen** der **Menüleiste** verfügbar.

### Passwort ändern
Damit können Sie ihr bestehendes Passwort ändern. Nach der Änderung des Passwortes müssen Sie sich neu anmelden.

### Spitzname und Land/Sprache ändern
Sie können den **Spitznamen** und die Eigenschaft "**Sprache und Land**" anpassen.

### Persönliche Daten exportieren

````
# Drop database
mysql -u root -p -D grafioschtrader_s -e "DROP DATABASE grafioschtrader_s"
mysql -u root -p -e "create database grafioschtrader_s; GRANT ALL PRIVILEGES ON grafioschtrader_s.* TO grafioschtrader@localhost IDENTIFIED BY 'YOUR_PASSWORD'"
mysql -grafioschtrader -p --default-character-set=utf8 grafioschtrader_s < gt_ddl.sql
mysql -grafioschtrader -p --default-character-set=utf8 grafioschtrader_s < gt_data5691368078466942791.sql
````

### Löschen meiner Daten und des GT-Kontos

