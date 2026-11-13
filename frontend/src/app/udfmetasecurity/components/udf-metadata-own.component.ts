import { Component } from '@angular/core';
import { UDFMetadataGeneralTableComponent } from '../../lib/udfmeta/components/udf.metadata.general.table.component';
import { UDFMetadataSecurityTableComponent } from './udf.metadata.security.table.component';

@Component({
  selector: 'udf-metadata-own',
  template: `<udf-metadata-general-table /><udf-metadata-security-table />`,
  styles: `
    :host {
      display: grid;
      grid-template-rows: 1fr 1fr;
      height: 100%;
      min-height: 0;
    }
  `,
  imports: [UDFMetadataGeneralTableComponent, UDFMetadataSecurityTableComponent]
})
export class UDFMetadataOwnComponent {}
