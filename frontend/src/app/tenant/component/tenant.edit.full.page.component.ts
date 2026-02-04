import {AfterViewInit, Component, OnInit} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {Tenant} from '../../entities/tenant';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {LoginService} from '../../lib/login/service/log-in.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {TenantService} from '../service/tenant.service';
import {TenantEditComponent} from './tenant.edit.component';

/**
 * Edit tenant fields on a full page layout used for a new tenant.
 */
@Component({
  template: `
    <div class="container">
      <div class="jumbotron-replacement mx-auto">
        <h2>{{ 'TENANT' | translate }}</h2>
        <h4>{{ 'TENANT_REGISTER' | translate }}</h4>
        <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                      #form="dynamicForm"
                      (submitBt)="submit($event)">
        </dynamic-form>
      </div>
    </div>
  `,
  standalone: true,
  imports: [TranslateModule, DynamicFormComponent]
})
export class TenantEditFullPageComponent extends TenantEditComponent implements OnInit, AfterViewInit {

  constructor(private loginService: LoginService,
    gpsGT: GlobalparameterGTService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    tenantService: TenantService,
    translateService: TranslateService) {
    super(gpsGT, gps, messageToastService, tenantService, translateService, true, 4);
  }

  ngOnInit(): void {
    super.init(false);
  }

  ngAfterViewInit(): void {
    this.loadData();
  }

  protected override closeInputDialog(tenant: Tenant): void {
    this.loginService.logoutWithLoginView();
  }

}
