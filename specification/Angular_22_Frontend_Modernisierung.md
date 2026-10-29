# Angular 22: Modernisierung des Frontends

**Code baseline:** backend 0.39.2, highest Flyway script `V0_39_2__typed_null_globalparameters.sql`; frontend
0.39.2 mit `@angular/core` 22.2.3, TypeScript 6.0.3, `@openng/optimus-ui` 2.0.2; Arbeitsbaum vom 29.10.2026.

## 1. Ziel

Beide Hosts (`frontend` und `grafiosch-host`) laufen zoneless, jede Komponente nutzt den `OnPush`-Standard, alle
Komponenten sind standalone und alle Templates verwenden die eingebaute Ablaufsteuerung (`@if`, `@for`, `@switch`).
Komponenten verwenden Signal-Eingaben und -Queries, Abhängigkeiten kommen aus `inject()`, Root-Services aus
`@Service()`. Beide Router binden Parameter an Eingaben; gemeinsam genutzter Chart- und Tab-Zustand liegt in Signalen.
Diese Spezifikation plant die strikte Typisierung sowie die Prüfung von `@defer` und Signal Forms.

Leitlinien:

- **Mechanische Umstellungen erledigen die Angular-Schematics.** Sie laufen über beide Projekte
  (`tsconfig.app.json` und `tsconfig.grafiosch-host.json`) und damit über Anwendung und Bibliothek `src/app/lib`
  gemeinsam. Von Hand wird nur nachgearbeitet, was ein Schematic ausdrücklich nicht umstellen kann.
- **Eine Umstellungsstufe ändert kein Verhalten.** Fachliche Verbesserungen (Ressourcen, `linkedSignal`) bilden eigene
  Stufen.
- **Jede Stufe endet mit einem Ratchet-Test**, der die abgelöste API dauerhaft verbietet (§3.2). Gemischter Code aus
  alter und neuer API entsteht so nicht erneut.
- **Gemessen wird vor dem Umbau.** Für `@defer` und für Signal Forms liefert zuerst eine Messung beziehungsweise ein
  Prototyp die Entscheidungsgrundlage (§4.6, §4.7).

## 2. Bestandsaufnahme

Erhoben über `src/**/*.ts` und `src/**/*.html` (925 TypeScript-Dateien einschließlich Tests).

| Bereich | Heute | Angular-22-Ziel |
| --- | --- | --- |
| Verzögertes Rendern | 0 × `@defer` | `@defer` nach Messung |
| Formulare | `dynamic-form` auf `UntypedFormGroup` (10 Dateien in `lib/dynamic-form` und `lib/validator`), 107 Verwender | Signal Forms nach Prototyp |
| Typisierung | `strict: false`, `strictTemplates: true`; `noImplicitAny`, `strictBindCallApply`, `strictFunctionTypes`, `noImplicitThis`, `useUnknownInCatchVariables`, `alwaysStrict`, `strictBuiltinIteratorReturn` aktiv | `strict: true` |

Fehlerzahlen von `npx tsc -p tsconfig.app.json --noEmit` mit zusätzlichen Compiler-Optionen (nur TypeScript-Code,
Templates nicht mitgezählt):

| Option | Fehler |
| --- | --- |
| Ohne zusätzliche Option | 0 |
| `--strictNullChecks` | 1272 (`lib` 213, `transaction` 160, `algo` 111, `shared` 106, `watchlist` 98, Rest < 110 je Modul) |
| `--strictNullChecks --strictPropertyInitialization` | 2077 |
| `--strict` | 2077 |

## 3. Querschnittsregeln

### 3.1 Prüfung jeder Stufe

- `npm run build` (Projekt `frontend`) und `npx ng build grafiosch-host` ohne Fehler und ohne neue Warnungen; das
  `initial`-Budget in `angular.json` wird nicht angehoben.
- `npm test` und `npm run format:check` grün. Die Schematics formatieren nicht nach GT-Stil; nach jedem Lauf folgt
  `npm run format`.
- Betroffene Playwright-Specs einzeln gegen den laufenden Stack. Stufen, die mehr als hundert Dateien berühren,
  gelten erst nach einem vollständigen E2E-Durchlauf als abgeschlossen; diesen stößt der Benutzer an.
- Manuelle Prüfung der Benutzerabläufe, die eine Stufe am ehesten bricht: rascher Wechsel der Auswahl im Hauptbaum,
  denselben Dialog zweimal öffnen, Sprachwechsel, Ladefehler und Serverfehler beim Speichern. Beide Hosts.

### 3.2 Ratchet-Test für abgelöste APIs

`src/angular-api.ratchet.spec.ts` sammelt die `.ts`- und `.html`-Dateien unter `src`. Der Test verbietet
Konstruktorinjektion, nicht begründete `@Injectable()`-Deklarationen, `new BehaviorSubject` und den Zugriff auf die
Parameter-Observables injizierter `ActivatedRoute`-Instanzen sowie `ngOnChanges` und `fieldsInitialized`.
Template-Switches deklarieren einen Standardfall oder eine Vollständigkeitsprüfung. Ausnahmen stehen als Liste
`Datei → Begründung` im Test selbst.
Der Test sichert außerdem die aktiven strikten TypeScript-Optionen in beiden Hosts und der Testkonfiguration ab.

Neue Muster erhalten jeweils Positiv- und Negativbeispiele. Das Frontend hat keine ESLint-Konfiguration; die Ratchets
sind die Absicherung gegen abgelöste APIs.

### 3.3 Dokumentation

Jede Stufe aktualisiert `frontend/CLAUDE.md` im selben Commit: Codebeispiele und Regeln zeigen die neue API.
Regeln, die beide Hosts betreffen, gelten ebenso für `src/grafiosch-host/README.md`.

## 4. Stufen

§4.5 wird in prüfbaren Teilstufen bearbeitet. §4.6 und §4.7 sind Prüfaufträge mit eigener Entscheidung.

### 4.5 Strikte Typisierung

1. **`strictNullChecks` über einen Zählstand.** Abbau in größeren Paketen mit gemeinsamer Ursache: Verträge in
   Formular- und Tabellenbasisklassen zusammen mit ihren Verbrauchern, DTOs mit den betroffenen Anwendungsmodulen,
   danach die verbleibenden Bereiche nach Fehlerzahl. Wiederkehrende Fehler werden über den zuständigen Basisvertrag
   behoben; Paketgrenzen folgen diesem Zusammenhang. Bestehende `null`-Initialwerte
   und optionale Eigenschaften bleiben erhalten; ihre Typen und die Verbraucher bilden fehlende Werte ausdrücklich ab.
   Jedes Paket wird mit `npm run check:strict` geprüft; `npm run check:strict -- --update` senkt anschließend die
   Grenzen in `scripts/strict-null-baseline.json`. Die Zählung erfasst den TypeScript-Abhängigkeitsgraphen von
   `tsconfig.app.json`; Angular-Templates und ausschließlich vom Bibliothekshost verwendete Dateien benötigen eigene
   Prüfungen. Bei null Fehlern wird die Option in `tsconfig.json` für beide Hosts gesetzt. Mit `strictNullChecks`
   prüft `strictTemplates` auch `null` in Template-Bindungen; diese Fehler kommen beim Einschalten hinzu und werden
   in derselben Stufe bereinigt. Danach entfallen `scripts/strict-null-count.mjs`, seine Baseline und Hilfstests sowie
   die zugehörigen npm-Aufrufe.
2. **`strictPropertyInitialization` zuletzt.** Eingaben, Queries und Abhängigkeiten sind
   initialisierte Felder; die verbleibenden Fehler betreffen fachliche Felder.
3. Am Ende steht `"strict": true` in `tsconfig.json`, die Einzeloptionen entfallen.

Strikte Null-Prüfung ist Voraussetzung dafür, dass `hasValue()` einer Ressource den Typ von `value()` tatsächlich
einengt und dass Signal Forms typisierte Feldzugriffe liefern.

### 4.6 `@defer` nach Messung

Routen, Dialoge, Dashboard-Widgets und Plotly werden bereits verzögert geladen (`loadComponent`,
`DialogComponentLoader`, `PlotlyLoaderService`). `@defer` lohnt sich nur für schwere, anfangs unsichtbare Teile
innerhalb einer bereits geladenen Ansicht.

Vorgehen: Für die Hauptansichten Portfolio-Zusammenfassung, Wertpapierkonto-Zusammenfassung, Watchlist,
Transaktionsliste und Algo-Baum werden Chunk-Größen (`ng build --stats-json`) und Renderzeit (Chrome Performance)
erhoben. Kandidaten sind nicht aktive Tab-Inhalte in einer Komponente (`security-edit.component.ts`,
`algo-alert-diagnostics-dialog.component.ts`) und Abschnitte unterhalb des sichtbaren Bereichs. `@defer (on viewport)`
beziehungsweise `(on interaction)` mit `@placeholder` wird nur eingesetzt, wo ein eigener Chunk von mindestens 50 kB
entsteht oder die erste Darstellung messbar schneller wird. Ergebnis der Stufe ist das Messprotokoll in der
Commit-Nachricht oder in einem GitHub-Issue; ohne Treffer gibt es keine Codeänderung.

### 4.7 Signal Forms für `dynamic-form` – Prototyp und Entscheidung

Signal Forms (`@angular/forms/signals`) sind in Angular 22 stabil. Für statisch bekannte Formulare erlaubt
`frontend/CLAUDE.md` sie bereits. Fast jedes Formular in GT entsteht jedoch zur Laufzeit in `DynamicFormComponent`
aus `FieldConfig` beziehungsweise `ClassDescriptorInputAndShow`. Ein einzelner, von Hand gebauter Signal-Forms-Dialog
sagt deshalb nichts über die Umstellbarkeit aus. Der Prototyp setzt an `dynamic-form` selbst an.

Ausgangslage:

- 107 Dateien verwenden `<dynamic-form>`.
- `FormNotifier` instrumentiert 27 Anzeigeeigenschaften jeder `FieldConfig` (`FIELD_DISPLAY_PROPERTIES`) als
  Accessor-Properties, damit Zuweisungen das OnPush-Formular benachrichtigen. Signal Forms leiten Zustand wie
  `disabled` und Validierung aus Regeln ab und würden diese Instrumentierung überflüssig machen.
- Die Feldkomponenten binden Optimus-Eingabefelder über `[formControlName]` (12 Dateien in `lib/dynamic-form`).
- Anwendungscode steuert Formulare imperativ: 83 × `formControl.enable/disable` in 23 Dateien, 20 × `resetValidator`,
  19 × `setValidators/addValidators/removeValidators`, 110 × `valueChanges` in 53 Dateien, 213 × `setValue/patchValue`
  in 60 Dateien. Signal Forms unterstützen `enable()`, `disable()` und das Ändern von Validatoren nicht; an ihre Stelle
  treten die Regeln `disabled(p, { when })`, `applyWhen(…)` und `required(…)`. Jede umgestellte Dialogklasse schreibt
  ihre Abhängigkeitslogik also neu.
- `transferBusinessObjectToForm` (46 Aufrufe) und `cleanMaskAndTransferValuesToBusinessObject` (50 Aufrufe) bilden die
  Brücke zwischen Geschäftsobjekt und Formular; letztere überträgt auch Werte deaktivierter Felder.

Prototyp auf einem Zweig: `SignalDynamicFormComponent` neben `DynamicFormComponent`, mit derselben Eingabe
`FieldConfig[]` und demselben Layout (`DynamicFormLayoutComponent`). Sie erzeugt zur Laufzeit ein Modell-Signal und
ein Schema, das aus den Validierungsangaben jeder `FieldConfig` und den Gruppenvalidatoren von
`FormGroupDefinition` zusammengesetzt wird. Umgestellt werden zwei Dialoge:

- `CashaccountEditComponent` – Formular vollständig aus der Backend-Entität abgeleitet;
- `GenericConnectorEndpointEditComponent` – kaskadierende Feldabhängigkeiten, Auswahllisten, Zahlen.

Entscheidungskriterien, alle müssen erfüllt sein:

1. Jede Feldkomponente unter `lib/dynamic-form/components` bindet ihr Optimus-Eingabefeld über `[formField]`, direkt
   oder mit einer dünnen `FormValueControl`-Hülle; die Optimus-Komponenten selbst bleiben unverändert.
2. Das Laufzeitschema deckt alles ab, was die Backend-Deskriptoren liefern (`required`, Länge, Bereich, `@Digits`,
   Muster, `@AfterEqual`, `@Future`) sowie die Gruppenvalidatoren.
3. Die Abhängigkeitslogik von `GenericConnectorEndpointEditComponent` ist als Regelwerk kürzer als heute und ohne
   `valueChanges`-Subscriptions formuliert.
4. Der Prototyp benötigt `FormNotifier` nicht.
5. `transferBusinessObjectToForm` und `cleanMaskAndTransferValuesToBusinessObject` behalten ihre Semantik.
6. Serverseitige Validierungsfehler erscheinen am betroffenen Feld.
7. Die bestehenden Playwright-Specs beider Dialoge laufen unverändert.

Sind alle Kriterien erfüllt, folgt eine eigene Spezifikation für die schrittweise Umstellung, Dialog für Dialog, mit
beiden Formularkomponenten nebeneinander. Andernfalls bleibt `dynamic-form` auf Reactive Forms; die Begründung kommt
nach `frontend/CLAUDE.md`. Bis zu dieser Entscheidung gilt die bestehende Regel.

## 5. Entscheidungen

| Thema | Entscheidung | Begründung |
| --- | --- | --- |
| Absicherung | Ratchet-Tests in Vitest statt ESLint | Bewährtes Muster im Projekt, läuft mit `npm test`, keine zusätzliche Werkzeugkette |
| Typisierte Reactive Forms (`FormGroup<T>`) | Keine Umstellung | Die Felder von `dynamic-form` sind erst zur Laufzeit bekannt; ein generischer Typ bringt dort keinen Gewinn. Typisierung kommt, falls überhaupt, mit Signal Forms |
| Signal Forms | Prototyp in `dynamic-form`, kein Einzeldialog außerhalb | Nur dort zeigt sich, ob Deskriptoren, Layout und Optimus-Felder tragen |
| `strictNullChecks` | Zählstand pro Verzeichnis statt Umstellung in einem Schritt | 1272 Fehler sind in einem Commit nicht prüfbar |
| `@defer` | Nur nach Messung | Routen, Dialoge und Plotly sind bereits verzögert geladen |
| Angular Aria | Für neu gebaute eigene Bedienelemente; kein Umbau bestehender Optimus-Komponenten | GT hat heute kein selbst gebautes Bedienelement, das einem Aria-Muster (Listbox, Combobox, Menü, Tabs, Baum, Grid, Toolbar) entspricht |

## 6. Nicht Gegenstand

- Ersatz von `moment` (39 Dateien) und von `class-transformer` samt globalem `reflect-metadata`-Skript in
  `angular.json`. Beides betrifft Bundle-Größe und Typisierung, ist aber keine Angular-Funktion und erhält bei Bedarf
  eine eigene Spezifikation.
- Server-Side Rendering, Hydration, `NgOptimizedImage` und Angular-i18n; GT ist eine angemeldete Single-Page-Anwendung
  mit Texten aus dem Backend.
- Komponententests mit `TestBed`; die Vitest-Tests bleiben auf reine Funktionen beschränkt.
