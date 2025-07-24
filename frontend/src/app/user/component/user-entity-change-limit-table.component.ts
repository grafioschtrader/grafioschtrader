import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild} from '@angular/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {UserEntityChangeLimit} from '../../lib/entities/user.entity.change.limit';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../shared/help/help.ids';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {User} from '../../lib/entities/user';
import {AppHelper} from '../../lib/helper/app.helper';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {UserEntityChangeLimitService} from '../service/user.entity.change.limit.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import {ProposeUserTaskService} from '../../shared/dynamicdialog/service/propose.user.task.service';
import {BaseSettings} from '../../lib/base.settings';

/**
 * For a user it is possible to set a limit cf changes for a certain entity. It is implemented as a nested table.
 */
@Component({
  selector: 'user-entity-change-limit-table',
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <div class="datatable nestedtable">
        <p-table [columns]="fields" [value]="user.userEntityChangeLimitList" selectionMode="single"
                 (onRowSelect)="onRowSelect($event)" (onRowUnselect)="onRowUnselect($event)"
                 (onPage)="onPage($event)" dataKey="idUserEntityChangeLimit" [paginator]="true" [rows]="20"
                 (sortFunction)="customSort($event)" [customSort]="true"
                 sortMode="multiple" [multiSortMeta]="multiSortMeta" [contextMenu]="cm"
                 stripedRows showGridlines>
          <ng-template #header let-fields>
            <tr>
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
          <ng-template #body let-el let-columns="fields">
            <tr [pContextMenuRow] [pSelectableRow]="el">
              @for (field of fields; track field) {
                @if (field.visible) {
                  <td [style.max-width.px]="field.width"
                      [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                      [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric)?
                                      'text-right': ''">
                    @switch (field.templateName) {
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
              }
            </tr>
          </ng-template>
        </p-table>
        <p-contextMenu #cm [model]="contextMenuItems" appendTo="body"></p-contextMenu>
      </div>
    </div>

    @if (visibleDialog) {
      <user-entity-change-limit-edit [visibleDialog]="visibleDialog"
                                     [user]="user"
                                     [existingUserEntityChangeLimit]="existingUserEntityChangeLimit"
                                     [proposeChangeEntityWithEntity]="proposeChangeEntityWithEntity"
                                     (closeDialog)="handleCloseDialog($event)">
      </user-entity-change-limit-edit>
    }`,
  standalone: false
})
export class UserEntityChangeLimitTableComponent extends TableConfigBase implements OnInit, OnDestroy, IGlobalMenuAttach {

  public static readonly USER_ENTITY_CHANGE_LIMIT = 'USER_ENTITY_CHANGE_LIMIT';
  public readonly UPPER_CASE_ENTITY_NAME = 'entityNameUpperCase';

  // @ViewChild('cm', { static: false }) contextMenu: ContextMenu;
  @ViewChild('cm') contextMenu: any;
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
    this.contextMenu && this.contextMenu.hide();
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
    this.contextMenu && this.contextMenu.hide();
  }

  callMeDeactivate(): void {
  }

  ngOnDestroy(): void {
    this.contextMenu && this.contextMenu.hide();
  }

  public getHelpContextId(): HelpIds {
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

