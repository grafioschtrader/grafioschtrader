# Abspaltungen mit mehreren Zielwertpapieren

**Code baseline:** backend 0.40.3, highest Flyway script `V0_40_3__performance_benchmark.sql`.

## 1. Anlass und Ziel

Am 16. Dezember 2024 wurden Canal+, Havas und Louis Hachette Group gleichzeitig von Vivendi abgespalten, je eine
Aktie jedes Zielwertpapiers pro Vivendi-Aktie. Vivendi bezeichnet Canal+ und Louis Hachette Group als
Teilabspaltungen und Havas als Sachausschüttung. Canal+ notiert in GBP, die beiden anderen in EUR. Banken haben
einzelne Zuteilungen nachträglich umbenannt und anders eingestuft, und auf die Sachausschüttung wurde zum Teil
Kapitalertragsteuer einbehalten.
[Vivendi, Informationen zum Börsenstart](https://www.vivendi.com/en/press-release/information-regarding-the-listings-of-canal-havas-and-louis-hachette-group/) ·
[Bankbelege im Forum](https://forum.portfolio-performance.info/t/spin-off-abrechnung-bei-brokern-beispiele-und-debugs-gesucht/39600)

Heute trägt eine `SecurityAction` vom Typ `SPIN_OFF` genau ein Zielwertpapier (`security_new`, `from_factor`,
`to_factor`, `cost_allocation`, `cost_allocation_source`, `tax_treatment_de`, `cost_allocation_de`).
`SpinOffActionService.record` lehnt eine zweite Abspaltung derselben Mutter am selben Tag ab
(`gt.security.action.spinoff.duplicate`). Würden drei Aktionen nacheinander erfasst, ginge jede vom Einstand aus,
den die vorherige übrig liess. Bei 1 000 EUR Einstand und Quoten von 20 % und 10 % erhielte die zweite Tochter
80 EUR statt 100 EUR, und der Fehler setzte sich bis in den Gewinn beim Verkauf fort.

**Ziel:** eine Wertpapieraktion pro Mutter und Ex-Tag mit **einer bis zehn Zuteilungen**. Alle Zuteilungen eines
Depots werden gemeinsam gebucht und storniert, alle Einstandsanteile stammen aus demselben Stand unmittelbar vor der
Aktion, und jede Zuteilung hat ihre eigene deutsche steuerliche Einordnung. Die Einziel-Abspaltung ist der Fall mit
genau einer Zuteilung. Ein eigener Pfad für sie bleibt nicht bestehen.

## 2. Datenmodell und Migration

### 2.1 Neue Tabelle `security_action_allotment`

Entity `SecurityActionAllotment` in `grafioschtrader-common/.../grafioschtrader/entities/`. Sie implementiert
`AdminEntity`, weil nur die Verwaltung schreibt. `SecurityAction` erhält
`@OneToMany(mappedBy = "securityAction", cascade = ALL, orphanRemoval = true, fetch = EAGER) List<SecurityActionAllotment> allotments`.

| Spalte | Typ | Regel |
|---|---|---|
| `id_security_action_allotment` | `INT AUTO_INCREMENT` | PK |
| `id_security_action` | `INT NOT NULL` | FK `fk_saal_action` → `security_action`, `ON DELETE CASCADE` |
| `id_security_new` | `INT NOT NULL` | FK `fk_saal_security` → `securitycurrency` |
| `isin_new` | `VARCHAR(12) NOT NULL` | denormalisiert zur Anzeige |
| `from_factor`, `to_factor` | `INT NOT NULL` | `> 0` |
| `cost_allocation` | `DOUBLE NOT NULL` | wirtschaftliche Quote, `0 < q < 1` |
| `cost_allocation_source` | `VARCHAR(255) NULL` | Beleg der Quote |
| `tax_treatment_de` | `TINYINT NOT NULL` | `SpinOffTaxTreatmentDe` |
| `cost_allocation_de` | `DOUBLE NULL` | abweichende deutsche Quote, `0 < q < 1` |

Der Unique Key `uq_saal_action_security (id_security_action, id_security_new)` stellt sicher, dass ein Zielwertpapier
pro Aktion nur einmal vorkommt. Die Zuteilung einer Zielbuchung ist deshalb über
`transaction.id_security_action_app → security_action_application.id_security_action` und
`transaction.id_securitycurrency` eindeutig bestimmt. `transaction` erhält keine neue Spalte.

### 2.2 Änderungen an bestehenden Tabellen

- `security_action`: Die Spalten `cost_allocation`, `cost_allocation_source`, `tax_treatment_de` und
  `cost_allocation_de` entfallen. `id_security_new`, `isin_new`, `from_factor` und `to_factor` gelten nur noch für
  `ISIN_CHANGE` und sind bei `SPIN_OFF` `NULL`. `isin_new` wird nullable (`@NotNull` am Feld entfällt, die Prüfung
  für `ISIN_CHANGE` übernimmt `SecurityActionService.createSecurityAction`). `note` wird auf `VARCHAR(4000)`
  erweitert (`@Size(max = 4000)`, Grenze in `SpinOffActionService.validateNote`), weil das Änderungsprotokoll je
  Zuteilung schreibt (§3.4).
- `security_action_application`: `cost_allocation` entfällt. `SpinOffActionService.correct` überschreibt den Wert
  heute bei jeder Korrektur in allen aktiven Anwendungen mit dem Wert der Aktion. Die Kopie hat also keine eigene
  Bedeutung. `SpinOffContext` und `GermanTaxSpinOff` lesen die Quoten künftig direkt aus den Zuteilungen.
  `id_transaction_sell` und `id_transaction_buy` werden bei `SPIN_OFF` nicht mehr gesetzt. `SpinOffApplyService`
  speichert dort heute nur die Buchungen des ersten Depots, und alle Lesewege laufen über
  `transaction.id_security_action_app` und `security_action_role`.
  `SimulationTenantService.copySpinOffApplication` kopiert keine Quote mehr.

### 2.3 Migration

Die Änderung wird an die höchste noch unveröffentlichte Migration angehängt. Ist 0.40.3 bereits veröffentlicht,
entsteht `V0_40_4__spin_off_allotment.sql`. Alle Schritte sind idempotent (CLAUDE.md, „Idempotent Flyway Migrations“):

1. `CREATE TABLE IF NOT EXISTS security_action_allotment (...)`.
2. Eine Prozedur kopiert die Daten nur, solange `security_action.cost_allocation` laut `INFORMATION_SCHEMA.COLUMNS`
   noch existiert: `INSERT INTO security_action_allotment (...) SELECT id_security_action, id_security_new, isin_new,
   from_factor, to_factor, cost_allocation, cost_allocation_source, COALESCE(tax_treatment_de, 0),
   cost_allocation_de FROM security_action a WHERE action_type = 1 AND NOT EXISTS (SELECT 1 FROM
   security_action_allotment x WHERE x.id_security_action = a.id_security_action)`.
3. Prüfung vor dem Umbau, mit Abbruch über `SIGNAL SQLSTATE '45000'`: Jede Aktion mit `action_type = 1` hat genau
   eine Zuteilung, und jede Buchung mit `security_action_role = 3` findet über Anwendung und `id_securitycurrency`
   ihre Zuteilung.
4. `UPDATE security_action SET id_security_new = NULL, isin_new = NULL, from_factor = NULL, to_factor = NULL WHERE
   action_type = 1`, `MODIFY isin_new VARCHAR(12) NULL`, `MODIFY note VARCHAR(4000) NULL`,
   `DROP COLUMN IF EXISTS` für die vier Spin-off-Spalten von `security_action` und für
   `security_action_application.cost_allocation`, `UPDATE security_action_application SET id_transaction_sell = NULL,
   id_transaction_buy = NULL` für Anwendungen von Spin-offs.

Vor der Auslieferung prüft ein schreibgeschütztes Skript die Schritte 2 und 3 gegen die Produktionsdatenbank. IDs von
Aktionen, Anwendungen und Buchungen, der Stornierungsstatus und die Kurshistorie bleiben unverändert. Das Feld
`history_basis` und die übrigen Kurshistorie-Felder bleiben am Aktionskopf.

### 2.4 Export, Kontolöschung, Mengengrenzen

- `MyDataExportDeleteDefinition.exportDefinitions`: `new ExportDefinition(SecurityActionAllotment.TABNAME,
  TENANT_USER.NONE, null, ExportDefinition.EXPORT_USE)` direkt nach `SecurityAction.TABNAME`. Die Tabelle hat kein
  `created_by`, und die Kaskade auf `security_action` erledigt das Löschen. `ExportDefinitionCoverageTest` muss grün
  sein.
- Kein `entity_limit`-Schlüssel, da `AdminEntity`. Die Obergrenze von zehn Zuteilungen pro Aktion ist eine Konstante
  `SpinOffActionService.MAX_ALLOTMENTS` und wird beim Speichern geprüft (`gt.security.action.spinoff.too.many`).
- Das Transaktionslimit pro Anwendung wird in §4.4 geregelt.

## 3. Erfassung und Korrektur durch die Verwaltung

### 3.1 Validierung (`SpinOffActionService`)

Für `record` und `correct` gilt:

- 1 ≤ Anzahl Zuteilungen ≤ `MAX_ALLOTMENTS`. Jedes Ziel ist eine Aktie (`isEquity`). Das Paar aus ISIN und Währung
  kommt nur einmal vor und ist nicht die Mutter. Für jedes Ziel gilt `requireRatio`.
- Jede wirtschaftliche Quote `q_k` liegt in `(0, 1)`, und `Σ q_k < 1`. Die Mutter behält einen positiven Rest.
- `tax_treatment_de` ist gesetzt. `cost_allocation_de` ist nur bei `COST_SPLIT` und `UNDETERMINED` erlaubt und wird
  sonst abgelehnt.
- Deutsche Restbedingung: Die wirksame Quote ist `q_k^DE = cost_allocation_de ?? cost_allocation`. Es muss gelten:
  `Σ q_k^DE über Ziele mit COST_SPLIT oder UNDETERMINED < 1`. `TAXABLE_DISTRIBUTION` und `ZERO_COST_BASIS`
  verbrauchen keinen steuerlichen Einstand der Mutter.
- Fehler nennen das betroffene Ziel: Die `DataViolationException` erhält ISIN und Name des Ziels als Argument. Die
  Meldungen `gt.security.action.spinoff.allocation.invalid`, `...child.invalid` und `...ratio.required` erhalten dafür
  einen Platzhalter `{0}`, neu ist `gt.security.action.spinoff.allocation.sum` für die beiden Summenregeln (EN und
  DE).

### 3.2 REST (`SecurityActionResource`)

Das Format ist ausschliesslich das neue. `SecurityAction` trägt bei `SPIN_OFF` die Liste `allotments`. Die
Schnittstelle hat keinen Fremd-Client, und ein paralleles Einziel-Format würde doppelte Schreibwege erzeugen.

- `POST /api/securityaction`: Für jede Zuteilung ohne `idSecuritycurrency` ruft die Resource vor der
  Datenbanktransaktion `SpinOffActionService.previewChild` auf. Doppelte ISIN/Währung und die Identität mit der Mutter
  werden vorher geprüft, damit keine unnötige Anbieterabfrage läuft. `record(action, Map<Integer, Security>
  proposalsByIndex, admin)` legt fehlende Ziele an und speichert Kopf und Zuteilungen. Die Duplikatsperre pro Mutter
  und Tag unter der Sperre auf der Mutter bleibt.
- `POST /api/securityaction/spinoff/allocation` ersetzt das bisherige `GET`. Der Body ist
  `SpinOffAllocationRequestDto(idSecurityOld, actionDate, List<Target(idSecurityNew, fromFactor, toFactor)>)`, die
  Antwort `SpinOffAllocationProposalDto(LocalDate priceDate, Double parentClose, List<TargetProposal(idSecurityNew,
  childClose, fxChildToParent, proposedAllocation)>)`.
- `PUT /api/securityaction/{id}` korrigiert (§3.4).
- Die interne Benachrichtigung in `notifyTenants` nennt alle Ziel-ISINs mit ihren Verhältnissen.

### 3.3 Quotenvorschlag (`SpinOffActionService.proposeAllocation`)

Gesucht wird der erste Tag `d` in `[actionDate, actionDate + 10]`, an dem die Mutter und alle Ziele einen gültigen
Schlusskurs haben und jeder benötigte Devisenkurs `fx_k(d)` (Ziel → Mutterwährung) vorhanden ist. Fehlt an einem
sonst gemeinsamen Kurstag einer der Devisenkurse, wird wie heute für das eine Ziel der nächste Tag geprüft. Mit
Split-Faktoren wie bisher (`splitFactor`) gilt:

```
v_k = (toFactor_k / fromFactor_k) × Z_k(d) × fx_k(d)        Wert der Zuteilung k je Mutterstück, Mutterwährung
q_k = v_k / (P(d) + Σ_j v_j)
```

Ohne gemeinsame Grundlage liefert die Methode `proposedAllocation = null` je Ziel, und die Verwaltung erfasst
belegte Quoten von Hand. `requireLaterHistoryDetermined` erhält die Mutter und alle Ziele.

### 3.4 Korrektur (`SpinOffActionService.correct`)

- **Ohne aktive Anwendung** (keine Anwendung mit `is_reversed = 0`) sind auch strukturelle Änderungen erlaubt: Ziele
  hinzufügen oder entfernen und Verhältnisse ändern. Mutter und Ex-Tag bleiben unveränderlich, ihre Änderung
  verlangt Löschen und Neuerfassung wie bisher.
- **Mit aktiver Anwendung** sind nur `cost_allocation`, `cost_allocation_source`, `tax_treatment_de`,
  `cost_allocation_de`, die Notiz und die Kurshistorie-Basis änderbar. Die Zuteilungen werden über ihre ID
  abgeglichen, und Menge, Ziele und Verhältnisse müssen unverändert sein (`gt.security.action.immutable`). Die
  gesamte Verteilung wird nach §3.1 neu geprüft. Da Anwendungen keine eigene Quote mehr tragen, wirkt die Korrektur
  bei der nächsten Auswertung auf alle aktiven Anwendungen.
- `describeAllocationChange` schreibt pro geänderter Zuteilung eine Zeile und stellt die ISIN voran:
  `<Datum>: <ISIN> allocation <alt> -> <neu>, DE <alt> -> <neu>, DE allocation <alt> -> <neu>, source changed`, jeweils
  nur mit den geänderten Teilen. Die Quelle selbst wird wie heute nicht wiederholt.
- Auswertungen rechnen immer mit der aktuellen Einordnung. Frühere und spätere Verkäufe eines Ziels werden also bei
  der nächsten Auswertung mit den Anschaffungskosten ausgewiesen, die aus der aktuellen Einordnung folgen.

## 4. Vorschau und Verbuchung (`SpinOffApplyService`)

### 4.1 Mengen und Kurse

- Die berechtigten Mutterstücke `N` je Depot werden einmal ermittelt (`entitlement`, unverändert). Für jedes Ziel
  gilt `r_k = toFactor_k / fromFactor_k`, die Zielstückzahl ist `n_k = N × r_k` (Bruchteile bleiben), und der
  Bruchteil ist `n_k − ⌊n_k⌋`.
- `P` ist der Mutterschlusskurs am Ex-Tag (unverändert). `Z_k` ist der erste gültige Schlusskurs des Ziels in
  `[actionDate, actionDate + 10]` an seinem Datum `d_k`. Beide gelten in der Stückelung des Ex-Tags
  (`calcSplitFatorForFromDate`).
- `fx_k→P` ist der Devisenkurs vom Ziel zur Mutterwährung am Tag `d_k`, an dem `Z_k` gilt. `fx_P→K` und `fx_k→K` sind
  die Kurse zur Kontowährung `K` am Ex-Tag, dem Buchungsdatum. Devisenpaare sind in GT lückengefüllt, ein exakter
  Tagesabruf genügt deshalb.
- Fehlt ein Kurs oder Devisenkurs, wird nicht gebucht. `gt.security.action.spinoff.no.child.price` und
  `gt.security.action.spinoff.no.fx.rate` nennen Ziel bzw. Währungspaar und Datum als Argument.

### 4.2 Buchungsbeträge (`SpinOffLegs`)

Pro gebuchtem Depot entstehen `2 + m` Buchungen: ein technischer Verkauf `SPIN_OFF_SALE` und ein Rückkauf
`SPIN_OFF_REPURCHASE` der Mutter über `N` Stück sowie ein Zugang `SPIN_OFF_CHILD` je Ziel über `n_k` Stück zum Kurs
`Z_k` mit `currencyExRate = fx_k→K`. Die Präzision `p` ist die der Kontowährung:

```
P_sale = P + Σ_k r_k × Z_k × fx_k→P                       theoretischer Verkaufskurs (Mutterwährung)
S      = round(N × P_sale × fx_P→K, p)                    Verkauf
R      = round(−N × P × fx_P→K, p)                        Rückkauf
C_k    = round(−N × r_k × Z_k × fx_k→P × fx_P→K, p)       Zugang k, über die Mutterwährung gerechnet
D      = −(S + R) − Σ_k C_k                               reiner Rundungsrest, |D| ≤ (m + 2)/2 Einheiten
```

`D` wird dem Zugang mit dem grössten `|C_k|` zugeschlagen, bei Gleichstand dem Ziel mit der kleinsten
`idSecuritycurrency`. Damit gilt `S + R + Σ C_k = 0` exakt, unabhängig von der Erfassungsreihenfolge. Da alle
Zugänge über dieselbe Kette `fx_k→P × fx_P→K` gerechnet werden, enthält `D` nur Rundung und keine
Kreuzkurs-Abweichung. Die Abweichung zur direkten Umrechnung trägt jeder Zugang selbst als
`cashaccountRoundingDiff_k = C_k − round(−n_k × Z_k × fx_k→K, p)` (`null` bei 0). Eine EUR-Zuteilung trägt deshalb
nie die GBP/CHF-Abweichung einer anderen Zuteilung. Bei `m = 1` und `d_1` gleich dem Ex-Tag entspricht das Ergebnis
der heutigen Rechnung.

### 4.3 Bereits manuell erfasste Buchungen

`SpinOffApplyService.manuallyRecorded` schlägt heute `SKIP` vor, sobald im Fenster ±10 Tage ein Zugang des Ziels
über die zugeteilten Stücke (±1) oder ein Verkauf mit anschliessendem Rückkauf der Mutterstücke vorliegt. Die
Zielprüfung wird für jedes Ziel `k` mit `n_k` einzeln ausgeführt:

- Mindestens ein erkanntes Ziel oder ein erkanntes Mutterpaar → Vorschlag `SKIP`. Die Vorschau zeigt je Ziel
  „erfasst“ (mit den Kandidaten) oder „fehlt“. `Candidate` erhält `idSecuritycurrency` statt `boolean child`.
- `BOOK` bleibt wählbar. Die Oberfläche verlangt dann eine Bestätigung, dass die markierten Buchungen bereinigt sind.
  Eine harte Sperre würde ein Depot unbuchbar machen, in dem kurz nach dem Ex-Tag regulär ein Ziel zugekauft wurde.
- Die Entscheidung gilt für alle Ziele eines Depots gemeinsam. Ein teilweises Buchen würde die Einstandserhaltung der
  Mutter brechen.

### 4.4 Schreiben, Sperren, Limits

- Die Vorschau liefert `planVersion`, einen SHA-256 über Aktions-ID, die Zuteilungen (ID, Ziel, Verhältnis, Quote,
  Einordnung), `P`, alle `Z_k`, `d_k` und Devisenkurse sowie `N` je Depot. `apply` baut den Plan unter
  `lockMonitoringTenant` und `findByIdForUpdate` neu auf und lehnt bei abweichender `planVersion` mit
  `gt.security.action.spinoff.preview.stale` ab. Das ersetzt den heutigen Vergleich nur der Mutterstücke in
  `validateDecisions`.
- `throwWhenTransactionLimitReached(tenant, (2 + m) × gebuchte Depots)`.
- Alle Buchungen aller Depots entstehen in einer Transaktion. Ein Fehler bei der letzten Zielbuchung hinterlässt keine
  Buchung. `requireUnapplied` und der Unique Key `uq_saa_action_tenant` schliessen eine zweite aktive Anwendung aus.
- `scheduleRebuild` bleibt (sanktionierter Bypass laut backend/CLAUDE.md).
- Ein Barausgleich für Bruchteile ist eine gewöhnliche Verkaufsbuchung des Anwenders zum tatsächlichen
  Abrechnungsbetrag. Sie gehört nicht zur Anwendung.

### 4.5 Einbehaltene Steuer auf eine steuerpflichtige Zuteilung

Zieht die Bank auf eine Zuteilung mit `TAXABLE_DISTRIBUTION` Kapitalertragsteuer oder Quellensteuer ein, erfasst der
Anwender den Betrag an der Zielbuchung. Eine zusätzliche Dividende würde den Ertrag doppelt zählen und scheitert ohne
Kurs an `s_quotation`, eine Gebühr verlöre die steuerliche Einordnung.

- `SpinOffDecisionDto` erhält `List<Withholding(idSecuritycurrency, taxAmount)>`. `taxAmount ≥ 0` ist in
  Kontowährung angegeben und nur für Ziele mit `TAXABLE_DISTRIBUTION` zulässig, höchstens `|C_k|`.
- An der Zielbuchung gelten `taxCost = taxAmount / fx_k→K` (Wertpapierwährung, wie bei jeder Buchung) und
  `cashaccountAmount = C_k − taxAmount`. Die Summe der Buchungen des Depots ist dann `−Σ taxAmount`. Das ist die
  tatsächliche Kontobelastung.
- Nachträgliche Änderung: `PUT /api/securityaction/{id}/spinoff/withholding` mit
  `List<(idTransaction, taxAmount)>`. Erlaubt ist das nur für `SPIN_OFF_CHILD`-Buchungen der eigenen aktiven
  Anwendung. Ein Wert > 0 setzt die aktuelle Einordnung `TAXABLE_DISTRIBUTION` voraus, 0 ist immer erlaubt (z. B.
  nach einer Erstattung infolge Umstufung). Der Endpunkt schreibt direkt wie `apply`, plant den Neuaufbau der
  Bestände ein und ruft `DailyLimitService.check`/`.log` für `Transaction` auf.
- Wirtschaftlich behandelt `SecurityGeneralCalc.calcSpinOffPosition` die Steuer der Zielbuchung wie die Quellensteuer
  einer Dividende (`calcDividend`): Sie mindert den Ertragserfolg der Position oder geht bei `excludeDivTax` in
  `excludedDivTax` ein. Sie ist kein Einstand.
- Wechselt die Verwaltung die Einordnung weg von `TAXABLE_DISTRIBUTION`, bleibt eine erfasste Steuer stehen. Die
  Steuerhilfe weist dann `WARN_SPIN_OFF_TAX_NOT_DISTRIBUTION` aus, bis der Anwender den Betrag korrigiert.

## 5. Wirtschaftlicher Einstand

### 5.1 Gruppenallokation (`SpinOffContext`, `SpinOffAllocator`)

`SpinOffContext.allocations` wird zu `Map<applicationId, Map<accountId, GroupAllocation>>` mit
`GroupAllocation(double allocatedCostTotal, List<CostLot> parentLotsAfter, Map<Integer idTarget, Allocation>
targets, boolean incomplete)`. Die Gruppe wird einmal aus dem Mutterstand unmittelbar vor dem Ex-Tag berechnet:

- `SPIN_OFF_SALE` entnimmt `allocatedCostTotal` und übernimmt `parentLotsAfter`. Dafür liest
  `SecurityGeneralCalc.calcSpinOffPosition` die Gruppe, nicht eine einzelne Zuteilung.
- `SPIN_OFF_CHILD` liest `targets.get(transaction.getIdSecuritycurrency())`.
- `SpinOffContext.resolve` erhält die Quoten aus `action.getAllotments()`. Die Prüfung
  `application.getCostAllocation() == null` entfällt.

**FIFO/LIFO:** `C` ist der Einstand der berechtigten Posten des Depots (`ledger.getCost()`). Die Zielbeträge sind
`A_k`, und `A = round(Σ q_k × C, p_P)` ist der Gesamtabgang. Die `A_k` entstehen aus `q_k × C` nach dem
Grösster-Rest-Verfahren in kleinsten Einheiten `10^−p_P`: Jedes Ziel erhält zunächst `⌊q_k × C⌋`, die fehlenden
Einheiten bis `A` gehen an die grössten Nachkommareste, bei Gleichstand an die kleinste `idSecuritycurrency`. Bei
`m = 1` gilt `A_1 = round(q × C)` wie heute. Wegen `Σ q_k < 1` ist `A ≤ C`. Die Kaufposten werden nach §5.2
verteilt. Jeder Zielposten behält Herkunft (`originIdTransaction`), Anschaffungszeitpunkt und die Stückzahl
`lot.units / N × n_k`. Nicht gedeckte Zielstücke werden wie bisher zu einem unvollständigen Posten.

**Durchschnittseinstand:** `A_k` wie oben, aber mit `C = N / averageUnits × averageCost`, also dem Anteil der
berechtigten Stücke am gemeinsamen Pool aller Depots mit `AVERAGE_COST`.

**Währungen:** Erhalten bleibt der Einstand in Mutterwährung. Danach wird jeder Zielbetrag in die Zielwährung
umgerechnet: `childCost_k = round(A_k × fx_P→k, p_k)`. Die Posten erhalten ihre Anteile mit
`SpinOffCostDistribution.distribute(childCost_k, moved_k, null, p_k)`, also ohne Obergrenze. Das historische investierte Kapital in Hauptwährung bleibt postenweise mit
dem Kaufkurs des Mutterpostens erhalten (`movedMain_ik = moved_ik × lot.exchangeRate`, wie heute je Posten).

### 5.2 Rundung über Kaufposten und Ziele

Für ein Ziel verteilt `SpinOffCostDistribution.distribute(total, weights, caps, precision)` den Betrag heute schon
nach dem Grösster-Rest-Verfahren mit dem Einstand der Posten als Gewicht und Obergrenze. `SpinOffAllocator` und
`GermanTaxLotEngine.spinOffCostSplit` verwenden es. Für mehrere Ziele teilen sich die Ziele die Kapazität der
Posten:

1. Die Ziele werden absteigend nach `A_k` abgearbeitet, bei Gleichstand nach aufsteigender `idSecuritycurrency`. Die
   Reihenfolge hängt also nicht von der Erfassung ab.
2. Jedes Ziel ruft `distribute(A_k, c, cap, p)` auf. Dabei ist `c_i` der ursprüngliche Einstand des Postens `i`
   (Gewicht), und `cap_i` ist die nach den vorherigen Zielen noch freie Kapazität (anfangs `c_i`).
3. `cap_i −= moved_ik`.

Damit gilt `Σ_i moved_ik = A_k` exakt. Kein Posten wird negativ, und ein Posten mit Einstand 0 gibt nichts ab.
`distribute` legt einen Rest, der beim Grösster-Rest-Schritt an der Obergrenze scheitert, in Ledger-Reihenfolge auf
Posten mit freier Kapazität. Ein Rest, der dann noch übrig bleibt, kann nur entstehen, wenn Posten Bruchteile der
kleinsten Einheit tragen und `C` nur wenige Einheiten beträgt. Er verringert `A_k` des betroffenen Ziels, und
`allocatedCostTotal` ist die Summe der tatsächlich verteilten Beträge. Bei `m = 1` ist das Ergebnis identisch mit
heute.

### 5.3 Positionsserie (`SecurityPositionSeriesReport`, `SecurityPositionSeries`)

Heute gibt `SecurityPositionSeries.spinOffTransfer` den gesamten Hauptwährungsabgang des `SPIN_OFF_SALE` zurück, und
`withParentInvestedMC` gibt ihn jedem Zielzugang. Bei drei Zielen erhielte jedes Ziel das investierte Kapital aller
drei, und der implizite Kaufkurs `investedMC / cost` der geerbten Posten wäre überhöht. Neu gilt:

- Die Zielposten (`CostLot` und `SpinOffLot`) tragen zusätzlich `parentCost`, den Abgang `moved_ik` in
  Mutterwährung.
- `spinOffTransfer(parentEvents, idSecurityActionApp, idTarget, childLots, …)` ordnet jedem Zielposten den Anteil
  `MC_i × moved_ik / Σ_j moved_ij` des Hauptwährungsabgangs seines Mutterpostens zu. Bei Durchschnittseinstand ist
  es der Anteil `removedMC × A_k / A`. Die Summe über alle Ziele ist der Abgang der Mutter.
- Technische Mutterbuchungen erzeugen weiterhin keinen realisierten Erfolg. Ein späterer Verkauf eines Ziels
  verbraucht nur dessen eigene Posten.

Die Bereinigung der historischen Mutterkurse (`history_basis`, `SpinOffProviderAdjustment`) und das Entfernen eines
vom Anbieter gemeldeten Splits (`SecuritysplitJpaRepositoryImpl.removeSplitsOfSpinOffs`) erfolgen einmal pro Aktion.
Sie lesen nur Mutter und Datum und bleiben unverändert.

## 6. Deutsche Steuerhilfe

### 6.1 Einordnungen

`SpinOffTaxTreatmentDe` erhält `ZERO_COST_BASIS((byte) 3)` mit dem NLS-Schlüssel `ZERO_COST_BASIS` (EN/DE).
Erweitert werden auch der TypeScript-Typ `SpinOffTaxTreatmentDe` in `security-action.model.ts` und die Optionsliste
`/spinoff/taxtreatments`.

| Einordnung | Mutterlose | Ziellos | Ertrag am Ex-Tag |
|---|---|---|---|
| `COST_SPLIT` | geben `q_k^DE` ihrer `costEur` und `preliminaryEur` ab | erben Anschaffungszeitpunkt und Bestandsschutz (§ 20 Abs. 4a Satz 7 EStG) | keiner |
| `UNDETERMINED` | wie `COST_SPLIT`, vorläufig | wie `COST_SPLIT`, als unvollständig markiert | keiner |
| `TAXABLE_DISTRIBUTION` | unverändert | ein Los zum Gegenwert, datiert auf den Ex-Tag | Gegenwert als Ertrag, einbehaltene Steuer aus `taxCost` |
| `ZERO_COST_BASIS` | unverändert | ein Los mit `costEur = 0`, datiert auf den Ex-Tag | keiner, Hinweis `WARN_SPIN_OFF_ZERO_COST` |

`ZERO_COST_BASIS` bildet die Einstufung deutscher Banken ab, bei der die Mutter 100 % der steuerlichen
Anschaffungskosten behält und der spätere Verkaufserlös des Ziels voll steuerpflichtig ist. Die wirtschaftliche Quote
bleibt davon unberührt.

### 6.2 Gruppenverarbeitung (`GermanTaxLotEngine`)

- `GermanTaxReportService.spinOffs` liefert `Map<applicationId, GermanTaxSpinOffGroup>` mit
  `Map<Integer idTarget, GermanTaxSpinOff(treatment, allocation)>` aus den Zuteilungen.
  `allocation = cost_allocation_de ?? cost_allocation`.
- `buildEvents` sammelt die Buchungen je `spinOffKey` in
  `SpinOffGroupLegs(Transaction sale, Transaction repurchase, List<Transaction> children)`. Das heutige
  `Transaction[3]`-Array, in dem eine zweite Zielbuchung die erste überschreibt, entfällt. Das Ereignis entsteht beim
  Verkauf.
- `spinOff(event)` verarbeitet in dieser Reihenfolge:
  1. Alle Ziele mit `COST_SPLIT` und `UNDETERMINED` gemeinsam: Verteilung der `costEur` der Mutterlose nach §5.2 in
     Cent mit `A_k = round(q_k^DE × Σ costEur)` (Grösster-Rest wie in §5.1). `preliminaryEur` wird ohne Rundung
     anteilig verteilt.
  2. Jedes Ziel mit `TAXABLE_DISTRIBUTION`: `spinOffDistribution(child, …)` übergibt `nz(child.getTaxCost())`
     statt `0.0` an `income`.
  3. Jedes Ziel mit `ZERO_COST_BASIS`: ein Los mit `costEur = 0`, ohne Ertrag.
- Unvollständigkeit: Fehlt zu einer Zuteilung die Zielbuchung oder fehlt der Rückkauf, wird die Gruppe mit
  `WARN_SPIN_OFF_INCOMPLETE` gekennzeichnet. Eine Zielbuchung ohne Verkauf bleibt `SPIN_OFF_CHILD` allein
  (`spinOffChildAlone`). `UNDETERMINED` kennzeichnet die betroffenen Lose und davon abhängige Veräusserungen wie heute.
- Neue Warnschlüssel in `GermanTaxKeys`: `WARN_SPIN_OFF_ZERO_COST` und `WARN_SPIN_OFF_TAX_NOT_DISTRIBUTION`, beide
  mit NLS-Text in EN und DE.

## 7. Storno (`SpinOffApplyService.reverse`)

`requireNoDependentBooking` prüft heute für das eine Ziel. Die Prüfung läuft künftig über alle Ziele der Aktion:

- Folgegeschäft: Sperrend ist jede Buchung eines beliebigen Ziels im selben Depot, die nach der Zielbuchung liegt
  (`Transaction.CALC_ORDER`) und nicht zur Anwendung gehört. Das gilt auch für einen Barausgleich von Bruchteilen.
  Die Meldung `gt.security.action.spinoff.reverse.blocked` nennt Datum, Ziel und Depot.
- Abhängige Aktion: Sperrend ist eine andere aktive Anwendung des Mandanten mit Aktionsdatum ≥ diesem Aktionsdatum,
  deren Mutter (`securityOld`), deren ISIN-Wechsel-Nachfolger (`securityNew`) oder eines deren Zuteilungsziele ein
  Ziel dieser Aktion ist. Die Meldung `gt.security.action.spinoff.reverse.blocked.action` nennt Datum und Ziel.
- Alle Systembuchungen der Anwendung, einschliesslich erfasster Steuer, werden in einer Transaktion gelöscht. Nach
  Rücknahme des Folgegeschäfts lässt sich die Gruppe vollständig stornieren und erneut anwenden. Ein erneutes Anwenden
  rechnet mit den aktuellen Kursen nach §4.1.

## 8. Oberfläche

- **`SecurityActionCreateDialogComponent`:** Das Kopfformular (Mutter, Ex-Tag, Notiz, Kurshistorie-Basis) steht
  oben, darunter eine eigene Kindkomponente auf Basis von `EditableTableComponent` für die Zuteilungen (ISIN,
  Währung, Instrument-Lookup, Verhältnis, Quote, Quelle, Einordnung, DE-Quote). Gemäss frontend/CLAUDE.md enthält
  keine Komponente Formular und Tabelle zugleich. Unter der Tabelle stehen die Anzeigen „Bei der Mutter verbleibend“
  `1 − Σ q_k` und „Steuerlich verbleibend“ `1 − Σ q_k^DE`. „Quoten vorschlagen“ füllt alle Zeilen aus einem Aufruf.
  Speichern ist erst bei gültiger Verteilung (§3.1) möglich. Mit aktiver Anwendung sind Ziel- und Verhältnisspalten
  gesperrt.
- **`SecurityActionTreetableComponent`:** Ein Spin-off ist ein aufklappbarer Knoten mit einer Kindzeile pro Zuteilung.
  Die Kopfzeile zeigt die Ziel-ISINs gekürzt und `Σ q_k`.
- **`SpinOffApplyDialogComponent` / `SpinOffPreviewTableComponent`:** Oben steht eine Tabelle der Ziele (Verhältnis,
  Kurs, Kursdatum `d_k`, Devisenkurs, Quote, Einordnung), darunter je Depot eine Zeile mit der gemeinsamen
  Entscheidung. Aufgeklappt zeigt sie je Ziel Stückzahl, Bruchteil, Betrag, den Status „erfasst/fehlt“ mit
  Kandidaten und bei `TAXABLE_DISTRIBUTION` das Feld für die einbehaltene Steuer. Ein Depot mit erkannten Buchungen
  verlangt für `BOOK` eine Bestätigung (§4.3). Die Einstandssumme der Mutter je Depot zeigt die Vorschau nicht, weil
  sie eine vollständige Neuberechnung aller Wertpapiere des Mandanten erfordern würde. Die Wirkung ist nach dem
  Buchen in den Auswertungen sichtbar.
- Eine Ansicht der Zielbuchung ermöglicht die nachträgliche Korrektur der Steuer (§4.5).

## 9. Entscheidungen

| Frage | Entscheidung | Begründung |
|---|---|---|
| Einziel-Kompatibilitätsformat | entfällt | einziger Client ist das eigene Frontend, parallele Felder hätten doppelte Schreibwege |
| Quote je Anwendung | entfällt | wurde bei jeder Korrektur überschrieben, war also nie mandantenspezifisch |
| FK von `transaction` auf die Zuteilung | nicht eingeführt | über Anwendung und Wertpapier eindeutig, ein FK wäre redundant und bräuchte eine Invariante |
| Tie-Breaker bei Rundung | `idSecuritycurrency` des Ziels | stabil, auch wenn die Reihenfolge der Erfassung ändert |
| Devisenkurs des Ziels | am Kursdatum `d_k` | Kurs und Umrechnung beziehen sich auf denselben Tag |
| Teiltreffer manueller Buchungen | Vorschlag `SKIP`, `BOOK` mit Bestätigung | harte Sperre blockiert Depots mit legitimen Zukäufen |
| Einstandsvorschau je Depot | entfällt | verlangt eine vollständige Neuberechnung, Wirkung ist nach dem Buchen sichtbar |
| Einbehaltene Steuer | an der Zielbuchung, nachträglich korrigierbar | belastet das Konto korrekt ohne doppelten Ertrag |
| Deutsche 0-EUR-Anschaffungskosten | Einordnung `ZERO_COST_BASIS` | realer Bankfall, berührt die wirtschaftliche Quote nicht |
| Wirtschaftliche Quote 0 % wegen einer Brokeranzeige | nicht zulässig | die Quote wirkt auf alle Mandanten, ein einzelner Depotbeleg rechtfertigt sie nicht. Steuerlich deckt `ZERO_COST_BASIS` den Fall ab |
| Obergrenze Zuteilungen | 10 | deckt reale Fälle (Vivendi 3), begrenzt Vorschau und Buchungszahl |

## 10. Abnahmekriterien

| Fall | Erwartetes Ergebnis |
|---|---|
| Zwei Ziele, 100 Mutterstücke zu 1 000 EUR, Verhältnisse 10:1 und 5:1, Quoten 20 % und 10 % | Mutter 100 Stück zu 700 EUR, Ziel A 10 Stück zu 200 EUR, Ziel B 20 Stück zu 100 EUR. Vier Systembuchungen ergeben 0 EUR auf dem Konto. Umgekehrte Erfassungsreihenfolge liefert identische Beträge. Verkauf aller 20 B zu 8 EUR ergibt vor Gebühren 60 EUR Gewinn. |
| Vivendi: 350 Mutterstücke EUR, Ziele Canal+ (GBP), Havas, Louis Hachette (EUR), je 1:1, Konto in CHF | Je 350 Stück, fünf Systembuchungen, eine Anwendung, Kontosumme 0 CHF. Die Kreuzkurs-Abweichung steht nur in `cashaccountRoundingDiff` der Canal+-Buchung. |
| Steuer Vivendi: Canal+ und Louis Hachette `COST_SPLIT`, Havas `TAXABLE_DISTRIBUTION` mit 25 EUR einbehaltener Steuer | Genau ein Ertrag in Höhe des Havas-Gegenwerts mit 25 EUR Steuer, Kontosumme −25 EUR. Die Mutterlose geben nur die beiden `COST_SPLIT`-Anteile ab. |
| 50 Kaufposten zu 0.03 EUR, zwei Ziele mit 20 % und 10 % | Ziele 0.30 EUR und 0.15 EUR, jeder Posten gibt höchstens 0.02 EUR ab, kein Posten negativ. |
| Zwei Kaufposten in Fremdwährung, zwei Depots, Bruchteile | FIFO, LIFO und Durchschnitt erhalten die Summen in Mutterwährung. Das investierte Kapital in Hauptwährung von Mutter und Zielen zusammen ist gleich dem der Mutter vorher. Ein späterer Spitzenverkauf ist dem richtigen Depot und Ziel zugeordnet. |
| Fehlender Kurs eines Ziels, fehlender Devisenkurs, geänderte Vorschau, überschrittenes Limit | Die Meldung nennt Ziel bzw. Währungspaar. Kein Versuch hinterlässt eine Systembuchung. Eine zweite aktive Anwendung entsteht nicht. |
| Zielbuchung importiert, Mutterpaar fehlt | Vorschlag `SKIP`, das Ziel ist als „erfasst“ markiert, `BOOK` nur mit Bestätigung. |
| Umstufung eines Ziels von `TAXABLE_DISTRIBUTION` auf `COST_SPLIT` | Gesamtkosten bleiben erhalten. Der Steuerreport ändert nur die vom Ziel abhängigen Beträge und warnt bei stehengebliebener Steuer. |
| Folgeverkauf von Ziel B | Storno gesperrt mit Datum, Instrument B und Depot. Nach Rücknahme des Verkaufs vollständig stornierbar und erneut anwendbar. |
| Migrierte Einziel-Aktion, aktiver und stornierter Mandant, ISIN-Wechsel | Stückzahlen, Buchungsbeträge, Historienfaktor, Steuerreport und Performance gleich wie vorher. Aktive Anwendungen lassen sich stornieren, stornierte erneut anwenden. ISIN-Wechsel unverändert. |

## 11. Tests

Wenige Tests, die falsche Geschäftsergebnisse aufdecken (CLAUDE.md, „Choosing useful tests“):

- `SpinOffAllocatorTest`: zwei Ziele aus demselben Stand, 50 Kleinposten mit geteilter Kapazität, Fremdwährung mit
  Hauptwährungserhaltung, Reihenfolgeunabhängigkeit.
- `SpinOffLegsTest`: drei Währungen mit CHF-Konto, Nullsumme und Lage der Kreuzkurs-Abweichung, Steuerabzug.
- `GermanTaxLotEngineTest`: gemischte Gruppe `COST_SPLIT` + `TAXABLE_DISTRIBUTION` + `ZERO_COST_BASIS`, fehlende
  Zielbuchung.
- `SecurityPositionSeriesTest`: investiertes Kapital über zwei Ziele.
- `SpinOffRecordingIntegrationTest` gegen die Testdatenbank: Unique Key, Kaskade, atomare Anwendung, Sperre des
  Stornos.
- Fixtures `spin_off.json`, `spin_off_apply.json` und `spin-off-securities.json` im Zuteilungsformat, mit einem Fall
  mit zwei Zielen. Die E2E-Specs `307-create-spin-off` und `312-apply-spin-off` werden auf mehrere Zuteilungen
  erweitert.
