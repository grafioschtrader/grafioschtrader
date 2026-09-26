import { Directive } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { TreeNode } from '@openng/optimus-ui/api';
import { plainToClass } from 'class-transformer';
import { TreeTableConfigBase } from '../../lib/datashowbase/tree.table.config.base';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AlgoTreeName } from '../../entities/view/algo.tree.name';
import { AlgoStrategyImplementationType } from '../../shared/types/algo.strategy.implementation.type';
import { AppSettings } from '../../shared/app.settings';
import { AlgoTop } from '../model/algo.top';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { AlgoStrategy } from '../model/algo.strategy';
import { AlgoTopAssetSecurity } from '../model/algo.top.asset.security';
import { AlgoStrategyDefinitionForm, AlgoStrategyParamCall } from '../model/algo.dialog.visible';
import { TreeAlgoStrategy, TreeAlgoTop } from '../model/tree.algo.base';
import { AlgoStrategyService } from '../service/algo.strategy.service';
import { AlgoStrategyHelper } from './algo.strategy.helper';

/**
 * Shared base of the views that show a strategy hierarchy as a tree: the editable live hierarchy and the frozen
 * hierarchy a historical replay was submitted with. It builds the tree from the hierarchy response, translates the
 * asset class and strategy values, and shows the parameters of a selected strategy in the strategy detail below the
 * tree. Editing, alerts and context menus stay with the subclass that offers them.
 */
@Directive()
export abstract class AlgoTreeViewBase extends TreeTableConfigBase {
  algoTop: AlgoTop;
  treeNodes: TreeNode[];
  selectedNode: TreeNode;

  /** Form definitions of the strategy implementations, loaded once per implementation and shared by the dialogs. */
  algoStrategyDefinitionForm = new AlgoStrategyDefinitionForm();

  /** Parameters of the strategy shown in the detail below the tree; its algoStrategy is null when none is selected. */
  algoStrategyShowParamCall: AlgoStrategyParamCall = new AlgoStrategyParamCall();

  /**
   * @param algoStrategyService - Loads the form definition of a strategy implementation for the strategy detail
   * @param translateService - Angular translation service for internationalization support
   * @param gps - Global parameter service providing user locale and formatting preferences
   */
  protected constructor(
    protected algoStrategyService: AlgoStrategyService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(translateService, gps);
  }

  /**
   * Adds the name column and the percentage column every hierarchy tree starts with.
   *
   * @returns The percentage column, so that a subclass can make it editable
   */
  protected addNameAndPercentageColumns(): ColumnConfig {
    this.addColumn(DataType.String, 'name', 'NAME', true, false, {
      fieldValueFN: this.getReadableUniqueName.bind(this)
    });
    return this.addColumn(DataType.Numeric, 'percentage', 'ALGO_PERCENTAGE', true, false, {
      maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION
    });
  }

  /** Adds the columns that follow the percentage: the child total, the activity dates of an instrument and the ID. */
  protected addTotalDateAndIdColumns(): void {
    this.addColumnFeqH(DataType.NumericShowZero, 'addedPercentage', true, false, {
      maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION
    });
    this.addColumnFeqH(DataType.DateString, 'security.activeFromDate', true, false);
    this.addColumnFeqH(DataType.DateString, 'security.activeToDate', true, false);
    this.addColumn(DataType.String, 'idTree', 'ID', true, false);
  }

  getReadableUniqueName(dataobject: AlgoTreeName, field: ColumnConfig, valueField: any): string {
    return dataobject.getNameByLanguage(this.gps.getUserLang());
  }

  /**
   * Builds the tree from a hierarchy response and translates its asset class and strategy values.
   *
   * @param algoTop - Top level of the hierarchy as delivered by the server
   * @param algoAssetclassList - Asset classes with their instruments and strategies
   */
  protected buildTree(algoTop: AlgoTop, algoAssetclassList: AlgoAssetclass[]): void {
    this.algoTop = plainToClass(AlgoTop, { ...algoTop, algoAssetclassList });
    this.treeNodes = [new TreeAlgoTop(this.algoTop)];
    this.translateDataForAssetclass();
    this.translateDataForStrategy();
  }

  /**
   * Returns CSS class for tree table rows. AlgoAssetclass rows are displayed in bold to distinguish them visually.
   *
   * @param rowNode - Optimus TreeNode wrapper
   * @param rowData - The row data object
   * @returns CSS class string or null
   */
  getAlgoRowClass(rowNode: any, rowData: any): string | null {
    return rowData instanceof AlgoAssetclass ? 'kb-row' : null;
  }

  /** Shows the parameters of a selected strategy below the tree and hides them for any other node. */
  onNodeSelect(event): void {
    if (this.selectedNode instanceof TreeAlgoStrategy) {
      // Needed to cause ngOnChanges
      this.algoStrategyShowParamCall = new AlgoStrategyParamCall();
      this.setFieldDescriptorInputAndShow(
        (<TreeNode>this.selectedNode).parent.data,
        this.selectedNode.data,
        this.algoStrategyShowParamCall
      );
    } else {
      this.algoStrategyShowParamCall.algoStrategy = null;
    }
  }

  onNodeUnselect(event): void {
    this.algoStrategyShowParamCall.algoStrategy = null;
  }

  private translateDataForAssetclass(): void {
    const fieldsAssetclass: ColumnConfig[] = [];
    this.addColumnToFields(fieldsAssetclass, DataType.String, 'assetclass.categoryType', '', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnToFields(
      fieldsAssetclass,
      DataType.String,
      'assetclass.specialInvestmentInstrument',
      '',
      true,
      false,
      { translateValues: TranslateValue.NORMAL }
    );

    const nonCustomAssetclasses = this.algoTop.algoAssetclassList.filter((ac) => !ac.isCustomCategory());
    TranslateHelper.createTranslatedValueStore(this.translateService, fieldsAssetclass, nonCustomAssetclasses);
  }

  private translateDataForStrategy(): void {
    const fieldAlgoStrategy: ColumnConfig[] = [];
    this.addColumnToFields(fieldAlgoStrategy, DataType.String, 'algoStrategyImplementations', '', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    const algoStrategyList: AlgoStrategy[] = [];
    this.traverseObjectTreeForAlgoStrategy(algoStrategyList, this.algoTop);
    TranslateHelper.createTranslatedValueStore(this.translateService, fieldAlgoStrategy, algoStrategyList);
  }

  private traverseObjectTreeForAlgoStrategy(
    algoStrategyList: AlgoStrategy[],
    algoTopAssetSecurity: AlgoTopAssetSecurity
  ): void {
    algoTopAssetSecurity.algoStrategyList && algoStrategyList.push(...algoTopAssetSecurity.algoStrategyList);
    const algoTopAssetSecurityList = algoTopAssetSecurity.getChildList();
    if (algoTopAssetSecurityList) {
      algoTopAssetSecurityList.forEach((atas) => {
        this.traverseObjectTreeForAlgoStrategy(algoStrategyList, atas);
      });
    }
  }

  private setFieldDescriptorInputAndShow<T extends AlgoTopAssetSecurity>(
    algoTopAssetSecurity: T,
    algoStrategy: AlgoStrategy,
    algoStrategyParamCall: AlgoStrategyParamCall
  ): void {
    const asiNo: number = AlgoStrategyImplementationType[algoStrategy.algoStrategyImplementations];
    const inputAndShowDefinition = this.algoStrategyDefinitionForm.inputAndShowDefinitionMap.get(asiNo);
    if (!inputAndShowDefinition) {
      this.algoStrategyService.getFormDefinitionsByAlgoStrategy(asiNo).subscribe((iasd) => {
        this.algoStrategyDefinitionForm.inputAndShowDefinitionMap.set(asiNo, iasd);
        algoStrategyParamCall.isComplexStrategy = iasd.isComplexStrategy;
        algoStrategyParamCall.fieldDescriptorShow = AlgoStrategyHelper.getFieldDescriptorInputAndShowByLevel(
          algoTopAssetSecurity,
          iasd
        );
        this.algoStrategyShowParamCall.algoStrategy = algoStrategy;
      });
    } else {
      algoStrategyParamCall.isComplexStrategy = inputAndShowDefinition.isComplexStrategy;
      algoStrategyParamCall.fieldDescriptorShow = AlgoStrategyHelper.getFieldDescriptorInputAndShowByLevel(
        algoTopAssetSecurity,
        inputAndShowDefinition
      );
      this.algoStrategyShowParamCall.algoStrategy = algoStrategy;
    }
  }
}
