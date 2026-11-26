# Review und Anregungen zu `Spin_Off_Mehrere_Zielwertpapiere.md`

**Geprüfte Code-Basis:** Backend `0.40.2`, höchstes Flyway-Skript `V0_40_3__performance_benchmark.sql`.

---

## 1. Gesamteinordnung: Was aus dem PP-Forum-Thread 39600 kann GT bewältigen?

Im Thread [Spin-off-Abrechnung bei Brokern: Beispiele und Debugs gesucht (39600)](https://forum.portfolio-performance.info/t/spin-off-abrechnung-bei-brokern-beispiele-und-debugs-gesucht/39600) geht es um reale Bankabrechnungen (u. a. DKB, ING, sBroker, flatex) zu komplexen Kapitalmaßnahmen wie **Vivendi → Canal+ / Havas / Louis Hachette Group**, **3M → Solventum**, **Thyssenkrupp → tk nucera**, **Tencent → JD.com**, **Sandvik → Alleima** und **E.ON → Uniper**.

Im Vergleich zu Portfolio Performance ist Grafioschtrader strukturell bereits sehr stark aufgestellt (Trennung von unbereinigter Mutter-Kurshistorie via `SpinOffProviderAdjustment`, automatischer Split-Filter in `SecuritysplitJpaRepositoryImpl.removeSplitsOfSpinOffs`, Lot-Vererbung mit Original-Anschaffungsdatum für FIFO/LIFO und Altbestände vor 2009 sowie eigene `GermanTaxLotEngine`). Mit der geplanten Erweiterung auf $1 \dots n$ Zielwertpapiere wird der Vivendi-Hauptfall im Kern lösbar.

Dennoch zeigt der Abgleich mit den realen Fällen aus Thread 39600 **vier fachliche Praxisprobleme**, die im aktuellen Entwurf noch fehlen oder zu eng gefasst sind:

| Praxisfall aus Thread 39600 | Stand mit dem vorliegenden Entwurf | Offene Lücke / Handlungsbedarf |
| --- | --- | --- |
| **1. Gleichzeitige Abspaltung mehrerer Töchter in verschiedenen Währungen und mit gemischter Steuerlogik** (Vivendi `EUR` → Canal+ an der LSE in `GBP`, Havas in `EUR` als Sachausschüttung, Louis Hachette in `EUR` als Abspaltung) | Grundsätzlich adressiert (ein Kopf, mehrere Zuteilungen, Einzel-Klassifikation je Ziel). | Kritische Berechnungsfehler bei Hauptwährungs-Übertrag im Positionschart (§ 2.1), Cross-Rate-Drifts bei 3 Währungen (§ 2.4) und Valutadaten von FX-Kursen (§ 2.5). |
| **2. Einbehaltene Quellensteuer / KapESt bei steuerpflichtiger Sachausschüttung (`TAXABLE_DISTRIBUTION`)** (z. B. Havas/Frankreich, 3M/Solventum, Tencent/JD.com, Schweizer Spin-offs) | **Nicht abgedeckt.** `SpinOffLegs` bucht zwingend cash-neutral (`0,00` Kontoeffekt, `taxCost = null`), und `GermanTaxLotEngine.spinOffDistribution` übergibt hartcodiert `tax = 0.0`. | Zieht der Broker Quellensteuer oder deutsche KapESt vom Geldkonto ein, kann der Nutzer dies nicht sauber buchen: Eine manuelle `DIVIDEND`-Buchung versteuert den Sachausschüttungsertrag in `GermanTaxLotEngine` **doppelt** (und scheitert bei `quotation = 0` am DB-Constraint `s_quotation`); eine `FEE`-Buchung verliert die Steuerqualifikation (`CATEGORY_FOREIGN_TAX` / Quellensteueranrechnung). Siehe § 2.6. |
| **3. Bundesweite steuerliche `0,00 EUR`-Anschaffungskosten ohne laufenden Kapitalertrag** (z. B. ING-Abrechnungen nach § 20 Abs. 4a Satz 5 EStG bzw. vorläufige WM-Datenservice-Einstufung: 100 % AK bleiben bei der Mutter, Tochter erhält 0 EUR AK) | **In Zeile 41 mit bloßer Broker-UI-Anzeige vermischt.** `costAllocationDe` erzwingt `> 0` und `< 1`. | Richtig ist: Eine fehlerhafte *Depotanzeige* eines einzelnen Brokers (flatex-UI zeigt 0 EUR trotz vorhandener Steuerdaten im Hintergrund) rechtfertigt keine 0-%-Quote. Davon zu unterscheiden ist aber eine *echte deutsche steuerliche 0-EUR-AK-Festsetzung nach WM Datenservice*, bei der wirtschaftlich `costAllocation > 0` gilt, steuerlich in DE aber `0 %` auf das Kind übergeht und beim späteren Verkauf der volle Erlös steuerpflichtig ist. Siehe § 2.7. |
| **4. Nachträgliche Korrektur / Storno durch Banken trotz bereits gebuchter Spitzenverkäufe** (DKB ändert Wochen später nur die Einstufung oder Abrechnung einzelner Vivendi-Töchter, nachdem Bruchstücke längst reguliert wurden) | Reine Quoten-/Steuerkorrektur geht über `SpinOffActionService.correct` ohne Storno; strukturelle Korrekturen erfordern `reverse`. | Sobald auch nur für **ein** Ziel ein Spitzenverkauf (`REDUCE` der Bruchstücke) gebucht wurde, blockiert `SpinOffApplyService.reverse` das Storno der gesamten Gruppe. Zudem sprengt das Änderungsprotokoll in `SecurityAction.note` (`VARCHAR(1024)`) bei 3 Zielen schon nach wenigen Korrekturen die Feldlänge (§ 3.5). |

---

## 2. Kritische fachliche, mathematische und algorithmische Befunde

### 2.1 Schwerwiegender Fehler im Positionschart: `SecurityPositionSeriesReport` übergibt 100 % des abgegangenen Hauptwährungs-Einstands an *jedes* Ziel
In Zeile 33 heißt es:
> *"`SecurityPositionSeriesReport` und `SecurityPositionSeries` ordnen jedem Ziel ausschließlich das zu ihm übertragene investierte Kapital zu."*

Der tatsächliche Mechanismus im Code funktioniert bei mehreren Zielen **nicht** ohne Umbau der Datenstrukturen:
- `SecurityGeneralCalc.calcSpinOffPosition` (`SecurityGeneralCalc.java:379-388`) zieht bei `SPIN_OFF_SALE` den **gesamten** Abgang über alle Ziele von der Mutter ab. `summary.transactionSpinOffLots` auf dem `SPIN_OFF_SALE`-Event enthält daher pro Mutter-Lot die **Summe** der an alle Ziele abgegebenen Kosten.
- `SecurityPositionSeriesReport.withParentInvestedMC` (`SecurityPositionSeriesReport.java:238-262`) ruft für jedes `SPIN_OFF_CHILD` `SecurityPositionSeries.spinOffTransfer(parentEvents, idSecurityActionApp, ...)` (`SecurityPositionSeries.java:357-373`) auf. Diese Methode liest `SPIN_OFF_SALE` und liefert den **gesamten** aus der Mutter ausgebuchten Hauptwährungsbetrag `investedMC` (sowie `investedMCByRoot`) zurück.
- **Folge bei 3 Zielen (z. B. Quoten 12 %, 10 %, 8 % = 30 % Gesamtabgang):** Jedes der drei Ziele erhielte im Chart volle 30 % des historischen Mutter-`investedMC` (zusammen 90 % statt 30 %!). Zusätzlich würde in `AccountState.spinOffChild` (`SecurityPositionSeries.java:473`) der implizite historische Kauf-Wechselkurs `lot.investedMC() / lot.cost()` um den Faktor $(\sum_j q_j) / q_k$ überhöht gespeichert, was bei jedem späteren Verkauf des Ziels falsche realisierte Hauptwährungsgewinne erzeugt.

**Empfehlung:** `SpinOffTransfer` (oder direkt `SpinOffAllocator`) muss den historischen Hauptwährungsbetrag pro Mutter-Lot **zielspezifisch** aufteilen, sodass über alle Ziele $k = 1 \dots m$ exakt $\sum_k \text{investedMC}_{i,k} = \text{removedMC}_i$ gilt.

---

### 2.2 Zweidimensionale Lot-Rundung in `SpinOffAllocator` und `GermanTaxLotEngine` (fehlende Formel & Negativ-Schutz)
Zeile 31 fordert richtig:
> *"Rundungsreste sind deterministisch zu verteilen, auch bei vielen kleinen Kaufposten. Kein Kaufposten mit positivem Einstand darf durch Rundung einen negativen Rest erhalten."*

Im aktuellen Einziel-Code (`SpinOffAllocator.java:54-61` und `GermanTaxLotEngine.java:589-598`) wird pro Lot `round(q * lot.cost, precision)` gerechnet und die gesamte Differenz zum gerundeten Gesamtbetrag blind dem **letzten Lot mit Kosten (`lastWithCost`)** zugeschlagen.
- **Schon bei einem Ziel bricht das bei Sparplänen mit vielen Klein-Lots:** Hat ein Depot 50 Kauf-Lots à `0,03 EUR` (Summe `1,50 EUR`) und $q = 20\,\%$, ist `allocated = round(0.20 * 1.50) = 0,30 EUR`. Jedes der ersten 49 Lots rundet `0.20 * 0.03 = 0,006` auf `0,01 EUR` (Summe `0,49 EUR`). Das 50. Lot erhält `remainder = 0,30 - 0,49 = -0,19 EUR` (negativer Einstand beim Ziel, Aufbuchung auf `0,22 EUR` bei der Mutter!). Bei Abrundung (`q = 15 %`, `0,0045 -> 0,00`) erhält das 50. Ziel-Lot `0,23 EUR > 0,03 EUR` und das 50. Mutter-Lot wird mit **`-0,20 EUR` negativ**!
- **Bei $m$ Zielen wird daraus ein 2D-Rundungsproblem** über Lots $i = 1 \dots L$ und Ziele $k = 1 \dots m$:
  1. Für jedes Ziel $k$ muss $\sum_{i=1}^L \text{moved}_{i,k} = \text{allocated}_k = \text{round}(q_k \cdot C_{\text{parent}}, p_{\text{parent}})$ gelten.
  2. Für jedes Lot $i$ mit $\text{cost}_i > 0$ muss $0 \le \text{moved}_{i,k}$ und $\sum_{k=1}^m \text{moved}_{i,k} \le \text{cost}_i$ gelten (Mutter-Resteinstand $\text{cost}_i - \sum_k \text{moved}_{i,k} \ge 0$).
  3. **Reihenfolgeunabhängigkeit:** Die Restverteilung darf weder vom letzten Lot allein noch von der Erfassungsreihenfolge der Ziele abhängen.

**Empfehlung für die Spezifikation:** Den konkreten Algorithmus vorgeben — z. B. **Largest-Remainder-Verfahren (Hare-Niemeyer / Matrix-Rundung)** in kleinsten Währungseinheiten ($10^{-p}$):
1. Zuerst pro Lot $i$ den Gesamtabgang $M_i = \lfloor (\sum_k q_k) \cdot \text{cost}_i \cdot 10^p \rfloor \cdot 10^{-p}$ bestimmen und die Differenz $\sum_k \text{allocated}_k - \sum_i M_i$ nach größtem Nachkommarest (Tie-Breaker: `lotIndex`, unter Beachtung der Obergrenze $M_i \le \text{cost}_i$) um je $+10^{-p}$ verteilen. Damit ist $\text{cost}_i - M_i \ge 0$ garantiert!
2. Danach (bzw. gekoppelt über Min-Cost-Flow / iteratives Spalten-Zeilen-Ausgleichen) jedes $M_i$ auf die Ziele $k$ verteilen, sodass sowohl alle Zeilensummen $M_i$ als auch alle Spaltensummen $\text{allocated}_k$ exakt eingehalten werden.
3. Danach jedes $\text{moved}_{i,k}$ nach demselben Muster in Zielwährung $k$ umrechnen, sodass $\sum_i \text{converted}_{i,k} = \text{round}(\text{allocated}_k \cdot \text{fx}_k, p_{\text{child},k})$ gilt.
4. Denselben Algorithmus in `GermanTaxLotEngine.spinOffCostSplit` für `costEur` (auf Cent) über alle `COST_SPLIT`- und `UNDETERMINED`-Ziele einer Gruppe verwenden.

---

### 2.3 Widerspruch zwischen Tie-Breaker „stabile Zielkennung“ (Zeile 25) und Reihenfolgeunabhängigkeit (Zeile 47)
- **Zeile 25:** *"Die Zielbuchung mit dem größten Gegenwert übernimmt die verbleibende Rundungsdifferenz, bei Gleichstand entscheidet die stabile Zielkennung."*
- **Zeile 47 (Abnahmekriterien):** *"Umgekehrte Erfassungsreihenfolge liefert dieselben Werte."*

Wenn mit „stabile Zielkennung“ die Primärschlüssel-ID der neuen Zuteilungszeile (`id_security_action_allotment`) gemeint ist, dreht eine umgekehrte Erfassungsreihenfolge genau diese IDs um — bei Gleichstand zweier Ziele (z. B. Vivendi: zwei Töchter je `1:1` mit identischem gerundetem Gegenwert) liefert die umgekehrte Erfassungsreihenfolge dann **andere** Werte!

**Empfehlung:** Als deterministischen Tie-Breaker überall **`securityNew.idSecuritycurrency`** (hilfsweise `isin` + `currency`) verwenden, niemals die Auto-Increment-ID oder Listenposition der Zuteilungszeile. Da jedes Zielwertpapier pro Aktion höchstens einmal vorkommen darf, ist `idSecuritycurrency` innerhalb der Aktion strikt eindeutig und völlig unabhängig von der Erfassungsreihenfolge.

---

### 2.4 Dreiecks-Währungsumrechnung in `SpinOffLegs` (`Cross-Rate-Drift` auf `cashaccountRoundingDiff`)
Im Vivendi-Fall notiert Vivendi in **EUR**, Canal+ an der LSE in **GBP**, Havas und Louis Hachette in **EUR**. Hat ein Schweizer Mandant ein Verrechnungskonto in **CHF**, treffen drei Währungen aufeinander (`EUR`, `GBP`, `CHF`).
Nach Zeile 25 gilt:
- $\text{salePrice} = \text{parentClose}_{\text{EUR}} + \sum_i r_i \cdot \text{childClose}_{i} \cdot \text{fx}(\text{child}_i \to \text{EUR})$
- $\text{saleAmount}_{\text{CHF}} = \text{round}(N \cdot \text{salePrice} \cdot \text{fx}(\text{EUR} \to \text{CHF}), p_{\text{CHF}})$
- $\text{repurchaseAmount}_{\text{CHF}} = \text{round}(-N \cdot \text{parentClose}_{\text{EUR}} \cdot \text{fx}(\text{EUR} \to \text{CHF}), p_{\text{CHF}})$
- Für jedes Ziel $i$ wird der natürliche Kontobetrag aber direkt mit $\text{fx}(\text{child}_i \to \text{CHF})$ gerechnet:
  $c_i = \text{round}(-\text{childUnits}_i \cdot \text{childClose}_i \cdot \text{fx}(\text{GBP} \to \text{CHF}), p_{\text{CHF}})$.

Weil in realen EOD-Marktdaten $\text{fx}(\text{GBP} \to \text{EUR}) \times \text{fx}(\text{EUR} \to \text{CHF}) \neq \text{fx}(\text{GBP} \to \text{CHF})$ ist (unterschiedliche Fixing-Zeitpunkte/Anbieter), ist die Differenz $-(\text{saleAmount} + \text{repurchaseAmount}) - \sum_i c_i$ bei größeren Positionen **keine Cent-Rundungsdifferenz**, sondern schnell mehrere CHF/EUR groß!
- Schlägt man diese gesamte Dreiecksdifferenz einer einzigen Zielbuchung zu (z. B. Havas in `EUR`, weil Havas zufällig den größten Gegenwert hat!), trägt eine reine `EUR`-Buchung plötzlich die `GBP/EUR/CHF`-Cross-Rate-Abweichung von Canal+ in ihrem `cashaccountRoundingDiff`!
- Noch schlimmer: Bei extrem ungünstiger Konstellation könnte bei kleinen Positionen der angepasste `cashaccountAmount` eines Ziels positiv statt negativ werden.

**Empfehlung:**
1. Entweder für `saleAmount - (-repurchaseAmount)` direkt die Summe der in Kontowährung bewerteten Zielgegenwerte verwenden ($\text{salePrice}$ aus der Kontowährungssumme rückrechnen bzw. `fxChildToParent` konsistent halten), oder die Differenz proportional / nur unter fremdwährungsbehafteten Zielen verteilen und prüfen, dass `childAmount_i < 0` bleibt.
2. Explizit dokumentieren, wie sich der **CSV-Transaktionsexport/-Re-Import** (`TransactionCsvExportGenerator`) verhält: Aktuell exportiert das CSV weder `securityActionRole` noch `cashaccountRoundingDiff`; ein Re-Import von System-Spin-off-Buchungen scheitert bei größerer `cashaccountRoundingDiff` an `Transaction.validateCashaccountAmount` (`gt.cashaccount.amount.calc`) und würde ohne Rolle Gewinn realisieren.

---

### 2.5 Kurstag-Logik bei `proposeAllocation` und `SpinOffApplyService.buildPlan` (inkl. bestehendem Bug in Zeile 168!)
1. **Bug im bestehenden Code (`SpinOffActionService.java:167-168`):**
   In `proposeAllocation` steht aktuell in der Tagesschleife über `[actionDate, actionDate + 10]`:
   ```java
   Double fx = childToParentRate(child.getCurrency(), parent.getCurrency(), quote.getDate());
   if (fx == null) {
     return NO_PROPOSAL;
   }
   ```
   Fehlt am ersten gemeinsamen Aktienhandelstag lediglich der EOD-Schlusskurs des Währungspaares, bricht die Methode die gesamte Suche mit `NO_PROPOSAL` ab, **statt mit `continue` den nächsten Tag im 10-Tages-Fenster zu prüfen**! Bei mehreren Zielen in verschiedenen Währungen steigt die Wahrscheinlichkeit dafür erheblich. Das muss in der Spezifikation auf Weiterprüfen des nächsten Kurstags (`continue`) gestellt werden.
2. **Stichtag der Devisenkurse bei der Buchung (`SpinOffApplyService.java:224, 237`):**
   Für die Buchung darf der erste gültige Kurs jedes Ziels $i$ an einem späteren Tag $d_i \in [\text{actionDate}, \text{actionDate} + 10]$ liegen (z. B. Handelsaufnahme erst an $T+1$ oder Börsenfeiertag). Aktuell liest `SpinOffApplyService` den Devisenkurs `exchangeRate(childCurrency, parentCurrency, date)` aber starr exakt am `actionDate` (`getHistoricalClosePriceForDate`, ohne Vortages-Fallback!).
   - Hat das Währungspaar am `actionDate` keinen Kurs (z. B. Feiertag bei der EZB/FX-Quelle, während die US-Börse offen war) oder weicht das Kursdatum $d_i$ des Ziels vom `actionDate` ab, sollte präzisiert werden: Welches FX-Datum gilt für Ziel $i$ ($d_i$ vs. `actionDate`) und ob `getClosePriceForDate` / letzter verfügbarer Schlusskurs $\le d$ herangezogen wird, statt hart mit `gt.security.action.spinoff.no.fx.rate` zu scheitern.
3. **Fehlermeldung je betroffenem Instrument (Zeile 24):**
   Aktuell wirft `SpinOffApplyService.invalid(...)` immer auf Feld `"action.date"` ohne Parameter (`gt.security.action.spinoff.no.child.price`). Bei mehreren Zielen muss die `DataViolationException` zwingend Name/ISIN des betroffenen Ziels als Argument übergeben, sonst weiß weder Nutzer noch Admin, bei welchem der 3 Ziele Kurse fehlen.

---

### 2.6 Steuerabzug / Quellensteuer bei `TAXABLE_DISTRIBUTION` und Validierung gemischter Steuergruppen
1. **Steuervalidierung bei `UNDETERMINED` in gemischten Gruppen (Zeile 35):**
   Zeile 35 fordert: *"Die Summe der wirksamen steuerlichen Aufteilungsquoten muss einen positiven Rest bei der Mutter zulassen."*
   Im Code (`GermanTaxLotEngine.java:550`) durchläuft **auch `UNDETERMINED`** den Zweig `spinOffCostSplit(...)` (vorläufige Aufteilung + `incomplete`-Markierung!).
   Deshalb muss die Server-Validierung in `SpinOffActionService` sicherstellen:
   $$\sum_{i:\,\text{treatment}_i \in \{\text{COST\_SPLIT},\,\text{UNDETERMINED}\}} q_i^{\text{eff,DE}} < 1$$
   wobei $q_i^{\text{eff,DE}} = \text{costAllocationDe}_i \neq \text{null} \ ? \ \text{costAllocationDe}_i : \text{costAllocation}_i$.
   Zugleich sollte für `TAXABLE_DISTRIBUTION` serverseitig erzwungen werden, dass `costAllocationDe == null` ist (ein gesetzter Wert wäre dort wirkungslos und irreführend).
2. **Quellensteuer / Kapitalertragsteuer auf Sachausschüttungen:**
   In `GermanTaxLotEngine.spinOffDistribution` (`GermanTaxLotEngine.java:646-647`) wird `income(..., value, 0.0, ...)` mit Steuer `0.0` gebucht. Zieht der Broker bei einer ausländischen Sachausschüttung (wie Havas in Frankreich oder Schweizer/US-Spin-offs) Quellensteuer vom Geldkonto ab, fehlt aktuell ein Buchungsweg in GT, der den Kontosaldo mindert und die Quellensteuer in `CATEGORY_FOREIGN_TAX` ausweist, ohne den Bruttoertrag durch eine zusätzliche `DIVIDEND`-Buchung zu verdoppeln. Zumindest ein klar definierter Buchungsweg (oder eine dokumentierte Abgrenzung für das Handbuch) gehört in das Konzept.

---

### 2.7 Unterscheidung bei `0,00 EUR`-Einstand: Broker-Anzeige vs. § 20 Abs. 4a Satz 5 EStG
Zeile 41 begründet überzeugend, warum eine bloße `0 EUR`-Anzeige im Web-Frontend eines einzelnen Brokers (wie im flatex-Beispiel zu Tencent/JD.com) keine globale `0 %`-Quote für `costAllocation` rechtfertigt.
Allerdings zeigen andere Belege in Thread 39600 (z. B. ING bei bestimmten US-Spin-offs wie 3M/Solventum oder Kapitalmaßnahmen ohne steuerneutrale Qualifikation und ohne Sachausschüttungsbesteuerung nach § 20 Abs. 4a Satz 5 EStG), dass **WM Datenservice bundesweit für alle deutschen Banken** steuerliche Anschaffungskosten von `0,00 EUR` für das neue Papier bei unveränderten `100 %` AK der Mutter festsetzen kann.
- Wirtschaftlich bleibt hier `costAllocation > 0` sinnvoll (Marktwertaufteilung für Performance/Einstand).
- Steuerlich in Deutschland wäre dafür ein vierter Wert in `SpinOffTaxTreatmentDe` (z. B. `ZERO_COST_BASIS`: Mutter behält 100 % ihrer `costEur`, kein laufender Kapitalertrag am Ex-Tag, neues Ziel-Lot startet am Ex-Tag mit `costEur = 0.0` und versteuert beim späteren Verkauf den vollen Erlös) eine sehr schlanke Erweiterung, die diesen realen deutschen Steuerfall exakt abdeckt, ohne die wirtschaftliche Quote auf 0 % zu öffnen!

---

### 2.8 Duplikat- und Teiltreffer-Erkennung in der Vorschau (`manuallyRecorded`, Zeile 26)
Zeile 26 lautet:
> *"Bei einem Teiltreffer zeigt die Vorschau die bereits erfassten und die fehlenden Ziele. Eine automatische Doppelbuchung ist gesperrt, bis der Anwender die Überschneidung bereinigt oder das Depot überspringt."*

Hier drohen **zwei gravierende Praxisprobleme**:
1. **Wann liegt überhaupt ein Treffer vor?**
   Aktuell verlangt `SpinOffApplyService.manuallyRecorded` (`SpinOffApplyService.java:286-300`), dass im Fenster `[date - 10, date + 10]` **sowohl** ein Verkauf + Rückkauf der Mutter über `n` Stück **als auch** ein `ACCUMULATE` des Ziels mit `abs(units - m) <= 1` existiert.
   Bei einem PDF-/CSV-Import von Bankbelegen existiert **niemals** ein Verkauf/Rückkauf der Mutter, sondern nur Einlieferungs-`ACCUMULATE`-Buchungen einzelner Ziele! Die Heuristik muss daher auf Ziel-Ebene prüfen, ob im Fenster eine manuelle `ACCUMULATE`-Buchung (ohne `idSecurityTransfer` und ohne `idSecurityActionApp`) für Ziel $i$ vorliegt.
2. **Harte Sperre von `Decision.BOOK` blockiert legitime Nachkäufe!**
   Kauft ein Anleger innerhalb von 10 Tagen vor oder nach dem Ex-Tag regulär Aktien eines Zielwertpapiers an der Börse nach (z. B. Zukauf von Canal+ am zweiten Handelstag nach dem Spin-off!), erkennt jede Fenster-Heuristik diese Kaufbuchung als Kandidaten/Teiltreffer.
   Wenn `Decision.BOOK` bei einem Teiltreffer **serverseitig hart gesperrt** wird (*"bis der Anwender die Überschneidung bereinigt oder das Depot überspringt"*), kann dieser Anleger den Spin-off für sein Depot **überhaupt nicht anwenden**, ohne vorher seinen echten Börsenkauf zu löschen!
   **Empfehlung:** Bei Voll- oder Teiltreffer in der Vorschau standardmäßig `Decision.SKIP` vorschlagen und die gefundenen Kandidaten pro Ziel deutlich hervorheben (`Candidate` benötigt dafür zusätzlich `idSecuritycurrency` / Instrumentenname statt nur `boolean child`!), dem Nutzer aber die bewusste Umstellung auf `Decision.BOOK` (ggf. mit Bestätigungshinweis) erlauben statt hart zu sperren.

---

## 3. Architektur-, Schema- und Code-Befunde in Grafioschtrader

### 3.1 Kein zweigleisiges „Einziel-Format“ mitschleppen (`SecurityAction` & `SecurityActionResource`)
Zeile 19 schlägt vor:
> *"Bisherige Einziel-Anfragen werden auf eine Zuteilung abgebildet. Die bisherigen Einzelfelder können bei genau einem Ziel weiterhin gelesen werden. Eine Änderung über das Einziel-Format darf keine weiteren Zuteilungen überschreiben."*

**Dringende Empfehlung dagegen:** `SecurityActionResource` hat keine externen Drittanbieter-Clients; Schreibzugriffe auf `SecurityAction` sind `@PreAuthorize("hasRole('ADMIN')")` und werden ausschließlich vom Angular-Frontend desselben Repositories genutzt.
Eine parallele Pflege der Einzelfelder auf `SecurityAction` neben einer Zuteilungsliste erzeugt unnötige Dual-Write-Komplexität und Fehlerquellen:
- Auf `security_action` bleiben `id_security_new`, `isin_new` (**muss in der Migration von `NOT NULL` auf `DEFAULT NULL` geändert werden!**, vgl. `gt_ddl.sql:2162` und `@NotNull` in `SecurityAction.java:69`), `from_factor`, `to_factor` **ausschließlich für `ISIN_CHANGE`**.
- Die vier reinen Spin-off-Spalten auf `security_action` (`cost_allocation`, `cost_allocation_source`, `tax_treatment_de`, `cost_allocation_de`) sollten nach der Flyway-Datenübernahme in die neue Zuteilungstabelle (z. B. `security_action_allotment`) **gedroppt** werden.
- Ebenso sollten auf `security_action_application` `id_transaction_sell` und `id_transaction_buy` für `SPIN_OFF` `NULL` bleiben (schon heute speichert `SpinOffApplyService.java:127-130` bei mehreren Depots willkürlich nur die IDs des ersten Depots; alle Lesewege laufen ohnehin über `transaction.id_security_action_app` und `transaction.security_action_role`).

---

### 3.2 Große Vereinfachung: Braucht es überhaupt mandantenbezogene Quotenzeilen und einen neuen FK auf `transaction`?
Im Entwurf (Zeile 16) werden faktisch **drei** Schema-Erweiterungen gefordert:
1. Zuteilungstabelle unter `security_action` (z. B. `security_action_allotment`).
2. Mandantenbezogene Quoten je Ziel unter `security_action_application` (*"Die Anwendung eines Mandanten speichert die auf jedes Ziel entfallende wirtschaftliche Quote"*).
3. Neuer Fremdschlüssel auf `transaction` (*"Jede erzeugte Zielbuchung verweist eindeutig auf ihre Zuteilung"*).

Prüft man den Code, sind **Punkt 2 und Punkt 3 überflüssig** (bzw. Punkt 3 zumindest optional):
- **Zu 2 (`SecurityActionApplication.costAllocation`):** Im heutigen Code wird `application.costAllocation` beim Anwenden aus `action.costAllocation` kopiert (`SpinOffApplyService.java:118`) und bei **jeder** Admin-Korrektur sofort wieder mit `action.costAllocation` überschrieben (`SpinOffActionService.java:225-226`). Es gibt keine mandantenindividuelle Abweichung (und Zeile 41 schließt diese auch fachlich aus). Entfernt man `security_action_application.cost_allocation` und liest die Quoten in `SpinOffContext` und `GermanTaxSpinOff` direkt aus den Zuteilungen von `SecurityAction`, **entfällt eine komplette zweite Datenbanktabelle inklusive JPA-Entity, Synchronisations-Update und `MyDataExportDeleteDefinition`-Join-SQL!**
- **Zu 3 (FK von `transaction` auf die Zuteilung):** Da `(id_security_action, id_security_new)` laut Zeile 16 **eindeutig** ist (`UNIQUE`), ist für jede `Transaction` mit `securityActionRole == SPIN_OFF_CHILD` die zugehörige Zuteilung bereits über `(transaction.id_security_action_app -> id_security_action, transaction.id_securitycurrency)` zu **100 % eindeutig identifiziert**! Ein zusätzlicher FK `transaction.id_security_action_allotment` wäre denormalisiert redundant (müsste per Invariante gegen `transaction.id_securitycurrency == allotment.id_security_new` abgesichert und bei Migration/Delete gepflegt werden). Will man ihn dennoch zur expliziten DB-Integrität einführen, darf er nur auf die globale Tabelle `security_action_allotment` zeigen (niemals auf eine mandantenbezogene Tabelle, um keinen neuen zirkulären FK beim Mandanten-Delete in `MyDataExportDeleteDefinition` zu erzeugen).

---

### 3.3 Pflichten aus `new-entity` (`MyDataExportDeleteDefinition`, `ExportDefinitionCoverageTest`, `LimitKeyConfig`)
Für jede neu eingeführte Tabelle (z. B. `security_action_allotment` in `grafioschtrader-common/.../entities/`):
1. **Modul-Regel:** Das Entity **muss** in `grafioschtrader-common` liegen (niemals in `grafiosch-base`, da `scripts/export-grafiosch-baseline.mjs` sonst die Tabelle in das wiederverwendbare Library-Schema exportiert).
2. **`MyDataExportDeleteDefinition` & `ExportDefinitionCoverageTest`:**
   - `security_action_allotment` muss in `MyDataExportDeleteDefinition.exportDefinitions` direkt **nach** `SecurityAction.TABNAME` (`MyDataExportDeleteDefinition.java:444`) eingetragen werden (`TENANT_USER.NONE, null, ExportDefinition.EXPORT_USE`; kein `created_by` auf der Kindtabelle nötig, `ON DELETE CASCADE` auf `fk_saa_action` Richtung `security_action`). Ohne diesen Eintrag bricht `ExportDefinitionCoverageTest.everyTableIsCoveredOrOmitted` den Build.
   - **Vorsicht bei `SECURITY_SELECT` / `SECURITYCURRENCY_S_SELECT` beim persönlichen Datenexport:** `SecurityAction` wird aktuell komplett exportiert (`TENANT_USER.NONE`), während `SECURITY_SELECT` (`MyDataExportDeleteDefinition.java:157-182`) nur Wertpapiere exportiert, die der Mandant in Transaktionen, Watchlists, Algos, Sparplänen oder Benchmarks referenziert. Hat ein Mandant die Mutteraktie im Bestand, den Spin-off vor dem Export aber **noch nicht angewandt**, fehlen neu angelegte Zielwertpapiere (`id_security_new` aus `security_action_allotment`) im Export-Dump (`foreign_key_checks = 0` beim Import versteckt den Fehler zunächst, bis `/api/securityaction/tree` in der Zieldatenbank mit fehlender `Security`-Referenz fehlschlägt!). `SECURITY_SELECT` / `SECURITYCURRENCY_S_SELECT` sollten daher zumindest Zielwertpapiere von `security_action`-Einträgen einschließen, deren Mutter im Mandantenbestand liegt (oder `security_action` beim Export entsprechend filtern).
3. **Mengengrenzen (`LimitKeyConfig`):** Da `SecurityAction` nur von `ROLE_ADMIN` angelegt wird (`AdminEntity`), ist kein `CREATOR`- oder `TENANT`-Limit in `entity_limit` erforderlich; dennoch sollte eine explizite Server-Obergrenze für die Anzahl Zuteilungen pro Aktion (z. B. `1 <= allotments.size() <= 20`) definiert werden.

---

### 3.4 Konkrete Code-Stellen im Backend, die angepasst werden müssen (inkl. bestehendem Storno-Bug!)
1. **`GermanTaxLotEngine.buildEvents` (`GermanTaxLotEngine.java:336-340, 375-387`):**
   Sammelt Spin-off-Buchungen aktuell in einem `new Transaction[3]`-Array über `t.getSecurityActionRole().ordinal()`. Ab dem zweiten `SPIN_OFF_CHILD` desselben Depots **überschreibt `legs[2]` die vorherige Zielbuchung stillschweigend**! Muss auf eine Gruppierung `SpinOffGroup(Transaction sale, Transaction repurchase, List<Transaction> children)` umgestellt werden, abgeglichen gegen die vollständige Zuteilungsliste der `SecurityAction`, damit auch eine fehlende einzelne Zielbuchung als unvollständig (`WARN_SPIN_OFF_INCOMPLETE`) erkannt wird.
2. **`SpinOffApplyService.reverse` (`SpinOffApplyService.java:154-170`) — Bestehender Bug ohne Datumsfilter!**
   Aktuell prüft `blockingAction`:
   ```java
   .filter(other -> childId.equals(other.getSecurityOld().getId())
       || (other.getSecurityNew() != null && childId.equals(other.getSecurityNew().getId())))
   ```
   - Erstens fehlt hier schon heute ein **Datumsfilter** (`!other.getActionDate().isBefore(action.getActionDate())`): Wurde ein Zielwertpapier *Jahre vor* dem Spin-off einmal durch einen ISIN-Wechsel erzeugt (und hält der Mandant davon Altstücke), blockiert dieser historische ISIN-Wechsel fälschlicherweise das Storno des späteren Spin-offs!
   - Zweitens muss die Prüfung über **alle** Ziel-IDs der zu stornierenden Aktion gegen `other.securityOld`, `other.securityNew` (für `ISIN_CHANGE`) **und alle Zuteilungen von `other`** (für andere `SPIN_OFF`s ab `actionDate`) laufen.
   - Drittens fordert Zeile 39 im Sperrfall die Nennung von *"Ziel, Depot soweit zuordenbar und Datum"* — die bestehende NLS-Meldung `gt.security.action.spinoff.reverse.blocked` nimmt aktuell nur `{0}` (Datum) entgegen und muss samt Parametern in `messages.properties` und `messages_de.properties` erweitert werden.
3. **Instrument-Lookup außerhalb der DB-Transaktion (`SecurityActionResource.createSecurityAction`, Zeile 79-94):**
   Der Provider-Lookup für noch nicht angelegte Zielwertpapiere dauert mehrere Sekunden und läuft deshalb bewusst **vor** der `@Transactional`-Methode `record(...)`. Bei $m$ Zielen muss `SecurityActionResource` vorab **alle** Zuteilungen ohne `idSecuritycurrency` auflösen (nach vorheriger Prüfung auf doppelte ISINs / Identität mit der Mutter, um unnötige externe HTTP-Aufrufe zu vermeiden).

---

### 3.5 Notiz-Längenlimit bei Korrekturen (`SpinOffActionService.correct`, Zeile 215-223)
`SpinOffActionService.correct` hängt bei jeder Quotenänderung pro geändertem Wert `"%s: allocation %s -> %s (%s)"` inklusive des bis zu 255 Zeichen langen `costAllocationSource` an `SecurityAction.note` (`VARCHAR(1024)`) an und wirft ab 1.024 Zeichen `gt.security.action.spinoff.note.too.long`.
Bei 3–5 Zielen kann schon ein einziger Korrekturdurchlauf 600–1.000 Zeichen erzeugen und jede weitere Korrektur dauerhaft blockieren.
**Empfehlung:** Entweder `security_action.note` in der Migration z. B. auf `VARCHAR(4096)` / `TEXT` erweitern oder im automatischen Protokolleintrag auf die Wiederholung von `costAllocationSource` verzichten (dieses steht bereits am Zuteilungsdatensatz selbst).

---

## 4. Frontend- und Vorschau-Anmerkungen

1. **Vorschau des Mutter-Einstands pro Depot (Zeile 23):**
   Zeile 23 verlangt in der Vorschau *"die Einstandssumme der Mutter vor und nach Verteilung, soweit sie für das Depot bestimmbar ist"*.
   **Hinweis zum GT-Code:** Bei `CostBasisMethod.AVERAGE_COST` führt `SecurityPositionSummary` (`SecurityGeneralCalc.java:38-39`) **einen gemeinsamen Durchschnittspool über alle `AVERAGE_COST`-Depots**, kein isoliertes `CostLedger` pro Depot. Zudem liest `SpinOffApplyService.preview` heute nur die Stückzahlen und Schlusskurse, ohne eine komplette Transaktions-Replay-Rechnung über alle Wertpapiere und Währungspaare des Mandanten anzustoßen. Falls diese Anzeige gewünscht bleibt, muss genau spezifiziert werden, wie sie bei `AVERAGE_COST` über mehrere Depots (`parentUnits / averageUnits * averageCost`) und bei fehlenden historischen FX-Kursen berechnet wird — oder ob darauf zugunsten einer schlanken, schnellen Buchungsvorschau verzichtet wird.
2. **Darstellung in `SecurityActionTreetableComponent` (`security-action-treetable.component.ts`):**
   Da die Ansicht bereits eine `ConfigurableTreeTableComponent` ist, bietet es sich an, Spin-offs mit mehreren Zielen als aufklappbaren Knoten (`leaf: false` mit Zuteilungs-Kindzeilen für `isinNew`, `fromFactor`, `toFactor`, `costAllocation`) darzustellen und auf der Spin-off-Kopfzeile die Summe der abgegebenen Quoten (`Σ costAllocation`) anzuzeigen.
3. **Admin-Dialog (`SecurityActionCreateDialogComponent`) & Strukturkorrekturen bei `appliedCount == 0`:**
   - Der heutige `600px`-Dialog mit flachem `<dynamic-form>` reicht für $1 \dots n$ Zuteilungen nicht aus; der Dialog sollte verbreitert werden, eine dynamische Zuteilungsliste (Hinzufügen/Entfernen von Zielen, Lookup pro Zeile, gemeinsamer Button „Quoten vorschlagen“ für alle Zeilen zugleich) sowie eine Live-Summenanzeige für den verbleibenden Mutteranteil (`100 % − Σ costAllocation` bzw. steuerlich `100 % − Σ costAllocationDe_eff`) bieten. Neuen Dialogcode gemäß `frontend/CLAUDE.md` auf Signal-Basis (`<signal-dynamic-form>` / Signals) aufbauen.
   - Solange **`appliedCount == 0`** ist (keine aktive oder historische Anwendung vorhanden, oder zumindest keine aktive Anwendung), wäre es sehr anwenderfreundlich, im Edit-Dialog auch das Korrigieren eines versehentlich falschen `fromFactor`/`toFactor` oder eines vergessenen Ziels zuzulassen, statt Löschen und komplette Neueingabe aller Ziele zu erzwingen.
4. **Anwendungs-Vorschau (`SpinOffApplyDialogComponent` / `SpinOffPreviewTableComponent`):**
   - Oberhalb der Depottabelle statt einzelner `childClose`-Felder eine kompakte Tabelle aller Zielwertpapiere mit Bezugsverhältnis, Kurs, Kursdatum, FX-Kurs und Quote anzeigen.
   - In der Depottabelle pro Depot eine gemeinsame Entscheidung (`BOOK` / `SKIP`) führen und die Zielbuchungen (`childUnits`, `fraction`, `fxChildToCash`, `childAmount`) strukturiert je Ziel unterhalb bzw. innerhalb der Depotzeile ausweisen.

---

## 5. Formale Hinweise gemäß GT-`specification`-Standard

Damit `Spin_Off_Mehrere_Zielwertpapiere.md` direkt als Implementierungsvorlage im Sinne von `.agents/skills/specification/SKILL.md` dienen kann, sollten bei der Überarbeitung noch folgende Punkte ergänzt werden:

1. **Konkrete Bezeichner statt reiner Prosa:**
   Aktuell ist das Dokument eine sehr gute fachliche Zusammenfassung (52 Zeilen), lässt aber Tabellennamen, Spaltennamen, SQL-Typen, Constraints, Entity-Klassennamen, geänderte REST-Endpunkte (z. B. Signatur von `/api/securityaction/spinoff/allocation` für mehrere Ziele: `POST` mit Request-DTO statt `GET` mit Einzelparametern!) und die exakten Record-Signaturen von `SpinOffPreviewDto`, `SpinOffAllocationProposalDto` und `GermanTaxSpinOff` offen.
2. **Idempotentes Flyway-Migrationsmuster (`CLAUDE.md`):**
   Konkretes Migrationsdesign festhalten (`CREATE TABLE IF NOT EXISTS`, `INSERT IGNORE` / idempotente Datenübernahme aus `security_action` über ein Kriterium wie `NOT EXISTS (...)`, gefolgt von `ALTER TABLE security_action MODIFY COLUMN isin_new VARCHAR(12) DEFAULT NULL` und `DROP COLUMN IF EXISTS` für die abgelösten Einziel-Spalten — niemals `gt_ddl.sql` manuell editieren).
3. **Entscheidungstabelle für offene Designfragen:**
   Insbesondere zu:
   - Wegfall von `security_action_application.cost_allocation` und Verzicht auf redundante Einziel-Felder (§ 3.1 / § 3.2),
   - Umgang mit Quellensteuer/KapESt bei `TAXABLE_DISTRIBUTION` (§ 2.6) und optionalem `ZERO_COST_BASIS` für § 20 Abs. 4a Satz 5 EStG (§ 2.7),
   - Warnung + Default `SKIP` statt harter Buchungssperre bei Kandidaten im ±10-Tages-Fenster (§ 2.8),
   - Verzicht oder genaue Definition der depotweisen Mutter-Einstandsanzeige in der Vorschau (§ 4.1).
4. **Aufräumhinweis nach Einarbeitung:**
   Gemäß `specification`-Skill („Rule 0 — a specification always reads as a first draft“) wird diese Begleitdatei `Spin_Off_Mehrere_Zielwertpapiere_g.md` wieder gelöscht, sobald die gewünschten Punkte nahtlos in `Spin_Off_Mehrere_Zielwertpapiere.md` eingearbeitet sind.
