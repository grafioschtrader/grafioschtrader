import { Component } from '@angular/core';
import { UDFMetadataGeneralSharedTableComponent } from '../../lib/udfmeta/components/udf.metadata.general.shared.table.component';
import { UDFMetadataSecuritySharedTableComponent } from './udf.metadata.security.shared.table.component';

@Component({
  selector: 'udf-metadata-shared',
  template: `<udf-metadata-general-shared-table /><udf-metadata-security-shared-table />`,
  styles: `
    :host {
      display: grid;
      grid-template-rows: 1fr 1fr;
      height: 100%;
      min-height: 0;
    }
  `,
  imports: [UDFMetadataGeneralSharedTableComponent, UDFMetadataSecuritySharedTableComponent]
})
export class UDFMetadataSharedComponent {}
