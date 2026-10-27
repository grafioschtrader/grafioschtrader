import { TableConfigBase } from './table.config.base';
import { DestroyRef, Directive, inject, Injector, OnInit, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService, FilterService, MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { Subscription } from 'rxjs';
import { UserSettingsService } from '../services/user.settings.service';
import { AppHelper } from '../helper/app.helper';
import { MessageToastService } from '../message/message.toast.service';
import { InfoLevelType } from '../message/info.leve.type';
import { BaseID } from '../entities/base.id';
import { ProcessedAction } from '../types/processed.action';
import { ProcessedActionData } from '../types/processed.action.data';
import { ActivePanelService } from '../mainmenubar/service/active.panel.service';
import { IGlobalMenuAttach } from '../mainmenubar/component/iglobal.menu.attach';
import { DeleteService } from './delete.service';
import { GlobalparameterService } from '../services/globalparameter.service';
import { AuditHelper } from '../helper/audit.helper';
import { TranslateHelper } from '../helper/translate.helper';
import { LimitEntityTransactionError } from '../login/service/limit.entity.transaction.error';
import { DynamicDialogs } from '../dynamicdialog/component/dynamic.dialogs';
import { BaseSettings } from '../base.settings';
import saveAs from '../filesaver/filesaver';

export enum CrudMenuOptions {
  Allow_Create,
  Allow_Edit,
  Allow_Delete,
  /**
   *  Avoid to add this menu directly
   */
  ParentControl
}

/** All CRUD operations, the default for crudMenuOptions. */
const ALLOW_ALL_CRUD_OPERATIONS: CrudMenuOptions[] = [
  CrudMenuOptions.Allow_Create,
  CrudMenuOptions.Allow_Edit,
  CrudMenuOptions.Allow_Delete
];

/**
 * CRUD table base with its template state held in signals; the base class of every CRUD table. Entity list,
 * selection, dialog visibility and context menu are signals and the menu is published translated and as a new array,
 * so a table runs as OnPush without further notification.
 *
 * A signal is changed with a new reference only, `entityList.update((list) => [...list, x])`, never by pushing into
 * the current array.
 */
@Directive()
export abstract class TableCrudSupportMenu<T extends BaseID>
  extends TableConfigBase
  implements OnInit, IGlobalMenuAttach
{
  /** All CRUD operations, the default for crudMenuOptions */
  public static readonly ALLOW_ALL_CRUD_OPERATIONS = ALLOW_ALL_CRUD_OPERATIONS;

  /** All entities currently displayed in the table */
  readonly entityList = signal<T[]>([]);

  /** Translated context menu of the selected entity, null when there is none */
  readonly contextMenuItems = signal<MenuItem[]>(null);

  /** Currently selected entity in the table */
  readonly selectedEntity = signal<T>(null);

  /** Controls visibility of the edit/create dialog */
  readonly visibleDialog = signal(false);

  /** Uppercase entity name used for translation keys and messages */
  entityNameUpper: string;

  /** Primary key field name for the entity type */
  entityKeyName: string;

  protected readonly destroyRef = inject(DestroyRef);

  /** Translation of the latest menu; a newer menu replaces one that is still being translated */
  private menuSubscription: Subscription;

  /**
   * Creates a new table with CRUD support and menu integration.
   *
   * @param entityName - Name of the entity type for translation and identification
   * @param deleteService - Service providing delete operations for entities
   * @param confirmationService - Optimus service for confirmation dialogs
   * @param messageToastService - Service for displaying toast messages
   * @param activePanelService - Service for global menu integration
   * @param dialogService - Optimus service for dynamic dialog management
   * @param filterService - Optimus service for table filtering
   * @param translateService - Angular translation service
   * @param gps - Global parameter service for locale and permissions
   * @param usersettingsService - Service for persisting user preferences
   * @param injector - Angular injector for lazy service resolution
   * @param crudMenuOptions - Array of allowed CRUD operations (defaults to all operations)
   */
  protected constructor(
    protected entityName: string,
    protected deleteService: DeleteService,
    protected confirmationService: ConfirmationService,
    protected messageToastService: MessageToastService,
    protected activePanelService: ActivePanelService,
    protected dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector,
    private crudMenuOptions: CrudMenuOptions[] = ALLOW_ALL_CRUD_OPERATIONS
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.entityNameUpper = AppHelper.toUpperCaseWithUnderscore(this.entityName);
    this.entityKeyName = this.gps.getKeyNameByEntityName(entityName);
    this.destroyRef.onDestroy(() => {
      this.menuSubscription?.unsubscribe();
      this.activePanelService.destroyPanel(this);
    });
  }

  /** Angular lifecycle hook - initializes the component and loads data. */
  ngOnInit(): void {
    this.initialize();
  }

  /**
   * Opens the edit dialog with a deep copy of the entity, so the dialog never changes the displayed row.
   *
   * @param entity - Entity to edit, or null for creating a new entity
   */
  handleEditEntity(entity: T): void {
    this.prepareCallParam(JSON.parse(JSON.stringify(entity)));
    this.visibleDialog.set(true);
  }

  /**
   * Deletes the entity after the user confirmed, then clears the selection and reloads the data. A failed deletion
   * also clears the selection: the toast is written by BaseAuthService.handleError, and the menu must not keep
   * offering the failed action.
   *
   * @param entity - Entity to delete
   */
  handleDeleteEntity(entity: T): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + this.entityNameUpper,
      () =>
        this.deleteService.deleteEntity(this.beforeDelete(entity)[this.entityKeyName]).subscribe({
          next: () => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
              i18nRecord: this.entityNameUpper
            });
            this.resetMenu(null);
            this.readData();
          },
          error: () => this.resetMenu(null)
        })
    );
  }

  /**
   * Closes the edit dialog and reloads the data when something changed; a limit violation opens the limit request.
   *
   * @param processedActionData - Result data from dialog operations
   */
  handleCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    } else {
      this.checkLimitError(processedActionData);
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    this.resetMenu(this.selectedEntity());
  }

  /** Called when another panel becomes active. Override to perform cleanup operations. */
  callMeDeactivate(): void {}

  /** Hides context menu. Override to perform custom menu hiding logic. */
  hideContextMenu(): void {}

  /**
   * Returns help context ID for this component. Override to provide context-sensitive help.
   *
   * @returns Help context identifier or null if no help available
   */
  public getHelpContextId(): string {
    return null;
  }

  /** @returns True if no entities are currently loaded */
  public isEmpty(): boolean {
    return this.entityList().length === 0;
  }

  /**
   * Exports table data to a CSV file. Only columns marked with the export flag are written, null values are left
   * empty. Uses the platform-specific line separator.
   *
   * @param data - Array of data objects to export
   */
  downloadCSvFile(data: any[]): void {
    const lineSeparator = navigator.appVersion.indexOf('Win') !== -1 ? '\r\n' : '\n';
    const replacer = (key, value) => (value === null ? undefined : value);
    const header: string[] = Object.keys(data[0]).filter((name) => this.getColumnConfigByField(name)?.export);
    const csv = data.map((row) => header.map((fieldName) => JSON.stringify(row[fieldName], replacer)).join(';'));
    csv.unshift(header.join(';'));
    const blob = new Blob([csv.join(lineSeparator)], { type: 'text/csv' });
    saveAs(blob, this.entityName.toLocaleLowerCase() + '.csv');
  }

  /** Initializes the component by loading data. Called during ngOnInit. */
  protected initialize(): void {
    this.readData();
  }

  /**
   * Prepares parameters for the edit dialog.
   *
   * @param entity - Entity to edit or null for new entity creation
   */
  protected abstract prepareCallParam(entity: T);

  /** Loads entity data from the backend and sets entityList. */
  protected abstract readData(): void;

  /**
   * Opens the dialog for requesting a higher limit when the edit dialog closed because of a limit violation.
   *
   * @param processedActionData - Result of the closed edit dialog
   */
  protected checkLimitError(processedActionData: ProcessedActionData): void {
    if (processedActionData.transformedError?.errorClass instanceof LimitEntityTransactionError) {
      DynamicDialogs.getOpenedLimitTransactionRequestDynamicComponent(
        this.translateService,
        this.dialogService,
        this.entityName
      );
    }
  }

  /**
   * Prepares items for the show menu (view-related operations).
   *
   * @returns Array of untranslated menu items or null if no show menu is needed
   */
  protected prepareShowMenu(): MenuItem[] {
    return null;
  }

  /**
   * Builds the untranslated edit menu for the entity; {@link resetMenu} translates it.
   *
   * @param entity - Currently selected entity or null
   * @returns Array of menu items or null if no items are available
   */
  protected prepareEditMenu(entity: T): MenuItem[] {
    const menuItems = this.getEditMenuItems(entity);
    return menuItems.length > 0 ? menuItems : null;
  }

  /**
   * Builds the untranslated create, edit and delete entries based on configuration and permissions. A read-only user
   * (managed client account, or advisor in a READ-level tenant) cannot create, update or delete tenant data, so these
   * entries are not shown at all rather than shown disabled. The backend enforces this regardless.
   *
   * @param entity - Currently selected entity or null; edit and delete need one
   * @returns Array of menu items for CRUD operations, possibly empty
   */
  protected getEditMenuItems(entity: T): MenuItem[] {
    const menuItems: MenuItem[] = [];
    const readOnly = this.gps.isReadOnlyUser();
    const allows = (option: CrudMenuOptions) => !readOnly && this.crudMenuOptions.indexOf(option) >= 0;
    if (allows(CrudMenuOptions.Allow_Create)) {
      menuItems.push({
        label: 'CREATE|' + this.entityNameUpper + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.handleEditEntity(null),
        disabled: !this.hasRightsForCreateEntity(entity)
      });
    }
    if (entity) {
      if (allows(CrudMenuOptions.Allow_Edit)) {
        menuItems.push({
          label: 'EDIT_RECORD|' + this.entityNameUpper + BaseSettings.DIALOG_MENU_SUFFIX,
          command: () => this.handleEditEntity(entity),
          disabled: !this.hasRightsForUpdateEntity(entity)
        });
      }
      if (allows(CrudMenuOptions.Allow_Delete)) {
        menuItems.push({
          label: 'DELETE_RECORD|' + this.entityNameUpper,
          command: () => this.handleDeleteEntity(entity),
          disabled: !this.hasRightsForDeleteEntity(entity)
        });
      }
      this.addCustomMenusToSelectedEntity(entity, menuItems);
    }
    return menuItems;
  }

  /**
   * Adds menu items specific to the selected entity beyond the standard CRUD operations.
   *
   * @param entity - Currently selected entity
   * @param menuItems - Array to add custom menu items to
   */
  protected addCustomMenusToSelectedEntity(entity: T, menuItems: MenuItem[]): void {}

  protected hasRightsForCreateEntity(entity: T): boolean {
    return true;
  }

  protected hasRightsForUpdateEntity(entity: T): boolean {
    return true;
  }

  protected hasRightsForDeleteEntity(entity: T): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteEntity(this.gps, entity);
  }

  protected getId(entity: T): number {
    return entity[this.entityKeyName];
  }

  /**
   * Pre-processes the entity before deletion.
   *
   * @param entity - Entity to be deleted
   * @returns Entity ready for deletion
   */
  protected beforeDelete(entity: T): T {
    return entity;
  }

  /** Selects the reloaded version of the selected entity and rebuilds the menus. */
  protected refreshSelectedEntity(): void {
    const selected = this.selectedEntity();
    if (selected) {
      this.resetMenu(this.entityList().find((entity) => entity[this.entityKeyName] === selected[this.entityKeyName]));
    }
  }

  /**
   * Selects the entity and publishes its context menu and the top menus once they are translated.
   *
   * @param entity - New selected entity or null to clear selection
   */
  protected resetMenu(entity: T): void {
    this.selectedEntity.set(entity ?? null);
    if (this.crudMenuOptions.indexOf(CrudMenuOptions.ParentControl) < 0) {
      const editMenu = this.prepareEditMenu(entity);
      const showMenu = this.prepareShowMenu();
      this.menuSubscription?.unsubscribe();
      this.menuSubscription = TranslateHelper.translateMenuItems$(editMenu, this.translateService).subscribe(
        (translatedEditMenu) => {
          this.contextMenuItems.set(translatedEditMenu);
          this.activePanelService.activatePanel(this, { editMenu: translatedEditMenu, showMenu });
        }
      );
    }
  }
}
