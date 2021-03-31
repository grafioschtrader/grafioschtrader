---
title: "Transaktionsimport"
date: 2021-03-30T22:54:47+01:00
draft: false
weight : 15
chapter: true
---
## Transaktionsimport
Für den import von Transaktionen gibt es mehrere Möglichkeiten. Allerdings muss das Depot mit der entsprechenden **Handelsplattform Plan** verknüpft sein. Mit dieser Verknüpfung wird der Import mit der entsprechende **Import Vorlagengruppe** importiert. Falls Ihre Handelsplattform einen Export von allen Transaktionen erlaubt, sollten Sie  möglicherweise einen CSV-Import in Erwägung ziehen. Leider fehlen solchen Exports oftmals die Gebühren oder Steuerdaten und eingenen sich nicht für den Import von Wertpapier Transaktionen.
### CSV-Import
....
### PDF-Import
Der PDF-Import kann bisher nur auf Wertpapier Transaktionen angewendet werden.
#### Einzelne mit Drag & Drop nach GT übertragen
Für den Import von einzelnen Transaktionen können Sie das Drag & Drop benutzen.
{{% notice warning %}}
Falls Sie Bedenken bezüglich des Datenschutz haben, sollten Sie diese Variante nicht anwenden.
{{% /notice %}}
#### GT Transform
Transform ist eine Desktop JavaFX Applikation, die es ermöglicht eine Vielzahl von PDF Dateien zu anonymisieren. Sie kann beispielsweise rekursiv alle PDF eines Dateiverzeichnis einlesen. Danach können in einer Massenverarbeitung die nicht für den Import relevanten Textzeilen entfernt werden. Falls Ihre Wertpapier Transaktionen mehr oder weniger vollständig als PDF-Dateien vorliegen, können Sie diese mit Transform verarbeiten und danach in GT importieren.

### Ansicht Transaktionsimport

#### Funktionen

##### Instrument zuweisen
Eier Wertpapiertransaktion können Sie eine Instrument zuweisen. Dazu öffnet sich der [Suchdialog für Instrumente](../../../watchlistinstrument/instrument/searchdialog).