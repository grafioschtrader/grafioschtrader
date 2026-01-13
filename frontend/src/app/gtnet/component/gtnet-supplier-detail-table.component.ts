import {Component, Input, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {GTNetExchangeService} from '../service/gtnet-exchange.service';
import {GTNetExchangeKindType, GTNetServerStateTypes} from '../model/gtnet';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ShowRecordConfigBase} from '../../lib/datashowbase/show.record.config.base';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {FilterService} from 'primeng/api';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';

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
      [valueGetterFn]="getValueByPath.bind(this)"
      [contextMenuEnabled]="false">
    </configurable-table>
  `
})
export class GTNetSupplierDetailTableComponent extends TableConfigBase implements OnInit {
  @Input() idSecuritycurrency: number;
  /** Discriminator type: 'S' for Security, 'C' for Currencypair */
  @Input() dtype: string;

  flattenedData: any[] = [];

  constructor(private gtNetExchangeService: GTNetExchangeService,
  filterService: FilterService, usersettingsService: UserSettingsService,
  translateService: TranslateService, gps: GlobalparameterService) {
    super(filterService, usersettingsService, translateService, gps);
  }

  ngOnInit(): void {
    this.addColumnFeqH(DataType.String, 'gtNet.domainRemoteName');
    this.addColumnFeqH(DataType.String, 'detail.serverState',  true, false, {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, 'detail.entityKind', true, false, {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.DateTimeNumeric, 'gtNet.gtNetConfig.supplierLastUpdate');

    this.prepareData();
  }

  private prepareData(): void {
    const observable = this.dtype === 'S'
      ? this.gtNetExchangeService.getSecuritySupplierDetails(this.idSecuritycurrency)
      : this.gtNetExchangeService.getCurrencypairSupplierDetails(this.idSecuritycurrency);

    observable.subscribe(data => {
      this.flattenedData = [];
      data.forEach(dto => {
        dto.details.forEach(detail => {
          const entity = dto.gtNet.gtNetEntities?.find((e: any) => e.entityKind === detail.entityKind);

          this.flattenedData.push({
            uniqueId: `${dto.gtNet.idGtNet}-${detail.idGtNetSupplierDetail}`,
            gtNet: dto.gtNet,
            detail: detail
          });
          detail['serverState'] = entity?.serverState ?? GTNetServerStateTypes.SS_NONE;
        });

      });
      this.createTranslatedValueStore(this.flattenedData);
      this.translateHeadersAndColumns();

    });
  }

  getServerStateLabel(data: any, field: ColumnConfig): string {
    // Get the server state from the appropriate GTNetEntity based on the detail's entityKind
    const gtNet = data.gtNet;
    const entityKind = data.detail.entityKind;
    const entity = gtNet.gtNetEntities?.find((e: any) => e.entityKind === entityKind);
    const state = entity?.serverState ?? GTNetServerStateTypes.SS_NONE;
    return GTNetServerStateTypes[state];
  }

}
