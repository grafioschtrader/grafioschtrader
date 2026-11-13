import { Component, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import {
  UDFMetadataSharedTable,
  UDF_SHARED_TABLE_TEMPLATE
} from '../../lib/udfmeta/components/udf.metadata.shared.table';
import { UDFMetadataSecurity } from '../model/udf.metadata.security';
import { UDFMetadataSecurityService } from '../service/udf.metadata.security.service';
import { GlobalSessionNames } from '../../lib/global.session.names';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { UDFSubscriptionEditComponent } from '../../lib/udfmeta/components/udf-subscription-edit.component';
import { securitySubscriptionNotice, securitySubscriptionRows } from './udf-metadata-subscription.helper';

@Component({
  selector: 'udf-metadata-security-shared-table',
  template: UDF_SHARED_TABLE_TEMPLATE,
  imports: [TranslateModule, ConfigurableTableComponent, UDFSubscriptionEditComponent]
})
export class UDFMetadataSecuritySharedTableComponent extends UDFMetadataSharedTable<UDFMetadataSecurity> {
  readonly title = 'UDF_METADATA_SECURITY';
  protected readonly service = inject(UDFMetadataSecurityService);
  protected readonly cacheKey = GlobalSessionNames.UDF_FORM_DESCRIPTOR_SECURITY;
  constructor() {
    super();
    this.addColumn(DataType.String, 'categoryTypeEnums', 'ASSETCLASS', true, false, {
      translateValues: TranslateValue.UPPER_CASE_ARRAY_TO_COMMA_SEPERATED
    });
    this.addColumn(DataType.String, 'specialInvestmentInstrumentEnums', 'FINANCIAL_INSTRUMENT', true, false, {
      translateValues: TranslateValue.UPPER_CASE_ARRAY_TO_COMMA_SEPERATED
    });
    this.addColumnFeqH(DataType.String, 'fillSource', true, false, { translateValues: TranslateValue.NORMAL });
    this.addColumnFeqH(DataType.String, 'fillPeriod', true, false, { translateValues: TranslateValue.NORMAL });
    this.addColumnFeqH(DataType.Boolean, 'active', true, false, { templateName: 'check' });
    this.prepareTableAndTranslate();
  }

  override subscriptionRows(field: UDFMetadataSecurity): { label: string; value: string | null }[] {
    return securitySubscriptionRows(field, this.translateService);
  }

  override subscriptionNotice(field: UDFMetadataSecurity): string | null {
    return securitySubscriptionNotice(field);
  }
}
