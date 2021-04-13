---
title: "Handelsplatz"
date: 2021-03-29T22:54:47+01:00
draft: false
weight : 5
chapter: true
---
## Handelsplatz
An den Handelsplätze werden die Instrumente gehandelt. In GT sind diese aus folgenden Gründen wichtig:
+ Mit dem Handelskalender eines Handelsplatz lässt sich die **Vollständigkeit** der **historischen Kursdaten** evaluieren.
+ Die Zuordnung des Handlesplatzes zu einem **Wertpapier** bestimmt, ob es überhaupt öffentlich zugängliche Kursdaten für dieses gibt.
+ In einer zukünftigen GT-Version wird die Aktualisierung der **historischen Kursdaten** gestaffelt in den Zeiträumen der **geschlossenen Handelsplätze** durchgeführt.

Das **Land** und die **Zeitzone** können bei einem bestehenden Handelsplatz nicht mehr verändert werden, dies soll einer totalen Umschreibung eines Handelsplatzes entgegen wirken.

### Erstellen und bearbeiten Anlageklasse
Ein **Handelsplatz** wird im Hauptbereich in der **Ansicht Handelsplatz** erstellt, bearbeitet und gelöscht. Diese **Ansicht** erreichen Sie im **Navigationsbereich** auf dem statischen Element Handelsplatz.
+ **Erstellen** eines **Handelsplatz** über **Kontextmenü**.
+ **Bearbeiten** eines **Handelsplatz** auf dem **selektieren Handelsplatz**.
+ **Löschen** eines **Handelsplatz** auf dem **selektieren Handelsplatz**.

### Eigenschaften und Tabellenspalten

#### Name
Der Name des Handelsplatzes. Diese können durch Zusammenschlüsse von Börsenplätze im Laufe der Zeit ändern.
#### Symbol
Das **Symbol** des Handelsplatz wird in manchen Kursdaten-Konnektoren benötigt. Daher sollte dies nur mit grösster Vorsicht geändert werden.

#### Keine Kursdaten
Falls es keine Kursdaten gibt, sollte dieses Markierungsfeld markiert werden. Dies aktiviert die Eingabemöglichkeit von **"Historischen Kurse für Periode"** beim Erfassen des **Instruments**.

#### Öffnungszeit, Zeit schluss und Zeitzone
In einer zukünftigen GT-Version wird die Aktualisierung der historischen Kursdaten gestaffelt in den Zeiträumen der geschlossenen Handelsplätze durchgeführt. Diese Eigenschaften bestimmen den Zeitraum im welchen die Aktualisierung stattfinden kann.

#### Index für Handelskalender
Der **Handelskalender** lässt sich automatisiert über einen an diesem Handelsplatz gehandelten **Index** akutalisieren. 

### Handelskalender
Der **Handelskalender** eines **Handelsplatz** ist eine Voraussetzung für die **Vollständigkeitsprüfung** der historischen Kursdaten. Die **automatisierte Markierung** für geschlossene Tage über den Index kommt nur nach dem jüngsten durch den Benutzer markierten Tag zur Anwendung. Das heisst das System markiert nie einen Tag der älter als die jüngste **blaue** Markierung. 
+ **Offene Tage**: **Grün** markierte Tage definieren sich aus dem **globalen Handelskalender**.
+ **Geschlossene Tage**: **Rot** markierte Tage wurden ursprünglich durch den **Index für Handelskalender** erstellt. **Blau** markierte Tage wurden durch den Benutzer verursacht. Durch Kopier-Funktionen ändert sich diese Merkmale nicht. 
#### Funktionen
Es gibt zwei Kopierfunktionen um einen gesamten Handelskalender oder eine Jahreskalender auf den selektieren Jahreskalender zu kopieren. Dabei werden bestehende Kalender überschrieben.