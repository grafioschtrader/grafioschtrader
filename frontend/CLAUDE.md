# Frontend CLAUDE.md

This file provides frontend-specific guidance to Claude Code when working with the Angular frontend.

## Minimal Business Logic in Frontend

**IMPORTANT**: Keep business logic in the frontend to an absolute minimum. Most business logic belongs in the backend.

### Allowed Frontend Logic

The frontend should only contain:

- **Validation**: Form field validation rules
- **UI State Management**: Enabling/disabling input elements based on other field values
- **Selection Logic**: Filtering options in dropdown/select inputs based on context
- **Display Formatting**: Transforming data for display (dates, numbers, translations)

### Why Minimal Frontend Logic?

1. **Reduced API Requests**: Some logic is placed in the frontend to avoid excessive REST calls, but this should be the exception, not the rule
2. **Mobile Portability**: If the frontend were reimplemented as a mobile app, excessive business logic would need to be duplicated or refactored
3. **Single Source of Truth**: Business rules in the backend ensure consistency across all clients
4. **Easier Maintenance**: Changes to business rules only need to be made in one place

### Examples

**DO** (Frontend):
- Disable a button when required fields are empty
- Filter a dropdown based on a previously selected value
- Format a date for display

**DON'T** (Move to Backend):
- Complex calculations or aggregations
- Data deduplication logic
- Matching/comparison algorithms
- Business rule enforcement beyond simple validation

## Component Separation: Form Inputs + Table

**CRITICAL**: Never combine form inputs (dropdowns, checkboxes, etc.) and a data table into a single monolithic component. Always split them:

1. **Parent component** — Uses `dynamic-form` (with `{nonModal: true}`) for input controls (dropdowns, checkboxes, etc.). Handles data loading and passes results to the child. For summary/statistics display, extend `ShowRecordConfigBase` and use `getValueByPath()` with `ColumnConfig`.
2. **Child table component** — Extends `TableConfigBase` (or appropriate base class). Receives data via `@Input()` and implements `OnChanges`. All column definitions, sorting, filtering, and translated value stores belong here.

This separation ensures that the table gets proper sorting, filtering, column visibility, and translated value support from its base class. Mixing form logic with table logic in one component leads to missing functionality and violations of GT's data type formatting patterns.

**Reference pattern**: `SingleRecordMasterViewBase` + child table (e.g., `SecurityaccountImportTransactionComponent` + `SecurityaccountImportTransactionTableComponent`).

**Data flow**:
```
Parent: dynamic-form input changes → loadData() → response received
  → pass details to child via @Input() binding
  → display summary stats in fieldsets using ShowRecordConfigBase.createColumnConfig() + getValueByPath()
Child: @Input() data changes → ngOnChanges → createTranslatedValueStore(data)
```

## PrimeNG Table and Tree Components

### Mandatory Base Class Inheritance

Components that use PrimeNG tables (`p-table`) or trees (`p-tree`, `p-treeTable`) **MUST** extend one of the following base classes:

| Base Class | Use Case |
|------------|----------|
| `ShowRecordConfigBase` | Single record display or simple dialog tables without sorting/filtering needs |
| `TableConfigBase` | **Standalone table view components** - tables with filtering, sorting, column visibility, user settings persistence |
| `TableEditConfigBase` | **Editable table components** using `EditableTableComponent` - provides `addEditColumnFeqH()`, sorting, translated values |
| `TableCrudSupportMenu` | Full CRUD tables with context menus, dialogs, and entity management |
| `TableTreetableTotalBase` | Tables or tree-tables with total/subtotal calculations |

**IMPORTANT**: Standalone table view components (e.g., `*TableComponent`) should extend at least `TableConfigBase`, not `ShowRecordConfigBase`. Use `ShowRecordConfigBase` only for single record displays or simple embedded tables in dialogs where filtering/sorting is not needed.

### Why Base Classes Are Required

1. **Translation Support**: Base classes provide `translateHeadersAndColumns()` for proper i18n of column headers
2. **Column Configuration**: Standardized `addColumn()`, `addColumnFeqH()` methods ensure consistent column setup
3. **Value Formatting**: `getValueByPath()` handles data type formatting, translations, and custom value functions
4. **User Settings**: Column visibility and table configurations can be persisted to LocalStorage
5. **Consistency**: All tables in the application follow the same patterns

### Always Use GT Data Types for Value Formatting

**CRITICAL**: All displayed/formatted values — whether in tables, summary statistics, single record views, or any other context — **MUST** use Grafioschtrader's data type infrastructure (`ColumnConfig`, `DataType`, `getValueByPath`). **Never** use raw Angular pipes (`| number`, `| percent`, `| currency`, `| date`) for formatting calculated or data-driven values.

**Why**: GT's formatting system (`AppHelper.getValueByPathWithField()`) provides locale-aware number/date formatting consistent across the entire application. Raw Angular pipes bypass this and produce inconsistent formatting.

**Pattern for values outside tables** (e.g., summary statistics displayed above/below a table):

1. Create standalone `ColumnConfig` objects using `ShowRecordConfigBase.createColumnConfig()` with the appropriate `DataType`
2. Translate the headers manually via `translateService.get()`
3. Display using `getValueByPath(dataObject, columnConfig)` in the template

```typescript
// In the component class
summaryFields: ColumnConfig[] = [];

private initSummaryFields(): void {
  this.summaryFields = [
    ShowRecordConfigBase.createColumnConfig(DataType.String, 'planName', 'PLAN'),
    ShowRecordConfigBase.createColumnConfig(DataType.NumericInteger, 'totalCount', 'TOTAL'),
    ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'averageValue', 'AVERAGE_VALUE'),
  ];
  const headerKeys = this.summaryFields.map(f => f.headerKey);
  this.translateService.get(headerKeys).subscribe(translations => {
    this.summaryFields.forEach(f => f.headerTranslated = translations[f.headerKey]);
  });
}
```

```html
<!-- In the template -->
@for (sf of summaryFields; track sf.field) {
  <span><strong>{{ sf.headerTranslated }}:</strong> {{ getValueByPath(responseData, sf) }}</span>
}
```

**DO**:
```typescript
ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'meanError', 'MEAN_ERROR')
// then: {{ getValueByPath(response, field) }}
```

**DON'T**:
```html
{{ response.meanError | number:'1.2-2' }}
{{ response.totalCount | number:'1.0-0' }}
```

### Using ConfigurableTableComponent

When using `ConfigurableTableComponent`, **always** bind the `valueGetterFn`:

```html
<configurable-table
  [data]="dataList"
  [fields]="fields"
  [valueGetterFn]="getValueByPath.bind(this)"
  [baseLocale]="baseLocale">
</configurable-table>
```

**CRITICAL**: The `[valueGetterFn]="getValueByPath.bind(this)"` binding is **required** for NLS translations to work in table columns. Without this binding:
- Columns with `translateValues: TranslateValue.NORMAL` will not display translated values
- The table will show raw enum/status values instead of localized text
- Sorting on translated columns will not work correctly

**CRITICAL**: For sorting to work, you **must** also bind `[customSortFn]` and `[multiSortMeta]`. The component defaults `enableCustomSort = true`, which requires a `customSortFn` callback — without it, clicking column headers does nothing silently.

```html
<configurable-table
  [data]="dataList"
  [fields]="fields"
  [customSortFn]="customSort.bind(this)"
  [multiSortMeta]="multiSortMeta"
  [valueGetterFn]="getValueByPath.bind(this)"
  [baseLocale]="baseLocale">
</configurable-table>
```

The `customSort` method is provided by `TableConfigBase`. Initialize default sort order in `ngOnInit()`:
```typescript
this.multiSortMeta.push({field: 'myDateField', order: -1}); // -1 = descending
```

### Translation of Table Values

For columns with translatable values (enums, status codes, etc.):

1. Set `translateValues: TranslateValue.NORMAL` in column options
2. Call `createTranslatedValueStore(data)` **after both fields are defined AND data is loaded**
3. The translated values will be available for display and sorting

```typescript
// Column definition
this.addColumn(DataType.String, 'status', 'STATUS', true, false,
  {translateValues: TranslateValue.NORMAL});

// After loading data
this.createTranslatedValueStore(this.entityList);
```

**IMPORTANT**: `createTranslatedValueStore()` must be called only after:
- All column fields have been defined (via `addColumn()`/`addColumnFeqH()`)
- `prepareTableAndTranslate()` has been called
- Data is available

### Dropdown Selection Filters (FilterType.withOptions)

**CRITICAL**: Columns using `FilterType.withOptions` require **three method calls** after data loads. Missing any step results in an empty or broken dropdown.

| Step | Method | Purpose |
|------|--------|---------|
| 1 | `createTranslatedValueStoreAndFilterField(data)` | Builds `translatedValueMap` on columns with `translateValues`, creates `field$` properties |
| 2 | `prepareFilter(data)` | Populates `field.filterValues` (the `ValueLabelHtmlSelectOptions[]`) that the `<p-select>` dropdown reads |

Without `prepareFilter(data)`, the dropdown renders but has **no options**.

**Column definition** — use `FilterType.withOptions` and, for enum columns, `TranslateValue.NORMAL`:

```typescript
// Translated enum values → dropdown shows translated labels
this.addColumnFeqH(DataType.String, 'transactionType', true, false,
  {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});

// Plain string values → dropdown shows unique values from data
this.addColumn(DataType.String, 'cashaccount.name', 'CASHACCOUNT', true, false,
  {filterType: FilterType.withOptions});

// Text input filter (not a dropdown)
this.addColumnFeqH(DataType.String, 'securityName', true, false,
  {filterType: FilterType.likeDataType});
```

**How `prepareFilter` works internally**: For translated columns, it reads `field.translatedValueMap` (built by `createTranslatedValueStore`) and creates sorted dropdown options with translated labels. For plain string columns, it extracts unique values from the data. In both cases it populates `field.filterValues` which the template's `<p-select>` binds to.

In components using `OnChanges` with `@Input()` data, note that `ngOnChanges` is called **before** `ngOnInit`. Use a flag to ensure proper ordering:

```typescript
private fieldsInitialized = false;

constructor(...) {
  super(...);
  this.addColumnFeqH(DataType.String, 'status', true, false,
    {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
  // ... more columns ...
  this.prepareTableAndTranslate();
  this.fieldsInitialized = true;
}

ngOnChanges(changes: SimpleChanges): void {
  if (changes['data'] && this.data && this.fieldsInitialized) {
    this.createTranslatedValueStoreAndFilterField(this.data);
    this.prepareFilter(this.data);
  }
}
```

### Using EditableTableComponent

**CRITICAL**: Components that use `EditableTableComponent` **MUST** be implemented as a **separate component** extending `TableEditConfigBase`. Do NOT embed `EditableTableComponent` directly in a dialog component or any component that does not extend a table base class. Without the proper base class:
- **Sorting will not work** — `EditableTableComponent` uses `enableCustomSort = true` by default, which requires a `customSortFn` callback. `TableConfigBase.customSort()` provides this.
- **Translated enum values will not sort correctly** — the base class `getValueByPath()` resolves translated values for sorting
- **`createTranslatedValueStoreAndFilterField()` is unavailable** — enum columns will show raw keys instead of translated text

**Required pattern**: Create a standalone component that extends `TableEditConfigBase`, define columns with `addEditColumnFeqH()`, and bind `[customSortFn]="customSort.bind(this)"` and `[valueGetterFn]="getValueByPath.bind(this)"`:

```typescript
@Component({
  selector: 'my-editable-table',
  template: `
    <editable-table #entityTable
      [data]="items" [fields]="fields" dataKey="id"
      [batchMode]="true" [startInEditMode]="true"
      [valueGetterFn]="getValueByPath.bind(this)"
      [customSortFn]="customSort.bind(this)"
      [baseLocale]="baseLocale">
    </editable-table>
  `,
  standalone: true,
  imports: [EditableTableComponent]
})
export class MyEditableTableComponent extends TableEditConfigBase {
  constructor(filterService: FilterService, usersettingsService: UserSettingsService,
              translateService: TranslateService, gps: GlobalparameterService) {
    super(filterService, usersettingsService, translateService, gps);
    // Define columns here using addEditColumnFeqH()
    this.prepareTableAndTranslate();
  }
}
```

**Reference implementations**:
- `TradingPeriodTableComponent` — batch-mode editable table embedded in a dialog
- `MailForwardSettingTableEditComponent` — standalone editable table with row-by-row save and `IGlobalMenuAttach`

### Prefer addColumnFeqH over addColumn

Just as `DynamicFieldHelper` provides `*HeqF` methods for form fields, `ShowRecordConfigBase` provides `addColumnFeqH` for table columns. It derives the header/translation key automatically from the field name using the same `UPPER_SNAKE_CASE` conversion.

**Rule**: **Always use `addColumnFeqH` by default.** Only use `addColumn` with an explicit header key when:
- The field uses a **dotted path** (e.g., `cashaccount.name`) where the derived key would be wrong (`NAME` instead of `CASHACCOUNT`)
- There is a **conflict** with an existing NLS key that has a different meaning

```typescript
// DEFAULT: Use addColumnFeqH — header key auto-derived from field name
this.addColumnFeqH(DataType.Numeric, 'cashaccountAmount', true, false);    // → 'CASHACCOUNT_AMOUNT'
this.addColumnFeqH(DataType.DateString, 'validFrom', true, false);         // → 'VALID_FROM'
this.addColumnFeqH(DataType.String, 'transactionType', true, false,        // → 'TRANSACTION_TYPE'
  {translateValues: TranslateValue.NORMAL});

// EXCEPTION: Dotted paths — derived key strips prefix (cashaccount.name → 'NAME'), so use explicit key
this.addColumn(DataType.String, 'cashaccount.name', 'CASHACCOUNT', true, false);
this.addColumn(DataType.String, 'security.name', 'SECURITY', true, false);
```

## PrimeNG Button Patterns (PrimeNG 21+)

**IMPORTANT**: In PrimeNG 21+, the `icon` attribute on `<p-button>` and `pButton` is **deprecated**. Set the
icon with the **`pButtonIcon` directive** on a projected child element instead. The `<i pButtonIcon>` element is
picked up via content projection (the button's `iconSignal = contentChild(ButtonIcon)` query) and styled as the
button icon. `ButtonModule` already exports the `pButtonIcon` directive, so no extra import is needed.

### Preferred: `<p-button>` Component

```html
<!-- Basic button with label -->
<p-button [label]="'SAVE' | translate" (click)="save()" />

<!-- Button with icon (project an <i pButtonIcon> child) -->
<p-button [label]="'SAVE' | translate" (click)="save()">
  <i class="pi pi-check" pButtonIcon></i>
</p-button>

<!-- Icon-only button -->
<p-button (click)="save()">
  <i class="pi pi-check" pButtonIcon></i>
</p-button>

<!-- Button with severity/style -->
<p-button [label]="'DELETE' | translate" severity="danger" (click)="delete()">
  <i class="pi pi-trash" pButtonIcon></i>
</p-button>
```

### Alternative: `pButton` Directive with Content Projection

When you need a native `<button>` element (e.g., for form submission), project the icon the same way:

```html
<!-- Text only -->
<button pButton type="button" (click)="save()">
  {{ 'SAVE' | translate }}
</button>

<!-- With icon - use a pButtonIcon child element -->
<button pButton type="submit" class="btn">
  <i class="pi pi-check" pButtonIcon></i>
  {{ 'SAVE' | translate }}
</button>
```

### DEPRECATED - Do NOT Use

```html
<!-- WRONG: the icon attribute on p-button / pButton is deprecated -->
<p-button [label]="'SAVE' | translate" icon="pi pi-check" (click)="save()" />
<button pButton icon="pi pi-check" (click)="save()"></button>
```

## Dialog Components

### Dialog Base Class Hierarchy

The frontend uses a hierarchical inheritance structure for dialog components:

```
FormBase (abstract)
├── SimpleEditBase (abstract directive)
│   └── SimpleEntityEditBase<T> (abstract directive)
└── SimpleDynamicEditBase<T> (abstract directive)
```

**Location**: `src/app/lib/edit/`

### Base Class Selection Guide

| Scenario | Base Class | Examples |
|----------|-----------|----------|
| Modal dialog with custom submit logic, no entity service | **SimpleEditBase** | `UploadFileDialogComponent`, `HistoryquoteDeleteDialogComponent` |
| Modal dialog with entity CRUD via service | **SimpleEntityEditBase<T>** | `HistoryquoteEditComponent`, `CashaccountEditComponent` |
| Programmatically opened dialog (via `DialogService.open()`) | **SimpleDynamicEditBase<T>** | `PortfolioEditDynamicComponent` |
| Dialog displaying a table (read-only) | **ShowRecordConfigBase** | Table display dialogs |

### SimpleEditBase - Standard Modal Dialog

**Use when**: Dialog has custom submit logic without automatic entity persistence.

**Provides**:
- `@Input() visibleDialog: boolean` - Dialog visibility control
- `@Output() closeDialog: EventEmitter<ProcessedActionData>` - Close event
- `@ViewChild(DynamicFormComponent) form` - Form access
- `onShow(event)` / `onHide(event)` - Lifecycle handlers
- `helpLink()` - Context-sensitive help

**Required Implementation**:
```typescript
protected abstract initialize(): void;  // Called on dialog show
```

**Example**:
```typescript
@Component({
  template: `
    <p-dialog [visible]="visibleDialog" (onShow)="onShow($event)" (onHide)="onHide($event)">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class MyDialogComponent extends SimpleEditBase implements OnInit {
  @Input() customInput: string;

  constructor(
    public translateService: TranslateService,
    gps: GlobalparameterService,
    private myService: MyService
  ) {
    super(HelpIds.HELP_MY_DIALOG, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('name', 64, true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    // Initialize form state when dialog opens
  }

  submit(value: any): void {
    // Custom submit logic
    this.myService.doSomething(value).subscribe({
      next: () => this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED))
    });
  }
}
```

### SimpleEntityEditBase<T> - Entity CRUD Dialog

**Use when**: Dialog creates/updates an entity via a service implementing `ServiceEntityUpdate<T>`.

**Extends SimpleEditBase** and adds:
- Automatic entity persistence via `serviceEntityUpdate.update()`
- Success/error toast notifications
- Audit trail support for proposed changes

**Required Implementation**:
```typescript
protected abstract getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): T;
```

**Example**:
```typescript
export class HistoryquoteEditComponent extends SimpleEntityEditBase<Historyquote> implements OnInit {
  @Input() callParam: HistoryquoteCallParam;

  constructor(
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    historyquoteService: HistoryquoteService  // implements ServiceEntityUpdate<Historyquote>
  ) {
    super(HelpIds.HELP_HISTORYQUOTES, 'HISTORYQUOTE', translateService, gps,
      messageToastService, historyquoteService);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: any): Historyquote {
    const historyquote = new Historyquote();
    this.copyFormToPrivateBusinessObject(historyquote, this.callParam.existingEntity);
    return historyquote;
  }
}
```

#### The `i18nRecord` argument (2nd `super()` parameter) — never use `.toUpperCase()`

The 2nd constructor argument is the `i18nRecord` **NLS key**. On save/delete it is passed as an
`i18n`-prefixed interpolation param to `MSG_RECORD_SAVED` / `MSG_DELETE_RECORD`, and the toast
component (`lib/message/message.toast.component.ts`) resolves any `i18n*` param **as a translation
key** via `translateService.get(value)`. So `i18nRecord` must be a real `UPPER_SNAKE_CASE` key.

When you derive it from an `AppSettings.*` / `BaseSettings.*` entity constant, those constants are
**PascalCase** (e.g. `TRADING_CALENDAR_RULE_SET = 'TradingCalendarRuleSet'`). Always wrap them with
`AppHelper.toUpperCaseWithUnderscore(...)`, **never** plain `.toUpperCase()`:

```typescript
// WRONG — .toUpperCase() drops the underscores: 'TradingCalendarRuleSet' → 'TRADINGCALENDARRULESET'
//         no such NLS key → the toast shows the raw token instead of the translated label.
super(HelpIds.HELP_BASEDATA_TRADING_CALENDAR_RULE, AppSettings.TRADING_CALENDAR_RULE_SET.toUpperCase(), ...);

// CORRECT — yields the real key 'TRADING_CALENDAR_RULE_SET'
super(HelpIds.HELP_BASEDATA_TRADING_CALENDAR_RULE,
  AppHelper.toUpperCaseWithUnderscore(AppSettings.TRADING_CALENDAR_RULE_SET), ...);
```

`toUpperCaseWithUnderscore` is a **no-op for single-word constants** (`'Cashaccount'` → `CASHACCOUNT`),
so it is always the correct choice — use it even when the current constant happens to be one word, so
the code stays correct if the constant later becomes multi-word. A ready `UPPER_SNAKE_CASE` string
literal (e.g. `'HISTORYQUOTE'` above) is equally fine; the pitfall is only `.toUpperCase()` on a
PascalCase constant.

### SimpleDynamicEditBase<T> - Programmatic Dialog

**Use when**: Dialog is opened programmatically via `DialogService.open()`, not template-bound.

**Key Differences**:
- Uses `DynamicDialogConfig` and `DynamicDialogRef` instead of `@Input/@Output`
- Closes via `dynamicDialogRef.close()` instead of `EventEmitter`
- Data passed via `dynamicDialogConfig.data`

**Example**:
```typescript
// Dialog component
export class PortfolioEditDynamicComponent extends SimpleDynamicEditBase<Portfolio> {
  constructor(
    dynamicDialogConfig: DynamicDialogConfig,
    dynamicDialogRef: DynamicDialogRef,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    portfolioService: PortfolioService
  ) {
    super(dynamicDialogConfig, dynamicDialogRef, HelpIds.HELP_PORTFOLIO,
      translateService, gps, messageToastService, portfolioService);
  }
}

// Opening the dialog
const dialogRef = this.dialogService.open(PortfolioEditDynamicComponent, {
  header: 'Create Portfolio',
  data: { callParam: { thisObject, parentObject } },
  modal: true, closable: true, closeOnEscape: true
});
dialogRef.onClose.subscribe((result: ProcessedActionData) => { ... });
```

> **ALWAYS make `DialogService.open(...)` dialogs closable.** This PrimeNG DynamicDialog setup does
> **not** reliably default to a closable dialog, so every `dialogService.open(...)` config **must**
> include `closable: true` (shows the X) and `closeOnEscape: true` (ESC closes), plus `modal: true`.
> Omitting them produces a dialog the user cannot dismiss — especially a read-only/table dialog that
> has no submit button. The only exception is a deliberately forced dialog (e.g. forced password
> change), which sets them to `false` on purpose. Match the existing convention used by
> `ColumnVisibilityDialogComponent` (`lib/datashowbase/table.config.base.ts`) and the search dialogs.

### Dialog Components with Tables

Dialog components displaying tables should extend `ShowRecordConfigBase`:

```typescript
@Component({
  template: `
    <p-dialog (onShow)="onShow()">
      <configurable-table
        [data]="items"
        [fields]="fields"
        [valueGetterFn]="getValueByPath.bind(this)"
        [baseLocale]="baseLocale">
      </configurable-table>
    </p-dialog>
  `
})
export class MyTableDialogComponent extends ShowRecordConfigBase {
  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
    this.addColumnFeqH(DataType.String, 'name', true, false);
  }

  onShow(): void {
    this.loadData();
    this.translateHeadersAndColumns();
  }
}
```

## Conditional Field Dependencies (Required + Enable/Disable)

When form fields only make sense given specific values in other fields, use `DynamicFieldHelper.resetValidator()` combined with `formControl.enable()/disable()` to dynamically toggle field state.

### Standard Helper Methods

Add these private methods to dialog components that need conditional dependencies:

```typescript
private enableField(fieldName: string, required: boolean): void {
  const fc = this.configObject[fieldName];
  fc.formControl.enable();
  DynamicFieldHelper.resetValidator(fc, required ? [Validators.required] : []);
}

private disableAndClearField(fieldName: string): void {
  const fc = this.configObject[fieldName];
  fc.formControl.setValue(null);
  DynamicFieldHelper.resetValidator(fc, []);
  fc.formControl.disable();
}
```

- `enableField` enables the control and sets/removes the required validator (with visual asterisk via `resetValidator`)
- `disableAndClearField` clears the value, removes validators, and disables the control. The `setValue(null)` is required because `cleanMaskAndTransferValuesToBusinessObject` copies disabled field values too

### Subscription Lifecycle

1. **Subscribe before `setDefaultValuesAndEnableSubmit()`** — subscriptions must be in place before the form is initialized
2. **Call the update method initially** — pass the entity's existing value (for edit mode) or `undefined` (for create mode) to set the correct initial state
3. **No explicit cleanup needed** — the dialog component is recreated each time it opens, so subscriptions are naturally garbage-collected

```typescript
protected override initialize(): void {
  // 1. Set dropdown options
  // 2. Subscribe to valueChanges + call initial state
  this.configObject.myField.formControl.valueChanges.subscribe(value =>
    this.updateMyFieldDependencies(value));
  this.updateMyFieldDependencies(this.entity?.myField);

  // 3. Enable form
  this.form.setDefaultValuesAndEnableSubmit();

  // 4. Transfer existing entity values (edit case)
  if (this.entity) {
    this.form.transferBusinessObjectToForm(this.entity);
  }
}
```

### Cascading Dependencies

When field A controls field B, and field B controls field C, cascade from the parent update method:

```typescript
private updateResponseFormatDependencies(format: string): void {
  if (format === 'JSON') {
    this.enableField('jsonDataStructure', true);
    // Cascade into jsonDataStructure dependencies
    this.updateJsonDataStructureDependencies(this.configObject.jsonDataStructure.formControl.value);
  }
}
```

Guard child subscriptions so they only apply when the parent is in the correct state:

```typescript
this.configObject.childField.formControl.valueChanges.subscribe(value => {
  if (this.configObject.parentField.formControl.value === 'EXPECTED_VALUE') {
    this.updateChildDependencies(value);
  }
});
```

### Reference Implementations

- `GenericConnectorEndpointEditComponent` — `responseFormat` → JSON/CSV/HTML fields, `htmlExtractMode` → regex/split fields, `jsonDataStructure` → columnNamesPath, `tickerBuildStrategy` → currencyPair fields
- `GenericConnectorDefEditComponent` — `rateLimitType` → requests/period/concurrent fields

## DynamicFieldHelper Methods

### Always Use HeqF Methods by Default

`DynamicFieldHelper` provides two variants for most field creation methods:
- **Standard**: `createFieldInputString(fieldName, headerKey, ...)` - explicit header key
- **HeqF**: `createFieldInputStringHeqF(fieldName, ...)` - header key derived from field name

**HeqF** stands for "Header equals Field" - the header/translation key is automatically derived from the field name by converting to `UPPER_SNAKE_CASE`.

**Rule**: **Always use `*HeqF` methods by default.** If the required NLS key doesn't exist, create it in the translation files. Only use standard methods with explicit header keys when there is a **conflict with an existing NLS key that has a different meaning**.

### Textarea vs Input: Use Textarea for maxLength > 80

**Rule**: When a string field has `maxLength > 80`, use `createFieldTextareaInputString*` instead of `createFieldInputString*`. A single-line input becomes too narrow for long values; a textarea gives the user room to see and edit the full content.

```typescript
// maxLength <= 80 → single-line input
DynamicFieldHelper.createFieldInputStringHeqF('shortId', 32, true)

// maxLength > 80 → textarea
DynamicFieldHelper.createFieldTextareaInputStringHeqF('readableName', 100, true)
DynamicFieldHelper.createFieldTextareaInputStringHeqF('domainUrl', 255, true)
```

### Field Name to Header Key Conversion

| Field Name | Derived Header Key |
|------------|-------------------|
| `name` | `NAME` |
| `mode` | `MODE` |
| `idUser` | `ID_USER` |
| `headName` | `HEAD_NAME` |
| `idGtNetSecurityImpHead` | `ID_GT_NET_SECURITY_IMP_HEAD` |

### When to Use Each Variant

```typescript
// DEFAULT: Always use HeqF methods - create NLS keys if they don't exist
DynamicFieldHelper.createFieldInputStringHeqF('name', 64, true)                      // 'name' → 'NAME'
DynamicFieldHelper.createFieldSelectStringHeqF('mode', true)                         // 'mode' → 'MODE'
DynamicFieldHelper.createFieldSelectNumberHeqF('idGtNetSecurityImpHead', false)      // → 'ID_GT_NET_SECURITY_IMP_HEAD'
DynamicFieldHelper.createFieldInputStringHeqF('headName', 64, false)                 // → 'HEAD_NAME'

// EXCEPTION: Only use explicit header key when there's a conflict with existing NLS key
// Example: 'status' field but 'STATUS' NLS key already means something different in context
DynamicFieldHelper.createFieldSelectString('status', 'ORDER_STATUS', true)
```

### Workflow for New Fields

1. **Check if HeqF-derived key exists**: Convert field name to `UPPER_SNAKE_CASE`
2. **If key exists with correct meaning**: Use HeqF method directly
3. **If key doesn't exist**: Create it in both language files of the owning backend module (see
   "Translation / i18n — the backend is the only source"), then use the HeqF method
4. **If key exists but has conflicting meaning**: Use standard method with a different explicit key

### Available HeqF Methods

| Standard Method | HeqF Variant |
|-----------------|--------------|
| `createFieldInputString` | `createFieldInputStringHeqF` |
| `createFieldSelectString` | `createFieldSelectStringHeqF` |
| `createFieldSelectNumber` | `createFieldSelectNumberHeqF` |
| `createFieldCheckbox` | `createFieldCheckboxHeqF` |
| `createFieldPcalendar` | `createFieldPcalendarHeqF` |
| `createFieldTextareaInputString` | `createFieldTextareaInputStringHeqF` |
| `createFieldInputNumber` | `createFieldInputNumberHeqF` |
| `createFieldMultiSelectString` | `createFieldMultiSelectStringHeqF` |
| `createFieldDropdownString` | `createFieldDropdownStringHeqF` |
| `createFieldInputWebUrl` | `createFieldInputWebUrlHeqF` |
| `createFieldTriStateCheckbox` | `createFieldTriStateCheckboxHeqF` |
| `createFieldMinMaxNumber` | `createFieldMinMaxNumberHeqF` |

See `src/app/lib/helper/dynamic.field.helper.ts` for complete list and signatures.

### Field Layout with usedLayoutColumns

The `usedLayoutColumns` option controls field width using a 12-column grid system:
- **Default (omitted)**: 12 columns = full width
- **6**: Half width (two fields per row)
- **4**: One-third width (three fields per row)

**Rule**: Only use `usedLayoutColumns` in **non-modal views** where multiple fields should appear side-by-side. **Do not use it in modal dialogs** — with one exception: **InputButton fields** (created via `createFieldInputButtonHeqF`) should share a line with the input they belong to, even in dialogs. Place the InputButton field directly after its associated input and give both `usedLayoutColumns: 6`.

| Context | usedLayoutColumns | Example |
|---------|-------------------|---------|
| Modal dialog (`<p-dialog>`) | **Omit** (full width) | `HistoryquoteDeleteDialogComponent` |
| Modal dialog with InputButton | `6` for the input + `6` for the InputButton | `StandingOrderSecurityEditComponent` |
| Non-modal view with `formConfig: {nonModal: true}` | `6` for side-by-side | `GTNetSecurityImportComponent` |
| Master view with dropdown + note | `6` for both fields | `SecurityaccountImportTransactionComponent` |

**Example - Non-modal view with side-by-side fields:**
```typescript
this.formConfig = {labelColumns: 2, nonModal: true};
this.config = [
  DynamicFieldHelper.createFieldSelectNumberHeqF('idEntity', false, {usedLayoutColumns: 6}),
  DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', 1000, false, {usedLayoutColumns: 6, disabled: true})
];
```

**Example - Modal dialog (no usedLayoutColumns):**
```typescript
this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
this.config = [
  DynamicFieldHelper.createFieldSelectStringHeqF('mode', true),
  DynamicFieldHelper.createFieldInputStringHeqF('name', 64, true),
  DynamicFieldHelper.createSubmitButton()
];
```

## Checklist for Creating New Dialog Components

**IMPORTANT**: Before creating a new dialog component, follow this checklist to ensure consistency with existing patterns.

### 1. Choose the Correct Base Class

| If the dialog... | Extend |
|------------------|--------|
| Has custom submit logic, no entity service | `SimpleEditBase` |
| Creates/updates an entity via service | `SimpleEntityEditBase<T>` |
| Is opened programmatically via `DialogService.open()` | `SimpleDynamicEditBase<T>` |
| Displays a read-only table | `ShowRecordConfigBase` |

### 2. Reference an Existing Similar Dialog

Before writing code, find and read an existing dialog that matches your use case:
- `HistoryquoteDeleteDialogComponent` - Simple dialog with custom logic
- `HistoryquoteEditComponent` - Entity CRUD dialog
- `PortfolioEditDynamicComponent` - Programmatic dialog

### 3. Field Configuration Checklist

- [ ] Use `*HeqF` methods by default (create NLS keys if needed)
- [ ] **Do NOT use `usedLayoutColumns`** in modal dialogs
- [ ] Add NLS keys to both `messages.properties` and `messages_de.properties` of the owning backend module
- [ ] Use `DynamicFieldHelper.createSubmitButton()` for the submit button

### 4. Template Checklist

- [ ] Pass `$event` to lifecycle handlers: `(onShow)="onShow($event)" (onHide)="onHide($event)"`
- [ ] Bind `translateService`: `[translateService]="translateService"`
- [ ] Use `#form="dynamicForm"` for form reference

### 5. Constructor and Initialization

- [ ] Call `super()` with appropriate parameters (help ID, etc.)
- [ ] Set up `formConfig` using `AppHelper.getDefaultFormConfig()` with `this.helpLink.bind(this)`
- [ ] Use `TranslateHelper.prepareFieldsAndErrors()` to initialize `configObject`

### Example Template for SimpleEditBase Dialog

```typescript
@Component({
  template: `
    <p-dialog header="{{'DIALOG_TITLE' | translate}}" [visible]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  standalone: false
})
export class MyDialogComponent extends SimpleEditBase implements OnInit {
  @Input() myInput: SomeType;

  constructor(
    public translateService: TranslateService,
    gps: GlobalparameterService,
    private myService: MyService
  ) {
    super(HelpIds.HELP_MY_FEATURE, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('name', 64, true),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idEntity', false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    // Load data or initialize form state
  }

  submit(value: any): void {
    this.myService.doSomething(value).subscribe({
      next: () => this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED)),
      error: () => this.configObject.submit.disabled = false
    });
  }
}
```

## File Organization

- **Components**: `src/app/<module>/component/`
- **Services**: `src/app/<module>/service/`
- **Types/Models**: `src/app/<module>/types/` or `src/app/entities/`
- **Shared utilities**: `src/app/lib/` (reusable across modules)

### Enums Mirrored From the Backend

Some TypeScript enums are hand-maintained copies of a backend Java enum, because the dropdown options are
built from the enum object instead of from a REST endpoint. A constant missing from the mirror can never
be selected and reverse lookups render `undefined` — see `backend/CLAUDE.md` →
"Enums Mirrored in the Frontend" for the full rule and the current list of pairs.

Every mirror declares its counterpart in its file comment, with a path relative to `backend/`:

```ts
 * Corresponds to backend: grafioschtrader-common/src/main/java/grafioschtrader/types/TaskTypeExtended.java
```

`src/enum.mirror.spec.ts` collects every file carrying that marker and fails `npm test` when the two
sides disagree on a constant name or value. **A new mirror must carry the marker line** — that is all it
takes to enrol it in the guard.

## Translation / i18n — the backend is the only source

**IMPORTANT**: There are **no translation files in the frontend**. `src/assets/i18n/` and
`src/app/lib/assets/` no longer exist. Every user interface text lives in a backend
`messages*.properties` file and is delivered by `GET /api/globalparameters/properties/{language}`,
which `MultiTranslateHttpLoader` loads as ngx-translate's single source (GitHub issue #214).

### Where a new text goes

Pick the module by the layer of the code that **uses** the key, exactly like the backend rule in
`backend/CLAUDE.md`:

| Consuming code | Properties file |
|----------------|-----------------|
| `src/app/lib/**` (reusable library) | `backend/grafiosch-base/src/main/resources/i18n/messages{,_de}.properties` |
| `src/app/<module>/**` (application) | `backend/grafioschtrader-common/src/main/resources/message/messages{,_de}.properties` |

Putting a library-consumed text in the application module compiles, passes tests and works in the
full application — and breaks the standalone `grafiosch` server, which serves the
`grafiosch-base` bundle alone. `node scripts/nls-tool.mjs check` catches this; run it after adding
keys used from `src/app/lib`.

### How a key is written

The client key is derived from the stored key by `grafiosch.nls.NlsKeyMapper`:

| Stored key | Client key | Use for |
|------------|-----------|---------|
| `READABLE_NAME` | `READABLE_NAME` | the normal case — write the key exactly as the frontend uses it |
| `readable.name` | `READABLE_NAME` | a field label that the backend **also** resolves server-side (`@Valid`, `DataViolationException`) |
| `c.required` | `required` | a **client-only** key: dynamic-form validator messages and the `login.*` codes |
| `GT_FILTER.gtIS` | `{GT_FILTER: {gtIS: ...}}` | the only allow-listed nested namespace |
| `g.…`, `gt.…`, `UDF_…` | unchanged | configuration parameters and metadata |

So in almost all cases you simply add `MY_KEY=My text` to both language files of the right module.

### Rules

1. **Always update both languages** — the build fails on any key present in only one of the two files.
2. If the German text is deliberately identical to the English one (a proper noun, an abbreviation),
   list the key in that module's `nls-en-reuse.txt`; otherwise the build reports it as a forgotten
   translation.
3. **UTF-8 without BOM** — the German file is full of umlauts and the guard rejects any invalid byte.
4. Use ngx-translate `{{name}}` placeholders for client-only texts and `MessageFormat` `{0}` for texts
   the server resolves; never both on one key, and double every `'` in a `{0}` text.
5. Keys may contain `A-Z a-z 0-9 _ . |` only — a space silently truncates the key in `.properties`.

`primeng` widget texts are the one exception: they stay on the client in
`src/app/lib/translator/primeng.translations.ts`, because they contain string arrays and differ in
size between the languages.

## Translation Keys

- Use UPPER_SNAKE_CASE for keys
- Add tooltip translations with `_TOOLTIP` suffix (e.g., `FIELD_NAME_TOOLTIP`); a missing `_TOOLTIP`
  key is harmless — `filterOut` suppresses the tooltip when the key does not resolve
- When deriving a key from a PascalCase/camelCase `AppSettings.*` / `BaseSettings.*` constant (e.g. the
  `i18nRecord` dialog argument), use `AppHelper.toUpperCaseWithUnderscore(...)`, **never** `.toUpperCase()`
  — the latter drops the underscores (`TradingCalendarRuleSet` → `TRADINGCALENDARRULESET`) and misses the key

### If the server is unreachable

Since the backend is the only source of texts, a failed load means there is nothing to render. The
application initializer in `app.module.ts` therefore stops the bootstrap and shows the bilingual
overlay from `nls.bootstrap.failure.ts` instead of painting raw keys everywhere.

## IGlobalMenuAttach Interface Pattern

**IMPORTANT**: When a component extends `TableEditConfigBase` directly (not via `TableCrudSupportMenu`), it **MUST** implement the `IGlobalMenuAttach` interface with a proper `resetMenu()` pattern to integrate context menu items with the application's main menu bar.

### Required Implementation

Components extending `TableEditConfigBase` must:

1. **Implement `IGlobalMenuAttach` interface**
2. **Inject `ActivePanelService`**
3. **Create a `resetMenu()` method** that:
   - Builds the context menu items
   - Calls `activePanelService.activatePanel()` to register with the main menu bar
4. **Call `resetMenu()` from**:
   - `onComponentClick()`
   - `onRowSelect()`
   - `onRowUnselect()`
   - After data loads

### Example Implementation

```typescript
@Component({...})
export class MyTableComponent extends TableEditConfigBase implements OnInit, IGlobalMenuAttach {

  contextMenuItems: MenuItem[] = [];

  constructor(
    private activePanelService: ActivePanelService,
    // ... other dependencies
  ) {
    super(filterService, usersettingsService, translateService, gps);
  }

  // IGlobalMenuAttach interface methods
  public getHelpContextId(): string {
    return AppHelpIds.HELP_MY_COMPONENT;
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  // Event handlers must call resetMenu()
  onComponentClick(event: any): void {
    this.resetMenu();
  }

  onRowSelect(event: any): void {
    this.resetMenu();
  }

  onRowUnselect(event: any): void {
    this.resetMenu();
  }

  // Prepare menu items
  prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({
      label: 'CREATE|ENTITY_NAME' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: () => this.handleCreate()
    });
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /**
   * Updates context menu and registers with main menu bar.
   * CRITICAL: Must call activePanelService.activatePanel() to show
   * menu items in both context menu AND main application menu bar.
   */
  private resetMenu(): void {
    this.contextMenuItems = this.prepareEditMenu();
    this.activePanelService.activatePanel(this, {
      showMenu: null,
      editMenu: this.contextMenuItems
    });
  }
}
```

### Why This Pattern Is Required

Without the `activePanelService.activatePanel()` call, menu items will only appear in the context menu when right-clicking. The main menu bar at the top of the application will not show the component's menu items, breaking the consistent UI pattern used throughout the application.

### Nested Components and `consumedGT` Event Guard

**CRITICAL**: When a parent component can contain nested child components that also register with `ActivePanelService` (e.g., an expandable table with an editable child table inside row expansions), the parent's `onComponentClick` **must** check `event[this.consumedGT]` before calling `resetMenu()`. Without this guard, native DOM click events bubble from the child up to the parent's `(click)` handler, causing the parent to overwrite the child's active panel registration — the child's context menu items disappear and only the parent's menu shows.

```typescript
// WRONG — unconditionally overwrites child's active panel
onComponentClick(event: any): void {
  this.resetMenu();
}

// CORRECT — skips when click was already handled by a nested component
onComponentClick(event: any): void {
  if (!event[this.consumedGT]) {
    this.resetMenu();
  }
}
```

The child component (e.g., one extending `TransactionContextMenu`) marks the event as consumed in its own `onComponentClick` via `event[this.consumedGT] = true`. The `consumedGT` property (`'consumedGT'`) is defined on `TableConfigBase`.

**Reference**: `StandingOrderTableBase.onComponentClick` (parent) + `StandingOrderTransactionTableComponent` (nested child via row expansion).

### Reference Implementations

- `MailForwardSettingTableEditComponent` - Clean example of IGlobalMenuAttach pattern
- `GTNetSecurityImportTableComponent` - Example with CSV upload functionality

## Generate Edit Forms From Backend Entity Definitions (Issue #27)

**Prefer generating an edit form from the backend entity's annotations over hand-coding
`DynamicFieldHelper.createField*` calls.** This removes duplicated constraints (max length,
required, ranges, regex) — the backend entity is the single source of truth.

### When to use which approach

| Form | Approach |
|------|----------|
| Whole form derivable from the entity | `DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(translateService, cdias, '', true)` |
| Form with custom layout, only some fields from backend | per-field `DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(translateService, fieldName, fdias)` |
| A constraint that no backend annotation can express | hand-coded `DynamicFieldHelper.createField*` (last resort) |

### How

1. Backend: annotate the entity fields with `@DynamicFormField` + Bean Validation and register the
   entity (see `backend/CLAUDE.md` → "Dynamic Form Definitions").
2. Fetch the definition (memoised) via `GlobalparameterService.getEntityFormDefinition(entityName, dialog?)`.
3. Build the config with `createFieldsFromClassDescriptorInputAndShow(...)`, then
   `TranslateHelper.prepareFieldsAndErrors(...)`.
4. The component only injects **runtime** concerns the entity cannot know: select options loaded at
   runtime (`configObject.field.valueKeyHtmlOptions = …`), and conditional enable/disable/visibility.

### Timing: config must be built synchronously in the dialog's `ngOnInit`

`<dynamic-form>` builds its `FormGroup` from `config` during its own `ngOnInit`, so for a dialog
mounted lazily (`@if (visibleDialog)`) the config **must already be set when the dialog renders** —
assigning it later from an async fetch leaves the form empty (edit values do not transfer reliably).

**Pattern: the opener pre-fetches the descriptor and passes it via `callParam`.** Because
`getEntityFormDefinition()` is memoised, the first open does one request and subsequent opens are
instant:

```typescript
// Opener (parent) — fetch, then show the dialog
this.gps.getEntityFormDefinition(AppSettings.CASHACCOUNT).subscribe(formDefinition => {
  this.callParam = new CallParam(portfolio, cashaccount, {...optParam, formDefinition});
  this.visibleDialog = true;
});

// Dialog ngOnInit — build config synchronously from the pre-fetched descriptor
this.config = <FieldConfig[]>DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
  this.translateService, this.callParam.optParam.formDefinition, '', true);
this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
```

`initialize()` (on dialog show) then keeps the original flow — runtime option injection, conditional
enable/disable, `setDefaultValuesAndEnableSubmit()`, `transferBusinessObjectToForm()`. See
`PortfolioCashaccountSummaryComponent.handleEditAccount` + `CashaccountEditComponent` for the
reference pattern.

### What propagates from the backend

`@NotNull`/`@NotBlank` → required, `@Size` → length, `@Min`/`@Max` and `@DecimalMin`/`@DecimalMax`
→ range, `@Digits` → integer/fraction precision, `@Pattern` → regex validator, `@AfterEqual` →
calendar `minDate`, `@Future` → future-date. `SELECT_OPTIONS` yields a select (string **or** numeric)
with empty options for the component to fill.
