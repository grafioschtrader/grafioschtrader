---
title: "Watchlist und Instrumente"
date: 2021-03-13T22:54:47+01:00
draft: false
weight : 15
chapter: true
---
## Watchlist und Instrumente
Ein Watchlist ist eine persönliche Zusammenstellung von Instrumenten mit Kursentwicklung und weiteren Angaben. In GT ist die Watchliste ein zentrales Element. Folgende Funktionen sind über die Watchlist erreichbar:
+ **Erstellen und löschen von Instrumenten**: Instrumente können nur über eine Watchlist erstellt werden.
+ **Hinzufügen von bestehenden Instrumenten**: Die Watchlist kann mit bestehenden Instrumenten ergänzt werden. Es gibt eine Limite der Anzahl Instrumente in einer Watchlist.
+ **Transaction auf Wertpapier**: Die Erfassung der ersten **Kauftransaktion** eines bestimmten Instrument muss zwingend über eine Watchlist erfolgen. Nachfolgende Transaktion wie erneuter **Kauf**,  **Dividende** oder **Verkauf** können auch über das **Depot** erfolgen.
+ **Aktualisierung der Innertag Kurse der Instrumente**: Nur über die Watchlist kann der Benutzer aktiv eine Aktualisierung der **Innertag Kurse** verursachen. Es gibt eine Systemeinstellung für den Zeitabstand bevor eine erneute Aktualisierung der Kurse erfolgt. Die Aktualisierung wird automatisch mit der Wahl der entsprechenden **Watchlist** im Navigationsbereich durchgeführt. Die Watchlist selbst sind **private Daten**, ihr Inhalt basiert aber auf **geteilten Daten**.
+ **Überwachung externer Datenquellen**: Überwachung der historischen und innertag Kursdaten und daraus folgende Aktionen.

Im folgenden vereinfachten Klassendiagramm sind die Beziehungen dargestellt:
{{< mermaid >}}
classDiagram
     Watchlist "*" o-- "*" Instrument
    Instrument <|-- Währungspaar
    Instrument <|-- Wertpapier
    Wertpapier "1" ..> "*" Historische_Kurs_Daten : hat
    Wertpapier "*" ..> "1" Handelsplatz : hat
    Wertpapier "*" ..> "1" Anlageklasse : hat
    Wertpapier "1" *-- "*" Splits
    Wertpapier "1" *-- "*" Dividend
    Historische_Kurs_Daten <|-- Berechnet
    Historische_Kurs_Daten <|-- Periodenkurs
    Historische_Kurs_Daten <|-- EOD_Kurs
    Währungspaar "1" ..> "*" EOD_Kurs : hat
    Berechnet o-- EOD_Kurs
    class Instrument{
        timestamp: time
        high: double
        low: double
        last: double
    }
{{< /mermaid >}}
Watchlist Teil 1 im Video erklärt:
{{< youtube 9lnezOqH39E >}}