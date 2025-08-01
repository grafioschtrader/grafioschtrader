import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {TreeTableConfigBase} from '../../lib/datashowbase/tree.table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ConfirmationService, MenuItem, TreeNode} from 'primeng/api';
import {Subscription} from 'rxjs';
import {ActivatedRoute, Params} from '@angular/router';
import {AlgoTop} from '../model/algo.top';
import {AlgoAssetclassService} from '../service/algo.assetclass.service';
import {AlgoAssetclass} from '../model/algo.assetclass';
import {AppHelper} from '../../lib/helper/app.helper';
import {plainToClass} from 'class-transformer';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {AlgoTopAssetSecurity} from '../model/algo.top.asset.security';
import {AlgoStrategy} from '../model/algo.strategy';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../shared/help/help.ids';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
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
import {RuleStrategyType} from '../../shared/types/rule.strategy.type';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AlgoStrategyHelper} from './algo.strategy.helper';
import {AlgoTopService} from '../service/algo.top.service';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {TreeAlgoAssetclass, TreeAlgoSecurity, TreeAlgoStrategy, TreeAlgoTop} from '../model/tree.algo.base';

/**
 * Shows algorithmic trading tree with its strategies.
 * Project: Grafioschtrader
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" (contextmenu)="onRightClick($event)"
         #cmDiv [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-treeTable [columns]="fields" [value]="treeNodes" selectionMode="single" [(selection)]="selectedNode"
                   (onNodeSelect)="onNodeSelect($event)" (onNodeUnselect)="onNodeUnselect($event)"
                   [(contextMenuSelection)]="selectedNode" dataKey="idTree">
        <ng-template #caption>
          <h4>{{ 'ALGO_OVERVIEW' | translate }}
            ({{ (algoTop.ruleStrategy === RuleStrategy[RuleStrategy.RS_RULE] ? 'ALGO_PORTFOLIO_STRATEGY' : 'ALGO_RULE_BASED') | translate }}
            )</h4>
        </ng-template>

        <ng-template #header let-fields>
          <tr>
            @for (field of fields; track field) {
              <th>
                {{ field.headerTranslated }}
              </th>
            }
          </tr>
        </ng-template>
        <ng-template #body let-rowNode let-rowData="rowData" let-columns="fields">
          <tr [ttContextMenuRow]="rowNode" [ttSelectableRow]="rowNode"
              [ngClass]="{'kb-row': rowData.constructor.name  === 'AlgoAssetclass'}">
            @for (field of fields; track field; let i = $index) {
              <td [ngClass]="{'text-right': (field.dataType===DataType.NumericInteger  || field.dataType===DataType.Numeric
              || field.dataType===DataType.DateTimeNumeric) || field.dataType===DataType.NumericShowZero}">
                @if (i === 0) {
                  <p-treeTableToggler [rowNode]="rowNode"></p-treeTableToggler>
                }
                {{ getValueByPath(rowData, field) }}
              </td>
            }
          </tr>
        </ng-template>
      </p-treeTable>
      <p-contextMenu #contextMenu [model]="contextMenuItems" [target]="cmDiv"></p-contextMenu>
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
    standalone: false
})
export class AlgoTopDataViewComponent extends TreeTableConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {
  @ViewChild('contextMenu', {static: true}) contextMenu: any;

// Otherwise enum DialogVisible can't be used in a html template
  AlgoDialogVisible: typeof AlgoDialogVisible = AlgoDialogVisible;
  RuleStrategy: typeof RuleStrategyType = RuleStrategyType;

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
    this.addColumn(DataType.Numeric, 'percentage', 'ALGO_PERCENTAGE', true, false);
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

  onRightClick(event): void {
    this.resetMenu();
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

  getHelpContextId(): HelpIds {
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

    TranslateHelper.createTranslatedValueStore(this.translateService, fieldsAssetclass, this.algoTop.algoAssetclassList);
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

  private resetMenu(): void {
    this.contextMenuItems = this.getEditMenu(this.selectedNode);
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions(),
      editMenu: this.contextMenuItems
    });
  }

  private getEditMenu(selectedNode: TreeNode): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (selectedNode instanceof TreeAlgoTop) {
      menuItems.push({
        label: 'ADD_RECORD|ASSETCLASS', command: (e) => this.addEdit(AlgoDialogVisible.ALGO_ASSETCLASS,
          this.algoTop, null)
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
    this.algoCallParam = new AlgoCallParam(parent, thisObject);
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
        algoStrategyParamCall.fieldDescriptorShow = AlgoStrategyHelper.getFieldDescriptorInputAndShowByLevel(algoTopAssetSecurity,
          iasd);
        this.algoStrategyShowParamCall.algoStrategy = algoStrategy;
      });
    } else {
      algoStrategyParamCall.fieldDescriptorShow = AlgoStrategyHelper.getFieldDescriptorInputAndShowByLevel(algoTopAssetSecurity,
        inputAndShowDefinition);
      this.algoStrategyShowParamCall.algoStrategy = algoStrategy;
    }

  }
}
