---
title: "Historische Kursdaten"
date: 2018-01-13T22:54:47+01:00
draft: true
weight : 12
chapter: true
---
## Historische Kursdaten

### Exportieren als csv-Datei
Die historischen Tagesdaten können exportiert werden. Das Exportformat entspricht dem Importformat.

### Importieren historischer Daten
Das Importformat können Sie dem Exportformat entnehmen. Beim Importieren von historischen Tagesdaten muss das Datum und der Schlusskurs vorhanden sein. Aus der ersten Zeile wird die Zuordnung der Spalte zun den Importfelder ermittelt. Im folgenden Beispiel wurde das Datum in der ersten Spalte und der Schlusskurs in der zweiten Spalte erwartet. Als Feldbegrenzer wird ein **Strichpunkt** erwartet. 
```
date;close;volume;open;high;low
18.02.2021;78;;;;
```
Bestehende historische Tagesdaten können mit einem Import nicht überschreiben werden.