---
title: "Daten und Benutzerrechte"
date: 2021-03-26T22:54:47+01:00
draft: false
weight : 20
chapter: true
---
## Daten
In GT gibt es **private** und **geteilte Daten**. Bei den **geteilten Daten** wird anhand der **Benutzerrechte** die Sicht- und  Veränderbarkeit geregelt. Daten wie die "Portfolios" und "Transaktionen" sind **private Daten** und für andere Benutzer nicht zugänglich. Die Informationsklassen "Anlageklassen" und "Handelsplatz" sind beispielsweise **geteilte Daten** und für alle Benutzer sichtbar aber die **Autorisierung** des Benutzers bestimmt deren **Veränderbarkeit**.

### Private Daten
Konto, Transaktion usw. sind **private Daten** und können von einem andere Benutzer nicht gesehen oder verändert werden.

### Geteilte Daten
Bei den geteilten Daten bestimmen die **Zugriffsrechte** des Benutzers bzw. ob dieser der Besitzer der **Entität** über die **Veränderbarkeit** einer Entität.

#### Besitzer einer Entität
Der welche eine **Entität** einer **Informationsklasse** der geteilten Daten erfasst, wird automatisch zum **Besitzer** dieser. Er kann diese **Entität** unabhängig seiner **Benutzerrechte** bearbeiten. Auch die Benutzer der **Privilegierten- bzw. Administratoren-Gruppe** können uneingeschränkt diese **Entität** ändern, alle anderen Benutzer können über [Datenänderungswunsch](../../basedata/) eine Änderung vorschalgen.

#### Optimistisches Sperren
GT verwendet ein **optimistisches Sperren** das auf der Basis einer Versionsnummer basiert. Mit der **optimistischen Sperre** verfügt jedes Entität über ein Attribut, das als Versionsnummer fungiert. Jede Entität besitzt eine **Versionsnummer** diese wird beim Aktualisieren bzw. beim Speichern mit der Versionsnummer in der Datenbank verglichen. Wenn diese Versionnummer nicht übereinstimmen, bedeutet dies, dass ein anderer Benutzer die Entität vor Ihnen geändert hatte. Die Aktualisierung schlägt fehl, da Sie eine veraltete Version der Entität modifizierten. Wenn dies der Fall ist, wiederholen Sie den Vorgang, indem Sie das Entität erneut abrufen und damit eine neue Version aktualisieren. Die optimistische Sperre hindert Sie daran, versehentlich **Änderungen** anderer **Benutzer** zu **überschreiben**. Sie hindert auch andere Benutzer daran, versehentlich Ihre Änderungen zu überschreiben.

## Benutzerrechte
Mit der **vollständigen Registrierung** eines Benutzers, wird dieser automatisch zum **Benutzer mit Limits** zugeordnet.

### Benutzer mit Limits
Benutzer mit Limit kann nur eine **bestimmte Anzahl** von **Entitäten** einer **Informationsklasse** bzw. **seiner geteilten Entitäten** pro Tag erstellen bzw. ändern. Dadurch wird verhindert, dass ein Benutzer einen grösseren willentlichen Schaden an den **geteilten Daten** herbei führen kann. Weitere Informationen siehe [Globale Einstellungen](../../admindata/globalsettings)

### Benutzer ohne Limits
Dieser Benutzer unterliegt keiner Einschränkung bezüglich dem Erstellen oder verändern **seiner geteilten Daten**.

### Privilegierter Benutzer
Dieser hat die gleichen Benutzerrechte wie Benutzer ohne Limits plus kann er auch die **geteilten Daten** aller Benutzer verändern. Das Bearbeiten des **Handelskalender global** und der **Globale Einstellungen** ist dem Benutzer dieser Gruppe nicht erlaubt.

### Administrator
Er kann alle **geteilten Daten** verändern. Zusätzlich kann er Globale und einige Einstellungen des Benutzers ändern.

## Schutz der Fremddaten und das Anfragelimit
Der **Client** von GT kommuniziert mittels einem **REST-API** mit dem **Back-End**. Dieses REST-API ist eine allgemeine Schnittstelle und könnte auch von einem anderen Dienst benutzt werden. Die **privaten Daten** müssen aber besonders geschützt bleiben. Gezielte Zugriff auf die privaten Daten anderer Benutzer werden erkannt und bestraft. Bei mehrfacher Widerhandlung wird der Benutzer gesperrt, die Globale Einstellung von **gt.max.security.breach.count** bestimmt dieses Limit.

### Anfragelimit
GT implementiert eine **Anfragelimit** mittels **Token-Bucket** auf Minuten- und Stundenbasis. D.h es können pro Benutzer nur eine limitierte Anzahl von Anfragen in den genannten Zeiträumen an das **Back-end** erfolgreich erteilt werden. Am häufigsten wird diese Ratenbegrenzung eingesetzt, um einen Ressourcenmangel zu vermeiden und so die Verfügbarkeit von API-basierten Diensten zu verbessern. Im täglichen Gebrauch wird dieses Abfragelimit kaum überschritten, andernfalls erhält der Benutzer ein Hinweis. Bei mehrmaligen Verstoss und mit dem überschreiten der Globalen Einstellung von **gt.max.limit.request.exceeded.count** wird dieser Benutzer von GT gesperrt.