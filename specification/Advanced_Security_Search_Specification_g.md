# Bemerkungen zur erweiterten Instrumentensuche

Bezug: `specification/Advanced_Security_Search_Specification.md`.
Geprüft gegen Backend 0.39.4, höchster Flyway-Stand `V0_39_4__cost_basis_method.sql`.
Die Spezifikation bleibt unverändert.

## Fazit

UDF-Kriterien in SQL und Indikatoren danach auf der bereits eingeschränkten Menge ist die richtige Teilung. Fünf UND-verknüpfte Kriterien, kein neues Schema, der ISIN-Kurzschluss im bestehenden `else` von `SecuritySearchBuilder.toPredicate` und die Ablehnung unbekannter Felder über den Katalog des angemeldeten Benutzers sind tragfähig.

Zwei Punkte würde ich vor der Umsetzung ändern. Die Rendite bis Fälligkeit steht nicht in `udf_data`, wäre im Katalog aber suchbar und träfe nie. Die Obergrenze von 500 Kandidaten hängt an der teuren Kursreihe; sie lässt das Beispiel „KGV unter 20“ auf einem Bestand mit mehr als 500 Aktien scheitern, obwohl diese Kennzahl keine Kursreihe braucht.

## Was so bleiben kann

- Der Knopf öffnet ein Select, die Wahl des Feldes zeigt die passenden Eingaben. Fünf feste Slots in `SecuritycurrencySearchBase` sind die passende Form dafür. `config` wird in `ngOnInit` einmal gebaut (`securitycurrency.search.base.ts`), ein zur Laufzeit eingefügtes Feld übernimmt das bestehende Formular nicht zuverlässig.
- Nur Wertpapiere. `CurrencypairJpaRepositoryImpl` antwortet bei gesetzten Kriterien mit einer leeren Liste, der Wertpapierzweig filtert. Währungspaare haben weder UDF noch Indikatoren.
- Bereich von/bis, beide Grenzen einschließlich, eine Grenze darf fehlen. Text als Teilstring wie der Name. Fehlender Wert trifft nicht.
- Höchstens fünf Kriterien, jedes Feld einmal, alles mit UND. ODER und Klammern lohnen den Dialog nicht.
- Der Feldschlüssel kommt vom Server (`U<idUDFMetadata>`, `I<idWatchlistIndicatorColumn>`). Die Zeile wird aus dem Katalog gelesen, nicht aus der Anfrage. Damit bleibt der Ausdruck einer Indikator-Spalte der gespeicherte Ausdruck.
- `McpReadToolService.findInstrument` baut `SecuritySearchBuilder` direkt und geht an `searchBuilderWithExclusion` vorbei. Eine leere Kriterienliste darf dort keine zusätzliche Bedingung und keinen Fehler erzeugen.
- Die NLS-Beschriftung eines Spezialfeldes ist der Name der Konstante (`UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY` und die drei Yahoo-Schlüssel daneben in `messages.properties`). Die Spalte `description` enthält bei diesen Zeilen `UDF_YTM`, `UDF_YAHOO_EARNING_LINK`, `UDF_YAHOO_EARNING_N_DATE`, `UDF_YAHOO_STAT_LINK`. Diese Schlüssel stehen nicht im Bundle. Die Watchlist-Spalte benutzt `description` und zeigt deshalb den Rohschlüssel. Die Suche sollte das nicht nachmachen.
- `getAllByIdUserInOrderByUiOrderExcludeDisabled` nimmt Benutzer und Benutzer 0, lässt `ui_order >= 100` weg und respektiert `udf_special_type_disable_user`. Das Yahoo-Symbol (`ui_order` 101) bleibt draussen. Der Primärschlüssel von `udf_data` ist `(entity, id_entity, id_user)`.
- Ein ISIN-Treffer lässt die übrigen Kriterien aus, die Mandantenprüfung und der Ausschluss der schon enthaltenen Instrumente bleiben. Die UDF-Prädikate gehören in den `else`-Zweig, nicht davor.
- Die Hilfe-Seite ist für alle vier Dialoge dieselbe: `HelpIds.HELP_WATCHLIST_SEARCHDIALOG`.

## 1. Berechnete UDF-Werte sind kein JSON-Feld

`YieldToMaturityCalculator.calcAndSetYTM` schreibt das Ergebnis mit `putValueToJsonValue(..., false)`. `AllUserFieldsSecurity` legt es dann nur in das JSON der Watchlist-Antwort, nicht in `udf_data`. `mayRunInBackground()` ist `false`, der nächtliche Lauf fasst das Feld also auch nicht an. Der Katalog aus `getAllByIdUserInOrderByUiOrderExcludeDisabled` enthält es trotzdem: Benutzer 0, `ui_order` 20, Typ Zahl. Eine Suche „Rendite bis 3“ liefert leer, während dieselbe Spalte auf der Watchlist Werte zeigt.

Die Yahoo-Felder mit `ui_order` 50, 60 und 80 werden beim Öffnen einer Watchlist nach Benutzer 0 geschrieben (`persist` wahr). Eine SQL-Suche findet nur Instrumente, deren Watchlist schon einmal geladen wurde, und sie ruft Yahoo nicht neu auf. Das ist brauchbar, wenn die Hilfe es sagt. Für die Rendite bis Fälligkeit gilt es nicht, dort ist nie eine Zeile da.

Vorschlag: ein Spezialfeld, dessen Watchlist-Wert nur im Speicher entsteht, kommt nicht als JSON-Prädikat in den Katalog. Entweder fehlt es in der Auswahl, oder die Suche berechnet es für die SQL-Kandidaten mit derselben Methode wie die Watchlist. Ein stiller Leer-Treffer ist in beiden Fällen schlechter.

## 2. Die Grenze von 500 Kandidaten trifft die falsche Rechnung

`WatchlistIndicatorReport.loadSeries` liest bis zu 1920 Tage und behält `AlertExpressionSupport.SERIES_OBSERVATIONS` (1200) Kurse je Instrument, und zusätzlich die Benchmarks. 500 Instrumente mit einer RSI- oder Performance-Spalte sind diese Menge. Eine Spalte `PRICE / EPS` braucht sie nicht: `requiredData` verlangt dann `FUNDAMENTALS` und die Variable `PRICE`, und `loadFundamentals` ist eine Abfrage.

Die Angabe „115 Instrumente etwa 1 s, also 500 etwa 5 s“ steht nicht im Code. Sie skaliert ausserdem linear über zwei verschieden teure Wege. Auf einem Bestand mit mehr als 500 Aktien scheitert „KGV unter 20“ ohne zusätzliche Enge, also genau die Suche über alle Sätze. Dieselbe Abfrage ohne Indikator, nur mit Anlageklasse Aktie, läuft heute und liefert die ganze Menge.

Vorschlag: die Grenze gilt, sobald eine gewählte Spalte `SERIES`, `BENCHMARK` oder `DIVIDENDS` verlangt. Eine Spalte, die nur `PRICE` und Fundamentaldaten braucht, läuft über das ganze SQL-Ergebnis. Das ist dieselbe Last wie die heutige Suche nach Anlageklasse, und der Filter verkleinert die Antwort. `AdvancedSearchField` sollte dafür ein Kennzeichen tragen, damit der Dialog vor dem Absenden sagen kann, dass diese Spalte höchstens N Instrumente auswertet.

`getWatchlistWithIndicators` ruft vor `calculate` `updateWatchlistPrices` auf. Die Suche darf das nicht: kein Connector-Lauf für die Kandidaten. `PRICE` ist dann der gespeicherte `sLast`. Das gehört in die Hilfe, sonst wirkt ein abweichender Wert wie ein Rechenfehler. `cellsOf` wertet die übergebenen Spalten aus und wendet `idWatchlists` nicht noch einmal an. Die Benchmarks der Spalten werden geladen, auch wenn sie nicht in der Kandidatenliste stehen. Der Abgleich bleibt `status == OK`. Neben den in der Spezifikation genannten Zuständen gibt es `NO_DIVIDEND_DATA`, `NO_FX_RATE` und `NOT_APPLICABLE` (`IndicatorFunctionSet`); auch die treffen nicht.

## 3. Ein angefangenes Kriterium darf die Suche nicht öffnen

Die Spezifikation lässt den Suchknopf zu, sobald `advField<n>` gefüllt ist, und `submit` wirft Zahl ohne Grenze, leeren Text und Boolean ohne Wert weg. Ist das der einzige Eintrag, geht eine Suche ohne jedes Kriterium an den Server. `atLeastOneFieldValidator` sieht nur, dass irgendein Control einen Wert hat.

Dieselbe Prüfung muss 0 und `false` als Wert zählen. `isSecondGroupEmpty` und `atLeastOneFieldValidator` tun das nicht: ein Boolean zählt nur bei `true`, eine Zahl nur bei truthy. „KGV von 0 bis 20“ und „Kennzahl ist Nein“ würden sonst verschluckt.

Vorschlag: die reine Funktion, die das Kriterium baut, ist auch die Funktion, die den Knopf freigibt. Ein Slot mit Feld und ohne verwertbaren Wert hält den Knopf gesperrt. 0 ist eine Grenze, `false` ist ein Wert.

## 4. Der bestehende Parameter-Builder schreibt das Array mit

`AppHelper.getHttpParamsOfObjectAllowBooleanNullFields` hängt jede eigene Property an. Ein Array wird zu `advancedCriteria=[object Object]`. Die Spezifikation ruft den neuen Helfer danach auf, nimmt die Property aus dem alten Lauf aber nicht heraus. Der Wertpapier-Service, die Watchlist, die Korrelation, die Algo-Anlageklasse, die Währungspaare und `stockexchange-edit.component.ts` (ebenfalls `SecurityService.searchByCriteria`) laufen alle durch diese eine Stelle.

`advancedCriteria` muss im bestehenden Lauf übersprungen werden. Der neue Helfer schreibt nur gesetzte Eigenschaften, `0` und `false` eingeschlossen, Datum als `yyyyMMdd`. Ist die Liste `null` oder leer, schreibt er nichts. MCP und der Börsen-Dialog senden dann dasselbe wie heute.

Spring legt die Liste bis zum höchsten Index an, die Auto-Grow-Grenze liegt bei 256. Ein Index darüber scheitert beim Binden mit einer anderen Ausnahme als `gt.advanced.search.too.many`. Für diesen Parameter die Grenze auf 5 zu setzen macht aus beidem dieselbe Meldung.

## 5. Layout der Slots und Schliessen

`getDefaultFormConfig(..., 3, ...)` legt das Formular auf drei Spalten. Ein Zahlen-Slot ist Select, von, bis und füllt eine Zeile. Ein Text- oder Boolean-Slot hat zwei Felder, das nächste Select rutscht daneben. Jeder Slot braucht eine eigene volle Zeile, mit der gewählten Bezeichnung an den Eingaben. Fünfmal dieselbe Überschrift „Von“ / „Bis“ ohne den Feldnamen ist nicht zuordenbar.

Ein geöffneter Slot geht in der Spezifikation nicht mehr zu. Nach fünf Klicks ist der Knopf weg, die leeren Selects bleiben. Jeder Slot braucht ein Entfernen, das die Felder leert und schliesst und die anderen Aufrückt oder die Lücke freigibt. Die leere Auswahl allein reicht nicht, weil der Slot offen bleibt und bei fünf offenen Slots nichts mehr dazukommt.

Die Auswahl gruppiert Benutzerfelder und Indikatoren. Dieselbe Bezeichnung kann beiderseits vorkommen. Eine flache Liste zeigt dann zweimal „KGV“.

## 6. Welche Indikator-Spalten angeboten werden

`WatchlistIndicatorReport.calculate` zeigt eine Spalte, deren `idWatchlists` leer ist, auf jeder Watchlist, sonst nur auf den genannten. Die Spezifikation bietet im Dialog alle Spalten des Mandanten an, auch in Korrelation, Algo und im allgemeinen Suchdialog.

Empfehlung: im Watchlist-Dialog die Spalten, die auf dieser Watchlist sichtbar wären; in den drei anderen Dialogen nur Spalten mit leerer Watchlist-Menge. Eine Spalte, die nur auf einer fremden Watchlist liegt, ist dort kein Suchfeld, das der Benutzer von der Indikator-Registerkarte kennt. Wer jede gespeicherte Formel überall suchen will, kann das bewusst anders entscheiden. Der Katalog muss die Regel dann explizit sagen.

## 7. Zahl aus JSON

Benutzerwerte und der automatische Lauf speichern Zahlen und Datum als Text, Boolean als JSON `true` / `false` (`UDFFillWriter.changeValue`). `UDFFieldsHelper` kann für Benutzer 0 ausserdem eine JSON-Zahl oder ein ISO-Datum schreiben. `JSON_VALUE` liefert beides als Text. Das ist der richtige Zugriff; die bestehende Abfrage `UDFDataFill.getFieldValuesOfUser` benutzt ihn mit `CONCAT('$.f', ?)`.

`CAST(... AS DOUBLE)` macht in MariaDB aus `''` und aus nicht numerischem Text `0`. Ein leerer oder unbrauchbarer Eintrag träfe dann jede Spanne, die 0 enthält. Die Spezifikation will, dass ein fehlender Wert nicht trifft. Das Prädikat muss einen nicht numerischen Text wie einen fehlenden Wert behandeln, zum Beispiel indem es ihn vor dem `CAST` verwirft. `NULL` von einem fehlenden Schlüssel ist bereits richtig.

`json_values` ist ein Hypersistence-`JsonType` auf `Map<String, Object>`. `regexp_instr` ist an einer normalen String-Spalte registriert. Dieselbe Registrierung beweist nicht, dass die Criteria-API die Map als JSON-Spalte an `JSON_VALUE` übergibt. Der Integrationstest auf MariaDB muss das erzeugte SQL zeigen, bevor das Formular darauf aufbaut. Wenn die Map als Bind-Parameter ankommt, bleibt es bei einem nativen `EXISTS`, analog zu `getFieldValuesOfUser`.

Datum: `UDF_DateString` liegt als `yyyy-MM-dd` (`STANDARD_DATE_FORMAT`). `UDF_DateTimeNumeric` kommt je nach Schreiber als `ISO_LOCAL_DATE_TIME` oder `ISO_OFFSET_DATE_TIME`. Die ersten zehn Zeichen sind in beiden Fällen das Datum. Ein Vergleich ohne Zeitzonenumrechnung ist damit deckungsgleich zur Anzeige.

Boolean: `JSON_VALUE` auf das JSON-Boolean liefert `true` bzw. `false`. Der Vergleich mit diesen beiden Texten trifft die gespeicherte Form. Die Strings `"1"` und `"0"` sind keine gespeicherte Form.

## 8. Treffer ohne den Wert, der sie erklärt

Die Ergebnistabelle zeigt den gefundenen UDF- oder Indikatorwert nicht. Bei einem selbst gepflegten Feld kann der Benutzer ihn nach dem Hinzufügen auf der Watchlist sehen. Bei einem Indikator sieht er ihn erst dort, und bei der Rendite bis Fälligkeit wäre er der einzige Beweis, dass die Spanne stimmt. Sortieren nach der Kennzahl geht so ebenfalls nicht.

Das muss nicht die erste Lieferung blockieren. Es sollte als eigene, benannte Stufe dastehen und nicht als dauerhaftes Nein: je verwendetem Kriterium eine Spalte in der Ergebnistabelle, Zahl und Datum so formatiert wie auf der Watchlist. Die Hilfe der ersten Stufe sagt bis dahin, dass die Werte nicht in der Liste stehen.

## 9. Kleineres

- Der Toast für `DataViolationException` setzt den Feldschlüssel vor die Meldung (`ValidationError`). `advanced.search` erscheint dann als Rohname. Der Meldungstext von `gt.advanced.search.indicator.candidates` muss allein verständlich sein.
- Negative Grenzen sind nötig. Ein KGV kann unter 0 liegen, und `field_size` eines Zahlen-UDF erlaubt ein negatives Minimum.
- `%`, `_` und `\` im Textkriterium mit explizitem `ESCAPE` suchen. Sich auf den MariaDB-Default zu verlassen kippt, sobald `NO_BACKSLASH_ESCAPES` gesetzt ist.
- Die Backend-Tests der Spezifikation sind die richtigen: gespeicherte Zeichenkette und JSON-Zahl in derselben Spanne, Grenze einschließlich, fremdes Feld abgelehnt, Benutzer 0 über die Zeile von Benutzer 0, Datum und Datum-mit-Zeit, `%` als Zeichen. Dazu ein nicht numerischer Text, der eine Spanne durch 0 nicht trifft, und `false` sowie die Grenze 0, die am Server ankommen. Der Indikator-Test mit unabhängig berechneten Zellen ist der richtige Test. Die 500er-Grenze wird an einer Spalte geprüft, die eine Kursreihe verlangt, und an einer Fundamental-Spalte, die über 500 Kandidaten hinweg rechnen darf, falls Abschnitt 2 so entschieden wird.
