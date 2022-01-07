import {Component, Input, OnInit} from '@angular/core';
import {TenantEditComponent} from './tenant.edit.component';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TenantService} from '../service/tenant.service';
import {TranslateService} from '@ngx-translate/core';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {Tenant} from '../../entities/tenant';
import {InfoLevelType} from '../../shared/message/info.leve.type';

/**
 * Dialog for change the existing tenant properties. It can also show only the currency, this is used
 * for changing the currencies on the client and its portfolios.
 */
@Component({
  selector: 'tenant-edit-dialog',
  template: `
    <p-dialog header="{{(onlyCurrency? 'CLIENT_CHANGE_CURRENCY': 'CLIENT') | translate}}" [(visible)]="visibleTenantDialog"
              [responsive]="true" [style]="{width: '450px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class TenantEditDialogComponent extends TenantEditComponent implements OnInit {
  @Input() onlyCurrency: boolean;

  constructor(gps: GlobalparameterService,
              messageToastService: MessageToastService,
              tenantService: TenantService,
              translateSercice: TranslateService) {
    super(gps, messageToastService, tenantService, translateSercice, false, 6);
  }

  ngOnInit(): void {
    super.init(this.onlyCurrency);
  }

  public onShow(event): void {
    setTimeout(() => this.loadData());
  }

  submit(value: { [name: string]: any }) {
    if (this.onlyCurrency) {
      this.tenantService.changeCurrencyTenantAndPortfolios(value.currency).subscribe(tenant => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: 'CLIENT'});
        const tenantChanged: Tenant = Object.assign(new Tenant(), tenant);
        this.afterSaved(tenantChanged);
      }, () => this.configObject.submit.disabled = false);
    } else {
      super.submit(value);
    }
  }

  protected afterSaved(tenant: Tenant): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED, tenant));
  }


}
