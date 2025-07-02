import {AfterViewInit, Component, Input, OnInit} from '@angular/core';
import {TenantEditComponent} from './tenant.edit.component';
import {GlobalparameterService} from '../../../shared/service/globalparameter.service';
import {MessageToastService} from '../../../shared/message/message.toast.service';
import {TenantService} from '../service/tenant.service';
import {TranslateService} from '@ngx-translate/core';
import {Tenant} from '../../../entities/tenant';
import {LoginService} from '../../../shared/login/service/log-in.service';
import {GlobalparameterGTService} from '../../../gtservice/globalparameter.gt.service';
import {BusinessHelper} from '../../../shared/helper/business.helper';
import {HelpIds} from '../../../shared/help/help.ids';
import {CallParam} from '../../../shared/maintree/types/dialog.visible';

/**
 * Edit tenant fields on a full page layout used for a new tenant.
 */
@Component({
    template: `
    <div class="container">
      <div class="jumbotron center-block">
        <h2>{{'CLIENT' | translate}}</h2>
        <h4>{{'CLIENT_REGISTER' | translate}}</h4>
        <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                      #form="dynamicForm"
                      (submitBt)="submit($event)">
        </dynamic-form>
      </div>
    </div>
  `,
    standalone: false
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
