import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { afterNextRender, Component, inject, Injector, OnInit } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { Tenant } from '../../entities/tenant';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { TenantEditComponent } from './tenant.edit.component';

/**
 * Dialog for change the existing tenant properties. It can also show only the currency, this is used
 * for changing the currencies on the client and its portfolios.
 */
@Component({
  template: `
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  `,
  imports: [DynamicFormComponent]
})
export class TenantEditDynamicComponent extends TenantEditComponent implements OnInit {
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private dynamicDialogRef = inject(DynamicDialogRef);

  onlyCurrency: boolean;
  private readonly renderInjector = inject(Injector);

  constructor() {
    super(false, 6);
  }

  ngOnInit(): void {
    this.onlyCurrency = this.dynamicDialogConfig.data.onlyCurrency;
    this.existingTenant = this.dynamicDialogConfig.data.tenant;
    super.init(this.onlyCurrency);
    afterNextRender(() => this.loadData(), { injector: this.renderInjector });
  }

  override submit(value: { [name: string]: any }) {
    if (this.onlyCurrency) {
      this.tenantService
        .changeCurrencyTenantAndPortfolios(value.currency)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (tenant) => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
              i18nRecord: 'TENANT'
            });
            const tenantChanged: Tenant = Object.assign(new Tenant(), tenant);
            this.closeInputDialog(tenantChanged);
          },
          error: () => (this.configObject.submit.disabled = false)
        });
    } else {
      super.submit(value);
    }
  }

  protected override closeInputDialog(tenant: Tenant) {
    this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.UPDATED, tenant));
  }
}
