import { Component, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { UDFMetadataSharedTable, UDF_SHARED_TABLE_TEMPLATE } from './udf.metadata.shared.table';
import { UDFMetadataGeneral } from '../model/udf.metadata';
import { UDFMetadataGeneralService } from '../service/udf.metadata.general.service';
import { GlobalSessionNames } from '../../global.session.names';
import { DataType } from '../../dynamic-form/models/data.type';
import { TranslateValue } from '../../datashowbase/column.config';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { UDFSubscriptionEditComponent } from './udf-subscription-edit.component';

@Component({
  selector: 'udf-metadata-general-shared-table',
  template: UDF_SHARED_TABLE_TEMPLATE,
  imports: [TranslateModule, ConfigurableTableComponent, UDFSubscriptionEditComponent]
})
export class UDFMetadataGeneralSharedTableComponent extends UDFMetadataSharedTable<UDFMetadataGeneral> {
  readonly title = 'UDF_METADATA_GENERAL';
  protected readonly service = inject(UDFMetadataGeneralService);
  protected readonly cacheKey = GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL;
  constructor() {
    super();
    this.addColumnFeqH(DataType.String, 'entity', true, false, { translateValues: TranslateValue.UPPER_CASE });
    this.prepareTableAndTranslate();
  }
}
