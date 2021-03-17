---
title: "Historische Kursdaten"
date: 2021-03-14T22:54:47+01:00
draft: false
weight : 12
chapter: true
---
## Historische Kursdaten
Wird ein **Instrument** in einer **Wachtliste** oder **Depot** selektiert ist die Funktion **Tagesenddaten als Tabelle** verfügbar. Diese zeigt im **Zusatzbereich** eine Tabelle mit den historischen Kursdaten und anderen nützlichen Informationen bezüglich dieser **historischen Kursdaten** an.

### Zusätzliche Überwachung historischer Kursdaten
Es gibt einige Funktionen in GT für die Überwachung der **historischen Kursdaten**. Diese finden Sie unter [Vollständigkeit historischer Kursdaten](../../../admindata/historyquotequality/) oder [Kurs Datenfeed](../../watchlist/pricefeed/).

### Funktionen auf historischen Kursdaten

#### Exportieren als csv-Datei
Die historischen Tagesdaten können exportiert werden. Das Exportformat entspricht dem Importformat.

#### Importieren historischer Daten
Das Importformat können Sie dem Exportformat entnehmen. Beim Importieren von historischen Tagesdaten muss das Datum und der Schlusskurs vorhanden sein. Aus der ersten Zeile wird die Zuordnung der Spalte zun den Importfelder ermittelt. Im folgenden Beispiel wurde das Datum in der ersten Spalte und der Schlusskurs in der zweiten Spalte erwartet. Als Feldbegrenzer wird ein **Strichpunkt** erwartet. 
```
date;close;volume;open;high;low
18.02.2021;78;;;;
```
Bestehende historische Tagesdaten können mit einem Import nicht überschreiben werden.