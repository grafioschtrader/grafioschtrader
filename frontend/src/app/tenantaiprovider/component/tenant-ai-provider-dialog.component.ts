import { Component, ChangeDetectionStrategy } from '@angular/core';
import { TenantAIProviderTableComponent } from './tenant-ai-provider-table.component';

/** Settings dialog; editing lives in its own form component. */
@Component({
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TenantAIProviderTableComponent],
  template: '<tenant-ai-provider-table />'
})
export class TenantAIProviderDialogComponent {}
