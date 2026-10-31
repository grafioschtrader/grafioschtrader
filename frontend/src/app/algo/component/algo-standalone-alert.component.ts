import { DestroyRef, inject, signal, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AlgoAlertDiagnosticsDialogComponent } from './algo-alert-diagnostics-dialog.component';
import { ButtonModule } from '@openng/optimus-ui/button';

import { TranslateModule } from '@ngx-translate/core';
import { ConfirmationService, MenuItem, TreeNode } from '@openng/optimus-ui/api';
import { TreeTableConfigBase } from '../../lib/datashowbase/tree.table.config.base';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
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
import { AlgoHierarchyAlertOverviewComponent } from './algo-hierarchy-alert-overview.component';

/**
 * Landing page of the rule-based trading root node. Shows the portfolio-independent (standalone) alerts of the tenant
 * in a tree table: each alerted security is a parent row, its alert strategies are the child rows. The live alert of
 * a strategy is switched with its alertEnabled checkbox; strategies are created, edited and deleted via the context
 * menu. Below them a read-only tree lists the alerts of the strategy hierarchies, marking those of every hierarchy
 * that is not assigned to monitoring as dormant; these alerts are managed in the view of their hierarchy. That tree is
 * left out inside a simulation environment, where no live alert is evaluated.
 */
@Component({
  selector: 'algo-standalone-alert',
  template: `
    <h4>{{ 'ALGO_OVERVIEW' | translate }}</h4>
    <p-button [label]="'ALERT_DIAGNOSTICS' | translate" (click)="visibleDiagnostics.set(true)" />
    @if (visibleDiagnostics()) {
      <algo-alert-diagnostics (closed)="visibleDiagnostics.set(false)" />
    }
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <configurable-tree-table
        [data]="treeNodes()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        dataKey="nodeKey"
        [(selection)]="selectedNode"
        (nodeSelect)="onRowSelect($event)"
        (nodeUnselect)="onRowUnselect($event)"
        [contextMenuItems]="contextMenuItems()"
        [contextMenuAppendTo]="'body'"
        [showContextMenu]="isActivated() && canWrite()"
        [valueGetterFn]="valueGetter"
        [checkboxVisibleFn]="isCheckboxVisibleCallback"
        [checkboxDisabledFn]="isCheckboxDisabledCallback"
        (checkboxChange)="onCheckboxChange($event)"
        (componentClick)="onComponentClick($event)" />
      @if (!simulationContext.isInSimulation()) {
        <algo-hierarchy-alert-overview />
      }
    </div>

    @if (visibleStrategyDialog()) {
      @if (algoCallParam(); as strategyCallParam) {
        <algo-strategy-edit
          [visibleDialog]="visibleStrategyDialog()"
          [algoCallParam]="strategyCallParam"
          (closeDialog)="onStrategyDialogClose($event)" />
      }
    }
  `,
  imports: [
    ButtonModule,
    AlgoAlertDiagnosticsDialogComponent,
    TranslateModule,
    ConfigurableTreeTableComponent,
    AlgoStrategyEditComponent,
    AlgoHierarchyAlertOverviewComponent
  ]
})
export class AlgoStandaloneAlertComponent extends TreeTableConfigBase implements OnInit, OnDestroy, IGlobalMenuAttach {
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private algoSecurityService: AlgoSecurityService = inject(AlgoSecurityService);
  private algoStrategyService: AlgoStrategyService = inject(AlgoStrategyService);
  protected simulationContext: SimulationContextService = inject(SimulationContextService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private confirmationService: ConfirmationService = inject(ConfirmationService);

  private readonly pendingReads = new Map<string, Subscription>();
  private readonly cleanupReads = inject(DestroyRef).onDestroy(() =>
    this.pendingReads.forEach((request) => request.unsubscribe())
  );
  private replaceAlgoRequest(key: string): Subscription {
    this.pendingReads.get(key)?.unsubscribe();
    const request = new Subscription();
    this.pendingReads.set(key, request);
    return request;
  }

  readonly isCheckboxVisibleCallback = (rowData: any, field: ColumnConfig) => this.isCheckboxVisible(rowData, field);
  readonly isCheckboxDisabledCallback = (rowData: any, field: ColumnConfig) => this.isCheckboxDisabled(rowData, field);
  protected readonly destroyRef = inject(DestroyRef);
  readonly treeNodes = signal<TreeNode[]>([]);
  readonly selectedNode = signal<TreeNode | null>(null);
  readonly contextMenuItems = signal<MenuItem[]>([]);

  readonly visibleDiagnostics = signal(false);
  readonly visibleStrategyDialog = signal(false);
  readonly algoCallParam = signal<AlgoCallParam | undefined>(undefined);
  algoStrategyDefinitionForm = new AlgoStrategyDefinitionForm();

  private algoSecurities: AlgoSecurity[] = [];
  /** Strategies whose alert preference is being saved; their checkbox stays disabled until the response arrives. */
  private readonly savingAlerts = signal(new Set<number>());

  constructor() {
    super();

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
    this.replaceAlgoRequest('alerts').add(
      this.algoSecurityService
        .getAllForTenant()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((algoSecurities: AlgoSecurity[]) => {
          this.algoSecurities = algoSecurities;
          this.buildTree();
          this.prepareTreeTableAndTranslate();
          this.createTranslateValuesStoreForTranslation(this.treeNodes());
        })
    );
  }

  private buildTree(): void {
    const selectedKey = this.selectedNode()?.data.nodeKey;
    this.treeNodes.set(
      this.algoSecurities.map((as) => {
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
      })
    );
    this.selectedNode.set(
      this.treeNodes()
        .flatMap((node) => [node, ...(node.children ?? [])])
        .find((node) => node.data.nodeKey === selectedKey) ?? null
    );
    if (this.isActivated()) this.resetMenu();
  }

  private patchAlert(strategy: AlgoStrategy, enabled: boolean): void {
    this.algoSecurities = this.algoSecurities.map((security) =>
      Object.assign(new AlgoSecurity(), security, {
        algoStrategyList: (security.algoStrategyList ?? []).map((row) =>
          Object.assign(
            new AlgoStrategy(),
            row,
            row.idAlgoRuleStrategy === strategy.idAlgoRuleStrategy ? { alertEnabled: enabled } : {}
          )
        )
      })
    );
    this.buildTree();
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
    return !this.canWrite() || this.savingAlerts().has(rowData.idAlgoRuleStrategy);
  }

  /** Persists the alert preference through its dedicated endpoint and rolls back when the backend refuses it. */
  onCheckboxChange(event: { rowData: any; field: ColumnConfig; value: boolean }): void {
    const { rowData, value } = event;
    if (!this.isStrategyRow(rowData) || this.isCheckboxDisabled(rowData, event.field)) {
      return;
    }
    const strategy: AlgoStrategy = rowData;
    const idAlgoRuleStrategy = strategy.idAlgoRuleStrategy;
    if (idAlgoRuleStrategy == null) return;
    const previous = strategy.alertEnabled;
    this.patchAlert(strategy, value);
    this.savingAlerts.update((pending) => new Set([...pending, idAlgoRuleStrategy]));
    this.algoStrategyService
      .setAlertEnabled(idAlgoRuleStrategy, value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (saved) => {
          this.patchAlert(strategy, saved.alertEnabled);
          this.savingAlerts.update((pending) => new Set([...pending].filter((id) => id !== idAlgoRuleStrategy)));
        },
        error: () => {
          this.patchAlert(strategy, previous);
          this.savingAlerts.update((pending) => new Set([...pending].filter((id) => id !== idAlgoRuleStrategy)));
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
    this.selectedNode.set(null);
    this.resetMenu();
  }

  private resetMenu(): void {
    const menu = this.getEditMenu();
    this.replaceAlgoRequest('menu').add(
      TranslateHelper.translateMenuItems$(menu, this.translateService)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((items) => {
          this.contextMenuItems.set(items);
          this.activePanelService.activatePanel(this, { showMenu: null, editMenu: items });
        })
    );
  }

  private getEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    const selected = this.selectedNode();
    if (!selected || !this.canWrite()) {
      return menuItems;
    }
    const rowData = selected.data;
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
    return menuItems;
  }

  private addStrategyCreateMenu(menuItems: MenuItem[], algoSecurity: AlgoSecurity): void {
    const id = algoSecurity.idAlgoAssetclassSecurity;
    if (id == null) return;
    const createItem: MenuItem = {
      label: 'CREATE|ALGO_STRATEGY',
      command: () => this.handleAddStrategy(algoSecurity)
    };
    menuItems.push(createItem);

    const unused = this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(id);
    if (unused) {
      createItem.disabled = unused.length === 0;
    } else {
      createItem.disabled = true;
      this.replaceAlgoRequest('unused-' + id).add(
        this.algoStrategyService
          .getUnusedStrategiesForManualAdding(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((unused) => {
            this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.set(id, unused);
            if (this.selectedNode()?.data.idAlgoAssetclassSecurity === id) this.resetMenu();
          })
      );
    }
  }

  // ============================================================================
  // CRUD Handlers
  // ============================================================================

  private handleAddStrategy(algoSecurity: AlgoSecurity): void {
    this.algoCallParam.set(new AlgoCallParam(algoSecurity, null, this.algoStrategyDefinitionForm));
    this.visibleStrategyDialog.set(true);
  }

  private handleEditStrategy(algoStrategy: AlgoStrategy): void {
    const parentSecurity = this.findParentSecurity(algoStrategy);
    if (parentSecurity) {
      this.algoCallParam.set(new AlgoCallParam(parentSecurity, algoStrategy, this.algoStrategyDefinitionForm));
      this.visibleStrategyDialog.set(true);
    }
  }

  private handleDeleteStrategy(algoStrategy: AlgoStrategy): void {
    const id = algoStrategy.idAlgoRuleStrategy;
    const parentId = algoStrategy.idAlgoAssetclassSecurity;
    if (id == null || parentId == null) return;
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|ALGO_STRATEGY',
      () => {
        this.algoStrategyService
          .deleteEntity(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe(() => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
              i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.ALGO_STRATEGY)
            });
            this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.delete(parentId);
            this.loadData();
          });
      }
    );
  }

  private handleDeleteSecurity(algoSecurity: AlgoSecurity): void {
    const id = algoSecurity.idAlgoAssetclassSecurity;
    if (id == null) return;
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|ALGO_SECURITY',
      () => {
        this.algoSecurityService
          .deleteEntity(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe(() => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
              i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.ALGO_SECURITY)
            });
            this.loadData();
          });
      }
    );
  }

  onStrategyDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleStrategyDialog.set(false);
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
