# Offene Punkte der historischen Wiederholung und der Portfolio-Überwachung

**Code baseline:** backend 0.37.2, highest Flyway script `V0_37_3__drop_securityaccount_lowest_transaction_cost.sql`.

GitHub-Issue #255, Meilenstein V0.38.0. Ausserhalb des Umfangs: Vorwärtsszenarien (#246) und die Leistungsarbeit an der
Wiederholung (#254).

Abkürzungen für Pfade:

| Kürzel | Pfad |
|---|---|
| `S` | `backend/grafioschtrader-server/src/main/java/grafioschtrader` |
| `C` | `backend/grafioschtrader-common/src/main/java/grafioschtrader` |
| `T` | `backend/grafioschtrader-server/src/test/java/grafioschtrader` |
| `R` | `backend/grafioschtrader-server/src/main/resources` |
| `F` | `frontend/src/app` |
| `M` | `backend/grafioschtrader-common/src/main/resources/message/messages{,_de}.properties` |

---

## 1. Zweck

Die Simulationsumgebung, die historische Wiederholung und die Portfolio-Überwachung mit Soll-/Ist-Allokation sind
ausgeliefert. Offen sind Konfigurationsoptionen, die das Modell deklariert, aber nicht ausführt, Kosten und
Kennzahlen, die die Wiederholung nicht abbildet, und Unstimmigkeiten im Alarmtext, im Javadoc und in den Tests der
Überwachung. Diese Spezifikation beschreibt die Umsetzung in acht unabhängigen Etappen (§3). Jede Etappe lässt sich
einzeln ausliefern.

## 2. Entscheidungen

| # | Thema | Entscheidung | Begründung |
|---|---|---|---|
| E1 | Einstiegstypen `breakout`, `indicator_signal` | Aus `EntryType` und dem Draft-Schema entfernen; `dip_buy` bleibt der einzige Wert. | `MeanReversionConfigValidator` lässt nur `dip_buy` zu; ein anderer Einstieg bräuchte eigene Konfigurationsfelder und eine eigene Entscheidungslogik, für die kein Bedarf besteht. |
| E2 | Drawdown-Limit auf Portfolioebene | Wird nicht eingeführt. | Mean Reversion setzt Kapital nur innerhalb von AlgoTop-, Bucket- und Wertpapierbudget ein, begrenzt durch `max_position_exposure_pct`, Cooldowns und `max_trades_per_asset_per_30d`; der Einsatz ist damit nach oben beschränkt. Die Live-Überwachung erzeugt nur Signale. In der Wiederholung ist der Drawdown eine Kennzahl, deren Dauer (§3.5) und Verlauf (§3.6) diese Spezifikation ergänzt. Eine Portfolioregel gehörte ohnehin nicht in die wertpapierbezogene `risk_controls`, sondern auf die AlgoTop-Ebene. |
| E3 | `block_entry_if_exposure_exceeded`, `block_add_if_exposure_exceeded` | Aus `RiskControlsConfig`, beiden Schemas und den gespeicherten Konfigurationen entfernen. | Das Exposure-Limit gilt immer (`AlgoMeanReversionDecisionService`, `AlgoAverageDownModule`, `AlgoMeanReversionFillBudgetService`). Ein Schalter, der es aufheben könnte, widerspräche der Budgetlogik. |
| E4 | Sollzinsen in der Wiederholung | ACT/360, tägliche Abgrenzung auf dem negativen Tagesendsaldo, Belastung als `INTEREST_CASHACCOUNT` am letzten Kalendertag jedes Monats und am Enddatum. | Übliche Bankkonvention für Kontokorrentkredite; monatliche Belastung entspricht der Kontoabrechnung. |
| E5 | Alarmtext der Mail | Übersetzter Text in der Sprache des Empfängers, aus den strukturierten Alarmdetails. | Die `algo.alarm.*`-Schlüssel existieren; die Details sind bereits JSON (die Spalte erzwingt es). |
| E6 | Kosten der Wertschriften-Daueraufträge in der Wiederholung | Die fixen Kosten oder Kostenformeln des Dauerauftrags selbst, wie bei der Live-Ausführung; nicht das Gebührenmodell des Depots. | Der Dauerauftrag beschreibt, was der Benutzer erwartet; Live und Wiederholung rechnen gleich. |
| E7 | Drawdown-Dauer | Längste Unterwasserphase in Kalendertagen: vom Höchststand bis zum ersten Tag, an dem der cash-flow-bereinigte Vermögensindex ihn wieder erreicht, oder bis zur letzten Beobachtung. | Kalendertage entsprechen der Konvention der annualisierten Rendite in `AlgoReplayMetrics`. |
| E8 | Datenquelle der Equity-Kurve | Die Tagesbewertungen, aus denen `AlgoReplayMetrics` rechnet, persistiert als JSON am Laufergebnis. | Die Werte existieren nur im Speicher des Laufs (`AlgoReplayState.equity`); eine Neuberechnung wäre eine zweite Bewertung. |

## 3. Etappen

Reihenfolge nach Aufwand und Abhängigkeit. Jede Etappe ist in sich vollständig.

| Etappe | Issue-Punkte | Inhalt |
|---|---|---|
| §3.1 A | 10, 11, 1, 3 | Bereinigung: Javadoc, Bedingung der manuellen Auswertung, Einstiegstypen, Risiko-Schalter |
| §3.2 B | 9 | Übersetzter Alarmtext und gültige JSON-Alarmdetails |
| §3.3 C | 8 | Top-Ebene und impliziter Cash-Sollwert im Überwachungsbericht |
| §3.4 D | 12 | Test des `REBALANCE_DRIFT`-Signals bis zur Deduplizierung |
| §3.5 E | 6 | Maximale Drawdown-Dauer |
| §3.6 F | 7 | Equity-Kurve des Laufs |
| §3.7 G | 5 | Sollzinsen |
| §3.8 H | 4 | Wertschriften-Daueraufträge in der Wiederholung |

### Flyway-Regel für alle Etappen

- SQL kommt in das höchste **noch nicht veröffentlichte** Skript unter `R/db/migration/`. Der Dateiname wird um den
  Inhalt erweitert. Ist keines offen, entsteht ein neues Skript in der Serie der aktuellen `pom.xml`-Version.
- Jede **Schemaänderung** erhält ein Gegenstück in `backend/grafioschtrader-server/src/test/resources/db/migration/test/`
  (nächste `V<n>__…`-Nummer, oder im noch nicht veröffentlichten höchsten Testskript).
- Idempotenz gemäss `CLAUDE.md` (`ADD COLUMN IF NOT EXISTS`, `JSON_CONTAINS_PATH`-Bedingungen).

---

### 3.1 Etappe A – Bereinigung

#### 3.1.1 Javadoc von `AlgoSignalKind` (Punkt 10)

`C/types/AlgoSignalKind.java`:

- Die Zusätze „Reserved, not produced yet." bei `ENTRY_SIGNAL`, `PROFIT_TAKE`, `STOP_LOSS`, `REBALANCE_DRIFT`,
  `RISK_BREACH` entfallen. Jede Konstante nennt ihren Erzeuger:
  - `ENTRY_SIGNAL`, `PROFIT_TAKE`, `STOP_LOSS`, `RISK_BREACH` → `AlgoMeanReversionEvaluationService.signalKind`
  - `REBALANCE_DRIFT` → `AlgoRebalancingService.notifyActionable`
- Der Klassenabsatz „Only the alert kinds are produced today; the trading kinds are reserved …" wird ersetzt: Alle
  Werte werden erzeugt; die Nummern sind Teil von `algo_message_alert.alarm_type` und dürfen nicht wiederverwendet
  werden.

#### 3.1.2 Manuelle Auswertung prüft die Aktivierbarkeit (Punkt 11)

`S/service/AlgoRebalancingService.java`:

- Neue private Methode `hasActivatableRebalancingStrategy(AlgoTop algoTop)` mit dem heutigen Stream aus `isDueToday`:
  `findByIdAlgoAssetclassSecurityAndIdTenant(...)` → `AS_HOLDING_TOP_REBALANCING && isActivatable()`.
- `isDueToday` ruft sie auf, statt den Stream selbst zu enthalten.
- `evaluateForTenant` filtert `isMonitored(algoTop) && hasActivatableRebalancingStrategy(algoTop)`. Ein Entwurf
  erhält damit weder einen gespeicherten Plan noch ein Signal, genau wie im täglichen Pfad. Der Bericht vergleicht
  weiterhin auf Anfrage (`plan(...)` bleibt unverändert).

Test: `T/service/AlgoRebalancingServiceTest` – eine Rebalancing-Strategie mit `activatable = false` führt in
`evaluateForTenant` zu keinem `algoRecommendationWriter`- und keinem `algoAlarmRecorder`-Aufruf.

#### 3.1.3 Einstiegstyp auf `dip_buy` reduzieren (Punkt 1, E1)

- `C/algo/strategy/model/complex/enums/EntryType.java`: nur noch `dip_buy`.
- `R/schemas/mean-reversion-dip-draft-schema.json`, `EntryConfig.type`: `"enum": ["dip_buy"]`, Beschreibung
  „Entry strategy type; dip_buy is the only type".
- `R/schemas/mean-reversion-dip-schema.json`, `EntryConfig.type`: Beschreibung ohne „breakout, or indicator_signal".
- `MeanReversionConfigValidator.validateEntry` bleibt fachlich gleich; die Prüfung `nullOr(c.entry.type,
  EntryType.dip_buy)` ist nach der Enum-Kürzung trivial erfüllt, bleibt aber als Schutz stehen. Der Klassen-Javadoc
  nennt den Einstiegstyp nicht mehr unter „settings with a single executable value", sondern als einzigen Wert.
- Flyway (nur Daten, kein Testskript nötig – die Testdaten enthalten keine `algo_strategy`-Zeilen):

```sql
UPDATE algo_strategy SET strategy_config = JSON_REMOVE(strategy_config, '$.entry.type')
WHERE algo_strategy_impl = 68 AND JSON_VALID(strategy_config)
AND JSON_UNQUOTE(JSON_EXTRACT(strategy_config, '$.entry.type')) IN ('breakout', 'indicator_signal');
```

Ein Entwurf mit einem der beiden Werte wird dadurch zu einem Dip-Entwurf. Er bleibt ein Entwurf, weil
`activatable` nicht verändert wird.

#### 3.1.4 Risiko-Schalter entfernen (Punkt 3, E3)

- `C/algo/strategy/model/complex/RiskControlsConfig.java`: Felder `block_entry_if_exposure_exceeded` und
  `block_add_if_exposure_exceeded` löschen. Der Klassen-Javadoc sagt: Das Positions-Exposure-Limit gilt für jeden
  Einstieg und jeden Nachkauf.
- Beide Schemas: die zwei Properties unter `RiskControlsConfig` löschen (Strict: Zeilen 1207–1214; Draft: Zeilen
  480–487). Die Beschreibung von `RiskControlsConfig` im Strict-Schema lautet „Risk management parameters of one
  position – exposure and drawdown limits" (heute fälschlich „portfolio-level").
- Flyway (nur Daten). `StrategyConfigValidator.executable` liest mit `FAIL_ON_UNKNOWN_PROPERTIES`; ohne die Migration
  würde jede gespeicherte Strategie mit einem der Schalter unlesbar:

```sql
UPDATE algo_strategy SET strategy_config = JSON_REMOVE(strategy_config,
  '$.risk_controls.block_entry_if_exposure_exceeded')
WHERE algo_strategy_impl = 68 AND JSON_VALID(strategy_config)
AND JSON_CONTAINS_PATH(strategy_config, 'one', '$.risk_controls.block_entry_if_exposure_exceeded');
UPDATE algo_strategy SET strategy_config = JSON_REMOVE(strategy_config,
  '$.risk_controls.block_add_if_exposure_exceeded')
WHERE algo_strategy_impl = 68 AND JSON_VALID(strategy_config)
AND JSON_CONTAINS_PATH(strategy_config, 'one', '$.risk_controls.block_add_if_exposure_exceeded');
```

- `algo_simulation_result.hierarchy_snapshot` bleibt unverändert: Es wird nur als Baum gelesen
  (`AlgoHistoricalReplayService`, `jsonMapper.readTree`), nie als `StrategyConfig`.

Tests: Die Fixtures unter `backend/grafioschtrader-server/src/test/resources/testdata/*-strategy.json` enthalten keine
der entfernten Werte. Ein Test in `T/service/AlgoMeanReversionDecisionServiceTest` bestätigt, dass
`StrategyConfigValidator.executable` eine Konfiguration mit `block_entry_if_exposure_exceeded` als unbekanntes Feld
und eine mit `entry.type: breakout` als ungültigen Enum-Wert ablehnt.

Handbuch: `content/algoalert/strategy/meanreversiondip/` erwähnt keinen der entfernten Werte. Ein Satz beim
Positions-Exposure-Limit hält fest, dass es immer gilt.

---

### 3.2 Etappe B – Übersetzter Alarmtext und gültige Alarmdetails (Punkt 9, E5)

#### 3.2.1 Alarmdetails sind immer gültiges JSON

`algo_message_alert.alarm_details` ist `LONGTEXT … CHECK (json_valid(alarm_details))`. Zwei Erzeuger verletzen das:

| Erzeuger | Heute | Folge |
|---|---|---|
| `AlgoMeanReversionEvaluationService.evaluate` (Zeile 135–137) | `identity + " | " + rationale + " | units=" + … + " | price=" + …` | Der `INSERT` von `AlgoMessageAlert.recordSignal` scheitert am CHECK; kein Mean-Reversion-Signal wird gespeichert. |
| `AlgoAlarmEvaluationService` Ausdrucksalarm (Zeile 401–402) | `String.format("{\"expression\":\"%s\",…}")` mit unmaskiertem Ausdruck | Ein `"` oder `\` im Ausdruck erzeugt ungültiges JSON. |

Regel: Alarmdetails werden mit Jackson gebaut (`tools.jackson.databind.json.JsonMapper`, `ObjectNode`), nie mit
`String.format`. Die neue Form der Mean-Reversion-Details:

```json
{"action":"ENTRY","reason":"MEAN_REVERSION_ENTRY","units":12.0,"price":45.6,"tranche":"","identity":"1:2:3:0:2026-09-28:ENTRY:1"}
```

`reason` ist der bare Schlüssel aus `Decision.rationale()`; `tranche` ist `Decision.tranche()` (leer ausser bei
`SCALE_OUT`). Die übrigen `String.format`-Erzeuger in `AlgoAlarmEvaluationService` (Zeilen 182, 192, 221, 228, 244,
281, 327, 358, 367) setzen nur Zahlen und feste Wörter ein. Sie bleiben, werden aber in derselben Etappe auf
dieselbe Jackson-Hilfsmethode umgestellt, damit alle Erzeuger gleich arbeiten.

#### 3.2.2 Renderer

Neue Klasse `S/service/AlgoAlarmTextRenderer.java` (`@Component`), aufgerufen in
`AlgoAlarmDeliveryService.authorizedClaim` an Stelle der heutigen Zeilen 178–180:

```
body = prefix(locale) + "\n"
     + headline(kind, locale) + ": " + securityName + "\n"
     + detailLine(kind, alarmDetails, locale)
```

- `headline`: Schlüssel `algo.alarm.` + `kind.name().toLowerCase(Locale.ROOT).replace('_', '.')`.
- `detailLine`: `alarmDetails` wird mit Jackson als Baum gelesen. Pro Art liefert ein MessageFormat-Schlüssel die
  Zeile, gespeist aus den JSON-Feldern. Zahlen werden über das MessageFormat-Muster lokalisiert formatiert
  (`{0,number,#,##0.00}`). Schlüssel-Werte (`action`, `reason`, `bound`, `direction`) werden vorher über
  `MessageSource` übersetzt. Scheitert das Parsen oder fehlt ein Pflichtfeld, erscheint der rohe Detailtext – eine
  Mail geht nie verloren, weil der Text nicht darstellbar ist.

| Art | Headline-Schlüssel | Detail-Schlüssel | Argumente (JSON-Feld) |
|---|---|---|---|
| `PRICE_ALERT` | `algo.alarm.price.alert` | `algo.alarm.price.alert.detail` | `bound`, `threshold`, `price` |
| `ENTRY_SIGNAL`, `PROFIT_TAKE`, `STOP_LOSS`, `RISK_BREACH` | `algo.alarm.entry.signal` usw. | `algo.alarm.trade.signal.detail` | `reason` (übersetzt), `units`, `price` |
| `REBALANCE_DRIFT` | `algo.alarm.rebalance.drift` | `algo.alarm.rebalance.drift.detail` | `action` (übersetzt), `target`, `actual`, `deviation`, `amount`, `currency`, `reason` (übersetzt) |
| `HOLDING_GAIN_LOSS` | `algo.alarm.holding.gain.loss` (neu) | `algo.alarm.holding.gain.loss.detail` | `positionGainLossPercent`, `gainThreshold`, `loseThreshold`, `price` |
| `PERIOD_PRICE_CHANGE` | `algo.alarm.period.price.change` (neu) | `algo.alarm.period.price.change.detail` | `changePercent`, `daysInPeriod`, `referenceDate`, `referenceClose`, `price` |
| `MA_CROSSING` | `algo.alarm.ma.crossing` (neu) | `algo.alarm.ma.crossing.detail` | `indicatorType`, `period`, `maValue`, `price`, `crossDirection` |
| `RSI_THRESHOLD` | `algo.alarm.rsi.threshold` (neu) | `algo.alarm.rsi.threshold.detail` | `rsiValue`, `threshold`, `direction` (übersetzt) |
| `EXPRESSION` | `algo.alarm.expression` (neu) | `algo.alarm.expression.detail` | `expression`, `price` |

Alle neuen Schlüssel kommen in `M`, Englisch und Deutsch. Die Texte werden vom Server aufgelöst (MessageFormat, jedes
`'` verdoppelt). `algo.alarm.subject` und `algo.alarm.mail.body.prefix` bleiben.

`S/service/AlgoRebalancingService.java` Zeile 1080: Der Kommentar lautet dann zutreffend „Locale independent JSON;
`AlgoAlarmTextRenderer` renders it in the reader's language."

Tests:
- Neu `T/service/AlgoAlarmTextRendererTest`: pro Art ein Detail-JSON gegen einen `StaticMessageSource` in `de` und
  `en`; ungültiges JSON fällt auf den Rohtext zurück.
- `T/service/AlgoMeanReversionEvaluationServiceTest`: Die an den Recorder übergebenen Details sind gültiges JSON mit
  den Feldern oben.
- `T/service/AlgoAlarmDeliveryServiceTest`: Der Mailkörper enthält die übersetzte Headline statt des rohen JSON.

---

### 3.3 Etappe C – Top-Ebene und Cash-Sollwert im Überwachungsbericht (Punkt 8)

#### 3.3.1 Backend

`C/reportviews/securityaccount/SecurityPositionGrandSummary.java`, neue Felder mit `@Schema` und gerundeten Gettern
(`DataBusinessHelper.roundPercentage`, wie `getOverallAllocationMismatchPercentage`):

| Feld | Formel | Beschreibung |
|---|---|---|
| `topTargetPercentage` | `plan.topPercentage()` | AlgoTop-Obergrenze in Prozentpunkten des Nettovermögens |
| `topActualPercentage` | `plan.grossExposure() / plan.netEquity() × 100`, `null` bei `netEquity == 0` | Tatsächliches Brutto-Exposure |
| `topDeviationPercentage` | `topActualPercentage − topTargetPercentage`, `null` wenn Ist `null` | Positiv = über der Obergrenze |

`S/reports/SecurityGroupByAlgoBucketRebalancingReport.java`:

- `applyPlan` setzt die drei Felder.
- `applyToGroup`, Zweig `cashLabel` (Zeile 204): zusätzlich
  `group.groupTargetPercentage = 100 − plan.topPercentage()` und
  `group.groupDeviationPercentage = groupActualPercentage − groupTargetPercentage` (beides `null` bei `netEquity == 0`).
  Der Cash-Sollwert ist eine Untergrenze: Die AlgoTop-Obergrenze begrenzt das Exposure, der Rest ist Cash. Eine
  negative Abweichung heisst, dass weniger Cash vorhanden ist als die Obergrenze übrig lässt.
- Die Gruppe „nicht zugeordnet" bleibt ohne Sollwert.
- Der Klassen-Javadoc („Two groups complete the picture and have no target of their own - cash, …") wird angepasst:
  Cash hat den impliziten Sollwert `100 − AlgoTop-Prozentsatz`.

#### 3.3.2 Frontend

- `F/entities/view/security.position.grand.summary.ts`: `topTargetPercentage`, `topActualPercentage`,
  `topDeviationPercentage` (`number | null`).
- `F/tenant/component/tenant.summaries.assetclass.component.ts`, `initRebalancingSummaryFields`: drei
  `DataType.NumericRaw`-Felder direkt nach `overallAllocationMismatchPercentage`, Schlüssel `TOP_TARGET_PERCENTAGE`,
  `TOP_ACTUAL_PERCENTAGE`, `TOP_DEVIATION_PERCENTAGE` (neu in `M`, jeweils mit `_TOOLTIP`).
- Die Cash-Gruppe zeigt ihre Soll- und Abweichungswerte über die vorhandenen `ColumnGroupConfig('groupTargetPercentage')`
  und `groupDeviationPercentage` ohne weitere Änderung.

Test: `T/reports/SecurityGroupByAlgoBucketRebalancingReportTest` – bei `topPercentage = 80`, `netEquity = 100 000`,
`grossExposure = 85 000`, `actualCash = 15 000`: Top-Ist 85 %, Abweichung +5; Cash-Soll 20 %, Ist 15 %, Abweichung −5.

Handbuch: `content/reportportfolio/securitycashaccountreport/` (de/en) um die Top-Zeile und den Cash-Sollwert
ergänzen.

---

### 3.4 Etappe D – Test des Rebalancing-Signals (Punkt 12)

Zwei Tests schliessen die Lücke zwischen Checkpoint und gespeicherter Zeile:

1. `T/service/AlgoRebalancingServiceTest`, neuer Test: Der Recorder ist ein Mock mit `ArgumentCaptor`. Bei fälligem
   Checkpoint und einer handelbaren Linie ruft `evaluateAll` den Recorder mit `AlgoSignalKind.REBALANCE_DRIFT`,
   Richtung `1` für eine Reduktion bzw. `-1` für einen Zukauf und Reduktionen zuerst auf. Die Details sind gültiges
   JSON mit `trigger`, `action`, `target`, `actual`, `deviation`. Ohne fälligen Checkpoint
   (`findLastCheckpointDate` = Bewertungstag) wird der Recorder nicht aufgerufen.
2. Neu `T/service/AlgoAlarmRecorderIntegrationTest` (`@GTIntegrationTestContext`, `@Transactional`, Rollback): echter
   `AlgoAlarmRecorder` gegen `grafioschtrader_t`, `AlgoMonitoringService` als `@MockitoBean` mit
   `permitsAlert → true`. Zweimal `record(scope, REBALANCE_DRIFT, (byte) 1, details, day)` am selben Tag ergibt eine
   Zeile (`UK_AlarmDay`). Ein anderer Tag oder die Gegenrichtung ergibt eine weitere. Die Zeile trägt
   `alarm_type = 5`, `delivery_status = 'PENDING'`. Mandant, Strategie und Wertpapier stammen aus den
   Flyway-Testdaten oder werden im Test angelegt; `algo_message_alert` hat keine Fremdschlüssel, daher genügen
   gültige Ids.

---

### 3.5 Etappe E – Maximale Drawdown-Dauer (Punkt 6, E7)

#### 3.5.1 Berechnung

`S/service/AlgoReplayMetrics.java`:

- `periodReturns` liefert zusätzlich das Datum jeder Periode: neuer privater Record `DatedReturn(LocalDate date,
  double value)`. `totalReturn`, `maxDrawdown` und `sharpeRatio` lesen weiterhin nur die Werte, sodass ihre
  Ergebnisse unverändert bleiben.
- Neue Methode `maxDrawdownDurationDays(LocalDate start, List<DatedReturn> returns)`:

```
W = 1; peak = 1; peakDate = start; underwater = false; longest = 0
für jede Periode (date, r):
  W = W × (1 + r)
  wenn W >= peak − 1e-12:
    wenn underwater: longest = max(longest, days(peakDate, date))
    peak = max(peak, W); peakDate = date; underwater = false
  sonst: underwater = true
nach der Schleife: wenn underwater: longest = max(longest, days(peakDate, letztes Datum))
```

  `start` ist das Datum der ersten beobachteten Bewertung. Ohne Beobachtung liefert die Methode `null` (wie
  `maxDrawdown`), eine nie fallende Reihe `0`.
- `Metrics` erhält `Integer maxDrawdownDurationDays`. `of(series, trades, terminal)` übernimmt sie aus der Reihe mit
  dem Endpunkt, wie `maxDrawdown`.
- Der Klassen-Javadoc ergänzt die Konvention: Die Dauer zählt Kalendertage der längsten Unterwasserphase, auch einer
  am Enddatum noch offenen.

#### 3.5.2 Speicherung und Anzeige

- Flyway (Schema, mit Testskript):
  `ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS max_drawdown_duration_days INT NULL;`
- `C/entities/AlgoSimulationResult.java`: Feld `maxDrawdownDurationDays` (`Integer`) mit `@Schema`, Getter und
  Setter.
- `S/service/AlgoHistoricalReplayService.java`: `finish` setzt das Feld (neben `setMaxDrawdown`, Zeile 1235);
  `submit` setzt es auf `null` (neben Zeile 499).
- `F/algo/model/simulation.run.ts`: `maxDrawdownDurationDays?: number`.
- `F/algo/component/algo-simulation-run.component.ts`, `initRunFields`: nach `maxDrawdown`
  `addFieldPropertyFeqH(DataType.NumericInteger, 'maxDrawdownDurationDays', { fieldsetName: this.RUN_PERFORMANCE })`;
  Schlüssel `MAX_DRAWDOWN_DURATION_DAYS` in `M`.

Test: `T/service/AlgoReplayMetricsTest` – Reihe 100, 110, 99, 105, 111 an fünf aufeinanderfolgenden Tagen: Dauer 3
(von Tag 2 bis Tag 5); eine Reihe, die am Ende unter ihrem Höchststand bleibt: Dauer bis zum letzten Datum; eine
Einzahlung am Tiefpunkt verkürzt die Dauer nicht (cash-flow-bereinigt).

Handbuch: `content/algoalert/historicalrun/` (de/en), Kennzahlentabelle um „Maximale Drawdown-Dauer" ergänzen.

---

### 3.6 Etappe F – Equity-Kurve (Punkt 7, E8)

#### 3.6.1 Backend

- Flyway (Schema, mit Testskript):
  `ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS equity_series_json LONGTEXT NULL;`
- `C/entities/AlgoSimulationResult.java`: Feld `equitySeriesJson`, `@JsonIgnore` (der Statusabruf wird gepollt und
  soll die Reihe nicht jedes Mal übertragen), `columnDefinition = "LONGTEXT"`.
- Neuer Record `S/service/SimulationRunEquityPoint.java`:
  `(@JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate date, double equity, double investedCapital)`
  mit `@Schema`. `investedCapital` ist das Eröffnungsvermögen plus die kumulierten externen Flüsse (Ein- und
  Auszahlungen), damit eine Einzahlung in der Kurve nicht als Gewinn erscheint.
- `AlgoHistoricalReplayService.finish` (Zeile 1211), nur bei `COMPLETED`: aus derselben Liste, die
  `AlgoReplayMetrics.of` erhält (`state.equity` plus Endpunkt), nur `priced`-Punkte; `investedCapital` kumuliert
  `externalCashFlow`. Geschrieben wird es mit `AlgoReplayInputs.write` in `equitySeriesJson`. `submit` setzt die Spalte
  auf `null`. Tage ohne vollständige Bewertung erscheinen nicht; sie stehen bereits als `UNAVAILABLE` im Verlauf.
- `AlgoHistoricalReplayService.equitySeries(Integer idSimTenant)`: prüft wie `status(...)` mit
  `requireOwnedSimulation(idSimTenant, currentUser())`, liest das Ergebnis über `results.findByIdTenant`,
  deserialisiert die Reihe; leere Liste ohne Reihe.
- `S/rest/TenantResource.java`: `GET /simulation/{idTenant}/run/equity` →
  `ResponseEntity<List<SimulationRunEquityPoint>>`, `@Operation` wie die Nachbarendpunkte.

Größe: `gt.simulation.max.run.trading.days` begrenzt die Punkte; drei Zahlen und ein Datum je Punkt bleiben weit unter
der Grösse von `tax_income_summary_json`.

#### 3.6.2 Frontend

Muster: `F/performanceperiod/component/performance.period.component.ts` (`navigateToChartRoute`,
`prepareChartDataWithRequest`, `ChartDataService.sentToChart`).

- `F/shared/app.settings.ts`: `SIMULATION_EQUITY_KEY = 'simulationequity'`.
- `F/algo/service/algo-simulation-run.service.ts`: `equitySeries(idTenant): Observable<SimulationRunEquityPoint[]>`.
- `F/algo/model/simulation.run.ts`: Interface `SimulationRunEquityPoint`.
- `AlgoSimulationRunComponent`: Show-Menü (heute `showMenu: null`) mit `SIMULATION_EQUITY_CHART`, aktiv nur bei
  `run.status === COMPLETED`. Die Aktion lädt die Reihe und navigiert zu
  `mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, AppSettings.SIMULATION_EQUITY_KEY]`. Zwei Linien-Traces
  (`PlotlyHelper.initializeChartTrace`), Namen `EQUITY` und `INVESTED_CAPITAL`, x-Achse `type: 'date'` mit
  Rangeslider. Die Subscription auf `requestFromChart$` wird in `ngOnDestroy` beendet.
- NLS in `M`, neu: `SIMULATION_EQUITY_CHART`, `EQUITY`, `INVESTED_CAPITAL`.

Test: `T/service/AlgoHistoricalReplayIntegrationTest` – ein abgeschlossener Lauf hat eine Reihe mit mindestens zwei
Punkten, deren erster das Eröffnungsdatum ist. Frontend: Kompilierung.

Handbuch: `content/algoalert/historicalrun/` – Abschnitt „Equity-Kurve".

---

### 3.7 Etappe G – Sollzinsen (Punkt 5, E4)

#### 3.7.1 Eingaben einfrieren

`S/service/AlgoReplayInputs.java`:

- `Snapshot` erhält die Komponente `Map<Integer, Double> borrowingRates` (Cash-Konto-Id → Jahreszins in Prozent, nur
  Konten mit `borrowingRate > 0`). Die Snapshot-Version steigt auf 6. Die Kompatibilitätskonstruktoren setzen
  `Map.of()`, alle `with…`-Methoden reichen die Komponente durch.
- `capture` erhält die Cash-Konten der Umgebung (`source.cashaccounts(idSimTenant)` in `submit`, Zeile 472) und füllt
  die Map. Ein Lauf rechnet damit mit dem Zinssatz zum Startzeitpunkt, wie bei den Daueraufträgen.
- `AlgoHistoricalReplayService.submit`: Ist die Map nicht leer, kommt der Konventionsschlüssel
  `REPLAY_OVERDRAFT_INTEREST_ACT360` in `conventions`. Text in `M`: „Negative balances of accounts with a borrowing
  rate accrue interest daily (ACT/360); it is charged at each month end and on the end date." / deutsch entsprechend.

#### 3.7.2 Abgrenzung und Belastung

Neue Klasse `S/service/AlgoReplayOverdraftInterest.java` (`@Service`, liefert eine Session je Lauf wie
`AlgoReplayCustodyService.Session`), gehalten in `AlgoReplayState`.

Formel, je Konto `k` mit Satz `r_k`:

```
Zins(d) = max(0, −B_k(d)) × r_k / 100 / 360
B_k(d)  = hold-Saldo am Tagesende d = cashHoldings.getBalanceBeforeDate(k, d + 1)
```

- `close(LocalDate date)` wird **einmal am Ende jedes Timeline-Tages** aufgerufen, nach allen Buchungen des Tages:
  1. Für die Kalendertage zwischen dem vorigen Timeline-Tag `P` und `date` (exklusiv) gilt `B_k(P)`, weil dazwischen
     nichts gebucht wird: `Zins += (date − P − 1) × Zins(P)`.
  2. `Zins += Zins(date)`.
  3. Ist `date` ein Belastungstag (letzter Kalendertag eines Monats oder `run.getEndDate()`) und der gerundete Betrag
     (`state.precision(Kontowährung)`) grösser als 0: Buchung über `transactions.saveOnlyAttributes` als
     `INTEREST_CASHACCOUNT`, `cashaccountAmount = −Betrag`, `transactionTime = date 23:59`,
     `algoFillId = idSimulationResult + ":I:" + k + ":" + date`, Notiz `[overdraft interest]`. Danach wird der
     Zähler zurückgesetzt. Die Buchung verändert `B_k(date)` nach der Abgrenzung von `date`; der Zins wird ab dem
     Folgetag mitverzinst (monatliche Kapitalisierung).
  4. Ereignis `OVERDRAFT_INTEREST` mit Betrag, Währung, Rationale `REPLAY_OVERDRAFT_INTEREST` und Kontoname in
     `details`.
- Die Belastungstage kommen in die Timeline (`timeline.addAll(...)` in `replay()`, neben den Custody-Daten, Zeile
  601), aber nur wenn `borrowingRates` nicht leer ist.
- Der Schleifenkörper von `replay()` (Zeilen 620–661) hat mehrere `continue`-Zweige. Er wird in eine Methode
  `processTimelineDate(state, date, …)` ausgelagert, die `boolean cancelled` zurückgibt. `close(date)` folgt ihrem
  Aufruf, sodass jeder Zweig abgegrenzt wird. Ein abgebrochener oder gescheiterter Lauf belastet nichts mehr.
- Sollzinsen sind Aufwand, kein externer Fluss: kein `state.addExternalCashFlow`. Sie senken Vermögen und Rendite.
- Buchungsfehler (z. B. Transaktionslimit) werden wie in `AlgoReplayCustodyService.Session.settle` behandelt:
  `TransactionLimitExceededException` bricht den Lauf ab, andere Fehler als
  `REPLAY_OVERDRAFT_INTEREST_FAILED: <Konto>: <Grund>`.

#### 3.7.3 Ereignistyp

- `C/types/AlgoEventType.java`: `OVERDRAFT_INTEREST` mit Javadoc. Die Spalte `algo_event_log.event_type` ist
  `VARCHAR(30)`, das reicht.
- Spiegel `F/algo/model/simulation.run.ts` `AlgoEventType`: gleicher Wert (`enum.mirror.spec.ts` prüft es).
- NLS in `M`: `OVERDRAFT_INTEREST`, `REPLAY_OVERDRAFT_INTEREST`, `REPLAY_OVERDRAFT_INTEREST_FAILED`,
  `REPLAY_OVERDRAFT_INTEREST_ACT360`.

Tests:
- Neu `T/service/AlgoReplayOverdraftInterestTest`: Saldo −10 000 bei 3,6 % über 30 Tage ergibt 30 × 1 = 30.00;
  Wochenend-Lücke zählt mit dem Freitagssaldo; ein positiver Saldo erzeugt nichts; der Belastungstag bucht und setzt
  zurück.
- `AlgoHistoricalReplayIntegrationTest`: Ein Konto mit `borrowingRate` und negativem Saldo hat nach dem Lauf
  `INTEREST_CASHACCOUNT`-Buchungen an Monatsenden. Ein Lauf ohne Zinssatz bleibt Buchung für Buchung identisch zum
  Referenzlauf (#254).

Handbuch: `content/algoalert/historicalrun/` (de/en) und `content/tenantportfolio/cashaccount/` – der Sollzins wirkt
in der Wiederholung.

---

### 3.8 Etappe H – Wertschriften-Daueraufträge in der Wiederholung (Punkt 4, E6)

#### 3.8.1 Erfassung in der Simulation zulassen

- `S/repository/StandingOrderJpaRepositoryImpl.java`, `saveOnlyAttributes`: Die Sperre (Zeilen 79–81) entfällt. Die
  bestehende Regel „keine zukünftigen Kurse" (`quote.tolerance.days > 0` verboten, Zeile 165) gilt weiter.
- `S/rest/StandingOrderResource.java` Zeile 106: `securityStandingOrderSupported = true` auch in der Simulation.
- Schlüssel `standing.order.simulation.cash.only` in `M` löschen, sofern nicht mehr verwendet.

#### 3.8.2 Eingaben einfrieren

`AlgoReplayInputs`:

- Neuer Record `SecurityStandingOrder(Integer id, Integer idSecurity, Integer idSecurityaccount, Integer idCashaccount,
  String cashaccountName, String cashaccountCurrency, String securityCurrency, TransactionType transactionType, Double
  units, Double investAmount, boolean amountIncludesCosts, boolean fractionalUnits, Double taxCost, String
  taxCostFormula, Double transactionCost, String transactionCostFormula, Integer idCurrencypair, RepeatUnit repeatUnit,
  short repeatInterval, Byte dayOfExecution, Byte monthOfExecution, PeriodDayPosition periodDayPosition,
  WeekendAdjustType weekendAdjust, byte quoteToleranceDays, LocalDate validFrom, LocalDate validTo, String note)`.
- `Snapshot` erhält `List<SecurityStandingOrder> securityStandingOrders` (Version 6, zusammen mit §3.7).
- `capture` füllt sie aus `standingOrders.findByIdTenant(idTenant)` gefiltert auf `StandingOrderSecurity`.
- `AlgoHistoricalReplayService.replaySecurities` (Zeile 519) nimmt die Wertpapiere dieser Daueraufträge auf. Nur so
  hat die Wiederholung Marktdaten, Kalender und eingefrorene Instrumentdaten für sie.

#### 3.8.3 Termine

`S/service/AlgoReplayStandingOrderService.java`:

- `scheduleSecurities(Snapshot, opening, end, Predicate<…> tradable)`. Wiederholung und Tagesauflösung wie
  `nextDate`/`resolveDay`. Danach Wochenendverschiebung, danach Verschiebung auf den nächsten Handelstag des
  Wertpapiers in Richtung `weekendAdjust` (höchstens
  `StandingOrderExecutionService.MAX_TRADING_DAY_ADJUSTMENT_ITERATIONS` = 10 Schritte; die Konstante wird dafür
  paketsichtbar). Handelbarkeit über dieselbe Prüfung wie
  `AlgoHistoricalReplayService.tradableOn(state, security, date)`. Termine auf oder vor dem Eröffnungsdatum oder nach
  dem Enddatum entfallen.
- `replay()` fügt die Termine der Timeline hinzu (neben `cashOrderSchedule`, Zeile 600).

#### 3.8.4 Ausführung

- `executeSecurities(state, date, orders)` läuft direkt nach den Konto-Daueraufträgen des Tages (Zeile 625) und vor
  Custody, Bewertung und Strategieentscheidungen.
- Kurs: Schlusskurs des Wertpapiers am Termin oder davor innerhalb `|quoteToleranceDays|` (wie
  `closeWithinPastTolerance`). Wechselkurs ebenso, falls `idCurrencypair` gesetzt.
- Stückzahl und Kosten: Die Berechnung aus `StandingOrderExecutionService.buildSecurityTransaction` (Schritte 2 und
  4, Zeilen 335–405) wird in eine paketsichtbare statische Methode ausgelagert, die Live und Wiederholung aufrufen:
  `static SecurityOrderAmounts securityOrderAmounts(Double units, Double investAmount, boolean amountIncludesCosts,
  boolean fractionalUnits, Double taxCost, String taxCostFormula, Double transactionCost, String
  transactionCostFormula, TransactionType type, double quotation, Double exchangeRate, String securityCurrency, String
  cashaccountCurrency)` → `record SecurityOrderAmounts(double units, double taxCost, double transactionCost, double
  cashaccountAmount)`. Bei `units <= 0` wirft sie eine Ausnahme mit dem Schlüssel `standing.order.exec.zero.units`.
  Die Live-Ausführung übersetzt ihn weiter über `messageSource`.
- Buchung über `transactions.saveOnlyAttributes` mit `idStandingOrder`, `simulationOpening = false`, Notiz des
  Auftrags. Überziehungs-, Bestands- und Handelstagsprüfung greifen wie bei jeder Transaktion; ein Verkauf über den
  Bestand hinaus wird dort abgelehnt.
- Ereignis `SECURITY_STANDING_ORDER` (`state.write` mit `idSecurity`, Stück, Kurs, Betrag, Kontowährung, Typ als
  Rationale, `details` = `#id · Wertpapiername`). Fehler: `UNAVAILABLE` mit `REPLAY_SECURITY_STANDING_ORDER_FAILED`;
  der Lauf geht weiter. `TransactionLimitExceededException` bricht ab, wie bei Konto-Daueraufträgen.
- Kein externer Fluss: Kauf und Verkauf tauschen Cash gegen Wertpapier.

#### 3.8.5 Zusammenspiel mit Strategien

Eine Dauerauftragsbuchung trägt kein `idAlgoStrategy`. Kauft ein Dauerauftrag ein Wertpapier, das eine
Mean-Reversion-Strategie verwaltet, gelten diese Stücke wie ein manueller Kauf als nicht zugeordnet
(`AlgoMeanReversionPositionService`). Die Strategie meldet dann `MEAN_REVERSION_ASSIGNMENT_REQUIRED`. Das
Rebalancing sieht die Stücke als gewöhnlichen Bestand. Das Handbuch nennt diese Einschränkung.

#### 3.8.6 Ereignistyp und Texte

- `C/types/AlgoEventType.java`: `SECURITY_STANDING_ORDER`; Javadoc von `CASH_STANDING_ORDER` bleibt.
- Frontend-Spiegel `AlgoEventType`: gleicher Wert.
- NLS in `M`: `SECURITY_STANDING_ORDER`, `REPLAY_SECURITY_STANDING_ORDER_FAILED`; `REPLAY_STANDING_ORDER_FAILED`
  heisst weiterhin „Cash standing order could not be executed".

Tests:
- Neu `T/service/StandingOrderSecurityAmountsTest`: `securityOrderAmounts` liefert für Stück- und Betragsmodus,
  brutto und netto, mit und ohne Wechselkurs, dieselben Werte wie die bisherige Inline-Berechnung.
- `T/service/AlgoReplayStandingOrderServiceTest` erweitern: Termin auf einem Börsenfeiertag wird je nach
  `weekendAdjust` verschoben; Termin vor der Eröffnung entfällt; Kurs nur aus der Vergangenheit.
- `AlgoHistoricalReplayIntegrationTest`: Ein monatlicher Sparplan erzeugt die erwarteten `ACCUMULATE`-Buchungen mit
  `idStandingOrder`. Ein Lauf ohne Wertschriften-Dauerauftrag ist identisch zum Referenzlauf.

Handbuch: `content/algoalert/historicalrun/standingorders/` (de/en). Wertschriften-Daueraufträge sind erfassbar und
werden ausgeführt; Terminlogik (Handelstage des Wertpapiers), Kostenquelle (E6) und die Einschränkung aus §3.8.5 sind
beschrieben. `content/algoalert/historicalrun/environment/` passt die Tabellenzeile an.

---

## 4. Verifikation

Je Etappe:

- `cd backend && mvn clean install -DskipTests` ohne `[WARNING]`-Compilerzeilen (Deprecations).
- Nur die in der Etappe genannten Testklassen, z. B. `mvn test -pl grafioschtrader-server -Dtest=AlgoReplayMetricsTest`.
  Integrationstests laufen gegen `grafioschtrader_t` mit `@ActiveProfiles("test")` (über `GTIntegrationTestContext`).
- Frontend bei Änderungen: `cd frontend && npm run build` und `npm test` (Enum-Spiegel).
- Für Etappen, die die Wiederholung berühren (§3.5–§3.8): Vergleich von Transaktionen, Ereignisverlauf und Kennzahlen
  eines Referenzlaufs vor und nach der Änderung wie in #254. Eine Änderung, die das Ergebnis nicht verändern soll,
  liefert identische Ausgabe; bei §3.7 und §3.8 gilt das für Läufe ohne Sollzins bzw. ohne Wertschriften-Dauerauftrag.
- `node scripts/nls-tool.mjs check` nach dem Hinzufügen von Schlüsseln.
