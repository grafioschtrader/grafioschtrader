# Angular 22: Modernisierung des Frontends

**Code baseline:** backend 0.39.1, highest Flyway script `V0_39_2__typed_null_globalparameters.sql`; frontend
0.39.1 mit `@angular/core` 22.2.3, TypeScript 6.0.3, `@openng/optimus-ui` 2.0.2; Arbeitsbaum vom 28.10.2026.

## 1. Ziel

Beide Hosts (`frontend` und `grafiosch-host`) laufen zoneless, jede Komponente nutzt den `OnPush`-Standard, alle
Komponenten sind standalone und alle Templates verwenden die eingebaute Ablaufsteuerung (`@if`, `@for`, `@switch`).
Die Programmierschnittstellen der Komponenten, die Dependency Injection, das Lesen von Serverdaten und die
Typisierung stammen dagegen überwiegend aus älteren Angular-Generationen. Diese Spezifikation plant, wie das Frontend
die Möglichkeiten von Angular 22 vollständig nutzt.

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

Erhoben über `src/**/*.ts` und `src/**/*.html` (912 TypeScript-Dateien einschließlich Tests, 315 Komponenten). Die Spalte „Schematic"
nennt das Ergebnis eines Probelaufs mit `--dry-run`.

| Bereich | Heute | Angular-22-Ziel | Schematic |
| --- | --- | --- | --- |
| Eingaben | 447 × `@Input()` in 157 Dateien (210 davon in `lib`), 40 × `input()` | `input()`, `input.required()`, `model()` | `signal-input-migration`: 392 von 438 |
| Ausgaben | 89 × `@Output()` / `new EventEmitter` in 47 Dateien, 3 × `output()` | `output()` | `output-migration`: 87 von 87 |
| View-/Content-Queries | 91 × `@ViewChild(ren)`, 8 × `@ContentChild(ren)`, 1 × `viewChild()` | `viewChild()`, `viewChildren()`, `contentChild()` | `signal-queries-migration`: 86 von 94 |
| Dependency Injection | 534 Konstruktoren, 164 × `inject()` | `inject()` | `inject-migration`: 379 Dateien |
| Services | 114 × `@Injectable`, davon 15 `providedIn: 'root'`; rund 95 Services einzeln in `app.config.ts` registriert | `@Service()` | `service-migration` |
| Änderungsreaktion | 43 × `ngOnChanges` | `computed()`, `effect()` | – |
| Zustand in RxJS | 6 × `BehaviorSubject` als Zustandshalter in 5 Dateien | `signal()` | – |
| Signal-basiertes Lesen | 2 × `httpResource`, 0 × `rxResource`, 0 × `toSignal` | `httpResource`, `rxResource` | – |
| Abhängiger Zustand | 0 × `linkedSignal` | `linkedSignal()` | – |
| Routenparameter | 23 Dateien lesen `ActivatedRoute.params` / `paramMap` per Subscription | `withComponentInputBinding()` + `input()` | – |
| Verzögertes Rendern | 0 × `@defer` | `@defer` nach Messung | – |
| Formulare | `dynamic-form` auf `UntypedFormGroup` (10 Dateien in `lib/dynamic-form` und `lib/validator`), 107 Verwender | Signal Forms nach Prototyp | – |
| Typisierung | `strict: false`, `strictTemplates: true` | `strict: true` | – |

Fehlerzahlen von `npx tsc -p tsconfig.app.json --noEmit` mit zusätzlichen Compiler-Optionen (nur TypeScript-Code,
Templates nicht mitgezählt):

| Option | Fehler |
| --- | --- |
| `--noImplicitAny` | 423 |
| `--strictBindCallApply` | 84 |
| `--strictFunctionTypes` | 255 |
| `--noImplicitThis`, `--useUnknownInCatchVariables`, `--alwaysStrict`, `--strictBuiltinIteratorReturn` | je 0 |
| `--strictNullChecks` | 2747 (`lib` 698, `transaction` 260, `entities` 258, `shared` 217, `algo` 174, `watchlist` 133, Rest < 125 je Modul) |
| `--strictNullChecks --strictPropertyInitialization` | 3910 |
| `--strict` | 4638 |

## 3. Querschnittsregeln

### 3.1 Prüfung jeder Stufe

- `npm run build` (Projekt `frontend`) und `npx ng build grafiosch-host` ohne Fehler und ohne neue Warnungen; das
  `initial`-Budget in `angular.json` wird nicht angehoben.
- `npm test` und `npm run format:check` grün. Die Schematics formatieren nicht nach GT-Stil; nach jedem Lauf folgt
  `npm run format`.
- Betroffene Playwright-Specs einzeln gegen den laufenden Stack. Stufen, die mehr als hundert Dateien berühren
  (§4.2, §4.3), gelten erst nach einem vollständigen E2E-Durchlauf als abgeschlossen; diesen stößt der Benutzer an.
- Manuelle Prüfung der Benutzerabläufe, die eine Stufe am ehesten bricht: rascher Wechsel der Auswahl im Hauptbaum,
  denselben Dialog zweimal öffnen, Sprachwechsel, Ladefehler und Serverfehler beim Speichern. Beide Hosts.

### 3.2 Ratchet-Test für abgelöste APIs

`src/angular-api.ratchet.spec.ts` sammelt die `.ts`- und `.html`-Dateien unter `src` und prüft verbotene APIs.
Jede Stufe erweitert den Test um ihre Muster, sobald der Bestand bereinigt ist:

| Stufe | Verbotenes Muster |
| --- | --- |
| §4.2 | `@Input(`, `@Output(`, `new EventEmitter`, `@ViewChild(`, `@ViewChildren(`, `@ContentChild(`, `@ContentChildren(` |
| §4.3 | Konstruktor mit Parametern in einer Klasse mit `@Component`, `@Directive`, `@Pipe` oder `@Service`; `@Injectable()` außerhalb der Ausnahmeliste von §4.3 |
| §4.4 | `new BehaviorSubject`; `activatedRoute.params` und `activatedRoute.paramMap` |

Ausnahmen stehen als Liste `Datei → Begründung` im Test selbst, nicht in einer separaten Datei. Eine Ausnahme ohne
Begründung ist unzulässig. Die Muster werden wie im bestehenden Ratchet mit einem eigenen Testfall gegen Positiv- und
Negativbeispiele geprüft. Das Frontend hat keine ESLint-Konfiguration; diese Tests sind die einzige
Absicherung gegen abgelöste APIs.

### 3.3 Dokumentation

Jede Stufe aktualisiert `frontend/CLAUDE.md` im selben Commit: Codebeispiele (Dialog-Vorlagen, `SimpleEditBase`,
Tabellenkomponenten) zeigen die neue API. Nach §4.2 verweist der Abschnitt „Change detection" statt auf die
ausstehende Schematic-Umstellung auf den Ratchet-Test aus §3.2. Nach §4.3 entfällt dort und in
`src/grafiosch-host/README.md` der Hinweis, dass die bestehenden Bibliotheks-Services in jedem Host registriert
werden müssen.

## 4. Stufen

Reihenfolge: §4.2 → §4.3 → §4.4. §4.5 kann nach §4.3 parallel zu §4.4 laufen, berührt aber dieselben Dateien
und wird deshalb nicht gleichzeitig mit §4.2 oder §4.3 bearbeitet. §4.6 und §4.7 sind Prüfaufträge mit eigener
Entscheidung.

### 4.2 Signal-Schnittstellen der Komponenten

Ausführung in dieser Reihenfolge, jeweils ein Commit:

1. `ng g @angular/core:output-migration` – alle 87 Ausgaben.
2. `ng g @angular/core:signal-queries-migration` – 86 von 94 Queries.
3. `ng g @angular/core:signal-input-migration` – 392 von 438 Eingaben.

Für die verbleibenden 46 Eingaben und 8 Queries listet ein erneuter Lauf mit `--insert-todos` die Gründe. Sie werden
nach diesen Regeln von Hand umgestellt:

| Grund | Umstellung |
| --- | --- |
| Die Komponente schreibt ihre Eingabe selbst | Eltern binden zweiseitig → `model()`; sonst `input()` plus internes `linkedSignal(() => this.x())` |
| Eingabe mit Setter-Logik | `input()` mit `transform` für reine Umwandlung, sonst `computed()` / `effect()` |
| Ein Elternteil setzt die Eingabe über eine Komponentenreferenz | Bindung im Template oder `ComponentRef.setInput()`, wie es `DynamicFieldDirective` bereits tut |
| Eingabe einer abstrakten Basisklasse, von Unterklassen überschrieben | Die Basisklasse deklariert das Signal, Unterklassen lesen es; keine Neudeklaration |

Die Template-Prüfung `interpolatedSignalNotInvoked: "error"` in `tsconfig.json` findet jede Lesestelle, an der nach
der Migration die Klammern fehlen.

Danach werden die 43 `ngOnChanges`-Implementierungen durchgesehen. Eine Implementierung, die nur abgeleiteten Zustand
berechnet, wird zu `computed()`. Eine Implementierung, die Tabellenzeilen vorbereitet, folgt dem
`effect`/`untracked`-Muster aus `frontend/CLAUDE.md` („Dropdown Selection Filters"); das Flag `fieldsInitialized`
entfällt dabei. Ein `ngOnChanges`, das mehrere Eingaben koordiniert und Requests auslöst, bleibt bis §4.4 bestehen.

### 4.3 Dependency Injection mit `inject()` und `@Service()`

**Basisklassen zuerst.** Die Hierarchien `ShowRecordConfigBase` → `TableConfigBase` → `TableEditConfigBase` /
`TableCrudSupportMenu` / `TableTreetableTotalBase`, `FormBase` → `SimpleEditBase` → `SimpleEntityEditBase`,
`SimpleDynamicEditBase`, `SingleRecordMasterViewBase`, `TransactionContextMenu`, `TransactionBaseOperations`,
`WatchlistTable` und `AlgoTreeViewBase` beziehen ihre Abhängigkeiten über `inject()`. Ihre Konstruktoren behalten
nur fachliche Parameter (Hilfe-ID, `i18nRecord`, Menüoptionen). Damit entfallen in jeder Unterklasse die
durchgereichten Parameter wie `filterService`, `usersettingsService`, `translateService`, `gps`. Das Schematic stellt
Klasse für Klasse um und ändert keine `super()`-Signaturen über eine Hierarchie hinweg; diese Umstellung erfolgt
deshalb von Hand und pro Hierarchie in einem eigenen Commit.

**Danach** `ng g @angular/core:inject-migration` für den Rest (379 Dateien im Probelauf). Felder, auf die ein
Template zugreift, bleiben `public` beziehungsweise `protected`. Mit `inject()` sind die meisten
Konstruktorparameter verschwunden; danach wird `strictInjectionParameters` in `tsconfig.json` auf `true` gesetzt.

**Services auf `@Service()`.** `@Service()` ist die Angular-22-Kurzform für einen Root-Singleton und setzt `inject()`
voraus. `ng g @angular/core:service-migration` stellt alle Services um, die die Bedingungen erfüllen. Das betrifft
Anwendung und Bibliothek. Folgen:

- Die rund 95 Einzeleinträge in `app.config.ts` und die entsprechenden Einträge in `src/grafiosch-host/main.ts`
  entfallen. Übrig bleiben Token-Provider (`TASK_EXTENDED_SERVICE`, `TASK_TYPE_ENUM`, `DIALOG_HANDLER`,
  `MAIN_TREE_CONTRIBUTOR`, `SETTINGS_MENU_EXTENSIONS`, `DASHBOARD_*`, `PERSONAL_DATA_ZIP_NAME`) und die Klassen, die
  ein Host ersetzt.
- Ein Service, der nur in einem Lazy-Chunk injiziert wird, wandert in diesen Chunk. Der Vergleich des
  Start-Bundles vor und nach der Umstellung (`ng build --stats-json`) gehört in die Commit-Nachricht.
- Die Falle aus `frontend/CLAUDE.md` („The library services are not `providedIn: 'root'`" → NG0201 beim Rendern von
  `/mainview`) entfällt; der Abschnitt und `src/grafiosch-host/README.md` werden entsprechend umgeschrieben.

**Ausnahmeliste – bleibt `@Injectable()`:**

| Fall | Beispiel |
| --- | --- |
| Basisklasse, die ein Host durch eine Unterklasse ersetzt | `AfterLoginHandler` (GT: `GtAfterLoginHandler`) |
| Service, der pro Komponente bereitgestellt wird | `DialogService` in `providers: [DialogService]` von Tabellenkomponenten (Optimus-Klasse, nicht migriert) |
| Klasse, die nur über `useClass` an ein Token gebunden ist | `AppDialogHandler` für `DIALOG_HANDLER` |

Die Ausnahmeliste wird in den Ratchet-Test übernommen (§3.2).

### 4.4 Serverdaten und abhängiger Zustand als Signale

**Routenparameter als Eingaben.** `provideRouter` beider Hosts erhält `withComponentInputBinding()`. Komponenten, die
heute `activatedRoute.params.subscribe(…)` verwenden, deklarieren
`readonly id = input.required({ transform: numberAttribute })`. Betroffen sind 23 Dateien in drei Gruppen:

| Gruppe | Dateien | Umstellung |
| --- | --- | --- |
| Tab-Menüs | `portfolio.tab.menu`, `securityaccount.tab.menu`, `watchlist.tab.menu`, `simulation-run-tab-menu`, `securityaccount-import-tab-menu` | nur Eingabe statt Subscription |
| Leseansichten | `securityaccount.summary`, `securityaccount.summaries`, `portfolio.cashaccount.summary`, `portfolio.transaction.table`, `securityaccount.import.transaction`, `algo.top.data.view`, `algo-simulation-run`, `algo-simulation-run-settings`, `time.series.chart`, `historyquote-host`, `seasonality`, `chart.general.purpose`, `gtnet-security-import`, `task.data.change.table`, `watchlist.table` | Eingabe, danach Prüfung auf Ressource (unten) |
| Anmeldung | `login`, `register`, `registration.token.verify` | Query-Parameter als Eingabe |

Die `routeSubscribe`-Felder und ihr `unsubscribe()` in `ngOnDestroy` entfallen. `withComponentInputBinding()` bindet
nur die Parameter der eigenen Route. Die Kindrouten in `app.routes.ts` tragen `:id` selbst; trifft das zu Beginn der
Stufe für jede betroffene Route zu, bleibt die Standard-`paramsInheritanceStrategy`, sonst wird die Route angepasst.

**Leseansichten als Ressource.** Für jede Leseansicht aus der Tabelle gilt die bestehende Regel „`httpResource` for
signal-driven reads" in `frontend/CLAUDE.md`. Erfüllt eine Ansicht sie, wird ihr `readData()` zu einer `httpResource`,
deren Request aus der Routen-Eingabe abgeleitet ist; die Service-Methode liefert dazu einen `get…Request()` wie
`SecurityService.getSeasonalReturnsRequest`. Ein Neuladen nach dem Schließen eines Dialogs, nach einem Import oder
nach einem `DataChangedService`-Ereignis ruft `reload()` der Ressource auf. Eine Ansicht, die mehrere
Service-Observables kombiniert, verwendet `rxResource` mit den bestehenden Service-Methoden. `watchlist.table` und die
Transaktionsdialoge behalten ihre eigene Abbruchlogik (`replaceWatchlistRequest()`, `replaceDialogRequest()`), bis
eine Ressource dieselben Fälle nachweislich abdeckt. Die Fehleranzeige läuft weiterhin über
`reportResourceErrors(resource, service)`.

**Auswahl, die ein Neuladen überlebt, als `linkedSignal`.** Drei Stellen suchen nach dem Laden die vorher gewählte
Zeile von Hand wieder heraus:

- `TableCrudSupportMenu.refreshSelectedEntity()` (`lib/datashowbase/table.crud.support.menu.ts`)
- `correlation-table.component.ts` (Wiederherstellung über `idSecuritycurrency`)
- `standing-order-table-base.ts` (Wiederherstellung über `idStandingOrder`)

Dort wird die Auswahl zu

```typescript
readonly selectedEntity = linkedSignal<T[], T | null>({
  source: this.entityList,
  computation: (list, previous) =>
    list.find((e) => e[this.entityKeyName] === previous?.value?.[this.entityKeyName]) ?? null
});
```

`linkedSignal` ist schreibbar; die bestehende API `selectedEntity.set(…)` bleibt gültig. Der Neuaufbau des Menüs
bleibt ein expliziter Aufruf, weil er übersetzt und `ActivePanelService.activatePanel()` aufruft. Zu Beginn der Stufe
werden `TransactionContextMenu`, `WatchlistTable`, `SingleRecordMasterViewBase` und `AlgoTreeViewBase` auf dasselbe
Muster geprüft.

**Zustand aus `BehaviorSubject` in Signale.** `correlation-chart.service.ts`, `tenant.dividends.chart.service.ts`,
`tenant.holdings.chart.service.ts` und `tab.menu.service.ts` halten Zustand in `BehaviorSubject`. Sie werden zu
`signal()`; Verbraucher lesen das Signal. Nur wo eine RxJS-Kette nachfolgt, bleibt ein `toObservable()`.
`ngxerrors.directive.ts` (`control$`) wird mit der Eingabe der Direktive in §4.2 zu einem Signal.

**Vollständigkeitsprüfung bei `@switch`.** Ein `@switch` über einen Union- oder Enum-Typ ohne fachlichen
Standardfall erhält `@default never;`. Die Prüfung greift nur auf Variablen, nicht auf Signal-Aufrufe; ein Signal wird
dafür vorher mit `@let` gebunden. Die 31 bestehenden `@switch` in 23 Dateien werden einmal durchgesehen; die Regel
kommt nach `frontend/CLAUDE.md`.

### 4.5 Strikte Typisierung

1. **Günstige Optionen einzeln einschalten**, jeweils mit Bereinigung in einem Commit: `noImplicitAny` (423),
   `strictBindCallApply` (84), `strictFunctionTypes` (255). Die Optionen mit null Fehlern (`noImplicitThis`,
   `useUnknownInCatchVariables`, `alwaysStrict`, `strictBuiltinIteratorReturn`) werden sofort gesetzt, damit sie
   nicht mehr verletzt werden können.
2. **`strictNullChecks` über einen Zählstand.** Das Skript `scripts/strict-null-count.mjs` führt
   `tsc -p tsconfig.app.json --noEmit --strictNullChecks` aus, zählt die Fehler pro Verzeichnis unter `src/app` und
   vergleicht sie mit `scripts/strict-null-baseline.json`. Es scheitert, wenn ein Verzeichnis zunimmt, und schreibt
   mit `--update` den neuen, niedrigeren Stand. Aufruf über `npm run check:strict`, nicht in `npm test`, weil ein
   vollständiger `tsc`-Lauf rund eine Minute dauert. Abbau in dieser Reihenfolge: `entities` (DTOs machen `null`
   für alle Verbraucher sichtbar), `lib`, danach die Anwendungsmodule nach Fehlerzahl. Bei null Fehlern wird die
   Option in `tsconfig.json` gesetzt und das Skript gelöscht. Mit `strictNullChecks` prüft `strictTemplates` auch
   `null` in Template-Bindungen; diese Fehler kommen beim Einschalten hinzu und werden in derselben Stufe bereinigt.
3. **`strictPropertyInitialization` zuletzt.** Nach §4.2 und §4.3 sind Eingaben, Queries und Abhängigkeiten
   initialisierte Felder, sodass die verbleibenden Fehler fachliche Felder betreffen.
4. Am Ende steht `"strict": true` in `tsconfig.json`, die Einzeloptionen entfallen.

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
| Umfang der Signal-API-Umstellung | Vollständige Umstellung per Schematic statt „nur bei Berührung" | Die Schematics erfassen 90 % (Eingaben) bis 100 % (Ausgaben) automatisch; gemischter Bestand würde sonst über Jahre bleiben |
| Absicherung | Ratchet-Tests in Vitest statt ESLint | Bewährtes Muster im Projekt, läuft mit `npm test`, keine zusätzliche Werkzeugkette |
| Services | `@Service()` für Anwendung und Bibliothek | Entfernt rund 95 Provider-Einträge und die NG0201-Falle; Services wandern in ihre Lazy-Chunks |
| Typisierte Reactive Forms (`FormGroup<T>`) | Keine Umstellung | Die Felder von `dynamic-form` sind erst zur Laufzeit bekannt; ein generischer Typ bringt dort keinen Gewinn. Typisierung kommt, falls überhaupt, mit Signal Forms |
| Signal Forms | Prototyp in `dynamic-form`, kein Einzeldialog außerhalb | Nur dort zeigt sich, ob Deskriptoren, Layout und Optimus-Felder tragen |
| `strictNullChecks` | Zählstand pro Verzeichnis statt Umstellung in einem Schritt | 2747 Fehler sind in einem Commit nicht prüfbar |
| `@defer` | Nur nach Messung | Routen, Dialoge und Plotly sind bereits verzögert geladen |
| Angular Aria | Für neu gebaute eigene Bedienelemente; kein Umbau bestehender Optimus-Komponenten | GT hat heute kein selbst gebautes Bedienelement, das einem Aria-Muster (Listbox, Combobox, Menü, Tabs, Baum, Grid, Toolbar) entspricht |

## 6. Nicht Gegenstand

- Ersatz von `moment` (39 Dateien) und von `class-transformer` samt globalem `reflect-metadata`-Skript in
  `angular.json`. Beides betrifft Bundle-Größe und Typisierung, ist aber keine Angular-Funktion und erhält bei Bedarf
  eine eigene Spezifikation.
- Server-Side Rendering, Hydration, `NgOptimizedImage` und Angular-i18n; GT ist eine angemeldete Single-Page-Anwendung
  mit Texten aus dem Backend.
- Komponententests mit `TestBed`; die Vitest-Tests bleiben auf reine Funktionen beschränkt.
