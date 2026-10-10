# Beurteilung von GTNet-Sicherheitskonzept A2

**Bezieht sich auf:** `specification/GTNet_Security_Decentral_Concept_A2.md`
**Gegen gelesen:** `GTNet_Security_Decentral_Concept.md` (A1) und `GTNet_Security_Central_Server_Concept.md`
**Code geprüft gegen:** Backend 0.38.1, höchste Flyway-Datei
`V0_38_2__correlation_set_include_dividends_and_hold_portfolio_indexes.sql`
**Datum:** 2026-10-10

A2 ist die richtige Antwort auf die Befürchtung, jemand werde über GTNet Kursdaten verfälschen. Es ist dafür
brauchbarer als A1 und brauchbarer als der zentrale Server. Umgesetzt in der vorliegenden Form würde es die
groben Angriffe schliessen und die Herkunft beweisbar machen. Einen einzelnen überzeugenden falschen Kurs, der
danach in Performance, Bewertung und Algorithmen weiterlebt, würde es im häufigen Fall weiterhin zulassen.

Die Einleitung verspricht, ein empfangener Kurs erreiche benutzerwirksame Daten erst, wenn er geprüft ist, seine
Herkunft beweisbar ist und er bei einem später erkannten Fehler wieder verschwindet. §22 und die Regeln in §9,
§10 und §13 lösen dieses Versprechen nur für einen Teil der Fälle ein. Die folgenden Punkte sind die Stellen, an
denen ich das Konzept vor einer Umsetzung ändern würde.

---

## Was A2 richtig ansetzt

Die Reihenfolge stimmt. Zuerst die Löcher im heutigen Austausch, dann ein Gate vor dem Schreiben, dann Herkunft,
erst danach Statistik und Sperren. A1 und das Zentralkonzept beginnen bei Identität und Stichproben und lassen
den Schreibpfad in `historyquote` und `s_last` offen. Gegen einen Angreifer, der heute schon einen Peer betreibt,
ist Phase 0 wirksamer als jede Signatur.

Die Beschreibung der Ausgangslage trifft den Code:

- `OpenLastpriceQueryStrategy.querySecurities` und `queryCurrencypairs` schreiben den mit der Anfrage
  mitgeschickten Preis in `security` bzw. `currencypair` des Lieferanten, sobald der Zeitstempel neuer ist
  (`updateSecurityFromDTO`, ab Zeile 76 bzw. 126). Ein verbundener Peer muss dafür kein Lieferant sein. Der
  so geschriebene Kurs kann anschliessend weitergegeben werden.
- `isNewer` in `GTNetLastpricePoolService`, `OpenLastpriceQueryStrategy` und `InstrumentExchangeSet` akzeptiert
  jeden späteren Zeitstempel. Ein Stempel in der Zukunft gewinnt dauerhaft, weil kein späteres GTNet-Update ihn
  überholt.
- `HistoryquotePushHandler.storeHistoryquotesForInstrument` und
  `GTNetHistoryquoteService.storeHistoryquotesForInstrument` fügen fehlende Tage direkt in `historyquote` ein.
  `findLocalSecurityMappings` verknüpft nur über ISIN und Währung und prüft `gt_net_historical_recv` nicht.
- `GTNetHistoricalImportService.selectBestPeer` wählt den Peer mit der grössten selbst gemeldeten Abdeckung in
  Tagen. Die Erfolgsquote ist dort ausdrücklich ungewichtet.
- `SupplierScoreCalculator` bildet die Erfolgsquote aus `entitiesUpdated / entitiesSent`. Wer immer einen neueren
  Stempel liefert, steigt im Score.
- `GTNetLastpriceService` und `HistoryquoteThruGTNet` fragen GTNet zuerst und den eigenen Konnektor für das, was
  übrig bleibt.

Ebenfalls richtig und beizubehalten:

- Signierte Bytes werden gespeichert und unverändert weitergegeben. Eine Instanz baut die Signatur nicht aus
  Datenbankfeldern neu.
- Testvektoren sind ein Release-Kriterium. Kanonisches JSON ist sonst der erste stille Bruch zwischen zwei
  Versionen.
- Equivocation ist nur dann ein Beweis, wenn beide Datensätze gültig signiert sind. Ein Hinweis allein sperrt
  niemanden.
- Die Sperrliste kann Kurse weder schreiben noch einen Ursprung freigeben, und ihr Ausbleiben hält den Austausch
  nicht an.
- Alarme auf einem unbestätigten Last-Preis auszulösen ist der falsche Ort für Vertrauen.
  `AlgoAlarmEvaluationService` ist der richtige Landepunkt.
- Rückruf mit `HoldDailyTotalMarker.markDirty` ist nötig. Die Klasse existiert und macht genau diese Markierung.
- Beobachtung vor Durchsetzung für die statistischen Zustände. Das Gate selbst wirkt in beiden Modi.
- Phase 0 ist konzeptunabhängig. Sie sollte unabhängig davon gebaut werden, welches Konzept am Ende gewinnt.
- Auf einen zentralen Server für diese Bedrohung zu verzichten ist richtig. Der zentrale Entwurf prüft Kurse
  ebenfalls nur lokal und stichprobenartig. Er verteilt danach Sanktionen. Er verhindert das Schreiben eines
  falschen Kurses nicht, und er schafft mit der Zentrale ein zusätzliches Ziel.

---

## Was ein Angreifer danach trotzdem kann

Das Schutzziel ist ein Kurs, der in `historyquote`, in `s_last` und in den darauf aufbauenden Rechnungen landet.
Daran gemessen bleiben fünf Wege offen. Sie sind alle mit dem Konzept vereinbar, ein Angreifer muss keine
Signatur fälschen.

### 1. Der einzige Lieferant schreibt den Kurs

Viele Instrumente haben in einem kleinen GTNet genau einen Ursprung und keinen eigenen Konnektor. Für sie gilt:

- Historische Zeilen werden mit Grad EINFACH nach `historyquote` geschrieben
  (`gt.gtnet.integrity.min.level.historical` steht auf 1).
- Der Sprungtest lässt bei Aktien jede Bewegung bis zum Maximum aus 8 % und `6 · σ · √Δt` durch. Ein Schlusskurs
  kann sich an einem Tag um knapp 8 % verschieben, ohne das Gate zu verlassen. An den folgenden Tagen wiederholt,
  wandert der Kurs beliebig weit, weil der Referenzwert der bereits geschriebene Kurs ist.
- Konsens entfällt, weil `q = 2` nicht erreicht wird.
- Die Stichprobe entfällt, weil kein Konnektor konfiguriert ist.
- CUSUM läuft pro Ursprung und Anlageklasse. Ein einzelnes Instrument bewegt diese Summe nicht, solange der
  Ursprung bei den übrigen Aktien ehrlich bleibt.
- Der Rückruf läuft, wenn der Ursprung gesperrt wird. Ein Ursprung, der bei fast allem stimmt und bei einem
  Instrument lügt, wird nicht gesperrt.

Damit liegt der gezielte Angriff — ein Wert, ein Tag oder eine langsame Verschiebung — genau in der Lücke, die
§22 als „Einzelquellen-Instrument“ benennt. Für die Befürchtung ist das der Normalfall und keine Randnotiz.

Dieselbe Lücke gilt für den Erstimport. H7 prüft sechs Monate nur, wenn eine zweite Quelle existiert. Sonst geht
die ganze Historie als EINFACH in `historyquote`. Das ist der Moment mit dem grössten Schaden, und es ist der
Moment, in dem `selectBestPeer` heute dem Peer mit der grössten selbst behaupteten Abdeckung vertraut.

### 2. Der erste Schreiber gewinnt

Historische Zeilen werden nur eingefügt, solange für das Datum noch keine Zeile existiert
(`existing.isEmpty()` in beiden `storeHistoryquotesForInstrument`). G8 hält eine abweichende spätere Lieferung
zurück, bis sie BESTÄTIGT ist. Wer das Datum zuerst signiert und das Gate besteht, besitzt den Schlusskurs. Eine
ehrliche spätere Lieferung wird zum Korrekturkandidaten und bleibt liegen, solange keine zweite freigegebene
Quelle oder ein eigener Konnektor sie trägt.

Ein freigegebener Ursprung, der den Tag sofort pusht, gewinnt gegen einen langsameren ehrlichen Ursprung.

### 3. Ein nicht freigegebener Ursprung erreicht nach 60 Minuten `s_last`

§8 und A2-D3 sagen, ein Ursprung im Zustand NEU könne allein nichts in benutzerwirksame Daten schreiben. §9 hebt
das für Last-Preise wieder auf: Fehlen ein zweiter freigegebener Ursprung und ein eigener Konnektor, wird der
zurückgehaltene Last-Preis nach 60 Minuten als EINFACH mit `gate_warning` übernommen.

EINFACH ist in §10.1 definiert als Wert eines freigegebenen Ursprungs. Die automatische Freigabe erzeugt denselben
Grad für einen Ursprung, den niemand freigegeben hat. Ein Peer, der ein exotisches Instrument als einziger anbietet,
setzt damit den angezeigten Kurs. Alarme bleiben aus. Bewertung und alles, was `s_last` direkt liest, sehen den
Wert.

### 4. Zwei freigegebene Ursprünge bestätigen einander

Konsens mit `q = 2` und dem Median ist erfüllt, sobald zwei Ursprünge in verschiedenen `operator_group` nah
beieinander liegen. Die Gruppe trägt der Administrator der empfangenden Instanz ein. Zwei Instanzen desselben
Betreibers, die niemand gruppiert, zählen als zwei Ursprünge.

Bei drei Werten, von denen zwei zusammen lügen, ist der Median der Lügenwert. Beide Lügner erhalten
BESTÄTIGT_KONSENS. Der ehrliche Ursprung weicht vom Median ab und wird WIDERLEGT, danach VERDÄCHTIG. Die Sperre
trifft damit die Quelle, die widerspricht.

`excludedOriginGtis` steht auf der Anfrage. Ein böser Lieferant darf es ignorieren. Verworfen werden muss lokal,
nach Prüfung der Signatur. Solange das nur eine Bitte an den Lieferanten ist, liefert er denselben Ursprung unter
zwei Kennungen zurück, und der Empfänger zählt beide.

Zwei Ursprünge, die denselben Konnektor benutzen, sind keine zwei Quellen. A2 hat dafür kein Feld. Zwei Peers, die
beide Yahoo weitergeben, heben einen fehlerhaften Yahoo-Kurs auf BESTÄTIGT_KONSENS. Das ist schwächer als ein
einzelner EINFACH-Wert, weil die Oberfläche und die Alarme ihn als bestätigt behandeln. Den gemeinsamen
Quellenfehler nennt §22 als Nicht-Ziel. Ihn zur Bestätigung hochzustufen sollte das Konzept trotzdem vermeiden.

### 5. „Neuerer Zeitstempel“ bleibt die Auswahlregel

H1 begrenzt den Stempel auf `now + skew`. Der Standard von `g.gnet.max.clock.skew.minutes` ist 5. Ein Stempel im
Jahr 2099 ist damit ausgeschlossen, und Angriff T3 ist erledigt.

Innerhalb der Handelssitzung gewinnt weiterhin der jüngste Stempel. Ein freigegebener Ursprung setzt `priceTime`
auf das Sitzungsende und einen falschen Last-Preis. Der echte Schluss von kurz davor ist älter und ersetzt ihn
nicht. G3 akzeptiert den Wert, weil er in der Sitzung liegt. Am nächsten Morgen ist dieser Last-Preis der Kandidat
für den Schlusskurs.

---

## Regeln, die ich vor einer Umsetzung ändern würde

Die folgenden Punkte sind Änderungen an A2. Sie lassen Schichten, Signatur und Rückruf stehen.

### Promovieren statt Hineinschreiben

Unbestätigte GTNet-Werte gehören in `gt_net_price_staging` oder in die Pools, solange sie die einzige Quelle sind.
`historyquote` und `s_last` bleiben die Werte, aus denen Bewertung, Periodenperformance, Steuer und Algorithmen
lesen. Eine zusätzliche Spalte `confirmation_level`, die jeder Leser beachten muss, ist als Schutz zu leicht zu
übersehen. `LastpriceOrigin.ofHistoryquoteCreateType` behandelt heute jeden `create_type` ausser 3 und 6 als
echt gehandelten Schlusskurs. Ein GTNet-Insert, der `create_type` auf dem Java-Standard 0 lässt, erscheint als
`CONNECTOR_CREATED` und damit als `HISTORY_CLOSE`.

Konkret:

- Eine neue historische Zeile wird übernommen, wenn der eigene Konnektor sie bestätigt oder wenn mindestens drei
  unabhängige Ursprünge sie tragen (unten). Liegt keines von beiden vor, bleibt sie im Staging.
- Der Administrator kann ein Instrument ausdrücklich als „einzige GTNet-Quelle akzeptiert“ markieren. Erst diese
  Markierung erlaubt EINFACH für dieses Instrument. Die Markierung ist pro Instrument sichtbar und gilt nicht für
  den ganzen Ursprung.
- Die 60-Minuten-Freigabe für Last-Preise entfällt für Ursprünge im Zustand NEU und VERDÄCHTIG. Für einen
  freigegebenen Ursprung ohne zweite Quelle zeigt die Oberfläche den Staging-Wert als unbestätigt und schreibt ihn
  erst nach Sitzungsende in `s_last`, zusammen mit `gate_warning`. Alarme bleiben daran gebunden, dass der Grad
  mindestens BESTÄTIGT ist oder ein eigener Konnektor vorliegt.
- `HistoryquoteCreateType` erhält einen eigenen Wert für GTNet. `LastpriceOrigin` bildet ihn auf einen eigenen
  Herkunftswert ab, der in der Watchlist schon heute der Anzeigepfad ist (`WatchlistReport`).

Der Preis dieser Regel ist, dass ein echter Kurssprung ohne zweite Quelle liegen bleibt, bis jemand ihn annimmt.
Das ist die richtige Abwägung, wenn die Befürchtung die Kompromittierung der Kurse ist. Ein verpasster Tag ist
sichtbar. Ein still falscher Schlusskurs ist es nicht.

### Erster Schreiber ist ein Kandidat

Für ein Datum ohne Zeile sammeln die Empfänger bis zum Ende der Handelssitzung plus einer kurzen Frist alle
gültigen Kandidaten. Übernommen wird danach der bestätigte Wert. Eine einzelne frühe Lieferung setzt sich nicht
durch, nur weil sie zuerst ankam. G8 gilt damit auch für den ersten Wert und nicht erst für eine Korrektur.

### Konsens zählt unabhängige Quellen und ist stabil, wenn eine davon fehlt

- In den signierten Datensatz gehört die Konnektor-Klasse aus A1 (`connectorClass`, ein Hash der Konnektor-Identität,
  ohne API-Schlüssel und ohne Konto). Ursprünge mit derselben Klasse zählen im Konsens als eine Quelle. Die Klasse
  ist eine Selbstauskunft. Eine gelogene Klasse erzeugt höchstens eine zusätzliche Stimme und keine Bestätigung
  durch den eigenen Konnektor des Empfängers.
- `q` für BESTÄTIGT_KONSENS ist mindestens 3.
- Ein Wert ist nur dann bestätigt, wenn der Median sich nicht ändert, sobald eine beliebige der beteiligten Quellen
  entfernt wird. Zwei zusammenarbeitende Ursprünge erfüllen das nicht.
- Der Empfänger verwirft Datensätze, deren `originGti` auf seiner Ausschlussliste steht, nachdem die Signatur
  geprüft ist. Die Bitte an den Lieferanten bleibt, die Entscheidung fällt lokal.
- Fehlt die Unabhängigkeit oder das Quorum, bleibt der Wert unbestätigt. Er wird nicht deshalb bestätigt, weil zwei
  gleiche Quellen übereinstimmen.

### Sprung und Drift am letzten bestätigten Wert

Der Sprungtest misst gegen den letzten übernommenen, also bestätigten oder selbst erhobenen Schlusskurs, über alle
dazwischen liegenden Tage. Eine Treppe von täglich 7 % bleibt dann sichtbar, auch wenn jeder Einzelschritt unter
der Tagesgrenze liegt.

CUSUM pro Anlageklasse bleibt als Hinweis auf einen systematisch verschobenen Lieferanten. Eine Sperre oder auch
nur der Zustand VERDÄCHTIG für den ganzen Ursprung hängt daran zusätzlich eine zweite Summe pro Instrument, mit
einer Mindestzahl von Vergleichen. Sonst sperrt ein ehrlicher, konstant um 0,2 % abweichender Schlusskurs-Stil den
Lieferanten, während die gezielte Lüge an einem Titel untergeht. Die Beispielrechnung in §11.3 (vier Vergleiche bei
+1 %) ist der günstige Fall. Der betrieblich häufige Fall ist die kleine methodische Differenz über viele Titel.

Auffällige einzelne Instrumente erzeugen eine Meldung und eine erhöhte Stichprobe. Sie setzen den übrigen Bestand
des Ursprungs nicht automatisch auf VERDÄCHTIG.

### Weitergeben und lokal annehmen sind zwei verschiedene Entscheide

§7.4 gibt einen Datensatz nur weiter, wenn das eigene Gate ihn angenommen hat. Ein Relay mit dünner eigener
Historie, einem falschen Split oder einem zu engen Sprungtest wirft dann einen echten Kurs weg. Übrig bleibt die
kleine Fälschung, die unter der Schwelle lag und überall durchgelaufen ist.

Weitergegeben wird jeder Datensatz mit gültiger Signatur, dessen Ursprung lokal nicht GESPERRT ist und der die
strukturellen Prüfungen besteht (G1, G2, H1, H4, Grösse). G5, G6 und G7 entscheiden nur über die lokale Übernahme.
Ein Relay ist damit kein zweites Gate für das ganze Netz.

### Sitzungsende schlägt den jüngsten Stempel

Für den Schlusskurs zählt der Wert, dessen `priceTime` am nächsten am offiziellen Handelsschluss der Börse liegt,
unter den Kandidaten, die das Gate bestehen. Ein späterer Stempel innerhalb der Toleranz nach Schluss ersetzt einen
näher am Schluss liegenden bestätigten Wert nicht. Ein frischer Wert des eigenen Konnektors bleibt in jedem Fall
der übernommene Last-Preis. `gt.gtnet.integrity.connector.first` wäre damit der Standard, sobald das Instrument
einen Konnektor hat. GTNet füllt Lücken und ausgefallene Konnektoren.

### Rückruf räumt alles auf, was aus dem Schlusskurs entstanden ist

`HoldDailyTotalMarker.markDirty` setzt die Bestände der Mandanten neu an. Drei weitere Ableitungen tragen denselben
falschen Kurs und sind in §13 nicht genannt:

- `historyquote_period` hat keinen eigenen Zeitstempel. Die Suche nach geänderten Kursen über
  `historyquote.create_modify_time` (`HoldDailyTotal.getEarliestChangedQuoteDateByTenant`) sieht diese Zeilen nicht.
  Periodenperformance bleibt sonst auf dem alten Schluss.
- Abgeleitete Wertpapiere rechnen ihre Historie aus dem Basiswert (`HistoryquoteCreateType.CALCULATED`).
- Gap-Filler kopieren den letzten Schluss auf Tage ohne Handel (`FILL_GAP_BY_CONNECTOR`,
  `FILLED_NON_TRADE_DAY`, `FILLED_CLOSED_LINEAR_TRADING_DAY`).

Der Rückruf eines Tages baut diese drei Reihen ab dem betroffenen Datum neu. Sonst ist die Tageszeile repariert und
die Rechnung, die der Benutzer sieht, bleibt falsch.

### Letzter Preis mit Revision, wie der Monatsblock

Dieselbe `priceTime` mit einem anderen `last` ist bei Datenanbietern eine Korrektur eines schlechten Ticks und in
§7.5 eine Equivocation, die den ganzen Ursprung sperrt. Ein ehrlicher Ursprung, der einen Fehlausdruck berichtigt,
verliert damit alle seine Kurse.

Last-Preise erhalten eine `revision`. Equivocation ist dieselbe Kombination aus Instrument, `priceTime` und
Revision mit verschiedenen Bytes. Eine höhere Revision ist ein Korrekturkandidat und folgt denselben Übernahme-
regeln wie ein historischer Monatsblock. Gesperrt wird bei einem echten Widerspruch derselben Revision.

### Widerruf wird wiederholt, bis er ankommt

Code 40 geht an alle gepinnten Peers. Wer offline ist, verpasst ihn. Die Erklärung liegt lokal in einer Ausgangs-
warteschlange, wird beim nächsten Handshake erneut gesendet und gilt erst nach ACK als zugestellt. Bis dahin
akzeptiert der Empfänger den alten Schlüssel weiter, was bei einem gestohlenen Schlüssel das Fenster ist. Die
Sperrliste bleibt der langsame Weg für den Fall, dass der Schlüssel selbst weg ist und niemand mehr signieren kann.

### Kalender und Krypto

Fehlt der Handelskalender einer Börse, werden historische Zeilen zurückgehalten. Ein leerer Kalender darf nicht
dazu führen, dass H5 entfällt und jeder Tag akzeptiert wird.

Kryptopaare handeln am Wochenende. `Currencypair.getIsCryptocurrency()` und
`GlobalConstants.CRYPTO_CURRENCY_SUPPORTED` sind die vorhandene Erkennung. H5 mit Montag bis Freitag würde
ehrliche Wochenendkurse dieser Paare verwerfen. Für sie gilt der Kalender des Paares, nicht der Devisenkalender.

### Split, Dividende und Notierungseinheit gehören in den Vergleich

Der Sprungtest rechnet `v_ref` mit `Securitysplit` um. Der Konsens vergleicht die gemeldeten Werte direkt. Rund um
einen Split von 2:1 weicht ein roh gelieferter Kurs um 50 % vom adjustierten ab, und ein ehrlicher Ursprung wird
WIDERLEGT. Dieselbe Falle ist eine Notierung in Pence gegen eine Notierung in Pfund.

Aus A1 übernommen und in jedem signierten Kursdatensatz gebunden:

- `venue` (MIC der Börse, auf die sich der Kurs bezieht; leer bei Devisen)
- `quotationUnit` (`MAJOR` oder `MINOR`)
- `adjustment` fest `RAW`
- `priceField` (`LAST` oder `CLOSE`)

Verglichen wird nur, wenn Venue, Einheit, Adjustment und Feld übereinstimmen oder die Einheit vor dem Vergleich
umgerechnet wurde. Ein Split oder eine Dividende im Fenster von ±3 Handelstagen macht den Konsens für diese Tage
unentschieden, analog zur Stichprobe. Der Monatsblock bleibt roh. Jede Instanz wendet ihre eigenen
`securitysplit`-Faktoren lokal an.

`instrumentKey` allein als `ISIN:CURRENCY` vermischt Börsenplätze. Xetra und ein anderer Platz derselben ISIN sind
verschiedene Kurse. Die Venue im signierten Datensatz ist die Unterscheidung. Die lokale Zuordnung trifft das
Instrument, dessen Börse denselben MIC trägt.

### Grenzwerte aus der Entwicklerdatenbank

P1 verlangt, dass der Sprungtest auf der Entwicklerdatenbank unter 0,1 % echte Kurse zurückhält. Diese Datenbank
ist ein kleiner, gepflegter Bestand. Ein Sprung an einem kleinen Titel, ein Split, der in `securitysplit` fehlt,
oder ein Kryptopaar über das Wochenende kommen darin kaum vor. Die Schwelle kalibriert man an einem Bestand mit
Splits, Pennystocks, Anleihen in Prozent und Krypto, und man zählt getrennt: echte Ablehnungen, fehlende Referenz,
Split-Fenster. 0,1 % als einziges Tor erzeugt Druck, `k` und `f_min` so weit zu öffnen, dass der gezielte Angriff
wieder bequem hindurchgeht.

---

## Was aus den anderen beiden Konzepten fehlt, und was dort zu viel ist

A1 hat die strengere signierte Form: Venue, Notierungseinheit, Konnektor-Klasse, Rohschluss, Domain-Trennung,
Testvektoren. A2 hat das strengere Verhalten vor dem Schreiben: Gate, Staging, keine Übernahme ohne Regel,
Rückruf. Die Identität (`gti1`, Ed25519, Pinning, Schlüsselwechsel mit beiden Signaturen) ist in beiden dieselbe
und kann gemeinsam gebaut werden.

Hop-Quittungen aus A1 braucht diese Bedrohung nicht. Sie beweisen, wer geliefert hat, gegenüber einem Dritten.
A2 verteilt keine Anschuldigungen, für die dieser Beweis nötig wäre. Der authentifizierte Peer auf der Verbindung
reicht für die lokale Sperre des Senders.

Beobachtungsnachrichten, die A1 im Netz herumreicht, können einen Ursprung in Verruf bringen, ohne dass der
Empfänger den Kurs selbst gesehen hat. A2 lässt Hinweise beratend und Equivocation nur als selbstprüfenden Beweis.
Das ist die bessere Grenze. Sie bleibt nur dann besser, wenn Equivocation eng definiert ist (dieselbe Revision)
und ein DEVIATION-Hinweis den Zustand nicht verändert. Beides steht so in A2 und sollte so bleiben.

Der zentrale Server liefert eine gemeinsame Sperre und gemeinsame Schwellen. Beides kann die signierte Datei aus
§14 in schwächerer Form, ohne dass eine Instanz Kurse oder Freigaben aussprechen kann. Für die Kompromittierung
von Kursen ist die Zentrale keine stärkere Kontrolle. Sie sieht die Kurse nicht, sie glaubt den Meldungen der
Instanzen. Eine kompromittierte Zentrale kann ehrliche Peers netzweit sperren. Eine kompromittierte Sperrlisten-
Datei kann das auch, aber nur für Instanzen, die diese Datei abonniert haben, und sie kann keinen Kurs schreiben.
Das ist der kleinere Schaden, und er setzt voraus, dass der Offline-Schlüssel der Liste abhandenkommt.

Die Vergleichstabelle in §20 ist in der Richtung richtig und an zwei Zellen zu knapp. A1 hat eine Prüfung, sie
findet nach dem Schreiben statt und sie sperrt lokal. Das Zentralkonzept hat ebenfalls diese nachgelagerte Prüfung
und zusätzlich Sanktionen. Beide haben vor dem Schreiben kein Gate. So formuliert bleibt der Vergleich korrekt,
und A2 ist bei dieser Bedrohung vorn.

---

## Weitere Ansätze

Sie ersetzen das Gate nicht. Sie adressieren den Fall, den Signaturen offen lassen: der Unterzeichner lügt, und
der Kurs ist trotzdem plausibel.

**Eigener Konnektor ist die Autorität.** Sobald `id_connector_intra` oder `id_connector_history` gesetzt ist und
der Konnektor einen frischen Wert liefert, ist das der übernommene Kurs. GTNet darf ihn nicht mit einem neueren
Stempel überschreiben. Das dreht L8 um und ist der grösste Gewinn für jede Instanz, die eigene Kontingente hat.
A2 hat den Schalter und lässt ihn auf 0. Für das genannte Schutzziel gehört er auf 1.

**Vertrauen pro Instrument, nicht nur pro Ursprung.** Ein Ursprung, der für Schweizer Aktien freigegeben ist,
schreibt damit noch keine Kryptokurse. Die Freigabe kann an Anlageklasse oder an eine explizite Instrumentliste
gebunden sein. Die globale Freigabe in §8 ist für einen kleinen, persönlich bekannten Kreis von Peers bequem und
für einen Peer, der später sein Angebot ausweitet, zu gross.

**Tägliche Wurzel der signierten Revisionen.** Der Ursprung veröffentlicht neben der Sperrliste eine signierte
Merkle-Wurzel seiner Monatsrevisionen, als statische Datei, ohne Server. Ein Peer, der eine andere Revision
derselben Nummer besitzt, kann das vorzeigen. Equivocation wird damit sichtbar, auch wenn die beiden Datensätze nie
auf derselben Instanz zusammentreffen. Das ist der eine Punkt, den A2 bei T8 offen lässt, und er braucht keine
Zentrale. Er ist eine spätere Phase, nicht ein Grund, P0 bis P3 aufzuhalten.

**Keine stillen Admin-Freigaben für ganze Historien.** `gate_override` für einen Tag, mit Grund, protokolliert und
nie weitergegeben, ist vertretbar. Ein Knopf, der den zurückgehaltenen Erstimport von zehn Jahren auf einmal
übernimmt, wird zur Gewohnheit und ist dann der Weg des Angriffs. Die Oberfläche zeigt die grössten Sprünge und
lässt einzelne Monate zu.

---

## Korrekturen an Behauptungen, die der Code nicht hergibt

Die Richtung von §2 stimmt. An diesen Stellen würde eine Umsetzung danebengreifen, wenn sie den Text wörtlich
nimmt.

| Stelle in A2 | Was der Code zeigt |
|---|---|
| L2, drei `isNewer`-Stellen | Dieselben drei schreiben bzw. ersetzen Kurse und haben keine Obergrenze. Zusätzlich legt `PushOpenLastpriceQueryStrategy` einen Anfragepreis im Pool an, wenn das Instrument dort noch fehlt (ab Zeile 171), ohne `isNewer`. H1 muss diesen Pfad einschliessen. `LastpricePoolQueryService.isNewer` entscheidet nur, ob ein lokaler Kurs zurückgesendet wird. |
| H3, Landepunkt `LastpricePushHandler` | Der Handler schreibt den Pool (`GTNetLastpricePoolService.updateSecurityLastpricesFromDTO`), nicht `security` / `currencypair`. Die benutzerwirksamen Last-Preis-Schreibstellen sind `OpenLastpriceQueryStrategy` und `InstrumentExchangeSet.processResponse`. Das Empfangs-Flag auf dem Pool zu erzwingen würde das Weiterleiten von Instrumenten unterbinden, die der Relay selbst nicht abonniert hat. |
| H6, Abdeckung im Score | `SupplierScoreCalculator` multipliziert die Anzahl der unterstützten Instrumente mit der Erfolgsquote und einem OHL-Faktor. Die Abdeckung in Tagen ist die Auswahl in `selectBestPeer`, und dort ist die Erfolgsquote ungewichtet. Der OHL-Faktor ist eine Selbstauskunft des Lieferanten und bleibt nach H6 ein Weg, den Score zu heben. |
| Belegte Codes 50–54, frei 31–49 | 51 hat keine Konstante. 31 trägt den Kommentar, den Wert nicht wiederzuverwenden (`GNetCoreMessageCode`, ehemals `GT_NET_ADMIN_MESSAGE_ALL_C`). 8, 9, 14–19, 32–49 sind frei. Code 8 für die Handshake-Ablehnung ist frei. |
| `DataHelper.round(value, 8)` als kanonische Zahl | `DataHelper.round` rechnet mit `double`. Acht Stellen entsprechen `FID_MAX_FRACTION_DIGITS` und der Spalte. Die kanonische Darstellung braucht `BigDecimal` mit festem Rundungsmodus, sonst unterscheiden sich zwei Sprachen in den Bytes und die Signatur bricht. Sehr kleine Kurse unterhalb von 1e-8 fallen zusammen. |
| Öffentlicher Schlüssel `VARCHAR(64)`, Signatur `VARCHAR(96)` | Base64 von 32 Bytes ist 44 Zeichen, Base64 einer Ed25519-Signatur ist 88 Zeichen. Die Längen reichen. Der private Schlüssel und das Stichproben-Geheimnis liegen Jasypt-verschlüsselt in der Datenbank. Wer die Datenbank und `JASYPT_ENCRYPTOR_PASSWORD` hat, hat die Identität. Das ist im Betrieb dieser Anwendung der normale Schlüsselort. Die Sicherung ist in A2 ein Flag `key_backed_up` ohne beschriebenen Export. Der Export gehört in die Phase, die den Schlüssel erzeugt, und der private Schlüssel bleibt aus jeder `ExportDefinition` heraus. Ein Test, der die Tabelle auf Export prüft, ist billiger als die spätere Suche nach dem Leck. |
| `gt_net_origin` in der Bibliothek | Identität, Pinning, signierte Hülle und Sperrliste gehören in `grafiosch-base`. Zähler wie `values_confirmed` und `anomalies_30d` und die Übergänge wegen Kursabweichungen sind Anwendungslogik und gehören in eine Tabelle der Anwendung. Sonst trägt die Bibliothek Vokabular, das nur Grafioschtrader füllt. |
| `gt_net_signed_record` ohne Aufbewahrung für überholte Last-Preise | Ein Last-Preis pro Aktualisierung und Instrument wächst ohne Grenze. Die Aufbewahrung „solange eine Kurszeile darauf verweist“ löscht alte Last-Preis-Signaturen, sobald `s_last` auf die neue zeigt. Dafür braucht es eine Regel, sonst bleibt jede Tick-Signatur liegen. Monatsblöcke bleiben, solange eine übernommene Zeile ihre Revision referenziert. |
| Fremdschlüssel auf `historyquote` | Der Text legt den Fremdschlüssel für `received_from_id_gt_net` an und einen Index auf `id_gt_net_origin`. Für `id_gt_net_origin` und `id_signed_record` ist kein Fremdschlüssel gezeigt. Auf dieser Tabelle ist das vertretbar, dann muss es ausdrücklich so stehen. Die Migration einer so grossen Tabelle braucht ein Online-DDL-Verfahren und eine Schätzung der Laufzeit. `gt_ddl.sql` bleibt unangetastet, das ist richtig. |
| Gemeinsamer Quellcode-Name `AlgoAlarmEvaluationService` | Der Klassenname stimmt. Die Auswertung liest den Last-Preis. Historische EINFACH-Werte fliessen laut §15 weiter in die Berechnungen. Das ist die Rechnung, die ein falscher Schlusskurs verfälscht, auch wenn kein Alarm klingelt. |

`findLocalSecurityMappings` kann mehrere lokale Wertpapiere derselben ISIN und Währung treffen und filtert
`id_tenant_private` nicht. Die Map behält dabei einen Treffer. H3 sollte festlegen: geschrieben wird nur in das
gemeinsame Instrument (`id_tenant_private IS NULL`), und nur wenn genau eines passt. Ein privates Duplikat eines
Mandanten ist kein GTNet-Ziel.

---

## Empfehlung

Phase 0 jetzt bauen, auch wenn die Konzeptwahl noch offen ist. H1, H2, H3, H4 und die Herkunftsspalte H8 ändern
das Protokoll nicht und schliessen den Angriff, der heute ohne jede Kryptographie funktioniert: ein Peer schreibt
über die Anfrage den Kurs des anderen und dieser gibt ihn weiter. H5, H6 und H7 können mit dem Gate kommen.

Danach A2 als Gerüst, mit der signierten Feldliste aus A1 (Venue, Einheit, Rohschluss, Konnektor-Klasse) und mit
den Übernahmeregeln aus diesem Dokument. Identität einmal bauen. Hop-Quittungen und den zentralen Server für
dieses Schutzziel weglassen. Die Sperrliste ist sinnvoll, sobald es mehr als eine Handvoll Peers gibt, und sie ist
keine Voraussetzung für den Schutz einer einzelnen Instanz.

P1 so, wie es jetzt steht, würde ich nicht ausliefern. Es schreibt EINFACH in `historyquote`, sobald das Gate
einen plausiblen Wert sieht. Das ist eine Verbesserung gegenüber heute und es ist noch nicht der Schutz, den die
Einleitung beschreibt. Der Schutz beginnt an der Stelle, an der ein unbestätigter Kurs die Tabellen nicht
erreicht, aus denen der Benutzer rechnet.

---

## Offene Entscheidungen

Diese drei Punkte ändern den Umfang. Sie folgen nicht aus dem Code.

1. Darf ein einziger vom Administrator freigegebener Peer der alleinige Kurs eines Instruments sein, das in die
   Performance eingeht? Wenn ja, bleibt EINFACH für freigegebene Ursprünge, und der Schutz gegen genau diesen Peer
   ist Gate, Anzeige und Rückruf. Wenn nein, gilt die Promotionsregel oben, und Instrumente ohne zweite Quelle
   brauchen eine explizite Freigabe pro Instrument.
2. Soll ein echter Tagessprung ohne zweite Quelle liegen bleiben, bis ein Mensch ihn ansieht? Das ist die
   praktische Folge eines engen Gates. Die Alternative ist die 60-Minuten- bzw. Sieben-Tage-Freigabe, und die
   öffnet denselben Weg dem Angreifer.
3. Wer betreibt die Sperrliste, und ist ein Offline-Schlüssel dafür gewollt? Ohne Liste bleiben Sperren lokal,
   und ein gestohlener Schlüssel ist erst widerrufen, wo der Betreiber seine Peers noch mit dem alten Schlüssel
   erreicht oder sie ihn von Hand sperren.
