---
title: "Kurs Datenfeed"
date: 2021-04-01T22:54:47+01:00
draft: false
weight : 20
chapter: true
---
## Kurs Datenfeed
Diese Watchlist dient primär der Überwachung der historischen und intraday Kursdaten.

### Funktionen
Die spezifischen Funktionen dieser **Watchlist-Ansicht** verlangt die **Benutzerrechte** eines **Privilegierten Benutzer**, andernfalls wird sie nicht angeboten.

#### Reparatur historischer Daten
 Es wird versucht die **historischen Kursdaten** aller Instrumente der Watchlist zu aktualisieren, wessen historischen **Wiederholungszähler grösser 0** ist. Dabei wird das Limit der möglichen Versuche ignoriert.

#### Reparatur intraday Daten
Es wird versucht die **intraday Kursdaten** aller Instrumente der Watchlist zu aktualisieren, wessen Innertag **Wiederholungszähler grösser 0** ist. Dabei wird das Limit der möglichen Versuche ignoriert.

### Expandierende Tabellenzeile
Die expandierte Tabellenzeile zeigt fast alle Eigenschaften des Instrumentes an. 