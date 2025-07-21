import {TableConfigBase} from './table.config.base';
import {Directive, OnInit} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {AppHelper} from '../helper/app.helper';
import {InfoLevelType} from '../message/info.leve.type';
import {MessageToastService} from '../message/message.toast.service';
import {BaseID} from '../entities/base.id';
import {ProcessedAction} from '../types/processed.action';
import {ProcessedActionData} from '../types/processed.action.data';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {DeleteService} from './delete.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {HelpIds} from '../../shared/help/help.ids';
import {AuditHelper} from '../helper/audit.helper';
import {TranslateHelper} from '../helper/translate.helper';
import {LimitEntityTransactionError} from '../../shared/login/service/limit.entity.transaction.error';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {DynamicDialogs} from '../../shared/dynamicdialog/component/dynamic.dialogs';
import saveAs from '../filesaver/filesaver';
import {BaseSettings} from '../base.settings';

export enum CrudMenuOptions {
  Allow_Create,
  Allow_Edit,
  Allow_Delete,
  /**
   *  Avoid to add this menu directly
   */
  ParentControl
}

/**
 * Abstract base class providing comprehensive CRUD (Create, Read, Update, Delete) operations
 * for table-based data management with integrated menu support, dialog handling, and export functionality.
 *
 * This class extends TableConfigBase to add entity management capabilities including:
 * - Context menus with create/edit/delete operations
 * - Modal dialog integration for entity editing
 * - Permission-based operation control
 * - CSV export functionality
 * - Global menu integration through ActivePanelService
 *
 * The class expects entities to implement BaseID interface and provides hooks for
 * customizing behavior through abstract and protected methods.
 */
@Directive()
export abstract class TableCrudSupportMenu<T extends BaseID> extends TableConfigBase implements OnInit, IGlobalMenuAttach {

  /**
   * Predefined configuration allowing all CRUD operations.
   * Convenience constant for common use cases where all operations are permitted.
   */
  public static readonly ALLOW_ALL_CRUD_OPERATIONS = [CrudMenuOptions.Allow_Create, CrudMenuOptions.Allow_Edit,
    CrudMenuOptions.Allow_Delete];

  /** Array containing all entities currently displayed in the table */
  entityList: T[] = [];

  /** Context menu items for the currently selected entity */
  contextMenuItems: MenuItem[];

  /** Currently selected entity in the table */
  selectedEntity: T;

  /** Controls visibility of the edit/create dialog */
  visibleDialog = false;

  /** Uppercase entity name used for translation keys and messages */
  entityNameUpper: string;

  /** Primary key field name for the entity type */
  entityKeyName: string;

  /**
   * Creates a new table with CRUD support and menu integration.
   *
   * @param entityName - Name of the entity type for translation and identification
   * @param deleteService - Service providing delete operations for entities
   * @param confirmationService - PrimeNG service for confirmation dialogs
   * @param messageToastService - Service for displaying toast messages
   * @param activePanelService - Service for global menu integration
   * @param dialogService - PrimeNG service for dynamic dialog management
   * @param filterService - PrimeNG service for table filtering
   * @param translateService - Angular translation service
   * @param gps - Global parameter service for locale and permissions
   * @param usersettingsService - Service for persisting user preferences
   * @param crudMenuOptions - Array of allowed CRUD operations (defaults to all operations)
   */
  protected constructor(protected entityName: string,
    protected deleteService: DeleteService,
    protected confirmationService: ConfirmationService,
    protected messageToastService: MessageToastService,
    protected activePanelService: ActivePanelService,
    protected dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    private crudMenuOptions: CrudMenuOptions[] = TableCrudSupportMenu.ALLOW_ALL_CRUD_OPERATIONS) {

    super(filterService, usersettingsService, translateService, gps);
    this.entityNameUpper = AppHelper.toUpperCaseWithUnderscore(this.entityName);
    this.entityKeyName = this.gps.getKeyNameByEntityName(entityName);
  }

  /**
   * Angular lifecycle hook - initializes the component and loads data.
   */
  ngOnInit(): void {
    this.initialize();
  }

  /**
   * Handles entity editing by opening the edit dialog.
   * Creates a deep copy of the entity to prevent accidental modifications to the original.
   *
   * @param entity - Entity to edit, or null for creating a new entity
   */
  handleEditEntity(entity: T): void {
    // need a copy of the entity
    this.prepareCallParam(JSON.parse(JSON.stringify(entity)));
    this.visibleDialog = true;
  }

  /**
   * Handles entity deletion with user confirmation.
   * Shows confirmation dialog and performs delete operation if confirmed.
   * Updates UI and reloads data after successful deletion.
   *
   * @param entity - Entity to delete
   */
  handleDeleteEntity(entity: T) {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + this.entityNameUpper, () => {
        entity = this.beforeDelete(entity);
        this.deleteService.deleteEntity(entity[this.entityKeyName]).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: this.entityNameUpper});
          this.resetMenu(null);
          this.readData();
        });
      });
  }

  /**
   * Handles dialog close events and processes the result.
   * Refreshes data if changes were made, or handles specific error cases.
   *
   * @param processedActionData - Result data from dialog operations
   */
  handleCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    } else if (processedActionData.transformedError && processedActionData.transformedError.errorClass
      && processedActionData.transformedError.errorClass instanceof LimitEntityTransactionError) {
      DynamicDialogs.getOpenedLimitTransactionRequestDynamicComponent(
        this.translateService, this.dialogService, this.entityName);
    }
  }

  /**
   * Checks if this component is currently the active panel.
   * Used for global menu integration and focus management.
   *
   * @returns True if this component is currently active
   */
  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  /**
   * Handles component click events for menu management.
   * Updates context menu based on current selection.
   *
   * @param event - DOM click event
   */
  onComponentClick(event): void {
    this.resetMenu(this.selectedEntity);
  }

  /**
   * Called when component is deactivated. Override to perform cleanup operations.
   */
  callMeDeactivate(): void {
  }

  /**
   * Hides context menu. Override to perform custom menu hiding logic.
   */
  hideContextMenu(): void {
  }

  /**
   * Returns help context ID for this component.
   * Override to provide context-sensitive help.
   *
   * @returns Help context identifier or null if no help available
   */
  public getHelpContextId(): HelpIds {
    return null;
  }

  /**
   * Checks if the entity list is empty.
   *
   * @returns True if no entities are currently loaded
   */
  public isEmpty(): boolean {
    return this.entityList.length === 0;
  }

  /**
   * Exports table data to CSV file format. Only exports columns marked with export flag and handles null values
   * appropriately. Uses platform-specific line separators for compatibility.
   *
   * @param data - Array of data objects to export
   */
  downloadCSvFile(data: any[]) {
    const lineSeparator = (navigator.appVersion.indexOf('Win') !== -1) ? '\r\n' : '\n';
    const replacer = (key, value) => value === null ? undefined : value; // specify how you want to handle null values here
    const header: string[] = [];
    Object.keys(data[0]).filter(name => {
      const columnConfig = this.getColumnConfigByField(name);
      if (columnConfig && columnConfig.export) {
        header.push(name);
      }
    });
    const csv = data.map(row => header.map(fieldName => JSON.stringify(row[fieldName], replacer)).join(';'));
    csv.unshift(header.join(';'));
    const csvArray = csv.join(lineSeparator);
    const blob = new Blob([csvArray], {type: 'text/csv'});
    saveAs(blob, this.entityName.toLocaleLowerCase() + '.csv');
  }

  /**
   * Initializes the component by loading data. Called during ngOnInit lifecycle.
   */
  protected initialize(): void {
    this.readData();
  }

  /**
   * Prepares parameters for the edit dialog.
   * Must be implemented by subclasses to configure dialog parameters.
   *
   * @param entity - Entity to edit or null for new entity creation
   */
  protected abstract prepareCallParam(entity: T);

  /**
   * Loads entity data from the backend.
   * Must be implemented by subclasses to fetch and populate entityList.
   */
  protected abstract readData(): void;

  /**
   * Prepares items for the show menu (view-related operations).
   * Override to provide custom show menu items.
   *
   * @returns Array of menu items or null if no show menu needed
   */
  protected prepareShowMenu(): MenuItem[] {
    return null;
  }

  /**
   * Prepares the edit menu based on current entity selection and permissions.
   * Translates menu items and returns only if items are available.
   *
   * @param entity - Currently selected entity or null
   * @returns Array of translated menu items or null if no items available
   */
  protected prepareEditMenu(entity: T): MenuItem[] {
    const menuItems: MenuItem[] = this.getEditMenuItems(entity);
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems.length > 0 ? menuItems : null;
  }

  /**
   * Builds the standard CRUD menu items based on configuration and permissions.
   * Creates menu items for create, edit, and delete operations as configured.
   *
   * @param entity - Currently selected entity or null
   * @returns Array of menu items for CRUD operations
   */
  protected getEditMenuItems(entity: T): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (this.crudMenuOptions.indexOf(CrudMenuOptions.Allow_Create) >= 0) {
      menuItems.push({
        label: 'CREATE|' + this.entityNameUpper + BaseSettings.DIALOG_MENU_SUFFIX,
        command: (event) => this.handleEditEntity(null),
        disabled: !this.hasRightsForCreateEntity(entity)
      });
    }
    if (entity) {
      if (this.crudMenuOptions.indexOf(CrudMenuOptions.Allow_Edit) >= 0) {
        menuItems.push({
          label: 'EDIT_RECORD|' + this.entityNameUpper + BaseSettings.DIALOG_MENU_SUFFIX,
          command: (event) => this.handleEditEntity(entity),
          disabled: !this.hasRightsForUpdateEntity(entity)
        });
      }
      if (this.crudMenuOptions.indexOf(CrudMenuOptions.Allow_Delete) >= 0) {
        menuItems.push({
          label: 'DELETE_RECORD|' + this.entityNameUpper,
          command: (event) => this.handleDeleteEntity(entity),
          disabled: !this.hasRightsForDeleteEntity(entity)
        });
      }
      this.addCustomMenusToSelectedEntity(entity, menuItems);
    }
    return menuItems;
  }

  /**
   * Adds custom menu items specific to the selected entity.
   * Override to add entity-specific menu options beyond standard CRUD operations.
   *
   * @param entity - Currently selected entity
   * @param menuItems - Array to add custom menu items to
   */
  protected addCustomMenusToSelectedEntity(entity: T, menuItems: MenuItem[]): void {
  }

  /**
   * Checks if user has permission to create new entities.
   * Override to implement custom creation permission logic.
   *
   * @param entity - Context entity (may be null)
   * @returns True if creation is allowed
   */
  protected hasRightsForCreateEntity(entity: T): boolean {
    return true;
  }

  /**
   * Checks if user has permission to update the specified entity.
   * Override to implement custom update permission logic.
   *
   * @param entity - Entity to check update permissions for
   * @returns True if update is allowed
   */
  protected hasRightsForUpdateEntity(entity: T): boolean {
    return true;
  }

  /**
   * Checks if user has permission to delete the specified entity.
   * Uses AuditHelper for standard audit-based permission checking.
   *
   * @param entity - Entity to check delete permissions for
   * @returns True if deletion is allowed
   */
  protected hasRightsForDeleteEntity(entity: T): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteEntity(this.gps, entity);
  }

  /**
   * Extracts the primary key value from an entity.
   *
   * @param entity - Entity to get ID from
   * @returns Primary key value
   */
  protected getId(entity: T): number {
    return entity[this.entityKeyName];
  }

  /**
   * Pre-processes entity before deletion.
   * Override to perform any necessary modifications before delete operation.
   *
   * @param entity - Entity to be deleted
   * @returns Modified entity ready for deletion
   */
  protected beforeDelete(entity: T): T {
    return entity;
  }

  /**
   * Refreshes the currently selected entity from the entity list.
   * Updates selection to reflect any changes made to the entity.
   */
  protected refreshSelectedEntity(): void {
    if (this.selectedEntity) {
      this.resetMenu(this.entityList.find(entity => entity[this.entityKeyName] === this.selectedEntity[this.entityKeyName]));
    }
  }

  /**
   * Updates the selected entity and rebuilds context menus.
   * Integrates with ActivePanelService for global menu management.
   *
   * @param entity - New selected entity or null to clear selection
   */
  protected resetMenu(entity: T): void {
    this.selectedEntity = entity;
    if (this.crudMenuOptions.indexOf(CrudMenuOptions.ParentControl) < 0) {
      this.contextMenuItems = this.prepareEditMenu(this.selectedEntity);
      this.activePanelService.activatePanel(this, {
        editMenu: this.contextMenuItems,
        showMenu: this.prepareShowMenu()
      });
    }
  }
}




