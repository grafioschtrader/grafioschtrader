import type { PanelClickEvent } from '../mainmenubar/component/iglobal.menu.attach';
import { DestroyRef, Directive, inject, linkedSignal, OnInit, signal } from '@angular/core';
import { ConfirmationService, MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { Subscription } from 'rxjs';
import { BaseSettings } from '../base.settings';
import { DynamicDialogs } from '../dynamicdialog/component/dynamic.dialogs';
import { BaseID } from '../entities/base.id';
import saveAs from '../filesaver/filesaver';
import { AppHelper } from '../helper/app.helper';
import { AuditHelper } from '../helper/audit.helper';
import { TranslateHelper } from '../helper/translate.helper';
import { LimitEntityTransactionError } from '../login/service/limit.entity.transaction.error';
import { IGlobalMenuAttach } from '../mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../mainmenubar/service/active.panel.service';
import { InfoLevelType } from '../message/info.leve.type';
import { MessageToastService } from '../message/message.toast.service';
import { ProcessedAction } from '../types/processed.action';
import { ProcessedActionData } from '../types/processed.action.data';
import { DeleteService } from './delete.service';
import { TableConfigBase } from './table.config.base';

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
  protected confirmationService: ConfirmationService = inject(ConfirmationService);
  protected messageToastService: MessageToastService = inject(MessageToastService);
  protected activePanelService: ActivePanelService = inject(ActivePanelService);
  protected dialogService: DialogService = inject(DialogService);

  /** All CRUD operations, the default for crudMenuOptions */
  public static readonly ALLOW_ALL_CRUD_OPERATIONS = ALLOW_ALL_CRUD_OPERATIONS;

  /** All entities currently displayed in the table */
  readonly entityList = signal<T[]>([]);

  /** Translated context menu of the selected entity, null when there is none */
  readonly contextMenuItems = signal<MenuItem[] | null>(null);

  /** Currently selected entity in the table */
  readonly selectedEntity = linkedSignal<T[], T | null>({
    source: this.entityList,
    computation: (rows, previous) =>
      rows.find((row) => row[this.entityKeyName] === previous?.value?.[this.entityKeyName]) ?? null
  });

  /** Controls visibility of the edit/create dialog */
  readonly visibleDialog = signal(false);

  /** Uppercase entity name used for translation keys and messages */
  entityNameUpper: string;

  /** Primary key field name for the entity type */
  entityKeyName: keyof T;

  protected readonly destroyRef = inject(DestroyRef);

  /** Translation of the latest menu; a newer menu replaces one that is still being translated */
  private menuSubscription?: Subscription;

  /**
   * Creates a new table with CRUD support and menu integration.
   *
   * @param entityName - Name of the entity type for translation and identification
   * @param deleteService - Service providing delete operations for entities
   * @param crudMenuOptions - Array of allowed CRUD operations (defaults to all operations)
   */
  protected constructor(
    protected entityName: string,
    protected deleteService: DeleteService | null,
    private crudMenuOptions: CrudMenuOptions[] = ALLOW_ALL_CRUD_OPERATIONS
  ) {
    super();
    this.entityNameUpper = AppHelper.toUpperCaseWithUnderscore(this.entityName);
    this.entityKeyName = this.gps.getKeyNameByEntityName(entityName) as keyof T;
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
  handleEditEntity(entity: T | null): void {
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
    const deleteService = this.deleteService;
    if (!deleteService) return;
    const id = this.beforeDelete(entity)[this.entityKeyName];
    if (typeof id !== 'number' && typeof id !== 'string') return;
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + this.entityNameUpper,
      () =>
        deleteService.deleteEntity(id).subscribe({
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

  onComponentClick(event: PanelClickEvent): void {
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
  public getHelpContextId(): string | null {
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
    const replacer = (key: string, value: unknown) => (value === null ? undefined : value);
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
  protected abstract prepareCallParam(entity: T | null): unknown;

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
  protected prepareShowMenu(): MenuItem[] | null {
    return null;
  }

  /**
   * Builds the untranslated edit menu for the entity; {@link resetMenu} translates it.
   *
   * @param entity - Currently selected entity or null
   * @returns Array of menu items or null if no items are available
   */
  protected prepareEditMenu(entity: T | null): MenuItem[] | null {
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
  protected getEditMenuItems(entity: T | null): MenuItem[] {
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
          disabled: !this.deleteService || !this.hasRightsForDeleteEntity(entity)
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

  protected hasRightsForCreateEntity(entity: T | null): boolean {
    return true;
  }

  protected hasRightsForUpdateEntity(entity: T): boolean {
    return true;
  }

  protected hasRightsForDeleteEntity(entity: T): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteEntity(this.gps, entity);
  }

  protected getId(entity: T): number | string | null | undefined {
    const id: unknown = entity[this.entityKeyName];
    if (id === null) return null;
    if (id === undefined) return undefined;
    if (typeof id === 'number' || typeof id === 'string') return id;
    throw new Error(`Invalid entity id: ${String(this.entityKeyName)}`);
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

  /** Rebuilds an active panel's menus after linked selection has followed the reloaded rows. */
  protected refreshSelectedEntity(): void {
    if (this.isActivated()) this.resetMenu(this.selectedEntity());
  }

  /**
   * Selects the entity and publishes its context menu and the top menus once they are translated.
   *
   * @param entity - New selected entity or null to clear selection
   */
  protected resetMenu(entity: T | null): void {
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
