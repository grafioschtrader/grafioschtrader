import {CrudMenuOptions, TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {Component, OnDestroy} from '@angular/core';
import {UserAdminService} from '../service/user.admin.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {HelpIds} from '../../shared/help/help.ids';
import {User} from '../../lib/entities/user';
import {UserEntityChangeLimitTableComponent} from './user-entity-change-limit-table.component';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {UserTaskType} from '../../lib/types/user.task.type';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {AppSettings} from '../../shared/app.settings';
import {LimitEntityTransactionError} from '../../lib/login/service/limit.entity.transaction.error';
import {ProposeUserTaskService} from '../../shared/dynamicdialog/service/propose.user.task.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {DynamicDialogs} from '../../shared/dynamicdialog/component/dynamic.dialogs';
import {BaseSettings} from '../../lib/base.settings';

/**
 * Main component for the user table. It contains nested table to change the limits on information classes.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-table [columns]="fields" [value]="entityList" selectionMode="single" [(selection)]="selectedEntity"
               sortField="nickname" [dataKey]="entityKeyName" [contextMenu]="cmDiv"
               stripedRows showGridlines>
        <ng-template #caption>
          <h4>{{ entityNameUpper | translate }}</h4>
        </ng-template>
        <ng-template #header let-fields>
          <tr>
            <th style="width:24px"></th>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field" [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                  [pTooltip]="field.headerTooltipTranslated">
                {{ field.headerTranslated }}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            }
          </tr>
        </ng-template>
        <ng-template #body let-el let-expanded="expanded" let-columns="fields">
          <tr [pContextMenuRow] [pSelectableRow]="el">
            <td>
              @if (el.userEntityChangeLimitList.length + el.userChangeLimitProposeList.length > 0) {
                <a href="#" [pRowToggler]="el">
                  <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
                </a>
              }
            </td>

            @for (field of fields; track field) {
              <td [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                  [ngClass]="(field.dataType===DataType.NumericShowZero || field.dataType === DataType.DateTimeNumeric
              || field.dataType===DataType.NumericInteger)? 'text-right': ''">
                @switch (field.templateName) {
                  @case ('check') {
                    <span><i [ngClass]="{'fa fa-check': getValueByPath(el, field)}" aria-hidden="true"></i></span>
                  }
                  @case ('icon') {
                    <svg-icon [name]="getValueByPath(el, field)"
                              [svgStyle]="{ 'width.px':14, 'height.px':14 }"></svg-icon>
                  }
                  @default {
                    {{ getValueByPath(el, field) }}
                  }
                }
              </td>
            }
          </tr>
        </ng-template>
        <ng-template #expandedrow let-user let-columns="fields">
          @if (user.userEntityChangeLimitList.length + user.userChangeLimitProposeList.length > 0) {
            <tr>
              <td [attr.colspan]="numberOfVisibleColumns + 1">
                <user-entity-change-limit-table [user]="user"
                                                (dateChanged)="handleChangesOnLimitTable($event)">
                </user-entity-change-limit-table>
              </td>
            </tr>
          }
        </ng-template>
      </p-table>
      <p-contextMenu #cmDiv appendTo="body" [model]="contextMenuItems"></p-contextMenu>
    </div>
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
  standalone: false
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
        iconReg.loadSvg(AppSettings.PATH_ASSET_ICONS + iconName + AppSettings.SVG, iconName);
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

  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_USER;
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
