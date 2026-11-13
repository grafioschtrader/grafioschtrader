import { requireInitialized } from '../../helper/required-state';
import type { PanelClickEvent } from '../../mainmenubar/component/iglobal.menu.attach';
import { TableConfigBase } from '../../datashowbase/table.config.base';
import { Component, DestroyRef, inject, signal, output, input, effect, untracked } from '@angular/core';
import { Subscription } from 'rxjs';

import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { EntityLimitEditComponent } from '../../entitylimit/component/entity.limit.edit.component';
import { DataType } from '../../dynamic-form/models/data.type';
import { ConfirmationService, MenuItem } from '@openng/optimus-ui/api';
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
import { dayCudKeyId, projectLimitRequest, readLimitRequest } from '../../entitylimit/model/limit.request.projection';
import { MessageToastService } from '../../message/message.toast.service';
import { ColumnConfig, TranslateValue } from '../../datashowbase/column.config';
import type { ProposeChangeEntityWithEntity } from '../../proposechange/model/propose.change.entity.whit.entity';
import { ProposeUserTaskService } from '../../dynamicdialog/service/propose.user.task.service';
import { BaseSettings } from '../../base.settings';

/**
 * For a user it is possible to set a limit cf changes for a certain entity. It is implemented as a nested table.
 */
@Component({
  selector: 'entity-limit-user-table',
  template: ` <div class="datatable nestedtable">
      <configurable-table
        [data]="entityLimits()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        [dataKey]="'idEntityLimit'"
        [selectionMode]="'single'"
        [(selection)]="selectedEntityLimit"
        [multiSortMeta]="multiSortMeta"
        [customSortFn]="customSorter"
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
        [showContextMenu]="!!contextMenuItems()"
        [contextMenuItems]="contextMenuItems()"
        [contextMenuAppendTo]="'body'"
        [valueGetterFn]="valueGetter"
        (rowSelect)="onRowSelect($event)"
        (rowUnselect)="onRowUnselect($event)"
        (componentClick)="onComponentClick($event)">
        <!-- Custom icon cell template for svg-icon rendering -->
        <ng-template #iconCell let-row let-field="field" let-value="value">
          <svg-icon [name]="value" [svgStyle]="{ 'width.px': 14, 'height.px': 14 }" />
        </ng-template>
      </configurable-table>
    </div>

    @if (visibleDialog()) {
      <entity-limit-edit
        [visibleDialog]="visibleDialog()"
        [idUser]="user().idUser"
        [existingEntityLimit]="existingEntityLimit"
        [proposeChangeEntityWithEntity]="proposeChangeEntityWithEntity"
        (closeDialog)="handleCloseDialog($event)" />
    }`,
  imports: [ConfigurableTableComponent, AngularSvgIconModule, EntityLimitEditComponent]
})
export class EntityLimitUserTableComponent extends TableConfigBase implements IGlobalMenuAttach {
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private entityLimitService: EntityLimitService = inject(EntityLimitService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private confirmationService: ConfirmationService = inject(ConfirmationService);
  private proposeUserTaskService: ProposeUserTaskService = inject(ProposeUserTaskService);

  public static readonly ENTITY_LIMIT = 'ENTITY_LIMIT';

  readonly user = input.required<User>();
  // Master table must be informed about changes thru editing
  readonly dateChanged = output<ProcessedActionData>();

  /** Translated context menu of the selected limit, null without a selection */
  readonly contextMenuItems = signal<MenuItem[] | null>(null);

  /** The limits of the user, followed by a row for every requested limit the user does not have yet */
  readonly entityLimits = signal<EntityLimit[]>([]);

  readonly selectedEntityLimit = signal<EntityLimit | null>(null);
  readonly visibleDialog = signal(false);
  entityKeyName: string;

  private initializedExistingEntityLimit?: EntityLimit;

  get existingEntityLimit(): EntityLimit {
    return requireInitialized(this.initializedExistingEntityLimit, 'EntityLimitUserTableComponent.existingEntityLimit');
  }

  set existingEntityLimit(value: EntityLimit) {
    this.initializedExistingEntityLimit = value;
  }

  proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity | undefined;
  /** The pending limit request per limit key id */
  private proposeMap: Map<string, ProposeChangeEntityWithEntity> = new Map();

  /** False when the last limit proposal asks for a limit the user does not have yet, so there is nothing to delete */
  private deleteAllowed = true;

  /** Translation of the latest menu; a newer menu replaces one that is still being translated */
  private menuSubscription?: Subscription;

  constructor() {
    super();

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
    inject(DestroyRef).onDestroy(() => {
      this.menuSubscription?.unsubscribe();
      this.activePanelService.destroyPanel(this);
    });

    effect(() => {
      this.user();
      untracked(() => {
        if (this.user()) {
          const limits = this.createProposeLimitForView();
          this.createTranslatedValueStoreAndFilterField(limits);
          this.entityLimits.set(limits);
        }
      });
    });
  }

  /** The entity name is a Java class name, its translation key is that name in UPPER_SNAKE_CASE. */
  private getEntityLabel(entityLimit: EntityLimit, field: ColumnConfig, valueField: any): string {
    return entityLimit.entityName
      ? this.translateService.instant(AppHelper.toUpperCaseWithUnderscore(entityLimit.entityName))
      : '';
  }

  getLimitProposeIcon(entityLimit: EntityLimit, field: ColumnConfig): string | null {
    return !entityLimit.idEntityLimit || this.proposeMap.has(entityLimit.keyId ?? '') ? 'user_limit_update' : null;
  }

  onComponentClick(event: PanelClickEvent): void {
    event[this.consumedGT] = true;
    this.setMenuItemsToActivePanel();
  }

  onRowSelect(event: { data: EntityLimit }) {
    this.selectedEntityLimit.set(event.data);
    this.setMenuItemsToActivePanel();
  }

  onRowUnselect(event: unknown) {
    this.selectedEntityLimit.set(null);
  }

  /** Publishes the translated menu of the selected limit as context menu and in the menu bar. */
  setMenuItemsToActivePanel(): void {
    this.menuSubscription?.unsubscribe();
    this.menuSubscription = TranslateHelper.translateMenuItems$(
      this.getMenuItemsOnEntityLimit(this.selectedEntityLimit()),
      this.translateService
    ).subscribe((menuItems) => {
      this.contextMenuItems.set(menuItems);
      this.activePanelService.activatePanel(this, { editMenu: menuItems });
    });
  }

  handleEditEntity(selectedEntityLimit: EntityLimit): void {
    this.existingEntityLimit = selectedEntityLimit;
    this.proposeChangeEntityWithEntity = this.proposeMap.get(selectedEntityLimit.keyId ?? '');
    this.visibleDialog.set(true);
  }

  handleDelete(selectedEntityLimit: EntityLimit) {
    const id = selectedEntityLimit.idEntityLimit;
    if (id == null) return;
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + EntityLimitUserTableComponent.ENTITY_LIMIT,
      () => {
        this.entityLimitService.deleteEntity(id).subscribe((response) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
            i18nRecord: EntityLimitUserTableComponent.ENTITY_LIMIT
          });
          this.dateChanged.emit(new ProcessedActionData(ProcessedAction.DELETED, null));
        });
      }
    );
  }

  handleCloseDialog(processedActionData: ProcessedActionData) {
    this.visibleDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.action === ProcessedAction.REJECT_DATA_CHANGE) {
        const proposalId = this.proposeChangeEntityWithEntity?.proposeChangeEntity.idProposeRequest;
        if (proposalId == null) return;
        this.proposeUserTaskService
          .rejectUserTask(proposalId, processedActionData.data)
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

  public getHelpContextId(): string {
    return HelpIds.HELP_USER;
  }

  /**
   * Builds the untranslated edit and delete entries for the selected limit.
   *
   * @param entityLimit - Selected limit or null
   * @returns The menu entries, or null without a selection
   */
  protected getMenuItemsOnEntityLimit(entityLimit: EntityLimit | null): MenuItem[] | null {
    return entityLimit == null
      ? null
      : [
          {
            label: 'EDIT_RECORD|' + EntityLimitUserTableComponent.ENTITY_LIMIT + BaseSettings.DIALOG_MENU_SUFFIX,
            command: () => this.handleEditEntity(entityLimit)
          },
          {
            label: 'DELETE_RECORD|' + EntityLimitUserTableComponent.ENTITY_LIMIT,
            command: () => this.handleDelete(entityLimit),
            disabled: !this.deleteAllowed
          }
        ];
  }

  /**
   * Returns the limits of the user plus a row for every limit proposal on a limit the user does not have yet, and maps
   * each proposal to the limit it changes by that limit's key id. The user passed in from the master table is left
   * unchanged.
   */
  private createProposeLimitForView(): EntityLimit[] {
    const user = this.user();
    const entityLimits = [...(user.entityLimitList ?? [])];
    this.proposeMap.clear();
    this.deleteAllowed = true;
    for (const proposeUserTask of user.userChangeLimitProposeList ?? []) {
      const request = readLimitRequest(proposeUserTask);
      if (!request) continue;
      // A limit increase request always asks for the daily CUD budget of one user.
      const keyId = dayCudKeyId(request.entityName);
      let entityLimit = entityLimits.find((limit) => limit.keyId === keyId);
      this.deleteAllowed = !!entityLimit;
      if (!entityLimit) {
        if (request.dayLimit == null || request.untilDate == null) continue;
        entityLimit = new EntityLimit();
        entityLimit.idUser = user.idUser;
        entityLimit.entityName = request.entityName;
        entityLimit.keyId = keyId;
        entityLimit.limitTypeKey = 'LIMIT_TYPE_DAY_CUD';
        entityLimit.limitValue = request.dayLimit;
        entityLimit.validUntil = request.untilDate as unknown as Date;
        entityLimits.push(entityLimit);
      }
      this.proposeMap.set(keyId, projectLimitRequest(entityLimit, proposeUserTask, request));
    }
    return entityLimits;
  }
}
