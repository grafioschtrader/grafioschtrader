import {CrudMenuOptions, TableCrudSupportMenu} from '../../shared/datashowbase/table.crud.support.menu';
import {Component, OnDestroy} from '@angular/core';
import {UserAdminService} from '../service/user.admin.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {DataType} from '../../dynamic-form/models/data.type';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {HelpIds} from '../../shared/help/help.ids';
import {User} from '../../entities/user';
import {UserEntityChangeLimitTableComponent} from './user-entity-change-limit-table.component';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {ColumnConfig, TranslateValue} from '../../shared/datashowbase/column.config';
import {UserTaskType} from '../../shared/types/user.task.type';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {AppSettings} from '../../shared/app.settings';
import {LimitEntityTransactionError} from '../../shared/login/service/limit.entity.transaction.error';
import {DynamicDialogHelper} from '../../shared/dynamicdialog/component/dynamic.dialog.helper';
import {ProposeUserTaskService} from '../../shared/dynamicdialog/service/propose.user.task.service';
import {InfoLevelType} from '../../shared/message/info.leve.type';

/**
 * Main component for the user table. It contains nested table to change the limits on information classes.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-table [columns]="fields" [value]="entityList" selectionMode="single" [(selection)]="selectedEntity"
               sortField="nickname" [dataKey]="entityKeyName"
               responsiveLayout="scroll"
               styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
        <ng-template pTemplate="caption">
          <h4>{{entityNameUpper | translate}}</h4>
        </ng-template>
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th style="width:24px"></th>
            <th *ngFor="let field of fields" [pSortableColumn]="field.field" [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                [pTooltip]="field.headerTooltipTranslated">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-el let-expanded="expanded" let-columns="fields">
          <tr [pSelectableRow]="el">
            <td>
              <a *ngIf="el.userEntityChangeLimitList.length + el.userChangeLimitProposeList.length > 0" href="#"
                 [pRowToggler]="el">
                <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
              </a>
            </td>

            <td *ngFor="let field of fields" [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                [ngClass]="(field.dataType===DataType.NumericShowZero || field.dataType === DataType.DateTimeNumeric
              || field.dataType===DataType.NumericInteger)? 'text-right': ''">
              <ng-container [ngSwitch]="field.templateName">
                <ng-container *ngSwitchCase="'check'">
                  <span><i [ngClass]="{'fa fa-check': getValueByPath(el, field)}" aria-hidden="true"></i></span>
                </ng-container>
                <ng-container *ngSwitchCase="'icon'">
                  <svg-icon [name]="getValueByPath(el, field)"
                            [svgStyle]="{ 'width.px':14, 'height.px':14 }"></svg-icon>
                </ng-container>
                <ng-container *ngSwitchDefault>
                  {{getValueByPath(el, field)}}
                </ng-container>
              </ng-container>
          </tr>
        </ng-template>
        <ng-template pTemplate="rowexpansion" let-user let-columns="fields">
          <tr *ngIf="user.userEntityChangeLimitList.length + user.userChangeLimitProposeList.length > 0">
            <td [attr.colspan]="numberOfVisibleColumns + 1">
              <user-entity-change-limit-table [user]="user"
                                              (dateChanged)="handleChangesOnLimitTable($event)">
              </user-entity-change-limit-table>
            </td>
          </tr>
        </ng-template>
      </p-table>
      <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"
                     appendTo="body"></p-contextMenu>
    </div>
    <user-edit *ngIf="visibleDialog" [visibleDialog]="visibleDialog"
               [callParam]="callParam"
               (closeDialog)="handleCloseDialog($event)">
    </user-edit>
    <user-entity-change-limit-edit *ngIf="visibleEditLimitDialog"
                                   [visibleDialog]="visibleEditLimitDialog"
                                   [user]="selectedEntity"
                                   (closeDialog)="handleEditLimitCloseDialog($event)">
    </user-entity-change-limit-edit>

    <user-change-owner-entities *ngIf="visibleChangeEntitiesOwnerDialog"
                                [visibleDialog]="visibleChangeEntitiesOwnerDialog"
                                [fromUser]="selectedEntity"
                                [allUsers]="entityList"
                                (closeDialog)="handleChangeOwnerCloseDialog($event)">
    </user-change-owner-entities>
  `,
  providers: [DialogService]
})
export class UserTableComponent extends TableCrudSupportMenu<User> implements OnDestroy {

  private static SVG = '.svg';
  private static createTypeIconMap: { [key: number]: string } = {
    [UserTaskType.RELEASE_LOGOUT]: 'user_update',
    [UserTaskType.LIMIT_CUD_CHANGE]: 'user_limit_update'
  };
  private static iconLoadDone = false;

  callParam: User;
  visibleEditLimitDialog: boolean;
  visibleChangeEntitiesOwnerDialog: boolean;

  private limitChangeMenuItem: MenuItem = {
    label: 'CREATE|' + UserEntityChangeLimitTableComponent.USER_ENTITY_CHANGE_LIMIT + AppSettings.DIALOG_MENU_SUFFIX,
    command: (event) => this.addUserEntityChangeLimit()
  };

  private changeEntitiesOwnerMenuItem: MenuItem = {
    label: 'USER_CHANGE_OWNER_ENTITIES' + AppSettings.DIALOG_MENU_SUFFIX, command: (event) => this.changeOwnerEntities()
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
    super(AppSettings.USER, userAdminService, confirmationService, messageToastService, activePanelService, dialogService,
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
        iconReg.loadSvg(AppSettings.PATH_ASSET_ICONS + iconName + UserTableComponent.SVG, iconName);
      }
      UserTableComponent.iconLoadDone = false;
    }
  }

  getReleaseLogoutProposeIcon(user: User, field: ColumnConfig): string {
    if (user.userChangePropose) {
      return UserTableComponent.createTypeIconMap[UserTaskType.RELEASE_LOGOUT];
    }
  }

  getLimitProposeIcon(user: User, field: ColumnConfig): string {
    if (user.userChangeLimitProposeList.length > 0) {
      return UserTableComponent.createTypeIconMap[UserTaskType.LIMIT_CUD_CHANGE];
    }
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_USER;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  onComponentClick(event): void {
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

  handleCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.action === ProcessedAction.REJECT_DATA_CHANGE) {
        // Reject the proposed user changes, which were created by an user violation
        this.proposeUserTaskService.rejectUserTask(this.selectedEntity.userChangePropose.idProposeRequest,
          processedActionData.data).subscribe(stringResponse => this.messageToastService.showMessage(InfoLevelType.SUCCESS,
          stringResponse.response));
      } else {
        this.readData();
      }

    } else if (processedActionData.transformedError && processedActionData.transformedError.errorClass
      && processedActionData.transformedError.errorClass instanceof LimitEntityTransactionError) {
      const dynamicDialogHelper = DynamicDialogHelper.getOpenedLimitTransactionRequestDynamicComponent(
        this.translateService, this.dialogService, this.entityName);
    }
  }

  resetMenu(user: User): void {
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
