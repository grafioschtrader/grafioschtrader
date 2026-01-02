import {Component, Input, OnInit, TemplateRef, ViewChild} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {TreeNode} from 'primeng/api';
import {TreeTableModule} from 'primeng/treetable';
import {TooltipModule} from 'primeng/tooltip';
import {TreeTableConfigBase} from '../../lib/datashowbase/tree.table.config.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {GTNetExchangeLogService} from '../service/gtnet-exchange-log.service';
import {GTNetExchangeLogTree, GTNetExchangeLogNode} from '../model/gtnet-exchange-log';
import {GTNetExchangeKindType} from '../model/gtnet';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ActivatedRoute} from '@angular/router';
import {AppSettings} from '../../shared/app.settings';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {ShowRecordConfigBase} from '../../lib/datashowbase/show.record.config.base';

/**
 * Component for displaying GTNet exchange log statistics.
 * Shows a table of GTNets with expandable rows containing supplier and consumer TreeTables.
 */
@Component({
  selector: 'gtnet-exchange-log',
  standalone: true,
  imports: [
    TreeTableModule,
    TooltipModule,
    TranslateModule,
    ConfigurableTableComponent
  ],
  template: `
    <configurable-table
      [data]="exchangeLogTrees"
      [fields]="mainTableFields"
      [dataKey]="'idGtNet'"
      [expandable]="true"
      [expandedRowTemplate]="expandedContent"
      [selectionMode]="null"
      [valueGetterFn]="getMainTableValue.bind(this)"
      [baseLocale]="baseLocale">
      <h4 caption>{{ titleKey | translate }}</h4>
    </configurable-table>

    <ng-template #expandedContent let-tree>
      <div class="p-3">
        <!-- Supplier Statistics -->
        <h5>{{ 'SUPPLIER_STATISTICS' | translate }}</h5>
        @if (hasData(tree.supplierTotal)) {
          <p-treeTable
            [value]="getSupplierNodes(tree)"
            [columns]="fields"
            [scrollable]="true">
            <ng-template pTemplate="header" let-columns>
              <tr>
                @for (col of columns; track col.field) {
                  <th [style.width.px]="col.width">{{ col.headerTranslated }}</th>
                }
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="columns">
              <tr>
                @for (col of columns; track col.field; let i = $index) {
                  <td>
                    @if (i === 0) {
                      <p-treeTableToggler [rowNode]="rowNode"></p-treeTableToggler>
                    }
                    {{ getValueByPath(rowData, col) }}
                  </td>
                }
              </tr>
            </ng-template>
          </p-treeTable>
        } @else {
          <p class="no-data">{{ 'NO_DATA_AVAILABLE' | translate }}</p>
        }

        <!-- Consumer Statistics -->
        <h5 class="mt-3">{{ 'CONSUMER_STATISTICS' | translate }}</h5>
        @if (hasData(tree.consumerTotal)) {
          <p-treeTable
            [value]="getConsumerNodes(tree)"
            [columns]="fields"
            [scrollable]="true">
            <ng-template pTemplate="header" let-columns>
              <tr>
                @for (col of columns; track col.field) {
                  <th [style.width.px]="col.width">{{ col.headerTranslated }}</th>
                }
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="columns">
              <tr>
                @for (col of columns; track col.field; let i = $index) {
                  <td>
                    @if (i === 0) {
                      <p-treeTableToggler [rowNode]="rowNode"></p-treeTableToggler>
                    }
                    {{ getValueByPath(rowData, col) }}
                  </td>
                }
              </tr>
            </ng-template>
          </p-treeTable>
        } @else {
          <p class="no-data">{{ 'NO_DATA_AVAILABLE' | translate }}</p>
        }
      </div>
    </ng-template>
  `,
  styles: [`
    h5 {
      margin-top: 0.5rem;
      margin-bottom: 0.5rem;
      font-weight: 500;
    }
    .no-data {
      padding: 1rem;
      text-align: center;
      color: #666;
      font-style: italic;
    }
    .mt-3 {
      margin-top: 1rem;
    }
    .p-3 {
      padding: 1rem;
    }
  `]
})
export class GTNetExchangeLogComponent extends TreeTableConfigBase implements OnInit {

  @Input() entityKind: GTNetExchangeKindType = GTNetExchangeKindType.LAST_PRICE;
  @ViewChild('expandedContent') expandedContent: TemplateRef<any>;

  exchangeLogTrees: GTNetExchangeLogTree[] = [];
  mainTableFields: ColumnConfig[] = [];
  titleKey: string = 'GTNET_EXCHANGE_LOG';

  private supplierNodesCache: Map<number, TreeNode[]> = new Map();
  private consumerNodesCache: Map<number, TreeNode[]> = new Map();

  constructor(
    private gtNetExchangeLogService: GTNetExchangeLogService,
    private route: ActivatedRoute,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    // Determine entityKind from route
    const path = this.route.snapshot.url[0]?.path;
    if (path === AppSettings.GT_NET_EXCHANGE_LOG_LASTPRICE_KEY) {
      this.entityKind = GTNetExchangeKindType.LAST_PRICE;
      this.titleKey = 'LAST_PRICE';
    } else if (path === AppSettings.GT_NET_EXCHANGE_LOG_HISTORICAL_KEY) {
      this.entityKind = GTNetExchangeKindType.HISTORICAL_PRICES;
      this.titleKey = 'HISTORICAL_PRICES';
    }

    // Configure main table column (outer table)
    this.mainTableFields = [
      ShowRecordConfigBase.createColumnConfig(DataType.String, 'domainRemoteName', 'DOMAIN_REMOTE_NAME', true, false)
    ];
    this.translateHeaders(
      this.mainTableFields.map(f => f.headerKey),
      this.mainTableFields
    );

    // Configure tree table columns (inner tables)
    this.addColumnFeqH(DataType.String, 'label', true, false, {width: 200});
    this.addColumnFeqH(DataType.NumericInteger, 'entitiesSent', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'entitiesUpdated', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'entitiesInResponse', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'requestCount', true, false);
    this.translateHeadersAndColumns();
    this.loadData();
  }

  getMainTableValue(row: GTNetExchangeLogTree, field: ColumnConfig): any {
    return row[field.field as keyof GTNetExchangeLogTree];
  }

  hasData(node: GTNetExchangeLogNode): boolean {
    return node && node.requestCount > 0;
  }

  getSupplierNodes(tree: GTNetExchangeLogTree): TreeNode[] {
    if (!this.supplierNodesCache.has(tree.idGtNet)) {
      this.supplierNodesCache.set(tree.idGtNet, this.buildTreeNodes(tree.supplierTotal));
    }
    return this.supplierNodesCache.get(tree.idGtNet)!;
  }

  getConsumerNodes(tree: GTNetExchangeLogTree): TreeNode[] {
    if (!this.consumerNodesCache.has(tree.idGtNet)) {
      this.consumerNodesCache.set(tree.idGtNet, this.buildTreeNodes(tree.consumerTotal));
    }
    return this.consumerNodesCache.get(tree.idGtNet)!;
  }

  private loadData(): void {
    this.supplierNodesCache.clear();
    this.consumerNodesCache.clear();

    this.gtNetExchangeLogService.getAllExchangeLogTrees(this.entityKind).subscribe(trees => {
      this.exchangeLogTrees = trees;
    });
  }

  private buildTreeNodes(node: GTNetExchangeLogNode): TreeNode[] {
    if (!node || !node.children || node.children.length === 0) {
      // If no children, show the total node itself
      if (node && node.requestCount > 0) {
        return [this.convertToTreeNode(node)];
      }
      return [];
    }

    return node.children.map(child => this.convertToTreeNode(child));
  }

  private convertToTreeNode(node: GTNetExchangeLogNode): TreeNode {
    const treeNode: TreeNode = {
      data: {
        label: node.label,
        entitiesSent: node.entitiesSent,
        entitiesUpdated: node.entitiesUpdated,
        entitiesInResponse: node.entitiesInResponse,
        requestCount: node.requestCount
      },
      expanded: false
    };

    if (node.children && node.children.length > 0) {
      treeNode.children = node.children.map(child => this.convertToTreeNode(child));
    }

    return treeNode;
  }
}
