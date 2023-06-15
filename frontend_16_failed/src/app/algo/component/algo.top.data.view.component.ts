import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {TreeTableConfigBase} from '../../shared/datashowbase/tree.table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {ConfirmationService, MenuItem, TreeNode} from 'primeng/api';
import {Subscription} from 'rxjs';
import {ActivatedRoute, Params} from '@angular/router';
import {AlgoTop} from '../model/algo.top';
import {AlgoAssetclassService} from '../service/algo.assetclass.service';
import {AlgoAssetclass} from '../model/algo.assetclass';
import {AppHelper} from '../../shared/helper/app.helper';
import {plainToClass} from 'class-transformer';
import {ColumnConfig, TranslateValue} from '../../shared/datashowbase/column.config';
import {AlgoTopAssetSecurity} from '../model/algo.top.asset.security';
import {AlgoStrategy} from '../model/algo.strategy';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../shared/help/help.ids';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {TreeAlgoTop} from '../model/tree.algo.top';
import {AlgoTreeName} from '../../entities/view/algo.tree.name';
import {TreeAlgoAssetclass} from '../model/tree.algo.assetclass';
import {TreeAlgoStrategy} from '../model/tree.algo.strategy';
import {AlgoSecurity} from '../model/algo.security';
import {TreeAlgoSecurity} from '../model/tree.algo.security';
import {
  AlgoCallParam,
  AlgoDialogVisible,
  AlgoStrategyDefinitionForm,
  AlgoStrategyParamCall
} from '../model/algo.dialog.visible';
import {ProcessedAction} from '../../shared/types/processed.action';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {BaseID} from '../../entities/base.id';
import {AlgoSecurityService} from '../service/algo.security.service';
import {AlgoStrategyService} from '../service/algo.strategy.service';
import {AlgoStrategyImplementations} from '../../shared/types/algo.strategy.implementations';
import {RuleStrategy} from '../../shared/types/rule.strategy';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {AlgoStrategyHelper} from './algo.strategy.helper';
import {AlgoTopService} from '../service/algo.top.service';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';

/**
 * Shows algorithmic trading tree with its strategies.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" (contextmenu)="onRightClick($event)"
         #cmDiv [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-treeTable [columns]="fields" [value]="treeNodes" selectionMode="single" [(selection)]="selectedNode"
                   (onNodeSelect)="onNodeSelect($event)" (onNodeUnselect)="onNodeUnselect($event)"
                   [(contextMenuSelection)]="selectedNode" dataKey="idTree">
        <ng-template pTemplate="caption">
          <h4>{{'ALGO_OVERVIEW' | translate}}
            ({{(algoTop.ruleStrategy === RuleStrategy[RuleStrategy.RS_RULE] ? 'ALGO_PORTFOLIO_STRATEGY' : 'ALGO_RULE_BASED') | translate}}
            )</h4>
        </ng-template>

        <ng-template pTemplate="header" let-fields>
          <tr>
            <th *ngFor="let field of fields">
              {{field.headerTranslated}}
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="fields">
          <tr [ttContextMenuRow]="rowNode" [ttSelectableRow]="rowNode"
              [ngClass]="{'kb-row': rowData.constructor.name  === 'AlgoAssetclass'}">
            <td *ngFor="let field of fields; let i = index"
                [ngClass]="{'text-right': (field.dataType===DataType.NumericInteger  || field.dataType===DataType.Numeric
              || field.dataType===DataType.DateTimeNumeric) || field.dataType===DataType.NumericShowZero}">
              <p-treeTableToggler [rowNode]="rowNode" *ngIf="i === 0"></p-treeTableToggler>
              {{getValueByPath(rowData, field)}}
            </td>
          </tr>
        </ng-template>
      </p-treeTable>
      <p-contextMenu #contextMenu [model]="contextMenuItems" [target]="cmDiv" appendTo="body"></p-contextMenu>
      <strategy-detail *ngIf="algoStrategyShowParamCall.algoStrategy"
                       [algoStrategyParamCall]="algoStrategyShowParamCall">
      </strategy-detail>
    </div>
    <algo-assetclass-edit *ngIf="visibleDialogs[AlgoDialogVisible.ALGO_ASSETCLASS]"
                          [visibleDialog]="visibleDialogs[AlgoDialogVisible.ALGO_ASSETCLASS]"
                          [algoCallParam]="algoCallParam"
                          (closeDialog)="handleCloseAlgoAssetclassDialog($event)">
    </algo-assetclass-edit>
    <algo-security-edit *ngIf="visibleDialogs[AlgoDialogVisible.ALGO_SECURITY]"
                        [visibleDialog]="visibleDialogs[AlgoDialogVisible.ALGO_SECURITY]"
                        [algoCallParam]="algoCallParam"
                        (closeDialog)="handleCloseAlgoAssetclassDialog($event)">
    </algo-security-edit>
    <algo-strategy-edit *ngIf="visibleDialogs[AlgoDialogVisible.ALGO_STRATEGY]"
                        [visibleDialog]="visibleDialogs[AlgoDialogVisible.ALGO_STRATEGY]"
                        [algoCallParam]="algoCallParam"
                        (closeDialog)="handleCloseAlgoAssetclassDialog($event)">
    </algo-strategy-edit>
  `,
  styles: [`
    .kb-row {
      font-weight: 700 !important;
    }
  `]
})
export class AlgoTopDataViewComponent extends TreeTableConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {
  @ViewChild('contextMenu', {static: true}) contextMenu: any;


// Otherwise enum DialogVisible can't be used in a html template
  AlgoDialogVisible: typeof AlgoDialogVisible = AlgoDialogVisible;
  RuleStrategy: typeof RuleStrategy = RuleStrategy;

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

  extendMenuWithAlgoStrategy(menuItems: MenuItem[], parent: AlgoTop | AlgoAssetclass | AlgoSecurity, algoStrategy: AlgoStrategy): void {
    menuItems.push({separator: true});
    const algoStrategyMenuItem: MenuItem = {
      label: 'CREATE|ALGO_STRATEGY', command: (e) => this.addEdit(AlgoDialogVisible.ALGO_STRATEGY, parent, algoStrategy,
        this.algoStrategyDefinitionForm)
    };
    menuItems.push(algoStrategyMenuItem);
    if (this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.has(parent.idAlgoAssetclassSecurity)) {
      algoStrategyMenuItem.disabled = this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(
        parent.idAlgoAssetclassSecurity).length === 0;
    } else {

      this.algoStrategyService.getUnusedStrategiesForManualAdding(parent.idAlgoAssetclassSecurity)
        .subscribe(algoStrategyImplementations => {
          this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.set(parent.idAlgoAssetclassSecurity, algoStrategyImplementations);
          algoStrategyMenuItem.disabled = this.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(
            parent.idAlgoAssetclassSecurity).length === 0;
        });
    }
  }

  handleDeleteEntity<T extends BaseID>(entity: T, deleteService: DeleteService): void {
    const entityMsg = AppHelper.convertPropertyNameToUppercase(entity.constructor.name);

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
    this.algoCallParam = new AlgoCallParam(parent, thisObject, algoStrategyDefinitionForm);
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

    const asiNo: number = AlgoStrategyImplementations[algoStrategy.algoStrategyImplementations];
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
