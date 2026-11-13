# Abnahme: Realisierte Gewinne und Steuerhilfe Deutschland

**Code baseline:** backend 0.39.4, highest Flyway script `V0_39_4__cost_basis_method.sql`.

Der Reiter `REALIZED_GAINS` (`RealizedGainReport`, `GET /api/portfolio/realizedgains`) und die Steuerhilfe Deutschland
(`GermanTaxLotEngine`, `GET /api/portfolio/taxgermany/{taxYear}`) liefern die Ausgangswerte aus den Transaktionen des
Tenants. Diese Spezifikation fasst zusammen, was vor der Auslieferung noch geprüft und abgesichert wird.

## 1. Prüfung am Original

Vor der Auslieferung werden am Original geprüft:

- das BMF-Schreiben „Einzelfragen zur Abgeltungsteuer“ auf bundesfinanzministerium.de und die Randnummern, auf die
  `GermanTaxLotEngine` und der Benutzerhandbuch-Abschnitt verweisen (Rn. 50, 51, 67, 85, 97, 98, 118, 123, 148, 184a,
  247),
- die Basiszinsen der Jahre 2018 bis 2026 an den jeweiligen BMF-Schreiben zur Basiszinsberechnung, die in `tax_year`
  für das Land `DE` stehen,
- das Anwendungsschreiben zum InvStG in der Fassung vom 24. November 2025: Es entscheidet, ob die Vorabpauschale bei
  Fremdwährungsfonds Preise und Ausschüttungen zu den Referenzkursen der Stichtage in Euro umrechnet, wie
  `GermanTaxLotEngine.calcPrepayment` es tut,
- die Übergangsregeln aus § 52 EStG und § 56 InvStG, die die Stichtage 1. Januar 2009 (Aktien, Kapitalforderungen) und
  1. Januar 2018 (Fonds) für den Block `LEGACY` festlegen,
- die Abschnitte von Anlage KAP und Anlage KAP-INV am Formular des jeweiligen Jahres.

Eine steuerkundige Person prüft die Beispiele von `GermanTaxLotEngineTest`.

## 2. Playwright-Specs

Beide Specs legen ihre Transaktionen selbst an und räumen ihre Daten auf (Skill `e2e-test`).

- **Realisierte Gewinne:** Eine Spec legt Käufe, Verkäufe und einen Depotübertrag an, wechselt in der Auswahl die
  Einstandswert-Methode, prüft die Jahreszeile von `REALIZED_GAINS` und die gekennzeichnete Systembuchung.
- **Steuerhilfe Deutschland:** Eine Spec arbeitet mit einem Euro-Tenant, dessen Land `DE` ist, und prüft, dass der
  Bereich `GERMAN_TAX_HELP` erscheint. Sie legt ein Portfolio mit `taxWithholding = null` an und prüft die Aufforderung
  zur Zuweisung und die Darstellung einer Summe ohne Betrag. Bei einem Tenant mit anderem Land oder anderer
  Hauptwährung erscheint der Bereich nicht.

## 3. REST-Integrationstests

Sie laufen in der nummerierten `ResourceTestSuite` gegen die Testdatenbank, an einer Stelle, an der ihre Voraussetzungen
schon bestehen:

- `GET /api/portfolio/taxgermany/{taxYear}` weist einen Tenant ab, der nicht in Euro rechnet oder nicht das Land `DE`
  hat, und ein Steuerjahr ausserhalb von 2018 bis zum laufenden Jahr.
- `PUT /api/taxdata/year/{id}/fundbaserate` setzt den Basiszins für einen Administrator, weist einen Benutzer ohne
  Rolle `ROLE_ADMIN` ab und weist ein Steuerjahr eines anderen Landes als `DE` ab.

## 4. Aufräumen bei der Auslieferung

- Die Test-Migration `V20__german_tax_help.sql` entfällt, sobald `nv.bat` `V1` und `V2` neu erzeugt hat.
- Das Skript `V0_39_4__cost_basis_method.sql` enthält die Spalten der Steuerhilfe. Eine Entwicklungsdatenbank, die es
  schon angewendet hat, braucht die Prüfsummen-Reparatur und die drei `ALTER TABLE`-Anweisungen von Hand.
