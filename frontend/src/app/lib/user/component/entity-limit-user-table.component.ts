import { TableConfigBase } from '../../datashowbase/table.config.base';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../services/globalparameter.service';
import {
  Component,
  EventEmitter,
  Injector,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
  ChangeDetectionStrategy
} from '@angular/core';

import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { EntityLimitEditComponent } from '../../entitylimit/component/entity.limit.edit.component';
import { UserSettingsService } from '../../services/user.settings.service';
import { DataType } from '../../dynamic-form/models/data.type';
import { ConfirmationService, FilterService, MenuItem } from '@openng/optimus-ui/api';
import { EntityLimit } from '../../entities/entity.limit';
import { ActivePanelService } from '../../mainmenubar/service/active.panel.service';
import { IGlobalMenuAttach } from '../../mainmenubar/component/iglobal.menu.attach';
import { HelpIds } from '../../help/help.ids';
import { ProcessedActionData } from '../../types/processed.action.data';
import { ProcessedAction } from '../../types/processed.action';
import { TranslateHelper } from '../../helper/translate.helper';
import { User } from '../../entities/user';
import { AppHelper } from '../../helper/app.helper';
import { InfoLevelType } from '../../message/info.leve.type';
import { EntityLimitService } from '../../entitylimit/service/entity.limit.service';
import { MessageToastService } from '../../message/message.toast.service';
import { ColumnConfig, TranslateValue } from '../../datashowbase/column.config';
import { AuditHelper } from '../../helper/audit.helper';
import { ProposeChangeEntityWithEntity } from '../../proposechange/model/propose.change.entity.whit.entity';
import { ProposeUserTaskService } from '../../dynamicdialog/service/propose.user.task.service';
import { BaseSettings } from '../../base.settings';

/**
 * For a user it is possible to set a limit cf changes for a certain entity. It is implemented as a nested table.
 */
@Component({
  selector: 'entity-limit-user-table',
  template: ` <div class="datatable nestedtable">
      <configurable-table
        [data]="user?.entityLimitList || []"
        [fields]="fields"
        [dataKey]="'idEntityLimit'"
        [selectionMode]="'single'"
        [(selection)]="selectedEntityLimit"
        [multiSortMeta]="multiSortMeta"
        [customSortFn]="customSort.bind(this)"
        [paginator]="true"
        [rows]="20"
        (pageChange)="onPage($event)"
        [stripedRows]="true"
        [showGridlines]="true"
        [containerClass]="{
          'data-container': true,
          'active-border': isActivated(),
          'passiv-border': !isActivated()
        }"
        [showContextMenu]="!!contextMenuItems"
        [contextMenuItems]="contextMenuItems"
        [contextMenuAppendTo]="'body'"
        [valueGetterFn]="getValueByPath.bind(this)"
        (rowSelect)="onRowSelect($event)"
        (rowUnselect)="onRowUnselect($event)"
        (componentClick)="onComponentClick($event)">
        <!-- Custom icon cell template for svg-icon rendering -->
        <ng-template #iconCell let-row let-field="field" let-value="value">
          <svg-icon [name]="value" [svgStyle]="{ 'width.px': 14, 'height.px': 14 }"></svg-icon>
        </ng-template>
      </configurable-table>
    </div>

    @if (visibleDialog) {
      <entity-limit-edit
        [visibleDialog]="visibleDialog"
        [idUser]="user.idUser"
        [existingEntityLimit]="existingEntityLimit"
        [proposeChangeEntityWithEntity]="proposeChangeEntityWithEntity"
        (closeDialog)="handleCloseDialog($event)">
      </entity-limit-edit>
    }`,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [ConfigurableTableComponent, AngularSvgIconModule, EntityLimitEditComponent]
})
export class EntityLimitUserTableComponent
  extends TableConfigBase
  implements OnInit, OnChanges, OnDestroy, IGlobalMenuAttach
{
  public static readonly ENTITY_LIMIT = 'ENTITY_LIMIT';

  @Input() user: User;
  // Master table must be informed about changes thru editing
  @Output() dateChanged = new EventEmitter<ProcessedActionData>();

  contextMenuItems: MenuItem[];

  selectedEntityLimit: EntityLimit;
  visibleDialog = false;
  entityKeyName: string;

  existingEntityLimit: EntityLimit;

  proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;
  private proposeMap: Map<number, ProposeChangeEntityWithEntity> = new Map();
  private deleteMenu: MenuItem = {
    label: 'DELETE_RECORD|' + EntityLimitUserTableComponent.ENTITY_LIMIT,
    command: (event) => this.handleDelete(this.selectedEntityLimit)
  };
  private menuItems: MenuItem[] = [
    {
      label: 'EDIT_RECORD|' + EntityLimitUserTableComponent.ENTITY_LIMIT + BaseSettings.DIALOG_MENU_SUFFIX,
      command: (event) => this.handleEditEntity(this.selectedEntityLimit)
    },
    this.deleteMenu
  ];

  constructor(
    private activePanelService: ActivePanelService,
    private entityLimitService: EntityLimitService,
    private messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    private proposeUserTaskService: ProposeUserTaskService,
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);

    this.addColumnFeqH(DataType.String, 'entityName', true, false, {
      fieldValueFN: this.getEntityLabel.bind(this)
    });
    this.addColumnFeqH(DataType.String, 'limitTypeKey', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.None, 'userChangePropose', 'L', true, true, {
      fieldValueFN: this.getLimitProposeIcon.bind(this),
      templateName: 'icon',
      width: 20
    });
    this.addColumnFeqH(DataType.NumericInteger, 'limitValue', true, false);
    this.addColumnFeqH(DataType.DateNumeric, 'validUntil', true, false);
    this.prepareTableAndTranslate();
    this.entityKeyName = this.gps.getKeyNameByEntityName(EntityLimit.name);
    this.multiSortMeta.push({ field: 'entityName', order: 1 });
    TranslateHelper.translateMenuItems(this.menuItems, this.translateService);
  }

  /** The entity name is a Java class name, its translation key is that name in UPPER_SNAKE_CASE. */
  private getEntityLabel(entityLimit: EntityLimit, field: ColumnConfig, valueField: any): string {
    return entityLimit.entityName
      ? this.translateService.instant(AppHelper.toUpperCaseWithUnderscore(entityLimit.entityName))
      : '';
  }

  getLimitProposeIcon(entityLimit: EntityLimit, field: ColumnConfig): string {
    return !entityLimit.idEntityLimit || this.proposeMap.has(entityLimit.idEntityLimit) ? 'user_limit_update' : null;
  }

  onComponentClick(event): void {
    event[this.consumedGT] = true;
    this.setMenuItemsToActivePanel();
  }

  onRowSelect(event) {
    this.selectedEntityLimit = event.data;
    this.setMenuItemsToActivePanel();
  }

  onRowUnselect(event) {
    this.selectedEntityLimit = null;
  }

  setMenuItemsToActivePanel(): void {
    this.activePanelService.activatePanel(this, {
      editMenu: this.getMenuItemsOnEntityLimit(this.selectedEntityLimit)
    });
    this.contextMenuItems = this.getMenuItemsOnEntityLimit(this.selectedEntityLimit);
  }

  handleEditEntity(selectedEntityLimit: EntityLimit): void {
    this.existingEntityLimit = selectedEntityLimit;
    this.proposeChangeEntityWithEntity = this.proposeMap.get(selectedEntityLimit.idEntityLimit);
    this.visibleDialog = true;
  }

  handleDelete(selectedEntityLimit: EntityLimit) {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + EntityLimitUserTableComponent.ENTITY_LIMIT,
      () => {
        this.entityLimitService.deleteEntity(selectedEntityLimit[this.entityKeyName]).subscribe((response) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
            i18nRecord: EntityLimitUserTableComponent.ENTITY_LIMIT
          });
          this.dateChanged.emit(new ProcessedActionData(ProcessedAction.DELETED, null));
        });
      }
    );
  }

  ngOnInit(): void {
    this.initializeUserData();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['user'] && this.user) {
      this.initializeUserData();
    }
  }

  private initializeUserData(): void {
    if (!this.user) {
      return;
    }
    this.createProposeLimitForView();
    this.createTranslatedValueStoreAndFilterField(this.user.entityLimitList);
  }

  handleCloseDialog(processedActionData: ProcessedActionData) {
    this.visibleDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.action === ProcessedAction.REJECT_DATA_CHANGE) {
        this.proposeUserTaskService
          .rejectUserTask(
            this.proposeChangeEntityWithEntity.proposeChangeEntity.idProposeRequest,
            processedActionData.data
          )
          .subscribe((stringResponse) =>
            this.messageToastService.showMessage(InfoLevelType.SUCCESS, stringResponse.response)
          );
        this.dateChanged.emit(new ProcessedActionData(ProcessedAction.UPDATED, processedActionData.data));
      }
      this.dateChanged.emit(new ProcessedActionData(ProcessedAction.UPDATED, processedActionData.data));
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  ngOnDestroy(): void {}

  public getHelpContextId(): string {
    return HelpIds.HELP_USER;
  }

  protected getMenuItemsOnEntityLimit(entityLimit: EntityLimit): MenuItem[] {
    return this.selectedEntityLimit == null ? null : this.menuItems;
  }

  private createProposeLimitForView(): void {
    if (this.user.userChangeLimitProposeList) {
      for (const proposeUserTask of this.user.userChangeLimitProposeList) {
        const entityField = proposeUserTask.proposeChangeFieldList.find((p) => p.field === 'entity');
        let existingLimit = this.user.entityLimitList.find((uec) => uec.entityName === entityField.valueDesarialized);
        this.deleteMenu.disabled = !existingLimit;
        if (!existingLimit) {
          existingLimit = new EntityLimit();
          // create and add user entity change Limit
          const uecl: EntityLimit = new EntityLimit();
          uecl.idUser = this.user.idUser;
          existingLimit.idUser = uecl.idUser;
          uecl.entityName = entityField.valueDesarialized;
          existingLimit.entityName = uecl.entityName;
          uecl.limitValue = proposeUserTask.proposeChangeFieldList.find(
            (p) => p.field === 'dayLimit'
          ).valueDesarialized;
          uecl.validUntil = proposeUserTask.proposeChangeFieldList.find(
            (p) => p.field === 'untilDate'
          ).valueDesarialized;
          // A limit increase request always asks for a daily CUD budget of one user.
          uecl.keyId = 'DAY_CUD|' + uecl.entityName + '|||';
          uecl.limitTypeKey = 'LIMIT_TYPE_DAY_CUD';
          this.user.entityLimitList.push(uecl);
        }
        this.proposeMap.set(
          existingLimit.idEntityLimit,
          AuditHelper.convertToProposeChangeEntityWithEntity(existingLimit, proposeUserTask)
        );
      }
    }
  }
}
