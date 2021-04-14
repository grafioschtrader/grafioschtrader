---
title: "Basisdaten"
date: 2021-03-14T22:54:47+01:00
draft: false
weight : 25
chapter: true
---
## Basisdaten
Es sind **geteilte Daten** die von denn Benutzer einer **GT-Instanz** benutzt und modifiziert werden.

### Statisches Element "Basisdaten"
Auf dem statischen Element **Basisdaten** im **Navigationsbereich** ist die Funktion **Datenänderungswunsch** implementiert. Obwohl die **Entitäten** von gewissen Informationsklassen geteilt werden, können die nicht von jedem Benutzer beliebig verändert werden.

### Datenänderungswunsch
Das Zusammenspiel von **geteilten Daten**, dem **Besitzer einer Entität** und **Benutzerrechte** können dazu führen, dass Sie einen **Datenänderungswunsch** kreieren oder einen erhalten.
+ Die Benutzer der Gruppe **Limits** oder **ohne Limits** kreieren einen **Datenänderungswunsch** falls diese eine **geteile Entität** bearbeiteten und speichern. Sie erhalten einen **Datenänderungswunsch** falls jemand der Gruppe **Limits** oder **ohne Limits** einer seiner erstellten **geteilten Entität** venändern möchte.
+ Ein Benutzer der mit **privilegierten oder administrativen Benutzerrechten** kreiert nie einen **Datenänderungswunsch**. Jedoch erhalten diese alle **Datenänderungswünsche**, auch wenn die betroffene **Entität** ursprünglich von einem anderen Benutzer erstellt wurde.

#### Einschränkungen Datenänderungswunsch
Für bestimmte Entitäten werden gewisse Datenänderungswünsche nicht unterstützt.

#### Abgeleitetes Instrument
Für die **Preisberechnungsformel** kann kein Datenänderungswunsch angebracht werden.
