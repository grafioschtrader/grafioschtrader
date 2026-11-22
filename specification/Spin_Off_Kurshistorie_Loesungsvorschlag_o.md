# Spin-off der Mutteraktie bei getrennten Split- und Kursquellen

**Code baseline:** backend 0.40.2, highest Flyway script `V0_40_2__spin_off_security_action.sql`.

**Vorschlag zu §6.6.** GT soll die Stückzahl aus der identifizierten Kapitalmassnahme bestimmen. Für die historische Bewertung soll GT je Kursquelle und Feld genau die Rückrechnung aufheben, die diese Quelle für den erfassten Spin-off vorgenommen hat. Die Behandlung echter Aktiensplits und anderer Ereignisse bleibt dabei erhalten. [Hauptspezifikation, §6.6](Spin_Off_Security_Action_Specification.md#66-provider-split-and-adjusted-history-of-the-parent)

## Beispiel mit Yahoo als Splitquelle

Eine Mutteraktie handelt vor der Abspaltung zu 100. Ohne weitere Marktbewegung entfallen am Ex-Tag 80 auf die Mutter und 20 auf die zugeteilten Tochteranteile. Yahoo meldet für die Mutter einen rechnerischen Split mit Faktor 1,25. GT ordnet die Meldung dem Spin-off zu und berücksichtigt sie beim Bestand mit Faktor 1. Wer eine Mutteraktie hatte, behält eine Mutteraktie. Die Zuteilung der Tochter folgt dem Bezugsverhältnis der Kapitalmassnahme.

| Was der historische Kursanbieter vor dem Ex-Tag als Schlusskurs liefert | Verarbeitung | Historischer Wert der Mutter |
|---|---|---|
| 100, für den Spin-off unbereinigt | 100 übernehmen | 100 |
| 80, für diesen Spin-off nachweislich um Faktor 1,25 bereinigt | 80 × 1,25 | 100 |
| 80, Bereinigungsbasis unbekannt | 80 übernehmen und Bewertung als ungeklärt kennzeichnen | Vor einer Klärung kann der Übergang von 80 auf zusammen 100 einen künstlichen Gewinn von 20 zeigen. |

Das Beispiel verwendet denselben Zahlenwert für beide Anbieter. Wenn der Kursanbieter einen anderen Faktor verwendet, gilt für seine Kurse sein eigener belegter Faktor. Die von Yahoo gemeldete Zahl bleibt als Information zur Splitmeldung erhalten. Die in §5 beschriebene Kostenaufteilung und das Bezugsverhältnis der Tochter sind eigenständige Grössen.

## Zuordnung der Splitmeldung

`SecuritysplitJpaRepositoryImpl.updateSplitData` ersetzt beim Import die vom Konnektor erzeugten Splits. Dort soll GT diejenige Meldung ausfiltern, die den erfassten Spin-off repräsentiert. Wertpapier und Ex-Tag grenzen die Kandidaten ein. Bei einer mehrdeutigen Meldung soll der Administrator Quelle, gemeldetes Datum und Verhältnis sehen und die Zuordnung bestätigen können. Wiederholte Importe müssen diese Entscheidung reproduzieren. [Splitimport](../backend/grafioschtrader-server/src/main/java/grafioschtrader/repository/SecuritysplitJpaRepositoryImpl.java)

Besonders wichtig ist ein echter Split am selben Tag. Bei einem echten 2:1-Split und einer getrennt identifizierten Spin-off-Meldung muss der Bestand von 100 auf 200 Mutteraktien steigen. Eine pauschale Löschung aller Meldungen dieses Tages würde ihn bei 100 belassen. Liefert der Anbieter einen einzigen kombinierten Faktor, braucht GT einen Beleg für dessen mengenwirksamen Anteil. Bis zur Klärung ist auch der Anspruch auf Tochteraktien offen.

## Kursbasis je Quelle, Ereignis und Feld

Für jede Mutter und jeden Spin-off soll die Entscheidung an den Historienkonnektor und dessen Instrumentkennung (`urlHistoryExtend`) gebunden sein. Sie hält fest, welche Felder bereits in der gewünschten Basis vorliegen, welche einen belegten Korrekturfaktor benötigen und für welche die Basis ungeklärt ist. Eine dokumentierte Konnektorregel kann die Entscheidung liefern. Für Ausnahmen soll der Administrator die betroffenen Felder, den Faktor und dessen Quelle bestätigen können. Ein Wechsel des Konnektors oder Symbols erfordert eine neue Beurteilung der gelieferten Reihe.

EODHD zeigt, weshalb die Entscheidung feldweise gelten muss. Der GT-Parser verwendet für Open, High und Low die Rohkursfelder, für Close das Feld `Adjusted_close` und für Volume das gelieferte Volumen. EODHD dokumentiert rohe OHLC-Kurse, einen für Splits und Dividenden bereinigten Schlusskurs und ein für Splits bereinigtes Volumen. Für einen konkreten Spin-off folgt daraus, dass die drei Rohkursfelder übernommen werden können. Für Close und Volume ist jeweils zu klären, ob und wie dieses Ereignis eingerechnet wurde. Der gesamte Quotient aus `adjusted_close` und `close` kann weitere Ereignisse enthalten. [GT-Parser](../backend/grafioschtrader-server/src/main/java/grafioschtrader/connector/instrument/eodhistoricaldata/EodHistoricalDataConnector.java) · [EODHD-Datenmethodik](https://eodhd.com/help/methodology)

Für einen Kurs am Tag `d` gilt folgende Berechnung:

```text
Preisfeld(d) = geliefertes Preisfeld(d) × Produkt der bestätigten Preisfaktoren
Volumen(d)   = runden(geliefertes Volumen(d) / Produkt der bestätigten Volumenfaktoren)
```

Jedes Produkt umfasst nur Ereignisse mit Ex-Tag nach `d`, für die das jeweilige Feld der konkreten Reihe entsprechend bereinigt wurde. Das leere Produkt ist 1. Fehlende Feldwerte bleiben leer. Bei ungeklärter Basis wird für das fragliche Ereignis keine rechnerische Korrektur vorgenommen und die Unsicherheit bleibt sichtbar. Der Kurssprung zwischen Vortag und Ex-Tag aus §6.6 eignet sich als Hinweis im Dialog. Auch eine normale Marktbewegung beeinflusst diesen Quotienten.

## Import und Neubewertung

Die Umrechnung erfolgt einmal auf frisch gelieferten Werten. `getDataByConnnector` delegiert heute bereits an `getHistoryQuote`. Eine Korrektur in beiden Methoden würde denselben Kurs zweimal multiplizieren. Ein direkter Konnektorimport über `GTNetHistoricalImportService` soll dieselbe Umrechnung verwenden. Für vom GTNet-Peer gelieferte Werte und ergänzte Archivzeilen zählt die Basis dieser Werte. Gerade nach einem Providerwechsel kann `supplementFromShadow` ältere Tage beisteuern, für die der neue Konnektor keine Daten liefert. [Import, Konnektorwechsel und Archivübernahme](../backend/grafioschtrader-server/src/main/java/grafioschtrader/repository/SecurityJpaRepositoryImpl.java)

Eine bestätigte oder geänderte Kursentscheidung löst den Neuaufbau der betroffenen Historie und danach der abhängigen Bewertungen aus. Der Dialog unterscheidet eine ungeklärte Splitzuordnung mit möglicher Auswirkung auf den Anspruch von einer ungeklärten Kursbasis mit Auswirkung auf die Bewertung. Der Abschluss des technischen Refreshs allein bestätigt die fachliche Kursbasis noch nicht.

## Abnahme für §16 und Stufe 6

| Fall | Erwartetes Ergebnis |
|---|---|
| Yahoo-Split und zwei fremde Kursreihen aus dem Beispiel | Beide bestätigten Varianten ergeben vor dem Ex-Tag 100, am Ex-Tag 80 für die Mutter und zusammen mit der Tochter 100. Die Stückzahlen sind identisch. |
| Kursbasis des Fremdproviders unbekannt | Gelieferter Kurs und sichtbarer Klärungsbedarf bleiben beim erneuten Import erhalten. Nach Bestätigung und Neuaufbau stimmt die historische Bewertung. |
| EODHD mit rohen Open/High/Low und für den Spin-off bereinigtem Close | Nur die nachweislich betroffenen Felder ändern sich. Das Volumen folgt seiner eigenen belegten Basis. |
| Echter 2:1-Split und Spin-off am selben Tag | Aus 100 Mutteraktien werden 200. Eine untrennbare kombinierte Meldung wird zur Klärung ausgewiesen. |
| Zwei Spin-offs, Wiederholung des Imports und Wechsel der Kursquelle | Jeder Faktor wirkt je betroffenem Feld und Datum genau einmal. Ein erneuter Import reproduziert dieselben Werte. Die alte Entscheidung wird keinem neuen Feed oder Symbol ungeprüft zugewiesen. |

Beim Einarbeiten betrifft die Änderung neben §6.6 auch D13, die Einschränkungen in §13, den Administratordialog in §15.1 sowie die Tests und Abnahmekriterien in §16 und §18.7. Insbesondere sollte der Mischprovider-Test die resultierende Bewertung und Stückzahl prüfen.
