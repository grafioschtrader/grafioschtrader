import {TableConfigBase} from './table.config.base';
import {Directive, OnInit} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../service/user.settings.service';
import {AppHelper} from '../helper/app.helper';
import {InfoLevelType} from '../message/info.leve.type';
import {MessageToastService} from '../message/message.toast.service';
import {BaseID} from '../../entities/base.id';
import {ProcessedAction} from '../types/processed.action';
import {ProcessedActionData} from '../types/processed.action.data';
import {ActivePanelService} from '../mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../mainmenubar/component/iglobal.menu.attach';
import {DeleteService} from './delete.service';
import {GlobalparameterService} from '../service/globalparameter.service';
import {HelpIds} from '../help/help.ids';
import {AuditHelper} from '../helper/audit.helper';
import {TranslateHelper} from '../helper/translate.helper';
import {LimitEntityTransactionError} from '../login/service/limit.entity.transaction.error';
import {DynamicDialogHelper} from '../dynamicdialog/component/dynamic.dialog.helper';
import * as filesaver from '../../shared/filesaver/filesaver';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {AppSettings} from '../app.settings';

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
 * The menus are changed because of listening to the component click.
 * It expect a dialog for editing of the entity.
 */
@Directive()
export abstract class TableCrudSupportMenu<T extends BaseID> extends TableConfigBase implements OnInit, IGlobalMenuAttach {

  public static readonly ALLOW_ALL_CRUD_OPERATIONS = [CrudMenuOptions.Allow_Create, CrudMenuOptions.Allow_Edit,
    CrudMenuOptions.Allow_Delete];

  entityList: T[] = [];

  // For the component Edit-Menu, it shows the same menu items as the context menu
  contextMenuItems: MenuItem[];
  selectedEntity: T;
  visibleDialog = false;
  entityNameUpper: string;
  entityKeyName: string;

  constructor(protected entityName: string,
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
    this.entityNameUpper = this.entityName.toUpperCase();
    this.entityKeyName = this.gps.getKeyNameByEntityName(entityName);
  }

  ////////////////////////////////////////////////
  ngOnInit(): void {
    this.initialize();
  }

  handleEditEntity(entity: T): void {
    this.prepareCallParam(JSON.parse(JSON.stringify(entity)));
    this.visibleDialog = true;
  }

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

  handleCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    } else if (processedActionData.transformedError && processedActionData.transformedError.errorClass
      && processedActionData.transformedError.errorClass instanceof LimitEntityTransactionError) {
      const dynamicDialogHelper = DynamicDialogHelper.getOpenedLimitTransactionRequestDynamicComponent(
        this.translateService, this.dialogService, this.entityName);
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  /**
   * When row is clicked, this event catches it as well
   */
  onComponentClick(event): void {
    this.resetMenu(this.selectedEntity);
  }


  ////////////////////////////////////////////////
  // Event handler

  callMeDeactivate(): void {
  }

  hideContextMenu(): void {
  }

  public getHelpContextId(): HelpIds {
    return null;
  }

  public isEmpty(): boolean {
    return this.entityList.length === 0;
  }

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
    filesaver.saveAs(blob, this.entityName.toLocaleLowerCase() + '.csv');
  }

  protected initialize(): void {
    this.readData();
  }

  /**
   * Prepare parameter data object for editing component.
   */
  protected abstract prepareCallParam(entity: T);

  protected abstract readData(): void;

  protected prepareShowMenu(): MenuItem[] {
    return null;
  }

  protected prepareEditMenu(entity: T): MenuItem[] {
    const menuItems: MenuItem[] = this.getEditMenuItems(entity);
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems.length > 0 ? menuItems : null;
  }

  protected getEditMenuItems(entity: T): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (this.crudMenuOptions.indexOf(CrudMenuOptions.Allow_Create) >= 0) {
      menuItems.push({
        label: 'CREATE|' + this.entityNameUpper + AppSettings.DIALOG_MENU_SUFFIX,
        command: (event) => this.handleEditEntity(null),
        disabled: !this.hasRightsForCreateEntity(entity)
      });
    }
    if (entity) {
      if (this.crudMenuOptions.indexOf(CrudMenuOptions.Allow_Edit) >= 0) {
        menuItems.push({
          label: 'EDIT_RECORD|' + this.entityNameUpper + AppSettings.DIALOG_MENU_SUFFIX,
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

  protected addCustomMenusToSelectedEntity(entity: T, menuItems: MenuItem[]): void {
  }

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

  protected beforeDelete(entity: T): T {
    return entity;
  }

  protected refreshSelectedEntity(): void {
    if (this.selectedEntity) {
      this.resetMenu(this.entityList.find(entity => entity[this.entityKeyName] === this.selectedEntity[this.entityKeyName]));
    }
  }

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




