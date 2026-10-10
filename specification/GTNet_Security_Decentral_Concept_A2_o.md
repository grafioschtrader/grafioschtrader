# Beurteilung des GTNet-Sicherheitskonzepts A2

**Datum:** 10. Oktober 2026.

**Code baseline:** backend 0.38.1, höchste vorhandene Flyway-Datei
`V0_38_2__correlation_set_include_dividends_and_hold_portfolio_indexes.sql`.
Die Prüfung bezieht sich auf den lokalen Arbeitsstand einschliesslich nicht eingecheckter Änderungen.

**Gegenstand:** [A2](GTNet_Security_Decentral_Concept_A2.md), verglichen mit
[A1](GTNet_Security_Decentral_Concept.md) und dem
[zentralen Konzept](GTNet_Security_Central_Server_Concept.md).
Dies ist die ausdrücklich gewünschte separate Beurteilung, keine ersetzende Umsetzungsspezifikation.
Abschnittsangaben mit „A2 §“ verweisen auf das untersuchte Dokument.

## 1. Mein Urteil

**Ich würde A2 als Grundlage weiterentwickeln, aber nicht unverändert umsetzen.** Sein stärkster Beitrag ist,
das Problem vom einzelnen importierten Kurs aus zu betrachten: vor dem Schreiben prüfen, Herkunft erhalten,
unbestätigte Daten zurückhalten und Fehler nachträglich reparieren. Das passt besser zur Sorge vor vergifteten
Kursdaten als eine Lösung, die hauptsächlich Identitäten und Sanktionen organisiert.

Die wichtigsten Bausteine sind sinnvoll: eine Freigabe pro Ursprung statt nur pro direktem Peer, obligatorische
Ursprungssignaturen, Staging, nachvollziehbare Bestätigungen und Rückruf. Auch der Verzicht auf ein verteiltes
Reputationssystem in Version 1 ist eine gute Begrenzung des Umfangs.

**Das Schutzversprechen ist derzeit stärker als die Regeln.** Standardmässig dürfen unbestätigte Einzelquellenwerte
weiterhin in die produktive Historie und in Berechnungen gelangen. Die Konsensformel kann einen Wert bestätigen,
dem keine der Referenzquellen zustimmt. Der Rückruf kann eine Bestätigung behalten, deren notwendige Stütze gerade
gesperrt wurde. Die Alarmabsicherung erfasst nur einen Teil der tatsächlich verwendeten Daten.

Ausserdem gibt A2 einige Vorsichtsmassnahmen der älteren Konzepte auf: genaue Vergleichssemantik, mehrere unabhängige
Referenzen für folgenreiche Sanktionen und eine vorsichtige Unterscheidung zwischen Anomalie und Fehlverhalten.
Weniger Komplexität ist hier teilweise mit höherem Fehlalarm- und Reparaturrisiko erkauft.

Meine Empfehlung ist ein **strenges A2-Kernsystem mit manuell zugelassenen Ursprüngen und dauerhaft gespeicherten
Bestätigungsbelegen**. Statistik, Hinweise und eine abonnierbare Sperrliste ergänzen dieses System später.
Eine zentrale Zulassungsstelle bleibt eine mögliche Ergänzung. Keine dieser Varianten kann die wirtschaftliche
Richtigkeit eines Kurses kryptographisch beweisen. Schutz benötigt eine ausdrücklich benannte Vertrauensannahme
über Quellen und ein definiertes Verhalten, wenn unabhängige Bestätigung fehlt.

## 2. Was die Codeprüfung bestätigt und präzisiert

Die Ausgangslage von A2 ist in wesentlichen Punkten richtig. Die folgenden Fundstellen wurden im aktuellen Baum
geprüft; Zeilennummern gelten für diesen Arbeitsstand.

| Aussage | Beurteilung und Beleg |
|---|---|
| Anfragen können echte Last-Preise verändern. | Bestätigt: `querySecurities` und `queryCurrencypairs` übernehmen neuere Anfragewerte; die Methoden laufen transaktional. [OpenLastpriceQueryStrategy.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/gtnet/handler/impl/lastprice/OpenLastpriceQueryStrategy.java), Zeilen 44, 77, 126 und 165. Das ist ein ausdrücklich gebauter bidirektionaler Austausch, dessen Schreibwirkung sicherheitlich eingeschränkt werden muss. |
| Es fehlt eine Obergrenze für Kurszeitstempel. | In den untersuchten Vergleichen bestätigt: [GTNetLastpricePoolService.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/service/GTNetLastpricePoolService.java), Zeile 373, und [InstrumentExchangeSet.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/service/InstrumentExchangeSet.java), Zeile 303. [GTNetProtocolLimits.java](../backend/grafiosch-server-base/src/main/java/grafiosch/gtnet/GTNetProtocolLimits.java), Zeilen 40–44, beschreibt dagegen den Envelope-Zeitstempel. |
| Historische Werte werden ungeprüft direkt gespeichert. | Im Kern richtig, aber „keine Wertprüfung“ ist zu pauschal: Der lokale Import verlangt Datum und Close, fügt nur fehlende Tage ein und bereinigt nichtpositive optionale OHLCV-Felder. Eine unabhängige Preisprüfung fehlt. [GTNetHistoryquoteService.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/service/GTNetHistoryquoteService.java), Zeile 1042; [HistoryquotePushHandler.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/gtnet/handler/impl/HistoryquotePushHandler.java), Zeile 289. |
| Die Bereinigung schützt auch den Schlusskurs. | Nein: `resetNonPositiveOhlcvToNull` lässt Close ausdrücklich unverändert. [BaseHistoryquote.java](../backend/grafioschtrader-common/src/main/java/grafioschtrader/entities/BaseHistoryquote.java), Zeile 143. Struktur-, Wertebereichs- und wirtschaftliche Plausibilitätsprüfung bleiben getrennte Aufgaben. |
| Der Erstimport vertraut der gemeldeten Abdeckung. | Bestätigt: `selectBestPeer` bevorzugt die grösste `coverageDays`-Angabe. Zusätzlich übernimmt `fetchFromPeer` die zurückgegebenen Zeilen für das angefragte lokale Wertpapier, ohne an dieser Stelle den Instrumentenschlüssel jedes Antwort-DTOs dagegen zu prüfen. [GTNetHistoricalImportService.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/service/GTNetHistoricalImportService.java), Methoden `selectBestPeer` und `fetchFromPeer`. H4 muss ausdrücklich auch diesen Pfad abdecken. |
| Der Lieferantenscore ist kein Integritätsnachweis. | Bestätigt: Er verwendet Abdeckung und Erfolgsquote; zusätzlich gibt es bereits einen konfigurierbaren OHLC-Reichhaltigkeitsfaktor. [SupplierScoreCalculator.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/service/SupplierScoreCalculator.java), Zeilen 18–25 und 97–99. |
| Der erste ausreichende Lieferant beendet den Bezug. | Bestätigt: Abbruch bei `allFilled()`; eigener Konnektor als späterer Rückfall. [GTNetLastpriceService.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/service/GTNetLastpriceService.java), Zeilen 266 und 360. |
| Eine Prüfung des Last-Preises schützt alle Alarme. | Nein: Periodenänderung, MA/RSI und Ausdrücke verwenden Historie; Ausdrücke ausserdem PrevClose, Open, High, Low und Volume. [AlgoAlarmEvaluationService.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/service/AlgoAlarmEvaluationService.java), Zeilen 263, 300, 343, 383 und 468. |
| Der vorhandene Verletzungszähler kann die neue 30-Tage-Regel tragen. | Nicht unverändert: `requestViolationCount` ist ein `Byte`, wird auf 99 begrenzt und enthält selbst keine Ereigniszeit. A2 erlaubt einen Grenzwert bis 1000. [GTNetConfig.java](../backend/grafiosch-base/src/main/java/grafiosch/entities/GTNetConfig.java), Zeilen 89–98 und 254–260. |

Diese Prüfung ist eine gezielte statische Prüfung der relevanten Pfade, kein vollständiger Sicherheitstest der
laufenden Anwendung. Die Konzeptbefunde im folgenden Abschnitt sind davon getrennt zu verstehen.

## 3. Sicherheitslücken und konkrete Verbesserungen

**Priorität:** „Blocker“ bedeutet: vor produktivem Betrieb mit dem behaupteten Integritätsschutz lösen.
„Hoch“ betrifft wesentliche Angriffs- oder Fehlalarmrisiken. „Mittel“ betrifft Präzisierung und Betriebsfähigkeit.

### B1 — Unbestätigte Werte und Zeitfreigabe umgehen das starke Schutzziel

**Blocker — A2 §3, §5 H7, §9, §10.4, §15.**

Ein freigegebener Ursprung darf plausible, aber falsche Werte als EINFACH in `historyquote` schreiben.
Beim Erstimport ohne zweite Quelle gilt dies sogar für die gesamte Historie. Das ist ein bewusst akzeptierter
Einzelquellenbetrieb, kein Schutz vor einem böswilligen freigegebenen Ursprung.

Die Freigabe zurückgehaltener Last-Preise nach 60 Minuten ist zusätzlich gefährlich unpräzise: Sie schliesst NEU,
VERDÄCHTIG und OHLC-widersprüchliche Datensätze nicht ausdrücklich aus. Nach dem Wortlaut kann Zeitablauf fehlendes
Vertrauen ersetzen. Weil das Gate beim ersten fehlgeschlagenen Test stoppt, können weitere Ablehnungsgründe noch
gar nicht geprüft worden sein.

**Vorschlag:** Ein strenges Standardprofil hält EINFACH-Werte ausserhalb autoritativer Kursreihen und automatisch
genutzter Pools. Die Oberfläche darf sie als gesonderte, unbestätigte Beobachtung anzeigen. Kein Zeitablauf hebt
Herkunfts-, Sperr-, Struktur- oder Vergleichsanforderungen auf. Vor jeder Übernahme aus Staging das gesamte Gate
mit der aktuellen Policy erneut ausführen. Manuelle Ausnahmen behalten einen sichtbaren Sonderstatus und werden
nicht automatisch weitergegeben oder für Alarme verwendet.

Wer mehr Verfügbarkeit braucht, kann ausdrücklich einen gelockerten Einzelquellenmodus wählen. Dann muss die
Oberfläche auch betroffene Bewertungen als vorläufig kennzeichnen. Die Aussage „jeder Kurs ist geprüft“ muss
zwischen Plausibilitätsprüfung und unabhängiger Bestätigung unterscheiden.

### B2 — Der Median ist noch kein Konsens

**Blocker — A2 §10.1–§10.3.**

Die Definition verlangt übereinstimmende unabhängige Ursprünge. Die Formel verlangt jedoch nur genügend Quellen
und Nähe des Kandidaten zum Median aller Quellen.

**Gegenbeispiel:** Zwei freigegebene Ursprünge liefern 90 und 110. Ein NEU-Ursprung liefert 100. Bei `q = 2` ist
der Median 100, die Kandidatenabweichung null. Der NEU-Ursprung wird BESTÄTIGT_KONSENS, obwohl keine Referenz seinen
Wert bestätigt. Bei zwei Quellen und mitgezähltem Kandidaten kann ausserdem deren gegenseitige Abweichung ungefähr
doppelt so gross wie die nominelle Konsenstoleranz sein.

**Vorschlag:** Erst eine Gruppe tatsächlich übereinstimmender, vergleichbarer Werte bestimmen; danach bestätigen.
Für positive Werte lässt sich die Übereinstimmung einer Gruppe beispielsweise durch
`(max(v) - min(v)) / min(v) ≤ τ_konsens` begrenzen. Es müssen mindestens `q` unabhängig zugelassene Gruppen
beteiligt sein. Pro Gruppe und wirtschaftlichem Ereignis zählt höchstens eine Stimme. Ein nicht freigegebener
Kandidat zählt nicht mit und muss zusätzlich zu den stützenden Werten passen.

Gibt es mehrere widersprüchliche tragfähige Gruppen, lautet das Ergebnis UNGEKLÄRT. Ein Median kann innerhalb
einer übereinstimmenden Gruppe der Auswertung dienen; ein daraus neu gebildeter Preis darf nicht die Signatur
einer Originalquelle erhalten.

`q = 2` bedeutet unter diesen Regeln eine Zweitbestätigung, keine allgemeine byzantinische Konsensgarantie.
Eine Empfehlung „ein bösartiger Teilnehmer wird toleriert“ benötigt eine feste zugelassene Menge, ein
Angreifermodell und Regeln für ausbleibende Antworten. Nur die zuerst antwortenden Quellen zu zählen genügt nicht.

### B3 — Quellenunabhängigkeit und automatische Reife sind angreifbar

**Hoch — A2 §8, §10, §19 T7/T10.**

Verschiedene Schlüssel bedeuten weder verschiedene Betreiber noch verschiedene Datenanbieter. Ein Angreifer kann
mehrere Ursprünge 30 Tage lang korrekte öffentliche Werte kopieren lassen und danach gemeinsam falsche Werte
liefern. Die 200 Bestätigungen belegen vergangene Übereinstimmung, nicht zukünftige Ehrlichkeit oder Unabhängigkeit.
Das gilt auch für das Neusignieren kopierter fremder Kurse: Die Signatur beweist den Unterzeichner, nicht dessen
behaupteten Datenbeschaffungsweg.

**Vorschlag:** Für Version 1 MANUELL beibehalten und AUTO_NACH_REIFE höchstens als Freigabeempfehlung verwenden.
Unbekannte Betreiberunabhängigkeit darf nicht automatisch als nachgewiesene Unabhängigkeit zählen. Betreibergruppen
und bekannte gemeinsame Datenanbieter getrennt führen; Angaben eines Ursprungs sind Hinweise, keine Beweise.
Mehrere Relays desselben Ursprungs geben keine zusätzlichen Stimmen. Bei Rotation bleibt die Gruppe erhalten.

Die Lieferantenauswahl braucht bewusste Diversität. `excludedOriginGtis` ist eine nützliche Anfragehilfe, aber der
Empfänger muss die tatsächlich gelieferten Ursprünge selbst prüfen. Die vorhandene Lieferantenabdeckung beweist
nicht, welche unabhängigen Ursprünge ein Relay für ein Instrument liefern kann.

### B4 — Bestätigungen müssen belegbar, feldbezogen und widerrufbar sein

**Blocker — A2 §10, §13.2, §17.**

Ein Zahlenwert für `confirmation_level` enthält keinen Beweis. `gt_net_verification_result` speichert im Entwurf
weder den exakten geprüften Record noch die beteiligten Konsensursprünge und deren Records. Damit lässt sich beim
Rückruf nicht verlässlich feststellen, ob eine Bestätigung ohne den gesperrten Ursprung bestehen bleibt.

**Angriff:** X und Y tragen eine Bestätigung bei Quorum 2. X wird gesperrt. A2 behält den Wert und schreibt dessen
Herkunft auf Y um. Es bleibt faktisch eine Einzelquelle, während die Zeile weiter als bestätigt erscheint.
Die Umschreibung kann zudem den weiterhin referenzierten signierten Bytes widersprechen.

**Vorschlag:** Einen unveränderlichen Bestätigungsbeleg je Record beziehungsweise historischer Zeile speichern:
stützende Record-Hashes und Ursprünge, lokal verwendete Unabhängigkeitsgruppen, Referenzbeobachtung, Zeitpunkt,
Vergleichssemantik, geprüfte Felder und Policy-Version. Beim Wegfall einer Stütze neu entscheiden und nötigenfalls
herabstufen oder zurückhalten. Ursprüngliche Herkunft bleibt erhalten; ein Ersatzwert erhält einen eigenen
Nachweis und einen Verweis auf die Ersetzung.

Close allein bestätigt nicht High, Low oder Volume. Entweder werden diese Felder eigenständig bestätigt oder nur
der bestätigte Teil darf in empfindliche Auswertungen eingehen. Eine reine Erhöhung des Zeilengrads wäre irreführend.

### B5 — Der Alarm- und Berechnungsschutz ist unvollständig

**Blocker — A2 §3 S5, §15 und §21 P8.**

Ein korrekter aktueller Preis verhindert keinen falschen Periodenalarm, wenn der historische Vergleichskurs
manipuliert ist. Analog beeinflussen manipulierte Historien MA/RSI und manipulierte Nebenfelder Ausdrücke.
Der Codebeleg steht in Abschnitt 2. A2 erlaubt historische EINFACH-Werte ausdrücklich in Berechnungen und verschiebt
die Alarmbehandlung bis P8; das widerspricht dem umfassenden Ziel S5.

**Vorschlag:** Für jede Auswertung müssen alle verwendeten Eingaben die erforderliche Qualität besitzen.
Abgeleitete Ergebnisse erhalten einen Qualitätsstatus aus ihren Abhängigkeiten. Fehlt eine notwendige Bestätigung,
entsteht ein Ergebnis „nicht ausreichend bestätigt“, kein reguläres Alarmsignal. Das betrifft auch Portfolio- und
Währungsbewertungen, nicht nur den Alarmservice. Diesen Schutz zusammen mit dem ersten produktiven Bestätigungsgrad
ausliefern.

`confirmation_level = NULL` darf nicht pauschal „vertrauenswürdig“ bedeuten. A2 fasst darunter eigene Konnektoren,
Benutzerwerte und Importe zusammen; bei der Migration können zusätzlich Altwerte unbekannter Herkunft auftreten.
Quellart und Vertrauensentscheidung ausdrücklich speichern. Selbst eingetragene Werte können lokal autorisiert
sein, werden dadurch aber nicht zu unabhängig überprüften Marktdaten.

### B6 — Signaturprüfung und importierter Inhalt müssen exakt zusammenpassen

**Blocker — A2 §7, §9 G2 und §16.**

Die bestehenden Kurs-DTOs sollen signierte Nutzdaten zusätzlich erhalten. Es muss feststehen, welcher Inhalt
autorisiert ist. Andernfalls lässt sich eine gültige Signatur über Kurs 100 prüfen und anschliessend der parallele
DTO-Wert 1000 speichern. Das ist eine Lücke der Spezifikation, kein Nachweis einer bereits existierenden
Signaturimplementierung.

**Vorschlag:** Im signierten Modus Instrument, Zeitpunkt, sämtliche Werte und Revision ausschliesslich aus dem
verifizierten kanonischen Payload ableiten. Doppelte Felder müssen entweder entfallen oder exakt übereinstimmen;
bei Abweichung abweisen. Die Bindung an Anfrage beziehungsweise Push-Berechtigung ebenfalls anhand dieser
verifizierten Daten prüfen. Signieren eigener Werte muss atomar zur gespeicherten Beobachtung erfolgen.

Das Format benötigt Regeln für doppelte JSON-Schlüssel, Unicode, Escapes, unbekannte Felder, negative Null,
nicht endliche Zahlen und Überläufe. Das Beispiel `"124.0"` widerspricht bereits der Regel „ohne abschliessende
Nullen“. `DataHelper.round` definiert noch keine vollständige Bytekodierung. Die Darstellung muss mit den
projektüblichen `double`-Werten auskommen; eine Umstellung des Datenmodells auf `BigDecimal` ist dafür nicht nötig.

Als Referenz ist [RFC 8785](https://www.rfc-editor.org/rfc/rfc8785.html) hilfreich. A2s feste Schemafeldreihenfolge,
NFC-Normalisierung und Zahlenstrings sind jedoch ein eigenes Format und dürfen nicht ungeprüft als JCS bezeichnet
werden. Positive und negative Testvektoren müssen dieses Format vollständig festlegen.

### B7 — Vergleichbarkeit kommt vor Abweichungsbewertung

**Hoch — A2 §7, §9, §10 und §11.2.**

`ISIN:CURRENCY` identifiziert keinen eindeutigen Börsenplatz und keine Kursart. Zwei Kurse innerhalb von 15 Minuten
können bei einem schnellen Markt beide richtig und deutlich verschieden sein. GBp/GBP, Roh-/adjustierte Reihen,
Bid/Ask/Last, Stückpreis/Prozentnotierung und unterschiedliche Sitzungen dürfen keine Manipulationsvorwürfe erzeugen.
Die älteren Konzepte behandeln mehrere dieser Voraussetzungen ausdrücklicher.

**Vorschlag:** Die signierte Beobachtung und die lokale Instrumentzuordnung müssen die Vergleichssemantik eindeutig
festlegen: Listing/Handelsplatz soweit relevant, Währung und Einheit, Kursart, Anpassungszustand und Handelsdatum
beziehungsweise Ereigniszeit. Ist die Normalisierung nicht belastbar möglich, bleibt die Prüfung INCONCLUSIVE.
Ein syntaktisch gültiger `adjustment`-Wert ersetzt diese Prüfung nicht.

Kalenderregeln müssen zum Instrument passen. „Währungspaare Montag bis Freitag“ widerspricht dem gleichzeitig
vorgesehenen Kryptopaar-Betrieb. Illiquide Titel und Handelsunterbrüche können legitime ältere Beobachtungen haben;
Staleness ist dann fehlende Aktualität, nicht automatisch ein Sendervergehen. Kalenderlücken dürfen nicht eine
ganze Quelle sanktionieren. Die Annahme von Werten während oder ausserhalb einer Sitzung benötigt eigene Regeln
für laufenden Handel und abgeschlossene Schlusskurse.

### B8 — Ein einzelner lokaler Konnektor darf nicht ungeprüft alle anderen ersetzen

**Hoch — A2 §11.2, §12 und §13.1.**

A2 ersetzt bei einer Stichprobenanomalie sofort den importierten Wert durch `v_ref`. Ein falsch konfigurierter,
veralteter oder selbst fehlerhafter Konnektor kann dadurch korrekte Daten überschreiben und ehrliche Ursprünge
sanktionieren. Das gilt insbesondere bei verschiedenen Handelsplätzen und Kurseinheiten.

**Vorschlag:** Zunächst den betroffenen Kandidaten zurückhalten beziehungsweise seine Verwendbarkeit entziehen.
Ein automatischer Ersatz braucht eine vergleichbare, ausreichend vertrauenswürdige Referenz und einen eigenen
Prüfnachweis. Für folgenreiche wirtschaftliche Vorwürfe eine zweite unabhängige Referenz oder manuelle Klärung
verlangen. Lokale Direktquellen können bewusst bevorzugt werden; diese lokale Vertrauensentscheidung muss als
solche erkennbar bleiben.

Auch die Dreierregel bis GESPERRT braucht deduplizierte wirtschaftliche Ereignisse, nicht einfach drei Abrufe
desselben falschen Referenzwerts. Datensatzschutz darf sofort wirken; eine automatische Ursprungssperre sollte
zunächst befristete Quarantäne mit definiertem Wiederaufnahmeverfahren sein. Dauerhafte Sperren bleiben für
belastbare Protokollbeweise oder eine bewusste administrative Entscheidung reserviert.

### B9 — Rückruf braucht vollständige Abhängigkeiten und Wiederanlaufsicherheit

**Blocker — A2 §13 und §17.**

Der Ansatz mit `HoldDailyTotalMarker` ist richtig. Die Auswahl nur über gehaltene Wertpapiere reicht jedoch nicht:
Bewertungen hängen auch von Währungskursen für Wertpapier- und Barbestände ab. Die Named Query
`HoldSecurityaccountSecurity.getPeriodHoldingsByTenant` verwendet entsprechende Historyquote-Verknüpfungen;
[jpa-named-queries.properties](../backend/grafioschtrader-server/src/main/resources/META-INF/jpa-named-queries.properties),
Zeile 104. Der Tageswertservice benutzt diese Periodenabfragen.

Weitere Lücken: `securitycurrency` erhält in A2 §17.2 keinen Verweis auf den signierten Lastpreis-Record und keine
Bestätigungsflags. Der „letzte eigene Wert“ wird nicht als wiederherstellbarer Stand modelliert. Ein Ersatz nur
von `s_last` kann die übrigen OHLCV-Felder und den Zeitstempel inkonsistent zurücklassen. Daten, die vor H8 importiert
wurden, lassen sich durch nachträgliches Hinzufügen der Spalte nicht rückwirkend einem Peer zuordnen.

**Vorschlag:**

- Die Sperre muss sofort auch Lesen, Weitergabe und neue Importe betroffener Werte verhindern. Die asynchrone
  Reparatur darf diese Wirkung nicht bis zu ihrem Abschluss verzögern.
- Originalbeobachtungen, ersetzte Versionen und Ersatzbelege erhalten; Last-Preise als zusammengehörigen Datensatz
  behandeln oder Feldherkunft führen. Fehlt ein sicherer Ersatz, „nicht verfügbar“ statt erfundener Rückfallwerte.
- Betroffene Wertpapier-, FX-, Barbestands- und sonstige abgeleitete Auswertungen erfassen. Wenn eine genaue
  Abhängigkeitsermittlung zunächst fehlt, konservativ alle möglicherweise betroffenen Mandanten neu berechnen.
- Sperrentscheidung und dauerhaften Reparaturauftrag atomar speichern. Der Worker arbeitet mit Fortschritt,
  begrenzten Batches und Wiederholungsschutz und überschreibt keine zwischenzeitliche korrekte Neuversion.
- Originale, Bestätigungen und Sperrbeweise so lange behalten, wie aktive Werte oder Entscheidungen davon abhängen.
  Eine pauschale zwölfmonatige Löschung der Prüfbelege passt nicht zu jahrzehntelang genutzten Historien.

Ein Rückruf kann gespeicherte Kurse und berechenbare Ergebnisse reparieren. Bereits zugestellte Alarme oder
Entscheidungen eines Benutzers lassen sich dadurch nicht rückgängig machen; betroffene Signale brauchen einen
sichtbaren Korrekturhinweis.

### B10 — Equivocation muss echte Konflikte von normalen Kursänderungen unterscheiden

**Hoch — A2 §7.1, §7.3, §7.5 und §12.**

Last-Preise werden sekundengenau kodiert. Zwei normale Trades innerhalb derselben Sekunde können verschiedene
Last-Werte haben und würden nach der Definition zur sofortigen Sperre führen. Historische Blöcke derselben
Revision können schon durch ein anderes `createdAt` verschiedene Bytes haben, wenn sie nach einem Neustart neu
erzeugt werden. Ein gültiges Signaturpaar beweist dann zwei Aussagen, aber ohne eindeutiges Ereignismodell noch
keinen wirtschaftlichen Betrug.

**Vorschlag:** Eine eindeutige Beobachtungskennung beziehungsweise monotone Sequenz und explizite Korrekturbezüge
festlegen. Eine bereits signierte Version bleibt unveränderlich und wird wiederverwendet. Revisionen müssen
Neustart und Wiederherstellung überstehen; Konflikte nach einem Restore müssen erkannt werden. Derselbe verbindliche
Ereignisschlüssel mit widersprüchlichem Inhalt kann eine sofortige Quarantäne rechtfertigen. Unterschiedliche
Beobachtungen oder angekündigte Korrekturen dürfen nicht so behandelt werden.

Die Monatsblöcke brauchen ausserdem eindeutige Tage, Grenzen für Revisionen, Schutz vor Regression und Regeln für
Entfernung irrtümlicher Zeilen. Ein vollständiger signierter Monatsblock darf als Beweis mitgeliefert werden,
während nur angefragte Tage importiert werden. Sonst kollidiert §7.3 mit H4 „nur angefragte Datumsbereiche“.

### B11 — Stichproben und CUSUM liefern Erkennung, keine Annahmegarantie

**Hoch — A2 §5 H7, §9 G6 und §11.**

Eine geheime Stichprobenauswahl ist sinnvoll. Bei 5 % Prüfquote wird ein einzelner gezielter falscher Record aber
zunächst mit 95 % Wahrscheinlichkeit nicht ausgewählt. Unter unabhängiger Auswahl und einer stets brauchbaren
Referenz beträgt die Wahrscheinlichkeit mindestens einer Prüfung nach `n` falschen Records `1 - 0.95^n`;
erst bei 59 Records überschreitet sie 95 %. Eine Prüfung bedeutet zudem noch keine sichere Erkennung.

Beim Erstimport erkennen sechs zufällig ausgewählte Monate einen einzelnen vergifteten Monat in zehn Jahren
Historie nur mit 6/120 = 5 % Wahrscheinlichkeit, sofern gleichverteilt ohne Zurücklegen ausgewählt wird und die
Vergiftung bei Prüfung erkennbar wäre. Die anderen Monate dürfen deshalb nicht automatisch als bestätigt gelten.

Die Stichprobeneinheit ist unklar: `recordHash` bezeichnet historische Monatsblöcke, Reifekriterien sprechen von
Werten. Re-Signierung, zusätzliche Revisionen und wiederholter Empfang dürfen keine zusätzlichen unabhängigen
Erfolge erzeugen. Eine Auswahl am stabilen wirtschaftlichen Ereignis und eine getrennte Inhaltsprüfung vermeiden
dieses Problem; Auditresultate brauchen Deduplizierung.

CUSUM ist als Warninstrument interessant. Die gemeinsame Summe pro Ursprung und Anlageklasse lässt sich jedoch
durch viele unauffällige Werte zwischen gezielten Manipulationen abbauen. Normale Differenzen zwischen Anbietern
können andererseits dauerhaften Bias erzeugen. **Empfehlung:** zusätzlich pro Instrument beziehungsweise homogener
Vergleichsgruppe beobachten, Ereignisse deduplizieren und Referenzwechsel markieren. Fehlalarmrate und
Erkennungsverzögerung an unabhängigen Testdaten kalibrieren. Die Auswahl von Referenzwert und Entscheidungsgrenze
sollte sich an gewünschter Laufzeit bis Fehlalarm beziehungsweise Erkennung orientieren; siehe
[NIST: CUSUM Average Run Length](https://www.itl.nist.gov/div898/handbook/pmc/section3/pmc3131.htm).

Beim Sprungtest müssen Prozentwerte vor Verwendung im Log-Renditeraum eindeutig umgerechnet werden. Historie für
`σ₆₀` benötigt selbst ausreichende Qualität und eine konsistente Splitbereinigung; sonst kann manipulierte
Referenzhistorie spätere Gates schwächen. Der Test bleibt eine Heuristik. „Unter 0,1 % zurückgehaltene echte Werte“
allein ist kein Sicherheitsziel: Eine sehr weite Grenze erreicht das leicht und lässt zugleich Angriffe passieren.

### B12 — Identität, Pinning und Schlüsselverlust sind noch nicht vollständig geregelt

**Hoch — A2 §6, §7.4 und §19 T9.**

Ein Schlüsselwechsel mit zwei Signaturen belegt Kontinuität und Besitz beider Schlüssel. Hat ein Angreifer den
alten privaten Schlüssel gestohlen, kann er selbst einen neuen erzeugen und beide Signaturen liefern. Rotation
ist deshalb keine Wiederherstellung nach Kompromittierung. Die Tabelle T9 darf Pin-Abweichung nicht als Schutz
gegen den weiterbenutzten gestohlenen Schlüssel darstellen: Dieser passt weiterhin zum Pin.

**Vorschlag:** Reguläre Rotation und Wiederherstellung nach Schlüsselverlust getrennt behandeln. Rotationen sind
monoton, idempotent und konfliktgeprüft; sie übertragen Gruppenzugehörigkeit und Einschränkungen, niemals zusätzliche
Rechte. Alte und neue Kennung bleiben für Sperre, Herkunft und Rückruf verknüpft. Wiederherstellung benötigt
ausserhalb des kompromittierten Schlüssels geprüftes Vertrauen, etwa manuelle Neuzulassung oder einen vorab
festgelegten separaten Wiederherstellungsschlüssel. Eine zentrale Liste benötigt dafür ebenfalls ein belastbares
Verfahren zur Prüfung des meldenden Betreibers.

Das Pinning muss den bereits bekannten Peer an seine erwartete Identität binden. Die Suche im Pseudocode nur nach
der vom Gegenüber neu behaupteten GTI genügt nicht als ausdrückliche Kontinuitätsprüfung des bisherigen Peers.
Handshake und Antwort sollen Modus, Version, Rollen, Ziel und beteiligte Identitäten eindeutig an den Austausch
binden. Replay-Zustand muss auch parallele Anfragen, Neustarts und verzögerte manuelle Freigaben berücksichtigen;
danach gegebenenfalls einen frischen Besitznachweis verlangen. Diese Gestaltungsfragen entsprechen den
Abdeckungs- und Replay-Risiken aus [RFC 9421, Abschnitt 7](https://www.rfc-editor.org/rfc/rfc9421.html#section-7).

Auf Hop-Quittungen kann A2 für rein lokale Senderentscheidungen verzichten, wenn authentifizierter Transport,
geschützte Tokens und eine eindeutige Zuordnung der Sitzung zur gepinnten Identität gewährleistet sind. Dann gibt
es bewusst keinen portablen Zustellbeweis. Eine spätere netzweite Bestrafung eines Relays dürfte nicht allein auf
einer lokalen Empfangsbehauptung beruhen.

### B13 — Eine signierte Sperrliste ist eine Autorität mit erheblicher Wirkung

**Hoch — A2 §14.**

Die statische Verteilung ist betrieblich attraktiv. Sie entfernt aber nur den zentralen Online-Dienst, nicht
die zentrale Vertrauensentscheidung. Ein Herausgeber kann zwar keine fremde Kurssignatur fälschen, durch Sperren
und Rückrufe jedoch grosse Teile des Datenbestands unbrauchbar machen.

Ungeklärt sind insbesondere die Bereitstellung des zum Fingerabdruck passenden öffentlichen Schlüssels, seine
Rotation und Kompromittierung sowie die Bedeutung einer entfernten Listenzeile. A2 §12 erwähnt Entsperrung durch
Entfernung, während §14 sagt, der Herausgeber könne nur Sperren hinzufügen.

**Vorschlag:** Vollständige versionierte Snapshots definieren. Eine Entfernung hebt nur den Sperrgrund dieser
Liste auf; lokale oder andere Listensperren bleiben bestehen. Eine Ausnahme gilt nur für den bezeichneten Grund.
Gleiche Sequenz mit gleichem Inhalt ist ein Duplikat, gleiche Sequenz mit anderem Inhalt ein Konflikt; niedrigere
Sequenzen werden abgewiesen. Bootstrap, Höchststände nach Backup-Restore, abgelaufene Listen und
Herausgeberschlüsselwechsel ausdrücklich behandeln. Abruf, Parser und Listenlänge bleiben begrenzt.

Die notwendigen Muster für Versionen, Ablauf und Schlüsselwechsel lassen sich aus
[The Update Framework](https://theupdateframework.github.io/specification/latest/) übernehmen, ohne dessen
gesamte Infrastruktur nachzubauen. Eine Sperrliste sollte zunächst ein bewusster lokaler Bezug sein, keine
unbemerkte Bindung an einen Projektbetreiber. Für besonders weitreichende Sperrlisten sind mehrere getrennte
Herausgeberschlüssel beziehungsweise eine Schwellenfreigabe erwägenswert.

„Admin setzt URL“ bedeutet ausserdem nicht „keine SSRF-Fläche“. Es reduziert den Kreis möglicher Auslöser.
HTTPS, Zielprüfung, Redirectregeln, DNS-/IP-Prüfung sowie Antwort- und Zeitlimits bleiben erforderlich.

### B14 — Ressourcenverbrauch kann die Sicherheitsprüfung selbst ausschalten

**Hoch — A2 §8, §11, §16 und §17.**

Jede neue gültige GTI soll einen Ursprungseintrag erzeugen. Ein zugelassener Relay kann sehr viele korrekt signierte
NEU-Ursprünge und Records liefern. Ein einzelnes Grössenlimit und 50 Hinweise pro Peer begrenzen weder den gesamten
Speicher noch die ausgelösten Konnektorabrufe. Mehrere Hinweise können wiederholt 100-%-Prüfung provozieren.

**Vorschlag:** Globale und peerbezogene Budgets für neue Identitäten, Staging-Zeilen/-Bytes, Signaturprüfungen,
Revisionen, ausgehende Vergleichsabrufe und Hinweise festlegen. Deduplizierung und negative Caches begrenzen
wiederholte Arbeit; knappe Verifikationsbudgets dürfen niemals zur automatischen Annahme ungeprüfter Werte führen.
Zunächst nur minimale, begrenzte Daten über unbekannte Ursprünge speichern.

Die mögliche Ausnahme von `entity_limit` für Admin-/Systemdaten ersetzt solche Netzwerklimits nicht. Ebenso ist
die Aussage „ohne Benutzerbezug“ für alle neuen Tabellen zu weit: `gt_net_origin_audit` enthält `actor_id_user`.
Export-/Löschbehandlung ist je Entität zu begründen, einschliesslich möglicher Benutzerreferenzen, nicht pauschal.
Für einen echten 30-Tage-Verletzungszähler braucht es zeitbezogene Ereignisse oder Zeitbuckets; der vorhandene
Byte-Zähler aus Abschnitt 2 ist dafür ungeeignet.

### B15 — Einführung, Migration und Beobachtungsmodus müssen zusammenpassen

**Hoch — A2 §5, §12, §17 und §21.**

P0 verlangt schon Gate-Annahme, Herkunft und Erstimport-Bestätigung; Gate und Konsens folgen jedoch in P1 und P4.
P3 führt signierte Daten ein, der Rückruf kommt erst P6, der Alarmfilter P8. Damit sind die Phasen nicht in der
beschriebenen Form unabhängig auslieferbar. H2 „Anfragen schreiben nie“ hat ausserdem bereits eine Pool-Ausnahme;
fachlich muss klar sein, dass dort ein gesonderter Importpfad mit denselben Schutzregeln gemeint ist.

Der Beobachtungsmodus ist widersprüchlich: Die Zustandstabelle nennt sofortige Übergänge zu VERDÄCHTIG, der
nachfolgende Text nur simulierte Übergänge. Gleichzeitig bleiben die sofortige Ersetzung bei Anomalien und die
statistischen Gate-Prüfungen wirksam. Das kann Daten verändern, obwohl der Betreiber „Beobachtung“ erwartet.

**Vorschlag:** Drei Wirkungen getrennt schalten und dokumentieren: zwingende Protokoll-/Strukturprüfung,
wirtschaftliche Annahmepolicy und simulierte/aktive Sanktionen. Historische Altbestände erhalten den Status
„Herkunft unbekannt“; unsignierte Daten dürfen weder nachsigniert noch durch `NULL` automatisch aufgewertet werden.
Signierte und unsignierte Peer-Verbindungen brauchen beim Moduswechsel eine beidseitige Deaktivierungs- und
Neuhandshake-Regel. Persistierende alte Tokens dürfen die Netztrennung nicht umgehen.

Die konkrete Wiederverwendung numerischer Nachrichtencodes verbietet nur eine unverhandelte Vermischung der
Protokolle. Sie beweist keine fachliche Unvereinbarkeit der Konzepte. Auch gleiche `gti1`-Kennungen garantieren
keine direkte A1/A2-Kompatibilität: Domain-Tags, Payloads und Schlüsselübergänge benötigen eine explizite Migration.

### B16 — Weitere Datenmodell- und Betriebsdetails

**Mittel — A2 §5 H6, §7 und §17.**

- `gt_net_signed_record` enthält Instrument-, Preisart- und Monatssemantik. Die Platzierung in der Bibliothek ist
  nur dann sauber, wenn deren Kern wirklich generisch bleibt; Preisinterpretation gehört in die Anwendung.
- Die höchsten historischen Revisionen sowie Schlüsselübergänge und Sperrbeweise benötigen dauerhafte
  Fortsetzungsinformationen. Nur aktuell referenzierte Records aufzubewahren kann Replay-/Konflikterkennung schwächen.
- Ablehnen, ungeklärt Zurückhalten, Ausfall einer Referenz und erfolgreiche Bestätigung sind verschiedene Ergebnisse.
  Der Lieferantenscore darf einen ehrlichen Anbieter nicht für den Ausfall des lokalen Konnektors bestrafen.
- Mehrfachbezug kostet abhängig von Ursprung, Monat, Revision, Cache und Ausfällen mehr als pauschal eine zusätzliche
  Anfrage. Last-Preis-Signaturen und wachsende Monatsrevisionen benötigen Kapazitätsmessungen und Wiederverwendung.
- Fehlender Referenzkurs, fehlender Börsenkalender, Datumsduplikate, ganze Monatsblöcke und Überläufe brauchen
  explizite Ergebnisse. Ein globales „Wert > 0“ darf nicht ungeprüft auf Volumen oder alle unterstützten
  Instrumentarten angewandt werden.

## 4. Vergleich der drei Ansätze

Die Tabelle bewertet die beschriebenen Entwürfe, nicht hypothetische Erweiterungen und nicht bereits ausgelieferte
Funktionen. „Stärker“ bei Zulassung bedeutet eine zusätzliche organisatorische Hürde, keine Garantie ehrlicher Betreiber.

| Kriterium | Zentral | A1 | A2 |
|---|---|---|---|
| Prävention plausibler falscher Werte vor Verwendung | Schwerpunkt auf nachgelagerter Prüfung | Schwerpunkt auf Herkunft und nachgelagerter Prüfung | Bester Ansatz durch Gate/Staging; Standards und Konsensregel müssen verschärft werden |
| Beweis der ursprünglichen Aussage | Optionaler Ausbau | Verpflichtende Ursprungssignatur | Verpflichtende Ursprungssignatur |
| Übertragbarer Beweis der Lieferung durch einen Relay | Schwerpunkt auf unmittelbarer Lieferantenverantwortung | Signierte Hop-Quittung | Bewusst nicht vorgesehen |
| Zulassung/Wiederkehr unter neuer Identität | Gemeinsame manuelle Zulassung und Limits | Lokale Peer-Zulassung; freie Identitätserzeugung | Zusätzliche Ursprungsfreigabe; AUTO_NACH_REIFE bleibt angreifbar |
| Vorsicht bei wirtschaftlichen Sanktionen | Beobachtung, mehrere unabhängige Referenzen und gestufte Sanktionen | Entsprechende lokale Vorsicht und Rollentrennung | Einfacher, aber teilweise zu aggressiv |
| Rückruf und Neuberechnung | Kein entsprechend ausgearbeiteter Schwerpunkt | Kein entsprechend ausgearbeiteter Schwerpunkt | Klarer Fortschritt; Beleg- und Abhängigkeitsmodell fehlt noch |
| Gemeinsamer Widerruf | Zentrale Autorität mit aktualisiertem Status | Lokale Entscheidungen und begrenzte Hinweise | Optionale veröffentlichte Sperrliste; Verteilung und Aktualität bleiben begrenzt |
| Betrieb | Höchster Aufwand durch zentralen Dienst und HA | Kein zentraler Dienst; komplexe Evidenz-/Vertrauenslogik | Kein obligatorischer Dienst; erheblicher lokaler Aufwand für Gate, Mehrfachbezug und Rückruf |
| Passung zum Hauptziel „Kursvergiftung verhindern“ | Zulassung ist wertvoll, allein aber unzureichend | Gute Herkunftsbasis, allein aber unzureichend | Bevorzugte Grundlage nach Behebung der Blocker |

**Meine Gewichtung:** Ein wirksames Import- und Verwendungsmodell hat Vorrang vor einer ausgefeilten Reputation.
Die sorgfältigere Vergleichs- und Sanktionierungssemantik aus A1 sollte dabei erhalten bleiben. Die zentrale
Variante bleibt attraktiv, wenn eine gemeinsame Zulassung und schnelle gemeinsame Sperrentscheidungen gewünscht
und dauerhaft betreibbar sind. Auch sie braucht Gate, Belege und Rückruf auf den einzelnen Instanzen.

## 5. Empfohlener Lösungsweg und weitere Ansätze

### 5.1 A2-Kern mit strengem Annahmeprofil

Für eine erste sichere Variante empfehle ich:

1. Bestehende Empfangspfade härten: Zeit, Instrumentbindung, Bereiche, Empfangsflags und begrenzte Schreibwirkung.
2. Signierte Originalbeobachtungen, manuelle Ursprungsfreigabe und einheitliches Staging einführen.
3. Vor produktiver Nutzung entweder eine ausdrücklich vertrauenswürdige lokale Referenz oder eine tragfähige
   unabhängige Mehrquellenbestätigung verlangen. Fehlende Bestätigung führt zu fehlenden beziehungsweise
   sichtbar vorläufigen Daten; sie darf nicht stillschweigend in Vertrauen umgewandelt werden.
4. Bestätigungen samt Abhängigkeiten speichern und zusammen mit Rückruf, Neuberechnung und Alarmfilter ausliefern.
5. CUSUM und Sanktionen zunächst beobachten; Hinweise und Sperrlisten erst nach Stabilisierung ergänzen.

Historische Mehrfachprüfungen können einmalig erfolgen und danach über unveränderte Record-Hashes und vorhandene
Belege wiederverwendet werden. Das spart Abrufe, ohne aus Stichproben eine fiktive Vollbestätigung zu machen.

Für Last-Preise ist der Zielkonflikt härter: Ohne eigene Referenz oder vergleichbare unabhängige Zweitquelle gibt
es keine kostenfreie starke Integritätsgarantie. Die bewusste Alternative ist ein explizit gelockerter Modus mit
sichtbarer Unsicherheit, nicht ein Zeitlimit, nach dem Unsicherheit verschwindet.

### 5.2 Kleiner zugelassener Quellenkreis statt offener Reputationsökonomie

Für ein zunächst kleines GTNet kann ein begrenzter Kreis bekannter Betreiber und Datenquellen wirksamer und
einfacher sein als automatische Reife und globale Scores. Die Betreiberzulassung bleibt eine menschliche
Vertrauensentscheidung; Technik setzt anschliessend die Datensatzregeln durch. Das verhindert keinen
kompromittierten zugelassenen Server, begrenzt aber die einfache Vervielfachung unabhängiger Stimmen.

Dieser Kreis kann lokal gepflegt oder durch eine signierte, ausdrücklich abonnierte Zulassungsliste unterstützt
werden. Eine solche Liste ist eine zusätzliche Autorität und sollte von einer Sperrliste getrennte Befugnisse
haben. Ihre Aufnahme eines Betreibers bestätigt weiterhin keinen einzelnen Kurs.

### 5.3 Zentralität als austauschbare Zulassungsschicht

Importprüfung und Zulassung müssen nicht dieselbe Architekturentscheidung sein. Ein gemeinsamer Kern aus
signierten Beobachtungen, lokalen Prüfungen und Rückruf kann mit lokaler Freigabe beginnen. Später kann ein
Zertifikatsaussteller bestehende Schlüssel zertifizieren. Dies ist eine neue kombinierte Variante und benötigt
eine saubere Protokollversionierung; sie ist keine automatische Kompatibilität der vorliegenden Entwürfe.

Ein Blockchain- oder allgemeines Konsenssystem würde die zentrale Frage nach korrekten Eingangspreisen nicht
auflösen. Es könnte höchstens Aussagen und deren Reihenfolge festhalten. Als spätere Ergänzung wäre ein
Transparenzprotokoll signierter Revisionen eher gezielt nützlich, falls tatsächlich viele widersprüchliche
Aussagen untersucht werden müssen. Für Version 1 rechtfertigt das den zusätzlichen Betrieb nicht.

## 6. Sinnvolle Reihenfolge und überprüfbare Abnahmekriterien

Die folgende Reihenfolge ist eine Empfehlung für eine spätere Umsetzung, keine Festlegung auf A2 ohne die genannten
Korrekturen. Alte Phasennummern werden bewusst nicht als bereits unabhängig lieferbar vorausgesetzt.

| Lieferung | Inhalt | Abnahme |
|---|---|---|
| 1: Unmittelbare Härtung | Zeitgrenzen vor Neuanlage und Update, Instrument-/Bereichsbindung, Empfangsflags, minimale lokale Herkunft, Parser- und Ressourcenlimits | Falsches Instrument, Zukunftszeit und deaktivierter Empfang verändern keine produktiven Werte; auch Erstimport und Anfragen sind abgedeckt. |
| 2: Prüf- und Herkunftsbasis | Identität, Signaturen, Gate, Staging, Herkunft unbekannter Altbestände, Bestätigungsbelege und manuelle Zulassung; neue Daten zunächst zurückgehalten | Gleiche Bytes ergeben gleiche Ergebnisse; parallele DTO-Werte können keine gültige Signatur missbrauchen; keine implizite Freigabe durch Timeout. |
| 3: Sichere Verwendung und Reparatur | Korrigierte Mehrquellenregel, lokale Referenzpolitik, kontrollierte Übernahme, Abhängigkeiten, Rückruf und Schutz aller Auswertungen | Bestätigungen verlieren beim Wegfall von Quellen korrekt ihre Wirkung; gesperrte Daten werden sofort ausgeschlossen und wiederanlauffähig repariert. |
| 4: Beobachtung und begrenzte Durchsetzung | Deduplizierte Stichproben, kalibrierte Drift, getrennte simulierte/aktive Sanktionen | Messbare Fehlalarmrate und Erkennungsverzögerung, begrenzte Last und keine Datenänderung allein durch simulierte Sanktionen. |
| 5: Optionale gemeinsame Warnungen | Hinweise, Sperrlisten, Herausgeber- und Wiederherstellungsabläufe | Replay, Listenkonflikte, Schlüsselverlust, Ausfall und lokale Ausnahmen haben eindeutige Ergebnisse. |

Für die spätere Implementierung gehören mindestens folgende Szenarien in die Abnahme:

| Szenario | Erwartetes Ergebnis |
|---|---|
| Freigegebene Quellen 90/110, NEU-Kandidat 100 | Kein Konsens. |
| Derselbe Ursprung über drei Relays oder zwei rotierte Kennungen | Höchstens eine unabhängige Stimme. |
| Zwei ausreichend unabhängige, eng übereinstimmende Quellen | Bestätigung mit gespeicherten konkreten Belegen; nur geprüfte Felder werden aufgewertet. |
| Zwei widersprüchliche Quellengruppen | Ungeklärt; keine opportunistische Auswahl der passenden Gruppe. |
| NEU-/VERDÄCHTIG-Wert nach 60 Minuten, Referenz ausgefallen | Keine automatische autoritative Übernahme. |
| Gültiger signierter Wert 100, paralleles DTO mit 1000 oder anderem Instrument | Abweisung; keinerlei Speicherung des ungebundenen Werts. |
| Zukunftszeit beim erstmaligen Pool-Eintrag, nicht nur beim Update | Abweisung auch ohne vorhandenen Vergleichswert. |
| Echter Kurssprung, Split, GBp/GBP, anderes Listing, Krypto-Wochenende | Fachlich passende Normalisierung oder ungeklärtes Ergebnis; kein pauschaler Manipulationsvorwurf. |
| Zwei legitime Beobachtungen in derselben Sekunde | Keine falsche Equivocation-Sperre. |
| Re-Signierung, Retries, Monatsrevisionen desselben Ereignisses | Keine künstliche Erhöhung von Reife-, Anomalie- oder CUSUM-Zählern. |
| Sperre einer notwendigen Konsensstütze | Bestätigung neu bewerten; keine Herkunftsumschreibung zur Rettung des Grades. |
| Sperre während gleichzeitigem Import beziehungsweise während eines Worker-Abbruchs | Sofortige Verwendungssperre; Wiederanlauf ohne Verlust späterer korrekter Werte. |
| Korrektur eines FX-Kurses für einen Mandanten mit Barbestand | Betroffene Bewertung und Tageswerte werden invalidiert und neu berechnet. |
| Manipulierte Historie bei korrektem aktuellem Last-Preis | Historienabhängiger Alarm wird unterdrückt beziehungsweise als unbestätigt behandelt. |
| Tausende neue Ursprünge und wiederholte Hinweise über einen Peer | Speicher-, CPU- und Abrufbudgets bleiben begrenzt; keine Freigabe wegen Budgetmangels. |
| Gleiche Sperrlistensequenz mit anderem Inhalt; Restore mit altem Höchststand | Konflikt beziehungsweise ausdrücklich behandelter Wiederherstellungsfall, kein stiller Rollback. |
| Keine Referenz verfügbar | Sichtbare Unsicherheit; keine Bestätigung und keine Anomalie allein wegen des Ausfalls. |

Die Kalibrierung sollte neben ehrlichen Daten gezielte Einzelwertangriffe, langfristige kleine Verschiebungen,
gemeinsame Anbieterfehler und selektive Ausfälle enthalten. Auswerten sollte man vor allem akzeptierte falsche
Werte, zurückgehaltene richtige Werte, Zeit bis Entdeckung, Zeit bis vollständiger Reparatur und Prüfkosten.
„Mehrere Wochen ohne simulierte Sperre“ allein belegt noch keine wirksame Angriffserkennung.

## 7. Entscheidungsempfehlung

**A2 weiterverfolgen; vor der Umsetzung die Blocker B1, B2, B4, B5, B6 und B9 schliessen.** Die Punkte zu
Vergleichssemantik, Ereignisidentität, Schlüsselkontinuität und begrenztem Ressourcenverbrauch gehören ebenfalls
in den ersten tatsächlich sicher nutzbaren Stand, nicht in eine unverbindliche spätere Erweiterung.

Bei der zentralen Variante würde ich mich nicht wegen der Hoffnung auf „wahre Preise durch zentrale Identität“
entscheiden. Ihr Mehrwert liegt in gemeinsamer Zulassung und Widerruf. Bei A1 überzeugt mich die vorsichtige
Beweis- und Sanktionierungslogik. Bei A2 überzeugt mich die Kontrolle des gesamten Wegs bis zur Verwendung und
Reparatur. Diese Stärken lassen sich fachlich zusammenführen.

Der entscheidende Satz für das überarbeitete Konzept sollte lauten: **Ein Wert wird erst entsprechend der lokal
festgelegten Vertrauensanforderung verwendet; fehlt diese Grundlage oder fällt sie später weg, bleibt seine
Verwendung begrenzt und seine Herkunft nachvollziehbar.** Daraus ergeben sich klarere Regeln als aus dem
Versprechen, Signaturen, Median und Stichproben könnten jeden falschen Kurs verhindern.
