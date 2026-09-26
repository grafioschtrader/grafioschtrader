import { Component, OnDestroy, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { AlgoTreeViewBase } from './algo.tree.view.base';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { SimulationContextService } from '../service/simulation.context.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ConfirmationService, MenuItem, TreeNode } from '@openng/optimus-ui/api';
import { concat, Subscription } from 'rxjs';
import { toArray } from 'rxjs/operators';
import { ActivatedRoute, Params } from '@angular/router';
import { AlgoTop, AlgoTopReadiness } from '../model/algo.top';
import { AlgoAssetclassService } from '../service/algo.assetclass.service';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { AppHelper } from '../../lib/helper/app.helper';
import { ColumnConfig, EditInputType } from '../../lib/datashowbase/column.config';
import { AlgoTopAssetSecurity } from '../model/algo.top.asset.security';
import { AlgoStrategy } from '../model/algo.strategy';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { HelpIds } from '../../lib/help/help.ids';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { AlgoSecurity } from '../model/algo.security';

import { AlgoCallParam, AlgoDialogVisible, AlgoStrategyDefinitionForm } from '../model/algo.dialog.visible';
import { ProcessedAction } from '../../lib/types/processed.action';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { BaseID } from '../../lib/entities/base.id';
import { AlgoSecurityService } from '../service/algo.security.service';
import { AlgoStrategyService } from '../service/algo.strategy.service';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AlgoTopService } from '../service/algo.top.service';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { TreeAlgoAssetclass, TreeAlgoSecurity, TreeAlgoStrategy, TreeAlgoTop } from '../model/tree.algo.base';
import { AlgoSecurityEditComponent } from './algo-security-edit.component';
import { Tenant } from '../../entities/tenant';

/**
 * Shows algorithmic trading tree with its strategies.
 * Supports inline editing of the percentage column for AlgoTop, AlgoAssetclass, and AlgoSecurity nodes.
 */
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { StrategyDetailComponent } from './strategy-detail.component';
import { AlgoAssetclassEditComponent } from './algo-assetclass-edit.component';
import { AlgoAssetclassAddInstrumentComponent } from './algo-assetclass-add-instrument.component';
import { AlgoStrategyEditComponent } from './algo-strategy-edit.component';
import {
  ConfigurableTreeTableComponent,
  TreeTableCellEditEvent
} from '../../lib/datashowbase/configurable-tree-table.component';

@Component({
  template: `
    <div
      class="data-container"
      [ngClass]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <configurable-tree-table
        [data]="treeNodes"
        [fields]="fields"
        dataKey="idTree"
        [(selection)]="selectedNode"
        (nodeSelect)="onNodeSelect($event)"
        (nodeUnselect)="onNodeUnselect($event)"
        [showContextMenu]="!hierarchyReadOnly"
        [contextMenuItems]="contextMenuItems"
        [valueGetterFn]="getValueByPath.bind(this)"
        [baseLocale]="baseLocale"
        [canEditCellFn]="canEditCell.bind(this)"
        (cellEditComplete)="onCellEditComplete($event)"
        [checkboxVisibleFn]="isCheckboxVisible.bind(this)"
        [checkboxDisabledFn]="isCheckboxDisabled.bind(this)"
        (checkboxChange)="onCheckboxChangeHandler($event)"
        (componentClick)="onComponentClick($event)"
        [rowClassFn]="getAlgoRowClass.bind(this)"
        [cellClassFn]="getAlgoCellClass.bind(this)"
        [enableSort]="false">
        <h4 caption>{{ 'ALGO_OVERVIEW' | translate }}</h4>
      </configurable-tree-table>
      @if (readiness) {
        <div class="readiness">
          <strong>{{
            (readiness.readyForReplay ? 'ALGO_READY_FOR_REPLAY' : 'ALGO_NOT_READY_FOR_REPLAY') | translate
          }}</strong>
          @if (readiness.readyForReplay) {
            <span>
              {{
                (readiness.readyForRebalancing ? 'ALGO_READY_FOR_REBALANCING' : 'ALGO_NOT_READY_FOR_REBALANCING')
                  | translate
              }}
            </span>
          }
          @if (readiness.issues.length > 0) {
            <ul>
              @for (issue of readiness.issues; track $index) {
                <li [class.readiness-blocking]="issue.blocking">{{ issue.message }}</li>
              }
            </ul>
          }
        </div>
      }
      <p>{{ 'ALGO_SIMULATION_EXCLUDED_HINT' | translate }}</p>
      @if (algoStrategyShowParamCall.algoStrategy) {
        <strategy-detail [algoStrategyParamCall]="algoStrategyShowParamCall"> </strategy-detail>
      }
    </div>
    @if (visibleDialogs[AlgoDialogVisible.ALGO_ASSETCLASS]) {
      <algo-assetclass-edit
        [visibleDialog]="visibleDialogs[AlgoDialogVisible.ALGO_ASSETCLASS]"
        [algoCallParam]="algoCallParam"
        (closeDialog)="handleCloseAlgoAssetclassDialog($event)">
      </algo-assetclass-edit>
    }
    @if (visibleDialogs[AlgoDialogVisible.ALGO_SECURITY]) {
      <algo-security-edit
        [visibleDialog]="visibleDialogs[AlgoDialogVisible.ALGO_SECURITY]"
        [algoCallParam]="algoCallParam"
        (closeDialog)="handleCloseAlgoAssetclassDialog($event)">
      </algo-security-edit>
    }
    @if (visibleDialogs[AlgoDialogVisible.ALGO_ADD_INSTRUMENT]) {
      <algo-assetclass-add-instrument
        [visibleDialog]="visibleDialogs[AlgoDialogVisible.ALGO_ADD_INSTRUMENT]"
        [idAlgoAssetclassSecurity]="idAlgoAssetclassAddInstrument"
        (closeDialog)="handleCloseAddInstrumentDialog($event)">
      </algo-assetclass-add-instrument>
    }
    @if (visibleDialogs[AlgoDialogVisible.ALGO_STRATEGY]) {
      <algo-strategy-edit
        [visibleDialog]="visibleDialogs[AlgoDialogVisible.ALGO_STRATEGY]"
        [algoCallParam]="algoCallParam"
        (closeDialog)="handleCloseAlgoAssetclassDialog($event)">
      </algo-strategy-edit>
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
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    AlgoSecurityEditComponent,
    CommonModule,
    TranslateModule,
    ConfigurableTreeTableComponent,
    StrategyDetailComponent,
    AlgoAssetclassEditComponent,
    AlgoAssetclassAddInstrumentComponent,
    AlgoStrategyEditComponent
  ]
})
export class AlgoTopDataViewComponent extends AlgoTreeViewBase implements IGlobalMenuAttach, OnInit, OnDestroy {
  /** Backend-selected property paths to highlight, keyed by hierarchy node ID. */
  private invalidFields: Record<number, string[]> = {};
  /** Backend-selected property paths to display on a yellow background. */
  private warningFields: Record<number, string[]> = {};
  /** Whether the strategy can be replayed and compared as it stands, refreshed with every hierarchy load. */
  readiness: AlgoTopReadiness;

  // Otherwise enum DialogVisible can't be used in a html template
  AlgoDialogVisible: typeof AlgoDialogVisible = AlgoDialogVisible;

  // For modal dialogs
  visibleDialogs: boolean[] = [];
  /** The custom category the instrument search dialog adds to. */
  idAlgoAssetclassAddInstrument: number;

  algoCallParam: AlgoCallParam;

  contextMenuItems: MenuItem[] = [];
  private routeSubscribe: Subscription;
  private monitoringSubscription: Subscription;
  private alertEditable = false;
  private savingAlerts = new Set<number>();
  /** True when no create, update or delete action on the hierarchy may be offered. */
  hierarchyReadOnly: boolean;

  constructor(
    private activatedRoute: ActivatedRoute,
    private activePanelService: ActivePanelService,
    private algoTopService: AlgoTopService,
    private algoAssetclassService: AlgoAssetclassService,
    private algoSecurityService: AlgoSecurityService,
    algoStrategyService: AlgoStrategyService,
    private dataChangedService: DataChangedService,
    protected messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    private simulationContext: SimulationContextService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(algoStrategyService, translateService, gps);
    // The strategy hierarchy belongs to the home tenant: inside a simulation environment it is shown but not
    // edited, and a read-only user may not change it either. Resolved once, because entering or leaving an
    // environment reloads the application.
    this.hierarchyReadOnly = this.simulationContext.isInSimulation() || gps.isReadOnlyUser();

    const percentageCol = this.addNameAndPercentageColumns();
    percentageCol.cec = {
      inputType: EditInputType.InputNumber,
      min: 0,
      max: 100,
      maxFractionDigits: 2
    };
    this.addTotalDateAndIdColumns();
    this.addColumn(DataType.Boolean, 'alertEnabled', 'ALERT_ENABLED', true, false, {
      templateName: 'editableCheck'
    });
  }

  ngOnInit(): void {
    this.monitoringSubscription = this.dataChangedService.dateChanged$.subscribe((change) => {
      if (change.data instanceof Tenant && this.algoTop) {
        this.readDataWithoutTopLevel();
      }
    });
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      const id = +params['id'];
      this.translateHeadersAndColumns();
      this.readHierarchy(id);
    });
  }

  readDataWithTopLevel(): void {
    this.readHierarchy(this.algoTop.idAlgoAssetclassSecurity, true);
  }

  readDataWithoutTopLevel(): void {
    this.readHierarchy(this.algoTop.idAlgoAssetclassSecurity);
  }

  /** Refreshes the hierarchy and warnings together after every edit, deletion or normalization. */
  private readHierarchy(idAlgoTop: number, notifyNavigation = false): void {
    this.algoTopService.getHierarchy(idAlgoTop).subscribe((hierarchy) => {
      this.invalidFields = hierarchy.invalidFields;
      this.warningFields = hierarchy.warningFields;
      this.readiness = hierarchy.algoTop.readiness;
      this.alertEditable = hierarchy.alertEditable;
      this.buildTree(hierarchy.algoTop, hierarchy.algoAssetclassList);
      this.refreshSelectedEntity();
      if (notifyNavigation) {
        this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.UPDATED, new AlgoTop()));
      }
    });
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
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
      this.algoTopService.update(rowData).subscribe({
        next: () => this.readDataWithTopLevel(),
        error: () => (rowData[event.field.field] = event.originalValue)
      });
    } else if (rowData instanceof AlgoAssetclass) {
      this.algoAssetclassService.update(rowData).subscribe({
        next: () => this.readDataWithoutTopLevel(),
        error: () => (rowData[event.field.field] = event.originalValue)
      });
    } else if (rowData instanceof AlgoSecurity) {
      this.algoSecurityService.update(rowData).subscribe({
        next: () => this.readDataWithoutTopLevel(),
        error: () => (rowData[event.field.field] = event.originalValue)
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
    if (!(rowData instanceof AlgoTopAssetSecurity)) {
      return null;
    }
    const classes: string[] = [];
    if (this.invalidFields[rowData.idAlgoAssetclassSecurity]?.includes(field.field)) {
      classes.push('algo-value-invalid');
    }
    if (this.warningFields[rowData.idAlgoAssetclassSecurity]?.includes(field.field)) {
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
      (!this.alertEditable || this.hierarchyReadOnly || this.savingAlerts.has(rowData.idAlgoRuleStrategy))
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
      strategy.alertEnabled = event.value;
      this.savingAlerts.add(strategy.idAlgoRuleStrategy);
      this.algoStrategyService.setAlertEnabled(strategy.idAlgoRuleStrategy, event.value).subscribe({
        next: (saved) => {
          strategy.alertEnabled = saved.alertEnabled;
          this.savingAlerts.delete(strategy.idAlgoRuleStrategy);
        },
        error: () => {
          strategy.alertEnabled = previous;
          this.savingAlerts.delete(strategy.idAlgoRuleStrategy);
          this.readDataWithoutTopLevel();
        }
      });
      return;
    }
    event.rowData._selected = event.value;
  }

  extendMenuWithAlgoStrategy(
    menuItems: MenuItem[],
    selectedNode: AlgoTop | AlgoAssetclass | AlgoSecurity,
    algoStrategy: AlgoStrategy
  ): void {
    menuItems.push({ separator: true });
    const algoStrategyMenuItem: MenuItem = {
      label: 'CREATE|ALGO_STRATEGY',
      command: (e) =>
        this.addEdit(AlgoDialogVisible.ALGO_STRATEGY, selectedNode, algoStrategy, this.algoStrategyDefinitionForm)
    };
    menuItems.push(algoStrategyMenuItem);
    if (this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.has(selectedNode.idAlgoAssetclassSecurity)) {
      algoStrategyMenuItem.disabled =
        this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(selectedNode.idAlgoAssetclassSecurity).length === 0;
    } else {
      this.algoStrategyService
        .getUnusedStrategiesForManualAdding(selectedNode.idAlgoAssetclassSecurity)
        .subscribe((algoStrategyImplementations) => {
          this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.set(
            selectedNode.idAlgoAssetclassSecurity,
            algoStrategyImplementations
          );
          algoStrategyMenuItem.disabled =
            this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(selectedNode.idAlgoAssetclassSecurity).length ===
            0;
        });
    }
  }

  handleDeleteEntity<T extends BaseID>(entity: T, deleteService: DeleteService): void {
    const entityMsg = AppHelper.toUpperCaseWithUnderscore(entity.constructor.name);
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + entityMsg,
      () => {
        deleteService.deleteEntity(entity.getId()).subscribe((response) => {
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
    this.visibleDialogs = new Array(this.visibleDialogs.length).fill(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.data instanceof AlgoTopAssetSecurity) {
        this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.delete(processedActionData.data.idAlgoAssetclassSecurity);
      }
      if (this.algoCallParam.parentObject.getId() === this.algoTop.idAlgoAssetclassSecurity) {
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
    if (this.algoCallParam.addInstrumentsBySearch && saved?.name != null && saved.idAlgoAssetclassSecurity) {
      this.algoCallParam.addInstrumentsBySearch = false;
      this.idAlgoAssetclassAddInstrument = saved.idAlgoAssetclassSecurity;
      this.visibleDialogs[AlgoDialogVisible.ALGO_ADD_INSTRUMENT] = true;
    }
  }

  /** Reloads the hierarchy below the top level, since the search dialog may have added instruments. */
  handleCloseAddInstrumentDialog(processedActionData: ProcessedActionData): void {
    this.visibleDialogs = new Array(this.visibleDialogs.length).fill(false);
    this.readDataWithoutTopLevel();
  }

  searchTree(treeNode: TreeNode, idTree: string): TreeNode {
    if (treeNode.data.idTree === idTree) {
      return treeNode;
    } else if (treeNode.children != null) {
      let result = null;
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

  getMenuShowOptions(): MenuItem[] {
    return null;
  }

  ngOnDestroy(): void {
    this.routeSubscribe.unsubscribe();
    this.monitoringSubscription.unsubscribe();
  }

  private getCheckedSecurities(): AlgoSecurity[] {
    const result: AlgoSecurity[] = [];
    if (this.algoTop?.algoAssetclassList) {
      for (const ac of this.algoTop.algoAssetclassList) {
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
    const checked = this.getCheckedSecurities();
    if (checked.length === 0) {
      return;
    }
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|SECURITY',
      () => {
        const deleteObs = checked.map((sec) => this.algoSecurityService.deleteEntity(sec.idAlgoAssetclassSecurity));
        concat(...deleteObs)
          .pipe(toArray())
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
    this.algoTopService.normalizePercentages(idAlgoAssetclassSecurity).subscribe(() => {
      this.readDataWithoutTopLevel();
    });
  }

  private handleNormalizeAllPercentages(idAlgoAssetclassSecurity: number): void {
    this.algoTopService.normalizeAllPercentages(idAlgoAssetclassSecurity).subscribe(() => {
      this.readDataWithoutTopLevel();
    });
  }

  private resetMenu(): void {
    this.contextMenuItems = this.getEditMenu(this.selectedNode);
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions(),
      editMenu: this.contextMenuItems
    });
  }

  private getEditMenu(selectedNode: TreeNode): MenuItem[] {
    if (this.hierarchyReadOnly) {
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
        command: (e) => this.addEdit(AlgoDialogVisible.ALGO_ASSETCLASS, this.algoTop, null)
      });
      menuItems.push({
        label: 'NORMALIZE_PERCENTAGES',
        command: () => this.handleNormalizePercentages(this.algoTop.idAlgoAssetclassSecurity),
        disabled: !this.algoTop.algoAssetclassList || this.algoTop.algoAssetclassList.length === 0
      });
      menuItems.push({
        label: 'NORMALIZE_ALL_PERCENTAGES',
        command: () => this.handleNormalizeAllPercentages(this.algoTop.idAlgoAssetclassSecurity),
        disabled: !this.algoTop.algoAssetclassList || this.algoTop.algoAssetclassList.length === 0
      });
      this.extendMenuWithAlgoStrategy(menuItems, selectedNode.data, null);
    } else if (selectedNode instanceof TreeAlgoAssetclass) {
      menuItems.push({
        label: 'EDIT_RECORD|ASSETCLASS',
        command: (e) => this.addEdit(AlgoDialogVisible.ALGO_ASSETCLASS, this.algoTop, selectedNode.data)
      });
      menuItems.push({
        label: 'DELETE_RECORD|ASSETCLASS',
        command: (e) => this.handleDeleteEntity(selectedNode.data, this.algoAssetclassService),
        disabled: selectedNode.children.length > 0
      });
      menuItems.push({ separator: true });
      menuItems.push({
        label: 'ADD_RECORD|SECURITY',
        command: (e) => this.addEdit(AlgoDialogVisible.ALGO_SECURITY, <AlgoAssetclass>selectedNode.data, null)
      });
      menuItems.push({
        label: 'NORMALIZE_PERCENTAGES',
        command: () => this.handleNormalizePercentages(selectedNode.data.idAlgoAssetclassSecurity),
        disabled: !selectedNode.data.algoSecurityList || selectedNode.data.algoSecurityList.length === 0
      });
      this.extendMenuWithAlgoStrategy(menuItems, selectedNode.data, null);
    } else if (selectedNode instanceof TreeAlgoSecurity) {
      menuItems.push({
        label: 'EDIT_RECORD|SECURITY',
        command: (e) =>
          this.addEdit(
            AlgoDialogVisible.ALGO_SECURITY,
            (<TreeNode>selectedNode).parent.data,
            <AlgoSecurity>selectedNode.data
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
          this.addEdit(
            AlgoDialogVisible.ALGO_STRATEGY,
            (<TreeNode>selectedNode).parent.data,
            selectedNode.data,
            this.algoStrategyDefinitionForm
          )
      });
      menuItems.push({
        label: 'DELETE_RECORD|ALGO_STRATEGY',
        command: (e) => this.handleDeleteEntity(selectedNode.data, this.algoStrategyService)
      });
    }
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  private addEdit(
    algoDialogVisible: AlgoDialogVisible,
    parent: AlgoTop | AlgoAssetclass | AlgoSecurity,
    thisObject: AlgoTop | AlgoAssetclass | AlgoSecurity | AlgoStrategy,
    algoStrategyDefinitionForm?: AlgoStrategyDefinitionForm
  ): void {
    const idWatchlist =
      algoDialogVisible === AlgoDialogVisible.ALGO_SECURITY &&
      parent instanceof AlgoAssetclass &&
      parent.isCustomCategory()
        ? this.algoTop.idWatchlist
        : undefined;
    this.algoCallParam = new AlgoCallParam(
      parent,
      thisObject,
      algoStrategyDefinitionForm,
      idWatchlist,
      this.algoTop.referenceDate
    );
    if (algoDialogVisible === AlgoDialogVisible.ALGO_ASSETCLASS) {
      this.gps.getEntityFormDefinition('AlgoAssetclass').subscribe((definition) => {
        this.algoCallParam.formDefinition = definition;
        this.visibleDialogs[algoDialogVisible] = true;
      });
    } else {
      this.visibleDialogs[algoDialogVisible] = true;
    }
  }

  private refreshSelectedEntity(): void {
    if (this.selectedNode) {
      this.selectedNode = this.searchTree(this.treeNodes[0], this.selectedNode.data.idTree);
      setTimeout(() => this.onNodeSelect(null));
    }
  }
}
