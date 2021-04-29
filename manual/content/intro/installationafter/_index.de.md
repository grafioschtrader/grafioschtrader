---
title: "Nach Installation und neue Versionen"
date: 2021-04-2522:54:47+01:00
draft: false
weight : 60
chapter: true
---
## Nach Installation und neue Versionen
Die Daten von Grafioschtrader (GT) werden mittels eines relationalen Datenbankmanagementsystem (RDBMS) verwaltet. Falls die Struktur der GT-Datenbank ändert, wird automatisch eine Migration der Datenbank durchgeführt werden.

### Nach der Installation von GT
Nach der installation von GT und mit dem **ersten Start** des **Back-End** werden bestimmte geteilte **Informationsklassen** mit einigen **Entitäten** befüllt. Der Benutzer der in `application.properties` unter dem property `gt.main.user.admin.mail` genannten E-Mail Adresse wird mit seiner Registrierung zum Besitzer dieser Entitäten.

+ **Handelskalender Global**: Die **Obermenge** aller **Handelstage**.
+ **Handelsplatz**: Die "wichtigsten" **Handelsplätze** und ihre Handelskalender.
+ **Anlageklassen**: Die **Anlageklassen** mit einer **länderspezifisch** ausgerichteten **Unter Anlageklasse**.
+ **Instrumente**: Die Selektion der **Instrumente** beinhaltet die Indizes, welche im Handelskalender benutzt werden. Zusätzlich sind Instrumente mit einer vielfallt unterschiedlicher **Konnektoren** beigefügt, diese können als Vorlage bzw. Spickzettel für Ihre Instrumente dienen.
+ **Splits** und **Divideden**: Die beigefügten **Instrumente** kommen mit ihren **Splits** und **Dividenden**.
+ **Historische Kursdaten**: Die **historischen Kursdaten** der bei beigefügten Instrumente, diese sind bis 26.03.2021 nachgeführt. Diese werden danach vom System kontinuierlich zum aktuellen Datum automatisch nachgeführt.

### Neue Versionen
Seit der **Version 0.10.0** werden die Daten der Datenbank migriert, d.h Sie können GT produktiv einsetzen. Ihre Daten werden mit einem Versionswechsel automatisch in die neue Version migriert.

In diesem Video wird dies ausführlich erklärt:
{{< youtube NiCT8B076-0 >}}