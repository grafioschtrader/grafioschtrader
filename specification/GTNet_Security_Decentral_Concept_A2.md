# GTNet-Sicherheitskonzept A2 — Preisintegrität zuerst: Härtung, signierte Herkunft, Bestätigung und Rückruf

**Status**: Nur Konzept — nichts aus diesem Dokument ist umgesetzt.

**Code baseline:** backend 0.38.1, highest Flyway script
`V0_38_2__correlation_set_include_dividends_and_hold_portfolio_indexes.sql`.

**Positionierung**: A2 ist eine dezentrale Variante neben `GTNet_Security_Central_Server_Concept.md` und
`GTNet_Security_Decentral_Concept.md` (A1). Die drei Konzepte belegen dieselben freien Codes in
`GNetCoreMessageCode` und erweitern denselben Handshake; umgesetzt wird eines. Eine spätere Kombination (etwa eine
Zertifizierung der A2-Schlüssel durch einen zentralen Aussteller) ist möglich, verlangt aber eine eigene
Protokollversion — gleiche `gti1`-Kennungen allein machen zwei Varianten nicht kompatibel.

**Leitsatz**: *Ein über GTNet empfangener Wert wird nur so weit verwendet, wie die lokal festgelegte
Vertrauensanforderung erfüllt ist. Fehlt diese Grundlage oder fällt sie später weg, bleibt seine Verwendung begrenzt
und seine Herkunft nachvollziehbar.*

Zwei Begriffe werden durchgehend getrennt:

- **Plausibel** — der Wert hat die deterministischen und heuristischen Prüfungen des Import-Gates bestanden (§9). Das
  sagt nichts über seine Richtigkeit.
- **Bestätigt** — der eigene Konnektor oder eine Gruppe unabhängiger, übereinstimmender Quellen trägt den Wert (§10).
  Nur bestätigte Werte erreichen im Standardprofil die Tabellen, aus denen Bewertung, Performance, Steuer und
  Algorithmen rechnen.

Keine Signatur, kein Median und keine Stichprobe beweist die wirtschaftliche Richtigkeit eines Kurses. A2 macht die
Vertrauensannahme deshalb ausdrücklich: Bestätigung beruht auf dem eigenen Konnektor oder auf Quellen, die der
Administrator freigegeben und als voneinander unabhängig eingestuft hat.

---

## Inhalt

1. [Entscheidungen](#1-entscheidungen)
2. [Ausgangslage im Code](#2-ausgangslage-im-code)
3. [Bedrohungsmodell und Schutzziele](#3-bedrohungsmodell-und-schutzziele)
4. [Schichtenarchitektur](#4-schichtenarchitektur)
5. [Phase 0: Härtung des bestehenden Austauschs](#5-phase-0-härtung-des-bestehenden-austauschs)
6. [Identität, Handshake und Schlüssel](#6-identität-handshake-und-schlüssel)
7. [Signierte Kursdatensätze](#7-signierte-kursdatensätze)
8. [Ursprungs-Freigabe](#8-ursprungs-freigabe)
9. [Import-Gate](#9-import-gate)
10. [Bestätigung und Übernahme](#10-bestätigung-und-übernahme)
11. [Stichprobenprüfung und Drift-Erkennung](#11-stichprobenprüfung-und-drift-erkennung)
12. [Ursprungszustände und Sanktionen](#12-ursprungszustände-und-sanktionen)
13. [Rückruf und Reparatur](#13-rückruf-und-reparatur)
14. [Auswertungen, Alarme und Benutzeroberfläche](#14-auswertungen-alarme-und-benutzeroberfläche)
15. [Signierte Sperrliste](#15-signierte-sperrliste)
16. [Nachrichten-Codes, Protokollregeln und Budgets](#16-nachrichten-codes-protokollregeln-und-budgets)
17. [Datenmodell](#17-datenmodell)
18. [Konfiguration](#18-konfiguration)
19. [Angriffsanalyse](#19-angriffsanalyse)
20. [Verhältnis zu den Varianten Zentral und A1](#20-verhältnis-zu-den-varianten-zentral-und-a1)
21. [Umsetzungsphasen](#21-umsetzungsphasen)
22. [Abnahmeszenarien](#22-abnahmeszenarien)
23. [Bekannte Grenzen](#23-bekannte-grenzen)

---

## 1. Entscheidungen

| # | Frage | Entscheidung |
|---|---|---|
| A2-D1 | Erkennen oder Verhindern? | **Verhindern.** Jeder Datensatz durchläuft vor dem Schreiben Gate und Übernahmeregel (§9, §10). Statistik (§11) ergänzt, ersetzt nie. |
| A2-D2 | Darf ein unbestätigter Wert autoritativ werden? | **Standard: nein** (Profil *streng*). Unbestätigte, plausible Werte bleiben im Staging und werden als unbestätigte Beobachtung angezeigt. Ein **instanzweiter Schalter** (Profil *gelockert*) lässt Einzelquellenwerte freigegebener Ursprünge zu; abhängige Ergebnisse gelten dann als vorläufig (§10.6, §14). |
| A2-D3 | Was geschieht mit einem Kurssprung ohne Bestätigung? | Er **bleibt liegen, bis der Administrator entscheidet** — in beiden Profilen, ohne zeitgesteuerte Freigabe. Ein verspäteter Kurs ist sichtbar, ein still falscher nicht. |
| A2-D4 | Ist die Herkunft beweisbar? | **Ja, Pflicht im signierten Modus.** Werte, Instrument und Datum werden ausschliesslich aus dem verifizierten Payload gelesen (§7.6). |
| A2-D5 | Wer bestimmt, wessen Kurse zählen? | Jede Instanz pro **Ursprung**, nicht nur pro direktem Peer; Freigabe **manuell**. Reifekriterien erzeugen nur eine Freigabeempfehlung (§8). |
| A2-D6 | Was ist Bestätigung? | Eigener Konnektor, oder eine **übereinstimmende Gruppe** von mindestens `q` unabhängigen freigegebenen Quellen ohne Widerspruch einer weiteren freigegebenen Quelle. Jede Bestätigung hinterlegt einen unveränderlichen **Beleg** (§10). |
| A2-D7 | Wer hat Vorrang, eigener Konnektor oder GTNet? | Der **eigene Konnektor beim Wert**: ein GTNet-Wert ersetzt nie einen frischen eigenen Wert. Die Abfragereihenfolge (GTNet zuerst, um Konnektor-Kontingente zu sparen) bleibt einstellbar (H11). |
| A2-D8 | Wie werden schleichende Verschiebungen erkannt? | CUSUM **pro Instrument** gegen den methodischen Grundversatz des Ursprungs und **pro Anlageklasse** als Warnsignal (§11.4). |
| A2-D9 | Wie wird sanktioniert? | Wertebene sofort (zurückhalten, entziehen). Ursprungsebene gestuft: VERDÄCHTIG → befristete QUARANTÄNE nach deduplizierten, teils doppelt belegten Ereignissen → GESPERRT nur durch Beweis (Equivocation, Widerruf, Sperrliste) oder Administrator (§12). |
| A2-D10 | Was geschieht mit bereits verwendeten falschen Werten? | **Rückruf** als dauerhafter, wiederanlauffähiger Auftrag: Belege neu bewerten, Originale archivieren, ersetzen oder entziehen, abhängige Reihen und Tageswerte neu rechnen (§13). |
| A2-D11 | Wird Evidenz verbreitet? | Nein. Nur ein beratender 1-Hop-Hinweis; bei Equivocation selbstbeweisend (§16). |
| A2-D12 | Netzweite Sperren? | Optional über eine abonnierbare, offline signierte Sperrliste, als letzte Phase (§15). |
| A2-D13 | Wo beginnt die Umsetzung? | Mit **Phase 0** (§5): Lücken im heutigen Austausch, ohne neue Identität schliessbar, in jedem Konzept nötig. |

---

## 2. Ausgangslage im Code

Pfade relativ zu `backend/grafioschtrader-server/src/main/java/grafioschtrader/`, sofern nicht anders angegeben.

### 2.1 Schreibpfade für Kurse

| # | Heutiges Verhalten | Stelle |
|---|---|---|
| L1 | Empfangene Kurse werden nicht gegen eine Referenz geprüft. Geprüft wird nur „Datum und Close vorhanden“; `BaseHistoryquote.resetNonPositiveOhlcvToNull` setzt nichtpositive Open/High/Low/Volume auf `NULL`, lässt Close aber unverändert. | alle Pfade unten; `grafioschtrader-common/.../entities/BaseHistoryquote.java` |
| L2 | „Neuerer Zeitstempel gewinnt“ **ohne Obergrenze**; ein Zeitstempel in der Zukunft gewinnt dauerhaft. Die Skew-Prüfung (`g.gnet.max.clock.skew.minutes`, Standard 5, in `GTNetProtocolLimits`) gilt nur dem Envelope. | `service/GTNetLastpricePoolService.isNewer`, `gtnet/handler/impl/lastprice/OpenLastpriceQueryStrategy.isNewer`, `service/InstrumentExchangeSet.isNewer` |
| L3 | Ein **anfragender** Peer schreibt in die Kurse des Lieferanten: mitgeschickte Preise werden bei neuerem Zeitstempel in `security`/`currencypair` des AC_OPEN-Lieferanten übernommen, ohne `gtNetLastpriceRecv`. | `OpenLastpriceQueryStrategy.querySecurities` / `queryCurrencypairs` → `updateSecurityFromDTO` / `updateCurrencypairFromDTO` |
| L4 | Ein AC_PUSH_OPEN-Lieferant legt für ein im Pool fehlendes Instrument den Anfragepreis **ohne jede Zeitprüfung** als neuen Pool-Eintrag an. | `gtnet/handler/impl/lastprice/PushOpenLastpriceQueryStrategy` (Abschnitt „instruments not found in pool“) |
| L5 | Last-Preis-Push schreibt in den Pool (`GTNetLastpricePoolService.updateSecurityLastpricesFromDTO`); die benutzerwirksamen Last-Preis-Schreibstellen sind `InstrumentExchangeSet.processResponse` und L3. Eine AC_PUSH_OPEN-Instanz füllt ihr eigenes `s_last` zuerst aus dem Pool. | `gtnet/handler/impl/LastpricePushHandler`, `service/GTNetLastpriceService` |
| L6 | Historische Kurse gehen bei lokal vorhandenem Instrument direkt in `historyquote`, nur für fehlende Tage, ohne Datumsbereichs- oder Handelstagprüfung. Push und direkter API-Pfad ignorieren `gtNetHistoricalRecv`. Ein falscher Wert bleibt dauerhaft, und **wer einen Tag zuerst liefert, besitzt ihn**. | `service/GTNetHistoryquoteService.storeHistoryquotesForInstrument`, `gtnet/handler/impl/HistoryquotePushHandler.storeHistoryquotesForInstrument`, `gtnet/handler/impl/historyquote/PushOpenHistoryquoteQueryStrategy` |
| L7 | Die lokale Zuordnung verknüpft nur über ISIN und Währung, filtert `id_tenant_private` nicht und behält bei mehreren Treffern einen beliebigen. | `repository/GTNetInstrumentSecurityJpaRepository.findLocalSecurityMappings` |
| L8 | Der Historien-Erstimport wählt den Peer mit der grössten **selbst gemeldeten** Abdeckung (`selectBestPeer`); `fetchFromPeer` schreibt **alle Zeilen aller Antwort-DTOs** dem angefragten Wertpapier zu, ohne Instrumentschlüssel oder Datumsbereich der Antwort zu prüfen. | `service/GTNetHistoricalImportService`, `task/exec/GTNetSecurityImportTask` |
| L9 | Erster Lieferant gewinnt; keine Vergleichslogik. | `GTNetLastpriceService.queryRemoteServersWithTracking` (Abbruch bei `allFilled()`), `service/HistoryquoteExchangeSet` |
| L10 | Der Score `coverageCount × successRate × ohlFactor` beruht auf Selbstauskünften (Abdeckung, OHL-Anteil) und belohnt mit `entitiesUpdated / entitiesSent` jeden, der neuere Zeitstempel liefert. | `service/SupplierScoreCalculator` |
| L11 | Für Instrumente mit Empfangs-Flag kommt GTNet vor dem eigenen Konnektor; der Konnektor ist Rückfall. | `GTNetLastpriceService` (Schritt „Connector fallback“), `priceupdate/historyquote/HistoryquoteThruGTNet` |
| L12 | Daten werden über mehrere Hops weitergereicht; keine Zeile hält ihre Herkunft fest. GTNet-Zeilen in `historyquote` behalten `create_type = 0` (`CONNECTOR_CREATED`), und `LastpriceOrigin.ofHistoryquoteCreateType` zeigt sie als `HISTORY_CLOSE`, also als echt gehandelten Schlusskurs. | `PushOpen*QueryStrategy`, `GTNetLastpricePushService`, `grafioschtrader-common/.../types/LastpriceOrigin.java` |

### 2.2 Weitere Landepunkte

- **Historien sind konnektor-adjustiert, nicht roh.** Ändern sich die Splits eines Wertpapiers, lädt
  `task/exec/UpdateSplitForSecurityTask` die gesamte Historie neu. Zwei Instanzen können für dieselben Tage vor einem
  Split verschieden skalierte Werte haben, je nachdem, ob sie den Split schon verarbeitet haben.
- **Tagesgesamtwerte** (`hold_daily_total`) erkennen geänderte und neue Kurse selbst: `HoldDailyTotalService.updateTenant`
  sucht über `HoldDailyTotal.getEarliestChangedQuoteDateByTenant` (jüngeres `historyquote.create_modify_time`, fünf
  Zweige: Wertpapier, Positions- und Barbestands-Währungspaare). **Gelöschte** Zeilen hinterlassen keinen Zeitstempel;
  dafür gibt es `HoldDailyTotalMarker.markDirty(idTenant, fromDate)`.
- **Abgeleitete Wertpapiere** rechnen ihre Historie aus Basiswerten (`security_derived_link`,
  `priceupdate/ThruCalculationHelper`, `create_type = CALCULATED`); Lückenfüller erzeugen `FILLED_NON_TRADE_DAY`,
  `FILLED_CLOSED_LINEAR_TRADING_DAY`, `FILL_GAP_BY_CONNECTOR`.
- **Alarme** lesen nicht nur den Last-Preis: `service/AlgoAlarmEvaluationService` lädt Historie für Periodenänderung,
  gleitende Durchschnitte und RSI; Ausdrücke verwenden zusätzlich PrevClose, Open, High, Low und Volume. Fehlt eine
  Eingabe, meldet `unavailable(scope, reason)` den Alarm als nicht auswertbar. Zugestellte Alarme stehen in
  `AlgoMessageAlert`.
- **Zustellung mit Wiederholung** für gespeicherte Nachrichten an bekannte Peers gibt es bereits:
  `GTNetMessageAttempt` (grafiosch-base).
- `GTNetConfig.requestViolationCount` ist ein `Byte`, gekappt bei 99, ohne Zeitbezug — geeignet für die bestehende
  Limitsperre, nicht für Zeitfenster.
- `GNetCoreMessageCode`: belegt 0–7, 10–13, 20–30, 50, 52–54; 31 darf nicht wiederverwendet werden (früher
  `GT_NET_ADMIN_MESSAGE_ALL_C`). Frei: 8, 9, 14–19, 32–49, 51.

---

## 3. Bedrohungsmodell und Schutzziele

### Angreifer

Ein Betreiber einer GTNet-Instanz oder jemand, der eine übernommen hat. Er kann beliebig viele Schlüsselpaare
erzeugen, im Rahmen des Protokolls beliebige Nachrichten senden, fremde Daten weiterleiten, zurückhalten oder kopieren
und mehrere Instanzen koordinieren. Er kann keine Ed25519-Signatur fälschen und keinen fremden privaten Schlüssel
lesen, solange er die betreffende Instanz nicht übernommen hat.

### Angriffe

| Code | Angriff |
|---|---|
| T1 | Ursprung liefert falsche Werte — grob, plausibel, oder als Treppe kleiner Schritte |
| T2 | Relay verändert Werte oder schreibt sie einem fremden Ursprung zu |
| T3 | Zukunfts-Zeitstempel sperrt einen Kurs (L2, L4) |
| T4 | Anfrager schreibt über die Anfrage in fremde Kurse (L3, L4) |
| T5 | Übernahme des Historien-Erstimports (L8) |
| T6 | Erster Schreiber eines Tages setzt den Schlusskurs (L6) |
| T7 | Unterschwellige Drift, gezielt an einem Instrument |
| T8 | Sybil-Ursprünge oder kooperierende freigegebene Ursprünge bestätigen sich gegenseitig |
| T9 | Equivocation: derselbe Ursprung signiert widersprüchliche Aussagen |
| T10 | Gestohlener privater Schlüssel |
| T11 | Wiederkehr unter neuer Identität |
| T12 | Ressourcenerschöpfung: viele Identitäten, Datensätze, Hinweise, provozierte Prüfabrufe |
| T13 | Signaturgültiger Datensatz mit abweichenden Parallelfeldern im DTO |

### Schutzziele

- **S1** — Im Profil *streng* erreicht kein unbestätigter GTNet-Wert `historyquote` oder `securitycurrency`.
- **S2** — Kein einzelner Ursprung, kein nicht freigegebener Ursprung und keine Gruppe nicht unabhängiger Ursprünge
  kann allein einen Wert bestätigen.
- **S3** — Manipulation durch einen Relay ist deterministisch erkennbar.
- **S4** — Jeder übernommene GTNet-Wert trägt Herkunft und Beleg und ist zurückrufbar.
- **S5** — Jede Auswertung (Alarm, Bewertung, Performance) kennt die Qualität ihrer Eingaben; Alarme lösen nie auf
  unzureichend bestätigten Eingaben aus.
- **S6** — Ressourcen für Prüfung und Speicherung sind begrenzt; ein erschöpftes Budget führt nie zur Annahme.

### Nicht-Ziele

- Ein gemeinsamer Fehler aller Quellen desselben Datenanbieters ist nicht erkennbar. A2 vermeidet aber, ihn zur
  Bestätigung hochzustufen, wo der gemeinsame Anbieter erkennbar ist (§10.3).
- Verfügbarkeit: Zurückhalten und Verlangsamen sind Qualitätsfragen; Score und Konnektor-Rückfall decken sie ab.

---

## 4. Schichtenarchitektur

```
 empfangener Datensatz
        │
        ▼
 H  Härtung (§5)             Zeitobergrenze, Instrument-/Bereichsbindung, Empfangs-Flags, keine Schreibwirkung
        │                    von Anfragen, Budgets
        ▼
 S  Herkunft (§6, §7)        Signatur über exakte Bytes, Werte nur aus dem Payload, Sender = gepinnter Peer
        │
        ▼
 U  Ursprung (§8, §15)       NEU / FREIGEGEBEN / VERDÄCHTIG / QUARANTÄNE / GESPERRT, Freigabe-Umfang, Sperrliste
        │
        ▼
 G  Plausibilität (§9)       Kalender, Vergleichbarkeit, OHLC, Sprung → ABWEISEN / ZURÜCKHALTEN / PLAUSIBEL
        │
        ▼
 B  Bestätigung (§10)        eigener Konnektor oder übereinstimmende unabhängige Gruppe → Beleg
        │
        ▼
 Ü  Übernahme (§10.6)        Profil streng / gelockert, Kandidatenfenster, Vorrang eigener Werte
        │
        ▼
    historyquote, securitycurrency  (Herkunft, Quellart, Grad, bestätigte Felder)
        │
        ▼
 P  Nachprüfung (§11, §12)   Stichprobe, Drift, Zustände
        │
        ▼
 R  Rückruf (§13)            Beleg neu bewerten, archivieren, ersetzen/entziehen, abhängige Reihen neu rechnen
```

Ein Relay entscheidet über **Weitergabe** nur mit den Schichten H, S und U; die wirtschaftliche Prüfung G, B und Ü
betrifft nur die **lokale Übernahme** (§7.5). Kein Relay wird so zum zweiten Gate des ganzen Netzes.

---

## 5. Phase 0: Härtung des bestehenden Austauschs

Unabhängig vom Netzwerkmodus und vom späteren Konzept. Keine Protokolländerung ausser H4 (Antwortbindung) und H9
(neue Spalten).

| # | Regel | Landepunkt |
|---|---|---|
| H1 | **Zeitobergrenze überall**: ein Kurszeitstempel `t` wird nur akzeptiert, wenn `t ≤ now_UTC + skew` — beim Aktualisieren **und** beim Neuanlegen. Verstoss: Datensatz verworfen, Eintrag in `gt_net_violation_log` (H10). | `GTNetLastpricePoolService`, `OpenLastpriceQueryStrategy`, `InstrumentExchangeSet.processResponse`, `PushOpenLastpriceQueryStrategy`, historische Speicherpfade |
| H2 | **Anfragen schreiben nicht direkt**: mitgeschickte Preise dienen nur dem Vergleich „wer hat Neueres“. In `security`/`currencypair` werden sie nie übernommen. Ein AC_PUSH_OPEN-Lieferant darf sie in seinen Pool aufnehmen, aber nur über denselben Importpfad wie einen Push (H1, H4, H5, ab P1 das Gate). | `OpenLastpriceQueryStrategy.updateSecurityFromDTO` / `updateCurrencypairFromDTO` entfallen; `PushOpenLastpriceQueryStrategy` ruft den Importpfad |
| H3 | **Empfangs-Flags auf benutzerwirksamen Schreibpfaden**: in ein lokales Instrument (`historyquote`, `securitycurrency`) wird nur geschrieben, wenn `gtNetHistoricalRecv` bzw. `gtNetLastpriceRecv` gesetzt ist. Pool-Einträge zum Weiterreichen brauchen kein lokales Flag. | `HistoryquotePushHandler`, `GTNetHistoryquoteService.storeHistoryquotesForInstrument`, `PushOpenHistoryquoteQueryStrategy` |
| H4 | **Antwortbindung**: Eine Antwort darf nur angefragte Instrumente und Datumsbereiche enthalten, ein Push nur Instrumente mit gemeldetem „want to receive“. `fetchFromPeer` übernimmt nur DTOs, deren ISIN und Währung dem angefragten Wertpapier entsprechen, und nur Daten im angefragten Bereich. Überzähliges: verworfen und protokolliert. | Exchange- und Push-Handler, `GTNetHistoricalImportService.fetchFromPeer` |
| H5 | **Eindeutige lokale Zuordnung**: geschrieben wird nur in ein gemeinsames Instrument (`id_tenant_private IS NULL`) und nur, wenn genau eines passt. Ab P3 muss zusätzlich der MIC der Börse übereinstimmen (§7.2). | `findLocalSecurityMappings` und Aufrufer |
| H6 | **Handelskalender**: historische Zeilen nur für Handelstage der Börse des Instruments (`trading_days_plus` über `stockexchange`), bis zum letzten abgeschlossenen Handelstag. Kryptopaare (`Currencypair.getIsCryptocurrency()`, `GlobalConstants.CRYPTO_CURRENCY_SUPPORTED`) jeden Tag, übrige Währungspaare Montag bis Freitag. Fehlt der Kalender einer Börse, wird nicht geschrieben (ab P1: zurückgehalten). | historische Speicherpfade, `HistoryquoteThruGTNet` |
| H7 | **Score ohne Selbstauskunft-Belohnung**: die Erfolgsquote zählt nur geschriebene (ab P1: übernommene) Datensätze; abgewiesene und zurückgehaltene senken sie, ein Ausfall der eigenen Referenz nicht. Abdeckung und OHL-Anteil werden auf die in den letzten 30 Tagen tatsächlich gelieferte Abdeckung bzw. den beobachteten OHL-Anteil gekappt; `selectBestPeer` ebenso. | `SupplierScoreCalculator`, `GTNetHistoricalImportService.selectBestPeer`, `gt_net_exchange_log` |
| H8 | **Erstimport mit Vollvergleich**: liefert ein zweiter Peer das Instrument, wird der gesamte überlappende Zeitraum verglichen (Konsens-Toleranz, §10.3). Abweichende Monate werden nicht importiert und dem Administrator gemeldet. Ohne zweite Quelle gilt in P0 das bisherige Verhalten mit Herkunftsmarkierung (H9); ab P1 die Übernahmeregel. | `GTNetHistoricalImportService`, `GTNetSecurityImportTask` |
| H9 | **Herkunft**: `received_from_id_gt_net` (FK `gt_net`, `ON DELETE SET NULL`) auf `historyquote`, `gt_net_historyquote`, `gt_net_lastprice`; `s_received_from_id_gt_net` auf `securitycurrency`. Neuer `HistoryquoteCreateType.GTNET_RECEIVED` (7) mit Frontend-Gegenstück in `frontend/src/app/entities/historyquote.base.ts`; neuer `LastpriceOrigin`-Wert `GTNET` (Gegenstück `frontend/src/app/entities/types/lastprice.origin.ts`), damit die Watchlist GTNet-Kurse nicht als echten Schlusskurs ausweist. | Migration; `Historyquote`, `GTNetHistoryquote`, `GTNetLastprice`, `Securitycurrency`, `LastpriceOrigin.ofHistoryquoteCreateType`, `WatchlistReport` |
| H10 | **Verletzungsprotokoll mit Zeitbezug**: `gt_net_violation_log` (Peer, Tag, Art, Anzahl). Der bestehende `requestViolationCount` bleibt für die Limitsperre. | grafiosch-base |
| H11 | **Vorrang des eigenen Werts**: ein GTNet-Last-Preis ersetzt `s_last` nicht, wenn der aktuelle Wert vom eigenen Konnektor stammt (`s_price_source = 0`) und jünger als `gt.gtnet.integrity.own.value.max.age.minutes` ist. Historische GTNet-Zeilen ersetzen nie eigene Zeilen. Die Abfragereihenfolge regelt `gt.gtnet.integrity.connector.first` (0 = GTNet zuerst wie heute, 1 = Konnektor zuerst). | `InstrumentExchangeSet.processResponse`, `GTNetLastpriceService`, `HistoryquoteThruGTNet` |
| H12 | **Budgets** für die bestehenden Pfade: Datensätze pro Peer und Tag, Antwortgrösse, neue Pool-Instrumente pro Peer und Tag (§16.3). Überschreitung: Abweisung, nie Annahme. | Handler, `GTNetProtocolLimits` |

Daten, die vor H9 über GTNet importiert wurden, lassen sich nachträglich keinem Peer zuordnen; sie sind von
Konnektorzeilen nicht unterscheidbar (§17.3, §23).

---

## 6. Identität, Handshake und Schlüssel

### 6.1 Selbstzertifizierende Identität

Beim Wechsel in den signierten Modus erzeugt die Instanz ein **Ed25519-Schlüsselpaar**. Der private Schlüssel liegt
Jasypt-verschlüsselt in `gt_net_instance_identity.private_key`, ist `@JsonIgnore` und erscheint in keiner
`ExportDefinition`; ein Guard-Test prüft beides. Wer Datenbank und `JASYPT_ENCRYPTOR_PASSWORD` besitzt, besitzt die
Identität — derselbe Schutzstand wie für die übrigen Geheimnisse der Anwendung.

```
gti = "gti1:" + base32_ohne_padding( SHA-256( Ed25519-Public-Key, 32 Rohbytes ) )
```

- Gespeichert in `VARCHAR(64)`, verglichen immer vollständig; die Oberfläche zeigt zusätzlich einen Fingerabdruck
  (12 Zeichen nach `gti1:`).
- Überall, wo Kennung und Schlüssel zusammen auftreten: `gti == deriveGti(publicKey)`.
- **Schlüsselsicherung**: der Administrator lädt den privaten Schlüssel als PKCS#8 `EncryptedPrivateKeyInfo` (PBES2,
  mit selbst gewählter Passphrase) herunter; erst danach wird `key_backed_up` gesetzt.
- **Wiederherstellungsschlüssel** (optional, empfohlen): ein zweites Ed25519-Paar, dessen öffentlicher Teil beim
  Pinning mitgeteilt wird und dessen privater Teil nur offline existiert (§6.5).

### 6.2 Handshake

`FirstHandshakeMsg` (grafiosch-base, heute nur `tokenThis`) erhält `integrityMode`, `instanceGti`, `publicKey`,
`keyVersion`, `recoveryPublicKey` (optional), `protocolVersion` und `handshakeSignature`.

Der Initiator signiert

```
"gtnet-a2/handshake/v1" || 0x0A || protocolVersion || 0x0A || "INITIATOR" || 0x0A || instanceGti
  || 0x0A || zielDomainKanonisch || 0x0A || tokenThis || 0x0A || envelopeTimestamp
```

`tokenThis` ist frisch zufällig. Der Responder prüft Signatur, Skew-Fenster und Einmaligkeit des Tokens gegen
`gt_net_seen_token` (persistent, Aufbewahrung `2 × skew`, übersteht Neustarts und parallele Anfragen). Die
ACCEPT-Antwort signiert analog mit Rolle `RESPONDER`, beiden Kennungen und beiden Tokens. Der Nachweis ist gegenseitig
und an Rollen, Ziel und Austausch gebunden.

Eine **verzögerte manuelle Freigabe** (`HandlerResult.AwaitingManualResponse`) nimmt die ursprüngliche Signatur nicht
nachträglich an: die Freigabe hinterlegt nur die Zulassung, der Initiator muss danach einen frischen Handshake
durchführen.

### 6.3 Pinning und Kontinuität

- Nach Freigabe werden `partner_gti`, `partner_public_key`, `partner_key_version`, `partner_recovery_public_key` und
  `key_pinned_at` in `gt_net_config` des betreffenden `gt_net`-Eintrags gespeichert.
- Kontinuität wird **am bekannten Peer** geprüft, nicht an der behaupteten Kennung: für einen bestehenden
  `gt_net`-Eintrag muss die vorgelegte Kennung der gepinnten entsprechen und der Schlüssel dem gepinnten Schlüssel.
  Eine fremde, ebenfalls gültige Kennung auf derselben Verbindung ist `KEY_MISMATCH`.
- Bei jedem Handshake und Token-Refresh wird geprüft; Abweichung ⇒ Code 8, Administratorwarnung.

### 6.4 Netztrennung und Moduswechsel

Signierter und unsignierter Modus verbinden sich nicht. Ablehnung mit Code 8
`GT_NET_FIRST_HANDSHAKE_REJECT_NETWORK_MODE_S` und Grundcode (`MODE_MISMATCH`, `IDENTITY_INVALID`,
`SIGNATURE_INVALID`, `KEY_MISMATCH`, `LOCALLY_BLOCKED`, `AWAITING_APPROVAL`, `REPLAY`).

`gt_net_config.integrity_session` hält fest, in welchem Modus die aktuellen Tokens ausgestellt wurden. Ein Wechsel des
Modus setzt bei allen Peers die Tokens ausser Kraft; ein Peer muss im neuen Modus einen neuen Handshake durchführen.
Mit alten Tokens nimmt die Instanz keine Anfrage an — die Netztrennung lässt sich so nicht umgehen. Bestehende
Datenbestände werden durch einen Moduswechsel weder nachsigniert noch aufgewertet.

### 6.5 Schlüsselerklärungen (Code 40)

`GT_NET_KEY_STATEMENT_ALL_C` trägt eine signierte Erklärung mit `statementUuid` und monotoner `keyVersion`:

| Art | Signiert mit | Wirkung beim Empfänger |
|---|---|---|
| **ROTATE** | altem **und** neuem Schlüssel | neuer Schlüssel gepinnt; alter Ursprungseintrag `superseded_by`; Freigabe-Umfang, Gruppe, Zustand und Einschränkungen gehen über, **nie zusätzliche Rechte** |
| **REVOKE** | dem zu widerrufenden Schlüssel | Ursprung GESPERRT (`SELF_REVOKED`), Rückruf |
| **RECOVER** | dem Wiederherstellungsschlüssel | alter Schlüssel GESPERRT, neuer gepinnt, Herkunft bleibt verknüpft |

- ROTATE belegt Kontinuität, **nicht** Wiederherstellung nach Diebstahl: wer den alten Schlüssel gestohlen hat, kann
  selbst rotieren. Nach Diebstahl hilft nur RECOVER oder manuelle Neuzulassung durch jeden Peer; ohne
  Wiederherstellungsschlüssel zusätzlich die Sperrliste (§15).
- Konflikte (zwei ROTATE derselben `keyVersion` mit verschiedenen neuen Schlüsseln) ⇒ Ursprung QUARANTÄNE und
  Administratorentscheid.
- Erklärungen werden über `GTNetMessageAttempt` zugestellt, beim nächsten Handshake erneut gesendet und gelten erst nach
  ACK (Code 42) als zugestellt.
- **Wiederherstellung aus einer Sicherung**: bevor eine aus einer Sicherung zurückgeholte Instanz wieder signiert,
  rotiert sie ihren Schlüssel. Damit kollidieren keine Sequenzen oder Revisionen, die nach der Sicherung schon
  signiert wurden (§7.7).

---

## 7. Signierte Kursdatensätze

### 7.1 Kanonische Form

Eigenes, vollständig festgelegtes Format (kein JCS):

- UTF-8 ohne BOM; ein JSON-Objekt; Felder **in der Schema-Reihenfolge**, ohne Leerraum; keine doppelten, keine
  unbekannten, keine fehlenden Felder (fehlende Werte als `null`).
- Strings NFC-normalisiert; maskiert werden nur `"`, `\` und Steuerzeichen U+0000–U+001F als `\u00xx` (Kleinbuchstaben).
- Ganzzahlen (`schemaVersion`, `keyVersion`, `sequence`, `revision`) als JSON-Zahlen ohne Vorzeichen.
- **Dezimalwerte als Strings**, aus `double` erzeugt: `v` muss endlich und ungleich `-0` sein; `v' = DataHelper.round(v, 8)`;
  `s = Double.toString(v')` (kürzeste eindeutige Darstellung); ein Exponent wird per Zeichenkettenoperation
  ausgeschrieben; abschliessende Nullen und ein abschliessender Punkt werden entfernt. Beispiel: `124.0` → `"124"`,
  `1.0E-5` → `"0.00001"`. Empfänger lesen mit `Double.parseDouble`. Werte unter `1e-8` sind nicht darstellbar und werden
  abgewiesen. `BigDecimal` wird nicht benötigt: Empfänger prüfen die gespeicherten Bytes und erzeugen sie nie neu.
- Zeitpunkte ISO-8601 UTC mit `Z`, Sekunden; Daten `yyyy-MM-dd`; Monate `yyyy-MM`.
- Signiert wird `domainTag || 0x0A || kanonischeBytes`. Die exakten Bytes werden gespeichert und unverändert
  weitergegeben.

Domain-Tags: `gtnet-a2/handshake/v1`, `gtnet-a2/handshake-accept/v1`, `gtnet-a2/lastprice/v1`,
`gtnet-a2/hist-month/v1`, `gtnet-a2/key-statement/v1`, `gtnet-a2/notice/v1`, `gtnet-a2/blocklist/v1`.

**Testvektoren** (positiv: Eingabe → Bytes, Hash, Signatur eines festen Testschlüssels; negativ: Feldreihenfolge,
doppelter Schlüssel, unbekanntes Feld, Exponent, `-0`, `NaN`, Ordinal-Enum, falscher Domain-Tag, Escape-Varianten,
nicht-NFC) sind Release-Kriterium.

### 7.2 Last-Preis-Datensatz

```json
{"schemaVersion":1,"recordKind":"LASTPRICE","originGti":"gti1:…","keyVersion":1,"sequence":918273,
 "instrumentKey":"CH0012005267:CHF","venueMic":"XSWX","priceTime":"2026-10-09T15:30:12Z",
 "open":"123.1","high":"124","low":"122.85","last":"123.45","volume":"120400",
 "dataSourceClass":"gt.datafeed.six","createdAt":"2026-10-09T15:30:15Z"}
```

- `sequence` steigt pro Ursprung und Schlüsselversion streng monoton (gespeichert in `gt_net_instance_identity`).
  Eine höhere Sequenz mit gleichem `priceTime` ist eine **Korrektur** (z. B. eines Fehl-Ticks), keine Equivocation.
- `instrumentKey` ist `ISIN:CURRENCY` bzw. `FROM:TO`; `venueMic` ist der MIC der Börse des Ursprungs-Wertpapiers
  (`stockexchange.mic`), `null` bei Währungspaaren.
- `dataSourceClass` ist die Konnektor-ID (`id_connector_intra`). Sie ist eine Selbstauskunft und wird nur verwendet,
  um Stimmen **zusammenzulegen** (§10.3), nie um Toleranzen zu lockern.
- Gültigkeit: `priceTime ≤ createdAt ≤ now + skew`; Frische beurteilt der Empfänger aus seinem Handelskalender (§9).
- Ein Ursprung signiert einen Last-Preis **einmal**, speichert ihn und verwendet denselben Datensatz bei jeder
  Auslieferung wieder.

### 7.3 Historischer Monatsblock

```json
{"schemaVersion":1,"recordKind":"HISTORICAL_MONTH","originGti":"gti1:…","keyVersion":1,
 "instrumentKey":"CH0012005267:CHF","venueMic":"XSWX","month":"2026-09","revision":3,
 "adjustedThroughSplitDate":"2024-05-17","dataSourceClass":"gt.datafeed.six",
 "rows":[{"date":"2026-09-01","open":"…","high":"…","low":"…","close":"…","volume":"…"}, …],
 "createdAt":"2026-10-01T05:40:00Z"}
```

- `rows` mit eindeutigen, aufsteigenden Daten im genannten Monat, höchstens 31.
- `adjustedThroughSplitDate` ist das Datum des jüngsten Splits aus `securitysplit`, den die Reihe des Ursprungs
  widerspiegelt (`null`, wenn keiner). Empfänger vergleichen nur, wenn ihr eigener jüngster Split dasselbe Datum hat;
  sonst ist der Block für Tage vor dem jüngeren Split **nicht vergleichbar** und wird nicht übernommen.
- Signiert werden nur Zeilen, die der Ursprung selbst von einem Konnektor hat (`create_type = CONNECTOR_CREATED`,
  `price_source = 0`); nie über GTNet erhaltene, gefüllte, berechnete oder manuelle Zeilen.
- Der laufende Monat wächst über neue Revisionen. Eine Revision ist unveränderlich und wird beim Ursprung gespeichert
  und wiederverwendet. Höchstens `g.gnet.integrity.max.revisions.per.month` (40) Revisionen pro Monat; mehr ist eine
  Protokollverletzung. Eine niedrigere als die bekannte Revision wird ignoriert.
- Fehlt in einer höheren Revision eine Zeile, entsteht ein **Entfernungskandidat**; lokal wird nie automatisch gelöscht.
- Der ganze Block wird als Beweis mitgeliefert; übernommen werden nur die angefragten Tage (H4).

### 7.4 Ereignisschlüssel

Für Deduplizierung, Stichprobe und Zählung zählt das wirtschaftliche Ereignis, nicht der Datensatz:

```
eventKey = SHA-256( originGti | instrumentKey | venueMic | datum (historisch) bzw. priceTime-Minute (Last-Preis) )
```

Rotierte Schlüssel desselben Ursprungs (über ROTATE verknüpft) verwenden die Kennung des ersten Schlüssels der Kette.
Wiederholter Empfang, neue Revisionen und Weiterleitungen erzeugen kein neues Ereignis.

### 7.5 Weitergabe

Ein Relay gibt einen Datensatz byte-identisch weiter, wenn H1, H4, Grösse, Signatur und Kennung stimmen, der Ursprung
bei ihm weder QUARANTÄNE noch GESPERRT ist und das Budget des Empfängers es erlaubt. Ob das eigene Gate den Wert
zurückhält, spielt für die Weitergabe keine Rolle. Weitergegeben werden signierte Datensätze anderer Ursprünge und
eigene Konnektordaten; ein Wert, den die Instanz nur durch eine Administratorfreigabe übernommen hat, wird nie als
eigene Aussage signiert.

Verändert, fremd signiert oder ohne gültige Signatur geliefert ⇒ Protokollverletzung des **Senders** (H10); ab
`g.gnet.integrity.sender.violation.limit` Verletzungen in 30 Tagen wird der Austausch mit dem Peer gesperrt.

Hop-Quittungen sind nicht vorgesehen: der Sender ist durch gepinnten Schlüssel und Token auf der Verbindung
authentifiziert, und A2 verbreitet keine Anschuldigungen gegen Relays, für die ein übertragbarer Zustellbeweis nötig
wäre.

### 7.6 Bindung von Signatur und Inhalt

Im signierten Modus tragen die Kurs-DTOs (`InstrumentPriceDTO`, `HistoryquoteRecordDTO` bzw. die Monatsblock-Hülle)
nur noch `originCanonicalPayload`, `originSignature`, `originPublicKey`. Instrument, Zeitpunkt, alle Werte, Sequenz
und Revision werden **ausschliesslich aus dem verifizierten Payload** gelesen. Enthält ein DTO zusätzlich
Parallelfelder mit Werten, wird es abgewiesen. Auch die Bindung an Anfrage und Push-Berechtigung (H4) wird gegen den
verifizierten Payload geprüft. Ein Ursprung signiert und speichert seine eigenen Werte in einer Transaktion.

### 7.7 Equivocation

Equivocation sind zwei gültig signierte Datensätze desselben Ursprungs und derselben Schlüsselversion mit

- `LASTPRICE`: gleicher `sequence`, aber verschiedenen Bytes; oder
- `HISTORICAL_MONTH`: gleichem `instrumentKey`, `month` und `revision`, aber verschiedenem `rows`-Inhalt.

Verschiedene Sequenzen und verschiedene Revisionen sind normal. Weil Ursprünge signierte Revisionen und Sequenzen
speichern und nach einer Wiederherstellung rotieren (§6.5), entsteht Equivocation bei ehrlichem Betrieb nicht. Sie ist
selbstbeweisend und führt zu GESPERRT (§12).

---

## 8. Ursprungs-Freigabe

Jede gültige Kennung, die eine Instanz sieht, erhält einen Eintrag in `gt_net_origin` — innerhalb des Budgets
`g.gnet.origin.new.per.peer.day` pro übermittelndem Peer; darüber werden Datensätze unbekannter Ursprünge mit
`RATE_LIMITED` abgewiesen. Unbekannte Ursprünge werden zunächst nur minimal gespeichert (Kennung, Schlüssel, erster
Peer, Zeit).

| Zustand | Bedeutung | Übernahme | Konsens-Stimme | Weitergabe |
|---|---|---|---|---|
| **NEU** | gesehen, nicht freigegeben | nur wenn andere bestätigen | nein | ja |
| **FREIGEGEBEN** | vom Administrator freigegeben | nach §10.6 | ja | ja |
| **VERDÄCHTIG** | Auffälligkeit (§12) | nur wenn andere bestätigen | nein | ja |
| **QUARANTÄNE** | befristet, automatisch (§12) | nein, Rückruf | nein | nein |
| **GESPERRT** | Beweis oder Administrator | nein, Rückruf | nein | nein |

- **Freigabe ist manuell.** Die Oberfläche zeigt pro Ursprung: zuerst gesehen, über welche Peers, Anzahl Werte, davon
  bestätigt, Abweichungen, Datenquellenklassen. Sind Reifekriterien erfüllt (`g.gnet.origin.maturation.days` 30 Tage,
  `gt.gtnet.origin.maturation.confirmed` 200 bestätigte Ereignisse, keine Anomalie), erscheint eine
  **Freigabeempfehlung** — keine automatische Freigabe. Vergangene Übereinstimmung beweist keine künftige Ehrlichkeit.
- Gibt der Administrator einen direkten Peer frei, kann er dessen Kennung zugleich als Ursprung freigeben oder „nur
  Relay“ wählen.
- **Freigabe-Umfang**: optional auf Anlageklassen (`AssetclassType`) oder eine Instrumentliste begrenzt
  (`gt_net_origin_scope`). Werte ausserhalb des Umfangs behandelt die Instanz wie die eines NEU-Ursprungs.
- **Unabhängigkeitseinheit**: der Administrator ordnet Ursprünge einer `operator_group` zu, wenn er weiss, dass sie
  demselben Betreiber gehören. Unbekannte Unabhängigkeit gilt nicht als nachgewiesen — sie ist eine bewusste Annahme
  des Administrators bei der Freigabe. Über ROTATE verknüpfte Schlüssel bleiben in derselben Einheit.

---

## 9. Import-Gate

Das Gate prüft **Plausibilität**. Es bestätigt nichts.

### 9.1 Abweisende Prüfungen (Abbruch beim ersten Verstoss)

| # | Prüfung |
|---|---|
| G1 | Struktur: Schema, Grössenlimit, Pflichtfelder; Kurswerte endlich und > 0, Volumen ≥ 0, eindeutige Daten |
| G2 | Signatur, Kennung, Bindung (§7.6); Ursprung nicht QUARANTÄNE, GESPERRT oder auf einer aktiven Sperrliste |
| G3 | Zeit: H1; `priceTime` nicht nach dem Sitzungsende der Börse + `gt.gtnet.integrity.session.tolerance.minutes`; historisch H6 |
| G4 | Umfang und Zuordnung: H3, H4, H5, MIC; Budget (§16.3) |

Ein älterer Last-Preis eines illiquiden Titels ist kein Verstoss; er ist schlicht nicht neuer und wird nicht verwendet.

### 9.2 Zurückhaltende Prüfungen (alle werden ausgewertet)

| Bit | # | Prüfung |
|---|---|---|
| 1 | G5 | OHLC-Konsistenz: `low ≤ min(open, close) ≤ max(open, close) ≤ high`, sofern vorhanden |
| 2 | G6 | Sprungtest (§9.3) |
| 4 | G7 | Ursprung NEU, VERDÄCHTIG, ausserhalb des Freigabe-Umfangs oder für das Instrument wegen Drift gesperrt (§11.4) |
| 8 | G8 | Korrekturkandidat: abweichender Wert für ein bereits vorhandenes Datum bzw. höhere Last-Preis-Sequenz mit gleichem `priceTime` |
| 16 | G9 | Nicht vergleichbar: Split-Stand (§7.3) oder Kalender fehlt |

Die Gründe werden als Bitmaske in `gt_net_price_staging.hold_reasons` gespeichert. Vor jeder Übernahme aus dem Staging
läuft das **gesamte Gate erneut** mit der dann gültigen Policy.

### 9.3 Sprungtest (G6)

Referenz ist der letzte **vertrauenswürdige** Schlusskurs `v_ref` (eigener Konnektor oder BESTÄTIGT) vor dem Datum;
`Δt` zählt alle Handelstage dazwischen. Eine Treppe aus vielen kleinen unbestätigten Schritten bleibt dadurch sichtbar,
weil der Abstand zur letzten vertrauenswürdigen Referenz wächst. Ein Split zwischen `v_ref` und dem Datum wird über die
`calcSplitFator*`-Methoden von `Securitysplit` eingerechnet.

```
r      = | ln( v / v_ref ) |
σ₆₀    = Standardabweichung der täglichen Log-Renditen der letzten 60 vertrauenswürdigen Handelstage
Grenze = max( k · σ₆₀ · √Δt , ln(1 + f_min) )
ZURÜCKHALTEN, wenn r > Grenze
```

`k = gt.gtnet.integrity.jump.sigma` (6), `f_min` aus `gt_net_threshold_config.jump_floor_percent` (Aktien 8 %,
Anleihen 3 %, Geldmarkt 1 %, Währungspaare 3 %, Krypto 25 %). Weniger als 60 vertrauenswürdige Tage: nur `f_min`;
gar keine Referenz: G6 entfällt, die Übernahmeregel (§10.6) greift.

Der Sprungtest ist eine Heuristik gegen grobe Fehler. Gegen plausible Fälschungen schützt die Bestätigung, nicht das
Gate.

### 9.4 Staging

Zurückgehaltene und im Profil *streng* auch alle plausiblen, unbestätigten Werte liegen in `gt_net_price_staging`.
Ein Eintrag wird aufgelöst durch Bestätigung (→ Übernahme), Widerlegung (→ verworfen, Anomalie-Ereignis),
Administratorentscheid (→ Übernahme mit `ADMIN_OVERRIDE`, Grund Pflicht) oder Ablauf nach
`gt.gtnet.integrity.staging.days`. Die Oberfläche zeigt zurückgehaltene Werte mit Gründen, die grössten Sprünge zuerst;
ein Administratorentscheid betrifft einen Wert oder einen Monat, nie eine ganze Historie in einem Schritt.

---

## 10. Bestätigung und Übernahme

### 10.1 Quellart und Grad

Jede Kurszeile trägt ihre **Quellart** (`price_source`) ausdrücklich — `NULL` bedeutet nicht „vertrauenswürdig“:

| Wert | Quellart |
|---|---|
| 0 | Eigener Konnektor |
| 1 | Lokal erfasst (Benutzer, Import, Lückenfüller, Berechnung) — lokal autorisiert, nicht unabhängig geprüft |
| 2 | GTNet |
| 3 | Herkunft unbekannt (Bestand vor der Migration, §17.3) |

GTNet-Zeilen tragen zusätzlich den **Grad**:

| Wert | Grad | Bedeutung |
|---|---|---|
| 1 | UNBESTÄTIGT | plausibel, nur im Profil *gelockert* übernommen |
| 2 | BESTÄTIGT_KONSENS | übereinstimmende unabhängige Gruppe (§10.3) |
| 3 | BESTÄTIGT_KONNEKTOR | eigener Konnektor stimmt überein (§10.4) |
| 4 | ADMIN_OVERRIDE | Administratorentscheid mit Grund |

und `confirmed_fields` (Bitmaske O=1, H=2, L=4, C=8, V=16; beim Last-Preis steht C für Last): bestätigt ist nur, was
verglichen wurde. Ein bestätigter Schlusskurs bestätigt kein High, Low oder Volumen.

### 10.2 Vergleichbarkeit

Verglichen werden zwei Werte nur bei gleichem `instrumentKey`, gleichem `venueMic`, gleicher Kursart (Schluss bzw.
Last), gleichem Split-Stand und — beim Last-Preis — `priceTime` innerhalb von
`gt.gtnet.integrity.lastprice.window.seconds` (60). Split oder Dividende (`securitysplit`, `dividend`) innerhalb ± 3
Handelstagen, fehlender Kalender oder geschlossene Börse ⇒ **UNGEKLÄRT**, nie Bestätigung, nie Anomalie.

### 10.3 Konsens: übereinstimmende Gruppe

Gegeben ein Kandidat `c` (Ursprung beliebig ausser QUARANTÄNE/GESPERRT) und die vergleichbaren Werte aller
**freigegebenen** Ursprünge im Freigabe-Umfang. Die Werte werden zu **Stimmen** zusammengefasst: eine Stimme pro
Unabhängigkeitseinheit (`operator_group`, sonst Ursprungskette); Ursprünge mit gleicher `dataSourceClass` ergeben
ebenfalls nur eine Stimme. Weicht innerhalb einer Einheit ein Wert ab, zählt die Einheit nicht.

```
V        = Menge der Stimmen ohne die Stimme von c (je ein Wert)
Gruppe G ⊆ V übereinstimmend  ⇔  (max(G) − min(G)) / min(G) ≤ τ_konsens

BESTÄTIGT_KONSENS(c)  ⇔  es gibt ein übereinstimmendes G mit |G| ≥ q
                          und (max(G ∪ {c}) − min(G ∪ {c})) / min(G ∪ {c}) ≤ τ_konsens
                          und jede Stimme in V ∖ G liegt innerhalb τ_anomalie vom Median von G
WIDERLEGT(c)          ⇔  V ist übereinstimmend, |V| ≥ q,
                          und | c − Median(V) | / Median(V) > τ_anomalie
sonst UNGEKLÄRT
```

- Die Stimme von `c` selbst zählt nie für seine eigene Bestätigung; `q` andere Stimmen sind nötig.
- Freigegebene Quellen 90 und 110 bestätigen einen NEU-Kandidaten 100 nicht: es gibt keine übereinstimmende Gruppe.
- Zwei kooperierende freigegebene Lügner und eine ehrliche dritte Stimme ergeben **UNGEKLÄRT**, weil die dritte
  ausserhalb `τ_anomalie` liegt. Niemand wird bestätigt und niemand allein durch Konsens beschuldigt.
- Ein aus dem Median gebildeter Wert wird nie gespeichert; übernommen wird immer der signierte Originalwert des
  Kandidaten.
- Gezählt werden nur eingetroffene Antworten. `q = 2` ist eine **Zweitbestätigung**, keine byzantinische
  Fehlertoleranz.

Toleranzen pro `AssetclassType` in `gt_net_threshold_config` (in %, Last-Preis / historisch):

| Anlageklasse | τ_konsens | τ_anomalie |
|---|---|---|
| EQUITIES, COMMODITIES, REAL_ESTATE, MULTI_ASSET | 1,0 / 0,5 | 3,0 / 1,5 |
| FIXED_INCOME, CONVERTIBLE_BOND | 0,8 / 0,4 | 2,5 / 1,0 |
| MONEY_MARKET | 0,5 / 0,2 | 1,5 / 0,5 |
| CREDIT_DERIVATIVE | 1,5 / 0,8 | 4,0 / 2,0 |
| CURRENCY_PAIR | 0,5 / 0,2 | 1,5 / 0,5 |
| Überschreibung CFD | 1,5 / 0,8 | 4,0 / 2,0 |
| Überschreibung Krypto-Paar | 3,0 / 2,0 | 7,0 / 3,0 |

Dazu eine Mindestabweichung pro Währung für `τ_anomalie`. Toleranzen hängen nie von Angaben des Ursprungs ab.

### 10.4 Bestätigung durch den eigenen Konnektor

Ein vergleichbarer Wert des eigenen Konnektors innerhalb `τ_konsens` ergibt BESTÄTIGT_KONNEKTOR. Das ist eine lokale
Vertrauensentscheidung und wird im Beleg als solche ausgewiesen.

### 10.5 Abfragen

- Konsensabfragen laufen über die bestehenden Codes 60/61 und 80/81. Die Anfrage trägt das optionale Feld
  `excludedOriginGtis` als Bitte; **entschieden wird lokal** nach Signaturprüfung: ausgeschlossene und doppelte
  Ursprünge werden verworfen, mehrere Relays desselben Ursprungs ergeben eine Stimme.
- Die Lieferantenauswahl sucht bewusst verschiedene Unabhängigkeitseinheiten; die Abdeckungsangabe eines Relays beweist
  nicht, welche Ursprünge es liefern kann.
- Historische Monatsblöcke werden **einmal** bestätigt; unveränderte Record-Hashes verwenden den vorhandenen Beleg. Beim
  Erstimport wird der gesamte überlappende Zeitraum geprüft, nicht eine Stichprobe.
- Budgets (§16.3) begrenzen ausgehende Abfragen; ein erschöpftes Budget lässt Werte im Staging.

### 10.6 Übernahmeregel

`gt.gtnet.integrity.single.source.mode`: `0` = **streng** (Standard), `1` = **gelockert**.

| Lage | streng | gelockert |
|---|---|---|
| BESTÄTIGT_KONSENS oder BESTÄTIGT_KONNEKTOR | übernehmen | übernehmen |
| PLAUSIBEL ohne Haltegrund, Ursprung FREIGEGEBEN im Umfang, keine zweite Quelle | Staging, Anzeige „unbestätigt“ | übernehmen als UNBESTÄTIGT |
| Haltegrund G6 (Sprung) oder G8 (Korrektur) | Staging bis Bestätigung oder Administrator | ebenso |
| Haltegrund G5, G7 oder G9 | Staging bis Bestätigung oder Administrator | ebenso |
| UNGEKLÄRT bei widersprüchlichen Quellen | Staging, Administratormeldung | ebenso |

Weitere Regeln:

- **Kandidatenfenster**: ein historisches Datum `D` wird frühestens zum Sitzungsende von `D` plus
  `gt.gtnet.integrity.candidate.window.minutes` (120) entschieden; bis dahin werden alle Kandidaten gesammelt und
  gemeinsam bewertet. Wer zuerst liefert, gewinnt nicht.
- **Vorrang eigener Werte** (H11) gilt immer.
- **Pools** (`gt_net_lastprice`, `gt_net_historyquote`) nehmen plausible Werte zum Weiterreichen auf; ein Pool-Wert
  erreicht `s_last` einer AC_PUSH_OPEN-Instanz nur über diese Übernahmeregel.
- Bei der Umstellung auf eine Version mit dieser Regel zeigt die Oberfläche dem Administrator die Instrumente, die im
  Profil *streng* keine GTNet-Kurse mehr übernehmen würden, und bietet den Schalter an. Die Verfügbarkeit ändert sich
  nicht, ohne dass er es sieht.

### 10.7 Belege

Jede Übernahme schreibt einen unveränderlichen Beleg in `gt_net_confirmation`: Zielzeile, Grad, `confirmed_fields`,
stützende signierte Datensätze mit Ursprung und Unabhängigkeitseinheit, Referenzwert und Konnektor (bei
BESTÄTIGT_KONNEKTOR), verwendete Toleranzen, Policy-Version, Zeitpunkt, bei `ADMIN_OVERRIDE` Benutzer und Grund.

Fällt eine Stütze weg (Ursprung QUARANTÄNE, GESPERRT, Sperrliste), wird der Beleg **neu bewertet** (§13.2). Herkunft wird
nie umgeschrieben; ein Ersatzwert erhält einen eigenen Beleg mit Verweis auf den ersetzten.

---

## 11. Stichprobenprüfung und Drift-Erkennung

Im Profil *streng* prüft die Stichprobe vor allem Konsens-bestätigte Werte (gegen kooperierende Quellen und
gemeinsame Anbieterfehler); im Profil *gelockert* zusätzlich die unbestätigten.

### 11.1 Auswahl

```
auswahl(event) = HMAC-SHA256(lokalesGeheimnis, eventKey) mod 10000 < rate × 100
```

Ausgewählt wird pro Ereignis (§7.4), höchstens einmal. Raten: FREIGEGEBEN `gt.gtnet.integrity.sampling.rate.released`
(5 %), NEU `gt.gtnet.integrity.sampling.rate.new` (25 %), VERDÄCHTIG 100 %. Bei 5 % wird ein einzelnes falsches
Ereignis mit 95 % Wahrscheinlichkeit nicht geprüft; die Wahrscheinlichkeit, unter `n` falschen Ereignissen mindestens
eines zu prüfen, ist `1 − 0,95ⁿ` (ab `n = 59` über 95 %). Die Stichprobe ist deshalb Erkennung, keine
Annahmegarantie.

### 11.2 Vergleich

Referenz ist der eigene Konnektor, unter den Vergleichbarkeitsregeln von §10.2. Ergebnisse: BESTÄTIGT (`≤ τ_konsens`),
UNAUFFÄLLIG, ANOMALIE (`> τ_anomalie` und über der Mindestabweichung), UNGEKLÄRT (nicht vergleichbar, Konnektorfehler,
Budget erschöpft). Ein Konnektorausfall ist nie eine Anomalie und senkt keinen Score.

### 11.3 Folge einer Anomalie

- Der betroffene Wert wird **sofort entzogen**: aus `historyquote` bzw. `s_last` zurück ins Staging (Rückruf eines
  Werts, §13).
- Ersetzt wird er durch den Konnektorwert nur, wenn die Vergleichbarkeit gesichert ist; sonst bleibt die Stelle offen.
- Das Ereignis zählt für den Ursprung (§12). Für QUARANTÄNE braucht es zusätzlich eine zweite unabhängige Referenz
  oder einen Administratorentscheid.

### 11.4 Drift (CUSUM)

Abweichungen `d_i = (v_origin − v_ref) / v_ref` (vorzeichenbehaftet, nur BESTÄTIGT/UNAUFFÄLLIG/ANOMALIE, je Ereignis
einmal).

**Grundversatz pro Ursprung und Anlageklasse**: `m = Median der letzten 200 d_i` des Ursprungs in der Anlageklasse — der
methodische Unterschied zwischen Anbietern (etwa ein konstant um 0,2 % anderer Schlusskurs-Stil).

**Instrument-Ebene** (gezielte Manipulation) pro (Ursprung, Instrument), ab mindestens 8 Ereignissen:

```
e_i  = d_i − m
S⁺_i = max(0, S⁺_{i−1} + e_i − κ_I)      S⁻_i = max(0, S⁻_{i−1} − e_i − κ_I)
INSTRUMENT_DRIFT, wenn S⁺ oder S⁻ > h_I
```

**Anlageklassen-Ebene** (systematische Verschiebung) pro (Ursprung, Anlageklasse) auf `d_i` mit `κ_A`, `h_A`: nur
**Warnung** und erhöhte Stichprobe, nie allein ein Zustandswechsel.

Startwerte: `κ_I = 0,25 · τ_konsens`, `h_I = 2 · τ_anomalie`, `κ_A = 0,5 · τ_konsens`, `h_A = 4 · τ_anomalie`. Sie
werden in P4 kalibriert (§21): auf Daten mit ehrlichen Anbieterdifferenzen und eingespielten Angriffen (Einzelwert,
Treppe, Dauerverschiebung an einem Instrument, gemeinsamer Anbieterfehler), gemessen als mittlere Laufzeit bis
Fehlalarm und bis Erkennung. Ein Wechsel der Referenz (anderer Konnektor) setzt die Summen zurück
(`reference_connector` in `gt_net_origin_drift`).

`INSTRUMENT_DRIFT` hält die Werte dieses Ursprungs **für dieses Instrument** zurück (G7) und meldet es; der übrige
Bestand des Ursprungs bleibt unberührt.

---

## 12. Ursprungszustände und Sanktionen

Drei Wirkungen werden getrennt geschaltet:

| Wirkung | Schalter | Zeitpunkt |
|---|---|---|
| Protokoll- und Strukturprüfung (G1–G4, H1–H12) | immer | ab P0 |
| Annahmepolicy (Haltegründe, Übernahmeregel, Wertentzug bei Anomalie) | immer | ab P1 |
| Ursprungssanktionen aus Statistik | `gt.gtnet.integrity.sanction.mode` (0 simuliert, 1 aktiv) | ab P4 |

Im simulierten Modus werden statistische Übergänge mit Quelle `SIMULATED` in `gt_net_origin_audit` protokolliert und
ändern keinen Zustand. Deterministische Übergänge (Equivocation, REVOKE, RECOVER, Sperrliste, Administrator) wirken
immer.

| Übergang | Auslöser |
|---|---|
| NEU → FREIGEGEBEN | nur Administrator |
| FREIGEGEBEN → VERDÄCHTIG | ein Anomalie-Ereignis, WIDERLEGT, oder INSTRUMENT_DRIFT in zwei Instrumenten; Dauer `g.gnet.origin.suspect.days`, verlängert durch neue Ereignisse |
| VERDÄCHTIG → FREIGEGEBEN | Dauer abgelaufen und mindestens 20 Ereignisse ohne Auffälligkeit |
| VERDÄCHTIG → QUARANTÄNE | `g.gnet.origin.quarantine.events` (3) verschiedene Anomalie-Ereignisse in 30 Tagen, davon mindestens eines durch eine zweite unabhängige Referenz belegt (WIDERLEGT oder zweiter Konnektor); Dauer `g.gnet.origin.quarantine.days` (14) |
| QUARANTÄNE → VERDÄCHTIG | Ablauf ohne neue Ereignisse |
| beliebig → GESPERRT | Equivocation; REVOKE/RECOVER; Sperrliste; Administrator. Drei Quarantänen in 180 Tagen erzeugen einen Sperrvorschlag an den Administrator, keine automatische Sperre. |
| GESPERRT → FREIGEGEBEN | nur Administrator; bei reiner Sperrlisten-Ursache durch Entfernung aus der Liste, sofern kein lokaler Grund besteht |

Jeder Übergang schreibt `gt_net_origin_audit` (alter/neuer Zustand, Quelle, Beweis-Referenzen, Parameterwerte,
`actor_id_user` bei manuellen Entscheiden). QUARANTÄNE und GESPERRT lösen den Rückruf aus (§13).

---

## 13. Rückruf und Reparatur

### 13.1 Auslösen

Auslöser: Ursprung QUARANTÄNE oder GESPERRT; Rückruf pro Peer über `received_from_id_gt_net` (auch für Daten aus P0);
Administrator; Anomalie eines Einzelwerts (§11.3).

- **Sofort und atomar**: Zustandswechsel, Rückrufauftrag in `gt_net_recall` (Zustand `PENDING`) und `TaskDataChange`
  werden in **einer** Transaktion gespeichert. Ab diesem Commit lehnen Import und Weitergabe den Ursprung ab.
- Der Auftrag läuft als Task (neuer `TaskTypeExtended`-Wert im Anwendungsband 30–79, Gegenstück in
  `frontend/src/app/shared/types/task.type.extended.ts`, NLS-Schlüssel) in Stapeln (`gt.gtnet.integrity.recall.batch`,
  500), speichert seinen Fortschritt und ist nach Abbruch wiederaufnehmbar und idempotent.
- Bis der Auftrag abgeschlossen ist, stehen die betroffenen Werte noch in den Tabellen. Die Oberfläche zeigt einen
  laufenden Rückruf; Alarme werten Werte eines Ursprungs in QUARANTÄNE/GESPERRT bereits ab dem Commit als nicht
  ausreichend bestätigt (§14).

### 13.2 Pro Zeile

1. Zeile wählen: `id_gt_net_origin` in der Ursprungskette bzw. `received_from_id_gt_net`; übersprungen wird eine Zeile,
   die nach Auftragsbeginn durch eine andere Quelle neu geschrieben wurde (`create_modify_time` nach Auftragsbeginn und
   Herkunft geändert).
2. Beleg neu bewerten ohne die weggefallene Stütze (§10.3/§10.4 mit den gespeicherten Datensätzen):
   - weiterhin bestätigt ⇒ Zeile bleibt, neuer Beleg mit Verweis auf den alten;
   - nicht mehr bestätigt ⇒ im Profil *gelockert* UNBESTÄTIGT, sofern der Ursprung des Werts selbst noch FREIGEGEBEN
     ist; sonst **entziehen**.
3. Entziehen: Original in `gt_net_recall_row` archivieren (Tabelle, Zeilen-ID, alle Werte, Herkunft, Beleg), dann
   ersetzen aus eigenem Konnektor oder einer anderen bestätigten Quelle (neuer Beleg), sonst löschen
   (`historyquote`, Pools).
4. Last-Preis-Felder als **ein Satz**: `s_last`, `s_open`, `s_high`, `s_low`, `s_volume`, `s_timestamp`,
   `s_prev_close` werden gemeinsam ersetzt — durch einen frischen Abruf des eigenen Konnektors, sonst durch den letzten
   vertrauenswürdigen Schlusskurs aus `historyquote` (Datum als Zeitstempel, übrige Felder `NULL`). Ohne beides bleibt
   der Satz leer und das Instrument zeigt „nicht verfügbar“.

### 13.3 Abhängige Daten

Nach dem Stapel eines Instruments `X` mit frühestem betroffenem Datum `D`:

- **Tagesgesamtwerte**: ersetzte und neue Zeilen erkennt `HoldDailyTotalService` über `create_modify_time` selbst. Für
  **gelöschte** Zeilen ruft der Auftrag `HoldDailyTotalMarker.markDirty(idTenant, D)` für jeden Mandanten, dessen
  Hold-Tabellen `X` ab `D` referenzieren — als Wertpapier, als Positions-Währungspaar oder als Barbestands-Währungspaar,
  in denselben fünf Zweigen wie `HoldDailyTotal.getEarliestChangedQuoteDateByTenant`.
- **Abgeleitete Wertpapiere**: für jedes über `security_derived_link` von `X` abhängige Wertpapier die berechnete
  Historie ab `D` neu erzeugen (`ThruCalculationHelper`).
- **Gefüllte Zeilen**: Zeilen von `X` mit `create_type` `FILLED_NON_TRADE_DAY`, `FILLED_CLOSED_LINEAR_TRADING_DAY` oder
  `FILL_GAP_BY_CONNECTOR` ab `D` neu füllen lassen.
- **Zugestellte Alarme**: `AlgoMessageAlert`-Einträge, deren Auswertung einen entzogenen Wert verwendet hat, erhalten
  einen sichtbaren Korrekturhinweis. Ein bereits zugestellter Alarm und eine darauf getroffene Entscheidung lassen sich
  nicht rückgängig machen.

`historyquote_period` (manuell erfasste Ersatzkurse) wird von GTNet nie beschrieben und ist nicht betroffen.

### 13.4 Protokoll und Aufbewahrung

`gt_net_recall` hält Auslöser, Zustand, Fortschritt, Anzahl behalten/herabgestuft/ersetzt/gelöscht und frühestes Datum.
Archivzeilen, Belege und Sperrbeweise bleiben erhalten, solange eine Kurszeile, ein Beleg oder ein Zustandsentscheid
auf sie verweist; danach `gt.gtnet.integrity.evidence.retention.months` (24). Ein Administrator kann einen Rückruf nach
einer Entsperrung **umkehren**: archivierte Werte laufen erneut durch Gate und Übernahmeregel.

---

## 14. Auswertungen, Alarme und Benutzeroberfläche

### 14.1 Qualität von Eingaben

Eine Eingabe ist **ausreichend bestätigt**, wenn ihre Quellart 0 oder 1 ist, oder GTNet mit Grad BESTÄTIGT_* bzw.
ADMIN_OVERRIDE und das verwendete Feld in `confirmed_fields` steht, und ihr Ursprung weder QUARANTÄNE noch GESPERRT
ist. Quellart 3 (unbekannt) gilt als ausreichend, wird aber als „Herkunft unbekannt“ ausgewiesen.

### 14.2 Alarme

`AlgoAlarmEvaluationService` prüft **alle** Eingaben einer Auswertung: Last-Preis, PrevClose, die Historie für
Periodenänderung, gleitende Durchschnitte und RSI sowie jedes Feld, das ein Ausdruck verwendet. Ist eine nicht
ausreichend bestätigt, endet die Auswertung über `unavailable(scope, reason)` mit „nicht ausreichend bestätigt“ statt
mit einem Signal. Das wird mit P1 ausgeliefert, zusammen mit dem ersten übernommenen GTNet-Grad.

### 14.3 Bewertung und Performance

Im Profil *streng* erreichen unbestätigte Werte die Rechentabellen nicht. Im Profil *gelockert* kennzeichnen
Bewertung, Performance und Tagesgesamtwerte ein Ergebnis als **vorläufig**, wenn eine verwendete Kurszeile UNBESTÄTIGT
ist (Wertpapier- oder Währungskurs).

### 14.4 Oberfläche

- Kurstabelle und Watchlist zeigen Quellart und Grad: eigen, lokal, GTNet bestätigt, GTNet unbestätigt, Admin,
  unbekannt; Tooltip mit Fingerabdruck des Ursprungs und Belegverweis. `LastpriceOrigin.GTNET` (H9) trägt das in den
  `WatchlistReport`.
- Unbestätigte Beobachtungen aus dem Staging erscheinen gesondert, nie als Kurs.
- Administration (Rolle Admin): Ursprünge mit Zustand, Gruppe, Umfang, Statistik, Freigabeempfehlung; Staging mit
  Gründen und Entscheid pro Wert oder Monat; Rückrufe mit Fortschritt und Umkehr; Sperrlisten-Quellen und Ausnahmen;
  eigene Kennung, Schlüsselsicherung und Wiederherstellungsschlüssel.

---

## 15. Signierte Sperrliste

Optional und letzte Phase. Ein Herausgeber (Standard: das Projekt) signiert mit einem **offline gehaltenen** Schlüssel
eine vollständige Liste und legt sie statisch ab. Die Liste ist eine Autorität: sie kann keine Kurse schreiben und
keinen Ursprung freigeben, aber über Sperren und Rückrufe grosse Datenbestände unbrauchbar machen. Deshalb abonniert
eine Instanz eine Liste nur durch bewussten Entscheid des Administrators; keine Liste ist vorkonfiguriert aktiv.

```json
{"schemaVersion":1,"listId":"gtnet-project","sequence":42,"issuedAt":"2026-10-10T08:00:00Z",
 "expiresAt":"2026-11-10T08:00:00Z","publisherKeyVersion":2,"nextPublisherKeys":[],
 "entries":[{"gti":"gti1:…","kind":"BLOCK","reasonCode":"PRICE_MANIPULATION","since":"2026-10-01T00:00:00Z"},
            {"gti":"gti1:…","kind":"KEY_REVOKED","reasonCode":"KEY_LOST","since":"2026-09-20T00:00:00Z"}],
 "signature":"…"}
```

- **Bootstrap**: der Administrator trägt URL und den vollständigen öffentlichen Schlüssel des Herausgebers ein; die
  Oberfläche zeigt den Fingerabdruck zum Abgleich über einen unabhängigen Kanal.
- **Herausgeberschlüssel-Wechsel**: `nextPublisherKeys` wird vom aktuellen Schlüssel mitsigniert; die folgende Liste
  darf mit einem dort angekündigten Schlüssel signiert sein. Ist der Herausgeberschlüssel kompromittiert, entfernt der
  Administrator die Quelle; alle ihre Einträge verlieren sofort ihre Wirkung.
- **Snapshots und Sequenzen**: jede Liste ist vollständig. Niedrigere Sequenz ⇒ abgewiesen; gleiche Sequenz, gleicher
  Inhalt ⇒ Duplikat; gleiche Sequenz, anderer Inhalt ⇒ **Konflikt**, alte Liste bleibt, Administratorwarnung. Die
  höchste Sequenz liegt in der Datenbank; nach einer Wiederherstellung aus einer Sicherung wird nur eine höhere
  angenommen, und die Oberfläche weist auf den möglichen Rückstand hin.
- **Wirkung pro Grund**: ein Eintrag ist ein Sperrgrund dieser Liste. Fällt er weg, entfällt nur dieser Grund; lokale
  Gründe und Gründe anderer Listen bleiben. Eine lokale Ausnahme gilt für genau einen Grund und wird protokolliert.
- **Ablauf**: `expiresAt` überschritten ⇒ Warnung; die Einträge bleiben wirksam, der Austausch läuft weiter.
- **Abruf**: täglicher Task, nur HTTPS, Weiterleitungen nur auf denselben Host, DNS-Auflösung mit Prüfung der Ziel-IP
  (private, Loopback- und Link-Local-Bereiche nur nach ausdrücklicher Freigabe für ein LAN-Ziel), Timeout 10 s,
  höchstens 1 MB und 10 000 Einträge.
- **Schlüsselverlust eines Betreibers**: er meldet sich beim Herausgeber, der die Identität des Betreibers ausserhalb
  des Netzes prüft und `KEY_REVOKED` aufnimmt.

---

## 16. Nachrichten-Codes, Protokollregeln und Budgets

### 16.1 Codes

| Code | Name | Richtung | Zweck |
|---|---|---|---|
| 8 | `GT_NET_FIRST_HANDSHAKE_REJECT_NETWORK_MODE_S` | Responder → Initiator | Ablehnung mit Grundcode (§6.4) |
| 40 | `GT_NET_KEY_STATEMENT_ALL_C` | Instanz → gepinnte Peers | ROTATE, REVOKE, RECOVER (§6.5) |
| 41 | `GT_NET_INTEGRITY_NOTICE_SEL_C` | Instanz → direkter Peer | Hinweis: Abweichung, Equivocation, Sanktion |
| 42 | `GT_NET_INTEGRITY_NOTICE_ACK_S` | Peer → Instanz | Ergebnis pro Eintrag bzw. Erklärung |

31 bleibt unbenutzt; 32–39 und 43–49 sind reserviert.

### 16.2 Hinweis (Code 41)

Enthält die betroffenen signierten Datensätze, die Art (`DEVIATION`, `EQUIVOCATION`, `ORIGIN_SANCTIONED`), bei
`DEVIATION` Referenzwert und -art, `noticeUuid`, signiert vom Absender (`gtnet-a2/notice/v1`).

- `EQUIVOCATION`: beide Datensätze werden geprüft; erfüllen sie §7.7, wird der Ursprung GESPERRT — selbstbeweisend.
- `DEVIATION`, `ORIGIN_SANCTIONED`: beratend. Stichprobe für den Ursprung auf 100 % für `g.gnet.origin.suspect.days`,
  Meldung an den Administrator, **kein** Zustandswechsel. Höchstens eine solche Erhöhung pro Ursprung und Zeitraum,
  egal wie viele Hinweise eintreffen.
- Nur von gepinnten Peers; nie weitergeleitet.

### 16.3 Budgets

| Budget | Standard | Schlüssel |
|---|---|---|
| neue Ursprünge pro Peer und Tag | 20 | `g.gnet.origin.new.per.peer.day` |
| Staging-Zeilen pro Peer | 50 000 | `gt.gtnet.integrity.staging.max.per.peer` |
| Staging-Zeilen gesamt | 1 000 000 | `gt.gtnet.integrity.staging.max.total` |
| Signaturprüfungen pro Peer und Tag | 200 000 | `g.gnet.integrity.verify.max.per.peer.day` |
| ausgehende Konsensabfragen pro Tag | 2 000 | `gt.gtnet.integrity.consensus.max.per.day` |
| Konnektorabrufe für Stichproben pro Tag | 2 000 | `gt.gtnet.integrity.verification.max.per.day` |
| Hinweise pro Peer und Tag | 50 | `g.gnet.integrity.notice.max.per.day` |

Überschreitung ⇒ `RATE_LIMITED`, Datensatz nicht angenommen bzw. Prüfung nicht durchgeführt (UNGEKLÄRT). Negative
Caches verhindern, dass dieselbe ungültige Signatur oder derselbe unbekannte Ursprung wiederholt Arbeit erzeugt.

### 16.4 Protokollregeln

- `schemaVersion` überall; unbekannte Versionen signierter Objekte abgewiesen; gespeicherte Objekte bleiben mit ihrer
  Version prüfbar.
- Grössen vor jeder Persistierung: Last-Preis ≤ 1 KB, Monatsblock ≤ 8 KB, Hinweis ≤ 64 KB, Erklärung ≤ 4 KB, dazu
  `GTNetProtocolLimits`.
- Idempotenz: `recordHash`, `noticeUuid`, `statementUuid`.
- Signatur-, Kennungs- und Kanonisierungsfehler sind permanent und werden nie wiederholt.
- Stabile Fehlercodes: `SCHEMA_VERSION_UNSUPPORTED`, `SIGNATURE_INVALID`, `IDENTITY_MISMATCH`, `DOMAIN_TAG_MISMATCH`,
  `PAYLOAD_BINDING_MISMATCH`, `TIMESTAMP_IN_FUTURE`, `NOT_REQUESTED`, `NOT_COMPARABLE`, `ORIGIN_BLOCKED`, `NOT_PINNED`,
  `REPLAY`, `RATE_LIMITED`.
- Bootstrap-Ausnahmen bleiben `GT_NET_PING` (0) und `GT_NET_FIRST_HANDSHAKE_SEL_RR_S` (1).

---

## 17. Datenmodell

Migrationen idempotent nach Root-`CLAUDE.md`, stabile Namen, `gt_ddl.sql` nie bearbeitet. Enum-gestützte
`TINYINT`-Spalten mit Enum-Accessoren; temporale Felder ans Frontend mit `@JsonFormat` und `BaseConstants`.

**Platzierung**: In `grafiosch-base` liegt nur, was Bibliothekscode selbst benutzt und was frei von Kurssemantik ist:
Identität, Pinning, generische signierte Hülle, Ursprung mit Zustand, Sperrliste, Verletzungsprotokoll,
Replay-Schutz, Ursprungs-Audit. Statistik, Umfang, Staging, Belege, Prüfergebnisse, Drift, Schwellen und Rückruf
liegen in `grafioschtrader-common`.

**Export und Limits**, pro Tabelle im Klassen-Javadoc begründet: alle neuen Tabellen sind instanzbezogene Systemdaten
und erscheinen in keiner `ExportDefinition`. Benutzerbezug haben nur `gt_net_origin_audit.actor_id_user` und
`gt_net_confirmation.actor_id_user`; beide mit `ON DELETE SET NULL`, damit eine Kontolöschung den Prüfpfad erhält.
Schreibbar sind die Tabellen nur durch das System oder Administratoren (`AdminEntity`); Netzwerkbudgets (§16.3)
begrenzen das Wachstum, darum kein `entity_limit`-Schlüssel.

### 17.1 Bibliothek (grafiosch-base)

```sql
CREATE TABLE IF NOT EXISTS gt_net_instance_identity (
  id_gt_net_instance_identity INT PRIMARY KEY CHECK (id_gt_net_instance_identity = 1),
  integrity_mode TINYINT(1) NOT NULL DEFAULT 0,
  instance_gti VARCHAR(64),
  key_version INT,
  public_key VARCHAR(64),                 -- base64, 44 Zeichen
  private_key VARCHAR(512),               -- Jasypt, @JsonIgnore
  recovery_public_key VARCHAR(64),
  sampling_secret VARCHAR(512),           -- Jasypt, @JsonIgnore
  last_sequence BIGINT NOT NULL DEFAULT 0,
  key_backed_up TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME,
  key_rotated_at DATETIME
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gt_net_origin (
  id_gt_net_origin INT AUTO_INCREMENT PRIMARY KEY,
  origin_gti VARCHAR(64) NOT NULL,
  public_key VARCHAR(64) NOT NULL,
  key_version INT NOT NULL,
  recovery_public_key VARCHAR(64),
  origin_state TINYINT NOT NULL DEFAULT 0,        -- NEU/FREIGEGEBEN/VERDÄCHTIG/QUARANTÄNE/GESPERRT
  state_until DATETIME,
  relay_only TINYINT(1) NOT NULL DEFAULT 0,
  operator_group VARCHAR(40),
  chain_root_id_gt_net_origin INT,                -- erster Schlüssel der ROTATE-Kette
  superseded_by_id_gt_net_origin INT,
  first_seen_at DATETIME NOT NULL,
  first_seen_via_id_gt_net INT,
  CONSTRAINT UK_GTNetOrigin_Gti UNIQUE (origin_gti),
  CONSTRAINT FK_GTNetOrigin_Superseded FOREIGN KEY (superseded_by_id_gt_net_origin)
    REFERENCES gt_net_origin (id_gt_net_origin) ON DELETE SET NULL,
  CONSTRAINT FK_GTNetOrigin_FirstSeenVia FOREIGN KEY (first_seen_via_id_gt_net)
    REFERENCES gt_net (id_gt_net) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gt_net_signed_record (
  id_signed_record BIGINT AUTO_INCREMENT PRIMARY KEY,
  record_hash CHAR(64) NOT NULL,
  id_gt_net_origin INT NOT NULL,
  record_kind VARCHAR(24) NOT NULL,               -- von der Anwendung definiert
  subject_key VARCHAR(80) NOT NULL,               -- von der Anwendung definiert (z. B. instrumentKey|venueMic)
  period_key VARCHAR(24) NOT NULL,                -- von der Anwendung definiert (Monat, Sequenz)
  revision BIGINT NOT NULL DEFAULT 0,             -- Revision bzw. Sequenz
  schema_version INT NOT NULL,
  canonical_payload MEDIUMTEXT NOT NULL,
  signature VARCHAR(96) NOT NULL,                 -- base64, 88 Zeichen
  received_at DATETIME NOT NULL,
  CONSTRAINT UK_SignedRecord_Hash UNIQUE (record_hash),
  CONSTRAINT FK_SignedRecord_Origin FOREIGN KEY (id_gt_net_origin) REFERENCES gt_net_origin (id_gt_net_origin)
) ENGINE=InnoDB;
DROP INDEX IF EXISTS idx_signed_record_conflict ON gt_net_signed_record;
ALTER TABLE gt_net_signed_record
  ADD INDEX idx_signed_record_conflict (id_gt_net_origin, record_kind, subject_key, period_key, revision);

CREATE TABLE IF NOT EXISTS gt_net_seen_token (
  token_hash CHAR(64) PRIMARY KEY,
  expires_at DATETIME NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gt_net_violation_log (
  id_gt_net INT NOT NULL,
  violation_day DATE NOT NULL,
  violation_kind TINYINT NOT NULL,
  violation_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id_gt_net, violation_day, violation_kind),
  CONSTRAINT FK_ViolationLog_GtNet FOREIGN KEY (id_gt_net) REFERENCES gt_net (id_gt_net) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gt_net_origin_audit (
  id_origin_audit BIGINT AUTO_INCREMENT PRIMARY KEY,
  id_gt_net_origin INT NOT NULL,
  old_state TINYINT NOT NULL,
  new_state TINYINT NOT NULL,
  decision_source TINYINT NOT NULL,   -- AUTOMATIC/SIMULATED/MANUAL/BLOCKLIST/SELF_REVOKED/RECOVERED/EQUIVOCATION
  actor_id_user INT,
  trigger_detail TEXT NOT NULL,       -- JSON: Beweis-Referenzen, Parameterwerte
  decided_at DATETIME NOT NULL,
  CONSTRAINT FK_OriginAudit_Origin FOREIGN KEY (id_gt_net_origin) REFERENCES gt_net_origin (id_gt_net_origin),
  CONSTRAINT FK_OriginAudit_User FOREIGN KEY (actor_id_user) REFERENCES user (id_user) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gt_net_blocklist_source (
  id_blocklist_source INT AUTO_INCREMENT PRIMARY KEY,
  url VARCHAR(255) NOT NULL,
  publisher_public_key VARCHAR(64) NOT NULL,
  publisher_key_version INT NOT NULL,
  next_publisher_keys TEXT,
  allow_private_target TINYINT(1) NOT NULL DEFAULT 0,
  active TINYINT(1) NOT NULL DEFAULT 1,
  list_id VARCHAR(40),
  last_sequence BIGINT,
  last_content_hash CHAR(64),
  last_fetched_at DATETIME,
  expires_at DATETIME
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gt_net_blocklist_entry (
  id_blocklist_source INT NOT NULL,
  origin_gti VARCHAR(64) NOT NULL,
  entry_kind TINYINT NOT NULL,                    -- BLOCK/KEY_REVOKED
  reason_code VARCHAR(40) NOT NULL,
  since DATETIME NOT NULL,
  local_exception TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id_blocklist_source, origin_gti),
  CONSTRAINT FK_BlocklistEntry_Source FOREIGN KEY (id_blocklist_source)
    REFERENCES gt_net_blocklist_source (id_blocklist_source) ON DELETE CASCADE
) ENGINE=InnoDB;

ALTER TABLE gt_net_config
  ADD COLUMN IF NOT EXISTS partner_gti VARCHAR(64),
  ADD COLUMN IF NOT EXISTS partner_public_key VARCHAR(64),
  ADD COLUMN IF NOT EXISTS partner_key_version INT,
  ADD COLUMN IF NOT EXISTS partner_recovery_public_key VARCHAR(64),
  ADD COLUMN IF NOT EXISTS key_pinned_at DATETIME,
  ADD COLUMN IF NOT EXISTS integrity_session TINYINT(1) NOT NULL DEFAULT 0;
```

### 17.2 Anwendung (grafioschtrader-common)

```sql
-- Herkunft auf allen Kurszielen, schmal (INT/BIGINT/TINYINT)
ALTER TABLE historyquote
  ADD COLUMN IF NOT EXISTS price_source TINYINT,
  ADD COLUMN IF NOT EXISTS received_from_id_gt_net INT,
  ADD COLUMN IF NOT EXISTS id_gt_net_origin INT,
  ADD COLUMN IF NOT EXISTS id_signed_record BIGINT,
  ADD COLUMN IF NOT EXISTS confirmation_level TINYINT,
  ADD COLUMN IF NOT EXISTS confirmed_fields TINYINT;
-- gleiche Spalten auf gt_net_historyquote und gt_net_lastprice
ALTER TABLE securitycurrency
  ADD COLUMN IF NOT EXISTS s_price_source TINYINT,
  ADD COLUMN IF NOT EXISTS s_received_from_id_gt_net INT,
  ADD COLUMN IF NOT EXISTS s_id_gt_net_origin INT,
  ADD COLUMN IF NOT EXISTS s_id_signed_record BIGINT,
  ADD COLUMN IF NOT EXISTS s_confirmation_level TINYINT,
  ADD COLUMN IF NOT EXISTS s_confirmed_fields TINYINT;
```

- Auf `historyquote` erhält nur `received_from_id_gt_net` einen Fremdschlüssel (`ON DELETE SET NULL`).
  `id_gt_net_origin` und `id_signed_record` bekommen **bewusst keinen** Fremdschlüssel, um die grösste Tabelle nicht
  mit zusätzlichen Prüfungen zu belasten; ihre Integrität sichern Rückruf und Aufbewahrung (ein referenzierter
  signierter Datensatz wird nie gelöscht). Index `idx_historyquote_origin (id_gt_net_origin)`.
- `ADD COLUMN` nullbarer Spalten läuft auf MariaDB als `ALGORITHM=INSTANT`; die Indexanlage mit
  `ALGORITHM=INPLACE, LOCK=NONE`. Ihre Laufzeit wird vor der Auslieferung auf einer Kopie in Produktionsgrösse
  gemessen und in den Release-Hinweisen genannt.

Weitere Tabellen (Skizze der Spalten):

| Tabelle | Inhalt |
|---|---|
| `gt_net_origin_stats` | pro Ursprung: Werte empfangen, Ereignisse bestätigt, Anomalie-Ereignisse 30 Tage, Datenquellenklassen |
| `gt_net_origin_scope` | pro Ursprung: erlaubte Anlageklassen oder Instrumente; instrumentbezogene Drift-Sperren |
| `gt_net_price_staging` | Art, lokales oder Pool-Instrument, Datum/Zeit, Werte, Ursprung, signierter Datensatz, Peer, `hold_reasons` (Bitmaske), `decide_not_before` (Kandidatenfenster), gehalten seit, Ablauf |
| `gt_net_confirmation` | Ziel (Tabelle, Zeile), Grad, `confirmed_fields`, stützende Datensätze und Einheiten (JSON), Referenz und Konnektor, Toleranzen, Policy-Version, `supersedes_id`, `actor_id_user`, Grund, Zeitpunkt |
| `gt_net_verification_result` | Ursprung, `event_key` (eindeutig je Ursprung), Instrument, Art, Referenzart und -konnektor, Werte, `d`, Ergebnis, Zeitpunkt |
| `gt_net_origin_drift` | (Ursprung, Ebene Instrument/Anlageklasse, Schlüssel): `cusum_pos`, `cusum_neg`, Anzahl, Grundversatz `m`, `reference_connector` |
| `gt_net_threshold_config` | Anlageklasse, `special_investment_instrument` (−1 = Basis), `crypto`, `consensus_last_percent`, `consensus_hist_percent`, `anomaly_last_percent`, `anomaly_hist_percent`, `jump_floor_percent`; Standardwerte per `INSERT IGNORE` |
| `gt_net_recall` | Auslöser, Ursprung oder Peer, Zustand, Fortschritt (letzte Zeilen-ID je Tabelle), Zähler, frühestes Datum, Start/Ende |
| `gt_net_recall_row` | Rückruf, Tabelle, Zeilen-ID, Originalwerte, Herkunft, Beleg, Ersatzbeleg |

### 17.3 Bestandsmigration

Bestehende `historyquote`-Zeilen erhalten `price_source` nach `create_type`: `CONNECTOR_CREATED` → 3 (Herkunft
unbekannt, weil GTNet-Zeilen bisher denselben Typ trugen), `MANUAL_IMPORTED`, `ADD_MODIFIED_USER`, `CALCULATED` und die
Füllarten → 1. Ab H9 setzt jeder Schreibpfad die Quellart ausdrücklich; neue Konnektorzeilen erhalten 0, und ein
späteres Neuladen einer Historie über den Konnektor ersetzt die Quellart 3 durch 0. Unsignierte Altbestände werden
weder nachsigniert noch aufgewertet.

### 17.4 Aufbewahrung

- Last-Preis-Datensätze: der von `s_id_signed_record` bzw. dem Pool referenzierte und alle der letzten 7 Tage
  (Equivocation-Fenster); ältere unreferenzierte werden gelöscht.
- Monatsblöcke: jede referenzierte Revision und die höchste je (Ursprung, Instrument, Monat).
- Belege, Prüfergebnisse, Archivzeilen: §13.4. Ursprungs-Audit und Schlüsselerklärungen: unbefristet.

---

## 18. Konfiguration

Globalparameters mit `input_rule`, Konstante, Zugriffsmethode und NLS-Schlüsseln in beiden Sprachen
(`backend/CLAUDE.md`). Präfix nach besitzendem Modul: `g.` für Bibliothek, `gt.` für Anwendung.

| Schlüssel | Standard | Regel |
|---|---|---|
| `gt.gtnet.integrity.single.source.mode` | 0 | `min:0,max:1` |
| `gt.gtnet.integrity.connector.first` | 0 | `min:0,max:1` |
| `gt.gtnet.integrity.own.value.max.age.minutes` | 30 | `min:1,max:1440` |
| `gt.gtnet.integrity.consensus.quorum` | 2 | `min:2,max:5` |
| `gt.gtnet.integrity.lastprice.window.seconds` | 60 | `min:5,max:900` |
| `gt.gtnet.integrity.candidate.window.minutes` | 120 | `min:0,max:1440` |
| `gt.gtnet.integrity.jump.sigma` | 6 | `min:3,max:20` |
| `gt.gtnet.integrity.session.tolerance.minutes` | 30 | `min:0,max:240` |
| `gt.gtnet.integrity.staging.days` | 14 | `min:1,max:90` |
| `gt.gtnet.integrity.sampling.rate.released` | 5 | `min:0,max:100` |
| `gt.gtnet.integrity.sampling.rate.new` | 25 | `min:0,max:100` |
| `gt.gtnet.integrity.sanction.mode` | 0 | `min:0,max:1` |
| `gt.gtnet.integrity.recall.batch` | 500 | `min:50,max:10000` |
| `gt.gtnet.integrity.evidence.retention.months` | 24 | `min:6,max:240` |
| `gt.gtnet.origin.maturation.confirmed` | 200 | `min:10,max:10000` |
| `g.gnet.origin.maturation.days` | 30 | `min:7,max:365` |
| `g.gnet.origin.suspect.days` | 7 | `min:1,max:60` |
| `g.gnet.origin.quarantine.events` | 3 | `min:1,max:50` |
| `g.gnet.origin.quarantine.days` | 14 | `min:1,max:90` |
| `g.gnet.integrity.sender.violation.limit` | 10 | `min:1,max:1000` |
| `g.gnet.integrity.max.revisions.per.month` | 40 | `min:5,max:500` |

Dazu die Budgets aus §16.3. Die Schwellen pro Anlageklasse stehen in `gt_net_threshold_config`.

---

## 19. Angriffsanalyse

| Angriff | Stoppende Schicht | Restrisiko |
|---|---|---|
| T1 grob | G6 hält zurück; Bestätigung fehlt oder widerlegt | keines im Profil streng |
| T1 plausibel, Einzelquelle | streng: keine Übernahme ohne Bestätigung | gelockert: UNBESTÄTIGT bis Stichprobe, Drift oder Rückruf; Ergebnisse als vorläufig markiert |
| T1 Treppe | G6 gegen letzte vertrauenswürdige Referenz; Instrument-CUSUM | Treppe unterhalb `τ_konsens` bei kooperierender Mehrheit |
| T2 Relay-Manipulation | Signatur, Bindung (§7.6) | keines unter Krypto-Annahmen |
| T3 Zukunftszeit | H1, G3 | keines |
| T4 Anfrager-Injektion | H2 | keines |
| T5 Erstimport-Übernahme | H4 Antwortbindung, H7, H8 Vollvergleich, Übernahmeregel | ohne zweite Quelle: Staging (streng) bzw. UNBESTÄTIGT (gelockert) |
| T6 Erster Schreiber | Kandidatenfenster, G8 | keines bei mehreren Quellen |
| T7 gezielte Drift | Instrument-CUSUM gegen Grundversatz | Drift kleiner als `κ_I` |
| T8 Sybil/Kooperation | NEU zählt nicht; Stimmen pro Einheit und Datenquellenklasse; Widerspruch ⇒ UNGEKLÄRT | zwei kooperierende freigegebene Einheiten ohne ehrliche dritte Stimme; Freigabe ist Administratorannahme |
| T9 Equivocation | §7.7, Hinweis selbstbeweisend | nur wo beide Datensätze zusammentreffen |
| T10 Schlüsseldiebstahl | RECOVER, Sperrliste, Rückruf | ohne Wiederherstellungsschlüssel bis zur manuellen Neuzulassung bzw. Sperrliste |
| T11 Wiederkehr | neue Kennung ist NEU, Freigabe manuell, Sperrliste | Administrator gibt bewusst frei |
| T12 Ressourcen | Budgets, negative Caches, minimale Speicherung | Verfügbarkeitsminderung, nie Annahme |
| T13 Parallelfelder | §7.6 | keines |

---

## 20. Verhältnis zu den Varianten Zentral und A1

| Element | Zentral | A1 | A2 |
|---|---|---|---|
| Identität | Issuer-Zertifikat | `gti1` | `gti1` + Wiederherstellungsschlüssel |
| Ursprungsbeweis | optional | Pflicht + Hop-Quittung | Pflicht, Bindung Signatur–Inhalt |
| Freigabe | zentral, manuell | pro direktem Peer | pro Ursprung, mit Umfang |
| Prüfung | nach dem Schreiben, Stichprobe; zentrale Sanktion | nach dem Schreiben, Stichprobe; lokale Sanktion | vor dem Schreiben (Gate, Bestätigung) und danach (Stichprobe, Drift) |
| Unbestätigte Werte | autoritativ | autoritativ | Staging (streng) bzw. markiert (gelockert) |
| Konsens | — | — | übereinstimmende Gruppe mit Widerspruchsprüfung |
| Rückruf | — | — | Belege, Archiv, abhängige Reihen |
| Netzweite Sperre | zentrale Sanktion | — | abonnierbare signierte Datei |
| Infrastruktur | Zentrale + Galera | keine | keine (statische Datei optional) |

Die Identitätsschicht lässt sich für A1 und A2 gemeinsam bauen; Domain-Tags, Payloads und Schlüsselerklärungen sind
verschieden.

---

## 21. Umsetzungsphasen

Jede Lieferung ist für sich nutzbar; was zusammen wirken muss, kommt zusammen.

| Lieferung | Inhalt | Abnahme |
|---|---|---|
| **P0 Härtung** | H1–H12 | Zukunftszeit (auch bei Neuanlage), Anfrage ohne Schreibwirkung, falsches Instrument in der Antwort, deaktivierter Empfang, privates Duplikat einer ISIN: keine produktiven Werte verändert; Zwei-Peer-Test (`--gtnet-app`) |
| **P1 Gate, Staging, Übernahme, Auswertungsschutz** | §9, §10.1–§10.2, §10.4, §10.6–§10.7 mit Bestätigung durch den eigenen Konnektor, §13 für Peer-Rückruf und Einzelwerte, §14, Bestandsmigration §17.3 | Profil streng lässt keinen unbestätigten Wert in die Rechentabellen; Alarme mit unzureichend bestätigten Eingaben enden „nicht ausreichend bestätigt“; Rückruf pro Peer wiederanlauffähig |
| **P2 Identität** | §6 vollständig, Erklärungen über `GTNetMessageAttempt`, Schlüsselsicherung | Testvektoren grün; Replay, verzögerte Freigabe und Moduswechsel getestet |
| **P3 Signaturen, Ursprünge, Konsens** | §7, §8, §10.3, §10.5, Kandidatenfenster, Ursprungs-Rückruf | im signierten Modus erreicht kein unsignierter oder ungebundener Wert die Rechentabellen; Konsens-Szenarien aus §22 |
| **P4 Stichprobe, Drift, Sanktionen** | §11, §12 mit `sanction.mode = 0`; Kalibrierung | Kalibrierdaten mit Splits, Pennystocks, Prozent-notierten Anleihen, Krypto-Wochenenden und eingespielten Angriffen; getrennt gemessen: übernommene falsche Werte, zurückgehaltene echte Werte nach Grund (Sprung, fehlende Referenz, Split-Fenster), Zeit bis Erkennung und Reparatur, Prüfkosten. Erst danach `sanction.mode = 1` als Standard |
| **P5 Hinweise** | §16.2 | Equivocation-Hinweis sperrt; DEVIATION ändert keinen Zustand; eine Flut von Hinweisen bleibt im Budget |
| **P6 Sperrliste** | §15, Kommandozeilenwerkzeug zum Signieren mit offline gehaltenem Schlüssel | Konflikt gleicher Sequenz, Rollback, Herausgeberwechsel, Ablauf, Restore, Entfernung eines Grundes |

Bis P3 gibt es keine unabhängigen Ursprünge, also auch keinen Konsens: in P1 und P2 bestätigt nur der eigene
Konnektor. Instrumente ohne Konnektor erhalten in diesem Zeitraum nur im Profil *gelockert* übernommene GTNet-Kurse.

---

## 22. Abnahmeszenarien

| Szenario | Erwartet |
|---|---|
| Freigegebene Quellen 90 und 110, NEU-Kandidat 100 | kein Konsens |
| Derselbe Ursprung über drei Relays oder zwei rotierte Kennungen | eine Stimme |
| Zwei Ursprünge mit gleicher `dataSourceClass` | eine Stimme |
| Zwei unabhängige, eng übereinstimmende Quellen | BESTÄTIGT_KONSENS mit Beleg; nur verglichene Felder in `confirmed_fields` |
| Zwei übereinstimmende Quellen, eine dritte widerspricht | UNGEKLÄRT, keine Sanktion |
| Kandidat einer Einzelquelle, Profil streng | Staging, Anzeige „unbestätigt“ |
| Kurssprung ohne zweite Quelle | Staging bis Administratorentscheid, in beiden Profilen |
| Gültige Signatur über 100, DTO-Parallelfeld 1000 | Abweisung, nichts gespeichert |
| Zukunftszeit beim ersten Pool-Eintrag | Abweisung |
| Verschiedener Split-Stand, anderer MIC, Krypto am Wochenende | nicht vergleichbar bzw. korrekt übernommen, keine Anomalie |
| Zwei Last-Preise derselben Sekunde mit verschiedenen Sequenzen | keine Equivocation |
| Gleiche Sequenz, verschiedene Bytes | GESPERRT |
| Neusignierung, Retry, neue Monatsrevision desselben Ereignisses | keine zusätzlichen Zähler |
| Sperre einer notwendigen Konsensstütze | Beleg neu bewertet, Zeile entzogen oder herabgestuft, Herkunft unverändert |
| Sperre während laufendem Import, Abbruch des Rückruf-Workers | Import abgewiesen ab Commit; Wiederaufnahme ohne Verlust späterer korrekter Werte |
| Rückruf eines FX-Kurses für einen Mandanten mit Barbestand | Tagesgesamtwerte ab `D` neu berechnet |
| Rückruf eines Basiswerts eines abgeleiteten Wertpapiers | abgeleitete Historie ab `D` neu berechnet |
| Manipulierte Historie, korrekter Last-Preis | historienabhängiger Alarm „nicht ausreichend bestätigt“ |
| Tausende neue Ursprünge und Hinweise über einen Peer | Budgets halten, keine Annahme wegen Budgetmangel |
| Konnektor der eigenen Instanz fällt aus | UNGEKLÄRT, keine Anomalie, Score unverändert |
| Sperrliste gleiche Sequenz, anderer Inhalt | Konflikt, alte Liste bleibt |

---

## 23. Bekannte Grenzen

- **Gemeinsamer Anbieterfehler**: liefern alle Quellen denselben falschen Wert desselben Anbieters, erkennt A2 ihn
  nicht; erkennbar gemeinsame Anbieter werden nur nicht zur Bestätigung zusammengezählt.
- **Profil streng kostet Verfügbarkeit**: Instrumente mit nur einer GTNet-Quelle und ohne Konnektor erhalten keine
  übernommenen GTNet-Kurse; ein echter Kurssprung ohne zweite Quelle erscheint erst nach Administratorentscheid.
- **Profil gelockert** schützt gegen einen freigegebenen, lügenden Einzelursprung nur durch Gate, Kennzeichnung,
  Stichprobe, Drift und Rückruf.
- **Freigabe und Unabhängigkeit sind Annahmen des Administrators.** Kooperierende freigegebene Einheiten ohne
  widersprechende dritte Stimme bestätigen sich gegenseitig.
- **Altbestände** vor H9 sind keinem Peer zuzuordnen (Quellart „unbekannt“).
- **Zugestellte Alarme** lassen sich nicht zurückholen, nur mit Korrekturhinweis versehen.
- **Lokale Sicht**: Zustände verschiedener Instanzen können dauerhaft abweichen; über die Instanz hinaus wirken nur
  Sperrliste und Equivocation-Hinweise.
- **Sperrliste**: hängt am Herausgeber und an dessen Offline-Schlüssel; fällt er aus, veraltet sie, der Austausch läuft
  weiter.
- **Pseudonymität**: stabile Kennungen und signierte Datensätze erlauben, Instrumente und Aktivität eines Ursprungs
  über die Zeit zu verknüpfen.
