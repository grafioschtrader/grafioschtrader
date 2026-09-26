import { AlgoAlertDiagnosticsComponent } from './algo-alert-diagnostics.component';
import { ButtonModule } from '@openng/optimus-ui/button';
import { Component, OnDestroy, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService, MenuItem, TreeNode } from '@openng/optimus-ui/api';
import { TreeTableConfigBase } from '../../lib/datashowbase/tree.table.config.base';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppHelper } from '../../lib/helper/app.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { AlgoSecurity } from '../model/algo.security';
import { AlgoStrategy } from '../model/algo.strategy';
import { AlgoSecurityService } from '../service/algo.security.service';
import { AlgoStrategyService } from '../service/algo.strategy.service';
import { SimulationContextService } from '../service/simulation.context.service';
import { AlgoCallParam, AlgoStrategyDefinitionForm } from '../model/algo.dialog.visible';
import { ConfigurableTreeTableComponent } from '../../lib/datashowbase/configurable-tree-table.component';
import { AlgoStrategyEditComponent } from './algo-strategy-edit.component';
import { AppSettings } from '../../shared/app.settings';

/**
 * Landing page of the rule-based trading root node. Shows the portfolio-independent (standalone) alerts of the tenant
 * in a tree table: each alerted security is a parent row, its alert strategies are the child rows. The live alert of
 * a strategy is switched with its alertEnabled checkbox; strategies are created, edited and deleted via the context
 * menu. Alerts that belong to a strategy hierarchy are not listed here, they are managed in the view of their hierarchy.
 */
@Component({
  selector: 'algo-standalone-alert',
  template: `
    <h4>{{ 'ALGO_OVERVIEW' | translate }}</h4>
    <p-button [label]="'ALERT_DIAGNOSTICS' | translate" (click)="visibleDiagnostics = true" />
    @if (visibleDiagnostics) {
      <algo-alert-diagnostics (closed)="visibleDiagnostics = false" />
    }
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      [ngClass]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <configurable-tree-table
        [data]="treeNodes"
        [fields]="fields"
        dataKey="nodeKey"
        [(selection)]="selectedNode"
        (nodeSelect)="onRowSelect($event)"
        (nodeUnselect)="onRowUnselect($event)"
        [contextMenuItems]="contextMenuItems"
        [contextMenuAppendTo]="'body'"
        [showContextMenu]="isActivated() && canWrite()"
        [valueGetterFn]="getValueByPath.bind(this)"
        [checkboxVisibleFn]="isCheckboxVisible.bind(this)"
        [checkboxDisabledFn]="isCheckboxDisabled.bind(this)"
        (checkboxChange)="onCheckboxChange($event)"
        (componentClick)="onComponentClick($event)">
      </configurable-tree-table>
    </div>

    @if (visibleStrategyDialog) {
      <algo-strategy-edit
        [visibleDialog]="visibleStrategyDialog"
        [algoCallParam]="algoCallParam"
        (closeDialog)="onStrategyDialogClose($event)">
      </algo-strategy-edit>
    }
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    ButtonModule,
    AlgoAlertDiagnosticsComponent,
    CommonModule,
    TranslateModule,
    ConfigurableTreeTableComponent,
    AlgoStrategyEditComponent
  ]
})
export class AlgoStandaloneAlertComponent extends TreeTableConfigBase implements OnInit, OnDestroy, IGlobalMenuAttach {
  treeNodes: TreeNode[] = [];
  selectedNode: TreeNode | null = null;
  contextMenuItems: MenuItem[] = [];

  visibleDiagnostics = false;
  visibleStrategyDialog = false;
  algoCallParam: AlgoCallParam;
  algoStrategyDefinitionForm = new AlgoStrategyDefinitionForm();

  private algoSecurities: AlgoSecurity[] = [];
  /** Strategies whose alert preference is being saved; their checkbox stays disabled until the response arrives. */
  private savingAlerts = new Set<number>();

  constructor(
    private activePanelService: ActivePanelService,
    private algoSecurityService: AlgoSecurityService,
    private algoStrategyService: AlgoStrategyService,
    private simulationContext: SimulationContextService,
    private messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(translateService, gps);

    this.addColumn(DataType.String, 'name', 'NAME', true, false, {
      fieldValueFN: this.getNodeName.bind(this)
    });
    this.addColumn(DataType.String, 'algoStrategyImplementations', 'ALGO_STRATEGY_NAME', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.Boolean, 'alertEnabled', true, false, { templateName: 'editableCheck' });
  }

  ngOnInit(): void {
    this.loadData();
  }

  // ============================================================================
  // IGlobalMenuAttach Implementation
  // ============================================================================

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_ALGO_ALERT;
  }

  /** Alerts are changed only by a writing user of the main tenant; the backend refuses everything else. */
  canWrite(): boolean {
    return !this.gps.isReadOnlyUser() && !this.simulationContext.isInSimulation();
  }

  // ============================================================================
  // Data Loading
  // ============================================================================

  private loadData(): void {
    this.algoSecurityService.getAllForTenant().subscribe((algoSecurities: AlgoSecurity[]) => {
      this.algoSecurities = algoSecurities;
      this.buildTree();
      this.prepareTreeTableAndTranslate();
      this.createTranslateValuesStoreForTranslation(this.treeNodes);
    });
  }

  private buildTree(): void {
    this.treeNodes = this.algoSecurities.map((as) => {
      (as as any).nodeKey = 'as' + as.idAlgoAssetclassSecurity;
      const strategyChildren: TreeNode[] = (as.algoStrategyList || []).map((strategy) => {
        (strategy as any).nodeKey = 'rs' + strategy.idAlgoRuleStrategy;
        return { data: strategy, leaf: true };
      });
      return {
        data: as,
        children: strategyChildren,
        expanded: true
      } as TreeNode;
    });
  }

  private getNodeName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return dataobject.security ? dataobject.security.name + ', ' + dataobject.security.currency : '';
  }

  // ============================================================================
  // Alert preference checkbox
  // ============================================================================

  private isStrategyRow(rowData: any): boolean {
    return rowData.algoStrategyImplementations !== undefined && rowData.algoStrategyImplementations !== null;
  }

  /** Only strategy rows carry the alert preference; security rows show no checkbox. */
  isCheckboxVisible(rowData: any, field: ColumnConfig): boolean {
    return this.isStrategyRow(rowData);
  }

  isCheckboxDisabled(rowData: any, field: ColumnConfig): boolean {
    return !this.canWrite() || this.savingAlerts.has(rowData.idAlgoRuleStrategy);
  }

  /** Persists the alert preference through its dedicated endpoint and rolls back when the backend refuses it. */
  onCheckboxChange(event: { rowData: any; field: ColumnConfig; value: boolean }): void {
    const { rowData, value } = event;
    if (!this.isStrategyRow(rowData) || this.isCheckboxDisabled(rowData, event.field)) {
      return;
    }
    const strategy: AlgoStrategy = rowData;
    const previous = strategy.alertEnabled;
    strategy.alertEnabled = value;
    this.savingAlerts.add(strategy.idAlgoRuleStrategy);
    this.algoStrategyService.setAlertEnabled(strategy.idAlgoRuleStrategy, value).subscribe({
      next: (saved) => {
        strategy.alertEnabled = saved.alertEnabled;
        this.savingAlerts.delete(strategy.idAlgoRuleStrategy);
      },
      error: () => {
        strategy.alertEnabled = previous;
        this.savingAlerts.delete(strategy.idAlgoRuleStrategy);
      }
    });
  }

  // ============================================================================
  // Context Menu
  // ============================================================================

  onComponentClick(event: any): void {
    this.resetMenu();
  }

  onRowSelect(event: any): void {
    this.resetMenu();
  }

  onRowUnselect(event: any): void {
    this.selectedNode = null;
    this.resetMenu();
  }

  private resetMenu(): void {
    this.contextMenuItems = this.getEditMenu();
    this.activePanelService.activatePanel(this, {
      showMenu: null,
      editMenu: this.contextMenuItems
    });
  }

  private getEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (!this.selectedNode || !this.canWrite()) {
      return menuItems;
    }
    const rowData = this.selectedNode.data;
    if (rowData.security) {
      this.addStrategyCreateMenu(menuItems, rowData as AlgoSecurity);
      menuItems.push({
        label: 'DELETE_RECORD|ALGO_SECURITY',
        command: () => this.handleDeleteSecurity(rowData as AlgoSecurity)
      });
    } else if (this.isStrategyRow(rowData)) {
      menuItems.push({
        label: 'EDIT_RECORD|ALGO_STRATEGY',
        command: () => this.handleEditStrategy(rowData as AlgoStrategy)
      });
      menuItems.push({
        label: 'DELETE_RECORD|ALGO_STRATEGY',
        command: () => this.handleDeleteStrategy(rowData as AlgoStrategy)
      });
    }
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  private addStrategyCreateMenu(menuItems: MenuItem[], algoSecurity: AlgoSecurity): void {
    const createItem: MenuItem = {
      label: 'CREATE|ALGO_STRATEGY',
      command: () => this.handleAddStrategy(algoSecurity)
    };
    menuItems.push(createItem);

    if (this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.has(algoSecurity.idAlgoAssetclassSecurity)) {
      createItem.disabled =
        this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(algoSecurity.idAlgoAssetclassSecurity).length === 0;
    } else {
      this.algoStrategyService
        .getUnusedStrategiesForManualAdding(algoSecurity.idAlgoAssetclassSecurity)
        .subscribe((unused) => {
          this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.set(algoSecurity.idAlgoAssetclassSecurity, unused);
          createItem.disabled = unused.length === 0;
        });
    }
  }

  // ============================================================================
  // CRUD Handlers
  // ============================================================================

  private handleAddStrategy(algoSecurity: AlgoSecurity): void {
    this.algoCallParam = new AlgoCallParam(algoSecurity, null, this.algoStrategyDefinitionForm);
    this.visibleStrategyDialog = true;
  }

  private handleEditStrategy(algoStrategy: AlgoStrategy): void {
    const parentSecurity = this.findParentSecurity(algoStrategy);
    if (parentSecurity) {
      this.algoCallParam = new AlgoCallParam(parentSecurity, algoStrategy, this.algoStrategyDefinitionForm);
      this.visibleStrategyDialog = true;
    }
  }

  private handleDeleteStrategy(algoStrategy: AlgoStrategy): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|ALGO_STRATEGY',
      () => {
        this.algoStrategyService.deleteEntity(algoStrategy.idAlgoRuleStrategy).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
            i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.ALGO_STRATEGY)
          });
          this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.delete(algoStrategy.idAlgoAssetclassSecurity);
          this.loadData();
        });
      }
    );
  }

  private handleDeleteSecurity(algoSecurity: AlgoSecurity): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|ALGO_SECURITY',
      () => {
        this.algoSecurityService.deleteEntity(algoSecurity.idAlgoAssetclassSecurity).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
            i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.ALGO_SECURITY)
          });
          this.loadData();
        });
      }
    );
  }

  onStrategyDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleStrategyDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.clear();
      this.loadData();
    }
  }

  private findParentSecurity(algoStrategy: AlgoStrategy): AlgoSecurity | null {
    return (
      this.algoSecurities.find((as) => as.idAlgoAssetclassSecurity === algoStrategy.idAlgoAssetclassSecurity) || null
    );
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }
}
