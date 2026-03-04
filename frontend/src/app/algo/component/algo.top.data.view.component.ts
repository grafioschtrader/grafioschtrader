import {Component, OnDestroy, OnInit} from '@angular/core';
import {TreeTableConfigBase} from '../../lib/datashowbase/tree.table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ConfirmationService, MenuItem, TreeNode} from 'primeng/api';
import {concat, Subscription} from 'rxjs';
import {toArray} from 'rxjs/operators';
import {ActivatedRoute, Params} from '@angular/router';
import {AlgoTop} from '../model/algo.top';
import {AlgoAssetclassService} from '../service/algo.assetclass.service';
import {AlgoAssetclass} from '../model/algo.assetclass';
import {AppHelper} from '../../lib/helper/app.helper';
import {plainToClass} from 'class-transformer';
import {ColumnConfig, EditInputType, TranslateValue} from '../../lib/datashowbase/column.config';
import {AlgoTopAssetSecurity} from '../model/algo.top.asset.security';
import {AlgoStrategy} from '../model/algo.strategy';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../lib/help/help.ids';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {AlgoTreeName} from '../../entities/view/algo.tree.name';
import {AlgoSecurity} from '../model/algo.security';

import {
  AlgoCallParam,
  AlgoDialogVisible,
  AlgoStrategyDefinitionForm,
  AlgoStrategyParamCall
} from '../model/algo.dialog.visible';
import {ProcessedAction} from '../../lib/types/processed.action';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {BaseID} from '../../lib/entities/base.id';
import {AlgoSecurityService} from '../service/algo.security.service';
import {AlgoStrategyService} from '../service/algo.strategy.service';
import {AlgoStrategyImplementationType} from '../../shared/types/algo.strategy.implementation.type';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AlgoStrategyHelper} from './algo.strategy.helper';
import {AlgoTopService} from '../service/algo.top.service';
import {DataChangedService} from '../../lib/maintree/service/data.changed.service';
import {TreeAlgoAssetclass, TreeAlgoSecurity, TreeAlgoStrategy, TreeAlgoTop} from '../model/tree.algo.base';
import {AlgoSecurityEditComponent} from './algo-security-edit.component';

/**
 * Shows algorithmic trading tree with its strategies.
 * Supports inline editing of the percentage column for AlgoTop, AlgoAssetclass, and AlgoSecurity nodes.
 */
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {StrategyDetailComponent} from './strategy-detail.component';
import {AlgoAssetclassEditComponent} from './algo-assetclass-edit.component';
import {AlgoStrategyEditComponent} from './algo-strategy-edit.component';
import {
  ConfigurableTreeTableComponent,
  TreeTableCellEditEvent
} from '../../lib/datashowbase/configurable-tree-table.component';

@Component({
  template: `
    <div class="data-container"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <configurable-tree-table
        [data]="treeNodes" [fields]="fields" dataKey="idTree"
        [(selection)]="selectedNode"
        (nodeSelect)="onNodeSelect($event)" (nodeUnselect)="onNodeUnselect($event)"
        [showContextMenu]="true" [contextMenuItems]="contextMenuItems"
        [valueGetterFn]="getValueByPath.bind(this)"
        [baseLocale]="baseLocale"
        [canEditCellFn]="canEditCell.bind(this)"
        (cellEditComplete)="onCellEditComplete($event)"
        [checkboxVisibleFn]="isSecurityRow.bind(this)"
        (checkboxChange)="onCheckboxChangeHandler($event)"
        (componentClick)="onComponentClick($event)"
        [rowClassFn]="getAlgoRowClass.bind(this)"
        [enableSort]="false">
        <h4 caption>{{ 'ALGO_OVERVIEW' | translate }}</h4>
      </configurable-tree-table>
      @if (algoStrategyShowParamCall.algoStrategy) {
        <strategy-detail [algoStrategyParamCall]="algoStrategyShowParamCall">
        </strategy-detail>
      }
    </div>
    @if (visibleDialogs[AlgoDialogVisible.ALGO_ASSETCLASS]) {
      <algo-assetclass-edit [visibleDialog]="visibleDialogs[AlgoDialogVisible.ALGO_ASSETCLASS]"
                            [algoCallParam]="algoCallParam"
                            (closeDialog)="handleCloseAlgoAssetclassDialog($event)">
      </algo-assetclass-edit>
    }
    @if (visibleDialogs[AlgoDialogVisible.ALGO_SECURITY]) {
      <algo-security-edit [visibleDialog]="visibleDialogs[AlgoDialogVisible.ALGO_SECURITY]"
                          [algoCallParam]="algoCallParam"
                          (closeDialog)="handleCloseAlgoAssetclassDialog($event)">
      </algo-security-edit>
    }
    @if (visibleDialogs[AlgoDialogVisible.ALGO_STRATEGY]) {
      <algo-strategy-edit [visibleDialog]="visibleDialogs[AlgoDialogVisible.ALGO_STRATEGY]"
                          [algoCallParam]="algoCallParam"
                          (closeDialog)="handleCloseAlgoAssetclassDialog($event)">
      </algo-strategy-edit>
    }
  `,
    styles: [`
    .kb-row {
      font-weight: 700 !important;
    }
  `],
    standalone: true,
    imports: [AlgoSecurityEditComponent, CommonModule, TranslateModule, ConfigurableTreeTableComponent,
      StrategyDetailComponent, AlgoAssetclassEditComponent, AlgoStrategyEditComponent]
})
export class AlgoTopDataViewComponent extends TreeTableConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {

// Otherwise enum DialogVisible can't be used in a html template
  AlgoDialogVisible: typeof AlgoDialogVisible = AlgoDialogVisible;

  // For modal dialogs
  visibleDialogs: boolean[] = [];

  algoTop: AlgoTop;
  treeNodes: TreeNode[];
  algoCallParam: AlgoCallParam;

  algoStrategyDefinitionForm = new AlgoStrategyDefinitionForm();

  selectedNode: TreeNode;

  contextMenuItems: MenuItem[] = [];
  // Detail Show param
  algoStrategyShowParamCall: AlgoStrategyParamCall = new AlgoStrategyParamCall();
  private routeSubscribe: Subscription;

  constructor(private activatedRoute: ActivatedRoute,
    private activePanelService: ActivePanelService,
    private algoTopService: AlgoTopService,
    private algoAssetclassService: AlgoAssetclassService,
    private algoSecurityService: AlgoSecurityService,
    private algoStrategyService: AlgoStrategyService,
    private dataChangedService: DataChangedService,
    protected messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(translateService, gps);

    this.addColumn(DataType.String, 'name', 'NAME', true, false,
      {fieldValueFN: this.getReadableUniqueName.bind(this)});
    this.addColumn(DataType.Boolean, '_selected', '', true, false, {templateName: 'editableCheck', width: 40});
    const percentageCol = this.addColumn(DataType.Numeric, 'percentage', 'ALGO_PERCENTAGE', true, false);
    percentageCol.cec = {inputType: EditInputType.InputNumber, min: 0, max: 100, maxFractionDigits: 2};
    this.addColumnFeqH(DataType.NumericShowZero, 'addedPercentage', true, false);
    this.addColumn(DataType.String, 'idTree', 'ID', true, false);
  }

  ngOnInit(): void {
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      this.algoTop = JSON.parse(params['object']) as AlgoTop;
      this.translateHeadersAndColumns();
      this.readDataWithoutTopLevel();
    });
  }

  readDataWithTopLevel(): void {
    this.algoTopService.getAlgoTopByIdAlgoAssetclassSecurity(this.algoTop.idAlgoAssetclassSecurity).subscribe(algoTop => {
      this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.UPDATED, new AlgoTop()));
      this.algoTop = algoTop;
      this.readDataWithoutTopLevel();
    });
  }

  readDataWithoutTopLevel(): void {
    this.algoAssetclassService.getAlgoAssetclassByIdTenantAndIdAlgoAssetclassParent(this.algoTop.idAlgoAssetclassSecurity)
      .subscribe((algoAssetclassList: AlgoAssetclass[]) => {
        this.algoTop.algoAssetclassList = algoAssetclassList;
        this.algoTop = plainToClass(AlgoTop, this.algoTop);
        this.treeNodes = [new TreeAlgoTop(this.algoTop)];
        this.translateDataForAssetclass();
        this.translateDataForStrategy();
        this.refreshSelectedEntity();
      });
  }

  getReadableUniqueName(dataobject: AlgoTreeName, field: ColumnConfig, valueField: any): string {
    return dataobject.getNameByLanguage(this.gps.getUserLang());
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
    return 'percentage' in rowData;
  }

  /**
   * Handles cell edit completion by persisting the changed value via the appropriate service.
   * On error, rolls back to the original value.
   *
   * @param event - The cell edit event containing rowData, field, originalValue, and newValue
   */
  onCellEditComplete(event: TreeTableCellEditEvent): void {
    if (event.originalValue === event.newValue) {
      return;
    }
    const rowData = event.rowData;
    if (rowData instanceof AlgoTop) {
      this.algoTopService.update(rowData).subscribe({
        next: () => this.readDataWithTopLevel(),
        error: () => rowData[event.field.field] = event.originalValue
      });
    } else if (rowData instanceof AlgoAssetclass) {
      this.algoAssetclassService.update(rowData).subscribe({
        next: () => this.readDataWithoutTopLevel(),
        error: () => rowData[event.field.field] = event.originalValue
      });
    } else if (rowData instanceof AlgoSecurity) {
      this.algoSecurityService.update(rowData).subscribe({
        next: () => this.readDataWithoutTopLevel(),
        error: () => rowData[event.field.field] = event.originalValue
      });
    }
  }

  /**
   * Returns CSS class for tree table rows.
   * AlgoAssetclass rows are displayed in bold to distinguish them visually.
   *
   * @param rowNode - PrimeNG TreeNode wrapper
   * @param rowData - The row data object
   * @returns CSS class string or null
   */
  getAlgoRowClass(rowNode: any, rowData: any): string | null {
    return rowData instanceof AlgoAssetclass ? 'kb-row' : null;
  }

  /**
   * Determines if a row should display the selection checkbox.
   * Only AlgoSecurity rows get checkboxes for batch selection.
   */
  isSecurityRow(rowData: any, field: ColumnConfig): boolean {
    return rowData instanceof AlgoSecurity;
  }

  /**
   * Handles checkbox toggle by storing the checked state on the row data object.
   */
  onCheckboxChangeHandler(event: {rowData: any; field: ColumnConfig; value: boolean}): void {
    event.rowData._selected = event.value;
  }

  extendMenuWithAlgoStrategy(menuItems: MenuItem[], selectedNode: AlgoTop | AlgoAssetclass | AlgoSecurity, algoStrategy: AlgoStrategy): void {
    menuItems.push({separator: true});
    const algoStrategyMenuItem: MenuItem = {
      label: 'CREATE|ALGO_STRATEGY',
      command: (e) => this.addEdit(AlgoDialogVisible.ALGO_STRATEGY, selectedNode, algoStrategy,
        this.algoStrategyDefinitionForm)
    };
    menuItems.push(algoStrategyMenuItem);
    if (this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.has(selectedNode.idAlgoAssetclassSecurity)) {
      algoStrategyMenuItem.disabled = this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(
        selectedNode.idAlgoAssetclassSecurity).length === 0;
    } else {

      this.algoStrategyService.getUnusedStrategiesForManualAdding(selectedNode.idAlgoAssetclassSecurity)
        .subscribe(algoStrategyImplementations => {
          this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.set(selectedNode.idAlgoAssetclassSecurity, algoStrategyImplementations);
          algoStrategyMenuItem.disabled = this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(
            selectedNode.idAlgoAssetclassSecurity).length === 0;
        });
    }
  }

  handleDeleteEntity<T extends BaseID>(entity: T, deleteService: DeleteService): void {
    const entityMsg = AppHelper.toUpperCaseWithUnderscore(entity.constructor.name);
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + entityMsg, () => {
        deleteService.deleteEntity(entity.getId()).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: entity.constructor.name});
          this.resetMenu();
          this.readDataWithoutTopLevel();
        });
      });
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
    }
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

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return HelpIds.HELP_ALGO;
  }

  onNodeSelect(event) {
    if (this.selectedNode instanceof TreeAlgoStrategy) {
      // Needed to cause ngOnChanges
      this.algoStrategyShowParamCall = new AlgoStrategyParamCall();
      this.setFieldDescriptorInputAndShow((<TreeNode>this.selectedNode).parent.data, this.selectedNode.data,
        this.algoStrategyShowParamCall);
      // this.algoStrategyShowParamCall.algoStrategy = this.selectedNode.algoStrategy;
    } else {
      this.algoStrategyShowParamCall.algoStrategy = null;
    }
  }

  onNodeUnselect(event) {
    this.algoStrategyShowParamCall.algoStrategy = null;
  }

  getMenuShowOptions(): MenuItem[] {
    return null;
  }

  ngOnDestroy(): void {
    this.routeSubscribe.unsubscribe();
  }

  private translateDataForAssetclass(): void {
    const fieldsAssetclass: ColumnConfig[] = [];
    this.addColumnToFields(fieldsAssetclass, DataType.String, 'assetclass.categoryType',
      '', true, false, {translateValues: TranslateValue.NORMAL});
    this.addColumnToFields(fieldsAssetclass, DataType.String, 'assetclass.specialInvestmentInstrument',
      '', true, false, {translateValues: TranslateValue.NORMAL});

    const nonCustomAssetclasses = this.algoTop.algoAssetclassList.filter(ac => !ac.isCustomCategory());
    TranslateHelper.createTranslatedValueStore(this.translateService, fieldsAssetclass, nonCustomAssetclasses);
  }

  private translateDataForStrategy(): void {
    const fieldAlgoStrategy: ColumnConfig[] = [];
    this.addColumnToFields(fieldAlgoStrategy, DataType.String, 'algoStrategyImplementations',
      '', true, false, {translateValues: TranslateValue.NORMAL});
    const algoStrategyList: AlgoStrategy[] = [];
    this.traverseObjectTreeForAlgoStrategy(algoStrategyList, this.algoTop);
    TranslateHelper.createTranslatedValueStore(this.translateService, fieldAlgoStrategy, algoStrategyList);
  }

  private traverseObjectTreeForAlgoStrategy(algoStrategyList: AlgoStrategy[], algoTopAssetSecurity: AlgoTopAssetSecurity): void {
    algoTopAssetSecurity.algoStrategyList && algoStrategyList.push(...algoTopAssetSecurity.algoStrategyList);
    const algoTopAssetSecurityList = algoTopAssetSecurity.getChildList();
    if (algoTopAssetSecurityList) {
      algoTopAssetSecurityList.forEach(atas => {
        this.traverseObjectTreeForAlgoStrategy(algoStrategyList, atas);
      });
    }
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
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|SECURITY', () => {
        const deleteObs = checked.map(sec => this.algoSecurityService.deleteEntity(sec.idAlgoAssetclassSecurity));
        concat(...deleteObs).pipe(toArray()).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {i18nRecord: 'AlgoSecurity'});
          this.resetMenu();
          this.readDataWithoutTopLevel();
        });
      });
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
    const menuItems: MenuItem[] = [];
    const checkedSecurities = this.getCheckedSecurities();
    if (checkedSecurities.length > 0) {
      menuItems.push({
        label: 'DELETE_SELECTED_SECURITIES',
        command: () => this.handleDeleteSelectedSecurities()
      });
      menuItems.push({separator: true});
    }
    if (selectedNode instanceof TreeAlgoTop) {
      menuItems.push({
        label: 'ADD_RECORD|ASSETCLASS', command: (e) => this.addEdit(AlgoDialogVisible.ALGO_ASSETCLASS,
          this.algoTop, null)
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
        label: 'EDIT_RECORD|ASSETCLASS', command: (e) => this.addEdit(AlgoDialogVisible.ALGO_ASSETCLASS,
          this.algoTop, selectedNode.data)
      });
      menuItems.push({
        label: 'DELETE_RECORD|ASSETCLASS',
        command: (e) => this.handleDeleteEntity(selectedNode.data, this.algoAssetclassService),
        disabled: selectedNode.children.length > 0
      });
      menuItems.push({separator: true});
      menuItems.push({
        label: 'ADD_RECORD|SECURITY', command: (e) => this.addEdit(AlgoDialogVisible.ALGO_SECURITY,
          <AlgoAssetclass>selectedNode.data, null)
      });
      menuItems.push({
        label: 'NORMALIZE_PERCENTAGES',
        command: () => this.handleNormalizePercentages(selectedNode.data.idAlgoAssetclassSecurity),
        disabled: !selectedNode.data.algoSecurityList || selectedNode.data.algoSecurityList.length === 0
      });
      this.extendMenuWithAlgoStrategy(menuItems, selectedNode.data, null);
    } else if (selectedNode instanceof TreeAlgoSecurity) {
      menuItems.push({
        label: 'EDIT_RECORD|SECURITY', command: (e) => this.addEdit(AlgoDialogVisible.ALGO_SECURITY,
          (<TreeNode>selectedNode).parent.data, <AlgoSecurity>selectedNode.data)
      });
      menuItems.push({
        label: 'DELETE_RECORD|SECURITY',
        command: (e) => this.handleDeleteEntity(selectedNode.data, this.algoSecurityService)
      });
      this.extendMenuWithAlgoStrategy(menuItems, selectedNode.data, null);
    } else if (selectedNode instanceof TreeAlgoStrategy) {
      menuItems.push({
        label: 'EDIT', command: (e) => this.addEdit(AlgoDialogVisible.ALGO_STRATEGY,
          (<TreeNode>selectedNode).parent.data, selectedNode.data, this.algoStrategyDefinitionForm)
      });
      menuItems.push({
        label: 'DELETE_RECORD|ALGO_STRATEGY',
        command: (e) => this.handleDeleteEntity(selectedNode.data, this.algoStrategyService)
      });
    }
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  private addEdit(algoDialogVisible: AlgoDialogVisible, parent: AlgoTop | AlgoAssetclass | AlgoSecurity,
    thisObject: AlgoTop | AlgoAssetclass | AlgoSecurity | AlgoStrategy,
    algoStrategyDefinitionForm?: AlgoStrategyDefinitionForm): void {
    const idWatchlist = (algoDialogVisible === AlgoDialogVisible.ALGO_SECURITY
      && parent instanceof AlgoAssetclass && parent.isCustomCategory())
      ? this.algoTop.idWatchlist : undefined;
    this.algoCallParam = new AlgoCallParam(parent, thisObject, algoStrategyDefinitionForm, idWatchlist);
    this.visibleDialogs[algoDialogVisible] = true;
  }

  private refreshSelectedEntity(): void {
    if (this.selectedNode) {
      this.selectedNode = this.searchTree(this.treeNodes[0], this.selectedNode.data.idTree);
      setTimeout(() => this.onNodeSelect(null));
    }
  }

  private setFieldDescriptorInputAndShow<T extends AlgoTopAssetSecurity>(algoTopAssetSecurity: T,
    algoStrategy: AlgoStrategy,
    algoStrategyParamCall: AlgoStrategyParamCall): void {
    const asiNo: number = AlgoStrategyImplementationType[algoStrategy.algoStrategyImplementations];
    const inputAndShowDefinition = this.algoStrategyDefinitionForm.inputAndShowDefinitionMap.get(asiNo);
    if (!inputAndShowDefinition) {
      this.algoStrategyService.getFormDefinitionsByAlgoStrategy(asiNo).subscribe(iasd => {
        this.algoStrategyDefinitionForm.inputAndShowDefinitionMap.set(asiNo, iasd);
        algoStrategyParamCall.isComplexStrategy = iasd.isComplexStrategy;
        algoStrategyParamCall.fieldDescriptorShow = AlgoStrategyHelper.getFieldDescriptorInputAndShowByLevel(algoTopAssetSecurity,
          iasd);
        this.algoStrategyShowParamCall.algoStrategy = algoStrategy;
      });
    } else {
      algoStrategyParamCall.isComplexStrategy = inputAndShowDefinition.isComplexStrategy;
      algoStrategyParamCall.fieldDescriptorShow = AlgoStrategyHelper.getFieldDescriptorInputAndShowByLevel(algoTopAssetSecurity,
        inputAndShowDefinition);
      this.algoStrategyShowParamCall.algoStrategy = algoStrategy;
    }

  }
}
