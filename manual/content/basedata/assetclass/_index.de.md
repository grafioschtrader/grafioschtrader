---
title: "Anlageklasse"
date: 2021-03-26T22:54:47+01:00
draft: false
weight : 5
chapter: true
---
## Anlageklasse
Die **Selektion** einer **Anlageklasse** hat bei der Erfassung eines **Instrument** erheblichen Einfluss auf dessen Eigenschaften. Zudem hat sie Einfluss auf das Reporting und die Art der Transaktion die auf ein Instrument angewendet werden können.
+ Solbald ein **Instrument** die Anlageklasse referenziert, kann nur noch die Eigenschaft "Unter Anlageklasse" geändert werden.
+ Eine durch ein **Instrument** referenziert Anlageklasse kann nicht gelöscht werden.


### Erstellen und bearbeiten Anlageklasse
Ein **Anlageklasse** wird im **Hauptbereich** in der **Ansicht Anlageklasse** erstellt, bearbeitet und gelöscht. Diese **Ansicht** erreichen Sie im **Navigationsbereich** auf dem statischen Element Anlageklasse.
+ **Erstellen** einer **Anlageklasse** über **Kontextmenü**.
+ **Bearbeiten** einer **Anlageklasse** auf dem **selektieren Anlageklasse**.
+ **Löschen** einer **Anlageklasse** auf dem **selektieren Anlageklasse**.

#### Eigenschaften und Tabellenspalten
Wie oben erwähnt sind die Eigenschaften eingeschränkt veränderbar. Die Eigenschaften und die Tabellenspalten sind übereinstimmend und bedürfen daher keiner differenzierten Beschreibung.

##### Anlageklasse
Die Eigenschaft **Anlageklasse** beeinflusst die Auswahl an **Finanzinstrument**, folgende Kombinationen sind möglich:
|Anlageklasse|Direktanlage|ETF|Investment-Fonds|Pensionsfonds|CFD|Forex|Index nicht investierbar|
|----|:-:|:-:|:-:|:-:|:-:|:-:|:-:| 
|**Aktien**| X | X | X | X | X |   | X |
|**Anleihen**| X | X | X | X |   |   | X |
|**Commodites**| X | X | X |  | X |   | X |
|**Geldmarkt**| X | X | X |   |   |   |  |
|**Immobilien**| X | X | X |   |   |   |  |
|**Kreditderivate**| X |   | X |   |   |   | X |
|**Multi asset**| X | X | X |   |   |   | X |
|**Währungspaar**|   |   |   |   |   | X |  |
|**Wandelanleihe**| X | X | X | X |   |   | X |

##### Unter Anlageklasse
Die Eigenschaft ist frei definierbar, wobei gleichbleibende Texte erwartet werden. Bei einer Regionen-Kategorisierung sollte beispielsweise der Text "Aktien USA" konsistent für die entsprechenden Anlageklassen eingesetzt werden. Zurzeit werden die beiden Sprachen **Deutsch** und **Englisch** von GT unterstützt.

##### Finanzinstrument
Wie oben dargestellt, ist die Auswahl des Finanzinstruments abhängig von der Selektion der Anlageklasse.