import {Component, Input, OnInit} from '@angular/core';
import {StatisticsSummary} from '../../entities/view/instrument.statistics.result';
import {TreeTableConfigBase} from '../../shared/datashowbase/tree.table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TreeNode} from 'primeng/api';
import {DataType} from '../../dynamic-form/models/data.type';

@Component({
  selector: 'instrument-statistics-summary',
  template: `
    <div class="datatable nestedtable" style="min-width: 200px; max-width: 400px;">
      <p-treeTable [value]="rootNodes" [columns]="fields">
        <ng-template pTemplate="caption">
          <div style="text-align:left">
            <h4>{{"STATISTICS_DATA" | translate}}</h4>
          </div>
        </ng-template>
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th *ngFor="let field of fields">
              {{field.headerTranslated}}
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="fields">
          <tr>
            <td *ngFor="let field of fields; let i = index">
              <p-treeTableToggler [rowNode]="rowNode" *ngIf="i == 0"></p-treeTableToggler>
              {{getValueByPath(rowData, field)}}
            </td>
          </tr>
        </ng-template>
      </p-treeTable>
    </div>
  `
})
export class InstrumentStatisticsSummaryComponent extends TreeTableConfigBase implements OnInit {
  @Input() statisticsSummary: StatisticsSummary;
  @Input() mainCurrency: string;
  rootNodes: TreeNode[];

  constructor(translateService: TranslateService,
              gps: GlobalparameterService) {
    super(translateService, gps);

    this.addColumnFeqH(DataType.String, 'property', true, false);
    this.addColumnFeqH(DataType.Numeric, 'value', true, false);
    this.addColumnFeqH(DataType.Numeric, 'valueMC', true, false);

  }

  ngOnInit(): void {
    const mainTree: TreeNode = {data: this.statisticsSummary.statsPropertyMap, children: [], leaf: false};
    const sptKeys = Object.keys(mainTree.data);
    sptKeys.forEach(sptKey => {
      const data = {property: sptKey};
      mainTree.children.push({data: data, children: this.getChildren(mainTree, sptKey), expanded: true, leaf: false});
    });
    this.translateHeadersAndColumns();
    this.rootNodes = mainTree.children;
  }

  private getChildren(mainTree: TreeNode, spt: string): TreeNode[] {
    const treeNodes: TreeNode[] = [];
    mainTree.data[spt].forEach(statsProperty => {
      treeNodes.push({data: statsProperty, leaf: true});
    });
    return treeNodes;
  }
}
