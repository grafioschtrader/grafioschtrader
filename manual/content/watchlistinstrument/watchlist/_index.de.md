---
title: "Watchlist"
date: 2021-03-24T10:54:47+01:00
draft: false
weight : 15
chapter: true
---
## Watchlist
Im Hauptbereich sind **3 Registerkarten** für die unterschiedlichen **Wachlisten-Ansichten** ersichtlich, diese unterscheiden sich primär durch ihren Inhalt, wobei sich die ersten Spalten der Tabelle zur Identifizierung des Instruments nicht unterscheiden.
+ **Performance**: Diese gibt einen Überblick der Kursdaten und der Perfomance der offenen Positionen. 
+ **Preis Datenfeed**: Sie dient der Überwachung der Zuverlässigkeit der Datenquellen von Kursdaten.
+ **Dividenden/Split Feed**: Damit erhalten Sie Informationen hinsichtlich von Dividenden uns Splits.

### Watchlist erstellen
Die Watchlist wird mittels dem **Kontextmenü** auf dem statische Element **Watchlist** im **Navigationsbereich** erstellt. Es gibt einen Beschränkung bezüglich der Anzahl Watchlisten die ein Benutzer erstellen kann.

### Watchlist löschen
Die Watchlist wird mittels dem **Kontextmenü** auf dem statische Element **Watchlist** im **Navigationsbereich** gelöscht werden. Dabei darf die Watchlist keine Instrumente enthalten.

### Performance Watchlist {{< svg "chart-line.svg" svg-icon-size >}}
Im **Navigationsbereich** ist einer bestimmten Watchlist das **Performance** Watchlist gekennzeichnet. Diese Watchlist sollte alle Ihre offenen Positionen enthalten. Dadurch erfolgt automatisch eine Aktualisierung der abhängigen **Währungspaare**.

### Allgemeine Funktionen der Watchlist-Ansichten
Es gibt Funktionen die in allen Watchlisten implementiert sind. Anderseits unterscheiden sich die Tabellenspalten der Watchlisten, daher kann es pro Watchlist unterschiedliche Funktionen geben. Im folgenden werden die Funktionen behandelt die in allen Ansichen verfügbar sind.

#### Instrument in andere Watchlist verschieben
Ein Instrumente kann mit Drag and Drop auf eine andere Watchlist verschoben werden. Dazu ziehen Sie das Symbol der **Instrumentenart** auf Ihre gewählte **Watchlist** im **Navigationsbereich**.

#### Hinzufügen bestehender Instrumente
In einer aktiven Watchlist des **Hauptbereichs** kann über das **Kontextmenü** ein bestehendes Instrument hinzugeführt werden. Hierzu öffnet sich ein der entsprechende [Suchdialog für Instrumente](../instrument/searchdialog).
+ Mit der Schaltfläche **Hinzufügen** werden die selektieren Instrumente der Watchlist hinzugefügt.

### Eigenschaften und Tabellenspalten
Spalten werden nicht weiter beschrieben falls diese **Instrument** bzw. **Anlagekasse** erwähnt wurden. Einige Spalten sind selbsterklärend oder dessen Tooltip und müssen nicht weiter erklärt werden.

#### Instrumentenart (I)
Die Symbole der Instrumente können der [Anlageklasse](../../basedata/assetclass) entnommen werden.
{{< svg "d.svg" svg-icon-size >}}: Dies steht zusätzlich für ein abgeleitetes Instrument.