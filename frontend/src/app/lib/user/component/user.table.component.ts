import { TableCrudSupportMenu, CrudMenuOptions } from '../../datashowbase/table.crud.support.menu';
import { Component, signal, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { TranslateModule } from '@ngx-translate/core';
import { UserAdminService } from '../service/user.admin.service';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { MenuItem } from '@openng/optimus-ui/api';
import { DataType } from '../../dynamic-form/models/data.type';
import { HelpIds } from '../../help/help.ids';
import { User } from '../../entities/user';
import { EntityLimitUserTableComponent } from './entity-limit-user-table.component';
import { UserEditComponent } from './user-edit-component';
import { EntityLimitEditComponent } from '../../entitylimit/component/entity.limit.edit.component';
import { UserChangeOwnerEntitiesDialogComponent } from './user-change-owner-entities-dialog.component';
import { ProcessedActionData } from '../../types/processed.action.data';
import { ProcessedAction } from '../../types/processed.action';
import { AuditHelper } from '../../helper/audit.helper';
import { ColumnConfig, TranslateValue } from '../../datashowbase/column.config';
import { UserTaskType } from '../../types/user.task.type';
import { SvgIconRegistryService } from 'angular-svg-icon';
import { ProposeUserTaskService } from '../../dynamicdialog/service/propose.user.task.service';
import { InfoLevelType } from '../../message/info.leve.type';
import { BaseSettings } from '../../base.settings';

/**
 * Main component for the user table. It contains nested table to change the limits on information classes.
 */
@Component({
  template: `
    <configurable-table
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="entityKeyName"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSorter"
      [scrollable]="false"
      [stripedRows]="true"
      [showGridlines]="true"
      [expandable]="true"
      [canExpandFn]="canExpandRow"
      [expandedRowTemplate]="expandedContent"
      [containerClass]="{
        'data-container': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [showContextMenu]="!!contextMenuItems()"
      [contextMenuItems]="contextMenuItems()"
      [valueGetterFn]="valueGetter"
      (componentClick)="onComponentClick($event)">
      <h4 caption>{{ entityNameUpper | translate }}</h4>

      <!-- Custom icon cell template for svg-icon rendering -->
      <ng-template #iconCell let-row let-field="field" let-value="value">
        <svg-icon [name]="value" [svgStyle]="{ 'width.px': 14, 'height.px': 14 }" />
      </ng-template>
    </configurable-table>

    <!-- Expanded row content template for user entity change limits -->
    <ng-template #expandedContent let-user>
      <entity-limit-user-table [user]="user" (dateChanged)="handleChangesOnLimitTable($event)" />
    </ng-template>
    @if (visibleDialog()) {
      <user-edit [visibleDialog]="visibleDialog()" [callParam]="callParam" (closeDialog)="handleCloseDialog($event)" />
    }
    @if (visibleEditLimitDialog()) {
      <entity-limit-edit
        [visibleDialog]="visibleEditLimitDialog()"
        [idUser]="selectedEntity()?.idUser"
        (closeDialog)="handleEditLimitCloseDialog($event)" />
    }

    @if (visibleChangeEntitiesOwnerDialog()) {
      <user-change-owner-entities
        [visibleDialog]="visibleChangeEntitiesOwnerDialog()"
        [fromUser]="selectedEntity()"
        [allUsers]="entityList()"
        (closeDialog)="handleChangeOwnerCloseDialog($event)" />
    }
  `,
  providers: [DialogService],
  imports: [
    ConfigurableTableComponent,
    AngularSvgIconModule,
    TranslateModule,
    EntityLimitUserTableComponent,
    UserEditComponent,
    EntityLimitEditComponent,
    UserChangeOwnerEntitiesDialogComponent
  ]
})
export class UserTableComponent extends TableCrudSupportMenu<User> {
  private iconReg: SvgIconRegistryService = inject(SvgIconRegistryService);
  private userAdminService: UserAdminService = inject(UserAdminService);
  private proposeUserTaskService: ProposeUserTaskService = inject(ProposeUserTaskService);

  private static createTypeIconMap: { [key: number]: string } = {
    [UserTaskType.RELEASE_LOGOUT]: 'user_update',
    [UserTaskType.LIMIT_CUD_CHANGE]: 'user_limit_update'
  };
  private static iconLoadDone = false;

  callParam: User;
  readonly visibleEditLimitDialog = signal(false);
  readonly visibleChangeEntitiesOwnerDialog = signal(false);

  /** Only users with entity limits or limit proposals have a nested limit table */
  readonly canExpandRow = (user: User): boolean =>
    user.entityLimitList.length + user.userChangeLimitProposeList.length > 0;

  constructor() {
    super(BaseSettings.USER, inject(UserAdminService), [CrudMenuOptions.Allow_Edit]);
    UserTableComponent.registerIcons(this.iconReg);

    this.addColumn(DataType.NumericInteger, 'idUser', 'ID', true, false, {
      width: 60
    });
    this.addColumnFeqH(DataType.String, 'nickname', true, false);
    this.addColumnFeqH(DataType.String, 'email', true, false, { width: 150 });
    this.addColumn(DataType.None, 'userChangePropose', 'U', true, true, {
      fieldValueFN: this.getReleaseLogoutProposeIcon.bind(this),
      templateName: 'icon',
      width: 20
    });
    this.addColumn(DataType.None, 'userLimit', 'L', true, true, {
      fieldValueFN: this.getLimitProposeIcon.bind(this),
      templateName: 'icon',
      width: 20
    });
    this.addColumnFeqH(DataType.String, 'mostPrivilegedRole', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.Boolean, 'enabled', true, false, {
      templateName: 'check'
    });
    this.addColumnFeqH(DataType.String, 'localeStr', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'timezoneOffset', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'securityBreachCount', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'limitRequestExceedCount', true, false);
    this.prepareTableAndTranslate();
  }

  private static registerIcons(iconReg: SvgIconRegistryService): void {
    if (!UserTableComponent.iconLoadDone) {
      for (const [key, iconName] of Object.entries(UserTableComponent.createTypeIconMap)) {
        iconReg.loadSvg(BaseSettings.PATH_ASSET_ICONS + iconName + BaseSettings.SVG, iconName);
      }
      UserTableComponent.iconLoadDone = true;
    }
  }

  getReleaseLogoutProposeIcon(user: User, field: ColumnConfig): string {
    return user.userChangePropose ? UserTableComponent.createTypeIconMap[UserTaskType.RELEASE_LOGOUT] : null;
  }

  getLimitProposeIcon(user: User, field: ColumnConfig): string {
    return user.userChangeLimitProposeList.length > 0
      ? UserTableComponent.createTypeIconMap[UserTaskType.LIMIT_CUD_CHANGE]
      : null;
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_USER;
  }

  override onComponentClick(event): void {
    if (!event[this.consumedGT]) {
      this.resetMenu(this.selectedEntity());
    }
  }

  addEntityLimit(): void {
    this.visibleEditLimitDialog.set(true);
  }

  changeOwnerEntities(): void {
    this.visibleChangeEntitiesOwnerDialog.set(true);
  }

  handleEditLimitCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleEditLimitDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  handleChangeOwnerCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleChangeEntitiesOwnerDialog.set(false);
  }

  handleChangesOnLimitTable(event) {
    this.readData();
  }

  override handleCloseDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.action === ProcessedAction.REJECT_DATA_CHANGE) {
        // Reject the proposed user changes, which were created by a user violation
        this.proposeUserTaskService
          .rejectUserTask(this.selectedEntity().userChangePropose.idProposeRequest, processedActionData.data)
          .subscribe((stringResponse) =>
            this.messageToastService.showMessage(InfoLevelType.SUCCESS, stringResponse.response)
          );
      } else {
        this.readData();
      }
    } else {
      this.checkLimitError(processedActionData);
    }
  }

  /** Below the edit entry: a new entity limit for a user with limited editing, and the transfer of owned entities. */
  protected override addCustomMenusToSelectedEntity(user: User, menuItems: MenuItem[]): void {
    menuItems.push({ separator: true });
    if (AuditHelper.isLimitedEditUser(user)) {
      menuItems.push({
        label: 'CREATE|' + EntityLimitUserTableComponent.ENTITY_LIMIT + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.addEntityLimit()
      });
    }
    menuItems.push({
      label: 'USER_CHANGE_OWNER_ENTITIES' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: () => this.changeOwnerEntities()
    });
  }

  protected readData(): void {
    this.userAdminService
      .getAllUsers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((users) => {
        this.createTranslatedValueStoreAndFilterField(users);
        this.entityList.set(users);
        this.refreshSelectedEntity();
      });
  }

  protected prepareCallParam(entity: User) {
    this.callParam = entity;
  }
}
