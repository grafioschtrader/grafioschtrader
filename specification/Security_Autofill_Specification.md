# Wertpapier aus ISIN, Währung und Börse automatisch befüllen

**Code baseline:** backend 0.38.1, highest Flyway script
`V0_38_2__correlation_set_include_dividends_and_hold_portfolio_indexes.sql`.

## 1 Ziel

Ein neues Wertpapier verlangt heute vier Connectoren mit ihren URL-Erweiterungen sowie Name, Ticker, Anlageklasse,
Daten, Ausschüttungsfrequenz und Stückelung. Der Benutzer muss das Format jedes Anbieters kennen. Beim Import lässt sich
eine unbekannte ISIN nur über GTNet auflösen (`CREATE_GTNET_IMPORT_FROM_MISSING`); eine Instanz ohne GTNet hat keinen
automatischen Weg.

Ein **Wertpapier-Lookup** liefert aus ISIN, Währung und Börse (MIC) eine Kandidatenliste mit befüllten Feldern und je
Feed gereihten Connectoren. Er besteht aus deterministischen Schritten und einer optionalen KI am Schluss:

| Schritt | Inhalt | Modellaufruf |
|---|---|---|
| PROFILE | Admin-gepflegtes YAML-Connector-Profil reiht die Connectoren je Feed nach MIC und Anlageklasse (§5); Connectoren leiten ihre URL-Erweiterung ab (§6) | nein |
| GTNET | bestehender Peer-Lookup, wenn `isGTNetOperational()` | nein |
| PROVIDER_SEARCH | Suche bei Datenanbietern über `ISecurityDataProvider.search` (§4) | nein |
| PROVIDER_BASE_DATA | Stammdaten des gewählten Listings über `ISecurityDataProvider.getSecurityBaseData` (§4.4) | nein |
| DERIVATION | Felder aus bereits Festgestelltem berechnet (§7) | nein |
| AI | Fallback nur für verbleibende Lücken (§9) | ja |

Jedes Feld, das ein deterministischer Schritt festlegt, muss die KI nicht mehr recherchieren. Die Token entstehen vor
allem in Tool-Runden (Websuche, abgerufene Seiten, Probes); die vorgelagerten Schritte senken deshalb sowohl die Zahl
der Modellaufrufe als auch die Kosten eines Aufrufs. Ist das Ergebnis ohne KI vollständig, findet kein Modellaufruf
statt.

GT prüft jedes Kandidatenfeld unabhängig von seiner Quelle. Ein erfolgreicher Preis-Probe allein beweist nicht die
Identität des Instruments. Kein Anbieter und kein Modell schreibt selbst ein Wertpapier.

Der Lookup ohne KI wird zuerst geliefert und gemessen (§14). Er muss ohne GTNet, KI und kostenpflichtiges Abonnement
funktionieren, wenn Profil und schlüsselfreie Anbieter die Pflichtfelder auflösen.

Konsumenten:
- **Bearbeitungsdialog:** Der Benutzer wählt einen Kandidaten und je Feed einen Connector aus den gereihten
  Alternativen (§10).
- **Import:** Eine Hintergrundaufgabe legt den besten Kandidaten an (§11).

## 2 Bestehender Code, auf dem die Arbeit aufsetzt

Das Paket `grafioschtrader.connector.securitysearch` (`grafioschtrader-server`) enthält die Anbietersuche:

- `ISecuritySearchProvider` mit `getProviderId()`, `supports(SecuritySearchQuery)`, `search(SecuritySearchQuery)`;
- `BaseSecuritySearchProvider`: ein Token-Bucket je Anbieter mit begrenzter Wartezeit, ein gemeinsamer `HttpClient`
  mit Connect- und Request-Timeout, Statusabbildung (429 → `RATE_LIMITED`, 401/403 → `NOT_ENTITLED`, 5xx/IO/nicht
  lesbar → `PROVIDER_ERROR`, Timeout → `TIMEOUT`), danach `SearchCandidateMatcher`;
- Adapter `YahooSymbolSearch` (`connector.yahoo`), `OpenFigiSecuritySearch`, `SixSecuritySearch`,
  `XetraSecuritySearch`;
- `SecuritySearchService`: fragt die gewählten Anbieter parallel auf virtuellen Threads mit gemeinsamer Frist;
- `SearchCandidateMatcher`, `MarketSegmentMics`, `SecuritySearchQuery`, `SecuritySearchResult`,
  `SearchFailureException`;
- in `grafioschtrader-common`: `dto/SecuritySearchCandidateDto`, `types/SecuritySearchStatus`,
  `types/IdentityEvidence`.

`YahooUDFConnect` löst Yahoo-Symbole über `YahooSymbolSearch` auf und übernimmt ein Symbol nur bei genau einem
Kandidaten auf der Börse des Wertpapiers.

## 3 Begriffe

- **Listing:** ein Instrument an einer Börse in einer Handelswährung. Eine ISIN hat oft mehrere Listings.
- **Evidenz** (`IdentityEvidence`) je Identitätsfeld ISIN, Währung, MIC: `RETURNED` (vom Anbieter geliefert),
  `FILTERED` (vom Anbieter als exakter serverseitiger Filter angewendet), `UNKNOWN`. Eine Volltextsuche mit der ISIN
  (Yahoo) ist kein Filter.
- **Herkunft** je Kandidatenfeld: der Schritt aus §1 und bei Anbietern zusätzlich die `providerId`.

## 4 Datenanbieter-Schnittstelle

### 4.1 Umbenennung

Die Schnittstelle deckt Suche und Stammdaten ab und heisst deshalb nicht mehr nach der Suche:

| Heute | Neu |
|---|---|
| Paket `connector.securitysearch` | `connector.securitydata` |
| `ISecuritySearchProvider` | `ISecurityDataProvider` |
| `BaseSecuritySearchProvider` | `BaseSecurityDataProvider` |
| `SecuritySearchService` | `SecurityDataService` |
| `SecuritySearchStatus` | `SecurityDataStatus` |
| `YahooSymbolSearch` | `YahooSecurityData` (bleibt in `connector.yahoo`) |
| `OpenFigiSecuritySearch`, `SixSecuritySearch`, `XetraSecuritySearch` | `OpenFigiSecurityData`, `SixSecurityData`, `XetraSecurityData` |

`SecuritySearchQuery`, `SecuritySearchResult`, `SecuritySearchCandidateDto`, `SearchCandidateMatcher` und
`SearchFailureException` beschreiben die Suche und behalten ihren Namen. `SearchFailureException` wird zu
`SecurityDataFailureException`, weil sie auch beim Stammdatenabruf geworfen wird.

### 4.2 Vertrag

```java
public interface ISecurityDataProvider {
  String getProviderId();
  boolean supports(SecuritySearchQuery query);
  SecuritySearchResult search(SecuritySearchQuery query);

  default boolean supportsBaseData(SecuritySearchCandidateDto candidate) { return false; }
  default SecurityBaseDataResult getSecurityBaseData(SecuritySearchCandidateDto candidate) {
    return SecurityBaseDataResult.failure(getProviderId(), SecurityDataStatus.UNSUPPORTED_QUERY, null);
  }
}
```

- Suche und Stammdatenabruf werfen nie; jeder Fehler wird zu einem `SecurityDataStatus`. `RATE_LIMITED`, `TIMEOUT` und
  `PROVIDER_ERROR` sind vorübergehend: Ein Aufrufer darf das Fehlen eines Ergebnisses danach weder cachen noch
  speichern.
- `BaseSecurityDataProvider` implementiert `getSecurityBaseData` als `final`-Vorlage mit denselben Grenzen wie die
  Suche (Bucket, Timeout, Statusabbildung) und ruft `protected SecurityBaseData fetchBaseData(candidate)` auf. Ein
  Adapter überschreibt `supportsBaseData` und `fetchBaseData`.
- Stammdaten werden immer für einen Kandidaten desselben Anbieters abgerufen; Schlüssel ist dessen
  `providerInstrumentId` oder `providerSymbol`. Ein Kandidat eines anderen Anbieters ergibt `UNSUPPORTED_QUERY`.
- Ein Anbietersymbol ist nur Kennung dieses Anbieters und nicht automatisch die URL-Erweiterung eines Feeds; die
  Umrechnung bleibt beim Connector (§6).

### 4.3 Suche

- **Abfrage** `SecuritySearchQuery`: ISIN, Währung und MIC (beide optional, z. B. beim Import), Ticker und Name nur
  als Hinweise für eine Ausweichsuche. Ein Anbieter, der die ISIN in eine URL oder einen Filterausdruck setzt,
  verlangt eine syntaktisch gültige ISIN (`hasValidIsin()`).
- **Kandidat** `SecuritySearchCandidateDto`: `providerId`, `providerSymbol`, `providerInstrumentId` (FIGI, SIX
  ValorId), `providerExchangeCode`, ISIN, Währung, MIC je mit Evidenz, Name, roher Instrumenttyp des Anbieters.
- **Abgleich** (`SearchCandidateMatcher`, für alle Anbieter gleich):
  - Ein Kandidat wird verworfen und gezählt, wenn eine *festgestellte* ISIN, Währung oder MIC der Abfrage
    widerspricht. Ein unbekannter Wert führt nie zum Verwerfen.
  - Währungen werden nach Umrechnung der Untereinheiten verglichen (`GBp`/`GBX` → GBP, `ZAc` → ZAR, `ILA` → ILS);
    der Kandidat behält die gelieferte Währung.
  - Reihenfolge: festgestellte MIC, dann Währung, dann ISIN. Nichts wählt den ersten Treffer:
    `uniqueCandidateOnMic(mic, tickerHint)` liefert nur einen Kandidaten, wenn genau ein Listing auf der Börse liegt,
    allenfalls eingeengt über den Ticker (`NOVN.SW`, nicht `NOVNEE.SW`). Zwei Handelswährungen eines Listings ohne
    abgefragte Währung bleiben mehrdeutig.
- **Börsenzuordnung:** `mic_provider_map` (ein Anbietercode und Symbolsuffix je MIC) wird unverändert verwendet. Ein
  Code oder Suffix wird nur dann zu einer MIC, wenn er auf genau eine MIC zeigt; sonst bleibt die MIC unbekannt. Ein
  fehlendes Mapping ist kein Fehler. Segment-MICs werden auf die Betreiber-MIC zurückgeführt (`MarketSegmentMics`):
  OpenFIGI kennt NASDAQ-Listings nur als XNGS/XNMS/XNCM, der Yahoo-Code `NMS` zeigt auf XNMS.
- **Betrieb:** `SecurityDataService` fragt parallel unter einer gemeinsamen Frist; ein Anbieter, der scheitert, wirft
  oder die Frist überschreitet, liefert seinen Status, die Ergebnisse der anderen bleiben erhalten.

### 4.4 Stammdaten

`getSecurityBaseData` liefert `SecurityBaseDataResult(providerId, status, SecurityBaseDataDto data, detail)`.

`SecurityBaseDataDto` (`grafioschtrader-common`, mit `@Schema`) ist anbieterneutral:

| Feld | Bedeutung |
|---|---|
| `providerId`, `providerInstrumentId` | Herkunft und verwendeter Schlüssel |
| `isin`, `currency`, `mic` je mit `IdentityEvidence` | Identität, wie der Detailendpunkt sie liefert |
| `name`, `tickerSymbol` | Name und Börsenkürzel |
| `issuerCountry` | Land des Emittenten bzw. Domizil eines Fonds |
| `activeFromDate` | erster Handelstag des Instruments, nur wenn der Anbieter genau das liefert |
| `distributionFrequency` | als `DistributionFrequency`, nur wenn der Anbieter eine Frequenz liefert |
| `denomination` | Stückelung einer Anleihe |
| `productLink` | stabile Detailseite des Anbieters |
| `providerAttributes` | `Map<String, String>` mit allen übrigen Rohwerten, z. B. `assetClass`, `secType`, `productLine`, `investmentRegion`, `replicationMethod`, `managementFee`, `fundCurrency` |

Regeln:
- Ein Adapter setzt ein GT-Feld nur, wenn der Anbieterwert genau dessen Bedeutung hat. Alles andere landet nur in
  `providerAttributes`. Die Abbildung von Rohwerten auf GT-Begriffe (Anlageklasse, Unterkategorie) geschieht in §7,
  nicht im Adapter.
- Widerspricht eine festgestellte Identität des Detailendpunkts dem Kandidaten, wird das Ergebnis verworfen
  (`NO_MATCH` mit Detail).
- Der Adapter schreibt nie in ein `Security`. Die Übernahme macht der Lookup-Service (§8), der bestätigte Werte nie
  überschreibt und jedem übernommenen Feld seine Herkunft mitgibt.

**SIX** (`SixSecurityData`): Schlüssel ist die ValorId aus der Suche (ISIN + Währung + Segmentziffer, z. B.
`IE00B27YCF74USD4`). Der Endpunkt richtet sich nach der `ProductLine` des Kandidaten:

| ProductLine | Endpunkt | Wichtige Felder |
|---|---|---|
| `ET` (ETF/ETP) | `sheldon/fund_details/v2/{valorId}/fund/info.json` | `general.valorSymbol`, `general.isin`, `general.tradingCurrency`, `general.exchange`, `general.secType`, `general.assetClass`, `general.domicileOfFund`, `general.investmentRegion`, `general.replicationMethod`, `general.dividendEntitlement`, `underlying`, `fundCurrency`, `managementFee` |
| Aktien (`BC`, `PS`, …) | `sheldon/share_details/v3/{valorId}/share/info.json` | `valorSymbol`, `isin`, `tradingCurrency`, `securityType`, `issuedBy`, `nominalValue`, `dividendEntitlement`, `indexSymbols` |
| `DB` (Anleihen) | noch zu bestimmen | die geprüften `bond_details`-Pfade leiten auf eine 404-Seite um |

Die Antwort hat die Form `{"status":"Ok","totalCount":1,"itemList":[…]}`. Aus SIX werden **nicht** als GT-Feld
übernommen:
- `firstTradingDate` – bei Novartis `20201207`, obwohl die Aktie seit 1996 gehandelt wird; er bezeichnet das aktuelle
  SIX-Listing, nicht den ersten Handelstag. Nur Rohattribut.
- `countryCode` der Aktie – bei Novartis `US`; nicht das Emittentenland. Nur Rohattribut.
- `dividendEntitlement` ist ein Ja/Nein-Wert und keine Frequenz.
`domicileOfFund` wird bei Fonds als `issuerCountry` übernommen.

Weitere Anbieter mit Detailendpunkt (Xetra, Yahoo) folgen demselben Muster und werden einzeln geprüft, bevor sie ein
GT-Feld setzen.

### 4.5 Schlüsselfreie Anbieter

| Anbieter | Endpunkt | Evidenz und Grenzen |
|---|---|---|
| Yahoo | öffentliche Symbolsuche | ISIN und Währung bleiben unbekannt; die MIC kommt aus dem Mapping der abgefragten MIC (Code oder Suffix), sonst aus einem Code/Suffix, der auf genau eine MIC zeigt – Novartis kommt als `EBS` zurück, nur `.SW` legt XSWX fest. Ausweichsuche über den Ticker, wenn die ISIN-Suche keinen Kandidaten auf der abgefragten Börse liefert. Ein Anfragebudget für alle Yahoo-Suchen der Anwendung. |
| OpenFIGI | `POST /v3/mapping` ohne Schlüssel (25 Anfragen/Minute) | ISIN, MIC und Währung `FILTERED`; FIGI als Instrument-Id. Ein Request enthält je MIC-Variante einen Job und einen ungefilterten Job, der „ISIN unbekannt“ von „kein Listing an dieser Börse“ unterscheidet. |
| SIX | FQS `itf/fqs/delayed/movie.json?where=ISIN=` | Börsendaten, daher ISIN, Währung und MIC (XSWX) `RETURNED`; eine Zeile je Handelswährung; ValorId als Instrument-Id. Nur für XSWX oder unbekannte MIC. Stammdaten nach §4.4. |
| Xetra | Börse Frankfurt `tradingview/search` + Deutsche Börse `price_information/single` | Ein `MIC:ISIN`-Symbol je Listing (XETR/XFRA), daher ISIN und MIC `RETURNED`; die Währung aus der Preisinformation von höchstens zwei Listings der abgefragten Börse, sonst unbekannt. Nur für XETR/XFRA oder unbekannte MIC. |

SIX und Xetra dienen hier als Quellen für Listing und Stammdaten; die Ableitung ihrer URL-Erweiterungen bleibt im
Connector (§6).

## 5 Connector-Profil (YAML)

- **Speicherung:** globalparameter `gt.connector.profile` in `property_blob`. Schreibzugriffe nur für Admins
  (`GlobalparametersJpaRepositoryImpl.saveOnlyAttributes`). Ein eigener GET/PUT-Endpunkt validiert. Keine neue
  Entität.
- **Validierung:** neues `YamlConfigurationValidation.Format.CONNECTOR_PROFILE` mit JSON-Schema
  `resources/schemas/connector-profile-schema.json`, ins Frontend übertragen durch `frontend/scripts/sync-yaml-schemas.mjs`.
  Semantik: jede Connector-Id existiert; eine Feed-Liste nennt nur Connectoren, die diesen Feed unterstützen.
- **Editor:** Admin-Menüeintrag mit `YamlEditorComponent`.
- **Struktur:**

```yaml
preferFree: true          # bei gleichem Rang Feeds ohne kostenpflichtiges Abonnement bevorzugen
rules:                    # von oben nach unten; je Feed gewinnt die letzte passende Regel
  - when: {}              # Auffangregel
    history: [yahoo]
    intra: [yahoo]
    dividend: [divvydiary, yahoo]
    split: [yahoo]
  - when: { mic: [XSWX] }
    history: [six, yahoo]
    intra: [six, consorsbank, comdirect, finanzench]
    dividend: [divvydiary, six]
    split: [yahoo]
  - when: { mic: [XETR, XFRA] }
    history: [xetra, yahoo]
    intra: [xetra, yahoo]
  - when: { categoryType: [FIXED_INCOME], specialInvestmentInstrument: [DIRECT_INVESTMENT] }
    dividend: []
    split: []
  - when: { specialInvestmentInstrument: [NON_INVESTABLE_INDICES, CFD] }
    dividend: []
    split: []
importExchange:           # nur wenn der Import keine Börse liefert
  - when: { currency: [CHF] }
    mic: XSWX
  - when: { currency: [EUR], isinCountry: [DE] }
    mic: XETR
probe:
  priceTolerancePercent: 5
  maxProbesPerLookup: 8
```

- **Kandidatenfilter:** Connector-Ids sind kurz (`six`, ohne `gt.datafeed.`). Kandidaten werden immer mit
  `isActivated()` und `supports(mic, country, act, sii)` gefiltert; ein Connector mit Schlüsselpflicht ohne
  gespeicherten Schlüssel erscheint nie. Ein gespeicherter Schlüssel beweist keine Berechtigung für einen Feed oder
  Markt.

## 6 Deterministische URL-Erweiterung

- Neue Default-Methode auf `IFeedConnector`: `Optional<String> deriveUrlExtension(Security draft, FeedSupport fs)`.
- Implementierungen:
  - SIX: `{isin}{currency}4`, `9` für Indizes und CFD;
  - Xetra / Eulerpool: die ISIN;
  - Yahoo: das eindeutig aufgelöste Symbol aus PROVIDER_SEARCH und das Börsenmapping; keine Netzwerksuche in der
    Ableitungsmethode;
  - Connectoren mit nicht-`_URL`-`FeedIdentifier` brauchen nichts.
- Ein Suchergebnis wird je Connector und Feed getrennt in eine URL-Erweiterung umgerechnet; das Symbol eines Anbieters
  wird nie für einen anderen verwendet.
- Connector-Probes sind für PROFILE, GTNET, PROVIDER_SEARCH und AI gemeinsam. Reine Metadaten reichern einen
  Kandidaten an, ohne einen Feed als geprüft auszuweisen.

## 7 Deterministische Feldableitung (DERIVATION)

Läuft nach den Connector-Probes und vor der KI. Ein abgeleiteter Wert füllt nur ein noch offenes Feld und überschreibt
nie einen bestätigten.

- **`activeFromDate`:** Datum des ersten Kurses, den der geprüfte History-Connector für das Listing liefert. Vor
  diesem Kurs wird kein Preis erzeugt.
- **`distributionFrequency`:** aus der Dividendenhistorie des geprüften Dividenden-Connectors über die letzten drei
  vollen Jahre. Die durchschnittliche Zahl Zahlungen pro Jahr ergibt `DF_YEAR` (1), `DF_SEMI_ANNUAL` (2),
  `DF_QUARTERLY` (4) oder `DF_MONTHLY` (12); keine Zahlung im ganzen Zeitraum `DF_NONE`, eine unregelmässige Zahl
  `DF_AD_HOC`. Eine Historie unter einem Jahr lässt das Feld offen. Bei Anleihen gilt die Couponfrequenz nur, wenn
  eine Quelle sie liefert.
- **Anlageklasse:** explizite, unit-getestete Abbildung von `(providerId, Rohtyp)` auf categoryType /
  specialInvestmentInstrument – z. B. Yahoo `EQUITY`/`ETF`, OpenFIGI `Common Stock`/`ETP`, SIX ProductLine
  `BC`/`ET`/`DB`, SIX `general.secType`, Xetra `Aktie`/`ETP`. Widersprechen sich Anbieter, bleibt das Feld offen.
- **Unterkategorie:** Hinweise aus `providerAttributes` (z. B. SIX `assetClass`, `investmentRegion`) dürfen auf eine
  bestehende Unterkategorie abgebildet werden, wenn die Abbildung eindeutig ist; sonst bleibt das Feld offen.
- `denomination` und `productLink` bleiben Lücken, wenn weder GTNet noch ein Anbieter sie liefert.
- Jedes abgeleitete Feld trägt seine Regel als Herkunft, damit der Dialog zeigen kann, warum ein Wert vorgeschlagen
  wird.

## 8 Lookup-Service

`SecurityLookupService` (`grafioschtrader-server`, Paket `security.lookup`).

- **Eingabe:** ISIN, Währung, MIC (optional), Ticker / Name (optional), optional Referenzpreis und -datum (Import).
- **Ausgabe:** Liste von `SecurityGtnetLookupDTO`, erweitert um
  - `lookupSource` (PROFILE / GTNET / PROVIDER_SEARCH / PROVIDER_BASE_DATA / DERIVATION / AI) und Herkunft je Feld;
  - gereihte Alternativen je Feed `connectorAlternatives[feed] = [{connectorId, urlExtension, probeStatus,
    sampleDate, sampleClose}]`;
  - die Status aller befragten Anbieter.
- **Zusammenführung:** GTNet, Suche und Stammdaten füllen fehlende Felder, abgeleitete Werte nur die danach noch
  offenen; das Profil reiht die Connectoren. Bestätigte Eingabeidentität bleibt erhalten. Die KI darf nur offene
  Felder und Connector-Alternativen vorschlagen.
- Widersprüchliche ISIN/Währung/MIC werden verworfen, fehlende Metadaten bleiben unbekannt. Mehrdeutige Listings
  bleiben zur Auswahl; der automatische Import verlangt genau einen ausreichend geprüften Kandidaten mit allen
  Pflichtfeldern.
- Anfragen sind durch Timeouts und Budgets begrenzt; Teilergebnisse und Anbieterstatus werden zurückgegeben.
- Die Verfügbarkeit einer Suche ist unabhängig von der Aktivierung eines Preis-Connectors: OpenFIGI braucht keinen
  Preis-Connector, und Zugang zur EODHD-Suche beweist keinen Zugang zu deren Kursfeeds.
- **GTNet-Ergebnisse** durchlaufen denselben `isActivated` / `supports` / Regex-Filter; das behebt
  `GTNetSecurityLookupService.findLocalConnector`.

## 9 KI-Fallback

Wird erst nach der Messung des Lookups ohne KI (§14) umgesetzt und nur für verbleibende Pflichtfelder oder
Connector-Lücken aufgerufen – nie, nur weil ein weiterer Anbieter verfügbar ist.

### 9.1 Zwei Modi

Gewählt aus der Lückenliste des Lookups ohne KI:
- **Nur Klassifizierung**, wenn nur Klassifizierungsfelder offen sind (Unterkategorie, Anlageklasse wo §7 sie offen
  liess): ein Aufruf **ohne Werkzeuge und ohne Websuche**. Eingabe: bestätigter Name, Rohtypen und
  `providerAttributes` der Anbieter, Emittentenland und die lokale Anlageklassenliste; Ausgabe: eine Wahl aus dieser
  Liste, nachgeprüft wie jedes Feld. Einige hundert Token statt einer Recherche mit mehreren Tool-Runden.
- **Recherche** für alle anderen Lücken (Identitätsmetadaten, Daten, Stückelung, Connector-Lücken) mit den Werkzeugen
  aus §9.4. Identität, URL-Erweiterungen und Daten werden im Klassifizierungsmodus nie erfragt, weil das Modell sie
  ohne Suche nur raten könnte.

### 9.2 Bibliothek und Anbieter

- Spring AI 2.0.x (Linie für Spring Boot 4.0/4.1) über `spring-ai-bom` in `backend/pom.xml`, Version zum
  Umsetzungszeitpunkt. In `grafioschtrader-server` nur die Modellmodule `spring-ai-anthropic`, `spring-ai-openai`,
  `spring-ai-ollama`; **keine Boot-Starter und keine `spring.ai.*`-Properties**. Anbieter, Modell und Schlüssel sind
  GT-Konfiguration, die sich zur Laufzeit ändert; das `ChatModel` wird programmatisch gebaut und bis zur nächsten
  Konfigurationsänderung gecacht – ohne Neustart.
- `gt.ai.provider` (int-Enum): 0 aus; 1 Anthropic (`AnthropicChatModel`); 2 OpenAI-kompatibel (`OpenAiChatModel` mit
  konfigurierbarer Basis-URL: OpenAI, xAI, Mistral, LM Studio, vLLM, llama.cpp-Server); 3 Ollama (`OllamaChatModel`,
  lokal, ohne Schlüssel).

### 9.3 Konfiguration

- globalparameters: `gt.ai.provider`, `gt.ai.model` (String), `gt.ai.web.search` (int-Enum: 0 vom Anbieter gehostet,
  1 GT-Werkzeug), maximale Tool-Aufrufe, Timeout in Sekunden. Nur Anthropic erhält ein Standardmodell
  (`claude-opus-5-5`); sonst muss der Admin eines setzen.
- Endpunkte sind Deployment-Properties, nicht vom Admin editierbar: `gt.ai.openai.base-url`, `gt.ai.ollama.base-url`,
  `gt.ai.web.search.searxng-url`. Ein Endpunkt im eigenen Netz ist eine Betriebsentscheidung; eine Admin-editierbare
  URL liesse GT auf beliebige interne Hosts zeigen.
- Schlüssel in `connector_apikey` mit id_provider `ai.anthropic` / `ai.openai`: Migration auf `api_key varchar(512)`
  und ein zweiter `IConnectorApiKeyReset`-Beitrag, damit der Admin-Schlüsseldialog die KI-Anbieter anbietet. Ollama hat
  keinen Schlüssel; bei einem lokalen OpenAI-kompatiblen Server ist die Schlüsselzeile optional.
- **Aktiv** = Anbieter ≠ 0 UND (kein Schlüssel nötig ODER Schlüsselzeile vorhanden). Der Recherchemodus verlangt
  zusätzlich einen Websuchpfad; der Klassifizierungsmodus nicht.
- Das Frontend-Feature-Flag `AI_ASSIST` wird beim Login über `FeatureTypeGT` gesetzt, wie GTNET über
  `FeatureTypeBase`.

### 9.4 Websuche und Werkzeuge (nur Recherchemodus)

- Ohne Websuche hat das Modell nur seine Trainingsdaten und rät die Metadaten einer ISIN; der Recherchemodus wird ohne
  funktionierenden Suchpfad nie angeboten.
- **Vom Anbieter gehostet** (`gt.ai.web.search` = 0): Anthropic über `AnthropicWebSearchTool` mit `max_uses`. Für
  OpenAI prüfen, ob die verwendete Spring-AI-Version OpenAIs gehostetes `web_search` anbietet; sonst nutzt Anbieter 2
  das GT-Werkzeug.
- **GT-Werkzeug** (`gt.ai.web.search` = 1, Pflicht für Ollama und OpenAI-kompatible Server ohne gehostete Suche):
  - `web_search(query)` liefert höchstens N Ergebnisse (Titel, URL, Ausschnitt) aus einem `WebSearchBackend`; erste
    Implementierung: JSON-API eines selbst betriebenen SearXNG, ohne Schlüssel;
  - `fetch_page(url)` liefert die Seite als Text, auf eine Grösse gekürzt. Nur http/https; die aufgelöste Adresse darf
    nicht Loopback, Link-Local oder privat sein; Timeout und Grössenlimit.
  - Beide zählen gegen das Tool-Aufruf-Limit.
- **`probe_connector(feed, connectorId, urlExtension)`:** GT baut ein transientes, ungespeichertes `Security` und
  führt die Regex-Prüfung und dann `getEodSecurityHistory` (letzte ~10 Tage oder um das Referenzdatum) /
  `updateSecurityLastPrice` / `getDividendHistory` / `getSplitHistory` aus; Rückgabe ok/Fehler und ein Beispielwert.
  Nur die für den Kontext angebotenen Connectoren sind aufrufbar. API-Schlüssel und Mandantendaten erreichen das Modell
  nie.

### 9.5 Agent

- Schnittstelle `AiResearchClient`, eine dünne GT-Schicht über Spring AIs `ChatClient`. Sie besitzt Prompt,
  Werkzeugregistrierung, Ergebnistyp und Nachprüfung; der übrige GT-Code referenziert Spring AI nie.
- Werkzeuge als Spring-AI-Tool-Callbacks (`@Tool`-Methoden) auf einem Objekt je Lookup; es hält den Kontext und zählt
  Aufrufe – nach Erreichen des Limits liefert jedes Werkzeug ein Fehlerergebnis.
- Ergebnis über Structured Output (`.entity(AiSecurityProposal.class)`); eine nicht lesbare Antwort beendet den Lookup
  ohne Kandidaten.
- Anthropic-spezifisch (`effort: low`/`medium`, Stop-Gründe `refusal` und `pause_turn`) über `AnthropicChatOptions`
  und die Antwortmetadaten, geprüft gegen das Javadoc der verwendeten Spring-AI-Version. Eine Verweigerung oder ein
  unvollendeter Turn beendet den Lookup mit Status und ohne Kandidaten.
- Der Timeout umschliesst den ganzen Agent-Aufruf.
- **Prompt-Eingabe:** ISIN, Währung, MIC, die gereihte Connector-Liste mit dem Hilfetext jedes Connectors
  (`BaseFeedConnector.getDescription()`), die lokale Anlageklassenliste, die bestätigten Felder und die ausdrückliche
  Lückenliste. Rohe Anbieterantworten und Kandidatenlisten bleiben draussen.
- **Ausgabefelder:** Name, Ticker, categoryType / sii / Unterkategorie, distributionFrequency, denomination,
  activeFromDate, productLink, issuerCountry und URL-Erweiterungen je Feed.
- **Nachprüfung durch GT** (harte Ablehnung oder Feld verwerfen) – sie, nicht die Modellwahl, macht einen Kandidaten
  vertrauenswürdig: ISIN / Währung / MIC unverändert; Name 2..80, Ticker ≤ 6; Enums gültig; Connector aus der
  angebotenen Liste, Regex ok, Probe ok; mit Referenzpreis |close − price| / price ≤ Toleranz (Prozentkurs bei
  Anleihen mit `per`-Flag).
- **Limits:** neue DAY_CUD-Pseudoentität `AiSecurityLookup`, registriert in `LimitKeyConfig` und geseedet in der
  Produktionsmigration und in `V4__seed_entity_limits.sql` (Skill `new-entity`). Eine fehlende Zeile bedeutet
  unbegrenzte Kosten. Das Limit gilt auch für lokale Anbieter, wo es die Serverlast begrenzt.
- **Logging:** Identität, Anbieter, Modell, Modus, Tokenverbrauch (falls gemeldet) und Status; nie Schlüssel,
  Suchergebnisse oder Seiteninhalt. Suchergebnisse und Seiteninhalt sind Daten, nie Anweisungen.

## 10 Bearbeitungsdialog

`frontend/src/app/shared/securitycurrency/security-edit.component.ts`.
- Der GTNet-Lookup-Dialog wird zum Dialog „Wertpapier-Lookup“ verallgemeinert: Spalte `lookupSource`, je Feed eine
  Auswahl der gereihten Alternativen mit Probe-Status, Herkunft je Feld.
- Die Schaltfläche ist verfügbar, sobald ISIN, Währung und Börse gesetzt sind.
- Anfragen: PROFILE + GTNET + PROVIDER_SEARCH + PROVIDER_BASE_DATA + DERIVATION in einem begrenzten Request ohne KI,
  der Teilergebnisse behält; die KI nur für offene Lücken in einem zweiten, abbrechbaren Request mit eigenem Spinner
  (nur mit `AI_ASSIST`).
- Übernahme über die bestehenden `applyGtnetLookupData` / `applyMatchedConnectors`; gespeichert wird über den normalen
  Dialog. NLS-Schlüssel nur in den Backend-Bundles.

## 11 Import

- Menü `CREATE_GTNET_IMPORT_FROM_MISSING` → `CREATE_SECURITY_IMPORT_FROM_MISSING`, sichtbar, sobald der Lookup einen
  Auflösungsweg hat, auch PROFILE/PROVIDER_SEARCH ohne GTNet und KI.
- Tabellen `gt_net_security_imp_*` und Task `GTNET_SECURITY_IMPORT_POSITIONS` bleiben; ihr Javadoc sagt, dass sie
  allen Lookup-Quellen dienen.
- Je Position: (a) PROFILE, GTNet wenn betriebsbereit, PROVIDER_SEARCH, PROVIDER_BASE_DATA und DERIVATION; (b) nur
  für verbleibende Pflichtfelder oder Connector-Lücken die optionale KI.
- Börsenkandidaten aus expliziten Anbieter-MIC-Mappings und `importExchange` des Profils (einschliesslich
  `country_code` der Handelsplattform) als Präferenz. Währungs-/Länderheuristik oder ein Sammelcode wie `US` allein
  bestätigen keine MIC. Vor der automatischen Anlage muss genau ein unterstütztes Listing feststehen; mehrdeutige
  Ergebnisse bleiben ungelöst.
- Referenzpreis ist der Kurs der Importposition an ihrem Transaktionsdatum.
- Positionen ohne auflösbare MIC scheitern; der Rückfall auf die „erste Börse“ entfällt.
- Anlage über `SecurityJpaRepository.saveOnlyAttributes` (damit `validateConnectorSupports` greift).
- `gt_net_security_imp_gap.id_gt_net` wird NULL-fähig; neue `GapCodeType`-Werte für ungelöste oder mehrdeutige
  Identität, nicht verfügbare Anbieter und ungeprüfte Connectoren – unabhängig davon, ob die KI lief.
- Die bestehenden Budgets `GTNetSecurityImport` / `KEY_DAY_GTNET_SECURITY_IMPORT` begrenzen weiterhin die angelegten
  Wertpapiere.
- `ImportTransactionPos.findByIsinAndCurrencyWithNoSecurity` erhält einen Mandantenfilter.

## 12 Migration

Das nächste Flyway-Skript der Pom-Version zum Umsetzungszeitpunkt, idempotent: globalparameters-Zeilen mit
`input_rule`, das geseedete Profil, `api_key varchar(512)`, `id_gt_net` NULL-fähig, `entity_limit`-Seed.

## 13 Anbieter mit Schlüssel

| Anbieter | Rolle und Grenzen | Zugang |
|---|---|---|
| Finnhub | Symbolsuche, deren [API-Schema](https://github.com/Finnhub-Stock-API/finnhub-go/blob/master/api/openapi.yaml) Symbol, Name, ISIN oder CUSIP annimmt. Details nur, wo der Zugang es erlaubt; nicht jedes Ergebnis bestätigt Währung und MIC. | konfigurierter Schlüssel; Endpunkt-/Marktabdeckung für den tatsächlichen Plan prüfen und dokumentieren |
| Alpha Vantage | [SYMBOL_SEARCH](https://www.alphavantage.co/documentation/#symbolsearch) für Ticker/Name; eine Ausweichquelle, kein ISIN-zu-MIC-Mapping. Meldet Ratenlimits mit HTTP 200 und einem Feld `Information`/`Note`, das `RATE_LIMITED` ergeben muss. | konfigurierter Schlüssel; Kontolimits beachten |
| EODHD | [Search API](https://eodhd.com/financial-apis/search-api-for-stocks-etfs-mutual-funds) für ISIN/Name/Ticker | nie Voraussetzung; ein Such-Token beweist keine Berechtigung für Kurs- oder Intradaydaten |

- **Ausdrückliches Einschalten je Anbieter** (globalparameter). Ein gespeicherter Schlüssel allein aktiviert die Suche
  nicht, weil sie dasselbe Tageskontingent wie der Preis-Connector verbraucht.
- `MISSING_CREDENTIALS` für einen eingeschalteten Anbieter ohne Schlüssel; der Admin-Schlüsseldialog bietet reine
  Suchschlüssel an (Erweiterung des `IConnectorApiKeyReset`-Beitrags). Ein optionaler OpenFIGI-Schlüssel hebt dessen
  Limits.
- **Sammelcodes** wie `US` (EODHD, Finnhub-Symbole ohne Suffix) werden nie einer einzelnen MIC gleichgesetzt.
  `mic_provider_map` kennt einen Code je MIC und keinen Sammelcode; sie wird so erweitert, dass ein Code für mehrere
  MICs die MIC verträglich, aber nicht festgestellt macht.

## 14 Messung vor Profil und KI

- Read-only-Skript `scripts/derive-connector-profile.mjs` (`--user/--password/--database`, Client-Auflösung wie in
  `scripts/export-generic-connectors.mjs`): gruppiert die aktiven, nicht abgeleiteten Wertpapiere nach MIC ×
  categoryType × specialInvestmentInstrument × den vier Connectoren, erzeugt ein vorgeschlagenes YAML und meldet die
  **Trefferquote** – wie viele bestehende Wertpapiere Profil + Ableitung exakt reproduzieren, je Feed und MIC (nur
  XSWX, XNAS, XNYS und XETR sind statistisch aussagekräftig). Der Benutzer führt es gegen die Produktionsdaten aus; der
  Flyway-Seed verwendet die Ausgabe.
- Read-only-JUnit-Berichtsklasse über alle aktiven, nicht abgeleiteten Wertpapiere dieser vier Börsen:
  - Suche: eindeutige, mehrdeutige und nicht gefundene Listings je MIC und Anbieter;
  - je Pflichtfeld (Name, Anlageklasse, Unterkategorie, `activeFromDate`, `distributionFrequency`, `denomination` bei
    Anleihen) und MIC: welcher Schritt es füllte und wie oft der Wert mit dem gespeicherten übereinstimmt;
  - der Anteil der Wertpapiere, deren Pflichtfelder **alle** ohne KI aufgelöst sind – die Lookups ohne Token;
  - die verbleibenden Lücken je Feld; sie bestimmen, welcher KI-Modus (§9.1) gebraucht wird.

## 15 Etappen

1. Umbenennung (§4.1) und Stammdaten mit SIX (§4.4), einschliesslich Bestimmung des SIX-Anleihenendpunkts.
2. Messung (§14) und Connector-Profil (§5), deterministische URL-Erweiterung (§6) und Feldableitung (§7).
3. Lookup-Service (§8), Bearbeitungsdialog (§10), Import (§11), Migration (§12).
4. KI-Fallback (§9).
5. Anbieter mit Schlüssel (§13).

## 16 Verifikation

- Trefferquote und Abdeckungsbericht aus §14 liegen vor, bevor das Profil geseedet und die KI umgesetzt wird.
- Unit-Tests:
  - Stammdaten: SIX-Fonds- und Aktienantwort als Fixture, Abbildung nur der zulässigen Felder, `firstTradingDate` und
    `countryCode` nur als Rohattribut, Identitätswiderspruch → `NO_MATCH`, Kandidat eines anderen Anbieters →
    `UNSUPPORTED_QUERY`, HTTP 429/Timeout als Status;
  - Profilregel-Auflösung; `deriveUrlExtension` (SIX / Xetra) und Umrechnung von Suchergebnissen in feedspezifische
    Erweiterungen;
  - Feldableitung: `activeFromDate` aus dem ersten gelieferten Kurs; Dividendenzahl → `distributionFrequency`
    einschliesslich Historie unter einem Jahr und unregelmässiger Zahl; Rohtyp → Anlageklasse und widersprechende
    Anbieter; ein abgeleiteter Wert überschreibt nie einen bestätigten;
  - Zusammenführung behält bestätigte Identität und Herkunft; nicht verfügbare Anbieter hinterlassen Teilergebnisse;
  - Moduswahl: nur Klassifizierungslücken → ein Aufruf ohne Werkzeuge; andere Lücke → Recherche; keine Lücke → kein
    Modellaufruf; mehrdeutige Listings lösen nie eine automatische Anlage aus;
  - Nachprüfung lehnt ab (falsche ISIN, falsche Regex, gescheiterter Probe, Preis ausserhalb der Toleranz);
  - Aktiv-Regel je Anbieter (Schlüssel nötig / nicht nötig; ohne Suchpfad nur Klassifizierungsmodus);
  - `fetch_page` lehnt Nicht-http(s) und Adressen auf Loopback, Link-Local oder privat ab;
  - Tool-Aufruf-Limit: danach liefert jedes Werkzeug ein Fehlerergebnis.
- Manuell auf `grafioschtrader_t`: Bearbeitungsdialog für einen XSWX-ETF, eine XNAS-Aktie und eine SIX-Anleihe, KI aus
  und an; ein Import mit zwei unbekannten ISINs auf einer Instanz ohne GTNet und ohne KI. Vollständig aufgelöste
  Positionen sind anlegbar, unvollständige oder mehrdeutige melden ihre Lücken.
- Manuell ohne kostenpflichtigen Zugang: alle Anbieter mit Schlüssel aus, Lookup über Yahoo/OpenFIGI/SIX/Xetra ohne
  GTNet und KI; kein nicht verfügbarer Kursfeed wird als geprüft angeboten.
- Manuell mit KI: das Log zeigt Modus und Tokenverbrauch je Lookup; ein Wertpapier, dem nur die Unterkategorie fehlt,
  wird im Klassifizierungsmodus ohne Tool-Aufruf aufgelöst.
- Manuell mit lokalem Modell: dieselben drei Wertpapiere mit Ollama (werkzeugfähiges Modell) und dem GT-Suchwerkzeug
  gegen ein selbst betriebenes SearXNG. Das Log zeigt `web_search` / `fetch_page` / `probe_connector` und weder
  Schlüssel noch Seiteninhalt; jeder akzeptierte Kandidat hat die Nachprüfung bestanden; SearXNG gestoppt → der Lookup
  endet mit Status, keine Exception erreicht den Dialog.
- Manuell Anbieterwechsel: `gt.ai.provider` von Anthropic auf Ollama und zurück ohne Neustart.
- `mvn clean install -DskipTests` endet ohne `[WARNING]`-Compilerzeilen.

## 17 Entscheidungen

| Thema | Entscheidung |
|---|---|
| Suche und Preisfeed | getrennt: `ISecurityDataProvider` ist unabhängig von `IFeedConnector`; nicht jeder Preis-Connector sucht, ein Metadatenanbieter liefert keine Preise |
| Schnittstellenschnitt | eine Schnittstelle für Suche und Stammdaten; `getSecurityBaseData` als Default-Methode, `UNSUPPORTED_QUERY` wo ein Anbieter keine hat |
| Rückgabe der Stammdaten | anbieterneutrales DTO mit Rohattributen; die Übernahme ins Wertpapier macht der Lookup-Service |
| Basis ohne Schlüssel | Yahoo, OpenFIGI, SIX, Xetra; kein Schlüssel, kein Abonnement, kein GTNet und keine KI nötig |
| Anbieter mit Schlüssel | nur nach ausdrücklichem Einschalten, weil sie das Tageskontingent der Preisfeeds teilen |
| Börsenzuordnung | `mic_provider_map` unverändert; Segment-MICs auf die Betreiber-MIC; Sammelcodes erst mit den Anbietern mit Schlüssel |
| Nicht zuverlässige Anbieterwerte | nur als Rohattribut (SIX `firstTradingDate`, `countryCode`) |
| KI | nur für Lücken; Klassifizierungsmodus ohne Werkzeuge, Recherchemodus nur mit Suchpfad |
| KI-Endpunkte | Deployment-Properties, nicht Admin-editierbar |

## 18 Nicht im Umfang

Ein MCP-Server, Währungspaare, ein KI-Schlüssel pro Benutzer.
