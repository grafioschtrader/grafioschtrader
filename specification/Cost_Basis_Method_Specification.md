# Einstandswert-Methode: Durchschnitt, FIFO und LIFO

**Code baseline:** backend 0.39.3, highest Flyway script `V0_39_3__watchlist_indicator_multi_watchlist.sql`.

Grafioschtrader führt den Einstandswert einer Wertschriftenposition nach dem gleitenden Durchschnitt. Diese
Spezifikation ergänzt FIFO (älteste Käufe gehen zuerst) und LIFO (jüngste Käufe gehen zuerst). Die Methode wird
pro Tenant gesetzt und lässt sich pro Portfolio übersteuern.

## 1. Kernaussage

**Die Methode verschiebt Gewinn zwischen realisiert und unrealisiert. Sie ändert die Gesamtrendite nicht.** Über
alle Verkäufe und die offene Restposition gilt für jede Methode, in Wertschriftenwährung:

```text
realisierter Kursgewinn + (Marktwert - verbleibender Einstand) = Summe Erlöse + Marktwert - Summe Kaufkosten
gainLossSecurity = realisierter Kursgewinn + Erträge + (Marktwert - verbleibender Einstand)
```

Kaufkosten erhöhen den Einstand, Verkaufskosten mindern den Erlös. Erträge sind Dividenden und Marchzinsen. Sie
gehören zu `gainLossSecurity` und berühren den Einstand nicht. Die rechte Seite der ersten Gleichung enthält keine
Grösse, die von der Methode abhängt. Daraus folgen drei Eigenschaften:

- Eine vollständig geschlossene Position zeigt bei jeder Methode denselben Gesamtgewinn.
- `gainLossSecurity`, `gainLossSecurityMC` und `gainLossCurrencyMC` einer bewerteten Position bleiben gleich.
- Die Einstellung lässt sich ohne Datenmigration umschalten, weil GT nichts davon speichert. Reports rechnen jedes
  Ergebnis beim Abruf aus den Transaktionen.

Beispiel: Kauf 100 zu 10, Kauf 100 zu 20, Verkauf 100 zu 30, aktueller Kurs 25.

| Methode | Einstand Verkauf | Realisiert | Einstand Rest | Unrealisiert | Summe | Offene Rendite |
|---|---:|---:|---:|---:|---:|---:|
| Durchschnitt | 1500 | 1500 | 1500 | 1000 | 2500 | 66.67 % |
| FIFO | 1000 | 2000 | 2000 | 500 | 2500 | 25.00 % |
| LIFO | 2000 | 1000 | 1000 | 1500 | 2500 | 150.00 % |

### 1.1 Messung am Bestand

Die Messung lief am 7. November 2026 lesend auf der Datenbank `grafioschtrader`, über alle Tenants und die ganze
Historie. Sie rechnet ohne Margin-Instrumente mit Einheiten auf der Split-Basis des Messtags. Der realisierte Gewinn
enthält nur Verkäufe, keine Erträge. Eine Abweichung zählt ab 0.01.

| Je Depot und Wertschrift | Wert |
|---|---:|
| Positionen | 1142 |
| davon mit mindestens einem Verkauf | 874 |
| davon mit abweichendem realisiertem Gewinn gegenüber Durchschnitt | 18 |
| geschlossene Positionen mit abweichendem realisiertem Gewinn | 0 |
| grösste Verletzung der Gleichung aus §1 | 3e-11 |
| Verkäufe, die die Lots nicht decken | 0 |
| Verkäufe mit Einstand 0, offene Positionen mit Einstand 0, Lot-Reste nach Glattstellung | 0 |
| grösste Abweichung einer Position (102 227 Durchschnitt, 89 331 FIFO, 140 247 LIFO) | 38 020 CHF |

| Verschiebung gegenüber Durchschnitt, Summe der Beträge | CHF | USD | EUR |
|---|---:|---:|---:|
| FIFO | 21 370 | 2 977 | 1 378 |
| LIFO | 45 864 | 4 693 | 1 378 |
| realisierter Gewinn nach Durchschnitt | 2 715 994 | 1 763 260 | 907 150 |

Der Report-Topf des Durchschnitts (§2) verschiebt mehr als die Wahl der Methode. Der Vergleich stellt den
chronologischen Topf eines Tenants dem Durchschnitt je Depot gegenüber.

| Report-Topf gegenüber Durchschnitt je Depot | Wert |
|---|---:|
| Wertschriften eines Tenants in mehreren Depots | 75 |
| davon mit abweichendem Verkaufsgewinn | 26 |
| Verkäufe dieser Wertschriften | 375 |
| davon mit abweichendem Gewinn | 137 |
| Summe der Beträge CHF, USD, EUR | 121 817, 97 580, 8 709 |
| grösste Abweichung einer Wertschrift | 30 233 CHF |

| Zeitgleiche Transaktionen und Sonderfälle | Wert |
|---|---:|
| Kauf und Verkauf mit gleichem Zeitstempel im selben Depot, über Depots hinweg | 0, 0 |
| Dividende und Verkauf mit gleichem Zeitstempel im selben Depot | 128 |
| Dividende und Kauf mit gleichem Zeitstempel im selben Depot | 10 |
| offene Positionen je Tenant und Wertschrift | 305 |
| davon mit Verkauf oder Ertrag | 220 |
| ausgeführte Depotüberträge (`security_transfer`), mit Margin | 0 |
| ausgeführte ISIN-Wechsel (`security_action_application`), mit Margin | 2 |

## 2. Heutiger Stand im Code

Der Durchschnitt steckt an zwei Stellen in `SecurityGeneralCalc`:

- `accumulate` addiert Kurswert und Kosten auf `SecurityPositionSummary.adjustedCostBase`.
- `reduceOrHypotheticalSell` zieht `acb = unitsSplited * adjustedCostBase / units` ab und rechnet daraus
  `transactionGainLoss`.

`adjustedCostBase` wird ausserhalb der Rechenklassen und von `SecurityPositionSummary` nur in
`AlgoHoldingGainLossService.observe` gelesen, `transactionCostBase` nur in `SecurityPositionSummary`. Die Änderung
trifft deshalb wenige Stellen.

Margin-Instrumente rechnen in `SecurityMarginCalc` bereits positionsgenau: Jeder Schluss verweist über
`connectedIdTransaction` auf seine Eröffnung (`TransactionsMarginOpenUnits`). Sie bleiben von dieser Spezifikation
unberührt. Die Eröffnung kann ein `REDUCE` und der Schluss ein `ACCUMULATE` sein.

Vier Eigenschaften der heutigen Berechnung prägen den Entwurf:

- **Der Geltungsbereich des Durchschnitts hängt vom Report ab.** `SecurityPositionSummaryReport` führt dieselbe
  Wertschrift über alle Depots des Reports in einer `SecurityPositionSummary` zusammen und läuft dabei Depot für
  Depot. `SecruityTransactionsReport` und `WatchlistReport` laufen auf Tenant-Ebene chronologisch über alle Depots,
  `AccountPositionGroupSummaryReport` je Portfolio. Derselbe Verkauf kann so je Sicht unterschiedliche Gewinne zeigen.
  Die Summe stimmt, die Aufteilung nicht (§1.1).
- **Die Sortierung ist nur nach Zeitstempel definiert.** `Transaction.compareTo`, `Securityaccount`
  (`@OrderBy("transactionTime ASC")`) und die Abfragen in `TransactionJpaRepository` kennen kein zweites Kriterium.
  `SecurityGeneralUnitsCheck` fügt nach Zeitstempel ein und tauscht mit `reorderTransactionForDividends` nur benachbarte
  Paare nach der Regel Kauf vor Dividende und Dividende vor Verkauf.
- **Alarm und Treemap zeigen den Gesamterfolg auf den Resteinstand.** `calcGainLossByPrice` setzt
  `transactionGainLossPercentage = gainLossSecurity * 100 / adjustedCostBase`, wobei `gainLossSecurity` auch realisierte
  Verkäufe und Erträge enthält. `AlgoHoldingGainLossService.observe` und `HoldingsTreemapBuilder` lesen diesen Wert. Die
  Watchlist rechnet dagegen über den hypothetischen Verkauf die offene Rendite. Im Beispiel aus §1 sind das beim
  Durchschnitt 166.67 % gegen 66.67 %.
- **Der Positionsreport kennt keinen hypothetischen Verkauf.** `processOpenPositionsWithActualPrice` bewertet über
  `calcGainLossByPrice`. Den hypothetischen Verkauf rechnen nur `SecruityTransactionsReport` und `WatchlistReport`.

## 3. Auswirkungen auf die Ausgaben

### 3.1 Von der Methode abhängig

| Ausgabe | Ort |
|---|---|
| Gewinn, Gewinn in %, Gewinn in Hauptwährung je Verkauf | `SecurityTransactionPosition`, `transaction.security.field.definition.ts`, `mobile.instrument.transactions.ts`, MCP `security_transactions` |
| Dieselben Werte des hypothetischen Verkaufs | `SecurityGeneralCalc.createHypotheticalSellTransaction` |
| Rendite einer Dividendenzeile, bezogen auf den laufenden Einstand an dieser Stelle des Durchlaufs | `SecurityGeneralCalc.calcDividend` |
| `positionGainLoss`, `positionGainLossPercentage` und die Werte in Hauptwährung | `WatchlistReport`, `watchlist.performance.component.ts`, `mobile.watchlist.rows.ts`, `mobile.instrument.details.ts` |
| Performance-Einfärbung der Treemap | `HoldingsTreemapBuilder` |
| Prozent-Schwellen der Haltungs-Alarme | `AlgoHoldingGainLossService.observe` |

Ein Alarm kann also nach dem Umschalten früher, später oder gar nicht mehr auslösen, ohne dass sich Kurs oder
Bestand geändert haben. Die Bestandesübersicht (`securityaccount.base.table.ts`) zeigt `gainLossSecurity` ohne Prozent
und bleibt gleich. Die Einstellung weist auf beides hin (§5.3).

### 3.2 Von der Methode unabhängig

| Ausgabe | Grund |
|---|---|
| `gainLossSecurity`, `gainLossSecurityMC` und deren Gruppen- und Gesamtsummen | Gleichung aus §1 |
| `gainLossCurrencyMC`, `transactionGainLossCurrencyMC` | beruhen auf den Geldflüssen `netInvestedSC` / `netInvestedMC` |
| Einheiten, Marktwert, Kontowert, Anteil am Total | keine Kostengrösse |
| `hold_*`-Tabellen, `hold_daily_total`, `PerformanceReport` | Marktwert und Geldflüsse, kein Einstand |
| `SecurityDividendsReport`, `SecurityTransactionCostReport` | kein Einstand |
| Steuerauszug eCH-0196, CSV-Export, Belege, `HoldingsRenderer` | kein Einstand je Verkauf |
| `DisposalCostEstimator` | schätzt Kosten, liest keinen Einstand |
| `AlgoExecutionState.averagePrice`, `ReferencePrice.avg_cost` | eigener Stückdurchschnitt der Strategie aus den Kursen, ohne Gebühren, unverändert bei einem Teilverkauf |

## 4. Rechenkern

### 4.1 Enum und Auflösung

`grafioschtrader.types.CostBasisMethod` in `grafioschtrader-common`:

```java
public enum CostBasisMethod {
  AVERAGE_COST((byte) 0), FIFO((byte) 1), LIFO((byte) 2);
}
```

`grafioschtrader.instrument.CostBasisMethods` hält die wirksame Methode eines Report-Laufs:

```java
public record CostBasisMethods(CostBasisMethod tenantMethod, Map<Integer, CostBasisMethod> byIdSecurityaccount) {
  public static final CostBasisMethods AVERAGE_ONLY = new CostBasisMethods(CostBasisMethod.AVERAGE_COST, Map.of());

  public static CostBasisMethods all(CostBasisMethod method) {
    return new CostBasisMethods(method, Map.of());
  }

  public CostBasisMethod forAccount(Integer idSecurityaccount) {
    return byIdSecurityaccount.getOrDefault(idSecurityaccount, tenantMethod);
  }
}
```

`CostBasisMethodService.forTenant(Integer idTenant)` baut den Record. Der übergebene `idTenant` ist die einzige Quelle
des Tenants, denn `observe` läuft in der Alarmbewertung ohne HTTP-Request und ohne `SecurityContext`. Die Karte enthält
nur Depots aus Portfolios mit Übersteuerung, im Normalfall ist sie leer. Sie kommt aus einer neuen Abfrage in
`PortfolioJpaRepository`:

```sql
SELECT sca.id_securitycash_account, p.cost_basis_method
FROM portfolio p JOIN securitycashaccount sca ON sca.id_portfolio = p.id_portfolio
WHERE p.id_tenant = ?1 AND p.cost_basis_method IS NOT NULL
```

Die Abfrage liefert auch Geldkonten. Das ist unschädlich, weil der Key nur für Depots nachgeschlagen wird.

`forAccount` wird nur mit der Depot-Id einer gebuchten Transaktion aufgerufen. `Map.of()` wirft bei einem `null`-Key,
und die Transaktion des hypothetischen Verkaufs trägt kein Depot (§4.3).

`all` setzt eine Methode für alle Depots, unabhängig von der Einstellung. Damit rechnen die Reports aus
`Realized_Gain_Tax_Report_Specification.md`.

`SecurityCalcService.calcTransactions` und `calcSingleSecurityTransaction` erhalten `CostBasisMethods` als
Pflichtparameter, ebenso die abstrakten Methoden in `SecurityBaseCalc`. Es gibt bewusst keine Überladung mit
Standardwert: Der Compiler soll jeden Aufrufer erzwingen, damit keiner still beim Durchschnitt bleibt. Jeder Report
löst den Record genau einmal auf und reicht ihn als unveränderlichen Kontext weiter, auch durch Schleifen je Depot.

| Aufrufer | Ort der Auflösung |
|---|---|
| `SecurityPositionSummaryReport.createPositionSummaryForSecurityaccount` | `getSecurityPositionGrandSummary`, vor der Depotschleife |
| `AccountPositionGroupSummaryReport.calcSecurityTransaction` | einmal je Report, vor der Portfolioschleife |
| `WatchlistReport.calcPositions` | `forTenant(tenant.getIdTenant())` vor dem Aufruf, nicht in `calcPositions`, das nach einem fehlenden Kurs ein zweites Mal läuft |
| `SecruityTransactionsReport.calcSummaryForTransactions` | neuer Parameter, je einmal in den vier aufrufenden Methoden, vor der Depotschleife von `getOpenPositionByIdTenantAndIdSecuritycurrency` |
| `AlgoHoldingGainLossService.observe` | `forTenant(idTenant)` |
| `AlgoHistoricalValuationService` | `CostBasisMethods.AVERAGE_ONLY`, der Zweig betrifft nur Margin-Instrumente |

### 4.2 Bestandsbuch je Depot

FIFO und LIFO rechnen je Depot. Zwei Depots desselben Portfolios teilen sich keine Lots, auch wenn die Einstellung am
Portfolio hängt. Der Durchschnitt bleibt beim Report-Topf.

`SecurityPositionSummary` erhält `@JsonIgnore private Map<Integer, CostLedger> costLedgers` mit dem Key
`idSecurityaccount`, dazu `ledgerUnits` und `ledgerCost`, die laufenden Summen aller Bücher. Ein Depot mit
`AVERAGE_COST` hat kein Bestandsbuch. `CostLedger` und `CostLot` liegen in
`grafioschtrader.reportviews.securityaccount` in `grafioschtrader-common` und führen je Depot eine
`ArrayDeque<CostLot>`. Sie kennen deshalb `SecurityBaseCalc.CalcTransactionPos` aus `grafioschtrader-server` nicht und
erhalten Mengen und Beträge als einfache Werte.

```java
public final class CostLot {
  public final Integer idTransaction;
  public final LocalDateTime transactionTime;
  public final Double exchangeRate; // Kurs zur Hauptwährung am Kauftag, null bei einer Wertschrift in Hauptwährung
  public double units;              // Stückzahl auf der Split-Basis des Report-Datums, entspricht ctp.unitsSplited
  public double cost;               // ctp.securitiesNetPrice + ctp.transactionTaxCost, anteilig gemindert
}

public final class CostLedger {
  public void add(CostLot lot);
  public Reduction reduce(double units, CostBasisMethod method);

  public record Reduction(List<CostLot> consumedLots, double cost, double uncoveredUnits) {
  }
}
```

`exchangeRate` wird nach derselben Bedingung gesetzt wie in `addFlowToCurrencyPosition`, denn
`DataBusinessHelper.getCurrencyExchangeRateToMainCurreny` liefert bei einer Wertschrift in Hauptwährung den Kreuzkurs des
Geldkontos. `CostLedger.reduce` gibt die verbrauchten Anteile als Kopien zurück, die das Bestandsbuch nicht mehr
verändert, dazu deren Kostensumme und die Menge, die kein Lot gedeckt hat. Einen Ersatzwert für diese Menge setzt es
nicht. Klassen und Felder sind öffentlich, weil `GermanTaxLotEngine` sie ausserhalb der Rechenklassen verwendet und
Jackson `consumedLots` ausgibt.

`adjustedCostBase` und `units` bleiben die Summen der ganzen Position. Der Durchschnittstopf ist der Rest:

```java
public double averageCost()  { return adjustedCostBase - ledgerCost; }
public double averageUnits() { return costLedgers.isEmpty() ? units : DataBusinessHelper.round(units - ledgerUnits); }
```

Beide Methoden sind öffentlich, weil `SecurityGeneralCalc` in einem anderen Modul liegt, und tragen `@JsonIgnore`. Eine
Position ohne Bestandsbuch rechnet damit denselben Ausdruck wie heute. Der Durchschnitt braucht keine zweite
Implementierung.

Das Bestandsbuch je Depot hat vier Gründe:

- `checkTradingDayAndUnitsIntegrity` prüft Einheiten ebenfalls je Depot (`findByIdSecurityaccountAndIdSecurity`).
  Ein Verkauf findet deshalb stets genügend Lots.
- Die Reihenfolge, in der ein Report die Depots abläuft, beeinflusst das Ergebnis nicht mehr.
- Ein Verkauf zeigt in Depot-, Portfolio-, Tenant- und Watchlist-Sicht denselben Gewinn.
- Das entspricht der Depotsicht von Banken.

Die Isolation gilt für FIFO und LIFO. Ein Durchschnittsdepot teilt sich den Topf mit allen Durchschnittsdepots
derselben Wertschrift im Report. Wechselt ein Portfolio auf FIFO, verlassen seine Käufe diesen Topf, und in einer Sicht
über mehrere Portfolios verschiebt sich der Verkaufsgewinn der verbleibenden Durchschnittsdepots.

Beispiel: Portfolio A kauft 100 zu 10, Portfolio B kauft 100 zu 20, A verkauft 100 zu 30. Mit beiden auf Durchschnitt
zeigt der Tenant-Report für den Verkauf 1500. Steht nur B auf FIFO, zeigt er 2000. Die Depotsicht von A zeigt in beiden
Fällen 2000.

### 4.3 Buchung

`accumulate` führt `adjustedCostBase` weiter. Bei `FIFO` und `LIFO` legt es zusätzlich ein Lot in das Bestandsbuch des
Depots und erhöht `ledgerUnits` und `ledgerCost`.

`reduceOrHypotheticalSell` trennt drei Fälle:

```java
final double balance = ctp.securitiesNetPrice - ctp.transactionTaxCost;
final double acb;
if (transaction.getTransactionType() == TransactionType.HYPOTHETICAL_SELL) {
  acb = ctp.unitsSplited * securityPositionSummary.adjustedCostBase / securityPositionSummary.units;
} else {
  final CostBasisMethod method = methods.forAccount(transaction.getIdSecurityaccount());
  securityPositionSummary.transactionCostBasisMethod = method;
  acb = method == CostBasisMethod.AVERAGE_COST
      ? ctp.unitsSplited * securityPositionSummary.averageCost() / securityPositionSummary.averageUnits()
      : securityPositionSummary.reduceFromLedger(transaction, ctp.unitsSplited, balance, method);
}
```

`reduceFromLedger(Transaction transaction, double unitsSplited, double proceeds, CostBasisMethod method)` ruft
`CostLedger.reduce` für das Depot der Transaktion auf, führt `ledgerUnits` und `ledgerCost` nach, setzt die Felder der
Zeile und gibt den Einstand des Verkaufs zurück.

- **`AVERAGE_COST`** rechnet auf dem Rest aus §4.2.
- **`FIFO`** verbraucht vom Kopf, **`LIFO`** vom Ende der Deque. Ein ganz verbrauchtes Lot gibt seine ganze `cost`
  ab, ein teilweise verbrauchtes `cost * verbraucht / lot.units`. So bleibt keine Restsumme stehen.
- **Der hypothetische Verkauf** veräussert immer die ganze Position. Die Formel ergibt dann bei jeder Methode den
  gesamten Resteinstand, auch bei gemischten Positionen. Er berührt die Bestandsbücher nicht und löst keine Methode
  auf, weil `createHypotheticalBuySellTransaction` kein Depot setzt.

Zusätzliche Regeln:

- Ein Lot, dessen Rest nach `DataBusinessHelper.round` 0 ist, gilt als verbraucht, seine Restkosten gehen an diesen
  Verkauf. Fällt die Menge eines Bestandsbuchs auf 0, wird es geleert. So überlebt kein Rest eine Glattstellung und
  kommt beim Wiederkauf als ältestes Lot zurück.
- Ein Verkauf, den die Lots nicht decken, kann nach geprüften Einheiten nicht entstehen und kommt im Bestand nicht vor.
  Tritt er bei Altdaten auf, setzt `reduceFromLedger` für `Reduction.uncoveredUnits` den anteiligen Erlös
  `proceeds * uncoveredUnits / unitsSplited` als Einstand ein, die fehlende Menge erzeugt also keinen Gewinn.
  `SecurityTransactionPosition.costBasisIncomplete` ist dann `true`, die Prozentzahl bleibt leer, das MCP liefert das
  Feld mit, und `log.warn` nennt die Transaktion. Ein Report bricht deswegen nicht ab. `ledgerUnits` und `ledgerCost`
  sinken um die ganze Verkaufsmenge und das ganze `acb`, damit der Durchschnittsrest unberührt bleibt.

Drei Felder von `SecurityPositionSummary` gelten wie `transactionGainLoss` nur für eine Zeile, tragen `@JsonIgnore` und
werden in `SecurityBaseCalc.createCalcTransactionPos` zusammen mit ihm auf `null` gesetzt:

| Feld | Inhalt | Ziel in `SecurityTransactionPosition` |
|---|---|---|
| `transactionCostBasisMethod` | Methode eines `REDUCE`, `null` bei allen anderen Zeilen und beim hypothetischen Verkauf | `costBasisMethod` |
| `transactionConsumedLots` | `Reduction.consumedLots` bei FIFO und LIFO, sonst `null` | `consumedLots` |
| `transactionCostBasisIncomplete` | `Boolean.TRUE` bei einem ungedeckten Verkauf, sonst `null` | `costBasisIncomplete` |

Die drei Zielfelder tragen `@JsonInclude(JsonInclude.Include.NON_NULL)`. Die Anzeige von `consumedLots` folgt in
Stufe 3.

Keine Sonderregeln brauchen:

- **Splits:** Lots stehen bereits auf der Split-Basis des Report-Datums, `SplitFactorAfterBefore.fromToDateFactor`
  liefert die Menge. Der Kostenbetrag bleibt unverändert.
- **Marchzinsen:** `createAccruedInterestPostion` und `simulateAccruedInterest` laufen über `calcDividend` und
  berühren den Einstand nicht.
- **Verkaufskosten:** Sie mindern den Erlös und werden keinem Lot zugerechnet.

### 4.4 Eindeutige Reihenfolge

Die Reihenfolge lautet `transactionTime`, fachlicher Rang, `idTransaction`, für alle drei Methoden. Der Rang ist Kauf
(0), Ertrag und alle übrigen Typen (1), Verkauf (2), Kauf eines Depotübertrags (3). Die Byte-Werte von
`TransactionType` taugen nicht dafür, weil sie den Verkauf vor die Dividende stellen: Bei den 128 zeitgleichen Paaren aus §1.1 sähe die Dividendenrendite den Bestand
nach dem Verkauf, während die Einheitenprüfung die Dividende davor verlangt.

Die Regel steht als Komparator an `Transaction`:

```java
public static final Comparator<Transaction> CALC_ORDER = Comparator.comparing(Transaction::getTransactionTime)
    .thenComparingInt(Transaction::calcRank)
    .thenComparing(Transaction::getIdTransaction, Comparator.nullsLast(Comparator.naturalOrder()));
```

- Ein Depotübertrag bucht Verkauf und Kauf derselben Wertschrift auf 12:00 Uhr des Übertragstags
  (`SecurityActionService.createAndSaveSellTransaction`, `createAndSaveBuyTransaction`). Der Kauf, ein `ACCUMULATE` mit
  `idSecurityTransfer`, steht deshalb hinter dem Verkauf. Stünde er davor, flösse der Schlusskurs in den
  Durchschnittstopf einer Sicht über beide Depots, bevor der Verkauf rechnet, und dessen Gewinn wiche von der Depotsicht
  ab. Die Einheitenprüfung lehnt damit einen Verkauf im Zieldepot ab, der auf dieselbe Minute fällt und die übertragenen
  Stücke braucht.
- `calcRank` gibt für Transaktionen ohne Wertschrift und für Margin-Instrumente 0 zurück. Es ist kein Getter und fehlt
  deshalb im JSON. Ein Margin-Schluss kann ein `ACCUMULATE` auf einer `REDUCE`-Eröffnung sein und muss hinter ihr
  bleiben, deshalb gilt dort allein `idTransaction`.
- `nullsLast` deckt die noch nicht gespeicherte Transaktion ab, die die Einheitenprüfung in die Liste legt.
- JPA-`@OrderBy` kann keinen Rang ausdrücken. Abfragen und `@OrderBy` bleiben deshalb wie sie sind, und `compareTo`
  bleibt für `SecurityDividendsReport` unverändert. Die Rechnung sortiert im Speicher.

Anzupassen sind:

| Stelle | Änderung |
|---|---|
| `SecurityCalcService.calcTransactions` | sortierte Kopie der Liste, deckt `SecruityTransactionsReport` und `observe` ab |
| `SecurityPositionSummaryReport.createPositionSummaryForSecurityaccount` | sortierte Kopie von `getSecurityTransactionList()` |
| `AccountPositionGroupSummaryReport.calcSecurityTransaction` | sortierte Kopie für die Wertschriften, die Geldkonten-Rechnung behält `Collections.sort` und damit ihre Reihenfolge |
| `WatchlistReport.calcPositions` | sortierte Kopie vor dem Durchlauf |
| `SecurityGeneralUnitsCheck.checkUnitsIntegrity` | `transactionAfter.sort(Transaction.CALC_ORDER)` ersetzt `reorderTransactionForDividends` |
| `SimulationTenantService.copyTransactionsUpToDate` | kopiert in dieser Reihenfolge, damit die neuen Ids sie erhalten |

Die Regel kann bestehende Ergebnisse an genau einer Stelle ändern: Fallen im selben Report ein Kauf und ein Verkauf
derselben Wertschrift auf denselben Zeitstempel, gilt neu Kauf vor Verkauf, ausser beim Depotübertrag. Im Bestand kommt
das nicht vor. Bei den
zeitgleichen Dividenden ändert sich höchstens die Rendite der Dividendenzeile, ihr Betrag bleibt gleich.

### 4.5 Offene Rendite und undefinierte Prozentwerte

Alarm, Treemap und Watchlist zeigen dieselbe Kennzahl, die offene Rendite:

```text
offene Rendite = (Marktwert - verbleibender Einstand) / verbleibender Einstand
```

Erträge und realisierte Gewinne zählen nicht dazu. `calcGainLossByPrice` führt `gainLossSecurity` unverändert weiter
und setzt für Wertschriften ohne Margin:

```java
transactionGainLossPercentage = adjustedCostBase == 0.0 ? null
    : DataBusinessHelper.roundPercentage((valueSecurity - adjustedCostBase) * 100 / adjustedCostBase);
```

Die Watchlist rechnet weiter über den hypothetischen Verkauf, beide Wege ergeben denselben Prozentsatz. Die Änderung
gilt für alle Tenants, auch beim Durchschnitt, unabhängig von der Einstellung. Margin-Instrumente behalten ihre Formel.
Die Zeile des hypothetischen Verkaufs im `SecruityTransactionsReport` enthält bei eingeschalteter Schätzung die
Veräusserungskosten und liegt dann entsprechend tiefer.

Ein Prozentwert ohne Einstand ist leer, entlang der ganzen Kette:

- `reduceOrHypotheticalSell` setzt `transactionGainLossPercentage = transactionGainLoss / acb * 100`, bei `acb == 0`
  `null`. Ein Gratis-Lot kann unter FIFO und LIFO allein einen Verkauf decken.
- `calcDividend` setzt den Wert nur bei `units > 0` und `adjustedCostBase != 0`.
- `getPositionGainLossPercentage` gibt `Double` zurück und entpackt nicht mehr. `AlgoHoldingGainLossService.observe` und
  `getPositionGainLossPercentageMC` behandeln `null`.

Bei `observe` schaltet ein Einstand von 0 weiterhin nur die Prozent-Schwellen ab.

### 4.6 Hauptwährung

`getPositionGainLossMC` und `getPositionGainLossPercentageMC` behalten ihre Formeln für alle drei Methoden:

```text
positionGainLossMC = offener Gewinn * Reportkurs + gainLossCurrencyMC
Nenner             = transactionCostBase * (netInvestedSC > 0 ? netInvestedMC / netInvestedSC : Reportkurs)
```

Die Methode wirkt nur über `transactionCostBase` und den offenen Gewinn. `gainLossCurrencyMC` bleibt das
Währungsergebnis der ganzen Position einschliesslich verkaufter Stücke, und `netInvestedSC` / `netInvestedMC` bleiben das
Modell der Geldflüsse. Die Kennzahl ist also keine Rendite der verbliebenen Lots in Hauptwährung. `CostLot.exchangeRate`
fliesst nicht in diese Formeln ein. So gilt eine Definition für alle Methoden und für gemischte Positionen.

`transactionGainLossMC` bleibt der Gewinn in Wertschriftenwährung mal Verkaufskurs und zeigt keinen CHF-Gewinn des
verbrauchten Lots.

Beispiel in USD mit Hauptwährung CHF: Kauf 1 zu 100 bei Kurs 0.80, Kauf 1 zu 200 bei Kurs 1.00, Verkauf 1 zu 300 bei
Kurs 1.10, Bewertung des Rests zu 250 bei Kurs 1.20. In allen Methoden ist `netInvestedSC` 0, `netInvestedMC` -50 und
`gainLossCurrencyMC` 50.

| Methode | Einstand Rest USD | `transactionGainLossMC` Verkauf | `positionGainLossMC` | Nenner | Prozent |
|---|---:|---:|---:|---:|---:|
| Durchschnitt | 150 | 165 | 170 | 180 | 94.44 % |
| FIFO | 200 | 220 | 110 | 240 | 45.83 % |
| LIFO | 100 | 110 | 230 | 120 | 191.67 % |

## 5. Einstellung

### 5.1 Datenmodell

Die Spalten kommen in das höchste noch nicht veröffentlichte Flyway-Skript, sonst in das nächste zur Pom-Version
passende. Sie verwenden `TINYINT` ohne `(1)`, weil der Treiber `TINYINT(1)` als `Boolean` projiziert.

```sql
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS cost_basis_method TINYINT NOT NULL DEFAULT 0;
ALTER TABLE portfolio ADD COLUMN IF NOT EXISTS cost_basis_method TINYINT NULL DEFAULT NULL;
```

Test und E2E migrieren nur `db/migration/test`. Dieselben Anweisungen kommen deshalb in eine eigene Test-Migration mit
der nächsten Nummer gemäss Kommentar in `application-test.properties` (`V19`). Sie entfällt, sobald `nv.bat` `V1` und
`V2` neu erzeugt hat. `gt_ddl.sql` wird erzeugt und nicht von Hand geändert.

| Entität | Feld | Bedeutung |
|---|---|---|
| `Tenant` | `byte costBasisMethod`, Getter und Setter als Enum, `null` im Setter ergibt `AVERAGE_COST` | Vorgabe, `AVERAGE_COST` für alle bestehenden Tenants |
| `Portfolio` | `Byte costBasisMethod`, Getter und Setter als Enum, `@PropertyAlwaysUpdatable` | `null` übernimmt die Vorgabe des Tenants |

`Tenant.updateThis` übernimmt das Feld. Es entsteht keine neue Entität und keine Tabelle, die Lots leben nur während
der Berechnung.

Das Verhalten der API ist festgelegt:

| Eingabe | `Tenant` | `Portfolio` |
|---|---|---|
| Feld fehlt oder ist `null` | `AVERAGE_COST`, weil `updateThis` den ganzen Zustand übernimmt | `null`, also Vorgabe des Tenants |
| gültiger Enum-Name | wird gesetzt | explizite Übersteuerung, auch `AVERAGE_COST`, die einer späteren Änderung der Vorgabe nicht folgt |
| ungültiger Wert | HTTP 400 | HTTP 400 |

### 5.2 Weitergabe

- `SimulationTenantService` setzt die Methode auf dem neuen Simulations-Tenant aus dem Haupt-Tenant, denn der
  Konstruktor von `Tenant` übernimmt sie nicht. Die Übersteuerung im Portfolio kommt über `BeanUtils.copyProperties` in
  `copyPortfolios` mit. Bei `COPY_PORTFOLIO` wirkt die Methode auf die kopierte Historie, bei `LIQUIDATE_TO_CASH` und
  `MANUAL_CASH` erst auf spätere Geschäfte.
- `CopyTenantService` übernimmt die Methode der Quelle in `updateTenantReference`, unabhängig von der
  Performance-Watchlist. `copyPortfolio` kopiert die Übersteuerung bereits mit der Portfolio-Zeile. Eine Kopie rechnet
  so mit der wirksamen Methode der Quelle.
- `SecurityPositionSummary` trägt `costBasisMethods`, ein `Set<CostBasisMethod>` der Methoden aller Depots, die in diese
  Position eingeflossen sind. Das Feld ist bei Margin-Instrumenten leer. `SecuritycurrencyPosition` übernimmt es für die
  Watchlist.
- `SecurityTransactionPosition` trägt bei Verkäufen `costBasisMethod`, die Methode, nach der dieser Verkauf gerechnet
  wurde, und bei Bedarf `costBasisIncomplete`.
- `GTMcpReadTools` beschreibt in `security_transactions` und bei den Haltungen, dass der Gewinn je Verkauf und die
  offene Rendite von der Methode abhängen. `McpReadToolService` liefert die wirksamen Methoden mit.

### 5.3 Oberfläche

Die Optionen kommen vom Backend: `GlobalparametersGTResource` liefert sie unter `/costbasismethods` als
`List<ValueKeyHtmlSelectOptions>`. Ungültige Werte scheitern am Enum.

| Stelle | Änderung |
|---|---|
| `tenant.edit.component.ts` | Auswahl `costBasisMethod`, Pflichtfeld, **nach** `disposalCostEstimate` |
| `portfolio.edit.dynamic.component.ts` | Auswahl `costBasisMethod`, optional, die leere Option speichert `null` und steht für die Vorgabe des Tenants |
| `tenant.ts`, `portfolio.ts` | neues Feld |
| Transaktionstabelle | Tooltip an der Gewinnspalte nennt je Verkaufszeile die Methode aus `costBasisMethod` |
| `watchlist.performance.component.ts` | Tooltip nennt die Methode aus `costBasisMethods`, bei mehreren `COST_BASIS_METHOD_MIXED` |

Die Position im Tenant-Formular ist wichtig: `getFields` greift bei der Registrierung und in den beiden `splice`-Aufrufen
über feste Indizes auf `fieldConfig` zu, ein weiter vorne eingefügtes Feld verschiebt diese Zugriffe. Die Registrierung
und das Formular nur mit Währung enthalten das Feld nicht.

NLS in `grafioschtrader-common/.../message/messages*.properties`:

| Key | Deutsch | Englisch |
|---|---|---|
| `COST_BASIS_METHOD` | Einstandswert-Methode | Cost basis method |
| `COST_BASIS_METHOD_TOOLTIP` | Bestimmt, welche Käufe ein Verkauf verbraucht. Die Gesamtrendite bleibt gleich, die Bestandesübersicht ändert sich nicht. Gewinn je Verkauf und offene Rendite ändern sich in Transaktionsliste, Watchlist und Treemap, ebenso die Prozent-Schwellen von Haltungs-Alarmen. Die Änderung gilt rückwirkend, auch in abgeschlossenen Perioden. Die Methode ist eine Auswertungssicht, GT berechnet keine Steuern. | Decides which purchases a sale consumes. The total return stays the same and the holdings overview does not change. Gain per sale and open return change in the transaction list, watchlist and treemap, as do the percentage thresholds of holding alerts. A change applies retroactively, including closed periods. The method is an evaluation view, GT does not calculate taxes. |
| `COST_BASIS_METHOD_INHERIT` | Wie Klient | Same as client |
| `COST_BASIS_METHOD_MIXED` | Gemischt, je Depot verschieden | Mixed, differs per security account |
| `AVERAGE_COST` | Gleitender Durchschnitt | Moving average |
| `FIFO` | FIFO (älteste Käufe zuerst) | FIFO (oldest purchases first) |
| `LIFO` | LIFO (jüngste Käufe zuerst) | LIFO (newest purchases first) |

## 6. Stufen

Die Entwicklung läuft in drei Stufen. Stufe 2 und Stufe 3 erscheinen im selben Release: Eine Methode lässt sich erst
umstellen, wenn ein Verkauf auch zeigt, welche Käufe er verbraucht hat. Stufe 1 ändert für alle Tenants die Prozentzahl
von Alarm und Treemap (§4.5) und kann für sich ausgeliefert werden.

Die Abnahme ruht auf festen Beispielen mit unabhängig gerechneten Werten. Die Gleichheit des Gesamtgewinns beweist die
Lot-Wahl nicht, weil ein falsch verbrauchtes Lot nur zwischen realisiert und unrealisiert verschiebt. Geprüft werden
deshalb je Verkauf die erwarteten Lots und deren Kosten.

### Stufe 1: Rechenkern und Nachweis

Umfang ist §4 ohne `CostBasisMethodService`, ohne Datenbankspalte und ohne Oberfläche. Alle Aufrufer übergeben
`CostBasisMethods.AVERAGE_ONLY`, bis Stufe 2 den Service nachzieht.

Abnahme:

- `CostLedgerTest` belegt ganzen und teilweisen Lot-Verbrauch, Verbrauch über mehrere Lots, Rundungsrest, Gratis-Lot
  und ungedeckten Verkauf.
- `SecurityGeneralCalcCostBasisTest` rechnet mit echten Domänenobjekten die Fälle der folgenden Tabelle.
- `CostBasisMethodComparisonTest` läuft als gesonderter Nachweis nach dem Muster von `CurrencyGainLossAttributionTest`
  (`@ActiveProfiles("prod")`, read-only, CSV in den Scratchpad), jedoch mit Assertions. Er vergleicht jede Position der
  echten Datenbank über alle drei Methoden und prüft den Durchschnitt gegen eine unabhängige Nachrechnung. Die Abnahme
  hängt nicht von diesem Lauf ab, weil er ohne die Datenbank nicht läuft.
- Die Ausgaben einer reinen Durchschnittsposition bleiben auf den gerundeten Getter-Werten exakt gleich. Ausnahmen sind
  die offene Rendite in Alarm und Treemap (§4.5) und die Dividendenrendite bei Zeitgleichheit (§4.4).

| Fall | Erwartung |
|---|---|
| Beispiel aus §1 | Einstand je Verkauf, Resteinstand und offene Rendite wie in der Tabelle, Gleichung aus §1 |
| Teilverkauf, dann Bewertung | Alarm, Treemap und Watchlist zeigen 66.67 %, 25.00 %, 150.00 % |
| Beispiel aus §4.6 | Werte der Tabelle, `gainLossCurrencyMC` bei allen Methoden 50 |
| Split und Reverse-Split, Stichtag davor und danach | Mengen auf der Basis des Stichtags, Kostenbetrag unverändert |
| Anleihe mit Marchzins | Einstand ohne Marchzins, Gleichung aus §1 |
| Zwei Depots mit unterschiedlicher Methode, Beispiel aus §4.2 | Gewinn 2000 im Tenant-Report und in der Depotsicht von A |
| Depotreihenfolge bei FIFO | Ergebnis unabhängig von der Reihenfolge der Depots |
| Zeitgleiche Käufe, Dividende, Verkauf, in jeder Eingabereihenfolge | Dieselbe Ordnung in Prüfung und Report, auch mit noch nicht gespeicherter Transaktion |
| Depotübertrag von A nach B, beide auf Durchschnitt, Tenant-Sicht | Verkauf vor Kauf, Gewinn des Verkaufs wie in der Depotsicht von A |
| Hypothetischer Verkauf bei FIFO ohne Übersteuerung im Portfolio | Keine Exception, `costBasisMethod` der Zeile leer |
| Margin-Eröffnung und Schluss mit gleichem Zeitstempel | Reihenfolge und Gewinn unverändert, bei allen drei Methoden gleich |
| Glattstellen und Wiederkauf | Kein Lot und kein Kostenrest aus dem geschlossenen Bestand |
| Verkauf eines Gratis-Lots, Gratis-Restbestand mit Bewertung, Dividende auf Einstand 0 | Keine Exception, Prozentwerte leer, kein nicht endlicher Wert |
| Teilübertrag und ISIN-Wechsel mit mehreren Lots | Neubuchung wie in §8 beschrieben, neues Lot nach der Methode des Ziels |

### Stufe 2: Einstellung

Umfang ist §5, einschliesslich Migration, Weitergabe an Simulationen und Tenant-Kopien, MCP und Hilfetexten.

Abnahme:

- Ein Tenant auf `FIFO` zeigt in Depot-, Portfolio- und Tenant-Sicht denselben Gewinn je Verkauf.
- Eine Übersteuerung auf FIFO oder LIFO bestimmt die Lots genau der Depots dieses Portfolios. Die Depotsicht anderer
  Portfolios ändert sich nicht. In einer Sicht über mehrere Portfolios ändert sich die Aufteilung der
  Durchschnittsdepots wie in §4.2 beschrieben.
- `gainLossSecurityMC` des Tenant-Reports entspricht der Summe der Portfolios. Der realisierte Gewinn tut das beim
  Durchschnitt nicht.
- Vorgabe ändern, explizite Portfolio-Methode beibehalten, Übersteuerung auf `null` zurücksetzen, fehlendes Feld, `null`
  und ungültiger Wert verhalten sich wie in §5.1.
- Eine Simulation und eine Tenant-Kopie rechnen mit der wirksamen Methode der Quelle.
- Ein bestehender Tenant zeigt nach der Migration gegenüber Stufe 1 unveränderte Werte.
- Eine Playwright-Spec nach dem `e2e-test`-Skill setzt die Methode am Anfang zurück, legt die geprüften Daten selbst an,
  stellt einen Tenant auf FIFO um, prüft eine Verkaufszeile und räumt am Ende auf.

### Stufe 3: Sichtbarkeit

Diese Stufe macht die Aufteilung in den Reports sichtbar. Heute zeigt GT nur `gainLossSecurity`, die Summe aus
realisiertem Gewinn, Ertrag und unrealisiertem Gewinn.

`SecurityPositionSummary` erhält drei Komponenten mit ihren Gegenstücken in Hauptwährung. Sie werden im Positionsreport
gefüllt, Bestandesübersicht und Gruppen zeigen sie an.

| Feld | Inhalt |
|---|---|
| `realizedGainLossSecurity` | Summe von `transactionGainLoss` der `REDUCE`-Zeilen |
| `incomeGainLossSecurity` | Summe aus `calcDividend`, also Dividenden und Marchzinsen, brutto oder netto gemäss `excludeDivTax` |
| `unrealizedGainLossSecurity` | `valueSecurity - adjustedCostBase` aus `calcGainLossByPrice`, 0 bei einer geschlossenen Position |

- In Wertschriftenwährung gilt `realized + income + unrealized = gainLossSecurity`. Bei Margin-Instrumenten ist
  `unrealized` das Ergebnis der offenen Positionen aus `calcGainLossByPriceForMargin`, `income` 0 und `realized` der Rest
  einschliesslich Finanzierungskosten.
- `calcMainCurrency` rechnet alle drei Komponenten zum Reportkurs um. Sie summieren sich auf `gainLossSecurityMC`,
  `gainLossCurrencyMC` bleibt daneben die vierte Grösse. Die Summe von `transactionGainLossMC` zum Kurs des
  Verkaufstags ist kein Summand.
- Geschätzte Veräusserungskosten bleiben ausserhalb der Aufteilung, weil sie auch nicht in `gainLossSecurity` des
  Positionsreports stehen.
- Summen in Wertschriftenwährung entstehen nur in `SecurityPositionCurrenyGroupSummary`, über Währungen hinweg nur in
  Hauptwährung, in `SecurityPositionGroupSummary` und der Gesamtsumme.

`consumedLots` nennt je verbrauchtem Lot die Felder von `CostLot`: `idTransaction`, Kaufzeit, Stückzahl auf der
Split-Basis des Reports, zugeordneten Einstand in Wertschriftenwährung und den Kurs zur Hauptwährung am Kauftag. Das
Depot ist das des Verkaufs, weil kein Bestandsbuch Lots eines anderen Depots hält. In der Transaktionstabelle
erscheinen die Lots als aufklappbare Zeile, das MCP liefert sie mit.

Abnahme:

| Fall | Erwartung |
|---|---|
| Beispiel aus §1 mit Nettodividende 100 | `gainLossSecurity` 2600, Durchschnitt 1500 + 100 + 1000 |
| Dividende und Marchzins, mit und ohne `excludeDivTax` | Komponenten summieren sich auf `gainLossSecurity` |
| Beispiel aus §4.6, FIFO | `gainLossSecurityMC` 300 = realisiert 240 + unrealisiert 60, `gainLossCurrencyMC` 50 |
| Margin-Position, teilweise geschlossen | Komponenten summieren sich auf `gainLossSecurity` |
| Verkauf über mehrere Lots | `consumedLots` nennt Lots, Mengen und Kosten, Summe gleich `transactionCostBase` |

## 7. Entscheidungen

| Thema | Entscheidung | Begründung |
|---|---|---|
| Ebene der Einstellung | Vorgabe im Tenant, optionale Übersteuerung im Portfolio | Deckt beide Ebenen ab. Wer nur eine Methode braucht, sieht das Portfolio-Feld nie als Pflicht. |
| Geltungsbereich FIFO/LIFO | je Depot | §4.2 |
| Geltungsbereich Durchschnitt | unverändert der Report-Topf | Bestehende Verkaufsgewinne bleiben gleich. Die Abhängigkeit vom Report und die Wechselwirkung zwischen Portfolios werden in Kauf genommen und dokumentiert. |
| Prozentzahl von Alarm, Treemap, Watchlist | überall die offene Rendite | Entspricht der Beschreibung des Alarms im Handbuch. Bestehende Alarme können dadurch anders auslösen. |
| Offene Rendite in Hauptwährung | heutige Formel für alle Methoden | Eine Definition auch für gemischte Positionen, das Modell der Geldflüsse bleibt. Eine Lot-Rendite in Hauptwährung bräuchte für alle Methoden einen Einstand in Hauptwährung. |
| Gewinn je Verkauf in Hauptwährung | Gewinn in Wertschriftenwährung mal Verkaufskurs | Bestehende Bedeutung bleibt. |
| Aufteilung des Gewinns | realisiert, Ertrag, unrealisiert, zum Reportkurs | Summiert sich auf `gainLossSecurity` und `gainLossSecurityMC`, `gainLossCurrencyMC` bleibt unverändert. |
| Reihenfolge bei gleichem Zeitstempel | Kauf, Ertrag, Verkauf, Kauf eines Depotübertrags, dann `idTransaction`, ohne Rang bei Margin | §4.4 |
| Ungedeckter Verkauf | Einstand gleich Erlös, Kennzeichnung sichtbar | Fehlende Belege dürfen keinen Gewinn ausweisen. |
| Auslieferung | Stufe 2 und 3 im selben Release | Eine rückwirkende Änderung braucht von Anfang an ihre Herleitung. |
| Änderung der Methode | wirkt rückwirkend auf die ganze Historie, auch vor `closedUntil` | Nichts ist gespeichert. `closedUntil` sperrt Transaktionen, nicht die Lesart. |
| Stichtag für einen Methodenwechsel | keiner | Ein Wechsel mitten in der Historie bräuchte eine Regel für offene Lots und brächte keinen Nutzen für die Gesamtrendite. |
| Reproduzierbarkeit früherer Auswertungen | keine | Ein Report zeigt immer die aktuelle Einstellung. Sie liesse sich nur mit gespeicherten Reportparametern erhalten. |
| Persistenz der Lots | keine | Transaktionen bleiben die einzige Quelle. Jede Korrektur wirkt sofort. |
| Depotübertrag | bleibt Verkauf und Kauf zum Schlusskurs | Umfangsentscheidung. FIFO gilt in GT seit der letzten systemischen Neubuchung. |
| ISIN-Wechsel | bleibt Verkauf und Kauf (`createPair`) | Das Paar verkauft immer den ganzen Bestand, die Methode wirkt nicht auf die Summe. |
| Margin-Instrumente | keine Methode wählbar | Die Zuordnung zur Eröffnung ist bereits fest. `connectedIdTransaction` taugt nicht als Lot-Bezug, weil ISIN-Wechsel und Übertrag es ebenfalls setzen. |
| LIFO | wird angeboten | Gewünscht und ohne Mehraufwand gegenüber FIFO. Der Hilfetext sagt, dass GT keine Steuern berechnet. |

## 8. Bekannte Grenzen

Diese Punkte gehören bei der Auslieferung ins Benutzerhandbuch.

- **GT berechnet keine Steuern.** Die Methode ist eine Auswertungssicht, die Nutzer tragen die Wahl selbst. Auch FIFO
  ergibt keine Steuerzahl, weil Gebühren, Fremdwährung, Teilfreistellungen und der fehlende Lot-Übergang beim
  Depotübertrag darin nicht steuerlich behandelt sind.
- **Der Durchschnitt bleibt reportabhängig.** Liegt eine Wertschrift in mehreren Depots, kann derselbe Verkauf je
  Sicht unterschiedliche Gewinne zeigen. FIFO und LIFO kennen diese Abweichung nicht. Stellt ein Portfolio auf FIFO
  oder LIFO um, verschiebt sich in gemeinsamen Sichten die Aufteilung der Durchschnittsdepots anderer Portfolios.
- **Die offene Rendite in Hauptwährung ist eine Mischgrösse.** Sie enthält das Währungsergebnis der ganzen Position
  (§4.6).
- **Ein Depotübertrag setzt die Lots zurück.** Im Quelldepot entsteht ein realisierter Gewinn, im Zieldepot ein neues
  Lot zum Schlusskurs mit dem Übertragsdatum, das der Methode des Ziels folgt. FIFO behandelt die übertragenen Stücke
  dort als jung, auch wenn sie ursprünglich früher gekauft wurden.
- **Ein ISIN-Wechsel fasst alle Lots zu einem zusammen.** Das neue Lot trägt den Kurs und das Datum des Wechsels.
- **Ein Methodenwechsel schreibt die Vergangenheit um.** Verkaufsgewinne ändern sich auch in abgeschlossenen Perioden.
  Bereits versandte Auswertungen stimmen danach nicht mehr mit einer neuen Abfrage überein, und GT kann sie nicht
  wiederherstellen.

## 9. Nicht im Umfang

- Steuerberechnung. Der Jahresbericht der realisierten Gewinne und die Steuerhilfe Deutschland stehen in
  `Realized_Gain_Tax_Report_Specification.md`.
- Freie Lot-Wahl je Verkauf (Specific Identification)
- Methode je Wertschrift oder Anlageklasse
- Stichtagsbezogene Methodenwechsel
- Übernahme von Lots bei Depotübertrag oder ISIN-Wechsel in den Auswertungen dieser Spezifikation. Sie bräuchte keine
  Lot-Tabelle, aber einen Lader, der die Übertragsbeziehung bis ins Quelldepot auflöst. Die Steuerhilfe Deutschland
  rechnet das in einer eigenen Engine.
- Einstand und Rendite der verbliebenen Lots in Hauptwährung

## 10. Dokumentation bei Auslieferung

- Repository `gt-user-manual`, `content/reportportfolio/_index.de.md` und `_index.en.md`: Der Absatz zum Einstandswert
  beschreibt heute nur die Durchschnittskostenmethode. Die Grenzen aus §8 gehören dorthin.
- `gt-user-manual`, `content/algoalert/strategy/pricealerts/`: Die Einstandswert-Methode verschiebt die Prozent-Schwellen
  der Haltungs-Alarme, die Referenz `avg_cost` einer Strategie nicht. Die Prozentzahl ist die offene Rendite, ohne
  realisierte Gewinne und Erträge.
- Release-Notiz zu Stufe 1: Alarm und Treemap zeigen nach Teilverkäufen oder Erträgen andere Prozentzahlen, und bestehende
  Alarme können anders auslösen.
- Javadoc von `SecurityCalcService`, `SecurityGeneralCalc`, `SecurityBaseCalc` und `AlgoHoldingGainLossService`, die
  heute allein den Durchschnitt nennen, sowie von `calcGainLossByPrice` und `getPositionGainLossPercentage`.
