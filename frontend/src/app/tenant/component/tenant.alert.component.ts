import {Component, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ConfirmationService, MenuItem, TreeNode} from 'primeng/api';
import {TreeTableConfigBase} from '../../lib/datashowbase/tree.table.config.base';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppHelper} from '../../lib/helper/app.helper';
import {HelpIds} from '../../lib/help/help.ids';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {AlgoSecurity} from '../../algo/model/algo.security';
import {AlgoStrategy} from '../../algo/model/algo.strategy';
import {AlgoSecurityService} from '../../algo/service/algo.security.service';
import {AlgoStrategyService} from '../../algo/service/algo.strategy.service';
import {AlgoCallParam, AlgoStrategyDefinitionForm} from '../../algo/model/algo.dialog.visible';
import {ConfigurableTreeTableComponent} from '../../lib/datashowbase/configurable-tree-table.component';
import {AlgoStrategyEditComponent} from '../../algo/component/algo-strategy-edit.component';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';

/**
 * Displays all alerts across the tenant in a tree table where AlgoSecurity entries are parent rows
 * and their AlgoStrategy entries are child rows. Users can activate/deactivate individual strategies
 * via checkboxes and perform CRUD on strategies via context menu dialogs.
 */
@Component({
  selector: 'tenant-alert',
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <configurable-tree-table
        [data]="treeNodes" [fields]="fields" dataKey="nodeKey"
        [(selection)]="selectedNode"
        (nodeSelect)="onRowSelect($event)"
        (nodeUnselect)="onRowUnselect($event)"
        [contextMenuItems]="contextMenuItems"
        [contextMenuAppendTo]="'body'"
        [showContextMenu]="isActivated()"
        [valueGetterFn]="getValueByPath.bind(this)"
        (checkboxChange)="onCheckboxChange($event)"
        (componentClick)="onComponentClick($event)">
      </configurable-tree-table>
    </div>

    @if (visibleStrategyDialog) {
      <algo-strategy-edit [visibleDialog]="visibleStrategyDialog"
        [algoCallParam]="algoCallParam"
        (closeDialog)="onStrategyDialogClose($event)">
      </algo-strategy-edit>
    }
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule, ConfigurableTreeTableComponent, AlgoStrategyEditComponent]
})
export class TenantAlertComponent extends TreeTableConfigBase implements OnInit, OnDestroy, IGlobalMenuAttach {

  treeNodes: TreeNode[] = [];
  selectedNode: TreeNode | null = null;
  contextMenuItems: MenuItem[] = [];

  visibleStrategyDialog = false;
  algoCallParam: AlgoCallParam;
  algoStrategyDefinitionForm = new AlgoStrategyDefinitionForm();

  private algoSecurities: AlgoSecurity[] = [];

  constructor(
    private activePanelService: ActivePanelService,
    private algoSecurityService: AlgoSecurityService,
    private algoStrategyService: AlgoStrategyService,
    private messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(translateService, gps);

    this.addColumn(DataType.String, 'name', 'NAME', true, false,
      {fieldValueFN: this.getNodeName.bind(this)});
    this.addColumn(DataType.String, 'algoStrategyImplementations', 'ALGO_STRATEGY_NAME', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'alertContext', 'ALERT_CONTEXT', true, false,
      {fieldValueFN: this.getAlertContext.bind(this)});
    this.addColumn(DataType.Boolean, 'activatable', 'ACTIVATABLE', true, false,
      {templateName: 'editableCheck'});
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

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return HelpIds.HELP_ALGO;
  }

  // ============================================================================
  // Data Loading
  // ============================================================================

  private loadData(): void {
    this.algoSecurityService.getAllForTenant().subscribe((algoSecurities: AlgoSecurity[]) => {
      this.algoSecurities = algoSecurities;
      this.buildTree();
      this.prepareTreeTableAndTranslate();
      this.translateStrategies();
    });
  }

  private buildTree(): void {
    this.treeNodes = this.algoSecurities.map(as => {
      (as as any).nodeKey = 'as' + as.idAlgoAssetclassSecurity;
      const strategyChildren: TreeNode[] = (as.algoStrategyList || []).map(strategy => {
        (strategy as any).nodeKey = 'rs' + strategy.idAlgoRuleStrategy;
        return {data: strategy, leaf: true};
      });
      return {
        data: as,
        children: strategyChildren,
        expanded: true
      } as TreeNode;
    });
  }

  private translateStrategies(): void {
    const allStrategies: AlgoStrategy[] = [];
    this.algoSecurities.forEach(as => {
      if (as.algoStrategyList) {
        allStrategies.push(...as.algoStrategyList);
      }
    });
    if (allStrategies.length > 0) {
      const strategyFields: ColumnConfig[] = [];
      this.addColumnToFields(strategyFields, DataType.String, 'algoStrategyImplementations',
        '', true, false, {translateValues: TranslateValue.NORMAL});
      TranslateHelper.createTranslatedValueStore(this.translateService, strategyFields, allStrategies);
    }
  }

  // ============================================================================
  // Value Getters for Columns
  // ============================================================================

  private getNodeName(dataobject: any, field: ColumnConfig, valueField: any): string {
    if (dataobject instanceof AlgoSecurity || dataobject.security) {
      return dataobject.security ? dataobject.security.name + ', ' + dataobject.security.currency : '';
    }
    return '';
  }

  private getAlertContext(dataobject: any, field: ColumnConfig, valueField: any): string {
    if (dataobject instanceof AlgoSecurity || dataobject.idAlgoSecurityParent !== undefined) {
      return dataobject.idAlgoSecurityParent ? 'AlgoTop' : this.translateService.instant('STANDALONE');
    }
    return '';
  }

  // ============================================================================
  // Checkbox Toggle
  // ============================================================================

  onCheckboxChange(event: {rowData: any; field: ColumnConfig; value: boolean}): void {
    const {rowData, value} = event;
    if (rowData.algoStrategyImplementations !== undefined && rowData.algoStrategyImplementations !== null) {
      // Strategy row
      rowData.activatable = value;
      this.algoStrategyService.update(rowData as AlgoStrategy).subscribe({
        error: () => {
          rowData.activatable = !value;
          this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'MSG_SAVE_ERROR');
        }
      });
    } else if (rowData.security) {
      // Security row
      rowData.activatable = value;
      this.algoSecurityService.update(rowData as AlgoSecurity).subscribe({
        error: () => {
          rowData.activatable = !value;
          this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'MSG_SAVE_ERROR');
        }
      });
    }
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
    if (!this.selectedNode) {
      return menuItems;
    }
    const rowData = this.selectedNode.data;
    if (rowData.security) {
      // AlgoSecurity row
      this.addStrategyCreateMenu(menuItems, rowData as AlgoSecurity);
      menuItems.push({
        label: 'DELETE_RECORD|ALGO_SECURITY',
        command: () => this.handleDeleteSecurity(rowData as AlgoSecurity)
      });
    } else if (rowData.algoStrategyImplementations !== undefined && rowData.algoStrategyImplementations !== null) {
      // AlgoStrategy row
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
      createItem.disabled = this.algoStrategyDefinitionForm.unusedAlgoStrategyMap
        .get(algoSecurity.idAlgoAssetclassSecurity).length === 0;
    } else {
      this.algoStrategyService.getUnusedStrategiesForManualAdding(algoSecurity.idAlgoAssetclassSecurity)
        .subscribe(unused => {
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
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|ALGO_STRATEGY', () => {
        this.algoStrategyService.deleteEntity(algoStrategy.idAlgoRuleStrategy).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: 'AlgoStrategy'});
          this.algoStrategyDefinitionForm.unusedAlgoStrategyMap
            .delete(algoStrategy.idAlgoAssetclassSecurity);
          this.loadData();
        });
      });
  }

  private handleDeleteSecurity(algoSecurity: AlgoSecurity): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|ALGO_SECURITY', () => {
        this.algoSecurityService.deleteEntity(algoSecurity.idAlgoAssetclassSecurity).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: 'AlgoSecurity'});
          this.loadData();
        });
      });
  }

  onStrategyDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleStrategyDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.clear();
      this.loadData();
    }
  }

  // ============================================================================
  // Helpers
  // ============================================================================

  private findParentSecurity(algoStrategy: AlgoStrategy): AlgoSecurity | null {
    return this.algoSecurities.find(as =>
      as.idAlgoAssetclassSecurity === algoStrategy.idAlgoAssetclassSecurity
    ) || null;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }
}
