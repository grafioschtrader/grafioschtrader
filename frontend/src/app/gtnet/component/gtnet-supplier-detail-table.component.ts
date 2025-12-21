import {Component, Input, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {GTNetExchangeService} from '../service/gtnet-exchange.service';
import {GTNetExchangeKindType, GTNetServerStateTypes} from '../model/gtnet';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ShowRecordConfigBase} from '../../lib/datashowbase/show.record.config.base';

@Component({
  selector: 'gtnet-supplier-detail-table',
  standalone: true,
  imports: [CommonModule, ConfigurableTableComponent, TranslateModule],
  template: `
    <configurable-table
      [data]="flattenedData"
      [fields]="fields"
      dataKey="uniqueId"
      [paginator]="false"
      [rows]="100"
      [enableCustomSort]="true"
      [contextMenuEnabled]="false">
    </configurable-table>
  `
})
export class GTNetSupplierDetailTableComponent implements OnInit {
  @Input() idSecuritycurrency: number;

  flattenedData: any[] = [];
  fields: ColumnConfig[] = [];

  constructor(private gtNetExchangeService: GTNetExchangeService, public translateService: TranslateService) {}

  ngOnInit(): void {
    this.fields = [
      ShowRecordConfigBase.createColumnConfig(DataType.String, 'supplier.gtNet.domainRemoteName', 'GT_NET_REMOTE_DOMAIN'),
      ShowRecordConfigBase.createColumnConfig(DataType.String, 'serverState', 'GT_NET_ENTITY_SERVER_STATE', true, true,
        {fieldValueFN: this.getServerStateLabel.bind(this)}),
      ShowRecordConfigBase.createColumnConfig(DataType.String, 'detail.priceType', 'PRICE_TYPE'),
      ShowRecordConfigBase.createColumnConfig(DataType.DateTimeNumeric, 'supplier.lastUpdate', 'LAST_UPDATE')
    ];
    this.translateHeaders(this.fields);

    this.gtNetExchangeService.getSupplierDetails(this.idSecuritycurrency).subscribe(data => {
      this.flattenedData = [];
      data.forEach(dto => {
        dto.details.forEach(detail => {
          this.flattenedData.push({
            uniqueId: `${dto.supplier.idGtNetSupplier}-${detail.idGtNetSupplierDetail}`,
            supplier: dto.supplier,
            detail: detail
          });
        });
      });
    });
  }

  getServerStateLabel(data: any, field: ColumnConfig): string {
    // Get the server state from the appropriate GTNetEntity based on the detail's priceType
    const gtNet = data.supplier.gtNet;
    const priceType = data.detail.priceType;
    const entityKind = priceType === 'HISTORICAL' ? GTNetExchangeKindType.HISTORICAL_PRICES : GTNetExchangeKindType.LAST_PRICE;
    const entity = gtNet.gtNetEntities?.find((e: any) => e.entityKind === entityKind);
    const state = entity?.serverState ?? GTNetServerStateTypes.SS_NONE;
    return GTNetServerStateTypes[state];
  }

  translateHeaders(fields: ColumnConfig[]): void {
    const headerKeys = fields.map(f => f.headerKey);
    this.translateService.get(headerKeys).subscribe(translations => {
      fields.forEach(f => {
        if (f.headerKey) {
          f.headerTranslated = translations[f.headerKey];
        }
      });
    });
  }
}
