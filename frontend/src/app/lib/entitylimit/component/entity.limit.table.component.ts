import {Component, Injector, OnDestroy, ViewChild} from '@angular/core';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {TooltipModule} from 'primeng/tooltip';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {combineLatest} from 'rxjs';

import {TableCrudSupportMenu} from '../../datashowbase/table.crud.support.menu';
import {ConfigurableTableComponent} from '../../datashowbase/configurable-table.component';
import {EntityLimitEditComponent} from './entity.limit.edit.component';
import {EntityLimit} from '../../entities/entity.limit';
import {EntityLimitService} from '../service/entity.limit.service';
import {LimitKeyDefinition} from '../model/limit.key.definition';
import {MessageToastService} from '../../message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {HelpIds} from '../../help/help.ids';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../datashowbase/column.config';
import {FilterType} from '../../datashowbase/filter.type';
import {BaseSettings} from '../../base.settings';
import {AppHelper} from '../../helper/app.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';

/**
 * Administration of every configured limit, of any limit type and any scope.
 *
 * A row whose limit key is a registered lifetime cap and which has neither a role nor a user is the default of that
 * key. Its value may be changed but the row itself must stay, because a key with no row at all resolves as unlimited;
 * the delete action is therefore hidden for those rows and the backend rejects the deletion as well.
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
      [hasFilter]="hasFilter && showFilterRow"
      [formLocale]="formLocale"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [showContextMenu]="!!contextMenuItems"
      [contextMenuItems]="contextMenuItems"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">

      <h4 caption>{{ entityNameUpper | translate }}</h4>

      <!-- Whether the limited entity holds data of one client/user or data shared between all tenants. The cell shows
           only the icon, the column value stays the translated text so that sorting and the dropdown filter work. -->
      <ng-template #iconCell let-row let-value="value">
        @if (isSharedData(row) !== undefined) {
          <i [class]="isSharedData(row) ? 'fa fa-globe' : 'fa fa-lock'"
             [pTooltip]="value" tooltipPosition="top" aria-hidden="true"></i>
        }
      </ng-template>

    </configurable-table>

    @if (visibleDialog) {
      <entity-limit-edit [visibleDialog]="visibleDialog"
                         [existingEntityLimit]="callParam"
                         [existingEntityLimits]="entityList"
                         (closeDialog)="handleCloseDialog($event)">
      </entity-limit-edit>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [ConfigurableTableComponent, TranslateModule, TooltipModule, EntityLimitEditComponent]
})
export class EntityLimitTableComponent extends TableCrudSupportMenu<EntityLimit> implements OnDestroy {

  /** The row the edit dialog works on, null when a new one is created. */
  callParam: EntityLimit;

  @ViewChild(ConfigurableTableComponent) private configurableTable: ConfigurableTableComponent;

  /** Key ids whose default row is mandatory, so the delete action can be withheld for exactly those rows. */
  private mandatoryKeyIds = new Set<string>();

  /** Translated role name per role id, so the role column shows a name instead of the raw id. */
  private roleNameById = new Map<string, string>();

  /**
   * Whether an entity holds data shared between all tenants, per entity name. Delivered with the limit key
   * definitions rather than derived from the owner scope of the row, because only the total-number limits have a
   * scope while the daily ones do not.
   */
  private sharedByEntityName = new Map<string, boolean>();

  constructor(private entityLimitService: EntityLimitService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector) {
    super('ENTITY_LIMIT_INFO_CLASS', entityLimitService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService, injector);

    this.addColumnFeqH(DataType.String, 'entityName', true, false,
      {fieldValueFN: this.getEntityLabel.bind(this), filterType: FilterType.withOptions});
    this.addColumn(DataType.String, 'dataScope', 'DATA_SCOPE', true, false,
      {fieldValueFN: this.getDataScopeLabel.bind(this), templateName: 'icon', width: 40,
        filterType: FilterType.withOptions});
    this.addColumnFeqH(DataType.String, 'limitTypeKey', true, false,
      {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
    this.addColumnFeqH(DataType.String, 'relationEntityName', true, false,
      {fieldValueFN: this.getRelationEntityLabel.bind(this), filterType: FilterType.withOptions});
    this.addColumnFeqH(DataType.String, 'countScopeKey', true, false,
      {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
    this.addColumnFeqH(DataType.String, 'ownerScopeKey', true, false,
      {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
    this.addColumnFeqH(DataType.String, 'idRole', true, false,
      {fieldValueFN: this.getRoleName.bind(this), filterType: FilterType.withOptions});
    this.addColumnFeqH(DataType.NumericInteger, 'idUser', true, false,
      {filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.NumericInteger, 'limitValue', true, false,
      {filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.DateNumeric, 'validUntil', true, false);
    this.prepareTableAndTranslate();
    this.multiSortMeta.push({field: 'entityName', order: 1});
    // The table is long and mixes three limit families, so the filter row is offered right away; the show menu
    // hides it again.
    this.filterRowToggleable = true;
    this.showFilterRow = true;
  }

  /**
   * The roles have to be resolved before the first read, because the dropdown filter of the role column is built
   * from the displayed name and not from the id.
   */
  protected override initialize(): void {
    combineLatest([this.entityLimitService.getLimitKeyDefinitions(), this.entityLimitService.getRoles()])
      .subscribe(([definitions, roles]: [LimitKeyDefinition[], ValueKeyHtmlSelectOptions[]]) => {
        this.mandatoryKeyIds = new Set(definitions.filter(d => d.mandatoryAllRow).map(d => d.keyId));
        this.sharedByEntityName = new Map(definitions.filter(d => d.sharedData != null)
          .map(d => [d.entityName, d.sharedData]));
        this.roleNameById = new Map(roles.map(role =>
          [String(role.key), this.translateService.instant(role.value)]));
        this.readData();
      });
  }

  protected override readData(): void {
    this.entityLimitService.getAllEntityLimits().subscribe((entityLimits: EntityLimit[]) => {
      this.createTranslatedValueStoreAndFilterField(entityLimits);
      this.prepareFilter(entityLimits);
      this.entityList = entityLimits;
    });
  }

  /**
   * The default row of a registered lifetime cap cannot be removed: without it the cap would silently become
   * unlimited. Role and user overrides, and every daily row, stay freely deletable.
   */
  protected override hasRightsForDeleteEntity(entityLimit: EntityLimit): boolean {
    return !(entityLimit.idRole == null && entityLimit.idUser == null
      && this.mandatoryKeyIds.has(entityLimit.keyId));
  }

  protected prepareCallParam(entity: EntityLimit): void {
    this.callParam = entity;
  }

  /**
   * Offers the entry that shows and hides the filter row. Without this override the table would have no show menu at
   * all, because TableCrudSupportMenu returns none by default.
   */
  protected override prepareShowMenu(): MenuItem[] {
    const menuItems = this.getMenuShowOptions();
    if (menuItems) {
      TranslateHelper.translateMenuItems(menuItems, this.translateService);
    }
    return menuItems;
  }

  /**
   * Drops the entered filters when the filter row is hidden. They would otherwise keep hiding rows while no control
   * to reset them is on screen any more.
   */
  override toggleFilterRow(): void {
    super.toggleFilterRow();
    if (!this.showFilterRow) {
      this.configurableTable?.clearFilters();
    }
  }

  /**
   * Whether the entity of a row holds data shared between all tenants.
   *
   * @param entityLimit the row
   * @return true for shared data, false for data of one client or user, undefined when the entity is unknown
   */
  isSharedData(entityLimit: EntityLimit): boolean | undefined {
    return this.sharedByEntityName.get(entityLimit.entityName);
  }

  private getEntityLabel(entityLimit: EntityLimit, field: ColumnConfig, valueField: any): string {
    return this.translateEntityName(entityLimit.entityName);
  }

  /**
   * The value of the icon column: the translated text rather than the icon name, so that the column stays sortable
   * and its dropdown filter offers the two readable labels. The cell itself renders only the icon.
   */
  private getDataScopeLabel(entityLimit: EntityLimit, field: ColumnConfig, valueField: any): string {
    const shared = this.isSharedData(entityLimit);
    return shared === undefined ? ''
      : this.translateService.instant(shared ? 'DATA_SCOPE_SHARED' : 'DATA_SCOPE_PRIVATE');
  }

  private getRelationEntityLabel(entityLimit: EntityLimit, field: ColumnConfig, valueField: any): string {
    return entityLimit.relationEntityName ? this.translateEntityName(entityLimit.relationEntityName) : '';
  }

  /** The role ids arrive as strings from the options endpoint, the column holds them as numbers. */
  private getRoleName(entityLimit: EntityLimit, field: ColumnConfig, value: any): string {
    return value == null ? '' : this.roleNameById.get(String(value)) ?? String(value);
  }

  private translateEntityName(entityName: string): string {
    return this.translateService.instant(AppHelper.toUpperCaseWithUnderscore(entityName));
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_ENTITY_LIMIT;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }
}
