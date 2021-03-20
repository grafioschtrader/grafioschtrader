---
title: "Import Transaktion Vorlage"
date: 2018-01-13T22:54:47+01:00
draft: false
weight : 35
chapter: true
---
## Import Transaktion Vorlage
Die meisten Handelsplattformen bieten eine Exportfunktion von Transaktionen. Das Resultat dieses Exports kann möglicherweise in GT importiert werden. Da jede Handelsplattform ihre eigenes Design der **Dokumente** implementiert, muss in GT jeweils entsprechend dieser Dokumente eine Importvorlage erstellt werden. GT kann die zwei Dokumentarten **PDF** und **CSV** verarbeiten.

### Import Implementierung
In den meisten Fällen genügt die in GT allgemein implementierte Importfunktion mit den entsprechenden Importvorlagen um die Dokumente einer Handelsplattform zu verarbeiten. In all anderen Fällen muss in GT ein Importfunktion implementiert werden.

### Vorlagengruppe
Für eine Handelsplattform kann es eine oder  mehrere Vorlagen geben, daher werden die in einer **Vorlagengruppe** gehalten. 
- **Dokmentenart**: Diese Angabe definiert, ob es sich um eine Vorlage für CSV oder PDF handelt.
- **Gültig ab**: Dies ist ein Hinweis für den Benutzer, damit er die Vorlage der sich ändernden Dokumentendesign der gleichen Art der Transaktion zu ordnen kann.
- **Sprache und Land:** Es bestimmt das grundsätzliche Zahlenformat der Dokumente.

## Importvorlage
Die Importvorlage für PDF und **CSV** haben einige Gemeinsamkeiten. Es wird eine Angabe benötigt bei welcher die relevanten Werte aus dem Dokument ermittelt werden können. Dies wird durch die Zuordnung der **Felder** erreicht. Zusätzlich wird eine Konfiguration benötigt, damit die gelesenen Werte der Felder korrekt interpretiert werden.

### Zuordnung Felder
Die für die Transaktion bestimmten Werte werden aus den Feldern gelesen. Die Folgenden Felder sind obligatorisch:
- **cac**: Währung des Bankkontos, es wird ein [ISO 4217](https://de.wikipedia.org/wiki/ISO_4217) Wert erwartet.
- **datetime**: Datum und Zeit der Transaktion, wobei die Zeit optional ist. 
- **isin**: Die ISIN Nummer, diese ist wichtig da eine Bestimmung des Wertpapier damit ziemlich exakt geschieht.
- **cin**: Die Währung des Instruments
- **quotation**: Dies ist der Kurs oder die Dividende pro Einheit.
- **ta**: Das Total, damit wird durch eine Kalkulation überprüft ob die anderen Angaben zu diesem Resultat führen.
- **tc1**: Die Hauptsteuerkosten.
- **tc2**: Manchmal benötigt es zwei Positionen für die Steuerkosten.
- **tt1**: Die Haupttransaktionskoten.
- **tt2**: Manchmal benötigt es zwei Positionen für die Transaktionskosten.
- **cct**: Währung der Transaktionskosten und Steuer, sollte benutzt werden falls die Kosten nicht in der Währung des Instruments anfallen.
- **symbol**: Falls das Dokument keine ISIN hat, ist das Symbol des Wertpapier eine Alternative. 
- **transType**: Mit dieser Angabe wird über eine zusätzliche Konfiguration die Art der Transaktion bestimmt.
- **units**: Die Anzahl der Einheiten an der Transaktion.
### Konfiugration
Am Ende der Importvorlage folgt die Sektion **[END]**. In dieser folgt die Konfiguration für die Verarbeitung der ""Vorlage** bzw. des zu importierenten **Dokumentes**.
+ **transType**: Diese ist eine Zuweisung des Textes zur einer **Transaktionsart**. Folgende Transaktionen werden in CSV und PDF Vorlagen unterstützt:
    - **ACCUMULATE**: Ist ein Kauf eines Wertpapieres.
    - **REDUCE**: Ist ein Verkauf eines Wertpapieres.
    - **DIVIDEND**: Ist üfr den Zins oder Dividende eines Wertpapieres.
Folgende können zusätzlich in einer Importvorlage für **CSV** angewendet werden:    
    - **INTEREST**: Ist der Zins für ein Konto.
    - **DEPOSIT**: Ist eine Auszahlung von einem Konto.
    - **WITHDRAWAL**: Ist eine Einzahlung von einem Konto.
+ **dateFormat**:
+ **timeFormat**:
+ **overRuleSeparators**: All<’'|.> oder de-CH<'|.>de-DE<,|.>
+ **otherFlagOption**: BOND_QUATION_CORRECTION, 

### Importvorlage für csv
Eine Importvorlage für csv ist für eine Menge von Transaktionen gedacht. Pro Zeile wird es maximal eine Transaktion in GT erzeugen. Gewisse Transaktionen gehen über mehrere Zeilen, beispielsweise ein Teilverkauf von einem Wertpapier.
#### Erste Zeile
Die erste Zeile enthält die ID des 
#### Zweite Zeile
Durch die erste Zeile der csv Datei erfolgt die Zuordnung gemäss Vorlage zu den relevanten Werten. ... 

### Importvorlage für PDF
Eine Importvorlage für PDF ist eine Definition für eine einzelne Wertpapier Transaktion pro Dokument. Eine Importvorlage für ein PDF besteht aus einem in Text umgewandelten PDF Dokument der Handelsplattform. Danach werden die für die Transaktion relevanten Werte durch Variablen mit Ankerpunkten ersetzt. Ein Feld mit Ankerpunkte wird mit "**{}**" umschlossen, die Ankerpunkt sind mit einem "**|**" vom Feldnamen und unter sich getrennt. Ein Beispiel für eine gültiges Feld mit Ankerpunkten, auch **Feldposition** genannt, könnte wie folgt aussehen **{datetime|P|N}**.

#### Ankerpunkte
Die für die Transaktion relevanten Werte sind über das gesamte PDF-Dokument verteilt. Jedes dieser Transaktions Dokumente hat eine feste Struktur die sich für gleiche Art von Transaktion nur geringfügig ändert. Diese gleichartige Struktur kann GT mit Ankerpunkten für die Erkennung von Werten benutzen.
+ Der Wert kann aus einer Dokumentenzeile erkannt werden. Falls die Struktur in einer Zeile sich kaum ändert, können die folgenden Ankerpunkte verweddet werden.
    - **P**: Vorhergendes Wort. Funktioniert auch wenn sich der Wert am Beginn einer Zeile befindet. 
    - **Pc**: Der Wert hat ein Prefix, d.h. zwischen dem Wert und der Prefix hat es kein Leerzeichen 
    - **N**: Nachfolgendes Wort. Funktioniert auch wenn sich der Wert am Ende einer Zeile befindet. 
    - **Nc**: Der Wert hat ein Suffix, d.h. zwischen dem Wert und der Suffix hat es kein Leerzeichen.
    - **SL**: Der Zeilenanfang derselben Zeile. Dieser Ankerpunkt kann auf einen Regulären Ausdruck verweisen.
+ Der Wert kann aus einer Dokumentenzeile nicht erkannt werden, beispielsweise wenn der Zeilenanfang und Zeilenende sich pro Dokument ändert und das vorhergehende wie auch nachfolgende Wort für die Erkennung nicht genutzt werden können.
    - **PL**: Der Zeilenanfang der vorhergehenden Zeile. Dieser Ankerpunkt kann auf einen Regulären Ausdruck verweisen.
    - **NL**: Der Zeilenanfang der nachfolgenden Zeile. Dieser Ankerpunkt kann auf einen Regulären Ausdruck verweisen.
#### Feldposition Konfiguration
Oftmals gibt es auch optionale Werte wie Steuerkosten. Falls dieser Wert nur in bestimmten Dokumenten vorkommt gibt die Ankerpunkt Konfiguration.
- **O** Dieser Ankerpunkt ist optional.
- **R** Der Inhalt dieser Zeile kann sich mehrfach wiederholen. Diese Konfiguration ist hilfreich bei Verkauf und Kauf von einem Wertpapier, da manchmal die Transaktion über mehrere Börsentransaktionen ausgeführt wird. Die Konfiguration muss in der esten Feldposition stehen. 

#### Beispiel einer PDF Vorlage
In folgenden ist ein solche Vorlage für den Kauf und Verkauf
von Wertpapieren aufgeführt.
{{< highlight markdown "linenos=true, hl_lines=1 2 5 8 10-13" >}}
Gland, {datetime|P|N}
(?:Börsengeschäft:|Börsentransaktion:) {transType|P|N} Unsere Referenz: 12121212 
Gemäss Ihrem Kaufauftrag vom 12.12.2012 haben wir folgende Transaktionen vorgenommen:
Titel Ort der Ausführung
ISHARES $ CORP BND ISIN: {isin|P} SIX Swiss Exchange
NKN: 1613957
Anzahl Preis Betrag
{units|PL|R} {quotation} {cac} 8’000.00
Total USD 8’250.00
Kommission Swissquote Bank AG USD {tc1|SL|N|O}
[Abgabe (Eidg. Stempelsteuer)|Eidgenössische Stempelsteuer] USD {tt1|SL|N}
Börsengebühren USD {tc2|SL|N}
Zu Ihren Lasten USD {ta|SL|N}
[END]
transType=ACCUMULATE|Kauf
transType=REDUCE|Verkauf
dateFormat=dd.MM.yyyy
overRuleThousandSeparators= '’
{{< / highlight >}}
- _Zeile 1_: Das **{datetime|P|N}** bezieht sich auf das Transaktionsdatum, als Ankerpunkt wurde das vorhergehende und nachfolgende Wort gewählt. Die diesem Fall bezieht sicht das **P** auf "Gland," und N auf das Zeilenende.
- _Zeile 2_: Diese Zeile enthält einen [Regulären Ausdruck](//de.wikipedia.org/wiki/Regul%C3%A4rer_Ausdruck#), damit dies funktioniert muss der Ausdruck als Rückwärtsreferenz **(?:…)** definiert werden, andernfalls kann Gruppierung von GT nicht mehr korrekt funktionieren.
- _Zeile 5_: Aus dieser Zeile wird die ISIN gelesen, normalerweise sollten mindestens zwei Ankerpunkte gesetzt werden. In diesem Fall ist **{isin|P}** über das gesamte Dokument eindeutig und ein Ankerpunkt genügt.
- _Zeile 8_: **{units|PL|R} {quotation} {cac}** Diese Zeile kann sich wiederholen. Diese Konfiguration **R** muss in der ersten Feldposition stehen.
- _Zeile 10_: Diese Feldposition der Hauptsteuerkosten **{tc1|SL|N|O}** ist optional. Die Ankerpunkte beziehen sich auf das Wort "Kommission" und dem erwarteten Zeilenende.