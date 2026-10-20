# Zusätzliche kostenlose Quellen für Fundamentaldaten

**Code baseline:** backend 0.38.3, highest Flyway script `V0_38_4__watchlist_indicator.sql`.

**Recherchezeitpunkt:** 20. Oktober 2026.

## Ziel und Bewertungsgrundlage

Zusätzliche Quellen sollen die Abdeckung und Überprüfbarkeit der in `security_fundamental` gespeicherten
Fundamentaldaten verbessern. Vorrang haben kostenlos automatisiert abrufbare Daten mit nachvollziehbarer
Währung, Berichtsperiode und Aktienbasis.

Die Bewertung beruht auf dem Quellcode und den öffentlich zugänglichen Anbieterunterlagen. Eine neue
Abdeckungsmessung gegen den Wertpapierbestand von Grafioschtrader wurde für diese Analyse nicht durchgeführt.
Tarife, Zugriffslimits und Nutzungsbedingungen sind vor einer Implementierung erneut zu prüfen.
Ein kostenloser Abruf bedeutet insbesondere bei kommerziellen Anbietern keine uneingeschränkte Erlaubnis
zur Anzeige für andere Nutzer oder zur Weiterverteilung.

## Ausgangspunkt im Code

Produktiv sind die Fundamentaldatenquellen `YAHOO`, `FINNHUB` und `EULERPOOL` vorgesehen.
`FundamentalDataLoadService` lädt die verfügbaren Quellen über `IFundamentalFeedConnector`; die
Konfiguration `gt.fundamental.sources` bestimmt die aktivierten Quellen.

Relevante Dateien:

- [FundamentalSource.java](../backend/grafioschtrader-common/src/main/java/grafioschtrader/types/FundamentalSource.java)
- [FundamentalMetric.java](../backend/grafioschtrader-common/src/main/java/grafioschtrader/types/FundamentalMetric.java)
- [IFundamentalFeedConnector.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/connector/fundamental/IFundamentalFeedConnector.java)
- [FundamentalAnswerMapper.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/connector/fundamental/FundamentalAnswerMapper.java)
- [FundamentalDataLoadService.java](../backend/grafioschtrader-server/src/main/java/grafioschtrader/fundamental/FundamentalDataLoadService.java)

Für SEC EDGAR, SimFin und EODHD existiert bereits Erprobungscode im
[FundamentalSourceCoverageProbeTest](../backend/grafioschtrader-server/src/test/java/grafioschtrader/connector/fundamental/FundamentalSourceCoverageProbeTest.java).
Diese Probes liefern Ansatzpunkte für eine Evaluation, sind aber keine vollständigen produktiven Importer.

Die zu ergänzenden Basiswerte sind:

| Kennzahl | Bedeutung |
|---|---|
| `EPS_TTM` | Gewinn je Aktie der letzten zwölf Monate |
| `EPS_FY` | Gewinn je Aktie des letzten Geschäftsjahres |
| `BVPS` | Buchwert je Aktie |
| `SPS_TTM` | Umsatz je Aktie der letzten zwölf Monate |
| `SPS_FY` | Umsatz je Aktie des letzten Geschäftsjahres |
| `SHARES` | Ausstehende Aktien der betreffenden Aktienklasse; bei ADRs in ADR-Einheiten |
| `NET_DEBT` | Gesamtschulden abzüglich Cash, in vollen Währungseinheiten |

`PE_CHECK` und `MCAP_CHECK` dienen bestehenden Plausibilitätsprüfungen. Eine neue Quelle muss keine fertigen
Bewertungskennzahlen liefern: GT berechnet Verhältniszahlen aus Basiswerten und Kursen.

## Geeignete Kandidaten

### SEC EDGAR / Company Facts

Die SEC bietet öffentliche JSON-APIs ohne API-Schlüssel. Enthalten sind strukturierte Abschlussdaten von
SEC-Berichterstattern, darunter US-Unternehmen und ausländische Emittenten mit entsprechenden
Berichtspflichten. `companyfacts` liefert die verfügbaren standardisierten XBRL-Konzepte eines Unternehmens
in einer Anfrage; zusätzlich gibt es Bulk-Downloads.
Quelle: [SEC – EDGAR Application Programming Interfaces](https://www.sec.gov/search-filings/edgar-application-programming-interfaces).

**Einschätzung:** Erste Priorität für eine unabhängige zusätzliche US-Quelle. EPS und Abschlusspositionen
können die benötigten Kennzahlen direkt oder als Berechnungsgrundlage liefern. Die Abdeckung ausländischer
Unternehmen hängt von deren SEC-Berichterstattung ab; eine allgemeine Europa-Abdeckung ist damit nicht gegeben.

Für die Integration sind eine eindeutige Zuordnung des Wertpapiers zur CIK, die Auswahl passender
US-GAAP-/IFRS-Konzepte sowie die Behandlung von Berichtsperioden und nachträglichen Berichtigungen erforderlich.
Unternehmensweite Werte müssen auf die Aktienklasse beziehungsweise ADR-Basis des GT-Wertpapiers bezogen werden.

Der vorhandene
[SecEdgarProbe](../backend/grafioschtrader-server/src/test/java/grafioschtrader/connector/fundamental/SecEdgarProbe.java)
prüft bereits die Auffindbarkeit von Konzepten. Für einen Import muss aus dieser Verfügbarkeitsprüfung eine
fachlich geprüfte Auswahl zusammengehöriger Werte werden.

### SimFin

Der kostenlose Tarif nennt rund 5’000 US-Aktien, Daten-API und Bulk-Downloads sowie zwei API-Aufrufe pro
Sekunde. Eine breite aktuelle Europa-Abdeckung lässt sich aus diesem Angebot nicht ableiten.
Quelle: [SimFin – Preise und Leistungsumfang](https://www.simfin.com/en/prices/).

**Einschätzung:** Pragmatischer Kandidat für US-Aktien. Aufbereitete Abschlussdaten können den Aufwand
gegenüber SEC-Rohdaten reduzieren. Welche Felder und abgeleiteten Datensätze im kostenlosen Account tatsächlich
zugänglich sind, ist mit einem solchen Account zu messen.

Der vorhandene
[SimFinProbe](../backend/grafioschtrader-server/src/test/java/grafioschtrader/connector/fundamental/SimFinProbe.java)
fragt abgeleitete TTM-Daten ab. Er liefert keine Aktienanzahl und verwendet das Vorhandensein von `Total Debt`
als Verfügbarkeitssignal für `NET_DEBT`. Für einen produktiven Import muss der passende Cash-Bestand
abgezogen werden; Gesamtschulden dürfen nicht als Nettoverschuldung gespeichert werden.

### Alpha Vantage

Alpha Vantage nennt grundsätzlich 25 kostenlose API-Anfragen pro Tag. Dokumentiert sind Endpunkte für
Gewinnhistorie (`EARNINGS`), Erfolgsrechnung (`INCOME_STATEMENT`), Bilanz (`BALANCE_SHEET`) und Aktienanzahl
(`SHARES_OUTSTANDING`). `OVERVIEW` ist ausdrücklich als Premium-Endpunkt gekennzeichnet.
Quellen: [API-Dokumentation](https://www.alphavantage.co/documentation/),
[Kontingente und Open-Source-Angebot](https://www.alphavantage.co/support/).

**Einschätzung:** Geeignet für kleinere Bestände oder das gezielte Schliessen einzelner Datenlücken.
Mehrere Aufrufe je Unternehmen und das Tageslimit erfordern eine über mehrere Tage verteilte Aktualisierung.
Die tatsächliche Abdeckung der benötigten Börsenplätze und Felder ist zu messen.

Der Anbieter nennt Sonderkontingente für verifizierte Open-Source- oder Bildungsprojekte. Für Grafioschtrader
ist eine Anfrage sinnvoll; eine entsprechende Freigabe darf bei der Planung nicht vorausgesetzt werden.

### filings.xbrl.org / ESEF und UKSEF

Das Archiv bietet eine kostenlose öffentliche API und stellt Unternehmensberichte zusätzlich als
xBRL-JSON bereit. Es umfasst unter anderem ESEF- und UKSEF-Berichte. Die Betreiber weisen auf unvollständige
Abdeckung und ausdrücklich auf Beschaffungslücken für Deutschland und Irland hin. Der Index verwendet
typischerweise die LEI als Unternehmenskennung.
Quellen: [Datenumfang und Einschränkungen](https://filings.xbrl.org/docs/about),
[API-Dokumentation](https://filings.xbrl.org/docs/api).

**Einschätzung:** Interessanteste zusätzliche Spur für europäische Aktien, insbesondere für Jahreswerte
wie `EPS_FY` und `SPS_FY`. Eine gleichwertige Versorgung mit aktuellen TTM-Werten ist gesondert zu prüfen.

Der Integrationsaufwand liegt vor allem in der Zuordnung des Wertpapiers zum Unternehmen, der Auswahl der
IFRS-Konzepte, der Behandlung unternehmensspezifischer Erweiterungen und der Trennung von Konzern- und
Einzelabschlusswerten. Unterschiedliche Sprachfassungen und berichtigte Berichte müssen erkannt werden.

Die fehlende vollständige Deutschland-Abdeckung ist für die Bewertung des Zusatznutzens wesentlich.
Für Schweizer Aktien darf aus dem europäischen Schwerpunkt keine allgemeine Abdeckung abgeleitet werden.

### Direkte Investor-Relations-Daten

Unternehmen stellen Abschlüsse teilweise in maschinenlesbaren Formaten bereit. Nestlé veröffentlicht
beispielsweise Excel-Dateien zu den Jahresergebnissen.
Quelle: [Nestlé – Jahresergebnisse 2025](https://www.nestle.com/media/mediaeventscalendar/allevents/2025-full-year-results).

**Einschätzung:** Gezielter Lückenfüller für wichtige Schweizer oder europäische Titel.
Originaldaten sind gut nachvollziehbar, während unternehmensspezifische Downloadpfade, Dateiformate und
Definitionen laufende Pflege verursachen. Ein solcher Import eignet sich eher für einzelne wichtige
Wertpapiere als als allgemeiner Ersatz für eine strukturierte Anbieter-API.

## Zurückzustellende Angebote

| Angebot | Begründung |
|---|---|
| Financial Modeling Prep | Der kostenlose Tarif nennt 250 Aufrufe pro Tag, beschränkt zahlreiche Fundamentaldaten-Endpunkte jedoch auf ausgewählte Titel. Die allgemeine FAQ und die detaillierte Tarifmatrix vermitteln unterschiedliche Reichweiten. Die Matrix und ein Test mit dem vorgesehenen Account müssen die Bewertung bestimmen. |
| Twelve Data | Fundamentaldaten sind im kostenpflichtigen Grow-Tarif aufgeführt. Der kostenlose Basic-Tarif ist keine allgemein nutzbare kostenlose Fundamentaldatenversorgung. |
| StockAnalysis | Es gibt laut Anbieter keine öffentliche API. Die Nutzungsbedingungen schliessen automatisiertes Auslesen ausdrücklich aus. Frei lesbare Webseiten sind daher keine geeignete Grundlage für einen neuen automatischen Fundamentaldaten-Connector. |
| EODHD | Erprobungscode ist vorhanden. Aus dem kostenlosen Testzugang allein lässt sich keine allgemeine kostenlose Fundamentaldatenversorgung ableiten; vor einer weiteren Priorisierung sind die konkreten Berechtigungen zu prüfen. |

Quellen:

- [Financial Modeling Prep – Tarifmatrix](https://site.financialmodelingprep.com/developer/docs/pricing)
- [Financial Modeling Prep – FAQ](https://site.financialmodelingprep.com/faqs?code=data)
- [Twelve Data – Tarife](https://twelvedata.com/pricing)
- [StockAnalysis – API-Auskunft](https://stockanalysis.com/help/faq/api-access/)
- [StockAnalysis – Nutzungsbedingungen](https://stockanalysis.com/terms-of-use/)
- [EODHD – Fundamental Data API](https://eodhd.com/lp/fundamental-data-api)

## Fachliche Anforderungen an eine zusätzliche Quelle

Jeder importierte Wert muss eine nachvollziehbare Bedeutung, Einheit, Währung, Berichtsperiode und
Aktienbasis besitzen. Folgende Regeln sollen die Evaluation und spätere Umsetzung bestimmen:

1. **Perioden unterscheiden:** Geschäftsjahreswerte und Werte der letzten zwölf Monate getrennt behandeln.
   Bei der Bildung von TTM-Werten dürfen kumulierte Zwischenberichte und einzelne Quartale nicht doppelt
   gezählt werden.
2. **Währungen zuordnen:** Berichtswährung und Handelswährung getrennt prüfen. Ein Betrag in der
   Berichtswährung darf nicht mit der Handelswährung beschriftet werden.
3. **Aktienbasis prüfen:** Ausstehende Aktien zu einem Stichtag, gewichtete Durchschnittsaktien und
   verwässerte Durchschnittsaktien unterscheiden. Durchschnittsaktien für EPS sind kein automatischer
   Ersatz für `SHARES`.
4. **Aktienklassen und ADRs berücksichtigen:** Werte je Aktie und Aktienanzahl müssen zur Basis des
   betreffenden GT-Wertpapiers passen.
5. **Nettoverschuldung berechnen:** `NET_DEBT = totalDebt - cash`. Beide Bestandteile müssen dieselbe
   Währung, denselben Stichtag und denselben Konsolidierungskreis besitzen. Fehlende Bestandteile
   dürfen nicht stillschweigend als null interpretiert werden.
6. **Werte je Aktie konsistent ableiten:** Für `BVPS` und `SPS_*` sind Zähler und Aktienbasis ausdrücklich
   festzulegen. Die Definition muss mit den vorhandenen GT-Werten vergleichbar sein.
7. **Aktualität und Berichtigungen behandeln:** Ein erfolgreicher Abruf alter Daten bedeutet keine
   aktuelle Versorgung. Berichtsdatum und Abrufzeit sind getrennt zu bewerten.
8. **Fehlende Werte erhalten:** Nicht verfügbare oder fachlich unklare Kennzahlen dürfen nicht durch
   scheinbar plausible Nullwerte ersetzt werden.

## Empfohlene Evaluation und Priorisierung

| Ziel | Priorität |
|---|---|
| Zusätzliche unabhängige Daten für US-Aktien | SEC EDGAR zuerst prüfen |
| Zusätzliche Abdeckung europäischer Aktien | Abdeckungsprobe für filings.xbrl.org erstellen |
| Geringerer Aufbereitungsaufwand für US-Daten | SimFin mit kostenlosem Account prüfen |
| Einzelne Lücken und kleine Bestände | Alpha Vantage prüfen; Open-Source-Sonderkontingent anfragen |
| Wichtige verbleibende Schweizer oder europäische Einzeltitel | Direkte Investor-Relations-Dateien gezielt untersuchen |

Die Evaluation soll den tatsächlichen GT-Wertpapierbestand als Massstab verwenden und je Quelle, Wertpapier
und Kennzahl mindestens Auffindbarkeit, gelieferten Wert, Einheit, Währung, Berichtsperiode und
Verwendungsfähigkeit festhalten. Die Zahl erfolgreicher HTTP-Antworten allein ist kein Abdeckungskriterium.

Besonders aussagekräftig sind Referenzfälle mit einer Schweizer Heimatnotierung, einer Notierung in einer
anderen Währung als der Berichtswährung, mehreren Aktienklassen und einem ADR. Für die weitere Entscheidung
zählt vor allem, wie viele bislang fehlende oder unsichere Werte eine Quelle tatsächlich ergänzt.

Eine produktive Anbindung soll anschliessend über `IFundamentalFeedConnector`, einen zusätzlichen Eintrag
in `FundamentalSource` und die vorhandene Speicherung und Qualitätsprüfung erfolgen. Quellenabhängige
Zuordnung, Tageskontingente und fachliche Normalisierung sind dabei ausdrücklich zu berücksichtigen.
