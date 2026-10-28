import { Component } from '@angular/core';
import { TenantAIProviderTableComponent } from './tenant-ai-provider-table.component';

/** Settings dialog; editing lives in its own form component. */
@Component({
  imports: [TenantAIProviderTableComponent],
  template: '<tenant-ai-provider-table />'
})
export class TenantAIProviderDialogComponent {}
