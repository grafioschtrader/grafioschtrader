import {Component, Input, OnInit} from '@angular/core';
import {StatisticsSummary} from '../../entities/view/instrument.statistics.result';
import {TreeTableConfigBase} from '../../shared/datashowbase/tree.table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';

import {DataType} from '../../dynamic-form/models/data.type';
import {TreeNode} from 'primeng/api';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {ColumnConfig, TranslateValue} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';

/**
 * Shows statistical data about an instrument.
 */
@Component({
  selector: 'instrument-statistics-summary',
  template: `
    <div class="datatable nestedtable" style="min-width: 200px; max-width: 400px;">
      <p-treeTable [value]="rootNodes" [columns]="fields">
        <ng-template pTemplate="caption">
          <div style="text-align:left">
            <h5>{{"STATISTICS_DATA" | translate}}</h5>
          </div>
        </ng-template>
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th *ngFor="let field of fields" [style.width.px]="field.width">
              {{field.headerTranslated}}
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="fields">
          <tr>
            <ng-container *ngFor="let field of fields; let i = index">
              <td *ngIf="field.visible"
                  [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric)? 'text-right': ''"
                  [style.width.px]="field.width">
                <p-treeTableToggler [rowNode]="rowNode" *ngIf="i === 0"></p-treeTableToggler>
                <ng-container [ngSwitch]="field.templateName">
                  <ng-container *ngSwitchCase="'greenRed'">
                  <span [pTooltip]="getValueByPath(rowData, field)"
                        [style.color]='isValueByPathMinus(rowData, field)? "red": "inherit"'
                        tooltipPosition="top">
                    {{getValueByPath(rowData, field)}}
                  </span>
                  </ng-container>
                  <ng-container *ngSwitchDefault>
                    <span [pTooltip]="getValueByPath(rowData, field)">{{getValueByPath(rowData, field)}}</span>
                  </ng-container>
                </ng-container>
              </td>
            </ng-container>
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
