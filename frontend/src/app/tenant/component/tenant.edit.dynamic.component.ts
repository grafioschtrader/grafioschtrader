import {Component, OnInit} from '@angular/core';
import {TenantEditComponent} from './tenant.edit.component';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TenantService} from '../service/tenant.service';
import {TranslateService} from '@ngx-translate/core';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {Tenant} from '../../entities/tenant';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';

/**
 * Dialog for change the existing tenant properties. It can also show only the currency, this is used
 * for changing the currencies on the client and its portfolios.
 */
@Component({
    template: `
       <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
  `,
    standalone: false
})
export class TenantEditDynamicComponent extends TenantEditComponent implements OnInit {
  onlyCurrency: boolean;

  constructor(private dynamicDialogConfig: DynamicDialogConfig,
              private dynamicDialogRef: DynamicDialogRef,
              gpsGT: GlobalparameterGTService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              tenantService: TenantService,
              translateService: TranslateService) {
    super(gpsGT, gps, messageToastService, tenantService, translateService, false, 6);
  }

  ngOnInit(): void {
    this.onlyCurrency = this.dynamicDialogConfig.data.onlyCurrency;
    this.existingTenant = this.dynamicDialogConfig.data.tenant;
    super.init(this.onlyCurrency);
    setTimeout(() => this.loadData());
  }

  override submit(value: { [name: string]: any }) {
    if (this.onlyCurrency) {
      this.tenantService.changeCurrencyTenantAndPortfolios(value.currency).subscribe({next: tenant => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: 'TENANT'});
        const tenantChanged: Tenant = Object.assign(new Tenant(), tenant);
        this.closeInputDialog(tenantChanged);
      }, error: () => this.configObject.submit.disabled = false});
    } else {
      super.submit(value);
    }
  }

  protected override closeInputDialog(tenant: Tenant) {
    this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.UPDATED, tenant));
  }

}
