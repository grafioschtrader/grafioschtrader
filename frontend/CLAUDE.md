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

## PrimeNG Table and Tree Components

### Mandatory Base Class Inheritance

Components that use PrimeNG tables (`p-table`) or trees (`p-tree`, `p-treeTable`) **MUST** extend one of the following base classes:

| Base Class | Use Case |
|------------|----------|
| `ShowRecordConfigBase` | Single record display or simple dialog tables without sorting/filtering needs |
| `TableConfigBase` | **Standalone table view components** - tables with filtering, sorting, column visibility, user settings persistence |
| `TableCrudSupportMenu` | Full CRUD tables with context menus, dialogs, and entity management |
| `TableTreetableTotalBase` | Tables or tree-tables with total/subtotal calculations |

**IMPORTANT**: Standalone table view components (e.g., `*TableComponent`) should extend at least `TableConfigBase`, not `ShowRecordConfigBase`. Use `ShowRecordConfigBase` only for single record displays or simple embedded tables in dialogs where filtering/sorting is not needed.

### Why Base Classes Are Required

1. **Translation Support**: Base classes provide `translateHeadersAndColumns()` for proper i18n of column headers
2. **Column Configuration**: Standardized `addColumn()`, `addColumnFeqH()` methods ensure consistent column setup
3. **Value Formatting**: `getValueByPath()` handles data type formatting, translations, and custom value functions
4. **User Settings**: Column visibility and table configurations can be persisted to LocalStorage
5. **Consistency**: All tables in the application follow the same patterns

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

In components using `OnChanges` with `@Input()` data, note that `ngOnChanges` is called **before** `ngOnInit`. Use a flag to ensure proper ordering:

```typescript
private fieldsInitialized = false;

ngOnChanges(changes: SimpleChanges): void {
  if (changes['data'] && this.data) {
    // Only create translated value store if fields have been initialized
    if (this.fieldsInitialized) {
      this.createTranslatedValueStore(this.data);
    }
  }
}

ngOnInit(): void {
  this.addColumn(...);
  this.prepareTableAndTranslate();
  this.fieldsInitialized = true;

  // If data was already provided before ngOnInit, create translated value store now
  if (this.data?.length > 0) {
    this.createTranslatedValueStore(this.data);
  }
}
```

## PrimeNG Button Patterns (PrimeNG 17+)

**IMPORTANT**: In PrimeNG 17+, the `pButton` directive **deprecated** the `label` and `icon` attributes. Use these patterns instead:

### Preferred: `<p-button>` Component

```html
<!-- Basic button with label -->
<p-button [label]="'SAVE' | translate" (click)="save()" />

<!-- Button with icon -->
<p-button [label]="'SAVE' | translate" icon="pi pi-check" (click)="save()" />

<!-- Icon-only button -->
<p-button icon="pi pi-check" (click)="save()" />

<!-- Button with severity/style -->
<p-button [label]="'DELETE' | translate" icon="pi pi-trash" severity="danger" (click)="delete()" />
```

### Alternative: `pButton` Directive with Content Projection

When you need a native `<button>` element (e.g., for form submission), use content projection:

```html
<!-- Text only -->
<button pButton type="button" (click)="save()">
  {{ 'SAVE' | translate }}
</button>

<!-- With icon - use nested elements -->
<button pButton type="submit" class="btn">
  <i class="pi pi-check"></i>
  {{ 'SAVE' | translate }}
</button>
```

### DEPRECATED - Do NOT Use

```html
<!-- WRONG: label and icon attributes on pButton are deprecated -->
<button pButton [label]="'SAVE' | translate" icon="pi pi-check" (click)="save()"></button>
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
    <p-dialog [(visible)]="visibleDialog" (onShow)="onShow($event)" (onHide)="onHide($event)">
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
  data: { callParam: { thisObject, parentObject } }
});
dialogRef.onClose.subscribe((result: ProcessedActionData) => { ... });
```

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

## DynamicFieldHelper Methods

### Always Use HeqF Methods by Default

`DynamicFieldHelper` provides two variants for most field creation methods:
- **Standard**: `createFieldInputString(fieldName, headerKey, ...)` - explicit header key
- **HeqF**: `createFieldInputStringHeqF(fieldName, ...)` - header key derived from field name

**HeqF** stands for "Header equals Field" - the header/translation key is automatically derived from the field name by converting to `UPPER_SNAKE_CASE`.

**Rule**: **Always use `*HeqF` methods by default.** If the required NLS key doesn't exist, create it in the translation files. Only use standard methods with explicit header keys when there is a **conflict with an existing NLS key that has a different meaning**.

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
3. **If key doesn't exist**: Create the NLS key in both `en.json` and `de.json`, then use HeqF method
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

**Rule**: Only use `usedLayoutColumns` in **non-modal views** where multiple fields should appear side-by-side. **Never use it in modal dialogs** - dialogs should use full-width fields (omit the option).

| Context | usedLayoutColumns | Example |
|---------|-------------------|---------|
| Modal dialog (`<p-dialog>`) | **Omit** (full width) | `HistoryquoteDeleteDialogComponent` |
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
- [ ] Add NLS keys to both `en.json` and `de.json` (alphabetically sorted)
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
    <p-dialog header="{{'DIALOG_TITLE' | translate}}" [(visible)]="visibleDialog"
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

## Translation / i18n File Placement

**IMPORTANT**: Translation files must be placed according to where the component resides.

| Component Location | Translation Files |
|-------------------|-------------------|
| `src/app/lib/` (shared library) | `src/app/lib/assets/i18n/en.json` and `de.json` |
| `src/app/<module>/` (app-specific) | `src/assets/i18n/en.json` and `de.json` |

### Rules

1. **Match component location**: If a component is in `src/app/lib/`, add translations to `src/app/lib/assets/i18n/*.json`
2. **Match component location**: If a component is in `src/app/<module>/` (e.g., `src/app/gtnet/`, `src/app/portfolio/`), add translations to `src/assets/i18n/*.json`
3. **Always update both languages**: Update both `en.json` (English) and `de.json` (German)
4. **Alphabetical order**: Keys must be sorted alphabetically in the JSON files

### Example

Adding a translation for a component in `src/app/lib/globalsettings/`:
```json
// In src/app/lib/assets/i18n/en.json
"INPUT_RULE": "Validation rule"

// In src/app/lib/assets/i18n/de.json
"INPUT_RULE": "Validierungsregel"
```

## Translation Keys

- Keys are sorted alphabetically in the JSON files
- Use UPPER_SNAKE_CASE for keys
- Add tooltip translations with `_TOOLTIP` suffix (e.g., `FIELD_NAME_TOOLTIP`)
- Both `en.json` and `de.json` must be updated together

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

### Reference Implementations

- `MailForwardSettingTableEditComponent` - Clean example of IGlobalMenuAttach pattern
- `GTNetSecurityImportTableComponent` - Example with CSV upload functionality
