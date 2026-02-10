import {Component, Input, OnInit} from '@angular/core';
import {StatisticsSummary} from '../../entities/view/instrument.statistics.result';
import {TreeTableConfigBase} from '../../lib/datashowbase/tree.table.config.base';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';

import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TreeNode} from 'primeng/api';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {ConfigurableTreeTableComponent} from '../../lib/datashowbase/configurable-tree-table.component';

/**
 * Shows statistical data about an instrument.
 */
@Component({
  selector: 'instrument-statistics-summary',
  template: `
    <div class="datatable nestedtable" style="min-width: 200px; max-width: 400px;">
      <configurable-tree-table
        [data]="rootNodes" [fields]="fields"
        [selectionMode]="null" [enableSort]="false"
        [showGridlines]="false"
        [valueGetterFn]="getValueByPath.bind(this)"
        [negativeValueFn]="isValueByPathMinus.bind(this)">
        <div caption style="text-align:left">
          <h5>{{ "STATISTICS_DATA" | translate }}</h5>
        </div>
      </configurable-tree-table>
    </div>
  `,
  imports: [
    TranslateModule,
    ConfigurableTreeTableComponent
  ],
  standalone: true
})
export class InstrumentStatisticsSummaryComponent extends TreeTableConfigBase implements OnInit {
  @Input() statisticsSummary: StatisticsSummary;
  @Input() mainCurrency: string;
  rootNodes: TreeNode[];

  constructor(translateService: TranslateService,
    gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'property', 'PROPERTY_PERIOD', true, false,
      {translateValues: TranslateValue.UPPER_CASE});
    this.addColumn(DataType.Numeric, 'value', 'VALUE_1', true, false,
      {templateName: 'greenRed', width: 50});
    this.addColumn(DataType.Numeric, 'valueMC', 'VALUE_1', true, false,
      {templateName: 'greenRed', width: 50, headerSuffix: this.mainCurrency});
    this.translateHeadersAndColumns();
    this.prepareData();
  }

  private prepareData(): void {
    const mainTree: TreeNode = {data: this.statisticsSummary.statsPropertyMap, children: [], leaf: false};
    const sptKeys = Object.keys(mainTree.data);
    sptKeys.forEach(sptKey => {
      const data = {property: sptKey};
      mainTree.children.push({data, children: this.getChildren(mainTree, sptKey), expanded: true, leaf: false});
    });
    this.rootNodes = mainTree.children;
    this.createTranslateValuesStoreForTranslation(this.rootNodes);
  }

  private getChildren(mainTree: TreeNode, spt: string): TreeNode[] {
    const treeNodes: TreeNode[] = [];
    mainTree.data[spt].forEach(statsProperty => {
      treeNodes.push({data: statsProperty, leaf: true});
    });
    return treeNodes;
  }

}
