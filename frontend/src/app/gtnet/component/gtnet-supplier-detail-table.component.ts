import {Component, Input, OnInit} from '@angular/core';
import {GTNetExchangeService} from '../service/gtnet-exchange.service';
import {
  GTNetExchangeKindType,
  GTNetSupplierWithDetails
} from '../../lib/gnet/model/gtnet';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {TranslateService} from '@ngx-translate/core';
import {TreeTableConfigBase} from '../../lib/datashowbase/tree.table.config.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {TreeNode} from 'primeng/api';
import {ConfigurableTreeTableComponent} from '../../lib/datashowbase/configurable-tree-table.component';

/**
 * Displays supplier details for a security or currency pair as a tree table.
 * Root nodes represent entity kinds (HISTORICAL_PRICES, LAST_PRICE),
 * child nodes represent individual supplier peers with their settings.
 */
@Component({
  selector: 'gtnet-supplier-detail-table',
  standalone: true,
  imports: [ConfigurableTreeTableComponent],
  template: `
    <configurable-tree-table
      [data]="treeNodes" [fields]="fields" dataKey="uniqueKey"
      [selectionMode]="null"
      [valueGetterFn]="getValueByPath.bind(this)">
    </configurable-tree-table>
  `
})
export class GTNetSupplierDetailTableComponent extends TreeTableConfigBase implements OnInit {
  @Input() idSecuritycurrency: number;
  /** Discriminator type: 'S' for Security, 'C' for Currencypair */
  @Input() dtype: string;

  treeNodes: TreeNode[] = [];

  constructor(private gtNetExchangeService: GTNetExchangeService,
              translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addColumnFeqH(DataType.String, 'name', true, false, {width: 200, translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.NumericInteger, 'retryHistoryLoad', 'RETRY_HISTORY_LOAD');
    this.addColumn(DataType.DateString, 'historyMinDate', 'MIN_DATE');
    this.addColumn(DataType.DateString, 'historyMaxDate', 'MAX_DATE');
    this.addColumn(DataType.Numeric, 'ohlPercentage', 'OHL_PERCENTAGE', true, false, {headerSuffix: '%'});
    this.addColumn(DataType.NumericInteger, 'retryIntraLoad', 'RETRY_INTRA_LOAD');
    this.addColumn(DataType.DateTimeNumeric, 'sTimestamp', 'TIMEDATE');
    this.addColumn(DataType.DateTimeString, 'supplierLastUpdate', 'SUPPLIER_LAST_UPDATE');

    this.prepareTreeTableAndTranslate();
    this.loadData();
  }

  private loadData(): void {
    const observable = this.dtype === 'S'
      ? this.gtNetExchangeService.getSecuritySupplierDetails(this.idSecuritycurrency)
      : this.gtNetExchangeService.getCurrencypairSupplierDetails(this.idSecuritycurrency);

    observable.subscribe(data => {
      this.treeNodes = this.buildTreeNodes(data);
    });
  }

  /**
   * Builds tree nodes from the supplier details response.
   * Groups by entity kind (HISTORICAL_PRICES, LAST_PRICE) as root nodes,
   * with individual supplier peers as children.
   */
  private buildTreeNodes(data: GTNetSupplierWithDetails[]): TreeNode[] {
    const histChildren: TreeNode[] = [];
    const lastChildren: TreeNode[] = [];

    for (const dto of data) {
      for (const dws of dto.details) {
        const detail = dws.detail;
        const childData: any = {
          uniqueKey: `${dto.gtNet.idGtNet}-${detail.idGtNetSupplierDetail}`,
          name: dto.gtNet.domainRemoteName,
          supplierLastUpdate: dto.gtNet.gtNetConfig?.supplierLastUpdate
        };

        if (detail.entityKind === GTNetExchangeKindType.HISTORICAL_PRICES && dws.histSettings) {
          childData.retryHistoryLoad = dws.histSettings.retryHistoryLoad;
          childData.historyMinDate = dws.histSettings.historyMinDate;
          childData.historyMaxDate = dws.histSettings.historyMaxDate;
          childData.ohlPercentage = dws.histSettings.ohlPercentage;
          histChildren.push({data: childData, leaf: true});
        } else if (detail.entityKind === GTNetExchangeKindType.LAST_PRICE && dws.lastSettings) {
          childData.retryIntraLoad = dws.lastSettings.retryIntraLoad;
          childData.sTimestamp = dws.lastSettings.sTimestamp;
          lastChildren.push({data: childData, leaf: true});
        } else {
          // No settings yet, still show the peer
          if (detail.entityKind === GTNetExchangeKindType.HISTORICAL_PRICES) {
            histChildren.push({data: childData, leaf: true});
          } else {
            lastChildren.push({data: childData, leaf: true});
          }
        }
      }
    }

    const roots: TreeNode[] = [];
    if (histChildren.length > 0) {
      roots.push({
        data: {uniqueKey: 'root-hist', name: GTNetExchangeKindType[GTNetExchangeKindType.HISTORICAL_PRICES]},
        children: histChildren,
        expanded: true,
        leaf: false
      });
    }
    if (lastChildren.length > 0) {
      roots.push({
        data: {uniqueKey: 'root-last', name: GTNetExchangeKindType[GTNetExchangeKindType.LAST_PRICE]},
        children: lastChildren,
        expanded: true,
        leaf: false
      });
    }

    // Translate root node names (enum names like HISTORICAL_PRICES, LAST_PRICE)
    this.createTranslateValuesStoreForTranslation(roots);

    return roots;
  }
}
