import { Component, OnInit, input } from '@angular/core';
import { StatisticsSummary, StatsProperty } from '../../entities/view/instrument.statistics.result';
import { TreeTableConfigBase } from '../../lib/datashowbase/tree.table.config.base';
import { TranslateModule } from '@ngx-translate/core';

import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TreeNode } from '@openng/optimus-ui/api';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { ConfigurableTreeTableComponent } from '../../lib/datashowbase/configurable-tree-table.component';

/**
 * Shows statistical data about an instrument.
 */
@Component({
  selector: 'instrument-statistics-summary',
  template: `
    <div class="datatable nestedtable" style="min-width: 200px; max-width: 400px;">
      <configurable-tree-table
        [data]="rootNodes"
        [fields]="fields"
        [selectionMode]="null"
        [enableSort]="false"
        [showGridlines]="false"
        [fieldsRevision]="fieldsRevision()"
        [valueGetterFn]="valueGetter"
        [negativeValueFn]="negativeValue">
        <div caption style="text-align:left">
          <h5>{{ 'STATISTICS_DATA' | translate }}</h5>
        </div>
      </configurable-tree-table>
    </div>
  `,
  imports: [TranslateModule, ConfigurableTreeTableComponent]
})
export class InstrumentStatisticsSummaryComponent extends TreeTableConfigBase implements OnInit {
  readonly statisticsSummary = input<StatisticsSummary>(undefined);
  readonly mainCurrency = input<string>();
  rootNodes: TreeNode[];
  readonly negativeValue = (row: any, field: ColumnConfig) => this.isValueByPathMinus(row, field);

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'property', 'PROPERTY_PERIOD', true, false, {
      translateValues: TranslateValue.UPPER_CASE
    });
    this.addColumn(DataType.Numeric, 'value', 'VALUE_1', true, false, {
      templateName: 'greenRed',
      width: 50
    });
    this.addColumn(DataType.Numeric, 'valueMC', 'VALUE_1', true, false, {
      templateName: 'greenRed',
      width: 50,
      headerSuffix: this.mainCurrency()
    });
    this.translateHeadersAndColumns();
    this.prepareData();
  }

  private prepareData(): void {
    const mainTree: TreeNode = {
      data: this.statisticsSummary().statsPropertyMap,
      children: [],
      leaf: false
    };
    const sptKeys = Object.keys(mainTree.data);
    sptKeys.forEach((sptKey) => {
      const data = { property: sptKey };
      mainTree.children.push({
        data,
        children: this.getChildren(mainTree, sptKey),
        expanded: true,
        leaf: false
      });
    });
    this.rootNodes = mainTree.children;
    this.createTranslateValuesStoreForTranslation(this.rootNodes);
  }

  private getChildren(mainTree: TreeNode, spt: string): TreeNode[] {
    const treeNodes: TreeNode[] = [];
    mainTree.data[spt].forEach((statsProperty: StatsProperty) => {
      treeNodes.push({ data: statsProperty, leaf: true });
    });
    return treeNodes;
  }
}
