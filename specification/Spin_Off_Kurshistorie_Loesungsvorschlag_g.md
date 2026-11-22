# Kurshistorie der Mutter bei einer Abspaltung — Lösungsvorschlag

**Gegenstand:** §6.6 der `Spin_Off_Security_Action_Specification.md` (Codebasis Backend 0.40.2).
**Geprüft an:** Quellcode dieses Stands, Datenbank `grafioschtrader` am 2026-11-21, Yahoo-Chart-API
`query2.finance.yahoo.com/v8/finance/chart/GE` am selben Tag.

## 1. Urteil

Die Richtung von §6.6 stimmt. Eine erfasste Abspaltung verdrängt den Provider-Split vom Ex-Tag, und die
gespeicherte Kurshistorie der Mutter muss der Kurs sein, zu dem an diesem Tag gehandelt wurde. Der
Performance-Satz pro Wertpapier ist

```
Gewinn = Wert(Ende) − Wert(Anfang) − Summe der Flows
```

(`SecurityPeriodSeries.calculate`). Der Flow der Mutter ist an diesem Tag negativ und vom Betrag her der
Marktwert des Kindes (`Spin_Off_Security_Action_Specification.md` §9.5 und §11). Am unveränderten
Handelskurs fällt der Mutterkurs ungefähr um diesen Betrag, der negative Flow hebt den Fall in der
Rechnung auf, und übrig bleibt die echte Marktbewegung. Liegt der Kurs vor dem Ex-Tag schon durch *f*
geteilt in der Tabelle, fehlt der Fall in der Reihe, der Flow addiert den Kindeswert trotzdem, und die
Periode weist einen Gewinn in dieser Höhe aus. Zusätzlich ist der Bestandswert vor dem Ex-Tag um *f* zu
tief, weil die Stücke nicht mitmultipliziert werden: der Split wurde ja verworfen.

Der Schalter dafür ist in §6.6 die Gleichheit von `idConnectorHistory` und `idConnectorSplit`. Dieser
Schalter beschreibt nicht, ob die geladene Reihe durch *f* geteilt wurde. In der Datenbank ist die
ungleiche Kombination der häufigere Fall (Abschnitt 4). Zwei Folgen:

- Eine Reihe, die der Historien-Konnektor nie geteilt hat, bleibt zu Recht unverändert. SIX und Onvista
  liefern hier den Handelskurs. Mit dem Yahoo-Faktor multipliziert wäre jeder Kurs vor dem Ex-Tag um *f*
  zu hoch, und die Periode wiese einen entsprechend grossen Verlust aus.
- Eine Reihe, die ein anderer Konnektor bereits geteilt hat, bleibt geteilt. EODHD, StockAnalysis,
  Eulerpool und Alpha Vantage speichern einen adjustierten Close. Dort bleibt der Fehler bestehen, den
  §6.6 für den gleichen Konnektor gerade beseitigt.

Der Faktor, mit dem zurückgerechnet wird, ist der Koeffizient der Reihe, die multipliziert wird. Yahoos
`1281 : 1000` ist der Koeffizient der Yahoo-Reihe von `GE`. Er ist nicht der Koeffizient der SIX-Reihe
von Novartis.

## 2. Was die Rechnung von der Reihe verlangt

Gespeichert wird der Handelskurs in der Stückbasis der Splits, die in `securitysplit` stehen. Der
Pseudo-Split der Abspaltung steht dort nicht. Also darf er auch nicht im Kurs stecken.

`Historyquote.close` ist die Basis des Konnektors zum Zeitpunkt des Ladens.
`SecurityActionService.getAsTradedClose` multipliziert mit dem Produkt der Splits nach dem Tag
(`Securitysplit.calcSplitFatorForFromDate`, Split am Tag selbst zählt als schon erfolgt). *P_p* ist
dieser Handelskurs am Ex-Tag. Der Ex-Tag selbst wird nicht mit *f* multipliziert: *g(d)* läuft über
Abspaltungen mit `actionDate > d`.

Für jeden Kurstag *d* vor einer Abspaltung *a*, deren Reihe den Faktor wirklich enthält:

```
g(d) = Π historyAdjustmentFactor(a)    über alle solchen Abspaltungen mit actionDate > d
Open, High, Low, Close × g(d)
Volumen ÷ g(d), gerundet, nur wenn dieser Konnektor das Volumen mit adjustiert
```

`historyAdjustmentFactor` ist null, wenn die geladene Reihe den Schritt nicht enthält. Dann ist der
Faktor in dem Produkt 1. `providerSplitFactor` bleibt der verworfene Split des Split-Konnektors und wird
nicht zum Multiplikator einer fremden Reihe.

## 3. Yahoo, und was in der Datenbank von General Electric steht

`YahooFeedConnectorCOM.getEodHistory` liest `indicators.quote.close` bei `events=history`, nicht
`adjclose`. Dieser Close ist split-adjustiert, der AdjClose zusätzlich um Dividenden. Die Splits liest
derselbe Konnektor als `fromFactor = denominator`, `toFactor = numerator`
(`YahooFeedConnectorCOM.getSplitHistory`). Die Chart-API lieferte am 2026-11-21 für `GE`:

| Tag | Yahoo-Close heute | Faktor danach | Close × Faktor | Close in `historyquote` |
|---|---:|---:|---:|---:|
| 2023-01-03 | 52.94397354 | 1.281 × 1.253 | 84.9800 | 84.9800 |
| 2023-01-04 | 56.02553940 | 1.253 | 70.2000 | 70.2000 |
| 2024-04-01 | 139.95211792 | 1.253 | 175.3600 | 175.3600 |
| 2024-04-02 | 136.47000122 | 1 | 136.4700 | 136.4700 |

Die beiden Pseudo-Splits sind `1000 → 1281` am 2023-01-04 und `1000 → 1253` am 2024-04-02, dazu der
Reverse Split `8 → 1` am 2021-08-02. Das Produkt der beiden Abspaltungsfaktoren trifft den gespeicherten
Close auf den Zehntelcent. Für diese Reihe ist der veröffentlichte Split der Adjustierungskoeffizient,
keine gerundete Näherung.

Das Volumen hat Yahoo in derselben Richtung skaliert. Am 2023-01-03 steht live 10 280 781 gegen
6 401 500 in der Tabelle (Verhältnis 1.606, Produkt der Faktoren 1.605), am 2023-01-04 steht 21 031 104
gegen 16 751 600 (1.255 gegen 1.253). Die Preise treffen exakt, das Volumen bis auf wenige Promille.

`securitycurrency.full_load_timestamp` von GE (3554) ist der 2021-08-04. Die Tage danach kamen als
Nachzug herein und wurden nie neu geladen. Deshalb steht in der Tabelle noch der Kurs, den Yahoo zum
Zeitpunkt des ersten Ladens auslieferte, also der Handelskurs. In `securitysplit` fehlen die beiden
Pseudo-Splits; der jüngste Konnektor-Split ist der Reverse Split von 2021. Ein Full-Reload heute würde
52.94 an die Stelle von 84.98 schreiben. Die Rückrechnung von §6.6 stellt für den Fall Yahoo-Historie
und Yahoo-Split genau diesen Handelskurs wieder her. Diesen Teil würde ich behalten.

Der Reload darf den Faktor erst anwenden, wenn die gelieferte Reihe ihn enthält. Yahoo publiziert den
Split oft Tage vor der Anpassung der Historie (`IFeedConnector.getNextAttemptInDaysForSplitHistorical`,
Task `CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES`). Multipliziert der Refresh in diesem Fenster,
wird aus dem noch rohen 84.98 ein 108.86, und die Periode weist einen Verlust in der Grösse des
Kindeswerts plus dieses Aufschlags aus.

## 4. Welche Paare in der Datenbank wirklich vorkommen

787 Wertpapiere. Historie und Split:

| Historie | Split | Anzahl | Was die Historie liefert |
|---|---|---:|---|
| `gt.datafeed.yahoo` | `gt.datafeed.yahoo` | 132 | Close split-adjustiert, Abschnitt 3 |
| `gt.datafeed.six` | `gt.datafeed.yahoo` | 102 | Handelskurs, siehe unten |
| `gt.datafeed.eodhistoricaldata` | `gt.datafeed.eodhistoricaldata` | 84 | `Adjusted_close`: Splits und Dividenden (`EodHistoricalDataConnector.parseResponseLineItems`, Feld 5) |
| `gt.datafeed.xetra` | `gt.datafeed.yahoo` | 34 | `cleanSplit=true`: Splits herausgerechnet, Ausschüttungen und Bezugsrechte nicht (`XetraFeedConnector.getPriceHistoryLink`) |
| `gt.datafeed.eodhistoricaldata` | `gt.datafeed.yahoo` | 16 | adjustierter Close, Split-Faktor kommt von Yahoo |
| `gt.datafeed.onvista` | `gt.datafeed.yahoo` | 11 | Handelskurs, siehe unten |
| `gt.datafeed.yahoo` | `gt.datafeed.eodhistoricaldata` | 3 | Yahoo-Close adjustiert, der verworfene Split ist der von EODHD |
| `gt.datafeed.stockanalysis` | `gt.datafeed.yahoo` | 3 | vom Konnektor als split-adjustiert dokumentiert |
| `gt.datafeed.eulerpool` | `gt.datafeed.yahoo` | 1 | dokumentiert split-adjustiert, eigener Split-Feed |
| `gt.datafeed.alphavantage` | `gt.datafeed.yahoo` | 1 | speichert `adjusted_close` (Feld 5) |

Bei gleichem Konnektor ist `url_history_extend` in dieser Datenbank immer gleich `url_split_extend`
(0 Abweichungen). Die Gleichheit der Id allein erzwingt das nicht.

SIX und Onvista zeigen den Fall am Ex-Tag. Splits aus diesen Tagen liegen nicht in `securitysplit`.

| Mutter | Historie | Ex-Tag | Close davor | Close am Tag |
|---|---|---|---:|---:|
| Novartis, SIX, 4141 | `CH0012005267CHF4` | 2023-10-04, Sandoz | 91.94 | 87.60 |
| ABB, SIX, 3595 | `CH0012221716CHF4` | 2022-10-03, Accelleron | 25.80 | 24.35 |
| Mercedes-Benz, Onvista, 3565 | Yahoo-Split `DAI.DE` | 2021-12-10, Daimler Truck | 86.18 | 74.25 |

Eine durch *f* ≈ 1.14 geteilte Mercedes-Reihe wäre über den Ex-Tag nahezu flach. Sie fällt um 11.93 EUR.
Novartis fällt um 4.34 CHF. Das sind die Handelskurse. §6.6 lässt sie stehen, und das ist richtig.

Siemens (Onvista, 3560) fällt vom 2020-09-25 auf den 2020-09-28 nur von 111.68 auf 109.96. Verteilt
wurden 55 % von Siemens Energy, eine Energy-Aktie je zwei Siemens-Aktien, Eröffnung 22.01 EUR. Der
rechnerische Abschlag lag bei etwa 5 %, der Markt hat weniger nachgegeben. Bei *f* ≈ 1.05 liegt ein
normaler Tag und ein adjustierter Tag im selben Band. Aus diesem einen Verhältnis darf niemand einen
Faktor ableiten.

## 5. Drei Fälle, die der Id-Vergleich zusammenwirft

**Gleicher Konnektor, gleiche URL, Reihe schon geteilt.** GE, sobald ein Full-Reload die heutigen
Yahoo-Closes holt. Rückrechnung mit dem eigenen Faktor. Das ist der Inhalt von §6.6, und die Tabelle in
Abschnitt 3 ist die Probe.

**Gleicher Konnektor, Reihe noch nicht geteilt.** Split steht, die alten Closes sind noch die
Handelskurse. Zustand von GE in dieser Datenbank seit 2021. Rückrechnung erst nach der nächsten vollen
Ladung, und nur wenn das Verhältnis aus gespeichertem Close und neu geliefertem Close über die Tage vor
dem Ex-Tag bei *g(d)* liegt und am letzten Ex-Tag bei 1. Bis dahin bleibt die Reihe, und der bestehende
Retry (`historicalDataUpdateWhenAdjusted`) lädt später neu. Ein unmittelbares Multiplizieren im
`SPIN_OFF_PARENT_REFRESH` würde die noch rohe Reihe aufblähen.

**Fremder Historien-Konnektor.** Der Yahoo-Faktor wird nicht an die fremde Reihe multipliziert.

- SIX, Onvista, Boursorama, Wien, Warschau, BX Swiss: die untersuchten Reihen enthalten den Fall. Faktor
  der Historie ist null. Speichern, wie geliefert.
- EODHD, StockAnalysis, Eulerpool, Alpha Vantage: die Reihe ist adjustiert, auch wenn der Split von
  Yahoo kommt. Zurückgerechnet wird mit dem Faktor, den der Historien-Konnektor selbst für diesen Tag
  nennt. EODHD und Eulerpool haben einen Split-Feed; der Aufruf benutzt `url_history_extend` und den
  Historien-Konnektor, nicht `url_split_extend`. Liefert dieser Feed am Ex-Tag einen Split, ist dessen
  `getFactor()` der `historyAdjustmentFactor`. Weicht er von Yahoos Faktor ab, bleiben beide Zahlen
  gespeichert: die eine wirft den Split weg, die andere skaliert die Kurse.
- Xetra rechnet Splits heraus und lässt Ausschüttungen stehen. Ob `cleanSplit` eine Abspaltung entfernt,
  sagt der Konnektor nicht. Ohne eigenen Split am Ex-Tag bleibt der Faktor null, die Reihe wird
  gespeichert wie geliefert, und der Dialog zeigt das Ergebnis der Prüfung aus Abschnitt 6.
- Yahoo-Historie bei EODHD-Split (3 Papiere): die Reihe trägt Yahoos Koeffizienten. Den liefert ein
  Split-Abruf über den Historien-Konnektor mit `url_history_extend`. Der verworfene EODHD-Split skaliert
  diese Reihe nicht.

EODHD-`Adjusted_close` enthält zusätzlich die Dividendenanpassung. Die Rückrechnung nimmt nur den
Abspaltungsfaktor heraus. Die Dividendenanpassung bleibt, wie sie bei jedem EODHD-Papier heute schon in
`historyquote` steht. Über den Ex-Tag hebt sich dieser langsame Faktor weitgehend auf. Einen
Handelskurs rekonstruiert die Rückrechnung für EODHD nicht.

## 6. Entscheidung je Abspaltung

Eine Methode, `SpinOffHistoryBasis.decide`, läuft im Parent-Refresh nach dem Split-Import und vor dem
Full-Reload. Eingang ist die vom Konnektor gelieferte Reihe, vor jeder Skalierung. Pro Abspaltung der
Mutter entsteht ein Entschluss `RAW`, `ADJUSTED` oder `UNKNOWN`.

Kandidat *f*:

1. Historien-Konnektor und Split-Konnektor sind gleich und die beiden URL-Erweiterungen sind gleich:
   *f* = `providerSplitFactor`.
2. Sonst, wenn der Historien-Konnektor `FS_SPLIT` kann: ein Split-Abruf mit dem Historien-Symbol. Ein
   Split mit Datum = Ex-Tag setzt *f* auf dessen Faktor. Dieser Split wird nicht nach `securitysplit`
   geschrieben.
3. Sonst gibt es keinen Kandidaten. Entschluss `RAW`, sobald die Reihe den Fall zeigt, sonst `UNKNOWN`.
   Multipliziert wird nicht.

Prüfung der gelieferten Reihe, *D* der Ex-Tag, *D₋* der letzte gelieferte Kurs davor,
*j* = Close(*D*) / Close(*D₋*):

```
m_adj = j          Marktbewegung, wenn die Reihe durch f geteilt wurde
m_raw = j × f      Marktbewegung, wenn sie den Handelskurs enthält
```

Band *b* = ln(1.08). Das ist die Bewegung, die GE HealthCare am Ex-Tag noch als adjustiert erkennbar
liess (live *j* = 1.058, |ln *j*| = 0.056) und den rohen gespeicherten Schritt 70.20 / 84.98 = 0.826
als roh (|ln(0.826 × 1.281)| = |ln 1.058| = 0.056).

- `ADJUSTED`, wenn |ln *j*| ≤ *b* und |ln(*j* × *f*)| > *b*.
- `RAW`, wenn |ln(*j* × *f*)| ≤ *b* und |ln *j*| > *b*.
- sonst `UNKNOWN`.

Bei `ADJUSTED` wird *f* zum `historyAdjustmentFactor`, aber nur wenn der Kandidat aus dem eigenen
Historien-Konnektor stammt (Schritt 1 oder 2). Ein Yahoo-Faktor auf einer SIX-Reihe wird nie zum
Multiplikator, auch wenn ein einzelner Tag zufällig flach aussieht.

Bei `RAW` ist der Faktor null.

Bei `UNKNOWN` ist der Faktor null. Der Dialog nennt *j*, *f* und die beiden Abstände. Der Administrator
kann den Entschluss auf `ADJUSTED` oder `RAW` stellen; diese Wahl bleibt an der Abspaltung stehen und
gilt für jede folgende Ladung, bis ein Full-Reload sie neu prüft oder er sie zurücksetzt. Unter *f* ≈ 1.08
(Siemens Energy, Sandoz liegt je nach Kindeskurs in dieser Gegend) ist `UNKNOWN` der ehrliche Ausgang.
Ein erzwungener `ADJUSTED` ohne eigenen Historien-Faktor benutzt den Split-Konnektor-Faktor und der
Dialog sagt, dass der Kurs um das Verhältnis der beiden Koeffizienten daneben liegen kann.

Dieselbe Prüfung läuft über ein Fenster, wenn die Tabelle schon Kurse hält. Für jeden Tag im
Rückblickfenster, das `isLatestSplitHistoryquotePossibleAdjusted` schon benutzt:

```
r(d) = gespeicherter Close(d) / gelieferter Close(d)
```

`ADJUSTED` ist bestätigt, wenn *r(d)* vor dem Ex-Tag bei *g(d)* liegt und am letzten Ex-Tag bei 1.
Für GE heute wäre das 84.98 / 52.94 = 1.605 und 136.47 / 136.47 = 1. Das ist die stärkere Probe, weil
sie die ganze Niveauverschiebung sieht und nicht einen Tag. Sie ersetzt den Ein-Tages-Test, sobald
beide Reihen da sind.

`UNKNOWN` oder `RAW` bei schon veröffentlichtem Split des gleichen Konnektors plant den bestehenden
Retry. Der Refresh multipliziert in diesem Zustand nicht und lädt die Historie nicht neu. Ein Reload
auf eine noch rohe Reihe wäre der teurere Fehler.

Mehrere Abspaltungen werden einzeln entschieden. *g(d)* ist das Produkt der Faktoren, deren Entschluss
`ADJUSTED` ist. Hat ein Provider nur die jüngere Abspaltung in die Reihe gerechnet, skaliert die ältere
Strecke nicht mit dem älteren Faktor.

## 7. Wo die Skalierung angewandt wird

`SpinOffProviderAdjustment.revert` bleibt die eine Methode und liest `historyAdjustmentFactor`. Sie
läuft an den drei Stellen, die §6.6 nennt:

- `SecurityJpaRepositoryImpl.getHistoryQuote`
- `SecurityJpaRepositoryImpl.getDataByConnnector`, damit der Split-Vergleich beide Seiten in derselben
  Basis sieht
- dem direkten Konnektor-Laden in `GTNetHistoricalImportService`

Kurse, die ein Peer über `HistoryquoteThruGTNet` liefert, gehen durch dieselbe Entscheidung. Die
gelieferte Strecke um den Ex-Tag wird geprüft. Sieht sie adjustiert aus und der eigene
Historien-Faktor ist bekannt, wird sie skaliert. Sieht sie roh aus, bleibt sie: ein Peer, der schon
zurückgerechnet hat, wird nicht ein zweites Mal multipliziert. Die pauschale Übernahme, die §6.6 für
GTNet vorsieht, würde eine adjustierte Peer-Reihe wieder in die Tabelle schreiben.

Eine inkrementelle Ladung nach dem letzten Ex-Tag hat *g* = 1 und ändert nichts. Eine Ladung, die Tage
vor dem Ex-Tag holt, benutzt den gespeicherten Entschluss und prüft nicht neu an einem Fenster, das sie
gar nicht enthält. Neu geprüft wird beim Parent-Refresh und beim Full-Reload.

`supplementFromShadow` teilt Archivzeilen durch die Splits nach dem Archivdatum. Der Abspaltungsfaktor
steht nicht in `securitysplit`, deshalb behalten Archivzeilen die Basis, in der sie archiviert wurden.
Nach einem Konnektorwechsel können Archiv und Neuladung auseinanderlaufen. Der Dialog prüft die
gespeicherte Reihe nach dem Zusammenfügen, damit dieser Bruch sichtbar ist. Die Skalierung selbst gilt
für die Zeilen, die der Historien-Konnektor bei dieser Ladung liefert.

Das Volumen wird nur mit skaliert, wenn die Preise skaliert werden und der Historien-Konnektor das
Volumen adjustiert. Für Yahoo ist das belegt (Abschnitt 3). Für einen Konnektor ohne diesen Beleg
bleiben die Stückzahlen stehen; ein falsches Volumen stört die Performance nicht, ein durch *f*
geteiltes Volumen eines roh gelieferten Umsatzes wäre eine erfundene Zahl.

## 8. Was die Zahlen dann tun

Vernova-Tag, eine Aktie, Yahoo-Faktor 1.253. Der Kindeswert aus dem Faktor ist
175.36 − 139.95 = 35.41 USD. Die Beine buchen den echten Kindeskurs; die Differenz zum Faktor ist die
Marktbewegung des Tages und bleibt zu Recht im Gewinn.

| Reihe | Wert Vortag | Wert Ex-Tag | Flow | Gewinn |
|---|---:|---:|---:|---:|
| Handelskurs, Faktor angewendet oder Reihe war schon roh | 175.36 | 136.47 | −35.41 | −3.48 |
| Yahoo-Close unverändert, Split verworfen | 139.95 | 136.47 | −35.41 | +31.93 |
| SIX- oder Onvista-Handelskurs zusätzlich mit 1.253 multipliziert | 219.72 | 136.47 | −35.41 | −47.84 |

−3.48 ist die Bewegung, die auch in Yahoos adjustierter Reihe zwischen 139.95 und 136.47 steckt. +31.93
ist diese Bewegung plus der Kindeswert. −47.84 ist der Schaden einer Rückrechnung auf einer Reihe, die
den Schritt nie enthalten hat. Deshalb skaliert ein fremder Faktor nie automatisch.

Dieselbe Rechnung über ein langes Fenster: jeder Close vor dem Ex-Tag ist in der adjustierten Reihe
durch *f* geteilt. Der Gewinn vom alten Anfang bis heute ist um `Anfang × (1 − 1/f)` zu hoch, auch wenn
man den einen Flow am Ex-Tag richtig bucht. Der Bestandeswert aller Tage davor ist um *f* zu tief.

## 9. Dialog und Persistenz

Zusätzlich zu `provider_split_factor`:

| Spalte auf `security_action` | Inhalt |
|---|---|
| `history_adjustment_factor` | Der Faktor, mit dem diese Abspaltung die Historie skaliert. Null, wenn die Reihe roh geladen wird. |
| `history_basis` | `AUTO`, `FORCE_ADJUSTED`, `FORCE_RAW`. `AUTO` ist der Entschluss von Abschnitt 6. |

Der Endpunkt von §15.1 liefert je Abspaltung: Konnektor und URL beider Seiten, den verworfenen Split,
den Historien-Faktor, *j*, den Entschluss und ob das Volumen mit skaliert wird. Die Plausibilität
*j* > 1/√*f* aus §6.6 bleibt als Anzeige der gespeicherten Reihe nach dem Reload. Sie entscheidet die
Skalierung nicht. Bei *f* nahe 1 liegt die Schwelle 1/√*f* so dicht an 1, dass ein gewöhnlicher Tag die
Anzeige kippt; der Dialog zeigt dann `UNKNOWN` statt eines bestimmten Wortes.

Ein Konnektorwechsel ist keine Voraussetzung. Wer die SIX-Reihe behalten will, behält sie. Wer eine
adjustierte EODHD-Reihe hat, bekommt deren eigenen Faktor, ohne das Historien-Symbol auf Yahoo zu
drehen.

## 10. Was aus §6.6 unverändert gilt

- Der Konnektor-Split mit Datum = Ex-Tag wird verworfen, ein Split an einem anderen Tag bleibt,
  ein Split mit `create_type` ungleich `CONNECTOR_CREATED` bleibt.
- `providerSplitFactor` schreibt nur der Split-Import.
- Anlegen und Löschen der Abspaltung stellen `SPIN_OFF_PARENT_REFRESH`. Eine Korrektur von *q* nicht.
- Solange der Refresh wartet oder läuft, werden Vorschau und Anwenden mit
  `gt.security.action.spinoff.refresh.pending` abgewiesen.
- Mit dem Löschen der letzten Abspaltung kommt der Provider-Split zurück, der Historien-Faktor wird
  null, und die nächste Ladung speichert die Reihe wieder so, wie der Konnektor sie liefert.
- Die drei Einstiegspunkte der Skalierung bleiben. Die Methode skaliert mit
  `historyAdjustmentFactor` und lässt Kurse ab dem letzten Ex-Tag unverändert.

## 11. Grenzen

- Ein Provider, der die Abspaltung an einem anderen Tag als dem Ex-Tag adjustiert, fällt durch die
  Datumsprüfung. Der Split bleibt gespeichert, der Historien-Faktor bleibt null. Der Dialog listet
  Splits im Fenster von zehn Tagen, wie §15.1 das schon vorsieht.
- Ein echter Split und die Abspaltung am selben Kalendertag landen bei Yahoo in einem Verhältnis. Der
  Import verwirft dieses eine Verhältnis. Zwei getrennte Ereignisse am selben Tag kann der Konnektor
  so nicht liefern.
- Die Ein-Tages-Prüfung trägt bei kleinem *f* nicht. Unter der Bandgrenze bleibt der Faktor null, bis
  der Administrator einen Entschluss setzt oder das Verhältnis gespeicherter zu gelieferter Reihe den
  Faktor über das Fenster bestätigt.
- Archivzeilen aus `historyquote_legacy` tragen die Basis ihres Archivtags. Ein Mix nach einem
  Konnektorwechsel ist an der Prüfung der gespeicherten Reihe sichtbar und wird nicht still
  umgerechnet.
- Die Dividendenanpassung von EODHD und Alpha Vantage bleibt in der Reihe. Sie ist der heutige Inhalt
  von `historyquote` für diese Konnektoren und ist nicht der Abspaltungsschritt.
