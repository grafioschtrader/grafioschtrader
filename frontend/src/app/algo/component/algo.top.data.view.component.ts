import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { httpResource } from '@angular/common/http';
import { Component, effect, inject, input, numberAttribute, OnDestroy, OnInit, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { ConfirmationService, MenuItem, TreeNode } from '@openng/optimus-ui/api';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { concat, Subscription } from 'rxjs';
import { toArray } from 'rxjs/operators';
import { Tenant } from '../../entities/tenant';
import { ColumnConfig, EditInputType } from '../../lib/datashowbase/column.config';
import {
  ConfigurableTreeTableComponent,
  TreeTableCellEditEvent
} from '../../lib/datashowbase/configurable-tree-table.component';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { BaseID } from '../../lib/entities/base.id';
import { HelpIds } from '../../lib/help/help.ids';
import { AppHelper } from '../../lib/helper/app.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { AlgoHierarchyDto } from '../model/algo-hierarchy-dto';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { AlgoCallParam, AlgoDialogVisible, AlgoStrategyDefinitionForm } from '../model/algo.dialog.visible';
import { AlgoSecurity } from '../model/algo.security';
import { AlgoStrategy } from '../model/algo.strategy';
import { AlgoTop, AlgoTopReadiness } from '../model/algo.top';
import { AlgoTopAssetSecurity } from '../model/algo.top.asset.security';
import { TreeAlgoAssetclass, TreeAlgoSecurity, TreeAlgoStrategy, TreeAlgoTop } from '../model/tree.algo.base';
import { AlgoAssetclassService } from '../service/algo.assetclass.service';
import { AlgoSecurityService } from '../service/algo.security.service';
import { AlgoTopService } from '../service/algo.top.service';
import { SimulationContextService } from '../service/simulation.context.service';
import { AlgoAssetclassAddInstrumentDialogComponent } from './algo-assetclass-add-instrument-dialog.component';
import { AlgoAssetclassEditComponent } from './algo-assetclass-edit.component';
import { AlgoSecurityEditComponent } from './algo-security-edit.component';
import { AlgoStrategyEditComponent } from './algo-strategy-edit.component';
import { AlgoTreeViewBase } from './algo.tree.view.base';
import { StrategyDetailComponent } from './strategy-detail.component';

@Component({
  template: `
    <div
      class="data-container"
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <configurable-tree-table
        [data]="treeNodes() ?? []"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        dataKey="idTree"
        [(selection)]="selectedNode"
        (nodeSelect)="onNodeSelect($event)"
        (nodeUnselect)="onNodeUnselect($event)"
        [showContextMenu]="!hierarchyReadOnly"
        [contextMenuItems]="contextMenuItems()"
        [valueGetterFn]="valueGetter"
        [baseLocale]="baseLocale"
        [canEditCellFn]="canEditCellCallback"
        (cellEditComplete)="onCellEditComplete($event)"
        [checkboxVisibleFn]="isCheckboxVisibleCallback"
        [checkboxDisabledFn]="isCheckboxDisabledCallback"
        (checkboxChange)="onCheckboxChangeHandler($event)"
        (componentClick)="onComponentClick($event)"
        [rowClassFn]="getAlgoRowClassCallback"
        [cellClassFn]="getAlgoCellClassCallback"
        [enableSort]="false">
        <h4 caption>{{ 'ALGO_OVERVIEW' | translate }}</h4>
        <ng-template #iconCell let-row let-value="value">
          @if (getDistributionIcon(row); as iconName) {
            <svg-icon
              [name]="iconName"
              [pTooltip]="'DISTRIBUTION_HEADER_TOOLTIP' | translate"
              tooltipPosition="top"
              [svgStyle]="{ 'width.px': 14, 'height.px': 14 }" />
          } @else {
            <span>{{ value }}</span>
          }
        </ng-template>
      </configurable-tree-table>
      @if (readiness(); as readinessValue) {
        <div class="readinessValue">
          <strong>{{
            (readinessValue.readyForReplay ? 'ALGO_READY_FOR_REPLAY' : 'ALGO_NOT_READY_FOR_REPLAY') | translate
          }}</strong>
          @if (readinessValue.readyForReplay) {
            <span>
              {{
                (readinessValue.readyForRebalancing ? 'ALGO_READY_FOR_REBALANCING' : 'ALGO_NOT_READY_FOR_REBALANCING')
                  | translate
              }}
            </span>
          }
          @if (readinessValue.issues.length > 0) {
            <ul>
              @for (issue of readinessValue.issues; track $index) {
                <li [class.readiness-blocking]="issue.blocking">{{ issue.message }}</li>
              }
            </ul>
          }
        </div>
      }
      <p>{{ 'ALGO_SIMULATION_EXCLUDED_HINT' | translate }}</p>
      @if (algoStrategyShowParamCall().algoStrategy) {
        <strategy-detail [algoStrategyParamCall]="algoStrategyShowParamCall()" />
      }
    </div>
    @if (visibleDialogs()[AlgoDialogVisible.ALGO_ASSETCLASS]) {
      @if (algoCallParam(); as editCallParam) {
        <algo-assetclass-edit
          [visibleDialog]="visibleDialogs()[AlgoDialogVisible.ALGO_ASSETCLASS]"
          [algoCallParam]="editCallParam"
          (closeDialog)="handleCloseAlgoAssetclassDialog($event)" />
      }
    }
    @if (visibleDialogs()[AlgoDialogVisible.ALGO_SECURITY]) {
      @if (algoCallParam(); as editCallParam) {
        <algo-security-edit
          [visibleDialog]="visibleDialogs()[AlgoDialogVisible.ALGO_SECURITY]"
          [algoCallParam]="editCallParam"
          (closeDialog)="handleCloseAlgoAssetclassDialog($event)" />
      }
    }
    @if (visibleDialogs()[AlgoDialogVisible.ALGO_ADD_INSTRUMENT]) {
      <algo-assetclass-add-instrument
        [visibleDialog]="visibleDialogs()[AlgoDialogVisible.ALGO_ADD_INSTRUMENT]"
        [idAlgoAssetclassSecurity]="idAlgoAssetclassAddInstrument"
        (closeDialog)="handleCloseAddInstrumentDialog($event)" />
    }
    @if (visibleDialogs()[AlgoDialogVisible.ALGO_STRATEGY]) {
      @if (algoCallParam(); as editCallParam) {
        <algo-strategy-edit
          [visibleDialog]="visibleDialogs()[AlgoDialogVisible.ALGO_STRATEGY]"
          [algoCallParam]="editCallParam"
          (closeDialog)="handleCloseAlgoAssetclassDialog($event)" />
      }
    }
  `,
  styles: [
    `
      .kb-row {
        font-weight: 700 !important;
      }
      .readiness span {
        margin-left: 1rem;
      }
      .readiness-blocking {
        color: red;
      }
    `
  ],
  imports: [
    AlgoSecurityEditComponent,
    TranslateModule,
    ConfigurableTreeTableComponent,
    StrategyDetailComponent,
    AlgoAssetclassEditComponent,
    AlgoAssetclassAddInstrumentDialogComponent,
    AlgoStrategyEditComponent,
    AngularSvgIconModule,
    TooltipModule
  ]
})
export class AlgoTopDataViewComponent extends AlgoTreeViewBase implements IGlobalMenuAttach, OnInit, OnDestroy {
  readonly id = input.required({ transform: numberAttribute });

  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private algoTopService: AlgoTopService = inject(AlgoTopService);
  private algoAssetclassService: AlgoAssetclassService = inject(AlgoAssetclassService);
  private algoSecurityService: AlgoSecurityService = inject(AlgoSecurityService);
  private dataChangedService: DataChangedService = inject(DataChangedService);
  protected messageToastService: MessageToastService = inject(MessageToastService);
  private confirmationService: ConfirmationService = inject(ConfirmationService);
  private simulationContext: SimulationContextService = inject(SimulationContextService);

  readonly canEditCellCallback = (rowData: any, field: ColumnConfig) => this.canEditCell(rowData, field);
  readonly isCheckboxVisibleCallback = (rowData: any, field: ColumnConfig) => this.isCheckboxVisible(rowData, field);
  readonly isCheckboxDisabledCallback = (rowData: any, field: ColumnConfig) => this.isCheckboxDisabled(rowData, field);
  readonly getAlgoRowClassCallback = (rowNode: any, rowData: any) => this.getAlgoRowClass(rowNode, rowData);
  readonly getAlgoCellClassCallback = (rowData: any, field: ColumnConfig) => this.getAlgoCellClass(rowData, field);
  /** Backend-selected property paths to highlight, keyed by hierarchy node ID. */
  private readonly invalidFields = signal<Record<number, string[]>>({});
  /** Backend-selected property paths to display on a yellow background. */
  private readonly warningFields = signal<Record<number, string[]>>({});
  /** Whether the strategy can be replayed and compared as it stands, refreshed with every hierarchy load. */
  readonly readiness = signal<AlgoTopReadiness | undefined>(undefined);

  // Otherwise enum DialogVisible can't be used in a html template
  AlgoDialogVisible: typeof AlgoDialogVisible = AlgoDialogVisible;

  // For modal dialogs
  readonly visibleDialogs = signal<boolean[]>([]);
  /** The custom category the instrument search dialog adds to. */
  idAlgoAssetclassAddInstrument: number;

  readonly algoCallParam = signal<AlgoCallParam | undefined>(undefined);

  readonly contextMenuItems = signal<MenuItem[]>([]);
  private monitoringSubscription: Subscription;
  private readonly alertEditable = signal(false);
  private readonly savingAlerts = signal(new Set<number>());
  /** True when no create, update or delete action on the hierarchy may be offered. */
  hierarchyReadOnly: boolean;

  readonly hierarchyResource = httpResource<AlgoHierarchyDto>(() => this.algoTopService.getHierarchyRequest(this.id()));
  private notifyNavigationAfterHierarchy = false;
  constructor() {
    super();
    // The strategy hierarchy belongs to the home tenant: inside a simulation environment it is shown but not
    // edited, and a read-only user may not change it either. Resolved once, because entering or leaving an
    // environment reloads the application.
    this.hierarchyReadOnly = this.simulationContext.isInSimulation() || this.gps.isReadOnlyUser();

    const percentageCol = this.addNameAndPercentageColumns();
    percentageCol.cec = {
      inputType: EditInputType.InputNumber,
      min: 0,
      max: 100,
      maxFractionDigits: 2
    };
    this.addTotalDateAndIdColumns(true);
    this.addColumn(DataType.Boolean, 'alertEnabled', 'ALERT_ENABLED', true, false, {
      templateName: 'editableCheck'
    });

    effect(() => {
      const id = this.id();
      untracked(() => {
        if (this.algoTop()?.idAlgoAssetclassSecurity !== id) {
          this.selectedNode.set(null);
          this.onNodeUnselect(null);
          this.algoTop.set(null);
          this.contextMenuItems.set([]);
        }
        this.translateHeadersAndColumns();
        this.notifyNavigationAfterHierarchy = false;
      });
    });

    reportResourceErrors(this.hierarchyResource, this.algoTopService);
    effect(() => {
      if (this.hierarchyResource.isLoading() || !this.hierarchyResource.hasValue()) return;
      const hierarchy = this.hierarchyResource.value();
      untracked(() => {
        const readinessChanged =
          this.readiness() != null &&
          this.algoTop()?.idAlgoAssetclassSecurity === hierarchy.algoTop.idAlgoAssetclassSecurity &&
          JSON.stringify(this.readiness()) !== JSON.stringify(hierarchy.algoTop.readiness);
        this.invalidFields.set(hierarchy.invalidFields);
        this.warningFields.set(hierarchy.warningFields);
        this.readiness.set(hierarchy.algoTop.readiness);
        this.alertEditable.set(hierarchy.alertEditable);
        this.buildTree(hierarchy.algoTop, hierarchy.algoAssetclassList);
        this.refreshSelectedEntity();
        if (this.isActivated()) this.resetMenu();
        const notify = this.notifyNavigationAfterHierarchy || readinessChanged;
        this.notifyNavigationAfterHierarchy = false;
        if (notify)
          this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.UPDATED, new AlgoTop()));
      });
    });
  }

  ngOnInit(): void {
    this.monitoringSubscription = this.dataChangedService.dateChanged$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((change) => {
        if (change.data instanceof Tenant && this.algoTop()) {
          this.readDataWithoutTopLevel();
        }
      });
  }

  readDataWithTopLevel(): void {
    this.readHierarchy(true);
  }

  readDataWithoutTopLevel(): void {
    this.readHierarchy();
  }

  /**
   * Refreshes the hierarchy and warnings together after every edit, deletion or normalization. The navigation tree
   * keeps a copy of each strategy including its readiness and refuses "create simulation" on that copy, so it is
   * refreshed as well whenever an edit below the top level changed the readiness of this strategy.
   */
  private readHierarchy(notifyNavigation = false): void {
    this.notifyNavigationAfterHierarchy ||= notifyNavigation;
    this.hierarchyResource.reload();
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event: PanelClickEvent): void {
    this.resetMenu();
  }

  /**
   * Determines if a specific cell should be editable.
   * Only AlgoTopAssetSecurity descendants (AlgoTop, AlgoAssetclass, AlgoSecurity) have percentage.
   * AlgoStrategy nodes do not have percentage and should not be editable.
   *
   * @param rowData - The row data object from the tree node
   * @param field - The column configuration
   * @returns true if the cell should be editable
   */
  canEditCell(rowData: any, field: ColumnConfig): boolean {
    return !this.hierarchyReadOnly && 'percentage' in rowData;
  }

  /**
   * Handles cell edit completion by persisting the changed value via the appropriate service.
   * On error, rolls back to the original value.
   *
   * @param event - The cell edit event containing rowData, field, originalValue, and newValue
   */
  onCellEditComplete(event: TreeTableCellEditEvent): void {
    if (this.hierarchyReadOnly || event.originalValue === event.newValue) {
      return;
    }
    const rowData = event.rowData;
    if (rowData instanceof AlgoTop) {
      this.algoTopService
        .update(rowData)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => this.readDataWithTopLevel(),
          error: () => this.patchHierarchyRow(rowData, { [event.field.field]: event.originalValue })
        });
    } else if (rowData instanceof AlgoAssetclass) {
      this.algoAssetclassService
        .update(rowData)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => this.readDataWithoutTopLevel(),
          error: () => this.patchHierarchyRow(rowData, { [event.field.field]: event.originalValue })
        });
    } else if (rowData instanceof AlgoSecurity) {
      this.algoSecurityService
        .update(rowData)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => this.readDataWithoutTopLevel(),
          error: () => this.patchHierarchyRow(rowData, { [event.field.field]: event.originalValue })
        });
    }
  }

  /**
   * Highlights fields selected by backend validation without repeating business rules in the client.
   *
   * @param rowData - The row data object
   * @param field - The column configuration
   * @returns CSS class string or null
   */
  getAlgoCellClass(rowData: any, field: ColumnConfig): string | null {
    if (!(rowData instanceof AlgoTopAssetSecurity) || rowData.idAlgoAssetclassSecurity == null) {
      return null;
    }
    const classes: string[] = [];
    if (this.invalidFields()[rowData.idAlgoAssetclassSecurity]?.includes(field.field)) {
      // A missing coupon rate leaves the distribution cell empty, so only a background can mark it.
      classes.push(field.field === AlgoTreeViewBase.DISTRIBUTION_FIELD ? 'cell-value-invalid' : 'algo-value-invalid');
    }
    if (this.warningFields()[rowData.idAlgoAssetclassSecurity]?.includes(field.field)) {
      classes.push('algo-value-warning');
    }
    return classes.join(' ') || null;
  }

  /**
   * Strategies show alert preferences; writable security rows also support batch selection.
   */
  isCheckboxVisible(rowData: any, field: ColumnConfig): boolean {
    if (field.field === 'alertEnabled') {
      return rowData instanceof AlgoStrategy;
    }
    return !this.hierarchyReadOnly && rowData instanceof AlgoSecurity;
  }

  /** Alert preferences stay visible outside the assigned hierarchy, including simulation sessions. */
  isCheckboxDisabled(rowData: any, field: ColumnConfig): boolean {
    return (
      field.field === 'alertEnabled' &&
      (!this.alertEditable() || this.hierarchyReadOnly || this.savingAlerts().has(rowData.idAlgoRuleStrategy))
    );
  }

  /**
   * Persists alert preferences through their dedicated endpoint, or updates local batch selection.
   */
  onCheckboxChangeHandler(event: { rowData: any; field: ColumnConfig; value: boolean }): void {
    if (event.field.field === 'alertEnabled') {
      if (this.isCheckboxDisabled(event.rowData, event.field)) {
        return;
      }
      const strategy: AlgoStrategy = event.rowData;
      const previous = strategy.alertEnabled;
      this.patchHierarchyRow(strategy, { alertEnabled: event.value });
      this.savingAlerts.update((pending) => new Set([...pending, strategy.idAlgoRuleStrategy]));
      this.algoStrategyService
        .setAlertEnabled(strategy.idAlgoRuleStrategy, event.value)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (saved) => {
            this.patchHierarchyRow(strategy, { alertEnabled: saved.alertEnabled });
            this.savingAlerts.update(
              (pending) => new Set([...pending].filter((id) => id !== strategy.idAlgoRuleStrategy))
            );
          },
          error: () => {
            this.patchHierarchyRow(strategy, { alertEnabled: previous });
            this.savingAlerts.update(
              (pending) => new Set([...pending].filter((id) => id !== strategy.idAlgoRuleStrategy))
            );
            this.readDataWithoutTopLevel();
          }
        });
      return;
    }
    this.patchHierarchyRow(event.rowData, { _selected: event.value });
  }

  extendMenuWithAlgoStrategy(
    menuItems: MenuItem[],
    selectedNode: AlgoTop | AlgoAssetclass | AlgoSecurity,
    algoStrategy: AlgoStrategy | null
  ): void {
    const id = selectedNode.idAlgoAssetclassSecurity;
    if (id == null) return;
    menuItems.push({ separator: true });
    const algoStrategyMenuItem: MenuItem = {
      label: 'CREATE|ALGO_STRATEGY',
      command: (e) =>
        this.addEdit(AlgoDialogVisible.ALGO_STRATEGY, selectedNode, algoStrategy, this.algoStrategyDefinitionForm)
    };
    menuItems.push(algoStrategyMenuItem);
    const unused = this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(id);
    if (unused) {
      algoStrategyMenuItem.disabled = unused.length === 0;
    } else {
      algoStrategyMenuItem.disabled = true;
      this.replaceAlgoRequest('unused-' + id).add(
        this.algoStrategyService
          .getUnusedStrategiesForManualAdding(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((algoStrategyImplementations) => {
            this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.set(id, algoStrategyImplementations);
            algoStrategyMenuItem.disabled = algoStrategyImplementations.length === 0;
            if (this.selectedNode()?.data.idAlgoAssetclassSecurity === id) this.resetMenu();
          })
      );
    }
  }

  handleDeleteEntity<T extends BaseID>(entity: T, deleteService: DeleteService): void {
    const id = entity.getId();
    if (id == null) return;
    const entityMsg = AppHelper.toUpperCaseWithUnderscore(entity.constructor.name);
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + entityMsg,
      () => {
        deleteService
          .deleteEntity(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((response) => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
              i18nRecord: AppHelper.toUpperCaseWithUnderscore(entity.constructor.name)
            });
            this.resetMenu();
            this.readDataWithoutTopLevel();
          });
      }
    );
  }

  handleCloseAlgoAssetclassDialog(processedActionData: ProcessedActionData) {
    this.visibleDialogs.set(new Array(this.visibleDialogs().length).fill(false));
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (
        processedActionData.data instanceof AlgoTopAssetSecurity &&
        processedActionData.data.idAlgoAssetclassSecurity != null
      ) {
        this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.delete(processedActionData.data.idAlgoAssetclassSecurity);
      }
      const param = this.algoCallParam();
      const top = this.algoTop();
      if (top && param?.parentObject.getId() === top.idAlgoAssetclassSecurity) {
        this.readDataWithTopLevel();
      } else {
        this.readDataWithoutTopLevel();
      }
      this.openAddInstrumentDialogWhenRequested(processedActionData);
    }
  }

  /**
   * Opens the instrument search after a custom category was saved with "add instruments by search" ticked, so that
   * instruments beyond those of the watchlist can be added to it.
   */
  private openAddInstrumentDialogWhenRequested(processedActionData: ProcessedActionData): void {
    const saved = processedActionData.data;
    const param = this.algoCallParam();
    if (param?.addInstrumentsBySearch && saved?.name != null && saved.idAlgoAssetclassSecurity) {
      this.algoCallParam.set({ ...param, addInstrumentsBySearch: false });
      this.idAlgoAssetclassAddInstrument = saved.idAlgoAssetclassSecurity;
      this.visibleDialogs.update((dialogs) =>
        Object.assign([...dialogs], { [AlgoDialogVisible.ALGO_ADD_INSTRUMENT]: true })
      );
    }
  }

  /** Reloads the hierarchy below the top level, since the search dialog may have added instruments. */
  handleCloseAddInstrumentDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialogs.set(new Array(this.visibleDialogs().length).fill(false));
    this.readDataWithoutTopLevel();
  }

  searchTree(treeNode: TreeNode, idTree: string): TreeNode | null {
    if (treeNode.data.idTree === idTree) {
      return treeNode;
    } else if (treeNode.children != null) {
      let result: TreeNode | null = null;
      for (let i = 0; result == null && i < treeNode.children.length; i++) {
        result = this.searchTree(treeNode.children[i], idTree);
      }
      return result;
    }
    return null;
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_ALGO_TREE;
  }

  getMenuShowOptions(): MenuItem[] | null {
    return null;
  }

  ngOnDestroy(): void {
    this.monitoringSubscription.unsubscribe();
    this.activePanelService.destroyPanel(this);
  }

  private getCheckedSecurities(): AlgoSecurity[] {
    const result: AlgoSecurity[] = [];
    const assetclasses = this.algoTop()?.algoAssetclassList;
    if (assetclasses) {
      for (const ac of assetclasses) {
        if (ac.algoSecurityList) {
          for (const sec of ac.algoSecurityList) {
            if ((sec as any)._selected) {
              result.push(sec);
            }
          }
        }
      }
    }
    return result;
  }

  private handleDeleteSelectedSecurities(): void {
    const ids = this.getCheckedSecurities()
      .map((sec) => sec.idAlgoAssetclassSecurity)
      .filter((id) => id != null);
    if (ids.length === 0) {
      return;
    }
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|SECURITY',
      () => {
        const deleteObs = ids.map((id) => this.algoSecurityService.deleteEntity(id));
        concat(...deleteObs)
          .pipe(toArray())
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe(() => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
              i18nRecord: 'AlgoSecurity'
            });
            this.resetMenu();
            this.readDataWithoutTopLevel();
          });
      }
    );
  }

  private handleNormalizePercentages(idAlgoAssetclassSecurity: number): void {
    this.algoTopService
      .normalizePercentages(idAlgoAssetclassSecurity)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.readDataWithoutTopLevel();
      });
  }

  private handleNormalizeAllPercentages(idAlgoAssetclassSecurity: number): void {
    this.algoTopService
      .normalizeAllPercentages(idAlgoAssetclassSecurity)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.readDataWithoutTopLevel();
      });
  }

  private resetMenu(): void {
    const menu = this.getEditMenu(this.selectedNode());
    this.replaceAlgoRequest('menu').add(
      TranslateHelper.translateMenuItems$(menu, this.translateService)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((items) => {
          this.contextMenuItems.set(items);
          this.activePanelService.activatePanel(this, { showMenu: null, editMenu: items });
        })
    );
  }

  private getEditMenu(selectedNode: TreeNode | null): MenuItem[] {
    const top = this.algoTop();
    const topId = top?.idAlgoAssetclassSecurity;
    if (this.hierarchyReadOnly || !top || topId == null) {
      return [];
    }
    const menuItems: MenuItem[] = [];
    const checkedSecurities = this.getCheckedSecurities();
    if (checkedSecurities.length > 0) {
      menuItems.push({
        label: 'DELETE_SELECTED_SECURITIES',
        command: () => this.handleDeleteSelectedSecurities()
      });
      menuItems.push({ separator: true });
    }
    if (selectedNode instanceof TreeAlgoTop) {
      menuItems.push({
        label: 'ADD_RECORD|ASSETCLASS',
        command: (e) => this.addEdit(AlgoDialogVisible.ALGO_ASSETCLASS, top, null)
      });
      menuItems.push({
        label: 'NORMALIZE_PERCENTAGES',
        command: () => this.handleNormalizePercentages(topId),
        disabled: !top.algoAssetclassList || top.algoAssetclassList.length === 0
      });
      menuItems.push({
        label: 'NORMALIZE_ALL_PERCENTAGES',
        command: () => this.handleNormalizeAllPercentages(topId),
        disabled: !top.algoAssetclassList || top.algoAssetclassList.length === 0
      });
      this.extendMenuWithAlgoStrategy(menuItems, selectedNode.data, null);
    } else if (selectedNode instanceof TreeAlgoAssetclass) {
      const id = selectedNode.data.idAlgoAssetclassSecurity;
      if (id == null) return menuItems;
      menuItems.push({
        label: 'EDIT_RECORD|ASSETCLASS',
        command: (e) => this.addEdit(AlgoDialogVisible.ALGO_ASSETCLASS, top, selectedNode.data)
      });
      menuItems.push({
        label: 'DELETE_RECORD|ASSETCLASS',
        command: (e) => this.handleDeleteEntity(selectedNode.data, this.algoAssetclassService),
        disabled: (selectedNode.children?.length ?? 0) > 0
      });
      menuItems.push({ separator: true });
      menuItems.push({
        label: 'ADD_RECORD|SECURITY',
        command: (e) => this.addEdit(AlgoDialogVisible.ALGO_SECURITY, <AlgoAssetclass>selectedNode.data, null)
      });
      menuItems.push({
        label: 'NORMALIZE_PERCENTAGES',
        command: () => this.handleNormalizePercentages(id),
        disabled: !selectedNode.data.algoSecurityList || selectedNode.data.algoSecurityList.length === 0
      });
      this.extendMenuWithAlgoStrategy(menuItems, selectedNode.data, null);
    } else if (selectedNode instanceof TreeAlgoSecurity) {
      menuItems.push({
        label: 'EDIT_RECORD|SECURITY',
        command: (e) =>
          this.withParent(selectedNode, (parent) =>
            this.addEdit(AlgoDialogVisible.ALGO_SECURITY, parent.data, <AlgoSecurity>selectedNode.data)
          )
      });
      menuItems.push({
        label: 'DELETE_RECORD|SECURITY',
        command: (e) => this.handleDeleteEntity(selectedNode.data, this.algoSecurityService)
      });
      this.extendMenuWithAlgoStrategy(menuItems, selectedNode.data, null);
    } else if (selectedNode instanceof TreeAlgoStrategy) {
      menuItems.push({
        label: 'EDIT',
        command: (e) =>
          this.withParent(selectedNode, (parent) =>
            this.addEdit(
              AlgoDialogVisible.ALGO_STRATEGY,
              parent.data,
              selectedNode.data,
              this.algoStrategyDefinitionForm
            )
          )
      });
      menuItems.push({
        label: 'DELETE_RECORD|ALGO_STRATEGY',
        command: (e) => this.handleDeleteEntity(selectedNode.data, this.algoStrategyService)
      });
    }
    return menuItems;
  }

  /**
   * Runs a menu command with the parent of the selected node. The parent is read when the command runs, not when the
   * menu is built: a reloaded hierarchy rebuilds the menu with new nodes before the tree table has rendered them and
   * assigned their parent.
   *
   * @param node - The selected security or strategy node
   * @param action - The command receiving the parent node
   */
  private withParent(node: TreeNode, action: (parent: TreeNode) => void): void {
    const parent = node.parent;
    if (parent) action(parent);
  }

  private addEdit(
    algoDialogVisible: AlgoDialogVisible,
    parent: AlgoTop | AlgoAssetclass | AlgoSecurity,
    thisObject: AlgoTop | AlgoAssetclass | AlgoSecurity | AlgoStrategy | null,
    algoStrategyDefinitionForm?: AlgoStrategyDefinitionForm
  ): void {
    const top = this.algoTop();
    if (!top) return;
    const idWatchlist =
      algoDialogVisible === AlgoDialogVisible.ALGO_SECURITY &&
      parent instanceof AlgoAssetclass &&
      parent.isCustomCategory()
        ? (top.idWatchlist ?? undefined)
        : undefined;
    const callParam = new AlgoCallParam(parent, thisObject, algoStrategyDefinitionForm, idWatchlist, top.referenceDate);
    this.algoCallParam.set(callParam);
    if (algoDialogVisible === AlgoDialogVisible.ALGO_ASSETCLASS) {
      this.replaceAlgoRequest('edit-dialog').add(
        this.gps
          .getEntityFormDefinition('AlgoAssetclass')
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((definition) => {
            if (
              this.algoCallParam() !== callParam ||
              this.algoTop()?.idAlgoAssetclassSecurity !== top.idAlgoAssetclassSecurity
            )
              return;
            this.algoCallParam.set({ ...callParam, formDefinition: definition });
            this.visibleDialogs.update((dialogs) => Object.assign([...dialogs], { [algoDialogVisible]: true }));
          })
      );
    } else {
      this.visibleDialogs.update((dialogs) => Object.assign([...dialogs], { [algoDialogVisible]: true }));
    }
  }

  private refreshSelectedEntity(): void {
    if (this.selectedNode()) this.onNodeSelect(null);
    else this.onNodeUnselect(null);
  }
}
