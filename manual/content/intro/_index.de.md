---
title: "Einführung"
date: 2021-03-08T15:00:00+01:00
draft: false
weight : 1
chapter: true
---
## Einführung

### Warum gibt es Grafioschtrader (GT)
Einige Jahre nutzte ich eine Depotverwaltungsapplikation die meine anfänglichen Bedürfnisse genügte. Mit der Zeit erkannte ich, dass die Transaktions- und Depotkosten einen erheblichen Einfluss auf die Gesamtrendite des Portfolios haben. So suchte ich unterschiedliche Onlinebörsenbroker die für meine Investment die günstigsten Handelskonditionen boten. Bisher genügt nur eine Kombination von mehreren Handelsplattformen meinen Bedürfnissen. Als schweizer Anleger kommt hinzu, dass früher oder später Investitionen mit Fremdwährungen tätigt.

Mit der Zeit verliert man die Übersicht wo wie viel in was investiert ist. Zudem gibt es keine Überblick über die gesamt Performance aller Portfolios. Ich dachte dies müsse nicht sein und als Softwareentwickler stürzte ich mich in das Abenteuer Grafioschtrader.

### Was kann GT
+ **Die Software ist kostenlos und Open-Source**: GT ist als Open-Source freigegeben und wird auf [GitHub](//github.com/hugograf/grafioschtrader) gefunden.
+ **Mandantenfähigkeit**: GT kann für eine Gruppe von Anlegern oder im Einzelmodus betrieben werden.
+ **Webapplikation**: GT ist eine Webapplikation und liefert die übersichtlichsten Ergebnisse mit einem Desktop-Webbrowser.
+ **Mehrere Portfolios mit Währungskonten**: Nachbildung mehreren Portfolios mit einem oder mehreren Depots und einem oder mehreren Bankkonten.  
+ **Mehrere Währungen**: Handel von Wertpapieren in unterschiedlichen Währungen. 
+ **Handel ab Jahrtausendwende**: Grundsätzliche Unterstützung von historischen Kursdaten ab dem Jahr 2000. Hierbei ist zu beachten, dass die Beschaffung von Kursdaten von nicht gehandelten Wertpapieren möglicherweise ein Problem darstellt.
+ **Unterschiedliche Finanzinstrumente**: Aktien, Anleihen, ETF, Wertpapiere ohne Kursdaten, Short ETF, CFD, Forex
+ **Import von Transaktionen**: Ein Import von einzelnen oder mehreren **PDFs** mit Wertpapier-Transaktionen Über **CSV**-Datei können auch Kontotransaktionen geladen werden.
+ **Auswertungen über Anlageklassen**: Auwertungen nach den gängigen Anlageklassen wie Aktien, Anleihen, Immobilien, Commodities, usw.

### Für wen eignet sich GT
GT ist der Versuch die Nachbildung der Portfolios gemäss der Realität, daher gibt es Kontos wie bei Ihrem Onlinebörsenbroker. Jede Buchung ist mit einem Cashkonto verbunden, somit werden die Buchungen gemäss Ihrer Handelsplattform nachgebildet.
+ Der Investor der seine Finanzdaten nicht einer intransparent Platform anvertrauen will. Sie können die GT selber hosten, dazu genügt ein [Raspberry Pi 4](//www.raspberrypi.org/products/raspberry-pi-4-model-b/) mit 4GB Arbeitsspeicher.
+ Ein Shareholder der eine Historie über alle seine Trades haben will. Sie können alle geschlossenen Positionen jederzeit einsehen.
+ Privatanleger der wissen will, wie sich seine Währungsgewinnen entwickeln. Es wird laufend der Währungsgewinn auf den Fremdwährungskonten ausgewiesen.
+ Investoren-Club der eine eigene GT-Instanz hosten will und sich ein Mitglied gerne um technischen Aspekte kümmert.
+ Anleger mit mehreren Portfolios bei unterschiedlichen Handelsplattformen. GT liefert Informationen pro Portfolio und kann diese aggregiert darstellen.
p### Für wen ist GT nicht gedacht
+ Dem Daytrader fehlen die Echtzeitkurse.
+ Investoren die ihren Handel losgelöst von Geldkontos verwalten möchten.
