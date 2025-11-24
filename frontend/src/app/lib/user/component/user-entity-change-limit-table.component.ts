import {TableConfigBase} from '../../datashowbase/table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ConfigurableTableComponent} from '../../datashowbase/configurable-table.component';
import {AngularSvgIconModule} from 'angular-svg-icon';
import {UserEntityChangeLimitEditComponent} from './user-entity-change-limit-edit.component';
import {UserSettingsService} from '../../services/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {UserEntityChangeLimit} from '../../entities/user.entity.change.limit';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../help/help.ids';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {TranslateHelper} from '../../helper/translate.helper';
import {User} from '../../entities/user';
import {AppHelper} from '../../helper/app.helper';
import {InfoLevelType} from '../../message/info.leve.type';
import {UserEntityChangeLimitService} from '../service/user.entity.change.limit.service';
import {MessageToastService} from '../../message/message.toast.service';
import {ColumnConfig, TranslateValue} from '../../datashowbase/column.config';
import {AuditHelper} from '../../helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../proposechange/model/propose.change.entity.whit.entity';
import {ProposeUserTaskService} from '../../dynamicdialog/service/propose.user.task.service';
import {BaseSettings} from '../../base.settings';

/**
 * For a user it is possible to set a limit cf changes for a certain entity. It is implemented as a nested table.
 */
@Component({
  selector: 'user-entity-change-limit-table',
  template: `
    <div class="datatable nestedtable">
      <configurable-table
        [data]="user.userEntityChangeLimitList"
        [fields]="fields"
        [dataKey]="'idUserEntityChangeLimit'"
        [selectionMode]="'single'"
        [(selection)]="selectedUserEntityChangeLimit"
        [multiSortMeta]="multiSortMeta"
        [customSortFn]="customSort.bind(this)"
        [paginator]="true"
        [rows]="20"
        (pageChange)="onPage($event)"
        [stripedRows]="true"
        [showGridlines]="true"
        [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
        [showContextMenu]="!!contextMenuItems"
        [contextMenuItems]="contextMenuItems"
        [contextMenuAppendTo]="'body'"
        [valueGetterFn]="getValueByPath.bind(this)"
        (rowSelect)="onRowSelect($event)"
        (rowUnselect)="onRowUnselect($event)"
        (componentClick)="onComponentClick($event)">

        <!-- Custom icon cell template for svg-icon rendering -->
        <ng-template #iconCell let-row let-field="field" let-value="value">
          <svg-icon [name]="value" [svgStyle]="{ 'width.px':14, 'height.px':14 }"></svg-icon>
        </ng-template>

      </configurable-table>
    </div>

    @if (visibleDialog) {
      <user-entity-change-limit-edit [visibleDialog]="visibleDialog"
                                     [user]="user"
                                     [existingUserEntityChangeLimit]="existingUserEntityChangeLimit"
                                     [proposeChangeEntityWithEntity]="proposeChangeEntityWithEntity"
                                     (closeDialog)="handleCloseDialog($event)">
      </user-entity-change-limit-edit>
    }`,
  standalone: true,
  imports: [CommonModule, ConfigurableTableComponent, AngularSvgIconModule, UserEntityChangeLimitEditComponent]
})
export class UserEntityChangeLimitTableComponent extends TableConfigBase implements OnInit, OnDestroy, IGlobalMenuAttach {

  public static readonly USER_ENTITY_CHANGE_LIMIT = 'USER_ENTITY_CHANGE_LIMIT';
  public readonly UPPER_CASE_ENTITY_NAME = 'entityNameUpperCase';

  @Input() user: User;
  // Master table must be informed about changes thru editing
  @Output() dateChanged = new EventEmitter<ProcessedActionData>();

  contextMenuItems: MenuItem[];

  selectedUserEntityChangeLimit: UserEntityChangeLimit;
  visibleDialog = false;
  entityKeyName: string;

  existingUserEntityChangeLimit: UserEntityChangeLimit;

  proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;
  private proposeMap: Map<number, ProposeChangeEntityWithEntity> = new Map();
  private deleteMenu: MenuItem = {
    label: 'DELETE_RECORD|' + UserEntityChangeLimitTableComponent.USER_ENTITY_CHANGE_LIMIT,
    command: (event) => this.handleDelete(this.selectedUserEntityChangeLimit)
  };
  private menuItems: MenuItem[] = [
    {
      label: 'EDIT_RECORD|' + UserEntityChangeLimitTableComponent.USER_ENTITY_CHANGE_LIMIT + BaseSettings.DIALOG_MENU_SUFFIX,
      command: (event) => this.handleEditEntity(this.selectedUserEntityChangeLimit)
    },
    this.deleteMenu
  ];

  constructor(private activePanelService: ActivePanelService,
    private userEntityChangeLimitService: UserEntityChangeLimitService,
    private messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    private proposeUserTaskService: ProposeUserTaskService,
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(filterService, usersettingsService, translateService, gps);

    this.addColumn(DataType.String, this.UPPER_CASE_ENTITY_NAME, 'ENTITY_NAME', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.None, 'userChangePropose', 'L', true, true,
      {fieldValueFN: this.getLimitProposeIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumnFeqH(DataType.NumericInteger, 'dayLimit', true, false);
    this.addColumnFeqH(DataType.DateNumeric, 'untilDate', true, false);
    this.prepareTableAndTranslate();
    this.entityKeyName = this.gps.getKeyNameByEntityName(UserEntityChangeLimit.name);
    this.multiSortMeta.push({field: this.UPPER_CASE_ENTITY_NAME, order: 1});
    TranslateHelper.translateMenuItems(this.menuItems, this.translateService);
  }


  getLimitProposeIcon(userEntityChangeLimit: UserEntityChangeLimit, field: ColumnConfig): string {
    return !userEntityChangeLimit.idUserEntityChangeLimit || this.proposeMap.has(userEntityChangeLimit.idUserEntityChangeLimit) ?
      'user_limit_update' : null;
  }

  onComponentClick(event): void {
    event[this.consumedGT] = true;
    this.setMenuItemsToActivePanel();
  }

  onRowSelect(event) {
    this.selectedUserEntityChangeLimit = event.data;
    this.setMenuItemsToActivePanel();
  }

  onRowUnselect(event) {
    this.selectedUserEntityChangeLimit = null;
  }

  setMenuItemsToActivePanel(): void {
    this.activePanelService.activatePanel(this,
      {editMenu: this.getMenuItemsOnUserEntityChangeLimit(this.selectedUserEntityChangeLimit)});
    this.contextMenuItems = this.getMenuItemsOnUserEntityChangeLimit(this.selectedUserEntityChangeLimit);
  }

  handleEditEntity(selectedUserEntityChangeLimit: UserEntityChangeLimit): void {
    this.existingUserEntityChangeLimit = selectedUserEntityChangeLimit;
    this.proposeChangeEntityWithEntity = this.proposeMap.get(selectedUserEntityChangeLimit.idUserEntityChangeLimit);
    this.visibleDialog = true;
  }

  handleDelete(selectedUserEntityChangeLimit: UserEntityChangeLimit) {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + UserEntityChangeLimitTableComponent.USER_ENTITY_CHANGE_LIMIT, () => {
        this.userEntityChangeLimitService.deleteEntity(selectedUserEntityChangeLimit[this.entityKeyName]).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: UserEntityChangeLimitTableComponent.USER_ENTITY_CHANGE_LIMIT});
          this.dateChanged.emit(new ProcessedActionData(ProcessedAction.DELETED,
            null));
        });
      });
  }

  ngOnInit(): void {
    this.createProposeLimitForView();
    this.user.userEntityChangeLimitList.forEach((userEntityChangeLimit: UserEntityChangeLimit) =>
      userEntityChangeLimit[this.UPPER_CASE_ENTITY_NAME] = userEntityChangeLimit.entityName.toUpperCase());
    this.createTranslatedValueStoreAndFilterField(this.user.userEntityChangeLimitList);
  }

  handleCloseDialog(processedActionData: ProcessedActionData) {
    this.visibleDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.action === ProcessedAction.REJECT_DATA_CHANGE) {
        this.proposeUserTaskService.rejectUserTask(this.proposeChangeEntityWithEntity.proposeChangeEntity.idProposeRequest,
          processedActionData.data).subscribe(stringResponse => this.messageToastService.showMessage(InfoLevelType.SUCCESS,
          stringResponse.response));
        this.dateChanged.emit(new ProcessedActionData(ProcessedAction.UPDATED,
          processedActionData.data));
      }
      this.dateChanged.emit(new ProcessedActionData(ProcessedAction.UPDATED,
        processedActionData.data));
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  ngOnDestroy(): void {
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_USER;
  }

  protected getMenuItemsOnUserEntityChangeLimit(userEntityChangeLimit: UserEntityChangeLimit): MenuItem[] {
    return this.selectedUserEntityChangeLimit == null ? null : this.menuItems;
  }

  private createProposeLimitForView(): void {
    if (this.user.userChangeLimitProposeList) {
      for (const proposeUserTask of this.user.userChangeLimitProposeList) {
        const entityField = proposeUserTask.proposeChangeFieldList.find(p => p.field === 'entity');
        let existingLimit = this.user.userEntityChangeLimitList.find(uec => uec.entityName === entityField.valueDesarialized);
        this.deleteMenu.disabled = !existingLimit;
        if (!existingLimit) {
          existingLimit = new UserEntityChangeLimit();
          // create and add user entity change Limit
          const uecl: UserEntityChangeLimit = new UserEntityChangeLimit();
          uecl.idUser = this.user.idUser;
          existingLimit.idUser = uecl.idUser;
          uecl.entityName = entityField.valueDesarialized;
          existingLimit.entityName = uecl.entityName;
          uecl.dayLimit = proposeUserTask.proposeChangeFieldList.find(p => p.field === 'dayLimit').valueDesarialized;
          uecl.untilDate = proposeUserTask.proposeChangeFieldList.find(p => p.field === 'untilDate').valueDesarialized;
          this.user.userEntityChangeLimitList.push(uecl);
        }
        this.proposeMap.set(existingLimit.idUserEntityChangeLimit,
          AuditHelper.convertToProposeChangeEntityWithEntity(existingLimit, proposeUserTask));
      }
    }
  }
}

