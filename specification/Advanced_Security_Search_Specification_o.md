# Einschätzung zur erweiterten Instrumentensuche

**Code baseline:** backend 0.39.4, höchstes Flyway-Skript `V0_39_4__cost_basis_method.sql`.

Geprüft am 12.11.2026 anhand der Ausgangsspezifikation und des aktuellen Arbeitsbaums.
Dieses Dokument enthält die angefragten Review-Anmerkungen; die Ausgangsspezifikation bleibt unverändert.

## 1. Gesamturteil

Die Grundidee ist sinnvoll und gut umsetzbar: „Erweiterte Suche +“ fügt ein Kriterium hinzu, dessen Feldwahl
die passenden Eingaben bestimmt. Das vorhandene dynamische Formular und die UDF-Metadaten bieten dafür eine
brauchbare Grundlage. Für gespeicherte UDF muss das JSON-Datenmodell zunächst nicht umgebaut werden.

Die Spezifikation trifft mehrere gute Entscheidungen: serverseitige Feldfreigabe, UND-Verknüpfung,
typabhängige Eingaben, Ausschluss fehlender Werte und Wiederverwendung der vorhandenen Indikatorauswertung.
Auch die gemeinsame Erweiterung von `SecuritycurrencySearchBase` passt zum bestehenden Aufbau.

Vor einer Umsetzung würde ich jedoch die Datenabdeckung und einige Suchregeln verbindlich klären.
Insbesondere ist „Suche über alle sichtbaren Instrumente“ technisch etwas anderes als „für alle sichtbaren
Instrumente sind die benötigten Kennzahlen vorhanden“. Außerdem enthält der Entwurf konkrete Lücken bei
berechneten UDF, unvollständigen Eingaben und der Parameterübertragung.

Meine Empfehlung ist eine erste Lieferung mit gespeicherten UDF und sauberer Kriterienvalidierung.
Indikatoren sollten darauf aufbauen, nachdem ihre Datenabdeckung und der Aufwand einer breiten Suche geprüft sind.
Eine neue Suchindex-Tabelle oder eine vollständige Screener-Infrastruktur würde ich dafür noch nicht einführen.

## 2. Wesentliche Befunde vor der Umsetzung

### 2.1 Globale Suche bedeutet noch keine globale Kennzahlenabdeckung

**Priorität: hoch. Betrifft §§1, 2, 5 und 6.**

Die SQL-Suche kann grundsätzlich alle für den Mandanten sichtbaren Securities betrachten. Die Daten, nach
denen gesucht werden soll, werden aber teilweise nur für einen wesentlich kleineren Bestand gepflegt:

- `UDFFillScopeService.getTenantScope()` umfasst Securities in Watchlists oder Transaktionen des Heimatmandanten,
  zusätzlich eingeschränkt auf die für das UDF vorgesehenen Anlageklassen und Instrumenttypen.
- `UDFDataFill.getTenantScopeSecurityIds` setzt genau diesen Umfang in SQL um.
- `SecurityFundamental.findScopeIds` beschränkt den automatischen Fundamentalbestand auf aktive direkte Aktien,
  die in einer Watchlist oder Transaktion vorkommen; hier ist der Umfang instanzweit.
  `SecurityFundamental.deleteOutOfScope` entfernt Fundamentalwerte außerhalb dieses Umfangs.

Damit kann beispielsweise eine noch in keiner Watchlist und keiner Transaktion enthaltene Aktie in der
Namenssuche auftauchen, bei einem KGV-Filter aber wegen fehlender EPS-Werte ausscheiden. Bei persönlichen,
automatisch befüllten UDF ist die Einschränkung auf den eigenen Mandanten noch stärker. Gerade im Dialog zum
Hinzufügen bisher unbeachteter Instrumente ist das eine relevante Einschränkung.

**Empfehlung:** Die erste Fassung ausdrücklich als Suche über **vorhandene Werte** definieren und dies im Dialog
erklären. Die Suche startet keine Resolver, keine LLM-Aufträge und keine externen Datenabrufe. Bei
Indikatorauswertung nach Möglichkeit die Anzahl der wegen fehlender Eingabedaten ausgeschlossenen Kandidaten
anzeigen. Eine vollständige Kennzahlenversorgung aller sichtbaren Securities wäre ein eigener Ausbau mit
separaten Aktualisierungs-, Umfangs- und Kostenentscheidungen.

**Abnahmebeispiel:** Zwei sichtbare Aktien, von denen nur eine EPS-Daten besitzt. Beide sind per Name auffindbar;
beim KGV-Filter ist nur die auswertbare Aktie ein möglicher Treffer. Die fehlende Abdeckung wird erklärt.

**Codebelege:** [UDF-Füllumfang][q1], [SQL für UDF- und Fundamentalumfang][q2],
[Auswahl direkter Aktien im regulären Fundamentallauf][q15].

### 2.2 Nicht jedes angebotene UDF besitzt einen gespeicherten Wert

**Priorität: hoch. Betrifft §§4 und 5.**

Der vorgesehene Katalog enthält auch UDF mit Spezialtyp. Die Verfallrendite
`UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY` wird jedoch im aktuellen Code für die Anzeige berechnet:
`YieldToMaturityCalculator.calcAndSetYTM()` ruft `putValueToJsonValue(..., false)` auf.
Der letzte Parameter verhindert die Speicherung. `mayRunInBackground()` liefert ebenfalls `false`.
Die Watchlist lädt diesen Wert über `WatchlistReport.combineWatchlistWithUDF()` hinzu.
Das Basisdatenskript legt das Feld für Benutzer 0 mit `ui_order = 20` an; es fällt somit unter die
vorgesehene Katalogabfrage mit `ui_order < 100`, sofern der Benutzer es nicht deaktiviert hat.

Ein `EXISTS` über `udf_data` bildet diesen Anzeigewert daher nicht verlässlich ab. Ein auswählbares Feld
„Verfallrendite“ könnte trotz sichtbarer Werte in der Watchlist keine Suchtreffer ergeben.

**Empfehlung:** Suchbare Felder nach ihrer tatsächlichen Wertquelle behandeln:

| Wertquelle | Behandlung |
|---|---|
| Gespeichertes persönliches oder systemweites UDF | SQL-Filter auf `udf_data` |
| Nur zur Laufzeit berechnetes Spezial-UDF | Zunächst aus dem Suchkatalog ausschließen oder ausdrücklich über einen geeigneten Auswerter unterstützen |
| Gespeicherte Indikatordefinition | Gemeinsame Indikatorauswertung nach SQL-Vorauswahl |

Die Entscheidung darf nicht allein aus `UDFDataType` abgeleitet werden. Für die erste Lieferung würde ich
die Verfallrendite ausschließen. Ihre Neuberechnung für die Suche wäre eine bewusste Funktionserweiterung.

**Codebelege:** [Berechnung ohne Speicherung][q3], [Bedeutung des Persistenzparameters][q4],
[Ergänzung der Watchlist-Anzeige][q5], [Basisdefinition des Feldes][q16].

### 2.3 Unvollständige Kriterien dürfen die Suche nicht unbemerkt erweitern

**Priorität: hoch. Betrifft §§8.2, 8.3 und 9.**

Der Entwurf zählt bereits eine Feldauswahl als ausreichendes Suchkriterium und überspringt anschließend beim
Absenden Kriterien ohne Vergleichswert. Beispiel: Der Benutzer wählt „KGV“, trägt aber keine Grenze ein.
Die Schaltfläche wird aktiv; abgesendet wird eine Suche ohne KGV-Einschränkung. Zusammen mit einem anderen
Filter würde eine beabsichtigte Bedingung ebenfalls stillschweigend verschwinden.

**Empfehlung:** Eine vollständig leere Zeile bleibt neutral. Sobald ein Feld gewählt ist, muss die Zeile
vollständig und gültig sein, andernfalls bleibt „Suchen“ gesperrt und die fehlende Eingabe wird an der Zeile
angezeigt. Beim Absenden werden keine ausgefüllten, aber ungültigen Zeilen stillschweigend verworfen.

Zusätzlich festlegen:

- `0` und `false` sind gültige Eingaben; nur `null`, `undefined` oder eine tatsächlich leere Eingabe fehlen.
- Bei Feldwechsel werden alte Vergleichswerte gelöscht oder ausdrücklich passend neu initialisiert.
  Unsichtbare Werte eines vorherigen Datentyps dürfen nicht mitgesendet werden.
- Jede Zeile hat eine sichtbare Entfernen-Aktion. Dadurch lassen sich auch belegte Plätze wieder freigeben.
- Der Wechsel auf Währungspaare deaktiviert die Kriterien eindeutig; deaktivierte Kriterien gelangen nicht
  in den Suchauftrag. Beim Zurückwechseln ist festgelegt, ob die Eingaben wiederhergestellt werden.

Der vorhandene `atLeastOneFieldValidator` prüft lediglich auf einen wahrheitswertig belegten Control-Wert.
Er ersetzt keine Validierung einer vollständigen Kriterienzeile.

**Codebelege:** [Bestehendes Suchformular][q6], [Formularvalidator][q7].

### 2.4 Die vorhandene Parameterfunktion muss die Kriterienliste ausdrücklich auslassen

**Priorität: hoch. Betrifft §8.1.**

`AppHelper.getHttpParamsOfObjectAllowBooleanNullFields()` ignoriert Arrays nicht. Die Funktion iteriert
über alle eigenen Eigenschaften und verwendet `'' + val`. Eine nicht leere Liste von Kriterienobjekten
würde damit als `advancedCriteria=[object Object]` übertragen, eine leere Liste als `advancedCriteria=`.
Das anschließende Ergänzen von `advancedCriteria[0].field=...` entfernt den zusätzlichen Parameter nicht.
Das kann das Spring-Binding der Liste stören.

**Empfehlung:** Ein gemeinsamer Suchparameter-Helfer trennt `advancedCriteria` vom flachen Suchobjekt,
serialisiert die bisherigen Eigenschaften unverändert und ergänzt anschließend ausschließlich die indizierten
Kriterienparameter. Alle fünf genannten Services verwenden diesen Helfer. Bei leerer Liste wird überhaupt
kein `advancedCriteria`-Parameter erzeugt. `0` und `false` müssen erhalten bleiben.

Hier sind ein kleiner Test der tatsächlich erzeugten Parameter und ein HTTP-Binding-Test besonders wertvoll.
Ein reiner Test des neuen DTO oder der Repository-Validierung würde diesen Fehler nicht erkennen.

**Codebeleg:** [Aktuelle String-Konvertierung der Parameter][q8].

### 2.5 Der ISIN-Vorrang ist bei Indikatoren noch nicht durchgängig spezifiziert

**Priorität: hoch. Betrifft §§2, 5 und 6.3.**

UDF-Prädikate liegen ausdrücklich im `else`-Zweig der ISIN-Suche und werden dadurch übersprungen.
Die nachgelagerte Indikatorfilterung soll dagegen die Kriterienliste nach `findAll()` anwenden.
Ohne zusätzliche Regel würde eine ISIN zusammen mit einem nicht passenden Indikator deshalb keinen Treffer
ergeben, obwohl die Entscheidungstabelle sagt, dass ISIN alle weiteren Kriterien übergeht.
Das Ausblenden im Formular reicht für direkte API-Aufrufe nicht aus.

**Empfehlung:** Nach der Request-Validierung einmal die wirksamen erweiterten Kriterien bestimmen.
Bei ISIN sind diese entsprechend dem gewählten Vorrang leer; weder Indikatorberechnung noch deren
Kandidatenlimit werden angewandt. Dieselben wirksamen Kriterien steuern die übrigen Suchzweige.
Sichtbarkeits- und Ausschlussregeln bleiben wirksam, wie im aktuellen Suchbuilder.

Alternativ wäre die Kombination aus ISIN und weiteren Kriterien ausdrücklich zurückzuweisen. Der bestehende
Dialog spricht jedoch dafür, den bisherigen ISIN-Vorrang beizubehalten.

**Codebelege:** [ISIN-Zweig und Mandantensichtbarkeit][q9], [Zentraler Suchablauf][q10].

## 3. Weitere Anforderungen an die Suchsemantik

### 3.1 „Weniger als 20“ und „bis einschließlich 20“ unterscheiden

Das ursprüngliche Beispiel verlangt `KGV < 20`. Ausschließlich inklusive Von-/Bis-Grenzen können nur
`KGV <= 20` ausdrücken. Eine Obergrenze von `19.99` ist wegen möglicher weiterer Nachkommastellen kein
gleichwertiger Ersatz.

Ich würde nach der Feldwahl für Zahlen einen kleinen Operator-Select anbieten:
`<`, `<=`, `=`, `>=`, `>` und „zwischen“. Ein einzelner Vergleich zeigt ein Wertefeld, „zwischen“ zwei.
Der Backend-Katalog liefert die zulässigen Operatoren und der Server validiert sie.
Alternativ können Von-/Bis-Grenzen um explizite Inklusivitätsmerkmale ergänzt werden.

Der Ausschluss doppelter Felder bleibt damit praktikabel: „zwischen“ enthält beide Grenzen in einer Zeile.
Für eine Fassung mit ausschließlich inklusiven Grenzen müsste die Benutzeranforderung entsprechend enger
formuliert werden.

Auch das Beispiel selbst braucht klare Semantik: Ein vorhandenes negatives KGV erfüllt mathematisch `< 20`.
Wer nur positive Werte möchte, benötigt zusätzlich eine Untergrenze. Die Suche sollte diese fachliche
Zusatzbedingung nicht eigenständig unterstellen.

### 3.2 JSON-Konvertierung und fehlende Werte sauber unterscheiden

Die Vereinheitlichung von JSON-Zahlen und als String gespeicherten Zahlen ist richtig. Ein bloßes
`CAST(JSON_VALUE(...) AS DOUBLE)` legt aber noch nicht fest, wie leere, fehlerhafte oder nur teilweise
numerische Strings behandelt werden. Solche Werte dürfen bei einem Obergrenzenfilter nicht durch eine
SQL-Konvertierung versehentlich wie eine gültige Null behandelt werden.

**Empfehlung:** Einen gültigen numerischen Skalar beziehungsweise eine vollständig gültige numerische
Stringdarstellung voraussetzen; andere Werte ergeben „kein auswertbarer Wert“. Die Normalisierung muss
mit den bestehenden Schreibpfaden vereinbar sein, einschließlich wissenschaftlicher Schreibweise, soweit
sie dort akzeptiert wird. Request-Grenzen müssen mit `Double.isFinite()` geprüft werden.
Für Datumswerte gilt entsprechend: gültiges erwartetes Format voraussetzen, dann den Kalendertag vergleichen.

Die konkreten SQL-Prädikate sollten auf MariaDB geprüft werden. Die offizielle
[Dokumentation zur Typkonvertierung](https://mariadb.com/docs/server/reference/sql-functions/string-functions/type-conversion)
zeigt, dass numerische Konvertierungen von Strings Warnungen und überraschende Vergleiche erzeugen können;
die Behandlung ungültiger Werte sollte deshalb ein ausdrücklich getesteter Teil des Suchvertrags sein.

Bei Boolean-UDF gehören insbesondere `false`, fehlender Schlüssel und JSON-`null` in getrennte Testfälle.
Die in der Spezifikation gewählte Regel „fehlend trifft weder Ja noch Nein“ halte ich für richtig.

### 3.3 Den fachlichen Geltungsbereich eines UDF berücksichtigen

UDF besitzen `categoryTypes` und `specialInvestmentInstruments`. Die vorhandenen Erzeuger und Füllpfade
prüfen diese Angaben über `matchAssetclassAndSpecialInvestmentInstruments()`.
Der geplante SQL-Filter prüft bisher nur den JSON-Wert und seinen Eigentümer.

**Empfehlung:** Ein Instrument trifft ein UDF-Kriterium nur, wenn es aktuell auch zum Geltungsbereich des
Feldes gehört. Andernfalls könnten etwa nach einer Änderung der Metadaten noch vorhandene ältere Werte
außerhalb des aktuellen Feldumfangs Treffer erzeugen. Automatische Füllläufe räumen zwar ihren Umfang auf;
die Suche sollte weder vom Zeitpunkt dieses Laufs noch von einer solchen Bereinigung abhängig sein.

Der öffentliche DTO darf kompakt bleiben. Intern muss die aufgelöste Felddefinition jedoch auch Eigentümer,
Metadaten-ID, Geltungsbereich und Wertquelle bereitstellen. `key`, `label`, `valueType` allein reichen
für die sichere und fachlich vollständige Ausführung nicht aus.

**Codebelege:** [Geltungsbereichsprüfung][q4], [Metadaten und Auslöser bei Umfangsänderung][q11].

### 3.4 Datenstand und Rundung ausdrücklich festlegen

Die vorgesehene Wiederverwendung der Indikatorauswertung ist sinnvoll. Sie verwendet für `PRICE` aktuell
`Securitycurrency.getSLast()` und lädt Fundamentalwerte zum übergebenen Tag. Ein KGV ist damit kein
historischer Wert zum Suchdatum eines anderen Formularfeldes.

Festzulegen wäre:

- Die Suche verwendet vorhandene Kurse und Kennzahlen zum aktuellen Auswertungsstand; das bisherige
  `activeDate` bleibt ein Filter auf den Aktivitätszeitraum des Instruments.
- Verglichen wird der ungerundete Auswertungswert. Eine Anzeige mit zwei Nachkommastellen verändert die
  Trefferentscheidung nicht. Grenzfälle müssen anhand einer ausreichend genauen Darstellung erklärbar sein.
- Gespeicherte UDF werden gelesen, ohne sie im Suchaufruf neu zu befüllen. Das kann vom Öffnen des
  Watchlist-UDF-Tabs abweichen: Dort werden Template-UDF vor dem Lesen neu geschrieben und Spezialwerte ergänzt.

**Codebelege:** [Indikatoreingaben und Fundamentalwerte][q12], [Watchlist-UDF-Ladepfad][q5].

## 4. Laufzeit und Wiederverwendung

### 4.1 Die Kandidatengrenze vor dem vollständigen Laden durchsetzen

Die Grenze von 500 ist als erste Schutzmaßnahme plausibel. Der geplante Ablauf lädt aber zunächst mit
`findAll()` sämtliche SQL-Treffer als Entities und prüft erst danach die Anzahl. Damit schützt die Grenze
die eigentliche Indikatorauswertung, aber noch nicht das vorherige Laden sehr großer Ergebnismengen.

**Empfehlung:** Bei wirksamen Indikatorkriterien höchstens `Limit + 1` Kandidaten laden beziehungsweise
zunächst entsprechend begrenzt ihre IDs ermitteln. 501 Kandidaten bedeuten bei einem Limit von 500 einen
vollständigen Abbruch mit Eingrenzungshinweis. Es dürfen nicht einfach die ersten 500 ausgewertet und als
vollständiges Suchergebnis präsentiert werden. Die Grenze wird nach allen SQL-Filtern angewandt.

Die Laufzeitangabe „115 Instrumente etwa eine Sekunde, daher 500 etwa fünf Sekunden“ ist noch keine
belastbare Zusage. Ausdruck, Zahl ausgewählter Spalten, Historienlänge und Datenbankzustand beeinflussen sie.
`WatchlistIndicatorReport.loadSeries()` lädt beispielsweise zunächst ein Zeitfenster von 1920 Tagen.
Eine reine Fundamentalformel hat einen anderen Aufwand als mehrere technische Indikatoren mit Benchmark.

Für gespeicherte UDF ist der vorhandene Primärschlüssel gut geeignet, die zu einem Instrument gehörige
Zeile zu finden. Er indexiert aber nicht den extrahierten numerischen JSON-Wert. Eine breite Suche muss
weiterhin bei vielen Instrumenten JSON auswerten. „Kein neuer Index zunächst“ ist vertretbar;
„deshalb unproblematische Laufzeit“ wäre ohne Messung zu weitgehend.

**Codebelege:** [Unbegrenzter aktueller Suchaufruf][q10], [Historienladepfad][q12], [UDF-Primärschlüssel][q13].

### 4.2 Gemeinsame Auswertung muss auch die bisherigen Berichtsdaten erhalten

Die Extraktion einer gemeinsamen Indikatorauswertung ist richtig. Das vorgeschlagene Ergebnis von
`cellsOf()` enthält jedoch nur Zellen. Der heutige Ablauf erzeugt beim Laden auch Benchmarkinformationen,
Fundamentalherkunft, Spaltenkategorien und das jüngste Historiendatum der Positionen.
Diese Daten braucht der bestehende Watchlist-Bericht weiterhin.

**Empfehlung:** Einen gemeinsamen Batch-Auswerter mit einem internen Ergebnisobjekt vorsehen, das neben
den Zellen auch die benötigten Begleitinformationen liefert. Der Suchpfad liest die Zellen daraus;
der Bericht übernimmt zusätzlich seine Anzeigeinformationen. Daten sollen nicht für die Berichtsanzeige
ein zweites Mal geladen werden. Den Mandanten zur Benchmark-Freigabe und `ClientClock.today()` explizit
als Auswertungskontext übergeben.

Das ist eine Präzisierung der Wiederverwendung, kein Anlass, einen zweiten Ausdrucksauswerter aufzubauen.

**Codebeleg:** [Bestehende Berechnung einschließlich Berichtsinformationen][q12].

### 4.3 Indizierte GET-Parameter auch vor der Repository-Validierung begrenzen

Bei den vorhandenen GET-Endpunkten erfolgt das Binding von `SecuritycurrencySearch` vor dem Aufruf
der Repository-Methode. Die dortige Prüfung der Listengröße kann daher die vorherige Vergrößerung einer
Liste beim Binding nicht begrenzen.

**Empfehlung:** Für diesen Request-Typ eine passende Binding-Grenze setzen und fehlerhafte Indizes,
Lücken, null-Einträge sowie Typkonvertierungsfehler mit einer verständlichen Client-Fehlermeldung behandeln.
Die fachliche Validierung bleibt zusätzlich am gemeinsamen Suchpfad. GET mit maximal fünf kompakten
Kriterien ist ansonsten für diese erste Fassung vertretbar; ein neuer POST-Endpunkt ist dafür nicht zwingend.

**Codebeleg:** [GET-Endpunkt mit Parameterobjekt][q14].

## 5. Bedienung und Ergebnisdarstellung

Den Einstieg mit einer Schaltfläche und zunächst nur einem Feld-Select würde ich beibehalten.
Fünf intern vorbereitete Plätze sind bei einer festen Obergrenze ein vertretbarer Weg für das bestehende
reaktive Formular. Für den Benutzer sollten es trotzdem eindeutig erkennbare Kriterienzeilen sein.

Ich würde folgende Ergänzungen vorsehen:

- Feldangebote nach „Eigene UDF“, „Allgemeine UDF“ und „Indikatoren“ gruppieren. Gleiche Beschriftungen
  sind dadurch weniger verwirrend; Indikatoren können zusätzlich ihre Formel oder einen Hilfetext zeigen.
  Für Indikatorbeschriftungen ist aktuell keine Eindeutigkeit vorgeschrieben.
- Nach Auswahl eines Feldes bleiben Feldname, Operator und Wert gemeinsam sichtbar. Eine Entfernen-Aktion
  gehört zur Zeile. „Alle Bedingungen müssen erfüllt sein“ erklärt die UND-Verknüpfung.
- Die für die Suche verwendeten Werte in der Ergebnisliste anzeigen, etwa als zusätzliche Spalten für die
  tatsächlich gewählten Kriterien oder in einer aufklappbaren Detailansicht. Gerade KGV und andere
  Kennzahlen müssen prüfbar sein. Die Entscheidung, überhaupt keine Trefferwerte anzuzeigen, halte ich
  aus Benutzersicht für unnötig einschränkend.
- Dafür dieselben Werte aus der Filterauswertung zurückgeben, statt sie nach dem Suchlauf neu zu berechnen.
  Ein kleines Suchergebnis-DTO wäre eine mögliche Umsetzung; das wäre eine bewusste Erweiterung des
  bisherigen Antwortvertrags.
- Einheit und Eingabegenauigkeit klar darstellen, soweit die Metadaten sie hergeben. Eine Prozentzahl darf
  nicht unbemerkt einmal als `5` und einmal als `0.05` verstanden werden. Keine Einheit aus dem Feldnamen erraten.
- Während einer Indikatorsuche einen Ladezustand zeigen und mehrfaches gleichzeitiges Absenden verhindern.

„Währungspaare bei erweiterten Kriterien ausgeschlossen“ ist eine sinnvolle Umfangsentscheidung für den
Anfang, aber keine allgemeine technische Grenze der Indikatorauswertung: Der heutige Bericht verarbeitet
auch Währungspaare. Der Dialog sollte die gewählte Einschränkung verständlich benennen.

Die Verwendung aller gespeicherten Indikatordefinitionen des Mandanten würde ich beibehalten, auch wenn
eine Spalte nur bestimmten Watchlists zugeordnet ist. Diese Zuordnung regelt ihre Anzeige; im Suchdialog
wählt der Benutzer die Definition ausdrücklich aus. Das sollte als bewusste Suchregel dokumentiert sein.

## 6. Empfohlener Lieferumfang und gezielte Abnahme

**Erste Lieferung:** Gespeicherte UDF, typabhängige Kriterienzeilen, klare Vergleichsoperatoren,
vollständige Validierung, bestehende Sichtbarkeitsregeln und nachvollziehbare Trefferwerte.
Nur zur Anzeige berechnete Spezial-UDF bleiben aus dem Katalog ausgeschlossen.

**Zweite Lieferung:** Gespeicherte Indikatordefinitionen, gemeinsamer Batch-Auswerter,
früh durchgesetzte Kandidatengrenze und Hinweis auf fehlende Eingabedaten.
Die Kennzahlenversorgung wird dabei nicht automatisch auf sämtliche sichtbaren Instrumente erweitert.

Die im Entwurf vorgesehenen Tests sind eine gute Grundlage. Besonders ergänzen würde ich diese Szenarien:

| Szenario | Erwartung |
|---|---|
| Nur ein Feld ausgewählt, noch kein Vergleichswert | Keine Suche; die Zeile zeigt den Eingabefehler |
| Wert `0`, Boolean `false`, Wechsel des Feldtyps, Entfernen einer Zeile | Werte korrekt erhalten beziehungsweise alte Werte zuverlässig entfernt |
| Leere und belegte Kriterienliste über HTTP | Nur das vereinbarte Parameterformat; keine zusätzliche flache Listeneigenschaft |
| ISIN zusammen mit UDF- und Indikatorkriterien | Ein einheitlich umgesetzter ISIN-Vorrang ohne Indikatorberechnung |
| Verfallrendite als nur berechnetes Spezial-UDF | Aus Katalog ausgeschlossen oder nach ausdrücklich gewählter Auswertungsregel unterstützt |
| Neue, unbenutzte Aktie ohne persönliche UDF oder EPS | Kein erfundener Wert; fehlende Datenabdeckung erklärt |
| Zahl `20` bei `< 20` und `<= 20` | Im ersten Fall ausgeschlossen, im zweiten enthalten |
| JSON-Zahl, Zahlenstring, leere/ungültige Darstellung, fehlender Schlüssel, JSON-`null` | Nur auswertbare, passende Werte treffen |
| UDF außerhalb seines aktuellen Anlageklassenumfangs | Kein Treffer trotz gegebenenfalls vorhandener älterer JSON-Daten |
| Fremdes persönliches UDF, fremder Indikator, private fremde Security und fremder Benchmark | Keine unberechtigte Auswahl oder Auswertung |
| 500 beziehungsweise 501 SQL-Kandidaten für Indikatoren | Vollständige Auswertung beziehungsweise klarer Abbruch; keine abgeschnittene Trefferliste |
| Watchlist-Indikatoren nach Extraktion des Auswerters | Zellen, Benchmarkinformationen, Fundamentalherkunft und Historiendatum weiterhin korrekt |

Für diesen Review wurden Quelltext und Spezifikation gelesen. Es wurden keine Anwendungsdaten verändert,
keine Tests ausgeführt und keine Laufzeitmessungen vorgenommen. Die vorgeschlagenen Laufzeitgrenzen sind
daher zu überprüfende Entwurfsentscheidungen.

## Codebelege

Die Links beziehen sich auf den aktuellen Quelltext, nicht auf eine bereits implementierte erweiterte Suche.

[q1]: ../backend/grafioschtrader-server/src/main/java/grafioschtrader/udffill/UDFFillScopeService.java#L61
[q2]: ../backend/grafioschtrader-server/src/main/resources/META-INF/jpa-named-queries.properties#L222
[q3]: ../backend/grafioschtrader-server/src/main/java/grafioschtrader/reports/udfalluserfields/YieldToMaturityCalculator.java#L342
[q4]: ../backend/grafioschtrader-server/src/main/java/grafioschtrader/reports/udfalluserfields/AllUserFieldsSecurity.java#L58
[q5]: ../backend/grafioschtrader-server/src/main/java/grafioschtrader/reports/WatchlistReport.java#L242
[q6]: ../frontend/src/app/securitycurrency/component/securitycurrency.search.base.ts#L138
[q7]: ../frontend/src/app/lib/validator/validator.ts#L12
[q8]: ../frontend/src/app/lib/helper/app.helper.ts#L407
[q9]: ../backend/grafioschtrader-common/src/main/java/grafioschtrader/search/SecuritySearchBuilder.java#L132
[q10]: ../backend/grafioschtrader-server/src/main/java/grafioschtrader/repository/SecurityJpaRepositoryImpl.java#L603
[q11]: ../backend/grafioschtrader-server/src/main/java/grafioschtrader/repository/UDFMetadataSecurityJpaRepositoryImpl.java#L88
[q12]: ../backend/grafioschtrader-server/src/main/java/grafioschtrader/reports/WatchlistIndicatorReport.java#L51
[q13]: ../backend/grafioschtrader-server/src/main/resources/db/migration/gt_ddl.sql#L2875
[q14]: ../backend/grafioschtrader-server/src/main/java/grafioschtrader/rest/SecurityResource.java#L281
[q15]: ../backend/grafioschtrader-server/src/main/java/grafioschtrader/fundamental/FundamentalDataLoadService.java#L101
[q16]: ../backend/grafioschtrader-server/src/main/resources/db/migration/B0_38_0__baseline.sql#L27941
