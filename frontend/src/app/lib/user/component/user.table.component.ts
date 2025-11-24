import {CrudMenuOptions, TableCrudSupportMenu} from '../../datashowbase/table.crud.support.menu';
import {Component, OnDestroy} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ConfigurableTableComponent} from '../../datashowbase/configurable-table.component';
import {AngularSvgIconModule} from 'angular-svg-icon';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {UserAdminService} from '../service/user.admin.service';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {MessageToastService} from '../../message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {UserSettingsService} from '../../services/user.settings.service';
import {HelpIds} from '../../help/help.ids';
import {User} from '../../entities/user';
import {UserEntityChangeLimitTableComponent} from './user-entity-change-limit-table.component';
import {UserEditComponent} from './user-edit-component';
import {UserEntityChangeLimitEditComponent} from './user-entity-change-limit-edit.component';
import {UserChangeOwnerEntitiesComponent} from './user-change-owner-entities.component';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {AuditHelper} from '../../helper/audit.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {ColumnConfig, TranslateValue} from '../../datashowbase/column.config';
import {UserTaskType} from '../../types/user.task.type';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {LimitEntityTransactionError} from '../../login/service/limit.entity.transaction.error';
import {ProposeUserTaskService} from '../../dynamicdialog/service/propose.user.task.service';
import {InfoLevelType} from '../../message/info.leve.type';
import {DynamicDialogs} from '../../dynamicdialog/component/dynamic.dialogs';
import {BaseSettings} from '../../base.settings';

/**
 * Main component for the user table. It contains nested table to change the limits on information classes.
 */
@Component({
  template: `
    <configurable-table
      [data]="entityList"
      [fields]="fields"
      [dataKey]="entityKeyName"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSort.bind(this)"
      [scrollable]="false"
      [stripedRows]="true"
      [showGridlines]="true"
      [expandable]="true"
      [canExpandFn]="canExpandRow.bind(this)"
      [expandedRowTemplate]="expandedContent"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [showContextMenu]="!!contextMenuItems"
      [contextMenuItems]="contextMenuItems"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">

      <h4 caption>{{ entityNameUpper | translate }}</h4>

      <!-- Custom icon cell template for svg-icon rendering -->
      <ng-template #iconCell let-row let-field="field" let-value="value">
        <svg-icon [name]="value" [svgStyle]="{ 'width.px':14, 'height.px':14 }"></svg-icon>
      </ng-template>

    </configurable-table>

    <!-- Expanded row content template for user entity change limits -->
    <ng-template #expandedContent let-user>
      <user-entity-change-limit-table [user]="user"
                                      (dateChanged)="handleChangesOnLimitTable($event)">
      </user-entity-change-limit-table>
    </ng-template>
    @if (visibleDialog) {
      <user-edit [visibleDialog]="visibleDialog"
                 [callParam]="callParam"
                 (closeDialog)="handleCloseDialog($event)">
      </user-edit>
    }
    @if (visibleEditLimitDialog) {
      <user-entity-change-limit-edit [visibleDialog]="visibleEditLimitDialog"
                                     [user]="selectedEntity"
                                     (closeDialog)="handleEditLimitCloseDialog($event)">
      </user-entity-change-limit-edit>
    }

    @if (visibleChangeEntitiesOwnerDialog) {
      <user-change-owner-entities [visibleDialog]="visibleChangeEntitiesOwnerDialog"
                                  [fromUser]="selectedEntity"
                                  [allUsers]="entityList"
                                  (closeDialog)="handleChangeOwnerCloseDialog($event)">
      </user-change-owner-entities>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [CommonModule, ConfigurableTableComponent, AngularSvgIconModule, TranslateModule,
    UserEntityChangeLimitTableComponent, UserEditComponent, UserEntityChangeLimitEditComponent,
    UserChangeOwnerEntitiesComponent]
})
export class UserTableComponent extends TableCrudSupportMenu<User> implements OnDestroy {

  private static createTypeIconMap: { [key: number]: string } = {
    [UserTaskType.RELEASE_LOGOUT]: 'user_update',
    [UserTaskType.LIMIT_CUD_CHANGE]: 'user_limit_update'
  };
  private static iconLoadDone = false;

  callParam: User;
  visibleEditLimitDialog: boolean;
  visibleChangeEntitiesOwnerDialog: boolean;

  private limitChangeMenuItem: MenuItem = {
    label: 'CREATE|' + UserEntityChangeLimitTableComponent.USER_ENTITY_CHANGE_LIMIT + BaseSettings.DIALOG_MENU_SUFFIX,
    command: (event) => this.addUserEntityChangeLimit()
  };

  private changeEntitiesOwnerMenuItem: MenuItem = {
    label: 'USER_CHANGE_OWNER_ENTITIES' + BaseSettings.DIALOG_MENU_SUFFIX,
    command: (event) => this.changeOwnerEntities()
  };

  constructor(private iconReg: SvgIconRegistryService,
    private userAdminService: UserAdminService,
    private proposeUserTaskService: ProposeUserTaskService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(BaseSettings.USER, userAdminService, confirmationService, messageToastService, activePanelService, dialogService,
      filterService, translateService, gps, usersettingsService, [CrudMenuOptions.ParentControl, CrudMenuOptions.Allow_Edit]);
    UserTableComponent.registerIcons(this.iconReg);

    this.addColumn(DataType.NumericInteger, 'idUser', 'ID', true, false, {width: 60});
    this.addColumnFeqH(DataType.String, 'nickname', true, false);
    this.addColumnFeqH(DataType.String, 'email', true, false, {width: 150});
    this.addColumn(DataType.None, 'userChangePropose', 'U', true, true,
      {fieldValueFN: this.getReleaseLogoutProposeIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumn(DataType.None, 'userLimit', 'L', true, true,
      {fieldValueFN: this.getLimitProposeIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumnFeqH(DataType.String, 'mostPrivilegedRole', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.Boolean, 'enabled', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.String, 'localeStr', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'timezoneOffset', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'securityBreachCount', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'limitRequestExceedCount', true, false);
    TranslateHelper.translateMenuItems([this.limitChangeMenuItem, this.changeEntitiesOwnerMenuItem], translateService);
    this.prepareTableAndTranslate();
  }

  private static registerIcons(iconReg: SvgIconRegistryService): void {
    if (!UserTableComponent.iconLoadDone) {
      for (const [key, iconName] of Object.entries(UserTableComponent.createTypeIconMap)) {
        iconReg.loadSvg(BaseSettings.PATH_ASSET_ICONS + iconName + BaseSettings.SVG, iconName);
      }
      UserTableComponent.iconLoadDone = false;
    }
  }

  getReleaseLogoutProposeIcon(user: User, field: ColumnConfig): string {
    return user.userChangePropose ? UserTableComponent.createTypeIconMap[UserTaskType.RELEASE_LOGOUT] : null;
  }

  getLimitProposeIcon(user: User, field: ColumnConfig): string {
    return (user.userChangeLimitProposeList.length > 0) ? UserTableComponent.createTypeIconMap[UserTaskType.LIMIT_CUD_CHANGE] : null;
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_USER;
  }

  canExpandRow(user: User): boolean {
    return user.userEntityChangeLimitList.length + user.userChangeLimitProposeList.length > 0;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  override onComponentClick(event): void {
    if (!event[this.consumedGT]) {
      this.resetMenu(this.selectedEntity);
    }
  }

  addUserEntityChangeLimit(): void {
    this.visibleEditLimitDialog = true;
  }

  changeOwnerEntities(): void {
    this.visibleChangeEntitiesOwnerDialog = true;
  }

  handleEditLimitCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleEditLimitDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  handleChangeOwnerCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleChangeEntitiesOwnerDialog = false;
  }

  handleChangesOnLimitTable(event) {
    this.readData();
  }

  override handleCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.action === ProcessedAction.REJECT_DATA_CHANGE) {
        // Reject the proposed user changes, which were created by a user violation
        this.proposeUserTaskService.rejectUserTask(this.selectedEntity.userChangePropose.idProposeRequest,
          processedActionData.data).subscribe(stringResponse => this.messageToastService.showMessage(InfoLevelType.SUCCESS,
          stringResponse.response));
      } else {
        this.readData();
      }

    } else if (processedActionData.transformedError && processedActionData.transformedError.errorClass
      && processedActionData.transformedError.errorClass instanceof LimitEntityTransactionError) {
      DynamicDialogs.getOpenedLimitTransactionRequestDynamicComponent(
        this.translateService, this.dialogService, this.entityName);
    }
  }

  override resetMenu(user: User): void {
    this.selectedEntity = user;
    if (this.selectedEntity) {
      // Menus for the user -> only edit
      this.contextMenuItems = this.prepareEditMenu(this.selectedEntity);
      // Menu for change user limit -> only create
      this.contextMenuItems.push({separator: true});
      if (this.selectedEntity && AuditHelper.isLimitedEditUser(this.selectedEntity)) {
        this.contextMenuItems.push(this.limitChangeMenuItem);
      }
      this.contextMenuItems.push(this.changeEntitiesOwnerMenuItem);
    } else {
      this.contextMenuItems = null;
    }
    this.activePanelService.activatePanel(this, {editMenu: this.contextMenuItems});
  }

  protected readData(): void {
    this.userAdminService.getAllUsers().subscribe(users => {
      this.createTranslatedValueStoreAndFilterField(users);
      this.entityList = users;
      this.refreshSelectedEntity();
    });
  }

  protected prepareCallParam(entity: User) {
    this.callParam = entity;
  }

}
