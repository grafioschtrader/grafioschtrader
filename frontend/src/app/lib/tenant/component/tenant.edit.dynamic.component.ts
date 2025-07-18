import {Component, OnInit} from '@angular/core';
import {TenantEditComponent} from './tenant.edit.component';
import {GlobalparameterService} from '../../../shared/service/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {TenantService} from '../service/tenant.service';
import {TranslateService} from '@ngx-translate/core';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {Tenant} from '../../../entities/tenant';
import {InfoLevelType} from '../../message/info.leve.type';
import {GlobalparameterGTService} from '../../../gtservice/globalparameter.gt.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';

/**
 * Dialog for change the existing tenant properties. It can also show only the currency, this is used
 * for changing the currencies on the client and its portfolios.
 */
@Component({
    template: `
    <!--
    <p-dialog header="{{(onlyCurrency? 'CLIENT_CHANGE_CURRENCY': 'CLIENT') | translate}}" [(visible)]="visibleTenantDialog"
               [style]="{width: '450px'}" (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
    -->
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    <!--
    </p-dialog>
    -->
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
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: 'CLIENT'});
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
